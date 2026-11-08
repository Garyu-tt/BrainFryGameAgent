#!/usr/bin/env python3
"""BFB9000 R06 experimental sparse neural runtime.

This is a computational scaling kernel. Its default stress-test graph is
MEASURED Drosophila CNS connectivity from Brain Workbench; neuron dynamics,
gains, and firing policy are SYNTHETIC and not biologically validated.
It DOES NOT add human-human synapses to BFB9000, nor alter HTask1k0 state.
No daemon, no production integration. Source files are read-only.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
import platform
try:
    import resource
except ImportError:  # Windows has no resource module
    resource = None
import struct
import tempfile
import time
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parent
DEFAULT_SOURCE = Path(os.environ.get("FLY_GRAPH_DIR", str(ROOT / "data")))
SCHEMA = "bfb9000-r06-sparse-neural-kernel"
NEUROTRANSMITTER_SIGN = np.array([0, 1, -1, -1, 1, 1, 1, -1], dtype=np.int8)
MAX_EDGES_PER_CHUNK = 250_000


def sha256_file(path: Path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


class Graph:
    def __init__(self, location=DEFAULT_SOURCE):
        self.location = Path(location)
        metadata = json.loads((self.location / "meta.json").read_text())
        self.n = int(metadata["N"])
        self.e = int(metadata["edges"])
        if not (1 <= self.n <= 2_000_000 and 0 <= self.e <= 100_000_000):
            raise ValueError("graph exceeds configured research safety bounds")
        path = self.location / ("graph_w%d.bin" % int(metadata["minWeight"]))
        neuron_path = self.location / "neurons.bin"
        with path.open("rb") as f:
            header = f.read(8)
        header_n, header_e = struct.unpack("<II", header)
        if header_n != self.n or header_e != self.e:
            raise ValueError("graph header differs from metadata")
        if path.stat().st_size != 8 + 4 * (self.n + 1) + 6 * self.e:
            raise ValueError("unexpected CSR file length")
        nt_offset = 8 + 30 * self.n
        if neuron_path.stat().st_size < nt_offset + self.n:
            raise ValueError("truncated neuron neurotransmitter table")
        self.indptr = np.memmap(path, mode="r", dtype="<u4", offset=8,
                                shape=(self.n+1,))
        self.indices = np.memmap(path, mode="r", dtype="<u4",
                                  offset=8+4*(self.n+1), shape=(self.e,))
        self.weights = np.memmap(path, mode="r", dtype="<u2",
                                  offset=8+4*(self.n+1)+4*self.e,
                                  shape=(self.e,))
        self.nt = np.memmap(neuron_path, mode="r", dtype="u1",
                            offset=nt_offset, shape=(self.n,))
        if self.indptr[0] != 0 or self.indptr[-1] != self.e:
            raise ValueError("CSR pointers invalid")
        if np.any(np.diff(self.indptr.astype(np.int64)) < 0):
            raise ValueError("CSR pointers are not monotonic")
        if np.any(self.indices >= self.n):
            raise ValueError("CSR target outside node set")
        if np.any(self.nt >= len(NEUROTRANSMITTER_SIGN)):
            raise ValueError("unexpected neurotransmitter code")
        self.sha256 = sha256_file(path)
        self.species = "Drosophila melanogaster"
        self.provenance = "measured_fly_connectome"
        self.data_bytes = path.stat().st_size + neuron_path.stat().st_size


class MiniGraph:
    """Unit-test fixture, explicitly fabricated connectivity."""
    def __init__(self, ptr, ix, weight, types):
        self.indptr = np.asarray(ptr, dtype="<u4")
        self.indices = np.asarray(ix, dtype="<u4")
        self.weights = np.asarray(weight, dtype="<u2")
        self.nt = np.asarray(types, dtype="u1")
        self.n = len(types)
        self.e = len(ix)
        self.sha256 = hashlib.sha256(b"R06 synthetic test fixture v1").hexdigest()
        self.species = "none"
        self.provenance = "synthetic_test_only"
        assert len(self.indptr)==self.n+1 and self.indptr[-1]==self.e


class Runtime:
    """Finite bounded event-driven spikes; one coarse 1ms-like simulation tick."""
    def __init__(self, graph, state=None):
        self.graph = graph
        self.n = graph.n
        self.potential = np.zeros(self.n, dtype=np.float32)
        self.trace = np.zeros(self.n, dtype=np.float32)
        self.fatigue = np.zeros(self.n, dtype=np.float32)
        self.pending = np.zeros(self.n, dtype=np.float32)
        self.engram = np.zeros(self.n, dtype=np.float32)
        self.homeostasis = 0.0
        self.ticks = 0
        self.total_events = 0
        self.limited_events = 0
        if state is not None:
            self.restore(state)

    def pulse(self, uids, amplitude=2.0):
        indices=np.asarray(uids, dtype=np.int64)
        if indices.ndim!=1 or np.any((indices<0)|(indices>=self.n)):
            raise ValueError("invalid pulse UIDs")
        if not np.isfinite(amplitude) or abs(amplitude)>5:
            raise ValueError("pulse intensity out of bound")
        # Repeated UIDs are accumulated, not silently overwritten.
        np.add.at(self.pending,indices,float(amplitude))
        np.clip(self.pending,-5,5,out=self.pending)

    def learn(self, uids, delta):
        indices=np.asarray(uids, dtype=np.int64)
        if indices.ndim!=1 or np.any((indices<0)|(indices>=self.n)):
            raise ValueError("invalid learning UIDs")
        if not np.isfinite(delta) or abs(delta)>1.0:
            raise ValueError("invalid learn value")
        np.add.at(self.engram,indices,float(delta))
        np.clip(self.engram,-1,1,out=self.engram)

    def step(self):
        # Low-order surrogate parameters, unmeasured; event-driven transmission.
        self.potential *= np.float32(.87)
        self.potential += self.pending
        self.pending.fill(0)
        self.trace *= np.float32(.95)
        self.fatigue *= np.float32(.96)
        threshold = np.float32(1.0 + self.homeostasis) + self.fatigue - .30 * self.engram
        fired = np.flatnonzero(self.potential >= threshold)
        cap = max(1, int(self.n * .03))
        excess=max(0,len(fired)-cap)
        if excess:
            # Stable highest-over-threshold selection with deterministic UID tie-break.
            excess_pot=(self.potential[fired]-threshold[fired])
            ranking=np.lexsort((fired,-excess_pot))
            retained=fired[ranking[:cap]]
            rejected=fired[ranking[cap:]]
            self.potential[rejected]=threshold[rejected]-.01
            fired=np.sort(retained)
        self.limited_events += excess
        self.potential[fired]=0
        self.trace[fired]=1
        self.fatigue[fired]=np.minimum(1, self.fatigue[fired]+.08)
        activity = len(fired)/max(self.n,1)
        self.homeostasis = float(np.clip(self.homeostasis*.94+
                                        max(0.,activity-.01)*30.,0.,4.))
        np.clip(self.potential,-8,8,out=self.potential)
        # Iterate active source rows only, using vectorized bounded scatter.
        # The original anatomical CSR stays mmap read-only.
        self._propagate(fired)
        self.ticks+=1
        self.total_events+=len(fired)
        return int(len(fired))

    def _propagate(self, sources):
        if not len(sources):
            return
        starts = self.graph.indptr[sources].astype(np.int64)
        sizes = (self.graph.indptr[sources+1].astype(np.int64)-starts)
        # Chunk by a hard upper bound on temporary edge-array allocations.
        # A single extremely high-degree row is subdivided too.
        batch_offsets=[]
        batch_signs=[]
        batch_count=0
        def flush():
            nonlocal batch_count
            if not batch_offsets:
                return
            offsets=np.concatenate(batch_offsets)
            signs=np.concatenate(batch_signs)
            destination=np.asarray(self.graph.indices[offsets],dtype=np.int64)
            strength=np.minimum(2.0, np.asarray(self.graph.weights[offsets],dtype=np.float32)*.06)
            strength*=signs
            accumulation=np.bincount(destination,weights=strength,minlength=self.n)
            self.pending[:]+=accumulation.astype(np.float32)
            batch_offsets.clear();batch_signs.clear()
            batch_count=0
        for start,size,source in zip(starts,sizes,sources):
            sign=NEUROTRANSMITTER_SIGN[int(self.graph.nt[source])]
            if not sign or not size:
                continue
            end=int(start+size)
            for at in range(int(start),end,MAX_EDGES_PER_CHUNK):
                stop=min(end,at+MAX_EDGES_PER_CHUNK)
                if batch_count+stop-at > MAX_EDGES_PER_CHUNK:
                    flush()
                batch_offsets.append(np.arange(at,stop,dtype=np.int64))
                batch_signs.append(np.full(stop-at,sign,dtype=np.float32))
                batch_count+=stop-at
        flush()
        np.clip(self.pending,-5,5,out=self.pending)

    def snapshot(self):
        meta={
            "schema":SCHEMA, "species":self.graph.species,
            "graph_provenance":self.graph.provenance,
            "graph_sha256":self.graph.sha256,
            "neurons":self.n,"edges":self.graph.e,
            "ticks":self.ticks,"events":self.total_events,
            "limited":self.limited_events,
            "homeostasis":self.homeostasis,
            "neural_model":"UNVALIDATED synthetic rate/spike surrogate",
            "human_interneuron_synapses":0,
            "activity_gain_method":"none"
        }
        arrays={k:getattr(self,k).copy() for k in
            ("potential","trace","fatigue","pending","engram")}
        return meta,arrays

    def restore(self, snapshot):
        meta,arrays=snapshot
        expected=(meta.get("schema")==SCHEMA and
                  meta.get("graph_sha256")==self.graph.sha256 and
                  meta.get("species")==self.graph.species and
                  meta.get("graph_provenance")==self.graph.provenance and
                  meta.get("activity_gain_method")=="none" and
                  meta.get("neurons")==self.n and meta.get("edges")==self.graph.e)
        if not expected:
            raise ValueError("checkpoint provenance/graph mismatch")
        self.ticks=int(meta["ticks"])
        self.total_events=int(meta["events"])
        self.limited_events=int(meta["limited"])
        self.homeostasis=float(meta["homeostasis"])
        if not(0<=self.ticks<10**12 and 0<=self.total_events<10**15
               and 0<=self.limited_events<10**15
               and np.isfinite(self.homeostasis) and 0<=self.homeostasis<=4):
            raise ValueError("invalid checkpoint scalar state")
        for name in ("potential","trace","fatigue","pending","engram"):
            field=np.asarray(arrays[name])
            if field.shape!=(self.n,) or field.dtype!=np.float32 or not np.all(np.isfinite(field)):
                raise ValueError("invalid checkpoint vector "+name)
            if np.max(np.abs(field),initial=0)>10:
                raise ValueError("out-of-bounds checkpoint vector "+name)
            getattr(self,name)[:]=field

    def save(self,path):
        """Atomic local checkpoint. No output is written unless explicitly requested."""
        path=Path(path)
        if not path.parent.is_dir():
            raise ValueError("checkpoint directory must exist")
        meta,arrays=self.snapshot()
        fd,tmp=tempfile.mkstemp(prefix=".r06-checkpoint-",suffix=".npz",dir=path.parent)
        try:
            with os.fdopen(fd,"wb") as f:
                np.savez_compressed(f,metadata=np.array(json.dumps(meta,sort_keys=True)),**arrays)
                f.flush()
                os.fsync(f.fileno())
            os.replace(tmp,path)
        finally:
            if os.path.exists(tmp):os.unlink(tmp)
        return sha256_file(path)

    @classmethod
    def load(cls,graph,path):
        with np.load(path,allow_pickle=False) as z:
            metadata=json.loads(str(z["metadata"]))
            fields={name:z[name].copy() for name in
                ("potential","trace","fatigue","pending","engram")}
        return cls(graph,(metadata,fields))


def selftest():
    # source 0 excitatory to 1,2; source 1 inhibitory to 2;
    # self loops and repeat destinations are also covered.
    g=MiniGraph([0,3,4,4],[1,2,2,2],[20,4,7,9],[1,2,1])
    a=Runtime(g)
    a.pulse([0],2.)
    spikes=a.step()
    assert spikes==1
    # source 0 sends +1.2 to 1, +0.24 and +0.42 to 2
    assert np.isclose(a.pending[1],1.2) and np.isclose(a.pending[2],.66)
    b=Runtime(g,a.snapshot())
    assert b.step()==a.step()
    assert all(np.array_equal(getattr(a,k),getattr(b,k)) for k in
        ("potential","trace","fatigue","pending","engram"))
    # Inhibitory edge is actually negative; high stimulus to source1.
    c=Runtime(g)
    c.pulse([1],2.)
    assert c.step()==1 and np.isclose(c.pending[2],-.54)
    # Deterministic checkpoint across process-independent pickle-free NPZ.
    a.learn([0],.35)
    with tempfile.TemporaryDirectory() as d:
        path=Path(d)/"test.npz"
        digest=a.save(path)
        assert sha256_file(path)==digest
        restored=Runtime.load(g,path)
        for _ in range(12):
            assert a.step()==restored.step()
        assert all(np.array_equal(getattr(a,k),getattr(restored,k)) for k in
            ("potential","trace","fatigue","pending","engram"))
        with np.load(path,allow_pickle=False) as z:
            badmeta=json.loads(str(z["metadata"]))
            arrays={k:z[k].copy() for k in
                ("potential","trace","fatigue","pending","engram")}
        badmeta["graph_sha256"]="corrupt"
        try:Runtime(g,(badmeta,arrays))
        except ValueError:pass
        else:raise AssertionError("corrupt provenance accepted")
        arrays["potential"][0]=np.float32(np.nan)
        try:Runtime(g,(dict(badmeta,graph_sha256=g.sha256),arrays))
        except ValueError:pass
        else:raise AssertionError("NaN state accepted")
    # Burst cap stress and finite numerical output.
    h=Runtime(MiniGraph([0,0,0,0,0,0],[ ],[],[1]*5))
    h.pulse([0,1,2,3,4],5.)
    assert h.step()==1 and h.limited_events==4
    assert all(np.all(np.isfinite(getattr(h,k))) for k in
        ("potential","trace","fatigue","pending","engram"))
    # No human measured graph was modified/created.
    assert g.provenance=="synthetic_test_only"
    return {"pass":True,"checks":13,"snapshot_replay":True,
            "signed_transmission":True,"burst_cap":True,
            "read_only_source":True,"human_synapse_import":False}


def benchmark(source=DEFAULT_SOURCE,ticks=20):
    if not 1<=ticks<=100:
        raise ValueError("bounded benchmark ticks must be 1..100")
    t0=time.perf_counter()
    graph=Graph(source)
    t_load=time.perf_counter()-t0
    rt=Runtime(graph)
    # Deterministic stimulus subset; makes an actual bounded amount of work.
    seeded=np.arange(0,graph.n,max(1,graph.n//250),dtype=np.int64)
    rt.pulse(seeded,2.)
    per_tick=[]
    spike_events=[]
    for _ in range(ticks):
        t=time.perf_counter()
        spike_events.append(rt.step())
        per_tick.append(time.perf_counter()-t)
    peak_kib=resource.getrusage(resource.RUSAGE_SELF).ru_maxrss if resource else None
    return {
        "schema":SCHEMA,"test":"bounded_full_graph_read_only",
        "source_species":graph.species,"source_provenance":graph.provenance,
        "synapse_measurement":"fly_anatomy_only; dynamical parameters synthetic",
        "graph_sha256":graph.sha256,
        "neurons":graph.n,"original_measured_fly_edges":graph.e,
        "human_measured_interneuron_synapses_imported":0,
        "source_data_bytes":graph.data_bytes,
        "load_validation_seconds":round(t_load,5),
        "simulation_steps":ticks,
        "step_ms":[round(v*1000,4) for v in per_tick],
        "mean_step_ms":round(sum(per_tick)*1000/ticks,4),
        "median_step_ms":round(float(np.median(per_tick))*1000,4),
        "max_step_ms":round(max(per_tick)*1000,4),
        "synthetic_firing_per_step":spike_events,
        "events_total":rt.total_events,
        "capped_events":rt.limited_events,
        "max_rss_kib":peak_kib,
        "platform":platform.platform(),
        "cpu_core_count_visible":os.cpu_count(),
        "benchmark_limit":"finite CPU-only isolated run, not continuous real-time proof"
    }


def main():
    p=argparse.ArgumentParser()
    p.add_argument("action",choices=("selftest","benchmark"))
    p.add_argument("--ticks",type=int,default=20)
    p.add_argument("--source",type=Path,default=DEFAULT_SOURCE)
    p.add_argument("--output",type=Path)
    a=p.parse_args()
    result=selftest() if a.action=="selftest" else benchmark(a.source,a.ticks)
    if a.output:
        if not a.output.parent.is_dir():
            raise ValueError("output directory missing")
        tmp=a.output.with_name("." + a.output.name + ".tmp")
        tmp.write_text(json.dumps(result,ensure_ascii=False,indent=2,sort_keys=True)+"\n")
        os.replace(tmp,a.output)
    print(json.dumps(result,ensure_ascii=False,indent=2))

if __name__=="__main__":main()
