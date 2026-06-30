package com.example.aqpfact.ui

import android.app.Application
import android.util.Log
import android.telephony.SmsManager
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aqpfact.data.AppDatabase
import com.example.aqpfact.data.GoogleDriveHelper
import com.example.aqpfact.data.Reading
import com.example.aqpfact.data.ReadingRepository
import com.example.aqpfact.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ReadingRepository
    private val settingsRepository = SettingsRepository(application)
    private val googleDriveHelper = GoogleDriveHelper(application)

    val allReadings: StateFlow<List<Reading>>

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus


    val lastBillTotal = settingsRepository.lastBillTotal.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "0.0"
    )

    val lastBillFixed = settingsRepository.lastBillFixed.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "0.0"
    )

    val nextReadingDate = settingsRepository.nextReadingDate.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )

    val meterNames: StateFlow<Map<Int, String>> = combine(
        settingsRepository.getMeterName(0),
        settingsRepository.getMeterName(1),
        settingsRepository.getMeterName(2),
        settingsRepository.getMeterName(3)
    ) { n0, n1, n2, n3 ->
        mapOf(0 to n0, 1 to n1, 2 to n2, 3 to n3)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun saveMeterName(id: Int, name: String) {
        viewModelScope.launch { settingsRepository.saveMeterName(id, name) }
    }


    fun saveBillSettings(total: String, fixed: String) {
        viewModelScope.launch { settingsRepository.saveBillSettings(total, fixed) }
    }

    fun saveNextReadingDate(date: Long?) {
        viewModelScope.launch { settingsRepository.saveNextReadingDate(date) }
    }

    init {
        val readingDao = AppDatabase.getDatabase(application).readingDao()
        repository = ReadingRepository(readingDao)
        allReadings = repository.allReadings.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        // Inizializza con un token se disponibile (da gestire idealmente con OAuth)
        // pCloudManager.initialize("YOUR_ACCESS_TOKEN")
    }

    fun addReading(meterId: Int, value: Double, photoPath: String? = null, groupId: String? = null) {
        viewModelScope.launch {
            repository.insert(Reading(meterId = meterId, value = value, date = System.currentTimeMillis(), photoPath = photoPath, groupId = groupId))
        }
    }

    fun addReadingSession(readings: List<Triple<Int, Double, String?>>) {
        viewModelScope.launch {
            val groupId = UUID.randomUUID().toString()
            val date = System.currentTimeMillis()
            readings.forEach { (meterId, value, photoPath) ->
                repository.insert(Reading(meterId = meterId, value = value, date = date, photoPath = photoPath, groupId = groupId))
            }
        }
    }

    fun deleteReading(id: Long) {
        viewModelScope.launch {
            repository.delete(id)
        }
    }

    fun deleteSession(groupId: String) {
        viewModelScope.launch {
            repository.deleteSession(groupId)
        }
    }

    fun uploadToDrive() {
        viewModelScope.launch {
            _syncStatus.value = "Esportazione su Google Sheets in corso..."
            try {
                repository.allReadings.first().let { readings ->
                    Log.d("MainViewModel", "Esportazione di ${readings.size} letture")
                    val success = googleDriveHelper.exportToSheets(readings)
                    _syncStatus.value = if (success) "Esportazione completata! (${readings.size} record)" else "Errore durante l'esportazione"
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Errore durante l'upload", e)
                _syncStatus.value = "Errore durante l'esportazione: ${e.message}"
            }
        }
    }

    fun downloadFromDrive() {
        viewModelScope.launch {
            _syncStatus.value = "Importazione da Google Sheets in corso..."
            val readings = googleDriveHelper.importFromSheets()
            if (readings != null) {
                repository.insertAll(readings)
                _syncStatus.value = "Importazione completata!"
            } else {
                _syncStatus.value = "Errore durante l'importazione"
            }
        }
    }

    fun clearSyncStatus() {
        _syncStatus.value = null
    }

    fun updateSyncStatus(message: String) {
        _syncStatus.value = message
    }

    fun sendSmsReading(value: Double) {
        try {
            val phoneNumber = "+393424110843"
            val message = "LETTURA 1002287812*00614651*30004398*${value.toInt()}"
            
            val smsManager = getApplication<Application>().getSystemService(SmsManager::class.java)
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            
            Toast.makeText(getApplication(), "SMS inviato correttamente", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(getApplication(), "Errore invio SMS: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
