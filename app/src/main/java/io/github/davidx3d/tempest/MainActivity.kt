package io.github.davidx3d.tempest

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import io.github.davidx3d.tempest.ui.TempestRoot
import io.github.davidx3d.tempest.ui.theme.TempestTheme

/** AppCompatActivity so AppCompatDelegate can switch the app language on Android 12 as well. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TempestTheme {
                TempestRoot()
            }
        }
    }
}
