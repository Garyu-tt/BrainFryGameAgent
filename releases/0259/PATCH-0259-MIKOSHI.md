# 0259 implementation notes

## New files
- `mikoshi/mikoshi-runtime.mjs`
- `mikoshi/language-bus.mjs`
- `mikoshi/homeostasis.mjs`
- `tools/mikoshictl.mjs`
- `MIKOSHI.md`
- standalone ECO/FULL launchers
- primary-runtime switch and return scripts

## Core allocator
`runtime/core/lifwasm.js` now supports `independentWeights:true`.

One WebAssembly.Memory contains:
- one shared indptr array;
- one shared indices array;
- one shared sign array;
- one effective-weight array per brain;
- one live state block per brain.

This avoids duplicating graph topology while preserving independent plasticity.

## Language channel
- 32 MBON-derived outgoing raw symbols;
- 32 dedicated KC input populations for received symbols;
- sender identity KC populations;
- 8 hidden cue populations;
- 8 MBON listener action outputs;
- bounded exploratory symbol seeding before a convention exists.

## Compatibility
Mikoshi can speak enough of the normal Brain runtime protocol for the Minecraft bridge to use Brain A directly:
`hello`, `io.configure/frame/zero/get`, `reinforce`, `state.sample`, bridge events/state, snapshots, run/pause/speed/reset and basic neuromod status.

Normal BrainWorkbench snapshots can be imported into a Mikoshi brain. Brain A can also be exported back as `brain-workbench-state/v1`.

## State safety
The 0259 ONECLICK updates code and adds Mikoshi files only. It does not replace:
- `minecraft/config.json`
- `minecraft/memory`
- `logs`
- `snapshots`
- external knowledge packs
