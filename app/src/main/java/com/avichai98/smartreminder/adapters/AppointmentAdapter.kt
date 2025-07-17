package com.avichai98.smartreminder.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.avichai98.smartreminder.R
import com.avichai98.smartreminder.models.Appointment

class AppointmentAdapter(
    private val appointments: MutableList<Appointment>,
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
            val organizerView = itemView.findViewById<TextView>(R.id.tvOrganizer)
            val dateTimeView = itemView.findViewById<TextView>(R.id.tvDateTime)
            val attendeesView = itemView.findViewById<TextView>(R.id.tvAttendees)
            val locationView = itemView.findViewById<TextView>(R.id.tvLocation)
            val durationView = itemView.findViewById<TextView>(R.id.tvDuration)

            val ctx = itemView.context

            titleView.text = appointment.summary
            organizerView.text = "${ctx.getString(R.string.organizer)}: ${appointment.organizer?.displayName ?: "Unknown"}"
            dateTimeView.text = "${appointment.getStartDate()} | ${appointment.getStartTime()}"
            attendeesView.text = "${ctx.getString(R.string.attendees)}: ${appointment.getAttendeeEmails()}"
            val locationText = appointment.location ?: ctx.getString(R.string.location_not_defined)
            locationView.text = "${ctx.getString(R.string.location)}: $locationText"
            durationView.text = "${ctx.getString(R.string.duration)}: ${appointment.getDurationMinutes()} ${ctx.getString(R.string.minutes)}"

        }
    }
}