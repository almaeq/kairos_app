package com.example.kairos.mobile.data

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.kairos.mobile.detection.WesadThresholds
import java.time.Instant

/**
 * Gestiona el acceso a Health Connect para lectura de señales biomédicas del usuario.
 *
 * Cubre US#3895 — la app detecta variaciones en métricas biomédicas
 * leyendo [HeartRateRecord] y [StepsRecord] desde el repositorio compartido de Health Connect.
 *
 * **Nota de arquitectura:**
 * Health Connect actúa como repositorio compartido entre la app del teléfono y la app Wear OS.
 * El smartwatch escribe los datos mediante su pipeline de sensores; esta clase los lee
 * desde el teléfono para análisis y visualización histórica.
 *
 * **Limitación importante:**
 * Health Connect tiene una latencia de sincronización de ~4 minutos entre la escritura
 * del reloj y la disponibilidad en el teléfono. Por este motivo, la detección de crisis
 * en tiempo real NO usa esta clase — se ejecuta directamente en el reloj mediante
 * `PassiveMonitoringClient`. Esta clase se usa únicamente para lectura histórica
 * y generación de reportes.
 *
 * @property context Contexto de la aplicación para inicializar el cliente de Health Connect.
 */
class HealthConnectManager(private val context: Context) {

    /**
     * Cliente de Health Connect inicializado de forma lazy.
     *
     * Se crea solo cuando se necesita por primera vez, evitando el costo de inicialización
     * si Health Connect no está disponible en el dispositivo.
     */
    private val client: HealthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    companion object {

        /**
         * Conjunto de permisos requeridos para operar con Health Connect.
         *
         * KAIROS necesita:
         * - Lectura y escritura de [HeartRateRecord]: para acceder al historial de HR del reloj.
         * - Lectura de [StepsRecord]: para usar los pasos como proxy de actividad física
         *   cuando el acelerómetro del reloj no está disponible.
         */
        val REQUIRED_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getWritePermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class)
        )

        /**
         * Verifica si Health Connect está instalado y disponible en el dispositivo.
         *
         * Health Connect no viene preinstalado en todos los dispositivos Android
         * y puede estar deshabilitado por el usuario. Debe verificarse antes de
         * intentar cualquier operación con el cliente.
         *
         * @param context Contexto necesario para consultar el estado del SDK.
         * @return `true` si Health Connect está disponible y listo para usar.
         */
        fun isAvailable(context: Context): Boolean =
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    /**
     * Verifica si la app tiene todos los permisos requeridos de Health Connect.
     *
     * Los permisos pueden ser revocados por el usuario en cualquier momento desde
     * la configuración del sistema, por lo que deben verificarse antes de cada operación.
     *
     * @return `true` si todos los permisos en [REQUIRED_PERMISSIONS] están otorgados.
     */
    suspend fun hasAllPermissions(): Boolean =
        client.permissionController
            .getGrantedPermissions()
            .containsAll(REQUIRED_PERMISSIONS)

    /**
     * Lee muestras de frecuencia cardíaca dentro de una ventana de tiempo.
     *
     * Retorna una lista plana de [HeartRateRecord.Sample] que puede usarse para
     * calcular HR media y RMSSD sobre la ventana especificada, siguiendo el mismo
     * esquema de features que el modelo entrenado en WESAD.
     *
     * En caso de error (permisos revocados, Health Connect no disponible, etc.)
     * retorna una lista vacía sin lanzar excepción, para no interrumpir el flujo
     * de la UI.
     *
     * @param windowSeconds Tamaño de la ventana de lectura en segundos hacia atrás
     *                      desde el momento actual. Por defecto 3600s (1 hora).
     * @return Lista de muestras de HR disponibles en la ventana. Puede estar vacía
     *         si no hay datos o si ocurrió un error.
     */
    suspend fun readHeartRateSamples(
        windowSeconds: Long = 3600L
    ): List<HeartRateRecord.Sample> {
        val end   = Instant.now()
        val start = end.minusSeconds(windowSeconds)

        Log.d("KAIROS_HC", "Ahora UTC: $end")
        Log.d("KAIROS_HC", "Buscando desde: $start")

        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType      = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            Log.d("KAIROS_HC", "Records encontrados: ${response.records.size}")
            // flatMap porque cada HeartRateRecord puede contener múltiples samples
            // (un record puede abarcar varios segundos con una muestra por segundo)
            response.records.flatMap { it.samples }
        } catch (e: Exception) {
            Log.e("KAIROS_HC", "Error leyendo HR de Health Connect: ${e.message}")
            emptyList()
        }
    }

    /**
     * Lee el total de pasos registrados dentro de una ventana de tiempo.
     *
     * Se usa como proxy de actividad física en la Capa 3 del detector cuando
     * el acelerómetro del reloj no está disponible directamente.
     * Un conteo de pasos elevado indica movimiento físico activo, lo que permite
     * distinguir taquicardia por ejercicio de taquicardia por estrés o ansiedad.
     *
     * En caso de error retorna 0 sin lanzar excepción.
     *
     * @param windowSeconds Tamaño de la ventana de lectura en segundos hacia atrás
     *                      desde el momento actual. Por defecto 3600s (1 hora).
     * @return Total de pasos registrados en la ventana, o 0 si no hay datos o hubo error.
     */
    suspend fun readStepsInWindow(
        windowSeconds: Long = 3600L
    ): Long {
        val end   = Instant.now()
        val start = end.minusSeconds(windowSeconds)
        return try {
            client.readRecords(
                ReadRecordsRequest(
                    recordType      = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            ).records.sumOf { it.count }
        } catch (e: Exception) {
            Log.e("KAIROS_HC", "Error leyendo pasos de Health Connect: ${e.message}")
            0L
        }
    }
}