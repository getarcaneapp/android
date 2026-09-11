package app.getarcane.android.ui.screens.images

import app.getarcane.sdk.errors.ArcaneError
import app.getarcane.sdk.models.base.JsonValue
import app.getarcane.sdk.models.image.ImageAttestation
import app.getarcane.sdk.models.image.ImageHistoryItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageInsightsModelsTest {
    @Test
    fun identityIsScopedToServerUserEnvironmentAndImmutableImage() {
        val identity = identity(server = "https://one.example", user = "user-1", environment = "edge-1")

        assertTrue(identity.isCurrent("https://one.example", "user-1", "edge-1"))
        assertFalse(identity.isCurrent("https://two.example", "user-1", "edge-1"))
        assertFalse(identity.isCurrent("https://one.example", "user-2", "edge-1"))
        assertFalse(identity.isCurrent("https://one.example", "user-1", "edge-2"))
        assertTrue(identity.requestKey.endsWith("\u0000edge-1\u0000sha256:immutable"))
    }

    @Test
    fun identityRouteRoundTripsNamesAndImageReferences() {
        val expected = identity(
            server = "https://one.example",
            user = "user-1",
            environment = "edge/one",
        ).copy(
            environmentName = "Edge & Lab",
            imageId = "registry.local/team/app@sha256:abc",
            imageDisplayName = "team/app:latest",
        )
        val route = imageInsightRoute("history", expected)
        val segments = route.split('/').drop(1)
        val names = listOf("sessionKey", "environmentId", "environmentName", "imageId", "imageDisplayName")
        val decoded = imageInsightIdentityFromRoute { name -> segments[names.indexOf(name)] }

        assertEquals(expected, decoded)
    }

    @Test
    fun filteringPreservesUnknownPredicateTypesAndCanProduceFilteredEmpty() {
        val provenance = attestation("https://slsa.dev/provenance/v1", "sha256:p")
        val unknown = attestation("vendor.example/predicate/new", "sha256:u")
        val values = listOf(unknown, provenance, unknown.copy(digest = "sha256:u2"))

        assertEquals(
            listOf("https://slsa.dev/provenance/v1", "vendor.example/predicate/new"),
            predicateTypeOptions(values),
        )
        assertEquals(listOf(provenance), filterAttestations(values, provenance.predicateType))
        assertTrue(filterAttestations(values, "missing/type").isEmpty())
        assertEquals("New", predicateTypeLabel(unknown.predicateType))
        assertEquals("Unknown predicate type", predicateTypeLabel(""))
    }

    @Test
    fun attestationSelectionUsesScopedIdentityDigestPredicateAndPlatform() {
        val identity = identity()
        val selected = attestation("type/a", "sha256:a", platform = "linux/amd64")
        val replacement = selected.copy(size = 99)

        assertEquals(replacement, selectAttestation(listOf(replacement), selected))
        assertNull(selectAttestation(listOf(replacement.copy(platform = "linux/arm64")), selected))
        assertTrue(attestationSelectionKey(identity, selected).startsWith(identity.requestKey))
        assertFalse(
            attestationSelectionKey(identity.copy(environmentId = "other"), selected) ==
                attestationSelectionKey(identity, selected),
        )
    }

    @Test
    fun staleSuccessAndFailureAreRejected() = runBlocking {
        var current = true
        val success = loadImageInsightResult(
            isCurrent = { current },
            load = { current = false; "stale" },
        )
        assertNull(success)

        current = true
        val failure = loadImageInsightResult<String>(
            isCurrent = { current },
            load = { current = false; throw ArcaneError.Server("FAILED", "stale") },
        )
        assertNull(failure)
    }

    @Test
    fun cancellationIsRethrownAndCurrentFailuresAreTyped() = runBlocking {
        val cancellation = runCatching {
            loadImageInsightResult(isCurrent = { true }) { throw CancellationException("cancel") }
        }.exceptionOrNull()
        assertTrue(cancellation is CancellationException)
        assertEquals(
            ImageInsightFailureKind.Unauthorized,
            imageInsightFailure(ArcaneError.Forbidden).kind,
        )
        assertEquals(
            ImageInsightFailureKind.Unsupported,
            imageInsightFailure(ArcaneError.NotFound).kind,
        )
        assertEquals(
            ImageInsightFailureKind.Malformed,
            imageInsightFailure(ArcaneError.Decoding("bad payload")).kind,
        )
        assertEquals(
            ImageInsightFailureKind.Error,
            imageInsightFailure(ArcaneError.Server("FAILED", "registry unavailable")).kind,
        )
    }

    @Test
    fun historyFormattingHandlesMissingAndUnknownValuesWithoutReordering() {
        val missing = ImageHistoryItem(
            id = "<missing>",
            created = 0,
            createdBy = "  ",
            tags = listOf("", "fixture:latest", "fixture:latest"),
            size = -1,
        )
        assertEquals("Metadata-only layer", formatLayerId(missing))
        assertEquals("No command recorded", formatLayerCommand(missing))
        assertEquals("Unknown size", formatLayerSize(missing))
        assertEquals("Creation time unavailable", formatLayerCreated(missing))
        assertEquals("fixture:latest", formatLayerTags(missing))

        val records = listOf(
            ImageHistoryItem(id = "new", createdBy = "RUN second"),
            ImageHistoryItem(id = "old", createdBy = "RUN first"),
        )
        assertEquals(listOf("new", "old"), records.map(::formatLayerId))
    }

    @Test
    fun rawStatementPreviewIsBoundedWhileCopyExportSourceRemainsComplete() {
        val json = rawStatementJson(
            JsonValue.Obj(
                mapOf(
                    "_type" to JsonValue.Str("https://in-toto.io/Statement/v1"),
                    "predicate" to JsonValue.Obj(mapOf("value" to JsonValue.Number(2.0))),
                ),
            ),
        )
        val preview = rawStatementPreview(json, limit = 12)

        assertTrue(json.contains("in-toto.io/Statement/v1"))
        assertEquals(12, preview.text.length)
        assertTrue(preview.truncated)
        assertTrue(json.length > preview.text.length)
        assertEquals("in-toto-attestation-abc123.json", rawStatementFilename(attestation("type", "sha256:abc-123")))
    }

    @Test
    fun successfulCurrentLoadPublishesValue() = runBlocking {
        val result = loadImageInsightResult(isCurrent = { true }) { listOf("one") }
        assertTrue(result is ImageInsightLoadResult.Success)
        assertEquals(listOf("one"), (result as ImageInsightLoadResult.Success).value)
    }

    private fun identity(
        server: String = "https://one.example",
        user: String = "user-1",
        environment: String = "edge-1",
    ) = ImageInsightIdentity(
        sessionKey = imageInsightSessionKey(server, user),
        environmentId = environment,
        environmentName = "Edge One",
        imageId = "sha256:immutable",
        imageDisplayName = "fixture:latest",
    )

    private fun attestation(type: String, digest: String, platform: String? = null) = ImageAttestation(
        digest = digest,
        mediaType = "application/vnd.in-toto+json",
        predicateType = type,
        platform = platform,
        size = 42,
    )
}
