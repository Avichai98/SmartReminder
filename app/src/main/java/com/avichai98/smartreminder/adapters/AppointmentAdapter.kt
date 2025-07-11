package com.avichai98.smartreminder.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.avichai98.smartreminder.R
import com.avichai98.smartreminder.models.Appointment

class AppointmentAdapter(
    private val appointments: MutableList<Appointment>,
    private val onDeleteClick: (Appointment) -> Unit,
    private val onUpdateClick: (Appointment) -> Unit
) : RecyclerView.Adapter<AppointmentAdapter.AppointmentViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppointmentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_appointment_card, parent, false)
        return AppointmentViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppointmentViewHolder, position: Int) {
        val appointment = appointments[position]
        holder.bind(appointment)
    }

    override fun getItemCount() = appointments.size

    inner class AppointmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(appointment: Appointment) {
            val titleView = itemView.findViewById<TextView>(R.id.tvTitle)
            val dateTimeView = itemView.findViewById<TextView>(R.id.tvDateTime)
            val emailView = itemView.findViewById<TextView>(R.id.tvEmail)
            val locationView = itemView.findViewById<TextView>(R.id.tvLocation)
            val durationView = itemView.findViewById<TextView>(R.id.tvDuration)
            val deleteButton = itemView.findViewById<ImageButton>(R.id.btnDelete)
            val updateButton = itemView.findViewById<ImageButton>(R.id.btnUpdate)

            titleView.text = appointment.title
            dateTimeView.text = "${appointment.date} | ${appointment.time}"
            emailView.text = "Customer: ${appointment.customerEmail}"
            val locationText = appointment.location ?: "Location not defined"
            locationView.text = "Location: $locationText"
            durationView.text = "Duration: ${appointment.durationMinutes} minutes"

            deleteButton.setOnClickListener { onDeleteClick(appointment) }
            updateButton.setOnClickListener { onUpdateClick(appointment) }
        }
    }
}