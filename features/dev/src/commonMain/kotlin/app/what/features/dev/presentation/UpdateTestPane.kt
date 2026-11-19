package app.what.schedule.features.dev.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.what.domain.services.ReleaseHighlight
import app.what.domain.services.ReleaseNotes
import app.what.features.onboarding.presentation.MandatoryUpdateScreen
import app.what.foundation.services.auto_update.AppUpdateManager
import app.what.foundation.services.auto_update.DownloadState
import app.what.foundation.services.auto_update.UpdateInfo
import app.what.foundation.services.auto_update.UpdateResult
import app.what.foundation.ui.Gap
import app.what.foundation.ui.icons.WHATIcons
import app.what.foundation.ui.icons.filled.ApkInstall
import app.what.foundation.ui.icons.filled.Download
import app.what.foundation.ui.icons.filled.ReleaseAlert

private class MockAppUpdateManager(
    initialState: DownloadState = DownloadState.Idle
) : AppUpdateManager {
    override var updateInfo by mutableStateOf<UpdateInfo?>(null)
    override var downloadState by mutableStateOf(initialState)

    override suspend fun checkForUpdates(): UpdateResult = UpdateResult.UpToDate
    override fun handleAction() {}
    override fun cancelDownload() {}
    override fun release() {}
}

@Composable
fun UpdateTestPane(modifier: Modifier = Modifier) {
    var isRuStore by remember { mutableStateOf(false) }
    var selectedState by remember { mutableStateOf<String>("idle") }
    var showFullScreen by remember { mutableStateOf(false) }

    val downloadState = when (selectedState) {
        "preparing" -> DownloadState.Preparing
        "downloading_45" -> DownloadState.Downloading(45)
        "downloading_92" -> DownloadState.Downloading(92)
        "completed" -> DownloadState.Completed()
        "error" -> DownloadState.Error("Сервер временно недоступен (503 Service Unavailable)")
        else -> DownloadState.Idle
    }

    val mockManager = remember(downloadState) {
        MockAppUpdateManager(downloadState)
    }

    val mockInfo = remember {
        UpdateInfo(
            version = "1.6.0",
            fileSize = 48_234_496L,
            downloadUrl = "https://github.com/whatrushki/schedule/releases",
            releaseNotes = "Регулярное обновление с улучшением стабильности и исправлением ошибок.",
            releaseNotesData = ReleaseNotes(
                version = "1.6.0",
                title = "Критическое обновление расписания",
                shortDescription = "В этой версии полностью переработан механизм синхронизации с серверами вузов и устранена потеря пар.",
                highlights = listOf(
                    ReleaseHighlight(
                        tag = "FIX",
                        title = "Синхронизация РКСИ и РГЭУ РИНХ",
                        description = "Устранена коллизия дублирования преподавателей со схожими фамилиями. Добавлен надежный облачный фоллбэк при недоступности серверов."
                    ),
                    ReleaseHighlight(
                        tag = "NEW",
                        title = "Управляемые обязательные обновления",
                        description = "Критические патчи теперь доставляются мгновенно в реальном времени с автоматической установкой."
                    )
                ),
                changelog = listOf(
                    "РКСИ: Восстановлен поиск преподавателей с одинаковыми фамилиями",
                    "РИНХ: Исправлен сбой при сетевых таймаутах расписания",
                    "Веб: Устранена ошибка запуска Wasm-клиента",
                    "Десктоп: Оптимизировано энергопотребление и скролл"
                )
            )
        )
    }

    if (showFullScreen) {
        Box(modifier = Modifier.fillMaxSize()) {
            MandatoryUpdateScreen(
                info = mockInfo,
                manager = mockManager,
                isRuStore = isRuStore,
                onExitApp = { showFullScreen = false },
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Карточка быстрого запуска
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colorScheme.primaryContainer.copy(alpha = 0.4f))
                .border(1.dp, colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    WHATIcons.ReleaseAlert,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = "Тестирование экрана обязательного обновления",
                        style = typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = "Позволяет проверить верстку, адаптивность и состояния кнопок",
                        style = typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = { showFullScreen = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(WHATIcons.ApkInstall, contentDescription = null, modifier = Modifier.size(20.dp))
                Gap(8)
                Text("Запустить полноэкранный тест", fontWeight = FontWeight.Bold)
            }
        }

        // Выбор источника (GitHub vs RuStore)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "1. Источник дистрибуции:",
                style = typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !isRuStore,
                    onClick = { isRuStore = false },
                    label = { Text("GitHub (Standalone APK)") },
                    leadingIcon = { Icon(WHATIcons.Download, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = isRuStore,
                    onClick = { isRuStore = true },
                    label = { Text("RuStore (Магазин)") }
                )
            }
        }

        // Выбор состояния для GitHub
        if (!isRuStore) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "2. Состояние скачивания (для GitHub):",
                    style = typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedState == "idle",
                        onClick = { selectedState = "idle" },
                        label = { Text("Ожидание (Idle)") }
                    )
                    FilterChip(
                        selected = selectedState == "preparing",
                        onClick = { selectedState = "preparing" },
                        label = { Text("Подготовка") }
                    )
                    FilterChip(
                        selected = selectedState == "downloading_45",
                        onClick = { selectedState = "downloading_45" },
                        label = { Text("Загрузка (45%)") }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedState == "downloading_92",
                        onClick = { selectedState = "downloading_92" },
                        label = { Text("Загрузка (92%)") }
                    )
                    FilterChip(
                        selected = selectedState == "completed",
                        onClick = { selectedState = "completed" },
                        label = { Text("Скачан (Completed)") }
                    )
                    FilterChip(
                        selected = selectedState == "error",
                        onClick = { selectedState = "error" },
                        label = { Text("Ошибка") }
                    )
                }
            }
        }

        // Интерактивный предпросмотр внутри карточки
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "3. Интерактивный предпросмотр (в миниатюре):",
                style = typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            ) {
                MandatoryUpdateScreen(
                    info = mockInfo,
                    manager = mockManager,
                    isRuStore = isRuStore,
                    onExitApp = { /* No-op in inline preview */ },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
