package com.example.kairos

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
import com.example.kairos.ui.WatchCrisisState
import com.example.kairos.ui.WatchMonitorState
import com.example.kairos.ui.theme.KairosTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestPermissions =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { grants ->
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
            "android.permission.ACTIVITY_RECOGNITION"
        ))

        setContent {
            KairosTheme {
                Scaffold(timeText = { TimeText() }) {
                    WatchScreen(
                        onResetBaseline = {
                            lifecycleScope.launch {
                                WatchBaseline.clear(this@MainActivity)
                                Log.d("KairosWatch", "Baseline borrado desde el reloj")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun WatchScreen(
    onResetBaseline: () -> Unit = {}
) {
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
        label = "stateColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = if (state.crisisState == WatchCrisisState.CRISIS) 1.12f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state.crisisState == WatchCrisisState.CRISIS) 400 else 1000,
                easing = EaseInOut
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // ScalingLazyColumn: responde a la corona física del reloj
    ScalingLazyColumn(
        state = rememberScalingLazyListState(),
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 32.dp)
    ) {

        // ── Item 1: pantalla principal ─────────────────────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .scale(pulseScale)
                        .background(
                            color = stateColor.copy(alpha = 0.12f),
                            shape = CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .background(
                            color = stateColor.copy(alpha = 0.08f),
                            shape = CircleShape
                        )
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (state.heartRate > 0)
                            "%.0f".format(state.heartRate) else "—",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "BPM",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = when (state.crisisState) {
                            WatchCrisisState.NORMAL    -> "Normal"
                            WatchCrisisState.PRE_ALERT -> "Estres"
                            WatchCrisisState.CRISIS    -> "Crisis!"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = stateColor,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (state.isCalibrated) "Calibrado"
                        else "${state.calibrationWindows}/3",
                        fontSize = 11.sp,
                        color = if (state.isCalibrated) KairosGreen else KairosOrange
                    )
                }
            }
        }

        // ── Item 2: botón recalibrar (solo visible si ya está calibrado) ───────
        // El usuario lo descubre haciendo scroll con la corona
        if (state.isCalibrated) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
            item {
                Button(
                    onClick = onResetBaseline,
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(40.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF1E293B)
                    )
                ) {
                    Text(
                        text = "Recalibrar",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}