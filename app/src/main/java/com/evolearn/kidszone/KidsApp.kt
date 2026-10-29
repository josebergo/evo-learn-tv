package com.evolearn.kidszone

import android.app.Application
import com.evolearn.kidszone.core.Prefs

class KidsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
    }
}
