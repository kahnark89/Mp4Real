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
package com.capsconc.arcshield.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay

@Composable
fun MainScreen(
    onNavigateToLabeler:  () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDebrief:  () -> Unit,
    onNavigateToGate:     () -> Unit,
    onRequestPermissions: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val state          by viewModel.sessionState.collectAsState()
    val candidateCount by viewModel.candidateCount.collectAsState()
    val cameraPreview  by viewModel.cameraPreview.collectAsState()
    val voiceActive    by viewModel.voiceAnnotationActive.collectAsState()
    val logPath        by viewModel.lastSessionLogPath.collectAsState()
    val dwellProgress  by viewModel.dwellProgress.collectAsState()
    val isDwelling     by viewModel.isDwelling.collectAsState()
    val lastDwellSec   by viewModel.lastDwellSec.collectAsState()
    val settingsVm: SettingsViewModel = hiltViewModel()

    val isBuilding  = state is SessionViewModel.SessionState.Building
    val isRecording = state is SessionViewModel.SessionState.Recording
    val isFinished  = state is SessionViewModel.SessionState.Finished
    val isActive    = isBuilding || isRecording

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("CAPTURE", "EVENTS", "CONFIG")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ArcShieldColors.Background),
    ) {
        // ── Header ────────────────────────────────────────────────────────
        AppHeader(state = state, isRecording = isRecording, isBuilding = isBuilding)

        // ── Tab row ───────────────────────────────────────────────────────
        TabRow(
            selectedTabIndex  = selectedTab,
            containerColor    = ArcShieldColors.Surface,
            contentColor      = ArcShieldColors.OnSurface,
            indicator         = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color    = ArcShieldColors.Blue,
                )
            },
            divider = {
                Box(Modifier.fillMaxWidth().height(1.dp).background(ArcShieldColors.Outline))
            },
        ) {
            tabs.forEachIndexed { index, label ->
                Tab(
                    selected = selectedTab == index,
                    onClick  = { selectedTab = index },
                    text = {
                        Text(
                            text       = label,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            fontSize   = 11.sp,
                            color      = if (selectedTab == index) ArcShieldColors.Blue else ArcShieldColors.OnSurfaceVariant,
                        )
                    },
                )
            }
        }

        // ── Tab content ───────────────────────────────────────────────────
        when (selectedTab) {
            0 -> CaptureTab(
                state            = state,
                candidateCount   = candidateCount,
                cameraPreview    = cameraPreview,
                voiceActive      = voiceActive,
                isBuilding       = isBuilding,
                isRecording      = isRecording,
                isActive         = isActive,
                dwellProgress    = dwellProgress,
                isDwelling       = isDwelling,
                lastDwellSec     = lastDwellSec,
                onRequestPermissions = onRequestPermissions,
                onStartStop      = {
                    if (isRecording) viewModel.stopSession()
                },
                onManualTrigger  = { viewModel.manualTrigger() },
                onToggleVoice    = { viewModel.toggleVoiceAnnotation() },
                onNewSession     = { viewModel.resetToIdle() },
            )
            1 -> EventsTab(
                state           = state,
                candidateCount  = candidateCount,
                logPath         = logPath,
                isRecording     = isRecording,
                isFinished      = isFinished,
                onNavigateToDebrief  = onNavigateToDebrief,
                onNewCiaerEntry = {
                    viewModel.manualTrigger()
                    onNavigateToDebrief()
                },
            )
            2 -> ConfigTab(
                settingsVm          = settingsVm,
                onNavigateToGate    = onNavigateToGate,
                onNavigateToLabeler = onNavigateToLabeler,
                onNavigateToSettings = onNavigateToSettings,
            )
        }
    }
}

// ── Header ─────────────────────────────────────────────────────────────────

