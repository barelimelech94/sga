package com.bar.gym

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import com.bar.gym.ui.GymNav
import com.bar.gym.ui.GymTheme
import com.bar.gym.ui.isDarkTheme

// the scrims enableEdgeToEdge() uses by default
private val lightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val darkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isDarkTheme()
            // the bar icons must follow the app's theme, which can differ from the phone's
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(lightScrim, darkScrim) { dark },
                )
                onDispose {}
            }
            GymTheme(dark) { GymNav() }
        }
    }
}
