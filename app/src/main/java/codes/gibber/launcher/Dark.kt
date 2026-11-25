package codes.gibber.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The approved dark design: a night ground, a blue and a red, and frosted white for what you tap. */
object Dark {
    val Blue = Color(0xFF1F3BFF)
    val Red = Color(0xFFE0262B)
    /** Blue and red mixed in OKLCH, the pink-purple where the circles light up. */
    val Mix = Color(0xFFB205B9)
    /** OKLCH 0.12 0.025 at the mix's hue. */
    val Night = Color(0xFF0A030A)
    val W62 = Color.White.copy(alpha = 0.62f)
    val W40 = Color.White.copy(alpha = 0.4f)
    val Frost = Color.White.copy(alpha = 0.07f)
    val FrostHi = Color.White.copy(alpha = 0.16f)
    /** A pop-up over the page: the night, nearly solid. */
    val Pane = Color(0xF2130A14)
    val Rule = Color.White.copy(alpha = 0.08f)
    val Green = Color(0xFF46D38A)
    val Research = Color(0xFFFF8A8D)
    val Feature = Color(0xFF7D8CFF)
    val Mono = FontFamily.Monospace

    /** The headings' soft glow, the mix at 45%. */
    val Glow = TextStyle(shadow = Shadow(color = Mix.copy(alpha = 0.45f), offset = Offset.Zero, blurRadius = 36f))

    /** Names under icons keep a dark shadow so they read over the circles. */
    val UnderIcon = TextStyle(shadow = Shadow(color = Color.Black.copy(alpha = 0.6f), offset = Offset(0f, 2f), blurRadius = 12f))

    fun mix(a: Color, b: Color, amountOfB: Float) = lerp(a, b, amountOfB)
}

fun Modifier.frost(hi: Boolean = false): Modifier = background(if (hi) Dark.FrostHi else Dark.Frost)

/** A section's name: small mono capitals with the glow. Holding it can start edit mode. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Head(text: String, modifier: Modifier = Modifier, onHold: (() -> Unit)? = null) {
    Text(
        text.uppercase(),
        color = Color.White,
        fontFamily = Dark.Mono,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.96.sp,
        style = Dark.Glow,
        modifier = if (onHold == null) modifier
        else modifier.combinedClickable(onClick = {}, onLongClick = onHold).padding(vertical = 2.dp),
    )
}

/** A screen's own name, like GIBBER HUB. */
@Composable
fun ScreenTitle(text: String) {
    Text(
        text.uppercase(),
        color = Color.White,
        fontFamily = Dark.Mono,
        fontSize = 22.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.3.sp,
    )
}

/** "‹ Back" at the top of a screen. */
@Composable
fun BackLink(label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 8.dp),
    ) {
        Glyph(Glyphs.Back, 16.dp, Dark.W62)
        Text(label, color = Dark.W62, fontSize = 13.sp)
    }
}

/** A flat frosted button: a word or two, square like the design. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FrostButton(
    label: String,
    modifier: Modifier = Modifier,
    hi: Boolean = false,
    lead: Glyphs? = null,
    height: Dp = 34.dp,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        modifier = modifier
            .height(height)
            .frost(hi)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp),
    ) {
        if (lead != null) Glyph(lead, 14.dp, Color.White)
        Text(
            label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The design's square switch: white when on, its knob the night. */
@Composable
fun SquareSwitch(on: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(width = 44.dp, height = 24.dp)
            .background(if (on) Color.White else Dark.FrostHi)
            .clickable(enabled = enabled) { onChange(!on) }
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(18.dp).background(if (on) Dark.Night else Dark.W62.copy(alpha = if (enabled) 0.62f else 0.3f)))
    }
}

/** The equalizer's round switch. */
@Composable
fun PillSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(width = 38.dp, height = 22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (on) Color.White else Dark.FrostHi)
            .clickable { onChange(!on) }
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(16.dp).clip(CircleShape).background(if (on) Color(0xFF111111) else Dark.W62))
    }
}

/** A coloured dot: green for running, grey for off. */
@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** A filled bar, like volume or usage. */
@Composable
fun Bar(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.height(4.dp).background(Color.White.copy(alpha = 0.14f))) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(4.dp)
                .background(Color.White)
        )
    }
}

/** A gap the design keeps between things in a column. */
@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

/** A hairline over a row in a list. */
fun Modifier.ruleAbove(): Modifier = drawBehind {
    drawLine(Dark.Rule, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
}

@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))
