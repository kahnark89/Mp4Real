package com.capsconc.arcshield.labeler

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One row in the `.labels.json` export file written by [LabelerViewModel.exportLabels]. */
@Serializable
data class LabelExportEntry(
    @SerialName("detected_at_nanos") val detectedAtNanos:  Long,
    @SerialName("lambda")            val lambda:            Float,
    @SerialName("label")             val label:             String,    // "TRUE_POSITIVE" | "FALSE_POSITIVE"
    @SerialName("provenance_class")  val provenanceClass:  String = "HUMAN",
    // CONFIRMED = all reviewers agree; INDETERMINATE = divergent / no R_phys (→ Q')
    @SerialName("status")            val status:           String = "CONFIRMED",
    @SerialName("elicited_action")   val elicitedAction:   ElicitedAction? = null,
)
