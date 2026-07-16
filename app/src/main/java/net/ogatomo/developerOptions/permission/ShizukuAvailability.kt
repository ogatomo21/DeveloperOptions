package net.ogatomo.developerOptions.permission

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * WRITE_SECURE_SETTINGS とは独立した、Shizuku / Sui の利用可否。
 * 仮の現在地（AppOps）のように操作のたびに shell 権限が必要な機能向け。
 */
object ShizukuAvailability {

    enum class Status {
        NOT_AVAILABLE,
        DEAD,
        PRE_V11,
        NO_PERMISSION,
        READY,
    }

    fun status(context: Context): Status {
        if (!Shizuku.pingBinder()) {
            return if (ShizukuGrantHelper.isShizukuInstalled(context)) {
                Status.DEAD
            } else {
                Status.NOT_AVAILABLE
            }
        }

        if (Shizuku.isPreV11()) {
            return Status.PRE_V11
        }

        return if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            Status.READY
        } else {
            Status.NO_PERMISSION
        }
    }

    fun isReady(context: Context): Boolean = status(context) == Status.READY

    fun requireReady(context: Context) {
        when (status(context)) {
            Status.READY -> return
            Status.NOT_AVAILABLE -> throw IllegalStateException("Shizuku is not installed")
            Status.DEAD -> throw IllegalStateException("Shizuku is not running")
            Status.PRE_V11 -> throw IllegalStateException("Shizuku pre-v11 is not supported")
            Status.NO_PERMISSION -> throw IllegalStateException("Shizuku permission not granted")
        }
    }
}
