package com.example.gps_kotlin.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class NetworkService(private val context: Context) {

    fun isMobileDataEnabled(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }
}