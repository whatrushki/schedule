package app.what.schedule.rgups_tuapse.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class TuapseGroup(
    val id: Int,
    val n: String,
    val c: Int = 0,
    val f: String? = null
)

@Serializable
data class TuapseTimetableResponse(
    val from: String? = null,
    val to: String? = null,
    val times: List<String> = emptyList(),
    val subjects: List<String> = emptyList(),
    val types: List<String> = emptyList(),
    val audiences: List<String> = emptyList(),
    val teachers: Map<String, String> = emptyMap(),
    val groups: List<TuapseGroup> = emptyList(),
    val rows: List<List<JsonElement>> = emptyList()
)
