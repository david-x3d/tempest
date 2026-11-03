package io.github.davidx3d.tempest.data.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

enum class SensorGroup { Motion, Position, Environment, Other }

/** One hardware or virtual sensor with its datasheet values. */
data class SensorSpec(
    val id: Int,
    val name: String,
    val vendor: String,
    val type: Int,
    val stringType: String,
    val group: SensorGroup,
    val wakeUp: Boolean,
    val maxRange: Float,
    val resolution: Float,
    val powerMa: Float,
    /** Fastest rate in Hz, null for sensors that only report on change. */
    val maxRateHz: Float?,
    val version: Int,
)

/**
 * Every sensor the phone exposes, plus live values. Values are sampled at the UI rate and pushed
 * out at most every [EMIT_MS] so a phone with 60 sensors does not flood the UI.
 */
class SensorsRepository(context: Context) {

    private val manager = context.getSystemService(SensorManager::class.java)

    val sensors: List<SensorSpec> = manager.getSensorList(Sensor.TYPE_ALL)
        .map { it.toSpec() }
        .sortedWith(compareBy({ it.group.ordinal }, { it.type }, { it.name }))

    fun values(): Flow<Map<Int, FloatArray>> = callbackFlow {
        val latest = ConcurrentHashMap<Int, FloatArray>()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                latest[event.sensor.id()] = event.values.copyOf()
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        manager.getSensorList(Sensor.TYPE_ALL)
            // One-shot sensors (significant motion) cannot be listened to like this.
            .filter { it.reportingMode != Sensor.REPORTING_MODE_ONE_SHOT }
            .forEach { runCatching { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) } }
        val pump = flow {
            while (true) {
                emit(HashMap(latest))
                delay(EMIT_MS)
            }
        }
        val job = launch { pump.collect { trySend(it) } }
        awaitClose {
            job.cancel()
            manager.unregisterListener(listener)
        }
    }.conflate()

    private fun Sensor.toSpec() = SensorSpec(
        id = id(),
        name = name,
        vendor = vendor,
        type = type,
        stringType = stringType.orEmpty(),
        group = groupOf(type),
        wakeUp = isWakeUpSensor,
        maxRange = maximumRange,
        resolution = resolution,
        powerMa = power,
        maxRateHz = minDelay.takeIf { it > 0 }?.let { 1_000_000f / it },
        version = version,
    )

    /** Sensor.getId() is 0 or -1 on many phones, so identity is built from what makes it unique. */
    private fun Sensor.id(): Int = (name + vendor + type + isWakeUpSensor).hashCode()

    private fun groupOf(type: Int): SensorGroup = when (type) {
        Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_ACCELEROMETER_UNCALIBRATED, Sensor.TYPE_GYROSCOPE,
        Sensor.TYPE_GYROSCOPE_UNCALIBRATED, Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_GRAVITY,
        Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_STEP_COUNTER, Sensor.TYPE_STEP_DETECTOR,
        Sensor.TYPE_SIGNIFICANT_MOTION, Sensor.TYPE_MOTION_DETECT, Sensor.TYPE_STATIONARY_DETECT,
        Sensor.TYPE_HEAD_TRACKER, Sensor.TYPE_ACCELEROMETER_LIMITED_AXES, Sensor.TYPE_GYROSCOPE_LIMITED_AXES,
        -> SensorGroup.Motion

        Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED, Sensor.TYPE_GAME_ROTATION_VECTOR,
        Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, Sensor.TYPE_PROXIMITY, Sensor.TYPE_POSE_6DOF,
        Sensor.TYPE_HINGE_ANGLE, Sensor.TYPE_HEADING,
        -> SensorGroup.Position

        Sensor.TYPE_AMBIENT_TEMPERATURE, Sensor.TYPE_LIGHT, Sensor.TYPE_PRESSURE, Sensor.TYPE_RELATIVE_HUMIDITY,
        -> SensorGroup.Environment

        else -> SensorGroup.Other
    }

    private companion object {
        const val EMIT_MS = 250L
    }
}
