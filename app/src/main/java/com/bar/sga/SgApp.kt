package com.bar.sga

import android.app.Application
import android.app.UiModeManager
import android.os.Build
import androidx.compose.runtime.mutableStateOf
import com.bar.sga.data.GymDb

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class SgApp : Application() {
    val db: GymDb by lazy { GymDb.create(this) }

    // lazy: SharedPreferences cannot be opened before the Application is attached to its base context
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private val theme by lazy { mutableStateOf(ThemeMode.entries.find { it.name == prefs.getString("theme", null) } ?: ThemeMode.SYSTEM) }

    var themeMode: ThemeMode
        get() = theme.value
        set(m) {
            theme.value = m
            prefs.edit().putString("theme", m.name).apply()
            // also makes the system start-up window match the choice
            if (Build.VERSION.SDK_INT >= 31) getSystemService(UiModeManager::class.java).setApplicationNightMode(
                when (m) {
                    ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                    ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                    ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
                },
            )
        }
}
