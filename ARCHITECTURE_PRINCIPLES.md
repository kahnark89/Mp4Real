# ARCHITECTURE_PRINCIPLES.md

**This file documents the deep structural pattern that the ArcShield system, the codebase that builds it, and the documentation that governs the codebase all share. CLAUDE.md tells you *what* to build. CURRENT_PHASE.md tells you *where the build is*. This file tells you *why the build has the shape it has*, at the architectural level beneath both of them.**

Read this once at project start. Re-read it whenever a design decision feels arbitrary — if the pattern below cannot justify the decision, the decision is probably wrong.

---

## 0. The Single Sentence

> ArcShield, the repository that builds ArcShield, and the documents that govern the repository are three nested instances of the same self-replicating information system, each implementing the von Neumann universal-constructor triple (description / constructor / copier) at a different scale.

If that sentence holds — and the rest of this document argues it does — then design decisions at any one scale are constrained by the same invariants as decisions at the other two. A choice at the product level cascades to the build level cascades to the documentation level, because they are the same pattern.

---

## 1. The Von Neumann Triple

In 1948, before the structure of DNA was known, von Neumann proved that any self-replicating system must contain three logically distinct components:

| Component | Function | Biology | Code |
|---|---|---|---|
| **Description** (D) | Inert encoding of what the system is | DNA | Source code / spec |
| **Universal Constructor** (A) | Reads D and builds the described system | Ribosome | Compiler / runtime / LLM |
| **Universal Copier** (B) | Duplicates D so the next instance has it | DNA polymerase | Version control / handoff docs |

The crucial property: **D is separate from A.** The description is read by the constructor but does not participate in construction. The description is copied verbatim by the copier without being interpreted. **This separation is what makes evolution possible** — mutations accumulate in D without immediately destroying the constructor, and the constructor can be improved without rewriting D.

