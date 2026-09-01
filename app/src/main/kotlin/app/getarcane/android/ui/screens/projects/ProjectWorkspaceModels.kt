package app.getarcane.android.ui.screens.projects

internal enum class ProjectWorkspaceEntryKind {
    DIRECTORY,
    TEXT,
    BINARY,
    TOO_LARGE,
    SYMLINK,
    SPECIAL,
}

internal data class ProjectWorkspaceEntryModel(
    val relativePath: String,
    val name: String,
    val kind: ProjectWorkspaceEntryKind,
    val size: Long = 0,
    val mode: String? = null,
    val linkTarget: String? = null,
    val editable: Boolean,
    val readOnlyReason: String? = null,
    val structuralEditable: Boolean = editable,
    val childCreationEditable: Boolean = editable,
) {
    val isDirectory: Boolean get() = kind == ProjectWorkspaceEntryKind.DIRECTORY
    val depth: Int get() = relativePath.count { it == '/' }
}

internal data class ProjectWorkspaceDocument(
    val path: String,
    val baselineContent: String,
    val content: String,
    val editable: Boolean,
    val readOnlyReason: String? = null,
    val mimeType: String? = null,
    val isNew: Boolean = false,
) {
    val isDirty: Boolean get() = content != baselineContent
}

internal enum class ProjectWorkspaceMutationKind {
    CREATE_FILE,
    CREATE_FOLDER,
    RENAME,
    MOVE,
    DELETE,
}

internal data class ProjectWorkspaceMutation(
    val kind: ProjectWorkspaceMutationKind,
    val path: String,
    val content: String? = null,
    val newName: String? = null,
    val newParentPath: String? = null,
    val recursive: Boolean? = null,
)

internal data class ProjectWorkspaceChangeSet(
    val mutations: List<ProjectWorkspaceMutation>,
    val documentUpdates: List<ProjectWorkspaceDocument>,
) {
    val isEmpty: Boolean get() = mutations.isEmpty() && documentUpdates.isEmpty()
}

