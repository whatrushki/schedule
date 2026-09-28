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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import app.what.features.account.components.AccountTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.what.features.account.components.SelectField
import app.what.foundation.core.Listener
import app.what.foundation.data.RemoteState
import app.what.foundation.ui.Gap
import app.what.schedule.core.models.EnquiryItemDto
import app.what.schedule.features.insts.rksi.domain.models.RksiEvent
import app.what.schedule.features.insts.rksi.domain.models.RksiState

@Composable
internal fun RksiEnquiriesDialog(
    state: State<RksiState>,
    listener: Listener<RksiEvent>,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (state.value.enquiries.isEmpty() && state.value.enquiryTypes.isEmpty()) {
            listener(RksiEvent.LoadEnquiries)
        }
    }

    val s = state.value
    var selectedTypeId by remember { mutableStateOf<String?>(null) }
    var count by remember { mutableIntStateOf(1) }

    // Period fields (types 1, 4)
    var periodFrom by remember { mutableStateOf("") }
    var periodTo by remember { mutableStateOf("") }

    // Tax deduction fields (type 5)
    var studentInn by remember { mutableStateOf("") }
    var payerFinf by remember { mutableStateOf("") }
    var payerFini by remember { mutableStateOf("") }
    var payerFino by remember { mutableStateOf("") }
    var payerBornDate by remember { mutableStateOf("") }
    var payerInn by remember { mutableStateOf("") }
    var payerDocSerial by remember { mutableStateOf("") }
    var payerDocDate by remember { mutableStateOf("") }

    // Multi-child fields (type 6)
    var contractNumber by remember { mutableStateOf("") }
    var contractDate by remember { mutableStateOf("") }
    var customerFinf by remember { mutableStateOf("") }
    var customerFini by remember { mutableStateOf("") }
    var customerFino by remember { mutableStateOf("") }

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
                "Заказ справок",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        if (s.enquiriesFetchState is RemoteState.Loading && s.enquiries.isEmpty() && s.enquiryTypes.isEmpty()) {
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
                // Section: Order New Certificate
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colorScheme.surfaceContainer)
                            .padding(16.dp)
                    ) {
                        Text(
                            "Заказать новую справку",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Gap(12)

                        val currentType = s.enquiryTypes.firstOrNull { it.id == selectedTypeId }
                            ?: s.enquiryTypes.firstOrNull().also { selectedTypeId = it?.id }

                        SelectField(
                            label = "Вид справки",
                            selectedValueText = currentType?.title ?: "Выберите тип справки",
                            options = s.enquiryTypes.map { it.id to it.title },
                            selectedId = selectedTypeId,
                            onSelect = { selectedTypeId = it }
                        )

                        val isPeriodEnquiry = selectedTypeId == "1" || selectedTypeId == "4" || currentType?.title?.contains("стипенди", ignoreCase = true) == true
                        val isTaxEnquiry = selectedTypeId == "5" || currentType?.title?.contains("ИФНС", ignoreCase = true) == true || currentType?.title?.contains("налогов", ignoreCase = true) == true
                        val isMultiChildEnquiry = selectedTypeId == "6" || currentType?.title?.contains("многодетн", ignoreCase = true) == true

                        if (isTaxEnquiry) {
                            Gap(12)
                            AccountTextField(
                                value = studentInn,
                                onValueChange = { studentInn = it },
                                label = "ИНН студента",
                                modifier = Modifier.fillMaxWidth()
                            )
                            Gap(8)
                            AccountTextField(
                                value = payerFinf,
                                onValueChange = { payerFinf = it },
                                label = "Фамилия плательщика",
                                modifier = Modifier.fillMaxWidth()
                            )
                            Gap(8)
                            AccountTextField(
                                value = payerFini,
                                onValueChange = { payerFini = it },
                                label = "Имя плательщика",
                                modifier = Modifier.fillMaxWidth()
                            )
                            Gap(8)
                            AccountTextField(
                                value = payerFino,
                                onValueChange = { payerFino = it },
                                label = "Отчество плательщика",
                                modifier = Modifier.fillMaxWidth()
                            )
                            Gap(8)
                            AccountTextField(
                                value = payerBornDate,
                                onValueChange = { payerBornDate = it },
                                label = "Дата рождения плательщика (ДД.ММ.ГГГГ)",
                                modifier = Modifier.fillMaxWidth()
                            )
                            Gap(8)
                            AccountTextField(
                                value = payerInn,
                                onValueChange = { payerInn = it },
                                label = "ИНН плательщика",
                                modifier = Modifier.fillMaxWidth()
                            )
                            Gap(8)
                            AccountTextField(
                                value = payerDocSerial,
                                onValueChange = { payerDocSerial = it },
                                label = "Паспорт плательщика (серия и номер)",
                                modifier = Modifier.fillMaxWidth()
                            )
                            Gap(8)
                            AccountTextField(
                                value = payerDocDate,
                                onValueChange = { payerDocDate = it },
                                label = "Дата выдачи паспорта (ДД.ММ.ГГГГ)",
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else if (isMultiChildEnquiry) {
                            Gap(12)
                            AccountTextField(
                                value = contractNumber,
                                onValueChange = { contractNumber = it },
                                label = "Номер договора",
                                modifier = Modifier.fillMaxWidth()
                            )
                            Gap(8)
                            AccountTextField(
                                value = contractDate,
                                onValueChange = { contractDate = it },
                                label = "Дата заключения договора (ГГГГ-ММ-ДД)",
                                modifier = Modifier.fillMaxWidth()
                            )
                            Gap(8)
                            AccountTextField(
                                value = customerFinf,
                                onValueChange = { customerFinf = it },
                                label = "Фамилия заказчика",
                                modifier = Modifier.fillMaxWidth()
                            )
                            Gap(8)
                            AccountTextField(
                                value = customerFini,
                                onValueChange = { customerFini = it },
                                label = "Имя заказчика",
                                modifier = Modifier.fillMaxWidth()
                            )
                            Gap(8)
                            AccountTextField(
                                value = customerFino,
                                onValueChange = { customerFino = it },
                                label = "Отчество заказчика",
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            if (isPeriodEnquiry) {
                                Gap(12)
                                AccountTextField(
                                    value = periodFrom,
                                    onValueChange = { periodFrom = it },
                                    label = "Период с (ДД.ММ.ГГГГ)",
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Gap(8)
                                AccountTextField(
                                    value = periodTo,
                                    onValueChange = { periodTo = it },
                                    label = "Период по (ДД.ММ.ГГГГ)",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Gap(12)

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shapes.medium)
                                    .background(colorScheme.surfaceContainerHigh)
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    "Количество экземпляров",
                                    color = colorScheme.onSurface,
                                    fontSize = 14.sp
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = { if (count > 1) count-- },
                                        enabled = count > 1
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Меньше")
                                    }
                                    Text(
                                        "$count",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface
                                    )
                                    IconButton(
                                        onClick = { if (count < 5) count++ },
                                        enabled = count < 5
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Больше")
                                    }
                                }
                            }
                        }

                        val isFormValid = when {
                            isTaxEnquiry -> studentInn.isNotBlank() && payerFinf.isNotBlank() && payerFini.isNotBlank() && payerInn.isNotBlank()
                            isMultiChildEnquiry -> contractNumber.isNotBlank() && customerFinf.isNotBlank() && customerFini.isNotBlank()
                            isPeriodEnquiry -> periodFrom.isNotBlank() && periodTo.isNotBlank()
                            else -> true
                        }

                        if (s.orderEnquirySuccess == true) {
                            Gap(8)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Gap(6)
                                Text(
                                    "Справка успешно заказана!",
                                    color = colorScheme.primary,
                                    fontSize = 13.sp
                                )
                            }
                        } else if (s.orderEnquirySuccess == false) {
                            Gap(8)
                            Text(
                                "Ошибка при заказе справки",
                                color = colorScheme.error,
                                fontSize = 13.sp
                            )
                        }

                        Gap(12)

                        val buttonText = if (isTaxEnquiry || isMultiChildEnquiry) "Подать заявку" else "Заказать"

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            shape = shapes.medium,
                            enabled = !s.isOrderingEnquiry && selectedTypeId != null && isFormValid,
                            onClick = {
                                selectedTypeId?.let { typeId ->
                                    val params = buildMap {
                                        if (isTaxEnquiry) {
                                            put("t_stInn", studentInn)
                                            put("t_stFinf", payerFinf)
                                            put("t_stFini", payerFini)
                                            put("t_stFino", payerFino)
                                            put("t_stFinborndate", payerBornDate)
                                            put("t_stFininn", payerInn)
                                            put("t_stFinserial", payerDocSerial)
                                            put("t_stFindocdate", payerDocDate)
                                        } else if (isMultiChildEnquiry) {
                                            put("t_stContractNumber", contractNumber)
                                            put("t_stContractDate", contractDate)
                                            put("t_stFinf", customerFinf)
                                            put("t_stFini", customerFini)
                                            put("t_stFino", customerFino)
                                        } else if (isPeriodEnquiry) {
                                            put("count", count.toString())
                                            put("t1", periodFrom)
                                            put("t2", periodTo)
                                        } else {
                                            put("count", count.toString())
                                        }
                                    }
                                    listener(RksiEvent.OrderEnquiryClicked(typeId, params))
                                }
                            }
                        ) {
                            if (s.isOrderingEnquiry) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(buttonText)
                            }
                        }
                    }
                }

                // Section: Existing Enquiries
                item {
                    Text(
                        "История заказов",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                }

                if (s.enquiries.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Заказанных справок нет",
                                color = colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    items(s.enquiries) { item ->
                        EnquiryCard(item)
                    }
                }

                item {
                    Gap(24)
                }
            }
        }
    }
}

@Composable
private fun EnquiryCard(item: EnquiryItemDto) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colorScheme.surfaceContainer)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                item.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Gap(8)
            val isDone = item.status.contains("готов", ignoreCase = true)
            val isProcessing = item.status.contains("обработ", ignoreCase = true) || item.status.contains("нов", ignoreCase = true)
            val statusColor = when {
                isDone -> colorScheme.primary
                isProcessing -> colorScheme.tertiary
                else -> colorScheme.outline
            }
            Text(
                item.status,
                color = statusColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        Gap(12)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Заказ: ",
                    fontSize = 13.sp,
                    color = colorScheme.onSurfaceVariant
                )
                Text(
                    item.orderDate,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Кол-во: ",
                    fontSize = 13.sp,
                    color = colorScheme.onSurfaceVariant
                )
                Text(
                    "${item.count} шт.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface
                )
            }
        }

        if (item.pickupLocation.isNotBlank()) {
            Gap(8)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    "Выдача: ",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurfaceVariant
                )
                Text(
                    item.pickupLocation,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.primary
                )
            }
        }
    }
}
