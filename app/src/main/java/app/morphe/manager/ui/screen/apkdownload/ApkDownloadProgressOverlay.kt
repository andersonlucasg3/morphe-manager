/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.apkdownload

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.AppDialogButton
import app.morphe.manager.ui.screen.shared.Overlay
import app.morphe.manager.ui.screen.shared.PulsingLogoIndicator
import app.morphe.manager.ui.screen.shared.SemanticTone
import app.morphe.manager.ui.screen.shared.StatusBadge
import app.morphe.manager.ui.screen.shared.rememberAccessibilityEnabled
import app.morphe.manager.util.formatBytes

/**
 * Shows the captured APK being fetched, and lets it be stopped.
 *
 * Separate from the dialog that starts the download on purpose: that dialog is dismissed as soon
 * as the file leaves the page, while the transfer it began runs on for minutes on a slow
 * connection. This is drawn over whatever screen the user moved on to.
 */
@Composable
fun ApkDownloadProgressOverlay(onCancel: () -> Unit) {
    val progress by ApkCaptureDownloader.progress.collectAsStateWithLifecycle()
    val current = progress ?: return

    val context = LocalContext.current
    val downloaded = remember(current.bytesDownloaded) {
        context.formatBytes(current.bytesDownloaded)
    }
    val total = remember(current.totalBytes) {
        current.totalBytes?.let { context.formatBytes(it) }
    }
    val speed = remember(current.bytesPerSecond) {
        // Below a kilobyte a second the rate is noise, and "0 B/s" reads as stalled
        current.bytesPerSecond.takeIf { it > 1024 }?.let { context.formatBytes(it) + "/s" }
    }

    // Fully opaque: nothing of the screen behind shows through. `Overlay` defaults to a partial
    // scrim, which leaves the app legible underneath and reads as a dialog over it; the download
    // takes over instead, so the theme background is drawn solid.
    Overlay(visible = true, backgroundAlpha = 1f) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PulsingLogoIndicator(
                size = 96.dp,
                contentDescription = stringResource(R.string.apk_download_in_progress),
            )

            Text(
                text = stringResource(R.string.apk_download_title, current.appName),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            // A percentage needs a total, and a server that sends none has not given one
            current.fraction?.let { fraction ->
                StatusBadge(
                    text = stringResource(R.string.patcher_percentage, (fraction * 100).toInt()),
                    tone = SemanticTone.Primary,
                )

                DownloadProgressBar(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                text = when {
                    total != null && speed != null -> stringResource(
                        R.string.apk_download_of_total_with_speed,
                        downloaded,
                        total,
                        speed
                    )
                    total != null -> stringResource(R.string.apk_download_of_total, downloaded, total)
                    else -> downloaded
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            current.remainingSeconds?.takeIf { it > 0 }?.let { seconds ->
                Text(
                    text = stringResource(R.string.apk_download_remaining, formatRemaining(seconds)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                )
            }

            AppDialogButton(
                text = stringResource(R.string.apk_capture_cancel),
                onClick = onCancel,
                icon = MaterialSymbols.Rounded.Close,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Time left as a rounded reading rather than a precise one. A download's rate moves constantly,
 * and "1m 47s" would claim an accuracy the estimate does not have.
 */
@Composable
private fun formatRemaining(seconds: Long): String = when {
    seconds < 60 -> stringResource(R.string.apk_download_remaining_seconds, seconds)
    seconds < 3600 -> stringResource(R.string.apk_download_remaining_minutes, (seconds + 30) / 60)
    else -> stringResource(R.string.apk_download_remaining_hours, (seconds + 1800) / 3600)
}

private val DownloadBarStrokeWidth = 6.dp

/** Height of a [DownloadProgressBar], with room above and below its stroke for the wave. */
private val DownloadBarHeight = 16.dp

/**
 * Progress along a line, drawn in the theme's primary with the wave the rest of the app uses.
 *
 * Kept here rather than taken from a shared helper: this screen is its only caller, so the fork
 * owns the few lines and cannot be broken by the app's own progress components being renamed or
 * removed upstream, as the shared `WavyProgressBar` was in v2.8.0.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadProgressBar(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest
) {
    val stroke = with(LocalDensity.current) {
        Stroke(width = DownloadBarStrokeWidth.toPx(), cap = StrokeCap.Round)
    }
    // Flat while accessibility services are on, which is how the app's other progress reads then
    val waveHeight = if (rememberAccessibilityEnabled()) 0f else 1f

    LinearWavyProgressIndicator(
        progress = progress,
        modifier = modifier.height(DownloadBarHeight),
        color = MaterialTheme.colorScheme.primary,
        trackColor = trackColor,
        stroke = stroke,
        trackStroke = stroke,
        // No stop dot at the end of the track, which the ring has no counterpart of
        stopSize = 0.dp,
        amplitude = { WavyProgressIndicatorDefaults.indicatorAmplitude(it) * waveHeight }
    )
}
