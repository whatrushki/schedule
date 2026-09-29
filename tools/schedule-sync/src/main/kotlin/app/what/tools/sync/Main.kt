package app.what.tools.sync

import app.what.schedule.core.models.DayScheduleDto
import app.what.schedule.core.models.GroupDto
import app.what.schedule.core.models.LessonDto
import app.what.schedule.core.models.LessonStateDto
import app.what.schedule.core.models.LessonsScheduleTypeDto
import app.what.schedule.core.models.TeacherDto
import app.what.schedule.dgtu.DGTUScheduleClient
import app.what.schedule.iubip.IUBIPScheduleClient
import app.what.schedule.rinh.RINHScheduleClient
import app.what.schedule.rksi.RKSILessonsSchedule
import app.what.schedule.rksi.RKSIScheduleClient
import app.what.schedule.rksi.parser.JvmXlsxReader
import app.what.schedule.rgups.RGUPSScheduleClient
import app.what.schedule.rgups_tuapse.RGUPSTuapseScheduleClient
import app.what.schedule.sfedu.SFEDUScheduleClient
import app.what.schedule.tvgu.TvGUScheduleClient
import com.fleeksoft.ksoup.Ksoup
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class InstitutionMeta(
    val lastSync: String,
    val institution: String,
    val groupCount: Int,
    val teacherCount: Int
)

val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun main(args: Array<String>) = runBlocking(Dispatchers.IO) {
    val targetIndex = args.indexOf("--target")
    val target = if (targetIndex in 0 until args.size - 1) args[targetIndex + 1] else "all"
    val outIndex = args.indexOf("--out")
    val outPath = if (outIndex in 0 until args.size - 1) args[outIndex + 1] else ".github/schedule"
    val outDir = File(outPath)
    outDir.mkdirs()

    val filterIndex = args.indexOf("--filter").takeIf { it != -1 } ?: args.indexOf("--group")
    val filter = if (filterIndex in 0 until args.size - 1) args[filterIndex + 1] else null

    val failOnError = args.contains("--fail-on-error")

    println("=== Starting Schedule Sync ===")
    println("Target: $target")
    println("Filter: ${filter ?: "none"}")
    println("Output: ${outDir.absolutePath}")
    println("Fail on error: $failOnError")

    val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 60_000
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    try {
        when (target.lowercase()) {
            "rksi" -> syncRksi(client, outDir, failOnError)
            "dgtu" -> syncDgtu(client, outDir, failOnError)
            "iubip" -> syncIubip(client, outDir, failOnError)
            "rinh" -> syncRinh(client, outDir, failOnError)
            "sfedu" -> syncSfedu(client, outDir, failOnError)
            "rgups" -> syncRgups(client, outDir, failOnError)
            "rgups_tuapse" -> syncRgupsTuapse(client, outDir, failOnError)
            "tvgu" -> syncTvgu(client, outDir, failOnError, filter)
            "others" -> syncOthers(client, outDir, failOnError)
            "all" -> {
                syncRksi(client, outDir, failOnError)
                syncOthers(client, outDir, failOnError)
            }
            else -> {
                println("Unknown target: $target. Use 'rksi', 'dgtu', 'iubip', 'rinh', 'sfedu', 'rgups', 'rgups_tuapse', 'tvgu', 'others', or 'all'.")
            }
        }
    } finally {
        client.close()
        println("=== Schedule Sync Finished ===")
    }
}

private fun normalizeName(name: String): String = name
    .replace(" ", "")
    .replace("-", "")
    .replace("—", "")
    .replace("–", "")
    .replace(".", "")
    .replace("c", "с", ignoreCase = true)
    .replace("a", "а", ignoreCase = true)
    .replace("e", "е", ignoreCase = true)
    .replace("o", "о", ignoreCase = true)
    .replace("p", "р", ignoreCase = true)
    .replace("x", "х", ignoreCase = true)
    .trim()
    .lowercase()

