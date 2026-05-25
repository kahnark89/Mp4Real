package com.capsconc.arcshield.labeler

import com.capsconc.arcshield.llr.CandidateWindow

data class LabeledWindow(
    val window: CandidateWindow,
    val label:  Label = Label.UNLABELED,
)
