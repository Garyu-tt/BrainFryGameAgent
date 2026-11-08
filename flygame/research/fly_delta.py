"""Personal, editable graph overlay for NOOSPHERE-FLY-001.

The owner Noosphere remains SQLite query_only; all fly edits live in its own
experiences.sqlite3 in the SAME atomic transaction as each episode/checkpoint.
Generated memory nodes are observations, never claimed facts.
"""
from __future__ import annotations
import hashlib
import json
import sqlite3

HOME = "fly:home"
MAX_NODES = 2048
MAX_EDGES = 8192
HOME_RECENT = 32


def install(db: sqlite3.Connection) -> None:
    db.executescript("""
    create table if not exists fly_nodes(
      id text primary key check(id like 'fly:%'),
      kind text not null check(kind in ('home','memory')),
      label text not null,
      description text not null default '',
      source_id text,
      first_step integer not null,
      last_step integer not null,
      revisions integer not null default 1,
      created_at text not null,
      updated_at text not null
    );
    create index if not exists fly_nodes_source_idx on fly_nodes(source_id);
    create table if not exists fly_edges(
      source text not null,
      target text not null,
      relation text not null check(relation in ('anchor','observed','stored','recalled_after')),
      weight integer not null default 1 check(weight between 1 and 255),
      created_at text not null,
      updated_at text not null,
      primary key(source,target,relation),
      check(source like 'fly:%' or target like 'fly:%')
    );
    create index if not exists fly_edges_target_idx on fly_edges(target);
    create table if not exists fly_edits(
      id integer primary key autoincrement,
      step integer not null,
      at_utc text not null,
      operation text not null,
      subject text not null,
      details text not null default ''
    );
    """)


def node(db: sqlite3.Connection, node_id: str):
    if not node_id.startswith("fly:"):
        return None
    return db.execute(
        "select id,kind,label from fly_nodes where id=?", (node_id,)
    ).fetchone()


def neighbor_ids(db: sqlite3.Connection, node_id: str, limit: int = 32):
    rows = db.execute("""
        select neighbor from (
          select target as neighbor,updated_at from fly_edges where source=?
          union all
          select source as neighbor,updated_at from fly_edges where target=?
        ) order by updated_at desc limit ?
    """, (node_id, node_id, limit * 2)).fetchall()
    out = []
    for (other,) in rows:
        if other != node_id and other not in out:
            out.append(other)
        if len(out) >= limit:
            break
    return out


def _edit(db, step, at, operation, subject, details=""):
    db.execute(
        "insert into fly_edits(step,at_utc,operation,subject,details) values (?,?,?,?,?)",
        (step, at, operation, subject, details[:240]),
    )


def _edge(db, source, target, relation, at, step):
    if source == target or not (source.startswith("fly:") or target.startswith("fly:")):
        return
    row = db.execute("select 1 from fly_edges where source=? and target=? and relation=?",
                     (source, target, relation)).fetchone()
    if row:
        db.execute("""update fly_edges set weight=min(weight+1,255),updated_at=?
                      where source=? and target=? and relation=?""",
                   (at, source, target, relation))
    elif db.execute("select count(*) from fly_edges").fetchone()[0] < MAX_EDGES:
        db.execute("""insert into fly_edges
                   (source,target,relation,weight,created_at,updated_at)
                   values (?,?,?,1,?,?)""", (source, target, relation, at, at))
        _edit(db, step, at, "link", source, relation + " -> " + target)


def _memory_id(source):
    return "fly:memory:" + hashlib.sha256(source.encode("utf-8")).hexdigest()[:24]


