package com.example.pleosrantlog

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.pleosrantlog.data.backend.FakeBackend
import com.example.pleosrantlog.data.navi.NaviLauncher
import com.example.pleosrantlog.data.net.AndroidNetworkStatus
import com.example.pleosrantlog.data.vehicle.PleosVehicleDataSource
import com.example.pleosrantlog.data.voice.VoiceGuide
import com.example.pleosrantlog.ui.AppDeps
import com.example.pleosrantlog.ui.AppViewModel

class RentLogApp : Application() {
    /** Manual DI, created lazily so a missing Pleos service can never crash app start. */
    val deps: AppDeps by lazy {
        AppDeps(
            backend = FakeBackend(),
            vehicle = PleosVehicleDataSource(this),
            speaker = VoiceGuide(this),
            navi = NaviLauncher(this),
            network = AndroidNetworkStatus(this),
        )
    }

    val viewModelFactory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(deps) as T
    }
}
