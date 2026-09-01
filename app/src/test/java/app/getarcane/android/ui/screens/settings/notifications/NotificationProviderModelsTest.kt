package app.getarcane.android.ui.screens.settings.notifications

import app.getarcane.sdk.models.base.JsonValue
import app.getarcane.sdk.models.notification.NotificationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationProviderModelsTest {
    @Test
    fun `older servers hide Google Chat`() {
        assertFalse(notificationProviders(supportsPost26Features = false).contains(NotificationProvider.GOOGLE_CHAT))
        assertTrue(notificationProviders(supportsPost26Features = true).contains(NotificationProvider.GOOGLE_CHAT))
        assertFalse(notificationProviders(supportsPost26Features = true).contains(NotificationProvider.UNKNOWN))
    }

    @Test
    fun `older servers omit post 2_6 generic fields`() {
        val fields = fieldsForProvider(
            provider = NotificationProvider.GENERIC,
            supportsPost26Features = false,
        )

        assertEquals(
            listOf("webhookUrl", "method", "customHeaders", "disableTls"),
            fields.map { it.key },
        )
        val payload = buildConfigPayload(
            values = mapOf(
                "webhookUrl" to "https://example.com/hook",
                "method" to "POST",
                "contentType" to "application/json",
                "titleKey" to "title",
                "messageKey" to "message",
                "successBodyContains" to "ok",
                "payloadTemplate" to "{{ title }}",
            ),
            provider = NotificationProvider.GENERIC,
            events = EventSubscriptions(),
            supportsPost26Features = false,
        )

        assertFalse(payload.containsKey("contentType"))
        assertFalse(payload.containsKey("titleKey"))
        assertFalse(payload.containsKey("messageKey"))
        assertFalse(payload.containsKey("successBodyContains"))
        assertFalse(payload.containsKey("payloadTemplate"))
    }

    @Test
    fun `provider field keys match Arcane v2_10 notification configs`() {
        val expected = mapOf(
            NotificationProvider.DISCORD to listOf("webhookId", "token", "username", "avatarUrl"),
            NotificationProvider.EMAIL to listOf("smtpHost", "smtpPort", "smtpUsername", "smtpPassword", "fromAddress", "toAddresses", "tlsMode", "authMode"),
            NotificationProvider.TELEGRAM to listOf("botToken", "chatIds", "preview", "notification", "parseMode", "title"),
            NotificationProvider.SIGNAL to listOf("host", "port", "user", "password", "token", "source", "recipients", "disableTls"),
            NotificationProvider.SLACK to listOf("token", "botName", "icon", "color", "title", "channel", "threadTs"),
            NotificationProvider.NTFY to listOf("host", "port", "topic", "username", "password", "title", "priority", "tags", "icon", "cache", "firebase", "disableTls", "disableTlsVerification"),
            NotificationProvider.PUSHOVER to listOf("token", "user", "devices", "priority", "title"),
            NotificationProvider.GOTIFY to listOf("host", "port", "token", "path", "priority", "title", "disableTls", "insecureSkipVerify", "useHeader"),
            NotificationProvider.MATRIX to listOf("host", "port", "rooms", "username", "password", "disableTlsVerification"),
            NotificationProvider.GOOGLE_CHAT to listOf("webhookUrl"),
            NotificationProvider.GENERIC to listOf("webhookUrl", "method", "contentType", "titleKey", "messageKey", "customHeaders", "disableTls", "successBodyContains", "payloadTemplate"),
        )

        expected.forEach { (provider, keys) ->
            assertEquals(provider.wire, keys, fieldsForProvider(provider).map { it.key })
        }
    }

    @Test
    fun `Google Chat uses the iOS parity label and treats its redacted webhook as a credential`() {
        assertEquals("Google Chat", NotificationProvider.GOOGLE_CHAT.displayName)
        assertEquals(
            listOf(
                ProviderFieldDescriptor(
                    key = "webhookUrl",
                    label = "Webhook URL",
                    placeholder = "https://chat.googleapis.com/...",
                    kind = ProviderFieldKind.Password,
                    required = true,
                    credential = true,
                ),
            ),
            fieldsForProvider(NotificationProvider.GOOGLE_CHAT),
        )
    }

    @Test
    fun `events decode from nested snake case object with iOS legacy defaults`() {
        val config = mapOf(
            "events" to JsonValue.Obj(
                mapOf(
                    "image_update" to JsonValue.Bool(false),
                    "prune_report" to JsonValue.Bool(false),
                ),
            ),
        )

        assertEquals(
            EventSubscriptions(
                imageUpdate = false,
                containerUpdate = true,
                vulnerabilityFound = true,
                pruneReport = false,
                autoHeal = false,
            ),
            EventSubscriptions.from(config),
        )
    }

    @Test
    fun `new provider form is clean until a value changes`() {
        val values = mapOf("username" to "Arcane")
        val events = EventSubscriptions()

        assertFalse(
            hasNotificationProviderChanges(
                values = values,
                enabled = false,
                events = events,
                originalValues = values,
                originalEnabled = false,
                originalEvents = events,
            ),
        )
        assertTrue(
            hasNotificationProviderChanges(
                values = values,
                enabled = true,
                events = events,
                originalValues = values,
                originalEnabled = false,
                originalEvents = events,
            ),
        )
    }

    @Test
    fun `email payload uses typed arrays nested events and preserves redacted credentials`() {
        val base = mapOf(
            "smtpPassword" to JsonValue.Str(""),
            "futureOption" to JsonValue.Bool(true),
        )
        val payload = buildConfigPayload(
            values = mapOf(
                "smtpHost" to "mail.example.com",
                "smtpPort" to "2525",
                "smtpUsername" to "arcane",
                "smtpPassword" to "",
                "fromAddress" to "arcane@example.com",
                "toAddresses" to "one@example.com, two@example.com",
                "tlsMode" to "starttls",
                "authMode" to "login",
            ),
            provider = NotificationProvider.EMAIL,
            events = EventSubscriptions(imageUpdate = false, autoHeal = false),
            baseConfig = base,
        )

        assertEquals(JsonValue.Number(2525.0), payload["smtpPort"])
        assertEquals(
            JsonValue.Arr(listOf(JsonValue.Str("one@example.com"), JsonValue.Str("two@example.com"))),
            payload["toAddresses"],
        )
        assertEquals(JsonValue.Str(""), payload["smtpPassword"])
        assertEquals(JsonValue.Bool(true), payload["futureOption"])
        assertFalse(payload.containsKey("imageUpdate"))
        assertEquals(
            JsonValue.Obj(
                linkedMapOf(
                    "image_update" to JsonValue.Bool(false),
                    "container_update" to JsonValue.Bool(true),
                    "vulnerability_found" to JsonValue.Bool(true),
                    "prune_report" to JsonValue.Bool(false),
                    "auto_heal" to JsonValue.Bool(false),
                ),
            ),
            payload["events"],
        )
    }

    @Test
    fun `Pushover picker priority serializes as a number`() {
        val payload = buildConfigPayload(
            values = mapOf("token" to "secret", "user" to "user-key", "priority" to "-1"),
            provider = NotificationProvider.PUSHOVER,
            events = EventSubscriptions(),
        )

        assertEquals(JsonValue.Number(-1.0), payload["priority"])
    }

    @Test
    fun `redacted required credentials validate without being reentered`() {
        val config = mapOf(
            "webhookId" to JsonValue.Str("123"),
            "token" to JsonValue.Str(""),
        )
        val values = extractConfigValues(config)
        val preserved = preservedCredentialKeys(config, NotificationProvider.DISCORD)

        assertEquals(setOf("token"), preserved)
        assertTrue(isProviderFormValid(values, NotificationProvider.DISCORD, enabled = true, preservedCredentials = preserved))
        assertFalse(isProviderFormValid(values, NotificationProvider.DISCORD, enabled = true))
        assertTrue(isProviderFormValid(emptyMap(), NotificationProvider.DISCORD, enabled = false))
    }

    @Test
    fun `Signal requires exactly one complete authentication mode and accepts preserved secrets`() {
        val baseValues = mapOf(
            "host" to "signal.example.com",
            "port" to "8080",
            "source" to "+15550000000",
            "recipients" to "+15551111111",
        )

        assertFalse(isProviderFormValid(baseValues, NotificationProvider.SIGNAL, enabled = true))
        assertTrue(
            isProviderFormValid(
                baseValues + ("user" to "arcane"),
                NotificationProvider.SIGNAL,
                enabled = true,
                preservedCredentials = setOf("password"),
            ),
        )
        assertTrue(
            isProviderFormValid(
                baseValues,
                NotificationProvider.SIGNAL,
                enabled = true,
                preservedCredentials = setOf("token"),
            ),
        )
        assertFalse(
            isProviderFormValid(
                baseValues + mapOf("user" to "arcane", "password" to "secret", "token" to "secret"),
                NotificationProvider.SIGNAL,
                enabled = true,
            ),
        )
    }

    @Test
    fun `list and header values round trip through compact form strings`() {
        val config = mapOf(
            "webhookUrl" to JsonValue.Str("https://example.com/hook"),
            "customHeaders" to JsonValue.Obj(
                linkedMapOf(
                    "Authorization" to JsonValue.Str("Bearer value:with:colons, commas too"),
                    "X-Test" to JsonValue.Str("yes"),
                ),
            ),
            "events" to JsonValue.Obj(emptyMap()),
        )
        val values = extractConfigValues(config)
        val payload = buildConfigPayload(
            values = values,
            provider = NotificationProvider.GENERIC,
            events = EventSubscriptions.from(config),
        )

        assertEquals(config["customHeaders"], payload["customHeaders"])
        assertEquals(JsonValue.Str("https://example.com/hook"), payload["webhookUrl"])
    }
}
