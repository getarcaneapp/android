package app.getarcane.android.ui.screens.settings.notifications

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.ui.graphics.vector.ImageVector
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneCyan
import app.getarcane.android.ui.theme.ArcaneGray
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneIndigo
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcanePurple
import app.getarcane.android.ui.theme.ArcaneTeal
import androidx.compose.ui.graphics.Color
import app.getarcane.sdk.models.base.JsonValue
import app.getarcane.sdk.models.notification.NotificationProvider

/** Display name for a provider. Mirrors iOS `NotificationProvider.displayName`. */
val NotificationProvider.displayName: String
    get() = when (this) {
        NotificationProvider.DISCORD -> "Discord"
        NotificationProvider.EMAIL -> "Email"
        NotificationProvider.TELEGRAM -> "Telegram"
        NotificationProvider.SIGNAL -> "Signal"
        NotificationProvider.SLACK -> "Slack"
        NotificationProvider.NTFY -> "Ntfy"
        NotificationProvider.PUSHOVER -> "Pushover"
        NotificationProvider.GOTIFY -> "Gotify"
        NotificationProvider.MATRIX -> "Matrix"
        NotificationProvider.GENERIC -> "Generic"
    }

/** Leading icon for a provider. Mirrors iOS `NotificationProvider.systemImage`. */
val NotificationProvider.iconVector: ImageVector
    get() = when (this) {
        NotificationProvider.DISCORD -> Icons.AutoMirrored.Filled.Chat
        NotificationProvider.EMAIL -> Icons.Filled.Email
        NotificationProvider.TELEGRAM -> Icons.AutoMirrored.Filled.Send
        NotificationProvider.SIGNAL -> Icons.Filled.Lock
        NotificationProvider.SLACK -> Icons.Filled.Numbers
        NotificationProvider.NTFY -> Icons.Filled.Notifications
        NotificationProvider.PUSHOVER -> Icons.Filled.PhoneIphone
        NotificationProvider.GOTIFY -> Icons.AutoMirrored.Filled.Send
        NotificationProvider.MATRIX -> Icons.Filled.Apps
        NotificationProvider.GENERIC -> Icons.Filled.Link
    }

/** Tint for a provider. Mirrors iOS `NotificationProvider.iconColor`. */
val NotificationProvider.iconTint: Color
    get() = when (this) {
        NotificationProvider.DISCORD -> ArcaneIndigo
        NotificationProvider.EMAIL -> ArcaneBlue
        NotificationProvider.TELEGRAM -> ArcaneCyan
        NotificationProvider.SIGNAL -> ArcaneBlue
        NotificationProvider.SLACK -> ArcanePurple
        NotificationProvider.NTFY -> ArcaneGreen
        NotificationProvider.PUSHOVER -> ArcaneTeal
        NotificationProvider.GOTIFY -> ArcaneOrange
        NotificationProvider.MATRIX -> ArcaneGreen
        NotificationProvider.GENERIC -> ArcaneGray
    }

// MARK: - Dynamic form field descriptors

enum class ProviderFieldKind { Text, Email, Password, Number, Url, Toggle, Textarea, Picker }

data class PickerOption(val label: String, val value: String)

data class ProviderFieldDescriptor(
    val key: String,
    val label: String,
    val placeholder: String = "",
    val kind: ProviderFieldKind = ProviderFieldKind.Text,
    val required: Boolean = false,
    val defaultValue: String = "",
    val pickerOptions: List<PickerOption> = emptyList(),
)

