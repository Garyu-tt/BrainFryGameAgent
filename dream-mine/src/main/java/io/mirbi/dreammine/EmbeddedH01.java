package io.mirbi.dreammine;

import net.minecraft.nbt.CompoundTag;
import java.util.Arrays;
import java.util.Random;

/**
 * Local Java adaptation of the principles implemented in h01-surrogate.mjs:
 * sparse recurrent microcolumns, membrane integration, spike traces,
 * plastic synaptic weights, eligibility traces and reward-gated learning.
 *
 * This is an embedded 8x96 microcolumn SURROGATE, not a full HCP800 model.
 * Runs strictly in the game JVM, with no outside process or network requests.
 */
public final class EmbeddedH01 {
    public static final int MODULES=8, COLUMNS=96, FANOUT=8;
    public static final int INPUTS=DreamSensory.SIZE, ACTIONS=5;
    public static final String[] ACTION_NAMES={"EXPLORE","GATHER_WOOD","CRAFT_PLANKS","BUILD_MARKER","OBSERVE_PLAYER"};
    private static final int EDGES=MODULES*COLUMNS*FANOUT;
    private static final double WEIGHT_SCALE=32767.0;
    private final float[] v=new float[MODULES*COLUMNS];
    private final float[] trace=new float[MODULES*COLUMNS];
    private final float[] adapt=new float[MODULES*COLUMNS];
    private final float[] inputSum=new float[MODULES*COLUMNS];
    private final float[] output=new float[INPUTS];
    private final int[] targets=new int[EDGES];
    private final float[] weight=new float[EDGES],base=new float[EDGES],eligibility=new float[EDGES];
    private final float[][] readout=new float[ACTIONS][INPUTS];
    private final float[][] readoutEligibility=new float[ACTIONS][INPUTS];
    private float[] lastSensors=new float[INPUTS];
    private float[] actionSensors=new float[INPUTS];
    private int lastAction=-1;
    private long steps,reinforcements;
    private float lastActivation;
    private final Random random;
    public EmbeddedH01(long seed) {
        random=new Random(seed ^ 0x0B1A5AFE77L);
        for(int m=0;m<MODULES;m++)for(int i=0;i<COLUMNS;i++){
            int unit=m*COLUMNS+i,offset=unit*FANOUT;
            v[unit]=(random.nextFloat()-.5f)*.08f;
            for(int e=0;e<FANOUT;e++){
                int edge=offset+e;
                targets[edge]=m*COLUMNS+random.nextInt(COLUMNS);
                if(targets[edge]==unit)targets[edge]=m*COLUMNS+((i+e+1)%COLUMNS);
                weight[edge]=((i%7)==0?-1:1)*(.16f+random.nextFloat()*.24f);
                base[edge]=weight[edge];
            }
        }
        for(int a=0;a<ACTIONS;a++)for(int i=0;i<INPUTS;i++)
            readout[a][i]=(random.nextFloat()-.5f)*.04f;
    }
    public static float[] sanitize(float[] sensors){
        if(sensors==null||sensors.length!=INPUTS)throw new IllegalArgumentException("expected "+INPUTS+" sensory values");
        float[] copy=Arrays.copyOf(sensors,INPUTS);
        for(int i=0;i<INPUTS;i++)copy[i]=Float.isFinite(copy[i])?Math.max(-1,Math.min(1,copy[i])):0;
        return copy;
    }
    /** Continuous recurrent sensory activity, including while navigating; no action selected. */
    public void senseFrame(float[] senses){
        lastSensors=sanitize(senses);
        pulse(lastSensors);
    }
    /** One physically grounded perception -> recurrent update -> masked decision. */
    public int choose(float[] senses,boolean[] feasible){
        if(feasible==null||feasible.length!=ACTIONS)throw new IllegalArgumentException("action mask");
        lastSensors=sanitize(senses);
        pulse(lastSensors);
        float best=Float.NEGATIVE_INFINITY;
        int action=-1;
        for(int a=0;a<ACTIONS;a++){
            if(!feasible[a])continue;
            float activation=0;
            for(int i=0;i<INPUTS;i++)
                activation+=lastSensors[i]*readout[a][i]+output[(i+a*2)%INPUTS]*.13f;
            // Affordance priors keep an untrained cortex from rejecting basic survival/resource chains.
            if(a==1)activation+=lastSensors[0]*1.1f;  // visible wood
            if(a==2)activation+=lastSensors[1]*1.8f;  // logs held
            if(a==3)activation+=lastSensors[2]*1.4f;  // construction materials
            if(a==4)activation+=lastSensors[3]*.25f; // nearby human
            if(a==0)activation+=.28f;
            activation+=random.nextFloat()*.09f;
            if(activation>best){best=activation;action=a;}
        }
        lastAction=action;
        if(action>=0) actionSensors=Arrays.copyOf(lastSensors,INPUTS);
        return action;
    }
    private void pulse(float[] senses){
        Arrays.fill(inputSum,0f);
        for(int n=0;n<trace.length;n++){
            float t=trace[n];
            if(t<.00001f)continue;
            int offset=n*FANOUT;
            for(int j=0;j<FANOUT;j++) inputSum[targets[offset+j]]+=t*weight[offset+j];
        }
        float activity=0;
        Arrays.fill(output,0f);
        for(int n=0;n<v.length;n++){
            int band=n%INPUTS, module=n/COLUMNS;
            float external=Math.max(0,senses[(band+module)%INPUTS]);
            float x=.30f*external+.12f*inputSum[n]+.048f-adapt[n];
            float voltage=v[n]+(.28f)*(-v[n]+x);
            float spike=voltage>.16f?1:0;
            if(spike>0){voltage=-.08f;adapt[n]=Math.min(.7f,adapt[n]+.045f);}
            else adapt[n]*=.97f;
            float tr=trace[n]*.83f+spike*.42f;
            v[n]=voltage;trace[n]=tr;
            activity+=tr;
            output[band]+=tr;
        }
        for(int k=0;k<INPUTS;k++)output[k]/=(float)(MODULES*COLUMNS/INPUTS);
        lastActivation=activity/v.length;
        // Eligibility decays even if the pre-synaptic neuron did not spike.
        for(int edge=0;edge<EDGES;edge++){
            int pre=edge/FANOUT,post=targets[edge];
            eligibility[edge]=Math.min(1f,eligibility[edge]*.993f+
                Math.min(1f,trace[pre]) * Math.min(1f,trace[post])*.012f);
        }
        steps++;
    }
    public void reward(float scalar) {
        if(lastAction<0||!Float.isFinite(scalar))return;
        scalar=Math.max(-1f,Math.min(1f,scalar));
        for(int k=0;k<INPUTS;k++){
            readoutEligibility[lastAction][k]=
                Math.min(1f,readoutEligibility[lastAction][k]*.82f+Math.abs(actionSensors[k])*.28f);
            float sign=Math.signum(actionSensors[k]);
            readout[lastAction][k]=clamp(
                readout[lastAction][k]+.04f*scalar*readoutEligibility[lastAction][k]*sign,-2,2);
        }
        // Reward-gated plasticity: the eligibility trace assigns credit for recent spiking.
        for(int e=0;e<EDGES;e++){
            float el=eligibility[e];
            if(el<.0005f)continue;
            float signed=weight[e]+Math.signum(base[e])*.008f*scalar*el;
            weight[e]=Math.signum(base[e])*Math.max(Math.abs(base[e])*.35f,
                Math.min(Math.abs(base[e])*2.8f,Math.abs(signed)));
            eligibility[e]*=.45f;
        }
        reinforcements++;
    }
    private static float clamp(float x,float lo,float hi){return Math.max(lo,Math.min(hi,x));}
    /** Flattened learned policy; bounded and safe to share between agents in the SAME world. */
    public float[] learnedReadout(){
        float[] values=new float[ACTIONS*INPUTS];
        for(int a=0;a<ACTIONS;a++)for(int i=0;i<INPUTS;i++)
            values[a*INPUTS+i]=readout[a][i];
        return values;
    }
    public void inheritReadout(float[] values,float proportion){
        if(values==null || values.length!=ACTIONS*INPUTS) return;
        float alpha=clamp(proportion,0,1);
        for(int a=0;a<ACTIONS;a++)for(int k=0;k<INPUTS;k++){
            float v=values[a*INPUTS+k];
            if(!Float.isFinite(v))continue;
            readout[a][k]=clamp((1-alpha)*readout[a][k]+alpha*v,-2,2);
        }
    }
    public long steps(){return steps;}
    public long reinforcementCount(){return reinforcements;}
    public float activation(){return lastActivation;}
    public int changedWeights(){
        int n=0;for(int i=0;i<EDGES;i++)if(Math.abs(weight[i]-base[i])>1e-7)n++;
        return n;
    }
    public int synapseCount(){return EDGES;}
    public int modules(){return MODULES;}
    /** Only learned weights are serialized; structural wiring is deterministic from per-agent UUID. */
    public CompoundTag save(){
        CompoundTag nbt=new CompoundTag();
        int[] w=new int[EDGES];
        for(int i=0;i<EDGES;i++)w[i]=(int)Math.round(weight[i]*WEIGHT_SCALE);
        nbt.putIntArray("weights",w);
        int[] r=new int[ACTIONS*INPUTS];
        for(int i=0;i<ACTIONS;i++)for(int j=0;j<INPUTS;j++)
            r[i*INPUTS+j]=(int)Math.round(readout[i][j]*WEIGHT_SCALE);
        nbt.putIntArray("readout",r);
        nbt.putLong("steps",steps);
        nbt.putLong("reinforcements",reinforcements);
        return nbt;
    }
    public void load(CompoundTag nbt){
        if(nbt==null)return;
        int[] w=nbt.getIntArray("weights");
        if(w.length==EDGES)for(int i=0;i<EDGES;i++)
            weight[i]=(float)(Math.signum(base[i])*Math.max(Math.abs(base[i])*.35f,
                Math.min(Math.abs(base[i])*2.8f,Math.abs(w[i]/WEIGHT_SCALE))));
        int[] r=nbt.getIntArray("readout");
        if(r.length==ACTIONS*INPUTS)for(int i=0;i<ACTIONS;i++)for(int j=0;j<INPUTS;j++)
            readout[i][j]=clamp((float)(r[i*INPUTS+j]/WEIGHT_SCALE),-2,2);
        else if(r.length==ACTIONS*12) { // v0.2.0 migration of the original 12 inputs
            for(int i=0;i<ACTIONS;i++)for(int j=0;j<12;j++)
                readout[i][j]=clamp((float)(r[i*12+j]/WEIGHT_SCALE),-2,2);
        }
        steps=Math.max(0,nbt.getLong("steps"));
        reinforcements=Math.max(0,nbt.getLong("reinforcements"));
    }
}
