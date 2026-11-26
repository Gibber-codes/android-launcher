package codes.gibber.launcher

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.lang.reflect.Method

/**
 * Your Bluetooth gear's battery, as the phone hears it: 0 to 100, or nothing when it isn't known.
 * Android keeps the level a device reports (HFP or BLE battery service) behind a hidden call and a
 * hidden broadcast; a phone that blocks them, or a device that never reports, just has no level.
 */
object Battery {
    private const val CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
    private const val EXTRA_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"

    private val getter: Method? by lazy {
        try { BluetoothDevice::class.java.getMethod("getBatteryLevel") } catch (_: Throwable) { null }
    }

    private fun level(device: BluetoothDevice): Int? =
        try { (getter?.invoke(device) as? Int)?.takeIf { it in 0..100 } } catch (_: Throwable) { null }

    /** Each piece of gear whose paired device has a known level; only with Nearby devices already allowed. */
    fun levels(context: Context): Map<Gear, Int> {
        if (!Speakers.allowed(context)) return emptyMap()
        val bonded = Speakers.bonded(context)
        return Speakers.ALL.mapNotNull { gear ->
            bonded.filter { Speakers.isGear(gear, it) }.firstNotNullOfOrNull { level(it) }?.let { gear to it }
        }.toMap()
    }

    /** Levels the system announces while the screen shows, by gear; an unknown level (-1) drops it. */
    @Composable
    fun heard(): SnapshotStateMap<Gear, Int> {
        val context = LocalContext.current
        val heard = remember { mutableStateMapOf<Gear, Int>() }
        DisposableEffect(Unit) {
            val receiver = object : BroadcastReceiver() {
                @Suppress("DEPRECATION")
                override fun onReceive(c: Context, intent: Intent) {
                    try {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                        val gear = Speakers.ALL.firstOrNull { Speakers.isGear(it, device) } ?: return
                        val level = intent.getIntExtra(EXTRA_LEVEL, -1)
                        if (level in 0..100) heard[gear] = level else heard.remove(gear)
                    } catch (_: Throwable) { }
                }
            }
            val registered = try {
                ContextCompat.registerReceiver(context, receiver, IntentFilter(CHANGED), ContextCompat.RECEIVER_EXPORTED)
                true
            } catch (_: Throwable) {
                false
            }
            onDispose { if (registered) try { context.unregisterReceiver(receiver) } catch (_: Throwable) { } }
        }
        return heard
    }
}

/** A thin ring round an icon showing a battery level, the level small under it. */
@Composable
fun BatteryRing(level: Int, icon: @Composable () -> Unit) {
    val colour = when {
        level >= 50 -> Dark.Green
        level >= 20 -> Color(0xFFFFB020)
        else -> Color(0xFFFF3B3B)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(34.dp)) {
            Canvas(Modifier.size(34.dp)) {
                val w = 2.5.dp.toPx()
                val inset = w / 2
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                val arc = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
                drawArc(Color.White.copy(alpha = 0.22f), 0f, 360f, false, topLeft, arc, style = Stroke(w))
                drawArc(colour, -90f, 360f * level / 100f, false, topLeft, arc, style = Stroke(w, cap = StrokeCap.Round))
            }
            icon()
        }
        Text("$level%", color = Color.White, fontFamily = Dark.Mono, fontSize = 9.sp, lineHeight = 10.sp)
    }
}
