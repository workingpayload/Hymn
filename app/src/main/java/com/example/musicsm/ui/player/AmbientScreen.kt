package com.example.musicsm.ui.player

import android.app.Activity
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.musicsm.R
import com.example.musicsm.ui.components.ArtworkImage
import com.example.musicsm.ui.components.ArtworkSize
import com.example.musicsm.ui.components.MixArtOverlay
import com.example.musicsm.ui.components.MixArtState
import com.example.musicsm.ui.components.rememberMixArtState
import com.example.musicsm.ui.components.rememberMixedAccent
import com.example.musicsm.ui.components.accentColorFor
import com.example.musicsm.ui.components.currentLocale
import com.example.musicsm.ui.components.rememberDominantColorState
import com.example.musicsm.ui.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

/** How long the transport controls stay up after a tap before fading away again. */
private const val CONTROLS_VISIBLE_MS = 5_000L

/** Screen brightness while ambient mode is showing; a docked phone at night shouldn't glare. */
private const val AMBIENT_BRIGHTNESS = 0.35f

/**
 * Cool and warm poles the artwork colour is pulled towards for the two secondary aurora blobs.
 *
 * Fixed rather than theme-derived on purpose: ambient mode is always a dark, night-time surface,
 * so it must not turn pale when the app is in its light theme. Blending *from* the artwork colour
 * keeps the result tied to whatever is playing instead of looking like a stock wallpaper.
 */
private val AuroraCoolPole = Color(0xFF6A5AE0)
private val AuroraWarmPole = Color(0xFF14B8A6)


/**
 * Chromecast-style "now playing" screensaver for a docked or idle phone.
 *
 * Four things make this a screensaver rather than just a big player:
 *
 * 1. **The screen stays on.** `FLAG_KEEP_SCREEN_ON` is held for exactly as long as this composable
 *    is on screen, and released on the way out, so it can never leak into the rest of the app.
 * 2. **Burn-in protection.** OLED panels retain static bright pixels, so every layer drifts slowly
 *    around its own wide ellipse. It is deliberately too slow to notice while watching, and far
 *    too large for any pixel to stay lit.
 * 3. **It gets out of the way.** System bars are hidden and the controls fade out; a tap brings
 *    them back for a few seconds.
 * 4. **It is lit by the music.** The backdrop is an aurora of slow-moving colour blobs derived
 *    from the artwork, so the room glows in the colour of whatever is playing.
 *
 * Every animated value is kept as a [State] and read inside `graphicsLayer`/draw lambdas rather
 * than in composition. These animations run continuously for as long as the screensaver is up, so
 * a composition-phase read would re-run this whole tree every frame, all night.
 */
