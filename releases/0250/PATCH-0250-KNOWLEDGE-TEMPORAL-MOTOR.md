# 0250 implementation notes

## Runtime additions
- minecraft/knowledge-graft.mjs
- minecraft/temporal-motor.mjs
- minecraft/knowledge/knowledge-graft-1.21.1.json
- tools/knowledge_graft_import.mjs
- tools/knowledge_graft_import.py

## Existing runtime integration
- minecraft-bridge.mjs: KnowledgeGraft + TemporalMotor wiring, exact input_state timing, knowledge/motor/glide commands, abstract food goal aliases, learned key-hold latching.
- embodied-agency.mjs: executes knowledge actions, knowledge-driven food reserve, Elytra temporal launch.
- autonomy-supervisor.mjs: reports both capabilities and uses the upgraded secure_food executor.
- Fabric client: exact key state telemetry; vanilla chest menu gains knowledge/food/motor/Elytra entries.

## Dataset boundary
Voyager skill metadata can be fetched into compact local packs. The importer retains skill names/descriptions but deliberately discards executable Voyager skill code. MineDojo/MineRL/BASALT-style local text/JSON exports can be compacted through the same pack format. Raw corpora are intentionally kept outside runtime.

## State safety
0250 ONECLICK does not replace config.json, minecraft/memory, logs, snapshots, or existing minecraft/knowledge/packs.
