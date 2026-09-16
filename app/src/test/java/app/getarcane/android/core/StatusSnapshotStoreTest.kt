package app.getarcane.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class StatusSnapshotStoreTest {
    @Test
    fun `widget update callback spends refresh only on material presentation changes`() {
        var updates = 0
        val store = StatusSnapshotStore(
            Files.createTempDirectory("arcane-snapshot-callback").toFile(),
            sourceVersion = 42,
            now = { 20 },
            onMaterialChange = { updates++ },
        )
        store.activateScope("a".repeat(64))

        assertTrue(store.publish(snapshot(generated = 10)))
        assertEquals(1, updates)
        assertTrue(store.publish(snapshot(generated = 11).copy(sourceUpdatedAtEpochMs = 10)))
        assertEquals(1, updates)
        assertTrue(store.publish(snapshot(generated = 12).copy(totalUpdates = 9)))
        assertEquals(2, updates)
        assertTrue(store.publishSignedOut())
        assertEquals(3, updates)
    }

    @Test
    fun `snapshot survives process restart and contains only opaque scope metadata`() {
        val directory = Files.createTempDirectory("arcane-snapshot").toFile()
        val store = StatusSnapshotStore(directory, sourceVersion = 42, now = { 20 })
        val snapshot = snapshot(generated = 10)
        store.activateScope("a".repeat(64))
        assertTrue(store.publish(snapshot))

        val bytes = store.fileForDebugInspection().readText()
        assertFalse(bytes.contains("https://"))
        assertFalse(bytes.contains("username"))
        assertFalse(bytes.contains("token"))
        val restarted = StatusSnapshotStore(directory, sourceVersion = 42, now = { 30 })
        assertEquals(StatusSnapshotRead.Available(snapshot), restarted.load("a".repeat(64)))
        assertEquals(StatusSnapshotRead.Available(snapshot), restarted.loadForExternalConsumer())
    }

    @Test
    fun `scope mismatch synchronously replaces data with signed out snapshot`() {
        val store = store()
        store.activateScope("a".repeat(64))
        store.publish(snapshot())
        val read = store.load("f".repeat(64)) as StatusSnapshotRead.Available
        assertEquals(SnapshotFreshness.SIGNED_OUT, read.snapshot.freshness)
        assertTrue(read.snapshot.environments.isEmpty())
        assertFalse(store.fileForDebugInspection().readText().contains("Environment One"))
    }

    @Test
    fun `logout replaces authenticated snapshot immediately`() {
        val store = store()
        store.activateScope("a".repeat(64))
        store.publish(snapshot())
        assertTrue(store.publishSignedOut())
        val read = store.load() as StatusSnapshotRead.Available
        assertEquals(SnapshotFreshness.SIGNED_OUT, read.snapshot.freshness)
        assertEquals(null, read.snapshot.scopeId)
    }

    @Test
    fun `unconfigured boundary deletes prior projection and refreshes widget`() {
        var updates = 0
        val store = StatusSnapshotStore(
            Files.createTempDirectory("arcane-snapshot-unconfigured").toFile(),
            sourceVersion = 42,
            onMaterialChange = { updates++ },
        )
        store.activateScope("a".repeat(64))
        assertTrue(store.publish(snapshot()))

        assertTrue(store.clearForUnconfigured())
        assertEquals(StatusSnapshotRead.Missing, store.loadForExternalConsumer())
        assertEquals(2, updates)
        assertFalse(store.clearForUnconfigured())
        assertEquals(3, updates)
    }

    @Test
    fun `delayed concurrent writer cannot replace newer snapshot`() {
        val store = store()
        store.activateScope("a".repeat(64))
        assertTrue(store.publish(snapshot(generated = 20).copy(totalContainers = 20)))
        assertFalse(store.publish(snapshot(generated = 19).copy(totalContainers = 19)))
        val loaded = (store.load("a".repeat(64)) as StatusSnapshotRead.Available).snapshot
        assertEquals(20, loaded.totalContainers)

        assertTrue(store.publishSignedOut())
        assertFalse(store.publish(snapshot(generated = 19)))
        assertFalse(store.publish(snapshot(generated = 20)))
        assertEquals(
            SnapshotFreshness.SIGNED_OUT,
            (store.load() as StatusSnapshotRead.Available).snapshot.freshness,
        )
    }

    @Test
    fun `signed out input is reduced to the closed signed out schema`() {
        val store = store()
        store.activateScope("a".repeat(64))
        val unsafe = snapshot(generated = 30).copy(freshness = SnapshotFreshness.SIGNED_OUT)
        assertTrue(store.publish(unsafe))
        val loaded = (store.load() as StatusSnapshotRead.Available).snapshot
        assertEquals(null, loaded.scopeId)
        assertEquals(null, loaded.activeEnvironmentKey)
        assertEquals(0, loaded.totalContainers)
        assertTrue(loaded.environments.isEmpty())
    }

    @Test
    fun `corrupt old and oversized snapshots fail closed`() {
        val store = store(maximumBytes = 2_048)
        store.fileForDebugInspection().apply { parentFile!!.mkdirs(); writeText("not-json") }
        assertEquals(StatusSnapshotRead.CorruptOrUnsupported, store.load())
        assertFalse(store.fileForDebugInspection().exists())

        store.activateScope("a".repeat(64))
        store.publish(snapshot())
        val file = store.fileForDebugInspection()
        file.writeText(file.readText().replace("\"schemaVersion\":2", "\"schemaVersion\":0"))
        assertEquals(StatusSnapshotRead.CorruptOrUnsupported, store.load())

        file.writeText("x".repeat(2_049))
        assertEquals(StatusSnapshotRead.CorruptOrUnsupported, store.load())
    }

    @Test
    fun `bounds names counts and environment rows before writing`() {
        val store = store()
        store.activateScope("a".repeat(64))
        val many = snapshot().copy(
            totalContainers = Int.MAX_VALUE,
            environments = List(20) { index ->
                StatusEnvironmentSnapshot(
                    environmentKey = "b".repeat(64),
                    displayName = "é".repeat(200),
                    online = true,
                    runningContainers = Int.MAX_VALUE,
                    totalContainers = Int.MAX_VALUE,
                    images = -1,
                    updatesAvailable = Int.MAX_VALUE,
                )
            },
        )
        store.publish(many)
        val loaded = (store.load("a".repeat(64)) as StatusSnapshotRead.Available).snapshot
        assertEquals(StatusSnapshotStore.MAXIMUM_ENVIRONMENTS, loaded.environments.size)
        assertTrue(loaded.environments.all { it.displayName.encodeToByteArray().size <= StatusSnapshotStore.MAXIMUM_NAME_BYTES })
        assertEquals(StatusSnapshotStore.MAXIMUM_COUNT, loaded.totalContainers)
        assertTrue(loaded.environments.all { it.images == 0 })
    }

    @Test
    fun `authenticated snapshot without valid route binding fails closed before writing`() {
        val store = store()
        store.activateScope("a".repeat(64))

        assertFalse(store.publish(snapshot().copy(serverBindingHash = "not-a-hash")))
        assertFalse(store.fileForDebugInspection().exists())
    }

    @Test
    fun `signed out boundary rejects delayed authenticated writers`() {
        val store = store()
        store.activateScope("a".repeat(64))
        assertTrue(store.publish(snapshot(generated = 10)))
        assertTrue(store.publishSignedOut())

        assertFalse(store.publish(snapshot(generated = 30)))
        assertEquals(
            SnapshotFreshness.SIGNED_OUT,
            (store.load() as StatusSnapshotRead.Available).snapshot.freshness,
        )
    }

    @Test
    fun `newly activated session can replace signed out state at the same clock tick`() {
        val store = store()
        assertTrue(store.publishSignedOut())
        store.activateScope("a".repeat(64))

        assertTrue(store.publish(snapshot(generated = 20)))
        assertEquals(
            SnapshotFreshness.FRESH,
            (store.load("a".repeat(64)) as StatusSnapshotRead.Available).snapshot.freshness,
        )
    }

    private fun store(maximumBytes: Int = StatusSnapshotStore.MAXIMUM_SNAPSHOT_BYTES) = StatusSnapshotStore(
        Files.createTempDirectory("arcane-snapshot").toFile(),
        sourceVersion = 42,
        now = { 20 },
        maximumBytes = maximumBytes,
    )

    private fun snapshot(generated: Long = 10) = StatusSnapshot(
        sourceVersion = 42,
        generatedAtEpochMs = generated,
        sourceUpdatedAtEpochMs = 9,
        freshness = SnapshotFreshness.FRESH,
        serverBindingHash = "c".repeat(64),
        scopeId = "a".repeat(64),
        activeEnvironmentKey = "b".repeat(64),
        totalRunningContainers = 1,
        totalContainers = 2,
        totalImages = 3,
        totalUpdates = 4,
        onlineEnvironments = 1,
        environments = listOf(
            StatusEnvironmentSnapshot("b".repeat(64), "Environment One", true, 1, 2, 3, 4),
        ),
    )
}
