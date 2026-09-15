package app.getarcane.android.core

import app.getarcane.sdk.errors.ArcaneError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

class ResilientReadCacheTest {
    private val scope = ReadCacheScope("a".repeat(64), "b".repeat(64), "c".repeat(64), "d".repeat(64))
    private val policy = ReadCachePolicy(revalidateAfterMillis = 0, maximumStaleMillis = 1_000)

    @Test
    fun `cached data is explicit stale then replaced by fresh data`() = runBlocking {
        var now = 100L
        val cache = cache(now = { now })
        val request = request("one")
        assertEquals(
            listOf(ResilientRead.Fresh(listOf("old"), 100)),
            cache.observe(scope, request, ListSerializer(String.serializer())) { listOf("old") }.toList(),
        )

        now = 200L
        val reads = cache.observe(scope, request, ListSerializer(String.serializer())) { listOf("new") }.toList()
        assertEquals(2, reads.size)
        assertEquals(listOf("old"), (reads[0] as ResilientRead.Stale).value)
        assertEquals(listOf("new"), (reads[1] as ResilientRead.Fresh).value)
    }

    @Test
    fun `expired entry is removed and cannot mask an offline failure`() = runBlocking {
        var now = 0L
        val cache = cache(now = { now })
        val request = request("expired")
        cache.observe(scope, request, String.serializer()) { "old" }.toList()
        now = 1_001L
        val reads = cache.observe(scope, request, String.serializer()) { error("offline") }.toList()
        assertEquals(1, reads.size)
        assertTrue(reads.single() is ResilientRead.Failure)
        assertEquals(0, cache.entryCount())
    }

    @Test
    fun `same scoped request coalesces concurrent network work`() = runBlocking {
        val cache = cache()
        val gate = CompletableDeferred<Unit>()
        val start = CompletableDeferred<Unit>()
        val ready = AtomicInteger()
        val calls = AtomicInteger()
        val request = request("coalesced")
        val tasks = List(8) {
            async {
                ready.incrementAndGet()
                start.await()
                cache.observe(scope, request, String.serializer(), forceRefresh = true) {
                    calls.incrementAndGet()
                    gate.await()
                    "value"
                }.toList()
            }
        }
        while (ready.get() < tasks.size) kotlinx.coroutines.yield()
        start.complete(Unit)
        while (calls.get() == 0) kotlinx.coroutines.yield()
        // Cache misses perform their file lookup on Dispatchers.IO before joining the in-flight
        // request. Keep the loader suspended long enough for every started collector to arrive.
        kotlinx.coroutines.delay(250)
        gate.complete(Unit)
        val results = tasks.awaitAll()
        assertEquals(1, calls.get())
        assertTrue(results.toString(), results.all { it.single() == ResilientRead.Fresh("value", 100L) })
    }

    @Test
    fun `account and permission scopes cannot read one another`() = runBlocking {
        val cache = cache()
        val request = request("scope")
        cache.observe(scope, request, String.serializer()) { "admin-only" }.toList()
        val restricted = scope.copy(accountBindingHash = "e".repeat(64), permissionContextHash = "f".repeat(64))
        val reads = cache.observe(restricted, request, String.serializer()) { error("offline") }.toList()
        assertTrue(reads.single() is ResilientRead.Failure)
    }

    @Test
    fun `authorization failure evicts stale data immediately`() = runBlocking {
        var now = 10L
        val cache = cache(now = { now })
        val request = request("permission-revoked")
        cache.observe(scope, request, String.serializer()) { "admin-only" }.toList()
        now++

        val reads = cache.observe(scope, request, String.serializer(), forceRefresh = true) {
            throw ArcaneError.Forbidden
        }.toList()

        assertTrue(reads.single() is ResilientRead.Failure)
        assertEquals(0, cache.entryCount())
    }

    @Test
    fun `forced refresh retains cached fallback for a transport failure`() = runBlocking {
        var now = 10L
        val cache = cache(now = { now })
        val request = request("manual-refresh")
        cache.observe(scope, request, String.serializer()) { "cached" }.toList()
        now++

        val reads = cache.observe(scope, request, String.serializer(), forceRefresh = true) {
            error("offline")
        }.toList()

        assertEquals(1, reads.size)
        val stale = reads.single() as ResilientRead.Stale
        assertEquals("cached", stale.value)
        assertTrue(stale.refreshError != null)
    }

