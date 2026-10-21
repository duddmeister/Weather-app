package io.github.duddmeister.weather.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.duddmeister.weather.ui.theme.SkyText
import io.github.duddmeister.weather.update.ReleaseInfo
import io.github.duddmeister.weather.update.UpdateState
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun UpdateSheet(
    state: UpdateState,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
    onCheck: () -> Unit,
    onOpenReleases: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val release: ReleaseInfo? = when (state) {
        is UpdateState.Available -> state.release
        is UpdateState.Downloading -> state.release
        is UpdateState.Installing -> state.release
        is UpdateState.Failed -> state.release
        else -> null
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF14203A),
        contentColor = Color.White,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            Text(
                if (release != null) "Nimbus ${release.version}" else "Updates",
                style = MaterialTheme.typography.headlineSmall,
            )
            release?.publishedAt?.let { published ->
                val date = runCatching {
                    OffsetDateTime.parse(published).format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))
                }.getOrNull()
                if (date != null) Text("Published $date", style = MaterialTheme.typography.bodySmall, color = SkyText.Secondary)
            }
            if (release != null) {
                Spacer(Modifier.height(16.dp))
                Text("WHAT'S NEW", style = MaterialTheme.typography.labelMedium, color = SkyText.Tertiary)
                Spacer(Modifier.height(6.dp))
                Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    Text(cleanNotes(release.notes), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
            }
            Spacer(Modifier.height(20.dp))
            when (state) {
                is UpdateState.Available -> PrimaryButton("Update now", onUpdate)
                is UpdateState.Downloading -> {
                    val progress = state.progress
                    if (progress == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color.White, trackColor = Color.White.copy(alpha = 0.2f))
                    } else {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.2f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Downloading" + (progress?.let { " ${(it * 100).toInt()}%" } ?: "…"),
                        style = MaterialTheme.typography.bodySmall,
                        color = SkyText.Secondary,
                    )
                }
                is UpdateState.Installing -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color.White, trackColor = Color.White.copy(alpha = 0.2f))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Installing. If Android asks, tap Update. Nimbus will close and the new version will be ready to open.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SkyText.Secondary,
                    )
                }
                is UpdateState.Failed -> {
                    Text(state.message, style = MaterialTheme.typography.bodyMedium, color = SkyText.Warning)
                    Spacer(Modifier.height(12.dp))
                    if (release != null) PrimaryButton("Try again", onUpdate) else PrimaryButton("Check again", onCheck)
                }
                is UpdateState.UpToDate -> Text("You're on the latest version.", style = MaterialTheme.typography.bodyMedium, color = SkyText.Secondary)
                UpdateState.Checking -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color.White, trackColor = Color.White.copy(alpha = 0.2f))
                UpdateState.Idle -> PrimaryButton("Check for updates", onCheck)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onOpenReleases) { Text("View all releases on GitHub", color = Color.White) }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0F3E7A)),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** Release notes are Markdown; show them as readable plain text. */
private fun cleanNotes(notes: String): String {
    if (notes.isBlank()) return "Bug fixes and improvements."
    return notes.lines()
        .map { it.trimEnd() }
        .filterNot { it.startsWith("**Full Changelog**") }
        .joinToString("\n") { line ->
            when {
                line.startsWith("#") -> line.trimStart('#').trim()
                line.startsWith("- ") || line.startsWith("* ") -> "•  " + line.drop(2)
                else -> line
            }
        }
        .replace("**", "")
        .trim()
}

@Composable
fun InstallPermissionDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Allow Nimbus to install updates") },
        text = {
            Text(
                "Android needs your OK before an app can install its own updates. On the next screen, turn on " +
                    "\"Allow from this source\", then come back and the update will continue.",
            )
        },
        confirmButton = { TextButton(onClick = onOpenSettings) { Text("Open settings") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
        containerColor = Color(0xFF14203A),
        titleContentColor = Color.White,
        textContentColor = SkyText.Secondary,
    )
}
