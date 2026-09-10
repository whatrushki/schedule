package app.what.schedule.data.local.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters

@Database(
    entities = [
        LessonDBO::class,
        OneTimeUnitDBO::class,
        GroupDBO::class,
        TeacherDBO::class,
        DayScheduleDBO::class,
        RequestDBO::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract val lessonsDao: LessonDAO
    abstract val otUnitsDao: OtUnitDAO
    abstract val groupsDao: GroupsDAO
    abstract val teachersDao: TeachersDAO
    abstract val requestsDao: RequestsDAO
    abstract val daySchedulesDao: DayScheduleDAO
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}