package app.what.features.settings.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.what.data.managers.CacheCategory
import app.what.data.managers.CacheCategoryType
import app.what.data.managers.CacheStorageManager
import app.what.data.network.traffic.NetworkTrafficManager
import app.what.data.network.traffic.TrafficCategory
import app.what.features.settings.presentation.utils.rememberThemePalette
import app.what.foundation.ui.Gap
import app.what.foundation.ui.SegmentTab
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@Composable
fun UsageStatsDialog(
    initialPage: Int = 0,
    onClose: () -> Unit = {}
) {
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, 1)) { 2 }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Шапка со стрелкой назад слева
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Память и сеть",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Таб-переключатель из foundation
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            SegmentTab(
                selected = pagerState.currentPage == 0,
                index = 0,
                count = 2,
                label = "Память",
                icon = null,
                onClick = {
                    scope.launch { pagerState.animateScrollToPage(0) }
                }
            )
            Gap(8)
            SegmentTab(
                selected = pagerState.currentPage == 1,
                index = 1,
                count = 2,
                label = "Сеть",
                icon = null,
                onClick = {
                    scope.launch { pagerState.animateScrollToPage(1) }
                }
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            when (page) {
                0 -> StorageTabPane()
                1 -> NetworkTabPane()
            }
        }
    }
}

@Composable
fun UsageStatsSheet(
    initialPage: Int = 0,
    onClose: () -> Unit = {}
) {
    UsageStatsDialog(initialPage = initialPage, onClose = onClose)
}

