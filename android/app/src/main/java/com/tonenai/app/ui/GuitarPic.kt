package com.tonenai.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.caverock.androidsvg.SVG
import com.tonenai.app.data.GuitarFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The guitar illustrations of the desktop app, pre-rendered to one SVG per model (assets/guitars, made by tools/render_guitar_svgs.js
 * from the same artwork). The body gradient is swapped for the finish chosen, and AndroidSVG draws the result into a cached bitmap.
 */
object GuitarArt {
    private val cache = object : LruCache<String, Bitmap>(24) {}

    // The finishes of the desktop app: three colours (centre, mid, edge) make a burst, two make a solid.
    private val BURST = mapOf(
        "sunburst" to listOf("#f3c247", "#c8631a", "#3b0c05"), "cherryburst" to listOf("#e8892a", "#a8200f", "#2d0504"),
        "honey" to listOf("#f1b94a", "#d98a2a", "#8a4a12"), "green" to listOf("#41a77f", "#1c6a4d", "#06251b"),
    )
    private val SOLID = mapOf(
        "butter" to listOf("#f0cf7a", "#c79a3b"), "cherry" to listOf("#c32a24", "#6a0d0b"), "black" to listOf("#2a2a2e", "#08080a"),
        "red" to listOf("#d23a2c", "#7a1710"), "tv" to listOf("#f2e3a2", "#cdb15a"), "natural" to listOf("#e9c98b", "#b9854a"),
        "teal" to listOf("#83cbc0", "#2f7f86"), "grey" to listOf("#9a9aa8", "#4a4a56"), "white" to listOf("#faf8f2", "#cfcab8"),
        "blue" to listOf("#4f86e0", "#1b3a86"),
    )
    private val BODY = Regex("<(?:radial|linear)Gradient id=\"(g\\d+b)\".*?</(?:radial|linear)Gradient>", RegexOption.DOT_MATCHES_ALL)

    /** The file for a model; models saved by older versions of the app map to the nearest current one. */
    private fun fileFor(kind: String, name: String): String {
        val k = when {
            kind == "Explorer / Flying V" -> if (Regex("flying|\\bv\\b", RegexOption.IGNORE_CASE).containsMatchIn(name)) "Flying V" else "Explorer"
            kind == "Acoustic" -> "Dreadnought"
            kind in GuitarFiles.KINDS || kind == "Flying V" -> kind
            else -> "Other"
        }
        return "guitars/" + k.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-') + ".svg"
    }

    fun svg(context: Context, kind: String, name: String, finish: String): String {
        val text = context.assets.open(fileFor(kind, name)).bufferedReader().use { it.readText() }
        val burst = BURST[finish]
        val solid = SOLID[finish]
        if (burst == null && solid == null) return text      // no finish chosen: the model's usual one
        return BODY.replace(text) { m ->
            val id = m.groupValues[1]
            if (burst != null)
                "<radialGradient id=\"$id\" cx=\".5\" cy=\".6\" r=\".65\"><stop offset=\"0\" stop-color=\"${burst[0]}\"/><stop offset=\".5\" stop-color=\"${burst[1]}\"/><stop offset=\"1\" stop-color=\"${burst[2]}\"/></radialGradient>"
            else
                "<linearGradient id=\"$id\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"1\"><stop offset=\"0\" stop-color=\"${solid!![0]}\"/><stop offset=\"1\" stop-color=\"${solid[1]}\"/></linearGradient>"
        }
    }

    fun bitmap(context: Context, kind: String, name: String, finish: String): Bitmap? {
        val key = "$kind|$name|$finish"
        cache.get(key)?.let { return it }
        return runCatching {
            val svg = SVG.getFromString(svg(context, kind, name, finish))
            val width = 480
            val height = (width / (svg.documentAspectRatio.takeIf { it > 0f } ?: 0.45f)).toInt().coerceIn(200, 2400)
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(bmp).drawPicture(svg.renderToPicture(width, height))
            bmp
        }.getOrNull()?.also { cache.put(key, it) }
    }
}

/** A guitar picture for [kind] in [finish], on the soft grey card the desktop app uses. */
@Composable
fun GuitarPic(kind: String, name: String, finish: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val bmp by produceState<Bitmap?>(null, kind, name, finish) {
        value = withContext(Dispatchers.Default) { GuitarArt.bitmap(context, kind, name, finish) }
    }
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(Brush.verticalGradient(listOf(Color(0xFFE6E6EE), Color(0xFFCFCFDA))))) {
        bmp?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.matchParentSize().padding(4.dp)) }
    }
}
