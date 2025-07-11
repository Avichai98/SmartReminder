package com.avichai98.smartreminder.activities

import com.avichai98.smartreminder.interfaces.GoogleCalendarApi
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.avichai98.smartreminder.R
import com.avichai98.smartreminder.adapters.AppointmentAdapter
import com.avichai98.smartreminder.databinding.ActivityAppointmentBinding
import com.avichai98.smartreminder.models.GoogleCalendar
import com.avichai98.smartreminder.models.Appointment
import com.avichai98.smartreminder.utils.PermissionManager
import com.avichai98.smartreminder.utils.Utils
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class AppointmentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppointmentBinding
    private lateinit var permissionManager: PermissionManager
    private val appointments = mutableListOf<Appointment>()
    private val utils = Utils()
    private lateinit var adapter: AppointmentAdapter

    private var calendarList: List<GoogleCalendar> = emptyList()
    private var selectedCalendarId: String = "primary"

    private val TAG = "HybridSignIn"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppointmentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = AppointmentAdapter(appointments,
            onDeleteClick = { appointment ->
                appointments.remove(appointment)
                adapter.notifyDataSetChanged()
            },
            onUpdateClick = { appointment ->
                showUpdateAppointmentDialog(this, appointment) { updated ->
                    val index = appointments.indexOfFirst { it.id == updated.id }
                    if (index != -1) {
                        appointments[index] = updated
                        adapter.notifyItemChanged(index)
                    }
                }
            }
        )

        binding.rvAppointments.layoutManager = LinearLayoutManager(this)
        binding.rvAppointments.adapter = adapter

        binding.fabAddAppointment.setOnClickListener {
            showAddAppointmentDialog(this) { newAppointment ->

                appointments.add(newAppointment)
                adapter.notifyItemInserted(appointments.size - 1)

                CoroutineScope(Dispatchers.Main).launch {
                    val accessToken = fetchAccessToken()
                    if (accessToken != null) {
                        utils.addEventToGoogleCalendar(newAppointment, accessToken)
                    }
                }
            }
        }

        binding.btnCreateCalendar.setOnClickListener {
            showCreateCalendarDialog()
        }

        binding.btnSelectCalendar.setOnClickListener {
            fetchCalendarList()
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            // Permission granted
        } else {
            permissionManager.openAppSettings()
        }
    }

    private suspend fun fetchAccessToken(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val account = GoogleSignIn.getLastSignedInAccount(this@AppointmentActivity)
                if (account != null) {
                    val scope = "oauth2:https://www.googleapis.com/auth/calendar"
                    account.account?.let { GoogleAuthUtil.getToken(this@AppointmentActivity, it, scope) }
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting access token: ${e.localizedMessage}")
                null
            }
        }
    }

    private fun showCreateCalendarDialog() {
        val input = EditText(this)
        input.hint = "Enter calendar name"

        AlertDialog.Builder(this)
            .setTitle("New Calendar")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString()
                if (name.isNotBlank()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val accessToken = fetchAccessToken()
                        accessToken?.let {
                            createNewCalendar(name, it)
                            fetchCalendarList()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
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
            val accessToken = fetchAccessToken()
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
            .setTitle("Select Calendar")
            .setItems(calendarNames) { _, which ->
                selectedCalendarId = calendarList[which].id
                fetchCalendarEvents() // Load events for the selected calendar
            }
            .show()
    }

    private fun fetchCalendarEvents() {
        CoroutineScope(Dispatchers.Main).launch {
            val accessToken = fetchAccessToken()
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

                            // Split date and time
                            val date = startDateTime.split("T")[0]
                            val time = startDateTime.split("T")[1].substring(0, 5) // Get HH:mm

                            // Calculate duration
                            val durationMinutes = calculateDuration(startDateTime, endDateTime)

                            // Extract attendees' emails (if available)
                            val attendeesEmails = event.attendees?.mapNotNull { it.email } ?: emptyList()

                            // Create appointment
                            appointments.add(
                                Appointment(
                                    title = event.summary ?: "No Title",
                                    date = date,
                                    time = time,
                                    durationMinutes = durationMinutes,
                                    customerName = event.organizer?.displayName ?: "Unknown",
                                    customerEmail = attendeesEmails.joinToString(", "), // Combine all emails as string
                                    location = event.location ?: "No Location"
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

    private fun calculateDuration(startDateTime: String, endDateTime: String): Int {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val start = sdf.parse(startDateTime.substring(0, 19))!!
        val end = sdf.parse(endDateTime.substring(0, 19))!!
        val durationMillis = end.time - start.time
        return (durationMillis / (60 * 1000)).toInt() // Duration in minutes
    }

    private fun getCurrentTimeIso(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun showAddAppointmentDialog(context: Context, onSave: (Appointment) -> Unit) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_appointment, null)
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setTitle("Add Appointment")
            .create()

        val etTitle = dialogView.findViewById<EditText>(R.id.etTitle)
        val etDate = dialogView.findViewById<EditText>(R.id.etDate)
        val etTime = dialogView.findViewById<EditText>(R.id.etTime)
        val etDuration = dialogView.findViewById<EditText>(R.id.etDuration)
        val etLocation = dialogView.findViewById<EditText>(R.id.etLocation)
        val etCustomerName = dialogView.findViewById<EditText>(R.id.etCustomerName)
        val etCustomerEmail = dialogView.findViewById<EditText>(R.id.etCustomerEmail)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSave)

        // Show Date Picker
        etDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            val datePicker = DatePickerDialog(context, { _, year, month, dayOfMonth ->
                val selectedDate = String.format("%02d-%02d-%04d", dayOfMonth, month + 1, year)
                etDate.setText(selectedDate)
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
            datePicker.show()
        }

        // Show Time Picker
        etTime.setOnClickListener {
            val calendar = Calendar.getInstance()
            val timePicker = TimePickerDialog(context, { _, hourOfDay, minute ->
                val selectedTime = String.format("%02d:%02d", hourOfDay, minute)
                etTime.setText(selectedTime)
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true)
            timePicker.show()
        }

        // Show Duration Picker
        etDuration.setOnClickListener {
            val durations = arrayOf("15", "30", "45", "60", "90", "120")
            AlertDialog.Builder(context)
                .setTitle("Select Duration (minutes)")
                .setItems(durations) { _, which ->
                    etDuration.setText(durations[which])
                }
                .show()
        }

        btnSave.setOnClickListener {
            val appointment = Appointment(
                title = etTitle.text.toString(),
                date = etDate.text.toString(),
                time = etTime.text.toString(),
                durationMinutes = etDuration.text.toString().toIntOrNull() ?: 60,
                customerName = etCustomerName.text.toString(),
                customerEmail = etCustomerEmail.text.toString(),
                location = etLocation.text.toString()
            )
            onSave(appointment)
            dialog.dismiss()
        }

        dialog.show()
    }


    private fun showUpdateAppointmentDialog(context: Context, appointment: Appointment, onUpdate: (Appointment) -> Unit) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_appointment, null)
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setTitle("Update Appointment")
            .create()

        val etTitle = dialogView.findViewById<EditText>(R.id.etTitle)
        val etDate = dialogView.findViewById<EditText>(R.id.etDate)
        val etTime = dialogView.findViewById<EditText>(R.id.etTime)
        val etDuration = dialogView.findViewById<EditText>(R.id.etDuration)
        val etLocation = dialogView.findViewById<EditText>(R.id.etLocation)
        val etCustomerName = dialogView.findViewById<EditText>(R.id.etCustomerName)
        val etCustomerEmail = dialogView.findViewById<EditText>(R.id.etCustomerEmail)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSave)

        etTitle.setText(appointment.title)
        etDate.setText(appointment.date)
        etTime.setText(appointment.time)
        etDuration.setText(appointment.durationMinutes.toString())
        etLocation.setText(appointment.location)
        etCustomerName.setText(appointment.customerName)
        etCustomerEmail.setText(appointment.customerEmail)


        btnSave.setOnClickListener {
            val updatedAppointment = appointment.copy(
                title = etTitle.text.toString(),
                date = etDate.text.toString(),
                time = etTime.text.toString(),
                durationMinutes = etDuration.text.toString().toIntOrNull() ?: 60,
                customerName = etCustomerName.text.toString(),
                customerEmail = etCustomerEmail.text.toString(),
                location = etLocation.text.toString()
            )
            onUpdate(updatedAppointment)
            dialog.dismiss()
        }

        dialog.show()
    }
}