internal data class ProjectWorkspaceSession(
    val revision: String,
    val entries: Map<String, ProjectWorkspaceEntryModel>,
    val documents: Map<String, ProjectWorkspaceDocument>,
    val mutations: List<ProjectWorkspaceMutation> = emptyList(),
    val selectedPath: String? = null,
    val rootEditable: Boolean,
    val rootReadOnlyReason: String? = null,
    val treeTruncated: Boolean = false,
    val corePaths: Set<String> = emptySet(),
    val protectedRootNames: Set<String> = defaultProtectedProjectRootNames,
) {
    val isDirty: Boolean
        get() = mutations.isNotEmpty() || documents.values.any(ProjectWorkspaceDocument::isDirty)

    val selectedEntry: ProjectWorkspaceEntryModel?
        get() = selectedPath?.let(entries::get)

    val selectedDocument: ProjectWorkspaceDocument?
        get() = selectedPath?.let(documents::get)

    fun orderedEntries(): List<ProjectWorkspaceEntryModel> = flattenWorkspaceEntries(entries.values)

    fun select(path: String?): ProjectWorkspaceSession =
        copy(selectedPath = path?.takeIf(entries::containsKey))

    fun withLoadedDocument(
        path: String,
        content: String,
        editable: Boolean,
        readOnlyReason: String?,
        mimeType: String?,
    ): ProjectWorkspaceSession {
        val normalized = normalizeWorkspacePath(path).getOrElse { return this }
        val existing = documents[normalized]
        if (existing != null) return this
        return copy(
            documents = documents + (
                normalized to ProjectWorkspaceDocument(
                    path = normalized,
                    baselineContent = content,
                    content = content,
                    editable = editable,
                    readOnlyReason = readOnlyReason,
                    mimeType = mimeType,
                )
            ),
        )
    }

    fun withFileContent(
        path: String,
        content: String?,
        mimeType: String?,
        size: Long,
        serverEditable: Boolean,
        serverReadOnlyReason: String?,
    ): ProjectWorkspaceSession {
        val normalized = normalizeWorkspacePath(path).getOrElse { return this }
        val entry = entries[normalized] ?: return this
        val effectiveEditable = entry.editable && serverEditable
        val effectiveReason = when {
            rootReadOnlyReason != null -> entry.readOnlyReason ?: rootReadOnlyReason
            serverReadOnlyReason != null -> workspaceReadOnlyReason(serverReadOnlyReason)
            entry.readOnlyReason != null -> entry.readOnlyReason
            !effectiveEditable -> workspaceReadOnlyReason(serverReadOnlyReason) ?: "This file is read-only."
            else -> null
        }
        val kind = when {
            content != null -> ProjectWorkspaceEntryKind.TEXT
            serverReadOnlyReason.orEmpty().let {
                it.contains("too large", ignoreCase = true) || it.contains("too_large", ignoreCase = true)
            } ->
                ProjectWorkspaceEntryKind.TOO_LARGE
            serverReadOnlyReason.orEmpty().contains("binary", ignoreCase = true) || !isWorkspaceTextMimeType(mimeType) ->
                ProjectWorkspaceEntryKind.BINARY
            else -> ProjectWorkspaceEntryKind.SPECIAL
        }
        val updatedEntry = entry.copy(
            kind = kind,
            size = size,
            editable = effectiveEditable,
            readOnlyReason = effectiveReason,
        )
        if (content == null) {
            return copy(entries = entries + (normalized to updatedEntry))
        }
        val existing = documents[normalized]
        val document = existing ?: ProjectWorkspaceDocument(
            path = normalized,
            baselineContent = content,
            content = content,
            editable = effectiveEditable,
            readOnlyReason = effectiveReason,
            mimeType = mimeType,
        )
        return copy(
            entries = entries + (normalized to updatedEntry),
            documents = documents + (normalized to document),
        )
    }

    fun edit(path: String, content: String): Result<ProjectWorkspaceSession> = runCatching {
        val normalized = normalizeWorkspacePath(path).getOrThrow()
        val document = requireNotNull(documents[normalized]) { "Load the file before editing it." }
        require(document.editable) { document.readOnlyReason ?: "This file is read-only." }
        copy(documents = documents + (normalized to document.copy(content = content)))
    }

    fun createFile(parentPath: String?, name: String, content: String = ""): Result<ProjectWorkspaceSession> =
        createEntry(parentPath, name, isDirectory = false, content = content)

    fun createFolder(parentPath: String?, name: String): Result<ProjectWorkspaceSession> =
        createEntry(parentPath, name, isDirectory = true, content = null)

    private fun createEntry(
        parentPath: String?,
        name: String,
        isDirectory: Boolean,
        content: String?,
    ): Result<ProjectWorkspaceSession> = runCatching {
        requireParentEditable(parentPath)
        val validName = normalizeWorkspaceName(name).getOrThrow()
        val normalizedParent = parentPath?.takeIf { it.isNotBlank() }?.let {
            normalizeWorkspacePath(it).getOrThrow()
        }
        val path = workspaceJoin(normalizedParent, validName)
        requireWorkspaceTargetAllowed(path)
        require(path !in entries) { "A file or folder already exists at $path." }
        val entry = ProjectWorkspaceEntryModel(
            relativePath = path,
            name = validName,
            kind = if (isDirectory) ProjectWorkspaceEntryKind.DIRECTORY else ProjectWorkspaceEntryKind.TEXT,
            editable = true,
        )
        val nextDocuments = if (isDirectory) {
            documents
        } else {
            documents + (
                path to ProjectWorkspaceDocument(
                    path = path,
                    baselineContent = content.orEmpty(),
                    content = content.orEmpty(),
                    editable = true,
                    isNew = true,
                )
            )
        }
        copy(
            entries = entries + (path to entry),
            documents = nextDocuments,
            mutations = mutations + ProjectWorkspaceMutation(
                kind = if (isDirectory) {
                    ProjectWorkspaceMutationKind.CREATE_FOLDER
                } else {
                    ProjectWorkspaceMutationKind.CREATE_FILE
                },
                path = path,
                content = content,
            ),
            selectedPath = path,
        )
    }

    fun rename(path: String, newName: String): Result<ProjectWorkspaceSession> = runCatching {
        val oldPath = normalizeWorkspacePath(path).getOrThrow()
        val entry = requireEditableEntry(oldPath)
        val validName = normalizeWorkspaceName(newName).getOrThrow()
        val newPath = workspaceJoin(workspaceParentPath(oldPath), validName)
        requireWorkspaceTargetAllowed(newPath)
        require(newPath == oldPath || newPath !in entries) { "A file or folder already exists at $newPath." }
        if (newPath == oldPath) return@runCatching this
        rewritePathPrefix(oldPath, newPath).copy(
            mutations = mutations + ProjectWorkspaceMutation(
                kind = ProjectWorkspaceMutationKind.RENAME,
                path = oldPath,
                newName = validName,
            ),
            selectedPath = selectedPath?.rewriteWorkspacePrefix(oldPath, newPath),
        ).also {
            require(entry.relativePath == oldPath)
        }
    }

    fun move(path: String, newParentPath: String?): Result<ProjectWorkspaceSession> = runCatching {
        val oldPath = normalizeWorkspacePath(path).getOrThrow()
        requireEditableEntry(oldPath)
        requireParentEditable(newParentPath)
        val normalizedParent = newParentPath?.takeIf { it.isNotBlank() }?.let {
            normalizeWorkspacePath(it).getOrThrow()
        }
        require(normalizedParent != oldPath && normalizedParent?.startsWith("$oldPath/") != true) {
            "A folder cannot be moved into itself."
        }
        val newPath = workspaceJoin(normalizedParent, workspaceBaseName(oldPath))
        requireWorkspaceTargetAllowed(newPath)
        require(newPath == oldPath || newPath !in entries) { "A file or folder already exists at $newPath." }
        if (newPath == oldPath) return@runCatching this
        rewritePathPrefix(oldPath, newPath).copy(
            mutations = mutations + ProjectWorkspaceMutation(
                kind = ProjectWorkspaceMutationKind.MOVE,
                path = oldPath,
                newParentPath = normalizedParent.orEmpty(),
            ),
            selectedPath = selectedPath?.rewriteWorkspacePrefix(oldPath, newPath),
        )
    }

    fun delete(path: String): Result<ProjectWorkspaceSession> = runCatching {
        val normalized = normalizeWorkspacePath(path).getOrThrow()
        val entry = requireEditableEntry(normalized)
        val removedPaths = entries.keys.filter { it == normalized || it.startsWith("$normalized/") }.toSet()
        copy(
            entries = entries - removedPaths,
            documents = documents - removedPaths,
            mutations = mutations + ProjectWorkspaceMutation(
                kind = ProjectWorkspaceMutationKind.DELETE,
                path = normalized,
                recursive = entry.isDirectory,
            ),
            selectedPath = selectedPath?.takeUnless { it in removedPaths },
        )
    }

    fun discardChanges(baseline: ProjectWorkspaceSession): ProjectWorkspaceSession =
        baseline.copy(selectedPath = selectedPath?.takeIf(baseline.entries::containsKey))

    fun changeSet(): ProjectWorkspaceChangeSet {
        val createdByCurrentPath = createdFileMutationIndexesByCurrentPath(mutations)
        val foldedMutations = mutations.toMutableList()
        for ((currentPath, mutationIndex) in createdByCurrentPath) {
            val latestContent = documents[currentPath]?.content ?: continue
            foldedMutations[mutationIndex] = foldedMutations[mutationIndex].copy(content = latestContent)
        }
        return ProjectWorkspaceChangeSet(
            mutations = foldedMutations,
            documentUpdates = documents.values
                .filter { it.isDirty && it.path !in createdByCurrentPath }
                .sortedBy(ProjectWorkspaceDocument::path),
        )
    }

    fun workspaceChangeSet(): ProjectWorkspaceChangeSet {
        val all = changeSet()
        return ProjectWorkspaceChangeSet(
            mutations = all.mutations.filterNot { it.path in corePaths },
            documentUpdates = all.documentUpdates.filterNot { it.path in corePaths },
        )
    }

    fun dirtyCoreDocuments(): Map<String, ProjectWorkspaceDocument> =
        documents.filter { (path, document) -> path in corePaths && document.isDirty }

    fun committedWorkspace(
        newRevision: String,
        authoritativeEntries: Map<String, ProjectWorkspaceEntryModel>,
    ): ProjectWorkspaceSession {
        val retainedDocuments = documents
            .filterKeys(authoritativeEntries::containsKey)
            .mapValues { (path, document) ->
                if (path in corePaths) {
                    document.copy(
                        editable = authoritativeEntries.getValue(path).editable,
                        readOnlyReason = authoritativeEntries.getValue(path).readOnlyReason,
                    )
                } else {
                    document.copy(
                        path = path,
                        baselineContent = document.content,
                        isNew = false,
                        editable = authoritativeEntries.getValue(path).editable,
                        readOnlyReason = authoritativeEntries.getValue(path).readOnlyReason,
                    )
                }
            }
        return copy(
            revision = newRevision,
            entries = authoritativeEntries,
            documents = retainedDocuments,
            mutations = emptyList(),
            selectedPath = selectedPath?.takeIf(authoritativeEntries::containsKey),
        )
    }

    fun committedCore(): ProjectWorkspaceSession = copy(
        documents = documents.mapValues { (path, document) ->
            if (path in corePaths) document.copy(baselineContent = document.content, isNew = false) else document
        },
    )

    fun baselineAfterWorkspaceCommit(previousBaseline: ProjectWorkspaceSession): ProjectWorkspaceSession = copy(
        documents = documents.mapValues { (path, document) ->
            if (path in corePaths) previousBaseline.documents[path] ?: document.copy(content = document.baselineContent)
            else document
        },
        mutations = emptyList(),
    )

    fun committed(newRevision: String, authoritativeEntries: Map<String, ProjectWorkspaceEntryModel>): ProjectWorkspaceSession {
        return committedWorkspace(newRevision, authoritativeEntries).committedCore()
    }

    fun replayDraftOnto(latest: ProjectWorkspaceSession): Result<ProjectWorkspaceSession> = runCatching {
        val changes = workspaceChangeSet()
        var rebased = latest
        changes.mutations.forEach { mutation ->
            rebased = when (mutation.kind) {
                ProjectWorkspaceMutationKind.CREATE_FILE -> rebased.createFile(
                    workspaceParentPath(mutation.path),
                    workspaceBaseName(mutation.path),
                    mutation.content.orEmpty(),
                ).getOrThrow()
                ProjectWorkspaceMutationKind.CREATE_FOLDER -> rebased.createFolder(
                    workspaceParentPath(mutation.path),
                    workspaceBaseName(mutation.path),
                ).getOrThrow()
                ProjectWorkspaceMutationKind.RENAME -> rebased.rename(
                    mutation.path,
                    requireNotNull(mutation.newName),
                ).getOrThrow()
                ProjectWorkspaceMutationKind.MOVE -> rebased.move(
                    mutation.path,
                    mutation.newParentPath?.ifEmpty { null },
                ).getOrThrow()
                ProjectWorkspaceMutationKind.DELETE -> rebased.delete(mutation.path).getOrThrow()
            }
        }
        changes.documentUpdates.forEach { document ->
            rebased = rebased.edit(document.path, document.content).getOrThrow()
        }
        dirtyCoreDocuments().forEach { (path, document) ->
            rebased = rebased.edit(path, document.content).getOrThrow()
        }
        rebased.select(selectedPath)
    }

    private fun requireEditableEntry(path: String): ProjectWorkspaceEntryModel {
        val entry = requireNotNull(entries[path]) { "The selected file no longer exists." }
        require(entry.structuralEditable) { entry.readOnlyReason ?: "This item is structurally read-only." }
        return entry
    }

    private fun requireParentEditable(path: String?) {
        if (path.isNullOrBlank()) {
            require(rootEditable) { rootReadOnlyReason ?: "This project is read-only." }
            return
        }
        val normalized = normalizeWorkspacePath(path).getOrThrow()
        val parent = requireNotNull(entries[normalized]) { "The destination folder no longer exists." }
        require(parent.isDirectory) { "The destination must be a folder." }
        require(parent.childCreationEditable) { parent.readOnlyReason ?: "The destination folder is read-only." }
    }

    private fun requireWorkspaceTargetAllowed(path: String) {
        val rootName = path.substringBefore('/')
        require(rootName !in protectedRootNames) {
            "$rootName is managed as project configuration and cannot be changed through the workspace."
        }
    }

    private fun rewritePathPrefix(oldPrefix: String, newPrefix: String): ProjectWorkspaceSession {
        val rewrittenEntries = entries.values.associate { entry ->
            val rewrittenPath = entry.relativePath.rewriteWorkspacePrefix(oldPrefix, newPrefix)
            rewrittenPath to entry.copy(relativePath = rewrittenPath, name = workspaceBaseName(rewrittenPath))
        }
        val rewrittenDocuments = documents.values.associate { document ->
            val rewrittenPath = document.path.rewriteWorkspacePrefix(oldPrefix, newPrefix)
            rewrittenPath to document.copy(path = rewrittenPath)
        }
        return copy(entries = rewrittenEntries, documents = rewrittenDocuments)
    }
}

