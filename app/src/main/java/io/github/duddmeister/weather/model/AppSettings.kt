package io.github.duddmeister.weather.model

data class AppSettings(
    val units: UnitPrefs = UnitPrefs(),
    val timeFormat: TimeFormat = TimeFormat.System,
    val aqiScale: AqiScale = AqiScale.Auto,
    /** Background refresh interval; 0 disables periodic refresh. */
    val refreshIntervalMinutes: Int = 15,
    val useCurrentLocation: Boolean = true,
    val backgroundLocation: Boolean = false,
    val precipitationAlerts: Boolean = true,
    val severeAlerts: Boolean = true,
    val updateNotifications: Boolean = true,
    val animatedBackground: Boolean = true,
    val onboardingDone: Boolean = false,
) {
    companion object {
        val REFRESH_INTERVALS = listOf(15, 30, 60, 0)
    }
}
