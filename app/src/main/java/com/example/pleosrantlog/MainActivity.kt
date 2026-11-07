package com.example.pleosrantlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pleosrantlog.ui.AppViewModel
import com.example.pleosrantlog.ui.Screen
import com.example.pleosrantlog.ui.UiState
import com.example.pleosrantlog.ui.demo.DemoControlSheet
import com.example.pleosrantlog.ui.screens.*
import com.example.pleosrantlog.ui.theme.PleosRantLogTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels { (application as RentLogApp).viewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PleosRantLogTheme {
                // Top screen of the app: Back moves the task to background instead of finishing.
                BackHandler { moveTaskToBack(true) }
                val state by vm.state.collectAsStateWithLifecycle()
                val snackbar = remember { SnackbarHostState() }
                LaunchedEffect(state.message) {
                    state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
                }
                Box(Modifier.fillMaxSize()) {
                    ScreenHost(state, vm)
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
                    if (state.showDemoControl) DemoControlSheet(state, vm)
                }
            }
        }
    }
}

@Composable
fun ScreenHost(state: UiState, vm: AppViewModel) {
    when (state.screen) {
        Screen.StartVerify -> StartVerifyScreen(state, vm)
        is Screen.ErrorState -> ErrorStateScreen(state, vm)
        Screen.PickupInspection -> PickupInspectionScreen(state, vm)
        Screen.Driving -> DrivingScreen(state, vm)
        Screen.CautionWarning -> CautionWarningScreen(state, vm)
        Screen.StoppedResponse -> StoppedResponseScreen(state, vm)
        Screen.ReturnNotice -> ReturnNoticeScreen(state, vm)
        Screen.ReturnInspection -> ReturnInspectionScreen(state, vm)
        Screen.Emergency -> EmergencyScreen(state, vm)
        Screen.ReturnComplete -> ReturnCompleteScreen(state, vm)
        Screen.Statement -> StatementScreen(state, vm)
    }
}
