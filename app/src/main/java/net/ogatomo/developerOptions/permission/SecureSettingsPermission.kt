package net.ogatomo.developerOptions.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings

object SecureSettingsPermission {

    const val PERMISSION: String = Manifest.permission.WRITE_SECURE_SETTINGS

    fun isGranted(context: Context): Boolean {
        return context.checkPermission(
            PERMISSION,
            Process.myPid(),
            Process.myUid()
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 権限フラグに加え、実際に Global 設定へ書けるかも軽く確認する。
     * （一部 OEM で grant 後もブロックされるケース向け）
     */
    fun canWriteSecureSettings(context: Context): Boolean {
        if (!isGranted(context)) return false
        return try {
            // 読み取りのみでも権限不足だと SecurityException になる端末がある
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
