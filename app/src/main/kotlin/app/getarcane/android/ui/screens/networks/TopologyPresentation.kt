package app.getarcane.android.ui.screens.networks

import app.getarcane.android.core.sha256
import app.getarcane.sdk.models.network.NetworkTopology
import app.getarcane.sdk.models.network.TopologyNodeType
import kotlin.math.max
import kotlin.math.min

internal enum class PresentedTopologyNodeKind { NETWORK, CONTAINER, UNKNOWN }

internal data class PresentedTopologyNode(
    val id: String,
    val label: String,
    val kind: PresentedTopologyNodeKind,
    val driver: String?,
    val scope: String?,
    val status: String?,
    val image: String?,
    val isDefault: Boolean,
)

internal data class PresentedTopologyEdge(
    val key: String,
    val source: String,
    val target: String,
    val ipv4Address: String?,
    val ipv6Address: String?,
)

internal data class TopologyNormalizationIssues(
    val duplicateNodes: Int = 0,
    val duplicateEdges: Int = 0,
    val malformedNodes: Int = 0,
    val missingEndpointEdges: Int = 0,
    val invalidRelationshipEdges: Int = 0,
    val unknownNodes: Int = 0,
    val inputNodesTruncated: Int = 0,
    val inputEdgesTruncated: Int = 0,
) {
    val total: Int
        get() = duplicateNodes + duplicateEdges + malformedNodes + missingEndpointEdges +
            invalidRelationshipEdges + unknownNodes + inputNodesTruncated + inputEdgesTruncated
}

internal data class PresentedTopology(
    val identity: String,
    val nodes: List<PresentedTopologyNode>,
    val edges: List<PresentedTopologyEdge>,
    val issues: TopologyNormalizationIssues,
    val interactiveEligible: Boolean,
) {
    val nodesById: Map<String, PresentedTopologyNode> = nodes.associateBy { it.id }
}

internal object TopologyNormalizer {
    const val MAXIMUM_INPUT_NODES = 500
    const val MAXIMUM_INPUT_EDGES = 2_000
    const val MAXIMUM_INTERACTIVE_NODES = 80
    const val MAXIMUM_INTERACTIVE_EDGES = 240
    private const val MAXIMUM_ID_BYTES = 512
    private const val MAXIMUM_LABEL_BYTES = 256

