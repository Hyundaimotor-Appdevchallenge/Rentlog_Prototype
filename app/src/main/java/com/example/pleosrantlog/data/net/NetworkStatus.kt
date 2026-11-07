package com.example.pleosrantlog.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings

/** Reports whether the (real) network is usable. Offline makes the app behave like the F-08 DISCONNECTED case. */
fun interface NetworkStatus {
    fun isOnline(): Boolean
}

/** Simulation / unit tests. */
object AlwaysOnline : NetworkStatus {
    override fun isOnline() = true
}

/**
 * Pure decision (unit-testable).
 * Primary signal: airplane mode ON => offline.
 * Secondary (opt-in via [noNetworkMeansOffline], default OFF): no active internet network => offline, but only after a
 * network was seen at least once since process start ([everConnected]). It is off by default because the Pleos/Android
 * emulator's cellular data often does not come back after an airplane-mode toggle, which would make the F-08 retry
 * impossible during a recording.
 */
fun decideOnline(
    airplaneOn: Boolean, hasActiveNetwork: Boolean, everConnected: Boolean, noNetworkMeansOffline: Boolean = false,
): Boolean = when {
    airplaneOn -> false
    hasActiveNetwork -> true
    else -> !(noNetworkMeansOffline && everConnected)
}

class AndroidNetworkStatus(context: Context, private val noNetworkMeansOffline: Boolean = false) : NetworkStatus {
    private val app = context.applicationContext
    @Volatile private var everConnected = false

    override fun isOnline(): Boolean = try {
        val airplane = Settings.Global.getInt(app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val active = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        if (active && !airplane) everConnected = true
        decideOnline(airplane, active, everConnected, noNetworkMeansOffline)
    } catch (t: Throwable) {
        true
    }
}
