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
        NotificationProvider.GOOGLE_CHAT -> "Google Chat"
        NotificationProvider.GENERIC -> "Generic"
        NotificationProvider.UNKNOWN -> "Unsupported Provider"
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
        NotificationProvider.GOOGLE_CHAT -> Icons.AutoMirrored.Filled.Chat
        NotificationProvider.GENERIC -> Icons.Filled.Link
        NotificationProvider.UNKNOWN -> Icons.Filled.Link
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
        NotificationProvider.GOOGLE_CHAT -> ArcaneBlue
        NotificationProvider.GENERIC -> ArcaneGray
        NotificationProvider.UNKNOWN -> ArcaneGray
    }

// MARK: - Dynamic form field descriptors

enum class ProviderFieldKind { Text, Email, Password, Number, Url, Toggle, Textarea, Picker }

enum class ProviderFieldEncoding { Scalar, StringList, StringMap }

data class PickerOption(val label: String, val value: String)

data class ProviderFieldDescriptor(
    val key: String,
    val label: String,
    val placeholder: String = "",
    val kind: ProviderFieldKind = ProviderFieldKind.Text,
    val required: Boolean = false,
    val defaultValue: String = "",
    val pickerOptions: List<PickerOption> = emptyList(),
    val encoding: ProviderFieldEncoding = ProviderFieldEncoding.Scalar,
    val credential: Boolean = false,
    val serializeAsNumber: Boolean = false,
)

