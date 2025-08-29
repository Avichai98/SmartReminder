package com.avichai98.smartreminder.utils

import android.content.Context
import android.text.format.DateFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Utility object for parsing and formatting date/time values
 * according to the device's locale and time zone.
 */
object DateTimeUtils {

    /** Convert RFC3339 string to ZonedDateTime in device's time zone */
    fun parseRfc3339(dateTime: String?): ZonedDateTime? {
        if (dateTime.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(dateTime).atZoneSameInstant(ZoneId.systemDefault())
        } catch (_: Exception) { null }
    }
    
    /** Best local date pattern (based on system locale) */
    private fun localDatePattern(locale: Locale): String {
        return DateFormat.getBestDateTimePattern(locale, "yMMMd")
    }

    /** Best local time pattern (12h/24h based on device settings) */
    private fun localTimePattern(ctx: Context): String {
        val is24 = DateFormat.is24HourFormat(ctx)
        val skeleton = if (is24) "Hm" else "jm"
        return DateFormat.getBestDateTimePattern(ctx.resources.configuration.locales[0], skeleton)
    }

    /** Format a ZonedDateTime to a localized date string */
    fun formatDate(zdt: ZonedDateTime, ctx: Context): String {
        val loc = ctx.resources.configuration.locales[0]
        val fmt = DateTimeFormatter.ofPattern(localDatePattern(loc), loc)
        return fmt.format(zdt)
    }

    /** Format a ZonedDateTime to a localized time string */
    fun formatTime(zdt: ZonedDateTime, ctx: Context): String {
        val loc = ctx.resources.configuration.locales[0]
        val fmt = DateTimeFormatter.ofPattern(localTimePattern(ctx), loc)
        return fmt.format(zdt)
    }

    /** Get current time in RFC3339 (ISO_OFFSET_DATE_TIME) with device time zone */
    fun nowIsoOffset(): String =
        OffsetDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}