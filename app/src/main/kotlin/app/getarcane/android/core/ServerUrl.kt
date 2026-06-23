package app.getarcane.android.core

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

object ServerUrl {
    private val pastedArcaneRoutes = setOf("dashboard", "login", "api")

    fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val uri = try {
            URI(withScheme)
        } catch (_: URISyntaxException) {
            return null
        }

        val scheme = uri.scheme?.takeIf { it.isNotBlank() } ?: return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val normalizedPath = normalizePath(uri.rawPath)

        return try {
            URI(
                scheme.lowercase(Locale.US),
                uri.rawUserInfo,
                host,
                uri.port,
                normalizedPath.ifEmpty { null },
                null,
                null,
            ).toString().trimEnd('/')
        } catch (_: URISyntaxException) {
            null
        }
    }

    private fun normalizePath(rawPath: String?): String {
        val path = rawPath.orEmpty().trimEnd('/')
        if (path.isBlank()) return ""

        val lastSegment = path.substringAfterLast('/').lowercase(Locale.US)
        if (lastSegment !in pastedArcaneRoutes) return path

        return path.substringBeforeLast('/', missingDelimiterValue = "")
    }
}
