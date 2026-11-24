package codes.gibber.phone

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PhoneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        makeChannels()
        watch(Telephony.MmsSms.CONTENT_URI)
        watch(Telephony.Sms.CONTENT_URI)
        watch(Telephony.Mms.CONTENT_URI)
        watch(CallLog.Calls.CONTENT_URI)
        watch(ContactsContract.Contacts.CONTENT_URI)
    }

    private fun makeChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TEXTS, "Texts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "New texts"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_INCOMING, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Someone is calling. Android plays the ringtone itself."
                setSound(null, null)
                enableVibration(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, "Current call", NotificationManager.IMPORTANCE_LOW).apply {
                description = "The call you're on"
                setSound(null, null)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PROBLEMS, "Texts that didn't go through", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pending = false

    /** Any change to texts, calls or contacts bumps [changes], and open screens load again. */
    private fun watch(uri: Uri) {
        try {
            contentResolver.registerContentObserver(uri, true, object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) = bump()
            })
        } catch (_: SecurityException) {
            // No permission yet; the screens also reload when you come back to them.
        }
    }

    /** Several changes in a burst count as one reload. */
    fun bump() {
        if (pending) return
        pending = true
        handler.postDelayed({
            pending = false
            changesFlow.value = changesFlow.value + 1
        }, 300)
    }

    companion object {
        lateinit var instance: PhoneApp
            private set

        const val CHANNEL_TEXTS = "texts"
        const val CHANNEL_INCOMING = "incoming_calls"
        const val CHANNEL_ONGOING = "ongoing_call"
        const val CHANNEL_PROBLEMS = "text_problems"

        private val changesFlow = MutableStateFlow(0)
        val changes: StateFlow<Int> = changesFlow
    }
}
