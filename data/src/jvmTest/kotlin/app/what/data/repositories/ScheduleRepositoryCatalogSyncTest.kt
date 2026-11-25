package app.what.data.repositories

import app.what.domain.models.DaySchedule
import app.what.domain.models.Group
import app.what.domain.models.Lesson
import app.what.domain.models.LessonState
import app.what.domain.models.LessonType
import app.what.domain.models.LessonsScheduleType
import app.what.domain.models.MetaInfo
import app.what.domain.models.OneTimeUnit
import app.what.domain.models.ScheduleResponse
import app.what.domain.models.ScheduleSearch
import app.what.domain.models.Teacher
import app.what.foundation.core.Feature
import app.what.foundation.data.settings.MemoryKeyValueStorage
import app.what.schedule.data.local.database.AppDatabaseSource
import app.what.schedule.data.local.database.DayScheduleDAO
import app.what.schedule.data.local.database.DayScheduleDBO
import app.what.schedule.data.local.database.DayScheduleSDBO
import app.what.schedule.data.local.database.GroupDBO
import app.what.schedule.data.local.database.GroupsDAO
import app.what.schedule.data.local.database.LessonDAO
import app.what.schedule.data.local.database.LessonDBO
import app.what.schedule.data.local.database.LessonSDBO
import app.what.schedule.data.local.database.OneTimeUnitDBO
import app.what.schedule.data.local.database.OtUnitDAO
import app.what.schedule.data.local.database.OtUnitSDBO
import app.what.schedule.data.local.database.RequestDBO
import app.what.schedule.data.local.database.RequestSDBO
import app.what.schedule.data.local.database.RequestsDAO
import app.what.schedule.data.local.database.TeacherDBO
import app.what.schedule.data.local.database.TeachersDAO
import app.what.schedule.data.local.settings.AppValues
import app.what.schedule.data.remote.api.AdditionalData
import app.what.schedule.data.remote.api.Institution
import app.what.schedule.data.remote.api.InstitutionManager
import app.what.schedule.data.remote.api.NewsService
import app.what.schedule.data.remote.api.ScheduleService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduleRepositoryCatalogSyncTest {

    @Test
    fun `saveRequest does not pollute teachers catalog and sync preserves distinct provider ids`() = runBlocking {
        val db = FakeAppDatabaseSource()
        val settings = AppValues(MemoryKeyValueStorage())
        settings.institution.set("test_inst")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        var catalogTeachers = listOf(
            Teacher(id = "10", name = "Арутюнян А.А."),
            Teacher(id = "20", name = "Костромина В.А."),
            Teacher(id = "21", name = "Костромина В.А")
        )
        val catalogGroups = listOf(
            Group(id = "g1", name = "ИС-41", year = 4)
        )
        val groupScheduleDays = listOf(
            DaySchedule(
                date = LocalDate(2026, 11, 25),
                scheduleType = LessonsScheduleType.COMMON,
                lessons = listOf(
                    Lesson(
                        date = LocalDate(2026, 11, 25),
                        number = 1,
                        startTime = LocalTime(8, 0),
                        endTime = LocalTime(9, 30),
                        subject = "Математика",
                        type = LessonType.LECTURE,
                        state = LessonState.COMMON,
                        otUnits = listOf(
                            OneTimeUnit(
                                group = Group(id = "g1", name = "ИС-41"),
                                // Full name coming from group schedule instead of catalog short name
                                teacher = Teacher(id = "", name = "Арутюнян Андраник Андреевич"),
                                auditory = "101",
                                building = "1"
                            )
                        )
                    )
                )
            )
        )

        val fakeMeta = MetaInfo(
            id = "test_inst",
            name = "Test",
            fullName = "Test Institution",
            description = "",
            sourceTypes = emptySet(),
            sourceUrl = ""
        )

        val fakeFactory = object : Institution.Factory {
            override val metadata = fakeMeta

            override fun create(): Institution = object : Institution {
                override val metadata = fakeMeta
                override val newsService = object : NewsService {}
                override val accountFeature: Feature<*, *>? = null
                override val scheduleService = object : ScheduleService {
                    override suspend fun getGroupSchedule(
                        group: String,
                        showReplacements: Boolean,
                        additional: AdditionalData
                    ): ScheduleResponse = ScheduleResponse.Available.FromSource(
                        schedules = groupScheduleDays,
                        lastModified = LocalDateTime(2026, 11, 25, 10, 0)
                    )

                    override suspend fun getTeacherSchedule(
                        teacher: String,
                        showReplacements: Boolean,
                        additional: AdditionalData
                    ): ScheduleResponse = ScheduleResponse.Empty

                    override suspend fun getGroups(): List<Group> = catalogGroups
                    override suspend fun getTeachers(): List<Teacher> = catalogTeachers
                }
            }
        }

        // Pre-populate stale full-name pollution & a favorite teacher from an older app version
        val oldId1 = db.nextId()
        db.teachers[oldId1] = TeacherDBO(
            id = oldId1,
            institutionId = "test_inst",
            name = "Арутюнян Андраник Андреевич",
            teacherId = "Арутюнян Андраник Андреевич",
            favorite = false,
            inCatalog = true
        )
        val oldId2 = db.nextId()
        db.teachers[oldId2] = TeacherDBO(
            id = oldId2,
            institutionId = "test_inst",
            name = "Арутюнян А.А.",
            teacherId = "10",
            favorite = true,
            inCatalog = true
        )

        val institutionManager = InstitutionManager(settings, scope, listOf(fakeFactory))
        val repo = ScheduleRepositoryImpl(db, institutionManager, scope)

        // 1. Load group schedule first — saveRequest writes OneTimeUnit with full name teacher
        val scheduleResp = repo.getSchedule(
            ScheduleSearch.Group(id = "g1", name = "ИС-41", institutionId = "test_inst"),
            useCache = false,
            requiresData = true
        )
        assertTrue(scheduleResp is ScheduleResponse.Available)

        // 2. Fetch catalog teachers
        val teachers = repo.getTeachers(institutionId = "test_inst", forceReload = true)

        assertEquals(3, teachers.size)
        assertTrue(teachers.any { it.id == "10" && it.name == "Арутюнян А.А." && it.favorite })
        assertTrue(teachers.any { it.id == "20" && it.name == "Костромина В.А." })
        assertTrue(teachers.any { it.id == "21" && it.name == "Костромина В.А" })
        assertFalse(teachers.any { it.name == "Арутюнян Андраник Андреевич" })

        // 3. Re-saving group schedule again must NOT put full name back into catalog
        repo.getSchedule(
            ScheduleSearch.Group(id = "g1", name = "ИС-41", institutionId = "test_inst"),
            useCache = false,
            requiresData = true
        )
        val teachersAfterSave = repo.getTeachers(institutionId = "test_inst", forceReload = false)
        assertEquals(3, teachersAfterSave.size)
        assertFalse(teachersAfterSave.any { it.name == "Арутюнян Андраник Андреевич" })
    }
}

