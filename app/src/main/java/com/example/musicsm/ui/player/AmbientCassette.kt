package com.example.musicsm.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicsm.ui.components.ArtworkImage
import com.example.musicsm.ui.components.ArtworkSize
import com.example.musicsm.ui.components.MixArtOverlay
import com.example.musicsm.ui.components.MixArtState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/** Width over height of a compact cassette (100 × 63 mm). */
internal const val CASSETTE_ASPECT = 100f / 63f

// Geometry, as fractions of the cassette's width (x, radii) or height (y).
private const val SHELL_CORNER = 0.045f
private const val LABEL_LEFT = 0.055f
private const val LABEL_RIGHT = 0.945f
private const val LABEL_TOP = 0.07f
private const val LABEL_BOTTOM = 0.72f
private const val LABEL_CORNER = 0.022f
private const val WINDOW_LEFT = 0.21f
private const val WINDOW_RIGHT = 0.79f
private const val WINDOW_TOP = 0.33f
private const val WINDOW_BOTTOM = 0.61f
private const val REEL_Y = 0.47f
private const val REEL_LEFT_X = 0.33f
private const val REEL_RIGHT_X = 0.67f
private const val PACK_MIN_R = 0.075f
private const val PACK_MAX_R = 0.19f
private const val HUB_R = 0.048f

/**
 * Tape moved per second at play speed, in reel-radius units × degrees. Chosen so an empty hub
 * turns about 0.7 rev/s, close to a real deck at 4.76 cm/s.
 */
private const val PLAY_RATE = 250f * PACK_MIN_R

/** Spin applied when the tape jumps (seek, new track): a full rewind is a few quick turns. */
private const val SEEK_GAIN = 160f

private val ShellBase = Color(0xFF16161A)
private val TapeDark = Color(0xFF140E0B)
private val TapeColor = Color(0xFF2B1D16)
private val TapeEdge = Color(0xFF5A402F)
private val HubColor = Color(0xFFF1ECE2)
private val HubShade = Color(0xFFB9B2A6)
private val WindowColor = Color(0xFF09090B)
private val MetalLight = Color(0xFFC9C9D1)
private val MetalDark = Color(0xFF4E4E56)
private val FeltColor = Color(0xFF7A6652)

// Retro label stripes. Pulled towards the artwork colour so each tape matches its cover.
private val StripeSun = Color(0xFFFFD166)
private val StripeOrange = Color(0xFFFF8C42)
private val StripePink = Color(0xFFEF476F)

/** Reel angles and tape progress, read only in draw so the animation never recomposes. */
private class CassetteMotion {
    var progress by mutableFloatStateOf(0f)
    var leftAngle by mutableFloatStateOf(0f)
    var rightAngle by mutableFloatStateOf(0f)
    var speed = 0f
}

/**
 * A compact cassette playing the current song: the cover art is the label, and through the
 * window the two reels turn while it plays and coast to a stop on pause.
 *
 * The tape really moves: the left pack empties into the right one as the song plays (by area, so
 * the radii change the way a real spool does), and an empty hub spins faster than a full one
 * because tape runs at constant speed. A seek or a new track spins the reels through the jump, so
 * starting the next song looks like a quick rewind.
 */
