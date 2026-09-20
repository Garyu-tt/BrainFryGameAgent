# 0256 implementation notes

## New runtime components
- `minecraft/schematic-library.mjs`
- `minecraft/gameplay-duties.mjs`
- `minecraft/knowledge/schematics-1.21.1.json`

## Key integration points
- `embodied-agency.mjs`: inventory capability hierarchy, acquisition contract, schematic execution, storage/hoarding, Gameplay Duties, persistent infrastructure bootstrap, portal construction and revised home builder.
- `temporal-motor.mjs`: explicit Minecraft `start_fall_flying` entity action during Elytra deployment.
- `minecraft-bridge.mjs`: acquisition gets exclusive background scheduling while active; new status/commands for inventory capabilities, pickup, duties, schematics and portal.
- `infrastructure-memory.mjs`: persistent table/furnace/smoker/chest/barrel/bed/portal positions.
- `autonomy-supervisor.mjs`: duties/schematic memory checkpointing and 10-obsidian minimal portal progression.
- Knowledge Graft adds inventory-capability, persistent-base, stockpile, aesthetic-completion, schematic, portal and pickup-closure concepts.

## Persistent state
0256 adds:
- `minecraft/memory/gameplay-duties.json`
- `minecraft/memory/schematic-memory.json`

Existing memory remains untouched by installation.

## Installer
The ONECLICK installer:
- patches the existing BrainWorkbench runtime in place;
- makes a timestamped code backup;
- preserves config, memory, logs, snapshots and external knowledge packs;
- best-effort installs `mineflayer-pathfinder@2.4.5`;
- builds/installs the 0256 Fabric control source;
- verifies the runtime marker `2026.09.20-0256`.
