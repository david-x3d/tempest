package io.github.davidx3d.tempest

import android.content.Context
import io.github.davidx3d.tempest.data.battery.BatteryRepository
import io.github.davidx3d.tempest.data.history.HistoryRepository
import io.github.davidx3d.tempest.data.device.DeviceRepository
import io.github.davidx3d.tempest.data.privilege.DeepRepository
import io.github.davidx3d.tempest.data.privilege.PrivilegeManager
import io.github.davidx3d.tempest.data.sensors.SensorsRepository
import io.github.davidx3d.tempest.data.settings.SettingsRepository
import io.github.davidx3d.tempest.data.thermal.ThermalRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map

/** Hand-wired dependencies, one instance per process. */
class AppContainer(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(context, scope)
    val battery = BatteryRepository(context, scope)
    val sensors = SensorsRepository(context)
    val privilege = PrivilegeManager(settings.settings, scope)
    val deep = DeepRepository(privilege, scope)
    val device = DeviceRepository(context, privilege, settings.settings.map { it.onlineLookup }, scope)
    val thermal = ThermalRepository(context, privilege, scope)
    val history = HistoryRepository(
        scope = scope,
        batteryCelsius = { battery.snapshot.value?.temperatureCelsius },
        socCelsius = { thermal.reading.value.socZone?.celsius },
    )
}
