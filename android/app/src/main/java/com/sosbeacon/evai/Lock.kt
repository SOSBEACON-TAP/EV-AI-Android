package com.sosbeacon.evai

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * App lock, like the laptop's start-up face check: EV AI opens with the phone's own face or fingerprint unlock,
 * and a password you set is the fallback. Android does not let apps read the face data, so the phone itself does the
 * matching and tells EV AI yes or no. The password is stored only as a salted hash.
 */
object AppLock {
    private val sp get() = EvApp.ctx.getSharedPreferences("applock", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false) && (hasPassword || biometricReady(EvApp.ctx))
        set(v) { sp.edit().putBoolean("enabled", v).apply() }

    val hasPassword: Boolean get() = (sp.getString("hash", "") ?: "") != ""

    private fun hash(pw: String, salt: ByteArray): String =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pw.toCharArray(), salt, 100_000, 256))
            .encoded.joinToString("") { "%02x".format(it) }

    fun setPassword(pw: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        sp.edit().putString("salt", salt.joinToString("") { "%02x".format(it) }).putString("hash", hash(pw, salt)).putInt("fails", 0).apply()
    }

    fun lockedForSec(): Long = ((sp.getLong("until", 0L) - System.currentTimeMillis()) / 1000).coerceAtLeast(0)

    /** 5 wrong tries lock the password box for 60 seconds. */
    fun check(pw: String): Boolean {
        if (lockedForSec() > 0 || !hasPassword) return false
        val salt = (sp.getString("salt", "") ?: "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        if (hash(pw, salt) == sp.getString("hash", "")) { sp.edit().putInt("fails", 0).apply(); return true }
        val fails = sp.getInt("fails", 0) + 1
        if (fails >= 5) sp.edit().putInt("fails", 0).putLong("until", System.currentTimeMillis() + 60_000).apply()
        else sp.edit().putInt("fails", fails).apply()
        return false
    }

    fun biometricReady(c: Context): Boolean =
        BiometricManager.from(c).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    /** Opens the phone's own screen where a face or fingerprint is added (Android keeps the face data to itself). */
    fun openEnrol(c: Context) {
        val intents = ArrayList<android.content.Intent>()
        if (android.os.Build.VERSION.SDK_INT >= 30) intents.add(android.content.Intent(android.provider.Settings.ACTION_BIOMETRIC_ENROLL)
            .putExtra(android.provider.Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, BiometricManager.Authenticators.BIOMETRIC_WEAK))
        intents.add(android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS))
        intents.add(android.content.Intent(android.provider.Settings.ACTION_SETTINGS))
        for (i in intents) { try { c.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (e: Exception) { } }
    }

    /** Why face unlock can't be used right now, in plain words (empty when it is ready). */
    fun biometricProblem(c: Context): String = when (BiometricManager.from(c).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
        BiometricManager.BIOMETRIC_SUCCESS -> ""
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "No face or fingerprint is added on this phone yet. Tap \"Add face / fingerprint\" and set one up first."
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "This phone has no face or fingerprint sensor that apps can use."
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "The face / fingerprint sensor is busy. Try again in a moment."
        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "Your phone needs a security update before it allows face unlock in apps."
        else -> "Face unlock isn't available on this phone for apps. Your password will be used."
    }
}

