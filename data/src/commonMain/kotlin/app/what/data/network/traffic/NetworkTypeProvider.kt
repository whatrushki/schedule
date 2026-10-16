package app.what.data.network.traffic

interface NetworkTypeProvider {
    fun getCurrentTransport(): NetworkTransportType
}

class DefaultNetworkTypeProvider : NetworkTypeProvider {
    override fun getCurrentTransport(): NetworkTransportType = NetworkTransportType.WIFI
}
