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
abstract class AppDatabase : RoomDatabase(), AppDatabaseSource {
    abstract override val lessonsDao: LessonDAO
    abstract override val otUnitsDao: OtUnitDAO
    abstract override val groupsDao: GroupsDAO
    abstract override val teachersDao: TeachersDAO
    abstract override val requestsDao: RequestsDAO
    abstract override val daySchedulesDao: DayScheduleDAO
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}