    fun normalize(topology: NetworkTopology): PresentedTopology {
        var duplicateNodes = 0
        var malformedNodes = 0
        var unknownNodes = 0
        val nodesById = linkedMapOf<String, PresentedTopologyNode>()
        topology.nodes.take(MAXIMUM_INPUT_NODES).forEach { node ->
            val id = node.id.trim()
            if (id.isEmpty() || id.encodeToByteArray().size > MAXIMUM_ID_BYTES) {
                malformedNodes++
                return@forEach
            }
            if (id in nodesById) {
                duplicateNodes++
                return@forEach
            }
            val kind = when (node.type) {
                TopologyNodeType.NETWORK -> PresentedTopologyNodeKind.NETWORK
                TopologyNodeType.CONTAINER -> PresentedTopologyNodeKind.CONTAINER
                else -> PresentedTopologyNodeKind.UNKNOWN
            }
            if (kind == PresentedTopologyNodeKind.UNKNOWN) unknownNodes++
            nodesById[id] = PresentedTopologyNode(
                id = id,
                label = boundedText(node.name.trim(), MAXIMUM_LABEL_BYTES).ifBlank {
                    when (kind) {
                        PresentedTopologyNodeKind.NETWORK -> "Unnamed network (${id.take(12)})"
                        PresentedTopologyNodeKind.CONTAINER -> "Unnamed container (${id.take(12)})"
                        PresentedTopologyNodeKind.UNKNOWN -> "Unknown node (${id.take(12)})"
                    }
                },
                kind = kind,
                driver = node.metadata.driver.boundedOrNull(),
                scope = node.metadata.scope.boundedOrNull(),
                status = node.metadata.status.boundedOrNull(),
                image = node.metadata.image.boundedOrNull(),
                isDefault = node.metadata.isDefault == true,
            )
        }

        var duplicateEdges = 0
        var missingEndpointEdges = 0
        var invalidRelationshipEdges = 0
        val edgeIds = mutableSetOf<String>()
        val edgeKeys = mutableSetOf<String>()
        val edges = buildList {
            topology.edges.take(MAXIMUM_INPUT_EDGES).forEach { edge ->
                val edgeId = edge.id.trim()
                val source = nodesById[edge.source]
                val target = nodesById[edge.target]
                if (source == null || target == null) {
                    missingEndpointEdges++
                    return@forEach
                }
                if (source.kind != PresentedTopologyNodeKind.NETWORK ||
                    target.kind != PresentedTopologyNodeKind.CONTAINER ||
                    source.id == target.id
                ) {
                    invalidRelationshipEdges++
                    return@forEach
                }
                if (edgeId.isNotEmpty() && edgeId in edgeIds) {
                    duplicateEdges++
                    return@forEach
                }
                val ipv4 = edge.ipv4Address.boundedOrNull()
                val ipv6 = edge.ipv6Address.boundedOrNull()
                val semanticKey = listOf(source.id, target.id, ipv4.orEmpty(), ipv6.orEmpty())
                    .joinToString("\u0000")
                if (!edgeKeys.add(semanticKey)) {
                    duplicateEdges++
                    return@forEach
                }
                if (edgeId.isNotEmpty()) edgeIds += edgeId
                add(
                    PresentedTopologyEdge(
                        key = sha256(semanticKey),
                        source = source.id,
                        target = target.id,
                        ipv4Address = ipv4,
                        ipv6Address = ipv6,
                    ),
                )
            }
        }.sortedWith(compareBy(PresentedTopologyEdge::source, PresentedTopologyEdge::target, PresentedTopologyEdge::key))

        val nodes = nodesById.values.sortedWith(
            compareBy<PresentedTopologyNode>({ it.kind.ordinal }, { it.label.lowercase() }, { it.id }),
        )
        val issues = TopologyNormalizationIssues(
            duplicateNodes = duplicateNodes,
            duplicateEdges = duplicateEdges,
            malformedNodes = malformedNodes,
            missingEndpointEdges = missingEndpointEdges,
            invalidRelationshipEdges = invalidRelationshipEdges,
            unknownNodes = unknownNodes,
            inputNodesTruncated = (topology.nodes.size - MAXIMUM_INPUT_NODES).coerceAtLeast(0),
            inputEdgesTruncated = (topology.edges.size - MAXIMUM_INPUT_EDGES).coerceAtLeast(0),
        )
        val canonicalIdentity = buildString {
            nodes.forEach { append(it.kind).append('|').append(it.id).append('|').append(it.label).append('\n') }
            edges.forEach { append(it.source).append('>').append(it.target).append('|').append(it.key).append('\n') }
        }
        val knownNodeCount = nodes.count { it.kind != PresentedTopologyNodeKind.UNKNOWN }
        return PresentedTopology(
            identity = sha256(canonicalIdentity),
            nodes = nodes,
            edges = edges,
            issues = issues,
            interactiveEligible =
                knownNodeCount in 1..MAXIMUM_INTERACTIVE_NODES &&
                    edges.size <= MAXIMUM_INTERACTIVE_EDGES &&
                    issues.inputNodesTruncated == 0 &&
                    issues.inputEdgesTruncated == 0,
        )
    }

    private fun String?.boundedOrNull(): String? = this
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { boundedText(it, MAXIMUM_LABEL_BYTES) }

    private fun boundedText(value: String, maximumBytes: Int): String {
        if (value.encodeToByteArray().size <= maximumBytes) return value
        var used = 0
        return buildString {
            value.forEach { character ->
                val bytes = character.toString().encodeToByteArray().size
                if (used + bytes > maximumBytes) return@buildString
                append(character)
                used += bytes
            }
        }
    }
}

internal data class TopologyPoint(val x: Float, val y: Float)
internal data class TopologySize(val width: Float, val height: Float)

