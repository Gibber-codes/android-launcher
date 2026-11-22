package codes.gibber.launcher

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/** Gibber Hub, built into the launcher as its second page. */
@Composable
fun HubScreen(prefs: Prefs, isShown: Boolean, onGoHome: () -> Unit) {
    val context = LocalContext.current
    var url by remember { mutableStateOf(normalizeHubUrl(prefs.hubUrl)) }
    var editing by remember { mutableStateOf(url.isBlank()) }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xCC101014))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("‹  Home", color = Color.White, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onGoHome))
            Text("Open in Chrome", color = Color.White, fontSize = 16.sp, modifier = Modifier.clickable {
                if (url.isNotBlank()) {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (e: Exception) {
                    }
                }
            })
            Text("Address", color = Color.White, fontSize = 16.sp, modifier = Modifier.clickable { editing = true })
        }

        if (editing || url.isBlank()) {
            AddressForm(
                current = url,
                onSave = {
                    prefs.hubUrl = normalizeHubUrl(it)
                    url = prefs.hubUrl
                    editing = false
                },
            )
        } else {
            key(url) { HubWebView(url = url, isShown = isShown, onGoHome = onGoHome) }
        }
    }
}

@Composable
private fun AddressForm(current: String, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current.ifBlank { "http://" }) }
    Column(Modifier.padding(24.dp)) {
        Text("Where does your hub run?", color = Color.White, fontSize = 20.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Type the hub's address as your phone reaches it, like http://192.168.1.20:5175 for your PC on the same Wi-Fi.",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onSave(text) }) { Text("Save") }
    }
}

/** Adds http:// when the address has no scheme, since a WebView loads nothing without one. */
fun normalizeHubUrl(raw: String): String {
    val t = raw.trim()
    if (t.isEmpty() || t == "http://" || t == "https://") return ""
    return if (t.startsWith("http://") || t.startsWith("https://")) t else "http://$t"
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HubWebView(url: String, isShown: Boolean, onGoHome: () -> Unit) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember(url) { mutableIntStateOf(0) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var pageError by remember(url) { mutableStateOf<String?>(null) }
    var missing by remember(url) { mutableStateOf<List<String>>(emptyList()) }

    // Back steps back inside the hub first, then returns home.
    BackHandler(enabled = isShown) {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else onGoHome()
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView.setWebContentsDebuggingEnabled(true)
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setBackgroundColor(android.graphics.Color.rgb(16, 16, 20))
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    // Chrome lays pages out at 100%; a bigger phone font would otherwise stretch the hub's layout.
                    settings.textZoom = 100
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            progress = newProgress
                        }

                        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                            if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                                pageError = "${message.message()} (line ${message.lineNumber()})"
                            }
                            return super.onConsoleMessage(message)
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? = LocalhostProxy.intercept(request, url)

                        override fun onPageStarted(view: WebView, pageUrl: String?, favicon: Bitmap?) {
                            error = null
                            missing = emptyList()
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            err: WebResourceError,
                        ) {
                            if (request.isForMainFrame) {
                                error = "Couldn't reach the hub: ${err.description}"
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView,
                            request: WebResourceRequest,
                            response: WebResourceResponse,
                        ) {
                            if (request.isForMainFrame) {
                                error = "The hub answered with error ${response.statusCode}"
                            } else {
                                val path = request.url.path ?: request.url.toString()
                                if (!path.endsWith("favicon.ico")) missing = missing + "$path (${response.statusCode})"
                            }
                        }
                    }
                    loadUrl(url)
                    webView = this
                }
            },
            update = { wv ->
                // Load again only when the saved address itself changes.
                if (wv.tag != url) {
                    if (wv.tag != null) wv.loadUrl(url)
                    wv.tag = url
                }
            },
        )

        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        val missingNote = if (missing.isEmpty()) null
        else "${missing.size} of the hub's files didn't load, like ${missing.first()}"
        val message = error ?: if (progress == 100) (missingNote ?: pageError) else null
        if (message != null) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xEE2A1414))
                    .padding(16.dp)
            ) {
                Text(message, color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(url, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                Text(
                    "Check the hub is running on your PC and your phone is on the same Wi-Fi.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        error = null
                        pageError = null
                        webView?.reload()
                    }) { Text("Retry") }
                    Button(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                        }
                    }) { Text("Open in Chrome") }
                }
            }
        }
    }
}
