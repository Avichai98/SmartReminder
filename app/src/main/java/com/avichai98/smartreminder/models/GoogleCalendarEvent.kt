package com.avichai98.smartreminder.models

data class GoogleCalendarEvent(
    val id: String,
    val summary: String?,
    val description: String?,
    val location: String?,
    val start: GoogleCalendarStart,
    val end: GoogleCalendarStart,
    val organizer: Organizer?,
    val attendees: List<Attendee>?
)