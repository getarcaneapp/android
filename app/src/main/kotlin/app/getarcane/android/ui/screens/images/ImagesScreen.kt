package app.getarcane.android.ui.screens.images

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.getarcane.android.nav.PopToRootOnSignal
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
    onInitialDestinationHandled: () -> Unit = {},
    onInitialDestinationBack: (() -> Unit)? = null,
) {
    val nav = rememberNavController()
    nav.PopToRootOnSignal(popToRootSignal, rootRoute = "list")
    var initialVulnerabilitiesActive by remember { mutableStateOf(false) }
    LaunchedEffect(initialDestination) {
        when (initialDestination) {
            ImagesInitialDestination.List -> Unit
            ImagesInitialDestination.Vulnerabilities -> nav.navigate("vulnerabilities") {
                popUpTo("list")
                launchSingleTop = true
            }.also { initialVulnerabilitiesActive = true }
        }
        if (initialDestination != ImagesInitialDestination.List) {
            onInitialDestinationHandled()
        }
    }
    // The list endpoint is the only source for the full set of images; keep the last-loaded
    // list here so the Updates screen can derive its tagged-ref set without re-fetching.
    var loadedImages by remember { mutableStateOf<List<ImageSummary>>(emptyList()) }

    NavHost(navController = nav, startDestination = "list") {
        composable("list") {
            ImageListScreen(
                onLoaded = { loadedImages = it },
                onOpen = { id -> nav.navigate("detail/$id") },
                onOpenUpdates = { nav.navigate("updates") },
                onOpenVulnerabilities = { nav.navigate("vulnerabilities") },
            )
        }
        composable(
            "detail/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            ImageDetailScreen(
                id = entry.arguments?.getString("id").orEmpty(),
                onBack = { nav.popBackStack() },
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
            )
        }
    }
}
