package app.what.domain.services

data class AttachedFile(
    val name: String,
    val sizeBytes: Long,
    val path: String,
    val mimeType: String? = null
)

interface FeedbackSharer {
    fun pickFiles(onFilesPicked: (List<AttachedFile>) -> Unit)
    suspend fun sendFeedback(
        isBug: Boolean,
        title: String,
        description: String,
        attachedFiles: List<AttachedFile>,
        userId: String = ""
    ): Result<Unit>
}

class NoOpFeedbackSharer : FeedbackSharer {
    override fun pickFiles(onFilesPicked: (List<AttachedFile>) -> Unit) {}
    override suspend fun sendFeedback(
        isBug: Boolean,
        title: String,
        description: String,
        attachedFiles: List<AttachedFile>,
        userId: String
    ): Result<Unit> = Result.success(Unit)
}
