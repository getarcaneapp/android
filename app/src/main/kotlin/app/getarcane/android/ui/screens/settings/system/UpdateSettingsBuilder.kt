package app.getarcane.android.ui.screens.settings.system

import app.getarcane.sdk.models.settings.UpdateSettings
import app.getarcane.sdk.serialization.ArcaneJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Build an [UpdateSettings] body from a flat `key -> string value` map of changed settings, mirroring
 * the iOS approach of JSON-encoding the changed pairs and decoding into the typed request. Keys not
 * present on [UpdateSettings] are tolerated (the codec ignores unknown keys).
 */
fun updateSettingsFrom(changed: Map<String, String>): UpdateSettings {
    val obj = JsonObject(changed.mapValues { JsonPrimitive(it.value) })
    return ArcaneJson.default.decodeFromJsonElement(UpdateSettings.serializer(), obj)
}
