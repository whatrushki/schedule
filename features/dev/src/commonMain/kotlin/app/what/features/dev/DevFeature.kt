package app.what.schedule.features.dev

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import app.what.navigation.core.LocalNavController
import app.what.foundation.ui.Gap
import app.what.foundation.ui.SegmentTab
import app.what.schedule.features.dev.presentation.LogsPane
import app.what.schedule.features.dev.presentation.NetworksPane
import app.what.foundation.ui.icons.WHATIcons
import app.what.foundation.ui.icons.filled.Features
import app.what.foundation.ui.icons.filled.Logs
import app.what.foundation.ui.icons.filled.Network

import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import app.what.schedule.features.dev.presentation.HealthCheckPane
import app.what.schedule.features.dev.presentation.SessionTrackerPane

enum class DevToolsTab(
    val title: String, val icon: ImageVector
) {
    LOGS("Логи", WHATIcons.Logs),
    NETWORK("Сеть", WHATIcons.Network),
    HEALTH("Тесты", Icons.Default.CheckCircle),
    SESSION("Сессии", Icons.Default.History);
    
    companion object {
        fun all() = listOf(LOGS, NETWORK, HEALTH, SESSION)
    }
}

@Composable
fun DevFeature(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) = Column(
    modifier = modifier.fillMaxSize().statusBarsPadding()
) {
    val devToolsTabs = DevToolsTab.all()
    val pagerState = rememberPagerState { devToolsTabs.size }
    val scope = rememberCoroutineScope()
    
    val navigator = LocalNavController.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        IconButton(onClick = {
            if (onBack != null) {
                onBack()
            } else {
                navigator?.c?.popBackStack()
            }
        }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Назад",
                tint = colorScheme.onSurface
            )
        }
        Gap(8)
        Text(
            text = "Панель разработчика",
            style = typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurface
        )
    }

    Gap(4)
    
    SingleChoiceSegmentedButtonRow(
        space = (-4).dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        devToolsTabs.forEachIndexed { index, it ->
            val selected = pagerState.currentPage == index
            
            SegmentTab(
                selected = selected,
                index = index,
                count = devToolsTabs.size,
                icon = it.icon,
                label = null
            ) {
                scope.launch {
                    pagerState.animateScrollToPage(index)
                }
            }
        }
    }
    
    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
    ) { page ->
        when (devToolsTabs.getOrNull(page) ?: DevToolsTab.LOGS) {
            DevToolsTab.LOGS -> LogsPane(Modifier.fillMaxSize())
            DevToolsTab.NETWORK -> NetworksPane(Modifier.fillMaxSize())
            DevToolsTab.HEALTH -> HealthCheckPane(Modifier.fillMaxSize())
            DevToolsTab.SESSION -> SessionTrackerPane(Modifier.fillMaxSize())
        }
    }
}
