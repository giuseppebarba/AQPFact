package com.example.aqpfact.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.OnConflictStrategy
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reading: Reading)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(readings: List<Reading>)

    @Query("SELECT * FROM readings ORDER BY date DESC")
    fun getAllReadings(): Flow<List<Reading>>

    @Query("SELECT * FROM readings WHERE meterId = :meterId ORDER BY date DESC")
    fun getReadingsByMeter(meterId: Int): Flow<List<Reading>>

    @Query("DELETE FROM readings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM readings WHERE groupId = :groupId")
    suspend fun deleteByGroupId(groupId: String)
}
