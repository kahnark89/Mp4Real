# CURRENT_PHASE.md

**This file is the live state of the ArcShield build. It changes often. CLAUDE.md does not.**

The handoff document (`/CLAUDE.md`) describes architecture, schema, and invariants that should never drift. This file describes where the build currently is, what's actively in flight, what's blocking, and what's next. Update it at the end of every working session.

> **Re-entry rule:** Read CLAUDE.md first for architecture. Read this file second for state. Never invert that order.

---

## 0. At a Glance

| | |
|---|---|
| **Current Phase** | _Phase 1 — Container + LLR gate, shadow mode_ |
| **Phase start date** | _YYYY-MM-DD_ |
| **Target completion** | _YYYY-MM-DD_ |
| **Calendar week of phase** | _Week 1 of 6_ |
| **Validation site status** | _PPVC Line 1 active / blocked / awaiting agreement_ |
| **Corpus depth** | _0 validated CIAER+ events_ |
| **Codebook size** | _0 primitives_ |
| **Last shift captured** | _YYYY-MM-DD / none yet_ |
| **Last working session** | 2026-05-24 — Claude Code — repo structure scaffolding |
| **Build is** | 🟢 _healthy_ &nbsp;/&nbsp; 🟡 _flagged_ &nbsp;/&nbsp; 🔴 _blocked_ |

---

## 1. Active Work Items

Things being worked on right now. Move items here from §3 (Backlog) when starting; move to §5 (Completed) on acceptance. Limit WIP to 3 active items at a time — more than that means something is actually blocked and should be in §4.

| ID | Item | Module(s) | Started | Owner | State |
|---|---|---|---|---|---|
| W-001 | _Example: implement `PolarBleBiometricSource.rrIntervals()` with PMD packet anchoring_ | `source-polar` | _YYYY-MM-DD_ | _Claude Code_ | _in-progress / review / blocked_ |
| | | | | | |
| | | | | | |

### Per-item working notes

Use this space for in-progress thinking that doesn't belong in commit messages. Anything that would otherwise be lost when the session ends goes here.

**W-001** — _Working notes for the active item. ε_sync measurements from yesterday's bench test: 47 ms median, 89 ms p95. Next step is the back-fill sync on BLE reconnect; H10 internal buffer holds the data but the SDK's read-back path isn't documented well — see vendor issue #__._

---

## 2. Phase 1 Acceptance Tracker

From CLAUDE.md §11. Do not advance phases until every criterion is checked.

- [ ] `core-capture`, `core-codec`, `core-llr` modules functional
- [ ] `source-polar` (H10) consuming PMD streams
- [ ] `source-camerax` POV video
- [ ] Phone-side mux pipeline writing fMP4 with all five Phase 1 tracks (POV, acoustic, vibration, biometric, voice)
- [ ] LLR gate operates in shadow mode (logs candidates, never persists a container)
- [ ] ε_sync measured and logged per session
- [ ] ε_sync sustained ≤ 100 ms across at least 5 production shifts
- [ ] 20–30 candidate windows logged
- [ ] Hand-labeling tool (`tools/shadow-mode-labeler`) operational
- [ ] All BLE dropouts emit explicit `BiometricGap` records (no silent interpolation)

**Phase 1 hard stop:** if ε_sync exceeds 250 ms persistently, halt and debug Polar↔phone clock anchor before proceeding. Document the failure mode here:

_None observed yet._

---

## 3. Backlog (Next Up)

Ordered by intended pickup, not by priority alone. Top of list is next.

1. _Bench-test `PolarBleBiometricSource` against H10 over a 4-hour continuous capture; characterize dropout rate near the extruder barrel._
2. _Wire `core-llr` Λ_env from acoustic spectral KL divergence — initial implementation only._
3. _Wire `core-llr` Λ_bio from HR delta + HRV-RMSSD ratio — Polar PMD-derived._
4. _Build `tools/shadow-mode-labeler` as a minimal Compose screen reading candidate windows from local storage._
5. _Implement `PreEnvSource.captureBaseline()` as a 90-second sample-all-channels routine triggered manually at shift start (auto-detection deferred to Phase 2)._

When pulling an item from this list into §1, copy its text verbatim and assign a W-### ID.

---

## 4. Blocked / Flagged

Items that cannot advance until something external resolves. Each entry needs an unblock condition.

