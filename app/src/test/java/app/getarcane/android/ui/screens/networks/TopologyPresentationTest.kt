package app.getarcane.android.ui.screens.networks

import app.getarcane.sdk.models.network.NetworkTopology
import app.getarcane.sdk.models.network.TopologyEdge
import app.getarcane.sdk.models.network.TopologyNode
import app.getarcane.sdk.models.network.TopologyNodeMetadata
import app.getarcane.sdk.models.network.TopologyNodeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TopologyPresentationTest {
    @Test
    fun `normalization is deterministic and drops duplicate missing reversed and cyclic edges`() {
        val topology = NetworkTopology(
            nodes = listOf(
                node("n", "Network", TopologyNodeType.NETWORK),
                node("c", "Container", TopologyNodeType.CONTAINER),
                node("c", "Duplicate", TopologyNodeType.CONTAINER),
                node("", "Blank", TopologyNodeType.CONTAINER),
            ),
            edges = listOf(
                edge("valid", "n", "c"),
                edge("duplicate", "n", "c"),
                edge("valid", "n", "c").copy(ipv4Address = "10.0.0.2/24"),
                edge("missing", "n", "missing"),
                edge("reversed", "c", "n"),
                edge("cycle", "n", "n"),
            ),
        )

        val normalized = TopologyNormalizer.normalize(topology)
        assertEquals(listOf("n", "c"), normalized.nodes.map { it.id })
        assertEquals(1, normalized.edges.size)
        assertEquals(1, normalized.issues.duplicateNodes)
        assertEquals(1, normalized.issues.malformedNodes)
        assertEquals(2, normalized.issues.duplicateEdges)
        assertEquals(1, normalized.issues.missingEndpointEdges)
        assertEquals(2, normalized.issues.invalidRelationshipEdges)
        assertEquals(normalized, TopologyNormalizer.normalize(topology))
    }

    @Test
    fun `blank labels get typed bounded fallbacks`() {
        val normalized = TopologyNormalizer.normalize(
            NetworkTopology(nodes = listOf(node("n123456789012345", " ", TopologyNodeType.NETWORK))),
        )
        assertEquals("Unnamed network (n12345678901)", normalized.nodes.single().label)
    }

    @Test
    fun `maximum graph remains interactive and over limit selects list fallback`() {
        val maximum = graph(
            networkCount = 1,
            containerCount = TopologyNormalizer.MAXIMUM_INTERACTIVE_NODES - 1,
            edgeCount = TopologyNormalizer.MAXIMUM_INTERACTIVE_NODES - 1,
        )
        assertTrue(TopologyNormalizer.normalize(maximum).interactiveEligible)

        val overLimit = graph(
            networkCount = 1,
            containerCount = TopologyNormalizer.MAXIMUM_INTERACTIVE_NODES,
            edgeCount = TopologyNormalizer.MAXIMUM_INTERACTIVE_NODES,
        )
        assertFalse(TopologyNormalizer.normalize(overLimit).interactiveEligible)
    }

    @Test
    fun `hard input bounds truncate safely`() {
        val topology = graph(
            networkCount = 1,
            containerCount = TopologyNormalizer.MAXIMUM_INPUT_NODES + 20,
            edgeCount = TopologyNormalizer.MAXIMUM_INPUT_EDGES + 20,
        )
        val normalized = TopologyNormalizer.normalize(topology)
        assertTrue(normalized.nodes.size <= TopologyNormalizer.MAXIMUM_INPUT_NODES)
        assertTrue(normalized.edges.size <= TopologyNormalizer.MAXIMUM_INPUT_EDGES)
        assertTrue(normalized.issues.inputNodesTruncated > 0)
        assertTrue(normalized.issues.inputEdgesTruncated > 0)
        assertFalse(normalized.interactiveEligible)
    }

    @Test
    fun `maximum hard-bound graph normalizes deterministically without truncation`() {
        val networks = List(40) { node("n$it", "Network $it", TopologyNodeType.NETWORK) }
        val containers = List(TopologyNormalizer.MAXIMUM_INPUT_NODES - networks.size) {
            node("c$it", "Container $it", TopologyNodeType.CONTAINER)
        }
        val edges = List(TopologyNormalizer.MAXIMUM_INPUT_EDGES) { index ->
            val network = index % networks.size
            val container = (index / networks.size) % containers.size
            TopologyEdge(
                id = "e$index",
                source = "n$network",
                target = "c$container",
                ipv4Address = "10.${index / 65_536}.${index / 256 % 256}.${index % 256}",
            )
        }

        val normalized = TopologyNormalizer.normalize(NetworkTopology(networks + containers, edges))

        assertEquals(TopologyNormalizer.MAXIMUM_INPUT_NODES, normalized.nodes.size)
        assertEquals(TopologyNormalizer.MAXIMUM_INPUT_EDGES, normalized.edges.size)
        assertEquals(0, normalized.issues.inputNodesTruncated)
        assertEquals(0, normalized.issues.inputEdgesTruncated)
        assertEquals(normalized, TopologyNormalizer.normalize(NetworkTopology(networks + containers, edges)))
        assertFalse(normalized.interactiveEligible)
    }

    @Test
    fun `layout is stable grouped and finite for isolated nodes`() {
        val normalized = TopologyNormalizer.normalize(graph(2, 5, 4))
        val first = TopologyGraphLayout.build(normalized)
        val second = TopologyGraphLayout.build(normalized)
        assertEquals(first, second)
        assertEquals(normalized.nodes.size, first.positions.size)
        assertTrue(first.contentSize.width > 0)
        assertTrue(first.contentSize.height > 0)
        assertTrue(first.positions.values.all { it.x.isFinite() && it.y.isFinite() })
    }

    @Test
    fun `fit and gesture viewport stay bounded`() {
        val viewport = TopologySize(800f, 600f)
        val content = TopologySize(2_000f, 4_000f)
        val fit = TopologyViewport.fit(viewport, content)
        assertTrue(fit.scale in TopologyViewport.MINIMUM_SCALE..TopologyViewport.MAXIMUM_SCALE)

        val clamped = TopologyViewport.clamp(
            viewport,
            content,
            TopologyViewport(99f, Float.MAX_VALUE, -Float.MAX_VALUE),
        )
        assertEquals(TopologyViewport.MAXIMUM_SCALE, clamped.scale)
        assertTrue(clamped.offsetX.isFinite())
        assertTrue(clamped.offsetY.isFinite())
    }

    @Test
    fun `selection survives only the same graph identity`() {
        val first = TopologyNormalizer.normalize(graph(1, 1, 1))
        assertEquals("c0", retainedTopologySelection(first.identity, first, "c0"))
        assertNull(retainedTopologySelection("different", first, "c0"))
        assertNull(retainedTopologySelection(first.identity, first, "deleted"))
    }

    private fun graph(networkCount: Int, containerCount: Int, edgeCount: Int): NetworkTopology {
        val networks = List(networkCount) { node("n$it", "Network $it", TopologyNodeType.NETWORK) }
        val containers = List(containerCount) { node("c$it", "Container $it", TopologyNodeType.CONTAINER) }
        val edges = List(edgeCount) { index ->
            edge("e$index", "n${index % networkCount.coerceAtLeast(1)}", "c${index % containerCount.coerceAtLeast(1)}")
        }
        return NetworkTopology(networks + containers, edges)
    }

    private fun node(id: String, name: String, type: TopologyNodeType) = TopologyNode(
        id = id,
        name = name,
        type = type,
        metadata = TopologyNodeMetadata(status = "running"),
    )

    private fun edge(id: String, source: String, target: String) = TopologyEdge(
        id = id,
        source = source,
        target = target,
    )
}
