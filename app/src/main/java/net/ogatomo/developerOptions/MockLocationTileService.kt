package net.ogatomo.developerOptions

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import net.ogatomo.developerOptions.permission.MockLocationOps
import net.ogatomo.developerOptions.permission.ShizukuAvailability
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 仮の現在地情報アプリの有効 / 無効をトグルする QS タイル。
 *
 * AppOps 操作のためタップのたびに Shizuku が必要。
 * 対象アプリ未選択時は設定画面を開く。
 *
 * タイル表示は選択パッケージのみを確認する（全アプリ走査はしない）。
 * 非同期完了前に仮の ON/OFF を出し、パネルが空白・無応答に見えないようにする。
 */
@RequiresApi(Build.VERSION_CODES.N)
class MockLocationTileService : TileService() {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger(0)

    @Volatile
    private var listening = false

    override fun onStartListening() {
        super.onStartListening()
        listening = true
        refreshTile()
    }

    override fun onStopListening() {
        listening = false
        // 進行中の非同期結果を破棄（閉じた後の updateTile を避ける）
        generation.incrementAndGet()
        super.onStopListening()
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

        // 操作中も字幕が空にならないよう、先に仮状態を維持したままトグル
        val gen = generation.incrementAndGet()
        executor.execute {
            val result = runCatching { MockLocationOps.toggle(this) }
            result.onFailure {
                notifyError(it.message ?: it.toString())
            }
            if (gen != generation.get()) return@execute
            val allowed = result.getOrElse {
                runCatching { MockLocationOps.isSelectedAllowed(this) }.getOrDefault(false)
            }
            val subtitle = if (allowed) {
                MockLocationOps.packageLabel(this, selected)
            } else {
                getString(R.string.tile_state_off)
            }
            postApply(gen, allowed, subtitle)
        }
    }

    override fun onDestroy() {
        listening = false
        generation.incrementAndGet()
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        tile.label = getString(R.string.mock_location)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_mock_location_icon)

        val selected = MockLocationOps.getSelectedPackage(this)
        if (selected.isNullOrEmpty()) {
            applyTile(
                state = Tile.STATE_INACTIVE,
                subtitle = getString(R.string.mock_location_not_selected)
            )
            return
        }

        if (!ShizukuAvailability.isReady(this)) {
            applyTile(
                state = Tile.STATE_INACTIVE,
                subtitle = getString(R.string.tile_state_off)
            )
            return
        }

        // 字幕が空のときだけ仮の OFF を即時表示（再表示時の点滅を避ける）
        val subtitleBlank = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            tile.subtitle.isNullOrBlank()
        if (subtitleBlank) {
            applyTile(
                state = Tile.STATE_INACTIVE,
                subtitle = getString(R.string.tile_state_off)
            )
        } else {
            // ラベル等だけ同期し、状態は非同期で確定
            try {
                tile.label = getString(R.string.mock_location)
                tile.icon = Icon.createWithResource(this, R.drawable.ic_mock_location_icon)
                tile.updateTile()
            } catch (_: Throwable) {
                // ignore
            }
        }

        val gen = generation.incrementAndGet()
        val selectedPkg = selected
        executor.execute {
            val allowed = runCatching { MockLocationOps.isSelectedAllowed(this) }.getOrDefault(false)
            if (gen != generation.get()) return@execute
            val subtitle = if (allowed) {
                MockLocationOps.packageLabel(this, selectedPkg)
            } else {
                getString(R.string.tile_state_off)
            }
            postApply(gen, allowed, subtitle)
        }
    }

    private fun postApply(gen: Int, allowed: Boolean, subtitle: String) {
        mainHandler.post {
            if (!listening || gen != generation.get()) return@post
            applyTile(
                state = if (allowed) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE,
                subtitle = subtitle
            )
        }
    }

    private fun applyTile(state: Int, subtitle: String) {
        val tile = qsTile ?: return
        try {
            tile.state = state
            tile.label = getString(R.string.mock_location)
            tile.icon = Icon.createWithResource(this, R.drawable.ic_mock_location_icon)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = subtitle
            }
            tile.updateTile()
        } catch (_: Throwable) {
            // QS 切断直後などで失敗してもサービスを落とさない
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

        try {
            NotificationManagerCompat.from(this).notify(id, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS 未許可時は無視（タイル処理は継続）
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 3
        private const val NOTIFICATION_ID_ERROR = 4
    }
}
