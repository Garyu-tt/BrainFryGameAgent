import sqlite3, tempfile, os
from pathlib import Path
from . import fly_delta

def evt(step, src, dest, action="explore", new=True):
    return dict(step=step, at_utc="2026-11-05T06:00:%02d+00:00" % (step%60),
                from_node=src, new_node=new, action=action,
                **{"from":src, "to":dest})

with tempfile.TemporaryDirectory() as tmp:
    c=sqlite3.connect(str(Path(tmp)/"fly.sqlite3"))
    fly_delta.install(c)
    assert c.execute("pragma integrity_check").fetchone()[0] == "ok"
    a=("concept:a","concept","A")
    b=("concept:b","concept","B")
    with c:
        d=fly_delta.apply_step(c,evt(1,"concept:a","concept:b"),b)
        assert d["created"] == 2, d
        assert fly_delta.stats(c)["personal_nodes"] == 2
    assert fly_delta.node(c,"fly:home")[2] == "Нора цифровой мухи"
    assert fly_delta.node(c,"fly:memory:"+__import__("hashlib").sha256(b"concept:b").hexdigest()[:24])
    assert "concept:a" in fly_delta.neighbor_ids(c,"fly:home")
    with c:
        d=fly_delta.apply_step(c,evt(2,"concept:b","concept:b","inspect",False),b)
    assert d["revised"] == 1, d
    assert fly_delta.stats(c)["revisions"] == 1
    assert fly_delta.stats(c)["personal_nodes"] == 2
    assert c.execute("select count(*) from fly_edges where relation='anchor'").fetchone()[0] == 1
    try:
        with c:
            fly_delta.apply_step(c,evt(3,"concept:b","concept:z"),("concept:z","concept","Z"))
            raise RuntimeError("simulate crash before commit")
    except RuntimeError:
        pass
    assert fly_delta.stats(c)["personal_nodes"] == 2
    for i in range(4,49):
        dest=("concept:"+str(i),"concept","item "+str(i))
        with c:
            fly_delta.apply_step(c,evt(i,"concept:b",dest[0]),dest)
    assert c.execute("select count(*) from fly_edges where relation='stored'").fetchone()[0] == 32
    assert c.execute("select count(*) from fly_edges where relation='anchor'").fetchone()[0] == 1
    assert c.execute("pragma integrity_check").fetchone()[0] == "ok"
    print("PASS",fly_delta.stats(c))
