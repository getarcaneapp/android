package app.getarcane.android.core

import app.getarcane.sdk.models.container.ContainerHostConfig
import app.getarcane.sdk.models.container.ContainerNetworkSettings
import app.getarcane.sdk.models.container.ContainerSummary
import app.getarcane.sdk.models.environment.Environment
import app.getarcane.sdk.models.image.ImageSummary
import app.getarcane.sdk.models.network.NetworkSummary
import app.getarcane.sdk.models.project.ProjectDetails
import app.getarcane.sdk.models.volume.Volume

/** Removes credentials, content, daemon paths, arbitrary metadata, and other non-list fields. */
internal fun Environment.sanitizedForReadCache(): Environment = copy(
    apiUrl = "",
    edgeSessionId = null,
    edgeAgentInstance = null,
    apiKey = null,
)

internal fun ContainerSummary.sanitizedForReadCache(): ContainerSummary = copy(
    command = "",
    ports = emptyList(),
    labels = emptyMap(),
    hostConfig = ContainerHostConfig(),
    networkSettings = ContainerNetworkSettings(),
    mounts = emptyList(),
)

internal fun ProjectDetails.sanitizedForReadCache(): ProjectDetails = copy(
    dirName = null,
    relativePath = null,
    path = "",
    urls = null,
    composeContent = null,
    composeFileName = null,
    envContent = null,
    includeFiles = null,
    directoryFiles = null,
    projectFiles = null,
    fileTreeRevision = null,
    services = null,
    runtimeServices = null,
    gitRepositoryURL = null,
    composeFiles = null,
    overrideContent = null,
    overrideFileName = null,
)

internal fun ImageSummary.sanitizedForReadCache(): ImageSummary = copy(
    labels = emptyMap(),
    usedBy = null,
)

internal fun Volume.sanitizedForReadCache(): Volume = copy(
    mountpoint = "",
    options = emptyMap(),
    labels = emptyMap(),
    containers = emptyList(),
)

internal fun NetworkSummary.sanitizedForReadCache(): NetworkSummary = copy(
    options = emptyMap(),
    labels = emptyMap(),
)
