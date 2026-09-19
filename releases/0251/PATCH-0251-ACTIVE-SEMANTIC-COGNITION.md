# 0251 implementation notes

## New runtime component
- `minecraft/semantic-cognition.mjs`

## Neural embedding
- `concept_food`
- `concept_safety`
- `concept_progress`
- `concept_tool`
- `concept_smelting`
- `concept_shelter`
- `concept_explore`
- `concept_recovery`
- `knowledge_00..knowledge_15`

All 24 channels are mapped to 16 previously-unused Kenyon cells each (384 total). The regression suite verifies they do not overlap previous Minecraft inputs, outputs or pulse bindings.

## Runtime integration
`minecraft-bridge.mjs` now:
1. observes the live scene/homeostatic state through SemanticCognition;
2. injects semantic concept and retrieved-knowledge features into `io.frame`;
3. adds weak affordance motor priors when a semantic candidate is salient;
4. feeds current connectome motor scores back into SemanticCognition as the neural vote;
5. schedules semantic execution as a bounded background task.

The first grounded executable semantic domains are food and safety. Progress/tool/smelting/shelter concepts already affect retrieval/neural context while the existing Destiny/planner remains authoritative for campaign progression.

## State
Semantic experience is stored separately in `minecraft/memory/semantic-cognition.json`; existing memory is not replaced by the installer.
