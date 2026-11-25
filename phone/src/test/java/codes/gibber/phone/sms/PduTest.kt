package codes.gibber.phone.sms

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class PduTest {
    @Test
    fun sendRequestReadsBack() {
        val jpeg = ByteArray(300) { (it * 7).toByte() }
        val parts = listOf(
            Pdu.Part("image/jpeg", jpeg, name = "image0.jpg", contentId = "image0", contentLocation = "image0.jpg"),
            Pdu.Part("text/plain", "Hey 👋 group".toByteArray(), name = "text0.txt", contentId = "text0", contentLocation = "text0.txt", charset = Pdu.UTF8),
        )
        val bytes = Pdu.sendRequest(listOf("+19705550100", "9705550101"), "Hi", parts, "T123", 1_700_000_000)
        val m = Pdu.parse(bytes)
        assertEquals(Pdu.SEND_REQ, m.type)
        assertEquals("T123", m.transactionId)
        assertEquals(listOf("+19705550100/TYPE=PLMN", "9705550101/TYPE=PLMN"), m.to)
        assertEquals("Hi", m.subject)
        assertEquals(1_700_000_000L, m.date)
        assertEquals("application/vnd.wap.multipart.related", m.contentType)
        assertEquals(3, m.parts.size)
        assertEquals("application/smil", m.parts[0].contentType)
        assertEquals("image/jpeg", m.parts[1].contentType)
        assertEquals("image0", m.parts[1].contentId)
        assertEquals("image0.jpg", m.parts[1].contentLocation)
        assertArrayEquals(jpeg, m.parts[1].data)
        assertEquals("text/plain", m.parts[2].contentType)
        assertEquals(Pdu.UTF8, m.parts[2].charset)
        assertEquals("Hey 👋 group", m.parts[2].text())
    }

    /** A carrier-style notice, built byte by byte the way the MMS spec lays it out. */
    @Test
    fun notificationNotice() {
        val b = ByteArrayOutputStream()
        fun text(s: String) { b.write(s.toByteArray()); b.write(0) }
        b.write(0x8C); b.write(0x82)
        b.write(0x98); text("abc123")
        b.write(0x8D); b.write(0x92)
        // From: value-length, address-present, "+19705550199/TYPE=PLMN"
        val from = "+19705550199/TYPE=PLMN".toByteArray()
        b.write(0x89); b.write(from.size + 2); b.write(0x80); b.write(from); b.write(0)
        b.write(0x8A); b.write(0x80)
        b.write(0x8E); b.write(2); b.write(0x30); b.write(0x39) // size 12345
        b.write(0x88); b.write(5); b.write(0x81); b.write(3); b.write(0x09); b.write(0x3A); b.write(0x80) // expiry
        b.write(0x83); text("http://mmsc.example/abc123")
        val m = Pdu.parse(b.toByteArray())
        assertEquals(Pdu.NOTIFICATION_IND, m.type)
        assertEquals("abc123", m.transactionId)
        assertEquals("+19705550199/TYPE=PLMN", m.from)
        assertEquals(12345L, m.size)
        assertEquals("http://mmsc.example/abc123", m.contentLocation)
    }

    /** A downloaded group message with a well-known content type and a picture. */
    @Test
    fun retrieveConf() {
        val b = ByteArrayOutputStream()
        fun text(s: String) { b.write(s.toByteArray()); b.write(0) }
        fun uintvar(v: Int) {
            if (v >= 128) b.write(0x80 or (v shr 7))
            b.write(v and 0x7F)
        }
        b.write(0x8C); b.write(0x84)
        b.write(0x98); text("tx9")
        b.write(0x8D); b.write(0x92)
        b.write(0x8B); text("msg-1")
        b.write(0x85); b.write(4); b.write(0x65); b.write(0x53); b.write(0xF1); b.write(0x00)
        val from = "+19705550199/TYPE=PLMN".toByteArray()
        b.write(0x89); b.write(from.size + 2); b.write(0x80); b.write(from); b.write(0)
        b.write(0x97); text("+19705550100/TYPE=PLMN")
        b.write(0x97); text("+19705550101/TYPE=PLMN")
        // Subject with charset: value-length, utf-8, text
        val subj = "Plans".toByteArray()
        b.write(0x96); b.write(subj.size + 2); b.write(0xEA); b.write(subj); b.write(0)
        b.write(0x84); b.write(0xA3) // multipart/mixed, short form
        uintvar(2)
        // part 1: text/plain (0x83) with charset in general form
        val t = "Pic from the lake".toByteArray()
        val h1 = byteArrayOf(0x03, 0x83.toByte(), 0x81.toByte(), 0xEA.toByte(), 0x8E.toByte()) + "t.txt".toByteArray() + byteArrayOf(0)
        uintvar(h1.size); uintvar(t.size); b.write(h1); b.write(t)
        // part 2: image/jpeg (0x9E) with a Content-ID
        val img = ByteArray(200) { 1 }
        val h2 = byteArrayOf(0x9E.toByte(), 0xC0.toByte(), 0x22) + "<img1>".toByteArray() + byteArrayOf(0)
        uintvar(h2.size); uintvar(img.size); b.write(h2); b.write(img)

        val m = Pdu.parse(b.toByteArray())
        assertEquals(Pdu.RETRIEVE_CONF, m.type)
        assertEquals("msg-1", m.messageId)
        assertEquals("+19705550199/TYPE=PLMN", m.from)
        assertEquals(2, m.to.size)
        assertEquals("Plans", m.subject)
        assertEquals("application/vnd.wap.multipart.mixed", m.contentType)
        assertEquals(2, m.parts.size)
        assertEquals("text/plain", m.parts[0].contentType)
        assertEquals("Pic from the lake", m.parts[0].text())
        assertEquals("t.txt", m.parts[0].contentLocation)
        assertEquals("image/jpeg", m.parts[1].contentType)
        assertEquals("img1", m.parts[1].contentId)
        assertEquals(200, m.parts[1].data.size)
    }

    @Test
    fun acknowledgeReadsBack() {
        val m = Pdu.parse(Pdu.acknowledge("tx9"))
        assertEquals(Pdu.ACKNOWLEDGE_IND, m.type)
        assertEquals("tx9", m.transactionId)
    }
}
