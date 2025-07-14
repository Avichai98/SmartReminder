package com.avichai98.smartreminder.models

data class User(
    var uid: String = "",
    var email: String = "",
    var selectedCalendarIds: MutableList<String> = mutableListOf("primary"), // Default: primary calendar
    var reminderHoursBefore: Int = 24,          // Default: 24 hours before appointment
)
