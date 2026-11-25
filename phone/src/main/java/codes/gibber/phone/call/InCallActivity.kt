package codes.gibber.phone.call

import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import codes.gibber.phone.MainActivity
import codes.gibber.phone.data.Contacts
import codes.gibber.phone.data.Numbers
import codes.gibber.phone.ui.Avatar
import codes.gibber.phone.ui.FullScreen
import codes.gibber.phone.ui.GibberTheme
import codes.gibber.phone.ui.Ink
import kotlinx.coroutines.delay

/** The call screen: who it is, answer or decline, then mute, speaker, keypad, hold and hang up. */
class InCallActivity : ComponentActivity() {
    private var proximity: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent {
            GibberTheme {
                FullScreen()
                CallScreen(
                    onEmpty = { finish() },
                    onEarpiece = { near -> setProximity(near) },
                    onAddCall = {
                        startActivity(Intent(Intent.ACTION_DIAL, null, this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra("answer", false) == true) {
            CallManager.ringing()?.answer(VideoProfile.STATE_AUDIO_ONLY)
            intent.removeExtra("answer")
        }
    }

    /** Turns the screen off when the phone is at your ear, so your cheek doesn't press buttons. */
    private fun setProximity(on: Boolean) {
        val pm = getSystemService(PowerManager::class.java)
        if (on) {
            if (proximity == null && pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                proximity = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "gibberphone:ear").apply {
                    setReferenceCounted(false)
                    acquire(4 * 60 * 60 * 1000L)
                }
            }
        } else {
            proximity?.let { if (it.isHeld) it.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY) }
            proximity = null
        }
    }

    override fun onDestroy() {
        setProximity(false)
        super.onDestroy()
    }
}

private val quickReplies = listOf(
    "Can't talk right now. I'll call you back.",
    "I'll call you right back.",
    "On my way.",
    "Can't talk now. Text me?",
)

@Composable
private fun CallScreen(onEmpty: () -> Unit, onEarpiece: (Boolean) -> Unit, onAddCall: () -> Unit) {
    val context = LocalContext.current
    val calls by CallManager.calls.collectAsState()
    val tick by CallManager.tick.collectAsState()
    val audio by CallManager.audio.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showKeys by remember { mutableStateOf(false) }
    var showReplies by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    LaunchedEffect(calls) {
        if (calls.isEmpty()) {
            delay(400)
            if (CallManager.calls.value.isEmpty()) onEmpty()
        }
    }

    @Suppress("UNUSED_VARIABLE") val redraw = tick
    val primary = CallManager.primary()
    val ringing = CallManager.ringing()
    val live = CallManager.live()
    val active = live.firstOrNull { it.stateNow() == Call.STATE_ACTIVE }
    val held = live.firstOrNull { it.stateNow() == Call.STATE_HOLDING }
    val route = audio?.route ?: CallAudioState.ROUTE_EARPIECE
    val muted = audio?.isMuted ?: false
    val hasBluetooth = (audio?.supportedRouteMask ?: 0) and CallAudioState.ROUTE_BLUETOOTH != 0

    // The main call shown big: a ringing call when it's the only one, else the one you're on.
    val shown = if (ringing != null && active == null && held == null) ringing else (active ?: primary)
    val earpiece = shown != null && shown.stateNow() == Call.STATE_ACTIVE && route == CallAudioState.ROUTE_EARPIECE
    LaunchedEffect(earpiece) { onEarpiece(earpiece) }

    Box(Modifier.fillMaxSize().background(Ink.Page).safeDrawingPadding()) {
        if (shown == null) {
            Text("Call ended", color = Ink.Muted, fontSize = 20.sp, modifier = Modifier.align(Alignment.Center))
            return@Box
        }
        val number = shown.number()
        val title = shown.title(context)
        val match = Contacts.lookup(context, number)
        val state = shown.stateNow()
        val status = when (state) {
            Call.STATE_RINGING -> "Incoming call"
            Call.STATE_DIALING, Call.STATE_CONNECTING -> "Calling…"
            Call.STATE_HOLDING -> "On hold"
            Call.STATE_DISCONNECTING -> "Hanging up…"
            Call.STATE_DISCONNECTED -> "Call ended"
            Call.STATE_ACTIVE -> {
                val start = shown.details.connectTimeMillis
                if (start > 0) elapsed(now - start) else "Connected"
            }
            else -> ""
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Avatar(title, match?.photo, size = 112.dp)
            Spacer(Modifier.height(20.dp))
            Text(title, color = Ink.Text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (match != null && number.isNotBlank()) {
                Text(Numbers.pretty(number), color = Ink.Muted, fontSize = 16.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(status, color = if (state == Call.STATE_ACTIVE) Ink.Green else Ink.Muted, fontSize = 18.sp)

            if (held != null && held !== shown) {
                Spacer(Modifier.height(16.dp))
                Text("On hold: ${held.title(context)}", color = Ink.Muted, fontSize = 15.sp)
            }

            // A second call ringing while you're on one.
            if (ringing != null && ringing !== shown) {
                Spacer(Modifier.height(20.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ink.Card).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("${ringing.title(context)} is calling", color = Ink.Text, fontSize = 17.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        RoundButton(Icons.Filled.CallEnd, "Decline", Ink.Red) { ringing.reject(false, null) }
                        RoundButton(Icons.Filled.Call, "Answer and hold", Ink.Green) { ringing.answer(VideoProfile.STATE_AUDIO_ONLY) }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            if (state == Call.STATE_RINGING) {
                if (showReplies) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ink.Card)) {
                        for (r in quickReplies) {
                            Text(
                                r, color = Ink.Text, fontSize = 16.sp,
                                modifier = Modifier.fillMaxWidth().clickable { shown.reject(true, r) }.padding(16.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                    RoundButton(Icons.Filled.CallEnd, "Decline", Ink.Red, big = true) { shown.reject(false, null) }
                    SmallButton(Icons.Filled.Message, "Text", showReplies) { showReplies = !showReplies }
                    RoundButton(Icons.Filled.Call, "Answer", Ink.Green, big = true) { shown.answer(VideoProfile.STATE_AUDIO_ONLY) }
                }
                Spacer(Modifier.height(40.dp))
            } else {
                if (showKeys) {
                    Text(typed, color = Ink.Text, fontSize = 28.sp, maxLines = 1)
                    Spacer(Modifier.height(8.dp))
                    DtmfPad { key ->
                        typed += key
                        shown.playDtmfTone(key)
                        shown.stopDtmfTone()
                    }
                    Spacer(Modifier.height(12.dp))
                } else {
                    val canHold = shown.details.can(Call.Details.CAPABILITY_HOLD) || state == Call.STATE_HOLDING
                    val canMerge = active != null && held != null
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        SmallButton(if (muted) Icons.Filled.MicOff else Icons.Filled.Mic, if (muted) "Unmute" else "Mute", muted) {
                            CallManager.setMuted(!muted)
                        }
                        SmallButton(Icons.Filled.Dialpad, "Keypad", false) { showKeys = true }
                        SmallButton(Icons.Filled.VolumeUp, "Speaker", route == CallAudioState.ROUTE_SPEAKER) {
                            CallManager.setRoute(if (route == CallAudioState.ROUTE_SPEAKER) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        if (canMerge) {
                            SmallButton(Icons.Filled.CallMerge, "Merge", false) { active!!.conference(held!!) }
                            SmallButton(Icons.Filled.SwapCalls, "Swap", false) {
                                active!!.hold()
                                held!!.unhold()
                            }
                        } else {
                            SmallButton(
                                if (state == Call.STATE_HOLDING) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                if (state == Call.STATE_HOLDING) "Resume" else "Hold",
                                state == Call.STATE_HOLDING,
                                enabled = canHold,
                            ) {
                                if (state == Call.STATE_HOLDING) shown.unhold() else shown.hold()
                            }
                            SmallButton(Icons.Filled.PersonAdd, "Add call", false, enabled = state == Call.STATE_ACTIVE) { onAddCall() }
                        }
                        if (hasBluetooth) {
                            SmallButton(Icons.Filled.Bluetooth, "Bluetooth", route == CallAudioState.ROUTE_BLUETOOTH) {
                                CallManager.setRoute(if (route == CallAudioState.ROUTE_BLUETOOTH) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_BLUETOOTH)
                            }
                        } else {
                            SmallButton(Icons.Filled.PhoneInTalk, "Phone", route == CallAudioState.ROUTE_EARPIECE) {
                                CallManager.setRoute(CallAudioState.ROUTE_WIRED_OR_EARPIECE)
                            }
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    if (showKeys) {
                        Text("Hide", color = Ink.Muted, fontSize = 16.sp, modifier = Modifier.clickable { showKeys = false }.padding(16.dp))
                        Spacer(Modifier.width(24.dp))
                    }
                    RoundButton(Icons.Filled.CallEnd, "End", Ink.Red, big = true) { shown.disconnect() }
                    if (showKeys) Spacer(Modifier.width(80.dp))
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

private fun elapsed(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, color: Color, big: Boolean = false, onClick: () -> Unit) {
    val size = if (big) 76.dp else 58.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(size).clip(CircleShape).background(color).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(if (big) 34.dp else 26.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Ink.Muted, fontSize = 13.sp)
    }
}

@Composable
private fun SmallButton(icon: ImageVector, label: String, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(88.dp)) {
        Box(
            Modifier.size(64.dp).clip(CircleShape)
                .background(if (on) Ink.Text else Ink.Raised)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = if (on) Ink.Page else if (enabled) Ink.Text else Ink.Line, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = if (enabled) Ink.Muted else Ink.Line, fontSize = 13.sp, maxLines = 1)
    }
}

private val dtmfRows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'), listOf('*', '0', '#'))

@Composable
private fun DtmfPad(onKey: (Char) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in dtmfRows) {
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                for (k in row) {
                    Box(
                        Modifier.size(68.dp).clip(CircleShape).background(Ink.Raised).clickable { onKey(k) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(k.toString(), color = Ink.Text, fontSize = 28.sp)
                    }
                }
            }
        }
    }
}
