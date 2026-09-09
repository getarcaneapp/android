package app.getarcane.android.ui.screens.projects

import app.getarcane.sdk.models.project.ProjectDetails
import app.getarcane.sdk.models.project.ProjectFile
import app.getarcane.sdk.models.project.ProjectFileChangeOperation
import app.getarcane.sdk.models.project.ProjectWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectWorkspaceSdkMappingTest {
    @Test
    fun gitOpsUsesServerPerFileEditabilityForOverlays() {
        val session = projectWorkspaceSession(
            details = details(gitOpsManagedBy = "sync-1"),
            workspace = workspace(
                file("compose.yml", editable = false, reason = "Managed by GitOps."),
                file("overrides", directory = true, editable = true),
                file("overrides/managed.env", editable = false, reason = "gitops_managed"),
                file("overrides/local.env", editable = true),
            ),
            canUpdate = true,
        )

        assertTrue(session.rootEditable)
        assertFalse(session.entries.getValue("compose.yml").editable)
        assertTrue(session.entries.getValue("overrides/local.env").editable)
        assertFalse(session.entries.getValue("overrides").structuralEditable)
        assertTrue(session.entries.getValue("overrides").childCreationEditable)
        assertTrue(session.delete("overrides").isFailure)
        assertTrue(session.createFile("overrides", "operator.env").isSuccess)
    }

    @Test
    fun archivedAndMissingPermissionOverrideServerEditableFlags() {
        val serverWorkspace = workspace(file("compose.yml", editable = true))

        val archived = projectWorkspaceSession(details(isArchived = true), serverWorkspace, canUpdate = true)
        assertFalse(archived.entries.getValue("compose.yml").editable)
        assertTrue(archived.entries.getValue("compose.yml").readOnlyReason!!.contains("Archived"))

        val denied = projectWorkspaceSession(details(), serverWorkspace, canUpdate = false)
        assertFalse(denied.entries.getValue("compose.yml").editable)
        assertTrue(denied.entries.getValue("compose.yml").readOnlyReason!!.contains("permission"))
    }

    @Test
    fun sdkMappingEmitsAllSixOperationsAndBaselineOnlyForExistingUpdate() {
        var session = projectWorkspaceSession(
            details = details(),
            workspace = workspace(file("compose.yml", editable = true), file("folder", directory = true, editable = true)),
            canUpdate = true,
        )
        session = session.edit("compose.yml", "changed compose").getOrThrow()
        session = session.createFolder(null, "new-folder").getOrThrow()
        session = session.createFile("new-folder", "new.txt", "initial").getOrThrow()
        session = session.edit("new-folder/new.txt", "latest").getOrThrow()
        session = session.rename("new-folder/new.txt", "renamed.txt").getOrThrow()
        session = session.move("new-folder/renamed.txt", "folder").getOrThrow()
        session = session.delete("new-folder").getOrThrow()

        val sdk = projectWorkspaceSdkChanges(session.changeSet())
        assertEquals(
            listOf(
                ProjectFileChangeOperation.CREATE_FOLDER,
                ProjectFileChangeOperation.CREATE_FILE,
                ProjectFileChangeOperation.RENAME,
                ProjectFileChangeOperation.MOVE,
                ProjectFileChangeOperation.DELETE,
                ProjectFileChangeOperation.UPDATE_FILE,
            ),
            sdk.map { it.operation },
        )
        assertEquals("latest", sdk.first { it.operation == ProjectFileChangeOperation.CREATE_FILE }.content)
        assertEquals(null, sdk.first { it.operation == ProjectFileChangeOperation.CREATE_FILE }.baselineContent)
        val update = sdk.single { it.operation == ProjectFileChangeOperation.UPDATE_FILE }
        assertEquals("compose", update.baselineContent)
        assertEquals("changed compose", update.content)
    }

    @Test
    fun contentMappingClassifiesBinaryTooLargeAndTextSafely() {
        val start = projectWorkspaceSession(
            details = details(composeContent = null),
            workspace = workspace(
                file("binary.dat", editable = false),
                file("huge.yml", editable = false),
                file("notes.txt", editable = true),
            ),
            canUpdate = true,
        )

        val binary = start.withFileContent("binary.dat", null, "application/octet-stream", 20, false, "Binary file")
        val huge = binary.withFileContent("huge.yml", null, "text/yaml", 2_000_000, false, "too_large")
        val text = huge.withFileContent("notes.txt", "hello", "text/plain", 5, true, null)

        assertEquals(ProjectWorkspaceEntryKind.BINARY, text.entries.getValue("binary.dat").kind)
        assertEquals(ProjectWorkspaceEntryKind.TOO_LARGE, text.entries.getValue("huge.yml").kind)
        assertTrue(text.entries.getValue("huge.yml").readOnlyReason!!.contains("too large"))
        assertEquals("hello", text.documents.getValue("notes.txt").content)
    }

    @Test
    fun coreAndWorkspaceChangesAreSplitAndPartialCommitKeepsOnlyCoreDirty() {
        val server = workspace(
            file("compose.yml", editable = true),
            file("notes.txt", editable = true),
        )
        val baseline = projectWorkspaceSession(details(), server, canUpdate = true)
            .withFileContent("notes.txt", "before", "text/plain", 6, true, null)
        var dirty = baseline.edit("compose.yml", "changed compose").getOrThrow()
        dirty = dirty.edit("notes.txt", "after").getOrThrow()

        assertEquals(setOf("compose.yml"), dirty.dirtyCoreDocuments().keys)
        assertEquals(listOf("notes.txt"), dirty.workspaceChangeSet().documentUpdates.map { it.path })

        val partial = dirty.committedWorkspace("revision-2", dirty.entries)
        val partialBaseline = partial.baselineAfterWorkspaceCommit(baseline)
        assertTrue(partial.documents.getValue("compose.yml").isDirty)
        assertFalse(partial.documents.getValue("notes.txt").isDirty)
        assertEquals("compose", partialBaseline.documents.getValue("compose.yml").content)
        assertEquals("after", partialBaseline.documents.getValue("notes.txt").content)
        assertEquals("revision-2", partial.revision)

        val fullyCommitted = partial.committedCore()
        assertFalse(fullyCommitted.isDirty)
    }

    private fun details(
        isArchived: Boolean = false,
        gitOpsManagedBy: String? = null,
        composeContent: String? = "compose",
    ) = ProjectDetails(
        id = "project-1",
        name = "Example",
        path = "/projects/example",
        composeContent = composeContent,
        composeFileName = "compose.yml",
        envContent = "KEY=value",
        status = "stopped",
        isArchived = isArchived,
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z",
        gitOpsManagedBy = gitOpsManagedBy,
    )

    private fun workspace(vararg files: ProjectFile) = ProjectWorkspace(
        files = files.toList(),
        fileTreeRevision = "revision-1",
    )

    private fun file(
        path: String,
        directory: Boolean = false,
        editable: Boolean,
        reason: String? = null,
    ) = ProjectFile(
        path = "/projects/example/$path",
        relativePath = path,
        name = workspaceBaseName(path),
        isDirectory = directory,
        editable = editable,
        readOnlyReason = reason,
    )
}
