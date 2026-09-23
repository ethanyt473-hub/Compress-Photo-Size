package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.repository.HistoryRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.UserSettings
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)
    private val historyRepository = HistoryRepository(AppDatabase.getInstance(application).historyDao())

    val settings: StateFlow<UserSettings> = settingsRepository.settings

    fun setThemeMode(mode: String) {
        settingsRepository.setThemeMode(mode)
    }

    fun setDefaultTargetKb(kb: Int) {
        settingsRepository.setDefaultTargetKb(kb)
    }

    fun setDefaultFormat(format: String) {
        settingsRepository.setDefaultFormat(format)
    }

    fun setRemoveExif(remove: Boolean) {
        settingsRepository.setRemoveExif(remove)
    }

    fun setAutoSave(autoSave: Boolean) {
        settingsRepository.setAutoSave(autoSave)
    }

    fun setPreserveAspectRatio(preserve: Boolean) {
        settingsRepository.setPreserveAspectRatio(preserve)
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            historyRepository.clearAllHistory()
        }
    }
}
