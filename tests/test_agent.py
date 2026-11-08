import json
import subprocess
import sys
import tempfile
import unittest
import urllib.request
from pathlib import Path

from flygame.agent import ACTIONS, FlyAgent, demo_graph, validate_observation


class FlyAgentTests(unittest.TestCase):
    def test_graph_is_explicitly_synthetic(self):
        graph = demo_graph()
        self.assertEqual((graph.n, graph.e), (64, 192))
        self.assertEqual(graph.provenance, "synthetic_test_only")

    def test_agent_ticks_and_motor(self):
        agent = FlyAgent(demo_graph())
        response = agent.step({"food": 1, "novelty": .2})
        self.assertIn(response["action"], ACTIONS)
        self.assertEqual(response["neural_ticks"], 4)
        self.assertEqual(response["source"], "synthetic_test_only")

    def test_emergency_reflex(self):
        agent = FlyAgent(demo_graph())
        self.assertEqual(agent.step({"threat": 0.99})["action"], "retreat")

    def test_reject_unknown_sensor(self):
        with self.assertRaises(ValueError):
            validate_observation({"shell": "rm -rf /"})

    def test_reject_nan(self):
        with self.assertRaises(ValueError):
            validate_observation({"food": float("nan")})

    def test_reject_boolean(self):
        with self.assertRaises(ValueError):
            validate_observation({"food": True})

    def test_persistent_state_replay(self):
        with tempfile.TemporaryDirectory() as td:
            a = FlyAgent(demo_graph(), state_dir=Path(td), checkpoint_every=2)
            a.step({"food": 1})
            a.step({"reward": 0.7, "threat": 0.9})
            b = FlyAgent(demo_graph(), state_dir=Path(td), checkpoint_every=2)
            self.assertEqual(a.steps, b.steps)
            self.assertEqual(a.brain.ticks, b.brain.ticks)
            self.assertEqual(a.q, b.q)
            self.assertEqual(a.step({"novelty": 0.8}), b.step({"novelty": 0.8}))

    def test_corrupt_checkpoint_fail_closed(self):
        with tempfile.TemporaryDirectory() as td:
            a = FlyAgent(demo_graph(), state_dir=Path(td), checkpoint_every=1)
            a.step({"food": 0.5})
            state = json.loads((Path(td)/"state.json").read_text())
            (Path(td)/state["checkpoint"]).write_bytes(b"corruption")
            with self.assertRaises(ValueError):
                FlyAgent(demo_graph(), state_dir=Path(td))

    def test_jsonl_cli(self):
        result = subprocess.run([sys.executable, "-m", "flygame", "jsonl", "--demo"],
            input='{"observation":{"novelty":0.4}}\n', text=True,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=20, check=True)
        self.assertIn(json.loads(result.stdout)["action"], ACTIONS)

    def test_missing_data_does_not_fake_measured_connectome(self):
        result = subprocess.run([sys.executable, "-m", "flygame", "jsonl"],
            input='', text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=20)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("exactly one", result.stderr.lower())


if __name__ == "__main__":
    unittest.main()
