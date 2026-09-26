package app.what.schedule.rgups.models

import kotlinx.serialization.Serializable

@Serializable
data class RgupsFaculty(
    val id: String,
    val name: String
)

@Serializable
data class RgupsGroup(
    val id: String,
    val name: String,
    val courseId: Int,
    val facId: String,
    val eduType: String = "internal"
)
