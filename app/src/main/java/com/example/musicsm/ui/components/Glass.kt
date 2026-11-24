package com.example.musicsm.ui.components

import android.app.ActivityManager
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.musicsm.ui.theme.GlassFill
import com.example.musicsm.ui.theme.GlassStroke
import com.example.musicsm.ui.theme.GlassStrokeSoft
import com.example.musicsm.ui.theme.LocalMusicSmPalette
import com.example.musicsm.ui.theme.OverlayTint
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * Shared [HazeState] for the whole app: background content registers as the blur source,
 * glass panels sample it. Provided at the root so any panel can frost the scrolling content.
 */
val LocalHazeState: ProvidableCompositionLocal<HazeState?> = compositionLocalOf { null }

/**
 * Height (in dp) occupied by the floating glass bottom bar, so scrollable screens can add
 * matching bottom content padding and let their last item scroll out from under the glass.
 */
val LocalBottomBarPadding: ProvidableCompositionLocal<Dp> = compositionLocalOf { 0.dp }

/**
 * Recording of the screen content that `liquid` [GlassPanel]s refract. Provide it only around
 * panels that sit *outside* the recorded node: a panel sampling a layer that contains itself
 * recurses and crashes.
 */
val LocalLiquidBackdrop: ProvidableCompositionLocal<Backdrop?> = compositionLocalOf { null }

@Composable
fun rememberHazeState(): HazeState = remember { HazeState() }

/**
 * Whether this device should be handed the cheap version of every effect.
 *
 * Android only flags genuinely memory-starved hardware here, so this is a floor rather than a
 * judgement: on a device that reports it, spending frames on springs and cross-screen slides is
 * spending them on the wrong thing.
 */
@Composable
fun isLowEndDevice(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
    }
}

/**
 * Whether the real blur is worth attempting on this device.
 *
 * Separate from [isLowEndDevice] because the two ask different questions: blur needs a hardware
 * path that only exists from API 31, while motion is only ever a question of budget. An API 30
 * flagship keeps its animations and loses its blur; a low-RAM device loses both.
 */
@Composable
fun isGlassAllowed(): Boolean {
    val lowEnd = isLowEndDevice()
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !lowEnd
}

/** Marks this content as the backdrop that glass panels blur. No-op if no haze state is present. */
fun Modifier.glassBackdrop(state: HazeState?): Modifier =
    if (state != null) this.hazeSource(state) else this

/**
 * Traces [shape]'s edge with an additive light line.
 *
 * Additive rather than drawn-over: a rim is light caught on an edge, so it has to brighten what is
 * already there. A normal stroke of the same colour flattens into a drawn-on outline, and against
 * a dark backdrop it reads as a border rather than as a lit edge.
 */
private fun Modifier.glassRim(shape: Shape, brush: Brush, width: Dp): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val stroke = Stroke(width.toPx())
    onDrawWithContent {
        drawContent()
        drawOutline(outline, brush, style = stroke, blendMode = BlendMode.Plus)
    }
}

