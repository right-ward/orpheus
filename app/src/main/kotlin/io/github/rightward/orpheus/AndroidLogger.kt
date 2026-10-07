package io.github.rightward.orpheus

import android.util.Log

class AndroidLogger(
    private val tag: String = "Orpheus"
) : OrpheusLogger {
    override fun debug(message: String) {
        Log.d(tag, message)
    }

    override fun info(message: String) {
        Log.i(tag, message)
    }

    override fun warn(message: String) {
        Log.w(tag, message)
    }

    override fun error(message: String) {
        Log.e(tag, message)
    }
}
