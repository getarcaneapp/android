package app.getarcane.android.core

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class CoroutineFailuresTest {
    @Test
    fun `suspending result wrapper preserves success and ordinary failure`() = runBlocking {
        assertEquals("ok", runSuspendCatching { "ok" }.getOrThrow())

        val failure = IOException("offline")
        assertSame(failure, runSuspendCatching<String> { throw failure }.exceptionOrNull())
    }

    @Test
    fun `suspending result wrapper rethrows cancellation`() {
        val cancellation = CancellationException("superseded")

        val actual = assertThrows(CancellationException::class.java) {
            runBlocking {
                runSuspendCatching<String> { throw cancellation }
            }
        }

        assertSame(cancellation, actual)
    }

    @Test
    fun `a canceled context cannot publish a successful result`() {
        val cancellation = CancellationException("target changed")
        var published = false

        assertThrows(CancellationException::class.java) {
            runBlocking {
                runSuspendCatching {
                    currentCoroutineContext().cancel(cancellation)
                    "stale"
                }.onSuccess { published = true }
            }
        }

        assertFalse(published)
    }
}
