package app.what.data.adapters

import app.what.data.mappers.toDomain
import app.what.data.mappers.toDomainResponse
import app.what.domain.models.Group
import app.what.domain.models.NewItem
import app.what.domain.models.NewListItem
import app.what.domain.models.ScheduleResponse
import app.what.domain.models.Teacher
import app.what.data.remote.CloudScheduleClient
import app.what.foundation.services.AppLogger.Companion.Auditor
import app.what.foundation.utils.LogCat
import app.what.foundation.utils.LogScope
import app.what.foundation.utils.buildTag
import app.what.schedule.core.clients.NewsClient
import app.what.schedule.core.clients.ScheduleClient
import app.what.schedule.data.remote.api.AdditionalData
import app.what.schedule.data.remote.api.NewsService
import app.what.schedule.data.remote.api.ScheduleService

class AdaptedScheduleService(
    private val client: ScheduleClient,
    private val cloudClient: ScheduleClient? = null
) : ScheduleService {

    private val tag = buildTag(LogScope.NETWORK, LogCat.NET)

    override suspend fun getGroupSchedule(
        group: String,
        showReplacements: Boolean,
        additional: AdditionalData
    ): ScheduleResponse {
        val forceLive = additional["forceLive"] == true
        val cloud = cloudClient as? CloudScheduleClient
        val isCloudHealthy = cloud?.isSyncHealthy() ?: (cloudClient != null)

        if (!forceLive && cloudClient != null) {
            if (isCloudHealthy) {
                try {
                    val cloudSchedules = cloudClient.getGroupSchedule(group, showReplacements)
                    if (cloudSchedules.isNotEmpty()) {
                        Auditor.debug(tag, "[AdaptedScheduleService] Loaded schedule for group $group from cloud source")
                        return cloudSchedules.toDomainResponse()
                    }
                } catch (e: Exception) {
                    Auditor.warn(tag, "[AdaptedScheduleService] Cloud schedule fetch failed for group $group, falling back to live parser: ${e.message}")
                }
            } else {
                Auditor.warn(tag, "[AdaptedScheduleService] Cloud sync is UNHEALTHY or outdated for group $group, skipping cloud and using live parser")
            }
        }

        return try {
            client.getGroupSchedule(group, showReplacements).toDomainResponse()
        } catch (e: Exception) {
            if (cloudClient != null) {
                try {
                    val cloudSchedules = cloudClient.getGroupSchedule(group, showReplacements)
                    if (cloudSchedules.isNotEmpty()) {
                        Auditor.debug(tag, "[AdaptedScheduleService] Live failed, loaded schedule for group $group from cloud fallback")
                        return cloudSchedules.toDomainResponse()
                    }
                } catch (_: Exception) {}
            }
            ScheduleResponse.Error(null, null, e)
        }
    }

    override suspend fun getTeacherSchedule(
        teacher: String,
        showReplacements: Boolean,
        additional: AdditionalData
    ): ScheduleResponse {
        val forceLive = additional["forceLive"] == true
        val cloud = cloudClient as? CloudScheduleClient
        val isCloudHealthy = cloud?.isSyncHealthy() ?: (cloudClient != null)

        if (!forceLive && cloudClient != null) {
            if (isCloudHealthy) {
                try {
                    val cloudSchedules = cloudClient.getTeacherSchedule(teacher, showReplacements)
                    if (cloudSchedules.isNotEmpty()) {
                        Auditor.debug(tag, "[AdaptedScheduleService] Loaded schedule for teacher $teacher from cloud source")
                        return cloudSchedules.mergeTeacherLessons().toDomainResponse()
                    }
                } catch (e: Exception) {
                    Auditor.warn(tag, "[AdaptedScheduleService] Cloud schedule fetch failed for teacher $teacher, falling back to live parser: ${e.message}")
                }
            } else {
                Auditor.warn(tag, "[AdaptedScheduleService] Cloud sync is UNHEALTHY or outdated for teacher $teacher, skipping cloud and using live parser")
            }
        }

        return try {
            client.getTeacherSchedule(teacher, showReplacements).mergeTeacherLessons().toDomainResponse()
        } catch (e: Exception) {
            if (cloudClient != null) {
                try {
                    val cloudSchedules = cloudClient.getTeacherSchedule(teacher, showReplacements)
                    if (cloudSchedules.isNotEmpty()) {
                        Auditor.debug(tag, "[AdaptedScheduleService] Live failed, loaded schedule for teacher $teacher from cloud fallback")
                        return cloudSchedules.mergeTeacherLessons().toDomainResponse()
                    }
                } catch (_: Exception) {}
            }
            ScheduleResponse.Error(null, null, e)
        }
    }

    private fun List<app.what.schedule.core.models.DayScheduleDto>.mergeTeacherLessons(): List<app.what.schedule.core.models.DayScheduleDto> = map { day ->
        val merged = day.lessons
            .groupBy { it.number to it.startTime }
            .map { (_, groupLessons) ->
                if (groupLessons.size == 1) {
                    groupLessons.first()
                } else {
                    val first = groupLessons.first()
                    val state = when {
                        groupLessons.any { it.state == app.what.schedule.core.models.LessonStateDto.CHANGED } -> app.what.schedule.core.models.LessonStateDto.CHANGED
                        groupLessons.all { it.state == app.what.schedule.core.models.LessonStateDto.REMOVED } -> app.what.schedule.core.models.LessonStateDto.REMOVED
                        else -> first.state
                    }
                    val combinedUnits = groupLessons.flatMap { l ->
                        l.otUnits.map { u ->
                            if (u.subject.isNullOrBlank()) u.copy(subject = l.subject) else u
                        }
                    }.distinctBy { it.group to it.room to it.teacher to it.subject }

                    first.copy(
                        state = state,
                        otUnits = combinedUnits
                    )
                }
            }
            .sortedWith(compareBy({ it.startTime }, { it.number }))
        day.copy(lessons = merged)
    }

    override suspend fun getGroups(): List<Group> {
        if (cloudClient != null) {
            try {
                val cloudGroups = cloudClient.getGroups()
                if (cloudGroups.isNotEmpty()) {
                    Auditor.debug(tag, "[AdaptedScheduleService] Loaded ${cloudGroups.size} groups from cloud source")
                    return cloudGroups.map { it.toDomain() }
                }
            } catch (e: Exception) {
                Auditor.warn(tag, "[AdaptedScheduleService] Cloud groups fetch failed, falling back to live parser: ${e.message}")
            }
        }
        return try {
            client.getGroups().map { it.toDomain() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getTeachers(): List<Teacher> {
        if (cloudClient != null) {
            try {
                val cloudTeachers = cloudClient.getTeachers()
                if (cloudTeachers.isNotEmpty()) {
                    Auditor.debug(tag, "[AdaptedScheduleService] Loaded ${cloudTeachers.size} teachers from cloud source")
                    return cloudTeachers.map { it.toDomain() }
                }
            } catch (e: Exception) {
                Auditor.warn(tag, "[AdaptedScheduleService] Cloud teachers fetch failed, falling back to live parser: ${e.message}")
            }
        }
        return try {
            client.getTeachers().map { it.toDomain() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun clearCache() {
        (cloudClient as? CloudScheduleClient)?.clearCache()
    }
}

class AdaptedNewsService(
    private val client: NewsClient?
) : NewsService {

    override suspend fun getNews(page: Int): List<NewListItem> = try {
        client?.getNews(page)?.map { it.toDomain() } ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    override suspend fun getNewDetail(id: String): NewItem =
        client?.getNewDetail(id)?.toDomain() ?: error("News not found: $id")
}
