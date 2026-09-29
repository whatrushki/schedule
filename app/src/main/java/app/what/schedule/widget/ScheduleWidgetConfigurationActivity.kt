package app.what.schedule.features.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import app.what.foundation.data.RemoteState
import app.what.foundation.ui.Gap
import app.what.foundation.ui.animations.AnimatedEnter
import app.what.foundation.ui.bclick
import app.what.foundation.ui.useState
import app.what.schedule.data.local.settings.AppValues
import app.what.schedule.data.local.settings.ProvideGLobalAppValues
import app.what.schedule.data.remote.api.InstitutionManager
import app.what.domain.models.ScheduleSearch
import app.what.domain.models.toScheduleSearch
import app.what.domain.repositories.ScheduleRepository
import app.what.schedule.ui.components.ScheduleSearchData
import app.what.schedule.ui.components.ScheduleSearchPane
import app.what.schedule.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.compose.koinInject

class ScheduleWidgetConfigurationActivity : ComponentActivity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Получаем ID виджета
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // Если не передан ID, пробуем найти существующий ID виджета
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            val glanceId = kotlinx.coroutines.runBlocking {
                try {
                    GlanceAppWidgetManager(this@ScheduleWidgetConfigurationActivity)
                        .getGlanceIds(ScheduleWidget::class.java)
                        .firstOrNull()
                } catch (_: Exception) { null }
            }
            appWidgetId = glanceId?.let { (it as? AppWidgetId)?.appWidgetId }
                ?: AppWidgetManager.INVALID_APPWIDGET_ID
        }

        // По умолчанию возвращаем CANCELED — если пользователь нажмёт "Назад",
        // лаунчер корректно отменит добавление виджета
        val cancelIntent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_CANCELED, cancelIntent)

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            // НЕ ПЕРЕМЕЩАТЬ!!
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setNavigationBarContrastEnforced(false)
            }

            val settings = koinInject<AppValues>()
            val institutionManager = koinInject<InstitutionManager>()
            val scheduleRepository = koinInject<ScheduleRepository>()
            val scope = rememberCoroutineScope()

            val institutions = remember { institutionManager.getInstitutions() }
            var selectedInstitutionId by useState<String>(
                settings.institution.get() ?: institutions.firstOrNull()?.metadata?.id ?: "rksi"
            )
            var showInstitutionDialog by useState(false)

            var searchItems by useState<List<ScheduleSearch>>(emptyList())
            var isSearchesLoading by useState(false)
            var selectedSearch by useState<ScheduleSearch?>(null)
            val searchData = remember(searchItems, selectedSearch, isSearchesLoading) {
                mutableStateOf(
                    object : ScheduleSearchData {
                        override val scheduleSearches = searchItems
                        override val selectedSearch = selectedSearch
                        override val scheduleSearchesState = if (isSearchesLoading) RemoteState.Loading else RemoteState.Success
                    }
                )
            }

            ProvideGLobalAppValues(settings) {
                AppTheme {
                    LaunchedEffect(selectedInstitutionId) {
                        searchItems = emptyList()
                        selectedSearch = null
                        isSearchesLoading = true
                        try {
                            val items = kotlinx.coroutines.withContext(IO) {
                                val ut = async {
                                    scheduleRepository.getTeachers(selectedInstitutionId).map { it.toScheduleSearch() }
                                }
                                val ug = async {
                                    scheduleRepository.getGroups(selectedInstitutionId).map { it.toScheduleSearch() }
                                }
                                awaitAll(ut, ug).flatten()
                            }
                            searchItems = items
                        } catch (e: Exception) {
                            val tag = app.what.foundation.utils.buildTag(app.what.foundation.utils.LogScope.WIDGET, app.what.foundation.utils.LogCat.UI)
                            app.what.foundation.services.AppLogger.Auditor.debug(tag, "Ошибка загрузки поиска для виджета: ${e.message}")
                        } finally {
                            isSearchesLoading = false
                        }
                    }

                    var isSaving by useState(false)

                    WidgetConfigurationScreen(
                        appWidgetId = appWidgetId,
                        institutions = institutions.map { it.metadata.id to it.metadata.name },
                        selectedInstitutionId = selectedInstitutionId,
                        showInstitutionDialog = showInstitutionDialog,
                        onShowInstitutionDialogChange = { showInstitutionDialog = it },
                        onSelectInstitution = { newId ->
                            if (selectedInstitutionId != newId) {
                                selectedInstitutionId = newId
                                selectedSearch = null
                                searchItems = emptyList()
                            }
                            showInstitutionDialog = false
                        },
                        searchData = searchData,
                        onSelectSearch = { selectedSearch = it },
                        isSaving = isSaving,
                        onSave = { search ->
                            saveWidgetConfiguration(
                                appWidgetId = appWidgetId,
                                search = search,
                                institutionId = selectedInstitutionId,
                                scheduleRepository = scheduleRepository,
                                onSavingChange = { isSaving = it }
                            )
                        }
                    )
                }
            }
        }
    }


    @Composable
    private fun WidgetConfigurationScreen(
        appWidgetId: Int,
        institutions: List<Pair<String, String>>,
        selectedInstitutionId: String,
        showInstitutionDialog: Boolean,
        onShowInstitutionDialogChange: (Boolean) -> Unit,
        onSelectInstitution: (String) -> Unit,
        searchData: State<ScheduleSearchData>,
        onSelectSearch: (ScheduleSearch) -> Unit,
        isSaving: Boolean,
        onSave: (ScheduleSearch) -> Unit
    ) = Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background),
    ) {
        val hasSelection = searchData.value.selectedSearch != null

        AnimatedEnter(
            Modifier
                .zIndex(2f)
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            ExtendedFloatingActionButton(
                onClick = {
                    if (isSaving) return@ExtendedFloatingActionButton
                    val selected = searchData.value.selectedSearch ?: return@ExtendedFloatingActionButton
                    onSave(selected)
                }
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = colorScheme.onPrimaryContainer
                    )
                    Gap(8)
                    Text("Загрузка расписания...")
                } else {
                    Text(if (hasSelection) "Выбрать" else "Выберите группу")
                }
            }
        }

        if (showInstitutionDialog) {
            AlertDialog(
                onDismissRequest = { onShowInstitutionDialogChange(false) },
                title = { Text("Выберите учебное заведение") },
                text = {
                    LazyColumn {
                        items(institutions) { (id, name) ->
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .bclick { onSelectInstitution(id) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = id == selectedInstitutionId,
                                    onClick = null
                                )
                                Gap(12)
                                Text(
                                    text = name,
                                    style = typography.bodyLarge,
                                    color = colorScheme.onSurface
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onShowInstitutionDialogChange(false) }) {
                        Text("Отмена")
                    }
                }
            )
        }

        Column {
            Box(
                Modifier
                    .animateContentSize()
                    .height(180.dp)
            ) {
                AnimatedEnter(
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Text(
                        text = "Настройка виджета",
                        style = typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 40.sp,
                        color = colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, start = 18.dp, end = 16.dp)
                    )
                }
            }

            Gap(8)

            // Выбор учебного заведения
            val currentInstName = institutions.firstOrNull { it.first == selectedInstitutionId }?.second ?: selectedInstitutionId
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable { onShowInstitutionDialogChange(true) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Учебное заведение",
                        style = typography.labelSmall,
                        color = colorScheme.secondary
                    )
                    Text(
                        text = currentInstName,
                        style = typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface
                    )
                }
                Text(
                    text = "Сменить",
                    style = typography.labelMedium,
                    color = colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Gap(16)

            // Список для выбора
            ScheduleSearchPane(
                searchData,
                onClick = onSelectSearch,
                onLongClick = { /* Обработка долгого нажатия */ }
            )
        }
    }

    private fun saveWidgetConfiguration(
        appWidgetId: Int,
        search: ScheduleSearch,
        institutionId: String,
        scheduleRepository: ScheduleRepository,
        onSavingChange: (Boolean) -> Unit
    ) = lifecycleScope.launch {
        onSavingChange(true)
        val searchWithInstitution = if (search.institutionId == null) {
            search.copy(institutionId = institutionId)
        } else {
            search
        }

        val searchId = scheduleRepository.findSearchId(searchWithInstitution) ?: searchWithInstitution.id
        val effectiveSearch = when (searchWithInstitution) {
            is ScheduleSearch.Group -> searchWithInstitution.copy(id = searchId)
            is ScheduleSearch.Teacher -> searchWithInstitution.copy(id = searchId)
        }

        // Загружаем расписание в Room БД в foreground процессе перед закрытием экрана
        kotlinx.coroutines.withContext(IO) {
            var loaded = false
            // Попытка 1: загрузка по ID (основной путь)
            try {
                val result = scheduleRepository.getSchedule(
                    effectiveSearch,
                    useCache = false,
                    requiresData = true
                )
                loaded = result is app.what.domain.models.ScheduleResponse.Available
            } catch (e: Exception) {
                val tag = app.what.foundation.utils.buildTag(app.what.foundation.utils.LogScope.WIDGET, app.what.foundation.utils.LogCat.NET)
                app.what.foundation.services.AppLogger.Auditor.debug(tag, "Ошибка предзагрузки расписания для виджета по ID: ${e.message}")
            }
            // Попытка 2: загрузка по имени (если ID не дал результата)
            if (!loaded && effectiveSearch.name.isNotBlank() && effectiveSearch.name != effectiveSearch.id) {
                try {
                    val nameSearch = when (effectiveSearch) {
                        is ScheduleSearch.Group -> effectiveSearch.copy(id = effectiveSearch.name)
                        is ScheduleSearch.Teacher -> effectiveSearch.copy(id = effectiveSearch.name)
                    }
                    val result = scheduleRepository.getSchedule(
                        nameSearch,
                        useCache = false,
                        requiresData = true
                    )
                    loaded = result is app.what.domain.models.ScheduleResponse.Available
                } catch (e: Exception) {
                    val tag = app.what.foundation.utils.buildTag(app.what.foundation.utils.LogScope.WIDGET, app.what.foundation.utils.LogCat.NET)
                    app.what.foundation.services.AppLogger.Auditor.debug(tag, "Ошибка предзагрузки расписания для виджета по имени: ${e.message}")
                }
            }
        }

        try {
            val glanceId = try {
                GlanceAppWidgetManager(applicationContext).getGlanceIdBy(appWidgetId)
            } catch (_: Exception) {
                AppWidgetId(appWidgetId)
            }
            updateAppWidgetState(applicationContext, glanceId) { prefs ->
                prefs[stringPreferencesKey(INSTITUTION_ID_KEY)] = institutionId
                prefs[stringPreferencesKey(SEARCH_KEY)] = Json.encodeToString(effectiveSearch)
                prefs[intPreferencesKey(DAY_INDEX_KEY)] = 0
                prefs[stringPreferencesKey(LAST_DATE_KEY)] = app.what.foundation.utils.currentLocalDate().toString()
                prefs[longPreferencesKey("theme_timestamp")] = System.currentTimeMillis()
            }
            try {
                ScheduleWidget().update(applicationContext, glanceId)
            } catch (_: Exception) {}
            try {
                ScheduleWidget.instance.updateAll(applicationContext)
            } catch (_: Exception) {}

            // Явно посылаем broadcast ресиверу виджета, чтобы система Android гарантированно вызвала onUpdate
            val updateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                component = android.content.ComponentName(applicationContext, ScheduleWidgetReceiver::class.java)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            }
            applicationContext.sendBroadcast(updateIntent)
        } catch (e: Exception) {
            val tag = app.what.foundation.utils.buildTag(app.what.foundation.utils.LogScope.WIDGET, app.what.foundation.utils.LogCat.UI)
            app.what.foundation.services.AppLogger.Auditor.err(tag, "Ошибка обновления виджета", e)
        }

        // Возвращаем Intent с EXTRA_APPWIDGET_ID — обязательно по Android API
        val resultIntent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}