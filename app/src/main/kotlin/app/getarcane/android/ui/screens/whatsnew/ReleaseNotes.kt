package app.getarcane.android.ui.screens.whatsnew

/** A single Android release-note bullet. */
data class Bullet(val text: String)

/** One shipped Android app version's changelog entry. */
data class ReleaseNote(
    val version: String,
    val new: List<Bullet> = emptyList(),
    val changed: List<Bullet> = emptyList(),
    val fixed: List<Bullet> = emptyList(),
)

/**
 * Android-owned release notes. Presentation is always derived from the installed app version so
 * adding a future entry cannot advertise it from an older APK.
 */
object ReleaseNotes {
    val all: List<ReleaseNote> = listOf(
        ReleaseNote(
            version = "0.1.0",
            new = listOf(
                Bullet("Initial Android alpha for connecting to self-hosted Arcane servers."),
                Bullet("Dashboard and resource workflows for Docker environments, containers, images, networks, volumes, and Compose projects."),
                Bullet("Cross-environment image update status and update runs."),
            ),
        ),
    )

    fun visibleForInstalledVersion(installedVersion: String): List<ReleaseNote> =
        visibleReleaseNotes(installedVersion, all)

    /** Exact match used by current-version badges and any future automatic presentation. */
    fun currentForInstalledVersion(installedVersion: String): ReleaseNote? =
        currentReleaseNote(installedVersion, all)
}

internal fun visibleReleaseNotes(
    installedVersion: String,
    notes: List<ReleaseNote>,
): List<ReleaseNote> {
    val installed = ReleaseVersion.parse(installedVersion) ?: return emptyList()
    return notes
        .mapNotNull { note -> ReleaseVersion.parse(note.version)?.let { it to note } }
        .filter { (version) -> version <= installed }
        .sortedByDescending { (version) -> version }
        .map { (_, note) -> note }
}

internal fun currentReleaseNote(
    installedVersion: String,
    notes: List<ReleaseNote>,
): ReleaseNote? {
    val installed = ReleaseVersion.parse(installedVersion) ?: return null
    return notes.firstOrNull { note -> ReleaseVersion.parse(note.version) == installed }
}

/** SemVer comparison sufficient for Android version names, including prerelease identifiers. */
internal data class ReleaseVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: List<String>?,
) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int {
        compareValues(major, other.major).takeIf { it != 0 }?.let { return it }
        compareValues(minor, other.minor).takeIf { it != 0 }?.let { return it }
        compareValues(patch, other.patch).takeIf { it != 0 }?.let { return it }

        val left = preRelease
        val right = other.preRelease
        if (left == null && right == null) return 0
        if (left == null) return 1
        if (right == null) return -1

        for (index in 0 until maxOf(left.size, right.size)) {
            val leftPart = left.getOrNull(index) ?: return -1
            val rightPart = right.getOrNull(index) ?: return 1
            compareIdentifier(leftPart, rightPart).takeIf { it != 0 }?.let { return it }
        }
        return 0
    }

    companion object {
        private val pattern = Regex(
            """^(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$""",
        )

        fun parse(value: String): ReleaseVersion? {
            val match = pattern.matchEntire(value.trim()) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].toIntOrNull() ?: return null
            val patch = match.groupValues[3].toIntOrNull() ?: return null
            val preRelease = match.groupValues[4]
                .takeIf { it.isNotEmpty() }
                ?.split('.')
            if (preRelease.orEmpty().any { part -> part.all(Char::isDigit) && part.length > 1 && part.startsWith('0') }) {
                return null
            }
            return ReleaseVersion(major, minor, patch, preRelease)
        }
    }
}

private fun compareIdentifier(left: String, right: String): Int {
    val leftIsNumeric = left.all(Char::isDigit)
    val rightIsNumeric = right.all(Char::isDigit)
    return when {
        leftIsNumeric && rightIsNumeric ->
            compareValues(left.length, right.length).takeIf { it != 0 } ?: left.compareTo(right)
        leftIsNumeric -> -1
        rightIsNumeric -> 1
        else -> left.compareTo(right)
    }
}
