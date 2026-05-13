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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.kairos.mobile.data.BaselineRepository
import com.example.kairos.mobile.data.HealthConnectManager
import com.example.kairos.mobile.data.db.KairosDatabase
import com.example.kairos.mobile.detection.KairosMonitorService
import com.example.kairos.ui.CalibrationActivity
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var baselineRepo: BaselineRepository

    private val requestPermissions =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { grants ->
            if (grants.values.all { it }) {
                Log.d("KAIROS", "✅ Permisos otorgados")
                startMonitoring()
            } else {
                Log.e("KAIROS", "❌ Permisos denegados")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = KairosDatabase.getInstance(this)
        baselineRepo = BaselineRepository(db.kairosDao())

        lifecycleScope.launch {
            val isCalibrated = db.kairosDao().getBaseline() != null
            Log.d("KAIROS", "Calibrado: $isCalibrated")
        }

        setContent {
            MonitorScreen(
                onRecalibrate = { recalibrate() }
            )
        }
    }

    private fun recalibrate() {
        lifecycleScope.launch {
            baselineRepo.clear()
            startActivity(Intent(this@MainActivity, CalibrationActivity::class.java))
        }
    }

    private fun startMonitoring() {
        //KairosMonitorService.start(this)
        Log.d("KAIROS", "✅ Monitoreo iniciado")
    }
}

@Composable
fun MonitorScreen(
    onRecalibrate: () -> Unit = {}
) {
    val monitorData by MonitorState.data.collectAsState()

    val BackgroundDark  = Color(0xFF0A0E1A)
    val CardDark        = Color(0xFF111827)
    val KairosGreen     = Color(0xFF00E5A0)
    val KairosBlue      = Color(0xFF3B82F6)
    val KairosOrange    = Color(0xFFF59E0B)
    val KairosRed       = Color(0xFFEF4444)
    val TextPrimary     = Color(0xFFE2E8F0)
    val TextSecondary   = Color(0xFF64748B)

    val stateColor by animateColorAsState(
        targetValue = when (monitorData.crisisState) {
            CrisisState.NORMAL    -> KairosGreen
            CrisisState.PRE_ALERT -> KairosOrange
            CrisisState.CRISIS    -> KairosRed
        },
        animationSpec = tween(600),
        label = "stateColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = if (monitorData.crisisState == CrisisState.CRISIS) 1.15f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (monitorData.crisisState == CrisisState.CRISIS) 400 else 1000,
                easing = EaseInOut
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header
            Text(
                text = "KAIROS",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = stateColor,
                letterSpacing = 6.sp
            )
            Text(
                text = "The Right Time",
                fontSize = 12.sp,
                color = TextSecondary,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Círculo HR central
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .scale(pulseScale)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    stateColor.copy(alpha = 0.15f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .background(
                            color = stateColor.copy(alpha = 0.08f),
                            shape = CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(color = CardDark, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (monitorData.heartRate > 0)
                                "%.0f".format(monitorData.heartRate) else "—",
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "BPM",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }

            // Estado
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(stateColor.copy(alpha = 0.15f))
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text(
                    text = when (monitorData.crisisState) {
                        CrisisState.NORMAL    -> "● Normal"
                        CrisisState.PRE_ALERT -> "● Estrés elevado"
                        CrisisState.CRISIS    -> "● Crisis detectada"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = stateColor
                )
            }

            // Cards de métricas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    label = "RMSSD",
                    value = if (monitorData.rmssd > 0)
                        "%.1f ms".format(monitorData.rmssd) else "—",
                    color = KairosBlue,
                    cardColor = CardDark
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    label = "Calibración",
                    value = "${monitorData.calibrationWindows}/3",
                    color = if (monitorData.isCalibrated) KairosGreen else KairosOrange,
                    cardColor = CardDark
                )
            }

            // Aviso de calibración pendiente
            if (!monitorData.isCalibrated) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(KairosOrange.copy(alpha = 0.1f))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Calibrando perfil fisiológico...\nUsá el reloj un momento.",
                        fontSize = 13.sp,
                        color = KairosOrange,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Aviso de crisis
            if (monitorData.crisisState == CrisisState.CRISIS) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(KairosRed.copy(alpha = 0.15f))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "🚨 Crisis detectada\nSe están activando los protocolos de apoyo.",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = KairosRed,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Última actualización
            if (monitorData.lastUpdated > 0) {
                val segundos = ((System.currentTimeMillis() - monitorData.lastUpdated) / 1000).toInt()
                Text(
                    text = "Última lectura: hace ${segundos}s",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            // Empuja el botón al fondo
            Spacer(modifier = Modifier.weight(1f))

            // Botón de recalibrar
            TextButton(
                onClick = onRecalibrate,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "↺  Recalibrar baseline",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun MetricCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    color: Color,
    cardColor: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor)
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = label, fontSize = 11.sp, color = Color(0xFF64748B), letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}