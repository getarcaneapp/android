package app.getarcane.android.ui.screens.gitops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.getarcane.sdk.models.gitops.GitRepository

/** Collected git-repository form values. */
data class GitRepoForm(
    val name: String,
    val url: String,
    val authType: String,
    val username: String,
    val token: String,
    val sshKey: String,
    val description: String,
    val enabled: Boolean,
)

/**
 * Create/edit form for a git repository. Mirrors the iOS `DynamicCreateFormView` field set used by
 * `GitRepositoriesView` (name, url, auth, username, token, ssh key), plus description and enabled
 * toggle from the typed model. Save is disabled until required fields are filled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitRepositoryFormSheet(
    title: String,
    existing: GitRepository?,
    onDismiss: () -> Unit,
    onSubmit: (GitRepoForm) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var url by remember { mutableStateOf(existing?.url ?: "") }
    var authType by remember { mutableStateOf(existing?.authType ?: "none") }
    var username by remember { mutableStateOf(existing?.username ?: "") }
    var token by remember { mutableStateOf("") }
    var sshKey by remember { mutableStateOf("") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }

    val canSave = name.trim().isNotEmpty() && url.trim().isNotEmpty()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = { onSubmit(GitRepoForm(name.trim(), url.trim(), authType.trim().ifEmpty { "none" }, username.trim(), token, sshKey, description.trim(), enabled)) }, enabled = canSave) {
                    Text("Save")
                }
            }
            OutlinedTextField(name, { name = it }, label = { Text("Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(url, { url = it }, label = { Text("URL *") }, placeholder = { Text("https://github.com/org/repo.git") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(authType, { authType = it }, label = { Text("Auth Type") }, placeholder = { Text("none / token / ssh") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(token, { token = it }, label = { Text("Token") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(sshKey, { sshKey = it }, label = { Text("SSH Key") }, minLines = 4, maxLines = 10, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text("Description") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Enabled", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
        }
    }
}
