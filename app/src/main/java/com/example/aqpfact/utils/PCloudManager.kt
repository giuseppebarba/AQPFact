package com.example.aqpfact.utils

import android.content.Context
import android.util.Log
import com.pcloud.sdk.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PCloudManager(private val context: Context) {
    private var client: ApiClient? = null

    fun initialize(accessToken: String) {
        client = PCloudSdk.newClientBuilder()
            .authenticator(Authenticators.newOAuthAuthenticator(accessToken))
            .create()
    }

    suspend fun uploadDatabase(): Boolean = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath("aqp_fact_database")
        if (!dbFile.exists()) return@withContext false

        return@withContext try {
            client?.createFile(
                RemoteFolder.ROOT_FOLDER_ID.toLong(),
                "aqp_fact_database_backup.db",
                DataSource.create(dbFile)
            )?.execute()
            Log.d("PCloudManager", "Backup caricato con successo")
            true
        } catch (e: Exception) {
            Log.e("PCloudManager", "Errore durante l'upload", e)
            false
        }
    }
}
