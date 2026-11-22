package codes.gibber.launcher

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.net.HttpURLConnection
import java.net.URL

/**
 * The hub opens games at localhost, which on the phone is the phone itself. This fetches those
 * pages from the hub's PC instead (same port and path), so a game that listens on your Wi-Fi
 * shows inside the hub. Only plain page loads (GET) are passed on.
 */
object LocalhostProxy {
    private val LOCAL = setOf("localhost", "127.0.0.1")
    private val SKIP_REQUEST = setOf("host", "accept-encoding", "connection")
    private val SKIP_RESPONSE = setOf("content-encoding", "content-length", "transfer-encoding", "connection")

    fun intercept(request: WebResourceRequest, hubUrl: String): WebResourceResponse? {
        val url = request.url
        if (url.host !in LOCAL || request.method != "GET") return null
        val pcHost = Uri.parse(hubUrl).host ?: return null
        if (pcHost in LOCAL) return null
        val port = if (url.port > 0) url.port else 80
        val target = url.buildUpon().encodedAuthority("$pcHost:$port").build().toString()

        return try {
            val conn = URL(target).openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            request.requestHeaders.forEach { (k, v) ->
                if (k.lowercase() !in SKIP_REQUEST) conn.setRequestProperty(k, v)
            }
            val code = conn.responseCode
            if (code in 300..399) return null
            val type = conn.contentType ?: "application/octet-stream"
            val mime = type.substringBefore(';').trim()
            val charset = type.substringAfter("charset=", "").trim().ifEmpty { null }
            val headers = conn.headerFields
                .filterKeys { it != null && it.lowercase() !in SKIP_RESPONSE }
                .mapKeys { it.key!! }
                .mapValues { it.value.joinToString(", ") }
            val body = (if (code >= 400) conn.errorStream else conn.inputStream)
                ?: "".byteInputStream()
            val reason = conn.responseMessage?.ifBlank { null } ?: "OK"
            WebResourceResponse(mime, charset, code, reason, headers, body)
        } catch (e: Exception) {
            val text = "Couldn't reach $pcHost:$port. Start it on your PC and let it listen on your Wi-Fi (--host), " +
                "and allow port $port through Windows Firewall."
            WebResourceResponse("text/plain", "utf-8", 502, "Bad Gateway", emptyMap(), text.byteInputStream())
        }
    }
}
