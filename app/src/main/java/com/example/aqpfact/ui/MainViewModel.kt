package com.example.aqpfact.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aqpfact.data.AppDatabase
import com.example.aqpfact.data.Reading
import com.example.aqpfact.data.ReadingRepository
import com.example.aqpfact.data.SettingsRepository
import com.example.aqpfact.utils.PCloudManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ReadingRepository
    private val settingsRepository = SettingsRepository(application)
    val allReadings: StateFlow<List<Reading>>
    private val pCloudManager = PCloudManager(application)

    val pCloudToken = settingsRepository.pCloudToken.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )

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

    fun savePCloudToken(token: String) {
        viewModelScope.launch { settingsRepository.savePCloudToken(token) }
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

    fun syncToPCloud(accessToken: String) {
        viewModelScope.launch {
            pCloudManager.initialize(accessToken)
            pCloudManager.uploadDatabase()
        }
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
}