internal fun normalizeWorkspaceName(raw: String): Result<String> = runCatching {
    val name = raw.trim()
    require(name.isNotEmpty()) { "Name is required." }
    require(name != "." && name != "..") { "Name cannot be . or ..." }
    require('/' !in name && '\\' !in name) { "Name cannot contain path separators." }
    require(name.none { it == '\u0000' || it.isISOControl() }) { "Name contains unsupported characters." }
    name
}

internal fun normalizeWorkspacePath(raw: String): Result<String> = runCatching {
    require(raw.isNotBlank()) { "Path is required." }
    require(!raw.startsWith('/') && !raw.startsWith('\\')) { "Path must be relative to the project." }
    require(!Regex("^[A-Za-z]:").containsMatchIn(raw)) { "Path must be relative to the project." }
    val path = raw.replace('\\', '/').trim('/')
    val segments = path.split('/')
    require(segments.all { it.isNotEmpty() && it != "." && it != ".." }) {
        "Path cannot contain empty, . or .. segments."
    }
    require(segments.all { segment -> segment.none { it == '\u0000' || it.isISOControl() } }) {
        "Path contains unsupported characters."
    }
    segments.joinToString("/")
}

internal fun workspaceParentPath(path: String): String? =
    path.substringBeforeLast('/', missingDelimiterValue = "").ifEmpty { null }

