package io.github.davidx3d.tempest.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import io.github.davidx3d.tempest.R
import java.text.NumberFormat
import java.util.Locale

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

/** "35,2 °C" in German, "35.2 °C" in English. */
@Composable
@ReadOnlyComposable
fun celsius(value: Float, fractionDigits: Int = 1): String = formatUnit(R.string.unit_celsius, value, fractionDigits)

