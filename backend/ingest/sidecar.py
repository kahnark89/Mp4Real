"""
ingest.sidecar
==============
SessionSidecar — Pydantic model for the .mp4real.json sidecar written
by the Android capture app alongside each mp4Real container.

The sidecar carries session identity, sync metadata (ε_sync), and the
track map needed by the backend demuxer. It is the handoff point between
the edge (phone + Polar) and the backend ingest pipeline.

Per CLAUDE.md §3.2: ε_sync is the cross-track synchronization tolerance.
Target ≤ 100 ms (100_000_000 ns). Anything above 250 ms (250_000_000 ns)
flags the session with low_sync_confidence.
"""

from __future__ import annotations

import json
from pathlib import Path

from pydantic import BaseModel, Field


class SessionSidecar(BaseModel):
    """
    Parsed .mp4real.json sidecar written by the Android mp4Real capture app.

    All fields are camelCase to match the Android/JSON convention.
    """

    sessionId: str = Field(description="UUID v4 session identifier.")
    operatorId: str = Field(description="Anonymized operator hash. Never PII.")
    facilityId: str = Field(description="Facility identifier, e.g. 'HOLLOWELL_PPVC1'.")
    lineId: str = Field(description="Production line identifier, e.g. 'PPVC_LINE_1'.")
    captureSourceId: str = Field(
        description="CaptureSource implementation, e.g. 'phone_cameraX_v1'."
    )
    biometricSourceId: str = Field(
        description="BiometricSource implementation, e.g. 'polar_h10_v1'."
    )
    sessionStartNanos: int = Field(
        description="Session start time as elapsedRealtimeNanos (monotonic phone clock)."
    )
    epsSyncNanos: int = Field(
        default=0,
        description="Observed ε_sync across tracks in nanoseconds. "
                    "Target ≤ 100_000_000 ns. Above 250_000_000 ns = low_sync_confidence.",
    )
    lowSyncConfidence: bool = Field(
        default=False,
        description="True when ε_sync > 250 ms at any point in this session.",
    )
    trackMap: dict[str, str] = Field(
        default_factory=dict,
        description="Maps track name to fMP4 track index: {'video':'0','audio':'1',...}",
    )

    @property
    def eps_sync_ms(self) -> float:
        """ε_sync in milliseconds for human-readable display."""
        return self.epsSyncNanos / 1_000_000.0

    @property
    def is_sync_valid(self) -> bool:
        """True when ε_sync is within the 100 ms target and lowSyncConfidence is False."""
        return self.epsSyncNanos <= 100_000_000 and not self.lowSyncConfidence


def parse_sidecar(path: Path) -> SessionSidecar:
    """
    Read and validate a .mp4real.json sidecar file.

    Raises FileNotFoundError if the file does not exist.
    Raises pydantic.ValidationError if the content is malformed.
    """
    path = Path(path)
    if not path.exists():
        raise FileNotFoundError(f"Sidecar file not found: {path}")
    with open(path, "r", encoding="utf-8") as f:
        raw = json.load(f)
    return SessionSidecar.model_validate(raw)


def find_sidecar(container_path: Path) -> Path | None:
    """
    Look for a .mp4real.json sidecar alongside the given container file.

    Looks for: {container_stem}.mp4real.json in the same directory.
    Returns the Path if found, None otherwise.
    """
    container_path = Path(container_path)
    stem = container_path.stem
    # Strip known video extensions from stem to handle names like "session.mp4"
    candidate = container_path.parent / f"{stem}.mp4real.json"
    if candidate.exists():
        return candidate
    return None
