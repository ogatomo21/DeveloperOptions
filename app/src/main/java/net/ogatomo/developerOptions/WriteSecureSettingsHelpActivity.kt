package net.ogatomo.developerOptions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import net.ogatomo.developerOptions.permission.SecureSettingsPermission
import net.ogatomo.developerOptions.permission.ShizukuGrantHelper
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

class WriteSecureSettingsHelpActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var shizukuGrantButton: Button
    private lateinit var shizukuHintText: TextView
    private lateinit var copyCommandButton: Button

    private val executor = Executors.newSingleThreadExecutor()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread { refreshUi() }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        runOnUiThread { refreshUi() }
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != ShizukuGrantHelper.REQUEST_CODE_SHIZUKU_PERMISSION) return@OnRequestPermissionResultListener
            runOnUiThread {
                if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    // 許可直後にそのまま grant を試行
                    startGrant()
                } else {
                    Toast.makeText(
                        this,
                        R.string.shizuku_permission_denied,
                        Toast.LENGTH_LONG
                    ).show()
                    refreshUi()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_write_secure_help)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val root = findViewById<View>(R.id.root_layout)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val status = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(0, status.top, 0, 0)
            insets
        }

        statusText = findViewById(R.id.status_text)
        shizukuGrantButton = findViewById(R.id.shizuku_grant_button)
        shizukuHintText = findViewById(R.id.shizuku_hint_text)
        copyCommandButton = findViewById(R.id.copy_command_button)

        shizukuGrantButton.setOnClickListener { onShizukuGrantClicked() }
        copyCommandButton.setOnClickListener { copyAdbCommand() }

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)

        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        executor.shutdownNow()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun refreshUi() {
        when (val status = ShizukuGrantHelper.status(this)) {
            ShizukuGrantHelper.Status.PERMISSION_ALREADY_GRANTED -> {
                statusText.setText(R.string.status_permission_granted)
                shizukuGrantButton.isEnabled = false
                shizukuGrantButton.setText(R.string.shizuku_grant_button_done)
                shizukuHintText.setText(R.string.shizuku_hint_already_granted)
            }

            ShizukuGrantHelper.Status.NOT_AVAILABLE -> {
                statusText.setText(R.string.status_permission_missing)
                shizukuGrantButton.isEnabled = true
                shizukuGrantButton.setText(R.string.shizuku_open_download)
                shizukuHintText.setText(R.string.shizuku_hint_not_installed)
            }

            ShizukuGrantHelper.Status.DEAD -> {
                statusText.setText(R.string.status_permission_missing)
                shizukuGrantButton.isEnabled = true
                shizukuGrantButton.setText(R.string.shizuku_open_app)
                shizukuHintText.setText(R.string.shizuku_hint_not_running)
            }

            ShizukuGrantHelper.Status.PRE_V11 -> {
                statusText.setText(R.string.status_permission_missing)
                shizukuGrantButton.isEnabled = false
                shizukuGrantButton.setText(R.string.shizuku_grant_button)
                shizukuHintText.setText(R.string.shizuku_hint_pre_v11)
            }

            ShizukuGrantHelper.Status.NO_SHIZUKU_PERMISSION -> {
                statusText.setText(R.string.status_permission_missing)
                shizukuGrantButton.isEnabled = true
                shizukuGrantButton.setText(R.string.shizuku_request_permission)
                shizukuHintText.setText(R.string.shizuku_hint_need_permission)
            }

            ShizukuGrantHelper.Status.READY -> {
                statusText.setText(R.string.status_permission_missing)
                shizukuGrantButton.isEnabled = true
                shizukuGrantButton.setText(R.string.shizuku_grant_button)
                shizukuHintText.setText(R.string.shizuku_hint_ready)
            }
        }

        // ステータス表示に Shizuku 接続状況も足す（付与済み以外）
        if (!SecureSettingsPermission.isGranted(this)) {
            val shizukuLine = when (ShizukuGrantHelper.status(this)) {
                ShizukuGrantHelper.Status.NOT_AVAILABLE -> getString(R.string.status_shizuku_unavailable)
                ShizukuGrantHelper.Status.DEAD -> getString(R.string.status_shizuku_dead)
                ShizukuGrantHelper.Status.PRE_V11 -> getString(R.string.status_shizuku_pre_v11)
                ShizukuGrantHelper.Status.NO_SHIZUKU_PERMISSION -> getString(R.string.status_shizuku_no_permission)
                ShizukuGrantHelper.Status.READY -> getString(R.string.status_shizuku_ready)
                ShizukuGrantHelper.Status.PERMISSION_ALREADY_GRANTED -> ""
            }
            if (shizukuLine.isNotEmpty()) {
                statusText.append("\n")
                statusText.append(shizukuLine)
            }
        }
    }

    private fun onShizukuGrantClicked() {
        when (ShizukuGrantHelper.status(this)) {
            ShizukuGrantHelper.Status.PERMISSION_ALREADY_GRANTED -> {
                Toast.makeText(this, R.string.status_permission_granted, Toast.LENGTH_SHORT).show()
            }

            ShizukuGrantHelper.Status.NOT_AVAILABLE -> {
                openShizukuDownload()
            }

            ShizukuGrantHelper.Status.DEAD -> {
                openShizukuApp()
            }

            ShizukuGrantHelper.Status.PRE_V11 -> {
                Toast.makeText(this, R.string.shizuku_hint_pre_v11, Toast.LENGTH_LONG).show()
            }

            ShizukuGrantHelper.Status.NO_SHIZUKU_PERMISSION -> {
                ShizukuGrantHelper.requestShizukuPermission()
            }

            ShizukuGrantHelper.Status.READY -> {
                startGrant()
            }
        }
    }

    private fun startGrant() {
        shizukuGrantButton.isEnabled = false
        shizukuGrantButton.setText(R.string.shizuku_granting)
        shizukuHintText.setText(R.string.shizuku_hint_granting)

        executor.execute {
            val result = runCatching {
                ShizukuGrantHelper.grantWriteSecureSettings(this@WriteSecureSettingsHelpActivity)
            }
            runOnUiThread {
                result.fold(
                    onSuccess = {
                        Toast.makeText(
                            this,
                            R.string.shizuku_grant_success,
                            Toast.LENGTH_LONG
                        ).show()
                        refreshUi()
                    },
                    onFailure = { e ->
                        Toast.makeText(
                            this,
                            getString(R.string.shizuku_grant_failed, e.message ?: e.toString()),
                            Toast.LENGTH_LONG
                        ).show()
                        refreshUi()
                    }
                )
            }
        }
    }

    private fun openShizukuDownload() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ShizukuGrantHelper.SHIZUKU_DOWNLOAD_URL))
        runCatching { startActivity(intent) }
            .onFailure {
                Toast.makeText(this, R.string.shizuku_hint_not_installed, Toast.LENGTH_LONG).show()
            }
    }

    private fun openShizukuApp() {
        val launch = packageManager.getLaunchIntentForPackage(ShizukuGrantHelper.SHIZUKU_PACKAGE)
        if (launch != null) {
            startActivity(launch)
        } else {
            openShizukuDownload()
        }
    }

    private fun copyAdbCommand() {
        val command = getString(R.string.adb_helper_command)
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("adb", command))
        Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }
}
