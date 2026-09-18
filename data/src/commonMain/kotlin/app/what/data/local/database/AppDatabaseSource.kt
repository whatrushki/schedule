package app.what.schedule.data.local.database

interface AppDatabaseSource {
    val lessonsDao: LessonDAO
    val otUnitsDao: OtUnitDAO
    val groupsDao: GroupsDAO
    val teachersDao: TeachersDAO
    val requestsDao: RequestsDAO
    val daySchedulesDao: DayScheduleDAO
}
