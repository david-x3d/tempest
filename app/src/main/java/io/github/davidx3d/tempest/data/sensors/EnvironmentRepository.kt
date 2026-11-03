package io.github.davidx3d.tempest.data.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

enum class EnvironmentKind(val sensorType: Int) {
    AmbientTemperature(Sensor.TYPE_AMBIENT_TEMPERATURE),
    Humidity(Sensor.TYPE_RELATIVE_HUMIDITY),
    Pressure(Sensor.TYPE_PRESSURE),
    Light(Sensor.TYPE_LIGHT),
}

data class EnvironmentReading(val kind: EnvironmentKind, val sensorName: String, val value: Float)

data class EnvironmentState(
    val readings: Map<EnvironmentKind, EnvironmentReading> = emptyMap(),
    val sensorCount: Int = 0,
)

/** Live values from the phone's environmental sensors, collected only while someone listens. */
class EnvironmentRepository(context: Context) {

    private val sensorManager = context.getSystemService(SensorManager::class.java)

    val sensorCount: Int = sensorManager.getSensorList(Sensor.TYPE_ALL).size

    fun readings(): Flow<EnvironmentState> = callbackFlow {
        val latest = HashMap<EnvironmentKind, EnvironmentReading>()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val kind = EnvironmentKind.entries.firstOrNull { it.sensorType == event.sensor.type } ?: return
                latest[kind] = EnvironmentReading(kind, event.sensor.name, event.values[0])
                trySend(EnvironmentState(latest.toMap(), sensorCount))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        EnvironmentKind.entries.forEach { kind ->
            sensorManager.getDefaultSensor(kind.sensorType)?.let {
                sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
        trySend(EnvironmentState(emptyMap(), sensorCount))
        awaitClose { sensorManager.unregisterListener(listener) }
    }.conflate()
}