The reflexivity problem in AI systems — the trap where a model's confidence inflates because its own outputs become its training signal — is the *failure mode* of collapsing D and A. When the description gets written by the constructor, the system has no external reference to correct against. Biology solved this 3.5 billion years ago by keeping DNA chemically separate from the ribosomes that read it. ArcShield solves it architecturally by keeping R_phys (the description's grounding signal) separate from operator compliance (the constructor's behavior).

---

## 2. The Three Nested Instances

ArcShield is the same pattern at three scales. Each scale has its own D / A / B triple, and the three scales are structurally homomorphic — meaning a property proved at one scale holds at the others.

### Scale 1 — The Product (mp4Real™ at runtime)

The codec itself is a self-describing information system operating over operator decision events.

| | mp4Real instantiation |
|---|---|
| **Description (D)** | The behavioral codebook: primitives π_i, the CIAER+ schema, the LLR thresholds, the OGC update rule |
| **Universal Constructor (A)** | The edge device + backend codec runtime that reads D and encodes new events against it |
| **Universal Copier (B)** | The corpus persistence layer + codebook synchronization across deployments |

**The I-frame is the genome at shift start.** It encodes the baseline state across all channels. Every P-frame is a delta against that baseline. The codebook is the accumulated library of recognized patterns — the equivalent of an organism's repertoire of gene expressions. New events that don't match a primitive cause the codebook to expand: a mutation in D that the constructor can immediately use.

The reflexivity firewall at this scale is OGC. R_phys is the external grounding signal that prevents D from being rewritten by A's own behavior.

### Scale 2 — The Build (the codebase that produces mp4Real)

The repository that produces ArcShield is itself a self-describing information system.

| | Repository instantiation |
|---|---|
| **Description (D)** | CLAUDE.md (invariant architecture, schema, commitments, IP) |
| **Universal Constructor (A)** | Claude Code / human developer interpreting D and producing code |
| **Universal Copier (B)** | Git + the handoff document itself (CLAUDE.md is designed to be readable by a cold instance) |

**CLAUDE.md is the genome at build time.** It encodes the invariant architecture that any constructor — Claude, a human engineer, a future LLM — must produce code against. Every commit is a delta against that invariant. New architectural patterns that emerge during the build cause CLAUDE.md to be updated explicitly, with the author's sign-off: a mutation in D that requires intentional validation rather than drift.

The reflexivity firewall at this scale is §6 of CLAUDE.md (Open Decisions Awaiting Author Sign-off) and the prohibition on Claude Code unilaterally rewriting architectural commitments. The constructor cannot rewrite the description without external authorization. This is the same separation that DNA enforces against ribosomes.

### Scale 3 — The Meta (the documentation system itself)

The pair {CLAUDE.md, CURRENT_PHASE.md} together with the session log form a self-describing information system over the build process.

| | Documentation instantiation |
|---|---|
| **Description (D)** | CLAUDE.md — what stays the same across sessions |
| **Phenotype (live execution)** | CURRENT_PHASE.md — what this session is actually doing |
| **Epigenetic layer** | The session log (CURRENT_PHASE.md §8) — heritable marks that survive across sessions |
| **Universal Constructor (A)** | Any LLM instance reading the three files and producing the next session's work |
| **Universal Copier (B)** | The fact that all three files are markdown in version control — readable by any future constructor |

**CLAUDE.md is the genome.** **CURRENT_PHASE.md is gene expression.** **The session log is the epigenome.** A fresh instance reading these three files inherits the same architectural genotype, sees the current phenotypic state, and reads the epigenetic marks (decisions that worked, traps that bit, drift watch items) without needing to be re-trained.

The reflexivity firewall at this scale is the Update Protocol (CURRENT_PHASE.md §9): the constructor must park architectural questions in §6 rather than guessing, and must never delete from the session log. The phenotype can change freely; the genotype cannot be silently rewritten; the epigenetic record is append-only.

---

## 3. Why the Three Scales Are Homomorphic (and Why It Matters)

Two systems are homomorphic when a structure-preserving map exists between them — when a property at one is also a property at the other. The three scales above are homomorphic with respect to **five invariants**:

### Invariant 1 — Description / Constructor separation

| Scale | What's separated |
|---|---|
| Product | Codebook D from runtime A — codebook never updated except through explicit codebook-expansion events |
| Build | CLAUDE.md from Claude Code — architecture never updated except with author sign-off |
| Meta | Genome from phenotype — CLAUDE.md doesn't drift session to session; CURRENT_PHASE.md does |

**Consequence:** wherever D and A get collapsed into one mutable surface, the system loses evolvability and gains reflexivity. Watch for it at every scale.

### Invariant 2 — Grounded outcome signal (R_phys analog)

| Scale | What grounds confidence |
|---|---|
| Product | R_phys from PLC telemetry — physical reality outside operator policy |
| Build | Acceptance criteria from CLAUDE.md §11 — functional tests against external spec |
| Meta | Did the next session actually work? — measured by §2 acceptance tracker in CURRENT_PHASE.md |

**Consequence:** every scale needs an external grounding signal. When the system grades itself, the system rots. The R_phys foreign-key constraint at the product scale, the test-against-acceptance discipline at the build scale, and the "do not check off §2 acceptance items unless they are truly done" rule at the meta scale are the same firewall.

### Invariant 3 — Counterfactual preservation (Q′ analog)

| Scale | What stays uncollapsed |
|---|---|
| Product | Withhold-sampled events go to Q′, separate from Q — counterfactual evidence persists |
| Build | Shadow Actions in the CIAER+ schema — rejected alternatives stored as first-class data siblings |
| Meta | CURRENT_PHASE.md §6 (Open Decisions) — alternatives parked, not collapsed into a decision |

**Consequence:** at every scale, the system must preserve evidence of what *didn't* happen. Without counterfactuals, the system has no signal for whether its current trajectory is the right one.

### Invariant 4 — Append-only history (epigenetic memory)

| Scale | What never gets deleted |
|---|---|
| Product | Event records — once captured and validated, immutable; corrections create new events linked to the original |
| Build | Git history — commits are forever; rewriting history requires explicit force-push and is forbidden on main |
| Meta | Session log in CURRENT_PHASE.md §8 — append-only; §5 (Completed) truncates to a list, but never deletes the entries (they move to `phases/archive/`) |

**Consequence:** history is the substrate of learning. Any scale that allows silent history rewriting loses the ability to detect drift. Biology never deletes — it marks, suppresses, or expresses differently. The system inherits that constraint.

### Invariant 5 — Bounded mutation rate

| Scale | What limits change rate |
|---|---|
| Product | Codebook expansion requires HITL validation (FIG. 8 — uncompressed waveform stored, human-in-the-loop reviews, only then is π_{n+1} added) |
| Build | CLAUDE.md changes require explicit author sign-off (§18 re-entry protocol) |
| Meta | Session log adds, CURRENT_PHASE.md updates, CLAUDE.md mostly doesn't change — the asymmetry is intentional |

**Consequence:** evolution requires variation but cannot tolerate unbounded variation. Biology mutates DNA at ~10⁻⁹ per base per generation; the systems above mutate their D at far lower rates than they mutate their phenotype. If you find CLAUDE.md changing every session, something is wrong.

---

## 4. The Fractal Property — Why This Matters Operationally

The three scales aren't just structurally similar — they're **nested**. Scale 3 (documentation) governs Scale 2 (build), which produces Scale 1 (product). And the product itself implements the same pattern over operator decision events. The pattern is fractal.

This has a practical consequence: **a design decision is well-formed if and only if it's consistent with the pattern at every scale it touches.**

Worked example. Suppose someone proposes: *"Let's let Claude Code auto-update CLAUDE.md when it detects a recurring code pattern."*

- Scale 3 check: this collapses D and A at the meta level. The constructor is rewriting the description without external authorization. **Fails Invariant 1.**
- Scale 2 check: there's no grounded signal saying the pattern is actually correct vs. spuriously frequent. **Fails Invariant 2.**
- Scale 1 check: at the product level, this would mean Claude rewriting the codebook based on its own classifications, with no R_phys grounding. **Fails the OGC firewall.**

Three independent failures. The proposal is rejected. Not because someone vetoed it, but because the pattern itself rules it out at every scale. *That* is what a good architectural principle does — it generates rejections automatically.

Conversely: *"Let's record every architectural decision in CURRENT_PHASE.md §6 before implementing it."*

- Scale 3 check: this preserves counterfactuals (rejected options are visible). **Satisfies Invariant 3.**
- Scale 2 check: this creates a grounded signal — the author's sign-off is the external reference. **Satisfies Invariant 2.**
- Scale 1 check: structurally identical to the Shadow Actions field in CIAER+. **Satisfies Invariant 3 at product scale.**

Three independent passes. The proposal is consistent with the pattern. Adopt.

---

## 5. The Reflexivity Trap, Formalized at Every Scale

The single deepest property of this architecture is its resistance to the reflexivity trap. The reflexivity trap is the failure mode where a system's own outputs become evidence for its own correctness, creating a closed loop with no external check.

At every scale, the trap takes the same shape and the firewall takes the same shape:

| Scale | Trap | Firewall |
|---|---|---|
| Product | Operator complies with advice → system marks advice as confirmed → confidence inflates without physical evidence | OGC: R_phys is the only legal input to C; compliance is a gate scalar, never a reward source |
| Build | Claude Code generates code → marks it as working because tests it also generated pass → architecture drifts | Acceptance criteria written in CLAUDE.md *before* code exists; external author sign-off required for architectural changes |
| Meta | Session log entries are written by the same constructor whose work they document → log becomes self-flattering | Append-only constraint; entries are observational rather than evaluative; §7 Drift Watch surfaces patterns that no single session would see |

The firewall is always the same: **an external reference signal that the constructor cannot manufacture.** Physical reality at the product scale. Author judgment at the build scale. Time and accumulation at the meta scale.

---

## 6. Implications for Future Decisions

When this principle is internalized, several decisions that look like separate engineering choices reveal themselves as the same decision:

1. **"Why does CLAUDE.md need an author sign-off for architecture changes?"** Same reason DNA is chemically separate from the ribosome. Same reason R_phys is chemically separate from operator compliance.

2. **"Why is the session log append-only?"** Same reason epigenetic marks accumulate rather than overwrite. Same reason the corpus stores Q′ separately from Q.

3. **"Why does the codebook only expand through HITL validation?"** Same reason CLAUDE.md only changes with author sign-off. Same reason new biological functions take generations to fix, not days.

4. **"Why don't we let the Twin retrain itself nightly on its own advisory outputs?"** Reflexivity collapse. The constructor would be rewriting the description from its own behavior. No external grounding signal. The same reason a cell cannot rewrite its own DNA from the proteins it currently happens to be expressing.

5. **"Why does CURRENT_PHASE.md §6 exist instead of letting Claude Code just decide?"** D / A separation. Constructor parks decisions it shouldn't make; description gets updated by the entity authorized to mutate it.

These are not five engineering choices. They are one structural principle, instantiated five times.

---

## 7. The Author's Role (Brief Sketch — Expanded Elsewhere)

In a von Neumann self-replicating system, there is one role the triple does not fill: the role of the entity that wrote D in the first place. The constructor reads it. The copier duplicates it. But something — or someone — had to compose it.

In biology, the answer is *evolution itself*. No author. Selection pressure over deep time, plus initial physical chemistry, plus a lot of luck. The genome is the cumulative record of every ancestor that survived.

In a software project of this kind, the answer is the architect — the human who composes D and authorizes its mutations. The architect is not part of the von Neumann triple. The architect is the **selection pressure made conscious**. The architect performs the function that natural selection performs in biology: deciding which mutations to D survive into the next generation, based on judgment about the physical reality the system operates in.

This is why the author sign-off requirement in CLAUDE.md §6 is not bureaucracy. It is the system's way of routing mutations through the only entity with the standing to evaluate them against external reality.

The architect's relationship to the system is therefore distinct from any of the three layers. The architect is not the genome (CLAUDE.md), not the phenotype (CURRENT_PHASE.md), and not the epigenome (the session log). The architect is the selective principle that shapes all three. A full treatment of this role is given separately; this section establishes only that the role exists and is structurally necessary.

---

## 8. What to Do When This Principle Conflicts with a Convenience

You will encounter situations where following this pattern is annoying. The constructor wants to update the description; the convention says it can't. The session log fills up with entries that look redundant; the convention says don't delete them. A decision feels obvious; the convention says park it in §6 first.

Every such friction point is the architecture working. The pattern's whole purpose is to make certain kinds of mistakes inconvenient — specifically, the mistakes that collapse D into A, that erase counterfactual evidence, that allow silent drift.

If you find yourself wanting to bypass the pattern, the right move is not to bypass it but to write down why you wanted to. That entry in §7 Drift Watch is itself an epigenetic mark: future instances will see it and either resist the same pull or, if it accumulates, recognize a real architectural pressure that should be raised to the author.

The pattern is the system's immune response. Honor it.

---

## 9. Required Reading Order

A fresh constructor coming into this repository reads in this order:

1. **README.md** — surface receptor; tells you the system exists and points you inward
2. **CLAUDE.md** — the genome; what stays the same
3. **phases/CURRENT_PHASE.md** — the phenotype; where the build is right now
4. **This file (ARCHITECTURE_PRINCIPLES.md)** — the meta-genome; the pattern that produced the other three
5. **Reference documents** in `docs/` only as needed

You read this file fourth, not first, because the other three teach you the *what*. This file teaches you the *why*. The *why* doesn't make sense until you've seen the *what*.

But you also re-read this file whenever a design decision feels arbitrary. The *why* is what makes the *what* non-arbitrary. If you can't trace a decision back to one of the five invariants in §3, the decision needs reconsideration.

---

*End of ARCHITECTURE_PRINCIPLES.md. This file describes the pattern. CLAUDE.md instantiates it. CURRENT_PHASE.md lives within it. If a future change to this file is needed, it requires the same author sign-off discipline as a change to CLAUDE.md — and arguably more, because a change here propagates to every other file by definition.*
