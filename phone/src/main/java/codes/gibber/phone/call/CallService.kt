package codes.gibber.phone.call

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import codes.gibber.phone.PhoneApp
import codes.gibber.phone.R
import codes.gibber.phone.data.Contacts
import codes.gibber.phone.data.Numbers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The calls happening right now, shared by the call service, the call screen and notifications. */
object CallManager {
    private val callsFlow = MutableStateFlow<List<Call>>(emptyList())
    val calls: StateFlow<List<Call>> = callsFlow

    /** Bumps whenever any call changes state, so screens redraw. */
    private val tickFlow = MutableStateFlow(0)
    val tick: StateFlow<Int> = tickFlow

    private val audioFlow = MutableStateFlow<CallAudioState?>(null)
    val audio: StateFlow<CallAudioState?> = audioFlow

    var service: InCallService? = null

    fun add(call: Call) {
        callsFlow.value = callsFlow.value + call
        changed()
    }

    fun remove(call: Call) {
        callsFlow.value = callsFlow.value - call
        changed()
    }

    fun changed() {
        tickFlow.value = tickFlow.value + 1
    }

    fun setAudio(state: CallAudioState?) {
        audioFlow.value = state
    }

    /** The call that's ringing, if any. */
    fun ringing(): Call? = callsFlow.value.firstOrNull { it.stateNow() == Call.STATE_RINGING }

    /** The call you're talking on (or dialing), else whatever is left. */
    fun primary(): Call? {
        val list = callsFlow.value.filter { it.stateNow() != Call.STATE_DISCONNECTED }
        return list.firstOrNull { it.stateNow() == Call.STATE_ACTIVE }
            ?: list.firstOrNull { it.stateNow() in listOf(Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_PULLING_CALL, Call.STATE_SELECT_PHONE_ACCOUNT) }
            ?: list.firstOrNull { it.stateNow() == Call.STATE_RINGING }
            ?: list.firstOrNull()
            ?: callsFlow.value.firstOrNull()
    }

    fun live(): List<Call> = callsFlow.value.filter { it.stateNow() != Call.STATE_DISCONNECTED && it.parent == null }

    fun setMuted(muted: Boolean) = service?.setMuted(muted)

    @Suppress("DEPRECATION")
    fun setRoute(route: Int) = service?.setAudioRoute(route)
}

@Suppress("DEPRECATION")
fun Call.stateNow(): Int = if (Build.VERSION.SDK_INT >= 31) details.state else state

fun Call.number(): String = details.handle?.schemeSpecificPart ?: ""

/** A name for the call: your contact's name, else the caller ID name, else the number. */
fun Call.title(context: Context): String {
    if (details.hasProperty(Call.Details.PROPERTY_CONFERENCE)) return "Conference call"
    val number = number()
    Contacts.lookup(context, number)?.let { return it.name }
    details.callerDisplayName?.takeIf { it.isNotBlank() }?.let { return it }
    if (number.isBlank()) return "Unknown caller"
    return Numbers.pretty(number)
}

