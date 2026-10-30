package com.evolearn.kidszone.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.evolearn.kidszone.BuildConfig
import com.evolearn.kidszone.R
import com.evolearn.kidszone.core.ConfigRepo
import com.evolearn.kidszone.core.Prefs
import kotlinx.coroutines.launch

/** 家长设置：登录 / 配置地址 / 退出 */
class SettingsActivity : FragmentActivity() {

    private val qrLoginLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.hideBars(this)
        setContentView(R.layout.activity_settings)
        Ui.applyFont(findViewById(android.R.id.content))
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
        findViewById<Button>(R.id.btn_toggle_live_subtitle).setOnClickListener {
            Prefs.liveSubtitleEnabled = !Prefs.liveSubtitleEnabled
            refreshStatus()
        }
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
        findViewById<TextView>(R.id.setting_live_subtitle_status).text =
            getString(if (Prefs.liveSubtitleEnabled) R.string.settings_live_subtitle_state_on else R.string.settings_live_subtitle_state_off) +
                "\n" + getString(if (Prefs.liveSubtitleEnabled) R.string.settings_live_subtitle_hint_on else R.string.settings_live_subtitle_hint_off)
        findViewById<Button>(R.id.btn_toggle_live_subtitle).setText(
            if (Prefs.liveSubtitleEnabled) R.string.settings_live_subtitle_turn_off else R.string.settings_live_subtitle_turn_on
        )
    }

    /** 应用内配置的地址：保存后立即拉取并生效（第1优先级来源） */
    private fun showEditUrlDialog() {
        InputDialogFragment.show(
            supportFragmentManager,
            getString(R.string.settings_edit_config_url),
            getString(R.string.settings_input_url),
            initial = Prefs.customConfigUrl
        ) { _, url ->
            if (url.startsWith("http://") || url.startsWith("https://")) {
                Prefs.customConfigUrl = url
                reloadConfig()
                true
            } else {
                Ui.toast(this, getString(R.string.reload_fail))
                false
            }
        }
    }

    private fun reloadConfig() {
        lifecycleScope.launch {
            val cfg = ConfigRepo.load(this@SettingsActivity, forceRefresh = true)
            if (cfg.categories.isNotEmpty()) {
                Ui.toast(this@SettingsActivity, getString(R.string.reload_done, cfg.categories.size, ConfigRepo.totalItems(cfg)), long = true)
            } else {
                Ui.toast(this@SettingsActivity, getString(R.string.reload_fail), long = true)
            }
            refreshStatus()
        }
    }
}
