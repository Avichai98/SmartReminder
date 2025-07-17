package com.avichai98.smartreminder.services

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import androidx.core.app.NotificationCompat
import android.util.Log
import androidx.annotation.RequiresApi
import com.avichai98.smartreminder.R
import com.avichai98.smartreminder.interfaces.GoogleCalendarApi
import com.avichai98.smartreminder.notifications.EmailSender
import com.avichai98.smartreminder.utils.MyRealtimeFirebase
import com.avichai98.smartreminder.utils.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class AppointmentReminderService : Service() {

    private val TAG = "ReminderService"
    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval: Long = 60 * 1000 // Check every minute
    private val NOTIF_ID = 1
    private val CHANNEL_ID = "appointment_reminder_channel"
    private val utils: Utils = Utils()
    private val emailSender: EmailSender = EmailSender()

    private val onDismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            Log.d("ReminderService", "Notification dismissed - restarting foreground")
            // Restart the service in foreground after swipe
            Handler(Looper.getMainLooper()).postDelayed({
                startForeground(NOTIF_ID, createNotification())
            }, 60 * 1000)
        }
    }

    // Runnable that checks for appointments periodically
    private val checkAppointmentsRunnable = object : Runnable {
        override fun run() {
            loadUserPreferencesAndCheck()
            handler.postDelayed(this, checkInterval)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerReceiver(
            onDismissReceiver,
            IntentFilter("com.avichai98.smartreminder.NOTIF_DISMISSED"),
            RECEIVER_NOT_EXPORTED // This fixes the Android 13+ error
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start the service in foreground with persistent notification
        startForeground(NOTIF_ID, createNotification())

        // Start the periodic check for upcoming appointments
        handler.post(checkAppointmentsRunnable)

        return START_STICKY
    }


    override fun onDestroy() {
        // Remove any pending callbacks to prevent memory leaks or duplicate execution
        handler.removeCallbacks(checkAppointmentsRunnable)

        // Unregister the broadcast receiver for swipe-dismiss of the notification
        unregisterReceiver(onDismissReceiver)

        super.onDestroy()
    }


    override fun onBind(intent: Intent?): IBinder? = null

    // Logic to check for upcoming appointments and trigger reminders
    private fun checkUpcomingAppointments(calendarId: String, timeBeforeHours: Int, selfReminder: Boolean) {
        Log.d(TAG, "Checking appointments for calendar $calendarId with reminder $timeBeforeHours hours before")

        CoroutineScope(Dispatchers.IO).launch {
            val accessToken = utils.fetchAccessToken(this@AppointmentReminderService)
            if (accessToken == null) {
                Log.e(TAG, "Failed to fetch access token")
                return@launch
            }

            val calendarApi = Retrofit.Builder()
                .baseUrl("https://www.googleapis.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(GoogleCalendarApi::class.java)

            try {
                val now = ZonedDateTime.now()
                val timeMin = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                val timeMax = now.plusHours(timeBeforeHours.toLong()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

                val response = calendarApi.getEvents(
                    authHeader = "Bearer $accessToken",
                    calendarId = calendarId,
                    timeMin = timeMin,
                    timeMax = timeMax
                )

                Log.d(TAG, "Found ${response.items.size} upcoming events for calendar $calendarId")

                for (event in response.items) {

                    val reminderAlreadySent = withContext(Dispatchers.IO) {
                        MyRealtimeFirebase.getInstance().wasReminderSent(calendarId, event.id)
                    }

                    if (reminderAlreadySent) continue

                    val isPrimary = calendarId == "primary" || calendarId == MyRealtimeFirebase.getInstance().getCurrentUserEmail()

                    if (isPrimary &&
                        event.organizer!!.email != MyRealtimeFirebase.getInstance().getCurrentUserEmail())
                        continue


                    val title = event.summary ?: "No title"
                    val time = event.start.dateTime ?: continue
                    val attendees = event.attendees?.mapNotNull { it.email } ?: emptyList()

                    if (attendees.isEmpty()) {
                        Log.w(TAG, "Event $title has no attendees")
                        continue
                    }

                    var allEmailsSent = true

                    attendees.forEach { email ->
                        if (!selfReminder && email == MyRealtimeFirebase.getInstance().getCurrentUserEmail())
                            return@forEach

                        val sent = emailSender.sendEmail(
                            subject = "Reminder: $title",
                            body = "This is a reminder for \"$title\" scheduled at $time",
                            recipientEmail = email
                        )
                        if (!sent) allEmailsSent = false
                    }

                    if (allEmailsSent) {
                        MyRealtimeFirebase.getInstance().markReminderSent(calendarId, event.id)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching events for calendar $calendarId: ${e.localizedMessage}")
            }
        }
    }



    // Creates a notification to keep the service running in foreground
    private fun createNotification(): Notification {
        val dismissIntent = Intent("com.avichai98.smartreminder.NOTIF_DISMISSED").apply {
            setPackage(packageName)
        }
        val dismissPI = PendingIntent.getBroadcast(
            this, 0, dismissIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SmartReminder is running")
            .setContentText("Checking appointments and reminders")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // Still allows swipe on Android 13+
            .setDeleteIntent(dismissPI) // <- triggers broadcast on swipe
            .setAutoCancel(false)
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

    private fun loadUserPreferencesAndCheck() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val (calendarIds, hoursBefore, selfReminder) = MyRealtimeFirebase.getInstance().fetchUserPreferencesSuspend()
                calendarIds.forEach {
                    checkUpcomingAppointments(it, hoursBefore, selfReminder)
                }
            } catch (e: Exception) {
                Log.e("ReminderService", "Failed to load user preferences: ${e.message}")
            }
        }
    }

}
