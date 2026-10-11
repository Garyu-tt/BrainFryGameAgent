# Dream Mine 0.3.0 — intention continuity and verified action feedback

**Experimental NeoForge 1.21.1 mod, Java 21, tested for compilation with NeoForge 21.1.257 and Fake Players 2.2.0.** This is a development build, not a completed port of BrainFly HCP800.

## Why 0.2.1 still looked like a vanilla bot

The previous agent produced neural action labels but usually executed them for only one decision tick. Most intents lost ownership of the body before a real outcome could be checked. This caused oscillation among exploration, a brief glance at the player, and target selection. The source also confused navigation progress with high-level goal progress, while repeated failures rarely influenced which action was chosen next.

## New features

- **Persistent intention executive** per Dream: chooses a high-level task, commits for a bounded period, retains ownership across frames, progresses only when verifiable world events occur, and defers/replans on failure.
- **Verifiable action provenance**: the `/dreammine brain` command distinguishes `controller_ticks` from real `neural_decisions`, and prints `intent`, `phase`, `source`, `evidence`, verified successes, deferred failures and an individual's outcome-history count. The neural source is explicitly `EMBEDDED_H01` — **not** a full HCP800 or an unseen separate model.
- **Longer social observation**: when the network selects observing a nearby player, the Dream maintains its attention for ~12 seconds instead of looking for a fraction of a second, and records the number of frames in which the player remained visible and whether the player moved.
- **Tree inspection and resource workflows**: non-destructive inspection of real tree trunks is a sustained goal, repeated inspection of the exact same trunk is avoided using a bounded personal LRU. Optional resource actions still run through the harvesting and crafting executor.
- **Exploration evidence**: exploration success now requires actually moving to and sensing a previously unvisited world region or a different block material. Mere movement distance is no longer sufficient to claim a discovered location.
- **Learning from unsuccessful plans**: failed goals get temporary per-action cooldowns, affecting the mask of neural options. Outcome history, cooldowns, surveyed 8×8 map cells and inspected tree blocks are stored in individual NBT, capped to avoid unlimited growth.
- **Safety authority**: submersion, fire, critical health and nearby hostile mobs interrupt regular tasks; these are recorded as *infrastructure safety*, not falsely attributed to neural decisions.
- **Scalable scan budgets**: natural-log searches use bounded candidate work, with slower search intervals at larger population sizes. Existing sensor sampling also slows as the population grows.
- **Existing features remain**: one auto-spawned Dream, unlimited egg-spawned Dreams, 100-block boundary, custom nicknames and skin lookup, local individual/world-shared memory, optional aggregate diagnostics (off without individual opt-in + server HTTPS configuration).

## Installation and test

Keep `players-forge-1.21.1-2.2.0.jar` installed. Remove `dreammine-0.2.1.jar`, install **only** `dreammine-0.3.0.jar` in both client and server if multiplayer, then restart the game. Back up the world before upgrading.

Recommended test (no terrain destruction):

1. In a **disposable test world**, leave `/dreammine actions off` (default).
2. Spawn one Dream using `/give @s dreammine:dream_spawn_egg` or observe the automatically spawned Dream.
3. Run `/dreammine brain` several times over about 30 seconds. Watch `intent`, `phase`, `neural_decisions`, `evidence`, `verified`, `deferred`, and `history`. A committed goal should persist across controller ticks.
4. Run `/dreammine senses` to check actual vision reliability and proprioceptive feedback; position Dream near trees and move in front of it.
5. Observe whether it inspects a tree only once, watches the player for multiple seconds and reselects an action after an evidenced failure.
6. Optional, **only in a test world**: `/dreammine actions on` allows harvesting and placing blocks. The current simplified executor does **not** fully implement all land-claim protections, so do not enable in a valuable world. Confirm actual inventories and world blocks; do not infer success from a command alone.
7. Collect `latest.log`, plus outputs of `/dreammine brain` and `/dreammine senses` if behavior remains repetitive.

## Limitations / important distinctions

- The embedded neural runtime is still **H01-lite, 8×96 simulated microcolumns** with an expanded sensory registry (584 names / 768 reserved slots), NOT the original 800-module HCP800, nor the full BrainFly world-model/goal ecology/language/learning system.
- The high-level repertoire still offers five bounded action affordances. This release should reduce goal thrashing, but cannot yet reproduce all BrainFly emergent behavior.
- No full Minecraft playthrough or multiplayer smoke test has been completed for this version. The build server had only ~1.8 GiB memory available during the build, so starting a second NeoForge instance would have risked the running Minecraft/50 HCP services. Existing live world and those services were not restarted.
- Diagnostic data is disabled without explicit player consent and a separately configured HTTPS endpoint. The receiver can still observe the game server's IP, so the data is de-identified rather than guaranteed anonymous.
- Skin loading is provided by Fake Players and may use its normal external skin service.

## Verification

- Java 21 compilation against installed NeoForge/Fake Players dependencies: PASSED.
- 9,563 Java checks passed: 79 goal executive/evidence, 326 sensor registry, 6 sensory→neural grounding, 7,817 neural plasticity/persistence, 231 behavior, 1,104 exploration/safety.
- Packaged class files: `DreamExecutive`, `DreamReflex`, `DreamWork`, `EmbeddedH01`, `DreamSensory` and existing mod resources; test classes omitted from JAR.
- SHA-256: `db2d7185f22ffec442e0592fa6b019f3a7cd13dadb24477c4f0a63304e5561a4`.

**Release status: compiled/logic-tested candidate; in-game acceptance pending.**
