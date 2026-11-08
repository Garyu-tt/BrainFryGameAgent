# Noosphere Fly-001 — Nov 5 research source snapshot

This directory preserves the **source**, not the live identity/state, of the latest inspected Noosphere experimental graph inhabitant. The world SQLite is always opened read-only and the fly's own graph overlay is stored transactionally with its journal. It is not directly controlled by the generic game API.

Requires the same measured fly graph and a **separately provisioned** Noosphere-compatible SQLite world (tables `nodes` and `edges`). It cannot run against an empty DB.

```bash
export FLY_NOOSPHERE_DB=/path/to/knowledge.sqlite3
export FLY_GRAPH_DIR=/path/to/verified/fly/data
export FLY_STATE_DIR=/path/to/new/private/state
python -m flygame.research.fly001 --steps 1
```

Do **not** point `FLY_STATE_DIR` to the active resident's directory or start two writers. The identity string in this snapshot is part of the scientific artifact and must be changed in a fork for any independently deployed resident. See `docs/PROVENANCE.md` and the root README.

Unit test: `python -m flygame.research.test_fly_delta`.
