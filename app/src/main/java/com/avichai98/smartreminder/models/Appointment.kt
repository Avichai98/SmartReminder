package com.avichai98.smartreminder.models

data class Appointment(
    val eventId: String,                          // Google Calendar Event ID
    val summary: String?,                        // Event title
    val description: String?,                    // Event description
    val location: String?,                       // Location string
    val start: GoogleCalendarStart,              // Start datetime wrapper
    val end: GoogleCalendarStart,                // End datetime wrapper
    val organizer: Organizer?,                   // Organizer (usually current user)
    val attendees: List<Attendee>?               // List of participants
) {
    // Convenience methods for UI binding:
    fun getStartDate(): String {
        return start.dateTime?.split("T")?.getOrNull(0) ?: ""
    }

    fun getStartTime(): String {
        return start.dateTime?.split("T")?.getOrNull(1)?.substring(0, 5) ?: ""
    }

    fun getDurationMinutes(): Int {
        val startTime = start.dateTime?.substring(0, 19) ?: return 0
        val endTime = end.dateTime?.substring(0, 19) ?: return 0
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
        val startDate = sdf.parse(startTime)
        val endDate = sdf.parse(endTime)
        val durationMillis = (endDate?.time ?: 0) - (startDate?.time ?: 0)
        return (durationMillis / (60 * 1000)).toInt()
    }

    fun getAttendeeEmails(): String {
        return attendees?.joinToString(", ") { it.email ?: "" } ?: ""
    }

    fun getOrganizerName(): String {
        return organizer?.displayName ?: "Unknown"
    }
}