package app.what.data.network.traffic

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class AndroidNetworkTypeProvider(private val context: Context) : NetworkTypeProvider {
    private val cm by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    override fun getCurrentTransport(): NetworkTransportType {
        return try {
            val network = cm?.activeNetwork ?: return NetworkTransportType.OTHER
            val caps = cm?.getNetworkCapabilities(network) ?: return NetworkTransportType.OTHER

            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    val isRoaming = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
                    if (isRoaming) NetworkTransportType.ROAMING else NetworkTransportType.CELLULAR
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransportType.WIFI
                else -> NetworkTransportType.OTHER
            }
        } catch (_: Exception) {
            NetworkTransportType.OTHER
        }
    }
}
