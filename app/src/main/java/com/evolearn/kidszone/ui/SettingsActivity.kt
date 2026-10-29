package com.evolearn.kidszone.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import android.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.evolearn.kidszone.BuildConfig
import com.evolearn.kidszone.R
import com.evolearn.kidszone.core.ConfigRepo
import com.evolearn.kidszone.core.Prefs
import com.evolearn.kidszone.core.PinLock
import kotlinx.coroutines.launch

/** 家长设置（已过PIN门禁）：登录 / 配置地址 / PIN / 退出 */
class SettingsActivity : FragmentActivity() {

    private val qrLoginLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.hideBars(this)
        setContentView(R.layout.activity_settings)
        refreshStatus()

        findViewById<Button>(R.id.btn_qr_login).setOnClickListener {
            qrLoginLauncher.launch(QrLoginActivity.intent(this))
        }
        findViewById<Button>(R.id.btn_logout).setOnClickListener {
            Prefs.clearLogin()
            refreshStatus()
        }
        findViewById<Button>(R.id.btn_edit_config_url).setOnClickListener { showEditUrlDialog() }
        findViewById<Button>(R.id.btn_reset_config).setOnClickListener {
            Prefs.customConfigUrl = ""
            reloadConfig()
        }
        findViewById<Button>(R.id.btn_reload_config).setOnClickListener { reloadConfig() }
        findViewById<Button>(R.id.btn_change_pin).setOnClickListener { showChangePinDialog() }
        findViewById<Button>(R.id.btn_exit_app).setOnClickListener { finishAffinity() }
    }

    private fun refreshStatus() {
        val account = if (Prefs.isLoggedIn()) {
            "已登录：${Prefs.loginName}"
        } else {
            getString(R.string.settings_not_login)
        }
        val effectiveUrl = Prefs.customConfigUrl.ifBlank {
            if (BuildConfig.DEFAULT_CONFIG_URL.contains("yourname")) {
                "未部署（使用离线内置片单）"
            } else {
                BuildConfig.DEFAULT_CONFIG_URL
            }
        }
        val config = getString(R.string.settings_config_source, Prefs.configSourceLabel, Prefs.configVersion) +
            "\n" + getString(R.string.settings_config_url_current, effectiveUrl)
        findViewById<TextView>(R.id.setting_account_status).text = account
        findViewById<TextView>(R.id.setting_config_status).text = config
    }

    /** 应用内配置的地址：保存后立即拉取并生效（第1优先级来源） */
    private fun showEditUrlDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.settings_input_url)
            setText(Prefs.customConfigUrl)
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_edit_config_url)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val url = input.text.toString().trim()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    Prefs.customConfigUrl = url
                    reloadConfig()
                } else {
                    Toast.makeText(this, R.string.reload_fail, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showChangePinDialog() {
        val old = EditText(this).apply {
            hint = getString(R.string.settings_input_pin_old)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.settings_change_pin)
            .setView(old)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (PinLock.verify(old.text.toString())) {
                    dialog.dismiss()
                    showNewPinDialog()
                } else {
                    old.setText("")
                    old.hint = getString(R.string.pin_error)
                }
            }
        }
        dialog.show()
    }

    private fun showNewPinDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.settings_input_pin_new)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.settings_change_pin)
            .setView(input)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pin = input.text.toString()
                if (pin.length == 4 && pin.all { it.isDigit() }) {
                    PinLock.setup(pin)
                    dialog.dismiss()
                    Toast.makeText(this, R.string.pin_ok, Toast.LENGTH_SHORT).show()
                } else {
                    input.setText("")
                }
            }
        }
        dialog.show()
    }

    private fun reloadConfig() {
        lifecycleScope.launch {
            val cfg = ConfigRepo.load(this@SettingsActivity, forceRefresh = true)
            if (cfg.categories.isNotEmpty()) {
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.reload_done, cfg.categories.size, ConfigRepo.totalItems(cfg)),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(this@SettingsActivity, R.string.reload_fail, Toast.LENGTH_LONG).show()
            }
            refreshStatus()
        }
    }
}
