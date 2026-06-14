package com.example.kairos

import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.example.kairos.db.WatchBaseline
import com.example.kairos.detection.WatchCrisisDetector
import com.example.kairos.health.InterventionSession
import com.example.kairos.services.KairosWatchService
import com.example.kairos.techniques.ExercisePreference
import com.example.kairos.techniques.WatchExercisePrefs
import com.example.kairos.ui.BreathingScreen
import com.example.kairos.ui.GroundingScreen
import com.example.kairos.ui.WatchCrisisState
import com.example.kairos.ui.WatchMonitorState
import com.example.kairos.ui.theme.KairosTheme
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Pantallas posibles en el reloj. La navegación entre ellas es manejada por
 * [MainWatchScreen] en respuesta a cambios de [WatchMonitorState] y acciones del usuario.
 */
enum class WatchScreen { MONITOR, CRISIS, BREATHING, GROUNDING }

/**
 * Activity principal del módulo Wear OS de KAIROS.
 *
 * Solicita los permisos necesarios al iniciar y lanza [KairosWatchService] una vez
 * que los permisos son otorgados. Gestiona la confirmación y cancelación de crisis
 * enviando mensajes al teléfono via Wearable Message API.
 *
 * **Flujo de crisis:**
 * 1. [KairosWatchService] detecta crisis → actualiza [WatchMonitorState].
 * 2. [MainWatchScreen] observa el cambio y navega a [WatchScreen.CRISIS].
 * 3. El usuario confirma (`sendCrisisConfirmed`) o cancela (`sendCrisisCancelled`).
 * 4. Si confirma: el teléfono recibe `/kairos/crisis/confirmada` y abre [CrisisAlertActivity].
 * 5. El reloj navega al ejercicio de intervención según [WatchExercisePrefs].
 */
class MainActivity : ComponentActivity() {

    /**
     * Launcher de permisos. Una vez otorgados, inicia [KairosWatchService] si no está corriendo.
     * `BODY_SENSORS` y `health.READ_HEART_RATE` son necesarios para acceder al sensor de HR.
     * `ACTIVITY_RECOGNITION` es necesario para el filtro de movimiento via acelerómetro.
     */
    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            Log.d("KairosWatch", "Permisos: $grants")
            if (!KairosWatchService.isRunning) {
                startService(Intent(this, KairosWatchService::class.java))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("KairosWatch", "MainActivity onCreate")

        requestPermissions.launch(arrayOf(
            "android.permission.health.READ_HEART_RATE",
            "android.permission.BODY_SENSORS",
            "android.permission.ACTIVITY_RECOGNITION",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.health.WRITE_MINDFULNESS"
        ))

        setContent {
            KairosTheme {
                Scaffold(timeText = { TimeText() }) {
                    MainWatchScreen(
                        onResetBaseline   = {
                            lifecycleScope.launch {
                                WatchBaseline.clear(this@MainActivity)
                                Log.d("KairosWatch", "Baseline borrado")
                            }
                        },
                        onCrisisConfirmed = { sendCrisisConfirmed() },
                        onCrisisCancelled = { sendCrisisCancelled() },
                        vibrator          = getSystemService(Vibrator::class.java)
                    )
                }
            }
        }
    }

    /**
     * Notifica al teléfono que el usuario confirmó la crisis (no canceló en el countdown).
     * [KairosPhoneListener] recibe `/kairos/crisis/confirmada` y abre [CrisisAlertActivity].
     */
    private fun sendCrisisConfirmed() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nodes = Wearable.getNodeClient(this@MainActivity).connectedNodes.await()
                nodes.forEach { node ->
                    Wearable.getMessageClient(this@MainActivity)
                        .sendMessage(node.id, "/kairos/crisis/confirmada", ByteArray(0)).await()
                    Log.d("KairosWatch", "Crisis confirmada → ${node.displayName}")
                }
            } catch (e: Exception) {
                Log.e("KairosWatch", "Error: ${e.message}")
            }
        }
    }

    /**
     * Notifica al teléfono que el usuario canceló la crisis (presionó "Estoy bien").
     *
     * Además de notificar al teléfono:
     * - Resetea el contador de ventanas consecutivas en [WatchCrisisDetector] para evitar
     *   que la crisis se reactive inmediatamente.
     * - Resetea [WatchMonitorState] para que la UI vuelva al estado normal.
     */
    private fun sendCrisisCancelled() {
        WatchCrisisDetector.getInstance(this).onUserCancelled()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nodes = Wearable.getNodeClient(this@MainActivity).connectedNodes.await()
                nodes.forEach { node ->
                    Wearable.getMessageClient(this@MainActivity)
                        .sendMessage(node.id, "/kairos/crisis/cancelada", ByteArray(0)).await()
                    Log.d("KairosWatch", "Crisis cancelada → ${node.displayName}")
                }
            } catch (e: Exception) {
                Log.e("KairosWatch", "Error: ${e.message}")
            }
        }
        WatchMonitorState.reset()
    }
}

