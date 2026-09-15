package app.getarcane.android.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticatedRouteTest {
    @Test
    fun `resource route round trips server environment and unicode resource identity`() {
        val route = AuthenticatedRoute(
            serverBindingHash = "a".repeat(64),
            destination = RouteDestination.CONTAINER,
            environmentId = "edge/eu",
            resourceId = "container ü",
        )
        assertEquals(RouteParseResult.Valid(route), AuthenticatedRouteCodec.parse(AuthenticatedRouteCodec.encode(route)))
    }

    @Test
    fun `current binding is limited to static non-resource destinations`() {
        val current = AuthenticatedRoute(null, RouteDestination.DASHBOARD)
        assertEquals(RouteParseResult.Valid(current), AuthenticatedRouteCodec.parse(AuthenticatedRouteCodec.encode(current)))
        assertTrue(
            AuthenticatedRouteCodec.validateShape(
                AuthenticatedRoute(null, RouteDestination.CONTAINER, "env", "id"),
            ) != null,
        )
    }

    @Test
    fun `malformed unsupported and oversized intents fail closed`() {
        assertTrue(AuthenticatedRouteCodec.parse("https://example.com") is RouteParseResult.NotOwned)
        assertTrue(AuthenticatedRouteCodec.parse("arcane-mobile://route/v9/current/dashboard/-/-") is RouteParseResult.Invalid)
        assertTrue(AuthenticatedRouteCodec.parse("arcane-mobile://route/v1/current/mutate/-/-") is RouteParseResult.Invalid)
        assertTrue(AuthenticatedRouteCodec.parse("arcane-mobile://route/v1/current/dashboard/0/-") is RouteParseResult.Invalid)
        assertTrue(AuthenticatedRouteCodec.parse("arcane-mobile://route/v1/current/dashboard/-/-?next=project") is RouteParseResult.Invalid)
        assertTrue(AuthenticatedRouteCodec.parse("arcane-mobile://route/v1/current/container/656e76/6964") is RouteParseResult.Invalid)
        assertTrue(AuthenticatedRouteCodec.parse("arcane-mobile://route/" + "x".repeat(5_000)) is RouteParseResult.Invalid)
    }

    @Test
    fun `coordinator preserves a login-required continuation until consumed`() {
        val coordinator = AuthenticatedRouteCoordinator()
        val route = AuthenticatedRoute(null, RouteDestination.PROJECTS)
        coordinator.submit(route)
        assertEquals(route, coordinator.pendingRoute)
        coordinator.consume(route)
        assertEquals(null, coordinator.pendingRoute)
    }
}
