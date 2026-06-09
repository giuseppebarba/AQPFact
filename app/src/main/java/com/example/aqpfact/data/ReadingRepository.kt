package com.example.aqpfact.data

import kotlinx.coroutines.flow.Flow

class ReadingRepository(private val readingDao: ReadingDao) {
    val allReadings: Flow<List<Reading>> = readingDao.getAllReadings()

    fun getReadingsByMeter(meterId: Int): Flow<List<Reading>> = readingDao.getReadingsByMeter(meterId)

    suspend fun insert(reading: Reading) {
        readingDao.insert(reading)
    }

    suspend fun delete(id: Long) {
        readingDao.deleteById(id)
    }

    suspend fun deleteSession(groupId: String) {
        readingDao.deleteByGroupId(groupId)
    }
}
