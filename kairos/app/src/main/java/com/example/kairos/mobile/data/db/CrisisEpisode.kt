package com.example.kairos.mobile.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "crisis_episodes")
data class CrisisEpisode(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = Instant.now().toEpochMilli(),
    val hrBpm: Double,
    val rmssdMs: Double,
    val durationSeconds: Int = 0,
    val wasConfirmed: Boolean = false,  // true = usuario confirmó, false = falso positivo
    val notes: String = ""
)