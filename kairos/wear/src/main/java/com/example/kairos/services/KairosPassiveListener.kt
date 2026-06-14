package com.example.kairos.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import com.example.kairos.detection.WatchCrisisDetector
import com.example.kairos.ui.WatchCrisisState
import com.example.kairos.ui.WatchMonitorState
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Servicio pasivo que recibe datos de HR del sensor del reloj y ejecuta el pipeline
 * de detección de crisis en tiempo real.
 *
 * Usa `PassiveListenerService` de Health Services API en lugar de `ExerciseClient`
 * porque `ExerciseClient` pausa la entrega de datos cuando la pantalla se apaga,
 * mientras que `PassiveListenerService` con `foregroundServiceType="health"` mantiene
 * el acceso continuo al sensor independientemente del estado de la pantalla.
 *
 * **Persistencia del estado entre reinicios:**
 * El SO puede destruir y recrear este servicio en cualquier momento. Para mantener
 * continuidad del pipeline:
 * - [windowStartMs] se persiste en SharedPreferences via un property delegate con getter/setter.
 * - [WatchCrisisDetector] es singleton y restaura el baseline desde [WatchBaseline] en su `init`.
 *
 * **Pipeline de procesamiento por cada lote de HR recibido:**
 * 1. Acumular muestras en [hrBuffer] (máximo [MAX_BUFFER_SIZE]).
 * 2. Esperar mínimo 6 muestras antes de analizar.
 * 3. Si no está calibrado: esperar que pase [CALIBRATION_WINDOW_MS] antes de analizar.
 * 4. Llamar a [WatchCrisisDetector.analyze] con el buffer actual.
 * 5. Actualizar [WatchMonitorState] y enviar el mensaje correspondiente al teléfono.
 *
 * **Paths enviados al teléfono via Wearable Message API:**
 * - `/kairos/heartbeat` — estado normal, cada 60 segundos
 * - `/kairos/prealerta` — umbral de pre-alerta superado
 * - `/kairos/crisis` — crisis confirmada por N ventanas consecutivas
 */
class KairosPassiveListener : PassiveListenerService() {

    /**
     * Scope del servicio para el heartbeat periódico.
     * Se cancela en [onDestroy] para detener el loop cuando el servicio termina.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Buffer circular de muestras de HR de la ventana actual, en BPM. */
    private val hrBuffer = mutableListOf<Double>()

    private lateinit var detector: WatchCrisisDetector

    /** Duración de cada ventana de análisis: 60 segundos. */
    private val CALIBRATION_WINDOW_MS = 60_000L

    /**
     * Tamaño máximo del buffer de HR.
     * A 64 Hz (frecuencia del sensor E4), 60s = 3840 muestras.
     * Usamos 80 como límite práctico para el reloj — suficiente para RMSSD confiable.
     */
    private val MAX_BUFFER_SIZE = 80

    /** Intervalo entre heartbeats enviados al teléfono: 60 segundos. */
    private val HEARTBEAT_INTERVAL_MS = 60_000L

    /**
     * Timestamp de inicio de la ventana actual, persistido en SharedPreferences.
     * El getter/setter con lazy prefs garantiza que el valor sobreviva reinicios
     * del servicio sin necesidad de variables de instancia adicionales.
     */
    private val prefs by lazy {
        getSharedPreferences("kairos_passive", MODE_PRIVATE)
    }

    private var windowStartMs: Long
        get()      = prefs.getLong("window_start_ms", 0L)
        set(value) = prefs.edit().putLong("window_start_ms", value).apply()

