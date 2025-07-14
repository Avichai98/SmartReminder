package com.avichai98.smartreminder.models

data class CalendarItem(
    val id: String,
    val summary: String,
    var isSelected: Boolean = false
)
