package app.getarcane.android.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.WeakHashMap

private object SecureWindowOwners {
    private data class Entry(var owners: Int, val initiallySecure: Boolean)
    private val entries = WeakHashMap<Window, Entry>()

    @Synchronized
    fun acquire(window: Window) {
        val entry = entries[window]
        if (entry == null) {
            val initiallySecure = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
            entries[window] = Entry(1, initiallySecure)
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            entry.owners++
        }
    }

    @Synchronized
    fun release(window: Window) {
        val entry = entries[window] ?: return
        entry.owners--
        if (entry.owners <= 0) {
            if (!entry.initiallySecure) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            entries.remove(window)
        }
    }
}

private tailrec fun Context.activityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activityOrNull()
    else -> null
}

/** Prevents screenshots while write-only credentials or recovery material are on screen. */
@Composable
fun ProtectSensitiveWindow(active: Boolean = true) {
    val window = LocalContext.current.activityOrNull()?.window
    DisposableEffect(window, active) {
        if (active && window != null) SecureWindowOwners.acquire(window)
        onDispose { if (active && window != null) SecureWindowOwners.release(window) }
    }
}

/** Clears caller-owned transient secrets whenever the activity is backgrounded or disposed. */
@Composable
fun ClearSensitiveStateOnStop(clear: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentClear by rememberUpdatedState(clear)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) currentClear() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            currentClear()
        }
    }
}

/**
 * A non-saveable write-only field. Its value is removed from the accessibility semantics tree;
 * callers must clear [value] on every terminal transition.
 */
@Composable
fun SensitiveOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { androidx.compose.material3.Text(label) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = PasswordVisualTransformation(),
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = label },
    )
}