/**
 * Composable raíz que gestiona la navegación entre las 4 pantallas del reloj.
 *
 * Observa [WatchMonitorState] y navega automáticamente a [WatchScreen.CRISIS] cuando
 * el detector confirma una crisis, y vuelve a [WatchScreen.MONITOR] cuando el estado
 * regresa a NORMAL (excepto si está en medio de un ejercicio).
 *
 * **Secuencia de ejercicios según [WatchExercisePrefs]:**
 * - [ExercisePreference.BREATHING_ONLY]: CRISIS → BREATHING → MONITOR
 * - [ExercisePreference.GROUNDING_ONLY]: CRISIS → GROUNDING → MONITOR
 * - [ExercisePreference.BOTH]: CRISIS → BREATHING → GROUNDING → MONITOR
 *
 * @param onResetBaseline Callback para borrar el baseline y recalibrar.
 * @param onCrisisConfirmed Callback invocado cuando el countdown de pre-alerta termina sin cancelación.
 * @param onCrisisCancelled Callback invocado cuando el usuario presiona "Estoy bien".
 * @param vibrator Instancia del Vibrator para feedback háptico en [CrisisScreen].
 */
@Composable
fun MainWatchScreen(
    onResetBaseline:   () -> Unit = {},
    onCrisisConfirmed: () -> Unit = {},
    onCrisisCancelled: () -> Unit = {},
    vibrator:          Vibrator?  = null
) {
    val context = LocalContext.current
    val state   by WatchMonitorState.state.collectAsState()

    var currentScreen by remember { mutableStateOf(WatchScreen.MONITOR) }

    // Cargamos la preferencia de ejercicio al iniciar la pantalla
    val exercisePref by WatchExercisePrefs.preference.collectAsState()
    LaunchedEffect(Unit) {
        WatchExercisePrefs.load(context)
    }

    // Navegación reactiva según cambios de estado del detector
    LaunchedEffect(state.crisisState) {
        when (state.crisisState) {
            WatchCrisisState.CRISIS -> {
                // Solo navegamos a CRISIS si no estamos ya en un ejercicio en curso
                if (currentScreen == WatchScreen.MONITOR) {
                    currentScreen = WatchScreen.CRISIS
                }
            }
            WatchCrisisState.NORMAL -> {
                // Volvemos al monitor solo si no estamos en medio de un ejercicio
                if (currentScreen != WatchScreen.BREATHING &&
                    currentScreen != WatchScreen.GROUNDING) {
                    currentScreen = WatchScreen.MONITOR
                }
            }
            else -> { /* PRE_ALERT: no cambia de pantalla — solo actualiza el color del indicador */ }
        }
    }

    // Crossfade entre pantallas para transiciones suaves en la pantalla circular del reloj
    AnimatedContent(
        targetState    = currentScreen,
        transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
        label          = "screenSwitch"
    ) { screen ->
        when (screen) {

            WatchScreen.CRISIS -> CrisisScreen(
                heartRate           = state.heartRate,
                onUserIsOk          = {
                    onCrisisCancelled()
                    currentScreen = WatchScreen.MONITOR
                },
                onCountdownFinished = {
                    onCrisisConfirmed()
                    InterventionSession.onExerciseStarted(exercisePref, state.heartRate)
                    // Decidimos el primer ejercicio según la preferencia del usuario
                    currentScreen = when (exercisePref) {
                        ExercisePreference.GROUNDING_ONLY -> WatchScreen.GROUNDING
                        else -> WatchScreen.BREATHING  // BREATHING_ONLY o BOTH
                    }
                    Log.d("KairosWatch", "Iniciando ejercicio: $exercisePref")
                },
                vibrator = vibrator
            )

            WatchScreen.BREATHING -> BreathingScreen(
                onFinished = {
                    InterventionSession.onBreathingFinished(context)
                    // En BOTH: continuamos con grounding; en BREATHING_ONLY: volvemos al monitor
                    if (exercisePref == ExercisePreference.BOTH) {
                        currentScreen = WatchScreen.GROUNDING
                    } else {
                        WatchMonitorState.reset()
                        currentScreen = WatchScreen.MONITOR
                    }
                }
            )

            WatchScreen.GROUNDING -> GroundingScreen(
                onFinished = {
                    InterventionSession.onGroundingFinished(context)
                    WatchMonitorState.reset()
                    currentScreen = WatchScreen.MONITOR
                }
            )

            WatchScreen.MONITOR -> MonitorScreen(
                onResetBaseline = onResetBaseline
            )
        }
    }
}