fun sanitizeFileName(name: String): String = name
    .replace(Regex("""[\\/:*?"<>|\r\n\t]"""), "_")
    .trim()
    .trimEnd('.')
    .ifEmpty { "unknown" }

suspend fun syncRksi(client: HttpClient, rootDir: File, failOnError: Boolean = false) {
    println("\n--- Syncing RKSI ---")
    val dir = File(rootDir, "rksi").apply { mkdirs() }
    val groupsDir = File(dir, "groups").apply { mkdirs() }
    val teachersDir = File(dir, "teachers").apply { mkdirs() }

    runCatching {
        val xlsxReader = JvmXlsxReader()
        val rksiClient = RKSIScheduleClient(
            client = client,
            xlsxReader = xlsxReader,
            log = { println("  [RKSI] $it") }
        )

        val nowStr = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString()
        println("  Fetching RKSI groups & teachers...")
        val groups = rksiClient.getGroups()
        val teachers = rksiClient.getTeachers()
        println("  Found ${groups.size} groups and ${teachers.size} teachers")
        if (groups.isEmpty()) {
            error("RKSI returned 0 groups")
        }

        File(dir, "groups.json").writeText(json.encodeToString(groups))
        File(dir, "teachers.json").writeText(json.encodeToString(teachers))

        var groupCount = 0
        var teacherCount = 0
        val semaphore = Semaphore(6)

        println("  Fetching RKSI group schedules with replacements...")
        coroutineScope {
            groups.map { group ->
                async {
                    semaphore.withPermit {
                        runCatching {
                            delay(20)
                            val schedule = rksiClient.getGroupSchedule(group.name, showReplacements = true)
                            if (schedule.isNotEmpty()) {
                                val safeName = group.name.replace("/", "_").replace("\\", "_")
                                File(groupsDir, "$safeName.json").writeText(json.encodeToString(schedule))
                                synchronized(groupsDir) { groupCount++ }
                            }
                        }.onFailure {
                            println("  [RKSI] Failed schedule for ${group.name}: ${it.message}")
                        }
                    }
                }
            }.awaitAll()
        }

        println("  Fetching RKSI teacher schedules with replacements...")
        coroutineScope {
            teachers.map { teacher ->
                async {
                    semaphore.withPermit {
                        runCatching {
                            delay(20)
                            val schedule = rksiClient.getTeacherSchedule(teacher.id, showReplacements = true)
                            if (schedule.isNotEmpty()) {
                                val safeId = teacher.id.ifEmpty { teacher.name }.replace("/", "_").replace("\\", "_")
                                File(teachersDir, "$safeId.json").writeText(json.encodeToString(schedule))
                                synchronized(teachersDir) { teacherCount++ }
                            }
                        }.onFailure {
                            println("  [RKSI] Failed teacher schedule for ${teacher.name} (${teacher.id}): ${it.message}")
                        }
                    }
                }
            }.awaitAll()
        }

        val meta = InstitutionMeta(
            lastSync = nowStr,
            institution = "rksi",
            groupCount = groups.size,
            teacherCount = teachers.size
        )
        File(dir, "meta.json").writeText(json.encodeToString(meta))
        println("  [RKSI] Sync complete! Saved $groupCount group and $teacherCount teacher schedules.")
    }.onFailure {
        println("  [RKSI] Error syncing: ${it.message}")
        it.printStackTrace()
        if (failOnError) throw it
    }
}

suspend fun syncOthers(client: HttpClient, rootDir: File, failOnError: Boolean = false) {
    syncDgtu(client, rootDir, failOnError)
    syncIubip(client, rootDir, failOnError)
    syncRinh(client, rootDir, failOnError)
    syncSfedu(client, rootDir, failOnError)
    syncRgups(client, rootDir, failOnError)
    syncRgupsTuapse(client, rootDir, failOnError)
    syncTvgu(client, rootDir, failOnError)
}

