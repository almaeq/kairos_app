package com.example.kairos.mobile

import android.content.Context
import android.util.Log
import com.example.kairos.mobile.data.db.BaselineStats
import com.example.kairos.mobile.data.db.KairosDatabase
import com.example.kairos.mobile.episodeRegister.EpisodeTracker
import com.example.kairos.mobile.techniques.BreathingState
import com.example.kairos.mobile.techniques.GroundingState
import com.example.kairos.ui.BreathingActivity
import com.example.kairos.ui.CrisisAlertActivity
import com.example.kairos.ui.GroundingActivity
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Servicio que escucha y procesa todos los mensajes enviados desde el reloj al teléfono
 * via Wearable Message API.
 *
 * Es el punto de entrada principal de datos desde Wear OS hacia la app Android.
 * Se ejecuta en segundo plano y es iniciado automáticamente por el sistema cuando
 * llega un mensaje al path registrado.
 *
 * **Paths manejados:**
 * | Path | Descripción |
 * |------|-------------|
 * | `/kairos/ping` | Verificación de conectividad desde el reloj |
 * | `/kairos/heartbeat` | Actualización periódica de HR y RMSSD en estado normal |
 * | `/kairos/baseline` | Sincronización de parámetros de calibración (Welford) |
 * | `/kairos/hr` | Actualización puntual de HR |
 * | `/kairos/prealerta` | Notificación de pre-alerta detectada en el reloj |
 * | `/kairos/crisis` | Notificación de crisis confirmada por el detector del reloj |
 * | `/kairos/crisis/confirmada` | El usuario confirmó la crisis en el reloj |
 * | `/kairos/crisis/cancelada` | El usuario canceló la crisis en el reloj |
 * | `/kairos/grounding/paso` | Actualización de paso del ejercicio de grounding |
 * | `/kairos/breathing/update` | Actualización de fase y ciclo del ejercicio de respiración |
 * | `/kairos/breathing/done` | El ejercicio de respiración finalizó |
 *
 * **Flag [crisisConfirmed]:**
 * Evita que mensajes `/kairos/crisis` repetidos (enviados por el reloj en cada ventana
 * mientras el Modo Crisis está activo) interrumpan el ejercicio en curso.
 * Se resetea a `false` cuando llega un `/kairos/heartbeat` (señal de que el reloj
 * volvió al estado normal) o cuando el usuario cancela la crisis.
 */
class KairosPhoneListener : WearableListenerService() {

    /**
     * Indica si la crisis ya fue confirmada y el ejercicio está en curso.
     *
     * Mientras es `true`, los mensajes `/kairos/crisis` entrantes se ignoran
     * para evitar interrumpir el Modo Crisis con detecciones redundantes.
     * Se resetea a `false` al recibir `/kairos/heartbeat` o `/kairos/crisis/cancelada`.
     */
    private var crisisConfirmed = false

