package xyz.draumaz.sustenance.ui

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import xyz.draumaz.sustenance.R

/**
 * Tells the user why a Health Connect write/delete failed. A [SecurityException] means write
 * access was revoked, so the user is sent to fix permissions; anything else shows [failedMessage].
 */
fun reportHealthWriteFailure(
    context: Context,
    error: Exception,
    @StringRes failedMessage: Int,
    onManagePermissions: () -> Unit,
) {
    if (error is SecurityException) {
        Toast.makeText(context, R.string.write_permission_required, Toast.LENGTH_LONG).show()
        onManagePermissions()
    } else {
        Toast.makeText(context, failedMessage, Toast.LENGTH_LONG).show()
    }
}