    /**
     * Receiver que escucha el broadcast `com.example.kairos.RESET_WINDOW`
     * enviado por [KairosWatchService] cuando el usuario recalibra desde el teléfono.
     * Resetea el timestamp de ventana y limpia el buffer para comenzar desde cero.
     */
    private val resetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            windowStartMs = System.currentTimeMillis()
            hrBuffer.clear()
            Log.d("KairosPassive", "Ventana reseteada via broadcast")
        }
    }

    override fun onCreate() {
        super.onCreate()
        detector = WatchCrisisDetector.getInstance(this)

        // Si windowStartMs es 0 (primer arranque o tras reset completo), inicializamos ahora.
        // Si ya tiene valor, la ventana continúa desde donde quedó antes del reinicio —
        // esto evita perder muestras de una ventana parcialmente completada.
        if (windowStartMs == 0L) {
            windowStartMs = System.currentTimeMillis()
        }

        // RECEIVER_NOT_EXPORTED: el broadcast solo puede ser enviado por la misma app
        registerReceiver(
            resetReceiver,
            IntentFilter("com.example.kairos.RESET_WINDOW"),
            RECEIVER_NOT_EXPORTED
        )

        startHeartbeat()

        Log.d("KairosPassive", "PassiveListener iniciado — cal: ${detector.calibrationWindows}/3 " +
                "— ventana en curso desde hace ${(System.currentTimeMillis() - windowStartMs) / 1000}s")
    }

    /**
     * Inicia el loop de heartbeat periódico que envía el estado actual al teléfono
     * cada [HEARTBEAT_INTERVAL_MS] ms.
     *
     * Solo envía si hay datos de HR disponibles (heartRate > 0) para evitar
     * que el teléfono reciba actualizaciones vacías al inicio del servicio.
     * El heartbeat también resetea el flag `crisisConfirmed` en [KairosPhoneListener],
     * indicando que el reloj volvió al estado normal.
     */
    private fun startHeartbeat() {
        scope.launch {
            while (true) {
                delay(HEARTBEAT_INTERVAL_MS)
                val state = WatchMonitorState.state.value
                if (state.heartRate > 0) {
                    sendToPhone(
                        "/kairos/heartbeat",
                        "hr=${state.heartRate},rmssd=${state.rmssd},cal=${state.calibrationWindows}"
                    )
                    Log.d("KairosPassive", "Heartbeat enviado — HR=${state.heartRate}")
                }
            }
        }
    }

    /**
     * Callback de Health Services API invocado cada vez que llegan nuevas muestras de HR.
     *
     * Acumula las muestras en [hrBuffer], espera el mínimo de datos necesarios,
     * ejecuta el análisis cuando la ventana está completa y envía el mensaje
     * correspondiente al teléfono según el resultado.
     *
     * **Gestión del buffer tras análisis:**
     * - Durante calibración: se limpia el buffer completo para comenzar la siguiente ventana.
     * - Durante detección: se mantiene la mitad del buffer (overlap del 50%) para
     *   que cada ventana comparta datos con la anterior, mejorando la continuidad.
     *
     * @param dataPoints Contenedor de puntos de datos recibidos del sensor.
     */
    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        val hrData = dataPoints.getData(DataType.HEART_RATE_BPM)
        if (hrData.isEmpty()) return

        hrData.forEach { hrBuffer.add(it.value) }
        // Mantenemos el buffer acotado eliminando las muestras más antiguas
        while (hrBuffer.size > MAX_BUFFER_SIZE) hrBuffer.removeAt(0)

        Log.d("KairosPassive", "HR: ${hrData.last().value} BPM — buffer: ${hrBuffer.size}")

        // Necesitamos al menos 6 muestras para calcular RMSSD con 5 diferencias sucesivas
        if (hrBuffer.size < 6) return

        val now       = System.currentTimeMillis()
        val elapsedMs = now - windowStartMs

        if (!detector.isCalibrated()) {
            val remainingSecs = ((CALIBRATION_WINDOW_MS - elapsedMs) / 1000).coerceAtLeast(0)
            Log.d("KairosPassive", "Calibrando ${detector.calibrationWindows + 1}/3 " +
                    "— faltan ${remainingSecs}s")
            // Durante calibración, esperamos que pase la ventana completa antes de analizar
            if (elapsedMs < CALIBRATION_WINDOW_MS) return
            windowStartMs = now
        }

        val result = detector.analyze(hrBuffer.toList()) ?: return

        // Gestionamos el overlap del buffer según el estado de calibración
        if (!detector.isCalibrated()) {
            hrBuffer.clear()  // calibración: ventanas sin overlap
        } else {
            // detección: mantenemos 50% del buffer para overlap entre ventanas
            val keepFrom = hrBuffer.size / 2
            repeat(keepFrom) { if (hrBuffer.isNotEmpty()) hrBuffer.removeAt(0) }
        }

        // Actualizamos el estado observable de la UI del reloj
        WatchMonitorState.update(
            heartRate          = result.averageHrBpm,
            rmssd              = result.rmssdMs,
            crisisState        = when {
                result.isCrisisDetected -> WatchCrisisState.CRISIS
                result.isPreAlert       -> WatchCrisisState.PRE_ALERT
                else                    -> WatchCrisisState.NORMAL
            },
            calibrationWindows = result.calibrationWindows
        )

        // Al completar la calibración, enviamos un heartbeat inmediato al teléfono
        // sin esperar los 60s del timer para que la UI del teléfono se actualice de inmediato
        if (result.calibrationWindows == detector.calibrationWindows && detector.isCalibrated()) {
            Log.d("KairosPassive", "✅ Calibración completa — enviando heartbeat inmediato")
            sendToPhone(
                "/kairos/heartbeat",
                "hr=${result.averageHrBpm},rmssd=${result.rmssdMs},cal=${result.calibrationWindows}"
            )
        }

        // Enviamos el mensaje al teléfono según el resultado del análisis
        when {
            result.isCrisisDetected -> {
                Log.d("KairosPassive", "🚨 CRISIS detectada")
                sendToPhone("/kairos/crisis",
                    "hr=${result.averageHrBpm},rmssd=${result.rmssdMs}")
            }
            result.isPreAlert -> {
                Log.d("KairosPassive", "⚠️ PRE-ALERTA")
                sendToPhone("/kairos/prealerta", "hr=${result.averageHrBpm}")
            }
            else -> {
                Log.d("KairosPassive", "✅ Normal — HR=${"%.1f".format(result.averageHrBpm)} " +
                        "RMSSD=${"%.1f".format(result.rmssdMs)}")
            }
        }
    }

    /**
     * Envía un mensaje al teléfono via Wearable Message API.
     *
     * Usa un scope propio independiente del scope del servicio para garantizar
     * que el mensaje se despache incluso si [onDestroy] fue llamado justo antes
     * (el scope del servicio ya estaría cancelado en ese momento).
     *
     * @param path Path del mensaje (por ejemplo `/kairos/crisis`).
     * @param data Payload del mensaje como string (por ejemplo `"hr=95.3,rmssd=22.1"`).
     */
    private fun sendToPhone(path: String, data: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nodes = Wearable.getNodeClient(this@KairosPassiveListener)
                    .connectedNodes.await()
                nodes.forEach { node ->
                    Wearable.getMessageClient(this@KairosPassiveListener)
                        .sendMessage(node.id, path, data.toByteArray())
                        .await()
                    Log.d("KairosPassive", "Enviado $path → ${node.displayName}")
                }
            } catch (e: Exception) {
                Log.e("KairosPassive", "Error enviando $path: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(resetReceiver)
        scope.cancel()
    }
}