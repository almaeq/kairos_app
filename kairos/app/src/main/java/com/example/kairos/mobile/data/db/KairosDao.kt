package com.example.kairos.mobile.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface KairosDao {

    // ── Baseline ──────────────────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBaseline(stats: BaselineStats)

    @Query("SELECT * FROM baseline_stats WHERE id = 1")
    suspend fun getBaseline(): BaselineStats?

    // ── Episodios ─────────────────────────────────────────────────────────────
    @Insert
    suspend fun insertEpisode(episode: CrisisEpisode)

    @Query("SELECT * FROM crisis_episodes ORDER BY timestamp DESC")
    fun getAllEpisodes(): Flow<List<CrisisEpisode>>

    @Query("SELECT * FROM crisis_episodes ORDER BY timestamp DESC LIMIT 10")
    suspend fun getRecentEpisodes(): List<CrisisEpisode>

    // ── Contactos ─────────────────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveContact(contact: TrustedContact)

    @Query("SELECT * FROM trusted_contacts WHERE isActive = 1")
    suspend fun getActiveContacts(): List<TrustedContact>

    @Delete
    suspend fun deleteContact(contact: TrustedContact)

    @Query("DELETE FROM baseline_stats")
    suspend fun deleteBaseline()
}