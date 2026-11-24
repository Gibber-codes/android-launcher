package codes.gibber.phone.sms

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * Picture and group texts (MMS) travel as small binary files ("PDUs", OMA MMS encapsulation over
 * WAP's WSP encoding). Android sends and fetches them for the default texting app, but reading and
 * writing the files is the app's job. This is a small reader and writer for the kinds we need:
 * the "you have a message" notice, the downloaded message, sending, and the receipt we send back.
 * It has no Android code in it, so it can be tested on its own.
 */
object Pdu {
    const val SEND_REQ = 0x80
    const val SEND_CONF = 0x81
    const val NOTIFICATION_IND = 0x82
    const val NOTIFYRESP_IND = 0x83
    const val RETRIEVE_CONF = 0x84
    const val ACKNOWLEDGE_IND = 0x85
    const val DELIVERY_IND = 0x86

    const val RESPONSE_OK = 0x80
    const val MMS_VERSION_1_2 = 0x92
    const val UTF8 = 106

    data class Part(
        val contentType: String,
        val data: ByteArray,
        val name: String? = null,
        val contentId: String? = null,
        val contentLocation: String? = null,
        val charset: Int? = null,
    ) {
        fun text(): String = String(data, charsetFor(charset))
    }

    data class Message(
        val type: Int,
        val transactionId: String? = null,
        val messageId: String? = null,
        val contentLocation: String? = null,
        val from: String? = null,
        val to: List<String> = emptyList(),
        val cc: List<String> = emptyList(),
        val subject: String? = null,
        val date: Long? = null,
        val size: Long? = null,
        val responseStatus: Int? = null,
        val contentType: String? = null,
        val parts: List<Part> = emptyList(),
    )

    // ---- reading ----

    fun parse(bytes: ByteArray): Message = Reader(bytes).message()

    private class Reader(val b: ByteArray) {
        var pos = 0

        fun more() = pos < b.size
        fun peek(): Int = b[pos].toInt() and 0xFF
        fun u8(): Int = b[pos++].toInt() and 0xFF

        fun uintvar(): Long {
            var value = 0L
            var count = 0
            while (true) {
                val x = u8()
                value = (value shl 7) or (x and 0x7F).toLong()
                if (x and 0x80 == 0 || ++count >= 5) return value
            }
        }

        fun valueLength(): Int {
            val x = u8()
            return when {
                x <= 30 -> x
                x == 31 -> uintvar().toInt()
                else -> throw IllegalStateException("bad length $x at ${pos - 1}")
            }
        }

        fun textBytes(): ByteArray {
            if (more() && (peek() == 127 || peek() == 0x22)) pos++
            val start = pos
            while (more() && b[pos].toInt() != 0) pos++
            val out = b.copyOfRange(start, pos)
            if (more()) pos++ // the closing zero
            return out
        }

        fun text(): String = String(textBytes(), Charsets.UTF_8)

        fun longInt(): Long {
            val len = u8()
            var v = 0L
            repeat(len) { v = (v shl 8) or u8().toLong() }
            return v
        }

        fun integer(): Long = if (peek() and 0x80 != 0) (u8() and 0x7F).toLong() else longInt()

        fun encodedString(): String {
            val x = peek()
            if (x > 31) return text()
            val len = valueLength()
            val end = pos + len
            var charset = UTF8
            if (pos < end) {
                charset = if (peek() > 31 && peek() < 128) {
                    text(); UTF8
                } else integer().toInt()
            }
            val start = pos
            var stop = start
            while (stop < end && b[stop].toInt() != 0) stop++
            var from = start
            if (from < stop && (b[from].toInt() and 0xFF) == 127) from++
            val s = String(b.copyOfRange(from, stop), charsetFor(charset))
            pos = end
            return s
        }

        /** Steps over any value without reading it. */
        fun skip() {
            val x = peek()
            when {
                x <= 30 -> pos += 1 + x
                x == 31 -> {
                    pos++
                    val len = uintvar().toInt()
                    pos += len
                }
                x < 128 -> textBytes()
                else -> pos++
            }
        }

        class Type(val media: String, val params: Map<String, String>)

        fun wellKnownOrText(): String = when {
            peek() and 0x80 != 0 -> CONTENT_TYPES.getOrElse(u8() and 0x7F) { "application/octet-stream" }
            peek() >= 32 -> text()
            else -> CONTENT_TYPES.getOrElse(longInt().toInt()) { "application/octet-stream" }
        }

        fun contentType(): Type {
            val x = peek()
            if (x and 0x80 != 0 || x >= 32) return Type(wellKnownOrText(), emptyMap())
            val len = valueLength()
            val end = pos + len
            val media = wellKnownOrText()
            val params = HashMap<String, String>()
            while (pos < end) {
                if (peek() and 0x80 != 0) {
                    when (u8() and 0x7F) {
                        0x01 -> params["charset"] = if (peek() in 32..127) text() else integer().toString()
                        0x03 -> params["type"] = integer().toString()
                        0x09 -> params["type"] = wellKnownOrText()
                        0x0A -> params["start"] = text()
                        0x05, 0x17 -> params["name"] = text()
                        0x06, 0x18 -> params["filename"] = text()
                        else -> skip()
                    }
                } else {
                    val name = text().lowercase()
                    if (pos < end) {
                        if (peek() in 32..127) params[name] = text() else skip()
                    }
                }
            }
            pos = end
            return Type(media, params)
        }

        fun message(): Message {
            var type = 0
            var txId: String? = null
            var msgId: String? = null
            var location: String? = null
            var from: String? = null
            val to = ArrayList<String>()
            val cc = ArrayList<String>()
            var subject: String? = null
            var date: Long? = null
            var size: Long? = null
            var status: Int? = null
            var ct: String? = null
            var parts: List<Part> = emptyList()
            while (more()) {
                val h = u8()
                if (h and 0x80 == 0) {
                    // An application header: a name and a text value.
                    pos--
                    textBytes()
                    if (more()) skip()
                    continue
                }
                when (h and 0x7F) {
                    0x0C -> type = u8()
                    0x18 -> txId = text()
                    0x0B -> msgId = text()
                    0x03 -> location = text()
                    0x05 -> date = longInt()
                    0x0E -> size = longInt()
                    0x09 -> {
                        val len = valueLength()
                        val end = pos + len
                        if (pos < end && u8() == 0x80 && pos < end) from = encodedString()
                        pos = end
                    }
                    0x17 -> to.add(encodedString())
                    0x02 -> cc.add(encodedString())
                    0x01 -> encodedString() // bcc
                    0x16 -> subject = encodedString()
                    0x12 -> status = u8()
                    0x04 -> {
                        val t = contentType()
                        ct = t.media
                        if (more() && (type == RETRIEVE_CONF || type == SEND_REQ)) parts = body()
                        pos = b.size
                    }
                    else -> skip()
                }
            }
            return Message(type, txId, msgId, location, from, to, cc, subject, date, size, status, ct, parts)
        }

        fun body(): List<Part> {
            val count = uintvar().toInt()
            val out = ArrayList<Part>()
            repeat(count) {
                if (!more()) return out
                val headLen = uintvar().toInt()
                val dataLen = uintvar().toInt()
                val headStart = pos
                val headEnd = headStart + headLen
                val t = contentType()
                var cid: String? = null
                var location: String? = null
                var name = t.params["name"] ?: t.params["filename"]
                while (pos < headEnd) {
                    if (peek() and 0x80 != 0) {
                        when (u8() and 0x7F) {
                            0x0E -> location = text()
                            0x40 -> cid = text().trim('<', '>')
                            0x2E, 0x45 -> {
                                val len = valueLength()
                                val end = pos + len
                                if (pos < end) {
                                    if (peek() and 0x80 != 0) pos++ else textBytes()
                                }
                                while (pos < end) {
                                    if (peek() and 0x80 != 0) {
                                        when (u8() and 0x7F) {
                                            0x05, 0x06, 0x17, 0x18 -> name = name ?: text()
                                            else -> skip()
                                        }
                                    } else {
                                        textBytes()
                                        if (pos < end) skip()
                                    }
                                }
                                pos = end
                            }
                            else -> skip()
                        }
                    } else {
                        textBytes()
                        if (pos < headEnd) skip()
                    }
                }
                pos = headEnd
                val end = minOf(pos + dataLen, b.size)
                val data = b.copyOfRange(pos, end)
                pos = end
                out.add(
                    Part(
                        contentType = t.media.lowercase(),
                        data = data,
                        name = name ?: location,
                        contentId = cid,
                        contentLocation = location,
                        charset = t.params["charset"]?.toIntOrNull(),
                    ),
                )
            }
            return out
        }
    }

    // ---- writing ----

    private class Writer {
        val out = ByteArrayOutputStream()
        fun u8(x: Int) = out.write(x and 0xFF)
        fun bytes(x: ByteArray) = out.write(x, 0, x.size)
        fun uintvar(value: Long) {
            var v = value
            val tmp = ArrayList<Int>()
            tmp.add((v and 0x7F).toInt())
            v = v shr 7
            while (v > 0) {
                tmp.add(((v and 0x7F) or 0x80).toInt())
                v = v shr 7
            }
            for (i in tmp.indices.reversed()) u8(tmp[i])
        }
        fun valueLength(n: Int) {
            if (n < 31) u8(n) else {
                u8(31); uintvar(n.toLong())
            }
        }
        fun text(s: String) {
            val raw = s.toByteArray(Charsets.UTF_8)
            if (raw.isNotEmpty() && (raw[0].toInt() and 0xFF) >= 128) u8(127)
            bytes(raw)
            u8(0)
        }
        fun quoted(s: String) {
            u8(0x22); bytes(s.toByteArray(Charsets.UTF_8)); u8(0)
        }
        fun longInt(value: Long) {
            var v = value
            val tmp = ArrayList<Int>()
            do {
                tmp.add((v and 0xFF).toInt()); v = v shr 8
            } while (v > 0)
            u8(tmp.size)
            for (i in tmp.indices.reversed()) u8(tmp[i])
        }
        /** Text with a character set, for subjects that may hold emoji or accents. */
        fun encodedUtf8(s: String) {
            val raw = s.toByteArray(Charsets.UTF_8)
            val inner = Writer()
            inner.u8(0x80 or UTF8)
            if (raw.isNotEmpty() && (raw[0].toInt() and 0xFF) >= 128) inner.u8(127)
            inner.bytes(raw)
            inner.u8(0)
            val body = inner.out.toByteArray()
            valueLength(body.size)
            bytes(body)
        }
        fun toBytes(): ByteArray = out.toByteArray()
    }

    /** Address in the form the carrier expects: "+19705550100/TYPE=PLMN", or an email as is. */
    fun address(raw: String): String {
        val s = raw.trim()
        if (s.contains('@') || s.contains("/TYPE=", ignoreCase = true)) return s
        val keep = s.filter { it.isDigit() || it == '+' }
        return "$keep/TYPE=PLMN"
    }

    /** A message to send: one or more people, optional subject, and its parts (text, pictures). */
    fun sendRequest(to: List<String>, subject: String?, parts: List<Part>, transactionId: String, dateSeconds: Long): ByteArray {
        val w = Writer()
        w.u8(0x8C); w.u8(SEND_REQ)
        w.u8(0x98); w.text(transactionId)
        w.u8(0x8D); w.u8(MMS_VERSION_1_2)
        w.u8(0x85); w.longInt(dateSeconds)
        w.u8(0x89); w.valueLength(1); w.u8(0x81) // From: filled in by the carrier
        for (addr in to) {
            w.u8(0x97); w.text(address(addr))
        }
        if (!subject.isNullOrBlank()) {
            w.u8(0x96); w.encodedUtf8(subject)
        }
        w.u8(0x8A); w.u8(0x80) // class: personal
        w.u8(0x8F); w.u8(0x81) // priority: normal
        w.u8(0x86); w.u8(0x81) // no delivery report
        w.u8(0x90); w.u8(0x81) // no read report

        val all = ArrayList<Part>()
        all.add(smilFor(parts))
        all.addAll(parts)

        // Content-Type: multipart/related; type="application/smil"; start="<smil>"
        val ct = Writer()
        ct.u8(0x80 or 0x33)
        ct.u8(0x80 or 0x09); ct.text("application/smil")
        ct.u8(0x80 or 0x0A); ct.text("<smil>")
        val ctBytes = ct.toBytes()
        w.u8(0x84); w.valueLength(ctBytes.size); w.bytes(ctBytes)

        w.uintvar(all.size.toLong())
        for (p in all) {
            val head = Writer()
            if (p.contentType.startsWith("text/") || p.contentType == "application/smil") {
                val t = Writer()
                t.text(p.contentType)
                t.u8(0x81); t.u8(0x80 or UTF8)
                val tb = t.toBytes()
                head.valueLength(tb.size); head.bytes(tb)
            } else {
                head.text(p.contentType)
            }
            val cid = p.contentId ?: p.contentLocation ?: "part"
            head.u8(0xC0); head.quoted("<$cid>")
            p.contentLocation?.let { head.u8(0x8E); head.text(it) }
            val hb = head.toBytes()
            w.uintvar(hb.size.toLong())
            w.uintvar(p.data.size.toLong())
            w.bytes(hb)
            w.bytes(p.data)
        }
        return w.toBytes()
    }

    private fun smilFor(parts: List<Part>): Part {
        val body = StringBuilder()
        for (p in parts) {
            val src = p.contentLocation ?: continue
            when {
                p.contentType.startsWith("image/") ->
                    body.append("<par dur=\"5000ms\"><img src=\"$src\" region=\"Image\"/></par>")
                p.contentType.startsWith("text/") ->
                    body.append("<par dur=\"5000ms\"><text src=\"$src\" region=\"Text\"/></par>")
                p.contentType.startsWith("video/") ->
                    body.append("<par dur=\"5000ms\"><video src=\"$src\" region=\"Image\"/></par>")
                p.contentType.startsWith("audio/") ->
                    body.append("<par dur=\"5000ms\"><audio src=\"$src\"/></par>")
            }
        }
        val smil = "<smil><head><layout><root-layout/>" +
            "<region id=\"Image\" fit=\"meet\" top=\"0\" left=\"0\" height=\"80%\" width=\"100%\"/>" +
            "<region id=\"Text\" top=\"80%\" left=\"0\" height=\"20%\" width=\"100%\"/>" +
            "</layout></head><body>$body</body></smil>"
        return Part("application/smil", smil.toByteArray(Charsets.UTF_8), contentId = "smil", contentLocation = "smil.xml", charset = UTF8)
    }

    /** The "got it" we send after downloading a message, so the carrier doesn't send it again. */
    fun acknowledge(transactionId: String): ByteArray {
        val w = Writer()
        w.u8(0x8C); w.u8(ACKNOWLEDGE_IND)
        w.u8(0x98); w.text(transactionId)
        w.u8(0x8D); w.u8(MMS_VERSION_1_2)
        w.u8(0x91); w.u8(0x81) // report allowed: no
        return w.toBytes()
    }

    fun charsetFor(mib: Int?): Charset = try {
        when (mib) {
            null, 0, UTF8 -> Charsets.UTF_8
            3 -> Charsets.US_ASCII
            4 -> Charsets.ISO_8859_1
            1000 -> Charsets.UTF_16BE
            1013 -> Charsets.UTF_16BE
            1014 -> Charsets.UTF_16LE
            1015 -> Charsets.UTF_16
            17 -> Charset.forName("Shift_JIS")
            2026 -> Charset.forName("Big5")
            36 -> Charset.forName("EUC-KR")
            else -> Charsets.UTF_8
        }
    } catch (_: Exception) {
        Charsets.UTF_8
    }

    /** WSP's numbered content types (the ones that can show up in a text). */
    private val CONTENT_TYPES: Map<Int, String> = mapOf(
        0x00 to "*/*",
        0x01 to "text/*",
        0x02 to "text/html",
        0x03 to "text/plain",
        0x06 to "text/x-vcalendar",
        0x07 to "text/x-vcard",
        0x08 to "text/vnd.wap.wml",
        0x0B to "multipart/*",
        0x0C to "multipart/mixed",
        0x0F to "multipart/alternative",
        0x10 to "application/*",
        0x1C to "image/*",
        0x1D to "image/gif",
        0x1E to "image/jpeg",
        0x1F to "image/tiff",
        0x20 to "image/png",
        0x21 to "image/vnd.wap.wbmp",
        0x22 to "application/vnd.wap.multipart.*",
        0x23 to "application/vnd.wap.multipart.mixed",
        0x26 to "application/vnd.wap.multipart.alternative",
        0x27 to "application/xml",
        0x28 to "text/xml",
        0x33 to "application/vnd.wap.multipart.related",
        0x3B to "application/xhtml+xml",
        0x3D to "text/css",
        0x3E to "application/vnd.wap.mms-message",
    )
}
