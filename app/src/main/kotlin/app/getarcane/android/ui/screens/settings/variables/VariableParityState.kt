package app.getarcane.android.ui.screens.settings.variables

import app.getarcane.sdk.models.environment.Environment
import app.getarcane.sdk.models.role.Permission
import app.getarcane.sdk.models.user.User
import app.getarcane.sdk.models.user.hasPermission
import app.getarcane.sdk.models.variable.CreateGlobalVariableRequest
import app.getarcane.sdk.models.variable.EnvironmentSyncStatus
import app.getarcane.sdk.models.variable.GlobalVariable
import app.getarcane.sdk.models.variable.UpdateGlobalVariableRequest
import app.getarcane.sdk.models.variable.VariableSyncState
import kotlinx.coroutines.CancellationException
import java.util.Locale

internal data class VariablePermissionPolicy(
    val canRead: Boolean,
    val canCreate: Boolean,
    val canUpdate: Boolean,
    val canDelete: Boolean,
    val canSync: Boolean,
)

internal fun variablePermissionPolicy(user: User?): VariablePermissionPolicy = VariablePermissionPolicy(
    canRead = user?.hasPermission(Permission.Variables.READ) == true,
    canCreate = user?.hasPermission(Permission.Variables.CREATE) == true,
    canUpdate = user?.hasPermission(Permission.Variables.UPDATE) == true,
    canDelete = user?.hasPermission(Permission.Variables.DELETE) == true,
    canSync = user?.hasPermission(Permission.Variables.SYNC) == true,
)

/** A UI-safe projection. Secret values are discarded at the SDK/UI boundary. */
internal data class VariableDisplay(
    val id: String,
    val key: String,
    val visibleValue: String?,
    val isSecret: Boolean,
    val allEnvironments: Boolean,
    val environmentIds: Set<String>,
    val revision: String,
)

internal fun GlobalVariable.toVariableDisplay(): VariableDisplay = VariableDisplay(
    id = id,
    key = key,
    visibleValue = value.takeUnless { isSecret },
    isSecret = isSecret,
    allEnvironments = allEnvironments,
    environmentIds = environmentIds.toSet(),
    revision = listOf(
        updatedAt ?: createdAt,
        key,
        value.takeUnless { isSecret },
        isSecret,
        allEnvironments,
        environmentIds.toSet().sorted(),
    )
        .joinToString("|") { it.toString() },
)

internal fun filterVariables(
    variables: List<VariableDisplay>,
    query: String,
    environments: List<EnvironmentChoice> = emptyList(),
): List<VariableDisplay> {
    val needle = query.trim().lowercase(Locale.ROOT)
    if (needle.isEmpty()) return variables
    return variables.filter { variable ->
        val scopeKind = if (variable.allEnvironments) "all environments" else "selected environments"
        variable.key.lowercase(Locale.ROOT).contains(needle) ||
            (!variable.isSecret && variable.visibleValue.orEmpty().lowercase(Locale.ROOT).contains(needle)) ||
            scopeKind.contains(needle) ||
            variableScopeLabel(variable, environments).lowercase(Locale.ROOT).contains(needle)
    }
}

internal data class VariableScope(
    val allEnvironments: Boolean,
    val environmentIds: Set<String>,
) {
    fun normalized(): VariableScope = if (allEnvironments) {
        copy(environmentIds = emptySet())
    } else {
        copy(environmentIds = environmentIds.filterTo(linkedSetOf()) { it.isNotBlank() })
    }
}

internal data class VariableEditorDraft(
    val id: String? = null,
    val baselineRevision: String? = null,
    val key: String = "",
    val isSecret: Boolean = false,
    val originallySecret: Boolean = false,
    val allEnvironments: Boolean = true,
    val environmentIds: Set<String> = emptySet(),
)

internal fun VariableDisplay.toEditorDraft(): VariableEditorDraft = VariableEditorDraft(
    id = id,
    baselineRevision = revision,
    key = key,
    isSecret = isSecret,
    originallySecret = isSecret,
    allEnvironments = allEnvironments,
    environmentIds = environmentIds,
)

internal fun VariableDisplay.initialEditorValue(): String = visibleValue.orEmpty()

