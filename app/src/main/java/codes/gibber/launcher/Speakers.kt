package codes.gibber.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * One of your Bluetooth things: found among the phone's paired devices by a word in its name,
 * opened in its maker's app once it's connected.
 */
data class Gear(
    val name: String,
    val short: String,
    val key: String,
    val glyph: Glyphs,
    val app: String,
    val profiles: List<Int>,
)

/** Your JBL speaker and headphones: whether each is connected, connecting it, opening its app. */
object Speakers {
    val XTREME = Gear("JBL Xtreme 5", "Xtreme 5", "xtreme", Glyphs.Speaker, "JBL One", listOf(BluetoothProfile.A2DP))
    val ENDURANCE = Gear(
        "JBL Endurance", "Endurance", "endurance", Glyphs.Headphones, "JBL Headphones",
        listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP),
    )
    val ALL = listOf(XTREME, ENDURANCE)

    private val BT_OUTPUTS = setOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
    )

    /** Android 12 and later ask before an app sees paired devices or turns Bluetooth on. */
    val permission: String? = if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_CONNECT else null

    fun allowed(context: Context): Boolean =
        permission?.let { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED } ?: true

    private fun adapter(context: Context): BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    fun isOn(context: Context): Boolean = try { adapter(context)?.isEnabled == true } catch (_: Throwable) { false }

    @SuppressLint("MissingPermission")
    fun paired(context: Context, gear: Gear): BluetoothDevice? = try {
        adapter(context)?.bondedDevices?.firstOrNull { it.name?.contains(gear.key, ignoreCase = true) == true }
    } catch (_: Throwable) {
        null
    }

    /** Connected when the phone can send it sound: read from the phone's outputs, which needs no permission. */
    fun connected(context: Context, gear: Gear): Boolean = try {
        context.getSystemService(AudioManager::class.java)
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .any { it.type in BT_OUTPUTS && it.productName?.toString()?.contains(gear.key, ignoreCase = true) == true }
    } catch (_: Throwable) {
        false
    }

    /**
     * Asks Android to connect a paired device. Apps have no public way to do this; the hidden
     * connect call works on some phones and is refused on newer ones, so this says only whether
     * Android took the request. The caller watches for the connection and falls back to the
     * Bluetooth screen.
     */
    @SuppressLint("MissingPermission")
    suspend fun connect(context: Context, device: BluetoothDevice, gear: Gear): Boolean {
        val adapter = adapter(context) ?: return false
        var taken = false
        for (profile in gear.profiles) {
            val proxy = withTimeoutOrNull(3_000) { proxy(context, adapter, profile) } ?: continue
            try {
                val call = proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
                if (call.invoke(proxy, device) == true) taken = true
            } catch (_: Throwable) {
            } finally {
                try { adapter.closeProfileProxy(profile, proxy) } catch (_: Throwable) { }
            }
        }
        return taken
    }

    private suspend fun proxy(context: Context, adapter: BluetoothAdapter, profile: Int): BluetoothProfile? =
        suspendCancellableCoroutine { cont ->
            val asked = try {
                adapter.getProfileProxy(context.applicationContext, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(p: Int, proxy: BluetoothProfile) { if (cont.isActive) cont.resume(proxy) }
                    override fun onServiceDisconnected(p: Int) {}
                }, profile)
            } catch (_: Throwable) {
                false
            }
            if (!asked && cont.isActive) cont.resume(null)
        }

    fun openBluetooth(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) { }
    }

    /** Opens the maker's app; when it isn't on the phone, the Play Store's search for it. */
    fun openApp(context: Context, gear: Gear): String? {
        val app = Apps.load(context).firstOrNull { it.label.contains(gear.app, ignoreCase = true) }
        if (app != null) {
            Apps.launch(context, app)
            return null
        }
        val q = Uri.encode(gear.app)
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$q")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$q")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Throwable) { }
        }
        return "${gear.app} isn't on this phone, so the Play Store opened."
    }
}

