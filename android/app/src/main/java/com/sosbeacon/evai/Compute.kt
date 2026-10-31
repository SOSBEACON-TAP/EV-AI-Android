package com.sosbeacon.evai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Compute: control your computer from this phone, only when BOTH agree.
 * Pair once with the PIN shown on the computer (the computer asks "Allow?"),
 * then every session is approved on the computer again. Messages are encrypted
 * (AES-256-GCM) before they go through the ntfy relay. See core/compute.py.
 */
object ComputeClient {
    var status by mutableStateOf("")
    var paired by mutableStateOf(Prefs.computeKey.isNotEmpty())
    var session by mutableStateOf(false)
    var shot by mutableStateOf<Bitmap?>(null)
    var busy by mutableStateOf(false)

    @Volatile private var listening = false
    private val rnd = SecureRandom()

    private fun id(): String = DeviceCode.decode(DeviceCode.mine()) ?: ""
    private fun server() = Prefs.server.trimEnd('/')

    // ---- crypto ----
    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    private fun unb64(s: String) = Base64.decode(s.trim(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun pinKey(pin: String): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), ("ev-compute-" + id()).toByteArray(), 100_000, 256)).encoded

    private fun seal(key: ByteArray, raw: ByteArray, aad: String): ByteArray {
        val nonce = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        c.updateAAD(aad.toByteArray())
        return nonce + c.doFinal(raw)
    }

    private fun unseal(key: ByteArray, data: ByteArray, aad: String): ByteArray? = try {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, data.copyOfRange(0, 12)))
        c.updateAAD(aad.toByteArray())
        c.doFinal(data, 12, data.size - 12)
    } catch (e: Exception) { null }

    private fun sealJson(key: ByteArray, j: JSONObject) = b64(seal(key, j.toString().toByteArray(), "ev-compute"))
    private fun openJson(key: ByteArray, text: String): JSONObject? = try {
        unseal(key, unb64(text), "ev-compute")?.let { JSONObject(String(it)) }
    } catch (e: Exception) { null }

    private fun sessionTopic(key: ByteArray): String {
        val h = MessageDigest.getInstance("SHA-256").digest(key).joinToString("") { "%02x".format(it) }
        return "evc-${id().lowercase()}-${h.substring(0, 12)}"
    }

    private fun keyBytes(): ByteArray? = Prefs.computeKey.takeIf { it.length == 64 }?.chunked(2)
        ?.map { it.toInt(16).toByte() }?.toByteArray()

    private fun post(topic: String, text: String) {
        val c = URL("${server()}/$topic").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 10000; c.readTimeout = 10000
            c.setRequestProperty("Cache", "no")
            c.outputStream.use { it.write(text.toByteArray()) }
            c.responseCode
        } finally { c.disconnect() }
    }

    private fun stream(topic: String, since: Long, keepGoing: () -> Boolean, onEvent: (JSONObject) -> Unit) {
        val c = URL("${server()}/$topic/sse?since=$since").openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 100000
        c.setRequestProperty("Accept", "text/event-stream")
        try {
            c.inputStream.bufferedReader().use { r ->
                while (keepGoing()) {
                    val line = r.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val j = try { JSONObject(line.substring(5).trim()) } catch (e: Exception) { continue }
                    if (j.optString("event") == "message") onEvent(j)
                }
            }
        } finally { c.disconnect() }
    }

    // ---- pairing ----
    fun requestControl(pin: String) {
        val me = id()
        if (me.isEmpty() || pin.length != 6) { status = "Type the 6-digit PIN shown on the computer."; return }
        busy = true; status = "Waiting for the computer to say yes..."
        Thread {
            try {
                val pk = pinKey(pin)
                val topic = "evp-${me.lowercase()}-$pin"
                val start = System.currentTimeMillis() / 1000 - 5
                post(topic, sealJson(pk, JSONObject().put("k", "req").put("name", Build.MODEL).put("t", System.currentTimeMillis() / 1000.0)))
                val end = System.currentTimeMillis() + 60_000
                stream(topic, start, { System.currentTimeMillis() < end && busy }) { ev ->
                    val m = openJson(pk, ev.optString("message")) ?: return@stream
                    when (m.optString("k")) {
                        "grant" -> { Prefs.computeKey = m.optString("key"); paired = true; busy = false
                            status = "Paired. Start a session when you want control." }
                        "deny" -> { busy = false; status = "The computer said no." }
                    }
                }
                if (busy) status = "No answer from the computer. Try again."
            } catch (e: Exception) { status = "Couldn't reach the relay: ${e.message}" }
            busy = false
        }.start()
    }

    fun unpair() { Prefs.computeKey = ""; paired = false; session = false; shot = null; listening = false; status = "Unpaired." }

    // ---- session ----
    fun send(cmd: String, args: JSONObject = JSONObject()) {
        val key = keyBytes() ?: return
        Thread {
            try {
                val j = JSONObject().put("d", "c").put("n", ByteArray(6).also { rnd.nextBytes(it) }.joinToString("") { "%02x".format(it) })
                    .put("t", System.currentTimeMillis() / 1000.0).put("cmd", cmd).put("a", args)
                post(sessionTopic(key), sealJson(key, j))
            } catch (e: Exception) { status = "Couldn't reach the relay." }
        }.start()
    }

    fun startSession() { status = "Waiting for you to allow it on the computer..."; send("hello", JSONObject().put("name", Build.MODEL)) }
    fun endSession() { send("bye"); session = false }

    fun listen() {
        val key = keyBytes() ?: return
        if (listening) return
        listening = true
        Thread {
            var since = System.currentTimeMillis() / 1000
            while (listening) {
                try {
                    stream(sessionTopic(key), since, { listening }) { ev ->
                        since = ev.optLong("time", since)
                        val att = ev.optJSONObject("attachment")
                        if (att != null) {
                            val bytes = URL(att.optString("url")).openStream().use { it.readBytes() }
                            unseal(key, bytes, "ev-compute-bin")?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.let { shot = it }
                            return@stream
                        }
                        val m = openJson(key, ev.optString("message")) ?: return@stream
                        if (m.optString("d") != "r") return@stream
                        val msg = m.optString("msg")
                        if (msg.startsWith("Session started") || msg.startsWith("Session already")) session = true
                        if (msg.startsWith("No active session")) session = false
                        if (msg != "pong" && msg != "Screenshot sent.") status = msg
                    }
                } catch (e: Exception) { }
                try { Thread.sleep(2500) } catch (e: InterruptedException) { return@Thread }
            }
        }.start()
    }

    fun stopListening() { listening = false }
}

