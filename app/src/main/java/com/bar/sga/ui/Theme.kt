package com.bar.sga.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.bar.sga.SgApp
import com.bar.sga.R
import com.bar.sga.ThemeMode

@Composable
fun isDarkTheme(): Boolean = when ((LocalContext.current.applicationContext as SgApp).themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

// Explicit variation settings: without them the variable font is drawn at its default weight (300).
@OptIn(ExperimentalTextApi::class)
private fun rubikFont(w: FontWeight) = Font(R.font.rubik, w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))
private val Rubik = FontFamily(rubikFont(FontWeight.Normal), rubikFont(FontWeight.Medium), rubikFont(FontWeight.SemiBold), rubikFont(FontWeight.Bold))

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
