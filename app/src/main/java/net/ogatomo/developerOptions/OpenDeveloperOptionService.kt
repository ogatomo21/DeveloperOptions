package net.ogatomo.developerOptions

import android.app.Service
import android.content.Intent
import android.os.IBinder

class OpenDeveloperOptionService : Service() {

    override fun onCreate() {
        super.onCreate()
        DeveloperOptionsNavigator.open(this, showDisabledToast = true)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
