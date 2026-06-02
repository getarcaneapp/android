package app.getarcane.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.Prefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Stored theme preference. Drives nothing here — persisting/applying it is the lead's job. */
enum class ThemeOption(val label: String) { Light("Light"), Dark("Dark"), Auto("Auto") }

/**
 * Appearance settings: a theme picker (Light/Dark/Auto) and the accent-color grid. Mirrors iOS
 * `AppearanceSettingsView` (the iOS-only alternate App Icon picker is omitted on Android). The
 * selected accent hex is persisted via [Prefs.accentHex]; the theme selection is held locally.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val scope = rememberCoroutineScope()

    var theme by remember { mutableStateOf(ThemeOption.Auto) }
    var accentHex by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        accentHex = prefs.accentHex.first() ?: ""
    }
    val selected = AccentColorOption.fromHex(accentHex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SettingsSectionHeader("Theme")
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                ThemeOption.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = theme == option,
                        onClick = { theme = option },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeOption.entries.size),
                    ) { Text(option.label) }
                }
            }

            SettingsSectionHeader("Accent Color")
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 60.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightForRows(AccentColorOption.entries.size)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = false,
            ) {
                items(AccentColorOption.entries, key = { it.name }) { option ->
                    AccentSwatch(
                        option = option,
                        isSelected = selected == option,
                        onClick = {
                            accentHex = option.hex
                            scope.launch { prefs.setAccentHex(option.hex) }
                        },
                    )
                }
            }
            SettingsSectionFooter("Choose a color to customize the app's appearance.")

            TextButton(
                onClick = {
                    accentHex = ""
                    scope.launch { prefs.setAccentHex("") }
                },
                modifier = Modifier.padding(start = 8.dp, top = 16.dp),
            ) {
                Text("Reset to Default", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AccentSwatch(option: AccentColorOption, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(option.color, CircleShape)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(Icons.Filled.Check, contentDescription = option.displayName, tint = Color.White)
        }
    }
}

/** Approximate height for the non-scrolling swatch grid so it lays out inside a vertical scroll. */
private fun Modifier.heightForRows(itemCount: Int): Modifier {
    // ~5 columns on a phone; 60dp cell + 12dp spacing per row.
    val rows = (itemCount + 4) / 5
    return this.then(Modifier.height((rows * 60 + (rows - 1).coerceAtLeast(0) * 12 + 16).dp))
}
