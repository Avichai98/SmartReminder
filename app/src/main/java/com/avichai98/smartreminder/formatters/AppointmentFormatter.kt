package com.avichai98.smartreminder.formatters

import android.content.Context
import com.avichai98.smartreminder.models.Appointment
import com.avichai98.smartreminder.utils.DateTimeUtils
import java.time.ZonedDateTime

/**
 * Presentation helpers for Appointment — keeps adapters/activities clean.
 * All methods are pure formatters: they never mutate state.
 */
object AppointmentFormatter {

    /**
     * Parses the appointment START into a ZonedDateTime in device time zone.
     * Supports both timed events (start.dateTime) and all-day (start.date).
     */
    private fun startZdt(appt: Appointment): ZonedDateTime? {
        // Adjust property names if your model uses different field names
        val rfc3339 = appt.start.dateTime
        return DateTimeUtils.parseRfc3339(rfc3339)
    }

    /** Localized DATE for the appointment start (locale + device zone). */
    fun localizedStartDate(ctx: Context, appt: Appointment): String {
        val zdt = startZdt(appt) ?: return ""
        return DateTimeUtils.formatDate(zdt, ctx)
    }

    /** Localized TIME for the appointment start (locale + 12/24h user pref). */
    fun localizedStartTime(ctx: Context, appt: Appointment): String {
        val zdt = startZdt(appt) ?: return ""
        return DateTimeUtils.formatTime(zdt, ctx)
    }
}
