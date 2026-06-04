package app.getarcane.android.ui.screens.jobs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneIndigo

/**
 * Jobs tab. Mirrors the iOS `JobsView`: a small menu listing "Jobs" and "Schedules", each pushing
 * a detail screen. Uses a nested NavHost for the list -> detail back stacks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobsScreen() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "menu") {
        composable("menu") {
            Scaffold(topBar = { TopAppBar(title = { Text("Jobs") }) }) { padding ->
                Column(Modifier
                    .fillMaxSize()
                    .padding(padding)) {
                    ListItem(
                        leadingContent = {
                            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = ArcaneIndigo)
                        },
                        headlineContent = { Text("Jobs") },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForwardIos,
                                null,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        },
                        modifier = Modifier.clickable { nav.navigate("list") },
                    )
                    HorizontalDivider()
                    ListItem(
                        leadingContent = {
                            Icon(Icons.Filled.CalendarMonth, null, tint = ArcaneBlue)
                        },
                        headlineContent = { Text("Schedules") },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForwardIos,
                                null,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        },
                        modifier = Modifier.clickable { nav.navigate("schedules") },
                    )
                    HorizontalDivider()
                }
            }
        }
        composable("list") {
            JobsListScreen(
                onBack = { nav.popBackStack() },
                onOpen = { id -> nav.navigate("detail/$id") },
            )
        }
        composable("detail/{id}") { entry ->
            JobDetailScreen(
                jobId = entry.arguments?.getString("id").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
        composable("schedules") {
            JobScheduleConfigScreen(onBack = { nav.popBackStack() })
        }
    }
}