| Item | Blocked on | Unblock condition | First flagged | Last poked |
|---|---|---|---|---|
| _Facility deployment agreement with Hollowell_ | _Legal sign-off_ | _Signed MSA + data-rights addendum_ | _YYYY-MM-DD_ | _YYYY-MM-DD_ |
| _PLC API integration (Phase 3 prep)_ | _Vendor access + IT scope clarification_ | _Read-only OPC-UA endpoint or documented historian export_ | _YYYY-MM-DD_ | _YYYY-MM-DD_ |
| | | | | |

---

## 5. Recently Completed

Last 10 items max. Anything older lives in version control.

| Date | ID | Item | Notes |
|---|---|---|---|
| 2026-05-24 | W-000 | Repository scaffolded per CLAUDE.md §10 — full directory tree, files organized, CURRENT_PHASE.md moved to phases/ | Initial structure commit |
| | | | |
| | | | |

---

## 6. Open Decisions Awaiting Author Sign-off

Architecture-level questions where Claude Code should **not** decide unilaterally. Park them here; do not implement until Kahn responds.

| Decision needed | Options on the table | Claude Code's recommendation | Date raised |
|---|---|---|---|
| _Example: BiometricSource implementation when both H10 and Verity Sense are paired simultaneously — prefer ECG track from H10, accel from Verity Sense, or fail closed and require the user to select one?_ | _(a) prefer H10 / (b) prefer Verity Sense / (c) require explicit selection_ | _(a) prefer H10 for ECG, accept Verity Sense accel only if H10 absent. Mirrors the validation-vs-operational device strategy in CLAUDE.md §8.3._ | _YYYY-MM-DD_ |
| | | | |

---

## 7. Drift Watch

Things observed during recent sessions that **could** become problems if they continue. Not yet blocking. Re-evaluate weekly.

- _Nothing currently flagged._

Common patterns to watch for (delete this once seen at least once, since at that point it's documented above):

- LLR gate accumulating implicit state that survives shift boundaries — should be I-frame-bounded.
- BLE reconnect emitting interpolated values instead of `BiometricGap` markers.
- `R_phys` deferred queue items quietly expiring without their events being marked `INDETERMINATE`.
- Codebook dict entries being mutated in place rather than versioned.
- Confidence updates running on a path that doesn't reference a real R_phys row.

---

## 8. Session Log

Append-only. One entry per Claude Code session or per Kahn working session. Keep entries short — full reasoning belongs in commits and §1 working notes.

```
YYYY-MM-DD — Kahn / Claude Code session #N
  - What was worked on: …
  - What changed in state above: …
  - Surprises: …
  - Next session pickup point: …
```

```
2026-MM-DD — Kahn — repo init
  - Committed CLAUDE.md and CURRENT_PHASE.md.
  - No code yet. Phase 1 clock starts when Polar SDK integration begins.
  - Next session pickup point: scaffold `source-polar` module and run first H10 bench test.
```

```
2026-05-24 — Claude Code — repo structure scaffolding
  - Created full directory tree per CLAUDE.md §10.
  - Moved CURRENT_PHASE.md → phases/CURRENT_PHASE.md (README already referenced this path).
  - Organized docs: whitepapers → docs/whitepaper/, patent drawings → docs/patent/, analysis docs → docs/.
  - Moved MCP zip archives → tools/.
  - Added .gitkeep to all empty module directories.
  - Next session pickup point: begin source-polar Kotlin module (BiometricSource interface, PMD integration).
```

---

## 9. Update Protocol for Claude Code

At the end of every working session in this repo:

1. Move any newly completed items from §1 to §5.
2. Update §2 acceptance checkboxes against actual state — be honest, don't tick anything that isn't truly done.
3. If anything new is blocked, add it to §4 with the unblock condition.
4. If an architectural question came up that you couldn't resolve from CLAUDE.md, add it to §6 — do not guess and implement.
5. Add a one-paragraph entry to §8 with the next session's pickup point.
6. Update §0 corpus depth, codebook size, and "Last working session" line.
7. Update the "Build is" status indicator in §0 based on the state of §4 (any 🔴 blockers? → 🔴; any 🟡 flags in §7? → 🟡; clean? → 🟢).
8. Commit this file in the same commit as the code change it documents.

**Never delete from §5 or §8.** Truncate §5 to 10 entries by moving older entries into a `phases/archive/` subdirectory if needed, but do not lose history.

---

*End of CURRENT_PHASE.md. State only. Architecture lives in /CLAUDE.md.*
