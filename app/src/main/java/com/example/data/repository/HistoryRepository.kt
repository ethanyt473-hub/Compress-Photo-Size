package com.example.data.repository

import com.example.data.db.HistoryDao
import com.example.data.db.HistoryEntity
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val historyDao: HistoryDao) {
    val allHistory: Flow<List<HistoryEntity>> = historyDao.getAllHistory()

    suspend fun insertHistory(item: HistoryEntity): Long = historyDao.insert(item)

    suspend fun deleteHistory(id: Long) = historyDao.deleteById(id)

    suspend fun clearAllHistory() = historyDao.clearAll()
}