internal sealed interface VariableWritePlan {
    data class Create(val request: CreateGlobalVariableRequest) : VariableWritePlan
    data class Update(val id: String, val request: UpdateGlobalVariableRequest) : VariableWritePlan
}

internal fun variableWritePlan(
    draft: VariableEditorDraft,
    sensitiveValue: String,
): Result<VariableWritePlan> = runCatching {
    val key = draft.key.trim()
    require(key.isNotEmpty()) { "Variable name is required." }
    val scope = VariableScope(draft.allEnvironments, draft.environmentIds).normalized()
    require(scope.allEnvironments || scope.environmentIds.isNotEmpty()) {
        "Select at least one environment."
    }
    if (draft.id == null) {
        require(sensitiveValue.isNotEmpty()) { "Variable value is required." }
        VariableWritePlan.Create(
            CreateGlobalVariableRequest(
                key = key,
                value = sensitiveValue,
                isSecret = draft.isSecret,
                allEnvironments = scope.allEnvironments,
                environmentIds = scope.environmentIds.sorted(),
            ),
        )
    } else {
        require(draft.isSecret == draft.originallySecret || sensitiveValue.isNotEmpty()) {
            "Enter a replacement value when changing secret visibility."
        }
        VariableWritePlan.Update(
            draft.id,
            UpdateGlobalVariableRequest(
                key = key,
                // Secret values are write-only. Empty means preserve an existing secret.
                value = sensitiveValue.takeUnless {
                    it.isEmpty() && draft.originallySecret && draft.isSecret
                },
                isSecret = draft.isSecret,
                allEnvironments = scope.allEnvironments,
                environmentIds = scope.environmentIds.sorted(),
            ),
        )
    }
}

internal sealed interface VariableEditConflict {
    data class Changed(val latest: VariableDisplay) : VariableEditConflict
    data object Deleted : VariableEditConflict
}

internal fun concurrentVariableEdit(
    draft: VariableEditorDraft,
    current: List<VariableDisplay>,
): VariableEditConflict? {
    val id = draft.id ?: return null
    val latest = current.firstOrNull { it.id == id } ?: return VariableEditConflict.Deleted
    return if (latest.revision != draft.baselineRevision) VariableEditConflict.Changed(latest) else null
}

internal data class EnvironmentChoice(val id: String, val name: String)

internal fun environmentChoices(
    environments: List<Environment>,
    retainedIds: Set<String> = emptySet(),
): List<EnvironmentChoice> {
    val choices = environments
        .associateBy(Environment::id)
        .values
        .associate { it.id to EnvironmentChoice(it.id, it.name?.trim().orEmpty().ifEmpty { it.id }) }
        .toMutableMap()
    retainedIds.filter(String::isNotBlank).forEach { id -> choices.putIfAbsent(id, EnvironmentChoice(id, id)) }
    return choices.values.sortedWith(
        compareBy(String.CASE_INSENSITIVE_ORDER, EnvironmentChoice::name).thenBy(EnvironmentChoice::id),
    )
}

/** Keeps retained, no-longer-returned IDs while preferring current server display names. */
internal fun mergeEnvironmentChoices(
    authoritative: List<EnvironmentChoice>,
    retained: List<EnvironmentChoice>,
): List<EnvironmentChoice> =
    (retained + authoritative)
        .associateBy(EnvironmentChoice::id)
        .values
        .sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER, EnvironmentChoice::name)
                .thenBy(EnvironmentChoice::id),
        )

internal fun variableScopeLabel(
    variable: VariableDisplay,
    environments: List<EnvironmentChoice>,
): String {
    if (variable.allEnvironments) return "All environments"
    if (variable.environmentIds.isEmpty()) return "No environments"
    val namesById = environments.associate { it.id to it.name }
    return variable.environmentIds.sorted().joinToString(", ") { namesById[it] ?: it }
}

internal enum class SyncSummary { SYNCED, PENDING, PARTIAL, ERROR, UNKNOWN, TIMED_OUT }

