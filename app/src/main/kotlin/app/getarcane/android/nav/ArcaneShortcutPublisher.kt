package app.getarcane.android.nav

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.net.toUri
import app.getarcane.android.MainActivity
import app.getarcane.android.R
import app.getarcane.android.core.ReadCacheScope
import app.getarcane.android.core.sha256
import app.getarcane.sdk.models.environment.Environment

/** Static shortcuts live in XML; this publishes only reviewed, read-only environment destinations. */
class ArcaneShortcutPublisher(context: Context) {
    private val appContext = context.applicationContext

    fun publishEnvironments(scope: ReadCacheScope, environments: List<Environment>) {
        if (Build.VERSION.SDK_INT < 25) return
        val manager = appContext.getSystemService(ShortcutManager::class.java) ?: return
        val dynamicCapacity = (manager.maxShortcutCountPerActivity - STATIC_SHORTCUT_COUNT).coerceAtLeast(0)
        val eligible = environments.filter { it.enabled }.sortedBy { it.name ?: it.id }
            .take(minOf(MAXIMUM_DYNAMIC_ENVIRONMENTS, dynamicCapacity))
        val shortcuts = eligible.map { environment ->
            val label = environment.name?.takeIf(String::isNotBlank) ?: "Environment"
            val route = AuthenticatedRoute(
                serverBindingHash = scope.serverBindingHash,
                destination = RouteDestination.ENVIRONMENT,
                environmentId = environment.id,
            )
            ShortcutInfo.Builder(appContext, "environment-${sha256(environment.id).take(16)}")
                .setShortLabel(label.take(MAXIMUM_SHORT_LABEL))
                .setLongLabel("Open ${label.take(MAXIMUM_LONG_LABEL_SUFFIX)}")
                .setIcon(Icon.createWithResource(appContext, R.drawable.ic_shortcut_environment))
                .setIntent(
                    Intent(Intent.ACTION_VIEW, AuthenticatedRouteCodec.encode(route).toUri(), appContext, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
                .build()
        }
        manager.dynamicShortcuts = shortcuts
    }

    fun removeDynamicShortcuts() {
        if (Build.VERSION.SDK_INT < 25) return
        appContext.getSystemService(ShortcutManager::class.java)?.removeAllDynamicShortcuts()
    }

    companion object {
        const val MAXIMUM_DYNAMIC_ENVIRONMENTS = 3
        private const val STATIC_SHORTCUT_COUNT = 3
        private const val MAXIMUM_SHORT_LABEL = 10
        private const val MAXIMUM_LONG_LABEL_SUFFIX = 32
    }
}