    /**
     * Procesa cada mensaje recibido desde el reloj y ejecuta la acción correspondiente.
     *
     * @param messageEvent Evento de mensaje con path y payload en bytes.
     */
    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {

            "/kairos/ping" -> {
                // El reloj envía un ping al conectarse para verificar la comunicación
                Log.d("KairosPhone", "Ping recibido del reloj")
                MonitorState.setWatchConnected(true)
            }

            "/kairos/heartbeat" -> {
                // Actualización periódica del estado normal del reloj
                // Formato del payload: "hr=72.4,rmssd=45.2,cal=3"
                val data  = String(messageEvent.data)
                val bpm   = data.substringAfter("hr=").substringBefore(",").toDoubleOrNull() ?: 0.0
                val rmssd = data.substringAfter("rmssd=").substringBefore(",").toDoubleOrNull() ?: 0.0
                val cal   = data.substringAfter("cal=").toIntOrNull()
                    ?: MonitorState.data.value.calibrationWindows

                // El heartbeat indica que el reloj volvió al estado normal
                // → reseteamos el flag para volver a escuchar crisis
                crisisConfirmed = false

                Log.d("KairosPhone", "Heartbeat — HR=$bpm RMSSD=$rmssd cal=$cal")
                MonitorState.updateFromWear(
                    bpm                = bpm,
                    rmssd              = rmssd,
                    state              = CrisisState.NORMAL,
                    calibrationWindows = cal
                )
            }

            "/kairos/baseline" -> {
                // El reloj envía sus parámetros de Welford para sincronizar con Room
                // Formato: "hrMean=72.4,hrM2=120.5,hrCount=3,hrvMean=51.0,hrvM2=80.2,hrvCount=3,cal=3"
                val data     = String(messageEvent.data)
                val hrMean   = data.substringAfter("hrMean=").substringBefore(",").toDoubleOrNull() ?: 0.0
                val hrM2     = data.substringAfter("hrM2=").substringBefore(",").toDoubleOrNull() ?: 0.0
                val hrCount  = data.substringAfter("hrCount=").substringBefore(",").toIntOrNull() ?: 0
                val hrvMean  = data.substringAfter("hrvMean=").substringBefore(",").toDoubleOrNull() ?: 0.0
                val hrvM2    = data.substringAfter("hrvM2=").substringBefore(",").toDoubleOrNull() ?: 0.0
                val hrvCount = data.substringAfter("hrvCount=").substringBefore(",").toIntOrNull() ?: 0
                val cal      = data.substringAfter("cal=").toIntOrNull() ?: 0

                Log.d("KairosPhone", "Baseline recibido — HR=$hrMean RMSSD=$hrvMean cal=$cal")

                // Persistimos el baseline en Room en un coroutine de IO
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    KairosDatabase.getInstance(applicationContext).kairosDao().saveBaseline(
                        BaselineStats(
                            hrCount            = hrCount,
                            hrMean             = hrMean,
                            hrM2               = hrM2,
                            hrvCount           = hrvCount,
                            hrvMean            = hrvMean,
                            hrvM2              = hrvM2,
                            calibrationWindows = cal,
                            updatedAt          = System.currentTimeMillis()
                        )
                    )
                    MonitorState.preloadCalibration(cal)
                }
            }

            "/kairos/hr" -> {
                // Actualización puntual de HR (sin RMSSD ni calibración)
                // Se usa para mantener el indicador de HR en la UI del teléfono actualizado
                val bpm = String(messageEvent.data).toDoubleOrNull() ?: return
                Log.d("KairosPhone", "HR recibida: $bpm BPM")
                MonitorState.updateHr(bpm)
            }

            "/kairos/prealerta" -> {
                // El detector del reloj superó el umbral de pre-alerta
                // Formato del payload: "hr=85.2"
                val data = String(messageEvent.data)
                val bpm  = data.substringAfter("hr=").toDoubleOrNull() ?: 0.0
                Log.d("KairosPhone", "Pre-alerta recibida: hr=$bpm")
                MonitorState.updateFromWear(
                    bpm                = bpm,
                    rmssd              = MonitorState.data.value.rmssd,
                    state              = CrisisState.PRE_ALERT,
                    calibrationWindows = MonitorState.data.value.calibrationWindows
                )
            }

            "/kairos/crisis" -> {
                // El detector del reloj confirmó una crisis (N ventanas consecutivas positivas)
                // Formato del payload: "hr=95.3,rmssd=22.1"

                // Si el ejercicio ya está en curso, ignoramos mensajes de crisis redundantes
                // para no interrumpir el Modo Crisis con detecciones repetidas del reloj
                if (crisisConfirmed) {
                    Log.d("KairosPhone", "CRISIS ignorada — ejercicio en curso")
                    return
                }

                val data  = String(messageEvent.data)
                val bpm   = data.substringAfter("hr=").substringBefore(",").toDoubleOrNull() ?: 0.0
                val rmssd = data.substringAfter("rmssd=").toDoubleOrNull() ?: 0.0
                Log.d("KairosPhone", "CRISIS recibida: hr=$bpm rmssd=$rmssd")

                // Persistimos los datos en SharedPreferences como respaldo por si el proceso muere
                // entre la detección y la confirmación del usuario
                applicationContext.getSharedPreferences("kairos_crisis", Context.MODE_PRIVATE)
                    .edit()
                    .putFloat("last_hr", bpm.toFloat())
                    .putFloat("last_rmssd", rmssd.toFloat())
                    .putLong("last_timestamp", System.currentTimeMillis())
                    .apply()

                MonitorState.updateFromWear(
                    bpm                = bpm,
                    rmssd              = rmssd,
                    state              = CrisisState.CRISIS,
                    calibrationWindows = MonitorState.data.value.calibrationWindows
                )
                EpisodeTracker.onCrisisDetected(bpm, rmssd)
            }

