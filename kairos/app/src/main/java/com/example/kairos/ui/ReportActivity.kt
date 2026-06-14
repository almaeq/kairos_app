package com.example.kairos.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.tv.material3.OutlinedButtonDefaults
import com.example.kairos.mobile.data.db.BaselineStats
import com.example.kairos.mobile.data.db.CrisisEpisode
import com.example.kairos.mobile.data.db.KairosDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

/**
 * Activity que genera y muestra el reporte semanal de bienestar del usuario.
 *
 * Carga los episodios de los últimos 7 días y el baseline personal desde Room,
 * y los presenta tanto en una vista nativa de Compose como en un reporte HTML
 * exportable como PDF desde el navegador del dispositivo.
 *
 * **Modos de exportación:**
 * - HTML via browser → el usuario puede guardar como PDF desde el menú del navegador.
 * - Texto plano via `Intent.ACTION_SEND` → para compartir por WhatsApp, mail, etc.
 */
class ReportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = KairosDatabase.getInstance(this).kairosDao()

        setContent {
            var episodes by remember { mutableStateOf<List<CrisisEpisode>>(emptyList()) }
            var baseline by remember { mutableStateOf<BaselineStats?>(null) }
            var loading  by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                // Filtramos los episodios de los últimos 7 días en memoria
                // porque getRecentEpisodes() devuelve los últimos 50 sin filtro temporal
                val now     = System.currentTimeMillis()
                val weekAgo = now - 7 * 24 * 60 * 60 * 1000L
                episodes = dao.getRecentEpisodes().filter { it.timestamp >= weekAgo }
                baseline = dao.getBaseline()
                loading  = false
            }

            ReportScreen(
                episodes = episodes,
                baseline = baseline,
                loading  = loading,
                onBack   = { finish() }
            )
        }
    }
}

/**
 * Pantalla del reporte semanal de bienestar.
 *
 * Muestra un resumen de los episodios de la última semana con métricas agregadas
 * (total, confirmadas, canceladas, HR media, duración media) y el perfil fisiológico
 * personal si el usuario está calibrado.
 *
 * **Exportación HTML:**
 * [buildHtml] genera un documento HTML completo con estilos inline, guardado en
 * el directorio de caché y abierto via [FileProvider] en el navegador del dispositivo.
 * Desde el navegador, el usuario puede usar la función de impresión para guardar como PDF.
 *
 * @param episodes Lista de episodios de los últimos 7 días.
 * @param baseline Parámetros de la línea de base personal, o `null` si no existe.
 * @param loading `true` mientras se realizan las consultas a Room.
 * @param onBack Callback para cerrar la pantalla.
 */
