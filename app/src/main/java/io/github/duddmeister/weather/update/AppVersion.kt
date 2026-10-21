package io.github.duddmeister.weather.update

import kotlin.math.max

/**
 * A dotted version like "1.0.42", optionally with a pre-release suffix ("1.0.42-dev"). Release tags
 * may carry a leading "v". A version with a suffix sorts before the same version without one.
 */
data class AppVersion(val parts: List<Int>, val preRelease: String?) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        for (i in 0 until max(parts.size, other.parts.size)) {
            val a = parts.getOrElse(i) { 0 }
            val b = other.parts.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return when {
            preRelease == other.preRelease -> 0
            preRelease == null -> 1
            other.preRelease == null -> -1
            else -> preRelease.compareTo(other.preRelease)
        }
    }

    override fun toString(): String = parts.joinToString(".") + (preRelease?.let { "-$it" } ?: "")

    companion object {
        fun parse(raw: String?): AppVersion? {
            val text = raw?.trim()?.removePrefix("v")?.removePrefix("V") ?: return null
            if (text.isEmpty()) return null
            val withoutBuild = text.substringBefore('+')
            val core = withoutBuild.substringBefore('-')
            val suffix = withoutBuild.substringAfter('-', missingDelimiterValue = "").ifEmpty { null }
            val parts = core.split('.').map { it.toIntOrNull() ?: return null }
            return AppVersion(parts, suffix)
        }

        /** True when [candidate] (e.g. a release tag) is newer than [installed]. */
        fun isNewer(candidate: String?, installed: String?): Boolean {
            val c = parse(candidate) ?: return false
            val i = parse(installed) ?: return true
            return c > i
        }
    }
}
