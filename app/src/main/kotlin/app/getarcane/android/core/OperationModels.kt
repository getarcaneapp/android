package app.getarcane.android.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
enum class OperationKind(val verb: String) {
    PROJECT_DEPLOY("Deploy"),
    PROJECT_REDEPLOY("Redeploy"),
    PROJECT_PULL("Pull images"),
    PROJECT_BUILD("Build images"),
    IMAGE_PULL("Pull image"),
    CONTAINER_REDEPLOY("Redeploy"),
    UPDATER_RUN("Run updater"),
    FLEET_UPDATE("Update fleet"),
    UNKNOWN("Operation"),
}

@Serializable
enum class OperationState {
    QUEUED,
    STARTING,
    RUNNING,
    RECONNECTING,
    CANCEL_REQUESTED,
    SUCCESS,
    FAILURE,
    CANCELLED,
    INTERRUPTED,
    UNKNOWN,
    CLEARED,
}

@Serializable
enum class OperationRecoveryMode { ACTIVITY, FLEET_JOB, NONE }

@Serializable
enum class OperationTargetType { PROJECT, IMAGE, CONTAINER, ENVIRONMENT, FLEET, UNKNOWN }

@Serializable
enum class OperationPresentationCode {
    WAITING,
    STARTING,
    WORKING,
    RECONNECTING,
    CANCELLING,
    COMPLETE,
    COMPLETED_WITH_ISSUES,
    FAILED,
    CANCELLED,
    INTERRUPTED,
    OUTCOME_UNKNOWN,
}

data class OperationLine(val text: String, val isError: Boolean)

@Serializable
data class OperationRecord(
    val operationId: String,
    val kind: OperationKind = OperationKind.UNKNOWN,
    val state: OperationState = OperationState.UNKNOWN,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val serverBindingHash: String,
    val accountBindingHash: String,
    val credentialOriginHash: String,
    val environmentId: String,
    val targetType: OperationTargetType = OperationTargetType.UNKNOWN,
    val opaqueTargetId: String? = null,
    val duplicateKeyDigest: String,
    val activityBatchId: String,
    val recoveryMode: OperationRecoveryMode = OperationRecoveryMode.NONE,
    val serverActivityId: String? = null,
    val fleetJobId: String? = null,
    val presentationCode: OperationPresentationCode = OperationPresentationCode.STARTING,
    val progressPercent: Int? = null,
    val terminalAtEpochMs: Long? = null,
    @Transient val environmentName: String = "",
    @Transient val targetName: String = "",
    @Transient val lines: List<OperationLine> = emptyList(),
    @Transient val phases: List<String> = emptyList(),
    @Transient val detailMessage: String? = null,
) {
    val isActive: Boolean
        get() = state in ACTIVE_OPERATION_STATES

    val isTerminalLike: Boolean
        get() = state in TERMINAL_LIKE_OPERATION_STATES

    val title: String
        get() = targetName.takeIf { it.isNotBlank() }?.let { "${kind.verb} $it" } ?: kind.verb
}

internal val ACTIVE_OPERATION_STATES = setOf(
    OperationState.QUEUED,
    OperationState.STARTING,
    OperationState.RUNNING,
    OperationState.RECONNECTING,
    OperationState.CANCEL_REQUESTED,
)

internal val TERMINAL_LIKE_OPERATION_STATES = setOf(
    OperationState.SUCCESS,
    OperationState.FAILURE,
    OperationState.CANCELLED,
    OperationState.INTERRUPTED,
    OperationState.UNKNOWN,
)

internal object OperationStateMachine {
    private val transitions = mapOf(
        OperationState.QUEUED to setOf(
            OperationState.RUNNING, OperationState.RECONNECTING, OperationState.CANCEL_REQUESTED,
            OperationState.SUCCESS, OperationState.CANCELLED, OperationState.FAILURE, OperationState.INTERRUPTED,
            OperationState.UNKNOWN,
        ),
        OperationState.STARTING to setOf(
            OperationState.QUEUED, OperationState.RUNNING, OperationState.RECONNECTING,
            OperationState.CANCEL_REQUESTED, OperationState.SUCCESS, OperationState.FAILURE,
            OperationState.CANCELLED, OperationState.INTERRUPTED, OperationState.UNKNOWN,
        ),
        OperationState.RUNNING to setOf(
            OperationState.QUEUED, OperationState.RECONNECTING, OperationState.CANCEL_REQUESTED,
            OperationState.SUCCESS, OperationState.FAILURE, OperationState.CANCELLED,
            OperationState.INTERRUPTED, OperationState.UNKNOWN,
        ),
        OperationState.RECONNECTING to setOf(
            OperationState.QUEUED, OperationState.RUNNING, OperationState.CANCEL_REQUESTED,
            OperationState.SUCCESS, OperationState.FAILURE, OperationState.CANCELLED,
            OperationState.INTERRUPTED, OperationState.UNKNOWN,
        ),
        OperationState.CANCEL_REQUESTED to setOf(
            OperationState.CANCELLED, OperationState.SUCCESS, OperationState.FAILURE,
            OperationState.RECONNECTING, OperationState.INTERRUPTED, OperationState.UNKNOWN,
        ),
        OperationState.SUCCESS to setOf(OperationState.CLEARED),
        OperationState.FAILURE to setOf(OperationState.CLEARED),
        OperationState.CANCELLED to setOf(OperationState.CLEARED),
        OperationState.INTERRUPTED to setOf(OperationState.UNKNOWN, OperationState.CLEARED),
        OperationState.UNKNOWN to setOf(
            OperationState.RECONNECTING, OperationState.SUCCESS, OperationState.FAILURE,
            OperationState.CANCELLED, OperationState.CLEARED,
        ),
        OperationState.CLEARED to emptySet(),
    )

    fun canTransition(from: OperationState, to: OperationState): Boolean =
        from == to || to in transitions.getValue(from)
}

sealed interface OperationStartResult {
    data class Started(val operationId: String) : OperationStartResult
    data class Duplicate(val operationId: String) : OperationStartResult
    data class Rejected(val message: String) : OperationStartResult
}

data class ActivityOpenRequest(
    val requestId: Long,
    val activityId: String,
    val environmentId: String,
)

internal sealed interface OperationRoute {
    data object Center : OperationRoute
    data class Detail(val operationId: String) : OperationRoute

    companion object {
        fun parse(raw: String?): OperationRoute? {
            val uri = raw?.let { runCatching { java.net.URI(it) }.getOrNull() } ?: return null
            if (uri.scheme != "arcane-mobile" || uri.host != "operations") return null
            val pathSegments = uri.path.orEmpty().split('/').filter(String::isNotEmpty)
            return when (pathSegments.size) {
                0 -> Center
                1 -> pathSegments[0].takeIf(ACTIVITY_BATCH_ID_PATTERN::matches)?.let(::Detail)
                else -> null
            }
        }
    }
}

internal fun parseOperationCancelRoute(raw: String?): String? {
    val uri = raw?.let { runCatching { java.net.URI(it) }.getOrNull() } ?: return null
    if (uri.scheme != "arcane-mobile" || uri.host != "operations") return null
    val segments = uri.path.orEmpty().split('/').filter(String::isNotEmpty)
    return segments.takeIf { it.size == 2 && it[1] == "cancel" }
        ?.first()
        ?.takeIf(ACTIVITY_BATCH_ID_PATTERN::matches)
}

internal fun shouldSubmitSavedOperation(operationId: String?): Boolean = operationId == null
