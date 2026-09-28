package app.what.schedule.features.insts.rksi.presentation.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import app.what.foundation.services.LocalNotificationService
import app.what.foundation.services.AppNotification
import app.what.foundation.services.Event
import app.what.features.account.components.AccountTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.what.features.account.components.SelectField
import app.what.foundation.core.Listener
import app.what.foundation.data.RemoteState
import app.what.foundation.ui.Gap
import app.what.foundation.ui.bclick
import app.what.schedule.core.models.ProfileInputFieldDto
import app.what.schedule.features.insts.rksi.domain.models.RksiEvent
import app.what.schedule.features.insts.rksi.domain.models.RksiState

@Composable
internal fun RksiProfileEditorDialog(
    state: State<RksiState>,
    listener: Listener<RksiEvent>,
    onBack: () -> Unit
) {
    val notificationService = LocalNotificationService.current

    LaunchedEffect(Unit) {
        listener(RksiEvent.LoadProfileEditor)
    }

    val s = state.value

    val editedFields = remember { mutableStateMapOf<String, String>() }
    val checkedSocialIds = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(s.profileSections) {
        if (s.profileSections.isNotEmpty()) {
            editedFields.clear()
            s.profileSections.flatMap { it.fields }.forEach { field ->
                editedFields[field.name] = field.value
            }
        }
    }

    LaunchedEffect(s.socialStatusOptions) {
        if (s.socialStatusOptions.isNotEmpty()) {
            checkedSocialIds.clear()
            s.socialStatusOptions.forEach { opt ->
                checkedSocialIds[opt.id] = opt.isChecked
            }
        }
    }

    LaunchedEffect(s.saveProfileSuccess) {
        if (s.saveProfileSuccess == true) {
            notificationService?.notify(
                AppNotification(
                    title = "Профиль",
                    message = "Анкета успешно сохранена",
                    urgency = Event.Urgency.LOW
                )
            )
            listener(RksiEvent.ClearProfileSaveStatus)
        } else if (s.saveProfileSuccess == false) {
            notificationService?.notify(
                AppNotification(
                    title = "Профиль",
                    message = "Ошибка при сохранении анкеты",
                    urgency = Event.Urgency.MEDIUM
                )
            )
            listener(RksiEvent.ClearProfileSaveStatus)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surface)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = colorScheme.onSurface
                )
            }
            Text(
                "Редактирование анкеты",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        if (s.profileSectionsFetchState is RemoteState.Loading && s.profileSections.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colorScheme.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // Profile form sections
                s.profileSections.forEach { section ->
                    item {
                        Text(
                            section.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    items(section.fields) { field ->
                        ProfileFieldEditor(
                            field = field,
                            currentValue = editedFields[field.name] ?: field.value,
                            onValueChange = { newVal -> editedFields[field.name] = newVal }
                        )
                    }
                }

                // Social Status Section
                if (s.socialStatusOptions.isNotEmpty()) {
                    item {
                        Text(
                            "Социальный статус",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    items(s.socialStatusOptions) { opt ->
                        val isChecked = checkedSocialIds[opt.id] ?: opt.isChecked
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shapes.medium)
                                .bclick {
                                    checkedSocialIds[opt.id] = !isChecked
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checkedSocialIds[opt.id] = it }
                            )
                            Gap(8)
                            Text(
                                opt.title,
                                fontSize = 14.sp,
                                color = colorScheme.onSurface
                            )
                        }
                    }
                }

                // Save button
                item {
                    Gap(12)
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        shape = shapes.medium,
                        enabled = !s.isSavingProfile,
                        onClick = {
                            if (editedFields.isNotEmpty()) {
                                listener(RksiEvent.SaveProfileClicked(editedFields.toMap()))
                            }
                            if (s.socialStatusOptions.isNotEmpty()) {
                                val checkedSet = checkedSocialIds.filter { it.value }.keys
                                listener(
                                    RksiEvent.SaveSocialStatusClicked(
                                        checkedIds = checkedSet,
                                        allIds = s.socialStatusOptions.map { it.id }
                                    )
                                )
                            }
                        }
                    ) {
                        if (s.isSavingProfile) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Сохранить")
                        }
                    }
                    Gap(24)
                }
            }
        }
    }
}

@Composable
private fun ProfileFieldEditor(
    field: ProfileInputFieldDto,
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    if (field.type == "select" && field.options.isNotEmpty()) {
        val currentOptionTitle = field.options.firstOrNull { it.first == currentValue }?.second
            ?: currentValue.ifBlank { "Выберите значение" }

        SelectField(
            label = field.label,
            selectedValueText = currentOptionTitle,
            options = field.options,
            selectedId = currentValue,
            onSelect = onValueChange
        )
    } else {
        AccountTextField(
            value = currentValue,
            onValueChange = onValueChange,
            label = field.label,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
