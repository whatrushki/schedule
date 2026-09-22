package app.what.tools.sync

import app.what.schedule.core.models.DayScheduleDto
import app.what.schedule.core.models.GroupDto
import app.what.schedule.core.models.LessonDto
import app.what.schedule.core.models.LessonsScheduleTypeDto
import app.what.schedule.core.models.TeacherDto
import app.what.schedule.dgtu.DGTUScheduleClient
import app.what.schedule.iubip.IUBIPScheduleClient
import app.what.schedule.rinh.RINHScheduleClient
import app.what.schedule.rksi.RKSILessonsSchedule
import app.what.schedule.rksi.RKSIScheduleClient
import app.what.schedule.rksi.parser.JvmXlsxReader
import app.what.schedule.rksi.parser.RKSIGoogleDriveParser
import app.what.schedule.rksi.parser.RKSIReplacementsParser
import app.what.schedule.rksi.parser.files
import app.what.schedule.rksi.parser.folders
import app.what.schedule.sfedu.SFEDUScheduleClient
import com.fleeksoft.ksoup.Ksoup
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
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

    val failOnError = args.contains("--fail-on-error")

    println("=== Starting Schedule Sync ===")
    println("Target: $target")
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
            "others" -> syncOthers(client, outDir, failOnError)
            "all" -> {
                syncRksi(client, outDir, failOnError)
                syncOthers(client, outDir, failOnError)
            }
            else -> {
                println("Unknown target: $target. Use 'rksi', 'dgtu', 'iubip', 'rinh', 'sfedu', 'others', or 'all'.")
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

private fun isSameTeacher(baseTeacher: String?, repTeacher: String?): Boolean {
    if (baseTeacher.isNullOrBlank() || repTeacher.isNullOrBlank()) return false
    val cleanBase = normalizeName(baseTeacher)
    val cleanRep = normalizeName(repTeacher)
    return cleanBase == cleanRep || cleanBase.startsWith(cleanRep) || cleanRep.startsWith(cleanBase)
}

suspend fun fetchRksiReplacementsAll(client: HttpClient, xlsxReader: JvmXlsxReader): List<LessonDto> {
    return runCatching {
        println("  [RKSI] Fetching replacement sheets from Google Drive (single pass)...")
        val googleDriveParser = RKSIGoogleDriveParser(client)
        val response = client.get("https://rksi.ru/schedule").bodyAsText()
        val document = Ksoup.parse(response)
        val tabletUrl = document.getElementsMatchingText("Планшетка").lastOrNull()?.attr("href")
        val rootFolderId = tabletUrl?.split("/")?.lastOrNull()?.substringBefore("?")?.ifEmpty { null }
            ?: "1kUYiSAafghhYR0ARyXwPW1HZPpHcFIag"

        val rootItems = googleDriveParser.getFolderContent(rootFolderId)
        val rootFiles = rootItems.files().onEach { it.additionalData["building"] = 1 }
        val subFolderId = rootItems.folders().firstOrNull()?.id ?: "1bdHCozxsjzy7BVd76sBTTK_ckhZ78wbo"
        val subItems = googleDriveParser.getFolderContent(subFolderId)
        val subFiles = subItems.files().onEach { it.additionalData["building"] = 2 }
        val allFiles = rootFiles + subFiles

        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val relevantFiles = allFiles.filter { file ->
            val parts = file.name.split(".").take(2).mapNotNull { it.toIntOrNull() }
            if (parts.size == 2) {
                val fileDate = LocalDate(today.year, parts[1], parts[0])
                file.additionalData["date"] = fileDate
                fileDate >= today.minus(1, DateTimeUnit.DAY)
            } else false
        }

        println("  [RKSI] Found ${relevantFiles.size} relevant replacement files in Google Drive.")
        val replacements = mutableListOf<LessonDto>()
        for (file in relevantFiles) {
            val date = file.additionalData["date"] as? LocalDate ?: continue
            val building = file.additionalData["building"] as? Int ?: 1
            val columns = if (building == 1) 2 else 1
            val bytes = client.get(file.getDownloadLink()).readRawBytes()
            val sheets = xlsxReader.readSheets(bytes)
            val parsed = RKSIReplacementsParser.parse(
                sheets = sheets,
                columns = columns,
                date = date,
                predicate = { _, _ -> true }
            )
            replacements.addAll(parsed)
        }
        println("  [RKSI] Successfully parsed ${replacements.size} total replacements once in-memory.")
        replacements
    }.getOrElse {
        println("  [RKSI] Warning: failed to fetch replacements from Google Drive: ${it.message}")
        emptyList()
    }
}

fun applyReplacementsToSchedule(
    baseSchedule: List<DayScheduleDto>,
    allReplacements: List<LessonDto>,
    isGroup: Boolean,
    target: String
): List<DayScheduleDto> {
    if (allReplacements.isEmpty() || baseSchedule.isEmpty()) return baseSchedule
    val cleanTarget = normalizeName(target)

    val targetReplacements = allReplacements.filter { lesson ->
        lesson.otUnits.any { ot ->
            if (isGroup) {
                normalizeName(ot.group) == cleanTarget
            } else {
                val cleanOt = normalizeName(ot.teacher)
                cleanOt.contains(cleanTarget) || cleanTarget.contains(cleanOt) ||
                    (target.split(" ").firstOrNull()?.let { cleanOt.contains(normalizeName(it)) } == true)
            }
        }
    }
    if (targetReplacements.isEmpty()) return baseSchedule

    return baseSchedule.map { daySchedule ->
        val dayReplacements = targetReplacements.filter { it.date == daySchedule.date }
        if (dayReplacements.isNotEmpty()) {
            val timeSchedule = when (daySchedule.scheduleType) {
                LessonsScheduleTypeDto.SHORTENED -> RKSILessonsSchedule.SHORTENED
                LessonsScheduleTypeDto.WITH_CLASS_HOUR -> RKSILessonsSchedule.WITH_CLASS_HOUR
                else -> RKSILessonsSchedule.COMMON
            }
            val merged = RKSIReplacementsParser.applyReplacements(
                baseLessons = daySchedule.lessons,
                replacements = dayReplacements,
                timeSchedule = timeSchedule
            )
            daySchedule.copy(lessons = merged)
        } else daySchedule
    }
}

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

        // Pre-fetch all replacements once in-memory
        val allReplacements = fetchRksiReplacementsAll(client, xlsxReader)

        var groupCount = 0
        var teacherCount = 0
        val semaphore = Semaphore(6)

        println("  Fetching RKSI group schedules...")
        coroutineScope {
            groups.map { group ->
                async {
                    semaphore.withPermit {
                        runCatching {
                            delay(20)
                            val rawSchedule = rksiClient.getGroupSchedule(group.name, showReplacements = false)
                            val schedule = applyReplacementsToSchedule(rawSchedule, allReplacements, isGroup = true, target = group.name)
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

        println("  Fetching RKSI teacher schedules...")
        coroutineScope {
            teachers.map { teacher ->
                async {
                    semaphore.withPermit {
                        runCatching {
                            delay(20)
                            val rawSchedule = rksiClient.getTeacherSchedule(teacher.id, showReplacements = false)
                            val schedule = applyReplacementsToSchedule(rawSchedule, allReplacements, isGroup = false, target = teacher.name)
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
                    lessons = lessons.distinctBy { it.number to it.subject }.sortedBy { it.number }
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
