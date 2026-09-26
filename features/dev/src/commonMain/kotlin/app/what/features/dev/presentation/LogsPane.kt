package app.what.schedule.features.dev.presentation

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.what.foundation.services.AppLogger.Companion.Auditor
import app.what.foundation.services.LogEntry
import app.what.foundation.services.LogLevel
import app.what.foundation.ui.Gap
import app.what.foundation.ui.bclick
import app.what.foundation.ui.useState
import app.what.foundation.utils.ShareData
import app.what.foundation.utils.rememberShareManager
import app.what.schedule.features.dev.presentation.components.Filter
import app.what.schedule.features.dev.presentation.components.FilteredList
import app.what.schedule.ui.theme.icons.WHATIcons
import app.what.schedule.ui.theme.icons.filled.Warn
import app.what.foundation.utils.currentTimeMillis
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class LogFilter : Filter<LogEntry> {
    private val levelFilters = mutableListOf<LogLevel>()
    private val tagFilters = mutableListOf<String>()
    private val textFilters = mutableListOf<String>()
    
    private var hasErrorsOnly = false
    
    private var timeFilter: String? = null
    
    override fun clearFilters() {
        levelFilters.clear()
        tagFilters.clear()
        textFilters.clear()
        hasErrorsOnly = false
        timeFilter = null
    }
    
    override fun parseQuery(query: String) {
        clearFilters()
        if (query.isBlank()) return
        
        // Разбиваем строку запроса по пробелам
        val tokens = query.trim().split("\\s+".toRegex())
        
        tokens.forEach { token ->
            // Убираем кавычки, если пользователь их ввел по привычке ("text" -> text)
            val cleanToken = token.removeSurrounding("\"").removeSurrounding("'")
            
            when {
                // Фильтр по уровню: level:info или level:error
                cleanToken.startsWith("level:", ignoreCase = true) -> {
                    val levelName = cleanToken.substringAfter(":").uppercase()
                    // Ищем такой уровень в enum LogLevel
                    LogLevel.entries.find { it.name == levelName }?.let {
                        levelFilters.add(it)
                    }
                }
                
                // Фильтр по тегу: tag:Network
                cleanToken.startsWith("tag:", ignoreCase = true) -> {
                    val tagValue = cleanToken.substringAfter(":")
                    if (tagValue.isNotBlank()) tagFilters.add(tagValue)
                }
                
                // Спец. фильтры: is:error (наличие throwable)
                cleanToken.startsWith("is:", ignoreCase = true) -> {
                    val value = cleanToken.substringAfter(":").lowercase()
                    if (value == "error" || value == "crash") {
                        hasErrorsOnly = true
                    }
                }
                
                // Фильтр по времени: time:today
                cleanToken.startsWith("time:", ignoreCase = true) -> {
                    timeFilter = cleanToken.substringAfter(":").lowercase()
                }
                
                else -> {
                    if (cleanToken.isNotBlank()) {
                        textFilters.add(cleanToken)
                    }
                }
            }
        }
    }
    
    override fun matches(value: LogEntry): Boolean {
        // 1. Уровень лога (точное совпадение хотя бы с одним выбранным)
        if (levelFilters.isNotEmpty()) {
            if (value.level !in levelFilters) return false
        }
        
        // 2. Тег (частичное совпадение)
        if (tagFilters.isNotEmpty()) {
            val matchesTag = tagFilters.any { filterTag ->
                value.tag.contains(filterTag, ignoreCase = true)
            }
            if (!matchesTag) return false
        }
        
        // 3. Наличие ошибки (Throwable)
        if (hasErrorsOnly && value.throwable == null) {
            return false
        }
        
        // 4. Фильтр по времени
        if (timeFilter != null) {
            val now = currentTimeMillis()
            val logTime = value.timestamp
            
            val matchesTime = when (timeFilter) {
                "today" -> {
                    val dt = Instant.fromEpochMilliseconds(now).toLocalDateTime(TimeZone.currentSystemDefault())
                    val startOfDay = LocalDateTime(dt.year, dt.month, dt.dayOfMonth, 0, 0, 0, 0)
                        .toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
                    logTime >= startOfDay
                }
                
                "hour" -> (now - logTime) <= 3600000
                "5min" -> (now - logTime) <= 300000
                else -> true
            }
            if (!matchesTime) return false
        }
        
        if (textFilters.isNotEmpty()) {
            val matchesText = textFilters.any { filterText ->
                value.message.contains(filterText, ignoreCase = true) ||
                        value.tag.contains(filterText, ignoreCase = true)
            }
            if (!matchesText) return false
        }
        
        return true
    }
}

