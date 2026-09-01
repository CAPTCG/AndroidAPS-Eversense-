package app.aaps.ui.compose.overview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.objects.extensions.tickerFlow
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.ui.R

/** 15 minutes, matching the official Eversense app's post-calibration countdown bar. */
private const val CALIBRATION_COUNTDOWN_MS = 15 * 60 * 1000L

/**
 * Home-screen banner mirroring the official Eversense app's post-calibration countdown bar (see
 * the feature request's reference screenshot). Renders nothing once [submittedAtMs] is more than
 * 15 minutes old, or is 0 (the never-submitted default) — callers don't need a separate "is
 * Eversense in use" gate: EversenseLastCalibrationSubmittedAt is only ever written on a successful
 * calibration submission (both the Settings -> Eversense -> Calibration screen and the
 * quick-launch "Eversense Calibration" dialog write it), so a fresh value already implies
 * Eversense is the one in use.
 */
@Composable
fun EversenseCalibrationBanner(
    submittedAtMs: Long,
    modifier: Modifier = Modifier
) {
    var now by remember(submittedAtMs) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(submittedAtMs) {
        tickerFlow(1_000L).collect { now = System.currentTimeMillis() }
    }

    val remainingMs = submittedAtMs + CALIBRATION_COUNTDOWN_MS - now
    val visible = submittedAtMs > 0 && remainingMs > 0

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val totalSeconds = (remainingMs / 1000).coerceAtLeast(0).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val countdownText = "$minutes:${seconds.toString().padStart(2, '0')}"

        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(AapsSpacing.medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small)
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.eversense_calibration_in_progress, countdownText),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

// --- Previews ---

@Preview(showBackground = true)
@Composable
private fun EversenseCalibrationBannerPreview() {
    MaterialTheme {
        Surface {
            EversenseCalibrationBanner(submittedAtMs = System.currentTimeMillis() - 45_000L)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EversenseCalibrationBannerHiddenPreview() {
    MaterialTheme {
        Surface {
            EversenseCalibrationBanner(submittedAtMs = 0L)
        }
    }
}
