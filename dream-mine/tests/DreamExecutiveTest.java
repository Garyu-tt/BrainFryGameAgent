package io.mirbi.dreammine;

import net.minecraft.nbt.CompoundTag;

public final class DreamExecutiveTest {
    private static int checks;
    static void ok(boolean condition,String description){
        if(!condition)throw new AssertionError(description);
        checks++;
    }
    public static void main(String[] args){
        DreamExecutive e=new DreamExecutive();
        ok(e.ready(0),"initial goal available");
        e.commit(1,"GATHER_WOOD",500,600);
        ok(e.active(501),"active goal");
        ok(!e.ready(501),"cannot select again mid-goal");
        ok(e.source().equals("EMBEDDED_H01"),"explicit neural provenance");
        ok(e.progressAge(580)==80,"movement alone is not verified progress");
        e.progress("APPROACH","NONE",590);
        ok(e.progressAge(600)==100,"unverified milestone cannot reset clock");
        e.progress("BREAK_CONFIRMED","WORLD_BLOCK_BECAME_AIR",620);
        ok(e.progressAge(640)==20,"verified progress refresh");
        ok(e.evidence().equals("WORLD_BLOCK_BECAME_AIR"),"provenance tracked");
        e.complete("REAL_INVENTORY_INCREASED_BY_ONE",660);
        ok(!e.ready(699) && e.ready(700),"cooldown after completion");
        ok(e.successes()==1,"completion counted");
        e.commit(0,"EXPLORE",700,140);
        ok(!e.ready(780),"commitment prevents oscillation");
        e.expire(841);
        ok(e.phase()==DreamExecutive.Phase.DEFERRED,"deadline triggers deferral");
        ok(e.failures()==1,"failed goal counted");
        ok(!e.canChooseAction(0,841),"failed action excluded from neural choices");
        ok(e.canChooseAction(1,841),"unrelated goal not blacklisted");
        ok(e.canChooseAction(0,1100),"failed goal re-enabled after cooldown");
        ok(e.outcomeCount()==2,"success and failure logged separately");
        ok(!e.ready(920),"temporary deferral");
        ok(e.ready(1100),"eventual re-evaluation");
        e.commit(4,"OBSERVE_PLAYER",1100,260);
        e.interrupt("SAFETY_RETURN",1150);
        ok(e.phase()==DreamExecutive.Phase.DEFERRED,"safety interruption");
        ok(e.failures()==2,"interruption tracked separately from successes");
        ok(e.canChooseAction(4,1151),"safety interruption not treated as neural failure");
        ok(e.ready(1250),"safety cooldown ended");
        e.commit(1,"INSPECT_TREE",1250,600);
        CompoundTag saved=e.save();
        DreamExecutive restored=new DreamExecutive();
        restored.load(saved,1251);
        ok(restored.phase()==DreamExecutive.Phase.DEFERRED,"reload cannot resume unverifiable physical action");
        ok(restored.evidence().equals("RELOAD_REQUIRES_REVALIDATION"),"recheck marker");
        ok(restored.outcomeCount()==3,"personal verified outcomes persist");
        ok(!restored.ready(1251)&&restored.ready(1351),"recheck cooloff");
        try{
            e.commit(0,"SHOULD_NOT_REPLACE",1280,50);
            throw new AssertionError("neural goal overwritten during previous commitment");
        }catch(IllegalStateException expected){checks++;}
        try{
            e.complete("NONE",1400);
            throw new AssertionError("accepted missing evidence");
        }catch(IllegalArgumentException expected){checks++;}
        for(int i=0;i<50;i++){
            DreamExecutive x=new DreamExecutive();
            x.commit(0,"TASK_"+i,100,100);
            x.progress("ROUTE","NONE",150);
            ok(x.progressAge(160)==60,"no false progress "+i);
        }
        System.out.println("PASS "+checks+" executive autonomy/lease/evidence checks");
    }
}
