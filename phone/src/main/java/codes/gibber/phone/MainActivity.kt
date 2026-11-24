package codes.gibber.phone

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import codes.gibber.phone.data.Numbers
import codes.gibber.phone.sms.recipientsOf
import codes.gibber.phone.ui.ContactEditScreen
import codes.gibber.phone.ui.FullScreen
import codes.gibber.phone.ui.GibberTheme
import codes.gibber.phone.ui.HomeScreen
import codes.gibber.phone.ui.Ink
import codes.gibber.phone.ui.KeypadScreen
import codes.gibber.phone.ui.NewMessageScreen
import codes.gibber.phone.ui.PersonScreen
import codes.gibber.phone.ui.SettingsScreen

/** Where you are in the app. The list is the back stack; the last one is on screen. */
sealed class Screen {
    data object Home : Screen()
    data class Person(
        val addresses: List<String>,
        val threadId: Long? = null,
        val contactId: Long? = null,
        val draft: String = "",
        val images: List<Uri> = emptyList(),
    ) : Screen()
    data class Keypad(val number: String = "") : Screen()
    data class NewMessage(val draft: String = "", val images: List<Uri> = emptyList()) : Screen()
    data class EditContact(val contactId: Long? = null, val number: String? = null) : Screen()
    data object Settings : Screen()
}

class MainActivity : ComponentActivity() {
    private val stack = mutableStateListOf<Screen>(Screen.Home)

    /** Bumps when you come back to the app or answer a permission question, so screens reload. */
    val resumes = mutableIntStateOf(0)

    private var askedAt = 0L
    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        resumes.intValue++
        // When Android answers at once without showing anything, it has blocked the ask (a "restricted" permission,
        // or "don't ask again"). Open the app's own settings page so it can be allowed there.
        if (missingPermissions().isNotEmpty() && System.currentTimeMillis() - askedAt < 600) openAppInfo()
    }

    fun openAppInfo() {
        try {
            startActivity(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {
        }
    }
    private val askRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        resumes.intValue++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent {
            GibberTheme {
                FullScreen()
                Box(Modifier.fillMaxSize().background(Ink.Page).safeDrawingPadding()) {
                    Content()
                }
            }
        }
    }

    @Composable
    private fun Content() {
        val screen = stack.last()
        BackHandler(enabled = stack.size > 1) { back() }
        when (screen) {
            Screen.Home -> HomeScreen(this, resumes.intValue)
            is Screen.Person -> PersonScreen(this, screen, resumes.intValue)
            is Screen.Keypad -> KeypadScreen(this, screen.number)
            is Screen.NewMessage -> NewMessageScreen(this, screen)
            is Screen.EditContact -> ContactEditScreen(this, screen)
            Screen.Settings -> SettingsScreen(this, resumes.intValue)
        }
    }

    fun go(screen: Screen) {
        stack.add(screen)
    }

    /** Swaps the screen on top for another, so Back skips the one you came from. */
    fun replace(screen: Screen) {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
        stack.add(screen)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex) else finish()
    }

    fun home() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    override fun onResume() {
        super.onResume()
        resumes.intValue++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Other apps asking to dial or text a number, and taps on our own notifications. */
    private fun handle(intent: Intent?) {
        intent ?: return
        val data = intent.data
        when (intent.action) {
            Intent.ACTION_DIAL, Intent.ACTION_VIEW -> {
                if (data?.scheme == "tel") {
                    home(); go(Screen.Keypad(Numbers.clean(data.schemeSpecificPart ?: "")))
                } else if (data != null && (data.scheme ?: "") in listOf("sms", "smsto", "mms", "mmsto")) {
                    openText(data, intent)
                } else if (intent.action == Intent.ACTION_DIAL) {
                    home(); go(Screen.Keypad())
                }
            }
            Intent.ACTION_SENDTO -> if (data != null) openText(data, intent)
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                @Suppress("DEPRECATION")
                val image = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_STREAM)
                home(); go(Screen.NewMessage(text, listOfNotNull(image)))
            }
            "codes.gibber.phone.OPEN_THREAD" -> {
                val thread = intent.getLongExtra("thread", -1)
                if (thread >= 0) {
                    val addresses = codes.gibber.phone.data.Texts.conversations(this).firstOrNull { it.id == thread }?.addresses ?: emptyList()
                    home(); go(Screen.Person(addresses, thread))
                }
            }
        }
        setIntent(Intent())
    }

    private fun openText(data: Uri, intent: Intent) {
        val people = recipientsOf(data)
        val body = intent.getStringExtra("sms_body") ?: intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: data.getQueryParameterSafe("body") ?: ""
        home()
        if (people.isEmpty()) go(Screen.NewMessage(body)) else go(Screen.Person(people, draft = body))
    }

    private fun Uri.getQueryParameterSafe(name: String): String? = try {
        Uri.parse("x://y?" + (schemeSpecificPart?.substringAfter('?', "") ?: "")).getQueryParameter(name)
    } catch (_: Exception) {
        null
    }

    // ---- setup: permissions and default apps ----

    fun missingPermissions(): List<String> = neededPermissions(this).filter {
        ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
    }

    fun askForPermissions() {
        askedAt = System.currentTimeMillis()
        askPermissions.launch(missingPermissions().toTypedArray())
    }

    fun canReadTexts(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    fun isDefaultCalls(): Boolean = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_DIALER)

    fun isDefaultTexts(): Boolean = Telephony.Sms.getDefaultSmsPackage(this) == packageName

    /** Shows Android's own "use Gibber Phone as your default?" question. You answer it there. */
    fun askToBeDefault(role: String) {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(role) && !rm.isRoleHeld(role)) askRole.launch(rm.createRequestRoleIntent(role))
    }

    companion object {
        fun neededPermissions(context: Context): List<String> {
            val list = mutableListOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_CALL_LOG,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_PHONE_NUMBERS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_MMS,
            )
            if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= 31) list.add(Manifest.permission.BLUETOOTH_CONNECT)
            return list
        }
    }
}
