package com.example.kairos.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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

class ConfirmedEpisodesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = KairosDatabase.getInstance(this).kairosDao()

        setContent {
            var episodes by remember { mutableStateOf<List<CrisisEpisode>>(emptyList()) }
            var loading  by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                episodes = dao.getAllConfirmedEpisodes()
                loading  = false
            }

            ConfirmedEpisodesScreen(
                episodes = episodes,
                loading  = loading,
                onBack   = { finish() }
            )
        }
    }
}

@Composable
fun ConfirmedEpisodesScreen(
    episodes: List<CrisisEpisode>,
    loading:  Boolean,
    onBack:   () -> Unit
) {
    val Background    = Color(0xFF0A0E1A)
    val CardDark      = Color(0xFF111827)
    val KairosGreen   = Color(0xFF00E5A0)
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
                Text(
                    text       = "Crisis Confirmadas",
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color      = TextPrimary,
                    modifier   = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 44.dp)  // ← deja espacio para la flecha
                )
            }
            Text(
                text     = "Historial completo — estos episodios nunca se borran.",
                fontSize = 13.sp,
                color    = TextSecondary
            )

            if (loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = KairosGreen)
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
                        text      = "No hay crisis confirmadas todavía.",
                        fontSize  = 13.sp,
                        color     = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Resumen
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(KairosGreen.copy(alpha = 0.08f))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total de crisis confirmadas",
                            fontSize = 13.sp, color = TextSecondary)
                        Text("${episodes.size}",
                            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = KairosGreen)
                    }
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(episodes) { episode ->
                        ConfirmedEpisodeCard(
                            episode   = episode,
                            cardColor = CardDark,
                            textColor = TextPrimary,
                            subColor  = TextSecondary,
                            redColor  = KairosGreen
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
fun ConfirmedEpisodeCard(
    episode:   CrisisEpisode,
    cardColor: Color,
    textColor: Color,
    subColor:  Color,
    redColor:  Color
) {
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
                Text(
                    text       = formatConfirmedDateTime(episode.timestamp),
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = textColor
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(redColor.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text("Confirmada", fontSize = 11.sp,
                        color = redColor, fontWeight = FontWeight.SemiBold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ConfirmedEpisodeStat("HR", "%.0f BPM".format(episode.hrBpm), subColor, textColor)
                ConfirmedEpisodeStat("RMSSD", "%.1f ms".format(episode.rmssdMs), subColor, textColor)
                ConfirmedEpisodeStat("Duración", durationText, subColor, textColor)
            }
        }
    }
}

@Composable
fun ConfirmedEpisodeStat(label: String, value: String, subColor: Color, textColor: Color) {
    Column {
        Text(label, fontSize = 10.sp, color = subColor)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textColor)
    }
}

private fun formatConfirmedDateTime(epochMs: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(epochMs))
}