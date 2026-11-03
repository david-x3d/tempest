package io.github.davidx3d.tempest.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import io.github.davidx3d.tempest.R
import java.text.NumberFormat
import java.util.Locale

/** True when the user prefers °F. Temperatures are always stored and compared in °C. */
val LocalFahrenheit = staticCompositionLocalOf { false }

/** The locale the app's resources currently use (follows the in-app language choice). */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

fun formatNumber(value: Float, locale: Locale, fractionDigits: Int = 1): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
    }.format(value)

@Composable
@ReadOnlyComposable
fun formatUnit(@StringRes unit: Int, value: Float, fractionDigits: Int = 1): String =
    stringResource(unit, formatNumber(value, currentLocale(), fractionDigits))

fun toFahrenheit(celsius: Float) = celsius * 9f / 5f + 32f

/** "35,2 °C" in German, "35.2 °C" in English, "95.4 °F" when the user picked Fahrenheit. */
@Composable
@ReadOnlyComposable
fun celsius(value: Float, fractionDigits: Int = 1): String =
    if (LocalFahrenheit.current) {
        formatUnit(R.string.unit_fahrenheit, toFahrenheit(value), fractionDigits)
    } else {
        formatUnit(R.string.unit_celsius, value, fractionDigits)
    }

/** A temperature difference, e.g. a trend: no +32 offset for Fahrenheit. */
@Composable
@ReadOnlyComposable
fun temperatureDelta(value: Float): Float = if (LocalFahrenheit.current) value * 9f / 5f else value

/** Same as [celsius] for code outside Compose (notifications, tiles). */
fun formatTemperature(context: Context, celsius: Float, fahrenheit: Boolean, fractionDigits: Int = 1): String {
    val locale = context.resources.configuration.locales[0]
    return if (fahrenheit) {
        context.getString(R.string.unit_fahrenheit, formatNumber(toFahrenheit(celsius), locale, fractionDigits))
    } else {
        context.getString(R.string.unit_celsius, formatNumber(celsius, locale, fractionDigits))
    }
}

/** "2,84 GHz" from 1 GHz upwards, "800 MHz" below. */
@Composable
@ReadOnlyComposable
fun clock(khz: Long): String {
    val ghz = khz / 1_000_000f
    return if (ghz >= 1f) {
        stringResource(R.string.unit_ghz, formatNumber(ghz, currentLocale(), 2))
    } else {
        stringResource(R.string.unit_mhz, formatNumber(khz / 1000f, currentLocale(), 0))
    }
}

/** "7.45 GB", localized by the system formatter. */
@Composable
@ReadOnlyComposable
fun bytes(value: Long): String =
    android.text.format.Formatter.formatFileSize(androidx.compose.ui.platform.LocalContext.current, value)
