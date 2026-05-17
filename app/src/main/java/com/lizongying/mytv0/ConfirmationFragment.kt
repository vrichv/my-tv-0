package com.lizongying.mytv0

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.DialogFragment

class ConfirmationFragment(
    private val listener: ConfirmationListener,
    private val message: String,
    private val update: Boolean
) : DialogFragment() {

    private val handler = Handler(Looper.getMainLooper())
    private var countdown = AUTO_DISMISS_SECONDS
    private var dismissed = false

    private val countdownRunnable = object : Runnable {
        override fun run() {
            if (dismissed) return
            countdown--
            if (countdown <= 0) {
                listener.onCancel()
                dismissAllowingStateLoss()
            } else {
                val dialog = dialog as? AlertDialog ?: return
                if (update) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.text = "确定 (${countdown}s)"
                }
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.text =
                    if (update) "取消 (${countdown}s)" else "确定 (${countdown}s)"
                handler.postDelayed(this, 1000L)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            builder.setTitle(message)
            if (update) {
                builder.setMessage("确定更新吗？")
                    .setPositiveButton("确定 (${countdown}s)") { _, _ ->
                        dismissed = true
                        handler.removeCallbacks(countdownRunnable)
                        listener.onConfirm()
                    }
                    .setNegativeButton("取消 (${countdown}s)") { _, _ ->
                        dismissed = true
                        handler.removeCallbacks(countdownRunnable)
                        listener.onCancel()
                    }
            } else {
                builder.setMessage("")
                    .setNegativeButton("确定 (${countdown}s)") { _, _ ->
                        dismissed = true
                        handler.removeCallbacks(countdownRunnable)
                    }
            }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    override fun onStart() {
        super.onStart()
        handler.postDelayed(countdownRunnable, 1000L)
    }

    override fun onStop() {
        super.onStop()
        dismissed = true
        handler.removeCallbacks(countdownRunnable)
    }

    interface ConfirmationListener {
        fun onConfirm()
        fun onCancel()
    }

    companion object {
        private const val AUTO_DISMISS_SECONDS = 10
    }
}