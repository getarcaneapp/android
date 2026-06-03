package app.getarcane.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions

/**
 * Form field rows that mirror the iOS `FormFieldRows` components: a small semibold caption
 * label above the control, with optional helper text below. Material3 ports.
 */

@Composable
private fun FormLabel(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FormHelper(helper: String?) {
    if (!helper.isNullOrEmpty()) {
        Text(helper, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Labelled single/multi-line text field. Port of iOS `FormTextField`. */
@Composable
fun FormTextField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    autoCapitalize: Boolean = true,
    autoCorrect: Boolean = true,
    singleLine: Boolean = true,
    monospaced: Boolean = false,
    helper: String? = null,
    enabled: Boolean = true,
) {
    Column(modifier.fillMaxWidth().padding(vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FormLabel(title)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
            singleLine = singleLine,
            enabled = enabled,
            textStyle = if (monospaced) {
                MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
            } else {
                MaterialTheme.typography.bodyLarge
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                capitalization = if (autoCapitalize) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
                autoCorrect = autoCorrect,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        FormHelper(helper)
    }
}

/** Labelled secure (password) field. Port of iOS `FormSecureField`. */
@Composable
fun FormSecureField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    helper: String? = null,
    enabled: Boolean = true,
) {
    Column(modifier.fillMaxWidth().padding(vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FormLabel(title)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
            singleLine = true,
            enabled = enabled,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        FormHelper(helper)
    }
}

/** Labelled read-only value row. Port of iOS `FormValueRow`. */
@Composable
fun FormValueRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    helper: String? = null,
    monospaced: Boolean = false,
) {
    Column(modifier.fillMaxWidth().padding(vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FormLabel(title)
        Text(
            value,
            style = if (monospaced) {
                MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
            } else {
                MaterialTheme.typography.bodyLarge
            },
            color = MaterialTheme.colorScheme.onSurface,
        )
        FormHelper(helper)
    }
}

/** A single selectable option for [FormPicker]. */
data class FormPickerOption<T>(val value: T, val label: String)

/**
 * A title + inline menu picker row. Port of iOS `FormPicker` (a labelled `.menu` Picker).
 * The current selection's label shows as the trailing button; tapping opens a dropdown.
 */
@Composable
fun <T> FormPicker(
    title: String,
    selection: T,
    options: List<FormPickerOption<T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    helper: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().padding(vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Box {
                TextButton(onClick = { expanded = true }) {
                    Text(options.firstOrNull { it.value == selection }?.label ?: "")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = { onSelect(option.value); expanded = false },
                        )
                    }
                }
            }
        }
        FormHelper(helper)
    }
}
