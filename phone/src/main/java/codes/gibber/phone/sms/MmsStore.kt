package codes.gibber.phone.sms

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import codes.gibber.phone.data.Numbers
import codes.gibber.phone.data.Texts
import org.json.JSONArray
import org.json.JSONObject

/** Writes picture and group messages into Android's shared message store, where every texting app reads them. */
object MmsStore {
    private const val ADDR_FROM = 137
    private const val ADDR_TO = 151
    private const val ADDR_CC = 130

    fun saveOutgoing(context: Context, threadId: Long, to: List<String>, parts: List<Pdu.Part>, txId: String, now: Long): Uri? {
        val values = ContentValues().apply {
            put(Telephony.Mms.THREAD_ID, threadId)
            put(Telephony.Mms.DATE, now / 1000)
            put(Telephony.Mms.DATE_SENT, now / 1000)
            put(Telephony.Mms.READ, 1)
            put(Telephony.Mms.SEEN, 1)
            put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_OUTBOX)
            put(Telephony.Mms.MESSAGE_TYPE, Pdu.SEND_REQ)
            put(Telephony.Mms.MMS_VERSION, 0x12)
            put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related")
            put(Telephony.Mms.MESSAGE_CLASS, "personal")
            put(Telephony.Mms.PRIORITY, 129)
            put(Telephony.Mms.READ_REPORT, 129)
            put(Telephony.Mms.DELIVERY_REPORT, 129)
            put(Telephony.Mms.TRANSACTION_ID, txId)
            put(Telephony.Mms.MESSAGE_SIZE, parts.sumOf { it.data.size })
        }
        val uri = context.contentResolver.insert(Telephony.Mms.CONTENT_URI, values) ?: return null
        val id = uri.lastPathSegment ?: return uri
        writeParts(context, id, parts)
        addAddress(context, id, "insert-address-token", ADDR_FROM)
        for (a in to) addAddress(context, id, Numbers.clean(a), ADDR_TO)
        return uri
    }

    fun setBox(context: Context, uri: Uri, box: Int, messageId: String? = null) {
        val values = ContentValues().apply {
            put(Telephony.Mms.MESSAGE_BOX, box)
            if (messageId != null) put(Telephony.Mms.MESSAGE_ID, messageId)
        }
        try {
            context.contentResolver.update(uri, values, null, null)
        } catch (_: Exception) {
        }
    }

    /** Saves a downloaded message and returns its conversation, or null when it couldn't be saved. */
    fun saveIncoming(context: Context, msg: Pdu.Message): Long? {
        val from = msg.from?.let { Numbers.clean(it) }?.takeIf { it.isNotBlank() } ?: "Unknown"
        val me = myNumbers(context)
        val others = (msg.to + msg.cc).map { Numbers.clean(it) }.filter { it.isNotBlank() }
        val othersNotMe = others.filter { o -> me.none { Numbers.same(it, o) } }
        // With your number unknown, a message to one other number is just to you.
        val rest = if (me.isEmpty() && others.size == 1) emptyList() else othersNotMe
        val people = (listOf(from) + rest).distinctBy { Numbers.key(it) }
        val threadId = Texts.threadFor(context, people)

        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(Telephony.Mms.THREAD_ID, threadId)
            put(Telephony.Mms.DATE, now / 1000)
            put(Telephony.Mms.DATE_SENT, msg.date ?: (now / 1000))
            put(Telephony.Mms.READ, 0)
            put(Telephony.Mms.SEEN, 0)
            put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_INBOX)
            put(Telephony.Mms.MESSAGE_TYPE, Pdu.RETRIEVE_CONF)
            put(Telephony.Mms.MMS_VERSION, 0x12)
            put(Telephony.Mms.CONTENT_TYPE, msg.contentType ?: "application/vnd.wap.multipart.related")
            msg.messageId?.let { put(Telephony.Mms.MESSAGE_ID, it) }
            msg.transactionId?.let { put(Telephony.Mms.TRANSACTION_ID, it) }
            msg.subject?.let {
                put(Telephony.Mms.SUBJECT, it)
                put(Telephony.Mms.SUBJECT_CHARSET, Pdu.UTF8)
            }
            put(Telephony.Mms.MESSAGE_SIZE, msg.parts.sumOf { it.data.size })
            if (Build.VERSION.SDK_INT >= 22) put(Telephony.Mms.SUBSCRIPTION_ID, SubscriptionManager.getDefaultSmsSubscriptionId())
        }
        val uri = try {
            context.contentResolver.insert(Telephony.Mms.CONTENT_URI, values)
        } catch (_: Exception) {
            null
        } ?: return null
        val id = uri.lastPathSegment ?: return threadId
        writeParts(context, id, msg.parts)
        addAddress(context, id, from, ADDR_FROM)
        for (a in msg.to) addAddress(context, id, Numbers.clean(a), ADDR_TO)
        for (a in msg.cc) addAddress(context, id, Numbers.clean(a), ADDR_CC)
        return threadId
    }

    private fun writeParts(context: Context, id: String, parts: List<Pdu.Part>) {
        val partsUri = Uri.parse("content://mms/$id/part")
        parts.forEachIndexed { i, p ->
            val values = ContentValues().apply {
                put("mid", id)
                put("seq", i)
                put("ct", p.contentType)
                p.contentId?.let { put("cid", "<$it>") }
                p.contentLocation?.let { put("cl", it) }
                p.name?.let { put("name", it) }
                p.charset?.let { put("chset", it) }
                if (p.contentType.startsWith("text/") || p.contentType == "application/smil") put("text", p.text())
            }
            try {
                val partUri = context.contentResolver.insert(partsUri, values) ?: return@forEachIndexed
                if (!p.contentType.startsWith("text/") && p.contentType != "application/smil") {
                    context.contentResolver.openOutputStream(partUri)?.use { it.write(p.data) }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun addAddress(context: Context, id: String, address: String, type: Int) {
        val values = ContentValues().apply {
            put("address", address)
            put("type", type)
            put("charset", Pdu.UTF8)
            put("msg_id", id)
        }
        try {
            context.contentResolver.insert(Uri.parse("content://mms/$id/addr"), values)
        } catch (_: Exception) {
        }
    }

    fun imagesOf(context: Context, msgId: Long): List<Uri> {
        val out = ArrayList<Uri>()
        try {
            context.contentResolver.query(Uri.parse("content://mms/$msgId/part"), arrayOf("_id", "ct"), null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    if ((c.getString(1) ?: "").startsWith("image/")) out.add(Uri.parse("content://mms/part/${c.getLong(0)}"))
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    /** Your own phone number(s), so a group text doesn't list you as one of the people in it. */
    @SuppressLint("MissingPermission", "HardwareIds")
    fun myNumbers(context: Context): List<String> {
        val out = ArrayList<String>()
        try {
            val sm = context.getSystemService(SubscriptionManager::class.java)
            for (info in sm.activeSubscriptionInfoList ?: emptyList()) {
                val n = if (Build.VERSION.SDK_INT >= 33) sm.getPhoneNumber(info.subscriptionId)
                else @Suppress("DEPRECATION") info.number
                if (!n.isNullOrBlank()) out.add(n)
            }
        } catch (_: Exception) {
        }
        if (out.isEmpty()) {
            try {
                @Suppress("DEPRECATION")
                context.getSystemService(TelephonyManager::class.java).line1Number?.let { if (it.isNotBlank()) out.add(it) }
            } catch (_: Exception) {
            }
        }
        return out
    }
}

/** Picture messages that didn't download, kept so you can try again. */
object Pending {
    private const val PREFS = "pending_mms"

    data class Item(val location: String, val transactionId: String?, val from: String?, val at: Long)

    fun list(context: Context): List<Item> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("items", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Item(o.getString("loc"), o.optString("tx").ifBlank { null }, o.optString("from").ifBlank { null }, o.optLong("at"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(context: Context, items: List<Item>) {
        val arr = JSONArray()
        for (i in items.takeLast(30)) {
            arr.put(JSONObject().put("loc", i.location).put("tx", i.transactionId ?: "").put("from", i.from ?: "").put("at", i.at))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("items", arr.toString()).apply()
        codes.gibber.phone.PhoneApp.instance.bump()
    }

    @Synchronized
    fun add(context: Context, location: String, transactionId: String?, from: String?) {
        val items = list(context).filter { it.location != location }
        save(context, items + Item(location, transactionId, from, System.currentTimeMillis()))
    }

    @Synchronized
    fun remove(context: Context, location: String) {
        save(context, list(context).filter { it.location != location })
    }

    /** Asks Android to fetch them all again. */
    fun retryAll(context: Context) {
        for (i in list(context)) {
            remove(context, i.location)
            Sender.download(context, i.location, i.transactionId, i.from)
        }
    }
}
