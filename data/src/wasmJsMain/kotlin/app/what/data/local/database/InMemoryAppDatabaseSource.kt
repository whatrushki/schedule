package app.what.schedule.data.local.database

import kotlinx.datetime.LocalDate

class InMemoryAppDatabaseSource : AppDatabaseSource {
    override val lessonsDao: LessonDAO = InMemoryLessonDAO()
    override val otUnitsDao: OtUnitDAO = InMemoryOtUnitDAO()
    override val groupsDao: GroupsDAO = InMemoryGroupsDAO()
    override val teachersDao: TeachersDAO = InMemoryTeachersDAO()
    override val requestsDao: RequestsDAO = InMemoryRequestsDAO(this)
    override val daySchedulesDao: DayScheduleDAO = InMemoryDayScheduleDAO(this)

    val requests = mutableMapOf<Long, RequestDBO>()
    val daySchedules = mutableMapOf<Long, DayScheduleDBO>()
    val lessons = mutableMapOf<Long, LessonDBO>()
    val otUnits = mutableMapOf<Long, OneTimeUnitDBO>()
    val groups = mutableMapOf<Long, GroupDBO>()
    val teachers = mutableMapOf<Long, TeacherDBO>()
    private var idCounter = 1L
    fun nextId(): Long = idCounter++
}

private class InMemoryRequestsDAO(private val db: InMemoryAppDatabaseSource) : RequestsDAO {
    override suspend fun insert(request: RequestDBO): Long {
        val id = if (request.id == 0L) db.nextId() else request.id
        val saved = request.copy(id = id)
        db.requests[id] = saved
        return id
    }

    override suspend fun insert(requests: List<RequestDBO>) {
        requests.forEach { insert(it) }
    }

    override suspend fun selectAll(): List<RequestDBO> = db.requests.values.toList()

    override suspend fun selectLastOfInstitution(institutionId: String): RequestDBO? =
        db.requests.values.filter { it.institutionId == institutionId }.maxByOrNull { it.id }

    override suspend fun selectLastWithData(institutionId: String, query: String, afterDate: LocalDate): RequestSDBO? {
        val request = selectLast(institutionId, query, afterDate) ?: return null
        return buildRequestSDBO(request)
    }

    override suspend fun selectLastWithData(id: Long): RequestSDBO? {
        val request = db.requests[id] ?: return null
        return buildRequestSDBO(request)
    }

    private fun buildRequestSDBO(request: RequestDBO): RequestSDBO {
        val days = db.daySchedules.values.filter { it.fromRequest == request.id }.sortedBy { it.id }
        val daySchedulesWithLessons = days.map { day ->
            val dayLessons = db.lessons.values.filter { it.fromDay == day.id }.sortedBy { it.id }
            val lessonSDBOs = dayLessons.map { lesson ->
                val units = db.otUnits.values.filter { it.lessonId == lesson.id }.sortedBy { it.id }
                val otUnitSDBOs = units.mapNotNull { unit ->
                    val group = db.groups[unit.groupId] ?: return@mapNotNull null
                    val teacher = db.teachers[unit.teacherId] ?: return@mapNotNull null
                    OtUnitSDBO(unit = unit, group = group, teacher = teacher)
                }
                LessonSDBO(lesson = lesson, otUnits = otUnitSDBOs)
            }
            DayScheduleSDBO(daySchedule = day, lessons = lessonSDBOs)
        }
        return RequestSDBO(request = request, daySchedules = daySchedulesWithLessons)
    }

    override suspend fun selectLast(institutionId: String, query: String, afterDate: LocalDate): RequestDBO? =
        db.requests.values
            .filter { it.institutionId == institutionId && it.query == query && it.createdAt > afterDate }
            .maxByOrNull { it.id }

    override suspend fun update(request: RequestDBO) {
        db.requests[request.id] = request
    }

    override suspend fun deleteOld(currentDate: LocalDate) {
        db.requests.entries.removeAll { it.value.createdAt < currentDate }
    }

    override suspend fun delete(request: RequestDBO) {
        db.requests.remove(request.id)
    }

    override suspend fun deleteAll(institutionId: String, query: String) {
        db.requests.entries.removeAll { it.value.institutionId == institutionId && it.value.query == query }
    }
}

private class InMemoryDayScheduleDAO(private val db: InMemoryAppDatabaseSource) : DayScheduleDAO {
    override suspend fun insert(daySchedule: DayScheduleDBO): Long {
        val id = if (daySchedule.id == 0L) db.nextId() else daySchedule.id
        val saved = daySchedule.copy(id = id)
        db.daySchedules[id] = saved
        return id
    }

    override suspend fun insert(daySchedules: List<DayScheduleDBO>) {
        daySchedules.forEach { insert(it) }
    }

