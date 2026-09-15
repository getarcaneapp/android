package app.getarcane.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ShortcutPolicyTest {
    @Test
    fun `manifest publishes only the typed route shortcut resource`() {
        val manifest = document("src/main/AndroidManifest.xml")
        val metadata = manifest.getElementsByTagName("meta-data")
        val shortcutMetadata = (0 until metadata.length)
            .map { metadata.item(it) as Element }
            .single { it.android("name") == "android.app.shortcuts" }
        assertEquals("@xml/shortcuts", shortcutMetadata.android("resource"))
    }

    @Test
    fun `static shortcuts are deliberate read only destinations`() {
        val shortcuts = document("src/main/res/xml/shortcuts.xml").getElementsByTagName("shortcut")
        assertEquals(3, shortcuts.length)
        val destinations = (0 until shortcuts.length).map { index ->
            val shortcut = shortcuts.item(index) as Element
            val intent = shortcut.getElementsByTagName("intent").item(0) as Element
            intent.android("data")
        }.toSet()
        assertEquals(
            setOf(
                "arcane-mobile://route/v1/current/dashboard/-/-",
                "arcane-mobile://route/v1/current/containers/-/-",
                "arcane-mobile://route/v1/current/projects/-/-",
            ),
            destinations,
        )
        assertTrue(destinations.none { it.contains("start") || it.contains("stop") || it.contains("delete") })
    }

    private fun document(path: String) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(File(path))

    private fun Element.android(name: String): String = getAttributeNS(ANDROID_NAMESPACE, name)

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
