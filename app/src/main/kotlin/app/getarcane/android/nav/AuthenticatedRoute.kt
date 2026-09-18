package app.getarcane.android.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import app.getarcane.android.core.AuthStatus
import app.getarcane.android.core.ArcaneClientManager
import app.getarcane.android.core.sha256
import app.getarcane.sdk.EnvironmentId
import app.getarcane.sdk.models.role.Permission
import app.getarcane.sdk.models.user.hasPermission
import kotlinx.coroutines.CancellationException
import java.net.URI

enum class RouteDestination(val wire: String) {
    DASHBOARD("dashboard"),
    CONTAINERS("containers"),
    PROJECTS("projects"),
    ENVIRONMENT("environment"),
    CONTAINER("container"),
    PROJECT("project"),
    ACTIVITIES("activities"),
    ACTIVITY("activity"),
    OPERATIONS("operations"),
    OPERATION("operation"),
    ;

    companion object {
        fun fromWire(wire: String): RouteDestination? = entries.firstOrNull { it.wire == wire }
    }
}

data class AuthenticatedRoute(
    /** Null means the deliberate `current` binding allowed only for static, non-resource routes. */
    val serverBindingHash: String?,
    val destination: RouteDestination,
    val environmentId: String? = null,
    val resourceId: String? = null,
)

sealed interface RouteParseResult {
    data class Valid(val route: AuthenticatedRoute) : RouteParseResult
    data object NotOwned : RouteParseResult
    data class Invalid(val message: String) : RouteParseResult
}

object AuthenticatedRouteCodec {
    const val SCHEME = "arcane-mobile"
    const val HOST = "route"
    const val VERSION = "v1"
    const val MAXIMUM_URI_BYTES = 4_096
    const val MAXIMUM_ARGUMENT_BYTES = 512
    private const val EMPTY = "-"
    private const val CURRENT = "current"

    fun encode(route: AuthenticatedRoute): String {
        require(validateShape(route) == null)
        val server = route.serverBindingHash ?: CURRENT
        return "$SCHEME://$HOST/$VERSION/$server/${route.destination.wire}/" +
            "${encodeArgument(route.environmentId)}/${encodeArgument(route.resourceId)}"
    }

    fun parse(raw: String?): RouteParseResult {
        if (raw == null) return RouteParseResult.NotOwned
        if (raw.encodeToByteArray().size > MAXIMUM_URI_BYTES) {
            return RouteParseResult.Invalid("This Arcane link is too large.")
        }
        val uri = runCatching { URI(raw) }.getOrNull() ?: return RouteParseResult.NotOwned
        if (uri.scheme != SCHEME || uri.host != HOST) return RouteParseResult.NotOwned
        if (uri.rawQuery != null || uri.rawFragment != null || uri.userInfo != null || uri.port != -1) {
            return RouteParseResult.Invalid("This Arcane link has unsupported arguments.")
        }
        val segments = uri.rawPath.orEmpty().split('/').filter(String::isNotEmpty)
        if (segments.size != 5 || segments[0] != VERSION) {
            return RouteParseResult.Invalid("This Arcane link version is not supported.")
        }
        val binding = when (val value = segments[1]) {
            CURRENT -> null
            else -> value.takeIf(::isSha256)
                ?: return RouteParseResult.Invalid("This Arcane link has an invalid server identity.")
        }
        val destination = RouteDestination.fromWire(segments[2])
            ?: return RouteParseResult.Invalid("This Arcane destination is not supported.")
        val environment = decodeArgument(segments[3])
            ?: if (segments[3] == EMPTY) null else return RouteParseResult.Invalid("Invalid environment argument.")
        val resource = decodeArgument(segments[4])
            ?: if (segments[4] == EMPTY) null else return RouteParseResult.Invalid("Invalid resource argument.")
        val route = AuthenticatedRoute(binding, destination, environment, resource)
        return validateShape(route)?.let(RouteParseResult::Invalid) ?: RouteParseResult.Valid(route)
    }