private class FakeAppDatabaseSource : AppDatabaseSource {
    val requests = mutableMapOf<Long, RequestDBO>()
    val daySchedules = mutableMapOf<Long, DayScheduleDBO>()
    val lessons = mutableMapOf<Long, LessonDBO>()
    val otUnits = mutableMapOf<Long, OneTimeUnitDBO>()
    val groups = mutableMapOf<Long, GroupDBO>()
    val teachers = mutableMapOf<Long, TeacherDBO>()
    private var idSeq = 10L
    fun nextId(): Long = idSeq++

    override val requestsDao = object : RequestsDAO {
        override suspend fun insert(request: RequestDBO): Long {
            val id = if (request.id == 0L) nextId() else request.id
            requests[id] = request.copy(id = id)
            return id
        }
        override suspend fun insert(requests: List<RequestDBO>) { requests.forEach { insert(it) } }
        override suspend fun selectAll() = requests.values.toList()
        override suspend fun selectLastOfInstitution(institutionId: String) =
            requests.values.filter { it.institutionId == institutionId }.maxByOrNull { it.id }
        override suspend fun selectLastWithData(institutionId: String, query: String, afterDate: LocalDate) =
            selectLast(institutionId, query, afterDate)?.let { buildReq(it) }
        override suspend fun selectLastWithData(id: Long) = requests[id]?.let { buildReq(it) }
        override suspend fun selectLast(institutionId: String, query: String, afterDate: LocalDate) =
            requests.values.filter { it.institutionId == institutionId && it.query == query && it.createdAt > afterDate }.maxByOrNull { it.id }
        override suspend fun update(request: RequestDBO) { requests[request.id] = request }
        override suspend fun deleteOld(currentDate: LocalDate) { requests.entries.removeAll { it.value.createdAt < currentDate } }
        override suspend fun delete(request: RequestDBO) { requests.remove(request.id) }
        override suspend fun deleteOldRequests(institutionId: String, query: String, newRequestId: Long) {
            requests.entries.removeAll { it.value.institutionId == institutionId && it.value.query == query && it.key < newRequestId }
        }
        override suspend fun deleteAll(institutionId: String, query: String) {
            requests.entries.removeAll { it.value.institutionId == institutionId && it.value.query == query }
        }
        override suspend fun clearAll() { requests.clear() }
        private fun buildReq(req: RequestDBO): RequestSDBO {
            val days = daySchedules.values.filter { it.fromRequest == req.id }.map { d ->
                val ls = lessons.values.filter { it.fromDay == d.id }.map { l ->
                    val us = otUnits.values.filter { it.lessonId == l.id }.mapNotNull { u ->
                        val g = groups[u.groupId] ?: return@mapNotNull null
                        val t = teachers[u.teacherId] ?: return@mapNotNull null
                        OtUnitSDBO(u, g, t)
                    }
                    LessonSDBO(l, us)
                }
                DayScheduleSDBO(d, ls)
            }
            return RequestSDBO(req, days)
        }
    }

