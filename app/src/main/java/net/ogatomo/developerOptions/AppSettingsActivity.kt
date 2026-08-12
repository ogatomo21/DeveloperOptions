package net.ogatomo.developerOptions

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import net.ogatomo.developerOptions.permission.MockLocationOps
import net.ogatomo.developerOptions.permission.SecureSettingsPermission
import net.ogatomo.developerOptions.permission.ShizukuAvailability
import net.ogatomo.developerOptions.permission.ShizukuGrantHelper
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

/**
 * システム「アプリ情報」の「アプリ内の設定」から開かれる設定画面。
 * [android.intent.action.APPLICATION_PREFERENCES]
 */
class AppSettingsActivity : AppCompatActivity() {

    private lateinit var secureStatusText: TextView
    private lateinit var mockStatusText: TextView
    private lateinit var mockHintText: TextView
    private lateinit var mockEmptyText: TextView
    private lateinit var mockAppList: ListView
    private lateinit var mockEnableButton: Button
    private lateinit var mockDisableButton: Button
    private lateinit var mockRefreshButton: Button
    private lateinit var openPermissionHelpButton: Button
    private lateinit var versionText: TextView

    private val executor = Executors.newSingleThreadExecutor()
    private var candidates: List<MockLocationOps.Candidate> = emptyList()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread { refreshAll() }
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        runOnUiThread { refreshAll() }
    }
    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == ShizukuGrantHelper.REQUEST_CODE_SHIZUKU_PERMISSION) {
                runOnUiThread { refreshAll() }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_settings)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val root = findViewById<View>(R.id.root_layout)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val status = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(0, status.top, 0, 0)
            insets
        }

        secureStatusText = findViewById(R.id.secure_status_text)
        mockStatusText = findViewById(R.id.mock_status_text)
        mockHintText = findViewById(R.id.mock_hint_text)
        mockEmptyText = findViewById(R.id.mock_empty_text)
        mockAppList = findViewById(R.id.mock_app_list)
        mockEnableButton = findViewById(R.id.mock_enable_button)
        mockDisableButton = findViewById(R.id.mock_disable_button)
        mockRefreshButton = findViewById(R.id.mock_refresh_button)
        openPermissionHelpButton = findViewById(R.id.open_permission_help_button)
        versionText = findViewById(R.id.version_text)

        openPermissionHelpButton.setOnClickListener {
            startActivity(Intent(this, WriteSecureSettingsHelpActivity::class.java))
        }
        mockEnableButton.setOnClickListener { onEnableMockClicked() }
        mockDisableButton.setOnClickListener { onDisableMockClicked() }
        mockRefreshButton.setOnClickListener { refreshAll() }

        mockAppList.setOnItemClickListener { _, _, position, _ ->
            if (position in candidates.indices) {
                MockLocationOps.setSelectedPackage(this, candidates[position].packageName)
                refreshMockStatusOnly()
            }
        }

        val pInfo = packageManager.getPackageInfo(packageName, 0)
        @Suppress("DEPRECATION")
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            pInfo.longVersionCode
        } else {
            pInfo.versionCode.toLong()
        }
        versionText.text = getString(
            R.string.settings_version,
            pInfo.versionName ?: "?",
            versionCode
        )

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        executor.shutdownNow()
    }

    private fun refreshAll() {
        refreshSecureStatus()
        refreshCandidates()
        refreshMockStatusOnly()
    }

    private fun refreshSecureStatus() {
        secureStatusText.text = if (SecureSettingsPermission.isGranted(this)) {
            getString(R.string.status_permission_granted)
        } else {
            getString(R.string.status_permission_missing)
        }
    }

    private fun refreshCandidates() {
        candidates = MockLocationOps.listCandidates(this)
        if (candidates.isEmpty()) {
            mockAppList.visibility = View.GONE
            mockEmptyText.visibility = View.VISIBLE
            mockAppList.adapter = null
            return
        }

        mockAppList.visibility = View.VISIBLE
        mockEmptyText.visibility = View.GONE

        val labels = candidates.map { "${it.label} (${it.packageName})" }
        mockAppList.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_single_choice,
            labels
        )

        val selected = MockLocationOps.getSelectedPackage(this)
        val index = candidates.indexOfFirst { it.packageName == selected }
        if (index >= 0) {
            mockAppList.setItemChecked(index, true)
        } else {
            mockAppList.clearChoices()
        }
    }

    private fun refreshMockStatusOnly() {
        val shizuku = ShizukuAvailability.status(this)
        val selected = MockLocationOps.getSelectedPackage(this)

        val shizukuLine = when (shizuku) {
            ShizukuAvailability.Status.NOT_AVAILABLE -> getString(R.string.status_shizuku_unavailable)
            ShizukuAvailability.Status.DEAD -> getString(R.string.status_shizuku_dead)
            ShizukuAvailability.Status.PRE_V11 -> getString(R.string.status_shizuku_pre_v11)
            ShizukuAvailability.Status.NO_PERMISSION -> getString(R.string.status_shizuku_no_permission)
            ShizukuAvailability.Status.READY -> getString(R.string.status_shizuku_ready)
        }

        val selectedLine = if (selected.isNullOrEmpty()) {
            getString(R.string.settings_mock_selected_none)
        } else {
            getString(
                R.string.settings_mock_selected,
                MockLocationOps.packageLabel(this, selected),
                selected
            )
        }

        if (shizuku == ShizukuAvailability.Status.READY) {
            mockStatusText.text = getString(R.string.settings_mock_status_loading)
            mockEnableButton.isEnabled = false
            mockDisableButton.isEnabled = false
            executor.execute {
                val active = runCatching { MockLocationOps.getActivePackage(this) }.getOrNull()
                val enabled = active != null
                runOnUiThread {
                    val activeLine = if (active == null) {
                        getString(R.string.settings_mock_active_off)
                    } else {
                        getString(
                            R.string.settings_mock_active_on,
                            MockLocationOps.packageLabel(this, active),
                            active
                        )
                    }
                    mockStatusText.text = buildString {
                        append(selectedLine)
                        append('\n')
                        append(activeLine)
                        append('\n')
                        append(shizukuLine)
                    }
                    mockEnableButton.isEnabled = !selected.isNullOrEmpty()
                    mockDisableButton.isEnabled = enabled || !selected.isNullOrEmpty()
                    mockHintText.setText(
                        if (enabled) R.string.settings_mock_hint_on
                        else R.string.settings_mock_hint_ready
                    )
                }
            }
        } else {
            mockStatusText.text = buildString {
                append(selectedLine)
                append('\n')
                append(getString(R.string.settings_mock_active_unknown))
                append('\n')
                append(shizukuLine)
            }
            mockEnableButton.isEnabled = true
            mockDisableButton.isEnabled = true
            mockHintText.setText(
                when (shizuku) {
                    ShizukuAvailability.Status.NOT_AVAILABLE -> R.string.shizuku_hint_not_installed
                    ShizukuAvailability.Status.DEAD -> R.string.shizuku_hint_not_running
                    ShizukuAvailability.Status.PRE_V11 -> R.string.shizuku_hint_pre_v11
                    ShizukuAvailability.Status.NO_PERMISSION -> R.string.shizuku_hint_need_permission
                    ShizukuAvailability.Status.READY -> R.string.settings_mock_hint_ready
                }
            )
        }
    }

    private fun onEnableMockClicked() {
        ensureShizukuThen {
            val checked = mockAppList.checkedItemPosition
            val pkg = when {
                checked in candidates.indices -> candidates[checked].packageName
                else -> MockLocationOps.getSelectedPackage(this)
            }
            if (pkg.isNullOrEmpty()) {
                Toast.makeText(this, R.string.settings_mock_select_first, Toast.LENGTH_SHORT).show()
                return@ensureShizukuThen
            }
            MockLocationOps.setSelectedPackage(this, pkg)
            setBusy(true)
            executor.execute {
                val result = runCatching { MockLocationOps.enable(this, pkg) }
                runOnUiThread {
                    setBusy(false)
                    result.fold(
                        onSuccess = {
                            Toast.makeText(this, R.string.settings_mock_enable_ok, Toast.LENGTH_SHORT)
                                .show()
                            refreshAll()
                        },
                        onFailure = { e ->
                            Toast.makeText(
                                this,
                                getString(R.string.settings_mock_error, e.message ?: e.toString()),
                                Toast.LENGTH_LONG
                            ).show()
                            refreshMockStatusOnly()
                        }
                    )
                }
            }
        }
    }

    private fun onDisableMockClicked() {
        ensureShizukuThen {
            setBusy(true)
            executor.execute {
                val result = runCatching { MockLocationOps.disable(this, scanOthers = true) }
                runOnUiThread {
                    setBusy(false)
                    result.fold(
                        onSuccess = {
                            Toast.makeText(this, R.string.settings_mock_disable_ok, Toast.LENGTH_SHORT)
                                .show()
                            refreshAll()
                        },
                        onFailure = { e ->
                            Toast.makeText(
                                this,
                                getString(R.string.settings_mock_error, e.message ?: e.toString()),
                                Toast.LENGTH_LONG
                            ).show()
                            refreshMockStatusOnly()
                        }
                    )
                }
            }
        }
    }

    private fun ensureShizukuThen(block: () -> Unit) {
        when (ShizukuAvailability.status(this)) {
            ShizukuAvailability.Status.READY -> block()
            ShizukuAvailability.Status.NO_PERMISSION -> {
                ShizukuGrantHelper.requestShizukuPermission()
            }
            ShizukuAvailability.Status.DEAD -> {
                val launch = packageManager.getLaunchIntentForPackage(ShizukuGrantHelper.SHIZUKU_PACKAGE)
                if (launch != null) startActivity(launch)
                else Toast.makeText(this, R.string.shizuku_hint_not_running, Toast.LENGTH_LONG).show()
            }
            ShizukuAvailability.Status.NOT_AVAILABLE -> {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        android.net.Uri.parse(ShizukuGrantHelper.SHIZUKU_DOWNLOAD_URL)
                    )
                )
            }
            ShizukuAvailability.Status.PRE_V11 -> {
                Toast.makeText(this, R.string.shizuku_hint_pre_v11, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        mockEnableButton.isEnabled = !busy
        mockDisableButton.isEnabled = !busy
        mockRefreshButton.isEnabled = !busy
        if (busy) {
            mockHintText.setText(R.string.settings_mock_working)
        }
    }
}
