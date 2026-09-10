package app.what.foundation.services

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import app.what.foundation.utils.currentTimeMillis

enum class LogLevel(val emoji: String, val color: Color) {
    DEBUG("🐛", Color(0xFF4CAF50)),    // Зеленый
    INFO("🧢", Color(0xFF2196F3)),     // Синий
    WARNING("🍣", Color(0xFFFF9800)),  // Оранжевый
    ERROR("🌶️", Color(0xFFF44336)),    // Красный
    CRITICAL("🪻", Color(0xFF9C27B0))  // Фиолетовый
}

data class LogEntry(
    val id: Long,
    val timestamp: Long = currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null
) {
    fun toFormattedString(): String {
        return "[${level.emoji}] [$timestamp] [$tag] $message " +
                (throwable?.let { "\n${it.stackTraceToString()}" } ?: "")
    }
}

open class AppLogger(
    val logFilePath: String? = null
) {

    companion object {
        private const val MAX_MEMORY_LOGS = 500
        var instance: AppLogger = AppLogger()

        val Auditor: AppLogger
            get() = instance

        fun initialize(logger: AppLogger) {
            instance = logger
        }
    }

    private var atomicId = 0L

    var isLoggingPaused by mutableStateOf(false)
        private set

    private val _logFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs = _logFlow.asStateFlow()

    @Composable
    fun collectLogs() = logs.collectAsState()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun setIsLoggingPaused(value: Boolean) { isLoggingPaused = value }

    open fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(
            id = ++atomicId,
            level = level,
            tag = tag,
            message = message,
            throwable = throwable
        )

        println("[${level.emoji}] [$tag] $message" + (throwable?.let { " - ${it.message}" } ?: ""))

        if (!isLoggingPaused) {
            _logFlow.update { current ->
                (current + entry).takeLast(MAX_MEMORY_LOGS)
            }
        }
    }

    suspend fun readLogsSafe(): List<LogEntry> = _logFlow.value

    fun clearLogs() {
        _logFlow.value = emptyList()
    }

    fun exportLogs(): String {
        return _logFlow.value.joinToString("\n") { it.toFormattedString() }
    }

    fun debug(tag: String, msg: String) = log(LogLevel.DEBUG, tag, msg)
    fun info(tag: String, msg: String) = log(LogLevel.INFO, tag, msg)
    fun warn(tag: String, msg: String) = log(LogLevel.WARNING, tag, msg)
    fun err(tag: String, msg: String, t: Throwable? = null) = log(LogLevel.ERROR, tag, msg, t)
    fun critic(tag: String, msg: String, t: Throwable? = null) = log(LogLevel.CRITICAL, tag, msg, t)
}