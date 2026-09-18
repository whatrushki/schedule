package app.what.schedule.data.remote.api

import app.what.domain.models.*
import app.what.foundation.core.Feature
import app.what.foundation.core.UIComponent
import app.what.schedule.data.local.settings.AppValues
import app.what.schedule.data.remote.providers.dgtu.DGTU
import app.what.schedule.data.remote.providers.iubip.IUBIP
import app.what.schedule.data.remote.providers.rinh.RINH
import app.what.schedule.data.remote.providers.rksi.RKSI
import app.what.schedule.data.remote.providers.sfedu.SFEDU
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

typealias AdditionalData = Map<String, Any?>

interface Institution {
    interface Factory {
        val metadata: MetaInfo
        fun create(): Institution
    }
    
    val metadata: MetaInfo
    
    val scheduleService: ScheduleService
    val newsService: NewsService
    
    val accountFeature: Feature<*, *>?
    
    fun generateFileName(
        additional: AdditionalData,
        fileExtension: String
    ) = "${metadata.id}_${
        additional.map { (k, v) -> "$k&$v" }.joinToString("_")
    }.$fileExtension"
}

interface ScheduleService {
    suspend fun getGroupSchedule(
        group: String,
        showReplacements: Boolean = true,
        additional: AdditionalData = emptyMap()
    ): ScheduleResponse
    
    suspend fun getTeacherSchedule(
        teacher: String,
        showReplacements: Boolean = true,
        additional: AdditionalData = emptyMap()
    ): ScheduleResponse
    
    suspend fun getGroups(): List<Group>
    suspend fun getTeachers(): List<Teacher>
}

interface NewsService {
    suspend fun getNews(page: Int): List<NewListItem> = emptyList()
    suspend fun getNewDetail(id: String): NewItem {
        error("Not implemented")
    }
}

interface AccountService {
    val ui: UIComponent
}

val insts: List<Institution.Factory> by lazy {
    listOf(
        RKSI.Factory,
        DGTU.Factory,
        RINH.Factory,
        IUBIP.Factory,
        SFEDU.Factory
    )
}

class InstitutionManager(
    private val settings: AppValues,
    private val scope: CoroutineScope,
    private val institutions: List<Institution.Factory> = insts
) {
    init {
        actualize()
        scope.launch {
            settings.institution.observe().collect {
                actualize()
            }
        }
    }
    
    fun getInstitutions(): List<Institution.Factory> = institutions
    
    fun save(institutionId: String) {
        settings.institution.set(institutionId)
        actualize()
    }
    
    private fun actualize() {
        val savedData = settings.institution.get()
        savedInstitution = institutions.firstOrNull { it.metadata.id == savedData }?.create()
    }
    
    private var savedInstitution: Institution? = null
    fun getSavedInstitution(): Institution? = savedInstitution
    
    fun reset() {
        savedInstitution = null
        settings.institution.set(null)
    }
}