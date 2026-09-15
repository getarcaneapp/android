package app.getarcane.android

import android.app.Application
import app.getarcane.android.core.ArcaneClientManager
import app.getarcane.android.core.OperationStore

class ArcaneApplication : Application() {
    lateinit var arcaneManager: ArcaneClientManager
        private set
    lateinit var operationStore: OperationStore
        private set

    override fun onCreate() {
        super.onCreate()
        arcaneManager = ArcaneClientManager(this)
        operationStore = OperationStore(this, arcaneManager)
        arcaneManager.attachOperationStore(operationStore)
    }
}
