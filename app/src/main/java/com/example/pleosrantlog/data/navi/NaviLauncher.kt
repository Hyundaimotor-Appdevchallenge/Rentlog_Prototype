package com.example.pleosrantlog.data.navi

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener

fun interface NaviRouter { fun navigate(name: String, lat: Double, lng: Double): Boolean }

/**
 * NaviHelper.requestRoute. NaviHelper connects asynchronously, so it is initialised at construction
 * (app start); a request made before the connection is ready is queued and fired on connect
 * (timeout ~3 s). Fallbacks: launch the built-in navigation app, then a Toast as last resort.
 */
class NaviLauncher(context: Context) : NaviRouter {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var helper: NaviHelper? = null
    @Volatile private var ready = false
    private var pending: RouteInfo? = null
    private var timeout: Runnable? = null

    init { initHelper() }

    private fun initHelper() {
        if (helper != null) return
        try {
            val h = NaviHelper(app)
            h.addListener(object : NaviHelperEventListener {
                override fun onRouteStarted(routeStartInfo: ai.pleos.playground.navi.data.RouteStartInfo) { Log.i(TAG, "onRouteStarted") }
                override fun onError(errorCode: ai.pleos.playground.navi.constants.NaviErrorCode) {
                    Log.w(TAG, "navi onError: $errorCode")
                    main.post { launchNaviApp() }
                }
            })
            h.initialize(
                { Log.i(TAG, "NaviHelper connected"); main.post { ready = true; firePending() } },
                { Log.w(TAG, "NaviHelper init failed", it); main.post { helper = null; failPending() } },
            )
            helper = h
        } catch (t: Throwable) { Log.w(TAG, "NaviHelper unavailable", t) }
    }

    override fun navigate(name: String, lat: Double, lng: Double): Boolean {
        val route = RouteInfo(latitude = lat, longitude = lng, poiName = name, poiId = name, address = name, poiSubId = "0")
        if (helper == null) initHelper()
        if (helper == null) return fallback(name)
        if (ready) return send(route)
        pending = route
        timeout?.let(main::removeCallbacks)
        timeout = Runnable { Log.w(TAG, "navi connect timeout"); failPending() }.also { main.postDelayed(it, 3000) }
        return true
    }

    private fun firePending() {
        val r = pending ?: return
        pending = null
        timeout?.let(main::removeCallbacks)
        send(r)
    }

    private fun failPending() {
        val r = pending ?: return
        pending = null
        timeout?.let(main::removeCallbacks)
        fallback(r.poiName)
    }

    private fun send(r: RouteInfo): Boolean = try {
        helper!!.requestRoute(r)
        Log.i(TAG, "requestRoute sent: ${r.poiName} (${r.latitude},${r.longitude})")
        // Make sure the navigation app is visible even if it does not self-foreground.
        main.postDelayed({ launchNaviApp() }, 800)
        true
    } catch (t: Throwable) {
        Log.w(TAG, "requestRoute failed", t)
        fallback(r.poiName)
    }

    private fun launchNaviApp(): Boolean = try {
        val i = app.packageManager.getLaunchIntentForPackage(NAVI_PKG)
            ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(NAVI_PKG)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(i)
        true
    } catch (t: Throwable) { Log.w(TAG, "launch navi app failed", t); false }

    private fun fallback(name: String): Boolean {
        if (launchNaviApp()) return true
        try { Toast.makeText(app, "내비 앱을 열 수 없어 안내만 표시해요", Toast.LENGTH_SHORT).show() } catch (_: Throwable) {}
        return false
    }

    fun release() { try { helper?.release() } catch (_: Throwable) {} }

    private companion object {
        const val TAG = "NaviLauncher"
        const val NAVI_PKG = "ai.umos.maps.android.navigation.app"
    }
}
