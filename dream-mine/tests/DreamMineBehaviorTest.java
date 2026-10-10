package io.mirbi.dreammine;
import java.util.*;
public class DreamMineBehaviorTest {
    private static int passed;
    private static void check(boolean ok,String name){
        if(!ok)throw new AssertionError(name);
        passed++;
    }
    public static void main(String[] args){
        for(int n=0;n<100;n++) {
            check(DreamPopulation.shouldAttempt(n,0,true,false,1),"one-auto-available");
            check(!DreamPopulation.shouldAttempt(n,0,true,true,1),"one-auto-alive");
        }
        check(!DreamPopulation.shouldAttempt(500,600,true,false,1),"spawn-cooldown");
        check(!DreamPopulation.shouldAttempt(700,600,false,false,1),"spawn-disabled");
        check(!DreamPopulation.shouldAttempt(700,600,true,false,0),"no-players");
        check(DreamPopulation.REPLACEMENT_DELAY_TICKS == 600,"respawn-delay");
        check(DreamAppearance.validSkin("Notch"),"skin");
        check(!DreamAppearance.validSkin("https://evil"),"reject-uri");
        check(!DreamAppearance.validSkin("19chars1234567891011"),"length");
        check(DreamAppearance.fromEggLabel(null).account().equals("Dream"),"egg-default");
        check(DreamAppearance.fromEggLabel("Alfa@Dream").nick().equals("Alfa"),"egg-custom-name");
        check(DreamAppearance.fromEggLabel("Alfa@Dream").account().equals("Dream"),"egg-custom-skin");
        check(DreamAppearance.fromEggLabel("Jeb_").account().equals("Jeb_"),"egg-simple");
        check(DreamAppearance.fromEggLabel("Alfa@https://evil").account().equals("Dream"),"egg-bad-url");
        check(DreamLeash.decide(70*70,true,false) == DreamLeash.Decision.ROAM,"leash-roam");
        check(DreamLeash.decide(75*75,true,false) == DreamLeash.Decision.RETURN,"leash-return");
        check(DreamLeash.decide(101*101,true,false) == DreamLeash.Decision.RELOCATE,"leash-recover");
        check(DreamLeash.decide(50*50,true,true) == DreamLeash.Decision.ROAM,"leash-end-return");
        check(DreamLeash.decide(70*70,true,true) == DreamLeash.Decision.RETURN,"leash-hysteresis");
        check(DreamLeash.decide(40*40,false,false) == DreamLeash.Decision.IDLE,"leash-no-player");
        check(DreamLeash.MAX_RADIUS==100d,"max-radius");
        LearningMind a=new LearningMind(new double[8]);
        boolean[] safe={true,false,false,false,false,false,false,false};
        check(a.choose(safe,new Random(42))==0,"safe-action");
        a.observe(0,1);
        a.observe(0,-1);
        LearningMind b=new LearningMind(new double[8]);
        b.restorePersonal(a.scores(),a.attempts());
        check(Arrays.equals(a.scores(),b.scores()),"personal-scores");
        check(Arrays.equals(a.attempts(),b.attempts()),"personal-attempts");
        LearningMind c=new LearningMind(new double[8]);
        c.restorePersonal(new double[1],new int[1]);
        check(c.attempts()[0]==0,"bad-personal-guard");
        for(int i=0;i<8;i++){
            boolean[] no=new boolean[8];no[i]=true;
            check(a.choose(no,new Random(42),new double[8])==i,"shared-with-mask");
        }
        System.out.println("PASS "+passed+" checks");
    }
}
