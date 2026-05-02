package com.example.kairos.mobile.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "baseline_stats")
data class BaselineStats(
    @PrimaryKey val id: Int = 1,  // siempre un solo registro
    val hrCount: Int = 0,
    val hrMean: Double = 0.0,
    val hrM2: Double = 0.0,
    val hrvCount: Int = 0,
    val hrvMean: Double = 0.0,
    val hrvM2: Double = 0.0,
    val calibrationWindows: Int = 0,
    val updatedAt: Long = Instant.now().toEpochMilli()
)