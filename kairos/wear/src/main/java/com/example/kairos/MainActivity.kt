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

enum class WatchScreen { MONITOR, CRISIS, BREATHING, GROUNDING }

class MainActivity : ComponentActivity() {

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

// ── Pantalla principal con 4 estados ─────────────────────────────────────────

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

    // Leer preferencia de ejercicio del reloj (sincronizada desde el teléfono via DataClient)
    val exercisePref by WatchExercisePrefs.preference.collectAsState()
    LaunchedEffect(Unit) {
        WatchExercisePrefs.load(context)
    }

    LaunchedEffect(state.crisisState) {
        when (state.crisisState) {
            WatchCrisisState.CRISIS -> {
                if (currentScreen == WatchScreen.MONITOR) {
                    currentScreen = WatchScreen.CRISIS
                }
            }
            WatchCrisisState.NORMAL -> {
                if (currentScreen != WatchScreen.BREATHING &&
                    currentScreen != WatchScreen.GROUNDING) {
                    currentScreen = WatchScreen.MONITOR
                }
            }
            else -> { /* PRE_ALERT no cambia pantalla */ }
        }
    }

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
                    // Decidir qué ejercicio mostrar según preferencia guardada en el reloj
                    currentScreen = when (exercisePref) {
                        ExercisePreference.GROUNDING_ONLY -> WatchScreen.GROUNDING
                        else -> WatchScreen.BREATHING // BREATHING_ONLY o BOTH
                    }
                    Log.d("KairosWatch", "Iniciando ejercicio: $exercisePref")
                },
                vibrator = vibrator
            )

            WatchScreen.BREATHING -> BreathingScreen(
                onFinished = {
                    InterventionSession.onBreathingFinished(context)
                    // Si es BOTH, continuar con grounding después de la respiración
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

// ── MonitorScreen ─────────────────────────────────────────────────────────────

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
        animationSpec = tween(600), label = "stateColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = if (state.crisisState == WatchCrisisState.CRISIS) 1.12f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation  = tween(if (state.crisisState == WatchCrisisState.CRISIS) 400 else 1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    ScalingLazyColumn(
        state = rememberScalingLazyListState(),
        modifier = Modifier.fillMaxSize().background(Background),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 32.dp)
    ) {
        item {
            Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(160.dp).scale(pulseScale)
                    .background(color = stateColor.copy(alpha = 0.12f), shape = CircleShape))
                Box(modifier = Modifier.size(130.dp)
                    .background(color = stateColor.copy(alpha = 0.08f), shape = CircleShape))
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(
                        text = if (state.heartRate > 0) "%.0f".format(state.heartRate) else "—",
                        fontSize = 44.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Text("BPM", fontSize = 11.sp, color = TextSecondary, letterSpacing = 2.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = when (state.crisisState) {
                            WatchCrisisState.NORMAL    -> "Normal"
                            WatchCrisisState.PRE_ALERT -> "Estres"
                            WatchCrisisState.CRISIS    -> "Crisis!"
                        },
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = stateColor, textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text  = if (state.isCalibrated) "Calibrado" else "${state.calibrationWindows}/3",
                        fontSize = 11.sp,
                        color = if (state.isCalibrated) KairosGreen else KairosOrange
                    )
                }
            }
        }
        if (state.isCalibrated) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                Button(onClick = onResetBaseline, modifier = Modifier.fillMaxWidth(0.75f).height(40.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1E293B))) {
                    Text("Recalibrar", fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
    }
}

// ── CrisisScreen ──────────────────────────────────────────────────────────────

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

    LaunchedEffect(Unit) {
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300, 150, 600), -1))
    }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1_000L)
            secondsLeft--
            if (secondsLeft == 10 || secondsLeft == 5) {
                vibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
        onCountdownFinished()
    }

    val progress  = secondsLeft.toFloat() / countdownSeconds.toFloat()
    val arcColor by animateColorAsState(
        targetValue = if (secondsLeft > 10) KairosRed else KairosAmber,
        animationSpec = tween(500), label = "arcColor"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "urgency")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue  = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(350, easing = EaseInOut), RepeatMode.Reverse),
        label = "urgencyPulse"
    )

    Box(modifier = Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(110.dp).scale(pulseScale).drawBehind {
                    val stroke = Stroke(width = 6.dp.toPx())
                    drawArc(color = arcColor.copy(alpha = 0.15f), startAngle = -90f,
                        sweepAngle = -360f, useCenter = false, style = stroke)
                    drawArc(color = arcColor, startAngle = -90f,
                        sweepAngle = -360f * progress, useCenter = false, style = stroke)
                })
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$secondsLeft", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = arcColor)
                    Text("seg", fontSize = 10.sp, color = arcColor.copy(alpha = 0.7f), letterSpacing = 1.sp)
                }
            }

            Text("¿Estás bien?", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = TextPrimary, textAlign = TextAlign.Center)
            Text("HR %.0f BPM".format(heartRate), fontSize = 11.sp, color = KairosRed.copy(alpha = 0.8f))

            Button(onClick = onUserIsOk, modifier = Modifier.fillMaxWidth(0.8f).height(44.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF166534))) {
                Text("Estoy bien", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF86EFAC))
            }

            Text(
                text = "Sin respuesta se\navisa a tus contactos",
                fontSize = 10.sp, color = TextPrimary.copy(alpha = 0.4f),
                textAlign = TextAlign.Center, lineHeight = 13.sp
            )
        }
    }
}