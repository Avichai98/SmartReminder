package com.avichai98.smartreminder.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import androidx.recyclerview.widget.RecyclerView
import com.avichai98.smartreminder.R

/**
 * RecyclerView adapter for a simple checkable calendar list with a selection limit.
 *
 * - Supports toggling items on/off.
 * - Enforces a maximum number of selected items (default 2).
 * - Notifies the host when the user tries to exceed the limit.
 */
class CalendarAdapter(
    private val calendars: MutableList<String>,
    private val selectedPositions: MutableSet<Int>,
    private val maxSelectable: Int = 2,
    private val onSelectionLimitReached: (() -> Unit)? = null
) : RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_calendar, parent, false)
        return CalendarViewHolder(view)
    }

    override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) {
        holder.bind(calendars[position], position)
    }

    override fun getItemCount(): Int = calendars.size

    /** Replace data and selected set in one shot. */
    fun updateData(newCalendars: List<String>, newSelectedPositions: Set<Int>) {
        calendars.clear()
        calendars.addAll(newCalendars)
        selectedPositions.clear()
        // Clamp to the allowed maximum in case server holds more than maxSelectable
        selectedPositions.addAll(newSelectedPositions.take(maxSelectable))
        notifyDataSetChanged()
    }

    /** Return currently selected positions (indexes in calendars). */
    fun getSelectedPositions(): Set<Int> = selectedPositions

    inner class CalendarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bind(calendarName: String, position: Int) {
            val ctv = itemView.findViewById<CheckedTextView>(R.id.text1)
            ctv.text = calendarName
            ctv.isChecked = selectedPositions.contains(position)

            itemView.setOnClickListener {
                val isSelected = selectedPositions.contains(position)
                if (isSelected) {
                    // Toggle OFF
                    selectedPositions.remove(position)
                    ctv.isChecked = false
                } else {
                    // Toggle ON — enforce selection limit
                    if (selectedPositions.size >= maxSelectable) {
                        // Reject and notify host (Toast/Snackbar in activity)
                        onSelectionLimitReached?.invoke()
                        return@setOnClickListener
                    }
                    selectedPositions.add(position)
                    ctv.isChecked = true
                }
            }
        }
    }
}