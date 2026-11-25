package codes.gibber.phone.sms

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.app.RemoteInput
import codes.gibber.phone.data.Numbers
import codes.gibber.phone.data.Texts
import java.io.File
import kotlin.concurrent.thread

/** A new text. As the default texting app we save it ourselves, then show a notification. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return
        val first = parts[0]
        val address = first.displayOriginatingAddress ?: first.originatingAddress ?: return
        val body = parts.joinToString("") { it.displayMessageBody ?: it.messageBody ?: "" }
        val result = goAsync()
        thread {
            try {
                val threadId = Texts.threadFor(context, listOf(address))
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, address)
                    put(Telephony.Sms.BODY, body)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.DATE_SENT, first.timestampMillis)
                    put(Telephony.Sms.READ, 0)
                    put(Telephony.Sms.SEEN, 0)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                    put(Telephony.Sms.THREAD_ID, threadId)
                    put(Telephony.Sms.PROTOCOL, first.protocolIdentifier)
                    first.serviceCenterAddress?.let { put(Telephony.Sms.SERVICE_CENTER, it) }
                    val sub = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1)
                    if (sub >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, sub)
                }
                context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
                Notifier.newText(context, threadId)
            } catch (_: Exception) {
            } finally {
                result.finish()
            }
        }
    }
}

/** The carrier's notice that a picture or group message is waiting. We download it right away. */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        val data = intent.getByteArrayExtra("data") ?: return
        val notice = try {
            Pdu.parse(data)
        } catch (_: Exception) {
            return
        }
        if (notice.type != Pdu.NOTIFICATION_IND) return
        val location = notice.contentLocation ?: return
        Sender.download(context, location, notice.transactionId, notice.from)
    }
}

/** Results from Android: a text was sent (or not), a picture message finished downloading. */
class MessageStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Sender.ACTION_SMS_SENT -> smsSent(context, intent)
            Sender.ACTION_MMS_SENT -> mmsSent(context, intent)
            Sender.ACTION_MMS_DOWNLOADED -> {
                val code = resultCode
                val result = goAsync()
                thread {
                    try {
                        mmsDownloaded(context, intent, code)
                    } finally {
                        result.finish()
                    }
                }
            }
            Sender.ACTION_ACK_SENT -> intent.getStringExtra("file")?.let { File(it).delete() }
        }
    }

    private fun smsSent(context: Context, intent: Intent) {
        val uri = intent.data ?: return
        val part = intent.getIntExtra("part", 0)
        val parts = intent.getIntExtra("parts", 1)
        if (resultCode != Activity.RESULT_OK) {
            Sender.markSms(context, uri, Telephony.Sms.MESSAGE_TYPE_FAILED)
            val info = context.contentResolver.query(uri, arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getLong(0) to (c.getString(1) ?: "") else null }
            if (info != null) Notifier.sendFailed(context, info.first, info.second)
            return
        }
        if (part == parts - 1) {
            val failed = context.contentResolver.query(uri, arrayOf(Telephony.Sms.TYPE), null, null, null)
                ?.use { c -> c.moveToFirst() && c.getInt(0) == Telephony.Sms.MESSAGE_TYPE_FAILED } ?: false
            if (!failed) Sender.markSms(context, uri, Telephony.Sms.MESSAGE_TYPE_SENT)
        }
    }

    private fun mmsSent(context: Context, intent: Intent) {
        intent.getStringExtra("file")?.let { File(it).delete() }
        val uri = intent.data ?: return
        var ok = resultCode == Activity.RESULT_OK
        var messageId: String? = null
        intent.getByteArrayExtra(SmsManager.EXTRA_MMS_DATA)?.let { bytes ->
            try {
                val conf = Pdu.parse(bytes)
                messageId = conf.messageId
                if (conf.responseStatus != null && conf.responseStatus != Pdu.RESPONSE_OK) ok = false
            } catch (_: Exception) {
            }
        }
        MmsStore.setBox(context, uri, if (ok) Telephony.Mms.MESSAGE_BOX_SENT else Telephony.Mms.MESSAGE_BOX_FAILED, messageId)
        if (!ok) {
            val thread = intent.getLongExtra("thread", -1)
            if (thread >= 0) Notifier.sendFailed(context, thread, null)
        }
    }

    private fun mmsDownloaded(context: Context, intent: Intent, code: Int) {
        val path = intent.getStringExtra("file") ?: return
        val location = intent.getStringExtra("location") ?: return
        val from = intent.getStringExtra("from")
        val file = File(path)
        val bytes = if (file.exists()) file.readBytes() else ByteArray(0)
        file.delete()
        val msg = if (code == Activity.RESULT_OK && bytes.isNotEmpty()) try {
            Pdu.parse(bytes)
        } catch (_: Exception) {
            null
        } else null
        if (msg == null || msg.type != Pdu.RETRIEVE_CONF) {
            Pending.add(context, location, intent.getStringExtra("tx"), from)
            Notifier.downloadFailed(context, from)
            return
        }
        val threadId = MmsStore.saveIncoming(context, msg)
        if (threadId == null) {
            Pending.add(context, location, intent.getStringExtra("tx"), from)
            Notifier.downloadFailed(context, from)
            return
        }
        Pending.remove(context, location)
        val tx = msg.transactionId ?: intent.getStringExtra("tx")
        if (tx != null) Sender.acknowledge(context, tx)
        Notifier.newText(context, threadId)
    }
}

/** Reply and Mark read on a text's notification. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra("thread", -1)
        if (threadId < 0) return
        val result = goAsync()
        thread {
            try {
                when (intent.action) {
                    Notifier.ACTION_REPLY -> {
                        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifier.KEY_REPLY)?.toString()
                        val people = intent.getStringArrayExtra("addresses")?.toList() ?: emptyList()
                        if (!text.isNullOrBlank() && people.isNotEmpty()) {
                            Sender.send(context, people, text)
                        }
                        Texts.markRead(context, threadId)
                        Notifier.cancel(context, threadId)
                    }
                    Notifier.ACTION_MARK_READ -> {
                        Texts.markRead(context, threadId)
                        Notifier.cancel(context, threadId)
                    }
                }
            } catch (_: Exception) {
            } finally {
                result.finish()
            }
        }
    }
}

/** "Reply with a text" when you turn down a call. */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        val number = intent?.data?.let { recipientsOf(it) } ?: emptyList()
        if (!text.isNullOrBlank() && number.isNotEmpty()) {
            thread {
                try {
                    Sender.send(applicationContext, number, text)
                } catch (_: Exception) {
                }
                stopSelf(startId)
            }
        } else {
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }
}

/** The people in an sms:, smsto:, mms: or mmsto: link ("sms:9705550100,9705550101"). */
fun recipientsOf(uri: Uri): List<String> {
    val part = uri.schemeSpecificPart ?: return emptyList()
    return part.substringBefore('?').split(',', ';').map { Numbers.clean(Uri.decode(it)) }.filter { it.isNotBlank() }
}
