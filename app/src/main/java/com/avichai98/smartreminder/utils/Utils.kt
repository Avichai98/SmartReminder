package com.avichai98.smartreminder.utils

import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.avichai98.smartreminder.workers.AppointmentReminderWorker
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class Utils {
    suspend fun fetchAccessToken(context: Context): String? {
        return withContext(Dispatchers.IO) {
            try {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                if (account != null) {
                    val scope = "oauth2:https://www.googleapis.com/auth/calendar"
                    account.account?.let { GoogleAuthUtil.getToken(context, it, scope) }
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting access token: ${e.localizedMessage}")
                null
            }
        }
    }

    fun scheduleReminderWorker(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<AppointmentReminderWorker>(
            15, TimeUnit.MINUTES
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "AppointmentReminderWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

}
