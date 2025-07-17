package com.avichai98.smartreminder.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.avichai98.smartreminder.R
import com.avichai98.smartreminder.databinding.ActivitySettingsBinding
import com.avichai98.smartreminder.interfaces.GoogleCalendarApi
import com.avichai98.smartreminder.models.CalendarItem
import com.avichai98.smartreminder.utils.MyRealtimeFirebase
import com.avichai98.smartreminder.utils.Utils
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val calendarItems = mutableListOf<CalendarItem>()
    private lateinit var adapter: ArrayAdapter<String>
    private val utils = Utils()
    private lateinit var firebaseAuth: FirebaseAuth

    // Store previously selected calendar IDs to mark selected checkboxes
    private var previouslySelectedCalendars: Set<String> = emptySet()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup the list view adapter
        adapter = ArrayAdapter(this, R.layout.list_item_calendar)
        binding.calendarListView.adapter = adapter

        // Load user preferences and calendars
        loadSettings()
        loadCalendars()

        // Save button click
        binding.btnSaveSettings.setOnClickListener { saveSettings() }

        // Logout button click
        binding.btnLogout.setOnClickListener { logout() }
    }

    // Load minutesBefore and previously selected calendars from Firebase
    private fun loadSettings() {
        CoroutineScope(Dispatchers.Main).launch {
            val (selectedCalendars, hoursBefore) =
                MyRealtimeFirebase.getInstance().fetchUserPreferencesSuspend()
            previouslySelectedCalendars = selectedCalendars.toSet()
            binding.etHoursBefore.setText(hoursBefore.toString())
        }
    }

    // Load available calendars from Google Calendar API
    private fun loadCalendars() {
        CoroutineScope(Dispatchers.Main).launch {
            val accessToken = utils.fetchAccessToken(this@SettingsActivity)
            if (accessToken == null) {
                Toast.makeText(this@SettingsActivity, "Failed to fetch token", Toast.LENGTH_SHORT)
                    .show()
                return@launch
            }

            val calendarApi = Retrofit.Builder()
                .baseUrl("https://www.googleapis.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(GoogleCalendarApi::class.java)

            try {
                val response = calendarApi.getCalendarList("Bearer $accessToken")
                calendarItems.clear()

                // Fill calendarItems list with calendar data
                response.items.forEach { calendar ->
                    calendarItems.add(
                        CalendarItem(
                            id = calendar.id,
                            summary = calendar.summary,
                            isSelected = calendar.id in previouslySelectedCalendars
                        )
                    )
                }

                // Display names in ListView
                adapter.clear()
                adapter.addAll(calendarItems.map { it.summary })

                // Mark previously selected calendars as checked
                calendarItems.forEachIndexed { index, item ->
                    binding.calendarListView.setItemChecked(index, item.isSelected)
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@SettingsActivity,
                    "Failed to load calendars",
                    Toast.LENGTH_SHORT
                ).show()
                Log.e("Settings", "Error loading calendars: ${e.message}")
            }
        }
    }

    // Save selected settings to Firebase
    private fun saveSettings() {
        val hoursBefore = binding.etHoursBefore.text.toString().toIntOrNull() ?: 24
        val selfReminder = binding.selfNotification.isChecked

        val selectedCalendarIds = mutableListOf<String>()
        for (i in calendarItems.indices) {
            if (binding.calendarListView.isItemChecked(i)) {
                selectedCalendarIds.add(calendarItems[i].id)
            }
        }

        MyRealtimeFirebase.getInstance()
            .updatePreferences(selectedCalendarIds, hoursBefore, selfReminder)
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
    }

    private fun logout() {
        firebaseAuth = FirebaseAuth.getInstance()
        if (firebaseAuth.currentUser != null) {
            firebaseAuth.signOut()
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
            MyRealtimeFirebase.resetInstance()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
