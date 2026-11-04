package io.github.davidx3d.tempest.data.sensors

import android.hardware.Sensor
import android.hardware.SensorManager
import kotlin.math.asin
import kotlin.math.sqrt

/** What the live instruments on top of the sensors tab show. Null where the phone lacks the sensor. */
data class Instruments(
    /** Degrees from magnetic north, 0..360. */
    val azimuth: Float? = null,
    /** Tilt to the right and towards you in degrees, -90..90. */
    val tilt: Pair<Float, Float>? = null,
    val lux: Float? = null,
    val pressureHpa: Float? = null,
    /** True when something covers the proximity sensor. */
    val near: Boolean? = null,
    /** Acceleration without gravity, in g. */
    val gForce: Float? = null,
)

/** Which instruments this phone can show at all. */
data class InstrumentAvailability(
    val compass: Boolean = false,
    val level: Boolean = false,
    val light: Boolean = false,
    val pressure: Boolean = false,
    val proximity: Boolean = false,
    val motion: Boolean = false,
)

/** The sensors behind each instrument, picked once per phone. */
class InstrumentSensors(repository: SensorsRepository) {
    val rotation = repository.default(Sensor.TYPE_ROTATION_VECTOR)
    val magnetic = repository.default(Sensor.TYPE_MAGNETIC_FIELD)
    val gravity = repository.default(Sensor.TYPE_GRAVITY) ?: repository.default(Sensor.TYPE_ACCELEROMETER)
    val light = repository.default(Sensor.TYPE_LIGHT)
    val pressure = repository.default(Sensor.TYPE_PRESSURE)
    val proximity = repository.default(Sensor.TYPE_PROXIMITY)
    val linear = repository.default(Sensor.TYPE_LINEAR_ACCELERATION)

    /** Compass needs either the fused rotation vector or gravity plus the magnetometer. */
    private val compass = rotation?.let { listOf(it) } ?: listOfNotNull(magnetic, gravity).takeIf { it.size == 2 }.orEmpty()

    val available = InstrumentAvailability(
        compass = compass.isNotEmpty(),
        level = gravity != null,
        light = light != null,
        pressure = pressure != null,
        proximity = proximity != null,
        motion = linear != null,
    )

    val ids: Set<Int> = (compass + listOfNotNull(gravity, light, pressure, proximity, linear)).map { it.id }.toSet()

    private val matrix = FloatArray(9)
    private val angles = FloatArray(3)

    fun read(values: Map<Int, FloatArray>): Instruments {
        val g = gravity?.let { values[it.id] }
        return Instruments(
            azimuth = azimuth(values, g),
            tilt = g?.takeIf { it.size >= 3 }?.let { v ->
                val norm = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).takeIf { it > 1f } ?: return@let null
                Math.toDegrees(asin((v[0] / norm).coerceIn(-1f, 1f)).toDouble()).toFloat() to
                    Math.toDegrees(asin((v[1] / norm).coerceIn(-1f, 1f)).toDouble()).toFloat()
            },
            lux = light?.let { values[it.id]?.firstOrNull() },
            pressureHpa = pressure?.let { values[it.id]?.firstOrNull() },
            near = proximity?.let { p -> values[p.id]?.firstOrNull()?.let { it < p.maxRange } },
            gForce = linear?.let { values[it.id] }?.takeIf { it.size >= 3 }
                ?.let { sqrt(it[0] * it[0] + it[1] * it[1] + it[2] * it[2]) / SensorManager.GRAVITY_EARTH },
        )
    }

    private fun azimuth(values: Map<Int, FloatArray>, gravityValues: FloatArray?): Float? {
        val rotationValues = rotation?.let { values[it.id] }
        val ok = when {
            rotationValues != null -> {
                SensorManager.getRotationMatrixFromVector(matrix, rotationValues)
                true
            }
            gravityValues != null -> {
                val mag = magnetic?.let { values[it.id] } ?: return null
                SensorManager.getRotationMatrix(matrix, null, gravityValues, mag)
            }
            else -> false
        }
        if (!ok) return null
        SensorManager.getOrientation(matrix, angles)
        return (Math.toDegrees(angles[0].toDouble()).toFloat() + 360f) % 360f
    }
}
