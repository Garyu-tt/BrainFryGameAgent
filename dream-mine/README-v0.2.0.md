# Dream Mine v0.2.0 — embedded adaptive neural controller (experimental)

**Minecraft 1.21.1 · NeoForge 21.1.257 · Java 21 · Fake Players 2.2.0.**

## What changed

v0.1.x had an eight-direction reward table, which visually looked like ordinary random wandering. The v0.2.0 mod now includes a **local Java neural runtime directly inside the JAR**:

- An eight-module, 96-microcolumn/module sparse recurrent **H01-inspired surrogate** (768 simulated microcolumns, 6,144 adjustable recurrent connections).
- Membrane integration, recurrent spike traces, eligibility traces, and reward-gated synaptic plasticity.
- A 12-feature sensory vector: nearby wood, logs and planks carried, player proximity, health, water, player distance, world interaction permissions, previous crafting, harvesting, and day/night.
- Five masked action intentions: `EXPLORE`, `GATHER_WOOD`, `CRAFT_PLANKS`, `BUILD_MARKER` (crafting table), and `OBSERVE_PLAYER`.
- A bounded physical action executor. Even when destructive actions are not allowed, Dream can identify, approach and examine trees. When permitted, it can seek a tree, break a log, pick up fresh drops nearby, turn an applicable log into planks, and place a crafting table.
- Personal NBT persistent neural weights and episode memory, and world-local shared learned neural readout and block knowledge used to initialize subsequent generations.
- Added `/dreammine actions on|off` (op only) and expanded `/dreammine brain` to show neural activity, learned weights, current plan, inventory resources, and accomplishments.
- Kept one automatic Dream, unlimited manual spawn eggs, custom names and skins, player-distance boundary, and explicit opt-in for aggregated diagnostics.

**Important limitation:** this is a real small recurrent adaptive network with motor grounding, **not** the original HCP800 (which has many more modules and a separate language/multimodal pipeline), **not** a port of the entire H01 connectome, and **not** a general intelligence. Goal planning is currently a fixed, bounded task chain with neural action selection. It has not yet been validated in gameplay.

## Installation

Install both JARs into the `mods/` directory (client and server for multiplayer):

1. `dreammine-0.2.0.jar`
2. `players-forge-1.21.1-2.2.0.jar`

Remove all previous `dreammine-0.1.x.jar` files first. Do not run multiple versions simultaneously. Java 21 required.

## First trial

Use a fresh disposable test world first. The default behavior **does not break or place blocks**.

- `/give @s dreammine:dream_spawn_egg` — summon additional Dream (unlimited; mass spawning can seriously affect TPS).
- `/dreammine brain` — inspect nearby Dream cognition. The vanilla AI goals should be zero; `embedded=H01-lite` should appear.
- `/dreammine actions on` — as OP, explicitly allow Dream to harvest natural logs and place crafting tables. **This modifies the world**, so only enable it in a test world at first. `/dreammine actions off` immediately disables new world modifications.
- Wait near trees and observe the chosen `goal`, `plan`, `steps`, `updates`, `changed`, `mined`, `crafted`, and `built` metrics. Forest terrain is best for the initial trial.
- `/dreammine actions off` — return to non-destructive mode.
- `/dreammine auto on|off`, `/dreammine spawn`, `/dreammine name Nick`, `/dreammine skin Account`, and `/dreammine telemetry yes|no|status` retain their existing behavior.

The skin resolver comes from Fake Players and may contact a remote skin service. Dream Mine's neural runtime itself requires no websites or external processes. Network transmission of **aggregate diagnostic statistics** requires both player consent and a separately configured administrator-owned HTTPS endpoint. Uploaded payloads contain count summaries, not raw neural weights, UUIDs, chat, or positions; a receiver may still see the game server IP.

## Evidence / verification

- Built with OpenJDK 21 using the installed Minecraft 1.21.1/NeoForge 21.1.257 and Fake Players 2.2.0 classpath.
- **1,589 Java checks passed**: 254 on local synapses, neural plasticity, NBT persistence and inheritance, plus 1,335 behavior/safety checks.
- A full Minecraft client or separate game server was **not launched** for this build. Runtime behavior, block permissions and actual long-horizon gameplay remain to be tested. The existing Mars world and running server were not altered.

**SHA-256:** `06e664f9bf08de9f5e35ad400b8ef08b7230392516fa4e5370d9a6b637760bd9`
