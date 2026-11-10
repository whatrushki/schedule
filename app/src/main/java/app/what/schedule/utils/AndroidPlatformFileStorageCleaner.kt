package app.what.schedule.utils

import android.content.Context
import app.what.data.managers.PlatformFileStorageCleaner
import app.what.foundation.services.AppLogger.Companion.Auditor
import coil3.SingletonImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidPlatformFileStorageCleaner(
    private val context: Context
) : PlatformFileStorageCleaner {

    private fun getFolderSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var total = 0L
        dir.listFiles()?.forEach { file ->
            total += if (file.isDirectory) getFolderSize(file) else file.length()
        }
        return total
    }

    private fun deleteFolderContents(dir: File?) {
        if (dir == null || !dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) deleteFolderContents(file)
            file.delete()
        }
    }

    override suspend fun getImageCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        val imageCacheDir = context.cacheDir.resolve("image_cache")
        getFolderSize(imageCacheDir)
    }

    override suspend fun clearImageCache() = withContext(Dispatchers.IO) {
        try {
            SingletonImageLoader.get(context).diskCache?.clear()
        } catch (_: Exception) {}
        val imageCacheDir = context.cacheDir.resolve("image_cache")
        deleteFolderContents(imageCacheDir)
    }

    override suspend fun getLogsCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        try {
            val logsText = Auditor.exportLogs()
            logsText.encodeToByteArray().size.toLong()
        } catch (_: Exception) {
            0L
        }
    }

    override suspend fun clearLogsCache() = withContext(Dispatchers.IO) {
        try {
            Auditor.clearLogs()
        } catch (_: Exception) {}
    }

    override suspend fun getDatabaseSizeBytes(): Long = withContext(Dispatchers.IO) {
        try {
            val dbFile = context.getDatabasePath("schedule.db")
            val walFile = File(dbFile.path + "-wal")
            val shmFile = File(dbFile.path + "-shm")
            var size = 0L
            if (dbFile.exists()) size += dbFile.length()
            if (walFile.exists()) size += walFile.length()
            if (shmFile.exists()) size += shmFile.length()
            size
        } catch (_: Exception) {
            0L
        }
    }
}
