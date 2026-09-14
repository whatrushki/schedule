package app.what.data.cache

import app.what.schedule.core.cache.FileCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class JvmFileCache(
    private val baseDir: File = File(System.getProperty("user.home"), ".what_schedule/cache"),
    private val defaultMaxAgeMillis: Long = 3L * 24 * 60 * 60 * 1000, // 3 days
    private val maxCacheSizeBytes: Long = 50L * 1024 * 1024 // 50 MB
) : FileCache {

    init {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
    }

    override suspend fun get(key: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(baseDir, sanitizeKey(key))
        if (!file.exists()) return@withContext null

        val age = System.currentTimeMillis() - file.lastModified()
        if (age > defaultMaxAgeMillis) {
            file.delete()
            return@withContext null
        }

        try {
            file.readBytes()
        } catch (_: Exception) {
            null
        }
    }

    fun getFile(key: String): File? {
        val file = File(baseDir, sanitizeKey(key))
        if (!file.exists()) return null
        val age = System.currentTimeMillis() - file.lastModified()
        if (age > defaultMaxAgeMillis) {
            file.delete()
            return null
        }
        return file
    }

    override suspend fun put(key: String, bytes: ByteArray, ttlMillis: Long?) = withContext(Dispatchers.IO) {
        cleanup(ttlMillis ?: defaultMaxAgeMillis)
        val file = File(baseDir, sanitizeKey(key))
        try {
            FileOutputStream(file).use { it.write(bytes) }
        } catch (_: Exception) {}
    }

    override suspend fun exists(key: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(baseDir, sanitizeKey(key))
        if (!file.exists()) return@withContext false
        val age = System.currentTimeMillis() - file.lastModified()
        if (age > defaultMaxAgeMillis) {
            file.delete()
            false
        } else {
            true
        }
    }

    override suspend fun delete(key: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(baseDir, sanitizeKey(key))
        if (file.exists()) file.delete() else false
    }

    fun clear() {
        baseDir.listFiles()?.forEach { it.delete() }
    }

    override suspend fun cleanup(maxAgeMillis: Long) = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val files = baseDir.listFiles() ?: return@withContext

            var currentSize = 0L
            val validFiles = mutableListOf<File>()

            for (file in files) {
                if (now - file.lastModified() > maxAgeMillis) {
                    file.delete()
                } else {
                    currentSize += file.length()
                }
            }

            if (currentSize > maxCacheSizeBytes) {
                validFiles.sortBy { it.lastModified() }
                for (file in validFiles) {
                    if (currentSize <= maxCacheSizeBytes) break
                    currentSize -= file.length()
                    file.delete()
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun sanitizeKey(key: String): String =
        key.replace(Regex("[^a-zA-Z0-9._-]"), "_")
}
