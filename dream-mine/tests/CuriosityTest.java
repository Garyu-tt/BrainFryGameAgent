package io.mirbi.dreammine;

import java.util.Random;

public final class CuriosityTest {
    private static int tests=0;
    private static void check(boolean ok,String name){ if(!ok)throw new AssertionError(name);tests++; }
    public static void main(String[] args){
        LearningMind brain=new LearningMind(new double[8]);
        boolean[] safe=new boolean[8];
        double[] bonus=new double[8],shared=new double[8];
        safe[0]=safe[1]=true;
        bonus[0]=-0.75;bonus[1]=1.75;
        for(int i=0;i<1000;i++)check(brain.choose(safe,new Random(i),shared,bonus)==1,"prefer novel");
        safe[1]=false;
        for(int i=0;i<100;i++)check(brain.choose(safe,new Random(i),shared,bonus)==0,"respect safety");
        boolean[] blocked=new boolean[8];
        check(brain.choose(blocked,new Random(4),shared,bonus)==-1,"all blocked");
        double[] tooShort=new double[4];
        try{
            brain.choose(safe,new Random(1),shared,tooShort);
            throw new AssertionError("accepted invalid curiosity");
        }catch(IllegalArgumentException expected){tests++;}
        check(DreamPopulation.shouldAttempt(1200,600,true,false,1),"single automatic slot");
        check(!DreamPopulation.shouldAttempt(1200,600,true,true,1),"automatic slot occupied");
        System.out.println("PASS "+tests+" curiosity / safety checks");
    }
}