    internal fun validateShape(route: AuthenticatedRoute): String? {
        if (route.serverBindingHash == null && route.destination !in STATIC_CURRENT_DESTINATIONS) {
            return "Resource links must identify their Arcane server."
        }
        if (route.environmentId?.encodeToByteArray()?.size ?: 0 > MAXIMUM_ARGUMENT_BYTES ||
            route.resourceId?.encodeToByteArray()?.size ?: 0 > MAXIMUM_ARGUMENT_BYTES
        ) return "This Arcane link contains an oversized argument."
        if (route.environmentId?.isBlank() == true || route.resourceId?.isBlank() == true) {
            return "This Arcane link contains an empty argument."
        }
        return when (route.destination) {
            RouteDestination.DASHBOARD,
            RouteDestination.OPERATIONS,
            -> if (route.environmentId != null || route.resourceId != null) "Unexpected route arguments." else null

            RouteDestination.CONTAINERS,
            RouteDestination.PROJECTS,
            RouteDestination.ACTIVITIES,
            -> if (route.resourceId != null) "Unexpected resource argument." else null

            RouteDestination.ENVIRONMENT ->
                if (route.environmentId == null || route.resourceId != null) "An environment is required." else null

            RouteDestination.CONTAINER,
            RouteDestination.PROJECT,
            RouteDestination.ACTIVITY,
            -> if (route.environmentId == null || route.resourceId == null) "Environment and resource are required." else null

            RouteDestination.OPERATION ->
                if (route.environmentId != null || route.resourceId == null) "An operation is required." else null
        }
    }

    private fun encodeArgument(value: String?): String = value?.encodeToByteArray()?.joinToString("") {
        "%02x".format(it)
    } ?: EMPTY

    private fun decodeArgument(value: String): String? {
        if (value == EMPTY) return null
        if (value.length % 2 != 0 || value.length > MAXIMUM_ARGUMENT_BYTES * 2 ||
            value.any { it !in '0'..'9' && it !in 'a'..'f' }
        ) return null
        val bytes = ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
        return runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }.getOrNull()
    }

    private fun isSha256(value: String): Boolean = value.length == 64 && value.all {
        it in '0'..'9' || it in 'a'..'f'
    }

    private val STATIC_CURRENT_DESTINATIONS = setOf(
        RouteDestination.DASHBOARD,
        RouteDestination.CONTAINERS,
        RouteDestination.PROJECTS,
        RouteDestination.ACTIVITIES,
        RouteDestination.OPERATIONS,
    )
}

sealed interface AuthenticatedRouteResolution {
    data class Ready(val route: AuthenticatedRoute) : AuthenticatedRouteResolution
    data class Rejected(val message: String) : AuthenticatedRouteResolution
    data object LoginRequired : AuthenticatedRouteResolution
}

class AuthenticatedRouteCoordinator {
    var pendingRoute by mutableStateOf<AuthenticatedRoute?>(null)
        private set
    var issueMessage by mutableStateOf<String?>(null)
        private set

    fun submit(route: AuthenticatedRoute) {
        pendingRoute = route
        issueMessage = null
    }

    fun reject(message: String) {
        pendingRoute = null
        issueMessage = message
    }

    fun consume(route: AuthenticatedRoute) {
        if (pendingRoute == route) pendingRoute = null
    }

    fun consumeIssue() {
        issueMessage = null
    }
}

val LocalAuthenticatedRouteCoordinator = staticCompositionLocalOf<AuthenticatedRouteCoordinator> {
    error("AuthenticatedRouteCoordinator not provided")
}

