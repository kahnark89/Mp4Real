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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.capsconc.arcshield.app.ui.GateTuningScreen
import com.capsconc.arcshield.app.ui.MainScreen
import com.capsconc.arcshield.app.ui.SessionViewModel
import com.capsconc.arcshield.app.ui.SettingsScreen
import com.capsconc.arcshield.debrief.repository.DebriefRepository
import com.capsconc.arcshield.debrief.ui.DebriefScreen
import com.capsconc.arcshield.debrief.ui.EventAnnotationScreen
import com.capsconc.arcshield.debrief.viewmodel.DebriefState
import com.capsconc.arcshield.debrief.viewmodel.DebriefViewModel
import com.capsconc.arcshield.labeler.ui.LabelerScreen
import com.capsconc.arcshield.schema.llm.LlmClient
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var llmClient: LlmClient

    // BLUETOOTH (pre-API 31) is a NORMAL permission — auto-granted, never needs runtime request.
    // On API 31+ it is deprecated; requesting it via requestMultiplePermissions silently returns
    // false in the grants callback, which breaks allGranted and blocks session start.
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
                    val navController   = rememberNavController()
                    val sessionViewModel: SessionViewModel = hiltViewModel()

                    NavHost(navController = navController, startDestination = "main") {

                        // ── Main capture screen ───────────────────────────
                        composable("main") {
                            MainScreen(
                                viewModel            = sessionViewModel,
                                onNavigateToLabeler  = { navController.navigate("labeler") },
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToDebrief  = { navController.navigate("debrief") },
                                onNavigateToGate     = { navController.navigate("gate") },
                                onRequestPermissions = {
                                    pendingStart = {
                                        sessionViewModel.startSession(this@MainActivity)
                                    }
                                    permissionLauncher.launch(requiredPermissions)
                                },
                            )
                        }

                        // ── Shadow-mode labeler ───────────────────────────
                        composable("labeler") {
                            LabelerScreen(llmClient = llmClient)
                        }

                        // ── Settings ──────────────────────────────────────
                        composable("settings") {
                            SettingsScreen(
                                onNavigateBack = { navController.popBackStack() },
                            )
                        }

                        // ── Gate tuning ───────────────────────────────────
                        composable("gate") {
                            GateTuningScreen(
                                onNavigateBack   = { navController.popBackStack() },
                                latestWindowFlow = sessionViewModel.latestWindow,
                            )
                        }

                        // ── Debrief queue ─────────────────────────────────
                        // DebriefViewModel is scoped to this backstack entry so
                        // the annotation route can share the same instance.
                        composable("debrief") { debriefEntry ->
                            val context = LocalContext.current
                            val factory = remember {
                                DebriefViewModel.Factory(DebriefRepository(context))
                            }
                            val debriefVm: DebriefViewModel = viewModel(
                                viewModelStoreOwner = debriefEntry,
                                factory             = factory,
                            )
                            DebriefScreen(
                                viewModel              = debriefVm,
                                onNavigateToAnnotation = { eventId ->
                                    navController.navigate("annotation/$eventId")
                                },
                            )
                        }

                        // ── Annotation form ───────────────────────────────
                        // Retrieves the DebriefViewModel from the "debrief" backstack entry
                        // so edits made here are visible when the user presses back to the queue.
                        composable(
                            route     = "annotation/{eventId}",
                            arguments = listOf(navArgument("eventId") { type = NavType.StringType }),
                        ) { annotationEntry ->
                            val context = LocalContext.current

                            val debriefEntry = remember(annotationEntry) {
                                try {
                                    navController.getBackStackEntry("debrief")
                                } catch (_: Exception) {
                                    null
                                }
                            }
                            val factory = remember {
                                DebriefViewModel.Factory(DebriefRepository(context))
                            }
                            val debriefVm: DebriefViewModel = if (debriefEntry != null) {
                                viewModel(viewModelStoreOwner = debriefEntry, factory = factory)
                            } else {
                                viewModel(factory = factory)
                            }

                            val debriefState by debriefVm.state.collectAsState()
                            val record = when (val s = debriefState) {
                                is DebriefState.Editing    -> s.record
                                is DebriefState.Submitting -> s.record
                                else                       -> null
                            }

                            if (record != null) {
                                EventAnnotationScreen(
                                    record   = record,
                                    onSave   = { debriefVm.updateRecord(it) },
                                    onSubmit = { debriefVm.submitEvent(it) },
                                    onCancel = {
                                        debriefVm.cancelEditing()
                                        navController.popBackStack()
                                    },
                                )
                            } else {
                                // Record was submitted or state was lost — return to queue
                                LaunchedEffect(Unit) {
                                    navController.popBackStack("debrief", inclusive = false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
