package net.ogatomo.developerOptions.permission

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Shizuku (または Sui) 経由で [WRITE_SECURE_SETTINGS] を自アプリに grant する。
 *
 * 付与後は Shizuku なしで [Settings.Global] への書き込みが可能になる。
 */
object ShizukuGrantHelper {

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val SHIZUKU_DOWNLOAD_URL = "https://shizuku.rikka.app/download/"

    const val REQUEST_CODE_SHIZUKU_PERMISSION = 1001

    enum class Status {
        /** WRITE_SECURE_SETTINGS 付与済み */
        PERMISSION_ALREADY_GRANTED,

        /** Shizuku / Sui 未導入（binder も取れない） */
        NOT_AVAILABLE,

        /** Shizuku は入っているがサービス未起動 */
        DEAD,

        /** Shizuku pre-v11（非対応） */
        PRE_V11,

        /** 本アプリへの Shizuku 利用許可が未付与 */
        NO_SHIZUKU_PERMISSION,

        /** grant 実行可能 */
        READY,
    }

    fun isShizukuInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun status(context: Context): Status {
        if (SecureSettingsPermission.isGranted(context)) {
            return Status.PERMISSION_ALREADY_GRANTED
        }

        if (Shizuku.isPreV11()) {
            return Status.PRE_V11
        }

        if (!Shizuku.pingBinder()) {
            return if (isShizukuInstalled(context)) Status.DEAD else Status.NOT_AVAILABLE
        }

        return if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            Status.READY
        } else {
            Status.NO_SHIZUKU_PERMISSION
        }
    }

    fun requestShizukuPermission() {
        Shizuku.requestPermission(REQUEST_CODE_SHIZUKU_PERMISSION)
    }

    /**
     * shell 権限で IPackageManager#grantRuntimePermission を呼び出す。
     * 呼び出し元はバックグラウンドスレッド推奨。
     *
     * @throws IllegalStateException Shizuku 未接続・未許可
     * @throws Exception grant 失敗
     */
    fun grantWriteSecureSettings(context: Context) {
        if (SecureSettingsPermission.isGranted(context)) return

        if (!Shizuku.pingBinder()) {
            throw IllegalStateException("Shizuku binder is not available")
        }
        if (Shizuku.isPreV11()) {
            throw IllegalStateException("Shizuku pre-v11 is not supported")
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            throw IllegalStateException("Shizuku permission not granted")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("Landroid/content/pm/")
        }

        val packageName = context.packageName
        val userId = Process.myUid() / 100_000

        val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("package"))
        val iPm = asInterfacePackageManager(binder)
            ?: throw IllegalStateException("IPackageManager.Stub.asInterface failed")

        grantRuntimePermission(iPm, packageName, SecureSettingsPermission.PERMISSION, userId)

        if (!SecureSettingsPermission.isGranted(context)) {
            throw IllegalStateException("Permission still not granted after IPackageManager call")
        }
    }

    private fun asInterfacePackageManager(binder: IBinder): Any? {
        val stubClass = Class.forName("android.content.pm.IPackageManager\$Stub")
        val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
        return asInterface.invoke(null, binder)
    }

    /**
     * API 差のある grantRuntimePermission シグネチャを順に試す。
     * - (String, String, int) userId
     * - (String, String, int, int) deviceId, userId  (一部新 API)
     */
    private fun grantRuntimePermission(
        iPm: Any,
        packageName: String,
        permission: String,
        userId: Int,
    ) {
        val iPmClass = Class.forName("android.content.pm.IPackageManager")
        val errors = mutableListOf<Throwable>()

        // (package, permission, userId)
        try {
            val method = iPmClass.getMethod(
                "grantRuntimePermission",
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            )
            method.invoke(iPm, packageName, permission, userId)
            return
        } catch (t: Throwable) {
            errors.add(unwrap(t))
        }

        // (package, permission, deviceId, userId) — Android 14+ 系
        try {
            val method = iPmClass.getMethod(
                "grantRuntimePermission",
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            // deviceId: Context.DEVICE_ID_DEFAULT = 0
            method.invoke(iPm, packageName, permission, 0, userId)
            return
        } catch (t: Throwable) {
            errors.add(unwrap(t))
        }

        val message = errors.joinToString(separator = " | ") { it.message ?: it.toString() }
        throw IllegalStateException("grantRuntimePermission failed: $message")
    }

    private fun unwrap(t: Throwable): Throwable {
        var cur = t
        while (cur.cause != null && cur is java.lang.reflect.InvocationTargetException) {
            cur = cur.cause!!
        }
        return cur
    }
}
