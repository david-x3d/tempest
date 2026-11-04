package io.github.davidx3d.tempest.alerts

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.davidx3d.tempest.MainActivity
import io.github.davidx3d.tempest.R
import io.github.davidx3d.tempest.TempestApp
import io.github.davidx3d.tempest.ui.formatTemperature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Quick Settings tile that shows the live battery temperature and opens Tempest on tap. */
class TemperatureTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        val container = (application as TempestApp).container
        job = scope.launch {
            combine(container.battery.snapshot, container.settings.settings) { b, s -> b to s }.collect { (battery, settings) ->
                val tile = qsTile ?: return@collect
                val hot = battery != null && battery.temperatureCelsius >= settings.batteryThreshold
                tile.label = getString(R.string.tile_label)
                tile.subtitle = battery?.let { formatTemperature(this@TemperatureTileService, it.temperatureCelsius, settings.fahrenheit) }
                tile.icon = Icon.createWithResource(this@TemperatureTileService, R.drawable.ic_stat_thermometer)
                tile.state = if (hot) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                tile.updateTile()
            }
        }
    }

    override fun onStopListening() {
        job?.cancel()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            startLegacy(intent)
        }
    }

    /** Only reached below Android 14, where the Intent overload is still the way to go. */
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun startLegacy(intent: Intent) = startActivityAndCollapse(intent)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
