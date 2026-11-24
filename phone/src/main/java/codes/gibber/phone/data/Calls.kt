package codes.gibber.phone.data

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.telecom.TelecomManager

data class CallEntry(
    val id: Long,
    val number: String,
    val type: Int,
    val date: Long,
    val duration: Long,
    val isNew: Boolean,
) {
    val missed get() = type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE
    val outgoing get() = type == CallLog.Calls.OUTGOING_TYPE

    fun describe(): String = when (type) {
        CallLog.Calls.INCOMING_TYPE -> "Incoming call"
        CallLog.Calls.OUTGOING_TYPE -> "Outgoing call"
        CallLog.Calls.MISSED_TYPE -> "Missed call"
        CallLog.Calls.VOICEMAIL_TYPE -> "Voicemail"
        CallLog.Calls.REJECTED_TYPE -> "Declined call"
        CallLog.Calls.BLOCKED_TYPE -> "Blocked call"
        else -> "Call"
    }
}

object Calls {
    /** Your call history, newest first. */
    fun recent(context: Context, limit: Int = 600): List<CallEntry> {
        val out = ArrayList<CallEntry>()
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI.buildUpon().appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, limit.toString()).build(),
                arrayOf(
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.NEW,
                ),
                null, null, CallLog.Calls.DATE + " DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    out.add(
                        CallEntry(
                            id = c.getLong(0),
                            number = c.getString(1) ?: "",
                            type = c.getInt(2),
                            date = c.getLong(3),
                            duration = c.getLong(4),
                            isNew = c.getInt(5) == 1,
                        ),
                    )
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun delete(context: Context, id: Long) {
        try {
            context.contentResolver.delete(ContentUris.withAppendedId(CallLog.Calls.CONTENT_URI, id), null, null)
        } catch (_: Exception) {
        }
    }

    /** Clears the "missed call" mark once you've seen them, and Android's own missed-call notice. */
    @SuppressLint("MissingPermission")
    fun markSeen(context: Context) {
        try {
            val values = android.content.ContentValues().apply { put(CallLog.Calls.NEW, 0) }
            context.contentResolver.update(
                CallLog.Calls.CONTENT_URI, values,
                CallLog.Calls.NEW + "=1 AND " + CallLog.Calls.TYPE + "=" + CallLog.Calls.MISSED_TYPE, null,
            )
        } catch (_: Exception) {
        }
        try {
            context.getSystemService(TelecomManager::class.java)?.cancelMissedCallsNotification()
        } catch (_: Exception) {
        }
    }

    /** Starts a call. Returns false when Android wouldn't place it (no permission yet). */
    @SuppressLint("MissingPermission")
    fun place(context: Context, number: String): Boolean = try {
        val tm = context.getSystemService(TelecomManager::class.java)
        tm.placeCall(Uri.fromParts("tel", Numbers.clean(number), null), Bundle())
        true
    } catch (e: Exception) {
        false
    }

    @SuppressLint("MissingPermission")
    fun voicemail(context: Context): Boolean = try {
        val tm = context.getSystemService(TelecomManager::class.java)
        tm.placeCall(Uri.fromParts("voicemail", "", null), Bundle())
        true
    } catch (e: Exception) {
        false
    }
}