/** Field set per provider. Mirrors iOS `fieldsForProvider(_:)`. */
fun fieldsForProvider(
    provider: NotificationProvider,
    supportsPost26Features: Boolean = true,
): List<ProviderFieldDescriptor> = when (provider) {
    NotificationProvider.DISCORD -> listOf(
        ProviderFieldDescriptor("webhookId", "Webhook ID", required = true),
        ProviderFieldDescriptor("token", "Webhook Token", kind = ProviderFieldKind.Password, required = true, credential = true),
        ProviderFieldDescriptor("username", "Username", "Arcane", defaultValue = "Arcane"),
        ProviderFieldDescriptor("avatarUrl", "Avatar URL", "https://...", ProviderFieldKind.Url),
    )
    NotificationProvider.EMAIL -> listOf(
        ProviderFieldDescriptor("smtpHost", "SMTP Host", "smtp.example.com", required = true),
        ProviderFieldDescriptor("smtpPort", "SMTP Port", "587", ProviderFieldKind.Number, required = true, defaultValue = "587"),
        ProviderFieldDescriptor("smtpUsername", "SMTP Username", "user@example.com"),
        ProviderFieldDescriptor("smtpPassword", "SMTP Password", kind = ProviderFieldKind.Password, credential = true),
        ProviderFieldDescriptor("fromAddress", "From Address", "arcane@example.com", ProviderFieldKind.Email, required = true),
        ProviderFieldDescriptor("toAddresses", "To Address(es)", "alerts@example.com, ops@example.com", required = true, encoding = ProviderFieldEncoding.StringList),
        ProviderFieldDescriptor(
            "tlsMode", "TLS Mode", kind = ProviderFieldKind.Picker, defaultValue = "starttls",
            pickerOptions = listOf(PickerOption("None", "none"), PickerOption("STARTTLS", "starttls"), PickerOption("SSL/TLS", "ssl")),
        ),
        ProviderFieldDescriptor(
            "authMode", "Authentication", kind = ProviderFieldKind.Picker, defaultValue = "auto",
            pickerOptions = listOf(
                PickerOption("None", "none"), PickerOption("Auto", "auto"), PickerOption("PLAIN", "plain"),
                PickerOption("LOGIN", "login"), PickerOption("CRAM-MD5", "crammd5"),
            ),
        ),
    )
    NotificationProvider.TELEGRAM -> listOf(
        ProviderFieldDescriptor("botToken", "Bot Token", "123456:ABC...", ProviderFieldKind.Password, required = true, credential = true),
        ProviderFieldDescriptor("chatIds", "Chat ID(s)", "-1001234567890, 123456789", required = true, encoding = ProviderFieldEncoding.StringList),
        ProviderFieldDescriptor("preview", "Link Preview", kind = ProviderFieldKind.Toggle, defaultValue = "true"),
        ProviderFieldDescriptor("notification", "Notification Sound", kind = ProviderFieldKind.Toggle, defaultValue = "true"),
        ProviderFieldDescriptor("parseMode", "Parse Mode"),
        ProviderFieldDescriptor("title", "Title"),
    )
    NotificationProvider.SIGNAL -> listOf(
        ProviderFieldDescriptor("host", "Host", "localhost", required = true, defaultValue = "localhost"),
        ProviderFieldDescriptor("port", "Port", "8080", ProviderFieldKind.Number, required = true, defaultValue = "8080"),
        ProviderFieldDescriptor("user", "Username"),
        ProviderFieldDescriptor("password", "Password", kind = ProviderFieldKind.Password, credential = true),
        ProviderFieldDescriptor("token", "Token", kind = ProviderFieldKind.Password, credential = true),
        ProviderFieldDescriptor("source", "Source Number", "+1234567890", required = true),
        ProviderFieldDescriptor("recipients", "Recipients", "+1987654321, +1123456789", required = true, encoding = ProviderFieldEncoding.StringList),
        ProviderFieldDescriptor("disableTls", "Disable TLS", kind = ProviderFieldKind.Toggle),
    )
    NotificationProvider.SLACK -> listOf(
        ProviderFieldDescriptor("token", "Bot Token", kind = ProviderFieldKind.Password, required = true, credential = true),
        ProviderFieldDescriptor("botName", "Bot Name", "Arcane", defaultValue = "Arcane"),
        ProviderFieldDescriptor("icon", "Icon"),
        ProviderFieldDescriptor("color", "Color"),
        ProviderFieldDescriptor("title", "Title"),
        ProviderFieldDescriptor("channel", "Channel"),
        ProviderFieldDescriptor("threadTs", "Thread Timestamp"),
    )
    NotificationProvider.NTFY -> listOf(
        ProviderFieldDescriptor("host", "Host", "ntfy.sh", required = true, defaultValue = "ntfy.sh"),
        ProviderFieldDescriptor("port", "Port", "0", ProviderFieldKind.Number, defaultValue = "0"),
        ProviderFieldDescriptor("topic", "Topic", "arcane-alerts", required = true),
        ProviderFieldDescriptor("username", "Username"),
        ProviderFieldDescriptor("password", "Password", kind = ProviderFieldKind.Password, credential = true),
        ProviderFieldDescriptor("title", "Title"),
        ProviderFieldDescriptor("priority", "Priority", defaultValue = "default"),
        ProviderFieldDescriptor("tags", "Tags", "warning, whale", encoding = ProviderFieldEncoding.StringList),
        ProviderFieldDescriptor("icon", "Icon URL", kind = ProviderFieldKind.Url),
        ProviderFieldDescriptor("cache", "Cache Message", kind = ProviderFieldKind.Toggle, defaultValue = "true"),
        ProviderFieldDescriptor("firebase", "Forward to Firebase", kind = ProviderFieldKind.Toggle, defaultValue = "true"),
        ProviderFieldDescriptor("disableTls", "Disable TLS", kind = ProviderFieldKind.Toggle),
        ProviderFieldDescriptor("disableTlsVerification", "Disable TLS Verification", kind = ProviderFieldKind.Toggle),
    )
    NotificationProvider.PUSHOVER -> listOf(
        ProviderFieldDescriptor("token", "API Token", kind = ProviderFieldKind.Password, required = true, credential = true),
        ProviderFieldDescriptor("user", "User Key", required = true),
        ProviderFieldDescriptor("devices", "Devices", encoding = ProviderFieldEncoding.StringList),
        ProviderFieldDescriptor(
            "priority", "Priority", kind = ProviderFieldKind.Picker, defaultValue = "0",
            pickerOptions = listOf(
                PickerOption("Lowest", "-2"),
                PickerOption("Low", "-1"),
                PickerOption("Normal", "0"),
                PickerOption("High", "1"),
                PickerOption("Emergency", "2"),
            ),
            serializeAsNumber = true,
        ),
        ProviderFieldDescriptor("title", "Title"),
    )
    NotificationProvider.GOTIFY -> listOf(
        ProviderFieldDescriptor("host", "Host", "gotify.example.com", required = true),
        ProviderFieldDescriptor("port", "Port", "0", ProviderFieldKind.Number, defaultValue = "0"),
        ProviderFieldDescriptor("token", "App Token", kind = ProviderFieldKind.Password, required = true, credential = true),
        ProviderFieldDescriptor("path", "Path"),
        ProviderFieldDescriptor("priority", "Priority", kind = ProviderFieldKind.Number, defaultValue = "0"),
        ProviderFieldDescriptor("title", "Title"),
        ProviderFieldDescriptor("disableTls", "Disable TLS", kind = ProviderFieldKind.Toggle),
        ProviderFieldDescriptor("insecureSkipVerify", "Skip TLS Verification", kind = ProviderFieldKind.Toggle),
        ProviderFieldDescriptor("useHeader", "Send Token in Header", kind = ProviderFieldKind.Toggle),
    )
    NotificationProvider.MATRIX -> listOf(
        ProviderFieldDescriptor("host", "Host", "matrix.org", required = true),
        ProviderFieldDescriptor("port", "Port", "0", ProviderFieldKind.Number, defaultValue = "0"),
        ProviderFieldDescriptor("rooms", "Room ID(s)", "!roomId:matrix.org", required = true),
        ProviderFieldDescriptor("username", "Username"),
        ProviderFieldDescriptor("password", "Password", kind = ProviderFieldKind.Password, credential = true),
        ProviderFieldDescriptor("disableTlsVerification", "Disable TLS Verification", kind = ProviderFieldKind.Toggle),
    )
    NotificationProvider.GOOGLE_CHAT -> listOf(
        ProviderFieldDescriptor("webhookUrl", "Webhook URL", "https://chat.googleapis.com/...", ProviderFieldKind.Password, required = true, credential = true),
    )
    NotificationProvider.GENERIC -> listOf(
        ProviderFieldDescriptor("webhookUrl", "Webhook URL", kind = ProviderFieldKind.Url, required = true),
        ProviderFieldDescriptor(
            "method", "HTTP Method", kind = ProviderFieldKind.Picker, defaultValue = "POST",
            pickerOptions = listOf(
                PickerOption("POST", "POST"),
                PickerOption("PUT", "PUT"),
                PickerOption("PATCH", "PATCH"),
            ),
        ),
        ProviderFieldDescriptor("contentType", "Content Type", defaultValue = "application/json"),
        ProviderFieldDescriptor("titleKey", "Title Key", defaultValue = "title"),
        ProviderFieldDescriptor("messageKey", "Message Key", defaultValue = "message"),
        ProviderFieldDescriptor("customHeaders", "Custom Headers", "key1:value1\nkey2:value2", ProviderFieldKind.Textarea, encoding = ProviderFieldEncoding.StringMap),
        ProviderFieldDescriptor("disableTls", "Disable TLS", kind = ProviderFieldKind.Toggle),
        ProviderFieldDescriptor("successBodyContains", "Success Body Contains"),
        ProviderFieldDescriptor("payloadTemplate", "Payload Template", kind = ProviderFieldKind.Textarea),
    )
    NotificationProvider.UNKNOWN -> emptyList()
}.let { fields ->
    if (provider != NotificationProvider.GENERIC || supportsPost26Features) {
        fields
    } else {
        fields.filterNot { it.key in post26GenericFieldKeys }
    }
}