@Composable
fun ReportScreen(
    episodes: List<CrisisEpisode>,
    baseline: BaselineStats?,
    loading:  Boolean,
    onBack:   () -> Unit
) {
    val context = LocalContext.current

    val Background    = Color(0xFF0A0E1A)
    val CardDark      = Color(0xFF111827)
    val KairosGreen   = Color(0xFF00E5A0)
    val KairosBlue    = Color(0xFF3B82F6)
    val KairosOrange  = Color(0xFFF59E0B)
    val KairosRed     = Color(0xFFEF4444)
    val TextPrimary   = Color(0xFFE2E8F0)
    val TextSecondary = Color(0xFF94A3B8)

    // Derivamos la desviación estándar desde M2 (fórmula de Welford)
    fun std(m2: Double, count: Int) =
        if (count < 2) 0.0 else sqrt(m2 / (count - 1))

    val confirmed = episodes.count { it.wasConfirmed }
    val cancelled = episodes.count { !it.wasConfirmed }
    val avgHr     = if (episodes.isEmpty()) 0.0 else episodes.map { it.hrBpm }.average()
    val avgDur    = if (episodes.isEmpty()) 0 else episodes.map { it.durationSeconds }.average().toInt()
    val hrStd     = if (baseline != null) std(baseline.hrM2, baseline.hrCount) else 0.0
    val hrvStd    = if (baseline != null) std(baseline.hrvM2, baseline.hrvCount) else 0.0

    val today   = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
    val weekAgo = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        .format(Date(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L))

    /**
     * Genera el documento HTML completo del reporte para exportación.
     *
     * Incluye cabecera con período, métricas agregadas, tabla de episodios
     * y perfil fisiológico personal (si disponible). El HTML usa estilos inline
     * y una media query `@media print` para optimizar la versión PDF.
     *
     * @return String con el HTML completo listo para escribir en disco.
     */
    fun buildHtml(): String {
        val episodesRows = if (episodes.isEmpty()) {
            "<tr><td colspan='4' style='text-align:center;color:#64748B;padding:20px'>Sin episodios en los últimos 7 días</td></tr>"
        } else {
            episodes.joinToString("") { ep ->
                val color    = if (ep.wasConfirmed) "#EF4444" else "#00E5A0"
                val estado   = if (ep.wasConfirmed) "Confirmada" else "Cancelada"
                val duracion = if (ep.durationSeconds < 60) "${ep.durationSeconds}s"
                else "${ep.durationSeconds / 60}m ${ep.durationSeconds % 60}s"
                """
                <tr>
                    <td>${formatDateTime(ep.timestamp)}</td>
                    <td>${"%.0f".format(ep.hrBpm)} BPM</td>
                    <td>${"%.1f".format(ep.rmssdMs)} ms</td>
                    <td><span style='color:$color;font-weight:600'>$estado</span> · $duracion</td>
                </tr>
                """.trimIndent()
            }
        }

        val baselineSection = if (baseline != null && baseline.calibrationWindows >= 3) """
            <div class="section">
                <h2>🫀 Perfil Fisiológico Personal</h2>
                <table>
                    <tr><th>Métrica</th><th>Valor personal</th><th>Referencia WESAD</th></tr>
                    <tr>
                        <td>HR basal</td>
                        <td><strong>${"%.1f".format(baseline.hrMean)} BPM (±${"%.1f".format(hrStd)})</strong></td>
                        <td>72.4 BPM (±8.0)</td>
                    </tr>
                    <tr>
                        <td>RMSSD basal</td>
                        <td><strong>${"%.1f".format(baseline.hrvMean)} ms (±${"%.1f".format(hrvStd)})</strong></td>
                        <td>51.0 ms (±12.0)</td>
                    </tr>
                    <tr>
                        <td>Calibrado con</td>
                        <td colspan='2'>${baseline.calibrationWindows} ventanas de 60 segundos en reposo</td>
                    </tr>
                </table>
                <p style='font-size:12px;color:#64748B;margin-top:8px'>
                    Los valores personales son la referencia que KAIROS usa para detectar desviaciones.
                    La comparación con WESAD es orientativa.
                </p>
            </div>
        """.trimIndent() else ""

        return """
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>KAIROS — Reporte Semanal</title>
<style>
  body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
    background: #f8fafc; color: #1e293b; margin: 0; padding: 20px; font-size: 14px;
  }
  .header {
    background: linear-gradient(135deg, #1e3a5f, #2d6a9f);
    color: white; padding: 28px; border-radius: 12px;
    text-align: center; margin-bottom: 24px;
  }
  .header h1 { margin: 0; font-size: 32px; letter-spacing: 6px; }
  .header .subtitle { opacity: 0.7; margin: 4px 0 16px; font-size: 13px; }
  .metrics {
    display: grid; grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
    gap: 12px; margin-bottom: 24px;
  }
  .metric-card {
    background: white; border-radius: 10px; padding: 16px;
    text-align: center; box-shadow: 0 1px 4px rgba(0,0,0,0.08);
  }
  .metric-card .value { font-size: 24px; font-weight: 700; }
  .metric-card .label { font-size: 11px; color: #64748B; margin-top: 4px; }
  .section {
    background: white; border-radius: 12px; padding: 20px;
    margin-bottom: 20px; box-shadow: 0 1px 4px rgba(0,0,0,0.08);
  }
  .section h2 { margin: 0 0 16px; font-size: 16px; color: #1e293b; }
  table { width: 100%; border-collapse: collapse; }
  th {
    background: #f1f5f9; padding: 10px 12px; text-align: left;
    font-size: 12px; color: #64748B; font-weight: 600;
    text-transform: uppercase; letter-spacing: 0.5px;
  }
  td { padding: 10px 12px; border-bottom: 1px solid #f1f5f9; }
  tr:last-child td { border-bottom: none; }
  .footer {
    text-align: center; color: #94a3b8; font-size: 11px;
    margin-top: 24px; padding: 16px; border-top: 1px solid #e2e8f0;
  }
  .print-hint {
    background: #eff6ff; border: 1px solid #bfdbfe; border-radius: 8px;
    padding: 12px 16px; margin-bottom: 20px; font-size: 13px; color: #1d4ed8;
  }
  @media print { .print-hint { display: none; } body { background: white; padding: 0; } }
</style>
</head>
<body>
<div class="header">
  <h1>KAIROS</h1>
  <div class="subtitle">The Right Time</div>
  <div>Reporte de bienestar semanal</div>
  <div class="period">$weekAgo — $today</div>
</div>
<div class="print-hint">
  💡 Para guardar como PDF: tocá los 3 puntos del browser → <strong>Imprimir</strong> → <strong>Guardar como PDF</strong>
</div>
<div class="metrics">
  <div class="metric-card">
    <div class="value" style="color:#3B82F6">${episodes.size}</div>
    <div class="label">Total episodios</div>
  </div>
  <div class="metric-card">
    <div class="value" style="color:#EF4444">$confirmed</div>
    <div class="label">Confirmadas</div>
  </div>
  <div class="metric-card">
    <div class="value" style="color:#00E5A0">$cancelled</div>
    <div class="label">Canceladas</div>
  </div>
  ${if (episodes.isNotEmpty()) """
  <div class="metric-card">
    <div class="value" style="color:#F59E0B">${"%.0f".format(avgHr)}</div>
    <div class="label">HR media (BPM)</div>
  </div>
  <div class="metric-card">
    <div class="value" style="color:#F59E0B">${if (avgDur < 60) "${avgDur}s" else "${avgDur/60}m"}</div>
    <div class="label">Duración media</div>
  </div>
  """.trimIndent() else ""}
</div>
<div class="section">
  <h2>📋 Detalle de Episodios</h2>
  <table>
    <tr><th>Fecha y hora</th><th>HR</th><th>RMSSD</th><th>Estado</th></tr>
    $episodesRows
  </table>
</div>
$baselineSection
<div class="footer">
  ⚠️ Este reporte es informativo y no constituye un diagnóstico médico.<br>
  Generado por KAIROS — The Right Time · $today
</div>
</body>
</html>
        """.trimIndent()
    }

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick  = onBack,
                    modifier = Modifier.align(Alignment.CenterStart).size(36.dp)
                ) {
                    Text("←", fontSize = 20.sp, color = TextSecondary)
                }
                Text(
                    text       = "Reporte semanal",
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color      = TextPrimary,
                    modifier   = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 44.dp)
                )
            }

            // Cabecera del reporte con período cubierto
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(KairosBlue.copy(alpha = 0.15f))
                    .padding(20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier            = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "KAIROS",
                        fontSize      = 24.sp,
                        fontWeight    = FontWeight.Bold,
                        color         = KairosBlue,
                        letterSpacing = 6.sp
                    )
                    Text("The Right Time", fontSize = 12.sp, color = TextSecondary, letterSpacing = 2.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Reporte de bienestar semanal", fontSize = 14.sp,
                        color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("$weekAgo — $today", fontSize = 12.sp, color = TextSecondary)
                }
            }

            if (loading) {
                Box(
                    modifier         = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = KairosGreen)
                }
            } else {
                // ── Métricas de resumen ───────────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ReportMetricCard(Modifier.weight(1f), "${episodes.size}",
                        "Total", KairosBlue, CardDark, TextPrimary, TextSecondary)
                    ReportMetricCard(Modifier.weight(1f), "$confirmed",
                        "Confirmadas", KairosGreen, CardDark, TextPrimary, TextSecondary)
                    ReportMetricCard(Modifier.weight(1f), "$cancelled",
                        "Canceladas", KairosRed, CardDark, TextPrimary, TextSecondary)
                }

                if (episodes.isNotEmpty()) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ReportMetricCard(Modifier.weight(1f), "%.0f BPM".format(avgHr),
                            "HR media en crisis", KairosOrange, CardDark, TextPrimary, TextSecondary)
                        ReportMetricCard(Modifier.weight(1f),
                            if (avgDur < 60) "${avgDur}s" else "${avgDur/60}m ${avgDur%60}s",
                            "Duración media", KairosOrange, CardDark, TextPrimary, TextSecondary)
                    }

                    Text(
                        "Detalle de episodios",
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = TextPrimary
                    )

                    // Lista de episodios con indicador de color por estado
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardDark)
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            episodes.forEach { ep ->
                                Row(
                                    modifier              = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment     = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment     = Alignment.CenterVertically
                                    ) {
                                        // Punto de color: verde = confirmada, rojo = cancelada
                                        Box(
                                            modifier = Modifier.size(8.dp).background(
                                                if (ep.wasConfirmed) KairosGreen else KairosRed,
                                                CircleShape
                                            )
                                        )
                                        Text(formatDateTime(ep.timestamp),
                                            fontSize = 12.sp, color = TextPrimary)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text("%.0f BPM".format(ep.hrBpm),
                                            fontSize = 11.sp, color = TextSecondary)
                                        Text(
                                            if (ep.wasConfirmed) "Confirmada" else "Cancelada",
                                            fontSize = 11.sp,
                                            color    = if (ep.wasConfirmed) KairosGreen else KairosRed
                                        )
                                    }
                                }
                                if (ep != episodes.last()) {
                                    Divider(color = TextSecondary.copy(alpha = 0.1f))
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardDark)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Sin episodios en los últimos 7 días ✓",
                            fontSize  = 13.sp,
                            color     = KairosGreen,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // ── Perfil fisiológico (solo si calibrado) ────────────────────
                if (baseline != null && baseline.calibrationWindows >= 3) {
                    Text(
                        "Perfil fisiológico personal",
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = TextPrimary
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardDark)
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReportBaselineRow("HR basal",
                                "%.1f BPM (±%.1f)".format(baseline.hrMean, hrStd),
                                TextPrimary, TextSecondary)
                            Divider(color = TextSecondary.copy(alpha = 0.1f))
                            ReportBaselineRow("RMSSD basal",
                                "%.1f ms (±%.1f)".format(baseline.hrvMean, hrvStd),
                                TextPrimary, TextSecondary)
                            Divider(color = TextSecondary.copy(alpha = 0.1f))
                            ReportBaselineRow("Calibrado con",
                                "${baseline.calibrationWindows} ventanas de 60s",
                                TextPrimary, TextSecondary)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(TextSecondary.copy(alpha = 0.05f))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text       = "Este reporte es informativo y no constituye un diagnóstico médico.\nGenerado por KAIROS — The Right Time · $today",
                        fontSize   = 10.sp,
                        color      = TextSecondary,
                        textAlign  = TextAlign.Center,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Exporta el reporte como HTML, lo guarda en caché y lo abre en el browser
            // El usuario puede usar la función de impresión del browser para guardar como PDF
            Button(
                onClick = {
                    val html = buildHtml()
                    val file = File(context.cacheDir, "kairos_reporte.html")
                    file.writeText(html)
                    val uri = FileProvider.getUriForFile(
                        context, "${context.packageName}.provider", file)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "text/html")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                },
                enabled  = !loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = KairosBlue),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "🌐 Abrir reporte en browser (→ PDF)",
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = Color.White
                )
            }

            // Comparte el reporte como texto plano via intent chooser (WhatsApp, mail, etc.)
            OutlinedButton(
                onClick = {
                    val texto = buildString {
                        appendLine("🏥 KAIROS — Reporte Semanal · $today")
                        appendLine("Período: $weekAgo – $today")
                        appendLine()
                        appendLine("Episodios: ${episodes.size} total · $confirmed confirmadas · $cancelled canceladas")
                        if (episodes.isNotEmpty()) {
                            appendLine("HR media en crisis: ${"%.0f".format(avgHr)} BPM")
                            appendLine()
                            episodes.forEach { ep ->
                                val estado = if (ep.wasConfirmed) "✓" else "✗"
                                appendLine("$estado ${formatDateTime(ep.timestamp)} — ${"%.0f".format(ep.hrBpm)} BPM")
                            }
                        }
                        if (baseline != null && baseline.calibrationWindows >= 3) {
                            appendLine()
                            appendLine("HR basal: ${"%.1f".format(baseline.hrMean)} BPM · RMSSD: ${"%.1f".format(baseline.hrvMean)} ms")
                        }
                        appendLine()
                        appendLine("Generado por KAIROS — The Right Time")
                    }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, texto)
                    }
                    context.startActivity(Intent.createChooser(intent, "Compartir reporte"))
                },
                enabled  = !loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = KairosGreen),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "📤 Compartir como texto (WhatsApp, mail...)",
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("← Volver", fontSize = 13.sp, color = TextSecondary)
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Tarjeta de métrica para el reporte con valor numérico y label.
 *
 * @param modifier Modifier externo para control de tamaño.
 * @param value Valor formateado a mostrar en grande.
 * @param label Descripción del valor.
 * @param color Color de acento para el valor.
 * @param cardColor Color de fondo de la tarjeta.
 * @param textColor Color del valor (no usado directamente, reservado para extensión).
 * @param subColor Color del label.
 */
@Composable
fun ReportMetricCard(
    modifier:  Modifier,
    value:     String,
    label:     String,
    color:     Color,
    cardColor: Color,
    textColor: Color,
    subColor:  Color
) {
    Box(
        modifier         = modifier.clip(RoundedCornerShape(12.dp)).background(cardColor).padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 10.sp, color = subColor,
                textAlign = TextAlign.Center, lineHeight = 13.sp)
        }
    }
}

/**
 * Fila de datos del perfil fisiológico en el reporte.
 *
 * @param label Nombre del dato (por ejemplo: "HR basal", "RMSSD basal").
 * @param value Valor formateado con desviación estándar.
 * @param textColor Color del valor.
 * @param subColor Color del label.
 */
@Composable
fun ReportBaselineRow(label: String, value: String, textColor: Color, subColor: Color) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = subColor)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = textColor)
    }
}

/**
 * Formatea un timestamp en milisegundos a una cadena de fecha y hora compacta.
 *
 * @param epochMs Timestamp en milisegundos (epoch).
 * @return Fecha formateada como "dd/MM HH:mm" en el locale del dispositivo.
 */
private fun formatDateTime(epochMs: Long): String =
    SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(epochMs))