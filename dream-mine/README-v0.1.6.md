# Dream Mine 0.1.6 — independent controller patch

This is an **experimental NeoForge 1.21.1** build compiled for **NeoForge 21.1.257** with **Fake Players 2.2.0** installed as a required dependency. Copy `dreammine-0.1.6.jar` and Fake Players to the Minecraft client and server `mods/` folders. Remove `dreammine-0.1.5.jar`; do not run both at once.

## Why the previous build looked like ordinary Fake Players

Fake Players has several built-in goals such as random strolling, item pursuit, owner following, and combat. v0.1.5 left all these goals enabled while also sending its own movement orders, so both systems competed for the same entity navigation and the custom behavior was hard to distinguish.

**v0.1.6 explicitly removes the default `goalSelector` and `targetSelector` goals from only the entities owned by Dream Mine**, turns off their Fake Players jobs, and uses a small world-aware exploration controller. Existing Dream Mine entities are upgraded when loaded into a world with this version.

The independent controller ranks safe exploratory destinations using personal visited areas and shared encountered-block frequencies; it watches nearby players, retreats from water where possible, returns to the closest player within the configured 100-block area, and learns from movement successes/failures. Manual spawn egg limits, appearances, optional aggregate diagnostics, and individual/world-shared saved memories remain.

**Important:** this is still a **small adaptive controller, not the original HCP800 neural implementation**. A Java port of the full HCP800 system is separate development work. Compilation and logic tests alone cannot confirm in-game behavior.

## How to verify it in-game

1. Close Minecraft, replace `dreammine-0.1.5.jar` with `dreammine-0.1.6.jar`. Keep Fake Players 2.2.0 installed.
2. Enter a disposable test world. Spawn an entity using `/give @s dreammine:dream_spawn_egg` or find an existing Dream.
3. Stand within 64 blocks and run `/dreammine brain`.
4. Confirm it reports **`custom=true`, `vanilla goals=0`, `vanilla targets=0`**. Check that `decisions` increases over time, and `goal` changes through states like `SEEK_UNEXPLORED_AREA`, `TRAVEL_TO_DISCOVERY`, `OBSERVE_PLAYER`, `RETURN_TO_PLAYER`.
5. If these counters don't change, save the world and collect the game's `latest.log` containing `Dream Mine: independent controller attached`.

No local world/server was modified for this release; the host's main Mars server was left running.

## Local memory and diagnostics

Per-entity memory includes bounded learned direction scores, 32 action/outcome episodes and 64 visited map regions. The world shares learned direction scores and capped block observations; this does not imply a large language model or deep HCP memory.

Sending aggregated diagnostics is opt-in, off by default, and requires a separately configured HTTPS endpoint. No nicknames, chat, world seed, UUIDs, or coordinates are sent in the payload. The receiving service can still see the origin server IP, so it is not necessarily fully anonymous.

## Other notes

- One automatically spawned Dream and unlimited manually summoned entities, with an in-game warning that large numbers can cause severe lag.
- Eggs support `Nick@SkinAccount`. Actual skin fetching is performed by Fake Players and can depend on online skin services.
- The mods `neoforge` and `players` are required. No separate external brain service is required for this fallback behavior.
- The production game environment was **not** started to test this package.
- Offline tests: 231 base checks + 1,104 curiosity/safe-mask checks, passed.
- SHA-256: `fe8afd96dffa1bfd7a5453d499d0a6fc627ce29d691778e661b071dac2ce12d7`
