package com.example.aqpfact.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
        val LAST_BILL_AQUEDUCT = stringPreferencesKey("last_bill_aqueduct")
        val LAST_BILL_SEWERAGE = stringPreferencesKey("last_bill_sewerage")
        val LAST_BILL_PURIFICATION = stringPreferencesKey("last_bill_purification")
        val LAST_BILL_CHARGES = stringPreferencesKey("last_bill_charges")
        val LAST_BILL_BONUS = stringPreferencesKey("last_bill_bonus")
        val LAST_BILL_ADVANCES = stringPreferencesKey("last_bill_advances")
        val LAST_BILL_OTHER = stringPreferencesKey("last_bill_other")
        val LAST_BILL_TAXABLE = stringPreferencesKey("last_bill_taxable")
        val NEXT_READING_DATE = longPreferencesKey("next_reading_date")
    }

    val lastBillTotal: Flow<String> = context.dataStore.data.map { it[LAST_BILL_TOTAL] ?: "0.0" }
    val lastBillFixed: Flow<String> = context.dataStore.data.map { it[LAST_BILL_FIXED] ?: "0.0" }
    val nextReadingDate: Flow<Long?> = context.dataStore.data.map { it[NEXT_READING_DATE] }

    val billDetails: Flow<BillDetails> = context.dataStore.data.map { preferences ->
        BillDetails(
            total = preferences[LAST_BILL_TOTAL] ?: "0.0",
            fixed = preferences[LAST_BILL_FIXED] ?: "0.0",
            aqueduct = preferences[LAST_BILL_AQUEDUCT] ?: "0.0",
            sewerage = preferences[LAST_BILL_SEWERAGE] ?: "0.0",
            purification = preferences[LAST_BILL_PURIFICATION] ?: "0.0",
            charges = preferences[LAST_BILL_CHARGES] ?: "0.0",
            bonus = preferences[LAST_BILL_BONUS] ?: "0.0",
            advances = preferences[LAST_BILL_ADVANCES] ?: "0.0",
            other = preferences[LAST_BILL_OTHER] ?: "0.0",
            taxable = preferences[LAST_BILL_TAXABLE] ?: "0.0"
        )
    }

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

    suspend fun saveBillSettings(
        total: String,
        fixed: String,
        aqueduct: String = "0.0",
        sewerage: String = "0.0",
        purification: String = "0.0",
        charges: String = "0.0",
        bonus: String = "0.0",
        advances: String = "0.0",
        other: String = "0.0",
        taxable: String = "0.0"
    ) {
        context.dataStore.edit { preferences ->
            preferences[LAST_BILL_TOTAL] = total
            preferences[LAST_BILL_FIXED] = fixed
            preferences[LAST_BILL_AQUEDUCT] = aqueduct
            preferences[LAST_BILL_SEWERAGE] = sewerage
            preferences[LAST_BILL_PURIFICATION] = purification
            preferences[LAST_BILL_CHARGES] = charges
            preferences[LAST_BILL_BONUS] = bonus
            preferences[LAST_BILL_ADVANCES] = advances
            preferences[LAST_BILL_OTHER] = other
            preferences[LAST_BILL_TAXABLE] = taxable
        }
    }

    suspend fun resetBillSettings() {
        context.dataStore.edit { preferences ->
            preferences[LAST_BILL_TOTAL] = "0.0"
            preferences[LAST_BILL_FIXED] = "0.0"
            preferences[LAST_BILL_AQUEDUCT] = "0.0"
            preferences[LAST_BILL_SEWERAGE] = "0.0"
            preferences[LAST_BILL_PURIFICATION] = "0.0"
            preferences[LAST_BILL_CHARGES] = "0.0"
            preferences[LAST_BILL_BONUS] = "0.0"
            preferences[LAST_BILL_ADVANCES] = "0.0"
            preferences[LAST_BILL_OTHER] = "0.0"
            preferences[LAST_BILL_TAXABLE] = "0.0"
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
