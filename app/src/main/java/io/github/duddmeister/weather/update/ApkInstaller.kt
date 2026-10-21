package io.github.duddmeister.weather.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class UpdateException(message: String) : IOException(message)

/** Downloads a release APK, checks it really is an update for this app, and hands it to PackageInstaller. */
class ApkInstaller(
    context: Context,
    httpClient: OkHttpClient,
    private val userAgent: String,
) {
    private val context = context.applicationContext
    private val downloadClient = httpClient.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.MINUTES)
        .build()

    /** Whether the user has allowed this app to install packages ("Install unknown apps"). */
    fun canRequestInstalls(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun installPermissionSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    suspend fun download(release: ReleaseInfo, onProgress: (Float?) -> Unit): File = runInterruptible(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val target = File(directory, "nimbus-${release.version}.apk")
        val request = Request.Builder()
            .url(release.apkUrl)
            .header("User-Agent", userAgent)
            .header("Accept", "application/octet-stream")
            .build()
        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw UpdateException("Download failed (HTTP ${response.code})")
            val body = response.body ?: throw UpdateException("Download failed (empty response)")
            val total = body.contentLength().takeIf { it > 0 } ?: release.apkSize.takeIf { it > 0 }
            onProgress(if (total == null) null else 0f)
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    var reported = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total != null && written - reported >= total / 100) {
                            reported = written
                            onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            if (total != null && target.length() != total) {
                throw UpdateException("Download was incomplete. Please try again.")
            }
        }
        target
    }

    /** Refuses anything that isn't a newer build of this app signed with the same key. */
    suspend fun verify(apk: File) = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else signaturesFlag()
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: throw UpdateException("The downloaded file isn't a valid app package.")
        if (archive.packageName != context.packageName) {
            throw UpdateException("The download is for a different app (${archive.packageName}).")
        }
        val installed = pm.getPackageInfo(context.packageName, flags)
        if (PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(installed)) {
            throw UpdateException("The downloaded version isn't newer than the one installed.")
        }
        val newSigners = signers(archive)
        if (newSigners.isNotEmpty() && newSigners != signers(installed)) {
            throw UpdateException(
                "This release is signed with a different key, so Android won't install it over this copy. " +
                    "Uninstall Nimbus and install the new version from GitHub instead.",
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun signaturesFlag() = PackageManager.GET_SIGNATURES

    private fun signers(info: PackageInfo): Set<String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return emptySet()
            val certificates = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
            return certificates.orEmpty().map { it.toCharsString() }.toSet()
        }
        @Suppress("DEPRECATION")
        return info.signatures.orEmpty().map { it.toCharsString() }.toSet()
    }

    /** Streams the APK into an install session. The outcome arrives at [InstallResultReceiver]. */
    suspend fun install(apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Lets later updates skip the confirmation dialog once Nimbus is its own installer.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val intent = Intent(context, InstallResultReceiver::class.java)
                    .setAction(InstallResultReceiver.ACTION_INSTALL_STATUS)
                    .setPackage(context.packageName)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val statusReceiver = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(statusReceiver.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
    }
}
