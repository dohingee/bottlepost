package com.bottlepost.app.ui.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bottlepost.app.R
import com.bottlepost.app.ui.common.seaWavePath

// Same night-sea palette as the launcher icon (res/drawable/ic_launcher_background.xml).
private val SkyTop = Color(0xFF13283F)
private val SkyMid = Color(0xFF1F4A73)
private val SeaLight = Color(0xFF2F7A86)
private val WaveBack = Color(0xFF3B93A0)
private val WaveFront = Color(0xFF5FB8BE)
private val Moon = Color(0xFFF6E7C1)
private val Paper = Color(0xFFFBF7F0)

/** Relative star positions (x, y, radius in dp) so the sky looks the same on every screen size. */
private val Stars = listOf(
    Triple(0.12f, 0.08f, 1.6f), Triple(0.30f, 0.15f, 1.0f), Triple(0.70f, 0.06f, 1.3f),
    Triple(0.88f, 0.20f, 1.0f), Triple(0.08f, 0.30f, 0.9f), Triple(0.55f, 0.24f, 1.2f),
    Triple(0.92f, 0.36f, 0.8f), Triple(0.22f, 0.42f, 0.8f),
)

/**
 * Launch screen shown while the app checks the account: a bottle drifting on a night sea with the
 * app name. With [onRetry] it becomes the offline screen.
 */
@Composable
fun BrandSplash(message: String? = null, onRetry: (() -> Unit)? = null) {
    val motion = rememberInfiniteTransition(label = "sea")
    val bob by motion.animateFloat(-6f, 6f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "bob")
    val tilt by motion.animateFloat(-4f, 4f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "tilt")
    val phase by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "phase")
    val twinkle by motion.animateFloat(0.35f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "twinkle")

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SkyTop, SkyMid, SeaLight))),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            Stars.forEachIndexed { i, (x, y, r) ->
                val alpha = if (i % 2 == 0) twinkle else 1.35f - twinkle
                drawCircle(Color.White.copy(alpha = alpha.coerceIn(0.2f, 1f)), r.dp.toPx(), Offset(size.width * x, size.height * y))
            }
            val moonCenter = Offset(size.width * 0.78f, size.height * 0.12f)
            drawCircle(Moon.copy(alpha = 0.9f), 22.dp.toPx(), moonCenter)
            drawCircle(SkyTop, 19.dp.toPx(), moonCenter + Offset(10.dp.toPx(), -6.dp.toPx()))
        }

        // Back wave behind the bottle, front wave over its lower half: it floats.
        Waves(Modifier.align(Alignment.BottomCenter), phase, WaveBack.copy(alpha = 0.6f), crest = 0.42f, shift = 0f)

        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.app_name),
                color = Paper,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 44.sp,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.splash_tagline),
                color = Paper.copy(alpha = 0.8f),
                fontFamily = FontFamily.Serif,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Image(
                painter = painterResource(R.drawable.bottle_art),
                contentDescription = null,
                modifier = Modifier
                    .size(220.dp)
                    .graphicsLayer {
                        translationY = bob.dp.toPx()
                        rotationZ = tilt
                    },
            )
            Spacer(Modifier.weight(1f))
        }

        Waves(Modifier.align(Alignment.BottomCenter), phase, WaveFront.copy(alpha = 0.55f), crest = 0.30f, shift = 0.5f)

        Column(
            Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 40.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (message != null) {
                Text(message, color = Paper, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
            }
            if (onRetry != null) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = Paper, contentColor = SkyTop),
                ) { Text(stringResource(R.string.action_retry)) }
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.width(96.dp),
                    color = Paper.copy(alpha = 0.9f),
                    trackColor = Paper.copy(alpha = 0.2f),
                )
            }
        }
    }
}

/** A band of sea at the bottom; [crest] is the band height as a fraction of the screen. */
@Composable
private fun Waves(modifier: Modifier, phase: Float, color: Color, crest: Float, shift: Float) {
    Canvas(modifier.fillMaxSize()) {
        drawPath(seaWavePath(phase + shift, crest), color)
    }
}
