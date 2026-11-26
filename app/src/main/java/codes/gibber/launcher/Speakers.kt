package codes.gibber.launcher

import androidx.compose.foundation.Image
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.app.Activity
import android.os.SystemClock
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRouter2
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

/**
 * One of your Bluetooth things: known by any of its names (its own, or the one you gave it, like
 * "Gabes JBL"), opened in its maker's app.
 */
data class Gear(
    val name: String,
    val short: String,
    val keys: List<String>,
    val glyph: Glyphs,
    val app: String,
) {
    fun matches(label: String?): Boolean = label != null && keys.any { label.contains(it, ignoreCase = true) }
}

/** Your JBL speaker and headphones: which one is connected, and opening its app. */
object Speakers {
    val XTREME = Gear("JBL Xtreme 5", "Xtreme 5", listOf("Gabes JBL", "Xtreme"), Glyphs.Xtreme, "JBL One")
    val ENDURANCE = Gear("JBL Endurance", "Endurance", listOf("Endurance"), Glyphs.Earbuds, "JBL Headphones")
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

    /** The phone's paired devices, when Android lets the launcher see them. */
    @SuppressLint("MissingPermission")
    fun bonded(context: Context): Set<BluetoothDevice> {
        if (!allowed(context)) return emptySet()
        return try { adapter(context)?.bondedDevices.orEmpty() } catch (_: Throwable) { emptySet() }
    }

    /** Whether a device is known by one of the gear's names: its own, or the one you gave it. */
    @SuppressLint("MissingPermission")
    fun isGear(gear: Gear, d: BluetoothDevice): Boolean = try {
        gear.matches(d.name) || (Build.VERSION.SDK_INT >= 30 && gear.matches(d.alias))
    } catch (_: Throwable) {
        false
    }

    /** The addresses of paired devices known by one of the gear's names. */
    private fun pairedAddresses(context: Context, gear: Gear): Set<String> = try {
        bonded(context).filter { isGear(gear, it) }.mapNotNull { it.address?.uppercase()?.ifBlank { null } }.toSet()
    } catch (_: Throwable) {
        emptySet()
    }

