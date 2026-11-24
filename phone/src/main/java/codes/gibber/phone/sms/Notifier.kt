package codes.gibber.phone.sms

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import codes.gibber.phone.MainActivity
import codes.gibber.phone.PhoneApp
import codes.gibber.phone.R
import codes.gibber.phone.data.Contacts
import codes.gibber.phone.data.Numbers
import codes.gibber.phone.data.Texts

/** Text notifications: one per conversation, with Reply and Mark read. */
object Notifier {
    const val ACTION_REPLY = "codes.gibber.phone.REPLY"
    const val ACTION_MARK_READ = "codes.gibber.phone.MARK_READ"
    const val KEY_REPLY = "reply"
    private const val FAIL_BASE = 500_000
    private const val DOWNLOAD_FAIL = 499_999

    /** Which conversation is open on screen right now, so it doesn't buzz at you. */
    @Volatile var openThread: Long = -1

    private fun notificationId(threadId: Long) = (threadId % 400_000).toInt() + 1000

    fun openIntent(context: Context, threadId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction("codes.gibber.phone.OPEN_THREAD")
            .putExtra("thread", threadId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, threadId.toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun personFor(context: Context, address: String): Person {
        val match = Contacts.lookup(context, address)
        val b = Person.Builder().setName(match?.name ?: Numbers.pretty(address)).setKey(Numbers.key(address))
        match?.photo?.let { photo ->
            loadBitmap(context, Uri.parse(photo), 192)?.let { b.setIcon(IconCompat.createWithBitmap(it)) }
        }
        return b.build()
    }

    @SuppressLint("MissingPermission")
    fun newText(context: Context, threadId: Long) {
        PhoneApp.instance.bump()
        if (threadId == openThread) return
        val messages = Texts.unreadIn(context, threadId)
        if (messages.isEmpty()) return
        val conversation = Texts.conversations(context).firstOrNull { it.id == threadId }
        val addresses = conversation?.addresses ?: messages.mapNotNull { it.from }.distinct()
        val me = Person.Builder().setName("You").build()
        val style = NotificationCompat.MessagingStyle(me)
        if (addresses.size > 1) {
            style.isGroupConversation = true
            style.conversationTitle = codes.gibber.phone.data.GroupNames.title(context, addresses)
        }
        val people = HashMap<String, Person>()
        var picture: Bitmap? = null
        for (m in messages) {
            val from = m.from ?: continue
            val person = people.getOrPut(Numbers.key(from)) { personFor(context, from) }
            val text = when {
                m.body.isNotBlank() -> m.body
                m.images.isNotEmpty() -> "Picture"
                else -> "Message"
            }
            val line = NotificationCompat.MessagingStyle.Message(text, m.date, person)
            m.images.firstOrNull()?.let { img ->
                line.setData("image/jpeg", img)
                if (picture == null) picture = loadBitmap(context, img, 600)
            }
            style.addMessage(line)
        }

        val reply = RemoteInput.Builder(KEY_REPLY).setLabel("Reply").build()
        val replyIntent = Intent(ACTION_REPLY, null, context, NotificationActionReceiver::class.java)
            .putExtra("thread", threadId).putExtra("addresses", addresses.toTypedArray())
        val replyPending = PendingIntent.getBroadcast(context, threadId.toInt(), replyIntent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val readIntent = Intent(ACTION_MARK_READ, null, context, NotificationActionReceiver::class.java).putExtra("thread", threadId)
        val readPending = PendingIntent.getBroadcast(context, threadId.toInt(), readIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val n = NotificationCompat.Builder(context, PhoneApp.CHANNEL_TEXTS)
            .setSmallIcon(R.drawable.ic_notify_text)
            .setStyle(style)
            .setContentIntent(openIntent(context, threadId))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                NotificationCompat.Action.Builder(0, "Reply", replyPending)
                    .addRemoteInput(reply)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                    .setAllowGeneratedReplies(true)
                    .build(),
            )
            .addAction(
                NotificationCompat.Action.Builder(0, "Mark read", readPending)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                    .setShowsUserInterface(false)
                    .build(),
            )
        picture?.let { n.setLargeIcon(it) }
        try {
            NotificationManagerCompat.from(context).notify(notificationId(threadId), n.build())
        } catch (_: Exception) {
        }
    }

    fun cancel(context: Context, threadId: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(notificationId(threadId))
        PhoneApp.instance.bump()
    }

    @SuppressLint("MissingPermission")
    fun sendFailed(context: Context, threadId: Long, address: String?) {
        PhoneApp.instance.bump()
        val who = address?.let { Contacts.lookup(context, it)?.name ?: Numbers.pretty(it) }
        val n = NotificationCompat.Builder(context, PhoneApp.CHANNEL_PROBLEMS)
            .setSmallIcon(R.drawable.ic_notify_text)
            .setContentTitle(if (who != null) "Your text to $who didn't send" else "A text didn't send")
            .setContentText("Open the conversation and tap it to try again.")
            .setContentIntent(openIntent(context, threadId))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(FAIL_BASE + (threadId % 100_000).toInt(), n)
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    fun downloadFailed(context: Context, from: String?) {
        val who = from?.let { Numbers.clean(it) }?.let { Contacts.lookup(context, it)?.name ?: Numbers.pretty(it) }
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val n = NotificationCompat.Builder(context, PhoneApp.CHANNEL_PROBLEMS)
            .setSmallIcon(R.drawable.ic_notify_text)
            .setContentTitle(if (who != null) "A picture message from $who didn't download" else "A picture message didn't download")
            .setContentText("Mobile data has to be on for these. Open Gibber Phone to try again.")
            .setContentIntent(PendingIntent.getActivity(context, DOWNLOAD_FAIL, intent, PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(DOWNLOAD_FAIL, n)
        } catch (_: Exception) {
        }
    }

    fun loadBitmap(context: Context, uri: Uri, size: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) sample *= 2
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    } catch (_: Exception) {
        null
    }
}