/** Field set per provider. Mirrors iOS `fieldsForProvider(_:)`. */
fun fieldsForProvider(provider: NotificationProvider): List<ProviderFieldDescriptor> = when (provider) {
    NotificationProvider.DISCORD -> listOf(
        ProviderFieldDescriptor("webhookUrl", "Webhook URL", "https://discord.com/api/webhooks/...", ProviderFieldKind.Url, required = true),
        ProviderFieldDescriptor("username", "Username", "Arcane Bot"),
        ProviderFieldDescriptor("avatarUrl", "Avatar URL", "https://...", ProviderFieldKind.Url),
    )
    NotificationProvider.EMAIL -> listOf(
        ProviderFieldDescriptor("smtpHost", "SMTP Host", "smtp.example.com", required = true),
        ProviderFieldDescriptor("smtpPort", "SMTP Port", "587", ProviderFieldKind.Number, required = true, defaultValue = "587"),
        ProviderFieldDescriptor("smtpUser", "Username", "user@example.com", ProviderFieldKind.Email),
        ProviderFieldDescriptor("smtpPassword", "Password", kind = ProviderFieldKind.Password),
        ProviderFieldDescriptor("from", "From Address", "arcane@example.com", ProviderFieldKind.Email, required = true),
        ProviderFieldDescriptor("to", "To Address(es)", "alerts@example.com (comma-separated for multiple)", required = true),
        ProviderFieldDescriptor("tls", "Use TLS", kind = ProviderFieldKind.Toggle, defaultValue = "true"),
    )
    NotificationProvider.TELEGRAM -> listOf(
        ProviderFieldDescriptor("botToken", "Bot Token", "123456:ABC...", ProviderFieldKind.Password, required = true),
        ProviderFieldDescriptor("chatId", "Chat ID", "-1001234567890", required = true),
    )
    NotificationProvider.SIGNAL -> listOf(
        ProviderFieldDescriptor("apiUrl", "Signal API URL", "https://signal.example.com", ProviderFieldKind.Url, required = true),
        ProviderFieldDescriptor("number", "Signal Number", "+1234567890", required = true),
        ProviderFieldDescriptor("recipients", "Recipients", "+1987654321 (comma-separated)", required = true),
    )
    NotificationProvider.SLACK -> listOf(
        ProviderFieldDescriptor("webhookUrl", "Webhook URL", "https://hooks.slack.com/services/...", ProviderFieldKind.Url, required = true),
        ProviderFieldDescriptor("channel", "Channel Override", "#alerts"),
        ProviderFieldDescriptor("username", "Username Override", "Arcane"),
    )
    NotificationProvider.NTFY -> listOf(
        ProviderFieldDescriptor("serverUrl", "Server URL", "https://ntfy.sh", ProviderFieldKind.Url, required = true, defaultValue = "https://ntfy.sh"),
        ProviderFieldDescriptor("topic", "Topic", "arcane-alerts", required = true),
        ProviderFieldDescriptor("username", "Username (optional)"),
        ProviderFieldDescriptor("password", "Password (optional)", kind = ProviderFieldKind.Password),
    )
    NotificationProvider.PUSHOVER -> listOf(
        ProviderFieldDescriptor("userKey", "User Key", required = true),
        ProviderFieldDescriptor("apiToken", "API Token", kind = ProviderFieldKind.Password, required = true),
        ProviderFieldDescriptor(
            "priority", "Priority", kind = ProviderFieldKind.Picker, defaultValue = "0",
            pickerOptions = listOf(
                PickerOption("Lowest", "-2"),
                PickerOption("Low", "-1"),
                PickerOption("Normal", "0"),
                PickerOption("High", "1"),
                PickerOption("Emergency", "2"),
            ),
        ),
    )
    NotificationProvider.GOTIFY -> listOf(
        ProviderFieldDescriptor("serverUrl", "Server URL", "https://gotify.example.com", ProviderFieldKind.Url, required = true),
        ProviderFieldDescriptor("token", "App Token", kind = ProviderFieldKind.Password, required = true),
        ProviderFieldDescriptor("priority", "Priority", kind = ProviderFieldKind.Number, defaultValue = "5"),
    )
    NotificationProvider.MATRIX -> listOf(
        ProviderFieldDescriptor("homeserverUrl", "Homeserver URL", "https://matrix.org", ProviderFieldKind.Url, required = true),
        ProviderFieldDescriptor("accessToken", "Access Token", kind = ProviderFieldKind.Password, required = true),
        ProviderFieldDescriptor("roomId", "Room ID", "!roomId:matrix.org", required = true),
    )
    NotificationProvider.GENERIC -> listOf(
        ProviderFieldDescriptor("url", "Webhook URL", kind = ProviderFieldKind.Url, required = true),
        ProviderFieldDescriptor(
            "method", "HTTP Method", kind = ProviderFieldKind.Picker, defaultValue = "POST",
            pickerOptions = listOf(
                PickerOption("POST", "POST"),
                PickerOption("PUT", "PUT"),
                PickerOption("PATCH", "PATCH"),
            ),
        ),
        ProviderFieldDescriptor("customHeaders", "Custom Headers", "key1:value1, key2:value2", ProviderFieldKind.Textarea),
    )
}

