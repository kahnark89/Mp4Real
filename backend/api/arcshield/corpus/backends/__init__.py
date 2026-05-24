"""
arcshield.corpus.backends
==========================
Backend registry and factory.

To swap backends, change backend_type in config.toml.
No other changes required anywhere in the codebase.
"""

from __future__ import annotations

from arcshield.corpus.backend import CorpusBackend


def get_backend(backend_type: str, **kwargs) -> CorpusBackend:
    """
    Factory function. Returns the appropriate CorpusBackend implementation.

    Phase 1:  get_backend("json",   corpus_dir="./corpus", facility_id="HOLLOWELL_PPVC1")
    Phase 2:  get_backend("sqlite", db_path="./corpus.db", facility_id="HOLLOWELL_PPVC1")
    Phase 3:  get_backend("neo4j",  uri="bolt://...",       facility_id="HOLLOWELL_PPVC1")
    """
    if backend_type == "json":
        from arcshield.corpus.backends.json_backend import JsonCorpusBackend
        return JsonCorpusBackend(**kwargs)

    if backend_type == "sqlite":
        from arcshield.corpus.backends.sqlite_backend import SqliteCorpusBackend
        return SqliteCorpusBackend(**kwargs)

    if backend_type == "neo4j":
        from arcshield.corpus.backends.graph_backend import GraphCorpusBackend
        return GraphCorpusBackend(**kwargs)

    raise ValueError(
        f"Unknown backend_type: '{backend_type}'. "
        "Valid options: 'json', 'sqlite', 'neo4j'."
    )
