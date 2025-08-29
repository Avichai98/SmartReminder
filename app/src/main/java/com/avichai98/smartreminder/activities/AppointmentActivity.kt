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
import android.view.LayoutInflater
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.avichai98.smartreminder.R
import com.avichai98.smartreminder.adapters.AppointmentAdapter
import com.avichai98.smartreminder.databinding.ActivityAppointmentBinding
import com.avichai98.smartreminder.interfaces.GoogleCalendarApi
import com.avichai98.smartreminder.models.Appointment
import com.avichai98.smartreminder.models.GoogleCalendar
import com.avichai98.smartreminder.models.GoogleCalendarEvent
import com.avichai98.smartreminder.utils.MyRealtimeFirebase
import com.avichai98.smartreminder.utils.Utils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private lateinit var calendarLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionsLauncher: ActivityResultLauncher<Array<String>>
    private var pendingAction: String? =
        null // Stores the action to perform after permission is granted
    private var calendarList: List<GoogleCalendar> = emptyList()
    private var selectedCalendarId: String = "primary"
    private var afterCalendarPermissionGranted: (() -> Unit)? = null
    private var eventsObserver: android.database.ContentObserver? = null

    private val tag = "HybridSignIn"

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAppointmentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = AppointmentAdapter(appointments).apply {
            onItemClick = { appt ->
                val e = GoogleCalendarEvent(
                    id = appt.eventId,
                    iCalUID = appt.iCalUID,
                    summary = appt.summary,
                    description = appt.description,
                    location = appt.location,
                    start = appt.start,
                    end = appt.end,
                    organizer = appt.organizer,
                    attendees = appt.attendees
                )
                onEventClick(e)
            }
        }
        binding.rvAppointments.adapter = adapter
        binding.rvAppointments.layoutManager = LinearLayoutManager(this)

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
        //requestPermission("post_notifications")
    }

    override fun onStart() {
        super.onStart()
        if (hasCalendarPermission()) {
            registerEventsObserverIfNeeded()
        }
    }

    override fun onStop() {
        super.onStop()
        eventsObserver?.let { contentResolver.unregisterContentObserver(it) }
        eventsObserver = null
    }

    private fun showCreateCalendarDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_create_calendar, null)
        val til = view.findViewById<TextInputLayout>(R.id.tilCalendarName)
        val et = view.findViewById<TextInputEditText>(R.id.etCalendarName)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.create_new_calendar))
            .setView(view)
            .setPositiveButton(getString(R.string.create), null)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.setOnShowListener {
            val btn = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            btn.isEnabled = false

            fun validate() {
                val name = et.text?.toString()?.trim().orEmpty()
                val valid = name.isNotEmpty()
                til.error = if (valid) null else getString(R.string.field_required)
                btn.isEnabled = valid
            }

            et.addTextChangedListener(afterTextChanged = { validate() })
            validate()
            btn.setOnClickListener {
                val name = et.text?.toString()?.trim().orEmpty()
                if (name.isBlank()) {
                    validate(); return@setOnClickListener
                }

                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val token =
                            withContext(Dispatchers.IO) { utils.fetchAccessToken(this@AppointmentActivity) }
                        if (token.isNullOrEmpty()) {
                            til.error = getString(R.string.failed_to_authenticate)
                            return@launch
                        }
                        withContext(Dispatchers.IO) {
                            createNewCalendar(name, token)
                            fetchCalendarList()
                        }
                        dialog.dismiss()
                    } catch (_: Exception) {
                        til.error = getString(R.string.action_failed_try_again)
                    }
                }
            }
        }

        dialog.show()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun setupLaunchers() {
        calendarLauncher =
            registerForActivityResult(StartActivityForResult()) { result: ActivityResult ->
                if (result.resultCode == RESULT_OK) {
                    val account = Account(
                        MyRealtimeFirebase.getInstance().getCurrentUserEmail(),
                        "com.google"
                    )
                    val authority = "com.android.calendar"
                    val extras = Bundle().apply {
                        putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                        putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                    }
                    ContentResolver.requestSync(account, authority, extras)
                }
            }

        permissionsLauncher = registerForActivityResult(RequestMultiplePermissions()) { result ->
            val allGranted = result.values.all { it }
            val action = pendingAction
            pendingAction = null

            if (allGranted) {
                when (action) {
                    "read_calendar" -> {
                        registerEventsObserverIfNeeded()
                        afterCalendarPermissionGranted?.invoke()
                        afterCalendarPermissionGranted = null
                    }
                    // "post_notifications" – if needed
                }
                return@registerForActivityResult
            }

            // Here we know that all permissions are denied
            when (action) {
                "read_calendar" -> {
                    //  WITHOUT "Don't ask again"
                    val needRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                        this, Manifest.permission.READ_CALENDAR
                    )
                    if (needRationale) {
                        showPermissionRationaleDialogForReadCalendar(
                            onApprove = {
                                pendingAction = "read_calendar"
                                permissionsLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR))
                            }
                        )
                    } else {
                        // Don't ask again
                        showSettingsDialog()
                    }
                    afterCalendarPermissionGranted = null
                }

                "post_notifications" -> {
                    val needRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                        this, Manifest.permission.POST_NOTIFICATIONS
                    )
                    if (needRationale) {
                        showPermissionRationaleDialogGeneric(
                            title = getString(R.string.calendar_permission_title),
                            message = getString(R.string.calendar_permission_message),
                            onApprove = {
                                pendingAction = "post_notifications"
                                permissionsLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                            }
                        )
                    } else {
                        showSettingsDialog()
                    }
                }
            }
        }
    }

    private fun showSettingsDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.open_settings_title))
            .setMessage(getString(R.string.open_settings_message))
            .setPositiveButton(getString(R.string.open_settings)) { d, _ ->
                d.dismiss()
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = android.net.Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton(getString(R.string.cancel)) { d, _ -> d.dismiss() }
            .create()

        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(false)
        dialog.show()
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
            Log.d(tag, "Calendar created: ${response.id}")
        } catch (e: Exception) {
            Log.e(tag, "Failed to create calendar: ${e.localizedMessage}")
        }
    }

    private fun fetchCalendarList() {
        binding.loadingView.tvLoading.setText(R.string.loading_calendars)
        showLoading(true)
        showList(false)
        showEmpty(false)

        CoroutineScope(Dispatchers.Main).launch {
            val accessToken = utils.fetchAccessToken(this@AppointmentActivity)
            if (accessToken == null) {
                showLoading(false)
                if (adapter.itemCount == 0) showEmpty(true)
                return@launch
            }

            val retrofit = Retrofit.Builder()
                .baseUrl("https://www.googleapis.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val calendarApi = retrofit.create(GoogleCalendarApi::class.java)

            try {
                val response = calendarApi.getCalendarList("Bearer $accessToken")
                calendarList = response.items
                showLoading(false)
                showCalendarPicker()
            } catch (e: Exception) {
                Log.e(tag, "Error fetching calendar list: ${e.localizedMessage}")
                showLoading(false)
                if (adapter.itemCount == 0) {
                    showEmpty(true)
                    showList(false)
                } else {
                    showEmpty(false)
                    showList(true)
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
        // START UI STATE
        binding.loadingView.tvLoading.setText(R.string.loading_appointments)
        showLoading(true)
        showList(false)
        showEmpty(false)

        CoroutineScope(Dispatchers.Main).launch {
            val accessToken = utils.fetchAccessToken(this@AppointmentActivity)
            if (accessToken == null) {
                showLoading(false)
                if (adapter.itemCount == 0) {
                    showEmpty(true)
                    showList(false)
                }
                Log.e(tag, "Error: accessToken is null")
                return@launch
            }

            val retrofit = Retrofit.Builder()
                .baseUrl("https://www.googleapis.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val calendarApi = retrofit.create(GoogleCalendarApi::class.java)

            try {
                val response = calendarApi.getEvents(
                    authHeader = "Bearer $accessToken",
                    calendarId = selectedCalendarId,
                    timeMin = getCurrentTimeIso()
                )

                appointments.clear()
                if (response.items.isNotEmpty()) {
                    for (event in response.items) {
                        appointments.add(
                            Appointment(
                                eventId = event.id,
                                iCalUID = event.iCalUID,
                                summary = event.summary ?: "",
                                start = event.start,
                                end = event.end,
                                organizer = event.organizer,
                                attendees = event.attendees ?: emptyList(),
                                location = event.location ?: "",
                                description = event.description ?: ""
                            )
                        )
                        Log.d(tag, "Event: ${event.summary} at ${event.start.dateTime}")
                    }
                }

                adapter.notifyDataSetChanged()
                showLoading(false)
                if (appointments.isEmpty()) {
                    showEmpty(true)
                    showList(false)
                } else {
                    showEmpty(false)
                    showList(true)
                }
            } catch (e: Exception) {
                Log.e(tag, "Error fetching calendar events: ${e.localizedMessage}")

                showLoading(false)
                if (adapter.itemCount == 0) {
                    showEmpty(true)
                    showList(false)
                } else {
                    showEmpty(false)
                    showList(true)
                }
            }
        }
    }


    private fun getCurrentTimeIso(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
        return sdf.format(Date())
    }

    // RFC3339 "2025-08-23T10:00:00+03:00" -> millis
    private fun rfc3339ToMillis(dateTime: String?): Long? {
        if (dateTime.isNullOrBlank()) return null
        return try {
            java.time.Instant.parse(dateTime).toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }

    // All-day "yyyy-MM-dd" -> start of day millis
    private fun allDayDateToMillis(date: String?): Long? {
        if (date.isNullOrBlank()) return null
        return try {
            java.time.LocalDate.parse(date)
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }

    private fun eventStartMillis(e: GoogleCalendarEvent): Long? =
        rfc3339ToMillis(e.start.dateTime) ?: allDayDateToMillis(e.start.dateTime)

    private fun eventEndMillis(e: GoogleCalendarEvent): Long? =
        rfc3339ToMillis(e.end.dateTime) ?: allDayDateToMillis(e.end.dateTime)

    private suspend fun findLocalIdByICalUid(icalUid: String): Long? =
        withContext(Dispatchers.IO) {
            val projection = arrayOf(CalendarContract.Events._ID)
            val selection = "${CalendarContract.Events.UID_2445}=?"
            contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                arrayOf(icalUid),
                null
            )?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else null
            }
        }

    private fun openCalendarOnTime(startMillis: Long) {
        val uri = CalendarContract.CONTENT_URI.buildUpon()
            .appendPath("time")
            .appendPath(startMillis.toString())
            .build()
        startActivity(Intent(Intent.ACTION_VIEW).setData(uri))
    }

    private fun editLocalCalendarEvent(localEventId: Long, beginMillis: Long?, endMillis: Long?) {
        val uri = android.content.ContentUris.withAppendedId(
            CalendarContract.Events.CONTENT_URI,
            localEventId
        )
        val intent = Intent(Intent.ACTION_EDIT).setData(uri).apply {
            beginMillis?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
            endMillis?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
            setPackage("com.google.android.calendar")
        }
        try {
            startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_EDIT, uri))
        }
    }

    private fun onEventClick(e: GoogleCalendarEvent) {
        ensureReadCalendarPermission {
            val startMs = eventStartMillis(e)
            val endMs = eventEndMillis(e)

            CoroutineScope(Dispatchers.Main).launch {
                val localId = e.iCalUID?.let { findLocalIdByICalUid(it) }
                if (localId != null) {
                    editLocalCalendarEvent(localId, startMs, endMs)
                } else {
                    openCalendarOnTime(startMs ?: System.currentTimeMillis())
                }
            }
        }
    }

    private fun ensureReadCalendarPermission(then: () -> Unit) {
        val perm = Manifest.permission.READ_CALENDAR

        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
            then()
            return
        }

        val needRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, perm)
        if (needRationale) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.permission_required))
                .setMessage(getString(R.string.calendar_permission_rationale))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    pendingAction = "read_calendar"
                    afterCalendarPermissionGranted = then
                    permissionsLauncher.launch(arrayOf(perm))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            pendingAction = "read_calendar"
            afterCalendarPermissionGranted = then
            permissionsLauncher.launch(arrayOf(perm))
        }
    }

    private fun showLoading(show: Boolean) {
        binding.loadingView.root.visibility = if (show) View.VISIBLE else View.GONE
        if (show) binding.emptyState.visibility = View.GONE
    }

    private fun showEmpty(show: Boolean) {
        binding.emptyState.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showList(show: Boolean) {
        binding.rvAppointments.visibility = if (show) View.VISIBLE else View.INVISIBLE
    }

    private fun registerEventsObserverIfNeeded() {
        if (!hasCalendarPermission()) return
        if (eventsObserver != null) return

        eventsObserver = object : android.database.ContentObserver(android.os.Handler(mainLooper)) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                fetchCalendarEvents()
            }
        }
        contentResolver.registerContentObserver(
            CalendarContract.Events.CONTENT_URI,
            true,  // notifyForDescendants
            eventsObserver!!
        )
    }

    private fun hasCalendarPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

    private fun showPermissionRationaleDialogForReadCalendar(onApprove: () -> Unit) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.calendar_permission_title))
            .setMessage(getString(R.string.calendar_permission_message))
            .setPositiveButton(getString(R.string.ok)) { d, _ ->
                d.dismiss()
                onApprove()
            }
            .setNegativeButton(getString(R.string.cancel)) { d, _ ->
                d.dismiss()
            }
            .create()

        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(false)
        dialog.show()
    }

    private fun showPermissionRationaleDialogGeneric(
        title: String,
        message: String,
        onApprove: () -> Unit
    ) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(getString(R.string.ok)) { d, _ ->
                d.dismiss()
                onApprove()
            }
            .setNegativeButton(getString(R.string.cancel)) { d, _ ->
                d.dismiss()
            }
            .create()

        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(false)
        dialog.show()
    }
}
