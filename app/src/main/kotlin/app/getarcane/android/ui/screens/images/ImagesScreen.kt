package app.getarcane.android.ui.screens.images

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.displayName
import app.getarcane.android.nav.PopToRootOnSignal
import app.getarcane.sdk.EnvironmentId
import app.getarcane.sdk.models.image.ImageSummary

enum class ImagesInitialDestination {
    List,
    Vulnerabilities,
}

/**
 * Images tab with its own nested back stack:
 * list -> detail -> (vulnerabilities), plus list -> updates / all-vulnerabilities.
 * Mirrors the iOS `ImagesView` navigation graph.
 */
@Composable
fun ImagesScreen(
    popToRootSignal: Int = 0,
    initialDestination: ImagesInitialDestination = ImagesInitialDestination.List,
    vulnerabilitiesEnvironmentId: EnvironmentId? = null,
    vulnerabilitiesEnvironmentName: String? = null,
    onInitialDestinationHandled: () -> Unit = {},
    onInitialDestinationBack: (() -> Unit)? = null,
    nav: NavHostController = rememberNavController(),
) {
    val manager = LocalArcaneManager.current
    val initialRoute = remember { initialDestination.startRoute }
    nav.PopToRootOnSignal(popToRootSignal, rootRoute = "list")
    var initialVulnerabilitiesActive by remember {
        mutableStateOf(initialDestination == ImagesInitialDestination.Vulnerabilities)
    }
    LaunchedEffect(initialDestination) {
        when (initialDestination) {
            ImagesInitialDestination.List -> Unit
            ImagesInitialDestination.Vulnerabilities -> {
                if (initialRoute != "vulnerabilities") {
                    nav.navigate("vulnerabilities") {
                        popUpTo("list")
                        launchSingleTop = true
                    }
                }
                initialVulnerabilitiesActive = true
            }
        }
        if (initialDestination != ImagesInitialDestination.List) {
            onInitialDestinationHandled()
        }
    }
    // The list endpoint is the only source for the full set of images; keep the last-loaded
    // list here so the Updates screen can derive its tagged-ref set without re-fetching.
    var loadedImages by remember { mutableStateOf<List<ImageSummary>>(emptyList()) }

    NavHost(navController = nav, startDestination = initialRoute) {
        composable("list") {
            ImageListScreen(
                onLoaded = { loadedImages = it },
                onOpen = { image ->
                    val identity = ImageInsightIdentity(
                        sessionKey = imageInsightSessionKey(
                            manager.serverSessionIdentity,
                            manager.currentUser?.id.orEmpty(),
                        ),
                        environmentId = manager.activeEnvironmentId.rawValue,
                        environmentName = manager.activeEnvironmentName,
                        imageId = image.id,
                        imageDisplayName = image.displayName,
                    )
                    nav.navigate(imageInsightRoute("detail", identity))
                },
                onOpenUpdates = { nav.navigate("updates") },
                onOpenVulnerabilities = { nav.navigate("vulnerabilities") },
            )
        }
        composable(
            "detail/$IMAGE_INSIGHT_ROUTE_PATTERN",
            arguments = imageInsightRouteArguments,
        ) { entry ->
            val identity = imageInsightIdentityFromRoute { entry.arguments?.getString(it) }
            ImageDetailScreen(
                identity = identity,
                onBack = { nav.popBackStack() },
                onOpenAttestations = {
                    nav.navigate(imageInsightRoute("attestations", identity))
                },
                onOpenHistory = {
                    nav.navigate(imageInsightRoute("history", identity))
                },
                onOpenVulnerabilities = { imageId, displayName ->
                    nav.navigate(
                        "imageVulns/${
                            java.net.URLEncoder.encode(
                                imageId,
                                "UTF-8"
                            )
                        }/${java.net.URLEncoder.encode(displayName, "UTF-8")}"
                    )
                },
            )
        }
        composable(
            "attestations/$IMAGE_INSIGHT_ROUTE_PATTERN",
            arguments = imageInsightRouteArguments,
        ) { entry ->
            ImageAttestationsScreen(
                identity = imageInsightIdentityFromRoute { entry.arguments?.getString(it) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            "history/$IMAGE_INSIGHT_ROUTE_PATTERN",
            arguments = imageInsightRouteArguments,
        ) { entry ->
            ImageHistoryScreen(
                identity = imageInsightIdentityFromRoute { entry.arguments?.getString(it) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            "imageVulns/{imageId}/{displayName}",
            arguments = listOf(
                navArgument("imageId") { type = NavType.StringType },
                navArgument("displayName") { type = NavType.StringType },
            ),
        ) { entry ->
            val imageId =
                java.net.URLDecoder.decode(entry.arguments?.getString("imageId").orEmpty(), "UTF-8")
            val displayName = java.net.URLDecoder.decode(
                entry.arguments?.getString("displayName").orEmpty(),
                "UTF-8"
            )
            ImageVulnerabilitiesScreen(
                imageId = imageId,
                imageDisplayName = displayName,
                onBack = { nav.popBackStack() },
            )
        }
        composable("updates") {
            ImageUpdatesScreen(images = loadedImages, onBack = { nav.popBackStack() })
        }
        composable("vulnerabilities") {
            val returnToInitialSource = {
                initialVulnerabilitiesActive = false
                onInitialDestinationBack?.invoke()
            }
            BackHandler(enabled = initialVulnerabilitiesActive && onInitialDestinationBack != null) {
                returnToInitialSource()
            }
            AllVulnerabilitiesScreen(
                onBack = {
                    if (initialVulnerabilitiesActive && onInitialDestinationBack != null) {
                        returnToInitialSource()
                    } else {
                        nav.popBackStack()
                    }
                },
                environmentId = vulnerabilitiesEnvironmentId,
                environmentName = vulnerabilitiesEnvironmentName,
            )
        }
    }
}

private const val IMAGE_INSIGHT_ROUTE_PATTERN =
    "{sessionKey}/{environmentId}/{environmentName}/{imageId}/{imageDisplayName}"

private val imageInsightRouteArguments = listOf(
    "sessionKey",
    "environmentId",
    "environmentName",
    "imageId",
    "imageDisplayName",
).map { name -> navArgument(name) { type = NavType.StringType } }

internal val ImagesInitialDestination.startRoute: String
    get() = when (this) {
        ImagesInitialDestination.List -> "list"
        ImagesInitialDestination.Vulnerabilities -> "vulnerabilities"
    }
