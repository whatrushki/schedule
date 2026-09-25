package app.what.data

import app.what.data.adapters.AdaptedScheduleService
import app.what.data.remote.CloudScheduleClient
import app.what.domain.models.ScheduleResponse
import app.what.schedule.core.clients.ScheduleClient
import app.what.schedule.core.models.DayScheduleDto
import app.what.schedule.core.models.GroupDto
import app.what.schedule.core.models.TeacherDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class CloudScheduleClientTest {

    private val httpClient = HttpClient(CIO)

    @Test
    fun testCloudClientRksi() = runBlocking {
        val client = CloudScheduleClient("rksi", httpClient)
        val groups = client.getGroups()
        println("Fetched ${groups.size} RKSI groups from cloud")
        assertTrue(groups.isNotEmpty(), "RKSI groups should not be empty")

        val teachers = client.getTeachers()
        println("Fetched ${teachers.size} RKSI teachers from cloud")
        assertTrue(teachers.isNotEmpty(), "RKSI teachers should not be empty")

        val schedule = client.getGroupSchedule("БД-11", true)
        println("Fetched ${schedule.size} days for group БД-11")
        assertTrue(schedule.isNotEmpty(), "Schedule for БД-11 should not be empty")
        val totalLessons = schedule.sumOf { it.lessons.size }
        println("Total lessons for БД-11: $totalLessons")
        assertTrue(totalLessons > 0, "Lessons should not be empty")
    }

    @Test
    fun testCloudClientDgtu() = runBlocking {
        val client = CloudScheduleClient("dgtu", httpClient)
        val groups = client.getGroups()
        println("Fetched ${groups.size} DGTU groups from cloud")
        assertTrue(groups.isNotEmpty(), "DGTU groups should not be empty")
    }

    @Test
    fun testAdaptedScheduleServiceWithCloud() = runBlocking {
        val cloudClient = CloudScheduleClient("rksi", httpClient)
        val dummyLiveClient = object : ScheduleClient {
            override suspend fun getGroups(): List<GroupDto> = emptyList()
            override suspend fun getTeachers(): List<TeacherDto> = emptyList()
            override suspend fun getGroupSchedule(group: String, showReplacements: Boolean): List<DayScheduleDto> = emptyList()
            override suspend fun getTeacherSchedule(teacher: String, showReplacements: Boolean): List<DayScheduleDto> = emptyList()
        }

        val service = AdaptedScheduleService(dummyLiveClient, cloudClient)
        val groups = service.getGroups()
        assertTrue(groups.isNotEmpty(), "AdaptedScheduleService should return groups from cloud")

        val response = service.getGroupSchedule("БД-11", true)
        assertTrue(response is ScheduleResponse.Available.FromSource, "Should return Available.FromSource from cloud")
        println("AdaptedScheduleService loaded ${(response as ScheduleResponse.Available.FromSource).schedules.size} days")
    }
}
