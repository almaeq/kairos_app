package com.example.kairos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.kairos.mobile.CrisisState
import com.example.kairos.mobile.MonitorState
import com.example.kairos.mobile.SmsAlertManager
import com.example.kairos.mobile.data.BaselineRepository
import com.example.kairos.mobile.data.db.KairosDatabase
import com.example.kairos.ui.CalibrationActivity
import com.example.kairos.ui.ContactsActivity
import com.example.kairos.ui.ExerciseSettingsActivity
import com.example.kairos.ui.ProfileActivity
import com.example.kairos.ui.EpisodeLogActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var baselineRepo: BaselineRepository

    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_SEND_SMS) {
                Log.d("KAIROS", "Broadcast SMS recibido — enviando alerta")
                lifecycleScope.launch {
                    SmsAlertManager.sendEmergencyAlert(this@MainActivity)
                }
            }
        }
    }

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.all { it }) Log.d("KAIROS", "Permisos otorgados")
            else Log.e("KAIROS", "Permisos denegados")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = KairosDatabase.getInstance(this)
        baselineRepo = BaselineRepository(db.kairosDao(), this)

        requestPermissions.launch(arrayOf(
            "android.permission.SEND_SMS",
            "android.permission.READ_CONTACTS",
            "android.permission.READ_PHONE_STATE"
        ))

        registerReceiver(smsReceiver, IntentFilter(ACTION_SEND_SMS), Context.RECEIVER_NOT_EXPORTED)

        lifecycleScope.launch {
            val baseline = db.kairosDao().getBaseline()
            if (baseline != null) {
                Log.d("KAIROS", "Baseline local — cal: ${baseline.calibrationWindows}/3")
                MonitorState.preloadCalibration(baseline.calibrationWindows)
            } else {
                Log.d("KAIROS", "Sin baseline — esperando calibración")
            }
        }

        setContent {
            MonitorScreen(
                onRecalibrate = { recalibrate() },
                onContacts    = { startActivity(Intent(this, ContactsActivity::class.java)) },
                onTechnique   = { startActivity(Intent(this, ExerciseSettingsActivity::class.java)) },
                onProfile     = { startActivity(Intent(this, ProfileActivity::class.java)) },
                onEpisodes = { startActivity(Intent(this, EpisodeLogActivity::class.java)) },
                onTestSms     = { lifecycleScope.launch { SmsAlertManager.sendEmergencyAlert(this@MainActivity) } }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(smsReceiver) } catch (e: Exception) { }
    }

    private fun recalibrate() {
        lifecycleScope.launch {
            baselineRepo.clear()
            startActivity(Intent(this@MainActivity, CalibrationActivity::class.java))
        }
    }

    companion object {
        const val ACTION_SEND_SMS = "com.example.kairos.SEND_EMERGENCY_SMS"
    }
}

