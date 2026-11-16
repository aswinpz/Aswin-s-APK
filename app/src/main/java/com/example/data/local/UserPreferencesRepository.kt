package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "shoebin_prefs")

class UserPreferencesRepository(private val context: Context) {
  private val themeKey = stringPreferencesKey("theme_mode")
  private val sheetUrlKey = stringPreferencesKey("sheet_sync_url")

  val themeMode: Flow<ThemeMode> =
    context.dataStore.data.map { prefs ->
      when (prefs[themeKey]) {
        ThemeMode.LIGHT.name -> ThemeMode.LIGHT
        ThemeMode.DARK.name -> ThemeMode.DARK
        else -> ThemeMode.SYSTEM
      }
    }

  val sheetSyncUrl: Flow<String> =
    context.dataStore.data.map { prefs -> prefs[sheetUrlKey] ?: "" }

  suspend fun setThemeMode(mode: ThemeMode) {
    context.dataStore.edit { prefs -> prefs[themeKey] = mode.name }
  }

  suspend fun setSheetSyncUrl(url: String) {
    context.dataStore.edit { prefs -> prefs[sheetUrlKey] = url.trim() }
  }
}