    @Test
    fun `corrupt and old schema files recover as misses`() = runBlocking {
        val directory = Files.createTempDirectory("arcane-cache-corrupt").toFile()
        val cache = ResilientReadCache(directory)
        val request = request("corrupt")
        cache.observe(scope, request, String.serializer()) { "safe" }.toList()
        val file = directory.listFiles()!!.single { it.extension == "arc" }
        file.writeText("not-json")
        val restarted = ResilientReadCache(directory)
        assertTrue(restarted.observe(scope, request, String.serializer()) { error("offline") }.toList().single() is ResilientRead.Failure)

        restarted.observe(scope, request, String.serializer()) { "safe" }.toList()
        val old = directory.listFiles()!!.single { it.extension == "arc" }
        old.writeText(old.readText().replace("\"schemaVersion\":1", "\"schemaVersion\":0"))
        val afterUpgrade = ResilientReadCache(directory)
        assertTrue(afterUpgrade.observe(scope, request, String.serializer()) { error("offline") }.toList().single() is ResilientRead.Failure)
    }

    @Test
    fun `disk bounds and targeted invalidation preserve unrelated environments`() = runBlocking {
        val cache = cache(diskBytes = 900, diskEntries = 2)
        repeat(4) { index ->
            cache.observe(
                scope,
                request("item-$index", environment = "env-$index"),
                String.serializer(),
            ) { "x".repeat(80) + index }.toList()
        }
        assertTrue(cache.entryCount() <= 2)
        assertTrue(cache.diskBytes() <= 900)

        val roomy = cache()
        roomy.observe(scope, request("a", "env-a"), String.serializer()) { "a" }.toList()
        roomy.observe(scope, request("b", "env-b"), String.serializer()) { "b" }.toList()
        roomy.invalidate(scope, setOf(ReadResource.CONTAINERS), "env-a")
        val a = roomy.observe(scope, request("a", "env-a"), String.serializer()) { error("offline") }.toList()
        val b = roomy.observe(scope, request("b", "env-b"), String.serializer()) { error("offline") }.toList()
        assertTrue(a.single() is ResilientRead.Failure)
        assertTrue(b.first() is ResilientRead.Stale)
    }

    @Test
    fun `invalidation fences a late network result`() = runBlocking {
        val cache = cache()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val request = request("race")
        val load = async {
            cache.observe(scope, request, String.serializer(), forceRefresh = true) {
                started.complete(Unit)
                finish.await()
                "late"
            }.toList()
        }
        started.await()
        cache.invalidate(scope, setOf(ReadResource.CONTAINERS), "env")
        finish.complete(Unit)
        assertTrue(load.await().isEmpty())
        assertTrue(cache.observe(scope, request, String.serializer()) { error("offline") }.toList().single() is ResilientRead.Failure)
    }

    @Test
    fun `session end makes prior disk and memory entries unreachable immediately`() = runBlocking {
        val cache = cache()
        val request = request("signed-out")
        cache.observe(scope, request, String.serializer()) { "private" }.toList()
        cache.clearForSessionEnd()

        val reads = cache.observe(scope, request, String.serializer()) { error("offline") }.toList()
        assertTrue(reads.none { it is ResilientRead.Stale })
        assertTrue(reads.single() is ResilientRead.Failure)
    }

    private fun cache(
        now: () -> Long = { 100L },
        diskBytes: Long = 1_000_000,
        diskEntries: Int = 32,
    ) = ResilientReadCache(
        directory = Files.createTempDirectory("arcane-cache").toFile(),
        now = now,
        diskByteLimit = diskBytes,
        diskEntryLimit = diskEntries,
        memoryByteLimit = 100_000,
        memoryEntryLimit = 8,
        maximumEntryBytes = 100_000,
    )

    private fun request(identity: String, environment: String = "env") = ReadCacheRequest(
        resource = ReadResource.CONTAINERS,
        environmentId = environment,
        requestIdentity = identity,
        policy = policy,
    )
}
