# BrainFryGameAgent 🪰

**Experimental Drosophila connectome → game agent bridge**. A measured fly-CNS anatomical graph can power a synthetic spiking model and a bounded motor interface, so third-party games can provide sensors and receive actions.

[Руководство на русском](docs/GAME_INTEGRATION_RU.md) · [Architecture and scientific limitations](docs/PROVENANCE.md)

## Release status (2026-11-08)

This repository was previously just a September 19, 2026 Minecraft source recovery split into base64 fragments in `recovery/0351/`. That recovery is **preserved** for historical reasons, but is **not the current game entrypoint**.

The new `flygame/` directory contains:
- BFB9000 R06 sparse CPU neural runtime and R06.1 activity stabilization, adapted from our October 2026 research source;
- the Noosphere Fly-001 November 5 research snapshot with its personal writable memory overlay (isolated in `flygame/research/`);
- **new** game-agnostic sensor→brain→motor bridge, persistent training feedback and local-only HTTP/JSONL transport;
- test fixtures, an offline demo client, and an optional Mineflayer Minecraft adapter.

The original long-lived BrainFly R62 Minecraft fleet is **retired** and is not silently reactivated. Its October 12 migration archives contain private identities, histories and credentials; **they are not public assets and were not uploaded**. This release is a portable **fly research/game integration**, not a dump of that fleet, and not a tested autonomous playthrough.

## Quick start (Python 3.11+, CPU, no GPU required)

```bash
git clone https://github.com/Garyu-tt/BrainFryGameAgent.git
cd BrainFryGameAgent
python3 -m venv .venv
source .venv/bin/activate             # Windows: .venv\Scripts\activate
python -m pip install -r requirements.txt
python -m unittest discover -s tests -v

# Terminal 1 — FABRICATED 64-neuron test graph, not a real fly
python -m flygame server --demo --state-dir .flygame-state

# Terminal 2 — local dummy game client
python examples/arena_demo.py
```

**Attach your real measured fly graph** (compatible preprocessed BFB9000/BrainWorkbench format, not included):

```bash
python -m flygame server --graph /path/to/fly/runtime/data --state-dir .flygame-state-real
```

Required files: `meta.json`, `graph_w<minWeight>.bin`, `neurons.bin`. The verified original experiment used **165,122 neurons / 10,511,038 anatomical edges** and CSR SHA-256 `c34efca42d074786e8fb4fe4b6723d5b15aa4d69be17b0cf457321cb2ac6e030`. The loader validates metadata, CSR structure, dimensions and indices. This project does not distribute connectome data or claim to convert arbitrary FlyWire datasets. See [provenance](docs/PROVENANCE.md).

## Game protocol

```http
POST http://127.0.0.1:8765/v1/step
Content-Type: application/json

{"observation":{"food":0.7,"threat":0.1,"obstacle":0.0,"novelty":0.5,"hunger":0.4,"fatigue":0,"reward":0}}
```

Response example (values vary with the neural state):

```json
{"schema":"brainfry-flygame-r01","action":"forward","step":1,"neural_ticks":4,"spikes":0,"neural_readout":[0,0,0,0,0,0],"source":"synthetic_test_only","motor_mapping":"synthetic"}
```

The `reward` belongs to the **previous** action and is bounded to [-1, 1]. All sensor values also must be in [-1, 1]. Allowed commands are `forward`, `left`, `right`, `interact`, `retreat`, `idle`. The **game owns actual movement, collision, permissions and physics**.

`GET /health` exposes a lightweight local status. Use `python -m flygame jsonl --demo` for line-oriented stdin/stdout in engines without HTTP. HTTP refuses non-loopback binding; no remote-control or account authorization is included.

## Minecraft example

```bash
# Start FlyGame HTTP as above, then in your trusted test server:
npm install mineflayer
FLY_MC_HOST=127.0.0.1 FLY_MC_PORT=25565 node examples/minecraft_mineflayer.mjs
```

The example moves a separate Mineflayer test bot, detects nearby hostile entities and emits movement commands. It **does not** implement item detection, crafting, mine planning, block interactions or full Minecraft progression. It must not run on a production server without your own safeguards. See the [RU integration guide](docs/GAME_INTEGRATION_RU.md).

## What is scientifically real?

**Measured**: Fly anatomy used by the external input dataset. **Not measured/validated**: membrane dynamics, odor and game sensor coding, motor channels, Q-values, rewards, readout or any assertion of cognition/consciousness. The `--demo` fixture is entirely fabricated and labeled as such. This is an experimental engineering model, not a biological fly emulator.

## Layout

| Path | Purpose |
| --- | --- |
| `flygame/sparse_runtime_r06.py` | Read-only CSR connectome loader and bounded sparse dynamics |
| `flygame/stability_r06.py` | R06.1 stabilization layer |
| `flygame/agent.py` | new game sensorimotor learning adapter |
| `flygame/__main__.py` | JSONL/HTTP loopback server |
| `flygame/research/` | Noosphere-FLY-001 source snapshot (requires separate Noosphere SQLite) |
| `examples/` | generic local client and Mineflayer demo |
| `tests/` | executable smoke/recovery/validation checks |
| `recovery/0351/` | untouched September 2026 recovery history |

## Safety and data handling

No logins, API keys, game world saves, private memories, neurostate, source anatomy binaries or model weights are in this release. Persistent fly state stays in the chosen `--state-dir` and must remain private. For the research-only Noosphere resident, consult `flygame/research/README.md`.

License: no new license grant is asserted by this update; repository owner should explicitly choose a license before third-party reuse/distribution.
