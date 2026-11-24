package codes.gibber.launcher

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** "Update" on the home screen: checks GitHub for a newer build, downloads it and opens the installer. */
@Composable
fun UpdateButton(resumes: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var lastCheck by remember { mutableLongStateOf(0L) }

    fun say(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    // A quiet look every half hour or so, when you come back to the home screen.
    LaunchedEffect(resumes) {
        val now = System.currentTimeMillis()
        if (now - lastCheck > 30 * 60_000L) {
            lastCheck = now
            ready = Updater.latest()?.let { Updater.isNewer(it) } ?: ready
        }
    }

    val label = busy ?: if (ready) "Update ready" else "Update"
    Text(
        label,
        color = if (ready) Color(0xFF8AB4FF) else Color.White,
        fontSize = 18.sp,
        modifier = modifier
            .clickable(enabled = busy == null) {
                scope.launch {
                    Updater.install(context, busy = { busy = it }, say = ::say)
                }
            }
            .padding(vertical = 12.dp),
    )
}
