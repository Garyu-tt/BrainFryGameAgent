package io.mirbi.dreammine;

import java.util.*;

public final class DreamSensoryRegistryTest {
    private static int count;
    private static void check(boolean ok,String why){
        if(!ok) throw new AssertionError(why);
        count++;
    }
    public static void main(String[] args){
        check(DreamSensory.channels()<DreamSensory.SIZE,"sensor budget");
        check(DreamSensory.channels()>500,"full sensory map registered");
        check(DreamSensory.index("resource_wood_near")==0,"old resource index unchanged");
        check(DreamSensory.index("daylight")==11,"old daylight index unchanged");
        check(DreamSensory.index("proprio_impact")>0,"proprio impact");
        check(DreamSensory.index("voxel_front_foot_water")>0,"front water voxel");
        check(DreamSensory.index("voxel_underfoot_unknown")>0,"explicit unknown terrain");
        check(DreamSensory.index("retina_known_fraction")>0,"known fraction");
        check(DreamSensory.index("retina_fovea_hazard")>0,"foveal danger");
        check(DreamSensory.index("retina_sector_2_2_lava")>0,"sector hazard");
        check(DreamSensory.index("nonexistent_sensor")==-1,"unknown feature refusal");
        for(int row=0;row<7;row++)for(int col=0;col<11;col++)
            for(String kind:new String[]{"depth","structure","resource","danger"}){
                String key=String.format(Locale.ROOT,"retina_y%02d_x%02d_%s",row,col,kind);
                check(DreamSensory.index(key)>=0,key);
            }
        DreamSensory.Frame f=new DreamSensory.Frame();
        f.set("retina_y03_x05_danger",1.5);
        check(f.value("retina_y03_x05_danger")==1,"clamp positive");
        f.set("retina_y03_x05_depth",-4);
        check(f.value("retina_y03_x05_depth")==-1,"clamp negative");
        f.set("retina_y03_x05_danger",Double.NaN);
        check(f.value("retina_y03_x05_danger")==1,"NaN ignored");
        f.set("undocumented_feature",1);
        check(DreamSensory.channelNames().size()==DreamSensory.channels(),"channels count stable");
        check(new HashSet<>(DreamSensory.channelNames()).size()==DreamSensory.channels(),"no duplicate channels");
        check(DreamSensory.RETINA_COLS*DreamSensory.RETINA_ROWS==77,"grid shape");
        check(DreamSensory.MAX_VOXEL_READS<=900,"read limit");
        System.out.println("PASS "+count+" registry + bounds checks; sensors="+DreamSensory.channels()+"/"+DreamSensory.SIZE);
    }
}
