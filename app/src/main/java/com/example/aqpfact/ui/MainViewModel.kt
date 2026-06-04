package com.example.aqpfact.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aqpfact.data.AppDatabase
import com.example.aqpfact.data.Reading
import com.example.aqpfact.data.ReadingRepository
import com.example.aqpfact.utils.PCloudManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ReadingRepository
    val allReadings: StateFlow<List<Reading>>
    private val pCloudManager = PCloudManager(application)

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

    fun addReading(meterId: Int, value: Double, photoPath: String? = null) {
        viewModelScope.launch {
            repository.insert(Reading(meterId = meterId, value = value, date = System.currentTimeMillis(), photoPath = photoPath))
        }
    }

    fun deleteReading(id: Long) {
        viewModelScope.launch {
            repository.delete(id)
        }
    }
}
