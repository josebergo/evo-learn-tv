package com.evolearn.kidszone.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.evolearn.kidszone.R
import com.evolearn.kidszone.core.PinLock

/** 首次启动向导：家长设置4位PIN（孩子无法进入设置/退出应用） */
class SetupActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.hideBars(this)
        setContentView(R.layout.activity_setup)

        val pin = findViewById<EditText>(R.id.setup_pin)
        val pin2 = findViewById<EditText>(R.id.setup_pin2)
        val error = findViewById<TextView>(R.id.setup_error)

        findViewById<Button>(R.id.setup_save).setOnClickListener {
            val a = pin.text.toString()
            val b = pin2.text.toString()
            if (a.length == 4 && a.all { it.isDigit() } && a == b) {
                PinLock.setup(a)
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                error.visibility = TextView.VISIBLE
            }
        }
    }
}