@Composable
fun AmbientScreen(
    viewModel: PlayerViewModel,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val song = state.currentSong
    val context = LocalContext.current

    var controlsVisible by remember { mutableStateOf(true) }
    // Bumped on every tap; re-launches the auto-hide timer from scratch.
    var lastInteraction by remember { mutableLongStateOf(0L) }

    BackHandler { onExit() }

    LaunchedEffect(lastInteraction) {
        controlsVisible = true
        delay(CONTROLS_VISIBLE_MS)
        controlsVisible = false
    }

    // Keep the screen awake, go edge-to-edge and dim the panel — all strictly scoped to this
    // screen, with every window attribute restored on exit.
    DisposableEffect(context) {
        val activity = context as? Activity
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        val previousBrightness = window?.attributes?.screenBrightness

        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window?.attributes = window?.attributes?.apply { screenBrightness = AMBIENT_BRIGHTNESS }
        controller?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.attributes = window?.attributes?.apply {
                screenBrightness = previousBrightness
                    ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val currentAccent = rememberDominantColorState(
        url = song?.artworkUrl,
        fallback = accentColorFor(song?.id ?: song?.title),
    )
    // Artwork, glow and aurora all follow a crossfade/Mix into the next track.
    val mixBlend by viewModel.mixBlend.collectAsStateWithLifecycle()
    val mixArt = rememberMixArtState(
        blend = mixBlend,
        currentSongId = song?.id,
        positionFlow = viewModel.position,
        isPlaying = state.isPlaying,
    )
    val accent = rememberMixedAccent(currentAccent, mixArt)
    val cassette by viewModel.ambientCassette.collectAsStateWithLifecycle()

    // Deliberately mismatched, mutually prime-ish periods: the layers never line back up, so the
    // composition keeps changing instead of visibly looping.
    val drift = rememberInfiniteTransition(label = "ambientDrift")
    val driftPhase = drift.orbit(120_000, "ambientDriftPhase")
    val auroraSlow = drift.orbit(97_000, "auroraSlow")
    val auroraFast = drift.orbit(61_000, "auroraFast")

    // A long, gentle in-and-out, so the artwork looks like it is breathing rather than pulsing.
    val breath = drift.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(11_000, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "ambientBreath",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { lastInteraction = System.currentTimeMillis() },
                    onDoubleTap = { onExit() },
                )
            },
    ) {
        // Blurred artwork wash, scaled up so the drift never exposes an edge.
        val washDrift = Modifier.graphicsLayer {
            // Slow zoom as well as pan, so the wash never settles into a still frame.
            val zoom = 1.3f + breath.value * 0.06f
            scaleX = zoom
            scaleY = zoom
            translationX = cos(driftPhase.value) * 40f
            translationY = sin(driftPhase.value) * 40f
        }
        if (!song?.artworkUrl.isNullOrEmpty()) {
            AsyncImage(
                model = song?.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(washDrift)
                    .graphicsLayer { alpha = 0.55f }
                    .blur(90.dp)
                    .drawBehind { drawRect(Color.Black) },
            )
        }
        MixArtOverlay(
            state = mixArt,
            targetSizePx = ArtworkSize.TILE,
            modifier = Modifier
                .fillMaxSize()
                .then(washDrift)
                .graphicsLayer { alpha = 0.55f }
                .blur(90.dp)
                .drawBehind { drawRect(Color.Black) },
        )

        AuroraBackdrop(
            accent = accent,
            slow = auroraSlow,
            fast = auroraFast,
            drift = driftPhase,
            modifier = Modifier.fillMaxSize(),
        )

        // Vignette + floor scrim: this is a night-time surface and the text must stay readable
        // over any artwork, however bright.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.42f),
                            0.45f to Color.Black.copy(alpha = 0.20f),
                            1f to Color.Black.copy(alpha = 0.72f),
                        ),
                    )
                    drawRect(
                        Brush.radialGradient(
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.55f),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = maxOf(size.width, size.height) * 0.75f,
                        ),
                    )
                },
        )

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            val driftAmplitude = 16f

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // The burn-in orbit. Applied to the content layer only, so the backdrop
                        // and this move independently and no edge ever lines up twice. The
                        // multipliers are whole numbers so the path is still continuous where the
                        // animation restarts at 2π — a fractional one would snap.
                        translationX = cos(driftPhase.value) * driftAmplitude
                        translationY = sin(driftPhase.value * 3f) * driftAmplitude
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (landscape) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(56.dp),
                    ) {
                        Crossfade(
                            targetState = cassette,
                            animationSpec = tween(600),
                            label = "ambientCenterpiece",
                        ) { showCassette ->
                            if (showCassette) {
                                AmbientCassette(
                                    url = song?.artworkUrl,
                                    title = song?.title,
                                    artist = song?.artist,
                                    isPlaying = state.isPlaying,
                                    positionFlow = viewModel.position,
                                    durationMs = state.durationMs,
                                    accent = accent,
                                    breath = breath,
                                    mixArt = mixArt,
                                    modifier = Modifier.fillMaxHeight(0.66f).aspectRatio(CASSETTE_ASPECT),
                                )
                            } else {
                                AmbientArtwork(
                                    url = song?.artworkUrl,
                                    accent = accent,
                                    breath = breath,
                                    mixArt = mixArt,
                                    modifier = Modifier.fillMaxHeight(0.78f).aspectRatio(1f),
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            // In landscape the centrepiece fills most of the height, so a corner
                            // clock would sit on top of it. Head the text column with it instead.
                            AmbientClock(
                                timeSize = 60.sp,
                                modifier = Modifier.graphicsLayer {
                                    translationX = cos(driftPhase.value * 2f) * 8f
                                    translationY = sin(driftPhase.value) * 8f
                                },
                            )
                            Spacer(Modifier.height(20.dp))
                            AmbientText(
                                title = song?.title,
                                artist = song?.artist,
                                modifier = Modifier.fillMaxWidth(),
                                alignment = Alignment.Start,
                            )
                            Spacer(Modifier.height(28.dp))
                            AmbientProgress(
                                positionFlow = viewModel.position,
                                durationMs = state.durationMs,
                                accent = accent,
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Crossfade(
                            targetState = cassette,
                            animationSpec = tween(600),
                            label = "ambientCenterpiece",
                        ) { showCassette ->
                            if (showCassette) {
                                AmbientCassette(
                                    url = song?.artworkUrl,
                                    title = song?.title,
                                    artist = song?.artist,
                                    isPlaying = state.isPlaying,
                                    positionFlow = viewModel.position,
                                    durationMs = state.durationMs,
                                    accent = accent,
                                    breath = breath,
                                    mixArt = mixArt,
                                    modifier = Modifier.fillMaxWidth(0.98f).aspectRatio(CASSETTE_ASPECT),
                                )
                            } else {
                                AmbientArtwork(
                                    url = song?.artworkUrl,
                                    accent = accent,
                                    breath = breath,
                                    mixArt = mixArt,
                                    modifier = Modifier.fillMaxWidth(0.70f).aspectRatio(1f),
                                )
                            }
                        }
                        Spacer(Modifier.height(44.dp))
                        AmbientText(
                            title = song?.title,
                            artist = song?.artist,
                            modifier = Modifier.fillMaxWidth(),
                            alignment = Alignment.CenterHorizontally,
                        )
                        Spacer(Modifier.height(32.dp))
                        AmbientProgress(
                            positionFlow = viewModel.position,
                            durationMs = state.durationMs,
                            accent = accent,
                        )
                    }
                }
            }

            if (!landscape) {
                AmbientClock(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(36.dp)
                        .graphicsLayer {
                            // The clock is the brightest static element, so give it its own orbit.
                            translationX = cos(driftPhase.value * 2f) * 20f
                            translationY = sin(driftPhase.value) * 20f
                        },
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(800)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            AmbientControls(
                isPlaying = state.isPlaying,
                onPrevious = {
                    lastInteraction = System.currentTimeMillis()
                    viewModel.previous()
                },
                onPlayPause = {
                    lastInteraction = System.currentTimeMillis()
                    viewModel.togglePlayPause()
                },
                onNext = {
                    lastInteraction = System.currentTimeMillis()
                    viewModel.next()
                },
                showingCassette = cassette,
                onToggleCassette = {
                    lastInteraction = System.currentTimeMillis()
                    viewModel.setAmbientCassette(!cassette)
                },
                onExit = onExit,
                modifier = Modifier.padding(bottom = 52.dp),
            )
        }
    }
}

/** A phase that sweeps 0 → 2π every [periodMs], for driving a circular drift. */
@Composable
private fun androidx.compose.animation.core.InfiniteTransition.orbit(
    periodMs: Int,
    label: String,
): State<Float> = animateFloat(
    initialValue = 0f,
    targetValue = (2 * Math.PI).toFloat(),
    animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart),
    label = label,
)

