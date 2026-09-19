# BrainFly 2026.09.19-0246 — craft fault / stale busy recovery

Live symptom after 0245:

- mining interaction lock worked and BrainFly broke multiple blocks consecutively;
- then the panel showed `busy=yes` with `last=craft_backend_requires_reset`;
- autonomy stopped because motor output intentionally yields while `agency.busy` is true.

Root causes:

1. repeated craft backend faults could enter a long-lived quarantine after the first automatic reset;
2. the stuck/progress watchdogs intentionally ignored `agency.busy`, so a stale busy flag could suppress all movement indefinitely.

0246 changes:

- track the age of `agency.busy`;
- recover stale non-interaction busy states by stopping digging, closing windows, cancelling navigation and clearing controls;
- run stale-busy recovery before normal progress/stuck watchdogs;
- repeated craft faults now use bounded retry/backoff instead of permanent quarantine;
- craft recovery resets the backend, resumes the paused goal and recompiles its plan;
- SafeCraftingBackend may auto-reset an expired fault before a new craft attempt;
- control status exports `busy_age_ms`, `craft_status`, and `craft_requires_reset`.

Verification:

- 28/28 JavaScript regression tests PASS;
- `tools/smoke_test.py` PASS;
- new regression tests cover stale busy recovery and a second craft fault after a previous reset.

Packaged runtime hotfix SHA-256:

`e8bb4281b54d1fd8333f3fa84515846e7db2d42a3b7c0c8c20a885368402b5f3`

Full snapshot SHA-256:

`288d51abae51a36f142341dd41c812b6ecc2efe5451c738c3640f3014e821807`