@Composable
internal fun AmbientCassette(
    url: String?,
    title: String?,
    artist: String?,
    isPlaying: Boolean,
    positionFlow: StateFlow<Long>,
    durationMs: Long,
    accent: State<Color>,
    breath: State<Float>,
    mixArt: MixArtState,
    modifier: Modifier = Modifier,
) {
    // Read only inside the frame loop, never in composition, so position ticks don't recompose.
    val position = positionFlow.collectAsStateWithLifecycle()
    val playing by rememberUpdatedState(isPlaying)
    val duration by rememberUpdatedState(durationMs)
    val motion = remember { CassetteMotion() }

    LaunchedEffect(motion) {
        fun target(): Float =
            if (duration > 0L) (position.value.toFloat() / duration).coerceIn(0f, 1f) else 0f

        motion.progress = target()
        var last = withFrameNanos { it }
        while (true) {
            val goal = target()
            if (!playing && motion.speed < 0.001f && abs(goal - motion.progress) < 0.0005f) {
                // Stopped and settled: idle cheaply instead of drawing frames all night.
                motion.speed = 0f
                motion.progress = goal
                delay(200)
                last = withFrameNanos { it }
                continue
            }
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
            last = now

            motion.speed += ((if (playing) 1f else 0f) - motion.speed) * min(1f, dt * 5f)
            val previous = motion.progress
            motion.progress += (goal - previous) * min(1f, dt * 3f)
            val tape = motion.speed * PLAY_RATE * dt + (motion.progress - previous) * SEEK_GAIN

            motion.leftAngle = (motion.leftAngle + tape / packRadius(1f - motion.progress)) % 360f
            motion.rightAngle = (motion.rightAngle + tape / packRadius(motion.progress)) % 360f
        }
    }

    Box(
        modifier = modifier.graphicsLayer {
            val scale = 1f + breath.value * 0.018f
            scaleX = scale
            scaleY = scale
        },
    ) {
        // Lit from behind in the artwork's colour, and resting on a soft contact shadow.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val glow = accent.value
                    val radius = size.maxDimension * 0.62f
                    val center = Offset(size.width / 2f, size.height * 0.55f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glow.copy(alpha = 0.48f),
                                glow.copy(alpha = 0.15f),
                                glow.copy(alpha = 0f),
                            ),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                    val floor = Offset(size.width / 2f, size.height * 1.03f)
                    scale(scaleX = 1f, scaleY = 0.14f, pivot = floor) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                0f to Color.Black.copy(alpha = 0.75f),
                                1f to Color.Transparent,
                                center = floor,
                                radius = size.width * 0.52f,
                            ),
                            radius = size.width * 0.52f,
                            center = floor,
                        )
                    }
                },
        )

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = maxWidth
            val h = maxHeight
            val density = LocalDensity.current
            val titleSize = with(density) { (w * 0.048f).toSp() }
            val artistSize = with(density) { (w * 0.027f).toSp() }
            val sideSize = with(density) { (w * 0.036f).toSp() }

            Canvas(Modifier.fillMaxSize()) { drawShell(accent.value) }

            Box(
                Modifier
                    .offset(x = w * LABEL_LEFT, y = h * LABEL_TOP)
                    .size(w * (LABEL_RIGHT - LABEL_LEFT), h * (LABEL_BOTTOM - LABEL_TOP))
                    .clip(RoundedCornerShape(w * LABEL_CORNER)),
            ) {
                Crossfade(targetState = url, animationSpec = tween(900), label = "cassetteLabel") { art ->
                    ArtworkImage(
                        url = art,
                        shape = RectangleShape,
                        targetSizePx = ArtworkSize.HERO,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                MixArtOverlay(
                    state = mixArt,
                    targetSizePx = ArtworkSize.HERO,
                    modifier = Modifier.fillMaxSize(),
                )
                // Keeps the label text readable over any cover, and sinks the window area.
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.70f),
                            0.40f to Color.Black.copy(alpha = 0.34f),
                            0.75f to Color.Black.copy(alpha = 0.28f),
                            1f to Color.Black.copy(alpha = 0.62f),
                        ),
                    )
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = w * 0.035f, vertical = h * 0.04f),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Crossfade(
                            targetState = title,
                            animationSpec = tween(700),
                            label = "cassetteTitle",
                            modifier = Modifier.weight(1f),
                        ) { value ->
                            Text(
                                value.orEmpty(),
                                style = MaterialTheme.typography.titleLarge,
                                fontSize = titleSize,
                                lineHeight = titleSize * 1.15f,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.2).sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(w * 0.025f))
                        // "Side A" badge, tinted to the cover.
                        Box(
                            Modifier
                                .size(w * 0.064f)
                                .drawBehind {
                                    drawRoundRect(
                                        color = lerp(accent.value, Color.White, 0.35f),
                                        cornerRadius = CornerRadius(size.minDimension * 0.22f),
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "A",
                                fontSize = sideSize,
                                lineHeight = sideSize,
                                fontWeight = FontWeight.Black,
                                color = Color.Black.copy(alpha = 0.82f),
                            )
                        }
                    }
                    if (!artist.isNullOrBlank()) {
                        Spacer(Modifier.height(h * 0.014f))
                        Crossfade(targetState = artist, animationSpec = tween(700), label = "cassetteArtist") { value ->
                            Text(
                                value.uppercase(),
                                fontSize = artistSize,
                                lineHeight = artistSize * 1.2f,
                                letterSpacing = 2.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.70f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Canvas(Modifier.fillMaxSize()) {
                drawLabelDetails(accent.value)
                drawWindow(motion, breath.value)
                drawBottom()
                drawScrews()
                drawGloss()
            }
        }
    }
}

