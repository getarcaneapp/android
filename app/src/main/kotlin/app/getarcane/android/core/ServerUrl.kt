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

        val scheme = uri.scheme
            ?.lowercase(Locale.US)
            ?.takeIf { it == "http" || it == "https" }
            ?: return null
        if (uri.rawUserInfo != null) return null
        val host = uri.host
            ?.lowercase(Locale.US)
            ?.trimEnd('.')
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val port = when {
            scheme == "http" && uri.port == 80 -> -1
            scheme == "https" && uri.port == 443 -> -1
            else -> uri.port
        }
        val normalizedPath = normalizePath(uri.rawPath)

        return try {
            URI(
                scheme,
                null,
                host,
                port,
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
