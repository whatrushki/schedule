package app.what.schedule.sfedu.grade

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

class SfeduGradeClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://grade.sfedu.ru/api/v1/student"
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    private fun checkTokenError(body: String, status: HttpStatusCode) {
        if (status == HttpStatusCode.Forbidden || body.contains("Token is broken", ignoreCase = true)) {
            throw SfeduGradeTokenInvalidException()
        }
    }

    suspend fun getStudentData(token: String, semesterId: Int? = null): SfeduGradeStudentData {
        val url = buildString {
            append("$baseUrl/?token=$token")
            if (semesterId != null) {
                append("&sem_id=$semesterId")
            }
        }
        val response: HttpResponse = client.get(url)
        val body = response.bodyAsText()
        checkTokenError(body, response.status)

        val parsed = json.decodeFromString<SfeduGradeStudentResponse>(body)
        return parsed.response
    }

    suspend fun getSemesters(token: String): Map<String, SfeduGradeSemester> {
        val url = "$baseUrl/semester_list?token=$token"
        val response: HttpResponse = client.get(url)
        val body = response.bodyAsText()
        checkTokenError(body, response.status)

        val parsed = json.decodeFromString<SfeduGradeSemesterListResponse>(body)
        val result = mutableMapOf<String, SfeduGradeSemester>()
        for ((key, element) in parsed.response) {
            try {
                val semester = json.decodeFromJsonElement<SfeduGradeSemester>(element)
                result[key] = semester
            } catch (_: Exception) {
                // Ignore any malformed semester entry
            }
        }
        return result
    }

    suspend fun getDisciplineDetail(token: String, disciplineId: Int): SfeduGradeDisciplineDetail {
        val url = "$baseUrl/discipline/subject?id=$disciplineId&token=$token"
        val response: HttpResponse = client.get(url)
        val body = response.bodyAsText()
        checkTokenError(body, response.status)

        val parsed = json.decodeFromString<SfeduGradeDisciplineDetailResponse>(body)
        return parsed.response
    }

    suspend fun validateToken(token: String): Boolean {
        return try {
            getStudentData(token)
            true
        } catch (_: SfeduGradeTokenInvalidException) {
            false
        } catch (e: Exception) {
            throw e
        }
    }
}
