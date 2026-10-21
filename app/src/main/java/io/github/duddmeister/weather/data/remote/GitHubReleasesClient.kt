package io.github.duddmeister.weather.data.remote

import io.github.duddmeister.weather.core.AppJson
import io.github.duddmeister.weather.core.HttpException
import io.github.duddmeister.weather.core.HttpFetcher
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList(),
) {
    /** The installable APK attached to this release, if any. */
    val apkAsset: GitHubAsset?
        get() = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
}

@Serializable
data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0,
    @SerialName("content_type") val contentType: String? = null,
)

sealed interface LatestReleaseResult {
    data class Found(val release: GitHubRelease, val etag: String?) : LatestReleaseResult
    data object NotModified : LatestReleaseResult
    data object NoReleases : LatestReleaseResult
}

/**
 * Reads the latest published release of a public repository. Uses ETags so repeat checks are
 * conditional requests, which don't count against GitHub's unauthenticated rate limit.
 */
class GitHubReleasesClient(
    private val http: HttpFetcher,
    private val json: Json = AppJson,
) {
    suspend fun latestRelease(repository: String, etag: String?): LatestReleaseResult {
        val url = "https://api.github.com/repos/$repository/releases/latest".toHttpUrl()
        val headers = buildMap {
            put("Accept", "application/vnd.github+json")
            put("X-GitHub-Api-Version", "2022-11-28")
            if (!etag.isNullOrBlank()) put("If-None-Match", etag)
        }
        val result = http.get(url, headers)
        return when {
            result.code == 304 -> LatestReleaseResult.NotModified
            result.code == 404 -> LatestReleaseResult.NoReleases
            result.isSuccessful -> LatestReleaseResult.Found(
                json.decodeFromString(GitHubRelease.serializer(), result.body),
                result.headers["ETag"],
            )
            else -> throw HttpException(result.code, url.toString(), result.body)
        }
    }

    fun parseRelease(body: String): GitHubRelease = json.decodeFromString(GitHubRelease.serializer(), body)
}
