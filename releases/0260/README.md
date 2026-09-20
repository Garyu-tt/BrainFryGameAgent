# BrainFly 2026.09.20-0260 — Mikoshi Multi-Body Minecraft Habitat

0259 successfully hosted four independent neural brains in one shared-connectome process, but only Brain A had a Minecraft body. 0260 fixes that omission: A-D can now each own a real Mineflayer body and join the same Minecraft server.

## What changed

- every Minecraft bridge carries an explicit Mikoshi brain ID;
- A uses the existing BrainFly identity and memory;
- B-D join as `BrainFly-B`, `BrainFly-C`, `BrainFly-D`;
- B-D use isolated persistent Minecraft memory roots under `minecraft/memory/mikoshi/<ID>`;
- only A listens to Fabric UDP teacher/control telemetry and ordinary chat commands by default;
- ECO mode keeps A at a 20 Hz body loop and B-D at 8 Hz while all neural compute remains under the single Mikoshi shared CPU budget;
- Mikoshi `status` now exposes live per-body data from A-D;
- a body supervisor restarts one failed bridge without restarting the other bodies or Mikoshi.

## Launchers

- `START-MIKOSHI-HABITAT.cmd` — clean integrated A-D start.
- `START-MIKOSHI-AS-BRAIN.cmd` — compatibility alias to Habitat mode.
- `JOIN-MIKOSHI-BODIES.cmd` — when Mikoshi and ordinary BrainFly A are already running, add B-D only.
- `START-MIKOSHI-BODIES-4.cmd` — launch A-D against an already-running Mikoshi.
- `MIKOSHI-BODIES-STATUS.cmd` — compact body status.
- `START-MIKOSHI-BACKEND-ONLY.cmd` — retain the old headless 0259 behavior.

## Verification

- 56/56 JavaScript regression tests PASS.
- Smoke test PASS: N=165122, E=10511038, effective_edges=5016094, minecraft_motor_spike_delta=123.
- Four-brain mock integration verified four separate `io_bindings` and four independent live `status.bodies` entries.
- Explicit routing regression verifies that B/C/D requests do not silently target A.

The build environment cannot join the user's actual Minecraft server, so real network login of A-D is the remaining live validation step.

## Artifacts

- `BrainFly-0260-ONECLICK.zip` SHA-256 `a9353e928189e66320a7ee7c073d6fd5062ede2adc4db49c6569dc3944df4c0a`
- `BrainWorkbench-Minecraft-2026.09.20-0260.zip` SHA-256 `7328b887d0a09486ebd92b1f9495e01958ab8bcdb5084717755567df977e733a`
- `BrainFly-0260-MULTIBODY-HABITAT.patch` SHA-256 `5d8ace433acd20bdc6032b13a980977b888486013019f4571d5c9c76c4d334df`

No Fabric rebuild is required. The installer preserves config, learned memory, logs, snapshots and external knowledge packs.
