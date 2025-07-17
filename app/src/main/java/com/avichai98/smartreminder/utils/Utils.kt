package com.avichai98.smartreminder.utils

import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
}
