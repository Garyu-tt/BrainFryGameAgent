"""Local demo client; requires: python -m flygame server --demo"""
import json
import urllib.request

URL = "http://127.0.0.1:8765/v1/step"
SCENES = [
    {"novelty": .7}, {"food": .95, "hunger": .8}, {"obstacle": .9},
    {"threat": .98}, {"fatigue": .8}, {"reward": .6, "novelty": .4},
]
for observation in SCENES:
    body = json.dumps({"observation": observation}).encode()
    request = urllib.request.Request(URL, data=body,
        headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(request, timeout=8) as response:
        result = json.load(response)
    print(f"scene={observation} => {result['action']} | spikes={result['spikes']}")
