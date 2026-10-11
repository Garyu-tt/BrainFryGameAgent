package io.mirbi.dreammine;

import dev.duzo.players.entities.FakePlayerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/**
 * Sensor registry ported from BrainFly's spatial-vision, retinotopic-vision
 * and proprioception modules. Unknown/unloaded data remains UNKNOWN (not air).
 * Fixed 11x7 ray grid plus local egocentric body voxels, independently bounded
 * by a hard max voxel reads per cognitive frame. No external processes.
 */
public final class DreamSensory {
    public static final int SIZE=768;
    public static final int RETINA_COLS=11,RETINA_ROWS=7;
    public static final int MAX_VOXEL_READS=900;
    public static final String[] SLOT_NAMES={
        "underfoot","overhead","front_foot","front_face","front_head","front_support",
        "left_foot","right_foot"
    };
    public static final String[] MATERIAL_TYPES={
        "air","rock","soil","wood","foliage","ore","water","lava","bedrock",
        "crafted","hazard","other","unknown"
    };
    public static final String[] SECTOR_TYPES={
        "rock","soil","wood","foliage","ore","water","lava","crafted","unknown"
    };
    public static final String[] PROPRIO={
        "proprio_forward","proprio_backward","proprio_left","proprio_right",
        "proprio_up","proprio_down","proprio_yaw_left","proprio_yaw_right",
        "proprio_pitch_up","proprio_pitch_down","proprio_ground_load",
        "proprio_airborne","proprio_motor_effort","proprio_motion_error","proprio_impact"
    };
    private static final Map<String,Integer> MAP=new LinkedHashMap<>();
    private static final List<String> NAMES=new ArrayList<>();
    static {
        for(String legacy:new String[]{
            "resource_wood_near","inventory_logs","inventory_planks","player_near",
            "health","water","player_distance","world_actions","has_workbench",
            "crafted_count","mined_count","daylight"})add(legacy);
        for(String n:PROPRIO)add(n);
        for(String n:new String[]{
            "vision_forward","vision_left","vision_right","obstacle_forward",
            "obstacle_left","obstacle_right","cliff_forward","ground_contact",
            "falling","hostile_near","players_near","health_low","head_submerged",
            "breathable","heat","inventory_pressure","inventory_free_low",
            "ground_unknown","visual_front_near","visual_front_mid","visual_front_far",
            "visual_bedrock_front","visual_bedrock_left","visual_bedrock_right",
            "visual_bedrock_any","visual_solid_front","visual_solid_left",
            "visual_solid_right","visual_fluid_front","visual_open_front",
            "visual_unloaded_front","visual_reliability","visual_motion_blocked",
            "visual_underfoot_solid","visual_underfoot_fluid","visual_underfoot_unknown",
            "visual_feet_obstructed","visual_face_obstructed","visual_ceiling_obstructed",
            "visual_front_feet_obstructed","visual_front_face_obstructed",
            "visual_front_head_obstructed","visual_front_step_possible",
            "visual_front_support","visual_front_cliff","visual_front_safe",
            "visual_left_feet_obstructed","visual_right_feet_obstructed",
            "visual_left_support","visual_right_support","visual_left_safe","visual_right_safe",
            "visual_front_breakable","visual_front_protected","visual_front_hazard",
            "visual_body_known","visual_body_unknown"})add(n);
        for(String slot:SLOT_NAMES)for(String material:MATERIAL_TYPES)
            add("voxel_"+slot+"_"+material);
        for(int row=0;row<RETINA_ROWS;row++)for(int col=0;col<RETINA_COLS;col++)
            for(String mode:new String[]{"depth","structure","resource","danger"})
                add(String.format(Locale.ROOT,"retina_y%02d_x%02d_%s",row,col,mode));
        for(int y=0;y<3;y++)for(int x=0;x<3;x++)
            for(String material:SECTOR_TYPES)add("retina_sector_"+y+"_"+x+"_"+material);
        for(String n:new String[]{"retina_known_fraction","retina_coverage","retina_motion",
            "retina_novelty","retina_fovea_depth","retina_fovea_ore",
            "retina_fovea_hazard"})add(n);
        if(NAMES.size()>SIZE)throw new IllegalStateException("Sensor inputs exceed "+SIZE);
    }
    private static void add(String name){MAP.put(name,NAMES.size());NAMES.add(name);}
    public static int channels(){return NAMES.size();}
    public static int index(String channel){return MAP.getOrDefault(channel,-1);}
    public static List<String> channelNames(){return Collections.unmodifiableList(NAMES);}
    public static final class Frame {
        public final float[] values=new float[SIZE];
        public int voxelReads,retinaRays,knownRays,unloadedRays,bodyKnown,bodyTotal;
        public int salientResources,salientThreats;
        public long observedTick;
        public float yawRadians, verticalSpeed;
        public boolean grounded;
        public float reliability;
        public float value(String key){int slot=index(key);return slot<0?0:values[slot];}
        public void set(String key,double value){
            int slot=index(key);
            if(slot>=0 && Double.isFinite(value))
                values[slot]=Math.max(-1f,Math.min(1f,(float)value));
        }
    }
    private static final String[] REL={"front","left","right"};
    private static final String[] CLASS={"air","rock","soil","wood","foliage","ore",
        "water","lava","bedrock","crafted","hazard","other","unknown"};
    private DreamSensory(){}
    private static final class Sampler {
        final ServerLevel level;final Frame frame;
        final Map<Long,BlockState> cache=new HashMap<>();
        Sampler(ServerLevel l,Frame f){level=l;frame=f;}
        BlockState get(BlockPos pos){
            if(!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos))return null;
            if(!cache.containsKey(pos.asLong())){
                if(frame.voxelReads>=MAX_VOXEL_READS)return null;
                frame.voxelReads++;
                cache.put(pos.asLong(),level.getBlockState(pos));
            }
            return cache.get(pos.asLong());
        }
    }
    private static String material(BlockState state){
        if(state==null)return "unknown";
        if(state.isAir())return "air";
        if(state.is(net.minecraft.world.level.block.Blocks.BEDROCK))return "bedrock";
        if(state.is(net.minecraft.world.level.block.Blocks.LAVA))return "lava";
        if(state.is(net.minecraft.world.level.block.Blocks.WATER))return "water";
        if(hazard(state))return "hazard";
        if(state.is(BlockTags.LOGS)||state.is(BlockTags.PLANKS))return "wood";
        if(state.is(BlockTags.LEAVES)||state.is(BlockTags.FLOWERS))return "foliage";
        String name=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if(name.endsWith("_ore")||name.equals("ancient_debris"))return "ore";
        if(name.matches(".*(?:stone|cobble|basalt|tuff|deepslate|granite|andesite|diorite|obsidian).*"))return "rock";
        if(name.matches(".*(?:dirt|sand|mud|gravel|clay|soil|podzol|mycelium).*"))return "soil";
        if(name.matches(".*(?:brick|chest|barrel|furnace|crafting_table|door|stairs|slab|glass|bed|fence).*"))return "crafted";
        if(name.matches(".*(?:grass|fern|flower|vine|sapling).*"))return "foliage";
        return "other";
    }
    private static boolean hazard(BlockState s){
        return s.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
            || s.is(net.minecraft.world.level.block.Blocks.FIRE)
            || s.is(net.minecraft.world.level.block.Blocks.CACTUS)
            || s.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
            || s.is(net.minecraft.world.level.block.Blocks.LAVA);
    }
    private static boolean obstructed(Sampler sampler,BlockPos pos){
        BlockState s=sampler.get(pos);
        return s==null || !s.getCollisionShape(sampler.level,pos).isEmpty();
    }
    private static boolean support(Sampler sampler,BlockPos pos){
        BlockState s=sampler.get(pos);
        return s!=null && !hazard(s)
            && s.isFaceSturdy(sampler.level,pos,Direction.UP);
    }
    public static Frame observe(ServerLevel level, FakePlayerEntity body,ServerPlayer player,
                                float[] legacy,Frame prev,boolean movementOrdered){
        Frame f=new Frame();
        f.observedTick=level.getGameTime();
        if(legacy!=null)System.arraycopy(legacy,0,f.values,0,Math.min(12,legacy.length));
        Sampler sample=new Sampler(level,f);
        BlockPos here=body.blockPosition();
        Vec3 delta=body.getDeltaMovement();
        double yaw=Math.toRadians(body.getYRot()),pitch=Math.toRadians(body.getXRot());
        f.yawRadians=(float)yaw;
        f.verticalSpeed=(float)delta.y;
        f.grounded=body.onGround();
        if(prev!=null){
            double deltaYaw=Math.atan2(Math.sin(yaw-prev.yawRadians),Math.cos(yaw-prev.yawRadians));
            double elapsed=Math.max(.05,(level.getGameTime()-prev.observedTick)/20.);
            double turn=deltaYaw/elapsed/3.2;
            f.set("proprio_yaw_left",Math.max(0,-turn));
            f.set("proprio_yaw_right",Math.max(0,turn));
            f.set("proprio_impact",!prev.grounded && f.grounded
                ?Math.max(0,-prev.verticalSpeed)/.70:0);
        }
        // Minecraft yaw: forward (-sin(yaw),cos(yaw)), adapted from BrainFly.
        double fx=-Math.sin(yaw),fz=Math.cos(yaw), lx=-Math.cos(yaw),lz=-Math.sin(yaw);
        double vf=delta.x*fx+delta.z*fz,vl=delta.x*lx+delta.z*lz;
        f.set("proprio_forward",Math.max(0,vf)/.32);
        f.set("proprio_backward",Math.max(0,-vf)/.32);
        f.set("proprio_left",Math.max(0,vl)/.32);
        f.set("proprio_right",Math.max(0,-vl)/.32);
        f.set("proprio_up",Math.max(0,delta.y)/.60);
        f.set("proprio_down",Math.max(0,-delta.y)/.60);
        f.set("proprio_pitch_up",Math.max(0,-pitch)/(Math.PI/2));
        f.set("proprio_pitch_down",Math.max(0,pitch)/(Math.PI/2));
        f.set("proprio_ground_load",body.onGround()?1:0);
        f.set("proprio_airborne",body.onGround()?0:1);
        f.set("proprio_motor_effort",movementOrdered?1:0);
        f.set("proprio_motion_error",movementOrdered && body.onGround()
              ?1-Math.min(1,Math.hypot(delta.x,delta.z)/.035):0);
        if(prev!=null)f.set("retina_motion",Math.min(1,Math.hypot(delta.x,delta.z)*2));
        BlockPos front=here.offset((int)Math.round(fx),0,(int)Math.round(fz));
        BlockPos left=here.offset((int)Math.round(lx),0,(int)Math.round(lz));
        BlockPos right=here.offset((int)Math.round(-lx),0,(int)Math.round(-lz));
        BlockPos[] vox={
            here.below(),here.above(2),front,front.above(),
            front.above(2),front.below(),left,right
        };
        for(int i=0;i<vox.length;i++){
            BlockState state=sample.get(vox[i]);
            if(state!=null)f.bodyKnown++;
            f.bodyTotal++;
            f.set("voxel_"+SLOT_NAMES[i]+"_"+material(state),1);
        }
        boolean frontFoot=obstructed(sample,front),frontFace=obstructed(sample,front.above());
        boolean frontHead=obstructed(sample,front.above(2));
        boolean ground=support(sample,front.below());
        boolean leftFoot=obstructed(sample,left),rightFoot=obstructed(sample,right);
        boolean leftGround=support(sample,left.below()),rightGround=support(sample,right.below());
        boolean cliff=!ground && !support(sample,front.below(2));
        f.set("visual_front_feet_obstructed",frontFoot?1:0);
        f.set("visual_front_face_obstructed",frontFace?1:0);
        f.set("visual_front_head_obstructed",frontHead?1:0);
        f.set("visual_front_support",ground?1:0);
        f.set("visual_front_cliff",cliff?1:0);
        f.set("visual_front_safe",!frontFoot&&!frontFace&&ground?1:0);
        f.set("visual_front_step_possible",frontFoot&&!frontHead&&!frontFace?1:0);
        f.set("visual_left_feet_obstructed",leftFoot?1:0);
        f.set("visual_right_feet_obstructed",rightFoot?1:0);
        f.set("visual_left_support",leftGround?1:0);
        f.set("visual_right_support",rightGround?1:0);
        f.set("visual_left_safe",!leftFoot&&leftGround?1:0);
        f.set("visual_right_safe",!rightFoot&&rightGround?1:0);
        f.set("obstacle_forward",frontFoot||frontFace?1:0);
        f.set("obstacle_left",leftFoot?1:0);
        f.set("obstacle_right",rightFoot?1:0);
        f.set("cliff_forward",cliff?1:0);
        f.set("ground_contact",body.onGround()?1:0);
        f.set("falling",Math.max(0,-delta.y)/.6);
        f.set("health_low",1-body.getHealth()/Math.max(1,body.getMaxHealth()));
        f.set("head_submerged",body.isUnderWater()?1:0);
        f.set("breathable",!body.isUnderWater()?1:0);
        f.set("heat",body.isOnFire()?1:0);
        f.set("ground_unknown",f.bodyKnown<f.bodyTotal?1:0);
        f.set("visual_body_known",f.bodyKnown==f.bodyTotal?1:0);
        f.set("visual_body_unknown",f.bodyKnown<f.bodyTotal?1:0);
        f.set("vision_forward",frontFoot||frontFace?1:0);
        f.set("vision_left",leftFoot?1:0);
        f.set("vision_right",rightFoot?1:0);
        f.set("visual_underfoot_solid",support(sample,here.below())?1:0);
        f.set("visual_underfoot_unknown",sample.get(here.below())==null?1:0);
        f.set("visual_underfoot_fluid",body.isInWater()?1:0);
        f.set("visual_motion_blocked",movementOrdered && Math.hypot(delta.x,delta.z)<.025?1:0);
        f.set("visual_unloaded_front",sample.get(front)==null?1:0);
        f.set("visual_front_hazard",hazardOrUnknown(sample.get(front))?1:0);
        BlockState frontState=sample.get(front);
        String frontMaterial=material(frontState);
        f.set("visual_open_front",frontState!=null && !frontFoot&&!frontFace?1:0);
        f.set("visual_solid_front",frontState!=null && frontFoot?1:0);
        f.set("visual_fluid_front",("water".equals(frontMaterial)||"lava".equals(frontMaterial))?1:0);
        f.set("visual_bedrock_front","bedrock".equals(frontMaterial)?1:0);
        f.set("visual_bedrock_any","bedrock".equals(frontMaterial)?1:0);
        f.set("visual_solid_left",leftFoot?1:0);
        f.set("visual_solid_right",rightFoot?1:0);
        f.set("visual_bedrock_left","bedrock".equals(material(sample.get(left)))?1:0);
        f.set("visual_bedrock_right","bedrock".equals(material(sample.get(right)))?1:0);
        f.set("visual_front_protected","bedrock".equals(frontMaterial)?1:0);
        f.set("visual_front_breakable",frontFoot && sample.get(front)!=null && !hazardOrUnknown(sample.get(front))?1:0);
        f.set("players_near",Math.min(1,level.players().size()/4.));
        // Retinotopic 11x7 fan. Each cell's unknown state remains zero; NEVER
        // classify unloaded space as empty. Hard cap on total block reads.
        int[] sectorCount=new int[9];int[][] sectorM=new int[9][SECTOR_TYPES.length];
        double novel=0;int observed=0;
        for(int y=0;y<RETINA_ROWS;y++)for(int x=0;x<RETINA_COLS;x++){
            if(f.voxelReads>=MAX_VOXEL_READS)break;
            double az=yaw+((x+.5)/RETINA_COLS-.5)*1.7;
            double el=pitch+((y+.5)/RETINA_ROWS-.5)*1.16;
            double dx=-Math.sin(az)*Math.cos(el),dy=-Math.sin(el),dz=Math.cos(az)*Math.cos(el);
            BlockState hit=null;
            BlockPos hitPos=null;
            boolean unloaded=false,seen=false;
            int cell=y*RETINA_COLS+x;
            double foundDistance=22;
            long last=Long.MIN_VALUE;
            for(double dist=.65;dist<=22;dist+=dist<9?.96:1.4){
                BlockPos pos=BlockPos.containing(body.getX()+dx*dist,
                     body.getEyeY()+dy*dist,body.getZ()+dz*dist);
                if(pos.asLong()==last)continue;last=pos.asLong();
                BlockState state=sample.get(pos);
                if(state==null){unloaded=true;break;}
                seen=true;
                if(!state.isAir() && (!state.getCollisionShape(level,pos).isEmpty()
                    || !state.getFluidState().isEmpty() || hazard(state))){
                    hit=state;hitPos=pos;foundDistance=dist;break;
                }
                if(f.voxelReads>=MAX_VOXEL_READS){unloaded=true;break;}
            }
            f.retinaRays++;
            if(unloaded){f.unloadedRays++;continue;}
            if(!seen)continue;
            f.knownRays++;
            observed++;
            String prefix=String.format(Locale.ROOT,"retina_y%02d_x%02d_",y,x);
            if(hit!=null){
                f.set(prefix+"depth",1-Math.min(1,foundDistance/22));
                f.set(prefix+"structure",1);
                String mat=material(hit);
                if("ore".equals(mat)||"wood".equals(mat)){f.set(prefix+"resource",1);f.salientResources++;}
                if("hazard".equals(mat)||"lava".equals(mat)){f.set(prefix+"danger",1);f.salientThreats++;}
                int sector=(y*3/RETINA_ROWS)*3+(x*3/RETINA_COLS);
                sectorCount[sector]++;
                for(int i=0;i<SECTOR_TYPES.length;i++)if(mat.equals(SECTOR_TYPES[i]))sectorM[sector][i]++;
                if(x==RETINA_COLS/2&&y==RETINA_ROWS/2){
                    f.set("retina_fovea_depth",1-foundDistance/22);
                    f.set("retina_fovea_ore","ore".equals(mat)?1:0);
                    f.set("retina_fovea_hazard",hazard(hit)?1:0);
                }
                if(prev!=null && prev.value(prefix+"structure")<.5f)novel++;
            }
        }
        for(int sy=0;sy<3;sy++)for(int sx=0;sx<3;sx++){
            int index=sy*3+sx;
            for(int i=0;i<SECTOR_TYPES.length;i++)
                f.set("retina_sector_"+sy+"_"+sx+"_"+SECTOR_TYPES[i],
                      sectorCount[index]==0?0:(double)sectorM[index][i]/sectorCount[index]);
        }
        f.reliability=(float)f.knownRays/(RETINA_ROWS*RETINA_COLS);
        f.set("retina_known_fraction",f.reliability);
        f.set("retina_coverage",(float)f.retinaRays/(RETINA_ROWS*RETINA_COLS));
        f.set("retina_novelty",observed==0?0:novel/observed);
        f.set("visual_reliability",f.reliability);
        f.set("visual_front_near",f.value("retina_y03_x05_depth"));
        return f;
    }
    private static boolean hazardOrUnknown(BlockState state){
        return state==null || hazard(state);
    }
}
