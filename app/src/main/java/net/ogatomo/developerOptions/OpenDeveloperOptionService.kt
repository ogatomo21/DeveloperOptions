package net.ogatomo.developerOptions

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast

class OpenDeveloperOptionService : Service() {

    override fun onCreate() {
        super.onCreate()

        if (isdeveloperOptionsEnabled()) {
            // ON のときだけ開発者向け設定へ
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
        } else {
            Toast.makeText(
                applicationContext,
                getString(R.string.disabled_message),
                Toast.LENGTH_SHORT
            ).show()

            // OFF なら必ず端末情報へ
            val fallback = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            startActivity(fallback)

        }

        stopSelf()
    }

    private fun isdeveloperOptionsEnabled(): Boolean {
        return try {
            Settings.Global.getInt(
                contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
            ) == 1
        } catch (e: Exception) {
            false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
