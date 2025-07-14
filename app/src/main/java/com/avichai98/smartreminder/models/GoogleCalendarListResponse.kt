package com.avichai98.smartreminder.models

data class GoogleCalendarListResponse(
    val items: List<GoogleCalendar>
)

data class CalendarEntry(
    val id: String,
    val summary: String
)

