"""Game-facing adapter to the BFB9000 R06.1 experimental Drosophila simulator.

The connectome anatomy may be measured. Sensory encoding, spike dynamics,
motor readout, innate preferences and learning are SYNTHETIC research code.
"""
from __future__ import annotations

import hashlib
import json
import math
import os
import random
from pathlib import Path

import numpy as np

from .sparse_runtime_r06 import MiniGraph, sha256_file
from .stability_r06 import StabilizedRuntime

SENSORS = ("food", "threat", "obstacle", "novelty", "hunger", "fatigue")
ACTIONS = ("forward", "left", "right", "interact", "retreat", "idle")
SCHEMA = "brainfry-flygame-r01"
MAX_OBS_BYTES = 4096


def demo_graph():
    """64-neuron FABRICATED miniature graph for smoke tests only."""
    n = 64
    targets = []
    weights = []
    ptr = [0]
    for i in range(n):
        for offset, weight in ((1, 20), (7, 9), (17, 6)):
            targets.append((i + offset) % n)
            weights.append(weight)
        ptr.append(len(targets))
    nt = [1 if i % 5 else 2 for i in range(n)]
    return MiniGraph(ptr, targets, weights, nt)


def validate_observation(value):
    if not isinstance(value, dict):
        raise ValueError("observation must be an object")
    if len(json.dumps(value, separators=(",", ":")).encode()) > MAX_OBS_BYTES:
        raise ValueError("observation too large")
    if set(value) - set(SENSORS) - {"reward"}:
        raise ValueError("unsupported sensor: " + ", ".join(sorted(set(value) - set(SENSORS) - {"reward"})))
    normalized = {}
    for key in (*SENSORS, "reward"):
        raw = value.get(key, 0.0)
        if isinstance(raw, bool) or not isinstance(raw, (int, float)):
            raise ValueError(key + " must be a finite number")
        x = float(raw)
        if not math.isfinite(x) or not -1.0 <= x <= 1.0:
            raise ValueError(key + " outside [-1,1]")
        normalized[key] = x
    return normalized


