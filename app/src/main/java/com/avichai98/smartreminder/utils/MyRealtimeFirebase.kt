package com.avichai98.smartreminder.utils

import android.content.ContentValues.TAG
import android.util.Log
import com.avichai98.smartreminder.models.User
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MyRealtimeFirebase private constructor(private val user: User) {

    private val userDatabaseReference: DatabaseReference =
        FirebaseDatabase.getInstance().getReference("users")
    private val remindersSentDatabaseReference: DatabaseReference =
        FirebaseDatabase.getInstance().getReference("remindersSent")

    companion object {
        @Volatile
        private var instance: MyRealtimeFirebase? = null

        fun init(user: User): MyRealtimeFirebase {
            return instance ?: synchronized(this) {
                instance ?: MyRealtimeFirebase(user).also { instance = it }
            }
        }

        fun getInstance(): MyRealtimeFirebase {
            return instance ?: throw IllegalStateException("Instance not initialized. Call init() first.")
        }

        fun resetInstance() {
            instance = null
        }
    }

    fun saveUser() {
        userDatabaseReference.child(user.uid).setValue(user)
    }

    fun userExists(callback: (Boolean) -> Unit) {
        userDatabaseReference.child(user.uid).get()
            .addOnSuccessListener { snapshot -> callback(snapshot.exists()) }
            .addOnFailureListener { callback(false) }
    }

    // Called when saving user preferences like calendar list and time
    fun updatePreferences(selectedCalendars: List<String>, minutesBefore: Int, selfReminder: Boolean) {
        user.selectedCalendarIds = selectedCalendars.toMutableList()
        user.reminderHoursBefore = minutesBefore
        user.selfReminder = selfReminder
        setUser(user)
        saveUser()
    }

    // Coroutine-based version
    suspend fun fetchUserPreferencesSuspend(): Triple<List<String>, Int, Boolean> {
        return suspendCancellableCoroutine { continuation ->
            userDatabaseReference.child(user.uid).get()
                .addOnSuccessListener { snapshot ->
                    val updatedUser = snapshot.getValue(User::class.java)
                    if (updatedUser != null) {
                        setUser(updatedUser)
                        continuation.resume(
                            Triple(
                                updatedUser.selectedCalendarIds,
                                updatedUser.reminderHoursBefore,
                                updatedUser.selfReminder
                            )
                        )

                    } else {
                        continuation.resume(Triple(emptyList(), 15, false))
                    }
                }
                .addOnFailureListener {
                    continuation.resume(Triple(emptyList(), 15, false))
                }
        }
    }

    suspend fun wasReminderSent(calendarId: String, eventId: String): Boolean {
        return suspendCancellableCoroutine { continuation ->
            remindersSentDatabaseReference
                .child(user.uid)
                .child(encodeKey(calendarId))
                .child(eventId)
                .get()
                .addOnSuccessListener { snapshot ->
                    continuation.resume(snapshot.exists())
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Failed to check reminder status: ${exception.message}")
                    continuation.resume(false)
                }
        }
    }

    fun markReminderSent(calendarId: String, eventId: String) {
        remindersSentDatabaseReference
            .child(user.uid)
            .child(encodeKey(calendarId))
            .child(eventId)
            .setValue(true)
            .addOnFailureListener {
                Log.e(TAG, "Failed to mark reminder sent: ${it.message}")
            }
    }

    //Helper to update the internal user model with latest data from Firebase
    private fun setUser(updatedUser: User) {
        user.selectedCalendarIds = updatedUser.selectedCalendarIds
        user.reminderHoursBefore = updatedUser.reminderHoursBefore
        user.selfReminder = updatedUser.selfReminder
    }

    //Encode unsafe Firebase key characters
    private fun encodeKey(key: String): String {
        return key
            .replace(".", "_dot_")
            .replace("#", "_hash_")
            .replace("$", "_dollar_")
            .replace("[", "_open_")
            .replace("]", "_close_")
    }

    fun getCurrentUserEmail(): String {
        return user.email
    }
}
