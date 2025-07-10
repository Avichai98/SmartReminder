package com.avichai98.smartreminder.models

data class GoogleCalendarEventRequest(
    val summary: String,
    val location: String?,
    val description: String,
    val start: TimeObject,
    val end: TimeObject
)

data class TimeObject(
    val dateTime: String,
    val timeZone: String
)
