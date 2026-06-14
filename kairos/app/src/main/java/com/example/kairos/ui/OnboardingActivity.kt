package com.example.kairos.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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

/**
 * Activity que muestra el flujo de onboarding de 4 páginas la primera vez
 * que el usuario abre KAIROS.
 *
 * El estado de completado se persiste en SharedPreferences via [hasCompleted]
 * y [markCompleted]. Al finalizar o saltar el onboarding, navega a [TermsActivity]
 * para que el usuario acepte los términos de uso antes de acceder a la app.
 *
 * El onboarding solo se muestra una vez — [MainActivity] verifica [hasCompleted]
 * al iniciar y redirige aquí si es la primera apertura.
 */
class OnboardingActivity : ComponentActivity() {

    companion object {
        private const val PREFS_NAME    = "kairos_onboarding"
        private const val KEY_COMPLETED = "onboarding_completed"

        /**
         * Verifica si el usuario ya completó o saltó el onboarding.
         *
         * @param context Contexto para acceder a SharedPreferences.
         * @return `true` si el onboarding ya fue visto, `false` si es la primera apertura.
         */
        fun hasCompleted(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_COMPLETED, false)

        /**
         * Marca el onboarding como completado en SharedPreferences.
         * Se invoca tanto al finalizar la última página como al presionar "Saltar".
         *
         * @param context Contexto para acceder a SharedPreferences.
         */
        fun markCompleted(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_COMPLETED, true).apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OnboardingScreen(
                onFinish = {
                    markCompleted(this)
                    // Navegamos a TermsActivity para aceptación de términos antes de la app
                    TermsActivity.launchForOnboarding(this)
                    finish()
                }
            )
        }
    }
}

/**
 * Modelo de datos de una página del onboarding.
 *
 * @property emoji Ícono grande mostrado en el círculo de acento.
 * @property title Título principal de la página.
 * @property description Subtítulo destacado con el color de acento.
 * @property detail Texto explicativo detallado mostrado en la card oscura.
 * @property accentColor Color de acento único por página, aplicado al círculo,
 *           al subtítulo, al indicador de página activo y al botón "Siguiente".
 */
data class OnboardingPage(
    val emoji:       String,
    val title:       String,
    val description: String,
    val detail:      String,
    val accentColor: Color
)

/**
 * Pantalla de onboarding con 4 páginas animadas.
 *
 * Cada página presenta una funcionalidad clave de KAIROS con su propio color de acento.
 * La transición entre páginas usa slide horizontal + fade para sensación de avance lineal.
 *
 * **Indicadores de página:**
 * El indicador activo se expande a 24dp de ancho (los inactivos miden 8dp × 8dp),
 * creando un efecto de "pastilla" que señala la página actual.
 *
 * **Navegación:**
 * - "Siguiente →" avanza a la página siguiente.
 * - En la última página, el mismo botón dice "Comenzar →" e invoca [onFinish].
 * - "Saltar" (visible solo si no es la última página) invoca [onFinish] directamente.
 *
 * @param onFinish Callback invocado al completar o saltar el onboarding.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {

    val Background    = Color(0xFF0A0E1A)
    val CardDark      = Color(0xFF111827)
    val KairosGreen   = Color(0xFF00E5A0)
    val KairosBlue    = Color(0xFF3B82F6)
    val KairosOrange  = Color(0xFFF59E0B)
    val KairosRed     = Color(0xFFEF4444)
    val TextPrimary   = Color(0xFFE2E8F0)
    val TextSecondary = Color(0xFFCBD5E1)

    val pages = listOf(
        OnboardingPage(
            emoji       = "🧠",
            title       = "Bienvenida a KAIROS",
            description = "Tu compañero silencioso durante las crisis de ansiedad.",
            detail      = "KAIROS monitorea tu frecuencia cardíaca de forma continua a través de tu smartwatch y activa soporte automático cuando detecta una posible crisis — sin que tengas que hacer nada.",
            accentColor = KairosBlue
        ),
        OnboardingPage(
            emoji       = "⌚",
            title       = "Detección automática",
            description = "El reloj trabaja, vos descansás.",
            detail      = "Un modelo de Machine Learning analiza tu ritmo cardíaco y variabilidad en tiempo real. Si detecta una desviación sostenida de tu baseline personal durante 3 minutos, activa el modo crisis — con ejercicios guiados de respiración y grounding.",
            accentColor = KairosGreen
        ),
        OnboardingPage(
            emoji       = "👥",
            title       = "Tu red de apoyo",
            description = "No estás sola cuando más lo necesitás.",
            detail      = "Agregá hasta 3 contactos de confianza — familiares, amigos o tu terapeuta. Si no respondés en 30 segundos, KAIROS te asistirá para avisarles con un mensaje claro de cómo ayudarte.",
            accentColor = KairosOrange
        ),
        OnboardingPage(
            emoji       = "📊",
            title       = "Tu perfil fisiológico",
            description = "KAIROS aprende cómo sos vos, no el promedio.",
            detail      = "En los primeros minutos de uso, el sistema calibra tu frecuencia cardíaca basal y variabilidad personal. Cuanto más tiempo uses el reloj en reposo, más precisa es la detección. Podés recalibrar cuando quieras.",
            accentColor = KairosRed
        )
    )

    var currentPage by remember { mutableStateOf(0) }
    val isLast = currentPage == pages.size - 1

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(
            modifier            = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Indicadores de página: el activo se expande horizontalmente a 24dp
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                pages.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .size(if (index == currentPage) 24.dp else 8.dp, 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (index == currentPage) pages[currentPage].accentColor
                                else TextSecondary.copy(alpha = 0.3f)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Contenido animado con slide horizontal entre páginas
            AnimatedContent(
                targetState    = currentPage,
                transitionSpec = {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it } + fadeOut())
                },
                modifier = Modifier.weight(1f),
                label    = "onboarding_page"
            ) { page ->
                val data = pages[page]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier            = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(data.accentColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(data.emoji, fontSize = 52.sp)
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text       = data.title,
                        fontSize   = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color      = TextPrimary,
                        textAlign  = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text       = data.description,
                        fontSize   = 16.sp,
                        color      = data.accentColor,
                        textAlign  = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardDark)
                            .padding(20.dp)
                    ) {
                        Text(
                            text       = data.detail,
                            fontSize   = 14.sp,
                            color      = TextSecondary,
                            textAlign  = TextAlign.Center,
                            lineHeight = 21.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // El color del botón cambia con cada página para mantener coherencia visual
            Button(
                onClick = {
                    if (isLast) onFinish()
                    else currentPage++
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = pages[currentPage].accentColor
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text       = if (isLast) "Comenzar →" else "Siguiente →",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color(0xFF0A0E1A)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // "Saltar" solo visible mientras no se llegó a la última página
            if (!isLast) {
                TextButton(
                    onClick  = onFinish,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text     = "Saltar",
                        fontSize = 13.sp,
                        color    = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}