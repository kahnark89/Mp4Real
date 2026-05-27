"""
codebook.ontology
=================
Controlled vocabulary for failure_mode_tag.

Each tag is a snake_case string. New tags MUST be explicitly registered via
PrimitiveRegistry.register_ontology_tag() — no LLM guesses, no free-text
to tag coercion (CLAUDE.md §12 constraint 4).

The Hollowell Line 1 seed set covers common PVC extrusion failure modes
observed at PPVC Line 1. This set grows only through explicit human-in-the-loop
registration backed by domain-ontology review.
"""

from __future__ import annotations

# ---------------------------------------------------------------------------
# Seed vocabulary — Hollowell PPVC Line 1, PVC extrusion
# ---------------------------------------------------------------------------

_HOLLOWELL_PPVC1_TAGS: frozenset[str] = frozenset({
    # Hopper / feed system
    "material_segregation_funnel_flow",   # fines segregate in hopper, starving extruder
    # Die / head
    "die_drool",                          # melt buildup at die face, surface contamination
    # Thermal
    "melt_temp_high",                     # barrel zone temp exceeds spec upper limit
    "melt_temp_low",                      # barrel zone temp below minimum processing temp
    # Drive system
    "motor_amp_spike",                    # main drive motor current surge, screw obstruction
    "screw_speed_surge",                  # unexpected RPM increase, slip/feed irregularity
    # Dimensional / haul-off
    "line_speed_instability",             # haul-off speed variation causing dimensional defect
    # Cooling
    "cooling_insufficiency",              # cooling tank temp/flow insufficient for throughput
    # Head / barrel
    "pressure_spike_head",                # head pressure spike: partial blockage or viscosity shift
    # Output
    "output_rate_drop",                   # extruder output kg/h below target, multi-cause diagnostic
})

# The mutable registry-extended set lives in PrimitiveRegistry; this module
# provides the immutable seed and the validation helper.
_REGISTERED_TAGS: set[str] = set(_HOLLOWELL_PPVC1_TAGS)


def all_tags() -> frozenset[str]:
    """Return the current full set of registered tags (seed + any runtime additions)."""
    return frozenset(_REGISTERED_TAGS)


def is_valid_tag(tag: str) -> bool:
    """Return True if *tag* is in the registered ontology."""
    return tag in _REGISTERED_TAGS


def validate_tag(tag: str) -> None:
    """Raise ValueError if *tag* is not in the registered ontology."""
    if not is_valid_tag(tag):
        raise ValueError(
            f"failure_mode_tag '{tag}' is not in the registered ontology. "
            f"Valid tags: {sorted(_REGISTERED_TAGS)}. "
            "New tags require explicit registration — see ontology.register_tag()."
        )


def register_tag(tag: str) -> None:
    """
    Register a new ontology tag at runtime.

    This is the ONLY permitted path to extend the vocabulary.
    Called by PrimitiveRegistry.expand() after domain-review validation.
    """
    if not tag or not tag.replace("_", "").isalnum():
        raise ValueError(
            f"tag '{tag}' must be non-empty and contain only alphanumeric characters and underscores."
        )
    if not tag.islower():
        raise ValueError(f"tag '{tag}' must be snake_case (all lowercase).")
    _REGISTERED_TAGS.add(tag)