    override val daySchedulesDao = object : DayScheduleDAO {
        override suspend fun insert(daySchedule: DayScheduleDBO): Long {
            val id = if (daySchedule.id == 0L) nextId() else daySchedule.id
            daySchedules[id] = daySchedule.copy(id = id)
            return id
        }
        override suspend fun insert(daySchedules: List<DayScheduleDBO>) { daySchedules.forEach { insert(it) } }
        override suspend fun update(daySchedule: DayScheduleDBO) { daySchedules[daySchedule.id] = daySchedule }
        override suspend fun delete(daySchedule: DayScheduleDBO) { daySchedules.remove(daySchedule.id) }
        override suspend fun deleteOrphans() {}
        override suspend fun clearAll() { daySchedules.clear() }
    }

    override val lessonsDao = object : LessonDAO {
        override suspend fun insert(lesson: LessonDBO): Long {
            val id = if (lesson.id == 0L) nextId() else lesson.id
            lessons[id] = lesson.copy(id = id)
            return id
        }
        override suspend fun insert(lessons: List<LessonDBO>) { lessons.forEach { insert(it) } }
        override suspend fun update(lesson: LessonDBO) { lessons[lesson.id] = lesson }
        override suspend fun delete(lesson: LessonDBO) { lessons.remove(lesson.id) }
        override suspend fun deleteOrphans() {}
        override suspend fun clearAll() { lessons.clear() }
    }

