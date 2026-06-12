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
            modifier = Modifier.fillMaxSize().padding(24.dp),
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
                        .padding(start = 44.dp)  // ← espacio para la flecha
                ) {
                    Text("Registro de episodios", fontSize = 20.sp,
                        fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Últimos 50 episodios detectados.",
                        fontSize = 13.sp, color = TextSecondary)
                }
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

            if (loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = KairosRed)
                }
            } else if (episodes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardDark)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text      = "No hay episodios registrados todavía.\nKAIROS registrará automáticamente cada crisis detectada.",
                        fontSize  = 13.sp,
                        color     = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            } else {
                val confirmed = episodes.count { it.wasConfirmed }
                val cancelled = episodes.count { !it.wasConfirmed }
                val avgHr     = episodes.map { it.hrBpm }.average()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Confirmadas — tappeable, lleva al historial completo
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

            Spacer(modifier = Modifier.weight(1f))

            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("← Volver", fontSize = 13.sp, color = TextSecondary)
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

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
    val statusColor = if (episode.wasConfirmed) greenColor else redColor
    val statusText  = if (episode.wasConfirmed) "Confirmada" else "Cancelada"
    val durationText = when {
        episode.durationSeconds < 60 -> "${episode.durationSeconds}s"
        else -> "${episode.durationSeconds / 60}m ${episode.durationSeconds % 60}s"
    }

    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardColor)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(formatDateTime(episode.timestamp), fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, color = textColor)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(statusText, fontSize = 11.sp, color = statusColor,
                        fontWeight = FontWeight.SemiBold)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                EpisodeStat("HR",       "%.0f BPM".format(episode.hrBpm),  subColor, textColor)
                EpisodeStat("RMSSD",    "%.1f ms".format(episode.rmssdMs),  subColor, textColor)
                EpisodeStat("Duración", durationText,                        subColor, textColor)
            }
        }
    }
}

@Composable
fun EpisodeStat(label: String, value: String, subColor: Color, textColor: Color) {
    Column {
        Text(label, fontSize = 10.sp, color = subColor)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textColor)
    }
}

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

private fun formatDateTime(epochMs: Long): String {
    val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    return sdf.format(Date(epochMs))
}