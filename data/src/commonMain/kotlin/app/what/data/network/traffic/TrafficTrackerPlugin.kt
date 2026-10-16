package app.what.data.network.traffic

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength

class TrafficTrackerPluginConfig {
    var trafficManager: NetworkTrafficManager? = null
    var networkTypeProvider: NetworkTypeProvider? = null
}

val TrafficTrackerPlugin = createClientPlugin("TrafficTrackerPlugin", ::TrafficTrackerPluginConfig) {
    val trafficManager = pluginConfig.trafficManager
    val networkTypeProvider = pluginConfig.networkTypeProvider

    onResponse { response ->
        if (trafficManager == null) return@onResponse
        try {
            val url = response.call.request.url.toString()
            val sentBytes = response.call.request.content.contentLength ?: 0L
            val receivedBytes = response.contentLength() ?: response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L
            val contentType = response.headers[HttpHeaders.ContentType]
            val transport = networkTypeProvider?.getCurrentTransport() ?: NetworkTransportType.WIFI
            val category = TrafficClassifier.classify(url, contentType)

            // Учитываем базовые заголовки HTTP (примерно ~300 байт на запрос)
            val approxHeaders = 300L
            val effectiveSent = sentBytes + approxHeaders
            val effectiveReceived = if (receivedBytes > 0) receivedBytes + approxHeaders else approxHeaders

            trafficManager.record(
                category = category,
                transport = transport,
                sentBytes = effectiveSent,
                receivedBytes = effectiveReceived
            )
        } catch (_: Exception) {}
    }
}