    override val otUnitsDao = object : OtUnitDAO {
        override suspend fun insert(unit: OneTimeUnitDBO): Long {
            val id = if (unit.id == 0L) nextId() else unit.id
            otUnits[id] = unit.copy(id = id)
            return id
        }
        override suspend fun insert(unit: List<OneTimeUnitDBO>) { unit.forEach { insert(it) } }
        override suspend fun update(unit: OneTimeUnitDBO) { otUnits[unit.id] = unit }
        override suspend fun delete(unit: OneTimeUnitDBO) { otUnits.remove(unit.id) }
        override suspend fun deleteOrphans() {}
        override suspend fun clearAll() { otUnits.clear() }
    }

    override val groupsDao = object : GroupsDAO {
        override suspend fun insert(group: GroupDBO): Long {
            val existing = groups.values.find { it.institutionId == group.institutionId && it.groupId == group.groupId }
            if (existing != null) return -1L
            val id = if (group.id == 0L) nextId() else group.id
            groups[id] = group.copy(id = id)
            return id
        }
        override suspend fun insert(group: List<GroupDBO>) { group.forEach { insert(it) } }
        override suspend fun update(group: GroupDBO) { groups[group.id] = group }
        override suspend fun update(groups: List<GroupDBO>) { groups.forEach { update(it) } }
        override suspend fun selectByInstitution(institutionId: String) = groups.values.filter { it.institutionId == institutionId }
        override suspend fun selectCatalogByInstitution(institutionId: String) =
            groups.values.filter { it.institutionId == institutionId && it.inCatalog }.sortedBy { it.name }
        override suspend fun selectById(id: Long) = groups[id]!!
        override suspend fun selectByGroupId(institutionId: String, id: String) =
            groups.values.find { it.institutionId == institutionId && it.groupId == id }
        override suspend fun selectIdByGroupId(institutionId: String, id: String) =
            selectByGroupId(institutionId, id)?.id
        override suspend fun selectByName(institutionId: String, name: String) =
            groups.values.filter { it.institutionId == institutionId && it.name == name }.sortedByDescending { it.inCatalog }.firstOrNull()
        override suspend fun selectAllFavorites() = groups.values.filter { it.favorite }
        override suspend fun selectByYear(year: Int) = groups.values.first { it.year == year }
        override suspend fun delete(group: GroupDBO) { groups.remove(group.id) }
    }

    override val teachersDao = object : TeachersDAO {
        override suspend fun insert(teacher: TeacherDBO): Long {
            val existing = teachers.values.find { it.institutionId == teacher.institutionId && it.teacherId == teacher.teacherId }
            if (existing != null) return -1L
            val id = if (teacher.id == 0L) nextId() else teacher.id
            teachers[id] = teacher.copy(id = id)
            return id
        }
        override suspend fun insert(teacher: List<TeacherDBO>) { teacher.forEach { insert(it) } }
        override suspend fun update(teacher: TeacherDBO) { teachers[teacher.id] = teacher }
        override suspend fun update(teachers: List<TeacherDBO>) { teachers.forEach { update(it) } }
        override suspend fun selectByInstitution(institutionId: String) = teachers.values.filter { it.institutionId == institutionId }
        override suspend fun selectCatalogByInstitution(institutionId: String) =
            teachers.values.filter { it.institutionId == institutionId && it.inCatalog }.sortedBy { it.name }
        override suspend fun selectAllFavorites() = teachers.values.filter { it.favorite }
        override suspend fun selectById(id: Long) = teachers[id]!!
        override suspend fun selectByTeacherId(institutionId: String, id: String) =
            teachers.values.find { it.institutionId == institutionId && it.teacherId == id }
        override suspend fun selectIdByTeacherId(institutionId: String, id: String) =
            selectByTeacherId(institutionId, id)?.id
        override suspend fun selectByName(institutionId: String, name: String) =
            teachers.values.filter { it.institutionId == institutionId && it.name == name }.sortedByDescending { it.inCatalog }.firstOrNull()
        override suspend fun delete(teacher: TeacherDBO) { teachers.remove(teacher.id) }
    }
}
