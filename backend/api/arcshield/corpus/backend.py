"""
arcshield.corpus.backend
========================
Abstract base class for all ArcShield corpus storage backends.

Design contract
---------------
Every backend — Phase 1 flat JSON, Phase 2 SQLite, Phase 3 Neo4j — implements
this interface exactly. MCP tool handlers depend ONLY on this interface. Swapping
a backend is a one-line config change with zero tool-layer changes.

Method contracts
----------------
- All methods are async. Phase 1 flat-file I/O is fast enough that wrapping sync
  calls in asyncio.to_thread() is sufficient; Phase 2+ will be natively async.
- Methods that return lists always return [] rather than raising on empty results.
- Methods that return a single object raise EventNotFoundError when missing.
- graph_weight updates are atomic: the backend guarantees that a partial write
  cannot corrupt an existing record.
- All backends enforce the facility_id privacy boundary: a backend instance is
  scoped to ONE facility. Cross-facility queries are not supported at this layer.

Extension points
----------------
Backends MAY implement optional methods beyond this interface (e.g. full-text
search, graph traversal). MCP tools that need those capabilities should import
the concrete backend type and check isinstance() rather than breaking the
abstraction for all callers.
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass
from uuid import UUID

from arcshield.schema import (
    CIAEREvent,
    CauseSignatureQuery,
    FailureModeQuery,
    FailureModeSummary,
)


# ---------------------------------------------------------------------------
# Exceptions
# ---------------------------------------------------------------------------

class EventNotFoundError(Exception):
    """Raised when a requested event_id does not exist in the corpus."""
    def __init__(self, event_id: UUID) -> None:
        super().__init__(f"Event not found: {event_id}")
        self.event_id = event_id


class SchemaValidationError(Exception):
    """Raised when an incoming event fails CIAER schema validation."""


class BackendUnavailableError(Exception):
    """Raised when the storage backend cannot be reached (file lock, DB down, etc.)."""


# ---------------------------------------------------------------------------
# Weight update record
# ---------------------------------------------------------------------------

@dataclass(frozen=True)
class WeightUpdate:
    """
    Immutable record of a graph_weight change.
    Passed to update_graph_weight; stored as an audit log entry.
    """
    event_id   : UUID
    new_weight : float          # 0.0 – 1.0
    rationale  : str            # plain-text explanation (required, not optional)
    updated_by : str            # operator_id hash or "SYSTEM"


# ---------------------------------------------------------------------------
# Abstract base
# ---------------------------------------------------------------------------

class CorpusBackend(ABC):
    """
    Abstract interface for ArcShield corpus storage.

    Concrete implementations:
        JsonCorpusBackend   — flat JSON files, Phase 1 (< ~500 events)
        SqliteCorpusBackend — SQLite with indexes, Phase 2 (~500–5000 events)
        GraphCorpusBackend  — Neo4j / property graph, Phase 3 (multi-facility)
    """

    # ------------------------------------------------------------------
    # Lifecycle
    # ------------------------------------------------------------------

    @abstractmethod
    async def open(self) -> None:
        """
        Initialize the backend (open DB connections, scan corpus directory, etc.).
        Must be called before any other method.
        Idempotent: calling open() on an already-open backend is a no-op.
        """

    @abstractmethod
    async def close(self) -> None:
        """
        Flush writes and release all resources.
        After close(), no other methods may be called until open() is called again.
        """

    async def __aenter__(self) -> "CorpusBackend":
        await self.open()
        return self

    async def __aexit__(self, *_: object) -> None:
        await self.close()

    # ------------------------------------------------------------------
    # Identity
    # ------------------------------------------------------------------

    @property
    @abstractmethod
    def facility_id(self) -> str:
        """
        The facility this backend is scoped to.
        Privacy boundary: all read results are filtered to this facility_id.
        Write attempts carrying a different facility_id raise ValueError.
        """

    @property
    @abstractmethod
    def corpus_depth(self) -> int:
        """
        Total number of validated CIAER events stored.
        Backends must maintain this count in O(1) — no full scan on every call.
        """

    # ------------------------------------------------------------------
    # Write operations
    # ------------------------------------------------------------------

    @abstractmethod
    async def ingest_event(self, event: CIAEREvent) -> UUID:
        """
        Validate and store a new CIAER+ event record.

        Validation steps (backend must enforce all):
          1. event.facility_id matches self.facility_id
          2. event.event_id is globally unique in this corpus
          3. All REQUIRED schema fields are present (Pydantic already enforces this
             at the model level, but backends should re-check after deserialization)
          4. timestamp_start < timestamp_end when both are present
          5. escalation_delta == cause.escalation_state - result.escalation_state_at_result

        Returns the stored event_id on success.
        Raises SchemaValidationError with a descriptive message on any failure.
        Raises BackendUnavailableError if the write cannot be completed.

        Atomicity: either the full record is written or nothing is written.
        """

    @abstractmethod
    async def update_graph_weight(self, update: WeightUpdate) -> CIAEREvent:
        """
        Update the graph_weight field of an existing event.

        The WeightUpdate.rationale is appended to an audit log associated with
        the event — it is never discarded. This log is the counterfactual record
        that addresses open gap A13 (preventing graph_weight from becoming
        reflexively self-confirming at scale).

        Returns the updated event record.
        Raises EventNotFoundError if the event_id does not exist.
        Raises ValueError if new_weight is outside [0.0, 1.0].
        """

    # ------------------------------------------------------------------
    # Read operations — single event
    # ------------------------------------------------------------------

    @abstractmethod
    async def get_event(self, event_id: UUID) -> CIAEREvent:
        """
        Retrieve the full CIAER+ record for a single event.
        Raises EventNotFoundError if not found.
        """

    @abstractmethod
    async def get_shadow_actions(self, event_id: UUID) -> list[dict]:
        """
        Return only the shadow_actions array for a given event.

        Returns a list of dicts (not ShadowAction models) so that callers
        do not need to import schema types. Each dict contains:
          { action_type, rejection_rationale, confidence_in_rejection }

        Returns [] if the event exists but has no shadow actions.
        Raises EventNotFoundError if the event does not exist.
        """

    # ------------------------------------------------------------------
    # Read operations — queries
    # ------------------------------------------------------------------

    @abstractmethod
    async def query_by_failure_mode(
        self, query: FailureModeQuery
    ) -> list[CIAEREvent]:
        """
        Return the top-N events matching failure_mode_tag,
        sorted descending by graph_weight.

        Respects query.min_graph_weight as a lower bound filter.
        Respects query.srk_filter when set (returns only events at that SRK level).
        Returns [] when no matching events exist.
        """

    @abstractmethod
    async def query_by_cause_signature(
        self, query: CauseSignatureQuery
    ) -> list[CIAEREvent]:
        """
        Return the top-N events whose Cause phase most closely matches
        the provided sensor signature and escalation_state.

        Similarity metric is backend-defined but must consider:
          - escalation_state equality (exact match preferred, ±1 acceptable)
          - sensor_readings instrument overlap and value proximity
          - graph_weight as a tiebreaker (higher weight wins ties)

        Phase 1 (JsonCorpusBackend): cosine similarity on shared instrument_ids.
        Phase 2 (SqliteCorpusBackend): same, with indexed escalation_state pre-filter.
        Phase 3 (GraphCorpusBackend): embedding-based ANN search.

        The similarity algorithm is an implementation detail — callers receive
        ranked results regardless. The interface contract is ranking quality, not
        algorithm identity.

        Returns [] when no events exist with any matching instruments.
        """

    @abstractmethod
    async def list_failure_modes(self) -> list[FailureModeSummary]:
        """
        Return a summary of every failure_mode_tag in the corpus.

        Each FailureModeSummary contains:
          - failure_mode_tag: str
          - event_count: int
          - mean_graph_weight: float
          - outcome_distribution: dict[OutcomeTag, int]

        Sorted descending by event_count.
        Returns [] on an empty corpus.

        Backends should cache this summary and invalidate on ingest_event()
        to avoid a full corpus scan on every call.
        """

    # ------------------------------------------------------------------
    # Optional: graph traversal (Phase 3+)
    # ------------------------------------------------------------------

    async def get_divergent_chains(
        self,
        failure_mode_tag: str,
        min_operators: int = 2,
    ) -> list[list[CIAEREvent]]:
        """
        Return groups of events where multiple operators produced different
        Intuition-to-Action paths from the same Cause signature.

        Default implementation raises NotImplementedError.
        Phase 3 (GraphCorpusBackend) overrides this with native graph traversal.
        Phase 1 and 2 may implement a naive version by grouping on failure_mode_tag
        and operator_id, but this is not required.

        The divergence point — the specific environmental conditions under which
        expert models diverge — is described in the whitepaper as among the
        highest information-density nodes in the knowledge graph.
        """
        raise NotImplementedError(
            f"{type(self).__name__} does not implement get_divergent_chains. "
            "Upgrade to GraphCorpusBackend for graph traversal."
        )

    async def get_child_events(self, parent_event_id: UUID) -> list[CIAEREvent]:
        """
        Return all micro-CIAER cycles nested under a parent event's Action phase.
        Default: filter corpus on parent_event_id field.
        Phase 3: native graph traversal.
        """
        raise NotImplementedError(
            f"{type(self).__name__} does not implement get_child_events."
        )

    # ------------------------------------------------------------------
    # Diagnostics
    # ------------------------------------------------------------------

    @abstractmethod
    async def health_check(self) -> dict:
        """
        Return a dict describing backend health. Always safe to call.
        Minimum required keys:
          {
            "status":        "ok" | "degraded" | "unavailable",
            "backend_type":  str,          # e.g. "json", "sqlite", "neo4j"
            "facility_id":   str,
            "corpus_depth":  int,
            "storage_path":  str | None,   # None for remote backends
          }
        """