@Composable
private fun AppHeader(
    state: SessionViewModel.SessionState,
    isRecording: Boolean,
    isBuilding: Boolean,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue   = 0.4f,
        targetValue    = 1.0f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dotPulse",
    )

    val dotColor = when (state) {
        is SessionViewModel.SessionState.Recording -> ArcShieldColors.Green.copy(alpha = pulseAlpha)
        is SessionViewModel.SessionState.Building  -> ArcShieldColors.Amber.copy(alpha = pulseAlpha)
        is SessionViewModel.SessionState.Error     -> ArcShieldColors.Red
        is SessionViewModel.SessionState.Finished  -> ArcShieldColors.Blue
        else                                       -> ArcShieldColors.OnSurfaceVariant
    }

    val statusLabel = when (state) {
        is SessionViewModel.SessionState.Idle      -> "IDLE"
        is SessionViewModel.SessionState.Building  -> "BUILDING BASELINE"
        is SessionViewModel.SessionState.Recording -> "SHADOW MODE"
        is SessionViewModel.SessionState.Finished  -> "FINISHED"
        is SessionViewModel.SessionState.Error     -> "ERROR"
    }

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .background(ArcShieldColors.Surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // AS badge with blue/violet gradient
        Box(
            modifier        = Modifier
                .size(32.dp)
                .background(
                    Brush.linearGradient(listOf(ArcShieldColors.Blue, ArcShieldColors.Violet)),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text       = "AS",
                color      = Color.White,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize   = 11.sp,
            )
        }

        Text(
            text       = "ArcShield",
            color      = ArcShieldColors.OnBackground,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 16.sp,
        )

        Spacer(Modifier.weight(1f))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(modifier = Modifier.size(8.dp).background(dotColor, CircleShape))
            Text(
                text       = statusLabel,
                color      = dotColor,
                fontFamily = FontFamily.Monospace,
                fontSize   = 10.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ── CAPTURE TAB ─────────────────────────────────────────────────────────────

@Composable
private fun CaptureTab(
    state: SessionViewModel.SessionState,
    candidateCount: Int,
    cameraPreview: Preview?,
    voiceActive: Boolean,
    isBuilding: Boolean,
    isRecording: Boolean,
    isActive: Boolean,
    dwellProgress: Float,
    isDwelling: Boolean,
    lastDwellSec: Float,
    onRequestPermissions: () -> Unit,
    onStartStop: () -> Unit,
    onManualTrigger: () -> Unit,
    onToggleVoice: () -> Unit,
    onNewSession: () -> Unit,
) {
    var elapsedSec by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            elapsedSec = 0L
            while (true) {
                delay(1_000)
                elapsedSec++
            }
        } else {
            elapsedSec = 0L
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── Camera / dwell view ───────────────────────────────────────────
        val showCamera = cameraPreview != null && isActive
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(6.dp))
                .background(ArcShieldColors.SurfaceVariant)
                .border(1.dp, ArcShieldColors.Outline, RoundedCornerShape(6.dp)),
        ) {
            if (showCamera) {
                key(cameraPreview) {
                    val preview = cameraPreview!!
                    DisposableEffect(preview) { onDispose { preview.setSurfaceProvider(null) } }
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            }
                        },
                        update  = { it.setSurfaceProvider(preview.surfaceProvider) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                Box(
                    modifier        = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text       = if (isBuilding) "BUILDING BASELINE…" else "CAMERA INACTIVE",
                        color      = ArcShieldColors.OnSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 12.sp,
                    )
                }
            }

            // Overlay: elapsed time (top-left)
            if (isRecording) {
                val h = elapsedSec / 3600
                val m = (elapsedSec % 3600) / 60
                val s = elapsedSec % 60
                val timeStr = if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
                Text(
                    text     = timeStr,
                    color    = ArcShieldColors.Green,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }

            // Overlay: candidate count (top-right)
            if (isActive) {
                Text(
                    text       = "⬡ $candidateCount",
                    color      = ArcShieldColors.Blue,
                    fontFamily = FontFamily.Monospace,
                    fontSize   = 11.sp,
                    modifier   = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }

            // Overlay: dwell indicator (bottom-left)
            if (isRecording && (dwellProgress > 0f || isDwelling)) {
                DwellOverlay(
                    progress    = dwellProgress,
                    isDwelling  = isDwelling,
                    lastDwellSec = lastDwellSec,
                    modifier    = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                )
            }

            // Voice recording indicator (bottom-right)
            if (voiceActive) {
                val voicePulse = rememberInfiniteTransition(label = "voicePulse")
                val voiceAlpha by voicePulse.animateFloat(
                    initialValue  = 0.5f,
                    targetValue   = 1.0f,
                    animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
                    label         = "voiceAlpha",
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(ArcShieldColors.Red.copy(alpha = voiceAlpha * 0.7f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(Modifier.size(6.dp).background(ArcShieldColors.Red, CircleShape))
                    Text("REC", color = ArcShieldColors.Red, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
            }
        }

        // ── CIAER phase bar (only when recording or finished) ─────────────
        if (isRecording || state is SessionViewModel.SessionState.Finished) {
            CiaerPhaseBar()
        }

        // ── Session control ────────────────────────────────────────────────
        DarkSectionLabel("Session Control")

        when (state) {
            is SessionViewModel.SessionState.Idle -> Button(
                onClick  = onRequestPermissions,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = ArcShieldColors.Blue),
            ) {
                androidx.compose.material3.Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Start Shadow Capture", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
            }

            is SessionViewModel.SessionState.Building -> Button(
                onClick  = {},
                enabled  = false,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = ArcShieldColors.Amber.copy(alpha = 0.2f)),
            ) {
                Text(
                    "Building I-frame baseline…",
                    color      = ArcShieldColors.Amber,
                    fontFamily = FontFamily.Monospace,
                )
            }

            is SessionViewModel.SessionState.Recording -> Button(
                onClick  = onStartStop,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = ArcShieldColors.Red.copy(alpha = 0.85f)),
            ) {
                androidx.compose.material3.Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Stop Session", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
            }

            is SessionViewModel.SessionState.Finished -> Button(
                onClick  = onNewSession,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = ArcShieldColors.Blue),
            ) {
                Text("New Session", fontFamily = FontFamily.Monospace)
            }

            is SessionViewModel.SessionState.Error -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ErrorBanner(state.message)
                Button(
                    onClick  = onNewSession,
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(containerColor = ArcShieldColors.SurfaceVariant),
                ) {
                    Text("Dismiss", fontFamily = FontFamily.Monospace, color = ArcShieldColors.OnSurface)
                }
            }
        }

        // ── Capture event ──────────────────────────────────────────────────
        DarkSectionLabel("Capture Event")

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DarkOutlinedButton(
                onClick  = onManualTrigger,
                enabled  = isRecording,
                modifier = Modifier.weight(1f),
                label    = "Manual Trigger",
            )

            Button(
                onClick  = onToggleVoice,
                enabled  = isRecording,
                modifier = Modifier.weight(1f),
                colors   = ButtonDefaults.buttonColors(
                    containerColor        = if (voiceActive) ArcShieldColors.Red.copy(alpha = 0.85f) else ArcShieldColors.SurfaceVariant,
                    disabledContainerColor = ArcShieldColors.SurfaceVariant.copy(alpha = 0.5f),
                ),
            ) {
                androidx.compose.material3.Icon(
                    imageVector        = if (voiceActive) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = null,
                    modifier           = Modifier.size(16.dp),
                    tint               = if (voiceActive) ArcShieldColors.Red else ArcShieldColors.OnSurface,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text       = if (voiceActive) "Stop Voice" else "Voice Note",
                    fontFamily = FontFamily.Monospace,
                    fontSize   = 13.sp,
                    color      = if (voiceActive) ArcShieldColors.Red else ArcShieldColors.OnSurface,
                )
            }
        }

        // ── Console ────────────────────────────────────────────────────────
        DarkSectionLabel("Console")
        ConsolePanel(
            modifier = Modifier
                .border(1.dp, ArcShieldColors.Outline, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp)),
        )

        Spacer(Modifier.height(8.dp))
    }
}

