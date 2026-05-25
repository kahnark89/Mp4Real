package com.capsconc.arcshield.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.capsconc.arcshield.app.ui.MainScreen
import com.capsconc.arcshield.app.ui.SessionViewModel
import com.capsconc.arcshield.labeler.ui.LabelerScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // All permissions required before startSession() is called.
    private val requiredPermissions: Array<String> by lazy {
        buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.BLUETOOTH)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }.toTypedArray()
    }

    // Deferred start: called after permission launcher resolves.
    private var pendingStart: (() -> Unit)? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val allGranted = grants.all { it.value }
            if (allGranted) pendingStart?.invoke()
            pendingStart = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    // SessionViewModel is shared between MainScreen and the nav graph
                    // so candidateWindows keeps flowing while the user navigates to Labeler.
                    val sessionViewModel: SessionViewModel = hiltViewModel()

                    NavHost(navController = navController, startDestination = "main") {

                        composable("main") {
                            MainScreen(
                                viewModel = sessionViewModel,
                                onNavigateToLabeler = {
                                    navController.navigate("labeler")
                                },
                                onRequestPermissions = {
                                    pendingStart = {
                                        sessionViewModel.startSession(this@MainActivity)
                                    }
                                    permissionLauncher.launch(requiredPermissions)
                                },
                            )
                        }

                        composable("labeler") {
                            // LabelerScreen manages its own internal back (clearSelection).
                            // System back + NavHost handle returning to "main".
                            LabelerScreen()
                        }
                    }
                }
            }
        }
    }
}