def apply_step(db: sqlite3.Connection, event: dict, destination: tuple) -> dict:
    """Run INSIDE the parent episode transaction. No writes to shared world."""
    step, at = int(event["step"]), event["at_utc"]
    destid, kind, label = destination
    stats = {"created": 0, "revised": 0, "links": 0}
    links_before = db.execute("select count(*) from fly_edges").fetchone()[0]
    new_home = not db.execute("select 1 from fly_nodes where id=?", (HOME,)).fetchone()

    if new_home:
        db.execute("""insert into fly_nodes
            (id,kind,label,description,source_id,first_step,last_step,revisions,created_at,updated_at)
            values (?, 'home', ?, ?, null, ?, ?, 1, ?, ?)""",
            (HOME, "Нора цифровой мухи", "Личное пространство и воспоминания NOOSPHERE-FLY-001",
             step, step, at, at))
        _edit(db, step, at, "create", HOME, "Personal graph initialized")
        stats["created"] += 1

    # Home anchor ensures the personal world can be reached from the shared world.
    anchor = event["from"] if not event["from"].startswith("fly:") else destid
    if new_home and anchor != HOME and not anchor.startswith("fly:"):
        _edge(db, HOME, anchor, "anchor", at, step)

    if not destid.startswith("fly:"):
        should_record = bool(event["new_node"]) or event["action"] == "inspect"
        memid = _memory_id(destid)
        prior = db.execute("select revisions from fly_nodes where id=?", (memid,)).fetchone()
        if should_record and (prior or db.execute("select count(*) from fly_nodes").fetchone()[0] < MAX_NODES):
            description = json.dumps({
                "type": "observed_not_verified_fact", "source_id": destid,
                "source_kind": kind, "source_label": str(label)[:180],
                "last_action": event["action"],
                "last_step": step, "previous_visits": event.get("prior_visits", 0),
            }, ensure_ascii=False, separators=(",", ":"))
            short_label = "След: " + str(label).replace("\n", " ")[:112]
            if prior:
                db.execute("""update fly_nodes set label=?,description=?,last_step=?,
                              revisions=revisions+1,updated_at=? where id=?""",
                           (short_label, description, step, at, memid))
                _edit(db, step, at, "revise", memid, "Updated observation")
                stats["revised"] += 1
            else:
                db.execute("""insert into fly_nodes
                   (id,kind,label,description,source_id,first_step,last_step,
                    revisions,created_at,updated_at)
                   values (?, 'memory', ?, ?, ?, ?, ?, 1, ?, ?)""",
                   (memid, short_label, description, destid, step, step, at, at))
                _edit(db, step, at, "create", memid, "Remembered " + kind)
                stats["created"] += 1
            _edge(db, memid, destid, "observed", at, step)
            _edge(db, HOME, memid, "stored", at, step)
            # Home is a small navigable index, not an infinite list of links.
            old = db.execute("""select target from fly_edges
                 where source=? and relation='stored'
                 order by updated_at desc limit -1 offset ?""",
                 (HOME, HOME_RECENT)).fetchall()
            for (target,) in old:
                db.execute("delete from fly_edges where source=? and target=? and relation='stored'",
                           (HOME, target))
                _edit(db, step, at, "unlink", HOME, "Unpinned " + target)
    elif event["action"] == "inspect" and destid != HOME:
        db.execute("""update fly_nodes
                      set description=substr(description,1,380)||' [revisited at step '||?||']',
                      last_step=?,revisions=revisions+1,updated_at=?
                      where id=?""", (step, step, at, destid))
        if db.execute("select changes()").fetchone()[0]:
            _edit(db, step, at, "revise", destid, "Inspected own memory")
            stats["revised"] += 1

    # Build an association based on observed travel, not a factual assertion.
    if event["action"] == "explore" and event["from"] != destid:
        previous = _memory_id(event["from"])
        current = _memory_id(destid)
        both = db.execute("select count(*) from fly_nodes where id in (?,?)",
                          (previous, current)).fetchone()[0]
        if both == 2:
            _edge(db, previous, current, "recalled_after", at, step)
    stats["links"] = db.execute("select count(*) from fly_edges").fetchone()[0] - links_before
    return stats


def stats(db: sqlite3.Connection) -> dict:
    return {
        "personal_nodes": db.execute("select count(*) from fly_nodes").fetchone()[0],
        "personal_edges": db.execute("select count(*) from fly_edges").fetchone()[0],
        "personal_edits": db.execute("select count(*) from fly_edits").fetchone()[0],
        "created_memories": db.execute("select count(*) from fly_nodes where kind='memory'").fetchone()[0],
        "revisions": db.execute("select coalesce(sum(revisions)-count(*),0) from fly_nodes").fetchone()[0]
    }


if __name__ == "__main__":
    import sys
    db = sqlite3.connect(f"file:{sys.argv[1]}?mode=ro", uri=True)
    print(json.dumps(stats(db), ensure_ascii=False, indent=2))
