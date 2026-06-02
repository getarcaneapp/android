package app.getarcane.android.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Provisions and keeps alive a temporary hosted Arcane demo instance. Port of the iOS `DemoService`.
 *
 * Talks to the public demo-kuma management API at [DEMO_BASE_URL]; the returned credentials are then
 * used for a normal `auth/login` against the same base URL by [ArcaneClientManager.startDemo]. The
 * `session-id` returned by `start-instance` is replayed as a cookie on the heartbeat/end-session
 * calls (mirroring the iOS shared-cookie-storage behavior).
 */
object DemoService {
    const val DEMO_BASE_URL: String = "https://demo.getarcane.app"

    /** A provisioned demo session. [endsAtMillis] is epoch milliseconds. */
    data class DemoSession(
        val sessionId: String,
        val endsAtMillis: Long,
        val username: String,
        val password: String,
    )

    /** User-facing demo failure, mirroring iOS `DemoError`. */
    class DemoException(message: String) : Exception(message)

    @Volatile
    private var sessionId: String? = null
    private var heartbeatJob: Job? = null

    /** POST `demo-kuma/start-instance` and parse the provisioning payload. */
    suspend fun startInstance(): DemoSession = withContext(Dispatchers.IO) {
        val body = post("demo-kuma/start-instance", timeoutMs = 90_000, withCookie = false)
            ?: throw DemoException("The demo server couldn't spin up an instance. Try again in a moment.")
        val json = try {
            JSONObject(body)
        } catch (_: Exception) {
            throw DemoException("The demo server returned an unexpected response.")
        }
        if (!json.optBoolean("ok", false)) {
            throw DemoException("The demo server couldn't spin up an instance. Try again in a moment.")
        }
        val sid = json.optString("sessionID")
        val creds = json.optJSONObject("credentials")
        if (sid.isBlank() || creds == null) {
            throw DemoException("The demo server returned an unexpected response.")
        }
        // endSessionTime is epoch milliseconds.
        val endsAt = json.optDouble("endSessionTime", 0.0).toLong()
        sessionId = sid
        DemoSession(
            sessionId = sid,
            endsAtMillis = endsAt,
            username = creds.optString("username"),
            password = creds.optString("password"),
        )
    }

    /** Begin the 15s keep-alive heartbeat on [scope]; cancels any prior loop. */
    fun startHeartbeat(scope: CoroutineScope) {
        stopHeartbeat()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(15_000)
                if (!isActive) break
                runCatching { post("demo-kuma/heartbeat", timeoutMs = 10_000, withCookie = true) }
            }
        }
    }

    fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    /** Stop the heartbeat and tell the demo backend to tear down the instance. Best-effort. */
    suspend fun endSession() {
        stopHeartbeat()
        withContext(Dispatchers.IO) {
            runCatching { post("demo-kuma/end-session", timeoutMs = 10_000, withCookie = true) }
        }
        sessionId = null
    }

    /** POSTs to a demo-kuma path. Returns the body on HTTP 200, null on other statuses; throws [DemoException] on network failure. */
    private fun post(path: String, timeoutMs: Int, withCookie: Boolean): String? {
        val conn = (URL("$DEMO_BASE_URL/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("Accept", "application/json")
            if (withCookie) sessionId?.let { setRequestProperty("Cookie", "session-id=$it") }
        }
        return try {
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            throw DemoException("Couldn't reach the demo server: ${e.message ?: "network error"}")
        } finally {
            conn.disconnect()
        }
    }
}
