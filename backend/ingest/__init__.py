"""
ingest
======
mp4Real container sidecar parser and CIAER+ event skeleton builder.

Parses .mp4real.json sidecar files written by the Android capture app,
reads shadow-mode NDJSON candidate window logs, and constructs partial
CIAER+ event skeletons for HITL completion in debrief-ui.

The ingest pipeline does NOT write events to the corpus. It produces an
EventSkeleton that goes to debrief-ui for human-in-the-loop completion.
The completed skeleton is then submitted via IngestHandler.submit_completed_skeleton().
"""

from __future__ import annotations

from .sidecar import SessionSidecar, parse_sidecar, find_sidecar
from .event_builder import build_event_skeleton, skeleton_to_partial_event, EventSkeleton
from .upload_handler import IngestHandler

__all__ = [
    "SessionSidecar",
    "parse_sidecar",
    "find_sidecar",
    "build_event_skeleton",
    "skeleton_to_partial_event",
    "EventSkeleton",
    "IngestHandler",
]
