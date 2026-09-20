# BrainFly 2026.09.20-0259 — Mikoshi / Multi-Brain Habitat

0259 adds an optional 1–4 brain habitat in one Node/WASM process.

## Architecture

- immutable connectome topology is shared once;
- each brain has independent live neuron state, KC→MBON plasticity and effective synapse weights;
- Brain A can replace the ordinary Brain runtime on port 48177, so integrated four-brain mode is four brains total rather than the old BrainFly plus four extra processes;
- B–D can be suspended, resumed, forked from another brain, snapshotted and restored independently;
- per-brain generic I/O is exposed for future bodies/environments.

## Emergent communication

Mikoshi exposes 32 raw symbols by default. No symbol is given a predefined meaning.

A referential game provides learning pressure:
1. only a sender sees one of eight hidden cues;
2. sender emits raw MBON-derived symbols;
3. listener receives only symbol + sender identity;
4. listener chooses from a separate MBON action population;
5. both brains are rewarded only for a correct coordinated choice.

Observer-side language statistics never become a translation table for the brains.

## Extensible homeostasis

Each brain receives additional drives for:
- social contact;
- communication uncertainty;
- coordination pressure;
- novelty.

These are injected as neural inputs, not hard commands.

## Laptop / scheduler behavior

ECO mode uses one bounded single-threaded scheduler rather than four independent runtimes. It slows simulation under load instead of trying to consume four cores or catch up an unlimited backlog.

Reference 4-brain ECO measurement in the execution environment:
- WASM allocation: 248.1 MiB
- process RSS: ~397.8 MiB
- scheduler CPU: ~57.7% of one CPU core

Actual hardware figures will differ.

## Switching modes

- `START-MIKOSHI-4-ECO.cmd`: standalone four-brain habitat on port 48179.
- `START-MIKOSHI-AS-BRAIN.cmd`: snapshot current BrainFly, stop ordinary runtime, import it as Brain A, and run Mikoshi on port 48177.
- `RETURN-TO-SINGLE-BRAIN.cmd`: export Brain A back to normal BrainWorkbench snapshot format and restore single-brain runtime.

## Verification

- 53/53 JavaScript regression tests PASS
- smoke test PASS
- N = 165122
- E = 10511038
- effective_edges = 5016094
- Minecraft motor spike delta = 128
- independent-weight regression confirms topology is shared while mutable weight arrays are separate
- service regression covers status, raw signal delivery, fork and referential game startup
- Minecraft mock bridge successfully configures neural I/O against Mikoshi primary-compat mode

## Artifacts

- `BrainFly-0259-ONECLICK.zip` SHA-256 `18542395c47c8340ba2035d51fbe412dfa417c497713347f50eb3551ab4c669a`
- `BrainWorkbench-Minecraft-2026.09.20-0259.zip` SHA-256 `f4719ccb6fc643931c776da74d0c227b0c8d5d7f6062486cbd8008fa2fff6a96`
- `BrainFly-0259-MIKOSHI.patch` SHA-256 `fe50290e67df19c0c0db9eec78ebe6ac8940b191d0429e0b3ae17a0eeea85cb8`

The installer preserves config, learned Minecraft memory, logs, snapshots and external knowledge packs.