/** Radius (fraction of width) of a pack holding [fraction] of the tape; area scales with length. */
private fun packRadius(fraction: Float): Float {
    val min2 = PACK_MIN_R * PACK_MIN_R
    val max2 = PACK_MAX_R * PACK_MAX_R
    return sqrt(min2 + (max2 - min2) * fraction.coerceIn(0f, 1f))
}

/** Smoked plastic body, tinted by the artwork, with a lit top edge and an inner bevel. */
private fun DrawScope.drawShell(accent: Color) {
    val w = size.width
    val h = size.height
    val base = lerp(ShellBase, accent, 0.20f)
    val corner = CornerRadius(w * SHELL_CORNER)
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to lerp(base, Color.White, 0.10f),
            0.35f to base,
            1f to lerp(base, Color.Black, 0.40f),
        ),
        cornerRadius = corner,
    )
    // Inner bevel where the two shell halves meet.
    val inset = w * 0.014f
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.30f),
        topLeft = Offset(inset, inset),
        size = Size(w - inset * 2f, h - inset * 2f),
        cornerRadius = CornerRadius(w * (SHELL_CORNER - 0.012f)),
        style = Stroke(width = 1.dp.toPx()),
    )
    // Grip ribs on the lower corners.
    val ribColor = Color.White.copy(alpha = 0.07f)
    repeat(6) { i ->
        val dx = w * (0.065f + i * 0.014f)
        drawLine(ribColor, Offset(dx, h * 0.80f), Offset(dx, h * 0.92f), 1.dp.toPx(), StrokeCap.Round)
        drawLine(ribColor, Offset(w - dx, h * 0.80f), Offset(w - dx, h * 0.92f), 1.dp.toPx(), StrokeCap.Round)
    }
    // Rim lit from above.
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.26f),
            1f to Color.White.copy(alpha = 0.04f),
        ),
        cornerRadius = corner,
        style = Stroke(width = 1.2.dp.toPx()),
    )
}

/** Label edge and the retro stripes that run out either side of the window. */
private fun DrawScope.drawLabelDetails(accent: Color) {
    val w = size.width
    val h = size.height
    val stripeLeft = w * (LABEL_LEFT + 0.02f)
    val stripeRight = w * (LABEL_RIGHT - 0.02f)
    val thickness = h * 0.030f
    listOf(
        0.415f to lerp(accent, StripeSun, 0.40f),
        0.470f to lerp(accent, StripeOrange, 0.40f),
        0.525f to lerp(accent, StripePink, 0.40f),
    ).forEach { (y, color) ->
        drawRoundRect(
            color = color.copy(alpha = 0.92f),
            topLeft = Offset(stripeLeft, h * y - thickness / 2f),
            size = Size(stripeRight - stripeLeft, thickness),
            cornerRadius = CornerRadius(thickness / 2f),
        )
    }
    drawRoundRect(
        color = Color.White.copy(alpha = 0.10f),
        topLeft = Offset(w * LABEL_LEFT, h * LABEL_TOP),
        size = Size(w * (LABEL_RIGHT - LABEL_LEFT), h * (LABEL_BOTTOM - LABEL_TOP)),
        cornerRadius = CornerRadius(w * LABEL_CORNER),
        style = Stroke(width = 1.dp.toPx()),
    )
}