suspend fun resolveAuthenticatedRoute(
    route: AuthenticatedRoute,
    manager: ArcaneClientManager,
): AuthenticatedRouteResolution {
    if (manager.authStatus != AuthStatus.AUTHENTICATED) return AuthenticatedRouteResolution.LoginRequired
    AuthenticatedRouteCodec.validateShape(route)?.let {
        return AuthenticatedRouteResolution.Rejected(it)
    }
    val client = manager.client ?: return AuthenticatedRouteResolution.LoginRequired
    val user = manager.currentUser ?: return AuthenticatedRouteResolution.LoginRequired
    val currentServerHash = sha256(manager.serverSessionIdentity)
    if (route.serverBindingHash != null && route.serverBindingHash != currentServerHash) {
        return AuthenticatedRouteResolution.Rejected(
            "This link belongs to another Arcane server. Change Server or open a link for this server.",
        )
    }

    val environmentId = route.environmentId ?: manager.activeEnvironmentId.rawValue
    val accessSurfaceId = route.destination.accessSurfaceId
    if (accessSurfaceId != null && !manager.canAccessSurface(accessSurfaceId, environmentId)) {
        return AuthenticatedRouteResolution.Rejected("You no longer have permission to open this destination.")
    }
    val permission = route.destination.requiredPermission
    if (permission != null && user.permissionsByEnv != null && !user.hasPermission(permission, environmentId)) {
        return AuthenticatedRouteResolution.Rejected("You no longer have permission to open this destination.")
    }
    if (route.destination in ACTIVITY_DESTINATIONS && !manager.capabilities.supportsActivities) {
        return AuthenticatedRouteResolution.Rejected("Activities are not supported by this Arcane server.")
    }
    if (route.destination == RouteDestination.OPERATION &&
        !manager.canOpenOperationRoute(requireNotNull(route.resourceId))
    ) {
        return AuthenticatedRouteResolution.Rejected(
            "That operation is no longer available for this account and server.",
        )
    }

    try {
        if (route.destination.requiresEnvironment || route.environmentId != null) {
            val environment = client.environments.get(EnvironmentId(environmentId))
            if (!environment.enabled) {
                return AuthenticatedRouteResolution.Rejected("That environment is disabled. Choose an available environment.")
            }
        }
        when (route.destination) {
            RouteDestination.CONTAINER -> client.containers.inspect(
                envId = EnvironmentId(environmentId),
                id = requireNotNull(route.resourceId),
            )
            RouteDestination.PROJECT -> client.projects.get(
                envId = EnvironmentId(environmentId),
                projectId = requireNotNull(route.resourceId),
            )
            RouteDestination.ACTIVITY -> client.activities.detail(
                envId = EnvironmentId(environmentId),
                activityId = requireNotNull(route.resourceId),
                limit = 1,
            )
            else -> Unit
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        return AuthenticatedRouteResolution.Rejected(
            "This destination could not be verified. It may be stale, deleted, offline, or no longer authorized.",
        )
    }
    return AuthenticatedRouteResolution.Ready(route)
}

private val RouteDestination.requiresEnvironment: Boolean
    get() = this in setOf(
        RouteDestination.CONTAINERS,
        RouteDestination.PROJECTS,
        RouteDestination.ENVIRONMENT,
        RouteDestination.CONTAINER,
        RouteDestination.PROJECT,
        RouteDestination.ACTIVITY,
    )

private val RouteDestination.requiredPermission: String?
    get() = when (this) {
        RouteDestination.DASHBOARD -> Permission.Dashboard.READ
        RouteDestination.CONTAINERS -> Permission.Containers.LIST
        RouteDestination.CONTAINER -> Permission.Containers.READ
        RouteDestination.PROJECTS -> Permission.Projects.LIST
        RouteDestination.PROJECT -> Permission.Projects.READ
        RouteDestination.ENVIRONMENT -> Permission.Environments.READ
        RouteDestination.ACTIVITIES -> "activities:list"
        RouteDestination.ACTIVITY -> "activities:read"
        RouteDestination.OPERATIONS,
        RouteDestination.OPERATION,
        -> null
    }

private val RouteDestination.accessSurfaceId: String?
    get() = when (this) {
        RouteDestination.DASHBOARD -> "route.dashboard"
        RouteDestination.CONTAINERS -> "route.containers"
        RouteDestination.CONTAINER -> "route.containers.detail"
        RouteDestination.PROJECTS -> "route.projects"
        RouteDestination.PROJECT -> "route.projects.detail"
        RouteDestination.ENVIRONMENT -> "route.environments.detail"
        RouteDestination.ACTIVITIES,
        RouteDestination.ACTIVITY,
        -> "route.activities"
        RouteDestination.OPERATIONS,
        RouteDestination.OPERATION,
        -> null
    }

private val ACTIVITY_DESTINATIONS = setOf(RouteDestination.ACTIVITIES, RouteDestination.ACTIVITY)
