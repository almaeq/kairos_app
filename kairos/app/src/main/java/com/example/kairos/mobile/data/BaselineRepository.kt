package com.example.kairos.mobile.data

import android.content.Context
import android.util.Log
import com.example.kairos.mobile.data.db.BaselineStats
import com.example.kairos.mobile.data.db.KairosDao
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Repositorio responsable de persistir y gestionar la línea de base personal del usuario.
 *
 * La línea de base almacena los parámetros del algoritmo de Welford (media y M2 de HR y HRV),
 * que permiten calcular Z-scores personalizados para la detección de crisis sin depender
 * de umbrales globales estáticos.
 *
 * Cubre US#4010 — KAIROS aprende el baseline personal y lo recuerda entre sesiones.
 *
 * Cuando el usuario solicita recalibrar, este repositorio coordina el borrado tanto
 * en la base de datos local (Room) como en la memoria del reloj (Wear OS),
 * enviando un mensaje via [Wearable.getMessageClient].
 *
 * @property dao DAO de Room para operaciones de lectura y escritura sobre [BaselineStats].
 * @property context Contexto de la aplicación, necesario para comunicarse con el reloj.
 *           Puede ser `null` si solo se necesitan operaciones locales (ej: tests).
 */
class BaselineRepository(
    private val dao: KairosDao,
    private val context: Context? = null
) {

    /**
     * Persiste los parámetros actuales del algoritmo de Welford en la base de datos.
     *
     * Se invoca cada vez que el teléfono recibe una actualización de baseline desde el reloj,
     * garantizando que los parámetros de calibración sobrevivan reinicios de la app.
     *
     * @param hrCount Cantidad de muestras de HR procesadas acumuladas.
     * @param hrMean Media acumulada de HR calculada por Welford.
     * @param hrM2 Suma de diferencias al cuadrado de HR (para derivar la varianza).
     * @param hrvCount Cantidad de muestras de HRV procesadas acumuladas.
     * @param hrvMean Media acumulada de HRV calculada por Welford.
     * @param hrvM2 Suma de diferencias al cuadrado de HRV (para derivar la varianza).
     * @param calibrationWindows Número de ventanas de baseline completadas hasta el momento.
     */
    suspend fun save(
        hrCount: Int, hrMean: Double, hrM2: Double,
        hrvCount: Int, hrvMean: Double, hrvM2: Double,
        calibrationWindows: Int
    ) {
        dao.saveBaseline(
            BaselineStats(
                hrCount            = hrCount,
                hrMean             = hrMean,
                hrM2               = hrM2,
                hrvCount           = hrvCount,
                hrvMean            = hrvMean,
                hrvM2              = hrvM2,
                calibrationWindows = calibrationWindows,
                updatedAt          = Instant.now().toEpochMilli()
            )
        )
        Log.d("BaselineRepo", "Baseline guardado — calibración: $calibrationWindows/3")
    }

    /**
     * Recupera los parámetros de baseline almacenados en la base de datos.
     *
     * @return [BaselineStats] con los parámetros del algoritmo de Welford,
     *         o `null` si el usuario todavía no completó ninguna ventana de calibración.
     */
    suspend fun load() = dao.getBaseline()

    /**
     * Indica si el usuario completó el mínimo de ventanas de calibración requeridas.
     *
     * KAIROS requiere al menos 3 ventanas de baseline (3 × 60s = 3 minutos)
     * antes de activar la detección de crisis, para que los Z-scores sean estadísticamente
     * representativos de la línea de base personal.
     *
     * @return `true` si se completaron 3 o más ventanas de calibración, `false` en caso contrario.
     */
    suspend fun isCalibrated(): Boolean = (dao.getBaseline()?.calibrationWindows ?: 0) >= 3

    /**
     * Elimina la línea de base tanto en el teléfono como en el reloj.
     *
     * Se ejecuta en dos pasos:
     * 1. Borra el registro de [BaselineStats] en Room (siempre).
     * 2. Envía el mensaje `/kairos/reset_baseline` al reloj via Wearable Message API
     *    para que limpie sus parámetros en memoria (solo si hay reloj conectado).
     *
     * El paso 2 es best-effort: si el reloj no está conectado o falla la comunicación,
     * se registra un warning pero no se lanza excepción — el baseline local ya fue borrado
     * y el reloj se resincronizará en la próxima sesión.
     */
    suspend fun clear() {
        dao.deleteBaseline()
        Log.d("BaselineRepo", "Baseline local eliminado")

        if (context == null) return

        try {
            val nodes = getConnectedNodes(context)

            if (nodes.isEmpty()) {
                Log.w("BaselineRepo", "Reloj no conectado — baseline del reloj no se borró")
                return
            }

            nodes.forEach { node ->
                sendMessageToNode(context, node)
                Log.d("BaselineRepo", "reset_baseline enviado a ${node.displayName}")
            }

        } catch (e: Exception) {
            Log.e("BaselineRepo", "Error enviando reset al reloj: ${e.message}")
            // No es fatal — el baseline local ya se borró correctamente
        }
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    /**
     * Obtiene la lista de nodos Wear OS conectados al teléfono en este momento.
     *
     * Convierte la Task-based API de [Wearable.getNodeClient] a una coroutine
     * suspendible usando [suspendCancellableCoroutine], evitando bloquear el hilo
     * y respetando la cancelación de la coroutine padre.
     *
     * @param context Contexto necesario para acceder al cliente Wearable.
     * @return Lista de nodos conectados. Puede estar vacía si el reloj está fuera de rango.
     */
    private suspend fun getConnectedNodes(context: Context): List<Node> =
        suspendCancellableCoroutine { cont ->
            Wearable.getNodeClient(context)
                .connectedNodes
                .addOnSuccessListener { nodes -> cont.resume(nodes) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }

    /**
     * Envía el mensaje de reset de baseline a un nodo Wear OS específico.
     *
     * Usa el path `/kairos/reset_baseline` con payload vacío — el reloj solo necesita
     * recibir la señal para limpiar su estado interno, no requiere datos adicionales.
     *
     * @param context Contexto necesario para acceder al cliente Wearable.
     * @param node Nodo destino (el reloj conectado).
     */
    private suspend fun sendMessageToNode(context: Context, node: Node): Unit =
        suspendCancellableCoroutine { cont ->
            Wearable.getMessageClient(context)
                .sendMessage(node.id, "/kairos/reset_baseline", byteArrayOf())
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }
}