private val post26GenericFieldKeys = setOf(
    "contentType",
    "titleKey",
    "messageKey",
    "successBodyContains",
    "payloadTemplate",
)

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
        data class Key(val key: String, val wireKey: String, val label: String)

        val keys = listOf(
            Key("imageUpdate", "image_update", "Image Updates"),
            Key("containerUpdate", "container_update", "Container Updates"),
            Key("vulnerabilityFound", "vulnerability_found", "Vulnerabilities"),
            Key("pruneReport", "prune_report", "Prune Reports"),
            Key("autoHeal", "auto_heal", "Auto-Heal"),
        )

        /** Read nested `config.events`, using the current iOS defaults for missing legacy flags. */
        fun from(config: Map<String, JsonValue>): EventSubscriptions {
            val values = (config["events"] as? JsonValue.Obj)?.value.orEmpty()
            fun value(wireKey: String, default: Boolean) =
                (values[wireKey] as? JsonValue.Bool)?.value ?: default
            return EventSubscriptions(
                imageUpdate = value("image_update", true),
                containerUpdate = value("container_update", true),
                vulnerabilityFound = value("vulnerability_found", true),
                pruneReport = value("prune_report", false),
                autoHeal = value("auto_heal", false),
            )
        }
    }
}

fun hasNotificationProviderChanges(
    values: Map<String, String>,
    enabled: Boolean,
    events: EventSubscriptions,
    originalValues: Map<String, String>,
    originalEnabled: Boolean,
    originalEvents: EventSubscriptions,
): Boolean = values != originalValues || enabled != originalEnabled || events != originalEvents

