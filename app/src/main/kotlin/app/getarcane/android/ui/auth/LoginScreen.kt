package app.getarcane.android.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.R
import app.getarcane.android.core.ArcaneClientManager
import app.getarcane.android.core.AuthStatus
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.ui.components.ErrorBanner

/**
 * Login / first-run setup. Port of the iOS `LoginView`: an Arcane logo tile on a brand-tinted
 * gradient, a glassy field card (server URL in setup mode, credentials otherwise), a "Try the demo"
 * card, and matching primary actions.
 */
@Composable
fun LoginScreen() {
    val manager = LocalArcaneManager.current
    val brand = MaterialTheme.colorScheme.primary
    val bg = MaterialTheme.colorScheme.background
    val isSetup = manager.authStatus == AuthStatus.SETUP
    val focusManager = LocalFocusManager.current

    var url by rememberSaveable { mutableStateOf(manager.serverUrl) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPasswordForm by rememberSaveable { mutableStateOf(false) }

    // When OIDC is available the password form is hidden behind a disclosure so the provider button
    // is the primary action; the user can still reveal local sign-in (admin fallback).
    val shouldShowPassword = !manager.isOidcAvailable || showPasswordForm

    // Re-fetch OIDC status whenever we enter login (or the server changes) so the provider button
    // shows correctly. Keyed on auth status + server URL; the manager no-ops in setup mode.
    // Mirrors iOS `LoginView`'s `.task(id:)` OIDC refresh.
    LaunchedEffect(manager.authStatus, manager.serverUrl) {
        manager.refreshOidcStatus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(bg, brand.copy(alpha = 0.06f)),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(brand.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.20f),
                        radius = size.minDimension * 0.9f,
                    ),
                )
            }
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Header(isSetup = isSetup, isStartingDemo = manager.isStartingDemo, brand = brand)

            if (!manager.isStartingDemo) {
                if (isSetup) {
                    SetupFields(value = url, onValueChange = { url = it }) {
                        focusManager.clearFocus()
                        manager.configure(url)
                    }
                } else {
                    CredentialsFields(
                        manager = manager,
                        username = username,
                        onUsername = { username = it },
                        password = password,
                        onPassword = { password = it },
                        showPassword = shouldShowPassword,
                        onSubmit = {
                            focusManager.clearFocus()
                            manager.login(username.trim(), password)
                        },
                    )
                }

                manager.errorMessage?.let { ErrorBanner(it) }
                manager.demoExpiredMessage?.let { InfoBanner(it) { manager.dismissDemoExpiredMessage() } }

                Actions(
                    manager = manager,
                    isSetup = isSetup,
                    brand = brand,
                    connectEnabled = url.isNotBlank(),
                    onConnect = {
                        focusManager.clearFocus()
                        manager.configure(url)
                    },
                    signInEnabled = username.isNotBlank() && password.isNotBlank(),
                    onSignIn = {
                        focusManager.clearFocus()
                        manager.login(username.trim(), password)
                    },
                    showPassword = shouldShowPassword,
                    showPasswordForm = showPasswordForm,
                    onTogglePasswordForm = { showPasswordForm = it; focusManager.clearFocus() },
                    onChangeServer = {
                        focusManager.clearFocus()
                        manager.changeServer()
                    },
                )
            }

            DemoCard(manager = manager, brand = brand)
        }
    }
}

// MARK: - Header

@Composable
private fun Header(isSetup: Boolean, isStartingDemo: Boolean, brand: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(24.dp),
                    spotColor = brand.copy(alpha = 0.45f),
                    ambientColor = brand.copy(alpha = 0.25f),
                )
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.04f)),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.arcane_logo),
                contentDescription = "Arcane",
                modifier = Modifier.size(68.dp),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Arcane", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            val subtitle = when {
                isStartingDemo -> "Setting things up for you…"
                isSetup -> "Connect to your Arcane server"
                else -> null
            }
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// MARK: - Forms

