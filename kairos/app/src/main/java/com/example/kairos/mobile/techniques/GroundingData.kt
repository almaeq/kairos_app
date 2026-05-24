package com.example.kairos.mobile.techniques

data class GroundingStep(
    val number:     Int,
    val emoji:      String,
    val sense:      String,
    val audioText:  String,
    val durationMs: Long
)

val GROUNDING_STEPS = listOf(
    GroundingStep(
        number     = 5,
        emoji      = "👁️",
        sense      = "cosas que ves",
        audioText  = "Buscá 5 cosas que podés ver ahora mismo. Mirá a tu alrededor y nombralas en tu mente.",
        durationMs = 25_000L
    ),
    GroundingStep(
        number     = 4,
        emoji      = "✋",
        sense      = "cosas que tocás",
        audioText  = "Ahora buscá 4 cosas que podés tocar. Sentí su textura. Fijate si son frías, calientes, suaves o rugosas.",
        durationMs = 25_000L
    ),
    GroundingStep(
        number     = 3,
        emoji      = "👂",
        sense      = "cosas que escuchás",
        audioText  = "Escuchá con atención. ¿Qué 3 sonidos podés identificar ahora mismo?",
        durationMs = 20_000L
    ),
    GroundingStep(
        number     = 2,
        emoji      = "👃",
        sense      = "cosas que olés",
        audioText  = "Respirá profundo. ¿Podés identificar 2 olores distintos a tu alrededor?",
        durationMs = 20_000L
    ),
    GroundingStep(
        number     = 1,
        emoji      = "👅",
        sense      = "cosa que saboreás",
        audioText  = "Por último, prestá atención a tu boca. ¿Qué sabor tenés ahora mismo?",
        durationMs = 15_000L
    )
)