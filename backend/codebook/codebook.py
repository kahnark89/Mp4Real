"""
codebook.codebook
=================
PrimitiveCodebook: load, save, and expand the Phase 1 hand-curated primitive dict.

Storage: single JSON file (primitives.json) in the codebook package directory.
Format: {"codebook_version":"1.0","theta_tc_default":0.70,"primitives":[...]}

Expansion path (FIG. 8 step 808):
  1. Matcher returns is_novel=True for an event.
  2. Human validates the event in debrief-ui (HITL).
  3. Caller submits CodebookExpansionRequest(source_event_id, confirmed_tag, ...).
  4. add_from_corpus_event() creates a new Primitive and inserts it.
  5. save() persists the updated dict.

Thread safety: not thread-safe for concurrent writes.  The MCP server
serializes all writes via the FastMCP asyncio dispatch loop.
"""

from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from codebook.schema import (
    CanonicalSensorReading,
    CanonicalShadowAction,
    CodebookExpansionRequest,
    Primitive,
)

_DEFAULT_PRIMITIVES_PATH = Path(__file__).parent / "primitives.json"


class PrimitiveCodebook:
    """
    Manages the Phase 1 hand-curated primitive dict.

    Loading:
        book = PrimitiveCodebook.load()      # default primitives.json
        book = PrimitiveCodebook.load(path)  # custom path

    Querying:
        book.get_primitive("PRIM-001")
        book.get_by_failure_mode("material_segregation_funnel_flow")
        book.list_all()
        book.list_failure_modes()

    Expanding (HITL path — FIG. 8 step 808):
        pid = book.add_from_corpus_event(event_dict, req)
        book.save()
    """

    def __init__(
        self,
        primitives: list[Primitive],
        theta_tc_default: float = 0.70,
        codebook_version: str = "1.0",
        path: Path | None = None,
    ) -> None:
        self._primitives: dict[str, Primitive] = {p.primitive_id: p for p in primitives}
        self.theta_tc_default = theta_tc_default
        self.codebook_version = codebook_version
        self._path = path or _DEFAULT_PRIMITIVES_PATH

    # ------------------------------------------------------------------
    # Factory
    # ------------------------------------------------------------------

    @classmethod
    def load(cls, path: Path | None = None) -> "PrimitiveCodebook":
        """
        Load the codebook from a JSON file.

        Returns an empty codebook (no error) if the file does not exist.
        This lets the server start cleanly before any primitives are defined.
        """
        fpath = path or _DEFAULT_PRIMITIVES_PATH
        if not fpath.exists():
            return cls(primitives=[], path=fpath)

        raw = json.loads(fpath.read_text(encoding="utf-8"))
        primitives = [Primitive(**p) for p in raw.get("primitives", [])]
        return cls(
            primitives=primitives,
            theta_tc_default=raw.get("theta_tc_default", 0.70),
            codebook_version=raw.get("codebook_version", "1.0"),
            path=fpath,
        )

    # ------------------------------------------------------------------
    # Persistence
    # ------------------------------------------------------------------

    def save(self, path: Path | None = None) -> None:
        """Write the codebook to JSON, creating parent directories if needed."""
        fpath = path or self._path
        fpath.parent.mkdir(parents=True, exist_ok=True)
        data = {
            "codebook_version": self.codebook_version,
            "theta_tc_default": self.theta_tc_default,
            "primitives": [p.model_dump(mode="json") for p in self._primitives.values()],
        }
        fpath.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")

    # ------------------------------------------------------------------
    # Querying
    # ------------------------------------------------------------------

    def __len__(self) -> int:
        return len(self._primitives)

    def get_primitive(self, primitive_id: str) -> Primitive | None:
        return self._primitives.get(primitive_id)

    def get_by_failure_mode(self, failure_mode_tag: str) -> list[Primitive]:
        return [p for p in self._primitives.values() if p.failure_mode_tag == failure_mode_tag]

    def list_all(self) -> list[Primitive]:
        return list(self._primitives.values())

    def list_failure_modes(self) -> list[str]:
        """Sorted unique failure_mode_tags represented in this codebook."""
        return sorted({p.failure_mode_tag for p in self._primitives.values()})

    # ------------------------------------------------------------------
    # Expansion (HITL path — FIG. 8 step 808)
    # ------------------------------------------------------------------

    def _next_primitive_id(self) -> str:
        nums = [
            int(pid.split("-")[1])
            for pid in self._primitives
            if pid.startswith("PRIM-") and pid.split("-")[1].isdigit()
        ]
        return f"PRIM-{max(nums, default=0) + 1:03d}"

    def add_from_corpus_event(
        self,
        event_dict: dict[str, Any],
        req: CodebookExpansionRequest,
    ) -> str:
        """
        Create a new Primitive from a validated CIAER+ corpus event.

        event_dict is the raw JSON dict from the corpus backend (same structure
        as the seed event JSON).  This is the HITL expansion path from FIG. 8.

        Returns the new primitive_id.
        Raises ValueError if source_event_id is already in an existing primitive.
        """
        for p in self._primitives.values():
            if req.source_event_id in p.source_event_ids:
                raise ValueError(
                    f"Event {req.source_event_id} already contributed to "
                    f"primitive {p.primitive_id}."
                )

        cause = event_dict.get("cause", {})
        action = event_dict.get("action", {})
        shadow_actions = event_dict.get("shadow_actions", [])

        canonical_readings = [
            CanonicalSensorReading(
                instrument_id=r["instrument_id"],
                value=r["value"],
                unit=r["unit"],
                confidence=r.get("confidence", 1.0),
            )
            for r in cause.get("sensor_readings", [])
        ]
        canonical_shadows = [
            CanonicalShadowAction(
                action_type=sa["action_type"],
                rejection_rationale=sa["rejection_rationale"],
            )
            for sa in shadow_actions
        ]

        theta = (
            req.theta_tc_override
            if req.theta_tc_override is not None
            else self.theta_tc_default
        )
        pid = self._next_primitive_id()

        primitive = Primitive(
            primitive_id=pid,
            failure_mode_tag=req.confirmed_failure_mode_tag,
            description=req.description,
            srk_level=event_dict.get("intuition", {}).get("srk_level", "KNOWLEDGE"),
            canonical_sensor_readings=canonical_readings,
            canonical_acoustic_profile=cause.get("acoustic_profile"),
            canonical_escalation_state=cause.get("escalation_state", 0),
            theta_tc=theta,
            canonical_action_type=action.get("action_type", "MONITOR_HOLD"),
            canonical_action_summary=action.get("action_rationale", ""),
            canonical_shadow_actions=canonical_shadows,
            created_at=datetime.now(tz=timezone.utc),
            created_by=req.requested_by,
            source_event_ids=[req.source_event_id],
            domain_context=event_dict.get("domain_context", {}),
        )

        self._primitives[pid] = primitive
        return pid
