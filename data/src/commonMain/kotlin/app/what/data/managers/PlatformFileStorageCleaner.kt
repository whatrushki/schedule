package app.what.data.managers

interface PlatformFileStorageCleaner {
    suspend fun getImageCacheSizeBytes(): Long
    suspend fun clearImageCache()
    suspend fun getLogsCacheSizeBytes(): Long
    suspend fun clearLogsCache()
    suspend fun getDatabaseSizeBytes(): Long = 0L
}

class NoOpPlatformFileStorageCleaner : PlatformFileStorageCleaner {
    override suspend fun getImageCacheSizeBytes(): Long = 0L
    override suspend fun clearImageCache() {}
    override suspend fun getLogsCacheSizeBytes(): Long = 0L
    override suspend fun clearLogsCache() {}
    override suspend fun getDatabaseSizeBytes(): Long = 0L
}
