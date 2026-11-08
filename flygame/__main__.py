"""HTTP and JSON-lines interfaces for sandboxed game integrations."""
import argparse
import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

from .agent import FlyAgent, demo_graph
from .sparse_runtime_r06 import Graph


def build_agent(args):
    if args.demo == bool(args.graph):
        raise ValueError("Select exactly one: --demo OR --graph /path/to/data")
    graph = demo_graph() if args.demo else Graph(args.graph)
    return FlyAgent(graph, state_dir=Path(args.state_dir) if args.state_dir else None,
                    seed=args.seed, checkpoint_every=args.checkpoint_every)


def serve(agent, host, port):
    if host not in ("127.0.0.1", "::1", "localhost"):
        raise ValueError("HTTP interface is loopback-only; run a trusted local game adapter")
    class Handler(BaseHTTPRequestHandler):
        def respond(self, status, payload):
            body = json.dumps(payload, separators=(",", ":")).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            if self.path == "/health":
                self.respond(200, {"ok": True, "schema": "brainfry-flygame-r01",
                                   "steps": agent.steps, "neurons": agent.graph.n,
                                   "edges": agent.graph.e,
                                   "source": agent.graph.provenance})
            else:
                self.respond(404, {"error": "not found"})

        def do_POST(self):
            if self.path != "/v1/step":
                self.respond(404, {"error": "not found"})
                return
            try:
                length = int(self.headers.get("Content-Length", "-1"))
                if length < 2 or length > 4096:
                    raise ValueError("invalid body size (limit 4096)")
                request = json.loads(self.rfile.read(length))
                if not isinstance(request, dict) or set(request) != {"observation"}:
                    raise ValueError("expected {'observation': {...}}")
                result = agent.step(request["observation"])
                self.respond(200, result)
            except (ValueError, TypeError, KeyError, json.JSONDecodeError) as error:
                self.respond(400, {"error": str(error)})

        def log_message(self, *_args):
            pass

    httpd = HTTPServer((host, port), Handler)
    print(json.dumps({"listening": f"http://{host}:{httpd.server_port}",
                      "source": agent.graph.provenance}), flush=True)
    try:
        httpd.serve_forever(poll_interval=0.2)
    finally:
        agent.checkpoint()
        httpd.server_close()


def main():
    parser = argparse.ArgumentParser(description="BFB9000 R06.1 fly-game adapter")
    parser.add_argument("mode", choices=("server", "jsonl"))
    parser.add_argument("--graph", help="directory with measured meta.json, graph_w3.bin, neurons.bin")
    parser.add_argument("--demo", action="store_true", help="fabricated 64-neuron test fixture")
    parser.add_argument("--state-dir", help="private persistent directory, never commit")
    parser.add_argument("--seed", type=int, default=1)
    parser.add_argument("--checkpoint-every", type=int, default=10)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()
    try:
        agent = build_agent(args)
        if args.mode == "server":
            serve(agent, args.host, args.port)
        else:
            for line in sys.stdin:
                if not line.strip():
                    continue
                try:
                    data = json.loads(line)
                    print(json.dumps(agent.step(data["observation"]), separators=(",", ":")), flush=True)
                except (ValueError, TypeError, KeyError, json.JSONDecodeError) as exc:
                    print(json.dumps({"error": str(exc)}), flush=True)
            agent.checkpoint()
    except (ValueError, OSError, RuntimeError) as exc:
        parser.exit(2, "FlyGame: " + str(exc) + "\n")


if __name__ == "__main__":
    main()
