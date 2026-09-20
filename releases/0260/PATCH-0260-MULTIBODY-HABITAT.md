# 0260 implementation notes

## Root cause
0259 had four independent Mikoshi neural slots, but the normal Minecraft bridge was a singleton bound implicitly to Brain A. B-D therefore had no Mineflayer clients and could not appear in-game.

## Fix
- `BrainClient` accepts default request fields; body bridges inject `brain=A|B|C|D` into every Mikoshi request.
- `minecraft-bridge.mjs` supports environment overrides for brain ID, username, brain endpoint, tick rate and memory root.
- mutable Minecraft memory is isolated per secondary brain.
- `mikoshi/minecraft-bodies.mjs` supervises A-D bridge processes.
- Mikoshi captures each bridge summary from `io.frame` into `status.bodies`.
- secondary bodies do not bind the Fabric teacher UDP port and do not answer ordinary chat commands by default.

## State layout
- A: `minecraft/memory`
- B: `minecraft/memory/mikoshi/B`
- C: `minecraft/memory/mikoshi/C`
- D: `minecraft/memory/mikoshi/D`

## ECO
- A bridge: 20 Hz
- B/C/D bridges: 8 Hz
- the connectome still exists once inside the Mikoshi WASM process; these extra processes are Minecraft bodies, not duplicate brain simulations.

## Live use
If a 0259 primary Mikoshi and BrainFly are already running, install 0260 and run `JOIN-MIKOSHI-BODIES.cmd`. This deliberately starts only B-D and avoids duplicating the already-running A body.

For a clean next startup use `START-MIKOSHI-HABITAT.cmd`.
