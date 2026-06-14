package com.example.kairos.mobile.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de datos Room principal de KAIROS.
 *
 * Implementa el patrón Singleton para garantizar que exista una única instancia
 * de la base de datos durante todo el ciclo de vida de la aplicación.
 * Múltiples instancias simultáneas de Room sobre el mismo archivo pueden causar
 * condiciones de carrera y corrupción de datos.
 *
 * Contiene tres entidades:
 * - [BaselineStats]: parámetros de calibración personal del usuario (algoritmo de Welford).
 * - [CrisisEpisode]: historial de episodios de crisis detectados.
 * - [TrustedContact]: contactos de confianza para el envío de alertas SMS.
 *
 * Todos los datos se almacenan localmente en el dispositivo.
 * No existe sincronización con ningún servidor externo (diseño offline-first).
 */
@Database(
    entities = [BaselineStats::class, CrisisEpisode::class, TrustedContact::class],
    version = 1,
    exportSchema = false
)
abstract class KairosDatabase : RoomDatabase() {

    /**
     * Punto de acceso a todas las operaciones de la base de datos.
     *
     * @return Instancia del DAO principal de KAIROS.
     */
    abstract fun kairosDao(): KairosDao

    companion object {

        /**
         * Instancia única de la base de datos.
         *
         * [@Volatile] garantiza que los cambios sobre esta variable sean visibles
         * inmediatamente para todos los hilos, evitando lecturas de caché de CPU
         * que podrían devolver una instancia obsoleta o nula.
         */
        @Volatile
        private var INSTANCE: KairosDatabase? = null

        /**
         * Retorna la instancia única de la base de datos, creándola si no existe.
         *
         * Usa double-checked locking: primero verifica sin bloqueo (rápido),
         * y solo entra al bloque [synchronized] si la instancia es nula,
         * evitando el costo de sincronización en el caso común donde ya existe.
         *
         * @param context Contexto de la aplicación. Se usa [Context.applicationContext]
         *                para evitar memory leaks por retención de Activities o Fragments.
         * @return Instancia única de [KairosDatabase].
         */
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