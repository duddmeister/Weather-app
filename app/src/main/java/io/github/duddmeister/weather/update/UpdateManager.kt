package io.github.duddmeister.weather.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import io.github.duddmeister.weather.BuildConfig
import io.github.duddmeister.weather.core.AppJson
import io.github.duddmeister.weather.core.AppLog
import io.github.duddmeister.weather.data.local.SettingsStore
import io.github.duddmeister.weather.data.remote.GitHubRelease
import io.github.duddmeister.weather.data.remote.GitHubReleasesClient
import io.github.duddmeister.weather.data.remote.LatestReleaseResult
import io.github.duddmeister.weather.notify.Notifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ReleaseInfo(
    val version: String,
    val tag: String,
    val title: String,
    val notes: String,
    val publishedAt: String?,
    val pageUrl: String?,
    val apkUrl: String,
    val apkName: String,
    val apkSize: Long,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAt: Long) : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val release: ReleaseInfo, val progress: Float?) : UpdateState
    data class Installing(val release: ReleaseInfo) : UpdateState
    data class Failed(val message: String, val release: ReleaseInfo?) : UpdateState
}

/**
 * Follows GitHub Releases of [BuildConfig.UPDATE_REPO]. Checks are throttled and conditional (ETag),
 * downloads are verified before install, and the install confirmation is surfaced through
 * [pendingConfirmation] for the visible activity to launch.
 */
