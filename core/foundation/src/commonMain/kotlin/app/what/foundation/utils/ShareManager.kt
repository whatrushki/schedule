package app.what.foundation.utils

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.what.foundation.ui.Gap
import app.what.foundation.ui.components.ShareButton
import app.what.schedule.ui.theme.icons.WHATIcons
import app.what.schedule.ui.theme.icons.filled.Copy
import app.what.schedule.ui.theme.icons.filled.Telegram
import app.what.schedule.ui.theme.icons.filled.VK
import app.what.schedule.ui.theme.icons.filled.Whatsapp

sealed interface ShareData {
    val title: String?

    data class Text(
        val text: String,
        override val title: String? = null
    ) : ShareData

    data class Files(
        val uris: List<Any>,
        val mimeType: String = "*/*",
        override val title: String? = null,
        val text: String? = null
    ) : ShareData
}

enum class ShareChannel {
    Telegram,
    VK,
    WhatsApp,
    Clipboard,
    SystemDefault;

    companion object {
        val defaultChannels: List<ShareChannel> = listOf(
            SystemDefault,
            Telegram,
            VK,
            WhatsApp,
            Clipboard
        )
    }
}

class ShareManager(val context: Any?) {
    fun share(data: ShareData, channel: ShareChannel = ShareChannel.SystemDefault) {
        executePlatformShare(channel, data, context)
    }
}

@Composable
expect fun rememberShareManager(): ShareManager

@Composable
expect fun rememberPlatformContext(): Any?

expect fun executePlatformShare(channel: ShareChannel, data: ShareData, context: Any?)

fun executeShare(context: Any?, channel: ShareChannel, data: ShareData) {
    executePlatformShare(channel, data, context)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> CustomSharePane(
    title: String,
    items: List<T>,
    initialSelection: List<T>,
    channels: List<ShareChannel>,
    itemLabel: (T) -> String,
    onShare: (ShareChannel, List<T>) -> Unit
) {
    val selectedItems = remember { mutableStateListOf<T>().apply { addAll(initialSelection) } }

    Column(
        Modifier
            .padding(16.dp)
            .navigationBarsPadding()
    ) {
        Text(
            title,
            style = typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = colorScheme.primary
        )

        Gap(16)

        Text("Выберите элементы:", style = typography.labelLarge)
        Gap(8)

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items.forEach { item ->
                val selected = item in selectedItems
                FilterChip(
                    selected = selected,
                    onClick = {
                        if (selected) {
                            if (selectedItems.size > 1) selectedItems.remove(item)
                        } else {
                            selectedItems.add(item)
                        }
                    },
                    label = { Text(itemLabel(item)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colorScheme.primary,
                        selectedLabelColor = colorScheme.onPrimary
                    )
                )
            }
        }

        Gap(24)

        Text("Куда отправить:", style = typography.labelLarge)
        Gap(12)

        ShareChannelsRow(channels = channels) { channel ->
            onShare(channel, selectedItems.toList())
        }

        Gap(12)
    }
}

@Composable
fun ShareChannelsRow(
    channels: List<ShareChannel>,
    onChannelClick: (ShareChannel) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        channels.forEach { channel ->
            when (channel) {
                ShareChannel.SystemDefault -> {
                    ShareButton(
                        icon = Icons.Default.MoreVert,
                        color = colorScheme.secondaryContainer,
                        background = colorScheme.onSecondaryContainer,
                        iconSize = 34,
                        onClick = { onChannelClick(channel) }
                    )
                }
                ShareChannel.Telegram -> {
                    ShareButton(
                        icon = WHATIcons.Telegram,
                        color = Color(0xFF2AABEE),
                        background = Color.White,
                        iconSize = 46,
                        onClick = { onChannelClick(channel) }
                    )
                }
                ShareChannel.VK -> {
                    ShareButton(
                        icon = WHATIcons.VK,
                        color = Color(0xFF2196F3),
                        background = Color.White,
                        iconSize = 34,
                        onClick = { onChannelClick(channel) }
                    )
                }
                ShareChannel.WhatsApp -> {
                    ShareButton(
                        icon = WHATIcons.Whatsapp,
                        color = Color(0xFF4CAF50),
                        background = Color.White,
                        iconSize = 34,
                        onClick = { onChannelClick(channel) }
                    )
                }
                ShareChannel.Clipboard -> {
                    ShareButton(
                        icon = WHATIcons.Copy,
                        color = colorScheme.secondaryContainer,
                        background = colorScheme.onSecondaryContainer,
                        iconSize = 30,
                        onClick = { onChannelClick(channel) }
                    )
                }
            }
        }
    }
}
