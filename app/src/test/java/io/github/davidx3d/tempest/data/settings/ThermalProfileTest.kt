package io.github.davidx3d.tempest.data.settings

import org.junit.Assert.*
import org.junit.Test

class ThermalProfileTest {
    @Test fun activeUseTreats45AsBelowWarmWithoutRaisingBatteryLimit() {
        val settings = Settings(thermalProfile = ThermalProfile.Active)
        assertTrue(45f < settings.socLevels.warmFrom)
        assertEquals(40f, settings.batteryThreshold, 0f)
        assertEquals(65f, settings.effectiveSocThreshold, 0f)
    }
    @Test fun calibrationAffectsOnlyLoadProfiles() {
        val base = Settings(socNormalUnderLoad = 50f)
        assertEquals(60f, base.effectiveSocThreshold, 0f)
        assertEquals(70f, base.copy(thermalProfile = ThermalProfile.Active).effectiveSocThreshold, 0f)
        assertEquals(75f, base.copy(thermalProfile = ThermalProfile.Gaming).effectiveSocThreshold, 0f)
    }
    @Test fun customAlertAndColoursAgreeAndIgnoreBatteryColours() {
        val s = Settings(thermalProfile = ThermalProfile.Custom, socThreshold = 57f)
        assertEquals(57f, s.effectiveSocThreshold, 0f)
        assertEquals(s.effectiveSocThreshold, s.socLevels.hotFrom, 0f)
        assertEquals(40f, s.levels.hotFrom, 0f)
    }
}