/**
 * A frosted-glass panel: blurs whatever [LocalHazeState] content sits behind it, with a subtle
 * white veil and a hairline highlight stroke. Degrades to a translucent surface when no blur
 * source is available (e.g. previews) or the device cannot afford one.
 *
 * With [liquid] on, the panel reads like Apple's Liquid Glass: a light, clear blur, a diagonal
 * specular sheen sweeping the top-left, an additive rim catching the light along the same diagonal,
 * and a soft drop shadow so the pill floats above the content it frosts. This is the treatment the
 * floating bottom bars share, and they share it deliberately — the mini player and the tab bar are
 * two panes of one piece of glass, so any divergence between them reads as a mistake.
 *
 * No extra colour/saturation render effect is layered onto the blur: on Haze 1.6.x an external
 * render effect stacked on the capture layer goes stale for a frame across recompositions and screen
 * transitions (fixed upstream only in 2.x, #1333), which showed as the panel flashing unblurred on
 * tab switches and play/pause. The tint, sheen and rim carry the look instead.
 *
 * When a [LocalLiquidBackdrop] is provided, a liquid panel with a rounded shape is instead drawn as
 * real Liquid Glass (see [LiquidGlassPanel]); the Haze version above stays as the fallback.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    tint: Color = GlassFill,
    liquid: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val liquidBackdrop = LocalLiquidBackdrop.current
    if (liquid && liquidBackdrop != null && shape is CornerBasedShape && isGlassAllowed()) {
        LiquidGlassPanel(modifier, shape, tint, liquidBackdrop, content)
        return
    }
    val hazeState = LocalHazeState.current
    val frosted = hazeState != null && isGlassAllowed()
    // Cheaper than HazeMaterials: modest blur, no per-frame noise shader. Remembered so the
    // style isn't reallocated on every recomposition/scroll frame.
    val hazeTint = LocalMusicSmPalette.current.hazeTint
    val style = remember(hazeTint, liquid) {
        HazeStyle(
            // Liquid glass is a clear tinted pane, not a frosted one: only a hair of blur so the
            // content stays legible through it, and the tint + gloss do the separating instead.
            blurRadius = if (liquid) 5.dp else 20.dp,
            tint = HazeTint(hazeTint),
            noiseFactor = 0f,
        )
    }
    // Top gloss + faint bottom edge glow. A bright, quick-falling band across the top reads as a
    // hard reflection off a glossy pane; the long diagonal fade behind it keeps the light directional.
    val sheen = remember {
        Brush.linearGradient(
            0.0f to Color.White.copy(alpha = 0.48f),
            0.16f to Color.White.copy(alpha = 0.14f),
            0.5f to Color.Transparent,
            1.0f to Color.White.copy(alpha = 0.10f),
            start = Offset.Zero,
            end = Offset.Infinite,
        )
    }
    // Brightest where the sheen enters and fading along the same diagonal, so the rim and the
    // sheen read as one light source rather than two.
    val rim = remember {
        Brush.linearGradient(
            0.0f to Color.White.copy(alpha = RIM_ALPHA),
            0.55f to Color.White.copy(alpha = RIM_ALPHA * 0.25f),
            1.0f to Color.White.copy(alpha = RIM_ALPHA * 0.55f),
            start = Offset.Zero,
            end = Offset.Infinite,
        )
    }
    val shadowed = if (liquid) {
        modifier.shadow(
            elevation = 14.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.45f),
            spotColor = Color.Black.copy(alpha = 0.45f),
        )
    } else {
        modifier
    }

    // The blur modifier is remembered as one instance, keyed only on things that actually change the
    // blur, so recomposing the panel for an unrelated reason (a tab switch, a play/pause) reuses the
    // same node instead of rebuilding it.
    val blur = remember(frosted, hazeState, style) {
        if (frosted) Modifier.hazeEffect(state = hazeState!!, style = style) else null
    }
    Box(modifier = shadowed.clip(shape)) {
        // The blurred backdrop, kept in its own layer so the fill above it composites over a
        // finished image rather than over the live blur.
        val backdrop = Modifier.matchParentSize()
        if (blur != null) {
            Box(backdrop.then(blur))
        } else {
            Box(backdrop.background(OverlayTint.copy(alpha = 0.08f)))
        }
        Box(backdrop.background(tint))
        if (liquid) Box(backdrop.background(sheen))
        Box(
            if (liquid) {
                backdrop.glassRim(shape, rim, RIM_WIDTH)
            } else {
                backdrop.border(BorderStroke(0.5.dp, Brush.verticalGradient(listOf(GlassStroke, GlassStrokeSoft))), shape)
            },
        )
        content()
    }
}

/**
 * Apple-style Liquid Glass: the content behind is saturated, lightly blurred and bent by a lens
 * band along the rounded edge, with a specular highlight from the library. The lens needs
 * Android 13 (RuntimeShader); Android 12 keeps the blur and vibrancy.
 *
 * The bar's pills change size every frame while it minimises on scroll, so nothing here may cost
 * a rebuilt offscreen buffer per size change beyond the backdrop itself: the drop shadow is the
 * renderer's own elevation shadow (drawn analytically) rather than the library's blurred shadow
 * layer, and the rim is a crisp stroke rather than a blur-filtered one.
 */
@Composable
private fun LiquidGlassPanel(
    modifier: Modifier,
    shape: CornerBasedShape,
    tint: Color,
    backdrop: Backdrop,
    content: @Composable BoxScope.() -> Unit,
) {
    val hazeTint = LocalMusicSmPalette.current.hazeTint
    Box(
        modifier = modifier
            .shadow(
                elevation = LIQUID_SHADOW_ELEVATION,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.25f),
                spotColor = Color.Black.copy(alpha = 0.35f),
            )
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(LIQUID_BLUR.toPx())
                    lens(
                        refractionHeight = LIQUID_LENS_HEIGHT.toPx(),
                        refractionAmount = LIQUID_LENS_AMOUNT.toPx(),
                        depthEffect = true,
                    )
                },
                highlight = { LiquidHighlight },
                shadow = null,
                onDrawSurface = {
                    drawRect(hazeTint)
                    drawRect(tint)
                },
            )
            .clip(shape),
        content = content,
    )
}

/** The library's default rim, without the blur filter that is re-rasterised on every resize. */
private val LiquidHighlight = Highlight(blurRadius = 0.dp)

private val LIQUID_SHADOW_ELEVATION = 10.dp

// Clear rather than frosted, so what is behind stays recognisable through the bend.
private val LIQUID_BLUR = 4.dp

// The lens band must stay within the corner radius; the shortest pill (60 dp) has a 30 dp radius.
private val LIQUID_LENS_HEIGHT = 16.dp
private val LIQUID_LENS_AMOUNT = 32.dp

/** Peak opacity of the additive rim. Additive light saturates fast, so this stays low. */
private const val RIM_ALPHA = 0.48f

private val RIM_WIDTH = 0.8.dp