/** Android hands each call to this service while Gibber Phone is the default calls app. */
class CallService : InCallService() {
    private val handler = Handler(Looper.getMainLooper())

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            CallManager.changed()
            when (state) {
                Call.STATE_ACTIVE, Call.STATE_DIALING, Call.STATE_HOLDING -> {
                    CallNotifications.cancelIncoming(this@CallService)
                    CallNotifications.showOngoing(this@CallService)
                }
                Call.STATE_DISCONNECTED -> {
                    // Leave "Call ended" up for a moment, then let it go.
                    handler.postDelayed({ CallManager.remove(call); afterChange() }, 1200)
                }
            }
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) = CallManager.changed()
        override fun onChildrenChanged(call: Call, children: MutableList<Call>) = CallManager.changed()
        override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>) = CallManager.changed()
    }

    override fun onCreate() {
        super.onCreate()
        CallManager.service = this
    }

    override fun onDestroy() {
        if (CallManager.service === this) CallManager.service = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.service = this
        CallManager.add(call)
        call.registerCallback(callback)
        if (call.stateNow() == Call.STATE_RINGING) {
            CallNotifications.showIncoming(this, call)
        } else {
            CallNotifications.showOngoing(this)
            openScreen(this)
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callback)
        CallManager.remove(call)
        afterChange()
    }

    private fun afterChange() {
        if (CallManager.live().isEmpty()) {
            CallNotifications.cancelAll(this)
        } else if (CallManager.ringing() == null) {
            CallNotifications.cancelIncoming(this)
            CallNotifications.showOngoing(this)
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        CallManager.setAudio(audioState)
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        openScreen(this)
    }

    companion object {
        fun openScreen(context: Context) {
            try {
                context.startActivity(
                    Intent(context, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (_: Exception) {
                // The ongoing-call notification still opens it.
            }
        }
    }
}

object CallNotifications {
    private const val INCOMING = 7001
    private const val ONGOING = 7002

    private fun screenIntent(context: Context, answer: Boolean): PendingIntent {
        val intent = Intent(context, InCallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("answer", answer)
        return PendingIntent.getActivity(context, if (answer) 2 else 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun action(context: Context, action: String): PendingIntent {
        val intent = Intent(action, null, context, CallActionReceiver::class.java)
        return PendingIntent.getBroadcast(context, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)
    }

    @SuppressLint("MissingPermission")
    fun showIncoming(context: Context, call: Call) {
        val name = call.title(context)
        val full = screenIntent(context, false)
        val answer = screenIntent(context, true)
        val decline = action(context, CallActionReceiver.DECLINE)
        val builder = Notification.Builder(context, PhoneApp.CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.ic_notify_call)
            .setContentTitle(name)
            .setContentText("Incoming call")
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
        if (Build.VERSION.SDK_INT >= 31) {
            val person = android.app.Person.Builder().setName(name).setImportant(true).build()
            builder.setStyle(Notification.CallStyle.forIncomingCall(person, decline, answer))
        } else {
            builder.addAction(Notification.Action.Builder(null, "Decline", decline).build())
            builder.addAction(Notification.Action.Builder(null, "Answer", answer).build())
        }
        notify(context, INCOMING, builder.build())
    }

    @SuppressLint("MissingPermission")
    fun showOngoing(context: Context) {
        val call = CallManager.primary() ?: return
        val hangUp = action(context, CallActionReceiver.HANG_UP)
        val n = Notification.Builder(context, PhoneApp.CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_notify_call)
            .setContentTitle(call.title(context))
            .setContentText(
                when (call.stateNow()) {
                    Call.STATE_DIALING, Call.STATE_CONNECTING -> "Calling…"
                    Call.STATE_HOLDING -> "On hold"
                    else -> "On a call"
                },
            )
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setUsesChronometer(call.stateNow() == Call.STATE_ACTIVE)
            .setWhen(if (call.details.connectTimeMillis > 0) call.details.connectTimeMillis else System.currentTimeMillis())
            .setContentIntent(screenIntent(context, false))
            .addAction(Notification.Action.Builder(null, "Hang up", hangUp).build())
            .build()
        notify(context, ONGOING, n)
    }

    private fun notify(context: Context, id: Int, n: Notification) {
        try {
            context.getSystemService(NotificationManager::class.java).notify(id, n)
        } catch (_: Exception) {
        }
    }

    fun cancelIncoming(context: Context) = context.getSystemService(NotificationManager::class.java).cancel(INCOMING)

    fun cancelAll(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(INCOMING)
        nm.cancel(ONGOING)
    }
}

/** Decline and Hang up buttons on call notifications. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            DECLINE -> CallManager.ringing()?.reject(false, null)
            HANG_UP -> CallManager.primary()?.disconnect()
            ANSWER -> CallManager.ringing()?.answer(VideoProfile.STATE_AUDIO_ONLY)
        }
    }

    companion object {
        const val DECLINE = "codes.gibber.phone.DECLINE"
        const val HANG_UP = "codes.gibber.phone.HANG_UP"
        const val ANSWER = "codes.gibber.phone.ANSWER"
    }
}
