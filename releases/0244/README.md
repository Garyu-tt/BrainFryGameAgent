# BrainFly 2026.09.19-0244

This recovery point combines the in-game BrainFly Control Panel with the mining interaction-lock fix observed during live autonomous progression.

## Live bug fixed

The neural motor loop could still issue forward/back/yaw while a high-level `bot.dig()` was running. BrainFly would begin mining, turn or run away, and only occasionally complete one or two blocks.

0244 adds a canonical mining transaction:

`target -> movement lock -> look lock -> dig -> completion/error -> unlock`

The exact block position is retained, movement is repeatedly cleared, gaze is repeatedly re-aimed at the live block, and the motor loop yields while `agency.busy` / an interaction lock is active.

## Control Panel

The Fabric client provides:

- panel hotkey (default B);
- teach / follow / auto / show / use;
- positive and negative reinforcement;
- goals, crafting and navigation controls;
- home, den, destiny and overnight controls;
- real BrainFly inventory access;
- configurable Minecraft keybinds;
- localhost UDP control transport;
- live status and command results in the panel without chat spam.

The replacement keeps mod id `brainfly_inventory_link` so it supersedes the older inventory-link client rather than running as a second conflicting mod.

## Verification

- JavaScript regression suite: **25/25 PASS**
- `tools/smoke_test.py`: PASS
- Node syntax checks for bridge and embodied agency: PASS
- Interaction-lock regression verifies repeated movement suppression and repeated re-aiming until mocked dig completion.

The Fabric source targets Minecraft 1.21.1. The source and build/install scripts are packaged, but the remapped JAR was not built in the ChatGPT execution environment because Gradle/dependency-network access was unavailable there.

## Packaged artifact hashes

- Full snapshot `BrainWorkbench-Minecraft-2026.09.19-0244.zip`
  SHA-256: `dbaf8aaa6dcc222966ab1d8cc74c5a255ed1a6c41aca83ad8e91fe1226a47271`
- State-safe patch `BrainFly-0244-state-safe-patch.zip`
  SHA-256: `bb0b67c4dddd0e7fc470f9cfc74d80a6875388075dae4a5a12291be1f36138b3`
- Fabric source `BrainFly-Control-Fabric-1.21.1-2026.09.19-0244.zip`
  SHA-256: `e3a7c39eff7de29b5b3ed35723085af3e6d1663e0a184a93b7d04897c6256d2e`

For a live installation, prefer the state-safe patch so learned memory, logs, snapshots and current world-progress state are not replaced.