/**
 * Three soft colour blobs drifting behind everything else, lit from the artwork.
 *
 * Radial gradients are used rather than blurred shapes because the softness comes free from the
 * shader — no render-effect pass, which matters for something that animates all night on a phone
 * that may also be charging. Alphas stay low: this has to read as a glow in a dark room, not as a
 * wallpaper.
 */
@Composable
private fun AuroraBackdrop(
    accent: State<Color>,
    slow: State<Float>,
    fast: State<Float>,
    drift: State<Float>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val base = accent.value

        fun blob(color: Color, alpha: Float, cx: Float, cy: Float, radius: Float) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
                    center = Offset(cx, cy),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(cx, cy),
            )
        }

        blob(
            color = base,
            alpha = 0.38f,
            cx = w * 0.5f + cos(slow.value) * w * 0.30f,
            cy = h * 0.34f + sin(slow.value * 0.7f) * h * 0.16f,
            radius = maxOf(w, h) * 0.62f,
        )
        blob(
            color = lerp(base, AuroraCoolPole, 0.55f),
            alpha = 0.30f,
            cx = w * 0.22f + cos(fast.value * 0.8f) * w * 0.26f,
            cy = h * 0.72f + sin(fast.value) * h * 0.18f,
            radius = maxOf(w, h) * 0.52f,
        )
        blob(
            color = lerp(base, AuroraWarmPole, 0.45f),
            alpha = 0.24f,
            cx = w * 0.82f + cos(drift.value * 1.6f) * w * 0.22f,
            cy = h * 0.62f + sin(drift.value * 2f) * h * 0.20f,
            radius = maxOf(w, h) * 0.46f,
        )
    }
}

