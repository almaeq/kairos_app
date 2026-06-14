package com.example.kairos.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.feature.ExperimentalMindfulnessSessionApi
import androidx.health.connect.client.records.MindfulnessSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import java.time.Instant
import java.time.ZoneOffset

/**
 * Gestiona el registro de sesiones de mindfulness en Health Connect
 * como [MindfulnessSessionRecord].
 *
 * Es invocado por [InterventionSession] al finalizar cada ejercicio de intervención,
 * permitiendo que los datos de las sesiones de respiración y grounding de KAIROS
 * aparezcan en la app de Health Connect del usuario.
 *
 * **Limitación documentada — Pixel Watch 3:**
 * `MindfulnessSessionRecord` requiere `FEATURE_MINDFULNESS_SESSION` disponible en el
 * dispositivo. En el Pixel Watch 3 esta feature no está disponible por restricción de
 * versión del SDK de Health Connect — el intento de inserción es bloqueado en la
 * verificación de disponibilidad y se registra un warning en Logcat.
 * Esto está documentado como limitación de hardware del MVP, no como un bug.
 */
object MindfulnessSessionManager {

    private const val TAG = "MindfulnessSession"

    /**
     * Registra una sesión de intervención en Health Connect.
     *
     * Verifica la disponibilidad de [HealthConnectFeatures.FEATURE_MINDFULNESS_SESSION]
     * antes de intentar la inserción — si el dispositivo no soporta la feature,
     * registra un warning y retorna sin lanzar excepción para no interrumpir el flujo
     * de la app.
     *
     * Usa el offset de zona horaria del sistema en el momento exacto de inicio y fin,
     * para que las sesiones aparezcan con la hora local correcta en Health Connect
     * independientemente de cambios de zona horaria entre inicio y fin del ejercicio.
     *
     * @param context Contexto de la aplicación para obtener el cliente de Health Connect.
     * @param startTime Timestamp de inicio de la sesión en milisegundos (epoch).
     * @param endTime Timestamp de fin de la sesión en milisegundos (epoch).
     * @param sessionType Tipo de sesión de mindfulness. Usar las constantes de
     *        [MindfulnessSessionRecord] (por ejemplo,
     *        `MINDFULNESS_SESSION_TYPE_BREATHING` o `MINDFULNESS_SESSION_TYPE_MEDITATION`).
     * @param title Título descriptivo que aparece en la app de Health Connect.
     * @param notes Notas opcionales con contexto clínico (por ejemplo: "HR al inicio: 134 BPM").
     *        Se omite del registro si está en blanco.
     */
    @OptIn(ExperimentalMindfulnessSessionApi::class)
    suspend fun record(
        context:     Context,
        startTime:   Long,
        endTime:     Long,
        sessionType: Int,
        title:       String,
        notes:       String = ""
    ) {
        try {
            val client = HealthConnectClient.getOrCreate(context)

            // Verificamos disponibilidad antes de insertar — en Pixel Watch 3 esta
            // feature no está disponible y la inserción fallaría silenciosamente
            val featureStatus = client.features.getFeatureStatus(
                HealthConnectFeatures.FEATURE_MINDFULNESS_SESSION
            )
            if (featureStatus != HealthConnectFeatures.FEATURE_STATUS_AVAILABLE) {
                Log.w(TAG, "MindfulnessSession no disponible en este dispositivo — " +
                        "limitación documentada del Pixel Watch 3")
                return
            }

            // Calculamos el offset de zona horaria en el momento exacto de cada timestamp
            // para manejar correctamente sesiones que cruzaron un cambio de zona horaria
            val startInstant = Instant.ofEpochMilli(startTime)
            val endInstant   = Instant.ofEpochMilli(endTime)
            val zoneRules    = ZoneOffset.systemDefault().rules

            val record = MindfulnessSessionRecord(
                startTime              = startInstant,
                startZoneOffset        = zoneRules.getOffset(startInstant),
                endTime                = endInstant,
                endZoneOffset          = zoneRules.getOffset(endInstant),
                title                  = title,
                notes                  = notes.ifBlank { null },
                mindfulnessSessionType = sessionType,
                metadata               = Metadata.manualEntry()
            )

            client.insertRecords(listOf(record))
            val durationMin = (endTime - startTime) / 60_000
            Log.d(TAG, "Sesión registrada: '$title' — ${durationMin}min")

        } catch (e: Exception) {
            Log.e(TAG, "Error registrando sesión en Health Connect: ${e.message}")
        }
    }
}