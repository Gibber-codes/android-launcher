package codes.gibber.phone.sms

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.FileProvider
import codes.gibber.phone.data.Numbers
import codes.gibber.phone.data.Texts
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.max

/** Sends texts and picture messages and keeps Android's message store up to date (the default app's job). */
object Sender {
    const val ACTION_SMS_SENT = "codes.gibber.phone.SMS_SENT"
    const val ACTION_MMS_SENT = "codes.gibber.phone.MMS_SENT"
    const val ACTION_MMS_DOWNLOADED = "codes.gibber.phone.MMS_DOWNLOADED"
    const val ACTION_ACK_SENT = "codes.gibber.phone.ACK_SENT"

    /** Packages that run Android's MMS service; they need to read and write our PDU files. */
    private val MMS_PACKAGES = listOf("com.android.phone", "com.android.mms.service")

    fun smsManager(context: Context): SmsManager {
        val sub = SubscriptionManager.getDefaultSmsSubscriptionId()
        return if (Build.VERSION.SDK_INT >= 31) {
            val base = context.getSystemService(SmsManager::class.java)
            if (sub != SubscriptionManager.INVALID_SUBSCRIPTION_ID) base.createForSubscriptionId(sub) else base
        } else {
            @Suppress("DEPRECATION")
            if (sub != SubscriptionManager.INVALID_SUBSCRIPTION_ID) SmsManager.getSmsManagerForSubscriptionId(sub) else SmsManager.getDefault()
        }
    }

    /**
     * Sends a message to one person or a group. One person and no pictures goes as a plain text;
     * a group or a picture goes as MMS, which is how group chats work over SMS.
     */
    fun send(context: Context, addresses: List<String>, body: String, images: List<Uri> = emptyList()): Long {
        val clean = addresses.map { Numbers.clean(it) }.filter { it.isNotBlank() }.distinct()
        require(clean.isNotEmpty()) { "No one to send to" }
        val threadId = Texts.threadFor(context, clean)
        if (clean.size == 1 && images.isEmpty()) sendSms(context, clean[0], body, threadId)
        else sendMms(context, clean, body, images, threadId)
        return threadId
    }

