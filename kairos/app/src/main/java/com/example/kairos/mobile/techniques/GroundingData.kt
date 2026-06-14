package com.example.kairos.mobile.techniques

/**
 * Representa un paso individual de la técnica de grounding 5-4-3-2-1.
 *
 * Cada paso activa un sentido diferente para anclar al usuario al momento presente,
 * interrumpiendo el ciclo cognitivo del pánico mediante atención plena sensorial.
 *
 * @property number Cantidad de estímulos a identificar en este paso (5, 4, 3, 2, 1).
 *           El orden descendente es intencional: comienza con el sentido más accesible
 *           (vista) y termina con el más sutil (gusto), facilitando el anclaje progresivo.
 * @property emoji Ícono representativo del sentido activado en este paso.
 *           Se muestra en la UI como elemento visual de apoyo.
 * @property sense Descripción breve del sentido a activar, mostrada como subtítulo en la UI
 *           (por ejemplo: "cosas que ves", "cosas que tocás").
 * @property audioText Instrucción completa a reproducir por audio (text-to-speech)
 *           y mostrar como texto guía durante el paso.
 * @property durationMs Duración del paso en milisegundos.
 *           Los pasos iniciales (vista, tacto) son más largos porque requieren
 *           exploración activa del entorno; los finales (olfato, gusto) son más cortos.
 */
data class GroundingStep(
    val number:     Int,
    val emoji:      String,
    val sense:      String,
    val audioText:  String,
    val durationMs: Long
)

/**
 * Secuencia completa de la técnica de grounding 5-4-3-2-1.
 *
 * El grounding 5-4-3-2-1 es una técnica de Terapia Cognitivo-Conductual (TCC)
 * para interrumpir la disociación y el pensamiento catastrófico durante una crisis.
 * Funciona activando secuencialmente los cinco sentidos para traer la atención
 * al momento presente, cortando el bucle de pensamientos ansiosos.
 *
 * **Duración total:** 105 segundos (~1.75 minutos).
 * - Paso 5 (vista):   25s — exploración visual del entorno
 * - Paso 4 (tacto):   25s — exploración táctil de superficies cercanas
 * - Paso 3 (oído):    20s — atención auditiva al entorno
 * - Paso 2 (olfato):  20s — identificación de aromas presentes
 * - Paso 1 (gusto):   15s — atención al sabor actual en la boca
 */
val GROUNDING_STEPS = listOf(
    GroundingStep(
        number     = 5,
        emoji      = "👁️",
        sense      = "cosas que ves",
        audioText  = "Buscá 5 cosas que podés ver ahora mismo. Mirá a tu alrededor y nombralas en tu mente.",
        durationMs = 20_000L
    ),
    GroundingStep(
        number     = 4,
        emoji      = "✋",
        sense      = "cosas que tocás",
        audioText  = "Ahora buscá 4 cosas que podés tocar. Sentí su textura. Fijate si son frías, calientes, suaves o rugosas.",
        durationMs = 20_000L
    ),
    GroundingStep(
        number     = 3,
        emoji      = "👂",
        sense      = "cosas que escuchás",
        audioText  = "Escuchá con atención. ¿Qué 3 sonidos podés identificar ahora mismo?",
        durationMs = 15_000L
    ),
    GroundingStep(
        number     = 2,
        emoji      = "👃",
        sense      = "cosas que olés",
        audioText  = "Respirá profundo. ¿Podés identificar 2 olores distintos a tu alrededor?",
        durationMs = 15_000L
    ),
    GroundingStep(
        number     = 1,
        emoji      = "👅",
        sense      = "cosa que saboreás",
        audioText  = "Por último, prestá atención a tu boca. ¿Qué sabor tenés ahora mismo?",
        durationMs = 10_000L
    )
)