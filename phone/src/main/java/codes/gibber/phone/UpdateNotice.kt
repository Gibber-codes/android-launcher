package codes.gibber.phone

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** The "Update ready" notification, like the launcher's: a tap brings Gibber Phone up and installs quietly. */
object UpdateNotice {
    const val ACTION = "codes.gibber.phone.action.UPDATE"
    private const val CHANNEL = "updates"
    private const val ID = 4209
    private const val PREFS = "updates"

    /** A look on GitHub at most every half hour, when you open Gibber Phone. */
    private var lastCheck = 0L

    fun due(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastCheck < 30 * 60_000L) return false
        lastCheck = now
        return true
    }

    fun forget() {
        lastCheck = 0L
    }

    fun shownFor(context: Context): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("notified", 0)

    fun setShownFor(context: Context, build: Int) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("notified", build).apply()

    private fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, build: Int) {
        if (!allowed(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "People updates", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Says when a new People build is out" },
        )
        val tap = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).setAction(ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notice = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("People update ready")
            .setContentText("Build $build is out. Tap to update.")
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(context).notify(ID, notice) } catch (_: SecurityException) { }
    }

    fun clear(context: Context) = NotificationManagerCompat.from(context).cancel(ID)
}
