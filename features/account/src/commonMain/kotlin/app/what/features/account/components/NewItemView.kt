package app.what.features.account.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.what.domain.models.NewListItem
import app.what.foundation.ui.bclick
import app.what.foundation.utils.DateTimeUtils
import app.what.schedule.ui.components.AsyncImageWithFallback

@Composable
fun NewItemView(
    data: NewListItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) = Box(
    modifier
        .width(230.dp)
        .clip(shapes.large)
        .background(colorScheme.surfaceContainer)
        .bclick(block = onClick)
) {
    data.tags.firstOrNull()?.let { tag ->
        FilterChip(
            selected = true,
            onClick = {},
            label = { Text(tag.name) },
            modifier = Modifier
                .zIndex(2f)
                .padding(top = 8.dp, start = 8.dp)
        )
    }
    
    Column {
        AsyncImageWithFallback(
            data.bannerUrl,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        )
        
        Column(
            Modifier.padding(12.dp)
        ) {
            Text(
                DateTimeUtils.formatDate(data.timestamp),
                fontSize = 14.sp,
                color = colorScheme.onSurfaceVariant
            )
            
            Text(
                data.title,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                minLines = 2,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                color = colorScheme.onSurface
            )
        }
    }
}
