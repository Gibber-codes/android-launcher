package codes.gibber.phone.data

import android.content.Context

/** One row on the Recent list: a person or group, with their latest text or call. */
data class RecentItem(
    val key: String,
    val addresses: List<String>,
    val threadId: Long?,
    val name: String,
    val photo: String?,
    val contactId: Long?,
    val line: String,
    val date: Long,
    val unread: Boolean,
    val missedCalls: Int,
    val lastCall: CallEntry?,
) {
    val isGroup get() = addresses.size > 1
}

object Recent {
    fun load(context: Context, version: Int): List<RecentItem> {
        val conversations = Texts.conversations(context)
        val calls = Calls.recent(context)
        val byKey = LinkedHashMap<String, RecentItem>()

        for (c in conversations) {
            if (c.isGroup) {
                val names = c.addresses.map { a -> Contacts.lookup(context, a, version)?.name?.substringBefore(' ') ?: Numbers.pretty(a) }
                byKey["g:${c.id}"] = RecentItem(
                    key = "g:${c.id}", addresses = c.addresses, threadId = c.id,
                    name = names.joinToString(", "), photo = null, contactId = null,
                    line = c.snippet.ifBlank { "Picture" }, date = c.date, unread = c.unread, missedCalls = 0, lastCall = null,
                )
            } else {
                val address = c.addresses[0]
                val key = Numbers.key(address)
                if (byKey.containsKey(key)) continue
                val match = Contacts.lookup(context, address, version)
                byKey[key] = RecentItem(
                    key = key, addresses = c.addresses, threadId = c.id,
                    name = match?.name ?: Numbers.pretty(address), photo = match?.photo, contactId = match?.contactId,
                    line = c.snippet.ifBlank { "Picture" }, date = c.date, unread = c.unread, missedCalls = 0, lastCall = null,
                )
            }
        }

        for (call in calls) {
            if (call.number.isBlank()) continue
            val key = Numbers.key(call.number)
            val item = byKey[key]
            val missedNew = if (call.missed && call.isNew) 1 else 0
            if (item == null) {
                val match = Contacts.lookup(context, call.number, version)
                byKey[key] = RecentItem(
                    key = key, addresses = listOf(call.number), threadId = null,
                    name = match?.name ?: Numbers.pretty(call.number), photo = match?.photo, contactId = match?.contactId,
                    line = call.describe(), date = call.date, unread = false, missedCalls = missedNew, lastCall = call,
                )
            } else {
                val newer = call.date > item.date
                byKey[key] = item.copy(
                    line = if (newer) call.describe() else item.line,
                    date = maxOf(item.date, call.date),
                    missedCalls = item.missedCalls + missedNew,
                    lastCall = item.lastCall ?: call,
                )
            }
        }
        return byKey.values.sortedByDescending { it.date }
    }
}

/** One thing in a person's history: a text or a call. */
sealed class Moment {
    abstract val date: Long
    data class Text(val msg: Msg) : Moment() {
        override val date get() = msg.date
    }
    data class Phone(val call: CallEntry) : Moment() {
        override val date get() = call.date
    }
}

object History {
    /** Texts in the conversation and calls with any of these numbers, oldest first. */
    fun load(context: Context, threadId: Long?, numbers: List<String>): List<Moment> {
        val out = ArrayList<Moment>()
        if (threadId != null) Texts.messages(context, threadId).forEach { out.add(Moment.Text(it)) }
        if (numbers.isNotEmpty()) {
            val keys = numbers.map { Numbers.key(it) }.toSet()
            Calls.recent(context, 2000).filter { Numbers.key(it.number) in keys }.take(300).forEach { out.add(Moment.Phone(it)) }
        }
        out.sortBy { it.date }
        return out
    }

    /** The conversation with exactly these people, if there is one yet. */
    fun findThread(context: Context, addresses: List<String>): Long? {
        val keys = addresses.map { Numbers.key(it) }.toSet()
        return Texts.conversations(context).firstOrNull { c -> c.addresses.map { Numbers.key(it) }.toSet() == keys }?.id
    }
}
