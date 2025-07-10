package com.avichai98.smartreminder.models

import java.util.UUID

data class GoogleCalendar(
    val id: String = UUID.randomUUID().toString(),
    val summary: String // Example: Manicure, Doctor, Study, etc.
)