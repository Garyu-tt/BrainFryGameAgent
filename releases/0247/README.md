# BrainFly 2026.09.19-0247 — mined-drop pickup closure

Live video on 0246 exposed the next bottleneck after mining-lock and stale-busy recovery:

- `bridge=2026.09.19-0246` is active;
- the goal is `stone_pickaxe`;
- mining itself progresses;
- a mined cobblestone item remains on the stone floor while BrainFly stands on the adjacent ledge;
- the planner still sees cobblestone as missing and returns to mining/navigation.

Root cause:

1. raw acquisition ended at `dig -> waiting_pickup`; there was no canonical pickup action;
2. navigation's near-target shortcut was horizontal-only, so a drop one block below BrainFly could be reported as already reached.

0247 changes raw acquisition to:

`find -> approach -> dig -> detect matching item drop -> vertically-aware pickup -> verify inventory delta -> continue plan`

Additional behavior:

- an existing required drop within 10 blocks is collected before another block is mined;
- post-dig pickup waits for the expected item entity and verifies a real inventory increase;
- pickup navigation uses a strict vertical tolerance;
- pickup success returns ordinary `plan_progress`;
- failures are explicit: `pickup_drop_not_seen`, `pickup_not_collected`, or navigation failure.

Verification:

- 29/29 JavaScript regression tests PASS;
- `tools/smoke_test.py` PASS;
- new `test_mined_drop_collection.mjs` verifies that a loose cobblestone drop is collected before BrainFly may mine another stone block.

Runtime hotfix SHA-256:
`726a8fab80014e798b37f771c0bd2f2843bca655002bd75106a76bb2ca2dc613`

Full snapshot SHA-256:
`5a26e6fee612906b4943305b7d9a2f620e964c38eef511ed5a274e10d49b0096`
