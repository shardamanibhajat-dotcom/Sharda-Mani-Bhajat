package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.localization.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "creator_paisa_prefs")

class SessionPreferences(private val context: Context) {
    private val keyCurrentUserId = stringPreferencesKey("current_user_id")
    private val keyLanguage = stringPreferencesKey("app_language")
    private val keyDarkTheme = booleanPreferencesKey("dark_theme")

    val currentUserId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[keyCurrentUserId]
    }

    val appLanguage: Flow<AppLanguage> = context.dataStore.data.map { prefs ->
        when (prefs[keyLanguage]) {
            AppLanguage.HINDI.code -> AppLanguage.HINDI
            else -> AppLanguage.ENGLISH
        }
    }

    val isDarkTheme: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[keyDarkTheme] ?: true
    }

    suspend fun setCurrentUserId(userId: String?) {
        context.dataStore.edit { prefs ->
            if (userId == null) {
                prefs.remove(keyCurrentUserId)
            } else {
                prefs[keyCurrentUserId] = userId
            }
        }
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        context.dataStore.edit { prefs ->
            prefs[keyLanguage] = language.code
        }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[keyDarkTheme] = enabled
        }
    }
}
