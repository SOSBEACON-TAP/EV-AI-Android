package com.sosbeacon.evai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat

class Check(val name: String, val ok: Boolean, val fix: String, val action: String)

/** Error Doctor: checks everything EV needs and says how to fix what's missing. */
object Doctor {
    private fun perm(c: Context, p: String) = ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED

    fun run(c: Context): List<Check> {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        return listOf(
            Check("AI key", Prefs.apiKey.isNotBlank(), "Open Settings and paste your AI key.", "settings"),
            Check("Internet", net, "Connect to Wi-Fi or mobile data.", "none"),
            Check("Contacts permission", perm(c, Manifest.permission.READ_CONTACTS), "Needed to call or message people by name.", "perms"),
            Check("Phone call permission", perm(c, Manifest.permission.CALL_PHONE), "Needed to place calls directly.", "perms"),
            Check("SMS permission", perm(c, Manifest.permission.SEND_SMS), "Needed to send text messages.", "perms"),
            Check("Notifications", perm(c, Manifest.permission.POST_NOTIFICATIONS), "Needed for reminders and timers.", "perms"),
            Check("Usage access", Usage.hasAccess(c), "Needed for App Insights and Focus mode.", "usage"),
            Check("Accessibility (auto-send, close blocked apps)", EvAccess.enabled(c), "Turn on EV AI so it can press Send and block apps.", "access"),
        )
    }
}
