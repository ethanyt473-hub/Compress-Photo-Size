package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.db.HistoryEntity
import com.example.data.file.FileManager
import com.example.data.repository.HistoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val historyRepository = HistoryRepository(AppDatabase.getInstance(application).historyDao())
    private val fileManager = FileManager(application)

    val historyItems: StateFlow<List<HistoryEntity>> = historyRepository.allHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteItem(id: Long) {
        viewModelScope.launch {
            historyRepository.deleteHistory(id)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            historyRepository.clearAllHistory()
        }
    }

    fun shareItem(item: HistoryEntity) {
        if (File(item.filePath).exists()) {
            fileManager.shareImage(item.filePath)
        }
    }

    fun openItem(item: HistoryEntity) {
        if (File(item.filePath).exists()) {
            fileManager.openImage(item.filePath)
        }
    }

    fun isFileAvailable(filePath: String): Boolean {
        return File(filePath).exists()
    }
}
