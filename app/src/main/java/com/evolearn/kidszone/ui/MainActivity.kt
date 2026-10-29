package com.evolearn.kidszone.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.evolearn.kidszone.R
import com.evolearn.kidszone.core.ConfigRepo
import com.evolearn.kidszone.core.PinLock
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.hideBars(this)

        // 首次启动：家长设置PIN
        if (!PinLock.isSet()) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_container, MainFragment())
                .commit()
        }

        // 孩子按返回键不能直接退出：需家长PIN验证
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                PinDialog.show(supportFragmentManager, "家长验证后退出应用") {
                    finishAffinity()
                }
            }
        })

        // 后台静默刷新片单（网络可用时自动更新到最新版本）
        lifecycleScope.launch { ConfigRepo.load(this@MainActivity) }
    }
}
