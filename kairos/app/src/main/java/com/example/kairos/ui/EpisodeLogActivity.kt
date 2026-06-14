package com.example.kairos.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.kairos.mobile.data.db.CrisisEpisode
import com.example.kairos.mobile.data.db.KairosDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Activity que muestra el registro de los últimos 50 episodios detectados por KAIROS,
 * incluyendo tanto los confirmados como los cancelados (falsos positivos).
 *
 * Desde esta pantalla el usuario puede:
 * - Ver el resumen estadístico de episodios (confirmados, cancelados, HR media).
 * - Navegar al historial completo de crisis confirmadas ([ConfirmedEpisodesActivity]).
 * - Generar un informe exportable ([ReportActivity]).
 */
class EpisodeLogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = KairosDatabase.getInstance(this).kairosDao()

        setContent {
            var episodes by remember { mutableStateOf<List<CrisisEpisode>>(emptyList()) }
            var loading  by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                episodes = dao.getRecentEpisodes()
                loading  = false
            }

            EpisodeLogScreen(
                episodes    = episodes,
                loading     = loading,
                onConfirmed = { startActivity(Intent(this@EpisodeLogActivity, ConfirmedEpisodesActivity::class.java)) },
                onReport    = { startActivity(Intent(this@EpisodeLogActivity, ReportActivity::class.java)) },
                onBack      = { finish() }
            )
        }
    }
}

/**
 * Pantalla del registro de episodios.
 *
 * Muestra tres estados según [loading] y [episodes]:
 * - **Cargando:** indicador circular.
 * - **Vacío:** mensaje informativo.
 * - **Con datos:** tarjetas de resumen + lista de episodios con métricas.
 *
 * La tarjeta "Confirmadas" es tappeable y navega a [ConfirmedEpisodesActivity].
 * El botón "Informe →" se deshabilita si no hay episodios.
 *
 * @param episodes Lista de hasta 50 episodios recientes ordenados por fecha descendente.
 * @param loading `true` mientras se realiza la consulta a Room.
 * @param onConfirmed Callback para navegar al historial de crisis confirmadas.
 * @param onReport Callback para navegar a la pantalla de generación de informes.
 * @param onBack Callback para cerrar la pantalla.
 */