@Composable
fun MonitorScreen(
    onRecalibrate: () -> Unit = {},
    onContacts:    () -> Unit = {},
    onTechnique:   () -> Unit = {},
    onProfile:     () -> Unit = {},
    onEpisodes:     () -> Unit = {},
    onTestSms:     () -> Unit = {}
) {
    val monitorData by MonitorState.data.collectAsState()

    var tickerSeconds by remember { mutableStateOf(0) }
    LaunchedEffect(monitorData.lastUpdated) {
        while (true) {
            delay(1_000)
            tickerSeconds = if (monitorData.lastUpdated > 0)
                ((System.currentTimeMillis() - monitorData.lastUpdated) / 1000).toInt()
            else 0
        }
    }

    val BackgroundDark = Color(0xFF0A0E1A)
    val CardDark       = Color(0xFF111827)
    val KairosGreen    = Color(0xFF00E5A0)
    val KairosBlue     = Color(0xFF3B82F6)
    val KairosOrange   = Color(0xFFF59E0B)
    val KairosRed      = Color(0xFFEF4444)
    val TextPrimary    = Color(0xFFE2E8F0)
    val TextSecondary  = Color(0xFF64748B)

    val stateColor by animateColorAsState(
        targetValue = when (monitorData.crisisState) {
            CrisisState.NORMAL    -> KairosGreen
            CrisisState.PRE_ALERT -> KairosOrange
            CrisisState.CRISIS    -> KairosRed
        },
        animationSpec = tween(600), label = "stateColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = if (monitorData.crisisState == CrisisState.CRISIS) 1.15f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation  = tween(if (monitorData.crisisState == CrisisState.CRISIS) 400 else 1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick  = onProfile,
                    modifier = Modifier.align(Alignment.TopStart).size(36.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Default.Person,
                        contentDescription = "Mi perfil",
                        tint               = Color.White,
                        modifier           = Modifier.size(25.dp)
                    )
                }
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("KAIROS", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        color = stateColor, letterSpacing = 6.sp)
                    Text("The Right Time", fontSize = 12.sp, color = TextSecondary, letterSpacing = 2.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(180.dp).scale(pulseScale).background(
                    brush = Brush.radialGradient(colors = listOf(stateColor.copy(alpha = 0.15f), Color.Transparent)),
                    shape = CircleShape))
                Box(modifier = Modifier.size(150.dp).background(
                    color = stateColor.copy(alpha = 0.08f), shape = CircleShape))
                Box(modifier = Modifier.size(120.dp).background(
                    color = CardDark, shape = CircleShape),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (monitorData.heartRate > 0) "%.0f".format(monitorData.heartRate) else "—",
                            fontSize = 42.sp, fontWeight = FontWeight.Bold, color = TextPrimary
                        )
                        Text("BPM", fontSize = 12.sp, color = TextSecondary, letterSpacing = 2.sp)
                    }
                }
            }

            Box(modifier = Modifier.clip(RoundedCornerShape(50))
                .background(stateColor.copy(alpha = 0.15f))
                .padding(horizontal = 24.dp, vertical = 10.dp)) {
                Text(
                    text = when (monitorData.crisisState) {
                        CrisisState.NORMAL    -> "● Normal"
                        CrisisState.PRE_ALERT -> "● Estrés elevado"
                        CrisisState.CRISIS    -> "● Crisis detectada"
                    },
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = stateColor
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(modifier = Modifier.weight(1f), label = "RMSSD",
                    value = if (monitorData.rmssd > 0) "%.1f ms".format(monitorData.rmssd) else "—",
                    color = KairosBlue, cardColor = CardDark)
                MetricCard(modifier = Modifier.weight(1f), label = "Calibración",
                    value = if (monitorData.isCalibrated) "✓" else "${monitorData.calibrationWindows}/3",
                    color = if (monitorData.isCalibrated) KairosGreen else KairosOrange,
                    cardColor = CardDark)
            }

            if (!monitorData.isCalibrated) {
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(KairosOrange.copy(alpha = 0.1f)).padding(16.dp)) {
                    Text("Calibrando perfil fisiológico...\nUsá el reloj un momento.",
                        fontSize = 13.sp, color = KairosOrange, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                }
            }

            if (monitorData.crisisState == CrisisState.CRISIS) {
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(KairosRed.copy(alpha = 0.15f)).padding(16.dp)) {
                    Text("🚨 Crisis detectada\nSe están activando los protocolos de apoyo.",
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = KairosRed,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (monitorData.watchConnected) "⌚ Reloj conectado" else "⌚ Reloj desconectado",
                    fontSize = 11.sp,
                    color = if (monitorData.watchConnected) KairosGreen.copy(alpha = 0.7f) else TextSecondary
                )
                if (monitorData.lastUpdated > 0) {
                    Text(
                        text = when {
                            tickerSeconds < 60  -> "Último dato: hace ${tickerSeconds}s"
                            tickerSeconds < 120 -> "Último dato: hace 1 min"
                            else                -> "Último dato: hace ${tickerSeconds / 60} min"
                        },
                        fontSize = 11.sp,
                        color = when {
                            tickerSeconds < 90  -> TextSecondary
                            tickerSeconds < 180 -> KairosOrange
                            else                -> KairosRed.copy(alpha = 0.7f)
                        }
                    )
                } else {
                    Text("Esperando datos del reloj...", fontSize = 11.sp, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            TextButton(onClick = onTechnique, modifier = Modifier.fillMaxWidth()) {
                Text("🧘 Ejercicio de intervención", fontSize = 13.sp, color = TextSecondary)
            }
            TextButton(onClick = onContacts, modifier = Modifier.fillMaxWidth()) {
                Text("👥  Contactos de confianza", fontSize = 13.sp, color = TextSecondary)
            }
            TextButton(onClick = onEpisodes, modifier = Modifier.fillMaxWidth()) {
                Text("📋 Bitácora de episodios", fontSize = 13.sp, color = TextSecondary)
            }
            TextButton(onClick = onRecalibrate, modifier = Modifier.fillMaxWidth()) {
                Text("↺  Recalibrar baseline", fontSize = 13.sp, color = TextSecondary)
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun MetricCard(
    modifier: Modifier = Modifier,
    label: String, value: String, color: Color, cardColor: Color
) {
    Box(modifier = modifier.clip(RoundedCornerShape(16.dp)).background(cardColor).padding(16.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(label, fontSize = 11.sp, color = Color(0xFF64748B), letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