internal data class TopologyGraphLayout(
    val nodes: List<PresentedTopologyNode>,
    val edges: List<PresentedTopologyEdge>,
    val positions: Map<String, TopologyPoint>,
    val contentSize: TopologySize,
) {
    fun nodeSize(node: PresentedTopologyNode): TopologySize = when (node.kind) {
        PresentedTopologyNodeKind.NETWORK -> NETWORK_NODE_SIZE
        PresentedTopologyNodeKind.CONTAINER -> CONTAINER_NODE_SIZE
        PresentedTopologyNodeKind.UNKNOWN -> UNKNOWN_NODE_SIZE
    }

    companion object {
        val NETWORK_NODE_SIZE = TopologySize(260f, 144f)
        val CONTAINER_NODE_SIZE = TopologySize(300f, 180f)
        val UNKNOWN_NODE_SIZE = TopologySize(260f, 144f)
        private const val OUTER_PADDING = 40f
        private const val COLUMN_GAP = 140f
        private const val ROW_GAP = 28f
        private const val GROUP_GAP = 64f

        fun build(topology: PresentedTopology): TopologyGraphLayout {
            val networks = topology.nodes.filter { it.kind == PresentedTopologyNodeKind.NETWORK }
            val containers = topology.nodes.filter { it.kind == PresentedTopologyNodeKind.CONTAINER }
            val networkOrder = networks.withIndex().associate { it.value.id to it.index }
            val containersByNetwork = linkedMapOf<String, MutableList<PresentedTopologyNode>>()
            val isolated = mutableListOf<PresentedTopologyNode>()
            containers.forEach { container ->
                val primary = topology.edges.asSequence()
                    .filter { it.target == container.id }
                    .mapNotNull { edge -> networkOrder[edge.source]?.let { edge.source to it } }
                    .minByOrNull { it.second }
                    ?.first
                if (primary == null) isolated += container
                else containersByNetwork.getOrPut(primary) { mutableListOf() } += container
            }
            containersByNetwork.values.forEach { rows -> rows.sortWith(compareBy({ it.label.lowercase() }, { it.id })) }

            val networkX = OUTER_PADDING + NETWORK_NODE_SIZE.width / 2
            val containerX = OUTER_PADDING + NETWORK_NODE_SIZE.width + COLUMN_GAP + CONTAINER_NODE_SIZE.width / 2
            val positions = linkedMapOf<String, TopologyPoint>()
            var currentTop = OUTER_PADDING
            networks.forEach { network ->
                val rows = containersByNetwork[network.id].orEmpty()
                val containerHeight = columnHeight(rows.size, CONTAINER_NODE_SIZE.height)
                val groupHeight = max(NETWORK_NODE_SIZE.height, containerHeight)
                positions[network.id] = TopologyPoint(networkX, currentTop + groupHeight / 2)
                rows.forEachIndexed { index, container ->
                    positions[container.id] = TopologyPoint(
                        containerX,
                        currentTop + CONTAINER_NODE_SIZE.height / 2 + index * (CONTAINER_NODE_SIZE.height + ROW_GAP),
                    )
                }
                currentTop += groupHeight + GROUP_GAP
            }
            isolated.forEachIndexed { index, container ->
                positions[container.id] = TopologyPoint(
                    containerX,
                    currentTop + CONTAINER_NODE_SIZE.height / 2 + index * (CONTAINER_NODE_SIZE.height + ROW_GAP),
                )
            }
            if (isolated.isNotEmpty()) currentTop += columnHeight(isolated.size, CONTAINER_NODE_SIZE.height) + GROUP_GAP

            val height = max(
                currentTop - GROUP_GAP + OUTER_PADDING,
                max(NETWORK_NODE_SIZE.height, CONTAINER_NODE_SIZE.height) + OUTER_PADDING * 2,
            )
            return TopologyGraphLayout(
                nodes = networks + containers,
                edges = topology.edges,
                positions = positions,
                contentSize = TopologySize(
                    OUTER_PADDING * 2 + NETWORK_NODE_SIZE.width + COLUMN_GAP + CONTAINER_NODE_SIZE.width,
                    height,
                ),
            )
        }

        private fun columnHeight(count: Int, nodeHeight: Float): Float =
            if (count <= 0) 0f else count * nodeHeight + (count - 1) * ROW_GAP
    }
}

internal data class TopologyViewport(val scale: Float, val offsetX: Float, val offsetY: Float) {
    companion object {
        const val MINIMUM_SCALE = 0.35f
        const val MAXIMUM_SCALE = 1.75f
        private const val VIEWPORT_PADDING = 32f
        private const val PAN_MARGIN = 72f

        fun fit(viewport: TopologySize, content: TopologySize): TopologyViewport {
            if (viewport.width <= 0 || viewport.height <= 0 || content.width <= 0 || content.height <= 0) {
                return TopologyViewport(1f, 0f, 0f)
            }
            val scale = min(
                1f,
                min(
                    (viewport.width - VIEWPORT_PADDING).coerceAtLeast(1f) / content.width,
                    (viewport.height - VIEWPORT_PADDING).coerceAtLeast(1f) / content.height,
                ),
            ).coerceIn(MINIMUM_SCALE, MAXIMUM_SCALE)
            return TopologyViewport(scale, 0f, 0f)
        }

        fun clamp(viewport: TopologySize, content: TopologySize, proposed: TopologyViewport): TopologyViewport {
            val scale = proposed.scale.coerceIn(MINIMUM_SCALE, MAXIMUM_SCALE)
            val horizontal = max(PAN_MARGIN, (content.width * scale - viewport.width) / 2 + PAN_MARGIN)
            val vertical = max(PAN_MARGIN, (content.height * scale - viewport.height) / 2 + PAN_MARGIN)
            return TopologyViewport(
                scale = scale,
                offsetX = proposed.offsetX.coerceIn(-horizontal, horizontal),
                offsetY = proposed.offsetY.coerceIn(-vertical, vertical),
            )
        }
    }
}

internal fun retainedTopologySelection(
    previousGraphIdentity: String?,
    next: PresentedTopology,
    selectedNodeId: String?,
): String? = selectedNodeId?.takeIf { previousGraphIdentity == next.identity && it in next.nodesById }
