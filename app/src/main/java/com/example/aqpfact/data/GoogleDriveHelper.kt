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

    suspend fun exportToSheets(readings: List<Reading>): Boolean = withContext(Dispatchers.IO) {
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
                // Use Drive API to create the file with the specific title and mimeType
                val file = driveService.files().create(fileMetadata).execute()
                spreadsheetId = file.id
                Log.d(TAG, "exportToSheets: Created new spreadsheet with ID: $spreadsheetId")
            } else {
                Log.d(TAG, "exportToSheets: Found existing spreadsheet with ID: $spreadsheetId")
            }

            // 2. Prepare Data
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            val header = listOf("ID", "Data", "Contatore ID", "Valore", "Gruppo ID", "Foto Path")
            
            // Ordiniamo per data crescente per avere le letture più recenti in fondo (stile log)
            val sortedReadings = readings.sortedBy { it.date }
            
            val rows = sortedReadings.map { reading ->
                listOf(
                    reading.id.toString(),
                    dateFormat.format(Date(reading.date)),
                    reading.meterId.toString(),
                    reading.value.toString(),
                    reading.groupId ?: "",
                    reading.photoPath ?: ""
                )
            }
            val body = ValueRange().setValues(listOf(header) + rows)
            Log.d(TAG, "exportToSheets: Preparate ${rows.size} righe per l'esportazione")

            // 3. Update the Sheet (overwrite existing content in the first sheet)
            val spreadsheet = sheetsService.spreadsheets().get(spreadsheetId)
                .setFields("sheets.properties.title")
                .execute()
            val sheetName = spreadsheet.sheets?.firstOrNull()?.properties?.title ?: "Sheet1"

            Log.d(TAG, "exportToSheets: Updating sheet values for spreadsheetId: $spreadsheetId, sheet: $sheetName")
            sheetsService.spreadsheets().values()
                .update(spreadsheetId, "'$sheetName'!A1", body)
                .setValueInputOption("RAW")
                .execute()

            Log.d(TAG, "exportToSheets: Export successful")
            true
        } catch (e: GoogleJsonResponseException) {
            Log.e(TAG, "exportToSheets: Google API Error: ${e.details.message} (Code: ${e.statusCode})", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "exportToSheets: Unexpected error: ${e.message}", e)
            e.printStackTrace()
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
                .get(spreadsheetId, "'$sheetName'!A:F")
                .execute()

            val values = response.getValues() ?: run {
                Log.d(TAG, "importFromSheets: No values found in sheet")
                return@withContext emptyList<Reading>()
            }
            
            if (values.size <= 1) {
                Log.d(TAG, "importFromSheets: Sheet contains only header or is empty")
                return@withContext emptyList<Reading>()
            }

            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

            val readings = values.drop(1).mapNotNull { row ->
                try {
                    if (row.size < 4) return@mapNotNull null
                    Reading(
                        id = row[0].toString().toLongOrNull() ?: 0L,
                        date = dateFormat.parse(row[1].toString())?.time ?: System.currentTimeMillis(),
                        meterId = row[2].toString().toIntOrNull() ?: 0,
                        value = row[3].toString().toDoubleOrNull() ?: 0.0,
                        groupId = if (row.size > 4) row[4].toString().takeIf { it.isNotEmpty() } else null,
                        photoPath = if (row.size > 5) row[5].toString().takeIf { it.isNotEmpty() } else null
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "importFromSheets: Error parsing row $row: ${e.message}")
                    null
                }
            }
            Log.d(TAG, "importFromSheets: Successfully imported ${readings.size} readings")
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
