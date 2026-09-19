# BrainFryGameAgent

Connectome-based Drosophila brain simulation embodied as a Minecraft player.

Current recovery point: **2026.09.19-0351 — ROBUST DIG**

This repository is the continuation point for the BrainFly / BrainWorkbench Minecraft agent.

## What is included

- Minecraft bridge and embodied agency
- bounded recipe planner and safe crafting
- spatial memory, home and den builder
- innate Minecraft progression drive
- advancement tracking
- inventory/equipment agency
- overnight autonomy supervisor
- native-spinal navigation fallback
- navigation lease/preemption
- robust digging primitive
- BrainWorkbench runtime source
- test suite and documentation
- Fabric Inventory Link source

## Deliberately excluded

No personal/live mutable state is committed:
- behavior / episodic / spatial / inventory memories
- live config
- logs
- snapshots
- node_modules
- large connectome runtime binaries

The source snapshot in `recovery/` is intended to reconstruct the exact 0351 source tree.

## Current known frontier

Navigation is alive through native-spinal fallback. The latest fix replaces stale-block direct digging with a bounded robust-dig primitive: refresh block from world, check reachability, reposition, raycast face, retry, and verify that the block actually disappeared.

## Minecraft chat commands

See `COMMANDS.md`.

## Runtime data

Large connectome data is intentionally not stored in GitHub. Preserve the existing local runtime/data files when updating source.
