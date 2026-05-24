# ArcShield

**A domain-specific cyber-physical codec for industrial expertise.** ArcShield captures expert decision events from veteran manufacturing operators as multiplexed multi-track containers, encodes recurring causal patterns into a behavioral codebook, and grounds confidence updates against physical floor outcomes — not operator compliance.

This is the system documented in the [ArcShield Whitepaper v10.3](docs/whitepaper/) and the corresponding provisional patent.

---

## If you are picking up this repository cold

Read these three files in order. They are the complete operating context.

1. **[CLAUDE.md](CLAUDE.md)** — architecture, schema, invariants. The things that don't change.
2. **[phases/CURRENT_PHASE.md](phases/CURRENT_PHASE.md)** — live build state. The things that change every session.
3. **[ARCHITECTURE_PRINCIPLES.md](ARCHITECTURE_PRINCIPLES.md)** — the structural pattern beneath both. Read after the first two.

Reference documents are in [`docs/`](docs/). Don't read those until you've read the three above.

---

## What this system does in one paragraph

The edge device runs a log-likelihood ratio gate against shift-start baselines across acoustic, biometric, accelerometer, gaze, and POV-video channels. When the gate fires, a [t − 30s, t + 60s] window is muxed into an fMP4 container (the **mp4Real™** format) with timed-metadata tracks for each sensor channel. The container is matched against a behavioral codebook on the backend — known patterns are stored as primitive-reference tokens, novel patterns are stored uncompressed and queued for human-in-the-loop validation, which expands the codebook. Captured events are structured as CIAER+ records (Cause · Intuition · Action · Effect · Result, plus Pre-ENV layer and Shadow Actions). A Personal AI Twin trained on the resulting corpus can advise other operators; advisory confidence updates only when an outcome-grounded physical reward arrives via PLC telemetry. Withhold-sampling preserves counterfactual evidence to prevent the Twin from becoming self-confirming at scale.

The system is being built and validated at PPVC Line 1, Hollowell Industries (PVC extrusion).

---

## Status

| | |
|---|---|
| **Phase** | 1 — Container + LLR gate, shadow mode |
| **Build status** | See [phases/CURRENT_PHASE.md §0](phases/CURRENT_PHASE.md) |
| **Corpus depth** | See [phases/CURRENT_PHASE.md §0](phases/CURRENT_PHASE.md) |
| **Validation site** | PPVC Line 1, Hollowell Industries |
| **Provisional patent** | Filed (micro entity status) |
| **arXiv submission** | Whitepaper v10.x — gated on Paris Convention foreign-filing clock |

---

## Intellectual Property and Trademark Notice

mp4Real™, ArcShield™, CIAER™, and CIAER+™ are trademarks of Capps Consulting Company LLC. The multi-track cyber-physical capture architecture, the application of log-likelihood ratio (LLR) gating to multimodal industrial decision events, and the behavioral codebook discretization methods described in this repository are the proprietary intellectual property of Kahn Capps and Capps Consulting Company LLC. Unauthorized commercial use, reproduction, or implementation of the mp4Real™ container architecture or the CIAER™ and CIAER+™ schemas without explicit licensing is prohibited. All rights reserved.

---

*Architect: Kahn Capps · Capps Consulting Company LLC · Helena-West Helena, Arkansas*