private fun DrawScope.drawWindow(motion: CassetteMotion, breath: Float) {
    val w = size.width
    val h = size.height
    val left = w * WINDOW_LEFT
    val top = h * WINDOW_TOP
    val right = w * WINDOW_RIGHT
    val bottom = h * WINDOW_BOTTOM
    val corner = CornerRadius((bottom - top) * 0.24f)

    // Frame the window is set into.
    val frame = w * 0.014f
    drawRoundRect(
        color = Color(0xFF0E0E11),
        topLeft = Offset(left - frame, top - frame),
        size = Size(right - left + frame * 2f, bottom - top + frame * 2f),
        cornerRadius = CornerRadius(corner.x + frame),
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.6f),
            1f to Color.White.copy(alpha = 0.10f),
            startY = top - frame,
            endY = bottom + frame,
        ),
        topLeft = Offset(left - frame, top - frame),
        size = Size(right - left + frame * 2f, bottom - top + frame * 2f),
        cornerRadius = CornerRadius(corner.x + frame),
        style = Stroke(width = 1.dp.toPx()),
    )

    val window = Path().apply { addRoundRect(RoundRect(left, top, right, bottom, corner)) }
    clipPath(window) {
        drawRect(WindowColor)
        val leftCenter = Offset(w * REEL_LEFT_X, h * REEL_Y)
        val rightCenter = Offset(w * REEL_RIGHT_X, h * REEL_Y)
        val leftR = w * packRadius(1f - motion.progress)
        val rightR = w * packRadius(motion.progress)

        // Tape leaving each pack on its outer side, heading down to the guides.
        val strand = w * 0.005f
        drawLine(TapeEdge, Offset(leftCenter.x - leftR, leftCenter.y), Offset(leftCenter.x - leftR, bottom), strand)
        drawLine(TapeEdge, Offset(rightCenter.x + rightR, rightCenter.y), Offset(rightCenter.x + rightR, bottom), strand)

        drawPack(leftCenter, leftR)
        drawPack(rightCenter, rightR)
        drawHub(leftCenter, motion.leftAngle)
        drawHub(rightCenter, motion.rightAngle)

        // Counter ticks along the bottom of the glass.
        val tickColor = Color.White.copy(alpha = 0.22f)
        val tickStart = left + (right - left) * 0.12f
        val tickStep = (right - left) * 0.76f / 10f
        for (i in 0..10) {
            val x = tickStart + tickStep * i
            val len = h * if (i % 5 == 0) 0.035f else 0.020f
            drawLine(tickColor, Offset(x, bottom - h * 0.012f - len), Offset(x, bottom - h * 0.012f), 1.dp.toPx())
        }

        // Smoked glass with a glare that slides gently with the breath.
        drawRect(Color.Black.copy(alpha = 0.10f))
        val shift = (breath - 0.5f) * w * 0.08f
        drawRect(
            Brush.linearGradient(
                0f to Color.Transparent,
                0.42f to Color.White.copy(alpha = 0.0f),
                0.50f to Color.White.copy(alpha = 0.10f),
                0.58f to Color.White.copy(alpha = 0.0f),
                1f to Color.Transparent,
                start = Offset(left + shift, top),
                end = Offset(left + (bottom - top) * 1.6f + shift, bottom),
            ),
        )
        drawRect(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.07f),
                0.3f to Color.Transparent,
                startY = top,
                endY = bottom,
            ),
        )
    }
    drawPath(window, Color.White.copy(alpha = 0.16f), style = Stroke(width = 1.dp.toPx()))
}

