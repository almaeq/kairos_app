package com.example.kairos.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kairos.mobile.data.db.BaselineStats
import com.example.kairos.mobile.data.db.KairosDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

class ProfileActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dao = KairosDatabase.getInstance(this).kairosDao()

        setContent {
            var baseline by remember { mutableStateOf<BaselineStats?>(null) }
            var loading  by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                baseline = dao.getBaseline()
                loading  = false
            }

            ProfileScreen(
                baseline  = baseline,
                loading   = loading,
                onTerms   = { TermsActivity.launchForReading(this) },
                onBack    = { finish() }
            )
        }
    }
}

// ── Pantalla ──────────────────────────────────────────────────────────────────

@Composable
fun ProfileScreen(
    baseline: BaselineStats?,
    loading:  Boolean,
    onTerms:  () -> Unit = {},
    onBack:   () -> Unit
) {
    val Background    = Color(0xFF0A0E1A)
    val CardDark      = Color(0xFF111827)
    val KairosGreen   = Color(0xFF00E5A0)
    val KairosBlue    = Color(0xFF3B82F6)
    val KairosOrange  = Color(0xFFF59E0B)
    val TextPrimary   = Color(0xFFE2E8F0)
    val TextSecondary = Color(0xFF64748B)

    fun std(m2: Double, count: Int): Double =
        if (count < 2) 0.0 else sqrt(m2 / (count - 1))

    val hrStd        = if (baseline != null) std(baseline.hrM2, baseline.hrCount) else 0.0
    val hrvStd       = if (baseline != null) std(baseline.hrvM2, baseline.hrvCount) else 0.0
    val isCalibrated = (baseline?.calibrationWindows ?: 0) >= 3

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // ── Header con ícono de términos ──────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text       = "Mi perfil fisiológico",
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color      = TextPrimary,
                    modifier   = Modifier.align(Alignment.CenterStart)
                )
                IconButton(
                    onClick  = onTerms,
                    modifier = Modifier.align(Alignment.TopEnd).size(36.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Default.Info,
                        contentDescription = "Términos de uso",
                        tint               = Color.White,
                        modifier           = Modifier.size(20.dp)
                    )
                }
            }

            Text(
                text      = "KAIROS usa estos valores como referencia personal para detectar desviaciones.",
                fontSize  = 13.sp,
                color     = TextSecondary,
                lineHeight = 18.sp
            )

            if (loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = KairosGreen)
                }
            } else if (!isCalibrated || baseline == null) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(KairosOrange.copy(alpha = 0.1f))
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⚠️ Perfil no calibrado", fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold, color = KairosOrange)
                        Text(
                            "Usá el reloj en reposo durante unos minutos para que KAIROS aprenda tu frecuencia cardíaca basal.",
                            fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(KairosGreen.copy(alpha = 0.08f))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("✓ Perfil calibrado", fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold, color = KairosGreen)
                            Text(
                                text     = "Actualizado: ${formatDate(baseline.updatedAt)}",
                                fontSize = 11.sp,
                                color    = TextSecondary
                            )
                        }
                        Text(
                            text       = "${baseline.calibrationWindows}/3",
                            fontSize   = 13.sp,
                            color      = KairosGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text("Frecuencia cardíaca basal", fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, color = TextPrimary)

                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BaselineMetricCard(Modifier.weight(1f), "Media",
                        "%.1f".format(baseline.hrMean), "BPM", KairosBlue, CardDark, TextPrimary, TextSecondary)
                    BaselineMetricCard(Modifier.weight(1f), "Desv. estándar",
                        "%.1f".format(hrStd), "BPM", KairosBlue, CardDark, TextPrimary, TextSecondary)
                    BaselineMetricCard(Modifier.weight(1f), "Muestras",
                        "${baseline.hrCount}", "", KairosBlue, CardDark, TextPrimary, TextSecondary)
                }

                Text("Variabilidad cardíaca (RMSSD)", fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, color = TextPrimary)

                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BaselineMetricCard(Modifier.weight(1f), "Media",
                        "%.1f".format(baseline.hrvMean), "ms", KairosGreen, CardDark, TextPrimary, TextSecondary)
                    BaselineMetricCard(Modifier.weight(1f), "Desv. estándar",
                        "%.1f".format(hrvStd), "ms", KairosGreen, CardDark, TextPrimary, TextSecondary)
                    BaselineMetricCard(Modifier.weight(1f), "Muestras",
                        "${baseline.hrvCount}", "", KairosGreen, CardDark, TextPrimary, TextSecondary)
                }

                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardDark)
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Comparación con población de referencia",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(
                            "Los valores de referencia provienen del dataset WESAD (15 sujetos adultos en reposo).",
                            fontSize = 11.sp, color = TextSecondary, lineHeight = 16.sp
                        )
                        Divider(color = TextSecondary.copy(alpha = 0.15f))
                        ComparisonRow("HR media", "%.1f BPM".format(baseline.hrMean),
                            "72.4 BPM", TextPrimary, TextSecondary)
                        ComparisonRow("HR desv. estándar", "%.1f BPM".format(hrStd),
                            "8.0 BPM", TextPrimary, TextSecondary)
                        ComparisonRow("RMSSD media", "%.1f ms".format(baseline.hrvMean),
                            "51.0 ms", TextPrimary, TextSecondary)
                        ComparisonRow("RMSSD desv. estándar", "%.1f ms".format(hrvStd),
                            "12.0 ms", TextPrimary, TextSecondary)
                    }
                }

                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(KairosBlue.copy(alpha = 0.08f))
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("¿Cómo usa KAIROS estos valores?",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = KairosBlue)
                        Text(
                            "El sistema calcula qué tan lejos está tu frecuencia cardíaca y RMSSD actuales de tu baseline personal. " +
                                    "Si la desviación supera cierto umbral determinado por el modelo de Machine Learning, KAIROS detecta una posible crisis.",
                            fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("← Volver", fontSize = 13.sp, color = TextSecondary)
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ── Componentes ───────────────────────────────────────────────────────────────

@Composable
fun BaselineMetricCard(
    modifier:  Modifier,
    label:     String,
    value:     String,
    unit:      String,
    color:     Color,
    cardColor: Color,
    textColor: Color,
    subColor:  Color
) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(12.dp))
            .background(cardColor).padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, fontSize = 10.sp, color = subColor,
                textAlign = TextAlign.Center, lineHeight = 13.sp)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
            if (unit.isNotEmpty()) Text(unit, fontSize = 10.sp, color = subColor)
        }
    }
}

@Composable
fun ComparisonRow(
    label:     String,
    personal:  String,
    reference: String,
    textColor: Color,
    subColor:  Color
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = subColor, modifier = Modifier.weight(1f))
        Text(personal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = textColor)
        Text("  vs  ", fontSize = 11.sp, color = subColor)
        Text(reference, fontSize = 12.sp, color = subColor)
    }
}

private fun formatDate(epochMs: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(epochMs))
}