package app.what.schedule.wasm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.what.schedule.core.models.DayScheduleDto
import app.what.schedule.core.models.GroupDto
import app.what.schedule.core.models.TeacherDto
import app.what.schedule.wasm.model.WebInstitutionData
import app.what.schedule.wasm.model.WebSearchItem
import app.what.schedule.wasm.model.WebUniversity
import app.what.schedule.wasm.ui.components.WebUpdateBanner
import app.what.schedule.wasm.updater.WasmUpdateManager
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.browser.window
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WebApp(httpClient: HttpClient, updateManager: WasmUpdateManager) {
    val scope = rememberCoroutineScope()
    var selectedUniversity by remember { mutableStateOf(WebUniversity.RKSI) }
    var searchQuery by remember { mutableStateOf("") }
    var groups by remember { mutableStateOf<List<GroupDto>>(emptyList()) }
    var teachers by remember { mutableStateOf<List<TeacherDto>>(emptyList()) }
    var fullData by remember { mutableStateOf<WebInstitutionData?>(null) }
    var selectedItem by remember { mutableStateOf<WebSearchItem?>(null) }
    var currentSchedule by remember { mutableStateOf<List<DayScheduleDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isScheduleLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val dataBaseUrl = remember {
        val origin = window.location.origin
        val pathname = window.location.pathname.trimEnd('/')
        val hostname = window.location.hostname
        if (hostname == "localhost" || hostname == "127.0.0.1") {
            "$origin$pathname/schedule"
        } else {
            "https://raw.githubusercontent.com/whatrushki/schedule/master/.github/schedule"
        }
    }

    // Load university data when selected university changes
    LaunchedEffect(selectedUniversity) {
        isLoading = true
        errorMessage = null
        selectedItem = null
        currentSchedule = emptyList()
        val code = selectedUniversity.code
        try {
            val rawDataUrl = "$dataBaseUrl/$code/data.json"
            val data = httpClient.get(rawDataUrl).body<WebInstitutionData>()
            fullData = data
            groups = data.groups
            teachers = data.teachers
        } catch (_: Exception) {
            try {
                val relativeUrl = "${window.location.origin}${window.location.pathname.trimEnd('/')}/schedule/$code/data.json"
                val data = httpClient.get(relativeUrl).body<WebInstitutionData>()
                fullData = data
                groups = data.groups
                teachers = data.teachers
            } catch (_: Exception) {
                try {
                    groups = httpClient.get("$dataBaseUrl/$code/groups.json").body<List<GroupDto>>()
                    teachers = httpClient.get("$dataBaseUrl/$code/teachers.json").body<List<TeacherDto>>()
                } catch (e: Exception) {
                    errorMessage = "Не удалось загрузить данные для ${selectedUniversity.title}"
                }
            }
        } finally {
            isLoading = false
        }
    }

    // Load schedule when item is selected
    fun selectItem(item: WebSearchItem) {
        selectedItem = item
        isScheduleLoading = true
        scope.launch {
            val code = selectedUniversity.code
            val data = fullData
            if (data != null && data.schedules.containsKey(item.title)) {
                currentSchedule = data.schedules[item.title] ?: emptyList()
            } else {
                val safeId = item.title.replace("/", "_").replace("\\", "_")
                try {
                    currentSchedule = httpClient.get("$dataBaseUrl/$code/groups/$safeId.json").body<List<DayScheduleDto>>()
                } catch (_: Exception) {
                    try {
                        currentSchedule = httpClient.get("${window.location.origin}${window.location.pathname.trimEnd('/')}/schedule/$code/groups/$safeId.json").body<List<DayScheduleDto>>()
                    } catch (_: Exception) {
                        currentSchedule = emptyList()
                    }
                }
            }
            isScheduleLoading = false
        }
    }

    val filteredItems = remember(searchQuery, groups, teachers) {
        val q = searchQuery.trim().lowercase()
        val groupItems = groups.map { WebSearchItem(it.id, it.name, isTeacher = false) }
        val teacherItems = teachers.map { WebSearchItem(it.id, it.name, isTeacher = true) }
        val all = groupItems + teacherItems
        if (q.isEmpty()) all.take(40)
        else all.filter { it.title.lowercase().contains(q) }.take(40)
    }

    val darkScheme = darkColorScheme(
        primary = Color(0xFF4C8DFF),
        surface = Color(0xFF1E1F2A),
        background = Color(0xFF12131C),
        onSurface = Color(0xFFE2E2E9),
        surfaceVariant = Color(0xFF282936)
    )

    MaterialTheme(colorScheme = darkScheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                WebUpdateBanner(
                    updateInfo = updateManager.updateInfo,
                    onUpdateClick = { updateManager.handleAction() }
                )

                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "WHAT Schedule",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Button(onClick = {
                        window.open("https://github.com/whatrushki/schedule/releases", "_blank")
                    }) {
                        Text("Скачать приложение")
                    }
                }

                // Main Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                ) {
                    // University selector
                    Text("Выберите учебное заведение:", fontSize = 14.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (uni in WebUniversity.entries) {
                            FilterChip(
                                selected = selectedUniversity == uni,
                                onClick = { selectedUniversity = uni },
                                label = { Text(uni.title) }
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    if (isLoading) {
                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (errorMessage != null) {
                        Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error)
                    } else {
                        // Search field
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Поиск группы или преподавателя...") },
                            modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
                            singleLine = true
                        )

                        Spacer(Modifier.height(12.dp))

                        // Search suggestions chips
                        if (selectedItem == null) {
                            Text("Популярные группы и преподаватели:", fontSize = 12.sp, color = Color.Gray)
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
                                for (item in filteredItems) {
                                    ElevatedCard(
                                        modifier = Modifier.clickable { selectItem(item) },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            item.title,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Выбрано: ${selectedItem?.title}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                                Spacer(Modifier.width(12.dp))
                                Button(onClick = { selectedItem = null; currentSchedule = emptyList() }) {
                                    Text("Сменить")
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Schedule view
                        if (isScheduleLoading) {
                            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (selectedItem != null) {
                            if (currentSchedule.isEmpty()) {
                                Text("Расписание не найдено для выбранного элемента.", color = Color.Gray)
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(currentSchedule) { day ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                        ) {
                                            Column(Modifier.padding(16.dp)) {
                                                Text(
                                                    text = day.date.toString(),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 17.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(Modifier.height(8.dp))

                                                if (day.lessons.isEmpty()) {
                                                    Text("Нет пар", color = Color.Gray)
                                                } else {
                                                    for (lesson in day.lessons) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(vertical = 4.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    "${lesson.number}. ${lesson.subject}",
                                                                    fontWeight = FontWeight.Medium,
                                                                    fontSize = 15.sp
                                                                )
                                                                val details = lesson.otUnits.joinToString("; ") { unit ->
                                                                    listOfNotNull(unit.teacher, unit.group, unit.room, unit.additional).filter { it.isNotBlank() }.joinToString(", ")
                                                                }
                                                                if (details.isNotBlank()) {
                                                                    Text(details, fontSize = 13.sp, color = Color.Gray)
                                                                }
                                                            }
                                                            Text(
                                                                "${lesson.startTime} - ${lesson.endTime}",
                                                                fontSize = 13.sp,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
