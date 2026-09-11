package app.getarcane.android.ui.screens.images

import app.getarcane.android.core.formatBytes
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.sdk.errors.ArcaneError
import app.getarcane.sdk.models.base.JsonValue
import app.getarcane.sdk.models.image.ImageAttestation
import app.getarcane.sdk.models.image.ImageHistoryItem
import java.security.MessageDigest
import java.net.URLDecoder
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

internal const val RAW_STATEMENT_PREVIEW_LIMIT = 32_000

internal data class ImageInsightIdentity(
    val sessionKey: String,
    val environmentId: String,
    val environmentName: String,
    val imageId: String,
    val imageDisplayName: String,
) {
    val requestKey: String get() = "$sessionKey\u0000$environmentId\u0000$imageId"
}

private fun encodeImageInsightRouteValue(value: String): String =
    URLEncoder.encode(value.ifEmpty { "-" }, Charsets.UTF_8.name())

private fun decodeImageInsightRouteValue(value: String): String =
    URLDecoder.decode(value, Charsets.UTF_8.name()).let { if (it == "-") "" else it }

internal fun imageInsightRoute(prefix: String, identity: ImageInsightIdentity): String =
    listOf(
        prefix,
        identity.sessionKey,
        identity.environmentId,
        identity.environmentName,
        identity.imageId,
        identity.imageDisplayName,
    ).joinToString("/") { encodeImageInsightRouteValue(it) }

internal fun imageInsightIdentityFromRoute(argument: (String) -> String?): ImageInsightIdentity =
    ImageInsightIdentity(
        sessionKey = decodeImageInsightRouteValue(argument("sessionKey").orEmpty()),
        environmentId = decodeImageInsightRouteValue(argument("environmentId").orEmpty()),
        environmentName = decodeImageInsightRouteValue(argument("environmentName").orEmpty()),
        imageId = decodeImageInsightRouteValue(argument("imageId").orEmpty()),
        imageDisplayName = decodeImageInsightRouteValue(argument("imageDisplayName").orEmpty()),
    )

internal fun imageInsightSessionKey(serverIdentity: String, userId: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$serverIdentity\u0000$userId".encodeToByteArray())
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}

internal fun ImageInsightIdentity.isCurrent(
    serverIdentity: String,
    userId: String,
    activeEnvironmentId: String,
): Boolean =
    sessionKey == imageInsightSessionKey(serverIdentity, userId) &&
        environmentId == activeEnvironmentId

internal enum class ImageInsightFailureKind {
    Unauthorized,
    Unsupported,
    Malformed,
    Error,
}

internal class MissingAttestationStatementException : Exception()
internal class AttestationSelectionMissingException : Exception()

internal data class ImageInsightFailure(
    val kind: ImageInsightFailureKind,
    val message: String,
)

internal sealed interface ImageInsightUiState<out T> {
    data object Loading : ImageInsightUiState<Nothing>
    data class Content<T>(val value: T) : ImageInsightUiState<T>
    data class Failed(val failure: ImageInsightFailure) : ImageInsightUiState<Nothing>
    data object StaleSelection : ImageInsightUiState<Nothing>
}

internal sealed interface ImageInsightLoadResult<out T> {
    data class Success<T>(val value: T) : ImageInsightLoadResult<T>
    data class Failure(val failure: ImageInsightFailure) : ImageInsightLoadResult<Nothing>
}

internal fun imageInsightFailure(error: Throwable): ImageInsightFailure = when (error) {
    is ArcaneError.Unauthorized, is ArcaneError.Forbidden -> ImageInsightFailure(
        ImageInsightFailureKind.Unauthorized,
        "Your account does not have image-read access for this environment.",
    )
    is ArcaneError.NotFound -> ImageInsightFailure(
        ImageInsightFailureKind.Unsupported,
        "This Arcane server does not support this image insight.",
    )
    is ArcaneError.Decoding -> ImageInsightFailure(
        ImageInsightFailureKind.Malformed,
        "The server returned malformed or unsupported image-insight data.",
    )
    is MissingAttestationStatementException -> ImageInsightFailure(
        ImageInsightFailureKind.Malformed,
        "The server did not return a complete raw in-toto statement.",
    )
    is AttestationSelectionMissingException -> ImageInsightFailure(
        ImageInsightFailureKind.Error,
        "The selected attestation is no longer available. Close it and refresh the list.",
    )
    else -> ImageInsightFailure(ImageInsightFailureKind.Error, friendlyErrorMessage(error))
}

