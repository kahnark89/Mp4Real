package com.capsconc.arcshield.labeler

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import com.capsconc.arcshield.schema.llm.LlmClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class LabelerViewModel(
    application: Application,
    llmClient: LlmClient,
) : AndroidViewModel(application) {

    // ---- ExoPlayer (offline playback) -------------------------------------

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(application).build()

    // elapsedRealtimeNanos at session start — read from .ndjson header for PTS math
    private val _sessionStartNanos = MutableStateFlow(0L)
    val sessionStartNanos: StateFlow<Long> = _sessionStartNanos

    // True once bindVideoFile() has loaded a media item; drives VideoPlayerView guard.
    private val _hasVideo = MutableStateFlow(false)
    val hasVideo: StateFlow<Boolean> = _hasVideo

    // ---- Voice elicitation ------------------------------------------------

    private val voiceElicitation = VoiceElicitationManager(application, llmClient)

    sealed class ElicitationState {
        object Idle      : ElicitationState()
        object Listening : ElicitationState()
        data class Done(val action: ElicitedAction) : ElicitationState()
        data class Error(val msg: String) : ElicitationState()
    }

    private val _elicitationState = MutableStateFlow<ElicitationState>(ElicitationState.Idle)
    val elicitationState: StateFlow<ElicitationState> = _elicitationState

    // Last elicited action, keyed by detectedAtNanos of the window it was captured for
    private val _elicitedActions = MutableStateFlow<Map<Long, ElicitedAction>>(emptyMap())
    val elicitedActions: StateFlow<Map<Long, ElicitedAction>> = _elicitedActions

    // ---- Shift log list ---------------------------------------------------

    private val _shiftLogs = MutableStateFlow<List<File>>(emptyList())
    val shiftLogs: StateFlow<List<File>> = _shiftLogs

    // ---- Selection + raw window data --------------------------------------

    private val _selectedLog = MutableStateFlow<File?>(null)
    val selectedLog: StateFlow<File?> = _selectedLog

    private val _allWindows = MutableStateFlow<List<LabeledWindow>>(emptyList())
    val allWindows: StateFlow<List<LabeledWindow>> = _allWindows

    // ---- NMS toggle -------------------------------------------------------

    private val _suppressionEnabled = MutableStateFlow(true)
    val suppressionEnabled: StateFlow<Boolean> = _suppressionEnabled

    val windows: StateFlow<List<LabeledWindow>> =
        combine(_allWindows, _suppressionEnabled) { all, suppress ->
            if (suppress) {
                val peaks = NonMaxSuppressor.suppress(all.map { it.window })
                val peakSet = peaks.map { it.detectedAtNanos }.toHashSet()
                all.filter { it.window.detectedAtNanos in peakSet }
            } else {
                all
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ---- τ suggestion -----------------------------------------------------

    val tauSuggestion: StateFlow<TauCalibrator.TauSuggestion?> =
        windows.map { TauCalibrator.suggest(it) }
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    // ---- Export state -----------------------------------------------------

    sealed class ExportState {
        object Idle : ExportState()
        object Exporting : ExportState()
        data class Done(val file: File) : ExportState()
        data class Error(val msg: String) : ExportState()
    }

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState

    // ---- Initialization ---------------------------------------------------

    init { refreshShiftLogs() }

    fun refreshShiftLogs() {
        val filesDir = getApplication<Application>().filesDir
        _shiftLogs.value = CandidateWindowLog.listShiftLogs(filesDir)
    }

    // ---- Log selection + video binding ------------------------------------

    fun selectLog(file: File) {
        _selectedLog.value = file
        viewModelScope.launch(Dispatchers.IO) {
            val log = CandidateWindowLog(file)
            val sessionStart = log.readSessionStartNanos()
            val windows = log.read().map { LabeledWindow(it) }
            withContext(Dispatchers.Main) {
                _sessionStartNanos.value = sessionStart
                _allWindows.value = windows
                bindVideoFile(file)
            }
        }
    }

    /**
     * Finds the mp4 file that corresponds to the given .ndjson log by shared
     * session ID prefix and loads it into ExoPlayer.
     */
    private fun bindVideoFile(ndjsonFile: File) {
        val filesDir = getApplication<Application>().filesDir
        val mp4 = File(filesDir, "sessions/session_${ndjsonFile.nameWithoutExtension}.mp4")
        _hasVideo.value = mp4.exists()
        if (!mp4.exists()) return
        exoPlayer.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(mp4)))
        exoPlayer.prepare()
    }

    fun clearSelection() {
        _selectedLog.value = null
        _allWindows.value = emptyList()
        _sessionStartNanos.value = 0L
        _elicitedActions.value = emptyMap()
        _hasVideo.value = false
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
    }

    // ---- Seeking ----------------------------------------------------------

    /** Seeks the player to 30 s before the candidate window's detected timestamp. */
    fun seekToWindow(detectedAtNanos: Long) {
        val start = _sessionStartNanos.value
        if (start == 0L) return
        val ptsMsRaw = (detectedAtNanos - start) / 1_000_000L
        val seekMs   = maxOf(0L, ptsMsRaw - SEEK_PRE_WINDOW_MS)
        exoPlayer.seekTo(seekMs)
        exoPlayer.play()
    }

    // ---- Labeling ---------------------------------------------------------

    fun setLabel(detectedAtNanos: Long, label: Label) {
        _allWindows.value = _allWindows.value.map { lw ->
            if (lw.window.detectedAtNanos == detectedAtNanos) lw.copy(label = label) else lw
        }
    }

    fun toggleSuppression() { _suppressionEnabled.value = !_suppressionEnabled.value }

    // ---- Voice elicitation ------------------------------------------------

    /**
     * Runs the voice elicitation pipeline for the given candidate window:
     * prompts the operator via TTS, captures their response via STT, sends to Claude
     * for entity resolution, and stores the result keyed by [detectedAtNanos].
     */
    fun elicitForWindow(detectedAtNanos: Long) {
        viewModelScope.launch {
            _elicitationState.value = ElicitationState.Listening
            val relSec = if (_sessionStartNanos.value != 0L)
                (detectedAtNanos - _sessionStartNanos.value) / 1_000_000_000L else 0L
            val prompt = "At ${formatRelativeTime(relSec)}, " +
                "what was your constraint and intended adjustment for this event?"
            try {
                val action = voiceElicitation.elicit(prompt)
                _elicitedActions.value = _elicitedActions.value + (detectedAtNanos to action)
                _elicitationState.value = ElicitationState.Done(action)
            } catch (e: Exception) {
                _elicitationState.value = ElicitationState.Error(e.message ?: "elicitation failed")
            }
        }
    }

    fun resetElicitationState() { _elicitationState.value = ElicitationState.Idle }

    // ---- Export -----------------------------------------------------------

    /**
     * Writes a `.labels.json` export file alongside the source log.
     * Includes [LabelExportEntry.provenanceClass], [LabelExportEntry.status], and
     * any [LabelExportEntry.elicitedAction] captured for each window.
     */
    fun exportLabels() {
        val log = _selectedLog.value ?: return
        val labeled = windows.value
        if (labeled.isEmpty()) return

        _exportState.value = ExportState.Exporting
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val actions = _elicitedActions.value
                val entries = labeled
                    .filter { it.label != Label.UNLABELED }
                    .map { lw ->
                        val routing = ConvergenceRouter.firstPass()
                        LabelExportEntry(
                            detectedAtNanos = lw.window.detectedAtNanos,
                            lambda          = lw.window.lambda,
                            label           = lw.label.name,
                            provenanceClass = routing.provenanceClass,
                            status          = routing.status,
                            elicitedAction  = actions[lw.window.detectedAtNanos],
                        )
                    }
                val json = Json { prettyPrint = true }
                val out  = File(log.parent, log.nameWithoutExtension + ".labels.json")
                out.writeText(json.encodeToString(entries))
                withContext(Dispatchers.Main) { _exportState.value = ExportState.Done(out) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _exportState.value = ExportState.Error(e.message ?: "export failed")
                }
            }
        }
    }

    fun resetExportState() { _exportState.value = ExportState.Idle }

    // ---- Lifecycle --------------------------------------------------------

    override fun onCleared() {
        super.onCleared()
        exoPlayer.release()
        voiceElicitation.release()
    }

    // ---- Helpers ----------------------------------------------------------

    private fun formatRelativeTime(relSec: Long): String {
        val h = relSec / 3600; val m = (relSec % 3600) / 60; val s = relSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    // ---- Factory ----------------------------------------------------------

    companion object {
        private const val SEEK_PRE_WINDOW_MS = 30_000L  // seek 30 s before detection

        fun factory(llmClient: LlmClient) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                return LabelerViewModel(application, llmClient) as T
            }
        }
    }
}
