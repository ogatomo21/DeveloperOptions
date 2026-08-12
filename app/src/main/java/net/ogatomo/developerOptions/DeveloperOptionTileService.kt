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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.annotation.RequiresApi


@RequiresApi(Build.VERSION_CODES.N)
class AdbToggleTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()

        val current = isAdbEnabled()
        val newValue = if (current) 0 else 1

        try {
            // ADB ON/OFF の書き込み（WRITE_SECURE_SETTINGS が必要）
            Settings.Global.putInt(
                contentResolver,
                Settings.Global.ADB_ENABLED,
                newValue
            )

        } catch (e: SecurityException) {
            val channelId = "adb_error_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    getString(R.string.write_secure_permission_denied_title),
                    NotificationManager.IMPORTANCE_HIGH
                )
                val manager = getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(channel)
            }

            val intent = Intent(this, WriteSecureSettingsHelpActivity::class.java)
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val actionIntent = PendingIntent.getActivity(
                this,
                1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_adb_icon)
                .setContentTitle(getString(R.string.write_secure_permission_denied_title))
                .setContentText(getString(R.string.write_secure_permission_denied_message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pending)
                .addAction(
                    R.mipmap.ic_launcher,
                    getString(R.string.permission_help_action),
                    actionIntent
                )
                .setAutoCancel(true)

            try {
                NotificationManagerCompat.from(this).notify(1, builder.build())
            } catch (_: SecurityException) {
                // POST_NOTIFICATIONS 未許可時は無視
            }
            return
        }

        updateTileState()
    }

    private fun isAdbEnabled(): Boolean {
        return Settings.Global.getInt(
            contentResolver,
            Settings.Global.ADB_ENABLED,
            0
        ) == 1
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        try {
            val enabled = isAdbEnabled()
            tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = getString(R.string.usb_debug)
            tile.icon = Icon.createWithResource(this, R.drawable.ic_adb_icon)
            // API 29+: ON/OFF はサブタイトルに表示（空にしない）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = getString(
                    if (enabled) R.string.tile_state_on else R.string.tile_state_off
                )
            }
            tile.updateTile()
        } catch (_: Throwable) {
            // QS 切断直後などで失敗してもサービスを落とさない
        }
    }
}
