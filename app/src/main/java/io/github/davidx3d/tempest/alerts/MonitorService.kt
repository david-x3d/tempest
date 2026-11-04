package io.github.davidx3d.tempest.alerts

import io.github.davidx3d.tempest.data.settings.effectiveSocThreshold
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.davidx3d.tempest.TempestApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Keeps Tempest's process alive while background alerts are on: the repositories keep sampling,
 * the history keeps growing, and crossing a threshold posts a big alert notification.
 */
class MonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val watcher = AlertWatcher()
    private lateinit var notifier: AlertNotifier

    override fun onCreate() {
        super.onCreate()
        notifier = AlertNotifier(this).also { it.ensureChannels() }
        ServiceCompat.startForeground(
            this,
            AlertNotifier.MONITOR_ID,
            notifier.monitorNotification(null, null, false),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )

        val container = (application as TempestApp).container
        scope.launch {
            combine(container.battery.snapshot, container.thermal.reading, container.settings.settings) { battery, thermal, settings ->
                Triple(battery?.temperatureCelsius, thermal.socZone?.celsius, settings)
            }.collect { (battery, soc, settings) ->
                if (!settings.alertsEnabled) {
                    stopSelf()
                    return@collect
                }
                notifier.monitorNotification(battery, soc, settings.fahrenheit).let {
                    getSystemService(android.app.NotificationManager::class.java).notify(AlertNotifier.MONITOR_ID, it)
                }
                val repeat = settings.alertRepeatMinutes * 60_000L
                val now = SystemClock.elapsedRealtime()
                if (watcher.check(AlertSource.Battery, battery, settings.batteryThreshold, repeat, now)) {
                    notifier.showAlert(AlertSource.Battery, battery!!, settings.batteryThreshold, settings.fahrenheit)
                }
                if (watcher.check(AlertSource.Soc, soc, settings.effectiveSocThreshold, repeat, now)) {
                    notifier.showAlert(AlertSource.Soc, soc!!, settings.effectiveSocThreshold, settings.fahrenheit)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))

        fun stop(context: Context) = context.stopService(Intent(context, MonitorService::class.java))
    }
}
