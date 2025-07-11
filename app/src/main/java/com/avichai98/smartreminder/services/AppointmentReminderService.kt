package com.avichai98.smartreminder.services

import android.app.*
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import android.util.Log
import com.avichai98.smartreminder.R

class AppointmentReminderService : Service() {

    private val TAG = "ReminderService"
    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval: Long = 60 * 60 * 1000 // 1 hour

    // Runnable that checks for appointments periodically
    private val checkAppointmentsRunnable = object : Runnable {
        override fun run() {
            checkUpcomingAppointments()
            handler.postDelayed(this, checkInterval)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start the service in foreground with notification
        startForeground(1, createNotification())
        handler.post(checkAppointmentsRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(checkAppointmentsRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Logic to check for upcoming appointments and trigger reminders
    private fun checkUpcomingAppointments() {
        Log.d(TAG, "Checking for upcoming appointments...")
        // TODO: Load appointments, compare time, and send email or local notifications if needed
    }

    // Creates a notification to keep the service running in foreground
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SmartReminder is running")
            .setContentText("Checking appointments and reminders")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // Creates the required notification channel for Android 8.0+
    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Appointment Reminder Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    companion object {
        private const val CHANNEL_ID = "appointment_reminders_channel"
    }
}
