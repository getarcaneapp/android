package app.getarcane.android.ui.screens.settings.notifications

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
    fun `Google Chat uses the iOS parity label and required webhook field`() {
        assertEquals("Google Chat", NotificationProvider.GOOGLE_CHAT.displayName)
        assertEquals(
            listOf(
                ProviderFieldDescriptor(
                    key = "webhookUrl",
                    label = "Webhook URL",
                    placeholder = "https://chat.googleapis.com/...",
                    kind = ProviderFieldKind.Url,
                    required = true,
                ),
            ),
            fieldsForProvider(NotificationProvider.GOOGLE_CHAT),
        )
    }
}
