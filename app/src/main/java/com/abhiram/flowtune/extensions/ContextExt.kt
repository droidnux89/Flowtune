package com.abhiram.flowtune.extensions

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.abhiram.flowtune.constants.InnerTubeCookieKey
import com.abhiram.flowtune.constants.YtmSyncKey
import com.abhiram.flowtune.constants.LikedAutoDownloadKey
import com.abhiram.flowtune.constants.LikedAutodownloadMode
import com.abhiram.flowtune.utils.dataStore
import com.abhiram.flowtune.utils.get
import com.zionhuang.innertube.utils.parseCookieString
import kotlinx.coroutines.runBlocking

fun Context.isSyncEnabled(): Boolean {
    return runBlocking {
        val ytmSync = dataStore[YtmSyncKey] ?: true
        ytmSync && isUserLoggedIn()
    }
}

fun Context.isUserLoggedIn(): Boolean {
    return runBlocking {
        val cookie = dataStore[InnerTubeCookieKey] ?: ""
        "SAPISID" in parseCookieString(cookie) && isInternetConnected()
    }
}

fun Context.isInternetConnected(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
    return networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
}

fun Context.getLikeAutoDownload(): LikedAutodownloadMode  {
    return dataStore[LikedAutoDownloadKey].toEnum(LikedAutodownloadMode.OFF)
}