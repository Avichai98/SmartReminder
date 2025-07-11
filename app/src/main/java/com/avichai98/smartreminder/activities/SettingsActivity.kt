package com.avichai98.smartreminder.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.avichai98.smartreminder.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // default
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val minutesBefore = prefs.getInt("reminderMinutes", 15)
        binding.etMinutesBefore.setText(minutesBefore.toString())

        binding.btnSaveSettings.setOnClickListener {
            val value = binding.etMinutesBefore.text.toString().toIntOrNull() ?: 15
            prefs.edit { putInt("reminderMinutes", value) }
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }
    }
}