internal fun workspaceBaseName(path: String): String = path.substringAfterLast('/')

internal fun workspaceJoin(parent: String?, name: String): String =
    if (parent.isNullOrEmpty()) name else "$parent/$name"

internal fun ProjectWorkspaceChangeSet.serverPathForDocumentUpdate(currentPath: String): String {
    var path = currentPath
    mutations.asReversed().forEach { mutation ->
        when (mutation.kind) {
            ProjectWorkspaceMutationKind.RENAME -> {
                val newName = mutation.newName ?: return@forEach
                val newPrefix = workspaceJoin(workspaceParentPath(mutation.path), newName)
                path = path.rewriteWorkspacePrefix(newPrefix, mutation.path)
            }
            ProjectWorkspaceMutationKind.MOVE -> {
                val newPrefix = workspaceJoin(
                    mutation.newParentPath?.ifEmpty { null },
                    workspaceBaseName(mutation.path),
                )
                path = path.rewriteWorkspacePrefix(newPrefix, mutation.path)
            }
            else -> Unit
        }
    }
    return path
}

internal fun isWorkspaceTextMimeType(mimeType: String?): Boolean {
    val normalized = mimeType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
    return normalized.isEmpty() ||
        normalized.startsWith("text/") ||
        normalized in setOf(
            "application/json",
            "application/toml",
            "application/x-httpd-php",
            "application/x-sh",
            "application/x-yaml",
            "application/xml",
            "application/yaml",
        ) ||
        normalized.endsWith("+json") ||
        normalized.endsWith("+xml") ||
        normalized.endsWith("+yaml")
}

