package com.avichai98.smartreminder.activities

import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.avichai98.smartreminder.R
import com.avichai98.smartreminder.adapters.CalendarAdapter
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
    private lateinit var calendarAdapter: CalendarAdapter
    private val utils = Utils()
    private lateinit var firebaseAuth: FirebaseAuth

    // Store previously selected calendar IDs to mark selected checkboxes
    private var previouslySelectedCalendars: Set<String> = emptySet()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar: com.google.android.material.appbar.MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        toolbar.title = getString(R.string.settings)

        // Setup the RecyclerView
        binding.calendarRecyclerView.layoutManager = LinearLayoutManager(this)

        calendarAdapter = CalendarAdapter(
            mutableListOf(),
            mutableSetOf()
        )
        binding.calendarRecyclerView.adapter = calendarAdapter

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
            binding.loadingView.tvLoading.setText(R.string.loading_calendars)
            showLoading(true)
            try {
                val (selectedCalendars, hoursBefore) =
                    MyRealtimeFirebase.getInstance().fetchUserPreferencesSuspend()
                previouslySelectedCalendars = selectedCalendars.toSet()

                binding.npHoursBefore.apply {
                    minValue = 1
                    maxValue = 100
                    wrapSelectorWheel = false
                    setFormatter { v -> String.format(java.util.Locale.getDefault(), "%d", v) }

                    val clamped = hoursBefore.coerceIn(minValue, maxValue)
                    value = clamped
                    post {
                        value = clamped
                        fixNumberPickerInput(this)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, R.string.action_failed_try_again, Toast.LENGTH_SHORT).show()
                Log.e("Settings", "loadSettings error: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    @Suppress("DiscouragedApi")
    private fun fixNumberPickerInput(np: NumberPicker) {
        try {
            val id = Resources.getSystem().getIdentifier("numberpicker_input", "id", "android")
            val input = np.findViewById<EditText>(id) ?: return
            input.textDirection = View.TEXT_DIRECTION_LOCALE
            input.textAlignment = View.TEXT_ALIGNMENT_CENTER
            input.gravity = Gravity.CENTER
            input.minEms = 3
            input.setHorizontallyScrolling(false)
            input.setPadding(0, 0, 0, 0)
        } catch (_: Exception) {
            Log.e("Settings", "Error fixing number picker input")
        }
    }

    // Load available calendars from Google Calendar API
    private fun loadCalendars() {
        CoroutineScope(Dispatchers.Main).launch {
            showLoading(true)
            try {
                val accessToken = utils.fetchAccessToken(this@SettingsActivity)
                if (accessToken == null) {
                    Toast.makeText(this@SettingsActivity, R.string.failed_to_authenticate, Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val calendarApi = Retrofit.Builder()
                    .baseUrl("https://www.googleapis.com/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(GoogleCalendarApi::class.java)

                val response = calendarApi.getCalendarList("Bearer $accessToken")
                calendarItems.clear()

                response.items.forEach { calendar ->
                    calendarItems.add(
                        CalendarItem(
                            id = calendar.id,
                            summary = calendar.summary,
                            isSelected = calendar.id in previouslySelectedCalendars
                        )
                    )
                }

                calendarAdapter.updateData(
                    calendarItems.map { it.summary },
                    calendarItems.mapIndexedNotNull { index, item -> if (item.isSelected) index else null }.toSet()
                )

            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, R.string.failed_to_load_calendars, Toast.LENGTH_SHORT).show()
                Log.e("Settings", "loadCalendars error: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    // Save selected settings to Firebase
    private fun saveSettings() {
        showLoading(true)
        try {
            val hoursBefore = binding.npHoursBefore.value.coerceIn(1, 100)
            val selfReminder = binding.selfNotification.isChecked

            val selectedCalendarIds = mutableListOf<String>()
            calendarAdapter.getSelectedPositions().forEach { pos ->
                selectedCalendarIds.add(calendarItems[pos].id)
            }

            MyRealtimeFirebase.getInstance()
                .updatePreferences(selectedCalendarIds, hoursBefore, selfReminder)

            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        } finally {
            showLoading(false)
        }
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

    private fun showLoading(show: Boolean) {
        val v = binding.loadingView.root
        if (show) {
            v.alpha = 0f
            v.visibility = View.VISIBLE
            v.animate().alpha(1f).setDuration(150).start()
        } else {
            v.animate().alpha(0f).setDuration(150).withEndAction {
                v.visibility = View.GONE
            }.start()
        }
    }
}