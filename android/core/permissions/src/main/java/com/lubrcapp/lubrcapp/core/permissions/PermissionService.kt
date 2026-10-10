package com.lubrcapp.lubrcapp.core.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Android runtime-permission helper.
 *
 * Business capability authorization lives in the access-control engine and is
 * deliberately separate from operating-system runtime permissions.
 *
 * Import/export and backup file selection use the Storage Access Framework and
 * therefore do not request broad external-storage permissions.
 */
class PermissionService(private val context: Context) {
    fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            permission,
        ) == PackageManager.PERMISSION_GRANTED

    fun requestPermission(
        activity: android.app.Activity,
        permission: String,
        requestCode: Int,
    ) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(permission),
            requestCode,
        )
    }

    fun requestPermissions(
        activity: android.app.Activity,
        permissions: Array<out String>,
        requestCode: Int,
    ) {
        ActivityCompat.requestPermissions(
            activity,
            permissions,
            requestCode,
        )
    }

    fun hasCameraPermission(): Boolean =
        hasPermission(Manifest.permission.CAMERA)

    fun hasLocationPermission(): Boolean =
        hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)

    fun hasBluetoothConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            hasPermission(Manifest.permission.BLUETOOTH_CONNECT)

    fun supportsUsbHost(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
}

object PermissionCodes {
    const val CAMERA = 100
    const val LOCATION = 102
    const val NOTIFICATION = 103
    const val BLUETOOTH = 104
}
