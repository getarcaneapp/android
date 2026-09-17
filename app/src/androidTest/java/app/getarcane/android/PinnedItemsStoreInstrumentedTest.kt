package app.getarcane.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.getarcane.android.core.PinnedItemsStore
import app.getarcane.sdk.EnvironmentId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PinnedItemsStoreInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    @After
    fun clearPins() {
        context.getSharedPreferences("arcane_pinned", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun pinsRemainEnvironmentScopedAfterStoreRecreation() {
        val first = PinnedItemsStore(context)
        first.togglePin("shared-id", PinnedItemsStore.Kind.CONTAINER, EnvironmentId("0"))
        first.togglePin("remote-only", PinnedItemsStore.Kind.CONTAINER, EnvironmentId("remote"))

        val restored = PinnedItemsStore(context)
        assertEquals(setOf("shared-id"), restored.pinnedIds(PinnedItemsStore.Kind.CONTAINER, EnvironmentId("0")))
        assertEquals(setOf("remote-only"), restored.pinnedIds(PinnedItemsStore.Kind.CONTAINER, EnvironmentId("remote")))
    }
}
