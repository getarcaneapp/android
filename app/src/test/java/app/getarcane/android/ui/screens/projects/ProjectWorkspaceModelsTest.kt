package app.getarcane.android.ui.screens.projects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectWorkspaceModelsTest {
    @Test
    fun pathNormalizationRejectsTraversalAbsoluteAndInvalidNames() {
        assertEquals("config/app.yml", normalizeWorkspacePath("config\\app.yml").getOrThrow())
        assertTrue(normalizeWorkspacePath("../secret").isFailure)
        assertTrue(normalizeWorkspacePath("/etc/passwd").isFailure)
        assertTrue(normalizeWorkspacePath("C:\\secret").isFailure)
        assertTrue(normalizeWorkspaceName("a/b").isFailure)
        assertTrue(normalizeWorkspaceName("..").isFailure)
    }

    @Test
    fun createRenameMoveDeleteProducesOrderedAllSixMutationInputs() {
        var session = mutableSession()
        session = session.createFolder(null, "config").getOrThrow()
        session = session.createFile("config", "app.yml", "hello").getOrThrow()
        session = session.edit("config/app.yml", "changed").getOrThrow()
        session = session.rename("config/app.yml", "service.yml").getOrThrow()
        session = session.createFolder(null, "overlays").getOrThrow()
        session = session.move("config/service.yml", "overlays").getOrThrow()
        session = session.delete("config").getOrThrow()

        val changes = session.changeSet()
        assertEquals(
            listOf(
                ProjectWorkspaceMutationKind.CREATE_FOLDER,
                ProjectWorkspaceMutationKind.CREATE_FILE,
                ProjectWorkspaceMutationKind.RENAME,
                ProjectWorkspaceMutationKind.CREATE_FOLDER,
                ProjectWorkspaceMutationKind.MOVE,
                ProjectWorkspaceMutationKind.DELETE,
            ),
            changes.mutations.map(ProjectWorkspaceMutation::kind),
        )
        assertTrue(changes.documentUpdates.isEmpty())
        assertEquals(
            "changed",
            changes.mutations.single { it.kind == ProjectWorkspaceMutationKind.CREATE_FILE }.content,
        )
        assertTrue(session.isDirty)
    }

    @Test
    fun renameAndMoveRewriteDescendantsDocumentsAndSelection() {
        val session = mutableSession(
            entries = listOf(
                entry("config", directory = true),
                entry("config/nested", directory = true),
                entry("config/nested/app.yml"),
                entry("destination", directory = true),
            ),
            documents = listOf(document("config/nested/app.yml", "one")),
        ).select("config/nested/app.yml")

        val renamed = session.rename("config", "settings").getOrThrow()
        assertTrue("settings/nested/app.yml" in renamed.entries)
        assertTrue("settings/nested/app.yml" in renamed.documents)
        assertEquals("settings/nested/app.yml", renamed.selectedPath)

        val moved = renamed.move("settings/nested", "destination").getOrThrow()
        assertTrue("destination/nested/app.yml" in moved.entries)
        assertEquals("destination/nested/app.yml", moved.selectedPath)
    }

    @Test
    fun collisionsAndMovingFolderIntoItselfAreRejectedWithoutMutation() {
        val session = mutableSession(
            entries = listOf(
                entry("a", directory = true),
                entry("a/child", directory = true),
                entry("b", directory = true),
            ),
        )

        assertTrue(session.rename("a", "b").isFailure)
        assertTrue(session.move("a", "a/child").isFailure)
        assertFalse(session.isDirty)
    }

    @Test
    fun protectedProjectRootNamesAreRejectedForCreateRenameAndMove() {
        val session = mutableSession(
            entries = listOf(
                entry("notes.txt"),
                entry("nested", directory = true),
                entry("nested/compose.yaml"),
            ),
        )

        assertTrue(session.createFile(null, ".env.git").isFailure)
        assertTrue(session.rename("notes.txt", "project.env").isFailure)
        assertTrue(session.move("nested/compose.yaml", null).isFailure)
        assertFalse(session.isDirty)
    }

    @Test
    fun archiveGitOpsAndPerFileReasonsEnforcePermissionBoundaries() {
        val archived = mutableSession(rootEditable = false, rootReason = "Archived projects are read-only.")
        assertTrue(archived.createFile(null, "new.txt").isFailure)

        val gitOps = mutableSession(
            entries = listOf(
                entry("compose.yml", editable = false, reason = "Managed by GitOps."),
                entry("overrides.env", editable = true),
            ),
            documents = listOf(
                document("compose.yml", "managed", editable = false, reason = "Managed by GitOps."),
                document("overrides.env", "editable", editable = true),
            ),
            rootEditable = false,
            rootReason = "Managed by GitOps.",
        )

        assertTrue(gitOps.edit("compose.yml", "changed").isFailure)
        assertTrue(gitOps.edit("overrides.env", "changed").isSuccess)
        assertTrue(gitOps.createFolder(null, "new").isFailure)
    }

    @Test
    fun discardRestoresBaselineAndRebaseAdoptsLatestConflictInputs() {
        val baseline = mutableSession(
            entries = listOf(entry("compose.yml")),
            documents = listOf(document("compose.yml", "before")),
        ).select("compose.yml")
        val dirty = baseline.edit("compose.yml", "after").getOrThrow()

        assertTrue(dirty.isDirty)
        assertEquals("after", dirty.selectedDocument?.content)

        val discarded = dirty.discardChanges(baseline)
        assertFalse(discarded.isDirty)
        assertEquals("before", discarded.selectedDocument?.content)

        val latest = baseline.copy(
            revision = "revision-2",
            documents = baseline.documents + ("compose.yml" to document("compose.yml", "server-new")),
        )
        val rebased = dirty.replayDraftOnto(latest).getOrThrow()
        assertTrue(rebased.isDirty)
        assertEquals("revision-2", rebased.revision)
        assertEquals("server-new", rebased.selectedDocument?.baselineContent)
        assertEquals("after", rebased.selectedDocument?.content)
    }

    @Test
    fun conflictReplayMapsEditedRenameAndMoveBackToServerPath() {
        val baseline = mutableSession(
            entries = listOf(
                entry("config", directory = true),
                entry("config/app.yml"),
                entry("target", directory = true),
            ),
            documents = listOf(document("config/app.yml", "before")),
        )
        var dirty = baseline.edit("config/app.yml", "local").getOrThrow()
        dirty = dirty.rename("config/app.yml", "service.yml").getOrThrow()
        dirty = dirty.move("config/service.yml", "target").getOrThrow()
        val changes = dirty.workspaceChangeSet()

        assertEquals("config/app.yml", changes.serverPathForDocumentUpdate("target/service.yml"))

        val latest = baseline.copy(
            revision = "revision-2",
            documents = baseline.documents + ("config/app.yml" to document("config/app.yml", "server")),
        )
        val rebased = dirty.replayDraftOnto(latest).getOrThrow()
        assertEquals("revision-2", rebased.revision)
        assertEquals("server", rebased.documents.getValue("target/service.yml").baselineContent)
        assertEquals("local", rebased.documents.getValue("target/service.yml").content)
        assertEquals(
            listOf(ProjectWorkspaceMutationKind.RENAME, ProjectWorkspaceMutationKind.MOVE),
            rebased.mutations.map(ProjectWorkspaceMutation::kind),
        )
    }

    @Test
    fun conflictReplayReportsStructuralCollisionWithoutChangingOriginalDraft() {
        val baseline = mutableSession()
        val dirty = baseline.createFile(null, "new.txt", "local").getOrThrow()
        val latest = mutableSession(entries = listOf(entry("new.txt"))).copy(revision = "revision-2")

        assertTrue(dirty.replayDraftOnto(latest).isFailure)
        assertEquals("revision-1", dirty.revision)
        assertTrue(dirty.isDirty)
    }

    @Test
    fun commitAdoptsAuthoritativeRevisionAndClearsDirtyState() {
        val baseline = mutableSession(
            entries = listOf(entry("compose.yml")),
            documents = listOf(document("compose.yml", "before")),
        )
        val dirty = baseline.edit("compose.yml", "after").getOrThrow()
        val committed = dirty.committed("revision-2", dirty.entries)

        assertEquals("revision-2", committed.revision)
        assertFalse(committed.isDirty)
        assertEquals("after", committed.documents.getValue("compose.yml").baselineContent)
    }

    @Test
    fun existingFileEditRemainsASeparateBaselineAwareDocumentUpdate() {
        val baseline = mutableSession(
            entries = listOf(entry("compose.yml")),
            documents = listOf(document("compose.yml", "before")),
        )
        val changes = baseline.edit("compose.yml", "after").getOrThrow().changeSet()

        assertTrue(changes.mutations.isEmpty())
        assertEquals("compose.yml", changes.documentUpdates.single().path)
        assertEquals("before", changes.documentUpdates.single().baselineContent)
        assertEquals("after", changes.documentUpdates.single().content)
    }

    @Test
    fun hierarchyFlatteningKeepsChildrenImmediatelyAfterTheirFolder() {
        val session = mutableSession(
            entries = listOf(
                entry("z-root.txt"),
                entry("a-folder", directory = true),
                entry("a-folder/child.txt"),
                entry("b-folder", directory = true),
            ),
        )

        assertEquals(
            listOf("a-folder", "a-folder/child.txt", "b-folder", "z-root.txt"),
            session.orderedEntries().map(ProjectWorkspaceEntryModel::relativePath),
        )
    }

    @Test
    fun variablePreviewUsesInlineAndEnvDefaultsWithoutMutatingSource() {
        val source = "services:\n  app:\n    image: \${IMAGE:-nginx}:\${TAG}\n"
        val variables = scanForVariables(source, "TAG=stable\n")

        assertEquals(listOf("IMAGE", "TAG"), variables.map(ComposeVariable::name))
        assertEquals(listOf("nginx", "stable"), variables.map(ComposeVariable::default))
        assertEquals(
            "services:\n  app:\n    image: caddy:stable\n",
            substitute(source, variables, mapOf("IMAGE" to "caddy", "TAG" to "")),
        )
        assertTrue(source.contains("\${IMAGE:-nginx}"))
    }

    @Test
    fun deletingDirectoryRemovesDescendantsAndSelection() {
        val session = mutableSession(
            entries = listOf(entry("folder", directory = true), entry("folder/file.txt")),
            documents = listOf(document("folder/file.txt", "text")),
        ).select("folder/file.txt")

        val deleted = session.delete("folder").getOrThrow()
        assertTrue(deleted.entries.isEmpty())
        assertTrue(deleted.documents.isEmpty())
        assertNull(deleted.selectedPath)
        assertEquals(true, deleted.mutations.single().recursive)
    }

    private fun mutableSession(
        entries: List<ProjectWorkspaceEntryModel> = emptyList(),
        documents: List<ProjectWorkspaceDocument> = emptyList(),
        rootEditable: Boolean = true,
        rootReason: String? = null,
    ) = ProjectWorkspaceSession(
        revision = "revision-1",
        entries = entries.associateBy(ProjectWorkspaceEntryModel::relativePath),
        documents = documents.associateBy(ProjectWorkspaceDocument::path),
        rootEditable = rootEditable,
        rootReadOnlyReason = rootReason,
    )

    private fun entry(
        path: String,
        directory: Boolean = false,
        editable: Boolean = true,
        reason: String? = null,
    ) = ProjectWorkspaceEntryModel(
        relativePath = path,
        name = workspaceBaseName(path),
        kind = if (directory) ProjectWorkspaceEntryKind.DIRECTORY else ProjectWorkspaceEntryKind.TEXT,
        editable = editable,
        readOnlyReason = reason,
    )

    private fun document(
        path: String,
        content: String,
        editable: Boolean = true,
        reason: String? = null,
    ) = ProjectWorkspaceDocument(
        path = path,
        baselineContent = content,
        content = content,
        editable = editable,
        readOnlyReason = reason,
    )
}