@Composable
fun LogsPane(
    modifier: Modifier = Modifier,
) {
    val logs by Auditor.collectLogs()
    val shareManager = rememberShareManager()

    FilteredList(
        title = "Логи приложения",
        values = logs,
        vKey = { it.id },
        vContent = { LogItem(it) },
        exportValues = {
            shareManager.share(
                ShareData.Text(
                    text = Auditor.exportLogs(),
                    title = "Логи приложения"
                )
            )
        },
        clearValues = Auditor::clearLogs,
        setIsMonitoringPaused = Auditor::setIsLoggingPaused,
        isMonitoringPaused = Auditor.isLoggingPaused,
        modifier = modifier,
        filter = LogFilter(),
        filterHelpItems = listOf(
            "level:error" to "Фильтр по уровню (debug, info, warning, error)",
            "tag:Main" to "Поиск по тегу (содержит текст)",
            "is:error" to "Только логи с исключениями (Exception/Throwable)",
            "time:5min" to "Логи за последние 5 минут",
            "time:hour" to "Логи за последний час",
            "time:today" to "Логи с начала дня",
            "login failed" to "Простой поиск текста в сообщении"
        )
    )
}


@Composable
fun LogItem(logEntry: LogEntry) {
    val (expanded, setExpanded) = useState(false)
    val levelColor = logEntry.level.color
    val isError = logEntry.level == LogLevel.ERROR || logEntry.level == LogLevel.CRITICAL
    val hasThrowable = logEntry.throwable != null
    
    val timeFormatted = remember(logEntry.timestamp) {
        val dt = Instant.fromEpochMilliseconds(logEntry.timestamp).toLocalDateTime(TimeZone.currentSystemDefault())
        val ms = (logEntry.timestamp % 1000).toString().padStart(3, '0')
        "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}:${dt.second.toString().padStart(2, '0')}.$ms"
    }
    
    Column(
        modifier = Modifier
            .animateContentSize()
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(shapes.small)
            .bclick { setExpanded(!expanded) }
            .background(
                if (isError) colorScheme.errorContainer.copy(alpha = 0.22f)
                else colorScheme.surfaceContainerLow,
                shapes.small
            )
            .border(
                1.dp,
                if (isError) colorScheme.error.copy(alpha = 0.35f)
                else colorScheme.outlineVariant.copy(alpha = 0.35f),
                shapes.small
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(78.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(levelColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            text = logEntry.level.emoji,
                            fontSize = 10.sp
                        )
                        Gap(3)
                        Text(
                            text = logEntry.level.name,
                            color = levelColor,
                            style = typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    }
                }
                
                Gap(8)
                
                Text(
                    text = logEntry.tag,
                    style = typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (hasThrowable) {
                    Gap(4)
                    Icon(
                        imageVector = WHATIcons.Warn,
                        contentDescription = "Contains error",
                        tint = colorScheme.error,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            
            Text(
                text = timeFormatted,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                style = typography.labelSmall,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        
        Gap(4)
        
        Text(
            text = logEntry.message,
            style = typography.bodySmall,
            color = colorScheme.onSurface,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis
        )
        
        if (expanded && hasThrowable) {
            Gap(6)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shapes.extraSmall)
                    .background(colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                    .border(0.5.dp, colorScheme.outlineVariant.copy(alpha = 0.4f), shapes.extraSmall)
                    .padding(8.dp)
            ) {
                logEntry.throwable?.message?.let { exMsg ->
                    if (exMsg.isNotBlank()) {
                        Text(
                            text = exMsg,
                            color = colorScheme.error,
                            style = typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                        Gap(4)
                    }
                }
                Text(
                    text = logEntry.throwable?.stackTraceToString() ?: "",
                    color = colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 13.sp
                )
            }
        }
    }
}