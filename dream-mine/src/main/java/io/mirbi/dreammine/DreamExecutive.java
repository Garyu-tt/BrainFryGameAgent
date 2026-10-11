package io.mirbi.dreammine;

import net.minecraft.nbt.CompoundTag;
import java.util.Locale;
import java.util.ArrayDeque;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Persistent high-level intention contract. Port of BrainFly's intent-executive
 * invariants: neural decision and executor distinct, a goal survives multiple
 * ticks, progress needs an observed milestone, failures cause bounded deferral.
 *
 * No path-distance or mere command issuance is accepted as a goal milestone.
 */
public final class DreamExecutive {
    public enum Phase { IDLE, COMMITTED, COMPLETE, FAILED, DEFERRED }
    private String goal="NONE", source="NONE", stage="IDLE", evidence="NONE";
    private int action=-1,failures,completed,abandoned,attempts;
    private final long[] actionCooldowns=new long[EmbeddedH01.ACTIONS];
    private final ArrayDeque<CompoundTag> outcomes=new ArrayDeque<>();
    private long since,lastMilestone,deadline,nextAllowed;
    private Phase phase=Phase.IDLE;
    private static final long MIN_COMMIT_TICKS=80;
    public static final long DEFAULT_GOAL_TICKS=600;
    public boolean canChooseAction(int id,long now){
        return id>=0 && id<actionCooldowns.length && now>=actionCooldowns[id];
    }
    private void journal(String outcome,long now){
        CompoundTag event=new CompoundTag();
        event.putString("goal",goal);
        event.putString("source",source);
        event.putString("outcome",outcome);
        event.putString("evidence",evidence);
        event.putLong("tick",now);
        outcomes.addLast(event);
        while(outcomes.size()>24)outcomes.removeFirst();
    }
    public int outcomeCount(){return outcomes.size();}
    public boolean ready(long now){
        return now>=nextAllowed && (phase==Phase.IDLE || phase==Phase.COMPLETE ||
              phase==Phase.FAILED || phase==Phase.DEFERRED);
    }
    public boolean active(long now){
        return phase==Phase.COMMITTED && now<deadline;
    }
    public boolean deadlineExpired(long now){
        return phase==Phase.COMMITTED && now>=deadline;
    }
    public void commit(int neuralAction,String label,long now,long ttlTicks){
        if(!ready(now))throw new IllegalStateException("existing intention owns body");
        if(neuralAction<0||neuralAction>=EmbeddedH01.ACTIONS)throw new IllegalArgumentException("neural action");
        action=neuralAction;
        goal=label==null?"UNKNOWN":label;
        source="EMBEDDED_H01";
        stage="PLAN";
        evidence="NONE";
        since=now;
        lastMilestone=now;
        deadline=now+Math.max(MIN_COMMIT_TICKS,Math.min(2400,ttlTicks));
        failures=0;
        attempts++;
        phase=Phase.COMMITTED;
    }
    /** Evidence must be a fact observed AFTER game-state mutation or perception. */
    public void progress(String step,String verifiedEvidence,long now){
        if(phase!=Phase.COMMITTED || verifiedEvidence==null ||
             verifiedEvidence.isBlank() || "NONE".equals(verifiedEvidence))return;
        stage=step;
        evidence=verifiedEvidence;
        lastMilestone=now;
    }
    public void complete(String verifiedEvidence,long now){
        if(phase!=Phase.COMMITTED)throw new IllegalStateException("no active goal");
        if(verifiedEvidence==null || verifiedEvidence.isBlank()
            || "NONE".equals(verifiedEvidence))throw new IllegalArgumentException("no evidence");
        evidence=verifiedEvidence;
        stage="VERIFIED_DONE";
        phase=Phase.COMPLETE;
        completed++;
        nextAllowed=now+40;
        journal("VERIFIED_SUCCESS",now);
    }
    public void fail(String reason,long now){
        if(phase!=Phase.COMMITTED)return;
        failures++;
        evidence=reason==null?"UNKNOWN_FAILURE":reason;
        stage="REPLAN";
        if(failures>=2 || now>=deadline){
            phase=Phase.DEFERRED;
            abandoned++;
            nextAllowed=now+Math.min(1200,80L*(1L<<Math.min(4,failures)));
            if(action>=0 && action<actionCooldowns.length)
                actionCooldowns[action]=now+(action==0?220:600);
            journal("DEFERRED_FAILURE",now);
        }else{
            deadline=Math.min(now+180,deadline);
        }
    }
    public void interrupt(String reason,long now){
        if(phase!=Phase.COMMITTED)return;
        stage="SAFETY_INTERRUPTED";
        evidence=reason==null?"SAFETY":reason;
        phase=Phase.DEFERRED;
        abandoned++;
        nextAllowed=now+100;
        journal("INTERRUPTED_SAFETY",now);
    }
    public void expire(long now){
        if(deadlineExpired(now))fail("DEADLINE_WITHOUT_VERIFIED_MILESTONE",now);
    }
    public Phase phase(){return phase;}
    public int action(){return action;}
    public String goal(){return goal;}
    public String stage(){return stage;}
    public String source(){return source;}
    public String evidence(){return evidence;}
    public long lastChosenTick(){return since;}
    public long age(long now){return Math.max(0,now-since);}
    public long progressAge(long now){return Math.max(0,now-lastMilestone);}
    public int successes(){return completed;}
    public int failures(){return abandoned;}
    public long nextAllowed(){return nextAllowed;}
    public String debug(long now){
        return String.format(Locale.ROOT,
            "intent=%s/%s stage=%s source=%s age=%dt no_milestone=%dt evidence=%s verified=%d deferred=%d history=%d",
            goal,phase,stage,source,age(now),progressAge(now),evidence,completed,abandoned,outcomes.size());
    }
    public CompoundTag save(){
        CompoundTag x=new CompoundTag();
        x.putString("goal",goal);
        x.putString("source",source);
        x.putString("stage",stage);
        x.putString("evidence",evidence);
        x.putString("phase",phase.name());
        x.putInt("action",action);
        x.putInt("completed",completed);
        x.putInt("abandoned",abandoned);
        x.putInt("attempts",attempts);
        x.putLongArray("actionCooldowns",actionCooldowns);
        ListTag history=new ListTag();
        for(var entry:outcomes)history.add(entry.copy());
        x.put("outcomes",history);
        x.putLong("since",since);
        x.putLong("lastMilestone",lastMilestone);
        x.putLong("deadline",deadline);
        x.putLong("nextAllowed",nextAllowed);
        return x;
    }
    public void load(CompoundTag x,long now){
        if(x==null||x.isEmpty())return;
        goal=x.getString("goal");
        source=x.getString("source");
        stage=x.getString("stage");
        evidence=x.getString("evidence");
        try{phase=Phase.valueOf(x.getString("phase"));}
        catch(IllegalArgumentException e){phase=Phase.IDLE;}
        action=x.getInt("action");
        completed=Math.max(0,x.getInt("completed"));
        abandoned=Math.max(0,x.getInt("abandoned"));
        attempts=Math.max(0,x.getInt("attempts"));
        long[] cooldown=x.getLongArray("actionCooldowns");
        if(cooldown.length==actionCooldowns.length)
            System.arraycopy(cooldown,0,actionCooldowns,0,cooldown.length);
        ListTag past=x.getList("outcomes",Tag.TAG_COMPOUND);
        for(int i=Math.max(0,past.size()-24);i<past.size();i++)
            outcomes.addLast(past.getCompound(i).copy());
        since=x.getLong("since");
        lastMilestone=x.getLong("lastMilestone");
        deadline=x.getLong("deadline");
        nextAllowed=x.getLong("nextAllowed");
        // Atomic operations don't survive an unloaded body without game-state
        // reconciliation: end the lease safely and allow a new goal later.
        if(phase==Phase.COMMITTED){
            phase=Phase.DEFERRED;
            evidence="RELOAD_REQUIRES_REVALIDATION";
            abandoned++;
            nextAllowed=now+100;
        }
        if(nextAllowed>now+1200 || nextAllowed<now-24000)nextAllowed=now+100;
        for(int i=0;i<actionCooldowns.length;i++)
            if(actionCooldowns[i]>now+1200 || actionCooldowns[i]<now-24000)
                actionCooldowns[i]=now;
    }
}
