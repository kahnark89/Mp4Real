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

package com.capsconc.arcshield.debrief.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.capsconc.arcshield.debrief.model.PendingEventRecord
import com.capsconc.arcshield.debrief.model.ShadowActionRecord
import com.capsconc.arcshield.debrief.repository.DebriefRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// -------------------------------------------------------------------------
// State
// -------------------------------------------------------------------------

/**
 * All possible states for the HITL debrief UI.
 *
 * The UI is state-machine driven: composables receive a [DebriefState] and render
 * accordingly without holding their own mutable state (except local form fields).
 */
sealed class DebriefState {
    /** Async load in progress — show spinner. */
    object Loading : DebriefState()

    /** Main queue screen: list of events awaiting annotation. */
    data class Queue(val events: List<PendingEventRecord>) : DebriefState()

    /** Annotation form open for a specific record. */
    data class Editing(val record: PendingEventRecord) : DebriefState()

    /** Submission in flight — disable form, show progress. */
    data class Submitting(val record: PendingEventRecord) : DebriefState()

    /** Terminal error state — surface message and allow retry. */
    data class Error(val message: String) : DebriefState()
}

// -------------------------------------------------------------------------
// ViewModel
// -------------------------------------------------------------------------

/**
 * ViewModel for the Phase 2 HITL debrief workflow.
 *
 * No Hilt annotations — this module is intentionally decoupled from the app's DI graph.
 * The [Factory] wires the [DebriefRepository] dependency at construction time.
 */
class DebriefViewModel(
    private val repository: DebriefRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<DebriefState>(DebriefState.Loading)
    val state: StateFlow<DebriefState> = _state.asStateFlow()

    init {
        loadQueue()
    }

    // -------------------------------------------------------------------------
    // Queue management
    // -------------------------------------------------------------------------

    /** Loads all pending records from disk and transitions to [DebriefState.Queue]. */
    fun loadQueue() {
        viewModelScope.launch {
            _state.value = DebriefState.Loading
            try {
                val events = repository.loadPendingEvents()
                _state.value = DebriefState.Queue(events)
            } catch (e: Exception) {
                _state.value = DebriefState.Error("Failed to load queue: ${e.message}")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Navigation between states
    // -------------------------------------------------------------------------

    /** Opens the annotation form for [record]. */
    fun startEditing(record: PendingEventRecord) {
        _state.value = DebriefState.Editing(record)
    }

    /**
     * Returns to the queue screen. Does NOT discard any in-progress changes — the caller
     * should call [updateRecord] before [cancelEditing] if changes should be persisted.
     */
    fun cancelEditing() {
        loadQueue()
    }

    // -------------------------------------------------------------------------
    // Record mutations
    // -------------------------------------------------------------------------

    /**
     * Saves in-progress changes to disk and updates the [DebriefState.Editing] state
     * so the form reflects the latest field values without a full queue reload.
     */
    fun updateRecord(record: PendingEventRecord) {
        viewModelScope.launch {
            try {
                repository.saveProgress(record)
                // Update the editing state in-place so the form reflects persisted values.
                if (_state.value is DebriefState.Editing) {
                    _state.value = DebriefState.Editing(record)
                }
            } catch (e: Exception) {
                _state.value = DebriefState.Error("Failed to save progress: ${e.message}")
            }
        }
    }

    /**
     * Submits [record] to the backend corpus.
     *
     * Transitions through [DebriefState.Submitting] → [DebriefState.Queue] on success,
     * or [DebriefState.Error] on failure (leaves the file on disk for retry).
     */
    fun submitEvent(record: PendingEventRecord) {
        viewModelScope.launch {
            _state.value = DebriefState.Submitting(record)
            val result = repository.submitEvent(record)
            result.fold(
                onSuccess = {
                    // Delete the local pending file after confirmed backend acceptance.
                    repository.deleteRecord(record.eventId)
                    loadQueue()
                },
                onFailure = { e ->
                    _state.value = DebriefState.Error("Submit failed: ${e.message}")
                }
            )
        }
    }

    // -------------------------------------------------------------------------
    // Record deletion
    // -------------------------------------------------------------------------

    /**
     * Deletes a pending record from disk and reloads the queue.
     * Called when the operator explicitly discards an event (not a CIAER+ outcome).
     */
    fun deleteRecord(eventId: String) {
        viewModelScope.launch {
            try {
                repository.deleteRecord(eventId)
                loadQueue()
            } catch (e: Exception) {
                _state.value = DebriefState.Error("Failed to delete record: ${e.message}")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Import from shadow log
    // -------------------------------------------------------------------------

    /**
     * Imports all [com.capsconc.arcshield.llr.CandidateWindow] records from the shadow-mode
     * NDJSON log identified by [sessionId] (the date string, e.g. "2026-04-08").
     * Newly created records are added to the queue.
     */
    fun importFromSession(sessionId: String) {
        viewModelScope.launch {
            _state.value = DebriefState.Loading
            try {
                repository.importFromShadowLog(sessionId)
                loadQueue()
            } catch (e: Exception) {
                _state.value = DebriefState.Error("Import failed: ${e.message}")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Factory (no Hilt coupling)
    // -------------------------------------------------------------------------

    class Factory(private val repo: DebriefRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return DebriefViewModel(repo) as T
        }
    }
}
