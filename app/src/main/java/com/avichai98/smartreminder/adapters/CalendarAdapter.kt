package com.avichai98.smartreminder.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import androidx.recyclerview.widget.RecyclerView
import com.avichai98.smartreminder.R

class CalendarAdapter(
    private val calendars: MutableList<String>,
    private val selectedPositions: MutableSet<Int>
) : RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_calendar, parent, false)
        return CalendarViewHolder(view)
    }

    override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) {
        holder.bind(calendars[position], position)
    }

    fun updateData(newCalendars: List<String>, newSelectedPositions: Set<Int>) {
        calendars.clear()
        calendars.addAll(newCalendars)
        selectedPositions.clear()
        selectedPositions.addAll(newSelectedPositions)
        notifyDataSetChanged()
    }

    fun getSelectedPositions(): Set<Int> = selectedPositions

    override fun getItemCount() = calendars.size

    inner class CalendarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(calendarName: String, position: Int) {
            val checkedTextView = itemView.findViewById<CheckedTextView>(R.id.text1)
            checkedTextView.text = calendarName
            checkedTextView.isChecked = selectedPositions.contains(position)

            itemView.setOnClickListener {
                if (selectedPositions.contains(position)) {
                    selectedPositions.remove(position)
                    checkedTextView.isChecked = false
                } else {
                    selectedPositions.add(position)
                    checkedTextView.isChecked = true
                }
            }
        }
    }
}
