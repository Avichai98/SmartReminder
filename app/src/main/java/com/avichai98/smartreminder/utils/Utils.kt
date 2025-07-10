package com.avichai98.smartreminder.utils

import GoogleCalendarApi
import android.content.ContentValues.TAG
import android.util.Log
import com.avichai98.smartreminder.models.GoogleCalendarEventRequest
import com.avichai98.smartreminder.models.TimeObject
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class Utils {

    // Check if the appointment is within the next 24 hours
    fun isAppointmentInNext24Hours(appointmentDate: String, appointmentTime: String): Boolean {
        val sdf = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        val appointmentDateTime = sdf.parse("$appointmentDate $appointmentTime")
        val now = Date()
        val diff = appointmentDateTime.time - now.time
        return diff in 0..(24 * 60 * 60 * 1000)
    }

    // Add event to Google Calendar using Google Calendar API
    fun addEventToGoogleCalendar(appointment: Appointment, accessToken: String) {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val calendarApi = retrofit.create(GoogleCalendarApi::class.java)

        val userTimeZone = TimeZone.getDefault().id

        val eventRequest = GoogleCalendarEventRequest(
            summary = appointment.title,
            location = appointment.location,
            description = "${appointment.customerName} - ${appointment.customerEmail}",
            start = TimeObject(
                dateTime = formatDateTimeForGoogle(appointment.date, appointment.time),
                timeZone = userTimeZone
            ),
            end = TimeObject(
                dateTime = calculateEndTime(appointment.date, appointment.time, appointment.durationMinutes),
                timeZone = userTimeZone
            )
        )

        Log.d(TAG, "Event request: ${Gson().toJson(eventRequest)}")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = calendarApi.addEvent(
                    authHeader = "Bearer $accessToken",
                    calendarId = "primary",
                    event = eventRequest
                )
                Log.d(TAG, "Event added: ${response.summary}")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding event: ${e.localizedMessage}")
            }
        }
    }

    // Format start date-time to Google's required format
    private fun formatDateTimeForGoogle(date: String, time: String): String {
        val inputFormat = SimpleDateFormat("dd-MM-yyyy'T'HH:mm", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getDefault()

        val dateTime = inputFormat.parse("${date}T${time}")

        val outputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
        outputFormat.timeZone = TimeZone.getDefault()

        return outputFormat.format(dateTime!!)
    }

    // Calculate end date-time using the duration of the appointment
    private fun calculateEndTime(date: String, time: String, durationMinutes: Int): String {
        val inputFormat = SimpleDateFormat("dd-MM-yyyy'T'HH:mm", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getDefault()

        val startDateTime = inputFormat.parse("${date}T${time}")
        val calendar = Calendar.getInstance()
        calendar.time = startDateTime!!
        calendar.add(Calendar.MINUTE, durationMinutes)

        val outputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
        outputFormat.timeZone = TimeZone.getDefault()

        return outputFormat.format(calendar.time)
    }
}
