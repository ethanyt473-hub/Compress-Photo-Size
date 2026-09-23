package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserSettings(
    val themeMode: String = "SYSTEM", // "SYSTEM", "LIGHT", "DARK"
    val defaultTargetKb: Int = 100,
    val defaultFormat: String = "JPEG", // "JPEG", "PNG", "WEBP"
    val removeExif: Boolean = true,
    val autoSave: Boolean = false,
    val preserveAspectRatio: Boolean = true
)

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    private fun loadSettings(): UserSettings {
        return UserSettings(
            themeMode = prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM",
            defaultTargetKb = prefs.getInt("default_target_kb", 100),
            defaultFormat = prefs.getString("default_format", "JPEG") ?: "JPEG",
            removeExif = prefs.getBoolean("remove_exif", true),
            autoSave = prefs.getBoolean("auto_save", false),
            preserveAspectRatio = prefs.getBoolean("preserve_aspect_ratio", true)
        )
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    fun setDefaultTargetKb(kb: Int) {
        prefs.edit().putInt("default_target_kb", kb).apply()
        _settings.value = _settings.value.copy(defaultTargetKb = kb)
    }

    fun setDefaultFormat(format: String) {
        prefs.edit().putString("default_format", format).apply()
        _settings.value = _settings.value.copy(defaultFormat = format)
    }

    fun setRemoveExif(remove: Boolean) {
        prefs.edit().putBoolean("remove_exif", remove).apply()
        _settings.value = _settings.value.copy(removeExif = remove)
    }

    fun setAutoSave(autoSave: Boolean) {
        prefs.edit().putBoolean("auto_save", autoSave).apply()
        _settings.value = _settings.value.copy(autoSave = autoSave)
    }

    fun setPreserveAspectRatio(preserve: Boolean) {
        prefs.edit().putBoolean("preserve_aspect_ratio", preserve).apply()
        _settings.value = _settings.value.copy(preserveAspectRatio = preserve)
    }
}
