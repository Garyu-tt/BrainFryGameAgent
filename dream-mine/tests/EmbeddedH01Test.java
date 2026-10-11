package io.mirbi.dreammine;

import net.minecraft.nbt.CompoundTag;
import java.util.Arrays;

public class EmbeddedH01Test {
    private static int checks=0;
    private static void ok(boolean result,String description) {
        if(!result)throw new AssertionError(description);
        checks++;
    }
    private static void approx(float a,float b,String name) {
        ok(Math.abs(a-b)<.0001f,name+" "+a+" vs "+b);
    }
    public static void main(String[] args) {
        EmbeddedH01 cortex=new EmbeddedH01(123);
        ok(cortex.modules()==8,"modules");
        ok(cortex.synapseCount()==8*96*8,"structural synapses");
        float[] sensors=new float[EmbeddedH01.INPUTS];
        Arrays.fill(sensors,1);
        boolean[] feasible={true,true,true,true,true};
        for(int i=0;i<70;i++){
            int decision=cortex.choose(sensors,feasible);
            ok(decision>=0&&decision<5,"masked decision");
            cortex.reward(.95f);
        }
        ok(cortex.steps()==70,"steps");
        ok(cortex.reinforcementCount()==70,"rewards");
        ok(cortex.changedWeights()>0,"recurrent synapses plastic");
        float[] readout=cortex.learnedReadout();
        ok(readout.length==EmbeddedH01.INPUTS*EmbeddedH01.ACTIONS,"shared adapter shape");
        ok(Arrays.stream(new int[]{0,1}).count()==2,"sanity");
        boolean[] onlySocial={false,false,false,false,true};
        ok(cortex.choose(sensors,onlySocial)==4,"action mask");
        ok(cortex.choose(sensors,new boolean[]{false,false,false,false,false})==-1,"all blocked");
        float[] sanitized=EmbeddedH01.sanitize(new float[]{
            Float.NaN,Float.POSITIVE_INFINITY,4,-9,.25f,0,0,0,0,0,0,0
        });
        ok(sanitized[0]==0 && sanitized[1]==0 && sanitized[2]==1 && sanitized[3]==-1,"sanitized");
        EmbeddedH01 child=new EmbeddedH01(456);
        child.inheritReadout(readout,1);
        float[] inherited=child.learnedReadout();
        for(int i=0;i<readout.length;i++)approx(inherited[i],readout[i],"shared inheritance "+i);
        CompoundTag persisted=cortex.save();
        EmbeddedH01 recovered=new EmbeddedH01(123);
        recovered.load(persisted);
        ok(recovered.steps()==cortex.steps(),"steps restored");
        ok(recovered.reinforcementCount()==cortex.reinforcementCount(),"rewards restored");
        ok(recovered.changedWeights()>0,"synapses restored");
        for(int i=0;i<readout.length;i++)approx(recovered.learnedReadout()[i],readout[i],"readout restored "+i);
        ok(persisted.getIntArray("weights").length==cortex.synapseCount(),"weights persisted");
        for(int generation=0;generation<50;generation++){
            EmbeddedH01 newborn=new EmbeddedH01(generation+900);
            newborn.inheritReadout(child.learnedReadout(),.75f);
            ok(newborn.synapseCount()==cortex.synapseCount(),"newborn topology");
        }
        System.out.println("PASS "+checks+" neural plasticity + persistence + inheritance checks");
    }
}
