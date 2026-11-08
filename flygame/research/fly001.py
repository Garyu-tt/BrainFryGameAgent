#!/usr/bin/env python3
"""NOOSPHERE-FLY-001: bounded experimental inhabitant, NOT biological cognition.

Real measured Drosophila anatomical CSR from BFB9000 is read-only. All neuronal
dynamics, graph-to-sensory mapping, motor decoder, homeostasis and rewards
are artificial research hypotheses. Noosphere database is *strictly read-only*.
Only the fly's own isolated state directory is writable.
"""
import argparse
import fcntl
import hashlib
import json
import math
import os
import random
import shutil
import signal
import socket
import sqlite3
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
from . import fly_delta

ROOT = Path(__file__).resolve().parent
STATE = Path(os.environ.get("FLY_STATE_DIR", str(ROOT / "state")))
NOOSPHERE = Path(os.environ.get("FLY_NOOSPHERE_DB", ""))
from .. import sparse_runtime_r06 as fly_core
from ..stability_r06 import StabilizedRuntime

CKPT = STATE / "neural-state.npz"
PROFILE = STATE / "identity.json"
LOCAL_DB = STATE / "experiences.sqlite3"
SNAP_DIR = STATE / "checkpoints"
BACKUPS = STATE / "daily-backups"
EXPECTED_GRAPH = "c34efca42d074786e8fb4fe4b6723d5b15aa4d69be17b0cf457321cb2ac6e030"
ACTIONS = ("explore", "inspect", "rest", "jump")
STOP = False


def stop_handler(_sig, _frame):
    global STOP
    STOP = True


def watchdog_ping():
    # systemd detects stalls as well as crashes; no external network access.
    endpoint=os.environ.get("NOTIFY_SOCKET")
    if not endpoint:
        return
    try:
        addr=(chr(0)+endpoint[1:]) if endpoint.startswith("@") else endpoint
        with socket.socket(socket.AF_UNIX,socket.SOCK_DGRAM) as s:
            s.settimeout(0.5)
            s.connect(addr)
            s.sendall(b"WATCHDOG=1")
    except OSError:
        pass


def now():
    return datetime.now(timezone.utc).isoformat()