@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val ctx = LocalContext.current
    val act = ctx as? FragmentActivity
    val bio = remember { AppLock.biometricReady(ctx) }
    var showPw by remember { mutableStateOf(!bio) }
    var pw by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf(if (bio) "" else "No face or fingerprint is set up on this phone, so use your password.") }

    fun prompt() {
        if (act == null || !bio) { showPw = true; return }
        val bp = BiometricPrompt(act, ContextCompat.getMainExecutor(ctx), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onUnlocked() }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { showPw = AppLock.hasPassword; msg = if (AppLock.hasPassword) "Face or fingerprint did not unlock. Use your password." else "Face or fingerprint did not unlock: $errString" }
            override fun onAuthenticationFailed() { msg = "Face not recognised. Try again." }
        })
        bp.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock EV AI")
            .setSubtitle("Look at the phone or use your fingerprint")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setNegativeButtonText(if (AppLock.hasPassword) "Use password" else "Cancel")
            .build())
    }
    LaunchedEffect(Unit) {
        if (bio) prompt()
        else if (!AppLock.hasPassword) onUnlocked()          // nothing left to unlock with: never lock the owner out
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF05060A)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp).widthIn(max = 360.dp)) {
            Image(painterResource(R.drawable.ev_logo), null, Modifier.size(80.dp))
            Spacer(Modifier.height(16.dp))
            Text("EV AI IS LOCKED", color = Ui.accentColor(), fontSize = 14.sp, letterSpacing = 3.sp)
            Spacer(Modifier.height(16.dp))
            if (bio) OutlinedButton(onClick = { prompt() }) { Text("Unlock with face or fingerprint") }
            if (showPw && AppLock.hasPassword) {
                OutlinedTextField(pw, { pw = it }, Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true,
                    label = { Text("Password") }, visualTransformation = PasswordVisualTransformation())
                Button(onClick = {
                    val wait = AppLock.lockedForSec()
                    if (wait > 0) msg = "Too many wrong tries. Wait $wait seconds."
                    else if (AppLock.check(pw)) onUnlocked() else msg = "Wrong password."
                    pw = ""
                }, modifier = Modifier.padding(top = 8.dp)) { Text("Unlock") }
            }
            if (msg.isNotEmpty()) Text(msg, Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }
}

/** Settings section: turn the lock on with the phone's face / fingerprint, and optionally a password as a back-up. */
@Composable
fun AppLockSection() {
    var on by remember { mutableStateOf(AppLock.enabled) }
    var pw by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val act = ctx as? FragmentActivity
    var problem by remember { mutableStateOf(AppLock.biometricProblem(ctx)) }
    // coming back from the phone's own face set-up screen: check again
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) problem = AppLock.biometricProblem(ctx) }
        owner.lifecycle.addObserver(obs); onDispose { owner.lifecycle.removeObserver(obs) }
    }
    Section("App lock (face / fingerprint)") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(on, {
                if (it && problem.isNotEmpty() && !AppLock.hasPassword) msg = "Add a face or fingerprint on the phone first, or set a password below."
                else { AppLock.enabled = it; on = it
                    msg = if (it) "EV AI will ask for your face, fingerprint or password when it opens." else "App lock is off." }
            })
            Spacer(Modifier.width(10.dp)); Text(if (on) "EV AI asks to unlock when it opens" else "Off")
        }
        Text(if (problem.isEmpty()) "Face or fingerprint unlock from your phone is ready." else problem,
            Modifier.padding(top = 6.dp), color = if (problem.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            if (problem.isNotEmpty()) Button(onClick = { AppLock.openEnrol(ctx) }) { Text("Add face / fingerprint") }
            OutlinedButton(onClick = {
                if (act == null || problem.isNotEmpty()) msg = "Face unlock isn't ready yet."
                else BiometricPrompt(act, ContextCompat.getMainExecutor(ctx), object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { msg = "Face / fingerprint works." }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { msg = "Test stopped: $errString" }
                }).authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Test EV AI unlock").setNegativeButtonText("Cancel")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK).build())
            }) { Text("Test it") }
        }
        OutlinedTextField(pw, { pw = it }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true,
            label = { Text(if (AppLock.hasPassword) "New back-up password (4+ characters)" else "Back-up password (optional, 4+ characters)") },
            visualTransformation = PasswordVisualTransformation())
        Button(onClick = {
            if (pw.length < 4) msg = "The password needs at least 4 characters."
            else { AppLock.setPassword(pw); pw = ""; msg = "Password saved." }
        }, modifier = Modifier.padding(top = 6.dp)) { Text("Save password") }
        if (msg.isNotEmpty()) Text(msg, Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.primary)
    }
}
