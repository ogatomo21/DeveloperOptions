package net.ogatomo.developerOptions

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

/**
 * 開発者向けオプション / 端末情報を開く。
 *
 * Settings が既にタスクに残っていると [Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS]
 * だけでは画面遷移しないことがあるため、タスクをクリアしてルートから起動する。
 */
object DeveloperOptionsNavigator {

    /** 既存 Settings タスクを捨てて新規ルートとして開く */
    private const val LAUNCH_FLAGS =
        Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP

    fun isDeveloperOptionsEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            ) == 1
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 開発者向けオプションが有効ならその画面へ、無効なら端末情報へ。
     * @param showDisabledToast 無効時に Toast を出すか（Service 向け）
     */
    fun open(context: Context, showDisabledToast: Boolean = false) {
        if (isDeveloperOptionsEnabled(context)) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                    addFlags(LAUNCH_FLAGS)
                }
            )
        } else {
            if (showDisabledToast) {
                Toast.makeText(
                    context.applicationContext,
                    context.getString(R.string.disabled_message),
                    Toast.LENGTH_SHORT
                ).show()
            }
            context.startActivity(
                Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).apply {
                    addFlags(LAUNCH_FLAGS)
                }
            )
        }
    }
}
