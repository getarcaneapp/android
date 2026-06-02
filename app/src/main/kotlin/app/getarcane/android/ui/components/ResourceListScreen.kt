package app.getarcane.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.friendlyErrorMessage

/**
 * Reusable list screen: top bar + search + pull-to-refresh + loading/error/empty states + a list.
 * Most resource tabs (Volumes, Networks, Ports, Events, …) are a thin wrapper over this.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ResourceListScreen(
    title: String,
    searchPlaceholder: String,
    reloadKey: Any?,
    load: suspend () -> List<T>,
    itemKey: (T) -> Any,
    matches: (T, String) -> Boolean,
    emptyText: String = "Nothing here",
    row: @Composable (T) -> Unit,
) {
    var state by remember { mutableStateOf<Loadable<List<T>>>(Loadable.Loading) }
    var search by remember { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(reloadKey, refreshKey) {
        if (state !is Loadable.Success<*>) state = Loadable.Loading
        state = try {
            Loadable.Success(load())
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
        refreshing = false
    }

    Scaffold(topBar = { TopAppBar(title = { Text(title) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text(searchPlaceholder) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { refreshing = true; refreshKey++ },
                modifier = Modifier.fillMaxSize(),
            ) {
                when (val s = state) {
                    is Loadable.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    is Loadable.Error -> Box(Modifier.fillMaxSize().padding(16.dp)) { ErrorBanner(s.message, onRetry = { refreshKey++ }) }
                    is Loadable.Success -> {
                        val items = if (search.isBlank()) s.value else s.value.filter { matches(it, search) }
                        if (items.isEmpty()) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(items, key = itemKey) { row(it) }
                            }
                        }
                    }
                }
            }
        }
    }
}
