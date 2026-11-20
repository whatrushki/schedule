package app.what.domain.services

data class AttachedFile(
    val name: String,
    val sizeBytes: Long,
    val path: String = "",
    val mimeType: String? = null,
    val bytes: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AttachedFile

        if (name != other.name) return false
        if (sizeBytes != other.sizeBytes) return false
        if (path != other.path) return false
        if (mimeType != other.mimeType) return false
        if (bytes != null) {
            if (other.bytes == null) return false
            if (!bytes.contentEquals(other.bytes)) return false
        } else if (other.bytes != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + sizeBytes.hashCode()
        result = 31 * result + path.hashCode()
        result = 31 * result + (mimeType?.hashCode() ?: 0)
        result = 31 * result + (bytes?.contentHashCode() ?: 0)
        return result
    }
}

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