            "/kairos/crisis/confirmada" -> {
                // El usuario no canceló la pre-alerta en el reloj → crisis real confirmada
                // Activamos el flag para bloquear mensajes de crisis redundantes
                Log.d("KairosPhone", "Crisis confirmada — abriendo pantalla de alerta")
                crisisConfirmed = true
                CrisisAlertActivity.launch(applicationContext)
                EpisodeTracker.onCrisisConfirmed(applicationContext)
            }

            "/kairos/crisis/cancelada" -> {
                // El usuario canceló la pre-alerta → falso positivo, sin SMS
                // Reseteamos el flag para volver a escuchar crisis
                Log.d("KairosPhone", "Crisis cancelada por usuario — sin SMS")
                crisisConfirmed = false
                MonitorState.updateFromWear(
                    bpm                = MonitorState.data.value.heartRate,
                    rmssd              = MonitorState.data.value.rmssd,
                    state              = CrisisState.NORMAL,
                    calibrationWindows = MonitorState.data.value.calibrationWindows
                )
                EpisodeTracker.onCrisisCancelled(applicationContext)
            }

            "/kairos/grounding/paso" -> {
                // El reloj avanzó al siguiente paso del ejercicio de grounding
                val paso = String(messageEvent.data).toIntOrNull() ?: return
                GroundingState.updateStep(paso)
                // Abrimos la Activity de grounding solo cuando hay un paso activo (1-5)
                if (paso in 1..5) GroundingActivity.launch(applicationContext)
                Log.d("KairosPhone", "Grounding paso $paso")
            }

            "/kairos/breathing/update" -> {
                // El reloj avanzó a una nueva fase del ejercicio de respiración
                // Formato del payload: "phase=Inhalá,cycle=2"
                val data  = String(messageEvent.data)
                val phase = data.substringAfter("phase=").substringBefore(",")
                val cycle = data.substringAfter("cycle=").toIntOrNull() ?: 0
                BreathingState.updatePhase(phase, cycle)
                // Abrimos la Activity de respiración solo en el primer ciclo y primera fase
                // para no re-lanzarla en actualizaciones intermedias
                if (cycle == 1 && phase == "Inhalá") {
                    BreathingActivity.launch(applicationContext)
                }
                Log.d("KairosPhone", "Breathing update — phase=$phase cycle=$cycle")
            }

            "/kairos/breathing/done" -> {
                // El reloj completó todos los ciclos del ejercicio de respiración
                Log.d("KairosPhone", "Breathing completado")
                BreathingState.markDone()
            }
        }
    }

    /**
     * Callback invocado cuando el reloj se conecta al teléfono.
     *
     * @param peer Nodo Wear OS que se conectó.
     */
    override fun onPeerConnected(peer: com.google.android.gms.wearable.Node) {
        Log.d("KairosPhone", "Reloj conectado: ${peer.displayName}")
        MonitorState.setWatchConnected(true)
    }

    /**
     * Callback invocado cuando el reloj se desconecta del teléfono.
     * Actualiza [MonitorState] para que la UI refleje la pérdida de conexión.
     *
     * @param peer Nodo Wear OS que se desconectó.
     */
    override fun onPeerDisconnected(peer: com.google.android.gms.wearable.Node) {
        Log.d("KairosPhone", "Reloj desconectado: ${peer.displayName}")
        MonitorState.setWatchConnected(false)
    }
}