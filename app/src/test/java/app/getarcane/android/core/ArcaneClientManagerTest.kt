package app.getarcane.android.core

import app.getarcane.sdk.EnvironmentId
import app.getarcane.sdk.auth.InMemoryTokenStore
import io.ktor.client.engine.okhttp.OkHttp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArcaneClientManagerTest {
    @Test
    fun androidClientUsesLongEnoughRequestTimeoutForLargeUpdaterBatches() {
        val configuration = androidArcaneConfiguration(
            url = "https://arcane.example.com",
            tokenStore = InMemoryTokenStore(),
            defaultEnvironmentId = EnvironmentId.LOCAL_DOCKER,
            defaultHeaders = emptyMap(),
            engine = OkHttp.create(),
        )

        assertEquals(10 * 60 * 1000L, configuration.requestTimeoutMillis)
        assertTrue(configuration.requestTimeoutMillis > 30_000L)
        assertEquals(15_000L, configuration.connectTimeoutMillis)
    }
}
