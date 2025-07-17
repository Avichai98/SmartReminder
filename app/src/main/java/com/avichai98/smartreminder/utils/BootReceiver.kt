package com.avichai98.smartreminder.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.avichai98.smartreminder.services.AppointmentReminderService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, AppointmentReminderService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}