/**
 * Your speaker and headphones under Playing on. A tap connects one (turning Bluetooth on first,
 * if it's off); a tap once it's connected opens its app. Below, a button for each app.
 */
@Composable
fun SpeakersPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var connected by remember { mutableStateOf(Speakers.ALL.filter { Speakers.connected(context, it) }.toSet()) }
    var busy by remember { mutableStateOf<Gear?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    // A tap waiting on Android's "allow" or "turn on Bluetooth", and the one to carry on with.
    var waiting by remember { mutableStateOf<Gear?>(null) }
    var resume by remember { mutableStateOf<Gear?>(null) }

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val g = waiting
        waiting = null
        if (ok) resume = g else note = "The launcher needs Nearby devices to connect your speaker."
    }
    val turnOn = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val g = waiting
        waiting = null
        if (Speakers.isOn(context)) resume = g else note = "Bluetooth is still off."
    }

    fun tap(gear: Gear) {
        if (busy != null) return
        if (Speakers.connected(context, gear)) {
            note = Speakers.openApp(context, gear)
            return
        }
        val permission = Speakers.permission
        if (permission != null && !Speakers.allowed(context)) {
            waiting = gear
            ask.launch(permission)
            return
        }
        if (!Speakers.isOn(context)) {
            waiting = gear
            note = "Turning Bluetooth on to connect your ${gear.name}."
            try {
                turnOn.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } catch (_: Throwable) {
                waiting = null
                note = "Android wouldn't turn Bluetooth on from here, so the Bluetooth screen opened."
                Speakers.openBluetooth(context)
            }
            return
        }
        val device = Speakers.paired(context, gear)
        if (device == null) {
            note = "Your ${gear.name} isn't paired with this phone, so the Bluetooth screen opened: pair it there."
            Speakers.openBluetooth(context)
            return
        }
        busy = gear
        note = "Connecting your ${gear.name}…"
        scope.launch {
            val taken = Speakers.connect(context, device, gear)
            val made = taken && withTimeoutOrNull(12_000) {
                while (!Speakers.connected(context, gear)) delay(500)
                true
            } == true
            busy = null
            if (made) {
                connected = connected + gear
                note = null
            } else {
                note = "Android didn't let the launcher connect your ${gear.name}, so the Bluetooth screen opened: tap it there."
                Speakers.openBluetooth(context)
            }
        }
    }

    LaunchedEffect(resume) {
        val g = resume ?: return@LaunchedEffect
        resume = null
        tap(g)
    }
    // Connections change from elsewhere too: the speaker's button, Android's own Bluetooth screen.
    LaunchedEffect(Unit) {
        while (true) {
            connected = Speakers.ALL.filter { Speakers.connected(context, it) }.toSet()
            delay(1_500)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Speakers.ALL.forEach { gear ->
                GearTile(gear, gear in connected, busy == gear, Modifier.weight(1f)) { tap(gear) }
            }
        }
        note?.let { Text(it, color = Dark.W62, fontSize = 11.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Speakers.ALL.forEach { gear ->
                FrostButton("Open ${gear.app}", Modifier.weight(1f), hi = true, height = 38.dp) {
                    note = Speakers.openApp(context, gear)
                }
            }
        }
    }
}

@Composable
private fun GearTile(gear: Gear, connected: Boolean, busy: Boolean, modifier: Modifier, onTap: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .frost(hi = connected)
            .clickable(onClick = onTap)
            .padding(10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (connected) Color(0xFFFF5A00) else Dark.FrostHi),
        ) { Glyph(gear.glyph, 18.dp, Color.White) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(gear.short, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (connected) Dot(Dark.Green, 6.dp)
                Text(
                    when {
                        busy -> "Connecting…"
                        connected -> "Connected"
                        else -> "Tap to connect"
                    },
                    color = if (connected) Color.White else Dark.W62,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }
    }
}
