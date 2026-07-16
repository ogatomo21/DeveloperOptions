package net.ogatomo.developerOptions

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import net.ogatomo.developerOptions.permission.MockLocationOps
import net.ogatomo.developerOptions.permission.ShizukuAvailability
import java.util.concurrent.Executors

/**
 * 仮の現在地情報アプリの有効 / 無効をトグルする QS タイル。
 *
 * AppOps 操作のためタップのたびに Shizuku が必要。
 * 対象アプリ未選択時は設定画面を開く。
 */
@RequiresApi(Build.VERSION_CODES.N)
class MockLocationTileService : TileService() {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onStartListening() {
        super.onStartListening()
        refreshTileAsync()
    }

    override fun onClick() {
        super.onClick()

        when (ShizukuAvailability.status(this)) {
            ShizukuAvailability.Status.NOT_AVAILABLE,
            ShizukuAvailability.Status.DEAD,
            ShizukuAvailability.Status.PRE_V11,
            ShizukuAvailability.Status.NO_PERMISSION -> {
                notifyNeedShizuku()
                return
            }
            ShizukuAvailability.Status.READY -> Unit
        }

        val selected = MockLocationOps.getSelectedPackage(this)
        if (selected.isNullOrEmpty() || !MockLocationOps.isPackageInstalled(this, selected)) {
            openSettings()
            return
        }

        executor.execute {
            val result = runCatching { MockLocationOps.toggle(this) }
            // Tile 更新はメイン想定ではないが qsTile はどこからでも update 可
            result.fold(
                onSuccess = { refreshTileAsync() },
                onFailure = {
                    notifyError(it.message ?: it.toString())
                    refreshTileAsync()
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }

    private fun refreshTileAsync() {
        val tile = qsTile ?: return
        tile.label = getString(R.string.mock_location)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_mock_location_icon)

        val selected = MockLocationOps.getSelectedPackage(this)
        if (selected.isNullOrEmpty()) {
            tile.state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = getString(R.string.mock_location_not_selected)
            }
            tile.updateTile()
            return
        }

        if (!ShizukuAvailability.isReady(this)) {
            tile.state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = getString(R.string.tile_state_off)
            }
            tile.updateTile()
            return
        }

        executor.execute {
            val active = runCatching { MockLocationOps.getActivePackage(this) }.getOrNull()
            val enabled = active != null
            val labelForSub = if (active != null) {
                MockLocationOps.packageLabel(this, active)
            } else {
                getString(R.string.tile_state_off)
            }
            // update on main-ish
            val t = qsTile ?: return@execute
            t.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            t.label = getString(R.string.mock_location)
            t.icon = Icon.createWithResource(this, R.drawable.ic_mock_location_icon)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                t.subtitle = labelForSub
            }
            t.updateTile()
        }
    }

    private fun openSettings() {
        val intent = Intent(this, AppSettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // API 34+: startActivityAndCollapse(PendingIntent)
        if (Build.VERSION.SDK_INT >= 34) {
            val pending = PendingIntent.getActivity(
                this,
                30,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun notifyNeedShizuku() {
        notify(
            NOTIFICATION_ID,
            getString(R.string.mock_location_need_shizuku_title),
            getString(R.string.mock_location_need_shizuku_message)
        )
    }

    private fun notifyError(detail: String) {
        notify(
            NOTIFICATION_ID_ERROR,
            getString(R.string.mock_location_error_title),
            detail
        )
    }

    private fun notify(id: Int, title: String, message: String) {
        val channelId = "mock_location_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.mock_location),
                NotificationManager.IMPORTANCE_HIGH
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val intent = Intent(this, AppSettingsActivity::class.java)
        val pending = PendingIntent.getActivity(
            this,
            31,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_mock_location_icon)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this).notify(id, notification)
    }

    companion object {
        private const val NOTIFICATION_ID = 3
        private const val NOTIFICATION_ID_ERROR = 4
    }
}
