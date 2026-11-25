package codes.gibber.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRouter2
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
    val XTREME = Gear("JBL Xtreme 5", "Xtreme 5", listOf("Gabes JBL", "Xtreme"), Glyphs.Speaker, "JBL One")
    val ENDURANCE = Gear("JBL Endurance", "Endurance", listOf("Endurance"), Glyphs.Headphones, "JBL Headphones")
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

    /** The addresses of paired devices known by one of the gear's names, when Android lets the launcher see them. */
    @SuppressLint("MissingPermission")
    private fun pairedAddresses(context: Context, gear: Gear): Set<String> {
        if (!allowed(context)) return emptySet()
        return try {
            adapter(context)?.bondedDevices.orEmpty()
                .filter { d -> gear.matches(d.name) || (Build.VERSION.SDK_INT >= 30 && gear.matches(d.alias)) }
                .mapNotNull { it.address?.uppercase()?.ifBlank { null } }
                .toSet()
        } catch (_: Throwable) {
            emptySet()
        }
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
     * The system's pop-up for picking where sound plays (speakers and headphones), the one the
     * notification shade opens; Bluetooth settings where the phone has no such pop-up for apps.
     */
    fun openOutputs(context: Context) {
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
 * Your speaker and headphones under Playing on, each saying whether it's connected; a tap opens
 * its app. Beside them, the Bluetooth button: the system's pop-up for where sound plays, or turning
 * Bluetooth on if it's off.
 */
@Composable
fun SpeakersPanel() {
    val context = LocalContext.current
    var connected by remember { mutableStateOf(Speakers.connected(context)) }
    var on by remember { mutableStateOf(Speakers.isOn(context)) }
    var note by remember { mutableStateOf<String?>(null) }

    val enable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        on = Speakers.isOn(context)
        note = if (on) null else "Bluetooth is still off."
    }
    fun turnOn() {
        try {
            enable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } catch (_: Throwable) {
            note = "Android wouldn't turn Bluetooth on from here, so the Bluetooth screen opened."
            Speakers.openBluetooth(context)
        }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) turnOn() else {
            note = "Turning Bluetooth on from here needs Nearby devices, so the Bluetooth screen opened."
            Speakers.openBluetooth(context)
        }
    }
    fun tapBluetooth() {
        note = null
        val permission = Speakers.permission
        when {
            Speakers.isOn(context) -> Speakers.openOutputs(context)
            permission != null && !Speakers.allowed(context) -> ask.launch(permission)
            else -> turnOn()
        }
    }

    // Connections change from elsewhere: the speaker's own button, Android's Bluetooth screen.
    LaunchedEffect(Unit) {
        while (true) {
            connected = Speakers.connected(context)
            on = Speakers.isOn(context)
            delay(1_500)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
            Speakers.ALL.forEach { gear ->
                GearTile(gear, gear in connected, Modifier.weight(1f).fillMaxHeight()) { note = Speakers.openApp(context, gear) }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(56.dp)
                    .fillMaxHeight()
                    .frost(hi = on)
                    .clickable { tapBluetooth() },
            ) { Glyph(Glyphs.Bluetooth, 22.dp, if (on) Color.White else Dark.W62) }
        }
        note?.let { Text(it, color = Dark.W62, fontSize = 11.sp) }
    }
}

@Composable
private fun GearTile(gear: Gear, connected: Boolean, modifier: Modifier, onTap: () -> Unit) {
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
                    if (connected) "Connected" else "Not connected",
                    color = if (connected) Color.White else Dark.W62,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }
    }
}
