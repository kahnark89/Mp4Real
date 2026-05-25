package com.capsconc.arcshield.labeler

import com.capsconc.arcshield.llr.CandidateWindow

/** Minimal factory for test CandidateWindows. All unused fields default to 0. */
fun makeCandidateWindow(
    lambda:          Float = 0f,
    nanos:           Long  = 0L,
    lambdaEnv:       Float = 0f,
    lambdaAcoustic:  Float = 0f,
    lambdaAccel:     Float = 0f,
    lambdaMotion:    Float = 0f,
    lambdaGaze:      Float = 0f,
    lambdaBio:       Float = 0f,
    activityGate:    Float = 1f,
    thresholdReached: Boolean = false,
    shadowMode:      Boolean = true,
) = CandidateWindow(
    detectedAtNanos  = nanos,
    lambda           = lambda,
    lambdaEnv        = lambdaEnv,
    lambdaAcoustic   = lambdaAcoustic,
    lambdaAccel      = lambdaAccel,
    lambdaMotion     = lambdaMotion,
    lambdaGaze       = lambdaGaze,
    lambdaBio        = lambdaBio,
    activityGate     = activityGate,
    thresholdReached = thresholdReached,
    shadowMode       = shadowMode,
)
