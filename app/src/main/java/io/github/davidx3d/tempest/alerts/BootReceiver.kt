package io.github.davidx3d.tempest.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.davidx3d.tempest.TempestApp

/** Resumes background alerts after a reboot or an app update, if the user turned them on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val settings = (context.applicationContext as TempestApp).container.settings.settings
        val pending = goAsync()
        Thread {
            // DataStore emits its stored value within a few milliseconds of process start.
            Thread.sleep(500)
            if (settings.value.alertsEnabled) runCatching { MonitorService.start(context) }
            pending.finish()
        }.start()
    }
}