@Composable
private fun StorageTabPane() {
    val cacheManager = koinInject<CacheStorageManager>()
    val scope = rememberCoroutineScope()

    var categories by remember { mutableStateOf<List<CacheCategory>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isClearing by remember { mutableStateOf(false) }

    val selectedTypes = remember { mutableStateMapOf<CacheCategoryType, Boolean>() }
    val palette = rememberThemePalette(5)

    LaunchedEffect(Unit) {
        val loaded = cacheManager.getCategories()
        categories = loaded
        loaded.forEach { cat ->
            selectedTypes[cat.type] = cat.sizeBytes > 0
        }
        isLoading = false
    }

    val selectedCategories = categories.filter { selectedTypes[it.type] == true && it.sizeBytes > 0 }
    val totalSelectedBytes = selectedCategories.sumOf { it.sizeBytes }
    val (sizeValueText, sizeUnitText) = formatSizeParts(totalSelectedBytes)

    val segments = categories.mapIndexed { index, cat ->
        val color = palette.getOrElse(index) { MaterialTheme.colorScheme.primary }
        val isSelected = selectedTypes[cat.type] == true
        DonutSegment(
            bytes = if (isSelected) cat.sizeBytes else 0L,
            color = color
        )
    }

    val categoryItems = categories.mapIndexed { index, category ->
        val color = palette.getOrElse(index) { MaterialTheme.colorScheme.primary }
        val isSelected = selectedTypes[category.type] == true
        val percent = if (totalSelectedBytes > 0 && isSelected) {
            ((category.sizeBytes.toFloat() / totalSelectedBytes) * 100f).roundToInt()
        } else 0

        UsageCategoryItem(
            id = category.type,
            title = category.title,
            sizeBytes = category.sizeBytes,
            color = color,
            isSelected = isSelected,
            percent = percent,
            showCheckbox = true,
            onToggle = { selectedTypes[category.type] = !isSelected }
        )
    }

    UsageTabPane(
        title = "Использование памяти",
        segments = segments,
        totalBytes = totalSelectedBytes,
        sizeValueText = sizeValueText,
        sizeUnitText = sizeUnitText,
        isLoading = isLoading,
        categories = categoryItems,
        actionButton = {
            Button(
                onClick = {
                    if (totalSelectedBytes > 0 && !isClearing) {
                        isClearing = true
                        scope.launch {
                            val toClear = selectedTypes.filter { it.value }.keys.toSet()
                            val updated = cacheManager.clearCategories(toClear)
                            categories = updated
                            updated.forEach { cat ->
                                selectedTypes[cat.type] = cat.sizeBytes > 0
                            }
                            isClearing = false
                        }
                    }
                },
                enabled = totalSelectedBytes > 0 && !isClearing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                if (isClearing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    Text(
                        text = if (totalSelectedBytes > 0) "Очистить кэш $sizeValueText $sizeUnitText" else "Кэш пуст",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    )
}

@Composable
private fun NetworkTabPane() {
    val trafficManager = koinInject<NetworkTrafficManager>()
    val stats by trafficManager.stats.collectAsState()

    val selectedCategories = remember { mutableStateMapOf<TrafficCategory, Boolean>() }
    LaunchedEffect(stats) {
        TrafficCategory.entries.forEach { cat ->
            if (!selectedCategories.containsKey(cat)) {
                selectedCategories[cat] = stats.getBucketFor(cat, null).totalBytes > 0
            }
        }
    }

    val selectedEntries = TrafficCategory.entries.filter { selectedCategories[it] == true }
    val totalSelectedBytes = selectedEntries.sumOf { stats.getBucketFor(it, null).totalBytes }
    val (sizeValueText, sizeUnitText) = formatSizeParts(totalSelectedBytes)

    val palette = rememberThemePalette(TrafficCategory.entries.size)

    val segments = TrafficCategory.entries.mapIndexed { index, category ->
        val bucket = stats.getBucketFor(category, null)
        val color = palette.getOrElse(index) { MaterialTheme.colorScheme.primary }
        val isSelected = selectedCategories[category] == true
        DonutSegment(
            bytes = if (isSelected) bucket.totalBytes else 0L,
            color = color
        )
    }

    val categoryItems = TrafficCategory.entries.mapIndexed { index, category ->
        val bucket = stats.getBucketFor(category, null)
        val color = palette.getOrElse(index) { MaterialTheme.colorScheme.primary }
        val isSelected = selectedCategories[category] == true
        val percent = if (totalSelectedBytes > 0 && isSelected) {
            ((bucket.totalBytes.toFloat() / totalSelectedBytes) * 100f).roundToInt()
        } else 0

        UsageCategoryItem(
            id = category,
            title = category.displayName,
            sizeBytes = bucket.totalBytes,
            color = color,
            isSelected = isSelected,
            percent = percent,
            showCheckbox = true,
            onToggle = { selectedCategories[category] = !isSelected }
        )
    }

    val toReset = selectedCategories.filter { it.value }.keys.toSet()
    val canReset = toReset.isNotEmpty() && totalSelectedBytes > 0

    UsageTabPane(
        title = "Использование сети",
        segments = segments,
        totalBytes = totalSelectedBytes,
        sizeValueText = sizeValueText,
        sizeUnitText = sizeUnitText,
        isLoading = false,
        categories = categoryItems,
        actionButton = {
            Button(
                onClick = {
                    trafficManager.resetStats(toReset)
                    toReset.forEach { selectedCategories[it] = false }
                },
                enabled = canReset,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(
                    text = if (totalSelectedBytes > 0) "Сбросить статистику ($sizeValueText $sizeUnitText)" else "Статистика пуста",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    )
}

@Composable
private fun UsageTabPane(
    title: String,
    segments: List<DonutSegment>,
    totalBytes: Long,
    sizeValueText: String,
    sizeUnitText: String,
    categories: List<UsageCategoryItem>,
    isLoading: Boolean = false,
    actionButton: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                UsageDonutChart(
                    segments = segments,
                    totalBytes = totalBytes,
                    sizeValue = sizeValueText,
                    sizeUnit = sizeUnitText
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Горизонтальный разделитель
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Единый переиспользуемый компонент списка категорий
            UsageCategoriesList(categories = categories)

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Кнопка действия, всегда прижатая к самому низу
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            actionButton()
        }
    }
}

data class UsageCategoryItem(
    val id: Any,
    val title: String,
    val sizeBytes: Long,
    val color: Color,
    val isSelected: Boolean,
    val percent: Int,
    val showCheckbox: Boolean = false,
    val onToggle: (() -> Unit)? = null
)

@Composable
private fun UsageCategoriesList(
    categories: List<UsageCategoryItem>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        categories.forEach { item ->
            UsageCategoryRow(item = item)
        }
    }
}

@Composable
private fun UsageCategoryRow(
    item: UsageCategoryItem,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (item.showCheckbox && item.onToggle != null) Modifier.clickable { item.onToggle.invoke() }
                else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.showCheckbox) {
            // Круглый чекбокс в стиле Telegram с цветом из темы
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (item.isSelected) item.color else Color.Transparent)
                    .then(
                        if (!item.isSelected) Modifier.background(Color.Gray.copy(alpha = 0.2f), CircleShape)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (item.isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                }
            }
        } else {
            // Круглый маркер цвета категории, идентичный чекбоксу по размеру и геометрии
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (item.isSelected) item.color else item.color.copy(alpha = 0.25f))
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (item.percent > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${item.percent}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = formatSize(item.sizeBytes),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatSizeParts(bytes: Long): Pair<String, String> {
    if (bytes <= 0) return "0" to "Б"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0

    return when {
        gb >= 1.0 -> {
            val rounded = ((gb * 10).roundToInt() / 10.0).toString().replace('.', ',')
            rounded to "GB"
        }
        mb >= 1.0 -> {
            val rounded = ((mb * 10).roundToInt() / 10.0).toString().replace('.', ',')
            rounded to "MB"
        }
        kb >= 1.0 -> {
            val rounded = ((kb * 10).roundToInt() / 10.0).toString().replace('.', ',')
            rounded to "KB"
        }
        else -> bytes.toString() to "Б"
    }
}

private fun formatSize(bytes: Long): String {
    val (value, unit) = formatSizeParts(bytes)
    return "$value $unit"
}