internal fun summarizeSync(statuses: List<EnvironmentSyncStatus>, timedOut: Boolean = false): SyncSummary {
    if (timedOut && statuses.any { it.status == VariableSyncState.PENDING }) return SyncSummary.TIMED_OUT
    if (statuses.isEmpty()) return SyncSummary.UNKNOWN
    val states = statuses.map(EnvironmentSyncStatus::status).toSet()
    return when {
        states == setOf(VariableSyncState.SYNCED) -> SyncSummary.SYNCED
        states == setOf(VariableSyncState.PENDING) -> SyncSummary.PENDING
        states == setOf(VariableSyncState.ERROR) -> SyncSummary.ERROR
        VariableSyncState.ERROR in states -> SyncSummary.PARTIAL
        VariableSyncState.UNKNOWN in states && states.size > 1 -> SyncSummary.PARTIAL
        VariableSyncState.UNKNOWN in states -> SyncSummary.UNKNOWN
        VariableSyncState.PENDING in states -> SyncSummary.PENDING
        else -> SyncSummary.UNKNOWN
    }
}

internal data class SyncPollResult(
    val statuses: List<EnvironmentSyncStatus>,
    val summary: SyncSummary,
)

/** Bounded, cancellation-aware polling. The delay is injected to keep state tests deterministic. */
internal suspend fun pollVariableSync(
    initial: List<EnvironmentSyncStatus>,
    maxAttempts: Int = 30,
    isCurrent: () -> Boolean = { true },
    pause: suspend () -> Unit,
    loadStatus: suspend () -> List<EnvironmentSyncStatus>,
): SyncPollResult {
    require(maxAttempts >= 0)
    var latest = initial
    var attempts = 0
    try {
        while (
            latest.any { it.status == VariableSyncState.PENDING } &&
            attempts < maxAttempts &&
            isCurrent()
        ) {
            pause()
            if (!isCurrent()) break
            attempts++
            latest = try {
                loadStatus()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // A transient status-read failure must not turn a successful mutation into a
                // reported write failure. Keep the last outcome and spend one bounded attempt.
                latest
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    }
    val timedOut = latest.any { it.status == VariableSyncState.PENDING } && attempts >= maxAttempts
    return SyncPollResult(latest, summarizeSync(latest, timedOut))
}

internal data class SensitiveEditorState(
    val sessionKey: String,
    val editor: VariableEditorDraft? = null,
    val value: String = "",
) {
    fun bindSession(newSessionKey: String): SensitiveEditorState =
        if (newSessionKey == sessionKey) this else SensitiveEditorState(newSessionKey)

    fun close(): SensitiveEditorState = copy(editor = null, value = "")
    fun failed(): SensitiveEditorState = copy(value = "")
}

internal sealed interface SensitiveEditorEvent {
    data class SessionChanged(val sessionKey: String) : SensitiveEditorEvent
    data class Open(val draft: VariableEditorDraft, val initialValue: String) : SensitiveEditorEvent
    data class DraftChanged(val draft: VariableEditorDraft) : SensitiveEditorEvent
    data class ValueChanged(val value: String) : SensitiveEditorEvent
    data object Failed : SensitiveEditorEvent
    data object Closed : SensitiveEditorEvent
}

internal fun reduceSensitiveEditor(
    state: SensitiveEditorState,
    event: SensitiveEditorEvent,
): SensitiveEditorState = when (event) {
    is SensitiveEditorEvent.SessionChanged -> state.bindSession(event.sessionKey)
    is SensitiveEditorEvent.Open -> SensitiveEditorState(
        sessionKey = state.sessionKey,
        editor = event.draft,
        value = event.initialValue,
    )
    is SensitiveEditorEvent.DraftChanged -> {
        val old = state.editor
        val scopeOrSecretChanged = old == null ||
            old.isSecret != event.draft.isSecret ||
            old.allEnvironments != event.draft.allEnvironments ||
            old.environmentIds != event.draft.environmentIds
        state.copy(editor = event.draft, value = if (scopeOrSecretChanged) "" else state.value)
    }
    is SensitiveEditorEvent.ValueChanged -> state.copy(value = event.value)
    SensitiveEditorEvent.Failed -> state.failed()
    SensitiveEditorEvent.Closed -> state.close()
}

/** Accessibility projection deliberately omits every variable value. */
internal fun variableAccessibilityLabel(variable: VariableDisplay): String = buildString {
    append(variable.key)
    if (variable.isSecret) append(", secret variable")
    append(if (variable.allEnvironments) ", all environments" else ", selected environments")
}
