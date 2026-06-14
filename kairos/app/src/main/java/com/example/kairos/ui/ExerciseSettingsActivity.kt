package com.example.kairos.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.kairos.mobile.ExercisePreference
import com.example.kairos.mobile.ExercisePreferenceManager
import kotlinx.coroutines.launch

/**
 * Activity para configurar qué ejercicio de intervención se activa durante una crisis.
 *
 * Carga la preferencia actual desde SharedPreferences al iniciar y, al guardar,
 * persiste la nueva preferencia localmente y la sincroniza al reloj via
 * [ExercisePreferenceManager.syncToWatch] para que el Modo Crisis en Wear OS
 * ejecute el ejercicio correcto sin necesidad de conexión al teléfono.
 */
class ExerciseSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cargamos la preferencia actual para mostrarla seleccionada al abrir la pantalla
        val current = ExercisePreferenceManager.load(this)

        setContent {
            var selected by remember { mutableStateOf(current) }
            // Flag que controla la visibilidad del banner de confirmación
            var saved by remember { mutableStateOf(false) }

            ExerciseSettingsScreen(
                selected = selected,
                saved    = saved,
                onSelect = { selected = it; saved = false },
                onSave   = {
                    lifecycleScope.launch {
                        ExercisePreferenceManager.save(this@ExerciseSettingsActivity, selected)
                        ExercisePreferenceManager.syncToWatch(this@ExerciseSettingsActivity, selected)
                        saved = true
                    }
                },
                onBack = { finish() }
            )
        }
    }
}

/**
 * Pantalla de configuración del ejercicio de intervención.
 *
 * Muestra las tres opciones de [ExercisePreference] como tarjetas seleccionables.
 * La opción [ExercisePreference.BOTH] incluye un badge "Recomendado".
 * Al guardar, muestra un banner de confirmación y sincroniza la preferencia al reloj.
 *
 * El flag [saved] se resetea a `false` cuando el usuario cambia la selección,
 * para que el banner desaparezca e indique que hay cambios pendientes de guardar.
 *
 * @param selected Preferencia actualmente seleccionada en la UI.
 * @param saved `true` si la preferencia fue guardada y sincronizada exitosamente.
 * @param onSelect Callback invocado cuando el usuario toca una opción.
 * @param onSave Callback invocado cuando el usuario presiona "Guardar preferencia".
 * @param onBack Callback para cerrar la pantalla.
 */
@Composable
fun ExerciseSettingsScreen(
    selected: ExercisePreference,
    saved:    Boolean,
    onSelect: (ExercisePreference) -> Unit,
    onSave:   () -> Unit,
    onBack:   () -> Unit
) {
    val Background    = Color(0xFF0A0E1A)
    val CardDark      = Color(0xFF111827)
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
                Text(
                    text       = "Ejercicio de intervención",
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color      = TextPrimary,
                    modifier   = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 44.dp)
                )
            }

            Text(
                text       = "Elegí qué ejercicio se activa automáticamente cuando se detecta una crisis. Esta preferencia se sincroniza con tu reloj.",
                fontSize   = 13.sp,
                color      = TextSecondary,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Iteramos sobre todas las opciones de ExercisePreference para renderizar
            // una tarjeta seleccionable por cada una
            ExercisePreference.entries.forEach { option ->
                val isSelected = selected == option
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        // Fondo y borde cambian cuando la opción está seleccionada
                        .background(if (isSelected) KairosBlue.copy(alpha = 0.12f) else CardDark)
                        .border(
                            width = if (isSelected) 1.5.dp else 0.dp,
                            color = if (isSelected) KairosBlue else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelect(option) }
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment     = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick  = { onSelect(option) },
                            colors   = RadioButtonDefaults.colors(
                                selectedColor   = KairosBlue,
                                unselectedColor = TextSecondary
                            )
                        )
                        Column {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Text(
                                    text       = option.label,
                                    fontSize   = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = if (isSelected) KairosBlue else TextPrimary
                                )
                                // Badge "Recomendado" visible solo en la opción BOTH
                                if (option == ExercisePreference.BOTH) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(KairosGreen.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text     = "Recomendado",
                                            fontSize = 10.sp,
                                            color    = KairosGreen
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text       = option.description,
                                fontSize   = 12.sp,
                                color      = TextSecondary,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Banner de confirmación visible solo después de guardar exitosamente
            if (saved) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(KairosGreen.copy(alpha = 0.1f))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text     = "✓ Preferencia guardada y sincronizada con el reloj",
                        fontSize = 13.sp,
                        color    = KairosGreen
                    )
                }
            }

            Button(
                onClick  = onSave,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = KairosBlue),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text       = "Guardar preferencia",
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = Color.White
                )
            }

            TextButton(
                onClick  = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Volver", fontSize = 13.sp, color = TextSecondary)
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}