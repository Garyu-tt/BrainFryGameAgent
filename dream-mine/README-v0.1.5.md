# Dream Mine v0.1.5 — NeoForge 1.21.1

**Experimental build. Compiles against NeoForge 21.1.257 and Fake Players 2.2.0; in-game runtime is not yet verified.**

## Install

Install both JARs to the `mods/` directory of Minecraft 1.21.1 (client and server):

- `dreammine-0.1.5.jar` (this build)
- `players-forge-1.21.1-2.2.0.jar` (required dependency)

Requires Java 21. The original neural HCP800 model is **not** embedded in this prototype: it currently uses a small adaptive policy model.

## Gameplay

- One Dream spawns automatically in the world. If killed, a new generation is scheduled after ~30 seconds, preserving shared learned scores.
- Manual spawns using `dreammine:dream_spawn_egg` are unlimited and never occupy the automatic spawn slot.
- Find the egg in the Creative inventory under Spawn Eggs, or use `/give @s dreammine:dream_spawn_egg`.
- Rename an egg in an anvil to `Nick@SkinAccount` to set a nickname and a Minecraft account whose skin should be loaded by Fake Players. If the skin service is unavailable, loading may fail.
- Stand within eight blocks of a manually spawned Dream you created and use `/dreammine name Nick` or `/dreammine skin Account`. Operators may edit others.
- Automatic and manual characters stay near the nearest player (nominal 100-block tether).
- Each Dream stores personal action outcomes; all share local learned action scores and discoveries within the same world.
- There is **no hard cap** on manually spawned mobs. The item tooltip warns that large numbers can cause severe lag; additional chat warnings appear at multiples of eight.
- Existing worlds from v0.1.2 may preserve one existing character as the automatic slot.

## Optional diagnostic data

On first login, the player is offered an opt-in choice for aggregated diagnostics. This is off by default. The mod never sends anything unless an administrator separately configures a valid HTTPS endpoint and bearer token in `config/dreammine-telemetry.properties`:

```properties
endpoint=https://your-host.example/v1/dreammine/diagnostics
token=YOUR_SERVER_SECRET_TOKEN
```

Payload is only aggregated counts of action attempts and successful movements. Player names, UUIDs, chat, world seeds and coordinates are not uploaded. The receiving service can still observe the **server's IP address**, so this is *de-identified*, not guaranteed mathematically anonymous. Consent may be changed with `/dreammine telemetry yes` and `/dreammine telemetry no`. Local mob memories remain local and are not uploaded as raw data.

## Commands

- `/dreammine status`
- `/dreammine spawn` (operator, manual spawn)
- `/dreammine auto on|off` (operator)
- `/dreammine name Nick` (nearest owned manually spawned Dream, within 8 blocks)
- `/dreammine skin Account` (nearest owned manually spawned Dream, within 8 blocks)
- `/dreammine telemetry yes|no|status`
- `/dreammine forgetme`

## Build verification

- Java 21 javac with the NeoForge/Fake Players installed server dependency classpath: success.
- 231 small pure-Java behavior checks: success.
- In-game loading, client rendering, persistence and game-play are **not confirmed**. Test in a disposable world before installing on an established server.

JAR SHA-256: `37c387345c9f6ec0d641208907f3972ba165943edcda94b4d38a3c9fed017ce7`
