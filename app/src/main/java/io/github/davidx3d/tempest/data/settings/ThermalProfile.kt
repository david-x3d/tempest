package io.github.davidx3d.tempest.data.settings

/** User tuning presets, not manufacturer-certified temperature limits. Battery limits stay separate. */
enum class ThermalProfile { Balanced, Active, Gaming, Custom }

val Settings.socLevels: HeatLevels get() = when (thermalProfile) {
    ThermalProfile.Balanced -> HeatLevels(25f, 45f, 60f, 75f)
    ThermalProfile.Active -> HeatLevels(25f, socNormalUnderLoad + 5f, socNormalUnderLoad + 20f, socNormalUnderLoad + 30f)
    ThermalProfile.Gaming -> HeatLevels(25f, socNormalUnderLoad + 10f, socNormalUnderLoad + 25f, socNormalUnderLoad + 35f)
    ThermalProfile.Custom -> HeatLevels(20f, socThreshold - 10f, socThreshold, socThreshold + 10f)
}
val Settings.effectiveSocThreshold: Float get() =
    if (thermalProfile == ThermalProfile.Custom) socThreshold else socLevels.hotFrom
