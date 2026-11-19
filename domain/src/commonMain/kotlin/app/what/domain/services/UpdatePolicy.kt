package app.what.domain.services

import app.what.domain.constants.AppConstants
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Модель семантической версии для точного сравнения версий приложения.
 */
data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preReleaseType: PreReleaseType = PreReleaseType.STABLE,
    val preReleaseNumber: Int = 0
) : Comparable<SemanticVersion> {

    enum class PreReleaseType {
        ALPHA,
        BETA,
        RELEASE_CANDIDATE,
        STABLE
    }

    override fun compareTo(other: SemanticVersion): Int {
        return compareValuesBy(
            this, other,
            { it.major },
            { it.minor },
            { it.patch },
            { it.preReleaseType.ordinal },
            { it.preReleaseNumber }
        )
    }

    companion object {
        fun parse(versionString: String): SemanticVersion {
            val clean = versionString.trim().removePrefix("v").removePrefix("V")
            return try {
                val mainAndPre = clean.split("-", limit = 2)
                val mainPart = mainAndPre[0]
                val versionDigitsRegex = Regex("""\d+(\.\d+)*""")
                val matched = versionDigitsRegex.find(mainPart)?.value ?: mainPart
                val parts = matched.split(".").map { it.toIntOrNull() ?: 0 }

                var preType = PreReleaseType.STABLE
                var preNum = 0
                if (mainAndPre.size > 1) {
                    val preParts = mainAndPre[1].split(".")
                    preType = when (preParts[0].lowercase()) {
                        "alpha" -> PreReleaseType.ALPHA
                        "beta" -> PreReleaseType.BETA
                        "rc" -> PreReleaseType.RELEASE_CANDIDATE
                        else -> PreReleaseType.STABLE
                    }
                    preNum = preParts.getOrElse(1) { "0" }.toIntOrNull() ?: 0
                }

                SemanticVersion(
                    major = parts.getOrElse(0) { 0 },
                    minor = parts.getOrElse(1) { 0 },
                    patch = parts.getOrElse(2) { 0 },
                    preReleaseType = preType,
                    preReleaseNumber = preNum
                )
            } catch (_: Exception) {
                SemanticVersion(0, 0, 0, PreReleaseType.ALPHA, 0)
            }
        }
    }
}

/**
 * Политика обязательности обновлений (Вариант Б: управляемая обязательность).
 */
object UpdatePolicy {
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Проверяет, является ли обнаруженное обновление обязательным для текущей версии.
     *
     * Критерии обязательности:
     * 1. В `release-notes.json` выставлен флаг `"mandatory": true` или `"is_mandatory": true`.
     * 2. В `release-notes.json` указан `"min_version"` (или `"min_supported_version"`),
     *    и текущая версия приложения строго меньше этого значения.
     * 3. В описании, названии релиза или changelog присутствуют маркеры:
     *    `[mandatory]`, `[critical]`, `[blocking]`, `mandatory: true`, `обязательное обновление`.
     */
    fun isMandatory(
        info: UpdateInfo,
        currentVersion: String = AppConstants.VERSION_NAME,
        cachedNotesJson: String? = null
    ): Boolean {
        // 1. Проверка сырого JSON из release-notes.json
        if (!cachedNotesJson.isNullOrBlank()) {
            try {
                val element = jsonParser.parseToJsonElement(cachedNotesJson).jsonObject

                val mandatory = element["mandatory"]?.jsonPrimitive?.booleanOrNull
                    ?: element["is_mandatory"]?.jsonPrimitive?.booleanOrNull
                if (mandatory == true) return true

                val minVersion = element["min_version"]?.jsonPrimitive?.contentOrNull
                    ?: element["min_supported_version"]?.jsonPrimitive?.contentOrNull
                if (!minVersion.isNullOrBlank()) {
                    val current = SemanticVersion.parse(currentVersion)
                    val min = SemanticVersion.parse(minVersion)
                    if (current < min) return true
                }
            } catch (_: Exception) {}
        }

        // 2. Проверка текстовых маркеров в теле релиза GitHub или заметках
        val body = info.releaseNotes ?: ""
        val title = info.releaseNotesData?.title ?: ""
        val shortDesc = info.releaseNotesData?.shortDescription ?: ""
        val text = "$body $title $shortDesc".lowercase()

        val markers = listOf(
            "[mandatory]",
            "[critical]",
            "[blocking]",
            "mandatory: true",
            "mandatory:true",
            "обязательное обновление"
        )
        return markers.any { text.contains(it) }
    }
}
