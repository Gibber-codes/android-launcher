package codes.gibber.phone.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.provider.VoicemailContract

data class Voicemail(
    val id: Long,
    val number: String,
    val date: Long,
    val duration: Long,
    val read: Boolean,
    /** Whether the recording is on the phone yet; visual voicemail downloads it when you play it. */
    val hasAudio: Boolean,
    val transcript: String?,
    val source: String?,
) {
    val uri: Uri get() = ContentUris.withAppendedId(VoicemailContract.Voicemails.CONTENT_URI, id)
}

/**
 * Voicemails from Android's shared voicemail list, which the default phone app may read. Whether anything
 * is in it depends on the carrier's visual voicemail: some fill it, some keep recordings in their own app.
 */
object Voicemails {
    /** What's in the shared list, newest first, or null when Android won't let People read it. */
    fun list(context: Context): List<Voicemail>? = try {
        val out = ArrayList<Voicemail>()
        context.contentResolver.query(
            VoicemailContract.Voicemails.CONTENT_URI,
            arrayOf(
                VoicemailContract.Voicemails._ID,
                VoicemailContract.Voicemails.NUMBER,
                VoicemailContract.Voicemails.DATE,
                VoicemailContract.Voicemails.DURATION,
                VoicemailContract.Voicemails.IS_READ,
                VoicemailContract.Voicemails.HAS_CONTENT,
                VoicemailContract.Voicemails.TRANSCRIPTION,
                VoicemailContract.Voicemails.SOURCE_PACKAGE,
                VoicemailContract.Voicemails.DELETED,
            ),
            null, null, VoicemailContract.Voicemails.DATE + " DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(8) == 1) continue
                out.add(
                    Voicemail(
                        id = c.getLong(0),
                        number = c.getString(1) ?: "",
                        date = c.getLong(2),
                        duration = c.getLong(3),
                        read = c.getInt(4) == 1,
                        hasAudio = c.getInt(5) == 1,
                        transcript = c.getString(6)?.takeIf { it.isNotBlank() },
                        source = c.getString(7),
                    ),
                )
            }
        }
        out
    } catch (_: Exception) {
        null
    }

    /** Voicemails the call history knows about, for when the shared list can't be read: number and time only. */
    fun fromCallLog(context: Context): List<CallEntry> =
        Calls.recent(context, 1000).filter { it.type == CallLog.Calls.VOICEMAIL_TYPE }

    /** Asks the app that owns this voicemail to download its recording. */
    fun fetch(context: Context, v: Voicemail) {
        try {
            val intent = Intent(VoicemailContract.ACTION_FETCH_VOICEMAIL, v.uri)
            v.source?.let { intent.setPackage(it) }
            context.sendBroadcast(intent)
        } catch (_: Exception) {
        }
    }

    fun markRead(context: Context, v: Voicemail) {
        if (v.read) return
        try {
            val values = ContentValues().apply { put(VoicemailContract.Voicemails.IS_READ, 1) }
            context.contentResolver.update(v.uri, values, null, null)
        } catch (_: Exception) {
        }
    }

    // Visual voicemail apps a Samsung on AT&T may have, so People can offer to open the one that holds your recordings.
    private val APPS = listOf("com.samsung.vvm", "com.samsung.vvm.se", "com.att.mobile.android.vvm")

    /** A way to open your carrier's voicemail app, if one is on the phone. */
    fun appIntent(context: Context): Intent? = APPS.firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
}
