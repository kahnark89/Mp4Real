package com.capsconc.arcshield.labeler

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

class LabelerViewModel(application: Application) : AndroidViewModel(application) {

    // ---- Shift log list ---------------------------------------------------

    private val _shiftLogs = MutableStateFlow<List<File>>(emptyList())
    val shiftLogs: StateFlow<List<File>> = _shiftLogs

    // ---- Selection + raw window data --------------------------------------

    private val _selectedLog = MutableStateFlow<File?>(null)
    val selectedLog: StateFlow<File?> = _selectedLog

    /** All windows read from the selected log, before NMS. */
    private val _allWindows = MutableStateFlow<List<LabeledWindow>>(emptyList())
    val allWindows: StateFlow<List<LabeledWindow>> = _allWindows

    // ---- NMS toggle -------------------------------------------------------

    private val _suppressionEnabled = MutableStateFlow(true)
    val suppressionEnabled: StateFlow<Boolean> = _suppressionEnabled

    /** Windows presented to the operator (NMS-filtered or raw, per toggle). */
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

    // ---- τ suggestion (derived from displayed + labeled windows) ----------

    val tauSuggestion: StateFlow<TauCalibrator.TauSuggestion?> =
        windows.map { TauCalibrator.suggest(it) }
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    // ---- Export state -----------------------------------------------------

    sealed class ExportState { object Idle : ExportState(); object Exporting : ExportState()
        data class Done(val file: File) : ExportState()
        data class Error(val msg: String) : ExportState()
    }

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState

    // ---- Initialization ---------------------------------------------------

    init {
        refreshShiftLogs()
    }

    fun refreshShiftLogs() {
        val filesDir = getApplication<Application>().filesDir
        _shiftLogs.value = CandidateWindowLog.listShiftLogs(filesDir)
    }

    // ---- Actions ----------------------------------------------------------

    fun selectLog(file: File) {
        _selectedLog.value = file
        viewModelScope.launch(Dispatchers.IO) {
            val windows = CandidateWindowLog(file).read().map { LabeledWindow(it) }
            _allWindows.value = windows
        }
    }

    fun clearSelection() {
        _selectedLog.value = null
        _allWindows.value = emptyList()
    }

    /** Updates the label for a window identified by its [detectedAtNanos] timestamp. */
    fun setLabel(detectedAtNanos: Long, label: Label) {
        _allWindows.value = _allWindows.value.map { lw ->
            if (lw.window.detectedAtNanos == detectedAtNanos) lw.copy(label = label) else lw
        }
    }

    fun toggleSuppression() {
        _suppressionEnabled.value = !_suppressionEnabled.value
    }

    /**
     * Writes a labels export file alongside the source log:
     * `<log-name>.labels.json` — list of `{detectedAtNanos, lambda, label}`.
     */
    fun exportLabels() {
        val log = _selectedLog.value ?: return
        val labeled = windows.value
        if (labeled.isEmpty()) return

        _exportState.value = ExportState.Exporting
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entries = labeled
                    .filter { it.label != Label.UNLABELED }
                    .map { LabelExportEntry(it.window.detectedAtNanos, it.window.lambda, it.label.name) }
                val json   = Json { prettyPrint = true }
                val out    = File(log.parent, log.nameWithoutExtension + ".labels.json")
                out.writeText(json.encodeToString(entries))
                withContext(Dispatchers.Main) { _exportState.value = ExportState.Done(out) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _exportState.value = ExportState.Error(e.message ?: "export failed") }
            }
        }
    }

    fun resetExportState() { _exportState.value = ExportState.Idle }
}