suspend fun syncDgtu(client: HttpClient, rootDir: File, failOnError: Boolean = false) {
    println("\n--- Syncing DGTU ---")
    val dir = File(rootDir, "dgtu").apply { mkdirs() }
    val groupsDir = File(dir, "groups").apply { mkdirs() }
    val teachersDir = File(dir, "teachers").apply { mkdirs() }

    runCatching {
        val dgtuClient = DGTUScheduleClient(client, log = { println("  [DGTU] $it") })
        val nowStr = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString()

        println("  Fetching DGTU groups & teachers...")
        val groups = dgtuClient.getGroups()
        val teachers = dgtuClient.getTeachers()
        println("  Found ${groups.size} groups and ${teachers.size} teachers")
        if (groups.isEmpty()) {
            error("DGTU returned 0 groups")
        }

        File(dir, "groups.json").writeText(json.encodeToString(groups))
        File(dir, "teachers.json").writeText(json.encodeToString(teachers))

        var groupCount = 0
        var teacherCount = 0
        val semaphore = Semaphore(6)

        println("  Fetching DGTU group schedules...")
        coroutineScope {
            groups.map { group ->
                async {
                    semaphore.withPermit {
                        runCatching {
                            delay(20)
                            val schedule = dgtuClient.getGroupSchedule(group.name)
                            if (schedule.isNotEmpty()) {
                                val safeName = group.name.replace("/", "_").replace("\\", "_")
                                File(groupsDir, "$safeName.json").writeText(json.encodeToString(schedule))
                                synchronized(groupsDir) { groupCount++ }
                            }
                        }.onFailure {
                            println("  [DGTU] Failed group schedule for ${group.name}: ${it.message}")
                        }
                    }
                }
            }.awaitAll()
        }

        println("  Fetching DGTU teacher schedules...")
        coroutineScope {
            teachers.map { teacher ->
                async {
                    semaphore.withPermit {
                        runCatching {
                            delay(20)
                            val schedule = dgtuClient.getTeacherSchedule(teacher.id)
                            if (schedule.isNotEmpty()) {
                                val safeId = teacher.id.ifEmpty { teacher.name }.replace("/", "_").replace("\\", "_")
                                File(teachersDir, "$safeId.json").writeText(json.encodeToString(schedule))
                                synchronized(teachersDir) { teacherCount++ }
                            }
                        }.onFailure {
                            println("  [DGTU] Failed teacher schedule for ${teacher.name} (${teacher.id}): ${it.message}")
                        }
                    }
                }
            }.awaitAll()
        }

        val meta = InstitutionMeta(
            lastSync = nowStr,
            institution = "dgtu",
            groupCount = groups.size,
            teacherCount = teachers.size
        )
        File(dir, "meta.json").writeText(json.encodeToString(meta))
        println("  [DGTU] Sync complete! Saved $groupCount group and $teacherCount teacher schedules.")
    }.onFailure {
        println("  [DGTU] Error syncing: ${it.message}")
        it.printStackTrace()
        if (failOnError) throw it
    }
}

