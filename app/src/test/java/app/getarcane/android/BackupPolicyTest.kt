package app.getarcane.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class BackupPolicyTest {
    @Test
    fun `manifest enables only the explicit legacy and current backup policies`() {
        val application = document("src/main/AndroidManifest.xml")
            .getElementsByTagName("application")
            .item(0) as Element

        assertEquals("true", application.androidAttribute("allowBackup"))
        assertEquals("@xml/backup_rules", application.androidAttribute("fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", application.androidAttribute("dataExtractionRules"))
        assertFalse(application.hasAttributeNS(ANDROID_NAMESPACE, "backupAgent"))
    }

    @Test
    fun `legacy cloud and device transfer policies share one narrow allowlist`() {
        val legacy = includeRules(
            document("src/main/res/xml/backup_rules.xml").documentElement,
        )
        val current = document("src/main/res/xml/data_extraction_rules.xml")
        val cloud = includeRules(current.getElementsByTagName("cloud-backup").singleElement())
        val deviceTransfer = includeRules(current.getElementsByTagName("device-transfer").singleElement())

        assertEquals(EXPECTED_ALLOWLIST, legacy)
        assertEquals(legacy, cloud)
        assertEquals(legacy, deviceTransfer)
    }

    @Test
    fun `allowlist cannot capture protected or future persistence by default`() {
        val legacyDocument = document("src/main/res/xml/backup_rules.xml")
        val currentDocument = document("src/main/res/xml/data_extraction_rules.xml")
        val allRules = buildList {
            addAll(includeRules(legacyDocument.documentElement))
            addAll(includeRules(currentDocument.getElementsByTagName("cloud-backup").singleElement()))
            addAll(includeRules(currentDocument.getElementsByTagName("device-transfer").singleElement()))
        }

        assertTrue(allRules.all { it.path != "." && !it.path.endsWith("/") })
        assertTrue(allRules.none { it in PROTECTED_LOCATIONS })
        assertEquals(0, legacyDocument.getElementsByTagName("exclude").length)
        assertEquals(0, currentDocument.getElementsByTagName("exclude").length)
    }

    @Test
    fun `resilient read persistence uses only non backed up app private locations`() {
        val manager = File("src/main/kotlin/app/getarcane/android/core/ArcaneClientManager.kt").readText()
        assertTrue(manager.contains("java.io.File(appContext.cacheDir, ResilientReadCache.CACHE_DIRECTORY)"))
        assertTrue(manager.contains("java.io.File(appContext.cacheDir, OfflineReadSessionStore.DIRECTORY)"))
        assertTrue(manager.contains("java.io.File(appContext.noBackupFilesDir, StatusSnapshotStore.SNAPSHOT_DIRECTORY)"))

        val admittedPaths = EXPECTED_ALLOWLIST.map { it.path }
        assertTrue(admittedPaths.none { it.contains("arcane_read_cache") })
        assertTrue(admittedPaths.none { it.contains("arcane_offline_read_session") })
        assertTrue(admittedPaths.none { it.contains("arcane_status_snapshots") })
    }

    private fun document(relativePath: String) =
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File(relativePath).also { assertTrue("Missing $relativePath", it.isFile) })

    private fun includeRules(parent: Element): Set<Rule> =
        parent.getElementsByTagName("include")
            .let { nodes ->
                (0 until nodes.length)
                    .map { nodes.item(it) as Element }
                    .mapTo(mutableSetOf()) { Rule(it.getAttribute("domain"), it.getAttribute("path")) }
            }

    private fun org.w3c.dom.NodeList.singleElement(): Element {
        assertEquals(1, length)
        return item(0) as Element
    }

    private fun Element.androidAttribute(name: String): String = getAttributeNS(ANDROID_NAMESPACE, name)

    private data class Rule(val domain: String, val path: String)

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"

        val EXPECTED_ALLOWLIST = setOf(
            Rule("file", "datastore/arcane_tabs.preferences_pb"),
        )

        val PROTECTED_LOCATIONS = setOf(
            Rule("file", "datastore/arcane_prefs.preferences_pb"),
            Rule("file", "datastore/arcane_secure_tokens.preferences_pb"),
            Rule("file", "datastore/arcane_operations.preferences_pb"),
            Rule("sharedpref", "arcane_pinned.xml"),
            Rule("sharedpref", "arcane_project_deploy_options.xml"),
            Rule("sharedpref", "arcane_secure_prefs.xml"),
        )
    }
}
