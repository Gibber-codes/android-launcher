package codes.gibber.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.RectangleShape

/** Opens a site in the phone's browser; an address typed without http gets https. */
fun openSite(context: Context, address: String) {
    val url = if (address.contains("://")) address else "https://$address"
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "No browser could open $url", Toast.LENGTH_LONG).show()
    }
}

/** Asks for the site the Dev button opens. Hold Dev on the home screen to change it. */
@Composable
fun DevAddressDialog(current: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onCancel,
        shape = RectangleShape,
        containerColor = Hub.Night,
        title = { Text("Dev site", color = Hub.Text) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("The address to open") },
                supportingText = { Text("Hold Dev on the home screen to change it.") },
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { Text("Save and open", color = Hub.Primary) } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel", color = Hub.Muted) } },
    )
}
