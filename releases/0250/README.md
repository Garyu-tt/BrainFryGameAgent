# BrainFly 2026.09.20-0250 — Knowledge Graft + Temporal Motor

0250 keeps 0249's body schema, vanilla chest controls, exact teacher telemetry and Destiny-root campaign, then adds two missing capabilities: compact Minecraft knowledge priors and actions that persist/repeat over time.

## Knowledge Graft

- built-in campaign/world-model steps are indexed as semantic knowledge;
- abstract `food` / `secure_food` resolves to concrete embodied strategies: use/cook/craft/hunt/gather/search;
- external compact packs load from `minecraft/knowledge/packs/*.json`;
- Voyager is supported as a skill-name/description prior without bundling or executing Voyager skill code;
- local JSON/JSONL/TXT/MD exports (for example selected MineDojo text/wiki material) can be converted into bounded packs;
- `!brain knowledge reload` reloads packs without reinstalling the runtime.

## Temporal Motor

- exact teacher input transitions teach hold durations and repeat cadence;
- learned durations augment forward/backward/sprint/jump instead of treating every motor spike as a one-tick keypress;
- temporal primitives support HOLD, REPEAT, combined controls, and stop-on-condition;
- Elytra deployment repeatedly pulses jump while holding forward+sprint until gliding is detected, then can use a firework rocket.

The connectome remains the actor: these are priors and motor primitives, not a replacement policy.

## Verification

- 34/34 JavaScript regression tests PASS
- smoke test PASS
- Node syntax checks PASS for bridge, agency, supervisor, Knowledge Graft, Temporal Motor and Node importer
- Python importer compilation PASS
- Voyager-shaped importer check retained names/descriptions while dropping executable code

## Artifacts

- `BrainFly-0250-ONECLICK.zip` SHA-256: `e857b80dccc32cf14477f3bffb80acb4d3c22c9848eb414762e39eafbf049809`
- `BrainFly-Control-Fabric-1.21.1-2026.09.20-0250.zip` SHA-256: `82c509762bb754222d5bbd922f8ee6a50a3cc4c3c3d211c51ff959db660ceddb`
- `BrainWorkbench-Minecraft-2026.09.20-0250.zip` SHA-256: `5b886fe1f2bf05d6484882480f7df5a21ae12af29300955954dec3dc1d7b9b14`
- unified source patch SHA-256: `22a7ed4480c87629f710db69b7daba261dd7e8c2799336745f41aa6994a8ccc8`

The one-click installer preserves config, memory, logs, snapshots, and existing knowledge packs.
