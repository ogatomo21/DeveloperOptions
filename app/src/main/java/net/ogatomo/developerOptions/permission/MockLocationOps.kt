package net.ogatomo.developerOptions.permission

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import net.ogatomo.developerOptions.shizuku.ShellUserServiceClient

/**
 * 開発者向けオプション「仮の現在地情報アプリ」相当を AppOps で操作する。
 *
 * hidden の IAppOpsService は端末によって ClassNotFound になるため、
 * Shizuku UserService 上で shell の `appops` コマンドを実行する。
 *
 * ```
 * appops set <pkg> android:mock_location allow|deny
 * appops get <pkg> android:mock_location
 * ```
 */
object MockLocationOps {

    /** SDK から削除済みの権限名。候補列挙用に文字列で参照する。 */
    private const val PERMISSION_ACCESS_MOCK_LOCATION = "android.permission.ACCESS_MOCK_LOCATION"
    private const val OP_MOCK_LOCATION = "android:mock_location"

    private const val PREFS = "mock_location"
    private const val KEY_SELECTED_PACKAGE = "selected_package"

    data class Candidate(
        val packageName: String,
        val label: CharSequence,
        val applicationInfo: ApplicationInfo,
    )

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getSelectedPackage(context: Context): String? =
        prefs(context).getString(KEY_SELECTED_PACKAGE, null)?.takeIf { it.isNotEmpty() }

    fun setSelectedPackage(context: Context, packageName: String?) {
        prefs(context).edit().apply {
            if (packageName.isNullOrEmpty()) {
                remove(KEY_SELECTED_PACKAGE)
            } else {
                putString(KEY_SELECTED_PACKAGE, packageName)
            }
        }.apply()
    }

    /**
     * ACCESS_MOCK_LOCATION を要求しているアプリ + 選択済みパッケージを候補にする。
     */
    fun listCandidates(context: Context): List<Candidate> {
        val pm = context.packageManager
        val selected = getSelectedPackage(context)
        val byPackage = linkedMapOf<String, Candidate>()

        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }

        for (info in packages) {
            val requested = info.requestedPermissions ?: continue
            if (!requested.contains(PERMISSION_ACCESS_MOCK_LOCATION)) continue
            val appInfo = info.applicationInfo ?: continue
            if (appInfo.packageName == context.packageName) continue
            val label = runCatching { pm.getApplicationLabel(appInfo) }.getOrDefault(appInfo.packageName)
            byPackage[appInfo.packageName] = Candidate(appInfo.packageName, label, appInfo)
        }

        if (!selected.isNullOrEmpty() && !byPackage.containsKey(selected)) {
            runCatching {
                val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(selected, PackageManager.ApplicationInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(selected, 0)
                }
                val label = pm.getApplicationLabel(appInfo)
                byPackage[selected] = Candidate(selected, label, appInfo)
            }
        }

