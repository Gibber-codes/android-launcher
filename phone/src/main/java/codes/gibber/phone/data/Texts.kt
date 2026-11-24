package codes.gibber.phone.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony

/** One conversation: a person, or a group of people. */
data class Conversation(
    val id: Long,
    val addresses: List<String>,
    val snippet: String,
    val date: Long,
    val unread: Boolean,
    /** A name the phone saved for the conversation, on phones that keep one where other apps can read it. */
    val title: String? = null,
) {
    val isGroup get() = addresses.size > 1
}

enum class MsgState { RECEIVED, SENDING, SENT, FAILED, WAITING }

/** One text or picture message. */
data class Msg(
    val id: Long,
    val mms: Boolean,
    val threadId: Long,
    /** Who sent it; null for your own messages. */
    val from: String?,
    val body: String,
    val date: Long,
    val state: MsgState,
    val images: List<Uri> = emptyList(),
    val otherFiles: List<String> = emptyList(),
) {
    val outgoing get() = from == null
}

object Texts {
    private val CONVERSATIONS: Uri = Uri.parse("content://mms-sms/conversations?simple=true")
    private val ADDRESSES: Uri = Uri.parse("content://mms-sms/canonical-addresses")
    private val PARTS: Uri = Uri.parse("content://mms/part")

    /** Every conversation, newest first. */
    fun conversations(context: Context): List<Conversation> {
        val cr = context.contentResolver
        val addressById = HashMap<String, String>()
        try {
            cr.query(ADDRESSES, arrayOf("_id", "address"), null, null, null)?.use { c ->
                while (c.moveToNext()) addressById[c.getString(0)] = c.getString(1) ?: ""
            }
        } catch (_: Exception) {
        }
        val unread = unreadThreads(context)
        val out = ArrayList<Conversation>()
        // Some phones' conversation lists lack a column or two, so ask again with less when the first ask fails.
        val asks = listOf(
            arrayOf("_id", "date", "recipient_ids", "snippet", "message_count"),
            arrayOf("_id", "date", "recipient_ids", "snippet"),
        )
        for (projection in listOf<Array<String>?>(null) + asks) {
            try {
                cr.query(CONVERSATIONS, projection, null, null, "date DESC")?.use { c ->
                    val idCol = c.getColumnIndexOrThrow("_id")
                    val dateCol = c.getColumnIndexOrThrow("date")
                    val recipCol = c.getColumnIndexOrThrow("recipient_ids")
                    val snippetCol = c.getColumnIndex("snippet")
                    val countCol = c.getColumnIndex("message_count")
                    val nameCol = nameColumn(c.columnNames)
                    while (c.moveToNext()) {
                        if (countCol >= 0 && !c.isNull(countCol) && c.getInt(countCol) == 0) continue
                        val id = c.getLong(idCol)
                        val addresses = (c.getString(recipCol) ?: "").split(' ').filter { it.isNotBlank() }
                            .mapNotNull { addressById[it] }.filter { it.isNotBlank() }
                        if (addresses.isEmpty()) continue
                        val title = if (nameCol >= 0) try { c.getString(nameCol)?.trim()?.takeIf { it.isNotEmpty() } } catch (_: Exception) { null } else null
                        out.add(
                            Conversation(
                                id, addresses, if (snippetCol >= 0) c.getString(snippetCol) ?: "" else "",
                                c.getLong(dateCol), id in unread, title,
                            ),
                        )
                    }
                }
                break
            } catch (_: Exception) {
                out.clear()
            }
        }
        if (out.isEmpty()) out.addAll(fromTexts(context, unread))
        return out
    }

    /** Column names some phones use for a conversation's own name. */
    private val NAME_COLUMNS = listOf(
        "group_name", "chat_name", "conversation_name", "conversation_title", "group_title",
        "subject", "title", "display_name", "name", "alias",
    )

    private fun nameColumn(columns: Array<String>): Int {
        val lower = columns.map { it.lowercase() }
        for (n in NAME_COLUMNS) {
            val i = lower.indexOf(n)
            if (i >= 0) return i
        }
        return -1
    }

    /** The phone's own name for a conversation, if it keeps one where Gibber Phone can read it. */
    fun titleOf(context: Context, threadId: Long): String? =
        conversations(context).firstOrNull { it.id == threadId }?.title