def atomic_json(path, obj):
    temp = path.with_name("." + path.name + ".tmp")
    with temp.open("w", encoding="utf8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2, sort_keys=True)
        f.flush()
        os.fsync(f.fileno())
    os.replace(temp, path)


def open_world():
    if not os.environ.get("FLY_NOOSPHERE_DB") or not NOOSPHERE.is_file():
        raise RuntimeError("Noosphere source not available")
    db = sqlite3.connect("file:" + str(NOOSPHERE) + "?mode=ro", uri=True, timeout=3)
    db.execute("pragma query_only=ON")
    db.execute("pragma busy_timeout=3000")
    return db


def open_memory():
    db = sqlite3.connect(str(LOCAL_DB), timeout=10)
    db.execute("pragma journal_mode=WAL")
    db.execute("pragma synchronous=FULL")
    db.executescript("""
    create table if not exists visits(
      node_id text primary key, kind text, label text, seen integer not null,
      first_seen text not null, last_seen text not null, total_reward real not null
    );
    create table if not exists episodes(
      step integer primary key, at_utc text not null, action text not null,
      from_node text not null, to_node text not null, perceived text not null,
      reward real not null, energy real not null, curiosity real not null,
      neural_spikes integer not null, neural_ticks integer not null,
      brain_readout text not null
    );
    create table if not exists context_policy(
      context text not null, action integer not null,
      q real not null, trials integer not null default 0,
      updated_at text not null,
      primary key (context,action),
      check(action between 0 and 3), check(q between -2.0 and 2.0)
    );
    create table if not exists agent_state(
      identity text primary key,
      profile_json text not null,
      checkpoint text not null,
      sha256 text not null,
      updated_at text not null
    );
    """)
    fly_delta.install(db)
    return db


def start_node(db):
    for row in db.execute("""
       select id from nodes where kind='concept'
       and (lower(label) like '%noosphere%' or lower(label) like '%ноосфер%')
       order by occurrences desc limit 1
    """):
        return row[0]
    row = db.execute("select id from nodes where id='agent:BrainFly'").fetchone()
    if row:
        return row[0]
    return db.execute("""
       select id from nodes where kind='concept'
       order by occurrences desc limit 1 offset 35
    """).fetchone()[0]


def node(db, node_id, memory=None):
    if memory is not None:
        personal = fly_delta.node(memory, node_id)
        if personal is not None:
            return personal
    return db.execute("select id,kind,label from nodes where id=?", (node_id,)).fetchone()


def options(db, node_id, memory=None):
    # Shared world remains strictly read-only; local additions are in memory DB.
    rows = db.execute("""
      select n.id,n.kind,n.label from edges e join nodes n on n.id=e.target
      where e.source=? and n.kind not in ('source','spatial_marker')
      union all
      select n.id,n.kind,n.label from edges e join nodes n on n.id=e.source
      where e.target=? and n.kind not in ('source','spatial_marker')
      limit 64
    """, (node_id, node_id)).fetchall()
    if memory is not None:
        for other in fly_delta.neighbor_ids(memory, node_id, limit=24):
            r = node(db, other, memory)
            if r is not None and r[1] not in ("source","spatial_marker"):
                rows.append(r)
    return [r for r in dict((r[0], r) for r in rows).values() if r[0] != node_id]


def initial_profile(world):
    return {
        "schema": "noosphere-fly-001-v1",
        "identity": "NOOSPHERE-FLY-001",
        "created_utc": now(),
        "position": start_node(world),
        "step": 0, "energy": 0.82, "curiosity": 0.92,
        "stress": 0.0, "stuck": 0, "q": [0.0, 0.0, 0.0, 0.0],
        "graph_hash": EXPECTED_GRAPH, "neural_ticks": 0,
        "body": "virtual graph-walking fly", "world": "Noosphere read-only",
        "cognition_claim": "experimental sensorimotor policy, not consciousness"
    }


def sense_uids(graph_n, current):
    # Stable neural 'odor' code for same node: experimental synthetic encoder.
    key = "|".join(str(z) for z in current)
    seed = int.from_bytes(hashlib.sha256(key.encode("utf8")).digest()[:8], "big")
    rng = np.random.default_rng(seed)
    return rng.choice(graph_n, size=144, replace=False)


def neural_readout(brain, stimuli):
    brain.pulse(stimuli, amplitude=2.05)
    spikes = 0
    for _ in range(4):
        spikes += brain.step()
    active = np.flatnonzero(brain.trace > 0.50)
    if not len(active):
        return spikes, [0.0] * 4
    groups = (active.astype(np.uint64) * np.uint64(2654435761) >> np.uint64(11)) % np.uint64(4)
    raw = np.bincount(groups.astype(np.int32),
                      weights=brain.trace[active], minlength=4)
    proportions = raw / max(1.0, raw.sum())
    # The four neural channels affect actual action selection, but do not
    # substitute for an unmeasured biological motor/sensory mapping.
    return spikes, [round(float(x), 5) for x in proportions]


def seen_count(memory, node_id):
    row = memory.execute("select seen from visits where node_id=?", (node_id,)).fetchone()
    return int(row[0]) if row else 0


def softmax_choice(logits, seed):
    m = max(logits)
    probs = [math.exp(max(-30, v-m)) for v in logits]
    rng = random.Random(seed)
    pick = rng.random() * sum(probs)
    for i, v in enumerate(probs):
        pick -= v
        if pick <= 0:
            return i
    return len(probs)-1


def checkpoint_path(step):
    if not isinstance(step,int) or not 0<=step<10**10:
        raise RuntimeError("invalid checkpoint generation")
    SNAP_DIR.mkdir(parents=True,exist_ok=True)
    return SNAP_DIR / ("neural-%010d.npz" % step)


def restore_committed(world,memory,graph):
    row=memory.execute(
        "select profile_json,checkpoint,sha256 from agent_state where identity=?",
        ("NOOSPHERE-FLY-001",)).fetchone()
    if row:
        p=json.loads(row[0])
        cp=Path(row[1])
        if cp != checkpoint_path(p["step"]) or not cp.is_file():
            raise RuntimeError("authoritative checkpoint path missing")
        if fly_core.sha256_file(cp)!=row[2]:
            raise RuntimeError("authoritative checkpoint hash mismatch")
        brain=StabilizedRuntime.load(graph,cp)
        if brain.ticks!=int(p["neural_ticks"]):
            raise RuntimeError("neural tick/state transaction mismatch")
        if p.get("graph_hash")!=graph.sha256:
            raise RuntimeError("fly anatomical provenance mismatch")
        last=memory.execute("select max(step) from episodes").fetchone()[0]
        if last is not None and last>p["step"]:
            raise RuntimeError("journal advanced beyond committed fly")
        # Self-heal stale convenience identity.json after postcommit power loss.
        if not PROFILE.exists() or json.loads(PROFILE.read_text())!=p:
            atomic_json(PROFILE,p)
        prune_checkpoints(p["step"])
        return p,brain
    if PROFILE.exists():
        p=json.loads(PROFILE.read_text())
        if p.get("schema")!="noosphere-fly-001-v1" or p.get("graph_hash")!=graph.sha256:
            raise RuntimeError("legacy identity mismatch")
        if not CKPT.is_file():
            raise RuntimeError("missing legacy neural checkpoint")
        brain=StabilizedRuntime.load(graph,CKPT)
        if brain.ticks!=p["neural_ticks"]:
            raise RuntimeError("legacy identity/neural mismatch; no data overwritten")
        last=memory.execute("select max(step) from episodes").fetchone()[0]
        if last!=p["step"]:
            raise RuntimeError("legacy journal mismatch")
        cp=checkpoint_path(p["step"])
        shutil.copy2(CKPT,cp)
    else:
        if CKPT.exists():
            raise RuntimeError("orphan legacy checkpoint")
        p=initial_profile(world)
        brain=StabilizedRuntime(graph,exponent=0.2)
        cp=checkpoint_path(0)
        brain.save(cp)
    with memory:
        memory.execute("""insert into agent_state
          (identity,profile_json,checkpoint,sha256,updated_at)
          values (?,?,?,?,?)""",
          (p["identity"],json.dumps(p,ensure_ascii=False,sort_keys=True),
           str(cp),fly_core.sha256_file(cp),now()))
    return p,brain


def prune_checkpoints(latest):
    # Never delete the current committed generation, or its immediate predecessor.
    safe={checkpoint_path(latest),checkpoint_path(max(0,latest-1))}
    for f in SNAP_DIR.glob("neural-*.npz"):
        if f not in safe:
            f.unlink()


def daily_backup(memory,p,cp):
    # One backed-up independent history checkpoint per UTC day; no archive truncation.
    day=now()[:10]
    target=BACKUPS/day
    if target.is_dir() and (target/"complete.json").exists():
        return
    BACKUPS.mkdir(parents=True,exist_ok=True)
    tmp=BACKUPS/("."+day+"-incomplete")
    if tmp.exists(): shutil.rmtree(tmp)
    tmp.mkdir(mode=0o700)
    try:
        backupdb=sqlite3.connect(str(tmp/"experiences.sqlite3"))
        try: memory.backup(backupdb)
        finally: backupdb.close()
        if sqlite3.connect(str(tmp/"experiences.sqlite3")).execute(
                "pragma integrity_check").fetchone()[0]!="ok":
            raise RuntimeError("daily backup database integrity failure")
        shutil.copy2(cp,tmp/"neural-state.npz")
        atomic_json(tmp/"identity.json",p)
        atomic_json(tmp/"complete.json",{
            "date":day,"step":p["step"],
            "neural_sha256":fly_core.sha256_file(tmp/"neural-state.npz"),
            "db_sha256":fly_core.sha256_file(tmp/"experiences.sqlite3")})
        os.replace(tmp,target)
    finally:
        if tmp.exists():shutil.rmtree(tmp)


def journey(world, memory, brain, p):
    old_pos = p["position"]
    current = node(world, old_pos, memory)
    if current is None:
        # A source node can be removed by the owner; escape to a known landmark.
        current = node(world, start_node(world), memory)
        if current is None:
            raise RuntimeError("Noosphere has no navigable landmarks")
        old_pos = current[0]
    candidates = options(world, old_pos, memory)
    # Context-conditioned learning (experimental abstraction; no semantics).
    unseen = any(seen_count(memory,x[0])==0 for x in candidates)
    energy_band = "low" if p["energy"]<0.3 else ("medium" if p["energy"]<0.65 else "high")
    loop_band = "trapped" if p["stuck"]>=3 else "free"
    context = f"{current[1]}|energy={energy_band}|novel={int(unseen)}|{loop_band}"
    contextual_q = [0.0]*4
    for ai,weight in memory.execute(
        "select action,q from context_policy where context=?",(context,)):
        contextual_q[ai] = float(weight)
    uids = sense_uids(brain.n, current)
    spikes, readout = neural_readout(brain, uids)
    energy, curiosity = p["energy"], p["curiosity"]
    q = p["q"]
    logits = [
        0.6 + 0.65*curiosity + 0.25*len(candidates)/64,
        -0.5 + 0.28*(1.0-curiosity),
        -1.4 + 3.2*max(0, 0.65-energy),
        -1.2 + 1.1*min(5, p["stuck"])
    ]
    logits = [base + q[i] + 1.05*contextual_q[i] +
              55.0*(readout[i]-0.25) for i,base in enumerate(logits)]
    if not candidates:
        logits[0] = -30
    if energy < 0.15:
        action_id = 2
    else:
        action_id = softmax_choice(logits, p["step"] * 7919 + 23)
    action = ACTIONS[action_id]
    destination = current
    reward = -0.01
    if action == "explore":
        seed = p["step"] * 577 + 37
        choices = [(c, 1.0/(1+seen_count(memory,c[0])) +
                    (0.10 if c[1]!=current[1] else 0) +
                    random.Random(seed+i).random()*0.09)
                   for i,c in enumerate(candidates)]
        destination = max(choices, key=lambda x:x[1])[0]
        previous_seen = seen_count(memory,destination[0])
        reward = 0.88 if previous_seen == 0 else -0.10
        p["energy"] = max(0.0, energy-0.055)
        p["curiosity"] = min(1.0,max(0.1, curiosity+(0.055 if previous_seen==0 else -0.075)))
        p["stuck"] = 0 if previous_seen==0 else p["stuck"]+1
    elif action == "inspect":
        reward = 0.035 if seen_count(memory,old_pos)<=1 else -0.03
        p["energy"] = max(0.0,energy-0.01)
        p["curiosity"] = max(0.1,curiosity-0.025)
        p["stuck"] += 1
    elif action == "rest":
        reward = 0.08 if energy < 0.7 else -0.09
        p["energy"] = min(1.0,energy+0.22)
        p["curiosity"] = min(1.0,curiosity+0.045)
        p["stuck"] = max(0,p["stuck"]-1)
    else:
        # 'Flight': search for a new island outside repeatedly traversed subgraph.
        total = world.execute("""
            select count(*) from nodes where kind='concept'
            and occurrences between 3 and 1200
        """).fetchone()[0]
        for attempt in range(12):
            offset = (p["step"]*1039+attempt*7879+101) % max(total,1)
            candidate = world.execute("""
                select id,kind,label from nodes where kind='concept'
                and occurrences between 3 and 1200 order by id limit 1 offset ?
            """,(offset,)).fetchone()
            if candidate and candidate[0]!=old_pos:
                destination = candidate
                if seen_count(memory,candidate[0])==0:
                    break
        reward = 0.08 if seen_count(memory,destination[0])==0 else -0.12
        p["energy"] = max(0.0,energy-0.08)
        p["curiosity"] = min(1.0,curiosity+0.09)
        p["stuck"] = 0
    if destination is None:
        destination = current
    p["stress"] = min(1.0,max(0.0,p["stress"]*0.95+(0.05 if p["stuck"]>3 else -0.01)))
    # Separate associative action-value plasticity (not biological STDP).
    future = max(q)
    td = reward + 0.6 * future - q[action_id]
    q[action_id] = max(-2.0,min(2.0,q[action_id]+0.13*td))
    # Learn a contextual preference without overwriting anatomical synapses.
    local_td = reward + 0.65*max(contextual_q) - contextual_q[action_id]
    learned_local = max(-2.0,min(2.0,contextual_q[action_id]+0.17*local_td))
    brain.learn(uids[:32], max(-0.2,min(0.2,reward*0.10)))
    p["position"] = destination[0]
    p["step"] += 1
    p["neural_ticks"] = brain.ticks
    event = {
        "step": p["step"], "at_utc": now(), "action": action,
        "from": old_pos, "to": destination[0],
        "perceived": current[2][:180],
        "destination_label": destination[2][:180],
        "reward": round(reward,4),
        "energy": round(p["energy"],4),
        "curiosity": round(p["curiosity"],4),
        "spikes": spikes, "neural_ticks": brain.ticks,
        "readout": readout, "context": context,
        "new_node": seen_count(memory,destination[0])==0,
        "prior_visits": seen_count(memory,destination[0])
    }
    # Commit protocol: write immutable checkpoint first, then ONE SQL transaction
    # for episode, visits and authoritative profile pointer. A crash before SQL
    # commit leaves an orphan file, never a half-committed identity.
    cp = checkpoint_path(p["step"])
    digest = brain.save(cp)
    if os.environ.get("FLY_INJECT_FAIL") == "precommit":
        os._exit(88) # isolated recovery test only
    with memory:
        event["personal_changes"] = fly_delta.apply_step(memory, event, destination)
        memory.execute("""
          insert into visits (node_id,kind,label,seen,first_seen,last_seen,total_reward)
          values (?,?,?,?,?,?,?)
          on conflict(node_id) do update set
          seen=seen+1, last_seen=excluded.last_seen,
          total_reward=total_reward+excluded.total_reward
        """,(destination[0],destination[1],destination[2][:512],1,
             event["at_utc"],event["at_utc"],reward))
        memory.execute("""
          insert into episodes
          (step,at_utc,action,from_node,to_node,perceived,reward,
           energy,curiosity,neural_spikes,neural_ticks,brain_readout)
          values (?,?,?,?,?,?,?,?,?,?,?,?)
        """,(event["step"],event["at_utc"],action,old_pos,destination[0],
             event["perceived"],reward,p["energy"],p["curiosity"],spikes,
             brain.ticks,json.dumps(readout)))
        memory.execute("""
          insert into context_policy(context,action,q,trials,updated_at)
          values (?,?,?,1,?)
          on conflict(context,action) do update set
          q=excluded.q,trials=trials+1,updated_at=excluded.updated_at
        """,(context,action_id,learned_local,event["at_utc"]))
        memory.execute("""
          insert into agent_state(identity,profile_json,checkpoint,sha256,updated_at)
          values (?,?,?,?,?)
          on conflict(identity) do update set
          profile_json=excluded.profile_json, checkpoint=excluded.checkpoint,
          sha256=excluded.sha256,updated_at=excluded.updated_at
        """,(p["identity"],json.dumps(p,ensure_ascii=False,sort_keys=True),
             str(cp),digest,event["at_utc"]))
    if os.environ.get("FLY_INJECT_FAIL") == "postcommit":
        os._exit(89) # verify recovery from stale human-facing identity.json
    atomic_json(PROFILE,p)  # non-authoritative convenience view
    prune_checkpoints(p["step"])
    daily_backup(memory,p,cp)
    return event


def run(args):
    STATE.mkdir(parents=True, exist_ok=True)
    os.chmod(STATE,0o700)
    signal.signal(signal.SIGTERM, stop_handler)
    signal.signal(signal.SIGINT, stop_handler)
    with (STATE/"fly.lock").open("a+") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX|fcntl.LOCK_NB)
        except BlockingIOError:
            raise RuntimeError("Another fly instance is already running")
        world = open_world()
        memory = open_memory()
        graph = fly_core.Graph()
        if graph.sha256 != EXPECTED_GRAPH or graph.n != 165122 or graph.e != 10511038:
            raise RuntimeError("Measured fly source provenance mismatch")
        p,brain=restore_committed(world,memory,graph)
        daily_backup(memory,p,checkpoint_path(p["step"]))
        print(json.dumps({"event":"startup","identity":p["identity"],
              "world":"Noosphere read-only + fly-owned writable overlay","fly_neurons":graph.n,
              "fly_synapses":graph.e,"resume_step":p["step"],
              "graph_sha256":graph.sha256,"brain":"BFB9000 R06.1 experimental"},
              ensure_ascii=False),flush=True)
        watchdog_ping()
        count = 0
        while not STOP and (args.steps is None or count < args.steps):
            try:
                event=journey(world,memory,brain,p)
                print(json.dumps(event,ensure_ascii=False),flush=True)
                count += 1
                watchdog_ping()
            except sqlite3.OperationalError as exc:
                print(json.dumps({"error":"Noosphere database temporarily unavailable",
                         "detail":str(exc)},ensure_ascii=False),flush=True)
                # Never continue with mutated in-memory brain if a transaction
                # failed; restart from last committed checkpoint.
                raise
            if args.daemon:
                for _ in range(args.interval):
                    if STOP:break
                    time.sleep(1)
                    if _ % 30 == 0:
                        watchdog_ping()
        print(json.dumps({"event":"stopped","ticks":brain.ticks,
                          "steps":p["step"],"persistent":True}),flush=True)


def main():
    ap=argparse.ArgumentParser(description="BFB9000 experimental Noosphere inhabitant")
    ap.add_argument("--steps",type=int,default=10,help="Number of bounded steps (default 10)")
    ap.add_argument("--daemon",action="store_true",help="Keep living, one step per interval")
    ap.add_argument("--interval",type=int,default=90,help="Seconds between daemon steps")
    a=ap.parse_args()
    if a.daemon:
        a.steps=None
    if a.steps is not None and not 1 <= a.steps <= 200:
        ap.error("steps outside 1..200")
    if not 15 <= a.interval <= 3600:
        ap.error("interval outside 15..3600 seconds")
    run(a)


if __name__=="__main__":
    main()
