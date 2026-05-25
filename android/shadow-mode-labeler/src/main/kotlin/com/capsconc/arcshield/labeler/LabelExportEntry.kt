package com.capsconc.arcshield.labeler

import kotlinx.serialization.Serializable

/** One row in the `.labels.json` export file written by [LabelerViewModel.exportLabels]. */
@Serializable
data class LabelExportEntry(
    val detectedAtNanos: Long,
    val lambda:          Float,
    val label:           String,   // "TRUE_POSITIVE" | "FALSE_POSITIVE"
)
