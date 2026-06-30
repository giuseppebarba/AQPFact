package com.example.aqpfact.data

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.Spreadsheet
import com.google.api.services.sheets.v4.model.SpreadsheetProperties
import com.google.api.services.sheets.v4.model.ValueRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GoogleDriveHelper(private val context: Context) {
    private val TAG = "GoogleDriveHelper"

    private fun getCredential(): GoogleAccountCredential? {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account == null) {
            Log.e(TAG, "getCredential: No signed in account found. User must sign in first.")
            return null
        }
        Log.d(TAG, "getCredential: Using account ${account.email}")
        return GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_FILE, SheetsScopes.SPREADSHEETS)
        ).apply {
            selectedAccount = account.account
        }
    }

    private fun getDriveService(): Drive? {
        val credential = getCredential() ?: return null
        return Drive.Builder(AndroidHttp.newCompatibleTransport(), GsonFactory(), credential)
            .setApplicationName("AQPFact").build()
    }

    private fun getSheetsService(): Sheets? {
        val credential = getCredential() ?: return null
        return Sheets.Builder(AndroidHttp.newCompatibleTransport(), GsonFactory(), credential)
            .setApplicationName("AQPFact").build()
    }

    suspend fun exportToSheets(
        readings: List<Reading>,
        totalBill: Double,
        fixedCosts: Double,
        meterNames: Map<Int, String>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "exportToSheets: Starting export of ${readings.size} readings")
            val driveService = getDriveService() ?: run {
                Log.e(TAG, "exportToSheets: Failed to get Drive service")
                return@withContext false
            }
            val sheetsService = getSheetsService() ?: run {
                Log.e(TAG, "exportToSheets: Failed to get Sheets service")
                return@withContext false
            }

            // 1. Find or Create the Spreadsheet
            Log.d(TAG, "exportToSheets: Searching for spreadsheet 'AQPFact_Readings'")
            val query = "name = 'AQPFact_Readings' and mimeType = 'application/vnd.google-apps.spreadsheet' and trashed = false"
            val fileList = driveService.files().list().setQ(query).execute()
            var spreadsheetId = fileList.files.firstOrNull()?.id

            if (spreadsheetId == null) {
                Log.d(TAG, "exportToSheets: Spreadsheet not found, creating new one")
                val fileMetadata = com.google.api.services.drive.model.File().apply {
                    name = "AQPFact_Readings"
                    mimeType = "application/vnd.google-apps.spreadsheet"
                }
                val file = driveService.files().create(fileMetadata).execute()
                spreadsheetId = file.id
            }

            // 2. Prepare Data (One row per session/groupId)
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            
            // Header
            val header = listOf(
                "Data", "Generale (m³)", 
                "Contatore 1 (m³)", "Contatore 2 (m³)", "Contatore 3 (m³)",
                "Costo Totale (€)", "Costi Fissi (€)",
                "Utenza 1 (€)", "Utenza 2 (€)", "Utenza 3 (€)"
            )

            // Gruppiamo per groupId (le letture fatte insieme)
            val sessions = readings.groupBy { it.groupId ?: "SINGLE_${it.id}" }
                .toList()
                .sortedBy { it.second.first().date } // Ordine cronologico

            val readingsByMeter = readings.groupBy { it.meterId }
                .mapValues { it.value.sortedBy { r -> r.date } }

            val rows = sessions.map { (_, sessionReadings) ->
                val date = sessionReadings.first().date
                
                // Valori delle letture correnti
                val valGen = sessionReadings.find { it.meterId == 0 }?.value ?: 0.0
                val valU1 = sessionReadings.find { it.meterId == 1 }?.value ?: 0.0
                val valU2 = sessionReadings.find { it.meterId == 2 }?.value ?: 0.0
                val valU3 = sessionReadings.find { it.meterId == 3 }?.value ?: 0.0

                // Calcolo consumi (differenza con lettura precedente nel DB)
                fun getCons(meterId: Int, currentVal: Double, currentDate: Long): Double {
                    val prev = readingsByMeter[meterId]?.findLast { it.date < currentDate }
                    return if (prev != null) (currentVal - prev.value).coerceAtLeast(0.0) else 0.0
                }

                val consU1 = getCons(1, valU1, date)
                val consU2 = getCons(2, valU2, date)
                val consU3 = getCons(3, valU3, date)
                val totalCons = consU1 + consU2 + consU3

                // Ripartizione costi
                val varPart = (totalBill - fixedCosts).coerceAtLeast(0.0)
                val fixedPerUser = fixedCosts / 3.0

                fun calcUserCost(cons: Double): Double {
                    val variable = if (totalCons > 0) (cons / totalCons) * varPart else 0.0
                    return variable + fixedPerUser
                }

                val costU1 = calcUserCost(consU1)
                val costU2 = calcUserCost(consU2)
                val costU3 = calcUserCost(consU3)

                listOf(
                    dateFormat.format(Date(date)),
                    String.format(Locale.US, "%.2f", valGen),
                    String.format(Locale.US, "%.2f", valU1),
                    String.format(Locale.US, "%.2f", valU2),
                    String.format(Locale.US, "%.2f", valU3),
                    String.format(Locale.US, "%.2f", totalBill),
                    String.format(Locale.US, "%.2f", fixedCosts),
                    String.format(Locale.US, "%.2f", costU1),
                    String.format(Locale.US, "%.2f", costU2),
                    String.format(Locale.US, "%.2f", costU3)
                )
            }

            val body = ValueRange().setValues(listOf(header) + rows)

            // 3. Update the Sheet
            val spreadsheet = sheetsService.spreadsheets().get(spreadsheetId)
                .setFields("sheets.properties.title")
                .execute()
            val sheetName = spreadsheet.sheets?.firstOrNull()?.properties?.title ?: "Sheet1"

            sheetsService.spreadsheets().values()
                .update(spreadsheetId, "'$sheetName'!A1", body)
                .setValueInputOption("RAW")
                .execute()

            true
        } catch (e: Exception) {
            Log.e(TAG, "exportToSheets Error: ${e.message}", e)
            false
        }
    }

    suspend fun importFromSheets(): List<Reading>? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "importFromSheets: Starting import")
            val driveService = getDriveService() ?: return@withContext null
            val sheetsService = getSheetsService() ?: return@withContext null

            val query = "name = 'AQPFact_Readings' and mimeType = 'application/vnd.google-apps.spreadsheet' and trashed = false"
            val fileList = driveService.files().list().setQ(query).execute()
            val spreadsheetId = fileList.files.firstOrNull()?.id ?: run {
                Log.e(TAG, "importFromSheets: Spreadsheet 'AQPFact_Readings' not found on Drive")
                return@withContext null
            }

            Log.d(TAG, "importFromSheets: Fetching values from spreadsheetId: $spreadsheetId")
            val spreadsheet = sheetsService.spreadsheets().get(spreadsheetId)
                .setFields("sheets.properties.title")
                .execute()
            val sheetName = spreadsheet.sheets?.firstOrNull()?.properties?.title ?: "Sheet1"

            val response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "'$sheetName'!A:K")
                .execute()

            val values = response.getValues() ?: run {
                Log.d(TAG, "importFromSheets: No values found in sheet")
                return@withContext emptyList<Reading>()
            }
            
            if (values.size <= 1) {
                Log.d(TAG, "importFromSheets: Sheet contains only header or is empty")
                return@withContext emptyList<Reading>()
            }

            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

            val readings = mutableListOf<Reading>()
            
            // Saltiamo l'header e processiamo ogni riga (sessione)
            values.drop(1).forEach { row ->
                try {
                    if (row.isEmpty()) return@forEach
                    
                    val dateStr = row[0].toString()
                    val timestamp = dateFormat.parse(dateStr)?.time ?: System.currentTimeMillis()
                    val groupId = "IMPORT_${timestamp}"

                    // Helper per estrarre Double in modo sicuro
                    fun getVal(index: Int): Double = row.getOrNull(index)?.toString()?.toDoubleOrNull() ?: 0.0

                    // Creiamo una Reading per ogni contatore presente nella riga
                    // Indici: 1: Generale, 2: U1, 3: U2, 4: U3
                    readings.add(Reading(meterId = 0, value = getVal(1), date = timestamp, groupId = groupId))
                    readings.add(Reading(meterId = 1, value = getVal(2), date = timestamp, groupId = groupId))
                    readings.add(Reading(meterId = 2, value = getVal(3), date = timestamp, groupId = groupId))
                    readings.add(Reading(meterId = 3, value = getVal(4), date = timestamp, groupId = groupId))

                } catch (e: Exception) {
                    Log.w(TAG, "importFromSheets: Error parsing row $row: ${e.message}")
                }
            }
            Log.d(TAG, "importFromSheets: Successfully imported ${readings.size} readings from ${values.size - 1} sessions")
            readings
        } catch (e: GoogleJsonResponseException) {
            Log.e(TAG, "importFromSheets: Google API Error: ${e.details.message} (Code: ${e.statusCode})", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "importFromSheets: Unexpected error: ${e.message}", e)
            null
        }
    }
}