/**
 * The artwork, sitting in a pool of its own colour.
 *
 * The glow replaces a plain drop shadow: a black shadow on a dark backdrop reads as a smudge,
 * whereas a bloom in the artwork's own colour makes the sleeve look lit from behind. Its radius is
 * wider than the sleeve and nothing in the chain clips, so it deliberately bleeds past the edges.
 */
@Composable
private fun AmbientArtwork(
    url: String?,
    accent: State<Color>,
    breath: State<Float>,
    mixArt: MixArtState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.graphicsLayer {
            val scale = 1f + breath.value * 0.022f
            scaleX = scale
            scaleY = scale
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val glow = accent.value
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glow.copy(alpha = 0.55f),
                                glow.copy(alpha = 0.18f),
                                glow.copy(alpha = 0f),
                            ),
                            center = Offset(size.width / 2f, size.height * 0.56f),
                            radius = size.minDimension * 0.92f,
                        ),
                        radius = size.minDimension * 0.92f,
                        center = Offset(size.width / 2f, size.height * 0.56f),
                    )
                },
        )
        // Crossfade so a track change dissolves rather than snapping — the single biggest
        // difference between this feeling like a screensaver and like a slideshow.
        Crossfade(targetState = url, animationSpec = tween(900), label = "ambientArtwork") { art ->
            ArtworkImage(
                url = art,
                shape = RoundedCornerShape(24.dp),
                targetSizePx = ArtworkSize.HERO,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(24.dp)),
            )
        }
        // A crossfade/Mix into the next track dissolves the sleeve with the audio.
        MixArtOverlay(
            state = mixArt,
            targetSizePx = ArtworkSize.HERO,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(24.dp)),
        )
    }
}

