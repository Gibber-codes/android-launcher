package codes.gibber.launcher

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

/** The "Update ready" notification: tapping it brings the launcher up and starts the update. */
object UpdateNotice {
    const val ACTION = "codes.gibber.launcher.action.UPDATE"
    private const val CHANNEL = "updates"
    private const val ID = 4208

    fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, build: Int) {
        if (!allowed(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Launcher updates", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Says when a new Gibber Launcher build is out" }
        )
        val tap = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).setAction(ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notice = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Gibber Launcher update ready")
            .setContentText("Build $build is out. Tap to update.")
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(context).notify(ID, notice) } catch (e: SecurityException) { }
    }

    fun clear(context: Context) = NotificationManagerCompat.from(context).cancel(ID)
}
