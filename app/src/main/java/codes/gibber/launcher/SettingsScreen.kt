package codes.gibber.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Gibber Settings: the hub's address and updates. Opened from its entry in the app list. */
@Composable
fun SettingsScreen(
    hubUrl: String,
    onSaveUrl: (String) -> Unit,
    resumes: Int,
    onClose: () -> Unit,
) {
    BackHandler(enabled = true) { onClose() }
    HideSystemBars()
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF101014))
            .imePadding()
            .padding(top = 12.dp)
    ) {
        Text(
            "‹  Gibber Settings",
            color = Color.White,
            fontSize = 22.sp,
            modifier = Modifier
                .clickable(onClick = onClose)
                .padding(horizontal = 24.dp, vertical = 8.dp),
        )
        AddressForm(current = hubUrl, onSave = { onSaveUrl(it); onClose() })
        Spacer(Modifier.height(8.dp))
        Text(
            "Launcher version ${BuildConfig.VERSION_NAME}",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        UpdateButton(resumes = resumes, modifier = Modifier.padding(horizontal = 24.dp))
    }
}
