package app.getarcane.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import app.getarcane.android.core.parseOperationCancelRoute

class OperationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CANCEL) return
        val operationId = parseOperationCancelRoute(intent.data?.toString()) ?: return
        val pending = goAsync()
        val app = context.applicationContext as? ArcaneApplication
        if (app == null) {
            pending.finish()
            return
        }
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            try {
                val completed = withTimeoutOrNull(RECEIVER_DEADLINE_MILLIS) {
                    app.operationStore.cancelAfterAuthentication(operationId)
                    true
                }
                if (completed == null) app.operationStore.continueCancellationAfterAuthentication(operationId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_CANCEL = "app.getarcane.android.action.CANCEL_OPERATION"
        const val RECEIVER_DEADLINE_MILLIS = 8_000L
    }
}
