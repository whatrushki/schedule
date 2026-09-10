package app.what.foundation.utils

import app.what.foundation.services.AppLogger.Companion.Auditor

interface CrashReporter {
    fun recordException(throwable: Throwable) {}
    fun setCustomKey(key: String, value: String) {}
    fun setUserId(userId: String) {}
}

class ConsoleCrashReporter : CrashReporter {
    override fun recordException(throwable: Throwable) {
        Auditor.err("CrashReporter", "Exception: ${throwable.message}", throwable)
    }

    override fun setCustomKey(key: String, value: String) {
        Auditor.debug("CrashReporter", "CustomKey: $key = $value")
    }

    override fun setUserId(userId: String) {
        Auditor.debug("CrashReporter", "UserId: $userId")
    }
}

object AppCrashReporter : CrashReporter {
    var reporter: CrashReporter = ConsoleCrashReporter()

    override fun recordException(throwable: Throwable) = reporter.recordException(throwable)
    override fun setCustomKey(key: String, value: String) = reporter.setCustomKey(key, value)
    override fun setUserId(userId: String) = reporter.setUserId(userId)
}
