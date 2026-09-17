package app.getarcane.android.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import app.getarcane.android.MainActivity
import app.getarcane.android.OperationActionReceiver
import app.getarcane.android.R
import app.getarcane.android.nav.AuthenticatedRoute
import app.getarcane.android.nav.AuthenticatedRouteCodec
import app.getarcane.android.nav.RouteDestination
import app.getarcane.sdk.models.user.hasPermission

internal class OperationNotificationProjector(
    private val context: Context,
    private val manager: ArcaneClientManager,
) {
    private val systemNotificationManager = context.getSystemService(NotificationManager::class.java)
    private val notificationManager = NotificationManagerCompat.from(context)
    private var postedIds = runCatching {
        systemNotificationManager.activeNotifications
            .filter { it.notification.group == GROUP_KEY }
            .mapTo(mutableSetOf()) { it.id }
            .also { it.remove(SUMMARY_ID) }
    }.getOrDefault(emptySet())

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.operation_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.operation_notification_channel_description)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            }
            systemNotificationManager.createNotificationChannel(channel)
        }
    }

    fun project(operations: List<OperationRecord>) {
        try {
            if (!notificationsAllowed()) {
                postedIds.forEach(notificationManager::cancel)
                notificationManager.cancel(SUMMARY_ID)
                postedIds = emptySet()
                return
            }
            val visible = operations.filter { it.state != OperationState.CLEARED }
            val ids = stableOperationNotificationIds(visible.map(OperationRecord::operationId))
            visible.forEach { operation ->
                val id = ids.getValue(operation.operationId)
                notificationManager.notify(id, notification(operation, id))
            }
            val nextIds = ids.values.toSet()
            (postedIds - nextIds).forEach(notificationManager::cancel)
            if (visible.size > 1) {
                val active = visible.count(OperationRecord::isActive)
                val summaryText = if (active > 0) {
                    context.resources.getQuantityString(R.plurals.operation_notification_active_summary, active, active)
                } else {
                    context.resources.getQuantityString(
                        R.plurals.operation_notification_recent_summary,
                        visible.size,
                        visible.size,
                    )
                }
                val groupTitle = context.getString(R.string.operation_notification_group_title)
                val summary = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_operation_notification)
                    .setContentTitle(groupTitle)
                    .setContentText(summaryText)
                    .setContentIntent(centerIntent())
                    .setGroup(GROUP_KEY)
                    .setGroupSummary(true)
                    .setOnlyAlertOnce(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(publicNotification(groupTitle))
                    .build()
                notificationManager.notify(SUMMARY_ID, summary)
            } else {
                notificationManager.cancel(SUMMARY_ID)
            }
            postedIds = nextIds
        } catch (_: SecurityException) {
            // Permission can be revoked between the check and NotificationManager call.
            postedIds = emptySet()
        }
    }

    private fun notification(operation: OperationRecord, notificationId: Int): android.app.Notification {
        val stateText = operation.notificationStateText(context)
        val text = operation.environmentName.takeIf(String::isNotBlank)?.let {
            context.getString(R.string.operation_notification_detail, stateText, it.take(80))
        } ?: stateText
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_operation_notification)
            .setContentTitle(context.getString(operation.kind.notificationTitleRes()))
            .setContentText(text)
            .setContentIntent(operationIntent(operation.operationId, notificationId))
            .setGroup(GROUP_KEY)
            .setOnlyAlertOnce(operation.isActive)
            .setAutoCancel(operation.isTerminalLike)
            .setOngoing(false)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                publicNotification(
                    context.getString(
                        if (operation.isActive) {
                            R.string.operation_notification_public_active
                        } else {
                            R.string.operation_notification_public_finished
                        },
                    ),
                ),
            )
        operation.progressPercent?.takeIf { operation.isActive }?.let { builder.setProgress(100, it, false) }
            ?: if (operation.isActive) builder.setProgress(0, 0, true) else Unit
        if (operation.isActive && operation.serverActivityId != null &&
            (manager.currentUser?.hasPermission("activities:cancel", operation.environmentId) ?: false)
        ) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.cancel_operation),
                cancelIntent(operation.operationId, notificationId),
            )
        }
        return builder.build()
    }

    private fun publicNotification(text: String): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_operation_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .build()

    private fun operationIntent(operationId: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            data = AuthenticatedRouteCodec.encode(
                AuthenticatedRoute(
                    serverBindingHash = sha256(manager.serverSessionIdentity),
                    destination = RouteDestination.OPERATION,
                    resourceId = operationId,
                ),
            ).toUri()
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun centerIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            data = AuthenticatedRouteCodec.encode(
                AuthenticatedRoute(
                    serverBindingHash = null,
                    destination = RouteDestination.OPERATIONS,
                ),
            ).toUri()
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            SUMMARY_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelIntent(operationId: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, OperationActionReceiver::class.java).apply {
            action = OperationActionReceiver.ACTION_CANCEL
            data = "arcane-mobile://operations/$operationId/cancel".toUri()
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode xor CANCEL_REQUEST_MASK,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notificationsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return false
        return notificationManager.areNotificationsEnabled()
    }

    private companion object {
        const val CHANNEL_ID = "arcane_operations"
        const val GROUP_KEY = "app.getarcane.android.OPERATIONS"
        const val SUMMARY_ID = OPERATION_NOTIFICATION_SUMMARY_ID
        const val CANCEL_REQUEST_MASK = 0x40000000
    }
}

