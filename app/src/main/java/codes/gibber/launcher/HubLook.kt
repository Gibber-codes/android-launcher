package codes.gibber.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Gibber Hub's Gradient look: smoked glass over a dark night, sharp corners, and a rim lit blue
 * to red through violet. The pair is the hub's own (client/gradient.css in hub-kit).
 */
object Hub {
    val Blue = Color(0xFF1F3BFF)
    val Red = Color(0xFFE0262B)
    val Violet = Color(0xFF8A2E9E)
    val Night = Color(0xFF110B18)
    val Glass = Night.copy(alpha = 0.74f)
    val Primary = Color(0xFFC9A8F0) // the hub's light violet, for what is lit
    val Accent = Color(0xFF9DB2FF)
    val Text = Color.White
    val Muted = Color.White.copy(alpha = 0.62f)
    val Rim = Brush.linearGradient(listOf(Blue, Violet, Red))
}

/** A pane of the hub's glass: smoked, sharp-cornered, with the lit rim. */
fun Modifier.hubGlass(lit: Boolean = true): Modifier =
    this
        .background(Hub.Glass)
        .border(1.dp, if (lit) Hub.Rim else SolidColor(Hub.Muted.copy(alpha = 0.35f)), RectangleShape)

/** A small heading over a pane, in the hub's spaced capitals. */
@Composable
fun HubLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = Hub.Muted,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier,
    )
}

/** The hub's state pill: a dot, lit when something waits, and a word. */
@Composable
fun HubPill(text: String, lit: Boolean, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .hubGlass(lit)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .background(if (lit) Hub.Accent else Hub.Muted.copy(alpha = 0.4f), CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = if (lit) Hub.Text else Hub.Muted, fontSize = 13.sp)
    }
}
