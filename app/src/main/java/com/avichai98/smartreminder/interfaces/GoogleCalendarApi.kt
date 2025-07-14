package com.avichai98.smartreminder.interfaces

import com.avichai98.smartreminder.models.GoogleCalendar
import com.avichai98.smartreminder.models.GoogleCalendarEvent
import com.avichai98.smartreminder.models.GoogleCalendarEventRequest
import com.avichai98.smartreminder.models.GoogleCalendarListResponse
import com.avichai98.smartreminder.models.GoogleCalendarResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GoogleCalendarApi {
    @POST("calendar/v3/calendars/{calendarId}/events")
    suspend fun addEvent(
        @Header("Authorization") authHeader: String,
        @Path("calendarId") calendarId: String,
        @Body event: GoogleCalendarEventRequest
    ): GoogleCalendarEvent

    @POST("calendar/v3/calendars")
    suspend fun createCalendar(
        @Header("Authorization") authHeader: String,
        @Body calendarBody: Map<String, String>
    ): GoogleCalendar

    @GET("calendar/v3/calendars/primary/events")
    suspend fun getEvents(
        @Header("Authorization") authHeader: String,
        @Query("maxResults") maxResults: Int = 2500,
        @Query("orderBy") orderBy: String = "startTime",
        @Query("singleEvents") singleEvents: Boolean = true,
        @Query("timeMin") timeMin: String
    ): GoogleCalendarResponse

    @GET("calendar/v3/users/me/calendarList")
    suspend fun getCalendarList(
        @Header("Authorization") authHeader: String
    ): GoogleCalendarListResponse

    @GET("calendar/v3/calendars/{calendarId}/events")
    suspend fun getEvents(
        @Header("Authorization") authHeader: String,
        @Path("calendarId") calendarId: String,
        @Query("maxResults") maxResults: Int = 2500,
        @Query("orderBy") orderBy: String = "startTime",
        @Query("singleEvents") singleEvents: Boolean = true,
        @Query("timeMin") timeMin: String,
        @Query("timeMax") timeMax: String? = null
    ): GoogleCalendarResponse
}
