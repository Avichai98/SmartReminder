package com.avichai98.smartreminder

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.avichai98.smartreminder.services.AppointmentReminderService
import com.google.firebase.FirebaseApp

class App : Application() {
    @Override
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        Log.d("App", "Firebase initialized")

        val intent = Intent(this, AppointmentReminderService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}