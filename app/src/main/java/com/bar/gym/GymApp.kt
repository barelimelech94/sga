package com.bar.gym

import android.app.Application
import com.bar.gym.data.GymDb

class GymApp : Application() {
    val db: GymDb by lazy { GymDb.create(this) }
}
