package codes.gibber.launcher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/** Real Android widgets, like Spotify's, living on the home screen. */
object Widgets {
    private const val HOST_ID = 4207

    @Volatile private var hostRef: AppWidgetHost? = null

    fun host(context: Context): AppWidgetHost =
        hostRef ?: synchronized(this) {
            hostRef ?: AppWidgetHost(context.applicationContext, HOST_ID).also { hostRef = it }
        }

    fun manager(context: Context): AppWidgetManager = AppWidgetManager.getInstance(context)

    /** Every widget your apps offer, by app then widget name. */
    fun available(context: Context): List<AppWidgetProviderInfo> {
        val pm = context.packageManager
        return manager(context).installedProviders.sortedWith(
            compareBy<AppWidgetProviderInfo>({ appName(context, it).lowercase() }, { it.loadLabel(pm).lowercase() })
        )
    }

    fun appName(context: Context, info: AppWidgetProviderInfo): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
    } catch (e: Exception) {
        info.provider.packageName
    }
}

/**
 * The widgets on the home screen, plus the steps to add one: pick it, let the launcher show it
 * (Android asks once per app), then the widget's own setup if it has one.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WidgetArea(
    ids: List<Int>,
    onIdsChange: (List<Int>) -> Unit,
    picking: Boolean,
    onPickingChange: (Boolean) -> Unit,
    removing: Boolean,
    onRemovingChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val host = remember { Widgets.host(context) }
    val manager = remember { Widgets.manager(context) }
    var pending by remember { mutableStateOf<Pair<Int, AppWidgetProviderInfo>?>(null) }

    fun finish(id: Int) {
        pending = null
        onIdsChange(ids + id)
    }
    fun drop(id: Int) {
        pending = null
        host.deleteAppWidgetId(id)
    }

    val configure = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val p = pending ?: return@rememberLauncherForActivityResult
        if (result.resultCode == Activity.RESULT_OK) finish(p.first) else drop(p.first)
    }
    fun setUp(id: Int, info: AppWidgetProviderInfo) {
        val cfg = info.configure
        if (cfg == null) return finish(id)
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            .setComponent(cfg)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        try {
            configure.launch(intent)
        } catch (e: Exception) {
            // Some setup screens only open for the system; show the widget as it is.
            finish(id)
        }
    }
    val bind = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val p = pending ?: return@rememberLauncherForActivityResult
        if (result.resultCode == Activity.RESULT_OK) setUp(p.first, p.second) else drop(p.first)
    }
    fun add(info: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        pending = id to info
        if (manager.bindAppWidgetIdIfAllowed(id, info.provider)) {
            setUp(id, info)
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            try {
                bind.launch(intent)
            } catch (e: Exception) {
                drop(id)
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        ids.forEach { id ->
            val info = manager.getAppWidgetInfo(id)
            if (info == null) {
                // The app that made it was removed.
                return@forEach
            }
            WidgetView(id = id, info = info, onRemove = {
                host.deleteAppWidgetId(id)
                onIdsChange(ids - id)
            })
        }
    }

    if (removing) {
        val pm = context.packageManager
        AlertDialog(
            onDismissRequest = { onRemovingChange(false) },
            title = { Text("Remove a widget") },
            text = {
                Column {
                    if (ids.isEmpty()) Text("There are no widgets on your home screen.")
                    ids.forEach { id ->
                        val info = manager.getAppWidgetInfo(id)
                        Text(
                            if (info == null) "A removed app's widget" else "${Widgets.appName(context, info)}: ${info.loadLabel(pm)}",
                            color = Color.White,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    host.deleteAppWidgetId(id)
                                    onIdsChange(ids - id)
                                    onRemovingChange(false)
                                }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onRemovingChange(false) }) { Text("Cancel") } },
        )
    }

    if (picking) {
        val all = remember { Widgets.available(context) }
        val pm = context.packageManager
        AlertDialog(
            onDismissRequest = { onPickingChange(false) },
            title = { Text("Add a widget") },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    if (all.isEmpty()) Text("None of your apps offer a widget.")
                    all.forEach { info ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onPickingChange(false)
                                    add(info)
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(Widgets.appName(context, info), color = Color.White, fontSize = 16.sp)
                            Text(info.loadLabel(pm), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onPickingChange(false) }) { Text("Cancel") } },
        )
    }
}

/** One widget, as tall as it asks to be. Hold it to remove it. */
@Composable
private fun WidgetView(id: Int, info: AppWidgetProviderInfo, onRemove: () -> Unit) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val density = context.resources.displayMetrics.density
    val tall = (info.minHeight / density).coerceIn(56f, 400f)
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(tall.dp),
            factory = { ctx ->
                Widgets.host(ctx).createView(ctx, id, info).apply {
                    setOnLongClickListener { menuOpen = true; true }
                    val w = (ctx.resources.displayMetrics.widthPixels / density).toInt()
                    @Suppress("DEPRECATION")
                    updateAppWidgetSize(Bundle(), w, tall.toInt(), w, tall.toInt())
                }
            },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Remove widget") }, onClick = { menuOpen = false; onRemove() })
        }
    }
}
