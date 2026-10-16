package app.what.data.network.traffic

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

@Serializable
enum class TrafficCategory(val displayName: String) {
    SCHEDULE("Расписание"),
    NEWS("Новости"),
    ACCOUNT("Личный кабинет"),
    MEDIA("Фото и медиа"),
    SYSTEM("Система и обновления");

    val color: Color
        get() = when (this) {
            SCHEDULE -> Color(0xFF8E4E98) // Насыщенный пурпурный
            NEWS -> Color(0xFFBA68C8)     // Лиловый
            ACCOUNT -> Color(0xFF42A5F5)  // Голубой / акцент профиля
            MEDIA -> Color(0xFF66BB6A)    // Зеленый / медиа
            SYSTEM -> Color(0xFFFFA726)   // Оранжевый / система
        }
}

@Serializable
enum class NetworkTransportType(val displayName: String) {
    WIFI("Wi-Fi"),
    CELLULAR("Мобильный"),
    ROAMING("Роуминг"),
    OTHER("Другое")
}

@Serializable
data class TrafficBucket(
    val sentBytes: Long = 0L,
    val receivedBytes: Long = 0L,
    val requestsCount: Long = 0L
) {
    val totalBytes: Long get() = sentBytes + receivedBytes

    operator fun plus(other: TrafficBucket): TrafficBucket = TrafficBucket(
        sentBytes = sentBytes + other.sentBytes,
        receivedBytes = receivedBytes + other.receivedBytes,
        requestsCount = requestsCount + other.requestsCount
    )
}

@Serializable
data class CategoryTraffic(
    val wifi: TrafficBucket = TrafficBucket(),
    val cellular: TrafficBucket = TrafficBucket(),
    val roaming: TrafficBucket = TrafficBucket(),
    val other: TrafficBucket = TrafficBucket()
) {
    fun getBucket(type: NetworkTransportType): TrafficBucket = when (type) {
        NetworkTransportType.WIFI -> wifi
        NetworkTransportType.CELLULAR -> cellular
        NetworkTransportType.ROAMING -> roaming
        NetworkTransportType.OTHER -> other
    }

    val total: TrafficBucket
        get() = wifi + cellular + roaming + other

    fun withIncrement(
        transport: NetworkTransportType,
        sent: Long,
        received: Long
    ): CategoryTraffic = when (transport) {
        NetworkTransportType.WIFI -> copy(wifi = wifi + TrafficBucket(sent, received, 1L))
        NetworkTransportType.CELLULAR -> copy(cellular = cellular + TrafficBucket(sent, received, 1L))
        NetworkTransportType.ROAMING -> copy(roaming = roaming + TrafficBucket(sent, received, 1L))
        NetworkTransportType.OTHER -> copy(other = other + TrafficBucket(sent, received, 1L))
    }
}

@Serializable
data class NetworkTrafficStats(
    val trackingStartedAt: Long = 0L,
    val categories: Map<TrafficCategory, CategoryTraffic> = emptyMap()
) {
    fun getCategoryTraffic(category: TrafficCategory): CategoryTraffic =
        categories[category] ?: CategoryTraffic()

    fun getBucketFor(category: TrafficCategory, transportFilter: NetworkTransportType?): TrafficBucket {
        val cat = getCategoryTraffic(category)
        return if (transportFilter == null) cat.total else cat.getBucket(transportFilter)
    }

    fun getTotalBucket(transportFilter: NetworkTransportType?): TrafficBucket {
        var total = TrafficBucket()
        TrafficCategory.entries.forEach { cat ->
            total += getBucketFor(cat, transportFilter)
        }
        return total
    }
}
