package app.getarcane.android.ui.screens.settings.rbac

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PermIdentity
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneGray
import app.getarcane.android.ui.theme.ArcaneIndigo
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcanePurple
import app.getarcane.android.ui.theme.ArcaneTeal
import app.getarcane.sdk.models.role.Role

/** Display name shown in UI; falls back to the role ID. Mirrors iOS `Role.displayName`. */
val Role.displayNameOrId: String get() = name.ifEmpty { id }

/** Icon selected by built-in role ID, falling back to a generic person card. Mirrors iOS `Role.systemImage`. */
val Role.iconVector: ImageVector
    get() = when (id) {
        Role.BuiltIn.ADMIN -> Icons.Filled.Shield
        Role.BuiltIn.EDITOR, Role.BuiltIn.NO_SHELL_EDITOR -> Icons.Filled.Edit
        Role.BuiltIn.DEPLOYER -> Icons.Filled.Inventory2
        Role.BuiltIn.MONITOR -> Icons.Filled.RemoveRedEye
        Role.BuiltIn.VIEWER -> Icons.Filled.Description
        else -> Icons.Filled.PermIdentity
    }

/** Tint selected by built-in role ID. Mirrors iOS `Role.iconColor`. */
val Role.iconTint: Color
    get() = when (id) {
        Role.BuiltIn.ADMIN -> ArcaneIndigo
        Role.BuiltIn.EDITOR, Role.BuiltIn.NO_SHELL_EDITOR -> ArcaneBlue
        Role.BuiltIn.DEPLOYER -> ArcaneOrange
        Role.BuiltIn.MONITOR -> ArcaneTeal
        Role.BuiltIn.VIEWER -> ArcaneGray
        else -> ArcanePurple
    }

/** Render the v2 validation field map ("field: messages") as a single string. Mirrors iOS `formatValidationFields`. */
fun formatValidationFields(fields: Map<String, List<String>>): String =
    fields.entries
        .sortedBy { it.key }
        .joinToString("\n") { (key, messages) -> "$key: ${messages.joinToString(", ")}" }

/** One-line label for a permission scope. Mirrors iOS `displayScopeLabel`. */
fun displayScopeLabel(environmentId: String?, environments: List<app.getarcane.sdk.models.environment.Environment>): String {
    if (environmentId == null) return "Global"
    val env = environments.firstOrNull { it.id == environmentId }
    return env?.name ?: "Environment $environmentId"
}
