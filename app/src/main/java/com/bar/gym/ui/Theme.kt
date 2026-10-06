package com.bar.gym.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.bar.gym.GymApp
import com.bar.gym.R
import com.bar.gym.ThemeMode

@Composable
fun isDarkTheme(): Boolean = when ((LocalContext.current.applicationContext as GymApp).themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

// One variable font file; Compose applies the weight axis from each FontWeight.
private val Rubik = FontFamily(
    Font(R.font.rubik, FontWeight.Normal),
    Font(R.font.rubik, FontWeight.Medium),
    Font(R.font.rubik, FontWeight.SemiBold),
    Font(R.font.rubik, FontWeight.Bold),
)

// "tnum": fixed-width digits, so the countdown does not jitter
private fun TextStyle.rubik(w: FontWeight) = copy(fontFamily = Rubik, fontWeight = w, fontFeatureSettings = "tnum")

private val typography = Typography().run {
    copy(
        displayLarge = displayLarge.rubik(FontWeight.Bold),
        displayMedium = displayMedium.rubik(FontWeight.Bold),
        displaySmall = displaySmall.rubik(FontWeight.Bold),
        headlineLarge = headlineLarge.rubik(FontWeight.Bold),
        headlineMedium = headlineMedium.rubik(FontWeight.Bold),
        headlineSmall = headlineSmall.rubik(FontWeight.Bold),
        titleLarge = titleLarge.rubik(FontWeight.SemiBold),
        titleMedium = titleMedium.rubik(FontWeight.SemiBold),
        titleSmall = titleSmall.rubik(FontWeight.SemiBold),
        bodyLarge = bodyLarge.rubik(FontWeight.Normal),
        bodyMedium = bodyMedium.rubik(FontWeight.Normal),
        bodySmall = bodySmall.rubik(FontWeight.Normal),
        labelLarge = labelLarge.rubik(FontWeight.Medium),
        labelMedium = labelMedium.rubik(FontWeight.Medium),
        labelSmall = labelSmall.rubik(FontWeight.Medium),
    )
}

@Composable
fun GymTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme(), typography = typography, content = content)
}
