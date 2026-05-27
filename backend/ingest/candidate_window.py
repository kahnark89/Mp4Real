"""
ingest.candidate_window
=======================
CandidateWindowLog — one entry from the shadow-mode NDJSON log produced by
the Android LLR gate running in shadow mode (CLAUDE.md §4.3).

Each line in the NDJSON file records a candidate window that the gate
evaluated. thresholdReached=True means Λ ≥ τ was satisfied; the window
would have been captured in live mode.

In shadow mode, all candidate windows are logged regardless of threshold.
The hand-labeling tool (tools/shadow-mode-labeler) uses these to tune τ
toward ~80% TPR / ≤20% FPR before live capture begins.
"""

from __future__ import annotations

import json
from pathlib import Path

from pydantic import BaseModel, Field, ConfigDict


class CandidateWindowLog(BaseModel):
    """
    One candidate window entry from the shadow-mode NDJSON log.

    Field name 'lambda' is a Python keyword, so the model uses
    'lambda_' with an alias. Use model_config populate_by_name=True
    to allow construction by either name.
    """

    model_config = ConfigDict(populate_by_name=True)

    detectedAtNanos: int = Field(
        description="elapsedRealtimeNanos at window detection on the phone clock."
    )
    lambda_: float = Field(
        alias="lambda",
        description="Combined LLR score: Λ = Λ_env + Λ_bio.",
    )
    lambdaEnv: float = Field(
        description="Environmental component Λ_env (acoustic + accel + motion + gaze)."
    )
    lambdaAcoustic: float = Field(description="Acoustic spectral KL divergence contribution.")
    lambdaAccel: float = Field(description="Accelerometer RMS contribution.")
    lambdaMotion: float = Field(description="Frame-to-frame motion energy contribution.")
    lambdaGaze: float = Field(
        default=0.0,
        description="Gaze dwell duration contribution (0 when gaze not available).",
    )
    lambdaBio: float = Field(
        description="Biometric component Λ_bio (HR delta + HRV ratio + nonlinear HRV)."
    )
    activityGate: float = Field(
        description="Activity gate scalar in [0, 1]. High physical activity reduces Λ_bio."
    )
    thresholdReached: bool = Field(
        description="True when Λ ≥ τ. Would have triggered capture in live mode."
    )
    shadowMode: bool = Field(
        description="Always True in these logs — confirms this is a shadow-mode record."
    )


def parse_shadow_log(ndjson_path: Path) -> list[CandidateWindowLog]:
    """
    Parse a shadow-mode NDJSON log file into a list of CandidateWindowLog entries.

    Each line of the file must be a valid JSON object matching CandidateWindowLog.
    Empty lines are skipped.

    Raises FileNotFoundError if the file does not exist.
    Raises pydantic.ValidationError if any line fails validation.
    """
    ndjson_path = Path(ndjson_path)
    if not ndjson_path.exists():
        raise FileNotFoundError(f"Shadow log not found: {ndjson_path}")

    entries: list[CandidateWindowLog] = []
    with open(ndjson_path, "r", encoding="utf-8") as f:
        for lineno, line in enumerate(f, start=1):
            line = line.strip()
            if not line:
                continue
            raw = json.loads(line)
            entry = CandidateWindowLog.model_validate(raw)
            entries.append(entry)

    return entries


def find_shadow_log(session_id: str, shadow_dir: Path) -> Path | None:
    """
    Look for a shadow-mode NDJSON log for a given session_id in shadow_dir.

    Expects filename: {session_id}.ndjson
    Returns the Path if found, None otherwise.
    """
    shadow_dir = Path(shadow_dir)
    candidate = shadow_dir / f"{session_id}.ndjson"
    if candidate.exists():
        return candidate
    return None
