# 0252 implementation notes

New component:
- `minecraft/infrastructure-memory.mjs`

Core integration:
- `embodied-agency.mjs`: station reuse/reclaim, station-aware plan flattening, completed-goal short circuit, persistent mine, resource strategies, pillaring, step jump and bounded navigation-loop detection.
- `innate-world-model.mjs`: composite completion predicates and reusable station predicates; background campaign rows are not hard progression gates.
- `game-rules-1.21.1.json`: crafting table/furnace are infrastructure; food/home are background.
- `autonomy-supervisor.mjs`: loop/resource remedies and checkpoint coverage for infrastructure/semantic/motor memory.
- Knowledge Graft bundle adds infrastructure, mining, ore provenance, pillaring and anti-loop documents.

Runtime diagnostics:
- `!brain mine`
- `!brain mine raw_iron`
- `!brain stations`
- `!brain pillar 1`

No Fabric protocol change is required; the 0251 control mod remains compatible.
