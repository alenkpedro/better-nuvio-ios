package com.nuvio.app.betternuvio

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import kotlin.math.max
import kotlin.math.min

/**
 * Better Nuvio: the two colours of a title's art (its left and right sides) as soft pools
 * of light on the dark page, used faintly where artwork meets the page.
 */
data class RoomLightColors(val left: Color, val right: Color)

/** Light used while the art is still loading, so the room is never flat black. */
val DefaultRoomLight = RoomLightColors(left = Color(0xFF2B4E7A), right = Color(0xFF3A2E5E))

/** The art as pixels, per platform (iOS: Skia bitmap; Android: a software copy). */
internal expect fun roomLightBitmap(result: SuccessResult): ImageBitmap?

private val roomLightCache = mutableMapOf<String, RoomLightColors>()

/** Samples the average colour of the left and right thirds of [bitmap], lifted into usable light. */
fun roomLightFrom(bitmap: ImageBitmap): RoomLightColors? = runCatching {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return null
    val pixels = IntArray(w * h)
    bitmap.readPixels(pixels, 0, 0, w, h)
    fun average(fromX: Int, toX: Int): Color {
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        val step = max(1, (toX - fromX) * h / 4000)
        var i = 0
        for (y in 0 until h) for (x in fromX until toX) {
            if (i++ % step != 0) continue
            val p = pixels[y * w + x]
            r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF; n++
        }
        if (n == 0L) return Color.Gray
        return Color((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }
    RoomLightColors(
        left = average(0, w / 3).asLight(),
        right = average(w * 2 / 3, w).asLight(),
    )
}.getOrNull()

/** Keeps the hue, pushes saturation up and brightness to a level that reads as light on a dark ground. */
private fun Color.asLight(): Color {
    val mx = max(red, max(green, blue))
    val mn = min(red, min(green, blue))
    if (mx <= 0.001f) return Color(0xFF2B4E7A)
    val sat = if (mx == 0f) 0f else (mx - mn) / mx
    val targetMax = 0.62f
    val targetSat = (sat * 1.5f).coerceIn(0.35f, 0.85f)
    fun channel(c: Float): Float {
        val t = if (mx == mn) 1f else (c - mn) / (mx - mn)
        return targetMax * (1f - targetSat + targetSat * t)
    }
    return Color(channel(red), channel(green), channel(blue))
}

/**
 * Loads [imageUrl] small and returns its room light, animated so a change of title
 * cross-fades the light (the first beat of the "apagar a luz" signature).
 */
@Composable
fun rememberRoomLight(imageUrl: String?): RoomLightColors {
    val context = LocalPlatformContext.current
    val target = remember { mutableStateOf(imageUrl?.let { roomLightCache[it] } ?: DefaultRoomLight) }
    LaunchedEffect(imageUrl) {
        val url = imageUrl ?: return@LaunchedEffect
        roomLightCache[url]?.let { target.value = it; return@LaunchedEffect }
        val request = ImageRequest.Builder(context).data(url).size(Size(96, 54)).build()
        val result = runCatching { SingletonImageLoader.get(context).execute(request) }.getOrNull()
        val bitmap = (result as? SuccessResult)?.let(::roomLightBitmap) ?: return@LaunchedEffect
        roomLightFrom(bitmap)?.let { colors ->
            roomLightCache[url] = colors
            target.value = colors
        }
    }
    val left by animateColorAsState(target.value.left, tween(500), label = "roomLightLeft")
    val right by animateColorAsState(target.value.right, tween(500), label = "roomLightRight")
    return RoomLightColors(left, right)
}

/**
 * Paints the two pools of light behind the content: one from the left edge and one from
 * the right, centred at [centerYFraction] of the height, plus a softer pool under the art.
 * [intensity] dims the whole light (0 = lights off).
 */
fun Modifier.roomLight(
    colors: RoomLightColors,
    centerYFraction: Float = 0.44f,
    intensity: () -> Float = { 1f },
): Modifier = drawBehind {
    val k = intensity().coerceIn(0f, 1f)
    if (k <= 0f) return@drawBehind
    val cy = size.height * centerYFraction
    val w = size.width
    val h = size.height
    pool(colors.left.copy(alpha = 0.50f * k), Offset(0f, cy), radiusX = w * 1.4f, radiusY = h * 0.38f)
    pool(colors.right.copy(alpha = 0.44f * k), Offset(w, cy + h * 0.04f), radiusX = w * 1.4f, radiusY = h * 0.38f)
    pool(colors.left.copy(alpha = 0.30f * k), Offset(w / 2f, cy - h * 0.14f), radiusX = w * 0.9f, radiusY = h * 0.22f)
}

/** An elliptical radial pool with horizontal radius [radiusX] and vertical radius [radiusY]. */
private fun DrawScope.pool(color: Color, center: Offset, radiusX: Float, radiusY: Float) {
    if (radiusX <= 0f || radiusY <= 0f) return
    val radius = radiusX
    scale(scaleX = 1f, scaleY = radiusY / radiusX, pivot = center) {
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(0f to color, 0.7f to color.copy(alpha = 0f), 1f to Color.Transparent),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}
