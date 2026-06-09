package com.example.aqpfact.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "readings")
data class Reading(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val meterId: Int, // 0: Main, 1: User 1, 2: User 2, 3: User 3
    val value: Double,
    val date: Long,
    val photoPath: String? = null,
    val groupId: String? = null // To group readings taken in the same session
)