class FlyAgent:
    def __init__(self, graph, state_dir: Path | None = None, seed: int = 1, checkpoint_every: int = 10):
        if not 1 <= checkpoint_every <= 10000:
            raise ValueError("checkpoint_every outside 1..10000")
        self.graph = graph
        self.state_dir = Path(state_dir) if state_dir is not None else None
        self.seed = int(seed)
        self.checkpoint_every = checkpoint_every
        self.brain = StabilizedRuntime(graph, exponent=0.2)
        self.steps = 0
        self.q = [0.0] * len(ACTIONS)
        self.last_action = None
        self.last_uids = []
        if self.state_dir:
            self.state_dir.mkdir(parents=True, exist_ok=True)
            self._load()

    def _uids(self, name, count=24):
        digest = hashlib.sha256(("flygame|v1|" + name).encode()).digest()
        seed = int.from_bytes(digest[:8], "little")
        count = min(count, self.graph.n)
        return np.random.default_rng(seed).choice(self.graph.n, count, replace=False)

    def _load(self):
        path = self.state_dir / "state.json"
        if not path.exists():
            return
        profile = json.loads(path.read_text(encoding="utf-8"))
        if profile.get("schema") != SCHEMA or profile.get("graph_sha256") != self.graph.sha256:
            raise ValueError("state schema / graph mismatch; choose a new state directory")
        cp = profile.get("checkpoint")
        if not isinstance(cp, str) or not cp.startswith("neural-") or Path(cp).name != cp:
            raise ValueError("invalid checkpoint name")
        checkpoint = self.state_dir / cp
        if not checkpoint.is_file() or sha256_file(checkpoint) != profile.get("sha256"):
            raise ValueError("missing/corrupt checkpoint; refusing silent reset")
        brain = StabilizedRuntime.load(self.graph, checkpoint)
        steps = int(profile["steps"])
        q = profile["q"]
        uids = profile["last_uids"]
        last_action = profile["last_action"]
        if (steps < 0 or brain.ticks != 4 * steps or not isinstance(q, list)
                or len(q) != len(ACTIONS) or not all(isinstance(v, (int, float)) and math.isfinite(v) and -2 <= v <= 2 for v in q)
                or not isinstance(uids, list) or len(uids) > 200
                or any(not isinstance(i, int) or i < 0 or i >= brain.n for i in uids)
                or (last_action is not None and last_action not in range(len(ACTIONS)))):
            raise ValueError("invalid saved fly state")
        self.brain = brain
        self.steps = steps
        self.q = [float(v) for v in q]
        self.last_uids = uids
        self.last_action = last_action

    def checkpoint(self):
        if self.state_dir is None:
            return
        cp = "neural-%010d.npz" % self.steps
        target = self.state_dir / cp
        digest = self.brain.save(target)
        profile = {
            "schema": SCHEMA, "steps": self.steps, "graph_sha256": self.graph.sha256,
            "checkpoint": cp, "sha256": digest, "q": self.q,
            "last_action": self.last_action, "last_uids": self.last_uids,
        }
        tmp = self.state_dir / ".state.json.tmp"
        with tmp.open("w", encoding="utf-8") as out:
            json.dump(profile, out, separators=(",", ":"), sort_keys=True)
            out.flush()
            os.fsync(out.fileno())
        os.replace(tmp, self.state_dir / "state.json")
        # Only remove a prior checkpoint after the new pointer is durable.
        for old in self.state_dir.glob("neural-*.npz"):
            if old.name != cp:
                old.unlink()

    def step(self, observation):
        obs = validate_observation(observation)
        # Feedback is for the PREVIOUS game action, never for the current action.
        if self.last_action is not None:
            ai = self.last_action
            reward = obs["reward"]
            self.q[ai] = float(np.clip(self.q[ai] + 0.12 * (reward - self.q[ai]), -2, 2))
            if self.last_uids:
                self.brain.learn(self.last_uids, max(-0.2, min(0.2, reward * 0.08)))

        injected = []
        # Signs are encoded as distinct synthetic sensor populations.
        for key in SENSORS:
            value = obs[key]
            if abs(value) < 0.02:
                continue
            indices = self._uids(key + (":pos" if value >= 0 else ":neg"))
            self.brain.pulse(indices, min(4.5, 1.5 + abs(value) * 2.0))
            injected.extend(map(int, indices[:8]))
        self.brain.pulse(self._uids("self-motion", 4), 1.05)
        spikes = sum(self.brain.step() for _ in range(4))

        active = np.flatnonzero(self.brain.trace > 0.5)
        if len(active):
            groups = ((active.astype(np.uint64) * np.uint64(2654435761)) >> np.uint64(11)) % np.uint64(len(ACTIONS))
            scores = np.bincount(groups.astype(np.int32),
                                 weights=self.brain.trace[active], minlength=len(ACTIONS))
            proportions = scores / max(float(scores.sum()), 1.0)
        else:
            proportions = np.zeros(len(ACTIONS))
        f, t, o, n, h, tired = (obs[key] for key in SENSORS)
        innate = [
            0.45 + 0.4 * n - 0.9 * o - 1.1 * t,
            0.12 + 0.5 * o + 0.1 * n,
            0.12 + 0.5 * o + 0.1 * n,
            -0.2 + 1.2 * f + 0.4 * h - 0.7 * t,
            -0.8 + 1.8 * t,
            -0.25 + 1.0 * tired,
        ]
        logits = [innate[i] + self.q[i] + 5.0 * (float(proportions[i]) - 1.0 / len(ACTIONS))
                  for i in range(len(ACTIONS))]
        # A hard danger reflex is intentionally external to the connectome.
        if t >= 0.85:
            chosen = ACTIONS.index("retreat")
        else:
            chosen = max(range(len(ACTIONS)), key=lambda i: (logits[i], -i))
            # Deterministic, tiny exploration prevents a permanently locked policy.
            if random.Random(self.seed * 73 + self.steps * 577).random() < 0.03:
                chosen = random.Random(self.seed * 131 + self.steps * 811).randrange(len(ACTIONS))

        self.last_action = chosen
        self.last_uids = injected[:200]
        self.steps += 1
        if self.state_dir and self.steps % self.checkpoint_every == 0:
            self.checkpoint()
        return {
            "schema": SCHEMA, "action": ACTIONS[chosen], "step": self.steps,
            "neural_ticks": self.brain.ticks, "spikes": int(spikes),
            "neural_readout": [round(float(x), 5) for x in proportions],
            "source": self.graph.provenance,
            "motor_mapping": "synthetic",
        }
