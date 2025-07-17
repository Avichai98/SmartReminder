package com.avichai98.smartreminder.activities

import android.Manifest
import android.accounts.Account
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.util.Log
import android.widget.EditText
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.avichai98.smartreminder.R
import com.avichai98.smartreminder.adapters.AppointmentAdapter
import com.avichai98.smartreminder.databinding.ActivityAppointmentBinding
import com.avichai98.smartreminder.interfaces.GoogleCalendarApi
import com.avichai98.smartreminder.models.Appointment
import com.avichai98.smartreminder.models.GoogleCalendar
import com.avichai98.smartreminder.services.AppointmentReminderService
import com.avichai98.smartreminder.utils.MyRealtimeFirebase
import com.avichai98.smartreminder.utils.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class AppointmentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppointmentBinding
    private lateinit var adapter: AppointmentAdapter
    private val appointments = mutableListOf<Appointment>()
    private val utils = Utils()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val POST_NOTIFICATION_PERMISSION = Manifest.permission.POST_NOTIFICATIONS
    private lateinit var calendarLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionsLauncher: ActivityResultLauncher<Array<String>>
    private var pendingAction: String? = null // Stores the action to perform after permission is granted
    private var calendarList: List<GoogleCalendar> = emptyList()
    private var selectedCalendarId: String = "primary"

    private val TAG = "HybridSignIn"

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAppointmentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = AppointmentAdapter(
            appointments,
        )

        binding.rvAppointments.layoutManager = LinearLayoutManager(this)
        binding.rvAppointments.adapter = adapter

        binding.fabAddAppointment.setOnClickListener {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
            }
            calendarLauncher.launch(intent)
        }

        binding.btnCreateCalendar.setOnClickListener { showCreateCalendarDialog() }
        binding.btnSelectCalendar.setOnClickListener { fetchCalendarList() }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        setupLaunchers() // Initialize ActivityResultLauncher
        requestPermission("post_notifications")
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestPermission(action: String) {
        pendingAction = action

        when (action) {
            "post_notifications" -> {
                if (ContextCompat.checkSelfPermission(this, POST_NOTIFICATION_PERMISSION)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    permissionsLauncher.launch(arrayOf(POST_NOTIFICATION_PERMISSION))
                } else {
                    startReminderService()
                }
            }
            else -> Log.e("AppointmentActivity", "Invalid action: $action")
        }
    }

    private fun showCreateCalendarDialog() {
        val input = EditText(this)
        input.hint = R.string.enter_calendar_name.toString()

        AlertDialog.Builder(this)
            .setTitle(R.string.create_new_calendar)
            .setView(input)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = input.text.toString()
                if (name.isNotBlank()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val accessToken = utils.fetchAccessToken(this@AppointmentActivity)
                        accessToken?.let {
                            createNewCalendar(name, it)
                            fetchCalendarList()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun setupLaunchers() {
        calendarLauncher = registerForActivityResult(
            StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK) {
                val account = Account(MyRealtimeFirebase.getInstance().getCurrentUserEmail(), "com.google")
                val authority = "com.android.calendar"

                val extras = Bundle().apply {
                    putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                    putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                }

                ContentResolver.requestSync(account, authority, extras)
            }
        }

        permissionsLauncher = registerForActivityResult(RequestMultiplePermissions()) { result ->
            var allGranted = true
            var shouldShowRationale = false

            for ((permission, granted) in result) {
                if (!granted) {
                    allGranted = false
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                        shouldShowRationale = true
                    }
                }
            }

            if (allGranted) {
                when (pendingAction) {
                    "post_notifications" -> startReminderService()
                }
            } else if (shouldShowRationale) {
                showPermissionRationaleDialog()
            } else {
                showSettingsDialog()
            }

            pendingAction = null
        }
    }

    private fun startReminderService() {
        val intent = Intent(this, AppointmentReminderService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("This permission is needed for the reminders to work.")
            .setPositiveButton("OK") { _, _ ->
                requestPermission(pendingAction ?: "")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_denied)
            .setMessage(R.string.after_permission_denied)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = android.net.Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private suspend fun createNewCalendar(name: String, accessToken: String) {
        try {
            val retrofit = Retrofit.Builder()
                .baseUrl("https://www.googleapis.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val calendarApi = retrofit.create(GoogleCalendarApi::class.java)
            val newCalendar = mapOf(
                "summary" to name,
                "timeZone" to TimeZone.getDefault().id
            )
            val response = calendarApi.createCalendar("Bearer $accessToken", newCalendar)
            Log.d(TAG, "Calendar created: ${response.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create calendar: ${e.localizedMessage}")
        }
    }

    private fun fetchCalendarList() {
        CoroutineScope(Dispatchers.Main).launch {
            val accessToken = utils.fetchAccessToken(this@AppointmentActivity)
            if (accessToken != null) {
                val retrofit = Retrofit.Builder()
                    .baseUrl("https://www.googleapis.com/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

                val calendarApi = retrofit.create(GoogleCalendarApi::class.java)

                try {
                    val response = calendarApi.getCalendarList("Bearer $accessToken")
                    calendarList = response.items
                    showCalendarPicker()
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching calendar list: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun showCalendarPicker() {
        val calendarNames = calendarList.map { it.summary }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.select_calendar)
            .setItems(calendarNames) { _, which ->
                selectedCalendarId = calendarList[which].id
                fetchCalendarEvents() // Load events for the selected calendar
            }
            .show()
    }

    private fun fetchCalendarEvents() {
        CoroutineScope(Dispatchers.Main).launch {
            val accessToken = utils.fetchAccessToken(this@AppointmentActivity)
            if (accessToken != null) {
                val retrofit = Retrofit.Builder()
                    .baseUrl("https://www.googleapis.com/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

                val calendarApi = retrofit.create(GoogleCalendarApi::class.java)

                try {
                    val response = calendarApi.getEvents(
                        authHeader = "Bearer $accessToken",
                        calendarId = selectedCalendarId, // Load from the selected calendar
                        timeMin = getCurrentTimeIso()
                    )

                    appointments.clear()
                    if (response.items.isNotEmpty()) {
                        for (event in response.items) {
                            val startDateTime = event.start.dateTime ?: continue
                            val endDateTime = event.end.dateTime ?: continue

                            // Create appointment
                            appointments.add(
                                Appointment(
                                    eventId = event.id,
                                    summary = event.summary ?: "No Title",
                                    start = event.start,
                                    end = event.end,
                                    organizer = event.organizer,
                                    attendees = event.attendees ?: emptyList(), // Combine all emails as string
                                    location = event.location ?: "No Location",
                                    description = event.description ?: "No Description"
                                )
                            )

                            Log.d(TAG, "Event: ${event.summary} at ${event.start.dateTime}")
                        }
                        adapter.notifyDataSetChanged()
                    } else {
                        Log.d(TAG, "No upcoming events found.")
                        adapter.notifyDataSetChanged()
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching calendar events: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun getCurrentTimeIso(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
        return sdf.format(Date())
    }
}