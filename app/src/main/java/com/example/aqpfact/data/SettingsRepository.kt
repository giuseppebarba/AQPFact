package com.example.aqpfact.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val METER_NAME_0 = stringPreferencesKey("meter_name_0")
        val METER_NAME_1 = stringPreferencesKey("meter_name_1")
        val METER_NAME_2 = stringPreferencesKey("meter_name_2")
        val METER_NAME_3 = stringPreferencesKey("meter_name_3")
        val LAST_BILL_TOTAL = stringPreferencesKey("last_bill_total")
        val LAST_BILL_FIXED = stringPreferencesKey("last_bill_fixed")
        val NEXT_READING_DATE = longPreferencesKey("next_reading_date")
    }

    val lastBillTotal: Flow<String> = context.dataStore.data.map { it[LAST_BILL_TOTAL] ?: "0.0" }
    val lastBillFixed: Flow<String> = context.dataStore.data.map { it[LAST_BILL_FIXED] ?: "0.0" }
    val nextReadingDate: Flow<Long?> = context.dataStore.data.map { it[NEXT_READING_DATE] }

    fun getMeterName(id: Int): Flow<String> = context.dataStore.data.map { preferences ->
        when (id) {
            0 -> preferences[METER_NAME_0] ?: "Generale"
            1 -> preferences[METER_NAME_1] ?: "Utenza 1"
            2 -> preferences[METER_NAME_2] ?: "Utenza 2"
            3 -> preferences[METER_NAME_3] ?: "Utenza 3"
            else -> "Utenza $id"
        }
    }

    suspend fun saveMeterName(id: Int, name: String) {
        context.dataStore.edit { preferences ->
            val key = when (id) {
                0 -> METER_NAME_0
                1 -> METER_NAME_1
                2 -> METER_NAME_2
                3 -> METER_NAME_3
                else -> return@edit
            }
            preferences[key] = name
        }
    }

    suspend fun saveBillSettings(total: String, fixed: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_BILL_TOTAL] = total
            preferences[LAST_BILL_FIXED] = fixed
        }
    }

    suspend fun saveNextReadingDate(date: Long?) {
        context.dataStore.edit { preferences ->
            if (date != null) {
                preferences[NEXT_READING_DATE] = date
            } else {
                preferences.remove(NEXT_READING_DATE)
            }
        }
    }
}