    private fun sendSms(context: Context, address: String, body: String, threadId: Long) {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            put(Telephony.Sms.THREAD_ID, threadId)
        }
        val uri = context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
        val sms = smsManager(context)
        val parts = sms.divideMessage(body)
        val sent = ArrayList<PendingIntent>()
        for (i in parts.indices) {
            val intent = Intent(ACTION_SMS_SENT, uri, context, MessageStatusReceiver::class.java)
                .putExtra("part", i).putExtra("parts", parts.size)
            sent.add(PendingIntent.getBroadcast(context, requestCode(), intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        try {
            if (parts.size == 1) sms.sendTextMessage(address, null, body, sent[0], null)
            else sms.sendMultipartTextMessage(address, null, parts, sent, null)
        } catch (e: Exception) {
            uri?.let { markSms(context, it, Telephony.Sms.MESSAGE_TYPE_FAILED) }
            Notifier.sendFailed(context, threadId, address)
        }
    }

    fun markSms(context: Context, uri: Uri, type: Int) {
        val values = ContentValues().apply { put(Telephony.Sms.TYPE, type) }
        try {
            context.contentResolver.update(uri, values, null, null)
        } catch (_: Exception) {
        }
    }

    private var lastCode = (System.currentTimeMillis() % 100_000).toInt()
    @Synchronized
    fun requestCode(): Int = ++lastCode

    /** Carriers cap picture messages; AT&T's is about 1 MB. Leave room for the rest of the message. */
    private fun maxImageBytes(context: Context, count: Int): Int {
        val carrier = try {
            smsManager(context).carrierConfigValues.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE, 600 * 1024)
        } catch (_: Exception) {
            600 * 1024
        }
        val total = (carrier.coerceIn(300 * 1024, 1024 * 1024) * 0.85).toInt()
        return max(60 * 1024, total / max(1, count))
    }

    /** Shrinks a picture until it fits, as a JPEG. */
    private fun squeeze(context: Context, uri: Uri, limit: Int): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        bmp = rotateIfNeeded(context, uri, bmp)
        var quality = 85
        var scale = 1.0
        while (true) {
            val scaled = if (scale < 1.0) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true) else bmp
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (out.size() <= limit || (quality <= 40 && scale < 0.2)) return out.toByteArray()
            if (quality > 50) quality -= 15 else scale *= 0.75
        }
    }

    private fun rotateIfNeeded(context: Context, uri: Uri, bmp: Bitmap): Bitmap {
        val degrees = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                when (androidx.exifinterface.media.ExifInterface(input).getAttributeInt(
                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, 1,
                )) {
                    6 -> 90f
                    3 -> 180f
                    8 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (_: Exception) {
            0f
        }
        if (degrees == 0f) return bmp
        val m = android.graphics.Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    private fun sendMms(context: Context, addresses: List<String>, body: String, images: List<Uri>, threadId: Long) {
        val parts = ArrayList<Pdu.Part>()
        val limit = maxImageBytes(context, images.size)
        images.forEachIndexed { i, uri ->
            val data = squeeze(context, uri, limit) ?: return@forEachIndexed
            parts.add(Pdu.Part("image/jpeg", data, name = "image$i.jpg", contentId = "image$i", contentLocation = "image$i.jpg"))
        }
        if (body.isNotBlank()) {
            parts.add(Pdu.Part("text/plain", body.toByteArray(Charsets.UTF_8), name = "text0.txt", contentId = "text0", contentLocation = "text0.txt", charset = Pdu.UTF8))
        }
        if (parts.isEmpty()) return
        val txId = "T" + UUID.randomUUID().toString().replace("-", "").take(20)
        val now = System.currentTimeMillis()
        val pdu = Pdu.sendRequest(addresses, null, parts, txId, now / 1000)

        val messageUri = MmsStore.saveOutgoing(context, threadId, addresses, parts, txId, now)
        val file = File(File(context.cacheDir, "mms").apply { mkdirs() }, "send-$txId.pdu")
        file.writeBytes(pdu)
        val contentUri = shareFile(context, file, write = false)
        val intent = Intent(ACTION_MMS_SENT, messageUri, context, MessageStatusReceiver::class.java)
            .putExtra("file", file.absolutePath).putExtra("thread", threadId)
        val sent = PendingIntent.getBroadcast(context, requestCode(), intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            smsManager(context).sendMultimediaMessage(context, contentUri, null, null, sent)
        } catch (e: Exception) {
            messageUri?.let { MmsStore.setBox(context, it, Telephony.Mms.MESSAGE_BOX_FAILED) }
            Notifier.sendFailed(context, threadId, addresses.first())
        }
    }

    /** Hands a file in our cache to Android's MMS service. */
    fun shareFile(context: Context, file: File, write: Boolean): Uri {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val flags = if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
        else Intent.FLAG_GRANT_READ_URI_PERMISSION
        for (pkg in MMS_PACKAGES) {
            try {
                context.grantUriPermission(pkg, uri, flags)
            } catch (_: Exception) {
            }
        }
        return uri
    }

    /** Asks Android to fetch a picture message the carrier told us about. */
    @SuppressLint("MissingPermission")
    fun download(context: Context, location: String, transactionId: String?, from: String?) {
        val file = File(File(context.cacheDir, "mms").apply { mkdirs() }, "get-${System.currentTimeMillis()}.pdu")
        file.createNewFile()
        val uri = shareFile(context, file, write = true)
        val intent = Intent(ACTION_MMS_DOWNLOADED, null, context, MessageStatusReceiver::class.java)
            .putExtra("file", file.absolutePath)
            .putExtra("location", location)
            .putExtra("tx", transactionId)
            .putExtra("from", from)
        val done = PendingIntent.getBroadcast(context, requestCode(), intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            smsManager(context).downloadMultimediaMessage(context, location, uri, null, done)
        } catch (e: Exception) {
            Pending.add(context, location, transactionId, from)
            Notifier.downloadFailed(context, from)
        }
    }

    /** Tells the carrier we got the message. If this doesn't go through, nothing is lost. */
    fun acknowledge(context: Context, transactionId: String) {
        try {
            val file = File(File(context.cacheDir, "mms").apply { mkdirs() }, "ack-${System.currentTimeMillis()}.pdu")
            file.writeBytes(Pdu.acknowledge(transactionId))
            val uri = shareFile(context, file, write = false)
            val intent = Intent(ACTION_ACK_SENT, null, context, MessageStatusReceiver::class.java).putExtra("file", file.absolutePath)
            val done = PendingIntent.getBroadcast(context, requestCode(), intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            smsManager(context).sendMultimediaMessage(context, uri, null, null, done)
        } catch (_: Exception) {
        }
    }

    /** Tries again on a text that didn't go through: removes the failed one and sends it fresh. */
    fun retry(context: Context, msgId: Long, mms: Boolean, addresses: List<String>, body: String) {
        val uri = if (mms) Uri.parse("content://mms/$msgId") else Uri.parse("content://sms/$msgId")
        val images = if (mms) MmsStore.imagesOf(context, msgId) else emptyList()
        val copies = images.mapIndexedNotNull { i, part ->
            try {
                val f = File(File(context.cacheDir, "mms").apply { mkdirs() }, "retry-$msgId-$i.jpg")
                context.contentResolver.openInputStream(part)?.use { input -> f.outputStream().use { input.copyTo(it) } }
                Uri.fromFile(f)
            } catch (_: Exception) {
                null
            }
        }
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (_: Exception) {
        }
        send(context, addresses, body, copies)
    }
}