private fun DrawScope.drawPack(center: Offset, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            0f to TapeDark,
            0.55f to TapeColor,
            0.94f to TapeEdge,
            1f to TapeDark,
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
    // Faint winding rings so the pack reads as wound tape, not a disc.
    val hub = size.width * HUB_R
    val ringStep = (radius - hub) / 4f
    if (ringStep > 0f) {
        var r = hub + ringStep
        while (r < radius - 1f) {
            drawCircle(
                color = Color.White.copy(alpha = 0.035f),
                radius = r,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
            r += ringStep
        }
    }
    // Fixed light from the upper left, so the sheen stays still while the pack turns.
    val sheen = radius * 0.86f
    drawArc(
        color = Color.White.copy(alpha = 0.10f),
        startAngle = 195f,
        sweepAngle = 75f,
        useCenter = false,
        topLeft = Offset(center.x - sheen, center.y - sheen),
        size = Size(sheen * 2f, sheen * 2f),
        style = Stroke(width = radius * 0.08f, cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawHub(center: Offset, angle: Float) {
    val w = size.width
    val outer = w * HUB_R
    val inner = outer * 0.62f
    // Clear plastic flange around the hub.
    drawCircle(Color.Black.copy(alpha = 0.35f), radius = outer * 1.25f, center = center)
    drawCircle(
        brush = Brush.radialGradient(
            0f to HubColor,
            0.75f to HubColor,
            1f to HubShade,
            center = Offset(center.x - outer * 0.2f, center.y - outer * 0.2f),
            radius = outer * 1.2f,
        ),
        radius = outer,
        center = center,
    )
    drawCircle(WindowColor, radius = inner, center = center)
    rotate(degrees = angle, pivot = center) {
        val toothWidth = w * 0.011f
        repeat(6) { i ->
            rotate(degrees = i * 60f, pivot = center) {
                drawRoundRect(
                    color = HubColor,
                    topLeft = Offset(center.x - toothWidth / 2f, center.y - inner - 1f),
                    size = Size(toothWidth, inner * 0.46f),
                    cornerRadius = CornerRadius(toothWidth / 2f),
                )
            }
        }
        // One notch in the rim, so the spin is visible even at a glance.
        drawCircle(
            color = HubShade,
            radius = (outer - inner) * 0.30f,
            center = Offset(center.x, center.y - (outer + inner) / 2f),
        )
    }
}

/** The head ramp: guide rollers, felt pressure pad, and the tape running across the openings. */
private fun DrawScope.drawBottom() {
    val w = size.width
    val h = size.height
    val ramp = Path().apply {
        moveTo(w * 0.22f, h * 0.765f)
        lineTo(w * 0.78f, h * 0.765f)
        lineTo(w * 0.83f, h)
        lineTo(w * 0.17f, h)
        close()
    }
    drawPath(
        ramp,
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.08f),
            1f to Color.White.copy(alpha = 0.02f),
            startY = h * 0.765f,
            endY = h,
        ),
    )
    drawLine(
        Color.White.copy(alpha = 0.16f),
        Offset(w * 0.22f, h * 0.765f),
        Offset(w * 0.78f, h * 0.765f),
        1.dp.toPx(),
    )

    val hole = Color.Black.copy(alpha = 0.80f)
    listOf(0.30f, 0.70f).forEach { x ->
        val c = Offset(w * x, h * 0.88f)
        drawCircle(hole, radius = w * 0.024f, center = c)
        drawCircle(
            Brush.radialGradient(0f to MetalLight, 1f to MetalDark, center = c, radius = w * 0.012f),
            radius = w * 0.011f,
            center = c,
        )
    }
    listOf(0.425f, 0.535f).forEach { x ->
        drawRoundRect(
            hole,
            topLeft = Offset(w * x, h * 0.84f),
            size = Size(w * 0.04f, h * 0.085f),
            cornerRadius = CornerRadius(w * 0.006f),
        )
    }
    // Felt pressure pad, behind the tape at the head opening.
    drawRoundRect(
        FeltColor.copy(alpha = 0.85f),
        topLeft = Offset(w * 0.48f, h * 0.94f),
        size = Size(w * 0.04f, h * 0.035f),
        cornerRadius = CornerRadius(w * 0.004f),
    )
    drawLine(
        color = TapeEdge,
        start = Offset(w * 0.19f, h * 0.975f),
        end = Offset(w * 0.81f, h * 0.975f),
        strokeWidth = w * 0.006f,
    )
}

private fun DrawScope.drawScrews() {
    val w = size.width
    val h = size.height
    val radius = w * 0.014f
    listOf(
        Offset(w * 0.028f, h * 0.045f),
        Offset(w * 0.972f, h * 0.045f),
        Offset(w * 0.028f, h * 0.955f),
        Offset(w * 0.972f, h * 0.955f),
        Offset(w * 0.5f, h * 0.80f),
    ).forEach { c ->
        drawCircle(
            Brush.radialGradient(
                0f to MetalLight,
                1f to MetalDark,
                center = Offset(c.x - radius * 0.35f, c.y - radius * 0.35f),
                radius = radius * 1.4f,
            ),
            radius = radius,
            center = c,
            alpha = 0.7f,
        )
        val slot = Color.Black.copy(alpha = 0.65f)
        drawLine(slot, Offset(c.x - radius * 0.55f, c.y), Offset(c.x + radius * 0.55f, c.y), radius * 0.28f)
        drawLine(slot, Offset(c.x, c.y - radius * 0.55f), Offset(c.x, c.y + radius * 0.55f), radius * 0.28f)
    }
}

/** A soft diagonal sheen across the whole shell, as if lit from the upper left. */
private fun DrawScope.drawGloss() {
    val w = size.width
    val h = size.height
    drawRoundRect(
        brush = Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.09f),
            0.30f to Color.White.copy(alpha = 0.02f),
            0.45f to Color.Transparent,
            start = Offset.Zero,
            end = Offset(w, h),
        ),
        cornerRadius = CornerRadius(w * SHELL_CORNER),
    )
}
