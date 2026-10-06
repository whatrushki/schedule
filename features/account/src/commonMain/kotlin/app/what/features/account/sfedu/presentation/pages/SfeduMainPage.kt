package app.what.schedule.features.insts.sfedu.presentation.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.what.features.account.components.InfoBlock
import app.what.features.account.components.KeyValueList
import app.what.features.account.components.NewItemView
import app.what.foundation.core.Listener
import app.what.foundation.data.RemoteState
import app.what.foundation.ui.Gap
import app.what.foundation.ui.Show
import app.what.foundation.ui.SystemBarsGap
import app.what.foundation.ui.animations.rememberShimmer
import app.what.foundation.ui.bclick
import app.what.foundation.ui.controllers.rememberDialogController
import app.what.foundation.ui.controllers.rememberSheetController
import app.what.foundation.ui.useState
import app.what.schedule.features.insts.sfedu.domain.models.SfeduEvent
import app.what.schedule.features.insts.sfedu.domain.models.SfeduState
import app.what.schedule.sfedu.grade.SfeduGradeDiscipline
import app.what.schedule.ui.components.StyledTextField
import app.what.foundation.ui.icons.WHATIcons
import app.what.foundation.ui.icons.filled.Features

import app.what.features.account.components.rememberStackDialogController
import app.what.features.account.components.rememberStackSheetController

