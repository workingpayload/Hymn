package com.example.musicsm.ui.rename

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.example.musicsm.R
import com.example.musicsm.data.prefs.AppPreferences
import com.example.musicsm.ui.components.isLowEndDevice
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.CoralLight
import com.example.musicsm.ui.theme.JakartaSans
import com.example.musicsm.ui.theme.Lavender
import com.example.musicsm.ui.theme.OnAccent
import com.example.musicsm.ui.theme.OnDarkVariant
import com.example.musicsm.ui.theme.OnSurfaceLight
import com.example.musicsm.ui.theme.StitchBackground
import com.example.musicsm.ui.theme.Teal
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Only people who used the app as MusicSM need telling: an install that has been updated at least
 * once. A fresh install of Hymn has `lastUpdateTime == firstInstallTime`.
 */
internal fun shouldShowHymnIntro(seen: Boolean, firstInstallTime: Long, lastUpdateTime: Long): Boolean =
    !seen && lastUpdateTime > firstInstallTime

@HiltViewModel
class HymnIntroViewModel @Inject constructor(
    @ApplicationContext context: Context,
    preferences: AppPreferences,
) : ViewModel() {

    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    init {
        if (!preferences.hymnIntroSeen) {
            val info = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }.getOrNull()
            _visible.value = info != null &&
                shouldShowHymnIntro(seen = false, info.firstInstallTime, info.lastUpdateTime)
            // Marked straight away so the intro is truly one-time, even if the app is closed
            // halfway through it; fresh installs are marked without ever seeing it.
            preferences.hymnIntroSeen = true
        }
    }

    fun dismiss() {
        _visible.value = false
    }
}

// Timeline, in ms from the moment the overlay appears.
private const val BACKDROP_END = 700f
private const val OLD_IN = 250f
private const val OLD_IN_STAGGER = 45f
private const val OLD_IN_DUR = 450f
private const val OLD_OUT = 1700f
private const val OLD_OUT_STAGGER = 45f
private const val OLD_OUT_DUR = 520f
private const val WAVE_START = 1650f
private const val WAVE_PEAK = 2350f
private const val WAVE_FALL = 2700f
private const val WAVE_END = 3450f
private const val NEW_IN = 2550f
private const val NEW_IN_STAGGER = 110f
private const val NEW_IN_DUR = 780f
private const val GLOW_START = 2900f
private const val GLOW_END = 3900f
private const val SHIMMER_START = 3500f
private const val SHIMMER_END = 4500f
private const val LABEL_START = 2800f
private const val TEXT_START = 3700f
private const val BUTTON_START = 4150f
private const val FADE_DUR = 600f
private const val TOTAL_MS = 4800f
private const val EXIT_MS = 450

/** Progress of `this` (a time) through `[start, end]`, clamped to 0..1. */
private fun Float.span(start: Float, end: Float): Float = ((this - start) / (end - start)).coerceIn(0f, 1f)

private class Wave(
    val color: Color,
    val cycles: Float,
    val speed: Float,
    val offset: Float,
    val scale: Float,
    val alpha: Float,
)

/**
 * One-time, full-screen "MusicSM is now Hymn" moment. The old name rises in, dissolves into a
 * sound wave, and "Hymn" blooms out of it. A tap skips to the end; the button or Back closes it.
 */