@Composable
private fun AmbientText(
    title: String?,
    artist: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal = Alignment.CenterHorizontally,
) {
    val textAlign = if (alignment == Alignment.Start) TextAlign.Start else TextAlign.Center
    Column(modifier = modifier, horizontalAlignment = alignment) {
        Crossfade(targetState = title, animationSpec = tween(700), label = "ambientTitle") { value ->
            Text(
                value ?: stringResource(R.string.ambient_nothing_playing),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                // Large display type sets too loosely by default; pulling it in reads as editorial.
                letterSpacing = (-0.5).sp,
                lineHeight = 40.sp,
                color = Color.White,
                textAlign = textAlign,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (!artist.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Crossfade(targetState = artist, animationSpec = tween(700), label = "ambientArtist") { value ->
                Text(
                    value.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    // Wide tracking on a dimmed, uppercased credit is the classic "sleeve note"
                    // treatment, and it separates the artist from the title without a divider.
                    letterSpacing = 3.sp,
                    color = Color.White.copy(alpha = 0.62f),
                    textAlign = textAlign,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * A hairline progress line with the elapsed and total times either side.
 *
 * Deliberately not interactive: this is a screensaver, and a draggable control would invite
 * touching the screen that is meant to be left alone. It is thin and dim so it informs without
 * becoming another bright static element on an OLED panel.
 */
@Composable
private fun AmbientProgress(
    positionFlow: StateFlow<Long>,
    durationMs: Long,
    accent: State<Color>,
    modifier: Modifier = Modifier,
) {
    if (durationMs <= 0L) return
    // Collected here in the leaf so the ~1s position updates recompose only this thin line, not the
    // whole screensaver (whose continuous aurora/breath animations read their State in draw phase).
    val positionMs by positionFlow.collectAsStateWithLifecycle()
    val progress = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .drawBehind {
                    drawRect(Color.White.copy(alpha = 0.16f))
                    drawRect(
                        color = accent.value.copy(alpha = 0.85f),
                        size = size.copy(width = size.width * progress.coerceIn(0f, 1f)),
                    )
                },
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AmbientTimeLabel(formatDuration(positionMs))
            AmbientTimeLabel(formatDuration(durationMs))
        }
    }
}

@Composable
private fun AmbientTimeLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 1.sp,
        color = Color.White.copy(alpha = 0.45f),
    )
}

/** Transport controls floating in a frosted pill, matching the app's glass language. */
@Composable
private fun AmbientControls(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    showingCassette: Boolean,
    onToggleCassette: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.Filled.SkipPrevious,
                contentDescription = stringResource(R.string.player_previous),
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(32.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onPlayPause, modifier = Modifier.size(62.dp)) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(
                        if (isPlaying) R.string.action_pause else R.string.action_play,
                    ),
                    tint = Color.White,
                    modifier = Modifier.size(38.dp),
                )
            }
        }
        IconButton(onClick = onNext) {
            Icon(
                Icons.Filled.SkipNext,
                contentDescription = stringResource(R.string.player_next),
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.width(2.dp))
        IconButton(onClick = onToggleCassette) {
            if (showingCassette) {
                Icon(
                    Icons.Filled.Album,
                    contentDescription = stringResource(R.string.ambient_show_cover),
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Icon(
                    painterResource(R.drawable.ic_cassette),
                    contentDescription = stringResource(R.string.ambient_show_cassette),
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        IconButton(onClick = onExit) {
            Icon(
                Icons.Filled.CloseFullscreen,
                contentDescription = stringResource(R.string.ambient_exit),
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** Big, dim clock — the part you actually read from across a room. */
@Composable
private fun AmbientClock(modifier: Modifier = Modifier, timeSize: TextUnit = 76.sp) {
    val context = LocalContext.current
    val locale = currentLocale()
    // Respect the system 12/24-hour setting and locale rather than forcing "h:mm".
    val formatter = remember(locale, context) {
        val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    }
    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale)
    }
    var now by remember { mutableStateOf(LocalTime.now()) }
    var today by remember { mutableStateOf(LocalDate.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            today = LocalDate.now()
            // Re-sync on the minute boundary instead of ticking every second.
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }

    Column(modifier = modifier) {
        Text(
            now.format(formatter),
            fontSize = timeSize,
            lineHeight = timeSize * 1.05f,
            fontWeight = FontWeight.ExtraLight,
            // Tightened tracking stops a thin, very large face from looking loose and cheap.
            letterSpacing = (-2).sp,
            color = Color.White.copy(alpha = 0.88f),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            today.format(dateFormatter).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 3.sp,
            color = Color.White.copy(alpha = 0.45f),
        )
    }
}
