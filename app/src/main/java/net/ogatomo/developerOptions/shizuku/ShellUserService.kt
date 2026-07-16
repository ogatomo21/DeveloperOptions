package net.ogatomo.developerOptions.shizuku

import android.content.Context
import net.ogatomo.developerOptions.IShellCommandService
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shizuku が shell / root プロセスとして起動する UserService。
 * hidden API を使わず appops 等を実行する。
 *
 * ProGuard でクラス名を維持すること（ComponentName で参照）。
 */
class ShellUserService : IShellCommandService.Stub {

    @Suppress("unused")
    constructor()

    /** Shizuku v13+ が優先して呼ぶコンストラクタ */
    @Suppress("unused")
    constructor(@Suppress("UNUSED_PARAMETER") context: Context)

    override fun exec(command: String?): String {
        if (command.isNullOrBlank()) {
            return "1\nempty command"
        }
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val code = process.waitFor()
            "$code\n$output"
        } catch (t: Throwable) {
            "1\n${t.message ?: t.toString()}"
        }
    }

    override fun destroy() {
        System.exit(0)
    }
}