@Composable
internal fun SfeduMainScreen(
    state: State<SfeduState>,
    listener: Listener<SfeduEvent>
) {
    val dialog = rememberStackDialogController()
    val sheet = rememberStackSheetController()
    val shimmer = rememberShimmer()
    val uriHandler = LocalUriHandler.current
    
    LaunchedEffect(Unit) {
        listener(SfeduEvent.MainOpened)
    }
    
    if (state.value.disciplinesFetchState is RemoteState.Error && state.value.disciplines.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(colorScheme.errorContainer.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                }
                
                Gap(20)
                
                Text(
                    "Не удалось загрузить данные",
                    color = colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Gap(8)
                
                Text(
                    "Проверьте интернет-соединение или повторите попытку позже.",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
                
                Gap(24)
                
                Button(
                    onClick = { listener(SfeduEvent.RetryClicked) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Повторить попытку")
                }
                
                Gap(8)
                
                TextButton(
                    onClick = { listener(SfeduEvent.LogoutClicked) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Выйти из аккаунта", color = colorScheme.error)
                }
            }
        }
        return
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = 860.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ==========================================
        // 1. КАРТОЧКА ПРОФИЛЯ СТУДЕНТА (HEADER CARD)
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                .background(colorScheme.surfaceContainer)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Gap(16)
                
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Аватар с инициалами студента (ФИ)
                        val initials = state.value.displayShortName
                            .split(" ")
                            .mapNotNull { it.firstOrNull()?.toString() }
                            .take(2)
                            .joinToString("")
                        
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .clip(CircleShape)
                                .background(colorScheme.primary.copy(alpha = 0.15f))
                                .border(2.dp, colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (initials.isNotBlank()) {
                                Text(
                                    text = initials,
                                    color = colorScheme.primary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                        
                        Gap(16)
                        
                        Column {
                            // ФИ (например: Владислав Паршин)
                            Text(
                                text = state.value.displayShortName,
                                color = colorScheme.onSurface,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            // Курс и степень обучения (например: 2 курс • Бакалавриат)
                            val courseAndDegree = buildList {
                                state.value.displayCourse?.let { add("$it курс") }
                                state.value.displayDegree?.let { add(it) }
                            }.joinToString(" • ")
                            
                            if (courseAndDegree.isNotBlank()) {
                                Text(
                                    text = courseAndDegree,
                                    color = colorScheme.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            
                            state.value.displayDirection?.let { direction ->
                                Text(
                                    text = direction,
                                    color = colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                
                Gap(16)
                
                // Статистика (3 карточки как в ДГТУ)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                ) {
                    val avgRate = state.value.averageRate
                    val avgText = if (avgRate != null) {
                        val rounded = (avgRate * 10).toInt() / 10.0
                        if (rounded % 1.0 == 0.0) "${rounded.toInt()}" else "$rounded"
                    } else "—"
                    
                    StatBox(
                        data = avgText,
                        text = "Ср. балл",
                        accentColor = colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    
                    StatBox(
                        data = state.value.currentSemesterDisciplines.size.toString(),
                        text = "Предметов",
                        modifier = Modifier.weight(1f)
                    )
                    
                    StatBox(
                        data = state.value.examCount.toString(),
                        text = "Экзаменов",
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Gap(16)
                
                // Кнопки действий: "Подробнее" и "Выйти"
                Row(
                    modifier = Modifier
                        .height(IntrinsicSize.Min)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(shapes.small)
                            .background(colorScheme.primary)
                            .weight(1f)
                            .bclick {
                                sheet.open(true) {
                                    SfeduStudentDetailSheet(
                                        state = state.value,
                                        onEditClicked = {
                                            sheet.close()
                                            dialog.open(true) {
                                                SfeduProfileEditDialog(
                                                    currentName = state.value.displayStudentName,
                                                    currentGroup = state.value.displayGroup
                                                        ?: "",
                                                    currentDirection = state.value.displayDirection
                                                        ?: "",
                                                    onSave = { name, group, direction ->
                                                        listener(
                                                            SfeduEvent.SaveProfile(
                                                                name,
                                                                group,
                                                                direction
                                                            )
                                                        )
                                                        dialog.close()
                                                    },
                                                    onClose = { dialog.close() }
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                    ) {
                        Text(
                            text = "Подробнее",
                            modifier = Modifier.padding(12.dp, 8.dp),
                            color = colorScheme.onPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Gap(8)
                    
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .width(48.dp)
                            .fillMaxHeight()
                            .clip(shapes.small)
                            .background(colorScheme.error)
                            .bclick {
                                listener(SfeduEvent.LogoutClicked)
                            }
                    ) {
                        Icons.AutoMirrored.Filled.Logout.Show(colorScheme.onError, 22)
                    }
                }
                
                Gap(16)
            }
        }
        
        Gap(16)
        
        // ==========================================
        // 2. СЕРВИСЫ (БЛОК БАЛЛЫ)
        // ==========================================
        InfoBlock(
            accentColor = colorScheme.primary,
            icon = WHATIcons.Features,
            title = "Баллы",
            description = "Оценки и дисциплины",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
        ) {
            dialog.open(true) {
                SfeduBrsDialog(
                    state = state,
                    listener = listener,
                    onBack = { dialog.close() },
                    onDisciplineClick = { discipline ->
                        listener(SfeduEvent.DisciplineClicked(discipline.ID))
                        dialog.open(full = true, inStack = true) {
                            SfeduDisciplineDetailPage(
                                state = state,
                                onBack = {
                                    listener(SfeduEvent.DisciplineDetailClosed)
                                    dialog.back()
                                }
                            )
                        }
                    }
                )
            }
        }
        
        Gap(12)
        
        // ==========================================
        // 3. БЛОК НОВОСТЕЙ (ИНСТИТУТ)
        // ==========================================
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Новости института",
                    style = typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                TextButton(
                    onClick = { listener(SfeduEvent.OnShowAllNewsClicked) }
                ) {
                    Text("Все", fontSize = 14.sp, color = colorScheme.primary)
                }
            }
            
            Gap(8)
            
            if (state.value.newsFetchState == RemoteState.Loading && state.value.news.isEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp)
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .width(220.dp)
                                .height(130.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(shimmer)
                        )
                    }
                }
            } else if (state.value.news.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp)
                ) {
                    state.value.news.forEach { item ->
                        NewItemView(item) {
                            listener(SfeduEvent.OnNewClicked(item.id))
                        }
                    }
                }
            }
        }
        
        Gap(40)
        SystemBarsGap()
    }
}

/**
 * Диалоговое окно с баллами и дисциплинами студента БРС ЮФУ
 */
@Composable
private fun SfeduBrsDialog(
    state: State<SfeduState>,
    listener: Listener<SfeduEvent>,
    onBack: () -> Unit,
    onDisciplineClick: (SfeduGradeDiscipline) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surface)
            .statusBarsPadding()
            .systemBarsPadding()
    ) {
        // Заголовок диалога
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = colorScheme.onSurface
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Баллы",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                Text(
                    text = "${state.value.currentSemesterDisciplines.size} дисциплин",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Переключатель семестров
        if (state.value.semesters.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                items(state.value.semesters) { semester ->
                    val isSelected = semester.ID == state.value.selectedSemesterId
                    val seasonName = when (semester.Season) {
                        "autumn" -> "Осень"
                        "spring" -> "Весна"
                        else -> semester.Season
                    }
                    Box(
                        modifier = Modifier
                            .clip(shapes.medium)
                            .background(
                                if (isSelected) colorScheme.primary
                                else colorScheme.surfaceContainerHigh
                            )
                            .bclick { listener(SfeduEvent.SemesterSelected(semester.ID)) }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "$seasonName ${semester.CalendarYear}",
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface
                        )
                    }
                }
            }
            Gap(6)
        }
        
        // Список дисциплин
        if (state.value.disciplinesFetchState == RemoteState.Loading && state.value.disciplines.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = colorScheme.primary)
            }
        } else if (state.value.currentSemesterDisciplines.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Нет дисциплин за выбранный семестр",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                items(state.value.currentSemesterDisciplines) { discipline ->
                    val mark = state.value.marks[discipline.ID.toString()]
                    CompactDisciplineCard(
                        discipline = discipline,
                        ectsGrade = mark,
                        onClick = {
                            onDisciplineClick(discipline)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Компактная карточка дисциплины с круговым индикатором и ECTS оценкой внутри
 */
@Composable
private fun CompactDisciplineCard(
    discipline: SfeduGradeDiscipline,
    ectsGrade: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val progress = if (discipline.MaxCurrentRate > 0) {
        (discipline.Rate.toFloat() / discipline.MaxCurrentRate.toFloat()).coerceIn(0f, 1f)
    } else 0f
    
    val gradeColor = getEctsColor(ectsGrade ?: "")
    val gradeText = formatEctsGrade(ectsGrade ?: "Undefined")
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shapes.medium)
            .background(colorScheme.surfaceContainer)
            .bclick(block = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = discipline.SubjectName,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                color = colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Gap(4)
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val controlType = when (discipline.Type) {
                    "exam" -> "Экзамен"
                    "credit" -> "Зачёт"
                    "credit_grade", "grading_credit" -> "Дифф. зачёт"
                    else -> discipline.Type
                }
                Text(
                    text = controlType,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.primary
                )
                
                Text(
                    text = "•",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = "${discipline.Rate} / ${discipline.MaxCurrentRate} б.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
            }
            
            // Преподаватель
            val teacherName = buildString {
                if (discipline.LastName.isNotBlank()) {
                    append(discipline.LastName)
                    if (discipline.FirstName.isNotBlank()) {
                        append(" ${discipline.FirstName.first()}.")
                    }
                    if (discipline.SecondName.isNotBlank()) {
                        append("${discipline.SecondName.first()}.")
                    }
                }
            }
            if (teacherName.isNotBlank()) {
                Gap(2)
                Text(
                    text = teacherName,
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        Gap(12)
        
        // Круговой прогресс с оценкой внутри
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = gradeColor,
                trackColor = colorScheme.surfaceContainerHighest,
                strokeWidth = 3.5.dp
            )
            Text(
                text = gradeText,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = gradeColor
            )
        }
    }
}

@Composable
private fun StatBox(
    data: String,
    text: String,
    modifier: Modifier = Modifier,
    accentColor: Color = colorScheme.primary
) = Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
        .clip(shapes.medium)
        .background(colorScheme.surfaceContainerHigh)
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 12.dp)
    ) {
        Text(
            text = data,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
        Gap(2)
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SfeduStudentDetailSheet(
    state: SfeduState,
    onEditClicked: () -> Unit
) = Column(
    Modifier
        .fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = 860.dp)
        .padding(18.dp)
        .systemBarsPadding()
        .verticalScroll(rememberScrollState())
) {
    Text(
        "Анкета студента",
        style = typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 16.dp)
    )
    
    val items = buildList {
        add("ФИО" to state.displayStudentName)
        state.displayDirection?.let { add("Направление" to it) }
        state.displayFaculty?.let { add("Подразделение" to it) }
        state.displayCourse?.let { add("Курс" to "$it курс") }
        state.displayDegree?.let { add("Уровень образования" to it) }
        state.displayGroup?.let { add("Группа" to it) }
        state.displayEmail?.let { add("E-Mail" to it) }
        state.token?.let {
            val masked = if (it.length > 8) "${it.take(4)}...${it.takeLast(4)}" else "***"
            add("Токен БРС" to masked)
        }
    }
    
    KeyValueList(items = items)
    
    Gap(24)
    
    Button(
        onClick = onEditClicked,
        modifier = Modifier.fillMaxWidth(),
        shape = shapes.medium
    ) {
        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
        Gap(8)
        Text("Редактировать анкету")
    }
    
    Gap(16)
}

@Composable
private fun SfeduProfileEditDialog(
    currentName: String,
    currentGroup: String,
    currentDirection: String,
    onSave: (String, String, String) -> Unit,
    onClose: () -> Unit
) {
    val (name, setName) = useState(currentName)
    val (group, setGroup) = useState(currentGroup)
    val (direction, setDirection) = useState(currentDirection)
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Text(
            "Редактирование профиля",
            style = typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurface
        )
        
        Gap(16)
        
        StyledTextField(
            name, setName,
            modifier = Modifier.fillMaxWidth(),
            debounce = 500,
            placeholder = "ФИО (напр. Иванов Иван Иванович)",
            shape = shapes.medium
        )
        
        Gap(12)
        
        StyledTextField(
            group, setGroup,
            modifier = Modifier.fillMaxWidth(),
            debounce = 500,
            placeholder = "Группа (напр. 5 группа)",
            shape = shapes.medium
        )
        
        Gap(12)
        
        StyledTextField(
            direction, setDirection,
            modifier = Modifier.fillMaxWidth(),
            debounce = 500,
            placeholder = "Направление подготовки",
            shape = shapes.medium
        )
        
        Gap(20)
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = onClose,
                modifier = Modifier.weight(1f)
            ) {
                Text("Отмена")
            }
            
            Button(
                onClick = { onSave(name, group, direction) },
                modifier = Modifier.weight(1f),
                shape = shapes.medium
            ) {
                Text("Сохранить")
            }
        }
    }
}

private fun formatEctsGrade(grade: String): String = when (grade) {
    "ECTS-A" -> "A"
    "ECTS-B" -> "B"
    "ECTS-C" -> "C"
    "ECTS-D" -> "D"
    "ECTS-E" -> "E"
    "ECTS-F" -> "F"
    "Undefined" -> "—"
    else -> if (grade.isBlank()) "—" else grade
}

private fun getEctsColor(grade: String): Color = when (grade) {
    "ECTS-A" -> Color(0xFF2E7D32)
    "ECTS-B" -> Color(0xFF388E3C)
    "ECTS-C" -> Color(0xFF1976D2)
    "ECTS-D" -> Color(0xFFE65100)
    "ECTS-E" -> Color(0xFFEF6C00)
    "ECTS-F" -> Color(0xFFD32F2F)
    else -> Color(0xFF757575)
}
