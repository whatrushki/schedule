package app.what.schedule.sfedu

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.runBlocking
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import kotlin.test.Test

class LiveTest {
    @Test
    fun testLiveApi() = runBlocking {
        val httpClient = HttpClient(CIO) {
            followRedirects = true
            defaultRequest {
                header(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                header(HttpHeaders.Accept, "*/*")
            }
            engine {
                https {
                    trustManager = object : X509TrustManager {
                        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    }
                }
            }
        }

        val client = SFEDUScheduleClient(httpClient, log = { println("LOG: $it") })

        println("=== CALLING getGroups() ===")
        val groups = client.getGroups()
        println("=== GROUPS RESULT: size=${groups.size} ===")
        groups.take(10).forEach { println("  Group: ${it.name} (id=${it.id}, course=${it.course})") }

        println("=== CALLING getTeachers() ===")
        val teachers = client.getTeachers()
        println("=== TEACHERS RESULT: size=${teachers.size} ===")
        teachers.take(10).forEach { println("  Teacher: ${it.name} (id=${it.id})") }

        if (teachers.isNotEmpty()) {
            val t = teachers.firstOrNull { it.name.contains("Карякин", ignoreCase = true) } ?: teachers.first()
            println("=== CALLING getTeacherSchedule(${t.name}, id=${t.id}) ===")
            val teacherDays = client.getTeacherSchedule(t.id, true)
            println("=== TEACHER DAYS: size=${teacherDays.size} ===")
            teacherDays.forEach { day ->
                println("  Day ${day.date}: ${day.lessons.size} lessons")
                day.lessons.forEach { l ->
                    println("    Lesson ${l.number} (${l.startTime}-${l.endTime}): ${l.subject} [${l.otUnits}]")
                }
            }
        }

        if (groups.isNotEmpty()) {
            val g = groups.first()
            println("=== CALLING getGroupSchedule(${g.name}, id=${g.id}) ===")
            val groupDays = client.getGroupSchedule(g.id, true)
            println("=== GROUP DAYS: size=${groupDays.size} ===")
            groupDays.forEach { day ->
                println("  Day ${day.date}: ${day.lessons.size} lessons")
            }
        }
    }
}
