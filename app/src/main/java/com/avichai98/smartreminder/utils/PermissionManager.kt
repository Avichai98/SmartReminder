package com.avichai98.smartreminder.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionManager(
    private val context: Context,
    private val permissionsLauncher: ActivityResultLauncher<Array<String>>,
    private val onPermissionsGranted: () -> Unit,
    private val rationaleMessage: String = "This permission is required for the feature to work properly."
) {

    private var pendingPermissions: Array<String> = arrayOf()

    fun checkPermissions(permissions: Array<String>) {
        pendingPermissions = permissions
        if (hasAllPermissions()) {
            onPermissionsGranted()
        } else {
            val shouldShowRationale = permissions.any {
                ActivityCompat.shouldShowRequestPermissionRationale(context as Activity, it)
            }

            if (shouldShowRationale) {
                showRationaleDialog()
            } else {
                permissionsLauncher.launch(permissions)
            }
        }
    }

    private fun hasAllPermissions(): Boolean {
        return pendingPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun showRationaleDialog() {
        AlertDialog.Builder(context)
            .setTitle("Permission Required")
            .setMessage(rationaleMessage)
            .setPositiveButton("OK") { _, _ ->
                permissionsLauncher.launch(pendingPermissions)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.fromParts("package", context.packageName, null)
        context.startActivity(intent)
    }
}