@Composable
fun ComputeSection() {
    var pin by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var auto by remember { mutableStateOf(false) }
    val c = ComputeClient
    DisposableEffect(c.paired) { if (c.paired) c.listen(); onDispose { c.stopListening() } }
    LaunchedEffect(auto, c.session) {
        while (auto && c.session) { c.send("screenshot", JSONObject().put("scale", 0.5).put("q", 45)); delay(2500) }
    }
    Section("Compute: control your computer") {
        Text("Both devices must agree. The computer shows a 6-digit PIN (EV AI Settings, Phone control), you type it here, and the computer asks to allow it. Every session is approved on the computer again.",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        Spacer(Modifier.height(8.dp))
        if (!c.paired) {
            OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(6) }, label = { Text("PIN from the computer") },
                singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            Button(onClick = { c.requestControl(pin) }, enabled = !c.busy && pin.length == 6,
                modifier = Modifier.padding(top = 8.dp)) { Text("Request control") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!c.session) Button(onClick = { c.startSession() }) { Text("Start session") }
                else Button(onClick = { c.endSession() }) { Text("End session") }
                OutlinedButton(onClick = { c.unpair() }) { Text("Unpair") }
            }
            if (c.session) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { c.send("screenshot", JSONObject().put("scale", 0.5).put("q", 45)) }) { Text("Refresh screen") }
                    Switch(auto, { auto = it }); Text("Auto")
                }
                val bmp = c.shot
                if (bmp != null) {
                    var w by remember { mutableStateOf(1) }; var h by remember { mutableStateOf(1) }
                    Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth().padding(top = 8.dp)
                        .onSizeChanged { w = it.width.coerceAtLeast(1); h = it.height.coerceAtLeast(1) }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { o -> c.send("click", JSONObject().put("x", o.x / w).put("y", o.y / h)) },
                                onDoubleTap = { o -> c.send("click", JSONObject().put("x", o.x / w).put("y", o.y / h).put("double", true)) },
                                onLongPress = { o -> c.send("click", JSONObject().put("x", o.x / w).put("y", o.y / h).put("button", "right")) })
                        }
                        .pointerInput(Unit) {
                            var last = 0L
                            detectDragGestures { ch, _ ->
                                val now = System.currentTimeMillis()
                                if (now - last > 120) { last = now
                                    c.send("move", JSONObject().put("x", ch.position.x / w).put("y", ch.position.y / h)) }
                            }
                        })
                    Text("Tap = click, double tap = double click, long press = right click, drag = move.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { c.send("volume_down") }) { Text("Vol -") }
                    OutlinedButton(onClick = { c.send("volume_up") }) { Text("Vol +") }
                    OutlinedButton(onClick = { c.send("mute") }) { Text("Mute") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { c.send("scroll", JSONObject().put("dy", 3)) }) { Text("Scroll up") }
                    OutlinedButton(onClick = { c.send("scroll", JSONObject().put("dy", -3)) }) { Text("Scroll down") }
                    OutlinedButton(onClick = { c.send("lock") }) { Text("Lock") }
                    OutlinedButton(onClick = { c.send("sleep") }) { Text("Sleep") }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(text, { text = it }, label = { Text("Type on the computer") }, singleLine = true, modifier = Modifier.weight(1f))
                    Button(onClick = { c.send("type", JSONObject().put("text", text)); text = "" }) { Text("Send") }
                    OutlinedButton(onClick = { c.send("key", JSONObject().put("name", "enter")) }) { Text("Enter") }
                }
            }
        }
        if (c.status.isNotEmpty()) Text(c.status, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.primary)
    }
}