        return byPackage.values.sortedBy { it.label.toString().lowercase() }
    }

    fun packageLabel(context: Context, packageName: String): String {
        return runCatching {
            val pm = context.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(appInfo).toString()
        }.getOrDefault(packageName)
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)
    }

    /**
     * 選択中パッケージの mock_location が allow か（1 回の appops のみ）。
     * QS タイル更新向け。全アプリ走査はしない。
     */
    fun isSelectedAllowed(context: Context): Boolean {
        val selected = getSelectedPackage(context) ?: return false
        if (!isPackageInstalled(context, selected)) return false
        return runCatching {
            ShizukuAvailability.requireReady(context)
            isModeAllowed(context, selected)
        }.getOrDefault(false)
    }

    /**
     * 現在 mock_location が allow のパッケージ。見つからなければ null。
     * Shizuku READY が必要。設定画面など、選択以外の許可も見つけたい場合向け。
     */
    fun getActivePackage(context: Context): String? {
        ShizukuAvailability.requireReady(context)

        val selected = getSelectedPackage(context)
        if (!selected.isNullOrEmpty() && isModeAllowed(context, selected)) {
            return selected
        }

        for (candidate in listCandidates(context)) {
            if (candidate.packageName == selected) continue
            if (isModeAllowed(context, candidate.packageName)) {
                return candidate.packageName
            }
        }
        return null
    }

    fun isEnabled(context: Context): Boolean {
        return runCatching { isSelectedAllowed(context) }.getOrDefault(false)
    }

    /**
     * [packageName] を仮の現在地アプリとして有効化。以前の選択は deny。
     *
     * 全候補の appops 問い合わせは QS を固めるため行わない。
     * 直前の選択パッケージだけ deny してから allow する。
     */
    fun enable(context: Context, packageName: String) {
        ShizukuAvailability.requireReady(context)
        require(packageName.isNotEmpty()) { "packageName is empty" }
        if (!isPackageInstalled(context, packageName)) {
            throw IllegalStateException("Package not installed: $packageName")
        }

        val previous = getSelectedPackage(context)
        if (!previous.isNullOrEmpty() && previous != packageName) {
            runCatching { setMode(context, previous, allow = false) }
        }

        setMode(context, packageName, allow = true)
        setSelectedPackage(context, packageName)

        if (!isModeAllowed(context, packageName)) {
            throw IllegalStateException("Failed to allow mock_location for $packageName")
        }
    }

    /**
     * 選択中のアプリを deny して無効化。選択自体は保持（再 ON 用）。
     *
     * @param scanOthers true なら選択以外に allow が残っていればそれも deny（設定画面向け・重い）
     */
    fun disable(context: Context, scanOthers: Boolean = false) {
        ShizukuAvailability.requireReady(context)

        val selected = getSelectedPackage(context)
        if (!selected.isNullOrEmpty()) {
            runCatching { setMode(context, selected, allow = false) }
        }

        if (scanOthers) {
            val stillActive = runCatching { getActivePackage(context) }.getOrNull()
            if (!stillActive.isNullOrEmpty()) {
                runCatching { setMode(context, stillActive, allow = false) }
            }
        }
    }

    /**
     * タイル用トグル。選択パッケージ必須。選択アプリのみを見て切替（全走査なし）。
     * @return トグル後に有効なら true
     */
    fun toggle(context: Context): Boolean {
        val selected = getSelectedPackage(context)
            ?: throw IllegalStateException("No mock location app selected")
        return if (isSelectedAllowed(context)) {
            disable(context)
            false
        } else {
            enable(context, selected)
            true
        }
    }

    private fun isModeAllowed(context: Context, packageName: String): Boolean {
        // appops get は未設定時も 0 で戻ることが多い。出力をパースする。
        val result = ShellUserServiceClient.exec(
            context,
            "appops get ${shellEscape(packageName)} $OP_MOCK_LOCATION"
        )
        val text = result.output.lowercase()
        // 例: "android:mock_location: allow" / "No operations."
        if (text.contains("deny") || text.contains("ignore") || text.contains("errored")) {
            return false
        }
        return text.contains("allow")
    }

    private fun setMode(context: Context, packageName: String, allow: Boolean) {
        val mode = if (allow) "allow" else "deny"
        // まず appops、失敗時は cmd appops
        val cmd = "appops set ${shellEscape(packageName)} $OP_MOCK_LOCATION $mode"
        val result = ShellUserServiceClient.exec(context, cmd)
        if (result.exitCode == 0) return

        val alt = "cmd appops set ${shellEscape(packageName)} $OP_MOCK_LOCATION $mode"
        val altResult = ShellUserServiceClient.exec(context, alt)
        if (altResult.exitCode != 0) {
            throw IllegalStateException(
                "appops set failed (${result.exitCode}): ${result.output.trim()}\n" +
                    "cmd appops failed (${altResult.exitCode}): ${altResult.output.trim()}"
            )
        }
    }

    /** パッケージ名は通常英数字と . のみだが、念のためシングルクオートで囲む */
    private fun shellEscape(packageName: String): String {
        require(packageName.matches(Regex("[A-Za-z0-9._]+"))) {
            "Invalid package name: $packageName"
        }
        return packageName
    }
}
