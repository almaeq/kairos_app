package com.example.kairos.mobile.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BaselineStats::class, CrisisEpisode::class, TrustedContact::class],
    version = 1,
    exportSchema = false
)
abstract class KairosDatabase : RoomDatabase() {

    abstract fun kairosDao(): KairosDao

    companion object {
        @Volatile
        private var INSTANCE: KairosDatabase? = null

        fun getInstance(context: Context): KairosDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    KairosDatabase::class.java,
                    "kairos_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}