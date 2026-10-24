package com.sosbeacon.evai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Full-screen "EV is calling" screen, shown over the lock screen. */
class CallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        Voice.init()
        val message = intent.getStringExtra("message") ?: "Your computer needs you."
        setContent {
            var answered by remember { mutableStateOf(false) }
            Column(
                Modifier.fillMaxSize().background(Color(0xFF0A0F1C)).safeDrawingPadding().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 48.dp)) {
                    Text("EV AI", fontSize = 40.sp, color = Color(0xFF00E5FF))
                    Spacer(Modifier.height(8.dp))
                    Text(if (answered) "Connected" else "is calling…", fontSize = 20.sp, color = Color.White)
                    Spacer(Modifier.height(32.dp))
                    Text(message, fontSize = 20.sp, color = Color.White, textAlign = TextAlign.Center)
                }
                if (!answered) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 48.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Button(
                            onClick = { IncomingCall.dismiss(this@CallActivity); finish() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                        ) { Text("Decline", color = Color.White) }
                        Button(
                            onClick = { IncomingCall.dismiss(this@CallActivity); answered = true; Voice.speak(message) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047)),
                        ) { Text("Answer", color = Color.White) }
                    }
                } else {
                    Button(onClick = { Voice.stop(); finish() }, modifier = Modifier.padding(bottom = 48.dp)) { Text("Done") }
                }
            }
        }
    }

    override fun onDestroy() {
        IncomingCall.dismiss(this)
        super.onDestroy()
    }
}
