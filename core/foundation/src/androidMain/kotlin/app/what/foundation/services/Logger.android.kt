package app.what.foundation.services

import android.content.Context
import android.util.Log
import java.io.File

class AndroidAppLogger(context: Context) : AppLogger(
    logFilePath = "${context.applicationContext.filesDir.absolutePath}/audit_logs.txt"
) {
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        super.log(level, tag, message, throwable)
        val priority = when (level) {
            LogLevel.DEBUG -> Log.DEBUG
            LogLevel.INFO -> Log.INFO
            LogLevel.WARNING -> Log.WARN
            else -> Log.ERROR
        }
        Log.println(priority, tag, "$message " + (throwable?.let { "\n${it.stackTraceToString()}" } ?: ""))
    }
}

val AppLogger.logFile: File
    get() = File(logFilePath ?: "")

fun AppLogger.Companion.initialize(context: Context) {
    initialize(AndroidAppLogger(context))
}