@StringRes
internal fun OperationKind.notificationTitleRes(): Int = when (this) {
    OperationKind.PROJECT_DEPLOY -> R.string.operation_notification_project_deploy
    OperationKind.PROJECT_REDEPLOY -> R.string.operation_notification_project_redeploy
    OperationKind.PROJECT_PULL -> R.string.operation_notification_project_pull
    OperationKind.PROJECT_BUILD -> R.string.operation_notification_project_build
    OperationKind.IMAGE_PULL -> R.string.operation_notification_image_pull
    OperationKind.CONTAINER_REDEPLOY -> R.string.operation_notification_container_redeploy
    OperationKind.UPDATER_RUN -> R.string.operation_notification_updater
    OperationKind.FLEET_UPDATE -> R.string.operation_notification_fleet_update
    OperationKind.UNKNOWN -> R.string.operation_notification_unknown
}

internal fun OperationRecord.notificationStateText(context: Context): String = when (state) {
    OperationState.QUEUED -> context.getString(R.string.operation_state_queued)
    OperationState.STARTING -> context.getString(R.string.operation_state_starting)
    OperationState.RUNNING -> progressPercent?.let {
        context.getString(R.string.operation_notification_progress, it)
    } ?: context.getString(R.string.operation_state_running)
    OperationState.RECONNECTING -> context.getString(R.string.operation_state_reconnecting)
    OperationState.CANCEL_REQUESTED -> context.getString(R.string.operation_state_cancel_requested)
    OperationState.SUCCESS -> context.getString(R.string.operation_state_completed)
    OperationState.FAILURE -> if (presentationCode == OperationPresentationCode.COMPLETED_WITH_ISSUES) {
        context.getString(R.string.operation_state_completed_with_issues)
    } else {
        context.getString(R.string.operation_state_failed)
    }
    OperationState.CANCELLED -> context.getString(R.string.operation_state_cancelled)
    OperationState.INTERRUPTED -> context.getString(R.string.operation_state_interrupted)
    OperationState.UNKNOWN -> context.getString(R.string.operation_state_unknown)
    OperationState.CLEARED -> context.getString(R.string.operation_state_finished)
}

internal const val OPERATION_NOTIFICATION_SUMMARY_ID = 2_041_145_001

internal fun stableOperationNotificationIds(operationIds: List<String>): Map<String, Int> {
    val used = mutableSetOf(OPERATION_NOTIFICATION_SUMMARY_ID)
    return operationIds.distinct().sorted().associateWith { operationId ->
        var candidate = (operationId.hashCode() and Int.MAX_VALUE).coerceAtLeast(1)
        while (!used.add(candidate)) candidate = if (candidate == Int.MAX_VALUE) 1 else candidate + 1
        candidate
    }
}
