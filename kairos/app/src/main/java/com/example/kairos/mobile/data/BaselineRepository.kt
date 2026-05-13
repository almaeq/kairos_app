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
 * Repositorio de baseline — persiste el Z-Score personal del usuario.
 * Cubre US#4010 — KAIROS aprende el baseline y lo recuerda entre sesiones.
 */
class BaselineRepository(
    private val dao: KairosDao,
    private val context: Context? = null
) {

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

    suspend fun load() = dao.getBaseline()

    suspend fun isCalibrated(): Boolean = (dao.getBaseline()?.calibrationWindows ?: 0) >= 3

    suspend fun clear() {
        // 1. Borrar baseline local
        dao.deleteBaseline()
        Log.d("BaselineRepo", "Baseline local eliminado")

        // 2. Enviar comando al reloj
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
            // No es fatal — el baseline local ya se borró
        }
    }

    // ── Helpers con coroutines manuales para evitar problemas de Task<>.await() ──

    private suspend fun getConnectedNodes(context: Context): List<Node> =
        suspendCancellableCoroutine { cont ->
            Wearable.getNodeClient(context)
                .connectedNodes
                .addOnSuccessListener { nodes -> cont.resume(nodes) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }

    private suspend fun sendMessageToNode(context: Context, node: Node): Unit =
        suspendCancellableCoroutine { cont ->
            Wearable.getMessageClient(context)
                .sendMessage(node.id, "/kairos/reset_baseline", byteArrayOf())
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }
}