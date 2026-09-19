# BrainFly 2026.09.20-0251 — Active Semantic Cognition

0250 stored and retrieved Minecraft knowledge but mostly waited for an explicit caller. 0251 closes the live loop:

`world/homeostasis/Destiny -> needs -> concepts -> knowledge retrieval -> connectome vote -> embodied skill -> result -> experience`

## What changes

- live needs are derived continuously from food reserve/hunger, health/hostility, Destiny progress and failure state;
- semantic concepts activate automatically: food, safety, progress, tool, smelting, shelter, explore, recovery;
- retrieved Knowledge Graft documents/skill priors are projected into dedicated connectome inputs;
- 24 semantic inputs use 384 previously-unused Kenyon cells, with no overlap with earlier Minecraft bindings;
- semantic candidates have affordance classes such as `use`, `attack`, `move`, `escape`;
- the connectome's current motor outputs provide the neural vote for the candidate;
- stable, sufficiently salient candidates may call existing embodied skills; emergency survival needs may bypass a weak vote;
- success/failure experience is persisted in `minecraft/memory/semantic-cognition.json`;
- commands: `!brain cognition`, `cognition candidates`, `cognition on`, `cognition off`;
- vanilla chest menu gains `Активное мышление`.

The connectome remains the actor. Knowledge is a semantic prior and candidate generator, not a replacement scripted policy.

## Verification

- 36/36 JavaScript regression tests PASS
- `tools/smoke_test.py` PASS
- N = 165122 neurons
- E = 10511038 structural edges
- effective_edges = 5016094
- Minecraft motor spike delta during smoke test = 196
- semantic regression verifies that hunger activates food knowledge, knowledge reaches neural channels, a connectome `use` vote stabilizes the candidate, and the actual embodied Knowledge Graft action is dispatched.

## Artifacts

- `BrainFly-0251-ONECLICK.zip` SHA-256: `3484eecf3481e399ebd1950e3289fa7baf7c94bace30608bc6d8fb4924fdb166`
- `BrainFly-Control-Fabric-1.21.1-2026.09.20-0251.zip` SHA-256: `62a0380172e53634318aaeb308158fb4f026803a5dd7b08fc06948c828fc1c46`
- `BrainWorkbench-Minecraft-2026.09.20-0251.zip` SHA-256: `a3655c436edcbe6122f6f427b2afa4e661db89763c01799fb4a16e58f3d12f88`
- `BrainFly-0251-ACTIVE-SEMANTIC.patch` SHA-256: `1aff69badd6e438b28a063eaaec3bfb1626eca25f1aa0137fdac07d36caf1ae9`

The one-click installer preserves config, memory, logs, snapshots and existing knowledge packs.
