package com.evolearn.kidszone.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.evolearn.kidszone.R
import com.evolearn.kidszone.core.PinLock

/** 家长PIN验证弹窗：验证失败不关闭、清空重输（框架AlertDialog，兼容Leanback/Holo主题） */
class PinDialog(private val message: String, private val onVerified: () -> Unit) :
    DialogFragment() {

    companion object {
        fun show(fm: FragmentManager, message: String, onVerified: () -> Unit) {
            PinDialog(message, onVerified).show(fm, "pin_dialog")
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_pin, null)
        view.findViewById<TextView>(R.id.pin_title).text = message
        val input = view.findViewById<EditText>(R.id.pin_input)
        val err = view.findViewById<TextView>(R.id.pin_error)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (PinLock.verify(input.text.toString())) {
                    dismiss()
                    onVerified()
                } else {
                    err.visibility = TextView.VISIBLE
                    input.setText("")
                }
            }
        }
        return dialog
    }
}