class UpdateManager(
    private val context: Context,
    private val client: GitHubReleasesClient,
    private val settingsStore: SettingsStore,
    private val installer: ApkInstaller,
    private val scope: CoroutineScope,
    private val installedVersion: String = BuildConfig.VERSION_NAME,
    private val repository: String = BuildConfig.UPDATE_REPO,
) {
    /** Debug builds have a different package name, so they can't be updated by release APKs. */
    val enabled: Boolean = !BuildConfig.DEBUG

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** System "Do you want to update this app?" screen waiting to be shown by the activity. */
    private val _pendingConfirmation = MutableStateFlow<Intent?>(null)
    val pendingConfirmation: StateFlow<Intent?> = _pendingConfirmation.asStateFlow()

    /** Set when installing needs the "Install unknown apps" permission first. */
    private val _needsInstallPermission = MutableStateFlow<ReleaseInfo?>(null)
    val needsInstallPermission: StateFlow<ReleaseInfo?> = _needsInstallPermission.asStateFlow()

    private val checkLock = Mutex()
    private var updateJob: Job? = null

    val releasesPageUrl: String get() = "https://github.com/$repository/releases"

    /** Checks for a newer release; [force] bypasses the hourly throttle (manual "Check now"). */
    suspend fun check(force: Boolean = false): ReleaseInfo? = checkLock.withLock {
        if (!enabled) return@withLock null
        if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Installing) {
            return@withLock (_state.value as? UpdateState.Downloading)?.release
        }
        val cache = settingsStore.updateCache()
        val now = System.currentTimeMillis()
        if (!force && cache.releaseJson != null && now - cache.lastCheck < MIN_CHECK_INTERVAL) {
            return@withLock apply(parse(cache.releaseJson), cache.lastCheck)
        }
        if (force) _state.value = UpdateState.Checking
        try {
            when (val result = client.latestRelease(repository, cache.etag.takeIf { cache.releaseJson != null })) {
                is LatestReleaseResult.Found -> {
                    settingsStore.saveUpdateCheck(result.etag, AppJson.encodeToString(GitHubRelease.serializer(), result.release), now)
                    apply(result.release, now)
                }
                LatestReleaseResult.NotModified -> {
                    settingsStore.saveUpdateCheck(cache.etag, cache.releaseJson, now)
                    apply(parse(cache.releaseJson), now)
                }
                LatestReleaseResult.NoReleases -> {
                    settingsStore.saveUpdateCheck(null, null, now)
                    apply(null, now)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "Update check failed", e)
            if (force) _state.value = UpdateState.Failed("Couldn't reach GitHub. Check your connection.", null)
            null
        }
    }

    /** Background check from the worker: notify once per new version. */
    suspend fun checkInBackground() {
        val release = check(force = true) ?: return
        val settings = settingsStore.currentSettings()
        if (!settings.updateNotifications) return
        if (settingsStore.updateCache().notifiedVersion == release.version) return
        val builder = Notifications.builder(context, Notifications.CHANNEL_UPDATES)
            .setContentTitle("Nimbus ${release.version} is available")
            .setContentText("Tap to see what's new and update.")
            .setContentIntent(Notifications.openAppIntent(context, 10, action = ACTION_SHOW_UPDATE))
            .addAction(0, "Update now", Notifications.openAppIntent(context, 11, action = ACTION_INSTALL_UPDATE))
        Notifications.post(context, Notifications.ID_UPDATE_AVAILABLE, builder)
        settingsStore.setNotifiedUpdateVersion(release.version)
    }

    private fun parse(json: String?): GitHubRelease? =
        json?.let { runCatching { client.parseRelease(it) }.getOrNull() }

    private fun apply(release: GitHubRelease?, checkedAt: Long): ReleaseInfo? {
        val info = release?.takeIf { !it.draft && !it.prerelease }?.toInfo()
        return if (info != null && AppVersion.isNewer(info.tag, installedVersion)) {
            _state.value = UpdateState.Available(info)
            info
        } else {
            _state.value = UpdateState.UpToDate(checkedAt)
            null
        }
    }

    private fun GitHubRelease.toInfo(): ReleaseInfo? {
        val apk = apkAsset ?: return null
        return ReleaseInfo(
            version = tagName.removePrefix("v"),
            tag = tagName,
            title = name ?: tagName,
            notes = body.orEmpty().trim(),
            publishedAt = publishedAt,
            pageUrl = htmlUrl,
            apkUrl = apk.downloadUrl,
            apkName = apk.name,
            apkSize = apk.size,
        )
    }

    /** Downloads, verifies and installs [release] (or the currently available one, checking first if needed). */
    fun startUpdate(release: ReleaseInfo? = null) {
        if (updateJob?.isActive == true) return
        val target = release ?: availableRelease()
        if (target == null) {
            // "Update now" on the notification may start a fresh process that hasn't checked yet.
            scope.launch { check()?.let { startUpdate(it) } }
            return
        }
        if (!installer.canRequestInstalls()) {
            _needsInstallPermission.value = target
            return
        }
        _needsInstallPermission.value = null
        Notifications.cancel(context, Notifications.ID_UPDATE_AVAILABLE)
        updateJob = scope.launch {
            try {
                _state.value = UpdateState.Downloading(target, 0f)
                val apk = installer.download(target) { progress -> _state.value = UpdateState.Downloading(target, progress) }
                installer.verify(apk)
                _state.value = UpdateState.Installing(target)
                installer.install(apk)
            } catch (e: CancellationException) {
                _state.value = UpdateState.Available(target)
                throw e
            } catch (e: Exception) {
                AppLog.w(TAG, "Update failed", e)
                _state.value = UpdateState.Failed(
                    (e as? UpdateException)?.message ?: "The update couldn't be downloaded. Please try again.",
                    target,
                )
            }
        }
    }

    fun cancelPermissionRequest() {
        _needsInstallPermission.value = null
    }

    fun consumeConfirmation() {
        _pendingConfirmation.value = null
    }

    fun availableRelease(): ReleaseInfo? = when (val s = _state.value) {
        is UpdateState.Available -> s.release
        is UpdateState.Failed -> s.release
        is UpdateState.Downloading -> s.release
        is UpdateState.Installing -> s.release
        else -> null
    }

    /** Called by [InstallResultReceiver] with the PackageInstaller outcome. */
    fun onInstallStatus(status: Int, message: String?, confirmation: Intent?) {
        val release = availableRelease()
        AppLog.i(TAG, "Install status $status ${message.orEmpty()}")
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                if (confirmation != null) _pendingConfirmation.value = confirmation
            }
            PackageInstaller.STATUS_SUCCESS -> _state.value = UpdateState.UpToDate(System.currentTimeMillis())
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                _state.value = release?.let { UpdateState.Available(it) } ?: UpdateState.Idle
            }
            else -> _state.value = UpdateState.Failed(installFailureMessage(status, message), release)
        }
    }

    private fun installFailureMessage(status: Int, message: String?): String = when (status) {
        PackageInstaller.STATUS_FAILURE_CONFLICT ->
            "Android refused the update because it's signed differently. Uninstall Nimbus and install the new version from GitHub."
        PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage space to install the update."
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "This update isn't compatible with your device."
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "Installation was blocked by the system."
        else -> "Install failed" + (message?.let { ": $it" } ?: ".")
    }

    companion object {
        private const val TAG = "UpdateManager"
        private const val MIN_CHECK_INTERVAL = 60 * 60 * 1000L
        const val ACTION_SHOW_UPDATE = "io.github.duddmeister.weather.action.SHOW_UPDATE"
        const val ACTION_INSTALL_UPDATE = "io.github.duddmeister.weather.action.INSTALL_UPDATE"
    }
}
