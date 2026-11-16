package com.tonenai.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlin.math.pow
import org.json.JSONObject

/** The logo drawing of the desktop splash (tools/extract_splash_paths.py), parsed once. */
private class Logo(val ring: Path, val wave: Path, val w1: Path, val w2: Path) {
    val words = Path().apply { addPath(w1); addPath(w2) }
    val ringBounds = ring.getBounds()
}

private fun loadLogo(context: Context): Logo? = runCatching {
    val o = JSONObject(context.assets.open("splash/paths.json").bufferedReader().use { it.readText() })
    fun path(key: String) = PathParser().parsePathString(o.getString(key)).toPath()
    Logo(path("ring"), path("wave"), path("w1"), path("w2"))
}.getOrNull()

private fun span(t: Float, start: Float, dur: Float) = ((t - start) / dur).coerceIn(0f, 1f)
private fun easeOut(x: Float) = 1f - (1f - x).pow(3)
private fun easeInOut(x: Float) = if (x < .5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f

private const val RED = 0xFFE11D2E
private const val TOTAL = 4.5f          // seconds of the desktop timeline: bass 0.0, ring .7, wave .9, TO 1.24, NEN 1.32, shine 1.75, tagline 1.9, out 3.75

/**
 * The opening animation of the desktop app: a red pulse, the ring and sound wave draw themselves, TONEN and AI slide in, a shine
 * sweeps across, the tagline fades up. [sound] plays the desktop's opening sting and keeps the original 4.5 s timing (the motion
 * lands on its beats); without it the same animation runs faster. A tap skips it.
 */
@Composable
fun Splash(sound: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val logo = remember { loadLogo(context.applicationContext) }
    val speed = if (sound) 1f else 0.55f
    val clock = remember { Animatable(0f) }
    var skipped by remember { mutableStateOf(false) }
    val out = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        clock.animateTo(TOTAL, tween((TOTAL * 1000 * speed).toInt(), easing = LinearEasing))
        onDone()
    }
    LaunchedEffect(skipped) {
        if (skipped) {
            out.animateTo(1f, tween(250))
            onDone()
        }
    }
    DisposableEffect(sound) {
        val player = if (sound) startIntro(context.applicationContext) else null
        onDispose { runCatching { player?.stop(); player?.release() } }
    }

    val t = clock.value
    val fade = 1f - easeInOut(span(t, 3.75f, 0.7f))
    BoxWithConstraints(
        Modifier.fillMaxSize().graphicsLayer { alpha = fade * (1f - out.value); val z = 1f + 0.02f * (1f - fade); scaleX = z; scaleY = z }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { skipped = true },
    ) {
        val logoWidth = maxWidth * 0.88f
        val logoHeight = logoWidth * 400f / 1271f
        Canvas(Modifier.fillMaxSize()) { drawSplash(t, logo) }
        Text(
            "TONE FOR EVERY SONG", style = MaterialTheme.typography.labelMedium, color = Color(0xFF9A9AA8), letterSpacing = 4.sp,
            modifier = Modifier.align(Alignment.Center).offset(y = logoHeight / 2 + 34.dp).graphicsLayer { alpha = span(t, 1.9f, 0.8f) },
        )
    }
}

private fun DrawScope.drawSplash(t: Float, logo: Logo?) {
    val c = Offset(size.width / 2f, size.height * 0.45f)
    drawRect(Brush.radialGradient(listOf(Color(0xFF17171F), Color(0xFF0B0B0F), Color(0xFF050507)), center = c, radius = size.maxDimension * 0.75f))

    // the bass pulse
    val bp = easeOut(span(t, 0f, 1.6f))
    val bassAlpha = if (bp < .12f) bp / .12f else 1f - 0.65f * ((bp - .12f) / .88f)
    val bassR = min(size.width, size.height) * 0.35f * (0.4f + 0.85f * bp)
    drawCircle(Brush.radialGradient(listOf(Color(RED).copy(alpha = .33f * bassAlpha), Color(RED).copy(alpha = .09f * bassAlpha), Color.Transparent), center = c, radius = bassR), bassR, c)
    if (logo == null) return

    val s = size.width * 0.88f / 1271f * (0.94f + 0.11f * (t / TOTAL))
    withTransform({ translate(c.x, c.y); scale(s, s, Offset.Zero); translate(-635.5f, -300f) }) {
        // ring and sound wave draw themselves (the group is translate(-117.6 -6.6) scale(.63) in the desktop drawing)
        withTransform({ translate(-117.6f, -6.6f); scale(0.63f, 0.63f, Offset.Zero) }) {
            trimmed(logo.ring, easeOut(span(t, 0.7f, 0.5f)))?.let {
                drawPath(it, Brush.linearGradient(listOf(Color(0xFFFF3B47), Color(0xFFB20710)), start = logo.ringBounds.topLeft, end = logo.ringBounds.bottomRight),
                    style = Stroke(50f, cap = StrokeCap.Round))
            }
            trimmed(logo.wave, easeOut(span(t, 0.9f, 0.55f)))?.let {
                drawPath(it, Color(0xFFF4F4F6), style = Stroke(46f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        // TONEN slides in, then AI (with a red glow)
        val a1 = easeOut(span(t, 1.24f, 0.7f))
        withTransform({ translate(-24f * (1f - a1), 0f) }) { drawPath(logo.w1, Color(0xFFF4F4F6), alpha = a1) }
        val a2 = easeOut(span(t, 1.32f, 0.7f))
        val g = span(t, 1.6f, 1.6f).let { if (it <= 0f || it >= 1f) 0f else if (it < .35f) it / .35f else (1f - it) / .65f }
        withTransform({ translate(-24f * (1f - a2), 0f) }) {
            if (g > 0f) for ((w, al) in listOf(44f to .10f, 28f to .16f, 14f to .24f))
                drawPath(logo.w2, Color(RED).copy(alpha = al * g * a2), style = Stroke(w, join = StrokeJoin.Round))
            drawPath(logo.w2, Color(RED), alpha = a2)
        }
        // the shine sweeping over the letters
        val sh = easeInOut(span(t, 1.75f, 1.1f))
        if (sh > 0f && sh < 1f) clipPath(logo.words) {
            val x = 120f + 1150f * sh
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = .55f), Color.Transparent), startX = x, endX = x + 260f),
                topLeft = Offset(x, 100f), size = androidx.compose.ui.geometry.Size(260f, 400f))
        }
    }
}

/** The first [fraction] of a path's length (what the desktop does with stroke-dashoffset). */
private fun trimmed(path: Path, fraction: Float): Path? {
    if (fraction <= 0f) return null
    val measure = PathMeasure().apply { setPath(path, false) }
    return Path().also { measure.getSegment(0f, measure.length * fraction, it, true) }
}

/** Starts the desktop's opening sting from the APK. */
private fun startIntro(context: Context): MediaPlayer? = runCatching {
    context.assets.openFd("assets/tonenai-intro.wav").use { fd ->
        MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            prepare()
            start()
        }
    }
}.getOrNull()
