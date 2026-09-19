# BrainFly 2026.09.20-0252 — Minecraft World Model

0251 made knowledge active, but live play showed that BrainFly still lacked durable Minecraft-specific object permanence and resource procedures. 0252 adds those missing priors without replacing the connectome as the actor.

## Persistent infrastructure

- crafting tables and furnaces are remembered as reusable world objects;
- known stations satisfy campaign requirements even when not carried;
- recipe execution reuses a station before expanding its crafting recipe subtree;
- completed goals do not re-materialize already-consumed dependencies on the next tick;
- BrainFly-owned/previously-used portable stations can be reclaimed for expeditions;
- stale station memory is live-validated and removed when the block is gone.

## Persistent mine / resource provenance

Explicit source/tool/depth strategies cover cobblestone, coal, raw iron/copper/gold, lapis, redstone, diamond, emerald, obsidian and flint.

When underground ore is neither visible nor remembered, BrainFly creates/reuses a persistent mine with entrance, head, direction and target depth. It descends via a two-block-high staircase and then extends a tunnel while scanning newly exposed blocks.

Early progression is now explicitly grounded as:

`stone_pickaxe -> raw_iron (iron/deepslate iron ore, >= stone pickaxe, target Y~16) -> furnace/fuel -> iron_ingot -> iron_pickaxe`.

## Navigation

- native fallback actively jumps valid one-block steps;
- repeated no-progress geometry has a bounded recovery budget and returns `navigation_stuck_cycle`;
- close targets above the body can trigger `pillarUp`;
- A* pathfinder may use 1x1 towers when build material is available;
- the installer attempts to install `mineflayer-pathfinder@2.4.5` when it is missing, but keeps the improved native fallback if npm/network is unavailable;
- repeated goal signatures produce `goal_loop_detected` and autonomy changes tactic.

## Destiny

`stable_food` and `home` are now background survival/settlement objectives. They no longer hard-block technological progression from stone tools into iron; homeostasis and den logic continue independently.

## Verification

- **39/39** JavaScript regression tests PASS
- `tools/smoke_test.py` PASS
- N = 165122 neurons
- E = 10511038 structural edges
- effective_edges = 5016094
- Minecraft motor spike delta = 192

New tests cover existing-station reuse, completed-goal dependency suppression, persistent raw-iron mine creation, and stone->iron Destiny progression despite missing food/home milestones.

## Artifacts

- `BrainFly-0252-RUNTIME-HOTFIX.zip` SHA-256 `7d95e3c87a39d691be7830a89fff8ce3a6a7990895d950367bc414466575bbbd`
- `BrainWorkbench-Minecraft-2026.09.20-0252.zip` SHA-256 `e049495fa106c12ebcf507e5598823764af9ea7d693f0e7fec24418635008e69`
- `BrainFly-0252-WORLD-MODEL.patch` SHA-256 `82648ecf2bab1ae12898b9438cfac87f95a38177b15a885fcb835ce0c1aaf29c`

The hotfix preserves config, memory, logs, snapshots and external knowledge packs. The Fabric mod does not need rebuilding for this runtime-only release.
