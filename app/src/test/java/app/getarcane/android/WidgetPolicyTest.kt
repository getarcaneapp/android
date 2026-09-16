package app.getarcane.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class WidgetPolicyTest {
    @Test
    fun `widget receiver is private and has only the platform update action`() {
        val manifest = document("src/main/AndroidManifest.xml")
        val receivers = manifest.getElementsByTagName("receiver")
        val widgetReceiver = (0 until receivers.length)
            .map { receivers.item(it) as Element }
            .firstOrNull { it.androidAttribute("name") == ".widget.FleetStatusWidgetReceiver" }
        assertNotNull(widgetReceiver)
        widgetReceiver!!
        assertEquals("false", widgetReceiver.androidAttribute("exported"))
        val actions = widgetReceiver.getElementsByTagName("action")
        assertEquals(1, actions.length)
        assertEquals(
            "android.appwidget.action.APPWIDGET_UPDATE",
            (actions.item(0) as Element).androidAttribute("name"),
        )
    }

    @Test
    fun `widget has no periodic refresh configuration or mutation surface`() {
        val provider = document("src/main/res/xml/fleet_status_widget_info.xml").documentElement
        assertEquals("0", provider.androidAttribute("updatePeriodMillis"))
        assertEquals("horizontal|vertical", provider.androidAttribute("resizeMode"))
        assertEquals("@layout/fleet_status_widget_loading", provider.androidAttribute("initialLayout"))

        val source = File("src/main/kotlin/app/getarcane/android/widget/FleetStatusWidget.kt").readText()
        assertFalse(source.contains("ArcaneClient"))
        assertFalse(source.contains("LocalArcaneManager"))
        assertFalse(source.contains("HttpClient"))
        assertFalse(source.contains("GlanceStateDefinition"))
        assertFalse(source.contains("Button("))
        assertTrue(source.contains("loadForExternalConsumer()"))
    }

    private fun document(relativePath: String) =
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File(relativePath).also { assertTrue("Missing $relativePath", it.isFile) })

    private fun Element.androidAttribute(name: String): String = getAttributeNS(ANDROID_NAMESPACE, name)

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
