package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.formatBytes

@Entity(tableName = "compression_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val originalFilename: String,
    val originalSizeBytes: Long,
    val compressedSizeBytes: Long,
    val targetSizeBytes: Long,
    val format: String,
    val width: Int,
    val height: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val reductionPercentage: Float,
    val filePath: String,
    val isTargetAchieved: Boolean
) {
    val formattedOriginalSize: String
        get() = formatBytes(originalSizeBytes)

    val formattedCompressedSize: String
        get() = formatBytes(compressedSizeBytes)

    val formattedTargetSize: String
        get() = formatBytes(targetSizeBytes)
}
