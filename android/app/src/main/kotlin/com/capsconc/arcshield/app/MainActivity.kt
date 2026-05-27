/*
 * Intellectual Property and Trademark Notice
 *
 * mp4Real™, ArcShield™, CIAER™, and CIAER+™ are trademarks of Capps Consulting
 * Company LLC. The multi-track cyber-physical capture architecture, the
 * application of log-likelihood ratio (LLR) gating to multimodal industrial
 * decision events, and the behavioral codebook discretization methods described
 * in this document are the proprietary intellectual property of Kahn Capps and
 * Capps Consulting Company LLC. Unauthorized commercial use, reproduction, or
 * implementation of the mp4Real™ container architecture or the CIAER™ and CIAER+™
 * schemas without explicit licensing is prohibited. All rights reserved.
 */
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
import com.capsconc.arcshield.app.ui.SettingsScreen
import com.capsconc.arcshield.labeler.ui.LabelerScreen
import com.capsconc.arcshield.schema.llm.LlmClient
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var llmClient: LlmClient

    // All permissions required before startSession() is called.
    //
    // BLUETOOTH (pre-API 31) is a NORMAL permission — auto-granted at install time,
    // never needs a runtime request. On API 31+ it is deprecated; requesting it via
    // requestMultiplePermissions silently returns false in the grants callback, which
    // causes allGranted to fail and blocks session start.
    private val requiredPermissions: Array<String> by lazy {
        buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
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
            val denied = grants.filter { !it.value }.keys
            if (denied.isEmpty()) {
                pendingStart?.invoke()
            } else {
                android.widget.Toast.makeText(
                    this,
                    "Required permissions denied: ${denied.joinToString()}",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
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
                                onNavigateToSettings = {
                                    navController.navigate("settings")
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
                            LabelerScreen(llmClient = llmClient)
                        }

                        composable("settings") {
                            SettingsScreen(
                                onNavigateBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }
}