@Composable
fun HymnIntroOverlay(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lowEnd = isLowEndDevice()
    val blurAllowed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !lowEnd
    val scope = rememberCoroutineScope()

    val clock = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    var leaving by remember { mutableStateOf(false) }
    val buttonReady by remember { derivedStateOf { clock.value >= BUTTON_START } }

    LaunchedEffect(Unit) {
        val animationsOff = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
        if (animationsOff) {
            clock.snapTo(TOTAL_MS)
        } else {
            clock.animateTo(TOTAL_MS, tween(TOTAL_MS.toInt(), easing = LinearEasing))
        }
    }

    val finish: () -> Unit = {
        if (!leaving) {
            leaving = true
            scope.launch {
                exit.animateTo(1f, tween(EXIT_MS, easing = FastOutSlowInEasing))
                onFinished()
            }
        }
    }
    BackHandler(onBack = finish)

    val infinite = rememberInfiniteTransition(label = "hymnIntro")
    val drift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing)),
        label = "drift",
    )
    val wavePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1_300, easing = LinearEasing)),
        label = "wavePhase",
    )

    // Theme tokens are composable getters, so they're read here and captured by the draw lambdas.
    val background = StitchBackground
    val accent = Coral
    val accentLight = CoralLight
    val lavender = Lavender
    val teal = Teal
    val onSurface = OnSurfaceLight
    val onSurfaceVariant = OnDarkVariant
    val onAccent = OnAccent

    val waves = remember(accent, lavender, teal) {
        listOf(
            Wave(lavender, cycles = 2.2f, speed = 1f, offset = 0.9f, scale = 0.7f, alpha = 0.55f),
            Wave(teal, cycles = 3.1f, speed = -1f, offset = 2.1f, scale = 0.55f, alpha = 0.45f),
            Wave(accent, cycles = 2.6f, speed = 2f, offset = 0f, scale = 1f, alpha = 0.95f),
        )
    }
    val wavePath = remember { Path() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val e = exit.value
                alpha = 1f - e
                scaleX = 1f + 0.04f * e
                scaleY = 1f + 0.04f * e
            }
            // Swallows every touch so nothing behind reacts; a tap mid-animation skips to the end.
            .pointerInput(Unit) {
                detectTapGestures {
                    if (clock.value < TOTAL_MS) scope.launch { clock.snapTo(TOTAL_MS) }
                }
            },
    ) {
        // Backdrop: the app's own background with a slow aurora and a few rising sparks.
        Canvas(Modifier.fillMaxSize()) {
            val p = EaseOutCubic.transform(clock.value.span(0f, BACKDROP_END))
            drawRect(background.copy(alpha = p))
            val blobs = listOf(
                Triple(accent, Offset(0.2f, 0.28f), 0.62f),
                Triple(lavender, Offset(0.82f, 0.42f), 0.58f),
                Triple(teal, Offset(0.35f, 0.82f), 0.55f),
            )
            blobs.forEachIndexed { i, (color, anchor, radiusFraction) ->
                val a = drift * (1f + i * 0.35f) + i * 2f
                val c = Offset(
                    size.width * (anchor.x + 0.08f * cos(a)),
                    size.height * (anchor.y + 0.05f * sin(a * 1.3f)),
                )
                val r = size.maxDimension * radiusFraction
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(color.copy(alpha = 0.26f * p), Color.Transparent),
                        center = c,
                        radius = r,
                    ),
                    radius = r,
                    center = c,
                )
            }
            if (!lowEnd) {
                val cycle = drift / (2 * PI).toFloat()
                for (i in 0 until 18) {
                    val x = ((i * 0.618f) % 1f) * size.width
                    val rise = (cycle * (0.6f + (i % 5) * 0.15f) + i * 0.37f) % 1f
                    val fade = sin(PI * rise).toFloat()
                    drawCircle(
                        color = (if (i % 3 == 0) accentLight else onSurface).copy(alpha = 0.32f * fade * p),
                        radius = (1.2f + (i % 3) * 0.6f).dp.toPx(),
                        center = Offset(x, size.height * (1f - rise)),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.hymn_intro_label).uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 4.sp),
                color = accentLight,
                modifier = Modifier.fadeUp { clock.value.span(LABEL_START, LABEL_START + FADE_DUR) },
            )

            Spacer(Modifier.height(8.dp))

            // The stage: glow, wave and both names share one spot, so one turns into the other.
            // Its letters are decorative; the title below is what TalkBack reads.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val g = EaseOutCubic.transform(clock.value.span(GLOW_START, GLOW_END))
                    if (g <= 0f) return@Canvas
                    val breathe = 0.92f + 0.08f * sin(drift * 3f)
                    val r = size.minDimension * 1.1f * breathe
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(accent.copy(alpha = 0.34f * g), lavender.copy(alpha = 0.10f * g), Color.Transparent),
                            center = center,
                            radius = r,
                        ),
                        radius = r,
                        center = center,
                    )
                }

                Canvas(
                    Modifier
                        .fillMaxWidth(0.9f)
                        .height(120.dp),
                ) {
                    val t = clock.value
                    val amp = EaseOutCubic.transform(t.span(WAVE_START, WAVE_PEAK)) *
                        (1f - EaseInOutCubic.transform(t.span(WAVE_FALL, WAVE_END)))
                    if (amp <= 0.001f) return@Canvas
                    val mid = size.height / 2f
                    val maxAmp = size.height * 0.4f
                    val steps = 80
                    waves.forEach { w ->
                        wavePath.reset()
                        for (step in 0..steps) {
                            val xn = step / steps.toFloat()
                            // Tapered ends, so the wave reads as a sound rather than a stripe.
                            val envelope = sin(PI * xn).toFloat().pow(1.6f)
                            val y = mid + sin(xn * w.cycles * 2f * PI.toFloat() + wavePhase * w.speed + w.offset) *
                                maxAmp * amp * envelope * w.scale
                            val x = xn * size.width
                            if (step == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
                        }
                        drawPath(
                            path = wavePath,
                            color = w.color.copy(alpha = w.alpha * amp),
                            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                }

                AnimatedWord(
                    text = stringResource(R.string.hymn_intro_old_name),
                    style = TextStyle(fontFamily = JakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 40.sp),
                    color = onSurfaceVariant,
                ) { i ->
                    val t = clock.value
                    val inStart = OLD_IN + i * OLD_IN_STAGGER
                    val inP = EaseOutCubic.transform(t.span(inStart, inStart + OLD_IN_DUR))
                    val outStart = OLD_OUT + i * OLD_OUT_STAGGER
                    val outP = EaseInCubic.transform(t.span(outStart, outStart + OLD_OUT_DUR))
                    alpha = inP * (1f - outP)
                    translationY = (1f - inP) * 14.dp.toPx() - outP * 26.dp.toPx()
                    val scale = 1f - 0.14f * outP
                    scaleX = scale
                    scaleY = scale
                    renderEffect = blurOrNull(blurAllowed, outP * 12.dp.toPx())
                }

                AnimatedWord(
                    text = stringResource(R.string.app_name),
                    style = TextStyle(
                        fontFamily = JakartaSans,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 76.sp,
                        letterSpacing = (-1.5).sp,
                    ),
                    color = onSurface,
                    modifier = Modifier
                        // Offscreen, so the gradient and shimmer below tint only the letters.
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.linearGradient(
                                    listOf(accentLight, accent, lavender),
                                    start = Offset.Zero,
                                    end = Offset(size.width, size.height),
                                ),
                                blendMode = BlendMode.SrcIn,
                            )
                            val sweep = clock.value.span(SHIMMER_START, SHIMMER_END)
                            if (sweep > 0f && sweep < 1f) {
                                val band = size.width * 0.35f
                                val x = -band + (size.width + 2 * band) * EaseInOutCubic.transform(sweep)
                                drawRect(
                                    brush = Brush.linearGradient(
                                        listOf(Color.Transparent, Color.White.copy(alpha = 0.7f), Color.Transparent),
                                        start = Offset(x - band / 2f, 0f),
                                        end = Offset(x + band / 2f, size.height),
                                    ),
                                    blendMode = BlendMode.SrcAtop,
                                )
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) { i ->
                    val start = NEW_IN + i * NEW_IN_STAGGER
                    val p = clock.value.span(start, start + NEW_IN_DUR)
                    val settle = EaseOutBack.transform(p)
                    val fade = EaseOutCubic.transform(p)
                    alpha = fade
                    translationY = (1f - settle) * 30.dp.toPx()
                    val scale = 1.22f - 0.22f * settle
                    scaleX = scale
                    scaleY = scale
                    renderEffect = blurOrNull(blurAllowed, (1f - fade) * 14.dp.toPx())
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.hymn_intro_title),
                style = MaterialTheme.typography.titleLarge,
                color = onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fadeUp { clock.value.span(TEXT_START, TEXT_START + FADE_DUR) },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.hymn_intro_body),
                style = MaterialTheme.typography.bodyMedium,
                color = onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .fadeUp { clock.value.span(TEXT_START + 120f, TEXT_START + 120f + FADE_DUR) },
            )

            Spacer(Modifier.height(36.dp))

            Box(
                modifier = Modifier
                    .fadeUp(distance = 18f) { clock.value.span(BUTTON_START, BUTTON_START + FADE_DUR) }
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(accent, accentLight)))
                    .clickable(enabled = buttonReady && !leaving, onClick = finish)
                    .padding(horizontal = 36.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.hymn_intro_cta),
                    style = MaterialTheme.typography.labelLarge,
                    color = onAccent,
                )
            }
        }
    }
}

/** A word drawn letter by letter, each with its own animated layer (index → transform). */
@Composable
private fun AnimatedWord(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    letter: GraphicsLayerScope.(index: Int) -> Unit,
) {
    Row(modifier) {
        text.forEachIndexed { i, ch ->
            Text(
                text = ch.toString(),
                style = style,
                color = color,
                modifier = Modifier.graphicsLayer { letter(i) },
            )
        }
    }
}

/** Fades and lifts content in as [progress] goes 0 → 1; read in the layer, not in composition. */
private fun Modifier.fadeUp(distance: Float = 12f, progress: () -> Float): Modifier = graphicsLayer {
    val p = EaseOutCubic.transform(progress())
    alpha = p
    translationY = (1f - p) * distance.dp.toPx()
}

private fun blurOrNull(allowed: Boolean, radiusPx: Float) =
    if (allowed && radiusPx > 0.5f) BlurEffect(radiusPx, radiusPx, TileMode.Decal) else null