    /** Which columns the phone's conversation list has, for working out where things are kept. */
    fun storageInfo(context: Context): String {
        val lines = ArrayList<String>()
        fun cols(label: String, uri: Uri) {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    lines.add("$label (${c.count}): " + c.columnNames.joinToString(", "))
                } ?: lines.add("$label: nothing")
            } catch (e: Exception) {
                lines.add("$label: ${e.javaClass.simpleName}")
            }
        }
        cols("Conversations", CONVERSATIONS)
        cols("Threads", Uri.parse("content://mms-sms/conversations"))
        cols("Picture texts", Telephony.Mms.CONTENT_URI)
        return "Gibber Phone build ${codes.gibber.phone.BuildConfig.VERSION_CODE}\n" + lines.joinToString("\n")
    }

    /** The conversation list built from the texts themselves, for phones whose list can't be read. */
    private fun fromTexts(context: Context, unread: Set<Long>): List<Conversation> {
        val seen = LinkedHashMap<Long, Conversation>()
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                null, null, "date DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    if (id in seen) continue
                    val address = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                    seen[id] = Conversation(id, listOf(address), c.getString(2) ?: "", c.getLong(3), id in unread)
                }
            }
        } catch (_: Exception) {
        }
        return seen.values.toList()
    }

    /**
     * The one-to-one conversation with this number, found by searching the texts themselves. This works even when
     * the conversation list writes the number differently, like "+1 480..." against "(480) ...".
     */
    fun threadWith(context: Context, address: String): Long? {
        val key = Numbers.key(address)
        val digits = key.filter { it.isDigit() }
        if (digits.length < 4) return null
        val counts = HashMap<Long, Int>()
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS),
                "${Telephony.Sms.ADDRESS} LIKE ?", arrayOf("%" + digits.takeLast(4)), null,
            )?.use { c ->
                while (c.moveToNext()) {
                    if (Numbers.key(c.getString(1)) == key) counts[c.getLong(0)] = (counts[c.getLong(0)] ?: 0) + 1
                }
            }
        } catch (_: Exception) {
        }
        return counts.maxByOrNull { it.value }?.key
    }

    private fun unreadThreads(context: Context): Set<Long> {
        val out = HashSet<Long>()
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.THREAD_ID),
                "${Telephony.Sms.READ}=0 AND ${Telephony.Sms.TYPE}=${Telephony.Sms.MESSAGE_TYPE_INBOX}", null, null,
            )?.use { c -> while (c.moveToNext()) out.add(c.getLong(0)) }
        } catch (_: Exception) {
        }
        try {
            context.contentResolver.query(
                Telephony.Mms.CONTENT_URI, arrayOf(Telephony.Mms.THREAD_ID),
                "${Telephony.Mms.READ}=0 AND ${Telephony.Mms.MESSAGE_BOX}=${Telephony.Mms.MESSAGE_BOX_INBOX}", null, null,
            )?.use { c -> while (c.moveToNext()) out.add(c.getLong(0)) }
        } catch (_: Exception) {
        }
        return out
    }

    /** The conversation's thread with exactly these people, made if it doesn't exist yet. */
    fun threadFor(context: Context, addresses: Collection<String>): Long =
        Telephony.Threads.getOrCreateThreadId(context, addresses.map { Numbers.clean(it) }.toSet())

    /** A conversation's messages, oldest first (the newest [limit] of them). */
    fun messages(context: Context, threadId: Long, limit: Int = 400): List<Msg> {
        val out = ArrayList<Msg>()
        out.addAll(sms(context, threadId, limit))
        out.addAll(mms(context, threadId, limit))
        out.sortBy { it.date }
        return if (out.size > limit) out.takeLast(limit) else out
    }

    private fun sms(context: Context, threadId: Long, limit: Int): List<Msg> {
        val out = ArrayList<Msg>()
        val projection = arrayOf(
            Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE,
        )
        val selection = "${Telephony.Sms.THREAD_ID}=?"
        val args = arrayOf(threadId.toString())
        val cursor = try {
            context.contentResolver.query(Telephony.Sms.CONTENT_URI, projection, selection, args, "date DESC LIMIT $limit")
        } catch (_: Exception) {
            try {
                context.contentResolver.query(Telephony.Sms.CONTENT_URI, projection, selection, args, "date DESC")
            } catch (_: Exception) {
                null
            }
        }
        cursor?.use { c ->
            while (c.moveToNext() && out.size < limit) {
                val type = c.getInt(4)
                if (type == Telephony.Sms.MESSAGE_TYPE_DRAFT) continue
                val incoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX
                out.add(
                    Msg(
                        id = c.getLong(0),
                        mms = false,
                        threadId = threadId,
                        from = if (incoming) c.getString(1) ?: "" else null,
                        body = c.getString(2) ?: "",
                        date = c.getLong(3),
                        state = when (type) {
                            Telephony.Sms.MESSAGE_TYPE_INBOX -> MsgState.RECEIVED
                            Telephony.Sms.MESSAGE_TYPE_SENT -> MsgState.SENT
                            Telephony.Sms.MESSAGE_TYPE_FAILED -> MsgState.FAILED
                            else -> MsgState.SENDING
                        },
                    ),
                )
            }
        }
        return out
    }

    private class Part(val mid: Long, val id: Long, val type: String, val text: String?, val name: String?)

    private fun mms(context: Context, threadId: Long, limit: Int): List<Msg> {
        data class Head(val id: Long, val date: Long, val box: Int, val mType: Int, val subject: String?)
        val heads = ArrayList<Head>()
        val projection = arrayOf(
            Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_TYPE, Telephony.Mms.SUBJECT,
        )
        val selection = "${Telephony.Mms.THREAD_ID}=?"
        val args = arrayOf(threadId.toString())
        val cursor = try {
            context.contentResolver.query(Telephony.Mms.CONTENT_URI, projection, selection, args, "date DESC LIMIT $limit")
        } catch (_: Exception) {
            try {
                context.contentResolver.query(Telephony.Mms.CONTENT_URI, projection, selection, args, "date DESC")
            } catch (_: Exception) {
                null
            }
        }
        cursor?.use { c ->
            while (c.moveToNext() && heads.size < limit) {
                heads.add(Head(c.getLong(0), c.getLong(1) * 1000L, c.getInt(2), c.getInt(3), c.getString(4)))
            }
        }
        if (heads.isEmpty()) return emptyList()
        val parts = parts(context, heads.map { it.id })
        return heads.map { h ->
            val mine = parts[h.id] ?: emptyList()
            val text = mine.filter { it.type.startsWith("text/plain") }.mapNotNull { it.text }.joinToString("\n").trim()
            val images = mine.filter { it.type.startsWith("image/") }
                .map { Uri.parse("content://mms/part/${it.id}") }
            val others = mine.filter {
                !it.type.startsWith("image/") && !it.type.startsWith("text/") && it.type != "application/smil"
            }.map { it.name ?: it.type }
            val incoming = h.box == Telephony.Mms.MESSAGE_BOX_INBOX
            val notice = h.mType == 130 // a picture message that was never downloaded
            Msg(
                id = h.id,
                mms = true,
                threadId = threadId,
                from = if (incoming) mmsSender(context, h.id) ?: "" else null,
                body = when {
                    notice -> "Picture message that didn't download"
                    text.isNotEmpty() -> text
                    else -> h.subject?.takeIf { it.isNotBlank() } ?: ""
                },
                date = h.date,
                state = when {
                    notice -> MsgState.WAITING
                    incoming -> MsgState.RECEIVED
                    h.box == Telephony.Mms.MESSAGE_BOX_SENT -> MsgState.SENT
                    h.box == Telephony.Mms.MESSAGE_BOX_FAILED -> MsgState.FAILED
                    else -> MsgState.SENDING
                },
                images = images,
                otherFiles = others,
            )
        }
    }

    private fun parts(context: Context, ids: List<Long>): Map<Long, List<Part>> {
        val out = HashMap<Long, MutableList<Part>>()
        val projection = arrayOf("_id", "mid", "ct", "text", "name", "cl")
        fun read(c: android.database.Cursor) {
            while (c.moveToNext()) {
                val p = Part(c.getLong(1), c.getLong(0), (c.getString(2) ?: "").lowercase(), c.getString(3), c.getString(4) ?: c.getString(5))
                out.getOrPut(p.mid) { mutableListOf() }.add(p)
            }
        }
        try {
            ids.chunked(200).forEach { chunk ->
                context.contentResolver.query(PARTS, projection, "mid IN (${chunk.joinToString(",")})", null, null)?.use(::read)
            }
        } catch (_: Exception) {
            // Some phones won't list every part at once; ask message by message instead.
            out.clear()
            for (id in ids) {
                try {
                    context.contentResolver.query(Uri.parse("content://mms/$id/part"), projection, null, null, null)?.use(::read)
                } catch (_: Exception) {
                }
            }
        }
        return out
    }

    private fun mmsSender(context: Context, id: Long): String? = try {
        context.contentResolver.query(
            Uri.parse("content://mms/$id/addr"), arrayOf("address"), "type=137", null, null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (_: Exception) {
        null
    }

    fun markRead(context: Context, threadId: Long) {
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        try {
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI, values, "${Telephony.Sms.THREAD_ID}=? AND ${Telephony.Sms.READ}=0", arrayOf(threadId.toString()),
            )
        } catch (_: Exception) {
        }
        try {
            context.contentResolver.update(
                Telephony.Mms.CONTENT_URI, values, "${Telephony.Mms.THREAD_ID}=? AND ${Telephony.Mms.READ}=0", arrayOf(threadId.toString()),
            )
        } catch (_: Exception) {
        }
    }

    fun deleteMessage(context: Context, msg: Msg) {
        val uri = if (msg.mms) Uri.parse("content://mms/${msg.id}") else Uri.parse("content://sms/${msg.id}")
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (_: Exception) {
        }
    }

    fun deleteConversation(context: Context, threadId: Long) {
        try {
            context.contentResolver.delete(Uri.parse("content://mms-sms/conversations/$threadId"), null, null)
        } catch (_: Exception) {
        }
    }

    /** The newest unread texts in a conversation, for its notification. */
    fun unreadIn(context: Context, threadId: Long): List<Msg> =
        messages(context, threadId, 30).filter { it.state == MsgState.RECEIVED }.takeLast(8)
}
