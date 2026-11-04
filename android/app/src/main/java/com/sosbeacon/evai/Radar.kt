package com.sosbeacon.evai

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.WifiManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A thing around you: a real place (OpenStreetMap) or a Wi-Fi network. */
data class Blip(
    val name: String, val kind: String, val meters: Double, val bearing: Double,
    val lat: Double, val lon: Double, val color: Long, val wifi: Boolean = false, val note: String = "",
)

object RadarMath {
    /** Metres between two points on Earth (haversine). */
    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
        val dp = p2 - p1; val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Compass bearing from point 1 to point 2, 0 = north, clockwise, in degrees. */
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2); val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun compass(deg: Double): String = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[(((deg + 22.5) % 360) / 45).toInt()]

    fun nice(m: Double): String = if (m < 1000) "${m.toInt()} m" else String.format("%.1f km", m / 1000)

    /** dBm to a 0-100 signal estimate. */
    fun signalPercent(dbm: Int): Int = (2 * (dbm + 100)).coerceIn(0, 100)

    /** How well a Wi-Fi network is protected, from Android's capabilities string. */
    fun wifiSecurity(caps: String): Triple<String, Long, String> {
        val c = caps.uppercase()
        return when {
            "WPA3" in c || "SAE" in c -> Triple("WPA3", 0xFF43D17A, "Newest security. Good.")
            "WPA2" in c -> Triple("WPA2", 0xFF43D17A, "Good if the password is strong.")
            "WEP" in c -> Triple("WEP", 0xFFFF4D4D, "Broken for many years. Anyone can crack it quickly.")
            "WPA" in c -> Triple("WPA", 0xFFFFB020, "Old. Prefer WPA2 or WPA3.")
            else -> Triple("OPEN", 0xFFFF4D4D, "No password: anyone nearby can listen to what you send. Avoid it, or use a VPN.")
        }
    }

    fun placeColor(kind: String): Long = when (kind) {
        "hospital", "clinic", "pharmacy" -> 0xFFFF5C5C
        "police", "fire_station" -> 0xFF5C9DFF
        "atm", "bank" -> 0xFFFFD24D
        "restaurant", "cafe", "fast_food" -> 0xFFFF9A3C
        "fuel" -> 0xFFB388FF
        "supermarket", "convenience" -> 0xFF69F0AE
        else -> 0xFF4CC9F0
    }

    fun query(lat: Double, lon: Double, radius: Int): String =
        "[out:json][timeout:15];(" +
            "node(around:$radius,$lat,$lon)[amenity~\"^(hospital|clinic|pharmacy|police|fire_station|atm|bank|restaurant|cafe|fast_food|fuel|school|toilets|drinking_water|bus_station)$\"];" +
            "node(around:$radius,$lat,$lon)[shop~\"^(supermarket|convenience)$\"];" +
            ");out 80;"

    fun parsePlaces(json: String, lat: Double, lon: Double): List<Blip> {
        val out = mutableListOf<Blip>()
        val arr = JSONObject(json).optJSONArray("elements") ?: return out
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            val tags = e.optJSONObject("tags") ?: continue
            val pl = e.optDouble("lat", Double.NaN); val lo = e.optDouble("lon", Double.NaN)
            if (pl.isNaN() || lo.isNaN()) continue
            val kind = tags.optString("amenity").ifEmpty { tags.optString("shop") }
            val name = tags.optString("name").ifEmpty { kind.replace('_', ' ').replaceFirstChar { it.uppercase() } }
            out.add(Blip(name, kind.replace('_', ' '), distance(lat, lon, pl, lo), bearing(lat, lon, pl, lo), pl, lo, placeColor(kind)))
        }
        return out.sortedBy { it.meters }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun RadarScreen() {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var loc by remember { mutableStateOf<Location?>(null) }
    var heading by remember { mutableStateOf(0f) }
    var places by remember { mutableStateOf<List<Blip>>(emptyList()) }
    var wifi by remember { mutableStateOf<List<Blip>>(emptyList()) }
    var range by remember { mutableStateOf(500) }
    var mode by remember { mutableStateOf("both") }
    var status by remember { mutableStateOf("") }
    var loadedAt by remember { mutableStateOf<Location?>(null) }

    // GPS position
    DisposableEffect(granted) {
        if (!granted) return@DisposableEffect onDispose { }
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = LocationListener { l -> loc = l }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter {
            try { lm.isProviderEnabled(it) } catch (e: Exception) { false }
        }
        if (providers.isEmpty()) status = "Turn on Location in your phone's quick settings."
        for (p in providers) {
            try {
                lm.getLastKnownLocation(p)?.let { if (loc == null) loc = it }
                lm.requestLocationUpdates(p, 3000L, 5f, listener)
            } catch (e: Exception) { }
        }
        onDispose { try { lm.removeUpdates(listener) } catch (e: Exception) { } }
    }

    // Compass: the radar turns so "up" is where you are facing
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val l = object : SensorEventListener {
            override fun onSensorChanged(ev: SensorEvent) {
                val r = FloatArray(9); val o = FloatArray(3)
                SensorManager.getRotationMatrixFromVector(r, ev.values)
                SensorManager.getOrientation(r, o)
                val deg = ((Math.toDegrees(o[0].toDouble()) + 360) % 360).toFloat()
                // smooth so the radar doesn't jitter
                var d = deg - heading
                if (d > 180) d -= 360; if (d < -180) d += 360
                heading = (heading + d * 0.15f + 360f) % 360f
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) { }
        }
        if (sensor != null) sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(l) }
    }

    // Wi-Fi networks around you (what the phone already hears)
    fun readWifi() {
        try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION") wm.startScan()
            val seen = HashSet<String>()
            wifi = wm.scanResults.filter { seen.add(it.SSID + it.BSSID) }.map { r ->
                val (label, col, note) = RadarMath.wifiSecurity(r.capabilities)
                val pct = RadarMath.signalPercent(r.level)
                val h = (r.BSSID ?: r.SSID).hashCode()
                // no direction is known for Wi-Fi, so each network gets a steady spot; nearer centre = stronger signal
                Blip(r.SSID.ifEmpty { "(hidden)" }, "Wi-Fi $label", (100 - pct) * 1.0, ((h % 360) + 360) % 360.0, 0.0, 0.0, col, true, "$note Signal $pct%.")
            }.sortedBy { it.meters }
        } catch (e: Exception) { status = "Couldn't read Wi-Fi (is Wi-Fi on?)." }
    }
    LaunchedEffect(granted) { if (granted) { readWifi() } }

    // real places from OpenStreetMap (re-load when you move 150 m, or change the range)
    LaunchedEffect(loc, range) {
        val l = loc ?: return@LaunchedEffect
        val last = loadedAt
        if (last != null && last.distanceTo(l) < 150f && places.isNotEmpty()) return@LaunchedEffect
        status = "Scanning around you…"
        try {
            val text = withContext(Dispatchers.IO) {
                val c = URL("https://overpass-api.de/api/interpreter").openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 12000; c.readTimeout = 20000
                c.setRequestProperty("User-Agent", "EV-AI-Android/1.0 (educational radar)")
                c.outputStream.use { it.write(("data=" + java.net.URLEncoder.encode(RadarMath.query(l.latitude, l.longitude, range), "UTF-8")).toByteArray()) }
                c.inputStream.bufferedReader().use { it.readText() }
            }
            places = RadarMath.parsePlaces(text, l.latitude, l.longitude)
            loadedAt = l
            status = if (places.isEmpty()) "No mapped places within $range m." else ""
        } catch (e: Exception) { status = "Couldn't load places (no internet?)." }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        if (!granted) {
            Section("Radar") {
                Text("The radar shows what is around you: real places nearby (from OpenStreetMap) and the Wi-Fi networks your phone can hear, with how well each is protected. It needs your location, only while this screen is open. Nothing is stored or sent except the area search to OpenStreetMap.")
                Button(onClick = { ask.launch(Manifest.permission.ACCESS_FINE_LOCATION) }, modifier = Modifier.padding(top = 12.dp)) { Text("Allow location") }
            }
            return@Column
        }
        val me = loc
        Text(if (me == null) "Finding your position…" else String.format("%.5f, %.5f   ±%d m", me.latitude, me.longitude, me.accuracy.toInt()),
            color = MaterialTheme.colorScheme.primary)
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((v, t) in listOf("both" to "Both", "places" to "Places", "wifi" to "Wi-Fi")) {
                FilterChip(selected = mode == v, onClick = { mode = v }, label = { Text(t) })
            }
            Spacer(Modifier.weight(1f))
            for (r in listOf(250, 500, 1000)) {
                FilterChip(selected = range == r, onClick = { range = r; loadedAt = null }, label = { Text("${r}m") })
            }
        }
        val shown = (if (mode != "wifi") places else emptyList()) + (if (mode != "places") wifi else emptyList())
        RadarCanvas(shown, range, heading)
        if (status.isNotEmpty()) Text(status, Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        OutlinedButton(onClick = { readWifi() }, modifier = Modifier.padding(top = 6.dp)) { Text("Refresh Wi-Fi") }

        Section("Around you") {
            if (shown.isEmpty()) Text("Nothing to show yet.")
            for (b in shown.take(40)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(Color(b.color), androidx.compose.foundation.shape.CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(b.name, maxLines = 1)
                        Text(
                            if (b.wifi) b.kind + " · " + b.note else "${b.kind} · ${RadarMath.nice(b.meters)} ${RadarMath.compass(b.bearing)}",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                    if (!b.wifi) TextButton(onClick = {
                        try {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${b.lat},${b.lon}?q=${b.lat},${b.lon}(${Uri.encode(b.name)})")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: Exception) { }
                    }) { Text("Map") }
                }
            }
        }
        Section("Learn") {
            Text("Red Wi-Fi is OPEN or WEP: anyone near you can read the traffic, so avoid logging in on it. Amber is old WPA. Green is WPA2 or WPA3. " +
                "The radar only lists networks your phone already hears. It never connects to them or tests them.")
        }
    }
}

@Composable
fun RadarCanvas(blips: List<Blip>, range: Int, heading: Float) {
    val sweep by rememberInfiniteTransition(label = "sweep").animateFloat(0f, 360f,
        infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "a")
    val red = Color(0xFFFF2A2A)
    val accent = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 - 8.dp.toPx()
        drawCircle(Color(0xFF05080C), r + 6.dp.toPx(), c)
        for (i in 1..4) drawCircle(accent.copy(alpha = 0.25f), r * i / 4, c, style = Stroke(1.dp.toPx()))
        // the grid turns with the compass: "up" is where you are facing
        rotate(-heading, c) {
            drawLine(accent.copy(alpha = 0.25f), Offset(c.x - r, c.y), Offset(c.x + r, c.y))
            drawLine(accent.copy(alpha = 0.25f), Offset(c.x, c.y - r), Offset(c.x, c.y + r))
            drawIntoNorth(c, r)
        }
        // sweeping beam
        for (i in 0 until 36) {
            val a = Math.toRadians((sweep - i * 1.8 - 90).toDouble())
            drawLine(accent.copy(alpha = (0.5f - i * 0.014f).coerceAtLeast(0f)), c, Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()), 2.dp.toPx())
        }
        val paint = android.graphics.Paint().apply { textSize = 10.sp.toPx(); isAntiAlias = true }
        for (b in blips) {
            val frac = if (b.wifi) (b.meters / 100.0).coerceIn(0.1, 0.95) else (b.meters / range).coerceIn(0.0, 1.0)
            val ang = Math.toRadians(b.bearing - heading - 90.0)
            val p = Offset(c.x + (r * frac * cos(ang)).toFloat(), c.y + (r * frac * sin(ang)).toFloat())
            val flare = ((sweep - ((Math.toDegrees(ang) + 90 + 360) % 360).toFloat() + 360f) % 360f) < 40f
            val col = Color(b.color).copy(alpha = if (flare) 1f else 0.6f)
            if (b.wifi) {
                val d = Path().apply { moveTo(p.x, p.y - 7.dp.toPx()); lineTo(p.x + 7.dp.toPx(), p.y); lineTo(p.x, p.y + 7.dp.toPx()); lineTo(p.x - 7.dp.toPx(), p.y); close() }
                drawPath(d, col)
            } else drawCircle(col, 5.dp.toPx(), p)
            paint.color = android.graphics.Color.argb(200, (b.color shr 16 and 255).toInt(), (b.color shr 8 and 255).toInt(), (b.color and 255).toInt())
            drawContext.canvas.nativeCanvas.drawText(b.name.take(14), p.x + 8.dp.toPx(), p.y - 6.dp.toPx(), paint)
        }
        drawCircle(red, 6.dp.toPx(), c)
        drawCircle(red, r, c, style = Stroke(2.dp.toPx()))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIntoNorth(c: Offset, r: Float) {
    val paint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(255, 80, 80); textSize = 14.sp.toPx(); isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER }
    drawContext.canvas.nativeCanvas.drawText("N", c.x, c.y - r + 14.sp.toPx(), paint)
}
