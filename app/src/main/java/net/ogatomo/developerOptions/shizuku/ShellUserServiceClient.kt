package net.ogatomo.developerOptions.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import net.ogatomo.developerOptions.IShellCommandService
import net.ogatomo.developerOptions.permission.ShizukuAvailability
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * [ShellUserService] を bind し、shell 権限でコマンドを実行する。
 */
object ShellUserServiceClient {

    private const val SERVICE_CLASS =
        "net.ogatomo.developerOptions.shizuku.ShellUserService"
    private const val SERVICE_TAG = "developeroptions_shell"
    private const val SERVICE_VERSION = 2
    // QS タイル更新を長時間ブロックしない（失敗時は OFF 表示にフォールバック）
    private const val BIND_TIMEOUT_SEC = 5L

    private val lock = Any()
    private var service: IShellCommandService? = null
    private var connection: ServiceConnection? = null

    data class ExecResult(val exitCode: Int, val output: String)

    init {
        Shizuku.addBinderDeadListener {
            clear()
        }
    }

    fun exec(context: Context, command: String): ExecResult {
        ShizukuAvailability.requireReady(context)
        val svc = ensureService(context)
        val raw = try {
            svc.exec(command) ?: "1\nnull response"
        } catch (t: Throwable) {
            clear()
            throw IllegalStateException("UserService exec failed: ${t.message}", t)
        }
        return parse(raw)
    }

    fun execOrThrow(context: Context, command: String): String {
        val result = exec(context, command)
        if (result.exitCode != 0) {
            throw IllegalStateException(
                "Command failed (${result.exitCode}): $command\n${result.output.trim()}"
            )
        }
        return result.output
    }

    private fun parse(raw: String): ExecResult {
        val nl = raw.indexOf('\n')
        if (nl <= 0) {
            return ExecResult(1, raw)
        }
        val code = raw.substring(0, nl).toIntOrNull() ?: 1
        val body = raw.substring(nl + 1)
        return ExecResult(code, body)
    }

    private fun ensureService(context: Context): IShellCommandService {
        synchronized(lock) {
            val existing = service
            if (existing != null) {
                val binder = existing.asBinder()
                if (binder != null && binder.isBinderAlive && binder.pingBinder()) {
                    return existing
                }
                clearLocked()
            }

            val appContext = context.applicationContext
            val latch = CountDownLatch(1)
            val holder = AtomicReference<IShellCommandService?>()
            val error = AtomicReference<Throwable?>()

            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    try {
                        holder.set(IShellCommandService.Stub.asInterface(binder))
                    } catch (t: Throwable) {
                        error.set(t)
                    } finally {
                        latch.countDown()
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    synchronized(lock) {
                        service = null
                        connection = null
                    }
                }

                override fun onBindingDied(name: ComponentName?) {
                    synchronized(lock) {
                        service = null
                        connection = null
                    }
                }

                override fun onNullBinding(name: ComponentName?) {
                    error.set(IllegalStateException("Null binding for UserService"))
                    latch.countDown()
                }
            }

            val args = Shizuku.UserServiceArgs(ComponentName(appContext.packageName, SERVICE_CLASS))
                .daemon(false)
                .processNameSuffix("shell")
                .debuggable(false)
                .version(SERVICE_VERSION)
                .tag(SERVICE_TAG)

            connection = conn
            try {
                Shizuku.bindUserService(args, conn)
            } catch (t: Throwable) {
                connection = null
                throw IllegalStateException("bindUserService failed: ${t.message}", t)
            }

            if (!latch.await(BIND_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                runCatching { Shizuku.unbindUserService(args, conn, true) }
                connection = null
                throw IllegalStateException("UserService bind timeout (${BIND_TIMEOUT_SEC}s)")
            }

            error.get()?.let {
                connection = null
                throw IllegalStateException("UserService bind error: ${it.message}", it)
            }

            val bound = holder.get()
                ?: throw IllegalStateException("UserService binder is null")
            service = bound
            return bound
        }
    }

    fun clear() {
        synchronized(lock) {
            clearLocked()
        }
    }

    private fun clearLocked() {
        val conn = connection
        service = null
        connection = null
        if (conn != null) {
            runCatching {
                val args = Shizuku.UserServiceArgs(
                    ComponentName("placeholder", SERVICE_CLASS)
                ).tag(SERVICE_TAG).version(SERVICE_VERSION)
                // unbind には同じ ComponentName が望ましいが、切断時は失敗してもよい
                Shizuku.unbindUserService(
                    Shizuku.UserServiceArgs(
                        ComponentName(
                            // package 不明時は無視
                            "net.ogatomo.developerOptions",
                            SERVICE_CLASS
                        )
                    ).daemon(false)
                        .processNameSuffix("shell")
                        .version(SERVICE_VERSION)
                        .tag(SERVICE_TAG),
                    conn,
                    true
                )
            }
        }
    }
}