/**
 * Pantalla de monitoreo en tiempo real del reloj.
 *
 * Muestra la HR actual, el estado de detección y el progreso de calibración
 * en un diseño circular optimizado para la pantalla redonda del Pixel Watch 3.
 *
 * El color del indicador anima suavemente entre verde (normal), naranja (pre-alerta)
 * y rojo (crisis). La velocidad del pulso aumenta en estado de crisis para comunicar urgencia.
 *
 * El botón "Recalibrar" solo aparece cuando la calibración está completa,
 * para no interferir con el proceso de calibración inicial.
 *
 * @param onResetBaseline Callback para borrar el baseline y comenzar una nueva calibración.
 */
@Composable
fun MonitorScreen(onResetBaseline: () -> Unit = {}) {
    val state by WatchMonitorState.state.collectAsState()

    val KairosGreen   = Color(0xFF00E5A0)
    val KairosOrange  = Color(0xFFF59E0B)
    val KairosRed     = Color(0xFFEF4444)
    val Background    = Color(0xFF0A0E1A)
    val TextPrimary   = Color(0xFFE2E8F0)
    val TextSecondary = Color(0xFF64748B)

    val stateColor by animateColorAsState(
        targetValue = when (state.crisisState) {
            WatchCrisisState.NORMAL    -> KairosGreen
            WatchCrisisState.PRE_ALERT -> KairosOrange
            WatchCrisisState.CRISIS    -> KairosRed
        },
        animationSpec = tween(600),
        label         = "stateColor"
    )

    // Pulso más rápido y con mayor amplitud en crisis para comunicar urgencia
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = if (state.crisisState == WatchCrisisState.CRISIS) 1.12f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation  = tween(
                durationMillis = if (state.crisisState == WatchCrisisState.CRISIS) 400 else 1000,
                easing         = EaseInOut
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    ScalingLazyColumn(
        state               = rememberScalingLazyListState(),
        modifier            = Modifier.fillMaxSize().background(Background),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding      = PaddingValues(vertical = 32.dp)
    ) {
        item {
            Box(
                modifier         = Modifier.fillMaxWidth().height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                // Halo exterior pulsante
                Box(modifier = Modifier.size(160.dp).scale(pulseScale)
                    .background(color = stateColor.copy(alpha = 0.12f), shape = CircleShape))
                // Anillo interior estático
                Box(modifier = Modifier.size(130.dp)
                    .background(color = stateColor.copy(alpha = 0.08f), shape = CircleShape))
                // Contenido central: HR, BPM, estado y calibración
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text       = if (state.heartRate > 0) "%.0f".format(state.heartRate) else "—",
                        fontSize   = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color      = TextPrimary,
                        textAlign  = TextAlign.Center
                    )
                    Text("BPM", fontSize = 11.sp, color = TextSecondary, letterSpacing = 2.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = when (state.crisisState) {
                            WatchCrisisState.NORMAL    -> "Normal"
                            WatchCrisisState.PRE_ALERT -> "Estres"
                            WatchCrisisState.CRISIS    -> "Crisis!"
                        },
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = stateColor,
                        textAlign  = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Muestra "Calibrado" en verde o "N/3" en naranja durante calibración
                    Text(
                        text  = if (state.isCalibrated) "Calibrado" else "${state.calibrationWindows}/3",
                        fontSize = 11.sp,
                        color = if (state.isCalibrated) KairosGreen else KairosOrange
                    )
                }
            }
        }
        // El botón de recalibrar solo aparece cuando la calibración está completa
        if (state.isCalibrated) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                Button(
                    onClick  = onResetBaseline,
                    modifier = Modifier.fillMaxWidth(0.75f).height(40.dp),
                    colors   = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1E293B))
                ) {
                    Text("Recalibrar", fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
    }
}

