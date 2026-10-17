package com.bottlepost.app.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bottlepost.app.R
import kotlin.math.PI
import kotlin.math.sin

/** "Letter in a bottle" palette, shared with the launcher icon and the launch screen. */
object Sea {
    val Night = Color(0xFF13283F)
    val Deep = Color(0xFF1F4A73)
    val Teal = Color(0xFF2F7A86)
    val WaveBack = Color(0xFF3B93A0)
    val WaveFront = Color(0xFF5FB8BE)
    val Foam = Color(0xFFDCEBEA)
    val Moon = Color(0xFFF6E7C1)
    val Paper = Color(0xFFFBF7F0)
    val Parchment = Color(0xFFF3E6CC)
    val PaperEdge = Color(0xFFE2D3B5)
    val Rule = Color(0xFFD9CDB6)
    val InkText = Color(0xFF243447)
    val Cork = Color(0xFFC9A66B)
    val Wax = Color(0xFFB5473A)
}

val NightSeaBrush = Brush.verticalGradient(listOf(Sea.Night, Sea.Deep))

/** A band of sea at the bottom of the draw area; [crest] is the band height as a fraction of it. */
fun DrawScope.seaWavePath(phase: Float, crest: Float, amplitude: Dp = 10.dp, waves: Float = 1.5f): Path {
    val top = size.height * (1f - crest)
    val amp = amplitude.toPx()
    val wavelength = size.width / waves
    return Path().apply {
        moveTo(0f, size.height)
        var x = 0f
        while (x <= size.width + 1f) {
            lineTo(x, top + amp * sin(2 * PI * (x / wavelength + phase)).toFloat())
            x += 8f
        }
        lineTo(size.width, size.height)
        close()
    }
}

/** Top bar on a night-sea gradient with a wave edge underneath. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeaTopBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column {
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            modifier = Modifier.background(NightSeaBrush),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = Sea.Paper,
                navigationIconContentColor = Sea.Paper,
                actionIconContentColor = Sea.Paper,
            ),
        )
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            drawPath(
                Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    var x = size.width
                    while (x >= -1f) {
                        lineTo(x, size.height * 0.5f + size.height * 0.5f * sin(2 * PI * x / (size.width / 3f)).toFloat())
                        x -= 8f
                    }
                    close()
                },
                Sea.Deep,
            )
        }
    }
}

/** Screen background: warm paper fading into sea foam (night water in dark mode). */
@Composable
fun SeaBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val colors = if (isSystemInDarkTheme()) listOf(Color(0xFF121821), Sea.Night) else listOf(Sea.Paper, Sea.Foam)
    Box(modifier.background(Brush.verticalGradient(colors)), content = content)
}

/** Ruled writing lines matching a text's [lineHeight], drawn behind the text. */
fun Modifier.ruledLines(lineHeight: TextUnit, color: Color = Sea.Rule): Modifier = drawBehind {
    val step = lineHeight.toPx()
    var y = step - 5.dp.toPx()
    while (y < size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        y += step
    }
}

/**
 * A sheet of letter paper. Always paper-colored with dark ink, also in dark mode, so letters look
 * like letters. [tinted] gives the older, parchment look used for my own letters.
 */
@Composable
fun PaperSheet(
    modifier: Modifier = Modifier,
    tinted: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomEnd = 4.dp, bottomStart = 14.dp),
        color = if (tinted) Sea.Parchment else Sea.Paper,
        contentColor = Sea.InkText,
        border = BorderStroke(1.dp, Sea.PaperEdge),
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** Primary action: a pill from deep sea to teal. */
@Composable
fun BottleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
    ) {
        Row(
            Modifier
                .background(
                    if (enabled) Brush.horizontalGradient(listOf(Sea.Deep, Sea.Teal))
                    else Brush.horizontalGradient(listOf(Sea.Rule, Sea.PaperEdge)),
                )
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Sea.Paper, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = if (enabled) Sea.Paper else Sea.InkText.copy(alpha = 0.5f), fontWeight = FontWeight.SemiBold)
        }
    }
}

