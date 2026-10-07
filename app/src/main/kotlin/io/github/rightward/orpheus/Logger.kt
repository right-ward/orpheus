package io.github.rightward.orpheus

interface OrpheusLogger {
    fun debug(message: String)
    fun info(message: String)
    fun warn(message: String)
    fun error(message: String)
}
