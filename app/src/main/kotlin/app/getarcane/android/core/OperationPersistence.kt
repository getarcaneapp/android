package app.getarcane.android.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val Context.operationDataStore: DataStore<Preferences> by preferencesDataStore(name = "arcane_operations")

internal interface OperationPersistence {
    suspend fun load(): OperationLedgerRead
    suspend fun save(operations: List<OperationRecord>)
    suspend fun clear()
}

internal sealed interface OperationLedgerRead {
    data class Current(val operations: List<OperationRecord>) : OperationLedgerRead
    data object FutureSchema : OperationLedgerRead
    data object Corrupt : OperationLedgerRead
}

@Serializable
private data class OperationLedger(
    val schemaVersion: Int = OPERATION_SCHEMA_VERSION,
    val operations: List<OperationRecord> = emptyList(),
)

internal object OperationLedgerCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    fun encode(operations: List<OperationRecord>): String =
        json.encodeToString(
            OperationLedger(operations = operations.take(MAX_OPERATION_ROWS).mapNotNull(::sanitize)),
        )

    fun decode(raw: String?): OperationLedgerRead {
        if (raw.isNullOrBlank()) return OperationLedgerRead.Current(emptyList())
        return try {
            val element = json.parseToJsonElement(raw).jsonObject
            val version = element["schemaVersion"]?.jsonPrimitive?.intOrNull ?: return OperationLedgerRead.Corrupt
            if (version > OPERATION_SCHEMA_VERSION) return OperationLedgerRead.FutureSchema
            if (version < 1) return OperationLedgerRead.Corrupt
            val ledger = json.decodeFromString<OperationLedger>(raw)
            OperationLedgerRead.Current(ledger.operations.take(MAX_OPERATION_ROWS).mapNotNull(::sanitize))
        } catch (_: Throwable) {
            OperationLedgerRead.Corrupt
        }
    }

    private fun sanitize(record: OperationRecord): OperationRecord? {
        if (!ACTIVITY_BATCH_ID_PATTERN.matches(record.operationId)) return null
        if (record.activityBatchId != record.operationId) return null
        if (!HASH_PATTERN.matches(record.serverBindingHash) ||
            !HASH_PATTERN.matches(record.accountBindingHash) ||
            !HASH_PATTERN.matches(record.credentialOriginHash) ||
            !HASH_PATTERN.matches(record.duplicateKeyDigest)
        ) return null
        if (record.environmentId.isBlank() || record.environmentId.length > 128) return null
        val opaqueTarget = record.opaqueTargetId
            ?.takeIf { record.kind != OperationKind.IMAGE_PULL && it.length <= 256 }
        return record.copy(
            opaqueTargetId = opaqueTarget,
            serverActivityId = record.serverActivityId?.take(128),
            fleetJobId = record.fleetJobId?.take(128),
            progressPercent = record.progressPercent?.coerceIn(0, 100),
            environmentName = "",
            targetName = "",
            lines = emptyList(),
            phases = emptyList(),
            detailMessage = null,
        )
    }
}

internal class DataStoreOperationPersistence(
    private val store: DataStore<Preferences>,
) : OperationPersistence {
    constructor(context: Context) : this(context.applicationContext.operationDataStore)

    override suspend fun load(): OperationLedgerRead =
        OperationLedgerCodec.decode(store.data.first()[LEDGER])

    override suspend fun save(operations: List<OperationRecord>) {
        val encoded = OperationLedgerCodec.encode(operations)
        store.edit { preferences -> preferences[LEDGER] = encoded }
    }

    override suspend fun clear() {
        store.edit { preferences -> preferences.remove(LEDGER) }
    }

    private companion object {
        val LEDGER = stringPreferencesKey("operation_ledger")
    }
}

internal const val MAX_OPERATION_ROWS = 32
internal const val MAX_TERMINAL_OPERATION_ROWS = 20
internal const val OPERATION_SCHEMA_VERSION = 1
internal val ACTIVITY_BATCH_ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,64}$")
private val HASH_PATTERN = Regex("^[a-f0-9]{64}$")