    /**
     * Which of your things the phone can send sound to now: an output named like one, or (Android 9+,
     * with Nearby devices allowed) at the address of a paired one named like it.
     */
    fun connected(context: Context): Set<Gear> = try {
        val outputs = context.getSystemService(AudioManager::class.java)
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type in BT_OUTPUTS }
        ALL.filter { gear ->
            val addresses = if (Build.VERSION.SDK_INT >= 28) pairedAddresses(context, gear) else emptySet()
            outputs.any { out ->
                gear.matches(out.productName?.toString()) ||
                    (Build.VERSION.SDK_INT >= 28 && addresses.isNotEmpty() && out.address.uppercase() in addresses)
            }
        }.toSet()
    } catch (_: Throwable) {
        emptySet()
    }

    /** Android's full Bluetooth settings screen. */
    fun openBluetooth(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) { }
    }

    /**
     * A Bluetooth pop-up rather than the full settings screen: Samsung's Bluetooth pop-up on a
     * Galaxy, else the system's pop-up for picking where sound plays, else Bluetooth settings.
     */
    fun openBluetoothPopup(context: Context) {
        if (openSamsungBluetooth(context)) return
        openOutputs(context)
    }

    /**
     * Samsung's Bluetooth pop-up (its Settings' BluetoothScanDialog: the paired devices and a
     * scan, over the screen you're on). Sources: the action is declared on that activity in a
     * published One UI SecSettings manifest (github.com/GrifoDev/SuperMan-ModdedFiles,
     * SecSettings/AndroidManifest.xml), and Samsung's own SystemUI starts it the same way
     * (github.com/488315/android_samsung_frameworks_base, packages/SystemUI/src/com/android/
     * settingslib/bluetooth/BluetoothEventManager.java). Not public: a phone without it, or one
     * that refuses it, throws and the next try runs. AOSP has no Bluetooth panel to try
     * (Settings' manifest declares only the internet, NFC, Wi-Fi and volume panels).
     */
    private fun openSamsungBluetooth(context: Context): Boolean = try {
        context.startActivity(Intent("com.samsung.settings.bluetooth.scandialog.LAUNCH").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Throwable) {
        false
    }

    /**
     * The system's pop-up for picking where sound plays (speakers and headphones), the one the
     * notification shade opens; Bluetooth settings where the phone has no such pop-up for apps.
     */
    private fun openOutputs(context: Context) {
        if (Build.VERSION.SDK_INT >= 34) {
            val shown = try { MediaRouter2.getInstance(context).showSystemOutputSwitcher() } catch (_: Throwable) { false }
            if (shown) return
        } else if (Build.VERSION.SDK_INT >= 30) {
            // SystemUI's own output dialog (AOSP's MediaOutputDialogReceiver). A broadcast can't say
            // whether anything showed, so this isn't promised: a phone whose SystemUI ignores it
            // shows nothing. Android 14+ has the call above instead.
            try {
                context.sendBroadcast(
                    Intent("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG")
                        .setPackage("com.android.systemui")
                        .putExtra("package_name", context.packageName),
                )
                return
            } catch (_: Throwable) { }
        }
        openBluetooth(context)
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
 * The music screen's row of four, under the play controls: Spotify, your speaker, your earbuds
 * and Bluetooth, then the volume. The speaker and earbuds light up orange while connected, with a
 * battery ring when the phone knows their level, and a tap opens their app. A tap on Bluetooth
 * switches it on or off; holding it opens the Bluetooth pop-up.
 */
@Composable
fun MusicButtons() {
    val context = LocalContext.current
    var connected by remember { mutableStateOf(Speakers.connected(context)) }
    var on by remember { mutableStateOf(Speakers.isOn(context)) }
    var polled by remember { mutableStateOf(Battery.levels(context)) }
    val heard = Battery.heard()
    var note by remember { mutableStateOf<String?>(null) }
    var wantOff by remember { mutableStateOf(false) }
    var askedOffAt by remember { mutableStateOf(0L) }
    val spotify = remember { NowPlaying.spotifyIcon(context) }

    fun popupInstead(why: String) {
        note = why
        Speakers.openBluetoothPopup(context)
    }
    val enable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        on = Speakers.isOn(context)
        note = if (on) null else "Bluetooth is still off."
    }
    // Settings asks "an app wants to turn off Bluetooth". An answer this quick means no question was
    // shown: the phone refused without asking.
    val disable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        on = Speakers.isOn(context)
        note = when {
            result.resultCode == Activity.RESULT_OK || !on -> null
            SystemClock.elapsedRealtime() - askedOffAt < 400 -> {
                Speakers.openBluetoothPopup(context)
                OFF_REFUSED
            }
            else -> "Bluetooth is still on."
        }
    }
    fun turnOn() {
        try {
            enable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } catch (_: Throwable) {
            note = "Android wouldn't turn Bluetooth on from here, so the Bluetooth screen opened."
            Speakers.openBluetooth(context)
        }
    }
    // Apps for Android 13+ can't switch Bluetooth off themselves; this asks the system (a hidden action).
    fun turnOff() {
        try {
            askedOffAt = SystemClock.elapsedRealtime()
            disable.launch(Intent("android.bluetooth.adapter.action.REQUEST_DISABLE"))
        } catch (_: Throwable) {
            popupInstead(OFF_REFUSED)
        }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        when {
            ok && wantOff -> turnOff()
            ok -> turnOn()
            wantOff -> popupInstead("Switching Bluetooth off from here needs Nearby devices, so the Bluetooth pop-up opened.")
            else -> {
                note = "Turning Bluetooth on from here needs Nearby devices, so the Bluetooth screen opened."
                Speakers.openBluetooth(context)
            }
        }
    }
    fun tapBluetooth() {
        note = null
        wantOff = Speakers.isOn(context)
        val permission = Speakers.permission
        when {
            permission != null && !Speakers.allowed(context) -> ask.launch(permission)
            wantOff -> turnOff()
            else -> turnOn()
        }
    }
    fun holdBluetooth() {
        note = null
        Speakers.openBluetoothPopup(context)
    }

    // Connections and batteries change from elsewhere: the speaker's own button, Android's Bluetooth screen.
    LaunchedEffect(Unit) {
        while (true) {
            connected = Speakers.connected(context)
            on = Speakers.isOn(context)
            polled = Battery.levels(context)
            heard.keys.filter { it !in connected }.forEach { heard.remove(it) }
            delay(1_500)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SquareButton("Open Spotify", Dark.Frost, Modifier.weight(1f), onTap = {
                note = if (NowPlaying.openSpotify(context)) null else "Spotify couldn't be opened."
            }) {
                if (spotify != null) Image(spotify, contentDescription = null, modifier = Modifier.size(30.dp))
                else Glyph(Glyphs.Play, 22.dp, Color.White)
            }
            Speakers.ALL.forEach { gear ->
                val lit = gear in connected
                val battery = if (lit) polled[gear] ?: heard[gear] else null
                SquareButton(
                    "${gear.name}, ${if (lit) "connected" else "not connected"}${battery?.let { ", battery $it%" } ?: ""}",
                    if (lit) Color(0xFFFF5A00) else Dark.Frost,
                    Modifier.weight(1f),
                    onTap = { note = Speakers.openApp(context, gear) },
                ) {
                    val colour = if (lit) Color.White else Dark.W62
                    if (battery != null) BatteryRing(battery) { Glyph(gear.glyph, 20.dp, colour) }
                    else Glyph(gear.glyph, 24.dp, colour)
                }
            }
            SquareButton(
                if (on) "Bluetooth, on" else "Bluetooth, off",
                if (on) Dark.FrostHi else Dark.Frost,
                Modifier.weight(1f),
                onTap = ::tapBluetooth,
                tapLabel = if (on) "switch Bluetooth off" else "switch Bluetooth on",
                onHold = ::holdBluetooth,
                holdLabel = "open the Bluetooth menu",
            ) { Glyph(Glyphs.Bluetooth, 22.dp, if (on) Color.White else Dark.W40) }
        }
        VolumeRow { note = it }
        note?.let { Text(it, color = Dark.W62, fontSize = 11.sp) }
    }
}

private const val OFF_REFUSED = "Android didn't let the launcher switch Bluetooth off, so the Bluetooth pop-up opened."

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SquareButton(
    label: String,
    fill: Color,
    modifier: Modifier,
    onTap: () -> Unit,
    tapLabel: String? = null,
    onHold: (() -> Unit)? = null,
    holdLabel: String? = null,
    icon: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(fill)
            .semantics { contentDescription = label }
            .combinedClickable(
                role = Role.Button,
                onClickLabel = tapLabel,
                onLongClick = onHold,
                onLongClickLabel = holdLabel,
                onClick = onTap,
            ),
    ) { icon() }
}
