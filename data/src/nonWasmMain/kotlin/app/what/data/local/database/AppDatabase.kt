package app.what.schedule.data.local.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `groups` ADD COLUMN inCatalog INTEGER NOT NULL DEFAULT 1")
        connection.execSQL("ALTER TABLE `teachers` ADD COLUMN inCatalog INTEGER NOT NULL DEFAULT 1")
        connection.execSQL("DROP INDEX IF EXISTS `index_groups_institutionId_name`")
        connection.execSQL("DROP INDEX IF EXISTS `index_teachers_institutionId_name`")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_groups_institutionId_name` ON `groups` (`institutionId`, `name`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_teachers_institutionId_name` ON `teachers` (`institutionId`, `name`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_groups_institutionId_inCatalog` ON `groups` (`institutionId`, `inCatalog`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_teachers_institutionId_inCatalog` ON `teachers` (`institutionId`, `inCatalog`)")
    }
}

@Database(
    entities = [
        LessonDBO::class,
        OneTimeUnitDBO::class,
        GroupDBO::class,
        TeacherDBO::class,
        DayScheduleDBO::class,
        RequestDBO::class
    ],
    version = 6,
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