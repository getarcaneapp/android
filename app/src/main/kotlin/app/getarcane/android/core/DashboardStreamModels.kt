package app.getarcane.android.core

import app.getarcane.sdk.EnvironmentId
import app.getarcane.sdk.models.base.PaginationResponse
import app.getarcane.sdk.models.container.ContainerStatusCounts
import app.getarcane.sdk.models.image.ImageUsageCounts
import app.getarcane.sdk.models.version.VersionInfo
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.decodeFromJsonElement

enum class DashboardStreamEventType {
    Snapshot,
    Pending,
    Heartbeat,
    Error,
    Unknown,
}

enum class DashboardStreamErrorCode {
    AgentIncompatible,
    Unreachable,
    Unknown,
}

enum class DashboardActionItemKind {
    StoppedContainers,
    ImageUpdates,
    ActionableVulnerabilities,
    ExpiringKeys,
    Unknown,
}

enum class DashboardActionItemSeverity {
    Warning,
    Critical,
    Unknown,
}

@Serializable
data class DashboardStreamEvent(
    val type: String,
    @SerialName("environmentId")
    val environmentId: String? = null,
    val snapshot: DashboardSnapshot? = null,
    val error: String? = null,
    val errorCode: String? = null,
    val timestamp: String? = null,
) {
    val eventType: DashboardStreamEventType
        get() = when (type) {
            "snapshot" -> DashboardStreamEventType.Snapshot
            "pending" -> DashboardStreamEventType.Pending
            "heartbeat" -> DashboardStreamEventType.Heartbeat
            "error" -> DashboardStreamEventType.Error
            else -> DashboardStreamEventType.Unknown
        }

    val resolvedEnvironmentId: String
        get() = environmentId?.takeIf { it.isNotBlank() } ?: EnvironmentId.LOCAL_DOCKER.rawValue

    val streamErrorCode: DashboardStreamErrorCode?
        get() = when (errorCode) {
            null -> null
            "agent_incompatible" -> DashboardStreamErrorCode.AgentIncompatible
            "unreachable" -> DashboardStreamErrorCode.Unreachable
            else -> DashboardStreamErrorCode.Unknown
        }
}

@Serializable
data class DashboardSnapshot(
    val containers: DashboardSnapshotContainers,
    val images: DashboardSnapshotImages,
    val imageUsageCounts: ImageUsageCounts,
    val actionItems: DashboardActionItems = DashboardActionItems(),
    val settings: DashboardSnapshotSettings = DashboardSnapshotSettings(),
    val versionInfo: VersionInfo? = null,
)

@Serializable
data class DashboardSnapshotContainers(
    @Serializable(with = NullAsEmptyJsonElementListSerializer::class)
    val data: List<JsonElement> = emptyList(),
    val counts: ContainerStatusCounts = ContainerStatusCounts(0, 0, 0),
    val pagination: PaginationResponse? = null,
)

@Serializable
data class DashboardSnapshotImages(
    @Serializable(with = NullAsEmptyJsonElementListSerializer::class)
    val data: List<JsonElement> = emptyList(),
    val pagination: PaginationResponse? = null,
)

@Serializable
data class DashboardActionItems(
    val items: List<DashboardActionItem> = emptyList(),
)

@Serializable
data class DashboardActionItem(
    val kind: String,
    val count: Int,
    val severity: String,
) {
    val itemKind: DashboardActionItemKind
        get() = when (kind) {
            "stopped_containers" -> DashboardActionItemKind.StoppedContainers
            "image_updates" -> DashboardActionItemKind.ImageUpdates
            "actionable_vulnerabilities" -> DashboardActionItemKind.ActionableVulnerabilities
            "expiring_keys" -> DashboardActionItemKind.ExpiringKeys
            else -> DashboardActionItemKind.Unknown
        }

    val itemSeverity: DashboardActionItemSeverity
        get() = when (severity) {
            "warning" -> DashboardActionItemSeverity.Warning
            "critical" -> DashboardActionItemSeverity.Critical
            else -> DashboardActionItemSeverity.Unknown
        }
}

@Serializable
class DashboardSnapshotSettings

@Serializable
private data class DashboardSnapshotEnvelope(
    val data: DashboardSnapshot,
)

object NullAsEmptyJsonElementListSerializer : KSerializer<List<JsonElement>> {
    private val delegate = ListSerializer(JsonElement.serializer())

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<JsonElement> {
        val element = decoder.decodeSerializableValue(JsonElement.serializer())
        if (element is JsonNull) return emptyList()
        return decoder.serializersModule
            .let { app.getarcane.sdk.serialization.ArcaneJson.default.decodeFromJsonElement(delegate, element) }
    }

    override fun serialize(encoder: Encoder, value: List<JsonElement>) {
        encoder.encodeSerializableValue(delegate, value)
    }
}

fun parseDashboardSnapshotEnvelope(text: String): DashboardSnapshot =
    app.getarcane.sdk.serialization.ArcaneJson.default.decodeFromString<DashboardSnapshotEnvelope>(text).data
