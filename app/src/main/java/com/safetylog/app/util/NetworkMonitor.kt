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
            // Evaluate the network handed to us; activeNetwork may not have been
            // promoted/validated yet during a handover, which caused a stale false.
            _isOnline.value = isOnlineCapabilities(connectivityManager?.getNetworkCapabilities(network))
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

    private val callback = DefaultNetworkCallback()

    init {
        try {
            connectivityManager?.registerDefaultNetworkCallback(callback)
        } catch (_: Exception) {
            // Graceful fallback if permission or system service is unavailable
        }
    }

    /** Releases the system callback; call from the owning lifecycle when disposable. */
    fun unregister() {
        try {
            connectivityManager?.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
            // Already unregistered or service unavailable.
        }
    }
}

