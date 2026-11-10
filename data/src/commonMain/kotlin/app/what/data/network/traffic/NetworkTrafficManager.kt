package app.what.data.network.traffic

import app.what.foundation.data.settings.KeyValueStorage
import app.what.foundation.utils.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

interface NetworkTrafficManager {
    val stats: StateFlow<NetworkTrafficStats>
    fun record(category: TrafficCategory, transport: NetworkTransportType, sentBytes: Long, receivedBytes: Long)
    fun resetStats(categories: Set<TrafficCategory>? = null)
}

class NetworkTrafficManagerImpl(
    private val storage: KeyValueStorage,
    private val scope: CoroutineScope
) : NetworkTrafficManager {

    companion object {
        private const val KEY_TRAFFIC_STATS = "network_traffic_stats"
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }

    private val _stats = MutableStateFlow(loadInitialStats())
    override val stats: StateFlow<NetworkTrafficStats> = _stats.asStateFlow()

    private val pendingPersist = MutableStateFlow<Long>(0L)

    init {
        scope.launch(Dispatchers.Default) {
            @OptIn(FlowPreview::class)
            pendingPersist.debounce(3000L).collect { triggerTime ->
                if (triggerTime > 0L) {
                    persistCurrentStats()
                }
            }
        }
    }

    private fun loadInitialStats(): NetworkTrafficStats {
        val raw = storage.getString(KEY_TRAFFIC_STATS, "")
        if (!raw.isNullOrBlank()) {
            try {
                val parsed = json.decodeFromString<NetworkTrafficStats>(raw)
                if (parsed.trackingStartedAt > 0L) {
                    return parsed
                }
            } catch (_: Exception) {}
        }
        val initial = NetworkTrafficStats(trackingStartedAt = currentTimeMillis())
        try {
            storage.putString(KEY_TRAFFIC_STATS, json.encodeToString(NetworkTrafficStats.serializer(), initial))
        } catch (_: Exception) {}
        return initial
    }

    private fun persistCurrentStats() {
        try {
            val current = _stats.value
            val encoded = json.encodeToString(NetworkTrafficStats.serializer(), current)
            storage.putString(KEY_TRAFFIC_STATS, encoded)
        } catch (_: Exception) {}
    }

    override fun record(
        category: TrafficCategory,
        transport: NetworkTransportType,
        sentBytes: Long,
        receivedBytes: Long
    ) {
        val safeSent = sentBytes.coerceAtLeast(0L)
        val safeReceived = receivedBytes.coerceAtLeast(0L)

        _stats.update { current ->
            val updatedMap = current.categories.toMutableMap()
            val existing = updatedMap[category] ?: CategoryTraffic()
            updatedMap[category] = existing.withIncrement(transport, safeSent, safeReceived)
            current.copy(
                trackingStartedAt = if (current.trackingStartedAt == 0L) currentTimeMillis() else current.trackingStartedAt,
                categories = updatedMap
            )
        }
        pendingPersist.value = currentTimeMillis()
    }

    override fun resetStats(categories: Set<TrafficCategory>?) {
        if (categories == null || categories.isEmpty() || categories.size == TrafficCategory.entries.size) {
            val fresh = NetworkTrafficStats(trackingStartedAt = currentTimeMillis())
            _stats.value = fresh
        } else {
            _stats.update { current ->
                val updatedMap = current.categories.toMutableMap()
                categories.forEach { updatedMap.remove(it) }
                current.copy(categories = updatedMap)
            }
        }
        persistCurrentStats()
    }
}
