package com.sosbeacon.evai

import android.app.Application
import android.content.Context

class EvApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ctx = applicationContext
        Prefs.init(this)
    }

    companion object {
        lateinit var ctx: Context
    }
}
