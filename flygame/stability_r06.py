#!/usr/bin/env python3
"""BFB9000 R06.1: isolated synaptic activity-control A/B experiment.

Read-only anatomical fly graph; all propagation gains and degree normalization
are purely experimental software approximations, NOT measured physiology.
No changes to BFB9000 human research graph, production agents, or GPU services.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import math
import os
import resource
import time
from pathlib import Path

import numpy as np

from . import sparse_runtime_r06 as base

ROOT=Path(__file__).resolve().parent
SCHEMA="bfb9000-r06.1-activity-stability"
CHUNK=250_000

def incoming_indegrees(g):
    result=np.zeros(g.n,dtype=np.int64)
    for pos in range(0,g.e,CHUNK):
        k=np.asarray(g.indices[pos:pos+CHUNK],dtype=np.int32)
        result+=np.bincount(k,minlength=g.n)
    if result.sum()!=g.e:
        raise AssertionError("loss of graph edges during normalization")
    return result

class StabilizedRuntime(base.Runtime):
    def __init__(self,graph,state=None,exponent=.5):
        if not(0.<=exponent<=.75):raise ValueError("unsafe exponent")
        self.exponent=float(exponent)
        # Degree, not measured conductance or real inhibitory homeostasis.
        self._degree=incoming_indegrees(graph)
        self._gain=(np.maximum(1,self._degree).astype(np.float64)**(-self.exponent)).astype(np.float32)
        super().__init__(graph,state)

    def _propagate(self,sources):
        super()._propagate(sources)
        self.pending*=self._gain

    @classmethod
    def load(cls,graph,path):
        with np.load(path,allow_pickle=False) as z:
            metadata=json.loads(str(z["metadata"]))
            fields={name:z[name].copy() for name in
                ("potential","trace","fatigue","pending","engram")}
        exponent=float(metadata["normalization_exponent"])
        return cls(graph,(metadata,fields),exponent=exponent)

    def snapshot(self):
        meta,arrays=super().snapshot()
        meta["activity_gain_method"]="experimental_incoming_sqrt_indegree"
        meta["degree_max"]=int(self._degree.max(initial=0))
        meta["normalization_exponent"]=self.exponent
        return meta,arrays

    def restore(self,state):
        meta,_=state
        if "plasticity_schema" in meta:
            raise ValueError("cannot downgrade a synaptically plastic checkpoint")
        if meta.get("activity_gain_method")!="experimental_incoming_sqrt_indegree":
            raise ValueError("cannot mix normalized and unnormalized checkpoints")
        if meta.get("degree_max")!=int(self._degree.max(initial=0)):
            raise ValueError("normalization graph degree mismatch")
        if meta.get("normalization_exponent")!=self.exponent:
            raise ValueError("normalization policy mismatch")
        local=dict(meta)
        local["activity_gain_method"]="none"
        return super().restore((local,state[1]))

def run_case(graph,cls,ticks,seeded,repeated_every):
    rt=cls(graph)
    measured=[]
    firings=[]
    for step in range(ticks):
        if step%repeated_every==0:
            rt.pulse(seeded,2.0)
        t=time.perf_counter()
        firings.append(rt.step())
        measured.append((time.perf_counter()-t)*1000)
    return {
      "steps":ticks,
      "stimulus_period_steps":repeated_every,
      "stimulated_count":len(seeded),
      "mean_step_ms":round(float(np.mean(measured)),4),
      "median_step_ms":round(float(np.median(measured)),4),
      "peak_step_ms":round(max(measured),4),
      "events":rt.total_events,
      "capped":rt.limited_events,
      "cap_fraction_attempted":round(rt.limited_events/
          max(1,rt.total_events+rt.limited_events),6),
      "firings_first_20":firings[:20],
      "firings_last_10":firings[-10:],
      "homeostasis_last":round(rt.homeostasis,6),
      "nonstimulated_tick_spikes":int(sum(v for i,v in enumerate(firings) if i%repeated_every!=0)),
      "finite_states":all(np.isfinite(getattr(rt,k)).all() for k in
           ("potential","pending","engram","trace","fatigue")),
      "model_species":graph.species,
      "graph_hash_prefix":graph.sha256[:24]
    }

def selftest():
    g=base.MiniGraph([0,2,3,3], [1,2,2],[20,4,9], [1,2,1])
    indeg=incoming_indegrees(g)
    assert np.array_equal(indeg,np.array([0,1,2]))
    normal=StabilizedRuntime(g)
    normal.pulse([0],2.)
    assert normal.step()==1
    assert np.isclose(normal.pending[1],1.2)
    assert np.isclose(normal.pending[2],.24/math.sqrt(2))
    comparison=base.Runtime(g)
    comparison.pulse([0],2.)
    assert comparison.step()==1
    assert normal.pending[2]<comparison.pending[2]
    normal.learn([0],.2)
    import tempfile
    with tempfile.TemporaryDirectory() as tmp:
        path=Path(tmp)/"state.npz"
        signature=normal.save(path)
        assert base.sha256_file(path)==signature
        resumed=StabilizedRuntime.load(g,path)
        for _ in range(12):
            assert normal.step()==resumed.step()
        assert normal.snapshot()[0]==resumed.snapshot()[0]
        for field in ("potential","pending","engram","trace","fatigue"):
            assert np.array_equal(getattr(normal,field),getattr(resumed,field))
        try:base.Runtime.load(g,path)
        except ValueError:pass
        else:
            # Base loader has no normalization provenance constraint,
            # so a normalized state may be wrongly resumed by base runtime.
            raise AssertionError("base kernel accepted normalized checkpoint")
    return {"pass":True,"degree_sum":int(indeg.sum()),
      "checkpoint_replay":True,"normalization_changes_input":True}

def main():
    p=argparse.ArgumentParser()
    p.add_argument("action",choices=("selftest","compare"))
    p.add_argument("--ticks",type=int,default=50)
    p.add_argument("--every",type=int,default=10)
    p.add_argument("--output",type=Path)
    a=p.parse_args()
    if a.action=="selftest":
        result=selftest()
    else:
        if not 1<=a.ticks<=100 or not 1<=a.every<=100:
            raise ValueError("benchmark request outside safety bounds")
        t=time.perf_counter()
        g=base.Graph()
        seeded=np.arange(0,g.n,max(1,g.n//250),dtype=np.int64)
        baseline=run_case(g,base.Runtime,a.ticks,seeded,a.every)
        sweep={}
        for exponent in (0.1,0.2,0.3,0.5):
            sweep[str(exponent)]=run_case(g,lambda gr,exponent=exponent:StabilizedRuntime(gr,exponent=exponent),a.ticks,seeded,a.every)
        result={
          "schema":SCHEMA,
          "source_species":g.species,"provenance":g.provenance,
          "graph_hash":g.sha256,"neuron_count":g.n,"edge_count":g.e,
          "human_human_synapses_measured":0,
          "status":"experiment, not biological validation",
          "models":{
             "baseline":baseline,
             "incoming_degree_exponent_sweep":sweep
          },
          "speed_variance_note":"warm cache and host load may affect runtimes",
          "performance_is_not_behavioral_equivalence":True,
          "peak_memory_mib":round(resource.getrusage(resource.RUSAGE_SELF).ru_maxrss/1024,3),
          "elapsed_seconds":round(time.perf_counter()-t,4)
        }
    if a.output:
        if not a.output.parent.is_dir():raise ValueError("destination missing")
        tmp=a.output.with_name("."+a.output.name+".tmp")
        tmp.write_text(json.dumps(result,indent=2,ensure_ascii=False,sort_keys=True)+"\n")
        os.replace(tmp,a.output)
    print(json.dumps(result,indent=2,ensure_ascii=False))

if __name__=="__main__":main()