    override suspend fun update(daySchedule: DayScheduleDBO) {
        db.daySchedules[daySchedule.id] = daySchedule
    }

    override suspend fun delete(daySchedule: DayScheduleDBO) {
        db.daySchedules.remove(daySchedule.id)
    }
}

private class InMemoryLessonDAO : LessonDAO {
    private val lessons = mutableMapOf<Long, LessonDBO>()
    private var idCounter = 1L
    override suspend fun insert(lesson: LessonDBO): Long {
        val id = if (lesson.id == 0L) idCounter++ else lesson.id
        lessons[id] = lesson.copy(id = id)
        return id
    }

    override suspend fun insert(lessons: List<LessonDBO>) {
        lessons.forEach { insert(it) }
    }

    override suspend fun update(lesson: LessonDBO) {
        lessons[lesson.id] = lesson
    }

    override suspend fun delete(lesson: LessonDBO) {
        lessons.remove(lesson.id)
    }
}

private class InMemoryOtUnitDAO : OtUnitDAO {
    private val units = mutableMapOf<Long, OneTimeUnitDBO>()
    private var idCounter = 1L
    override suspend fun insert(unit: OneTimeUnitDBO): Long {
        val id = if (unit.id == 0L) idCounter++ else unit.id
        units[id] = unit.copy(id = id)
        return id
    }

    override suspend fun insert(unit: List<OneTimeUnitDBO>) {
        unit.forEach { insert(it) }
    }

    override suspend fun update(unit: OneTimeUnitDBO) {
        units[unit.id] = unit
    }

    override suspend fun delete(unit: OneTimeUnitDBO) {
        units.remove(unit.id)
    }
}

private class InMemoryGroupsDAO : GroupsDAO {
    private val groups = mutableMapOf<Long, GroupDBO>()
    private var idCounter = 1L
    override suspend fun insert(group: GroupDBO): Long {
        val existing = groups.values.find { it.institutionId == group.institutionId && it.groupId == group.groupId }
        if (existing != null) return existing.id
        val id = if (group.id == 0L) idCounter++ else group.id
        groups[id] = group.copy(id = id)
        return id
    }

    override suspend fun insert(group: List<GroupDBO>) {
        group.forEach { insert(it) }
    }

    override suspend fun update(group: GroupDBO) {
        groups[group.id] = group
    }

    override suspend fun selectByInstitution(institutionId: String): List<GroupDBO> =
        groups.values.filter { it.institutionId == institutionId }

    override suspend fun selectById(id: Long): GroupDBO = groups[id] ?: error("Group not found")

    override suspend fun selectByGroupId(institutionId: String, id: String): GroupDBO? =
        groups.values.find { it.institutionId == institutionId && it.groupId == id }

    override suspend fun selectIdByGroupId(institutionId: String, id: String): Long? =
        groups.values.find { it.institutionId == institutionId && it.groupId == id }?.id

    override suspend fun selectByName(institutionId: String, name: String): GroupDBO? =
        groups.values.find { it.institutionId == institutionId && it.name == name }

    override suspend fun selectByYear(year: Int): GroupDBO =
        groups.values.first { it.year == year }

    override suspend fun delete(group: GroupDBO) {
        groups.remove(group.id)
    }
}

private class InMemoryTeachersDAO : TeachersDAO {
    private val teachers = mutableMapOf<Long, TeacherDBO>()
    private var idCounter = 1L
    override suspend fun insert(teacher: TeacherDBO): Long {
        val existing = teachers.values.find { it.institutionId == teacher.institutionId && it.teacherId == teacher.teacherId }
        if (existing != null) return existing.id
        val id = if (teacher.id == 0L) idCounter++ else teacher.id
        teachers[id] = teacher.copy(id = id)
        return id
    }

    override suspend fun insert(teacher: List<TeacherDBO>) {
        teacher.forEach { insert(it) }
    }

    override suspend fun update(teacher: TeacherDBO) {
        teachers[teacher.id] = teacher
    }

    override suspend fun selectByInstitution(institutionId: String): List<TeacherDBO> =
        teachers.values.filter { it.institutionId == institutionId }

    override suspend fun selectById(id: Long): TeacherDBO = teachers[id] ?: error("Teacher not found")

    override suspend fun selectByTeacherId(institutionId: String, id: String): TeacherDBO? =
        teachers.values.find { it.institutionId == institutionId && it.teacherId == id }

    override suspend fun selectIdByTeacherId(institutionId: String, id: String): Long? =
        teachers.values.find { it.institutionId == institutionId && it.teacherId == id }?.id

    override suspend fun selectByName(institutionId: String, name: String): TeacherDBO? =
        teachers.values.find { it.institutionId == institutionId && it.name == name }

    override suspend fun delete(teacher: TeacherDBO) {
        teachers.remove(teacher.id)
    }
}