/**
 * Pantalla de pre-alerta de crisis con countdown de confirmación.
 *
 * Muestra un countdown circular de [countdownSeconds] segundos. Si el usuario
 * no presiona "Estoy bien" antes de que termine, se invoca [onCountdownFinished]
 * y el sistema confirma la crisis.
 *
 * **Feedback háptico:**
 * - Al mostrar la pantalla: patrón de vibración de alerta (3 pulsos).
 * - A los 10s y 5s restantes: vibración simple como recordatorio.
 *
 * **Arco de progreso:**
 * El arco cambia de color de rojo a ámbar cuando quedan menos de 10 segundos,
 * indicando urgencia creciente.
 *
 * @param heartRate HR en BPM al momento de la detección, mostrada como contexto.
 * @param countdownSeconds Duración del countdown en segundos (default: 15).
 * @param onUserIsOk Callback invocado cuando el usuario presiona "Estoy bien".
 * @param onCountdownFinished Callback invocado cuando el countdown llega a 0.
 * @param vibrator Instancia del Vibrator para feedback háptico. Puede ser `null`.
 */
@Composable
fun CrisisScreen(
    heartRate:           Double,
    countdownSeconds:    Int       = 15,
    onUserIsOk:          () -> Unit = {},
    onCountdownFinished: () -> Unit = {},
    vibrator:            Vibrator?  = null
) {
    val KairosRed   = Color(0xFFEF4444)
    val KairosAmber = Color(0xFFF59E0B)
    val Background  = Color(0xFF0A0E1A)
    val TextPrimary = Color(0xFFE2E8F0)

    var secondsLeft by remember { mutableIntStateOf(countdownSeconds) }

    // Vibración de alerta al mostrar la pantalla por primera vez
    LaunchedEffect(Unit) {
        vibrator?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300, 150, 600), -1)
        )
    }

    // Countdown con vibraciones de recordatorio a los 10s y 5s restantes
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1_000L)
            secondsLeft--
            if (secondsLeft == 10 || secondsLeft == 5) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            }
        }
        onCountdownFinished()
    }

    val progress  = secondsLeft.toFloat() / countdownSeconds.toFloat()
    // El arco cambia a ámbar en los últimos 10 segundos para indicar urgencia creciente
    val arcColor by animateColorAsState(
        targetValue   = if (secondsLeft > 10) KairosRed else KairosAmber,
        animationSpec = tween(500),
        label         = "arcColor"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "urgency")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.08f,
        animationSpec = infiniteRepeatable(
            tween(350, easing = EaseInOut),
            RepeatMode.Reverse
        ),
        label = "urgencyPulse"
    )

    Box(
        modifier         = Modifier.fillMaxSize().background(Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier            = Modifier.padding(horizontal = 16.dp)
        ) {
            // Arco circular de progreso con countdown en el centro
            Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier.size(110.dp).scale(pulseScale).drawBehind {
                        val stroke = Stroke(width = 6.dp.toPx())
                        // Arco de fondo (track)
                        drawArc(color = arcColor.copy(alpha = 0.15f), startAngle = -90f,
                            sweepAngle = -360f, useCenter = false, style = stroke)
                        // Arco de progreso (decrece con el tiempo)
                        drawArc(color = arcColor, startAngle = -90f,
                            sweepAngle = -360f * progress, useCenter = false, style = stroke)
                    }
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$secondsLeft",
                        fontSize   = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color      = arcColor
                    )
                    Text("seg", fontSize = 10.sp, color = arcColor.copy(alpha = 0.7f), letterSpacing = 1.sp)
                }
            }

            Text(
                "¿Estás bien?",
                fontSize   = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color      = TextPrimary,
                textAlign  = TextAlign.Center
            )
            Text(
                "HR %.0f BPM".format(heartRate),
                fontSize = 11.sp,
                color    = KairosRed.copy(alpha = 0.8f)
            )

            Button(
                onClick  = onUserIsOk,
                modifier = Modifier.fillMaxWidth(0.8f).height(44.dp),
                colors   = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF166534))
            ) {
                Text(
                    "Estoy bien",
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = Color(0xFF86EFAC)
                )
            }

            Text(
                text       = "Sin respuesta se\navisa a tus contactos",
                fontSize   = 10.sp,
                color      = TextPrimary.copy(alpha = 0.4f),
                textAlign  = TextAlign.Center,
                lineHeight = 13.sp
            )
        }
    }
}