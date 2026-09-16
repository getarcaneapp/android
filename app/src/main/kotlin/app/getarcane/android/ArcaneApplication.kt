package app.getarcane.android

import android.app.Application
import app.getarcane.android.core.ArcaneClientManager
import app.getarcane.android.core.OperationStore
import app.getarcane.android.nav.AuthenticatedRouteCoordinator

class ArcaneApplication : Application() {
    lateinit var arcaneManager: ArcaneClientManager
        private set
    lateinit var operationStore: OperationStore
        private set
    lateinit var routeCoordinator: AuthenticatedRouteCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        arcaneManager = ArcaneClientManager(this)
        routeCoordinator = AuthenticatedRouteCoordinator()
        operationStore = OperationStore(this, arcaneManager)
        arcaneManager.attachOperationStore(operationStore)
    }
}
