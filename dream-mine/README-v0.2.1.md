# Dream Mine v0.2.1 — sensor integration stage 1

**Experimental build for Minecraft 1.21.1 · NeoForge 21.1.257 · Java 21 · Fake Players 2.2.0.**

The original BrainFly project has an extensive sensory and cognitive architecture. This build is an **incremental partial Java port**, **not a full HCP800 port** or a claim that Dream Mine now reproduces BrainFly's behavior.

## Implemented in the game's JVM

- 584 named registered sensory channels within a fixed-width 768-channel neural input space. **Registered ≠ proven observed**: channel values depend on the loaded world and individual implementations; some registry entries remain zero and have no physical observer yet.
- Voxel-relative body observations inspired by `spatial-vision.mjs`: underfoot, above, front foot/face/head/support, left, right; 13 material classes, unknown/unloaded handled separately, blocking, cliff and support checks.
- Retinotopic sampling modeled after `retinotopic-vision.mjs`: 11×7 viewing rays (77 cells), depth, structure, resource and danger signals; nine sector summaries, visibility and foveal outputs. A **hard limit of 900 block reads per observation** prevents uncontrolled scans; incomplete rays remain unknown. This is a bounded approximation of BrainFly's retina, not byte-for-byte identical.
- Proprioception inspired by `proprioception.mjs`: motion components, vertical velocity, yaw and pitch, contact with ground, movement effort, errors and landing impact.
- Body sensors such as health, under-water/head state and hazards that can actually be read from Fake Player bodies. **No fake hunger, oxygen reserve, speech or unimplemented perception.**
- Ongoing sensory updates even while navigating: 40 game ticks for 1–4 loaded Dreams, 80 for 5–16, 160 for 17–64, 240 above 64. A sensor update advances recurrent neural state without requiring a new movement decision.
- Five existing goal affordances remain (`EXPLORE`, `GATHER_WOOD`, `CRAFT_PLANKS`, `BUILD_MARKER`, `OBSERVE_PLAYER`). New spatial input now influences the learned readout and recurrent activity, but **the high-level original goal executive has not yet been transplanted**.
- Fixes the temporal reward assignment: action reward uses the sensor snapshot from the chosen action, not a later unrelated frame.
- Migrates learned 12-channel neural readouts and shared policies from v0.2.0 into their corresponding first 12 slots, preserving the previous training.
- Local persistence and shared world knowledge retained; private neural state stays within individual entity NBT. Opt-in aggregate diagnostics only; no new remote network requests for cognition.

## Original module coverage matrix

| BrainFly source | Current Dream Mine status |
|---|---|
| `minecraft/spatial-vision.mjs` | PARTIAL — real body voxel material+safe-space signals |
| `minecraft/retinotopic-vision.mjs` | PARTIAL — bounded 11×7 ray approximation |
| `minecraft/proprioception.mjs` | PARTIAL — ground, velocity, rotations and impact |
| `minecraft/world-observer.mjs` | PENDING — multi-scale world observation atlas |
| `minecraft/spatial-memory.mjs` | BASIC — prior local visited cells, not a full world graph |
| `minecraft/intention-field.mjs` / `intention-executive.mjs` | PENDING — semantic intent arbitration and commitment |
| `minecraft/semantic-cognition.mjs` | PENDING — rich concepts and affordance reasoning |
| `minecraft/homeostatic-executive.mjs` | PENDING — metabolism-like needs and prioritization |
| `minecraft/learned-locomotion.mjs` | PENDING — continuous neural movement control |
| `minecraft/embodied-agency.mjs` | PARTIAL — safe navigation, limited resource workflow |
| `human/hcp800-runtime.mjs` | NOT PORTED — 800 cortical modules, motor/sensory topological projection, memory interfaces |
| Language, episodic retrieval, self feedback, sleep, motivation, endocrine and chemical layers | NOT PORTED |

The goal is **not** to substitute a small network for those modules. Every capability must be ported, connected and verified against observable game events. Maintaining an unlimited egg-spawn feature means compute budgets must be designed around population size.

## Trial

1. Back up your test world.
2. Remove `dreammine-0.2.0.jar` and install **only** `dreammine-0.2.1.jar`; keep Fake Players 2.2.0.
3. Spawn/approach Dream, use `/dreammine brain`: expect `embedded=H01-sensory` and a growing `sensed=...frames` even while moving.
4. Run **`/dreammine senses`** near Dream: this displays channel count, number of retinal rays, known/unloaded rays, sampled voxels, reliability, obstacle, cliff, water pressure and motion-error indicators. Different terrain and movement should change these.
5. `/dreammine actions on` remains administrator-only and destructive; **leave it off** for perception tests.

Game TPS and tick cost have not been profiled on the user's full modpack yet. NeoForge bytecode compilation and 9,484 unit assertions passed; in-game runtime behavior has not yet been validated. If a command reports an unexpected sensor count, collect `latest.log` and the output of `/dreammine brain` and `/dreammine senses`.

**Java JAR SHA-256:** `37b7f53071a7d694d1f3b22f759db4f8f0705cc839fb3e4df7eb6859074895cc`
