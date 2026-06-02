@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.getarcane.android.ui.screens.settings

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.components.ErrorBanner
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneIndigo
import app.getarcane.sdk.models.user.User
import app.getarcane.sdk.models.user.isAdmin
import kotlinx.coroutines.launch

/** Users list. Port of iOS `UsersView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(onOpenUser: (String) -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<Loadable<List<User>>>(Loadable.Loading) }
    var refreshKey by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshKey) {
        if (client == null) return@LaunchedEffect
        if (state !is Loadable.Success) state = Loadable.Loading
        state = try {
            Loadable.Success(client.users.listPaginated(limit = 100).data)
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
        refreshing = false
    }

    SettingsListScaffold(
        title = "Users",
        onAdd = { showCreate = true },
        addContentDescription = "Add user",
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; refreshKey++ },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (val s = state) {
                is Loadable.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is Loadable.Error -> Box(Modifier.fillMaxSize().padding(16.dp)) { ErrorBanner(s.message, onRetry = { refreshKey++ }) }
                is Loadable.Success -> {
                    if (s.value.isEmpty()) {
                        ContentUnavailable("No Users", Icons.Filled.PersonOff, "No users found")
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(s.value, key = { it.id }) { user ->
                                UserRow(
                                    user = user,
                                    onClick = { onOpenUser(user.id) },
                                    onDelete = {
                                        scope.launch {
                                            try {
                                                client?.users?.delete(user.id)
                                                refreshKey++
                                            } catch (e: Throwable) {
                                                actionError = friendlyErrorMessage(e)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateUserDialog(
            onDismiss = { showCreate = false },
            onCreated = { showCreate = false; refreshKey++ },
        )
    }

    actionError?.let { msg ->
        InfoAlert(title = "Couldn't Delete User", message = msg, onDismiss = { actionError = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserRow(user: User, onClick: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircleIcon(Icons.Filled.Person, if (user.isAdmin) ArcaneIndigo else ArcaneBlue)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(user.displayUsername, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                user.email?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (user.isAdmin) Pill("Admin", ArcaneIndigo, filled = true)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = { menu = false; onDelete() },
                leadingIcon = { Icon(Icons.Filled.Delete, null) },
            )
        }
    }
}