// ── EVENTS TAB ───────────────────────────────────────────────────────────────

@Composable
private fun EventsTab(
    state: SessionViewModel.SessionState,
    candidateCount: Int,
    logPath: String?,
    isRecording: Boolean,
    isFinished: Boolean,
    onNavigateToDebrief: () -> Unit,
    onNewCiaerEntry: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Queue summary card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(containerColor = ArcShieldColors.SurfaceVariant),
            shape    = RoundedCornerShape(6.dp),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "CANDIDATE QUEUE",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 11.sp,
                    color      = ArcShieldColors.OnSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text       = "$candidateCount",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 28.sp,
                        color      = if (candidateCount > 0) ArcShieldColors.Blue else ArcShieldColors.OnSurfaceVariant,
                    )
                    Text(
                        text  = "windows captured this session",
                        color = ArcShieldColors.OnSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        // CIAER phase legend
        CiaerPhaseBar()

        DarkSectionLabel("Data Input")

        Button(
            onClick  = onNewCiaerEntry,
            enabled  = isRecording,
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(containerColor = ArcShieldColors.Blue),
        ) {
            Text("New Manual CIAER+ Entry", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
        }

        DarkSectionLabel("Review & Annotate")

        Button(
            onClick  = onNavigateToDebrief,
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(containerColor = ArcShieldColors.SurfaceVariant),
        ) {
            val label = if (candidateCount > 0 && (isRecording || isFinished))
                "Debrief Queue  ($candidateCount)" else "Open Debrief Queue"
            Text(label, fontFamily = FontFamily.Monospace, color = ArcShieldColors.Blue)
        }

        if (isRecording || isFinished) {
            DarkSectionLabel("Session Data")

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DarkOutlinedButton(
                    onClick  = {
                        if (logPath != null) {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("session log", logPath))
                            Toast.makeText(context, "Path copied", Toast.LENGTH_SHORT).show()
                            AppLogger.info("Export", "Copied log path: $logPath")
                        }
                    },
                    enabled  = logPath != null,
                    modifier = Modifier.weight(1f),
                    label    = "Export Log",
                    icon = {
                        androidx.compose.material3.Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                    },
                )

                DarkOutlinedButton(
                    onClick  = onNavigateToDebrief,
                    enabled  = candidateCount > 0,
                    modifier = Modifier.weight(1f),
                    label    = "Import to Queue",
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ── CONFIG TAB ────────────────────────────────────────────────────────────────

@Composable
private fun ConfigTab(
    settingsVm: SettingsViewModel,
    onNavigateToGate: () -> Unit,
    onNavigateToLabeler: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val settings = settingsVm.settings
    val facilityId by settings.facilityId.collectAsState()
    val lineId     by settings.lineId.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DarkSectionLabel("Facility Context")

        DarkTextField(
            value       = facilityId,
            onValueChange = { settingsVm.setFacilityId(it) },
            label       = "Facility ID",
            placeholder = "e.g. hollowell_ppvc",
        )

        DarkTextField(
            value         = lineId,
            onValueChange = { settingsVm.setLineId(it) },
            label         = "Line ID",
            placeholder   = "e.g. line1_extrusion",
        )

        DarkSectionLabel("Analysis & Tuning")

        ConfigNavButton(
            label    = "Gate Tuning — Λ thresholds & channel control",
            accent   = ArcShieldColors.Blue,
            onClick  = onNavigateToGate,
        )

        ConfigNavButton(
            label    = "Shadow Labeler — hand-label candidate windows",
            accent   = ArcShieldColors.Green,
            onClick  = onNavigateToLabeler,
        )

        DarkSectionLabel("All Settings")

        DarkOutlinedButton(
            onClick  = onNavigateToSettings,
            modifier = Modifier.fillMaxWidth(),
            label    = "Advanced Settings →",
        )

        Spacer(Modifier.height(8.dp))
    }
}

// ── Shared composables ────────────────────────────────────────────────────────

@Composable
private fun DarkSectionLabel(text: String) {
    Text(
        text       = text.uppercase(),
        style      = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color      = ArcShieldColors.OnSurfaceVariant,
        letterSpacing = 1.5.sp,
    )
}

@Composable
private fun DarkOutlinedButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
) {
    OutlinedButton(
        onClick  = onClick,
        enabled  = enabled,
        modifier = modifier,
        colors   = ButtonDefaults.outlinedButtonColors(
            contentColor         = ArcShieldColors.OnSurface,
            disabledContentColor = ArcShieldColors.OnSurfaceVariant,
        ),
        border = BorderStroke(
            1.dp,
            if (enabled) ArcShieldColors.Outline else ArcShieldColors.Outline.copy(alpha = 0.5f),
        ),
    ) {
        icon?.invoke()
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    }
}

@Composable
private fun DarkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        label         = { Text(label, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
        placeholder   = { Text(placeholder, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = ArcShieldColors.OnSurfaceVariant) },
        modifier      = modifier.fillMaxWidth(),
        singleLine    = true,
        textStyle     = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor    = ArcShieldColors.Blue,
            unfocusedBorderColor  = ArcShieldColors.Outline,
            cursorColor           = ArcShieldColors.Blue,
            focusedLabelColor     = ArcShieldColors.Blue,
            unfocusedLabelColor   = ArcShieldColors.OnSurfaceVariant,
            focusedTextColor      = ArcShieldColors.OnSurface,
            unfocusedTextColor    = ArcShieldColors.OnSurface,
        ),
    )
}

