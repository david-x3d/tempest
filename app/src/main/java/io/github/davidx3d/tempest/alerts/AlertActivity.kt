package io.github.davidx3d.tempest.alerts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.davidx3d.tempest.MainActivity
import io.github.davidx3d.tempest.R
import io.github.davidx3d.tempest.TempestApp
import io.github.davidx3d.tempest.ui.formatTemperature
import io.github.davidx3d.tempest.ui.theme.TempestTheme
import io.github.davidx3d.tempest.ui.theme.flexFontFamily

/** The "really big" alert: a full-screen, pulsing temperature that also shows over the lock screen. */
class AlertActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val source = AlertSource.entries[intent.getIntExtra(EXTRA_SOURCE, 0)]
        val celsius = intent.getFloatExtra(EXTRA_CELSIUS, 0f)
        val threshold = intent.getFloatExtra(EXTRA_THRESHOLD, 0f)
        val fahrenheit = (application as TempestApp).container.settings.settings.value.fahrenheit
        val value = formatTemperature(this, celsius, fahrenheit)
        val limit = formatTemperature(this, threshold, fahrenheit, 0)

        setContent {
            TempestTheme(darkTheme = true) {
                val haptics = LocalHapticFeedback.current
                LaunchedEffect(Unit) { haptics.performHapticFeedback(HapticFeedbackType.Reject) }
                val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
                    initialValue = 0.94f,
                    targetValue = 1.06f,
                    animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                    label = "scale",
                )
                val spin by rememberInfiniteTransition(label = "spin").animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(9_000)),
                    label = "rotation",
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color(0xFFB3261E), Color(0xFF5C0A1A)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(360.dp)
                            .graphicsLayer {
                                rotationZ = spin
                                scaleX = pulse
                                scaleY = pulse
                            }
                            .background(Color.White.copy(alpha = 0.12f), MaterialShapes.Burst.toShape()),
                    )
                    Column(
                        Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            stringResource(if (source == AlertSource.Battery) R.string.alert_battery_title else R.string.alert_soc_title),
                            color = Color.White,
                            style = TextStyle(fontFamily = flexFontFamily(800, 120f), fontSize = 34.sp),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            value,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.graphicsLayer {
                                scaleX = pulse
                                scaleY = pulse
                            },
                            style = TextStyle(fontFamily = flexFontFamily(1000, 125f), fontSize = 96.sp, lineHeight = 100.sp),
                        )
                        Text(
                            stringResource(R.string.alert_body, limit),
                            color = Color.White.copy(alpha = 0.85f),
                            style = TextStyle(fontFamily = flexFontFamily(500), fontSize = 20.sp),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(48.dp))
                        Button(
                            onClick = { finish() },
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFB3261E)),
                        ) { Text(stringResource(R.string.alert_dismiss), fontSize = 20.sp) }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                startActivity(Intent(this@AlertActivity, MainActivity::class.java))
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        ) { Text(stringResource(R.string.alert_open)) }
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_SOURCE = "source"
        private const val EXTRA_CELSIUS = "celsius"
        private const val EXTRA_THRESHOLD = "threshold"

        fun intent(context: Context, source: AlertSource, celsius: Float, threshold: Float) =
            Intent(context, AlertActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra(EXTRA_SOURCE, source.ordinal)
                .putExtra(EXTRA_CELSIUS, celsius)
                .putExtra(EXTRA_THRESHOLD, threshold)
    }
}
