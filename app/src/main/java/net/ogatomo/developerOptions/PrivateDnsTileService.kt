package net.ogatomo.developerOptions

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * クイック設定タイルからプライベート DNS を ON/OFF する。
 *
 * - OFF → 前回のモード（なければ opportunistic）へ復元
 * - ON（opportunistic / hostname）→ OFF へ。復元用に mode / specifier を保存
 *
 * 書き込みには [WRITE_SECURE_SETTINGS] が必要（ADB タイルと同様）。
 * プライベート DNS 自体は API 28+。
 */
@RequiresApi(Build.VERSION_CODES.N)
class PrivateDnsTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return
        }

        try {
            if (isPrivateDnsEnabled()) {
                saveCurrentModeForRestore()
                Settings.Global.putString(contentResolver, PRIVATE_DNS_MODE, MODE_OFF)
            } else {
                restorePreviousMode()
            }
        } catch (_: SecurityException) {
            notifyPermissionDenied()
            return
        }

        updateTileState()
    }

    private fun isPrivateDnsEnabled(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val mode = getPrivateDnsMode()
        return mode != MODE_OFF && mode.isNotEmpty()
    }

    private fun getPrivateDnsMode(): String {
        return Settings.Global.getString(contentResolver, PRIVATE_DNS_MODE)
            ?.takeIf { it.isNotEmpty() }
            ?: MODE_OFF
    }

    private fun getPrivateDnsSpecifier(): String? {
        return Settings.Global.getString(contentResolver, PRIVATE_DNS_SPECIFIER)
    }

    private fun saveCurrentModeForRestore() {
        val mode = getPrivateDnsMode()
        val specifier = getPrivateDnsSpecifier().orEmpty()
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_MODE, mode)
            .putString(KEY_LAST_SPECIFIER, specifier)
            .apply()
    }

    private fun restorePreviousMode() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        var mode = prefs.getString(KEY_LAST_MODE, null)
            ?.takeIf { it.isNotEmpty() && it != MODE_OFF }
            ?: MODE_OPPORTUNISTIC

        // 指定ホストなのにホスト名が無い場合は自動（opportunistic）にフォールバック
        val specifier = prefs.getString(KEY_LAST_SPECIFIER, null).orEmpty()
        if (mode == MODE_HOSTNAME && specifier.isEmpty()) {
            mode = MODE_OPPORTUNISTIC
        }

        Settings.Global.putString(contentResolver, PRIVATE_DNS_MODE, mode)
        if (mode == MODE_HOSTNAME) {
            Settings.Global.putString(contentResolver, PRIVATE_DNS_SPECIFIER, specifier)
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = getString(R.string.private_dns)
            tile.icon = Icon.createWithResource(this, R.drawable.ic_private_dns_icon)
            tile.updateTile()
            return
        }

        val enabled = isPrivateDnsEnabled()
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.private_dns)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_private_dns_icon)

        // API 29+: 無効時は OFF、有効時はモード詳細
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                !enabled -> getString(R.string.tile_state_off)
                getPrivateDnsMode() == MODE_HOSTNAME -> {
                    getPrivateDnsSpecifier()?.takeIf { it.isNotEmpty() }
                        ?: getString(R.string.private_dns_mode_hostname)
                }
                getPrivateDnsMode() == MODE_OPPORTUNISTIC -> {
                    getString(R.string.private_dns_mode_automatic)
                }
                else -> getString(R.string.tile_state_on)
            }
        }

        tile.updateTile()
    }

    private fun notifyPermissionDenied() {
        val channelId = "private_dns_error_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.write_secure_permission_denied_title),
                NotificationManager.IMPORTANCE_HIGH
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val intent = Intent(this, WriteSecureSettingsHelpActivity::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contentPending = PendingIntent.getActivity(this, 10, intent, flags)
        val actionPending = PendingIntent.getActivity(this, 11, intent, flags)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_private_dns_icon)
            .setContentTitle(getString(R.string.write_secure_permission_denied_title))
            .setContentText(getString(R.string.write_secure_permission_denied_message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPending)
            .addAction(
                R.mipmap.ic_launcher,
                getString(R.string.permission_help_action),
                actionPending
            )
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        // Settings.Global の @hide キー（API 28+）
        private const val PRIVATE_DNS_MODE = "private_dns_mode"
        private const val PRIVATE_DNS_SPECIFIER = "private_dns_specifier"

        private const val MODE_OFF = "off"
        private const val MODE_OPPORTUNISTIC = "opportunistic"
        private const val MODE_HOSTNAME = "hostname"

        private const val PREFS_NAME = "private_dns_tile"
        private const val KEY_LAST_MODE = "last_mode"
        private const val KEY_LAST_SPECIFIER = "last_specifier"

        private const val NOTIFICATION_ID = 2
    }
}