suspend fun syncIubip(client: HttpClient, rootDir: File, failOnError: Boolean = false) {
    println("\n--- Syncing IUBIP ---")
    val dir = File(rootDir, "iubip").apply { mkdirs() }
    val groupsDir = File(dir, "groups").apply { mkdirs() }
    val teachersDir = File(dir, "teachers").apply { mkdirs() }

    runCatching {
        val iubipClient = IUBIPScheduleClient(client, log = { println("  [IUBIP] $it") })
        val nowStr = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString()

        println("  Fetching IUBIP groups & teachers...")
        val groups = iubipClient.getGroups()
        val teachers = iubipClient.getTeachers()
        println("  Found ${groups.size} groups and ${teachers.size} teachers")
        if (groups.isEmpty()) {
            error("IUBIP returned 0 groups")
        }

        File(dir, "groups.json").writeText(json.encodeToString(groups))
        File(dir, "teachers.json").writeText(json.encodeToString(teachers))

        val schedules = mutableMapOf<String, List<DayScheduleDto>>()
        val semaphore = Semaphore(5)

        coroutineScope {
            groups.map { group ->
                async {
                    semaphore.withPermit {
                        runCatching {
                            delay(30)
                            val schedule = iubipClient.getGroupSchedule(group.name)
                            if (schedule.isNotEmpty()) {
                                synchronized(schedules) {
                                    schedules[group.name] = schedule
                                }
                                val safeName = group.name.replace("/", "_").replace("\\", "_")
                                File(groupsDir, "$safeName.json").writeText(json.encodeToString(schedule))
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        // Extract teacher schedules from all parsed group schedules in memory
        var teacherCount = 0
        teachers.forEach { teacher ->
            val safeId = teacher.id.ifEmpty { teacher.name }.replace("/", "_").replace("\\", "_")
            val allDays = mutableMapOf<LocalDate, MutableList<app.what.schedule.core.models.LessonDto>>()
            schedules.values.forEach { days ->
                days.forEach { day ->
                    val teacherLessons = day.lessons.filter { lesson ->
                        lesson.otUnits.any { it.teacher.equals(teacher.name, ignoreCase = true) }
                    }
                    if (teacherLessons.isNotEmpty()) {
                        allDays.getOrPut(day.date) { mutableListOf() }.addAll(teacherLessons)
                    }
                }
            }
            val teacherSchedule = allDays.map { (date, lessons) ->
                DayScheduleDto(
                    date = date,
                    scheduleType = LessonsScheduleTypeDto.COMMON,
                    lessons = lessons.distinctBy { it.startTime to it.subject }
                        .sortedWith(compareBy({ it.startTime }, { it.number }))
                )
            }.sortedBy { it.date }

            if (teacherSchedule.isNotEmpty()) {
                File(teachersDir, "$safeId.json").writeText(json.encodeToString(teacherSchedule))
                teacherCount++
            }
        }

        val meta = InstitutionMeta(
            lastSync = nowStr,
            institution = "iubip",
            groupCount = groups.size,
            teacherCount = teachers.size
        )
        File(dir, "meta.json").writeText(json.encodeToString(meta))
        println("  [IUBIP] Sync complete! Saved ${schedules.size} group and $teacherCount teacher schedules.")
    }.onFailure {
        println("  [IUBIP] Error syncing: ${it.message}")
        it.printStackTrace()
        if (failOnError) throw it
    }
}

suspend fun syncRinh(client: HttpClient, rootDir: File, failOnError: Boolean = false) {
    println("\n--- Syncing RINH ---")
    val dir = File(rootDir, "rinh").apply { mkdirs() }
    val groupsDir = File(dir, "groups").apply { mkdirs() }
    val teachersDir = File(dir, "teachers").apply { mkdirs() }

    runCatching {
        val rinhClient = RINHScheduleClient(client, log = { println("  [RINH] $it") })
        val nowStr = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString()

        println("  Fetching RINH groups & teachers...")
        val groups = rinhClient.getGroups()
        val teachers = rinhClient.getTeachers()
        println("  Found ${groups.size} groups and ${teachers.size} teachers")
        if (groups.isEmpty()) {
            error("RINH returned 0 groups")
        }

        File(dir, "groups.json").writeText(json.encodeToString(groups))
        File(dir, "teachers.json").writeText(json.encodeToString(teachers))

        var groupCount = 0
        var teacherCount = 0
        val semaphore = Semaphore(5)

        coroutineScope {
            groups.map { group ->
                async {
                    semaphore.withPermit {
                        runCatching {
                            delay(30)
                            val schedule = rinhClient.getGroupSchedule(group.name)
                            if (schedule.isNotEmpty()) {
                                val safeName = group.name.replace("/", "_").replace("\\", "_")
                                File(groupsDir, "$safeName.json").writeText(json.encodeToString(schedule))
                                synchronized(groupsDir) { groupCount++ }
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        println("  Fetching RINH teacher schedules...")
        coroutineScope {
            teachers.map { teacher ->
                async {
                    semaphore.withPermit {
                        runCatching {
                            delay(30)
                            val schedule = rinhClient.getTeacherSchedule(teacher.name)
                            if (schedule.isNotEmpty()) {
                                val safeId = teacher.id.ifEmpty { teacher.name }.replace("/", "_").replace("\\", "_")
                                File(teachersDir, "$safeId.json").writeText(json.encodeToString(schedule))
                                synchronized(teachersDir) { teacherCount++ }
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        val meta = InstitutionMeta(
            lastSync = nowStr,
            institution = "rinh",
            groupCount = groups.size,
            teacherCount = teachers.size
        )
        File(dir, "meta.json").writeText(json.encodeToString(meta))
        println("  [RINH] Sync complete! Saved $groupCount group and $teacherCount teacher schedules.")
    }.onFailure {
        println("  [RINH] Error syncing: ${it.message}")
        it.printStackTrace()
        if (failOnError) throw it
    }
}

suspend fun syncSfedu(client: HttpClient, rootDir: File, failOnError: Boolean = false) {
    println("\n--- Syncing SFEDU ---")
    val dir = File(rootDir, "sfedu").apply { mkdirs() }
    val groupsDir = File(dir, "groups").apply { mkdirs() }
    val teachersDir = File(dir, "teachers").apply { mkdirs() }

    runCatching {
        val sfeduClient = SFEDUScheduleClient(client, log = { println("  [SFEDU] $it") })
        val nowStr = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString()

        println("  Fetching SFEDU groups & teachers...")
        val groups = sfeduClient.getGroups()
        val teachers = sfeduClient.getTeachers()
        println("  Found ${groups.size} groups and ${teachers.size} teachers")
        if (groups.isEmpty()) {
            error("SFEDU returned 0 groups")
        }

        File(dir, "groups.json").writeText(json.encodeToString(groups))
        File(dir, "teachers.json").writeText(json.encodeToString(teachers))

        var groupCount = 0
        var teacherCount = 0
        val semaphore = Semaphore(5)

        coroutineScope {
            groups.map { group ->
                async {
                    semaphore.withPermit {
                        runCatching {
                            delay(30)
                            val schedule = sfeduClient.getGroupSchedule(group.id)
                            if (schedule.isNotEmpty()) {
                                val safeName = group.name.replace("/", "_").replace("\\", "_")
                                File(groupsDir, "$safeName.json").writeText(json.encodeToString(schedule))
                                synchronized(groupsDir) { groupCount++ }
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        println("  Fetching SFEDU teacher schedules...")
        coroutineScope {
            teachers.map { teacher ->
                async {
                    semaphore.withPermit {
                        runCatching {
                            delay(30)
                            val schedule = sfeduClient.getTeacherSchedule(teacher.id)
                            if (schedule.isNotEmpty()) {
                                val safeId = teacher.id.ifEmpty { teacher.name }.replace("/", "_").replace("\\", "_")
                                File(teachersDir, "$safeId.json").writeText(json.encodeToString(schedule))
                                synchronized(teachersDir) { teacherCount++ }
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        val meta = InstitutionMeta(
            lastSync = nowStr,
            institution = "sfedu",
            groupCount = groups.size,
            teacherCount = teachers.size
        )
        File(dir, "meta.json").writeText(json.encodeToString(meta))
        println("  [SFEDU] Sync complete! Saved $groupCount group and $teacherCount teacher schedules.")
    }.onFailure {
        println("  [SFEDU] Error syncing: ${it.message}")
        it.printStackTrace()
        if (failOnError) throw it
    }
}

suspend fun syncRgups(client: HttpClient, rootDir: File, failOnError: Boolean = false) {
    println("\n--- Syncing RGUPS ---")
    val dir = File(rootDir, "rgups").apply { mkdirs() }
    val groupsDir = File(dir, "groups").apply { mkdirs() }
    val teachersDir = File(dir, "teachers").apply { mkdirs() }

    runCatching {
        val rgupsClient = RGUPSScheduleClient(client, log = { println("  [RGUPS] $it") })
        val nowStr = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString()

        println("  Fetching RGUPS groups...")
        val groups = rgupsClient.getGroups()
        println("  Found ${groups.size} groups")
        if (groups.isEmpty()) {
            error("RGUPS returned 0 groups")
        }

        File(dir, "groups.json").writeText(json.encodeToString(groups))

        var groupCount = 0
        val teacherSchedules = mutableMapOf<String, MutableMap<LocalDate, MutableList<LessonDto>>>()
        val semaphore = Semaphore(5)

        coroutineScope {
            groups.map { group ->
                async {
                    semaphore.withPermit {
                        runCatching {
                            delay(30)
                            val schedule = rgupsClient.getGroupSchedule(group.id)
                            if (schedule.isNotEmpty()) {
                                val safeName = group.name.replace("/", "_").replace("\\", "_")
                                File(groupsDir, "$safeName.json").writeText(json.encodeToString(schedule))
                                synchronized(groupsDir) { groupCount++ }

                                synchronized(teacherSchedules) {
                                    schedule.forEach { daySchedule ->
                                        daySchedule.lessons.forEach { lesson ->
                                            lesson.otUnits.forEach { unit ->
                                                val teachers = unit.teacher.split(Regex("""[,;\n/]"""))
                                                    .map { it.replace(Regex("""\[.*?\]"""), "").replace(Regex("""\s+"""), " ").trim() }
                                                    .filter { it.isNotBlank() && it != "—" && it != "-" && it != "_" }

                                                val safeGroup = if (group.name.isBlank() || group.name == "_") "-" else group.name
                                                val safeRoom = if (unit.room.isBlank() || unit.room == "_") "-" else unit.room

                                                for (cleanTeacher in teachers) {
                                                    val teacherDays = teacherSchedules.getOrPut(cleanTeacher) { mutableMapOf() }
                                                    val dayLessons = teacherDays.getOrPut(daySchedule.date) { mutableListOf() }
                                                    dayLessons.add(
                                                        lesson.copy(
                                                            otUnits = listOf(unit.copy(teacher = cleanTeacher, group = safeGroup, room = safeRoom))
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        println("  [RGUPS] Aggregated ${teacherSchedules.size} teachers from all group schedules")
        val teachersList = teacherSchedules.keys.sorted().map { TeacherDto(id = it, name = it) }
        File(dir, "teachers.json").writeText(json.encodeToString(teachersList))

        var teacherCount = 0
        teacherSchedules.forEach { (teacherName, daysMap) ->
            val teacherDays = daysMap.map { (date, lessons) ->
                val merged = lessons
                    .groupBy { Triple(it.number, it.startTime, it.subject) }
                    .map { (_, groupLessons) ->
                        val first = groupLessons.first()
                        val state = when {
                            groupLessons.any { it.state == LessonStateDto.CHANGED } -> LessonStateDto.CHANGED
                            groupLessons.all { it.state == LessonStateDto.REMOVED } -> LessonStateDto.REMOVED
                            else -> first.state
                        }
                        first.copy(
                            state = state,
                            otUnits = groupLessons.flatMap { it.otUnits }.distinctBy { it.group }
                        )
                    }
                    .sortedWith(compareBy({ it.startTime }, { it.number }))
                DayScheduleDto(
                    date = date,
                    scheduleType = LessonsScheduleTypeDto.COMMON,
                    lessons = merged
                )
            }.sortedBy { it.date }

            val safeTeacherId = teacherName.replace("/", "_").replace("\\", "_")
            File(teachersDir, "$safeTeacherId.json").writeText(json.encodeToString(teacherDays))
            teacherCount++
        }

        val meta = InstitutionMeta(
            lastSync = nowStr,
            institution = "rgups",
            groupCount = groups.size,
            teacherCount = teachersList.size
        )
        File(dir, "meta.json").writeText(json.encodeToString(meta))
        println("  [RGUPS] Sync complete! Saved $groupCount group schedules and $teacherCount teacher schedules.")
    }.onFailure {
        println("  [RGUPS] Error syncing: ${it.message}")
        it.printStackTrace()
        if (failOnError) throw it
    }
}

suspend fun syncRgupsTuapse(client: HttpClient, rootDir: File, failOnError: Boolean = false) {
    println("\n--- Syncing RGUPS Tuapse ---")
    val dir = File(rootDir, "rgups_tuapse").apply { mkdirs() }
    val groupsDir = File(dir, "groups").apply { mkdirs() }
    val teachersDir = File(dir, "teachers").apply { mkdirs() }

    runCatching {
        val tuapseClient = RGUPSTuapseScheduleClient(client, log = { println("  [Tuapse] $it") })
        val nowStr = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString()

        println("  Fetching RGUPS Tuapse groups & teachers...")
        val groups = tuapseClient.getGroups()
        val teachers = tuapseClient.getTeachers()
        println("  Found ${groups.size} groups and ${teachers.size} teachers")
        if (groups.isEmpty()) {
            error("RGUPS Tuapse returned 0 groups")
        }

        File(dir, "groups.json").writeText(json.encodeToString(groups))
        File(dir, "teachers.json").writeText(json.encodeToString(teachers))

        var groupCount = 0
        var teacherCount = 0
        val semaphore = Semaphore(10)

        coroutineScope {
            groups.map { group ->
                async {
                    semaphore.withPermit {
                        runCatching {
                            val schedule = tuapseClient.getGroupSchedule(group.id)
                            if (schedule.isNotEmpty()) {
                                val safeName = group.name.replace("/", "_").replace("\\", "_")
                                File(groupsDir, "$safeName.json").writeText(json.encodeToString(schedule))
                                synchronized(groupsDir) { groupCount++ }
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        coroutineScope {
            teachers.map { teacher ->
                async {
                    semaphore.withPermit {
                        runCatching {
                            val schedule = tuapseClient.getTeacherSchedule(teacher.id)
                            if (schedule.isNotEmpty()) {
                                val safeId = teacher.id.ifEmpty { teacher.name }.replace("/", "_").replace("\\", "_")
                                File(teachersDir, "$safeId.json").writeText(json.encodeToString(schedule))
                                synchronized(teachersDir) { teacherCount++ }
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        val meta = InstitutionMeta(
            lastSync = nowStr,
            institution = "rgups_tuapse",
            groupCount = groups.size,
            teacherCount = teachers.size
        )
        File(dir, "meta.json").writeText(json.encodeToString(meta))
        println("  [Tuapse] Sync complete! Saved $groupCount group and $teacherCount teacher schedules.")
    }.onFailure {
        println("  [Tuapse] Error syncing: ${it.message}")
        it.printStackTrace()
        if (failOnError) throw it
    }
}

suspend fun syncTvgu(client: HttpClient, rootDir: File, failOnError: Boolean = false, filter: String? = null) {
    println("\n--- Syncing TvGU ---")
    val dir = File(rootDir, "tvgu").apply { mkdirs() }
    val groupsDir = File(dir, "groups").apply { mkdirs() }
    val teachersDir = File(dir, "teachers").apply { mkdirs() }

    runCatching {
        val tvguClient = TvGUScheduleClient(client, log = { println("  [TvGU] $it") })
        val nowStr = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString()

        println("  Fetching TvGU groups...")
        val groups = tvguClient.getGroups()
        println("  Found ${groups.size} groups")
        if (groups.isEmpty()) {
            error("TvGU returned 0 groups")
        }

        File(dir, "groups.json").writeText(json.encodeToString(groups))

        val targetGroups = if (!filter.isNullOrBlank()) {
            val cleanFilter = filter.trim().lowercase()
            groups.filter { it.name.lowercase().contains(cleanFilter) || it.id.lowercase().contains(cleanFilter) }
        } else {
            groups
        }

        var groupCount = 0
        val teacherSchedules = mutableMapOf<String, MutableMap<LocalDate, MutableList<LessonDto>>>()
        val semaphore = Semaphore(6)

        println("  Fetching TvGU schedules for ${targetGroups.size} groups (filter: ${filter ?: "all"})...")
        coroutineScope {
            targetGroups.map { group ->
                async {
                    semaphore.withPermit {
                        runCatching {
                            delay(30)
                            val schedule = tvguClient.getGroupSchedule(group.name)
                            if (schedule.isNotEmpty()) {
                                val safeName = sanitizeFileName(group.name)
                                File(groupsDir, "$safeName.json").writeText(json.encodeToString(schedule))
                                synchronized(groupsDir) { groupCount++ }

                                synchronized(teacherSchedules) {
                                    schedule.forEach { daySchedule ->
                                        daySchedule.lessons.forEach { lesson ->
                                            lesson.otUnits.forEach { unit ->
                                                val cleanTeacher = unit.teacher.trim()
                                                if (cleanTeacher.isNotBlank() && cleanTeacher != "—" && cleanTeacher != "-" && cleanTeacher != "_") {
                                                    val safeGroup = if (group.name.isBlank() || group.name == "_") "-" else group.name
                                                    val safeRoom = if (unit.room.isBlank() || unit.room == "_") "-" else unit.room
                                                    val teacherDays = teacherSchedules.getOrPut(cleanTeacher) { mutableMapOf() }
                                                    val dayLessons = teacherDays.getOrPut(daySchedule.date) { mutableListOf() }
                                                    dayLessons.add(
                                                        lesson.copy(
                                                            otUnits = listOf(unit.copy(teacher = cleanTeacher, group = safeGroup, room = safeRoom))
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        println("  [TvGU] Aggregated ${teacherSchedules.size} teachers from group schedules")
        val teachersList = teacherSchedules.keys.sorted().map { TeacherDto(id = sanitizeFileName(it), name = it) }
        File(dir, "teachers.json").writeText(json.encodeToString(teachersList))

        var teacherCount = 0
        teacherSchedules.forEach { (teacherName, daysMap) ->
            val teacherDays = daysMap.map { (date, lessons) ->
                val merged = lessons
                    .groupBy { it.number to it.startTime }
                    .map { (_, groupLessons) ->
                        val first = groupLessons.first()
                        val state = when {
                            groupLessons.any { it.state == LessonStateDto.CHANGED } -> LessonStateDto.CHANGED
                            groupLessons.all { it.state == LessonStateDto.REMOVED } -> LessonStateDto.REMOVED
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
                    .sortedWith(compareBy({ it.startTime }, { it.number }))
                DayScheduleDto(
                    date = date,
                    scheduleType = LessonsScheduleTypeDto.COMMON,
                    lessons = merged
                )
            }.sortedBy { it.date }

            val safeTeacherId = sanitizeFileName(teacherName)
            File(teachersDir, "$safeTeacherId.json").writeText(json.encodeToString(teacherDays))
            teacherCount++
        }

        val meta = InstitutionMeta(
            lastSync = nowStr,
            institution = "tvgu",
            groupCount = groups.size,
            teacherCount = teachersList.size
        )
        File(dir, "meta.json").writeText(json.encodeToString(meta))
        println("  [TvGU] Sync complete! Saved $groupCount group schedules and $teacherCount teacher schedules.")
    }.onFailure {
        println("  [TvGU] Error syncing: ${it.message}")
        it.printStackTrace()
        if (failOnError) throw it
    }
}

