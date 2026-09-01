package app.getarcane.android.ui.screens.projects

import app.getarcane.sdk.models.project.ProjectDetails
import app.getarcane.sdk.models.project.ProjectFile
import app.getarcane.sdk.models.project.ProjectFileChange
import app.getarcane.sdk.models.project.ProjectFileChangeOperation
import app.getarcane.sdk.models.project.ProjectWorkspace

internal data class ProjectWorkspaceAccess(
    val canUpdate: Boolean,
    val archived: Boolean,
    val gitOpsManaged: Boolean,
)

internal fun projectWorkspaceAccess(
    details: ProjectDetails,
    canUpdate: Boolean,
): ProjectWorkspaceAccess = ProjectWorkspaceAccess(
    canUpdate = canUpdate,
    archived = details.isArchived,
    gitOpsManaged = !details.gitOpsManagedBy.isNullOrBlank(),
)

internal fun projectWorkspaceSession(
    details: ProjectDetails,
    workspace: ProjectWorkspace,
    canUpdate: Boolean,
): ProjectWorkspaceSession {
    val access = projectWorkspaceAccess(details, canUpdate)
    val entries = projectWorkspaceEntries(workspace.files, access).toMutableMap()

    val composePath = details.composeFileName
        ?.let(::normalizeWorkspacePath)
        ?.getOrNull()
        ?: "compose.yml"
    ensureCoreEntry(entries, composePath, access)
    ensureCoreEntry(entries, ".env", access)
    if (access.gitOpsManaged) {
        lockGitOpsCoreEntry(entries, composePath)
        if (".env" in entries) lockGitOpsCoreEntry(entries, ".env")
        lockGitOpsAncestorStructure(entries)
    }

    val documents = buildMap {
        details.composeContent?.let { content ->
            entries[composePath]?.let { entry ->
                put(composePath, entry.toDocument(content))
            }
        }
        entries[".env"]?.let { entry ->
            put(".env", entry.toDocument(details.envContent.orEmpty()))
        }
        workspace.files.forEach { file ->
            val path = normalizeWorkspacePath(file.relativePath).getOrNull() ?: return@forEach
            val entry = entries[path] ?: return@forEach
            val content = file.content
            if (content != null && path !in this) put(path, entry.toDocument(content))
        }
    }

    val rootReason = rootReadOnlyReason(access)
    return ProjectWorkspaceSession(
        revision = workspace.fileTreeRevision,
        entries = entries,
        documents = documents,
        rootEditable = rootReason == null,
        rootReadOnlyReason = rootReason,
        treeTruncated = workspace.fileTreeTruncated,
        selectedPath = composePath.takeIf(entries::containsKey) ?: entries.keys.firstOrNull(),
        corePaths = setOf(composePath, ".env"),
        protectedRootNames = defaultProtectedProjectRootNamesFor(composePath),
    )
}

internal fun projectWorkspaceEntries(
    files: List<ProjectFile>,
    access: ProjectWorkspaceAccess,
): Map<String, ProjectWorkspaceEntryModel> = buildMap {
    files.forEach { file ->
        val path = normalizeWorkspacePath(file.relativePath).getOrNull() ?: return@forEach
        val globalReason = when {
            access.archived -> "Archived projects are read-only."
            !access.canUpdate -> "You do not have permission to update projects in this environment."
            else -> null
        }
        val serverEditable = file.editable && file.protected != true
        val editable = globalReason == null && serverEditable
        val reason = when {
            globalReason != null -> globalReason
            file.protected == true -> workspaceReadOnlyReason(file.readOnlyReason) ?: "This project file is protected."
            !serverEditable && file.readOnlyReason != null -> workspaceReadOnlyReason(file.readOnlyReason)
            access.gitOpsManaged && !serverEditable -> "Project configuration is managed by GitOps."
            !serverEditable -> "This item is read-only."
            else -> null
        }
        put(
            path,
            ProjectWorkspaceEntryModel(
                relativePath = path,
                name = file.name.ifBlank { workspaceBaseName(path) },
                kind = projectWorkspaceEntryKind(file),
                size = file.size,
                mode = file.mode,
                linkTarget = file.linkTarget,
                editable = editable,
                readOnlyReason = reason,
            ),
        )
    }
}

