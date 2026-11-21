package com.sosbeacon.evai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Command Anywhere: select text in any app, choose "EV AI" in the menu, and have it explained, rewritten, translated or summarized. */
class PortalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.take(6000).orEmpty()
        setContent {
            MaterialTheme {
                val scope = rememberCoroutineScope()
                var answer by remember { mutableStateOf("") }
                var busy by remember { mutableStateOf(false) }
                fun run(instruction: String) {
                    busy = true; answer = "Thinking…"
                    scope.launch {
                        answer = try { Ai.chat("$instruction. Reply with only the result, no preamble.\n\n$text") { } }
                        catch (e: Exception) { "I couldn't reach the AI: ${e.message}" }
                        busy = false
                    }
                }
                AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text("EV AI", fontSize = 14.sp) },
                    text = {
                        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                            Text(text.take(160) + if (text.length > 160) "…" else "", fontSize = 12.sp)
                            Row(Modifier.fillMaxWidth()) {
                                TextButton(enabled = !busy, onClick = { run("Explain this in simple words") }) { Text("Explain", fontSize = 12.sp) }
                                TextButton(enabled = !busy, onClick = { run("Rewrite this to be clearer and better written") }) { Text("Rewrite", fontSize = 12.sp) }
                            }
                            Row(Modifier.fillMaxWidth()) {
                                TextButton(enabled = !busy, onClick = { run("Translate this into English (or into Hindi if it is already English)") }) { Text("Translate", fontSize = 12.sp) }
                                TextButton(enabled = !busy, onClick = { run("Summarize this in a few short bullet points") }) { Text("Summarize", fontSize = 12.sp) }
                            }
                            if (answer.isNotEmpty()) Text(answer, fontSize = 14.sp)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (answer.isNotEmpty() && !busy) {
                                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("EV AI", answer))
                            }
                        }) { Text("Copy answer") }
                    },
                    dismissButton = { TextButton(onClick = { finish() }) { Text("Close") } },
                )
            }
        }
    }
}
