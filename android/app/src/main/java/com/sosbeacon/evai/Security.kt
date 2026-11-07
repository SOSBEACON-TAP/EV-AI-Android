package com.sosbeacon.evai

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln

/**
 * Ultron's defensive tools for THIS phone. Everything here only looks at your own phone and accounts:
 * no scanning of other devices, no exploits. Results are plain lines the chat can show.
 */
object Security {
    class F(val level: String, val text: String)       // level: ok | info | warn | bad

    fun tag(f: F): String = when (f.level) { "ok" -> "[ OK ] "; "warn" -> "[WARN] "; "bad" -> "[ !! ] "; else -> "[ .. ] " } + f.text

    fun score(fs: List<F>): Int = (100 - fs.count { it.level == "bad" } * 20 - fs.count { it.level == "warn" } * 8).coerceIn(0, 100)

    private fun daysSince(date: String): Int? = try {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date) ?: return null
        ((Date().time - d.time) / 86_400_000L).toInt()
    } catch (e: Exception) { null }

    fun phoneScan(c: Context): List<F> {
        val out = mutableListOf<F>()
        val kg = c.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        out += if (kg.isDeviceSecure) F("ok", "Screen lock is set") else F("bad", "No screen lock. Anyone who picks up the phone can use it. Set a PIN, pattern or fingerprint.")

        val patch = Build.VERSION.SECURITY_PATCH
        val age = daysSince(patch)
        out += when {
            age == null -> F("info", "Security patch date unknown")
            age > 180 -> F("warn", "Security patch is $age days old ($patch). Check for system updates.")
            else -> F("ok", "Security patch is recent ($patch)")
        }
        out += if (Build.VERSION.SDK_INT < 30) F("warn", "Android ${Build.VERSION.RELEASE} is old and no longer gets fixes") else F("ok", "Android ${Build.VERSION.RELEASE}")

        val cr = c.contentResolver
        if (Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0) == 1) out += F("warn", "USB debugging is on. Turn it off unless you are developing apps.")
        else out += F("ok", "USB debugging is off")
        if (Settings.Global.getInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1) out += F("info", "Developer options are on")

        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) out += F("info", "A VPN is active")

        out += wifiFindings(c)
        out += permissionFindings(c)
        return out
    }

    /** The Wi-Fi you are connected to: open and old WEP networks are not safe. */
    @Suppress("DEPRECATION", "MissingPermission")
    fun wifiFindings(c: Context): List<F> {
        val out = mutableListOf<F>()
        try {
            val wm = c.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wm.connectionInfo
            val ssid = info?.ssid?.trim('"') ?: ""
            if (ssid.isEmpty() || ssid == "<unknown ssid>") { out += F("info", "Wi-Fi: not connected, or location permission is missing so the name is hidden"); return out }
            val cap = wm.scanResults.firstOrNull { it.BSSID.equals(info.bssid, true) }?.capabilities ?: ""
            out += when {
                cap.isEmpty() -> F("info", "Wi-Fi \"$ssid\": security type unknown (allow location and scan once)")
                cap.contains("WEP") -> F("bad", "Wi-Fi \"$ssid\" uses WEP, which can be broken in minutes. Change the router to WPA2 or WPA3.")
                !cap.contains("WPA") && !cap.contains("SAE") && !cap.contains("OWE") -> F("warn", "Wi-Fi \"$ssid\" is OPEN: others on it could watch unencrypted traffic. Avoid logins and payments, or use a VPN.")
                cap.contains("SAE") -> F("ok", "Wi-Fi \"$ssid\" uses WPA3")
                else -> F("ok", "Wi-Fi \"$ssid\" uses WPA2")
            }
        } catch (e: Exception) { out += F("info", "Wi-Fi check skipped: ${e.message ?: e.javaClass.simpleName}") }
        return out
    }

    /** Accessibility services, device admins, notification readers and apps with risky permission mixes. */
    @Suppress("DEPRECATION")
    fun permissionFindings(c: Context): List<F> {
        val out = mutableListOf<F>()
        try {
            val acc = (Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
                .split(":").filter { it.isNotBlank() }.map { it.substringBefore('/') }.filter { it != c.packageName }
            if (acc.isNotEmpty()) out += F("warn", "Accessibility services can read your screen: " + acc.joinToString { Usage.label(c, it) } + ". Keep only ones you trust.")
            val dpm = c.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admins = dpm.activeAdmins?.map { it.packageName }.orEmpty().filter { it != c.packageName }
            if (admins.isNotEmpty()) out += F("warn", "Device admin apps: " + admins.joinToString { Usage.label(c, it) })
            val listeners = NotificationManagerCompat.getEnabledListenerPackages(c).filter { it != c.packageName }
            if (listeners.isNotEmpty()) out += F("info", "Apps that can read your notifications: " + listeners.joinToString { Usage.label(c, it) })

            val pm = c.packageManager
            val flagged = mutableListOf<String>()
            var sideloaded = 0
            for (pi in pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
                val ai = pi.applicationInfo ?: continue
                if (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0 || pi.packageName == c.packageName) continue
                val perms = pi.requestedPermissions?.toSet().orEmpty()
                val why = mutableListOf<String>()
                if ("android.permission.READ_SMS" in perms || "android.permission.RECEIVE_SMS" in perms) why += "reads SMS"
                if ("android.permission.SYSTEM_ALERT_WINDOW" in perms) why += "draws over other apps"
                if ("android.permission.BIND_DEVICE_ADMIN" in perms) why += "device admin"
                if (why.size >= 2 || "reads SMS" in why) flagged += "${pm.getApplicationLabel(ai)} (${why.joinToString(", ")})"
                val installer = try { pm.getInstallerPackageName(pi.packageName) } catch (e: Exception) { null }
                if (installer != "com.android.vending") sideloaded++
            }
            if (flagged.isNotEmpty()) out += F("warn", "Apps with sensitive permissions: " + flagged.take(6).joinToString("; ") + ". Remove any you do not need.")
            else out += F("ok", "No app combines risky permissions")
            if (sideloaded > 0) out += F("info", "$sideloaded app(s) were not installed from Google Play. Only keep ones you trust.")
        } catch (e: Exception) { out += F("info", "App check skipped: ${e.message ?: e.javaClass.simpleName}") }
        return out
    }

    // ------------------------------------------------------------------ password lab
    /** Rough strength from length and character variety, with plain-language advice. */
    fun passwordStrength(pw: String): List<F> {
        if (pw.isEmpty()) return listOf(F("info", "Type a password first"))
        var pool = 0
        if (pw.any { it.isLowerCase() }) pool += 26
        if (pw.any { it.isUpperCase() }) pool += 26
        if (pw.any { it.isDigit() }) pool += 10
        if (pw.any { !it.isLetterOrDigit() }) pool += 32
        val bits = pw.length * (ln(pool.coerceAtLeast(1).toDouble()) / ln(2.0))
        val common = listOf("password", "123456", "qwerty", "letmein", "iloveyou", "admin", "welcome", "abc123")
        val out = mutableListOf<F>()
        if (common.any { pw.lowercase().contains(it) }) out += F("bad", "Contains a very common word or pattern")
        out += when {
            bits < 40 -> F("bad", "Weak (about ${bits.toInt()} bits). Use 14+ characters.")
            bits < 60 -> F("warn", "Fair (about ${bits.toInt()} bits). Longer is better.")
            bits < 80 -> F("ok", "Good (about ${bits.toInt()} bits)")
            else -> F("ok", "Strong (about ${bits.toInt()} bits)")
        }
        if (pw.length < 12) out += F("warn", "Shorter than 12 characters")
        return out
    }

    fun generatePassword(len: Int = 18): String {
        val sets = listOf("abcdefghjkmnpqrstuvwxyz", "ABCDEFGHJKLMNPQRSTUVWXYZ", "23456789", "!@#$%^&*-_=+?")
        val r = SecureRandom()
        val chars = sets.map { it[r.nextInt(it.length)] }.toMutableList()
        val all = sets.joinToString("")
        while (chars.size < len) chars += all[r.nextInt(all.length)]
        chars.shuffle(r)
        return chars.joinToString("")
    }

    /** Has this password appeared in known leaks? Only the first 5 characters of its SHA-1 leave the phone (k-anonymity). */
    fun leakCheck(pw: String): F {
        if (pw.isEmpty()) return F("info", "Type a password first")
        return try {
            val sha = MessageDigest.getInstance("SHA-1").digest(pw.toByteArray()).joinToString("") { "%02X".format(it) }
            val c = URL("https://api.pwnedpasswords.com/range/" + sha.take(5)).openConnection() as HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 10000
            c.setRequestProperty("User-Agent", "EV-AI")
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val hit = body.lineSequence().map { it.trim().split(":") }.firstOrNull { it.size == 2 && it[0] == sha.drop(5) }
            if (hit == null) F("ok", "Not found in known leaks") else F("bad", "Seen ${hit[1].trim()} times in leaks. Never use it again.")
        } catch (e: Exception) { F("warn", "Couldn't reach the leak check (internet?)") }
    }

    // ------------------------------------------------------------------ website analyzer
    /** One normal request to a site YOU choose, then a look at its security headers. No probing. */
    fun analyzeWebsite(input: String): List<F> {
        val url = input.trim().let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
        val out = mutableListOf<F>()
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 10000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "EV-AI-analyzer")
            val code = c.responseCode
            out += if (url.startsWith("https://")) F("ok", "Uses HTTPS (encrypted)") else F("warn", "Plain HTTP: traffic is not encrypted")
            out += F("info", "Answered with status $code")
            val h = c.headerFields.filterKeys { it != null }.mapKeys { it.key.lowercase() }
            fun need(name: String, label: String) { out += if (h.containsKey(name)) F("ok", "$label present") else F("warn", "$label missing") }
            need("strict-transport-security", "HSTS (forces HTTPS)")
            need("content-security-policy", "Content-Security-Policy")
            need("x-content-type-options", "X-Content-Type-Options")
            need("x-frame-options", "X-Frame-Options (clickjacking)")
            need("referrer-policy", "Referrer-Policy")
            h["server"]?.firstOrNull()?.let { if (it.any { ch -> ch.isDigit() }) out += F("info", "Server tells its version: $it") }
            out
        } catch (e: Exception) { listOf(F("warn", "Couldn't analyze that site: ${e.message ?: e.javaClass.simpleName}")) }
    }
}
