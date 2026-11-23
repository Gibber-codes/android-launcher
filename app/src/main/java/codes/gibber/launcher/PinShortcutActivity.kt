package codes.gibber.launcher

import android.app.Activity
import android.content.pm.LauncherApps
import android.os.Bundle
import android.widget.Toast

/**
 * Android opens this when an app asks to put a shortcut on the home screen, such as
 * Chrome's Add to home screen. It takes the shortcut, which then shows in the app list.
 */
class PinShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launcherApps = getSystemService(LauncherApps::class.java)
        val request = try {
            launcherApps?.getPinItemRequest(intent)
        } catch (e: Exception) {
            null
        }
        if (request != null && request.isValid &&
            request.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT
        ) {
            val label = request.shortcutInfo?.shortLabel ?: "The shortcut"
            val added = try {
                request.accept()
            } catch (e: Exception) {
                false
            }
            Toast.makeText(
                this,
                if (added) "$label is in your app list now." else "Couldn't add $label.",
                Toast.LENGTH_SHORT,
            ).show()
        }
        finish()
    }
}
