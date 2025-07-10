package com.avichai98.smartreminder.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import android.os.Handler

class AppointmentReminderService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval: Long = 60 * 60 * 1000 // 1 hour

    private val checkAppointmentsRunnable = object : Runnable {
        override fun run() {
            checkUpcomingAppointments()
            handler.postDelayed(this, checkInterval)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.post(checkAppointmentsRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(checkAppointmentsRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun checkUpcomingAppointments() {
        // Load appointments from selected calendar only
        // Check if appointments are within 24 hours and trigger email reminders
    }
}