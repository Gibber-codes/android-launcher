package codes.gibber.launcher

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/** Gibber Hub, built into the launcher as its second page. */
@Composable
fun HubScreen(prefs: Prefs, isShown: Boolean, onGoHome: () -> Unit) {
    var url by remember { mutableStateOf(prefs.hubUrl) }
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
            Text("Gibber Hub", color = Color.White, fontSize = 16.sp)
            Text("Address", color = Color.White, fontSize = 16.sp, modifier = Modifier.clickable { editing = true })
        }

        if (editing || url.isBlank()) {
            AddressForm(
                current = url,
                onSave = {
                    prefs.hubUrl = it
                    url = prefs.hubUrl
                    editing = false
                },
            )
        } else {
            HubWebView(url = url, isShown = isShown, onGoHome = onGoHome)
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HubWebView(url: String, isShown: Boolean, onGoHome: () -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    // Back steps back inside the hub first, then returns home.
    BackHandler(enabled = isShown) {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else onGoHome()
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                webViewClient = WebViewClient() // keep links inside the hub page
                loadUrl(url)
                webView = this
            }
        },
        update = { wv ->
            if (wv.url == null || !wv.url!!.startsWith(url.trimEnd('/'))) wv.loadUrl(url)
        },
    )
}