/** Flatten provider fields into editable strings while leaving `config.events` nested. */
fun extractConfigValues(config: Map<String, JsonValue>): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    for ((key, value) in config) {
        if (key == "events") continue
        when (value) {
            is JsonValue.Str -> result[key] = value.value
            is JsonValue.Bool -> result[key] = value.value.toString()
            is JsonValue.Number -> {
                val n = value.value
                result[key] = if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()
            }
            is JsonValue.Arr -> result[key] = value.value.mapNotNull { (it as? JsonValue.Str)?.value }.joinToString(", ")
            is JsonValue.Obj -> result[key] = value.value.entries
                .mapNotNull { (mapKey, mapValue) -> (mapValue as? JsonValue.Str)?.value?.let { "$mapKey:$it" } }
                .joinToString("\n")
            else -> Unit
        }
    }
    return result
}

/** Credential keys present in a redacted response can be left blank and preserved by the server. */
fun preservedCredentialKeys(
    config: Map<String, JsonValue>,
    provider: NotificationProvider,
): Set<String> = fieldsForProvider(provider)
    .filter { it.credential && config.containsKey(it.key) }
    .mapTo(LinkedHashSet()) { it.key }

/** Validate required fields without forcing an existing redacted credential to be re-entered. */
fun isProviderFormValid(
    values: Map<String, String>,
    provider: NotificationProvider,
    enabled: Boolean,
    preservedCredentials: Set<String> = emptySet(),
    supportsPost26Features: Boolean = true,
): Boolean {
    if (!enabled) return true
    val fields = fieldsForProvider(provider, supportsPost26Features)
    val requiredFieldsValid = fields.filter { it.required }.all { field ->
        !values[field.key].isNullOrBlank() || (field.credential && field.key in preservedCredentials)
    }
    val numberFieldsValid = fields.filter { it.kind == ProviderFieldKind.Number }.all { field ->
        values[field.key].isNullOrBlank() || values[field.key]?.toIntOrNull() != null
    }
    val providerCredentialsValid = when (provider) {
        NotificationProvider.SIGNAL -> {
            val hasUser = !values["user"].isNullOrBlank()
            val hasPassword = !values["password"].isNullOrBlank() || "password" in preservedCredentials
            val wantsBasicAuth = hasUser || !values["password"].isNullOrBlank()
            val hasBasicAuth = hasUser && hasPassword
            val hasTokenAuth = !values["token"].isNullOrBlank() || (!wantsBasicAuth && "token" in preservedCredentials)
            hasBasicAuth.xor(hasTokenAuth)
        }
        else -> true
    }
    return requiredFieldsValid && numberFieldsValid && providerCredentialsValid
}

/** Build the SDK config payload using the server's exact value shapes and event keys. */
fun buildConfigPayload(
    values: Map<String, String>,
    provider: NotificationProvider,
    events: EventSubscriptions,
    baseConfig: Map<String, JsonValue> = emptyMap(),
    supportsPost26Features: Boolean = true,
): Map<String, JsonValue> {
    val props = LinkedHashMap(baseConfig)
    val fields = fieldsForProvider(provider, supportsPost26Features)

    for (field in fields) {
        val key = field.key
        val value = values[key].orEmpty()
        if (value.isBlank()) {
            if (!field.credential || !baseConfig.containsKey(key)) props.remove(key)
            continue
        }
        props[key] = when (field.encoding) {
            ProviderFieldEncoding.StringList -> JsonValue.Arr(
                value.split(',').mapNotNull { item ->
                    item.trim().takeIf(String::isNotEmpty)?.let(JsonValue::Str)
                },
            )
            ProviderFieldEncoding.StringMap -> JsonValue.Obj(
                value.split(if ('\n' in value) '\n' else ',').mapNotNull { entry ->
                    val separator = entry.indexOf(':')
                    if (separator <= 0) return@mapNotNull null
                    val mapKey = entry.substring(0, separator).trim()
                    val mapValue = entry.substring(separator + 1).trim()
                    mapKey.takeIf(String::isNotEmpty)?.let { it to JsonValue.Str(mapValue) }
                }.toMap(LinkedHashMap()),
            )
            ProviderFieldEncoding.Scalar -> when (field.kind) {
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
            else -> if (field.serializeAsNumber) {
                JsonValue.Number(value.toDouble())
            } else {
                JsonValue.Str(value)
            }
            }
        }
    }

    props["events"] = JsonValue.Obj(
        linkedMapOf(
            "image_update" to JsonValue.Bool(events.imageUpdate),
            "container_update" to JsonValue.Bool(events.containerUpdate),
            "vulnerability_found" to JsonValue.Bool(events.vulnerabilityFound),
            "prune_report" to JsonValue.Bool(events.pruneReport),
            "auto_heal" to JsonValue.Bool(events.autoHeal),
        ),
    )

    return props
}
