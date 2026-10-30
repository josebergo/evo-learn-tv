package com.evolearn.kidszone.ui

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.evolearn.kidszone.R

/** 通用贴纸输入弹窗：白色大卡 + 蜡笔描边 + 芒果确定键，取代系统 Holo AlertDialog。
 *  onOk 返回 true 关闭弹窗，false 保持打开（可先 setError 提示）。 */
class InputDialogFragment(
    private val title: String,
    private val hint: String,
    private val initial: String,
    private val isPin: Boolean,
    private val onOk: (InputDialogFragment, String) -> Boolean
) : DialogFragment() {

    private var input: EditText? = null
    private var err: TextView? = null

    fun setError(text: String) {
        err?.text = text
        err?.visibility = TextView.VISIBLE
    }

    companion object {
        fun show(
            fm: FragmentManager, title: String, hint: String,
            initial: String = "", isPin: Boolean = false,
            onOk: (InputDialogFragment, String) -> Boolean
        ) {
            InputDialogFragment(title, hint, initial, isPin, onOk).show(fm, "input_dialog")
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_input, null)
        val titleView = view.findViewById<TextView>(R.id.dialog_title)
        input = view.findViewById(R.id.dialog_input)
        err = view.findViewById(R.id.dialog_error)
        val ok = view.findViewById<Button>(R.id.dialog_ok)
        val cancel = view.findViewById<Button>(R.id.dialog_cancel)

        titleView.text = title
        input?.apply {
            hint = this@InputDialogFragment.hint
            setText(initial)
            if (isPin) {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
                filters = arrayOf(InputFilter.LengthFilter(4))
            }
        }
        cancel.setOnClickListener { dismiss() }
        ok.setOnClickListener {
            if (onOk(this, input?.text.toString().trim().orEmpty())) dismiss()
        }
        Ui.applyFont(view)

        val dialog = Dialog(requireContext())
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return dialog
    }
}