@Composable
fun EpisodeLogScreen(
    episodes:    List<CrisisEpisode>,
    loading:     Boolean,
    onConfirmed: () -> Unit = {},
    onReport:    () -> Unit = {},
    onBack:      () -> Unit
) {
    val Background    = Color(0xFF0A0E1A)
    val CardDark      = Color(0xFF111827)
    val KairosRed     = Color(0xFFEF4444)
    val KairosGreen   = Color(0xFF00E5A0)
    val KairosBlue    = Color(0xFF3B82F6)
    val TextPrimary   = Color(0xFFE2E8F0)
    val TextSecondary = Color(0xFF94A3B8)

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        Column(
            modifier            = Modifier.fillMaxSize().padding(24.dp),
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
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 44.dp)
                ) {
                    Text(
                        "Registro de episodios",
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color      = TextPrimary
                    )
                    Text(
                        "Últimos 50 episodios detectados.",
                        fontSize = 13.sp,
                        color    = TextSecondary
                    )
                }
                // Botón de informe deshabilitado si no hay episodios para exportar
                TextButton(
                    onClick  = onReport,
                    enabled  = episodes.isNotEmpty(),
                    modifier = Modifier.align(Alignment.TopEnd),
                    colors   = ButtonDefaults.textButtonColors(
                        contentColor         = KairosBlue,
                        disabledContentColor = TextSecondary.copy(alpha = 0.4f)
                    )
                ) {
                    Text("Informe →", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            when {
                loading -> {
                    Box(
                        modifier         = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = KairosRed)
                    }
                }

                episodes.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardDark)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text       = "No hay episodios registrados todavía.\nKAIROS registrará automáticamente cada crisis detectada.",
                            fontSize   = 13.sp,
                            color      = TextSecondary,
                            textAlign  = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }

                else -> {
                    // Métricas de resumen calculadas sobre los episodios disponibles
                    val confirmed = episodes.count { it.wasConfirmed }
                    val cancelled = episodes.count { !it.wasConfirmed }
                    val avgHr     = episodes.map { it.hrBpm }.average()

                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // La tarjeta "Confirmadas" es tappeable — navega al historial completo
                        SummaryCard(
                            modifier  = Modifier.weight(1f).clickable { onConfirmed() },
                            value     = "$confirmed",
                            label     = "Confirmadas ↗",
                            color     = KairosGreen,
                            cardColor = CardDark,
                            textColor = TextPrimary,
                            subColor  = TextSecondary
                        )
                        SummaryCard(
                            modifier  = Modifier.weight(1f),
                            value     = "$cancelled",
                            label     = "Canceladas",
                            color     = KairosRed,
                            cardColor = CardDark,
                            textColor = TextPrimary,
                            subColor  = TextSecondary
                        )
                        SummaryCard(
                            modifier  = Modifier.weight(1f),
                            value     = "%.0f".format(avgHr),
                            label     = "HR media",
                            color     = KairosBlue,
                            cardColor = CardDark,
                            textColor = TextPrimary,
                            subColor  = TextSecondary
                        )
                    }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(episodes) { episode ->
                            EpisodeCard(
                                episode     = episode,
                                cardColor   = CardDark,
                                textColor   = TextPrimary,
                                subColor    = TextSecondary,
                                redColor    = KairosRed,
                                greenColor  = KairosGreen,
                                orangeColor = KairosGreen
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("← Volver", fontSize = 13.sp, color = TextSecondary)
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Tarjeta que muestra las métricas de un episodio individual.
 *
 * El color del badge de estado ([statusColor]) varía según si el episodio
 * fue confirmado (verde) o cancelado (rojo).
 *
 * @param episode Episodio a mostrar.
 * @param cardColor Color de fondo de la tarjeta.
 * @param textColor Color del texto principal.
 * @param subColor Color del texto secundario (labels de métricas).
 * @param redColor Color para episodios cancelados.
 * @param greenColor Color para episodios confirmados.
 * @param orangeColor Color alternativo (actualmente no usado, reservado para estados futuros).
 */
@Composable
fun EpisodeCard(
    episode:     CrisisEpisode,
    cardColor:   Color,
    textColor:   Color,
    subColor:    Color,
    redColor:    Color,
    greenColor:  Color,
    orangeColor: Color
) {
    val statusColor  = if (episode.wasConfirmed) greenColor else redColor
    val statusText   = if (episode.wasConfirmed) "Confirmada" else "Cancelada"
    val durationText = when {
        episode.durationSeconds < 60 -> "${episode.durationSeconds}s"
        else -> "${episode.durationSeconds / 60}m ${episode.durationSeconds % 60}s"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardColor)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    formatDateTime(episode.timestamp),
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = textColor
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        statusText,
                        fontSize   = 11.sp,
                        color      = statusColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                EpisodeStat("HR",       "%.0f BPM".format(episode.hrBpm),  subColor, textColor)
                EpisodeStat("RMSSD",    "%.1f ms".format(episode.rmssdMs), subColor, textColor)
                EpisodeStat("Duración", durationText,                       subColor, textColor)
            }
        }
    }
}

/**
 * Composable auxiliar que muestra una métrica con su label y valor.
 *
 * @param label Nombre de la métrica.
 * @param value Valor formateado de la métrica.
 * @param subColor Color del label.
 * @param textColor Color del valor.
 */
@Composable
fun EpisodeStat(label: String, value: String, subColor: Color, textColor: Color) {
    Column {
        Text(label, fontSize = 10.sp, color = subColor)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textColor)
    }
}

/**
 * Tarjeta de resumen con un valor numérico destacado y su label.
 *
 * Se usa en la fila de métricas globales (confirmadas, canceladas, HR media).
 * Acepta un [modifier] externo para permitir que el llamador agregue
 * comportamiento adicional (por ejemplo, `clickable` en la tarjeta de confirmadas).
 *
 * @param modifier Modifier aplicado al contenedor de la tarjeta.
 * @param value Valor numérico a mostrar en grande.
 * @param label Descripción del valor mostrada debajo.
 * @param color Color de acento para el valor numérico.
 * @param cardColor Color de fondo de la tarjeta.
 * @param textColor Color del texto principal (no usado actualmente en este composable).
 * @param subColor Color del label.
 */
@Composable
fun SummaryCard(
    modifier:  Modifier,
    value:     String,
    label:     String,
    color:     Color,
    cardColor: Color,
    textColor: Color,
    subColor:  Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(cardColor)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 10.sp, color = subColor, textAlign = TextAlign.Center)
        }
    }
}

/**
 * Formatea un timestamp en milisegundos a una cadena de fecha y hora compacta.
 *
 * Usa el formato "dd/MM HH:mm" (sin año) para ahorrar espacio en las tarjetas
 * de la lista de episodios.
 *
 * @param epochMs Timestamp en milisegundos (epoch).
 * @return Fecha formateada como "dd/MM HH:mm" en el locale del dispositivo.
 */
private fun formatDateTime(epochMs: Long): String {
    val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    return sdf.format(Date(epochMs))
}