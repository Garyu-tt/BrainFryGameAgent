# BrainFly 2026.09.20-0256 — Inventory Capabilities + Gameplay Duties + Schematics

0256 is cumulative over 0255 and targets the next live-play failure class: BrainFly knew recipes and resources but still lacked durable inventory semantics, reliable action closure, persistent base duties, spatial construction priors, and real Elytra deployment.

## Inventory capability model

Item goals are no longer always exact item-id checks.

- a stone/iron/diamond/netherite pickaxe satisfies a single wooden-pickaxe capability requirement;
- the same applies to axe/shovel/hoe/sword families;
- a known placed crafting table/furnace/chest/barrel may satisfy the corresponding infrastructure requirement;
- duplicate exact tools are not crafted just because a lower-tier recipe appears in a dependency tree;
- explicit counts greater than one remain literal, so deliberate spare-tool goals are still possible;
- diagnostics: `!brain inventory capabilities`.

## Acquisition closure

Mining and pickup now share one persistent acquisition contract.

`select target -> navigate -> lock -> dig -> locate expected drop -> 3D pickup -> verify inventory delta -> release`

While an acquisition contract is active, unrelated background tasks do not steal the executive. This prevents "start mining, wander away, maybe forget the drop."

Diagnostics: `!brain pickup status`.

## Persistent infrastructure and base memory

Crafting tables, furnaces, blast furnaces, smokers, chests, barrels, beds and Nether portals are tracked as persistent world infrastructure. On upgrade, older SpatialMemory samples are also used to bootstrap known station positions, so existing worlds do not have to rediscover every table/furnace from scratch.

## Gameplay Duties

A new persistent duty layer gives BrainFly continuing responsibilities beyond one-shot Destiny steps:

- maintain a proper home base;
- expand it as progression advances;
- create and use storage;
- hoard useful reserves instead of consuming everything immediately;
- treat aesthetics/completion as mandatory rather than optional decoration;
- maintain infrastructure and toolkit;
- maintain a persistent mine;
- construct portal infrastructure when progression requires it.

Storage uses reserve thresholds so chests receive surplus rather than critical tools/food/progression items.

Commands: `!brain duties`, `!brain duties status`, `!brain duties on`, `!brain duties off`.

## Schematic library

A persistent, rotatable schematic system is included with 11 built-ins:

- starter_house_5x5
- storage_annex_5x5
- mine_entrance
- bridge_3wide_7long
- nether_portal_minimal
- emergency_shelter_3x3
- workshop_5x5
- warehouse_7x5
- portal_shelter_7x5
- lit_mine_corridor_7
- watchtower_3x3

Structural material roles permit context-sensitive substitutions so BrainFly can improvise while preserving form. Exact-role blocks such as portal obsidian and storage remain exact. Schematics retain progress and can be resumed later.

Commands: `!brain schematic list`, `!brain schematic NAME`, `!brain schematic status`, `!brain schematic stop`.

The minimal Nether portal uses 10 obsidian, a 2x3 interior and omitted corners, and can be ignited as part of the procedure. `!brain portal build` starts the complete operation.

## Home construction

The den/home builder now materializes the starter-house schematic instead of the old primitive shell. The duty layer can later add storage and other expansions and will revisit optional/aesthetic elements until the base meets its completion threshold.

## Elytra deployment

The temporal Elytra skill no longer relies on jump-spam alone. It repeatedly sends the actual Minecraft `start_fall_flying` entity action while airborne/falling, combined with forward+sprint and bounded jump pulses. Glide diagnostics report both pulse and packet counts.

## Vanilla control menu

The Fabric 9x6 chest menu remains vanilla-style and adds entries for:
- Gameplay Duties
- Schematic list
- Build starter house
- Build Nether portal
- Inventory capabilities
- Pickup status
- Stations
- Mine status

Responses remain in normal Minecraft chat.

## Verification

- **48/48** JavaScript regression tests PASS
- `tools/smoke_test.py` PASS
- N = 165122
- E = 10511038
- effective_edges = 5016094
- Minecraft motor spike delta = 192

New regression coverage includes inventory capability substitution, actual Elytra fall-flying packet emission, minimal portal geometry, gameplay-duty selection, persistent acquisition behavior, and schematic/home building behavior.

## Artifacts

- `BrainFly-0256-ONECLICK.zip` SHA-256 `907e641c38a66cdc63d59c6cad78fb770c07ce9fe1133a1a294405e5547b7472`
- `BrainFly-Control-Fabric-1.21.1-2026.09.20-0256.zip` SHA-256 `4525cd5c0d607af9156e28d4f77a8c49fc29143bad4cc908c7e04004e304faa5`
- `BrainWorkbench-Minecraft-2026.09.20-0256.zip` SHA-256 `6db91ecd60d209c6a1d3c4d1b4f666a75b1fba0b648fb909a57f1d856e52878c`
- `BrainFly-0256-CAPABILITIES-DUTIES-SCHEMATICS.patch` SHA-256 `868b57432b0bd5a84f18429dbc973ce168ea50030e4ccd1ee64a5fd6d9cf74b0`

The ONECLICK installer preserves config, learned memory, logs, snapshots and external knowledge packs, and backs up replaced runtime code.