internal fun workspaceReadOnlyReason(wireReason: String?): String? {
    val reason = wireReason?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return when (reason.lowercase()) {
        "archived" -> "Archived projects are read-only."
        "binary" -> "Binary files cannot be edited as text."
        "gitops_managed" -> "Project configuration is managed by GitOps."
        "protected" -> "This project file is protected."
        "special" -> "Special files cannot be edited as text."
        "symlink" -> "Symbolic links cannot be edited."
        "too_large" -> "This file is too large for safe mobile text editing."
        else -> reason
    }
}

internal fun flattenWorkspaceEntries(
    entries: Collection<ProjectWorkspaceEntryModel>,
): List<ProjectWorkspaceEntryModel> {
    val byParent = entries.groupBy { workspaceParentPath(it.relativePath) }
    val result = ArrayList<ProjectWorkspaceEntryModel>(entries.size)
    fun append(parent: String?) {
        byParent[parent]
            .orEmpty()
            .sortedWith(
                compareByDescending<ProjectWorkspaceEntryModel> { it.isDirectory }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                    .thenBy { it.relativePath },
            )
            .forEach { entry ->
                result += entry
                if (entry.isDirectory) append(entry.relativePath)
            }
    }
    append(null)
    // Malformed server trees can omit a parent. Retain those entries instead of hiding them.
    val included = result.mapTo(HashSet()) { it.relativePath }
    result += entries.filterNot { it.relativePath in included }.sortedBy { it.relativePath }
    return result
}

