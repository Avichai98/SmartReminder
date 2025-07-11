package com.avichai98.smartreminder.models

import java.util.UUID


data class Appointment(
    var id: String = UUID.randomUUID().toString(),
    var title: String,
    var date: String,
    var time: String,
    var durationMinutes: Int,
    var customerName: String,
    var customerEmail: String,
    var location: String? = null
)