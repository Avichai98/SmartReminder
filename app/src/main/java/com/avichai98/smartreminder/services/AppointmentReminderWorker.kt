package com.avichai98.smartreminder.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.avichai98.smartreminder.notifications.EmailSender
import com.avichai98.smartreminder.utils.MyRealtimeFirebase
import com.avichai98.smartreminder.utils.Utils
import com.avichai98.smartreminder.interfaces.GoogleCalendarApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class AppointmentReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val TAG = "ReminderWorker"
    private val utils = Utils()
    private val emailSender = EmailSender()

    override suspend fun doWork(): Result {
        try {
            val (calendarIds, hoursBefore, selfReminder) =
                MyRealtimeFirebase.getInstance().fetchUserPreferencesSuspend()

            for (calendarId in calendarIds) {
                checkUpcomingAppointments(calendarId, hoursBefore, selfReminder)
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check appointments: ${e.message}")
            return Result.retry()
        }
    }

    private suspend fun checkUpcomingAppointments(calendarId: String, timeBeforeHours: Int, selfReminder: Boolean) {
        Log.d(TAG, "Checking appointments for $calendarId")

        val accessToken = utils.fetchAccessToken(applicationContext) ?: run {
            Log.e(TAG, "Access token null")
            return
        }

        val calendarApi = Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GoogleCalendarApi::class.java)

        val now = ZonedDateTime.now()
        val timeMin = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val timeMax = now.plusHours(timeBeforeHours.toLong()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        try {
            val response = calendarApi.getEvents(
                authHeader = "Bearer $accessToken",
                calendarId = calendarId,
                timeMin = timeMin,
                timeMax = timeMax
            )

            Log.d(TAG, "Found ${response.items.size} events for $calendarId")

            for (event in response.items) {
                val reminderAlreadySent = withContext(Dispatchers.IO) {
                    MyRealtimeFirebase.getInstance().wasReminderSent(calendarId, event.id)
                }
                if (reminderAlreadySent) continue

                val isPrimary = calendarId == "primary" ||
                        calendarId == MyRealtimeFirebase.getInstance().getCurrentUserEmail()

                if (isPrimary && event.organizer?.email != MyRealtimeFirebase.getInstance().getCurrentUserEmail())
                    continue

                val title = event.summary ?: "No title"
                val time = event.start.dateTime ?: continue
                val attendees = event.attendees?.mapNotNull { it.email } ?: emptyList()

                if (attendees.isEmpty()) continue

                var allEmailsSent = true
                for (email in attendees) {
                    if (!selfReminder && email == MyRealtimeFirebase.getInstance().getCurrentUserEmail())
                        continue

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
            Log.e(TAG, "Error checking appointments: ${e.message}")
        }
    }
}
