package app.what.features.account.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.what.foundation.ui.Gap
import app.what.foundation.ui.bclick

@Composable
fun SelectField(
    label: String,
    selectedValueText: String,
    options: List<Pair<String, String>>, // id to title
    selectedId: String?,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    var dialogOpen by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shapes.medium)
            .bclick { dialogOpen = true }
    ) {
        TextField(
            value = selectedValueText.ifBlank { "Выберите..." },
            onValueChange = {},
            readOnly = true,
            enabled = false,
            singleLine = true,
            maxLines = 1,
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant
                )
            },
            colors = TextFieldDefaults.colors(
                disabledTextColor = colorScheme.onSurface,
                disabledContainerColor = colorScheme.surfaceContainerHigh,
                disabledIndicatorColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledLabelColor = colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = shapes.medium
        )
    }

    if (dialogOpen) {
        OptionSelectionDialog(
            title = label,
            options = options,
            selectedId = selectedId,
            onDismiss = { dialogOpen = false },
            onSelect = { id ->
                onSelect(id)
                dialogOpen = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionSelectionDialog(
    title: String,
    options: List<Pair<String, String>>,
    selectedId: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colorScheme.surfaceContainerHigh)
                .padding(20.dp)
        ) {
            Text(
                title,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Gap(12)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp)
            ) {
                items(options) { (id, optTitle) ->
                    val isSelected = id == selectedId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shapes.small)
                            .bclick { onSelect(id) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            optTitle,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) colorScheme.primary else colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = colorScheme.primary
                            )
                        }
                    }
                }
            }

            Gap(12)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Закрыть")
                }
            }
        }
    }
}