internal fun projectWorkspaceSdkChanges(changeSet: ProjectWorkspaceChangeSet): List<ProjectFileChange> =
    buildList {
        changeSet.mutations.forEach { mutation ->
            add(
                when (mutation.kind) {
                    ProjectWorkspaceMutationKind.CREATE_FILE -> ProjectFileChange(
                        operation = ProjectFileChangeOperation.CREATE_FILE,
                        relativePath = mutation.path,
                        content = mutation.content.orEmpty(),
                    )
                    ProjectWorkspaceMutationKind.CREATE_FOLDER -> ProjectFileChange(
                        operation = ProjectFileChangeOperation.CREATE_FOLDER,
                        relativePath = mutation.path,
                    )
                    ProjectWorkspaceMutationKind.RENAME -> ProjectFileChange(
                        operation = ProjectFileChangeOperation.RENAME,
                        relativePath = mutation.path,
                        newName = mutation.newName,
                    )
                    ProjectWorkspaceMutationKind.MOVE -> ProjectFileChange(
                        operation = ProjectFileChangeOperation.MOVE,
                        relativePath = mutation.path,
                        newParentPath = mutation.newParentPath,
                    )
                    ProjectWorkspaceMutationKind.DELETE -> ProjectFileChange(
                        operation = ProjectFileChangeOperation.DELETE,
                        relativePath = mutation.path,
                        recursive = mutation.recursive,
                    )
                },
            )
        }
        changeSet.documentUpdates.forEach { document ->
            add(
                ProjectFileChange(
                    operation = ProjectFileChangeOperation.UPDATE_FILE,
                    relativePath = document.path,
                    content = document.content,
                    baselineContent = document.baselineContent,
                ),
            )
        }
    }

private fun projectWorkspaceEntryKind(file: ProjectFile): ProjectWorkspaceEntryKind = when {
    file.isDirectory -> ProjectWorkspaceEntryKind.DIRECTORY
    file.isSymlink -> ProjectWorkspaceEntryKind.SYMLINK
    file.readOnlyReason.isWorkspaceReason("too_large", "too large") -> ProjectWorkspaceEntryKind.TOO_LARGE
    file.readOnlyReason.isWorkspaceReason("binary") -> ProjectWorkspaceEntryKind.BINARY
    file.mode?.let(::isSpecialWorkspaceMode) == true -> ProjectWorkspaceEntryKind.SPECIAL
    else -> ProjectWorkspaceEntryKind.TEXT
}

private fun isSpecialWorkspaceMode(mode: String): Boolean {
    val normalized = mode.trim().lowercase()
    if (normalized.isEmpty()) return false
    // Go FileMode strings use d/L/D/p/S/u/? prefixes; Unix symbolic regular files start with '-'.
    return normalized.first() in setOf('p', 's', 'c', 'b', 'd')
}

private fun ensureCoreEntry(
    entries: MutableMap<String, ProjectWorkspaceEntryModel>,
    path: String,
    access: ProjectWorkspaceAccess,
) {
    if (path in entries) return
    val rootReason = rootReadOnlyReason(access)
    entries[path] = ProjectWorkspaceEntryModel(
        relativePath = path,
        name = workspaceBaseName(path),
        kind = ProjectWorkspaceEntryKind.TEXT,
        editable = rootReason == null,
        readOnlyReason = rootReason,
    )
}

private fun rootReadOnlyReason(access: ProjectWorkspaceAccess): String? = when {
    access.archived -> "Archived projects are read-only."
    !access.canUpdate -> "You do not have permission to update projects in this environment."
    else -> null
}

private fun lockGitOpsCoreEntry(
    entries: MutableMap<String, ProjectWorkspaceEntryModel>,
    path: String,
) {
    val entry = entries[path] ?: return
    entries[path] = entry.copy(
        editable = false,
        readOnlyReason = entry.readOnlyReason ?: "Project configuration is managed by GitOps.",
    )
}

private fun lockGitOpsAncestorStructure(entries: MutableMap<String, ProjectWorkspaceEntryModel>) {
    val ownedPaths = entries.values
        .filter { !it.editable && it.readOnlyReason?.contains("GitOps", ignoreCase = true) == true }
        .map(ProjectWorkspaceEntryModel::relativePath)
    ownedPaths.forEach { ownedPath ->
        var parent = workspaceParentPath(ownedPath)
        while (parent != null) {
            val entry = entries[parent]
            if (entry != null && entry.isDirectory) {
                entries[parent] = entry.copy(
                    structuralEditable = false,
                    readOnlyReason = "This folder contains project files managed by GitOps.",
                )
            }
            parent = workspaceParentPath(parent)
        }
    }
}

private fun defaultProtectedProjectRootNamesFor(composePath: String): Set<String> =
    setOf(
        ".env",
        ".env.git",
        "project.env",
        "compose.yaml",
        "compose.yml",
        "docker-compose.yaml",
        "docker-compose.yml",
        "podman-compose.yaml",
        "podman-compose.yml",
        "compose.override.yaml",
        "compose.override.yml",
        "docker-compose.override.yaml",
        "docker-compose.override.yml",
        composePath.substringBefore('/'),
    )

private fun String?.isWorkspaceReason(vararg values: String): Boolean {
    val normalized = this?.trim()?.lowercase().orEmpty()
    return values.any { normalized == it || normalized.contains(it) }
}

private fun ProjectWorkspaceEntryModel.toDocument(content: String): ProjectWorkspaceDocument =
    ProjectWorkspaceDocument(
        path = relativePath,
        baselineContent = content,
        content = content,
        editable = editable,
        readOnlyReason = readOnlyReason,
    )
