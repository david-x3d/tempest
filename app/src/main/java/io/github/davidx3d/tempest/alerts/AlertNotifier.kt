package io.github.davidx3d.tempest.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.davidx3d.tempest.MainActivity
import io.github.davidx3d.tempest.R
import io.github.davidx3d.tempest.ui.formatTemperature

enum class AlertSource { Battery, Soc }

/** Owns the two notification channels and builds the ongoing and the big alert notifications. */
class AlertNotifier(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannels() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITOR, context.getString(R.string.channel_monitor), NotificationManager.IMPORTANCE_LOW)
                .apply {
                    description = context.getString(R.string.channel_monitor_desc)
                    setShowBadge(false)
                },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    description = context.getString(R.string.channel_alerts_desc)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 800)
                    enableLights(true)
                    lightColor = Color.RED
                },
        )
    }

    fun monitorNotification(battery: Float?, soc: Float?, fahrenheit: Boolean) =
        NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_thermometer)
            .setContentTitle(context.getString(R.string.notif_monitor_title))
            .setContentText(statusLine(battery, soc, fahrenheit))
            .setContentIntent(openApp())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    fun statusLine(battery: Float?, soc: Float?, fahrenheit: Boolean): String {
        val dash = context.getString(R.string.value_unavailable)
        val b = battery?.let { formatTemperature(context, it, fahrenheit) } ?: dash
        return if (soc != null) {
            context.getString(R.string.notif_monitor_text_soc, b, formatTemperature(context, soc, fahrenheit))
        } else {
            context.getString(R.string.notif_monitor_text, b)
        }
    }

    /**
     * A heads-up notification with a custom layout that puts the temperature in huge type, plus a
     * full-screen intent that opens [AlertActivity] over the lock screen when Android allows it.
     */
    fun showAlert(source: AlertSource, celsius: Float, threshold: Float, fahrenheit: Boolean) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val title = context.getString(
            if (source == AlertSource.Battery) R.string.alert_battery_title else R.string.alert_soc_title,
        )
        val value = formatTemperature(context, celsius, fahrenheit)
        val body = context.getString(R.string.alert_body, formatTemperature(context, threshold, fahrenheit, 0))

        fun views(layout: Int) = RemoteViews(context.packageName, layout).apply {
            setTextViewText(R.id.alert_title, title)
            setTextViewText(R.id.alert_value, value)
            setTextViewText(R.id.alert_body, body)
        }

        val fullScreen = PendingIntent.getActivity(
            context,
            source.ordinal,
            AlertActivity.intent(context, source, celsius, threshold),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_thermometer)
            .setColor(Color.rgb(0xE5, 0x39, 0x2B))
            .setColorized(true)
            .setContentTitle(title)
            .setContentText("$value · $body")
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(views(R.layout.notification_alert_small))
            .setCustomBigContentView(views(R.layout.notification_alert_big))
            .setCustomHeadsUpContentView(views(R.layout.notification_alert_big))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .setAutoCancel(true)
            .build()
        manager.notify(ALERT_ID_BASE + source.ordinal, notification)
    }

    fun cancelAlert(source: AlertSource) = manager.cancel(ALERT_ID_BASE + source.ordinal)

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CHANNEL_MONITOR = "monitor"
        const val CHANNEL_ALERTS = "alerts"
        const val MONITOR_ID = 1
        private const val ALERT_ID_BASE = 100
    }
}
