package com.example.musicsm.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicsm.ui.theme.CoralLight
import com.example.musicsm.ui.theme.onTint

/**
 * An album or artist page while it loads, built from what was already known when it was opened:
 * the same full-bleed cover and title the loaded page starts with, then placeholder action pills
 * and track rows. The real page replaces it in place, so the header doesn't jump.
 *
 * @param tint the page background colour taken from the cover, which the title contrasts against.
 * @param rowArtwork placeholder rows carry a cover square (artist top songs) rather than a number.
 */
@Composable
fun DetailLoadingPreview(
    artworkUrl: String?,
    title: String,
    subtitle: String,
    tint: Color,
    rowArtwork: Boolean,
    modifier: Modifier = Modifier,
) {
    val progress = rememberShimmerProgress()
    Column(modifier = modifier.fillMaxSize()) {
        if (artworkUrl.isNullOrEmpty()) {
            Spacer(Modifier.statusBarsPadding().height(88.dp))
        } else {
            HeroArtwork(url = artworkUrl)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = tint.onTint(emphasis = 1f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                SkeletonBlock(progress = progress, modifier = Modifier.width(200.dp).height(28.dp))
            }
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = CoralLight,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SkeletonBlock(progress = progress, shape = CircleShape, modifier = Modifier.weight(1f).height(48.dp))
                SkeletonBlock(progress = progress, shape = CircleShape, modifier = Modifier.weight(1f).height(48.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        repeat(PLACEHOLDER_ROWS) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (rowArtwork) {
                    SkeletonBlock(progress = progress, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(48.dp))
                } else {
                    SkeletonBlock(progress = progress, modifier = Modifier.width(18.dp).height(14.dp))
                }
                Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                    SkeletonBlock(progress = progress, modifier = Modifier.fillMaxWidth(0.62f).height(14.dp))
                    Spacer(Modifier.height(6.dp))
                    SkeletonBlock(progress = progress, modifier = Modifier.fillMaxWidth(0.38f).height(12.dp))
                }
            }
        }
    }
}

private const val PLACEHOLDER_ROWS = 6
