package app.getarcane.android.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.getarcane.android.BuildConfig
import app.getarcane.android.MainActivity
import app.getarcane.android.core.StatusSnapshotStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class FleetStatusWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(120.dp, 100.dp),
            DpSize(240.dp, 120.dp),
            DpSize(300.dp, 200.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val read = StatusSnapshotStore(
            directory = File(context.noBackupFilesDir, StatusSnapshotStore.SNAPSHOT_DIRECTORY),
            sourceVersion = BuildConfig.VERSION_CODE,
        ).loadForExternalConsumer()
        val model = FleetWidgetModel.from(read, System.currentTimeMillis())
        provideContent { FleetStatusContent(context, model) }
    }
}

class FleetStatusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FleetStatusWidget()
}

/** Requests a local re-render after the app publishes a materially changed snapshot. */
internal object FleetStatusWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private var updateJob: Job? = null

    fun requestUpdate(context: Context) {
        val appContext = context.applicationContext
        synchronized(lock) {
            updateJob?.cancel()
            updateJob = scope.launch {
                val widget = FleetStatusWidget()
                widget.updateAll(appContext)
                // Glance may still have a short-lived composition holding the prior file model.
                // Reconcile once after it settles so sign-out/server switches never remain stale.
                delay(SESSION_RECONCILIATION_DELAY_MILLIS)
                widget.updateAll(appContext)
            }
        }
    }

    private const val SESSION_RECONCILIATION_DELAY_MILLIS = 12_000L
}

@OptIn(ExperimentalGlanceApi::class)
@Composable
private fun FleetStatusContent(context: Context, model: FleetWidgetModel) {
    val size = LocalSize.current
    val action = model.routeUri?.let { uri ->
        actionStartActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(uri), context, MainActivity::class.java),
        )
    }
    val modifier = GlanceModifier
        .fillMaxSize()
        .background(ColorProvider(Color(0xFF17202B)))
        .padding(14.dp)
        .let { base -> if (action == null) base else base.clickable(action) }

    Column(modifier) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Arcane fleet",
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = stateMarker(model.state),
                style = TextStyle(
                    color = stateColor(model.state),
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        Spacer(GlanceModifier.height(8.dp))

        if (model.state in setOf(FleetWidgetState.SIGNED_OUT, FleetWidgetState.UNCONFIGURED) ||
            (model.state == FleetWidgetState.UNAVAILABLE && model.totalContainers == 0)
        ) {
            Spacer(GlanceModifier.defaultWeight())
            Text(
                model.statusLabel,
                style = TextStyle(color = ColorProvider(Color.LightGray)),
            )
            Spacer(GlanceModifier.defaultWeight())
        } else {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        text = "${model.runningContainers}/${model.totalContainers}",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF86EFAC)),
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        text = "containers running",
                        style = TextStyle(color = ColorProvider(Color.LightGray)),
                    )
                }
                if (size.width >= 220.dp) {
                    Spacer(GlanceModifier.defaultWeight())
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${model.onlineEnvironments} online", style = secondaryText())
                        Text("${model.updates} updates", style = secondaryText())
                        Text("${model.images} images", style = secondaryText())
                    }
                }
            }
            if (size.height >= 180.dp && model.environments.isNotEmpty()) {
                Spacer(GlanceModifier.height(10.dp))
                model.environments.take(3).forEach { environment ->
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(
                            text = if (environment.online) "●" else "○",
                            style = TextStyle(
                                color = if (environment.online) {
                                    ColorProvider(Color(0xFF86EFAC))
                                } else {
                                    ColorProvider(Color.LightGray)
                                },
                            ),
                        )
                        Spacer(GlanceModifier.width(6.dp))
                        Text(environment.displayName, style = secondaryText(), maxLines = 1)
                        Spacer(GlanceModifier.defaultWeight())
                        Text(
                            "${environment.runningContainers}/${environment.totalContainers}",
                            style = secondaryText(),
                        )
                    }
                }
            }
        }

        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = listOfNotNull(model.statusLabel, model.freshnessLabel).joinToString(" · "),
            style = TextStyle(color = ColorProvider(Color.LightGray)),
            maxLines = 1,
        )
    }
}

private fun secondaryText(): TextStyle =
    TextStyle(color = ColorProvider(Color.LightGray))

private fun stateMarker(state: FleetWidgetState): String = when (state) {
    FleetWidgetState.FRESH -> "●"
    FleetWidgetState.STALE -> "STALE"
    FleetWidgetState.UNAVAILABLE -> "OFFLINE"
    FleetWidgetState.SIGNED_OUT -> "SIGNED OUT"
    FleetWidgetState.UNCONFIGURED -> "SET UP"
}

private fun stateColor(state: FleetWidgetState): ColorProvider = when (state) {
    FleetWidgetState.FRESH -> ColorProvider(Color(0xFF86EFAC))
    FleetWidgetState.STALE -> ColorProvider(Color(0xFFFCD34D))
    FleetWidgetState.UNAVAILABLE -> ColorProvider(Color(0xFFFCA5A5))
    FleetWidgetState.SIGNED_OUT,
    FleetWidgetState.UNCONFIGURED,
    -> ColorProvider(Color.LightGray)
}
