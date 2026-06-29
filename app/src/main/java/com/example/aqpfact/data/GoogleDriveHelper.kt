package com.example.aqpfact.data

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
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

    private fun getCredential(): GoogleAccountCredential? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        return GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_FILE, SheetsScopes.SPREADSHEETS)
        ).apply {
            selectedAccount = account.account
        }
    }

    private fun getDriveService(): Drive? {
        val credential = getCredential() ?: return null
        return Drive.Builder(NetHttpTransport(), GsonFactory(), credential)
            .setApplicationName("AQPFact").build()
    }

    private fun getSheetsService(): Sheets? {
        val credential = getCredential() ?: return null
        return Sheets.Builder(NetHttpTransport(), GsonFactory(), credential)
            .setApplicationName("AQPFact").build()
    }

    suspend fun exportToSheets(readings: List<Reading>): Boolean = withContext(Dispatchers.IO) {
        try {
            val driveService = getDriveService() ?: return@withContext false
            val sheetsService = getSheetsService() ?: return@withContext false

            // 1. Find or Create the Spreadsheet
            val query = "name = 'AQPFact_Readings' and mimeType = 'application/vnd.google-apps.spreadsheet' and trashed = false"
            val fileList = driveService.files().list().setQ(query).execute()
            var spreadsheetId = fileList.files.firstOrNull()?.id

            if (spreadsheetId == null) {
                val newSheet = Spreadsheet().setProperties(SpreadsheetProperties().setTitle("AQPFact_Readings"))
                val createdSheet = sheetsService.spreadsheets().create(newSheet).execute()
                spreadsheetId = createdSheet.spreadsheetId
            }

            // 2. Prepare Data
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            val header = listOf("ID", "Data", "Contatore ID", "Valore", "Gruppo ID", "Foto Path")
            val rows = readings.map { reading ->
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

            // 3. Update the Sheet (overwrite existing content in 'Sheet1')
            sheetsService.spreadsheets().values()
                .update(spreadsheetId, "Sheet1!A1", body)
                .setValueInputOption("RAW")
                .execute()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importFromSheets(): List<Reading>? = withContext(Dispatchers.IO) {
        try {
            val driveService = getDriveService() ?: return@withContext null
            val sheetsService = getSheetsService() ?: return@withContext null

            val query = "name = 'AQPFact_Readings' and mimeType = 'application/vnd.google-apps.spreadsheet' and trashed = false"
            val fileList = driveService.files().list().setQ(query).execute()
            val spreadsheetId = fileList.files.firstOrNull()?.id ?: return@withContext null

            val response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "Sheet1!A:F")
                .execute()

            val values = response.getValues() ?: return@withContext emptyList()
            if (values.size <= 1) return@withContext emptyList() // Only header or empty

            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

            return@withContext values.drop(1).mapNotNull { row ->
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
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
