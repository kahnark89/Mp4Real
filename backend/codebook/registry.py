"""
codebook.registry
=================
PrimitiveRegistry — loads, stores, and provides lookup/expansion operations
for the Phase 1 hand-curated behavioral primitive map.

The registry is the single authoritative source for:
  - Which primitives exist
  - Which failure_mode_tags are covered
  - Codebook expansion (validate ontology membership before registering)

Per CLAUDE.md §5.1: Phase 1 is a dict keyed by failure_mode_tag. Debuggable,
every entry inspectable. Phase 3+ replaces with RVQ (see CLAUDE.md §5.2).
"""

from __future__ import annotations

from collections import defaultdict
from pathlib import Path
from typing import Any

import yaml

from .ontology import register_tag, validate_tag
from .primitive import BehavioralPrimitive


class PrimitiveRegistry:
    """
    Registry of behavioral primitives for the Phase 1 codebook.

    Usage:
        registry = PrimitiveRegistry.load(yaml_path)
        primitive = registry.get("material_segregation_v1")
        matches = registry.get_by_failure_mode("motor_amp_spike")
    """

    def __init__(self) -> None:
        # primitive_id → BehavioralPrimitive
        self._by_id: dict[str, BehavioralPrimitive] = {}
        # failure_mode_tag → [BehavioralPrimitive, ...]
        self._by_tag: dict[str, list[BehavioralPrimitive]] = defaultdict(list)

    # ------------------------------------------------------------------
    # Construction
    # ------------------------------------------------------------------

    @classmethod
    def load(cls, yaml_path: str | Path) -> "PrimitiveRegistry":
        """
        Load primitives from a YAML file.

        The YAML file must contain a top-level 'primitives' list.  Each entry
        is validated against BehavioralPrimitive (including ontology membership).
        """
        path = Path(yaml_path)
        if not path.exists():
            raise FileNotFoundError(f"Codebook YAML not found: {path}")

        with open(path, "r", encoding="utf-8") as f:
            raw: Any = yaml.safe_load(f)

        if not isinstance(raw, dict) or "primitives" not in raw:
            raise ValueError(
                f"YAML file must have a top-level 'primitives' key. Got: {list(raw.keys()) if isinstance(raw, dict) else type(raw)}"
            )

        primitives_raw: list[dict] = raw["primitives"]
        if not isinstance(primitives_raw, list):
            raise ValueError("'primitives' key must contain a list.")

        registry = cls()
        for entry in primitives_raw:
            primitive = BehavioralPrimitive.model_validate(entry)
            registry._register(primitive)

        return registry

    # ------------------------------------------------------------------
    # Lookup
    # ------------------------------------------------------------------

    def get(self, primitive_id: str) -> BehavioralPrimitive:
        """Return a primitive by its unique ID.  Raises KeyError if not found."""
        try:
            return self._by_id[primitive_id]
        except KeyError:
            raise KeyError(
                f"No primitive with id '{primitive_id}'. "
                f"Known ids: {sorted(self._by_id.keys())}"
            )

    def get_by_failure_mode(self, failure_mode_tag: str) -> list[BehavioralPrimitive]:
        """
        Return all primitives registered for a failure_mode_tag.

        Returns an empty list (not an error) when the tag is valid but has no
        primitives yet.  Raises ValueError if the tag is not in the ontology.
        """
        validate_tag(failure_mode_tag)
        return list(self._by_tag.get(failure_mode_tag, []))

    def list_all(self) -> list[BehavioralPrimitive]:
        """Return all primitives sorted by failure_mode_tag then primitive_id."""
        return sorted(self._by_id.values(), key=lambda p: (p.failure_mode_tag, p.primitive_id))

    # ------------------------------------------------------------------
    # Mutation / expansion
    # ------------------------------------------------------------------

    def expand(self, primitive: BehavioralPrimitive) -> None:
        """
        Register a new primitive into the codebook.

        Validates that failure_mode_tag is in the registered ontology.
        Raises ValueError if the primitive_id already exists.
        Raises ValueError (from ontology) if failure_mode_tag is not registered.
        """
        validate_tag(primitive.failure_mode_tag)
        if primitive.primitive_id in self._by_id:
            raise ValueError(
                f"primitive_id '{primitive.primitive_id}' already exists in the registry. "
                "Use a versioned id (e.g. 'motor_amp_spike_v2') for updates."
            )
        self._register(primitive)

    def register_ontology_tag(self, tag: str) -> None:
        """
        Extend the ontology with a new tag.

        This is the only permitted path to add vocabulary (CLAUDE.md §12 constraint 4).
        The tag is registered in the module-level ontology, making it available
        globally (across all registry instances in the process).
        """
        register_tag(tag)

    # ------------------------------------------------------------------
    # Export
    # ------------------------------------------------------------------

    def to_dict(self) -> dict[str, list[BehavioralPrimitive]]:
        """Return {failure_mode_tag: [BehavioralPrimitive, ...]} dict."""
        return {tag: list(primitives) for tag, primitives in self._by_tag.items()}

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------

    def _register(self, primitive: BehavioralPrimitive) -> None:
        self._by_id[primitive.primitive_id] = primitive
        self._by_tag[primitive.failure_mode_tag].append(primitive)

    def __len__(self) -> int:
        return len(self._by_id)

    def __repr__(self) -> str:
        return f"PrimitiveRegistry(primitives={len(self)}, tags={len(self._by_tag)})"