@Composable
private fun ConfigNavButton(
    label: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Card(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = ArcShieldColors.SurfaceVariant),
        shape    = RoundedCornerShape(6.dp),
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text       = label,
                fontFamily = FontFamily.Monospace,
                fontSize   = 13.sp,
                color      = ArcShieldColors.OnSurface,
                modifier   = Modifier.weight(1f),
            )
            Text("→", color = accent, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = ArcShieldColors.Red.copy(alpha = 0.12f)),
        shape    = RoundedCornerShape(6.dp),
    ) {
        Text(
            text       = "⚠ $message",
            color      = ArcShieldColors.Red,
            fontFamily = FontFamily.Monospace,
            fontSize   = 12.sp,
            modifier   = Modifier.padding(12.dp),
        )
    }
}

// ── CIAER phase progress bar ─────────────────────────────────────────────────

@Composable
private fun CiaerPhaseBar() {
    val phases = listOf(
        "CAUSE"     to ArcShieldColors.Red,
        "INTUITION" to ArcShieldColors.Amber,
        "ACTION"    to ArcShieldColors.Blue,
        "EFFECT"    to ArcShieldColors.Green,
        "RESULT"    to ArcShieldColors.Violet,
    )

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        phases.forEach { (label, color) ->
            Column(
                modifier          = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(color, RoundedCornerShape(2.dp)),
                )
                Text(
                    text       = label,
                    color      = color.copy(alpha = 0.8f),
                    fontFamily = FontFamily.Monospace,
                    fontSize   = 8.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}

// ── Dwell overlay ─────────────────────────────────────────────────────────────

@Composable
private fun DwellOverlay(
    progress: Float,
    isDwelling: Boolean,
    lastDwellSec: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            val dwellColor = if (isDwelling) ArcShieldColors.Amber else ArcShieldColors.OnSurfaceVariant
            Box(Modifier.size(6.dp).background(dwellColor, CircleShape))
            Text(
                text       = if (isDwelling) "DWELLING" else "DWELL",
                color      = dwellColor,
                fontFamily = FontFamily.Monospace,
                fontSize   = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            if (isDwelling && lastDwellSec > 0f) {
                Text(
                    text       = "${"%.1f".format(lastDwellSec)}s",
                    color      = ArcShieldColors.Amber,
                    fontFamily = FontFamily.Monospace,
                    fontSize   = 10.sp,
                )
            }
        }
        LinearProgressIndicator(
            progress       = { progress },
            modifier       = Modifier.width(80.dp).height(2.dp),
            color          = if (isDwelling) ArcShieldColors.Amber else ArcShieldColors.Blue,
            trackColor     = ArcShieldColors.Outline,
            strokeCap      = StrokeCap.Round,
        )
    }
}
