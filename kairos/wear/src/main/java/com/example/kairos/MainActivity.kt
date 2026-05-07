package com.example.kairos

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.example.kairos.ui.theme.KairosTheme

class MainActivity : ComponentActivity() {

    private val requestPermissions =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { grants ->
            Log.d("KairosWatch", "Permisos: $grants")
            if (!KairosWatchService.isRunning) {
                startService(Intent(this, KairosWatchService::class.java))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("KairosWatch", "MainActivity onCreate")

        requestPermissions.launch(arrayOf(
            "android.permission.health.READ_HEART_RATE",
            "android.permission.BODY_SENSORS",
            "android.permission.ACTIVITY_RECOGNITION"
        ))

        setContent {
            KairosTheme {
                Scaffold(timeText = { TimeText() }) {
                    WatchScreen()
                }
            }
        }
    }
}

@Composable
fun WatchScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "KAIROS",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.title2
        )
        Text(
            text = "Monitoreando...",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.body2
        )
    }
}