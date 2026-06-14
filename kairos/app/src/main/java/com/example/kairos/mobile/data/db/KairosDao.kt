package com.example.kairos.mobile.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO (Data Access Object) principal de KAIROS.
 *
 * Define todas las operaciones de lectura y escritura sobre la base de datos Room.
 * Agrupa las queries en tres dominios: baseline de calibración, episodios de crisis
 * y contactos de confianza.
 *
 * Las funciones suspendidas se ejecutan en un hilo de IO gestionado por Room.
 * Las funciones que retornan [Flow] emiten automáticamente cuando los datos cambian,
 * permitiendo que la UI reaccione sin polling explícito.
 */
@Dao
interface KairosDao {

    // ── Baseline ──────────────────────────────────────────────────────────────

    /**
     * Guarda o actualiza los parámetros de la línea de base personal del usuario.
     *
     * Usa [OnConflictStrategy.REPLACE] porque la tabla siempre tiene un único registro
     * (id = 1). Cada llamada sobreescribe el estado anterior del algoritmo de Welford.
     *
     * @param stats Parámetros actualizados de la línea de base.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBaseline(stats: BaselineStats)

    /**
     * Recupera los parámetros actuales de la línea de base personal.
     *
     * @return [BaselineStats] si ya existe una calibración, `null` si el usuario
     *         todavía no completó ninguna ventana de baseline.
     */
    @Query("SELECT * FROM baseline_stats WHERE id = 1")
    suspend fun getBaseline(): BaselineStats?

    /**
     * Elimina por completo la línea de base almacenada.
     *
     * Se usa cuando el usuario solicita recalibrar desde cero en la configuración.
     */
    @Query("DELETE FROM baseline_stats")
    suspend fun deleteBaseline()

    // ── Episodios ─────────────────────────────────────────────────────────────

    /**
     * Inserta un nuevo episodio de crisis en la base de datos.
     *
     * @param episode Episodio detectado, con sus métricas fisiológicas y estado de confirmación.
     */
    @Insert
    suspend fun insertEpisode(episode: CrisisEpisode)

    /**
     * Retorna todos los episodios ordenados por fecha descendente como [Flow].
     *
     * La UI observa este Flow para actualizar la bitácora en tiempo real
     * cada vez que se registra un nuevo episodio.
     *
     * @return Flow que emite la lista completa de episodios ante cualquier cambio.
     */
    @Query("SELECT * FROM crisis_episodes ORDER BY timestamp DESC")
    fun getAllEpisodes(): Flow<List<CrisisEpisode>>

    /**
     * Retorna los últimos 50 episodios para generación de reportes.
     *
     * @return Lista de hasta 50 episodios ordenados por fecha descendente.
     */
    @Query("SELECT * FROM crisis_episodes ORDER BY timestamp DESC LIMIT 50")
    suspend fun getRecentEpisodes(): List<CrisisEpisode>

    /**
     * Retorna todos los episodios confirmados por el usuario, ordenados por fecha descendente.
     *
     * Se usa en la pantalla de historial, donde solo se muestran crisis reales
     * (episodios donde el usuario no canceló la pre-alerta).
     *
     * @return Lista de episodios con [CrisisEpisode.wasConfirmed] = `true`.
     */
    @Query("SELECT * FROM crisis_episodes WHERE wasConfirmed = 1 ORDER BY timestamp DESC")
    suspend fun getAllConfirmedEpisodes(): List<CrisisEpisode>

    /**
     * Retorna los episodios registrados a partir de una fecha determinada.
     *
     * Se usa para generar reportes acotados a un período específico,
     * por ejemplo la última semana o el último mes.
     *
     * @param since Timestamp en milisegundos (epoch) desde el cual filtrar.
     * @return Lista de episodios posteriores a [since], ordenados por fecha descendente.
     */
    @Query("SELECT * FROM crisis_episodes WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getEpisodesSince(since: Long): List<CrisisEpisode>

    /**
     * Limita el historial de episodios cancelados a los 50 más recientes.
     *
     * Los episodios confirmados ([CrisisEpisode.wasConfirmed] = `true`) nunca se eliminan
     * automáticamente — solo se purgan los falsos positivos descartados por el usuario.
     * Se invoca después de cada inserción para mantener acotado el tamaño de la base de datos.
     */
    @Query("""
        DELETE FROM crisis_episodes 
        WHERE wasConfirmed = 0 
        AND id NOT IN (
            SELECT id FROM crisis_episodes 
            WHERE wasConfirmed = 0 
            ORDER BY timestamp DESC 
            LIMIT 50
        )
    """)
    suspend fun keepLatestCancelledEpisodes()

    // ── Contactos ─────────────────────────────────────────────────────────────

    /**
     * Guarda o actualiza un contacto de confianza.
     *
     * Usa [OnConflictStrategy.REPLACE] para permitir edición del contacto existente
     * sin necesidad de eliminarlo previamente.
     *
     * @param contact Contacto a guardar o actualizar.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveContact(contact: TrustedContact)

    /**
     * Retorna los contactos de confianza activos.
     *
     * Solo se incluyen los contactos marcados como activos ([TrustedContact.isActive] = `true`).
     * Los contactos desactivados se conservan en la base de datos pero no reciben alertas SMS.
     *
     * @return Lista de contactos activos disponibles para el envío de alertas.
     */
    @Query("SELECT * FROM trusted_contacts WHERE isActive = 1")
    suspend fun getActiveContacts(): List<TrustedContact>

    /**
     * Elimina un contacto de confianza de la base de datos.
     *
     * @param contact Contacto a eliminar.
     */
    @Delete
    suspend fun deleteContact(contact: TrustedContact)
}