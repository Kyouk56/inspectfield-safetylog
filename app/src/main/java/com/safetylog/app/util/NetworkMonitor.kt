package com.safetylog.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors real-time network connectivity status.
 * Emits true when the device has an active internet-capable network (Wi-Fi or Cellular),
 * and false when disconnected or operating strictly in air-gapped / offline mode.
 */
class NetworkMonitor(context: Context) {

    private val appContext = context.applicationContext ?: context
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(checkInitialConnectivity())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private fun checkInitialConnectivity(): Boolean {
        val cm = connectivityManager ?: return false
        return try {
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            isOnlineCapabilities(caps)
        } catch (_: Exception) {
            false
        }
    }

    private fun isOnlineCapabilities(caps: NetworkCapabilities?): Boolean {
        if (caps == null) return false
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val isCaptive = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
        return hasInternet && isValidated && !isCaptive
    }

    private inner class DefaultNetworkCallback : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = checkInitialConnectivity()
        }

        override fun onLost(network: Network) {
            val cm = connectivityManager
            val active = cm?.activeNetwork
            if (active == null || active == network) {
                _isOnline.value = false
            } else {
                _isOnline.value = checkInitialConnectivity()
            }
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            val cm = connectivityManager
            val active = cm?.activeNetwork
            if (active == null || active == network) {
                _isOnline.value = isOnlineCapabilities(networkCapabilities)
            } else {
                _isOnline.value = checkInitialConnectivity()
            }
        }
    }

    init {
        try {
            val callback = DefaultNetworkCallback()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                connectivityManager?.registerDefaultNetworkCallback(callback)
            } else {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager?.registerNetworkCallback(request, callback)
            }
        } catch (_: Exception) {
            // Graceful fallback if permission or system service is unavailable
        }
    }
}