@Composable
private fun SetupFields(value: String, onValueChange: (String) -> Unit, onGo: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldCard {
            FieldRow(
                icon = Icons.Filled.Dns,
                label = "Server URL",
                value = value,
                onValueChange = onValueChange,
                placeholder = "https://arcane.example.com",
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
                keyboardActions = KeyboardActions(onGo = { onGo() }),
            )
        }
        Text(
            "For a local server, include the scheme — e.g. http://192.168.1.50:3000",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun CredentialsFields(
    manager: ArcaneClientManager,
    username: String,
    onUsername: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    showPassword: Boolean,
    onSubmit: () -> Unit,
) {
    val passwordFocus = remember { FocusRequester() }
    FieldCard {
        // Server (read-only) row.
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Dns, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Server", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                manager.serverUrl,
                style = MaterialTheme.typography.bodyMedium,
                color = if (showPassword) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (showPassword) {
            HorizontalDivider(Modifier.padding(start = 16.dp))
            FieldRow(
                icon = Icons.Filled.Person,
                label = "Username",
                value = username,
                onValueChange = onUsername,
                placeholder = "Username",
                imeAction = ImeAction.Next,
                keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
            )
            HorizontalDivider(Modifier.padding(start = 16.dp))
            FieldRow(
                icon = Icons.Filled.Lock,
                label = "Password",
                value = password,
                onValueChange = onPassword,
                placeholder = "Password",
                imeAction = ImeAction.Go,
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                visualTransformation = PasswordVisualTransformation(),
                textFieldModifier = Modifier.focusRequester(passwordFocus),
            )
        }
    }
}

// MARK: - Actions

@Composable
private fun Actions(
    manager: ArcaneClientManager,
    isSetup: Boolean,
    brand: Color,
    connectEnabled: Boolean,
    onConnect: () -> Unit,
    signInEnabled: Boolean,
    onSignIn: () -> Unit,
    showPassword: Boolean,
    showPasswordForm: Boolean,
    onTogglePasswordForm: (Boolean) -> Unit,
    onChangeServer: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (isSetup) {
            PrimaryButton(
                text = "Connect",
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                enabled = connectEnabled && !manager.isLoading,
                loading = manager.isLoading && !manager.isStartingDemo,
                onClick = onConnect,
            )
        } else {
            if (manager.isOidcAvailable && !showPasswordForm) {
                PrimaryButton(
                    text = "Continue with ${manager.oidc?.providerName?.takeIf { it.isNotBlank() } ?: "OIDC"}",
                    icon = Icons.Filled.VpnKey,
                    enabled = !manager.isLoading,
                    loading = false,
                    onClick = { /* OIDC browser flow wired via OidcAuthenticator */ },
                )
            }
            if (manager.isOidcAvailable) {
                OutlinedButton(
                    onClick = { onTogglePasswordForm(!showPasswordForm) },
                    enabled = !manager.isLoading,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Icon(if (showPasswordForm) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (showPasswordForm) "Hide password sign in" else "Sign in with username and password")
                }
            }
            if (showPassword) {
                PrimaryButton(
                    text = "Sign In",
                    icon = Icons.AutoMirrored.Filled.Login,
                    enabled = signInEnabled && !manager.isLoading,
                    loading = manager.isLoading,
                    onClick = onSignIn,
                )
            }
            TextButton(onClick = onChangeServer, modifier = Modifier.fillMaxWidth()) {
                Text("Change Server", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, icon: ImageVector, enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

// MARK: - Demo card

@Composable
private fun DemoCard(manager: ArcaneClientManager, brand: Color) {
    val starting = manager.isStartingDemo
    Surface(
        onClick = { manager.startDemo() },
        enabled = !manager.isLoading,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, brand.copy(alpha = 0.22f)),
        modifier = Modifier.fillMaxWidth().alpha(if (manager.isLoading && !starting) 0.5f else 1f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(40.dp).background(brand.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.AutoAwesome, null, tint = brand, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (starting) "Starting demo…" else "Try the demo",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (starting) "This usually takes about 30 seconds." else "Temporary instance for ~10 minutes. No account needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (starting) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = brand)
            } else {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = brand, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// MARK: - Building blocks

@Composable
private fun FieldCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun FieldRow(
    icon: ImageVector,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textFieldModifier: Modifier = Modifier,
) {
    val brand = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(brand),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction,
                capitalization = KeyboardCapitalization.None,
                autoCorrect = false,
            ),
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            modifier = textFieldModifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                inner()
            },
        )
    }
}

@Composable
private fun InfoBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary)
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, "Dismiss", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}