/** Event subscription flags. Mirrors iOS `EventSubscriptions`. */
data class EventSubscriptions(
    val imageUpdate: Boolean = true,
    val containerUpdate: Boolean = true,
    val vulnerabilityFound: Boolean = true,
    val pruneReport: Boolean = false,
    val autoHeal: Boolean = false,
) {
    fun get(key: String): Boolean = when (key) {
        "imageUpdate" -> imageUpdate
        "containerUpdate" -> containerUpdate
        "vulnerabilityFound" -> vulnerabilityFound
        "pruneReport" -> pruneReport
        "autoHeal" -> autoHeal
        else -> false
    }

    fun set(key: String, value: Boolean): EventSubscriptions = when (key) {
        "imageUpdate" -> copy(imageUpdate = value)
        "containerUpdate" -> copy(containerUpdate = value)
        "vulnerabilityFound" -> copy(vulnerabilityFound = value)
        "pruneReport" -> copy(pruneReport = value)
        "autoHeal" -> copy(autoHeal = value)
        else -> this
    }

    companion object {
        data class Key(val key: String, val label: String)

        val keys = listOf(
            Key("imageUpdate", "Image Updates"),
            Key("containerUpdate", "Container Updates"),
            Key("vulnerabilityFound", "Vulnerabilities"),
            Key("pruneReport", "Prune Reports"),
            Key("autoHeal", "Auto-Heal"),
        )

        /** Build an EventSubscriptions from the form's flat string map. Mirrors iOS `EventSubscriptions.from`. */
        fun from(values: Map<String, String>): EventSubscriptions = EventSubscriptions(
            imageUpdate = values["imageUpdate"]?.let { it == "true" } ?: true,
            containerUpdate = values["containerUpdate"]?.let { it == "true" } ?: true,
            vulnerabilityFound = values["vulnerabilityFound"]?.let { it == "true" } ?: true,
            pruneReport = values["pruneReport"]?.let { it == "true" } ?: false,
            autoHeal = values["autoHeal"]?.let { it == "true" } ?: false,
        )
    }
}

/** Flatten the SDK's tolerant config map into `[String:String]`. Mirrors iOS `extractConfigValues`. */
fun extractConfigValues(config: Map<String, JsonValue>): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    for ((key, value) in config) {
        when (value) {
            is JsonValue.Str -> result[key] = value.value
            is JsonValue.Bool -> result[key] = value.value.toString()
            is JsonValue.Number -> {
                val n = value.value
                result[key] = if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()
            }
            else -> Unit
        }
    }
    return result
}

/** Build the SDK config payload from the form's string map. Mirrors iOS `buildConfigPayload`. */
fun buildConfigPayload(
    values: Map<String, String>,
    provider: NotificationProvider,
    events: EventSubscriptions,
): Map<String, JsonValue> {
    val props = LinkedHashMap<String, JsonValue>()
    val fields = fieldsForProvider(provider)

    for ((key, value) in values) {
        if (value.isEmpty()) continue
        val field = fields.firstOrNull { it.key == key }
        props[key] = when (field?.kind) {
            ProviderFieldKind.Toggle -> JsonValue.Bool(value == "true")
            ProviderFieldKind.Number -> {
                val intVal = value.toIntOrNull()
                val dblVal = value.toDoubleOrNull()
                when {
                    intVal != null -> JsonValue.Number(intVal.toDouble())
                    dblVal != null -> JsonValue.Number(dblVal)
                    else -> JsonValue.Str(value)
                }
            }
            else -> JsonValue.Str(value)
        }
    }

    props["imageUpdate"] = JsonValue.Bool(events.imageUpdate)
    props["containerUpdate"] = JsonValue.Bool(events.containerUpdate)
    props["vulnerabilityFound"] = JsonValue.Bool(events.vulnerabilityFound)
    props["pruneReport"] = JsonValue.Bool(events.pruneReport)
    props["autoHeal"] = JsonValue.Bool(events.autoHeal)

    return props
}
