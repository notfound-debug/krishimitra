package com.krishi.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "krishi_prefs")

class PrefsStore(private val context: Context) {

    companion object {
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_ONBOARDED = booleanPreferencesKey("onboarded")
        val KEY_TUTORIAL_DONE = booleanPreferencesKey("tutorial_done")
        val KEY_DISTRICT = stringPreferencesKey("district")
        val KEY_TALUK = stringPreferencesKey("taluk")
        val KEY_CROPS = stringPreferencesKey("crops") // comma-separated
        val KEY_LAND_ACRES = stringPreferencesKey("land_acres")
    }

    // Onboarding — false until farmer completes setup
    val isOnboarded: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDED] ?: false
    }

    val isTutorialDone: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_TUTORIAL_DONE] ?: false
    }

    suspend fun completeTutorial() {
        context.dataStore.edit { it[KEY_TUTORIAL_DONE] = true }
    }

    // Language
    val language: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LANGUAGE] ?: "kn"
    }

    // Profile fields
    val district: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DISTRICT] ?: ""
    }

    val taluk: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_TALUK] ?: ""
    }

    val crops: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_CROPS]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }

    val landAcres: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAND_ACRES] ?: ""
    }

    // Device ID
    val deviceId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEVICE_ID] ?: ""
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { it[KEY_LANGUAGE] = lang }
    }

    suspend fun ensureDeviceId(): String {
        var id = ""
        context.dataStore.edit { prefs ->
            id = prefs[KEY_DEVICE_ID] ?: UUID.randomUUID().toString().also {
                prefs[KEY_DEVICE_ID] = it
            }
        }
        return id
    }

    suspend fun completeOnboarding(
        district: String,
        taluk: String,
        crops: List<String>,
        landAcres: Double,
        language: String,
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ONBOARDED] = true
            prefs[KEY_DISTRICT] = district
            prefs[KEY_TALUK] = taluk
            prefs[KEY_CROPS] = crops.joinToString(",")
            prefs[KEY_LAND_ACRES] = landAcres.toString()
            prefs[KEY_LANGUAGE] = language
        }
    }

    suspend fun updateProfile(
        district: String,
        taluk: String,
        crops: List<String>,
        landAcres: Double,
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DISTRICT] = district
            prefs[KEY_TALUK] = taluk
            prefs[KEY_CROPS] = crops.joinToString(",")
            prefs[KEY_LAND_ACRES] = landAcres.toString()
        }
    }
}
