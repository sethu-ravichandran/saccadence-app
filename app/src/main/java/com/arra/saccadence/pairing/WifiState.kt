package com.arra.saccadence.pairing

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Pairing needs this device on the same LAN as the clinic laptop (the rig is a
 * plaintext ws:// endpoint on that network, see saccadence-rig) — a QR will scan
 * fine over mobile data or with Wi-Fi off, but the connect step will just hang.
 * Tracks Wi-Fi connectivity live so [PairingScreen] can warn before that happens.
 */
@Composable
fun rememberIsOnWifi(): Boolean {
    val context = LocalContext.current
    var onWifi by remember {
        mutableStateOf(currentlyOnWifi(context))
    }

    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                onWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            }

            override fun onLost(network: Network) {
                onWifi = currentlyOnWifi(context)
            }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
        onDispose { connectivityManager.unregisterNetworkCallback(callback) }
    }

    return onWifi
}

private fun currentlyOnWifi(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}
