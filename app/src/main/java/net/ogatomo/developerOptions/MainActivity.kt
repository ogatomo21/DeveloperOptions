package net.ogatomo.developerOptions

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            openDeveloperOption()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 13 未満 → 権限関係ないのでそのまま実行
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            openDeveloperOption()
            return
        }

        // 既に通知許可あり → そのまま実行
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            openDeveloperOption()
            return
        }

        // 説明ダイアログ → 許可リクエスト
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.notification_need_title))
            .setMessage(getString(R.string.notification_need_message))
            .setPositiveButton(getString(R.string.allow)) { _, _ ->
                requestNotificationPermission.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
            .setNegativeButton(getString(R.string.maybe)) { _, _ ->
                openDeveloperOption()
            }
            .setCancelable(false)
            .show()
    }

    private fun openDeveloperOption() {
        val enabled = try {
            Settings.Global.getInt(
                contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            ) == 1
        } catch (e: Exception) {
            false
        }

        if (enabled) {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } else {
            val fallback = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(fallback)
        }

        finish()
    }
}
