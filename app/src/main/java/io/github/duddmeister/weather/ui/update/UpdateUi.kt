package io.github.duddmeister.weather.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.duddmeister.weather.ui.components.WideButton
import io.github.duddmeister.weather.ui.sky.LocalSkyBackdrop
import io.github.duddmeister.weather.ui.theme.SkyText
import io.github.duddmeister.weather.update.ReleaseInfo
import io.github.duddmeister.weather.update.UpdateState
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SheetColor = Color(0xFF111B36)

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
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = SheetColor,
        contentColor = Color.White,
    ) {
        // The sheet is a window of its own: the sky isn't behind it for glass to borrow.
        CompositionLocalProvider(LocalSkyBackdrop provides null) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.NewReleases, contentDescription = null, tint = SkyText.Warning, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(if (release != null) "Nimbus ${release.version}" else "Updates", style = MaterialTheme.typography.headlineSmall)
                        release?.publishedAt?.let { published ->
                            val date = runCatching {
                                OffsetDateTime.parse(published).format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))
                            }.getOrNull()
                            if (date != null) Text("Published $date", style = MaterialTheme.typography.bodySmall, color = SkyText.Secondary)
                        }
                    }
                }
                if (release != null) {
                    Spacer(Modifier.height(20.dp))
                    Text("WHAT'S NEW", style = MaterialTheme.typography.labelMedium, color = SkyText.Tertiary)
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                        ReleaseNotes(release.notes)
                    }
                }
                Spacer(Modifier.height(22.dp))
                when (state) {
                    is UpdateState.Available -> WideButton("Update now", onUpdate)
                    is UpdateState.Downloading -> {
                        val progress = state.progress
                        Progress(progress)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Downloading" + (progress?.let { " ${(it * 100).toInt()}%" } ?: "…"),
                            style = MaterialTheme.typography.bodySmall,
                            color = SkyText.Secondary,
                        )
                    }
                    is UpdateState.Installing -> {
                        Progress(null)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Installing. If Android asks, tap Update. Nimbus will close and the new version will be ready to open.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SkyText.Secondary,
                        )
                    }
                    is UpdateState.Failed -> {
                        Text(state.message, style = MaterialTheme.typography.bodyMedium, color = SkyText.Warning)
                        Spacer(Modifier.height(14.dp))
                        if (release != null) WideButton("Try again", onUpdate) else WideButton("Check again", onCheck)
                    }
                    is UpdateState.UpToDate -> Text("You're on the latest version.", style = MaterialTheme.typography.bodyMedium, color = SkyText.Secondary)
                    UpdateState.Checking -> Progress(null)
                    UpdateState.Idle -> WideButton("Check for updates", onCheck)
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onOpenReleases, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("View all releases on GitHub", style = MaterialTheme.typography.labelLarge, color = SkyText.Secondary)
                }
            }
        }
    }
}

@Composable
private fun Progress(progress: Float?) {
    val modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
    if (progress == null) {
        LinearProgressIndicator(modifier, color = Color.White, trackColor = Color.White.copy(alpha = 0.16f))
    } else {
        LinearProgressIndicator(progress = { progress }, modifier = modifier, color = Color.White, trackColor = Color.White.copy(alpha = 0.16f), drawStopIndicator = {})
    }
}

/** Release notes are Markdown; show them as readable text, with headings and bullets set apart. */
@Composable
private fun ReleaseNotes(notes: String) {
    val lines = noteLines(notes)
    Column {
        lines.forEach { line ->
            when (line) {
                is NoteLine.Heading -> Text(line.text, style = MaterialTheme.typography.titleSmall, color = Color.White, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                is NoteLine.Bullet -> Row(Modifier.padding(vertical = 3.dp)) {
                    Box(Modifier.padding(top = 8.dp).size(5.dp).clip(CircleShape).background(SkyText.Tertiary))
                    Spacer(Modifier.width(12.dp))
                    Text(line.text, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
                is NoteLine.Plain -> Text(line.text, style = MaterialTheme.typography.bodyMedium, color = SkyText.Secondary, modifier = Modifier.padding(vertical = 3.dp))
            }
        }
    }
}

internal sealed interface NoteLine {
    val text: String

    data class Heading(override val text: String) : NoteLine
    data class Bullet(override val text: String) : NoteLine
    data class Plain(override val text: String) : NoteLine
}

internal fun noteLines(notes: String): List<NoteLine> {
    val lines = notes.lines()
        .map { it.trim().replace("**", "") }
        .filter { it.isNotEmpty() && !it.startsWith("Full Changelog") }
        // Every release ends with how to install it; that's not news to someone already running Nimbus.
        .takeWhile { !it.equals("## Install", ignoreCase = true) }
        .filterNot { it.equals("## What's new", ignoreCase = true) }
        .map { line ->
            when {
                line.startsWith("#") -> NoteLine.Heading(line.trimStart('#').trim())
                line.startsWith("- ") || line.startsWith("* ") -> NoteLine.Bullet(line.drop(2).trim())
                else -> NoteLine.Plain(line)
            }
        }
    return lines.ifEmpty { listOf(NoteLine.Plain("Bug fixes and improvements.")) }
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
        containerColor = SheetColor,
        titleContentColor = Color.White,
        textContentColor = SkyText.Secondary,
    )
}