/** Loads one scoped result and rejects both stale success and stale failure publication. */
internal suspend fun <T> loadImageInsightResult(
    isCurrent: () -> Boolean,
    load: suspend () -> T,
): ImageInsightLoadResult<T>? {
    if (!isCurrent()) return null
    return try {
        val value = load()
        if (isCurrent()) ImageInsightLoadResult.Success(value) else null
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        if (isCurrent()) ImageInsightLoadResult.Failure(imageInsightFailure(error)) else null
    }
}

internal fun predicateTypeLabel(predicateType: String): String {
    val type = predicateType.trim()
    if (type.isEmpty()) return "Unknown predicate type"
    return when {
        "slsa.dev/provenance" in type -> "SLSA Provenance"
        "spdx" in type.lowercase() -> "SPDX SBOM"
        "cyclonedx" in type.lowercase() -> "CycloneDX SBOM"
        "vuln" in type.lowercase() -> "Vulnerability Scan"
        else -> type.substringAfterLast('/').ifBlank { type }.replaceFirstChar(Char::uppercase)
    }
}

internal fun predicateTypeOptions(attestations: List<ImageAttestation>): List<String> =
    attestations.map { it.predicateType }.distinct().sorted()

internal fun filterAttestations(
    attestations: List<ImageAttestation>,
    predicateType: String?,
): List<ImageAttestation> =
    if (predicateType == null) attestations else attestations.filter { it.predicateType == predicateType }

internal fun attestationSelectionKey(
    identity: ImageInsightIdentity,
    attestation: ImageAttestation,
): String = listOf(
    identity.requestKey,
    attestation.digest,
    attestation.predicateType,
    attestation.platform.orEmpty(),
).joinToString("\u0000")

internal fun selectAttestation(
    candidates: List<ImageAttestation>,
    selected: ImageAttestation,
): ImageAttestation? = candidates.firstOrNull {
    it.digest == selected.digest &&
        it.predicateType == selected.predicateType &&
        it.platform == selected.platform
}

internal fun formatLayerId(item: ImageHistoryItem): String =
    if (item.isMissingLayer) "Metadata-only layer" else item.id

internal fun formatLayerCommand(item: ImageHistoryItem): String =
    item.createdBy.trim().ifEmpty { "No command recorded" }

internal fun formatLayerSize(item: ImageHistoryItem): String =
    if (item.size < 0) "Unknown size" else formatBytes(item.size)

internal fun formatLayerCreated(item: ImageHistoryItem): String {
    if (item.created <= 0) return "Creation time unavailable"
    return runCatching { formatImageDate(Instant.fromEpochSeconds(item.created)) }
        .getOrDefault("Creation time unavailable")
}

internal fun formatLayerTags(item: ImageHistoryItem): String =
    item.tags.map(String::trim).filter(String::isNotEmpty).distinct().joinToString(", ")
        .ifEmpty { "No tags" }

@OptIn(ExperimentalSerializationApi::class)
private val prettyStatementJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

internal fun rawStatementJson(statement: JsonValue): String =
    prettyStatementJson.encodeToString(statement)

internal data class RawStatementPreview(val text: String, val truncated: Boolean)

internal fun rawStatementPreview(
    statement: String,
    limit: Int = RAW_STATEMENT_PREVIEW_LIMIT,
): RawStatementPreview {
    require(limit > 0)
    return if (statement.length <= limit) {
        RawStatementPreview(statement, truncated = false)
    } else {
        RawStatementPreview(statement.take(limit), truncated = true)
    }
}

internal fun rawStatementFilename(attestation: ImageAttestation): String {
    val digest = attestation.digest.substringAfter(':', attestation.digest)
        .filter(Char::isLetterOrDigit)
        .take(16)
        .ifEmpty { "unknown" }
    return "in-toto-attestation-$digest.json"
}