private fun createdFileMutationIndexesByCurrentPath(
    mutations: List<ProjectWorkspaceMutation>,
): Map<String, Int> {
    var currentPaths = linkedMapOf<String, Int>()
    mutations.forEachIndexed { index, mutation ->
        when (mutation.kind) {
            ProjectWorkspaceMutationKind.CREATE_FILE -> currentPaths[mutation.path] = index
            ProjectWorkspaceMutationKind.CREATE_FOLDER -> Unit
            ProjectWorkspaceMutationKind.RENAME -> {
                val newName = mutation.newName ?: return@forEachIndexed
                val newPrefix = workspaceJoin(workspaceParentPath(mutation.path), newName)
                currentPaths = currentPaths.entries.associateTo(linkedMapOf()) { (path, createIndex) ->
                    path.rewriteWorkspacePrefix(mutation.path, newPrefix) to createIndex
                }
            }
            ProjectWorkspaceMutationKind.MOVE -> {
                val newPrefix = workspaceJoin(mutation.newParentPath?.ifEmpty { null }, workspaceBaseName(mutation.path))
                currentPaths = currentPaths.entries.associateTo(linkedMapOf()) { (path, createIndex) ->
                    path.rewriteWorkspacePrefix(mutation.path, newPrefix) to createIndex
                }
            }
            ProjectWorkspaceMutationKind.DELETE -> {
                currentPaths.keys.removeAll { it == mutation.path || it.startsWith("${mutation.path}/") }
            }
        }
    }
    return currentPaths
}

private fun String.rewriteWorkspacePrefix(oldPrefix: String, newPrefix: String): String = when {
    this == oldPrefix -> newPrefix
    startsWith("$oldPrefix/") -> newPrefix + removePrefix(oldPrefix)
    else -> this
}

private val defaultProtectedProjectRootNames: Set<String> = setOf(
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
)
