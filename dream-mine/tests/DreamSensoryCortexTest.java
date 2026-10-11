package io.mirbi.dreammine;

public final class DreamSensoryCortexTest {
    private static void require(boolean ok,String why) {
        if(!ok)throw new AssertionError(why);
    }
    public static void main(String[] args){
        EmbeddedH01 baseline=new EmbeddedH01(48183),signalled=new EmbeddedH01(48183);
        float[] quiet=new float[EmbeddedH01.INPUTS];
        float[] danger=new float[EmbeddedH01.INPUTS];
        int slot=DreamSensory.index("retina_y03_x05_danger");
        require(slot>=0 && slot<EmbeddedH01.INPUTS,"connected retina channel");
        danger[slot]=1;
        for(int i=0;i<30;i++){
            baseline.senseFrame(quiet);
            signalled.senseFrame(danger);
        }
        require(baseline.steps()==30 && signalled.steps()==30,"continuous sensing updates neural runtime");
        require(Math.abs(baseline.activation()-signalled.activation())>0.000001f,
            "A real retinal danger stimulus changes neuronal activation");
        EmbeddedH01 blank=new EmbeddedH01(48183);
        require(blank.steps()==0,"no phantom sensory ticks");
        float[] social=new float[EmbeddedH01.INPUTS];
        social[DreamSensory.index("proprio_motion_error")]=1;
        EmbeddedH01 motor=new EmbeddedH01(48183);
        for(int i=0;i<30;i++)motor.senseFrame(social);
        require(Math.abs(blank.activation()-motor.activation())>0.000001f,
            "Proprioceptive motion error reaches neuronal activity");
        System.out.println("PASS 6 neural sensor grounding assertions");
    }
}
