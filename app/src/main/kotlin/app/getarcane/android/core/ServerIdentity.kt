package app.getarcane.android.core

import java.net.URI
import java.security.MessageDigest

internal data class ServerIdentity(
    val normalizedUrl: String,
    val canonicalOrigin: String,
    val tokenAccount: String,
)

internal object ServerIdentities {
    fun from(rawUrl: String): ServerIdentity? {
        val normalizedUrl = ServerUrl.normalize(rawUrl) ?: return null
        val uri = URI(normalizedUrl)
        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it >= 0 } ?: when (scheme) {
            "http" -> 80
            "https" -> 443
            else -> return null
        }
        val authorityHost = if (host.contains(':') && !host.startsWith('[')) "[$host]" else host
        val path = uri.rawPath.orEmpty().let { if (it == "/") "" else it }
        val canonicalOrigin = "$scheme://$authorityHost:$port$path"

        return ServerIdentity(
            normalizedUrl = normalizedUrl,
            canonicalOrigin = canonicalOrigin,
            tokenAccount = "server.${canonicalOrigin.sha256()}",
        )
    }
}

private fun String.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