/** The red-and-blue diagonal edge of an airmail envelope. */
@Composable
fun AirmailStripe(modifier: Modifier = Modifier, faded: Boolean = false) {
    Canvas(modifier) {
        val w = 14.dp.toPx()
        val alpha = if (faded) 0.35f else 0.9f
        var x = -size.height
        var i = 0
        while (x < size.width) {
            val color = if (i % 2 == 0) Sea.Wax else Sea.Deep
            drawPath(
                Path().apply {
                    moveTo(x, size.height)
                    lineTo(x + size.height, 0f)
                    lineTo(x + size.height + w * 0.6f, 0f)
                    lineTo(x + w * 0.6f, size.height)
                    close()
                },
                color.copy(alpha = alpha),
            )
            x += w
            i++
        }
    }
}

/** Unread marker: a red wax seal. */
@Composable
fun WaxSeal(count: Int) {
    Box(
        Modifier.size(24.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFFD0604F), Sea.Wax))),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 9) "9+" else "$count", color = Sea.Paper, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** The app's bottle, gently bobbing and tilting. */
@Composable
fun BobbingBottle(size: Dp, modifier: Modifier = Modifier) {
    val motion = rememberInfiniteTransition(label = "bottle")
    val bob by motion.animateFloat(-4f, 4f, infiniteRepeatable(tween(1700), RepeatMode.Reverse), label = "bob")
    val tilt by motion.animateFloat(-6f, 6f, infiniteRepeatable(tween(2500), RepeatMode.Reverse), label = "tilt")
    Image(
        painterResource(R.drawable.bottle_art),
        contentDescription = null,
        modifier = modifier.size(size).graphicsLayer {
            translationY = bob.dp.toPx()
            rotationZ = tilt
        },
    )
}

/** A small night sea with a bottle drifting across it, for "your letter is on its way". */
@Composable
fun DriftingBottleScene(modifier: Modifier = Modifier, height: Dp = 112.dp) {
    val motion = rememberInfiniteTransition(label = "drift")
    val phase by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(5000, easing = LinearEasing)), label = "phase")
    val drift by motion.animateFloat(0.12f, 0.62f, infiniteRepeatable(tween(9000), RepeatMode.Reverse), label = "x")
    val bob by motion.animateFloat(-4f, 4f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "bob")
    val tilt by motion.animateFloat(-10f, 10f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "tilt")
    BoxWithConstraints(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(18.dp)).background(NightSeaBrush)) {
        val width = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            listOf(0.1f to 0.2f, 0.32f to 0.12f, 0.58f to 0.28f, 0.86f to 0.16f).forEach { (x, y) ->
                drawCircle(Color.White.copy(alpha = 0.7f), 1.2.dp.toPx(), Offset(size.width * x, size.height * y))
            }
            val moon = Offset(size.width * 0.86f, size.height * 0.3f)
            drawCircle(Sea.Moon.copy(alpha = 0.9f), 12.dp.toPx(), moon)
            drawCircle(Sea.Night, 10.dp.toPx(), moon + Offset(5.dp.toPx(), -3.dp.toPx()))
            drawPath(seaWavePath(phase, 0.42f, amplitude = 5.dp, waves = 2f), Sea.WaveBack.copy(alpha = 0.7f))
        }
        Image(
            painterResource(R.drawable.bottle_art),
            contentDescription = null,
            modifier = Modifier
                .size(height * 0.72f)
                .align(Alignment.BottomStart)
                .graphicsLayer {
                    translationX = (width * drift).toPx()
                    translationY = bob.dp.toPx() - 6.dp.toPx()
                    rotationZ = tilt
                },
        )
        Canvas(Modifier.fillMaxSize()) {
            drawPath(seaWavePath(phase + 0.5f, 0.24f, amplitude = 4.dp, waves = 2.5f), Sea.WaveFront.copy(alpha = 0.6f))
        }
    }
}

/** Empty list: a lone bottle and a line of text. */
@Composable
fun EmptyBottle(text: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BobbingBottle(140.dp, Modifier.graphicsLayer { alpha = 0.9f })
        Spacer(Modifier.height(16.dp))
        Text(text, textAlign = TextAlign.Center, fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 26.sp)
    }
}
