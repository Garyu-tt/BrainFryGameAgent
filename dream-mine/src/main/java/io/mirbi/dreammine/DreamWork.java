package io.mirbi.dreammine;

import dev.duzo.players.entities.FakePlayerEntity;
import dev.duzo.players.entities.inventory.FakePlayerInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

/**
 * Server-only bounded action planner for the embedded H01 controller.
 * Implements visible causal actions (seek tree -> harvest -> collect -> craft
 * planks -> place workbench); no background processes, services or network.
 *
 * Safety: destructive actions require the world owner's explicit opt-in.
 * Manual eggs remain unlimited; cognition is time-sliced per loaded entity.
 */
public final class DreamWork {
    private DreamWork(){}
    private static final Map<UUID,WorkState> ACTIVE=new HashMap<>();
    private static final String KEY="dreammine_embedded_h01_v017";
    public static final class WorkState {
        public final EmbeddedH01 cortex;
        public final DreamExecutive executive=new DreamExecutive();
        public final LinkedHashSet<Long> surveyedCells=new LinkedHashSet<>();
        public final LinkedHashSet<Long> inspectedLogs=new LinkedHashSet<>();
        public long targetPositionForInspection;
        public BlockPos explorationOrigin;
        public String initialMaterial="UNKNOWN";
        public int socialFrames, observedPlayerMovement, intentDecisions;
        public DreamSensory.Frame perception;
        public int sensoryFrames=0;
        public long lastSensedTick=-100000;

        public String goal="INITIALIZING",plan="OBSERVE";
        public BlockPos target,stand,harvestedAt;
        public long startTick,workUntil,nextScan,decisionTick,lastSave;
        public long gathered,crafted,built;
        public boolean finishedTable;
        public int decision=-1;
        public WorkState(FakePlayerEntity entity) {
            cortex=new EmbeddedH01(entity.getUUID().getMostSignificantBits()
                                    ^entity.getUUID().getLeastSignificantBits());
            CompoundTag data=entity.getPersistentData().getCompound(KEY);
            cortex.load(data.getCompound("neural"));
            executive.load(data.getCompound("executive"),entity.level().getGameTime());
            for(long log:data.getLongArray("inspectedLogs")){
                inspectedLogs.add(log);
                if(inspectedLogs.size()>64)inspectedLogs.remove(inspectedLogs.iterator().next());
            }
            for(long cell:data.getLongArray("surveyedCells")){
                surveyedCells.add(cell);
                if(surveyedCells.size()>128)surveyedCells.remove(surveyedCells.iterator().next());
            }
            if(!data.contains("neural")){
                DreamData shared=DreamData.get(entity.getServer());
                if(shared.neuralContributions()>0)
                    cortex.inheritReadout(shared.neuralCulture(),.75f);
            }
            gathered=Math.max(0,data.getLong("gathered"));
            crafted=Math.max(0,data.getLong("crafted"));
            built=Math.max(0,data.getLong("built"));
            finishedTable=data.getBoolean("finishedTable");
        }
        private static long cellKey(BlockPos pos){
            long x=Math.floorDiv(pos.getX(),8);
            long z=Math.floorDiv(pos.getZ(),8);
            return (x<<32) ^ (z&0xffffffffL);
        }
        boolean previouslySurveyed(BlockPos pos){return surveyedCells.contains(cellKey(pos));}
        void recordSurvey(BlockPos pos){
            surveyedCells.add(cellKey(pos));
            while(surveyedCells.size()>128)surveyedCells.remove(surveyedCells.iterator().next());
        }
        public void save(FakePlayerEntity e,long tick){
            CompoundTag data=new CompoundTag();
            data.put("neural",cortex.save());
            data.put("executive",executive.save());
            long[] visited=new long[surveyedCells.size()];
            int k=0;for(long cell:surveyedCells)visited[k++]=cell;
            data.putLongArray("surveyedCells",visited);
            long[] knownLogs=new long[inspectedLogs.size()];
            int logIndex=0;for(long log:inspectedLogs)knownLogs[logIndex++]=log;
            data.putLongArray("inspectedLogs",knownLogs);
            data.putLong("gathered",gathered);
            data.putLong("crafted",crafted);
            data.putLong("built",built);
            data.putBoolean("finishedTable",finishedTable);
            e.getPersistentData().put(KEY,data);
            lastSave=tick;
        }
    }
    public static WorkState forEntity(FakePlayerEntity dream) {
        return ACTIVE.computeIfAbsent(dream.getUUID(),u->new WorkState(dream));
    }
    public static void release(FakePlayerEntity dream){
        WorkState state=ACTIVE.remove(dream.getUUID());
        if(state!=null)state.save(dream,dream.level().getGameTime());
    }
    public static void saveAndReleaseAll(){
        // Invoked by world unload? Individual saving is driven by server ticks.
        ACTIVE.clear();
    }
    /** Every four server ticks: only active harvesting/collection is processed. */
    public static boolean tick(ServerLevel level,FakePlayerEntity dream,
                               ServerPlayer anchor,boolean worldActions){
        WorkState w=forEntity(dream);
        long now=level.getGameTime();
        if(now-w.lastSave>=1200){w.save(dream,now);}
        // Perception continues while the entity is walking; decision events alone
        // are not enough to create a sensorimotor learning loop.
        int population=DreamData.get(level.getServer()).livingCount();
        int sensoryInterval=population<=4?40:population<=16?80:population<=64?160:240;
        if(now-w.lastSensedTick>=sensoryInterval){
            float[] legacy=new float[EmbeddedH01.INPUTS];
            int logs=count(dream.getInventory(),true),planks=count(dream.getInventory(),false);
            legacy[0]=0; // no asserted target until the resource scan provides evidence
            legacy[1]=logs>0?1:0;
            legacy[2]=planks>=4?1:0;
            legacy[3]=dream.distanceToSqr(anchor)<100?1:0;
            legacy[4]=Math.min(1,dream.getHealth()/Math.max(1,dream.getMaxHealth()));
            legacy[5]=dream.isInWater()?1:0;
            legacy[6]=(float)Math.min(1,dream.distanceToSqr(anchor)/10000);
            legacy[7]=worldActions?1:0;
            legacy[8]=w.finishedTable?1:0;
            legacy[9]=(float)Math.min(1,w.crafted/8.);
            legacy[10]=(float)Math.min(1,w.gathered/4.);
            legacy[11]=level.isDay()?1:0;
            w.perception=DreamSensory.observe(level,dream,anchor,legacy,w.perception,
                                                   !dream.getNavigation().isDone());
            w.sensoryFrames++;
            w.lastSensedTick=now;
            w.cortex.senseFrame(w.perception.values);
        }

        w.executive.expire(now);
        if("WATCH_PLAYER".equals(w.goal) && w.executive.phase()==DreamExecutive.Phase.COMMITTED){
            if(now-w.decisionTick>240 || !w.executive.active(now)){
                if(w.socialFrames>=8){
                    w.executive.complete("PLAYER_SEEN_IN_"+w.socialFrames+"_FRAMES",now);
                    reinforcement(level,w,.40f);
                }else w.executive.fail("INSUFFICIENT_OBSERVATION_FRAMES",now);
                w.goal="SOCIAL_OBSERVATION_COMPLETE";
                return false;
            }
            dream.getNavigation().stop();
            dream.getLookControl().setLookAt(anchor,30,30);
            if(now%20==0){
                w.socialFrames++;
                if(anchor.getDeltaMovement().horizontalDistanceSqr()>.006)w.observedPlayerMovement++;
                w.executive.progress("ATTEND_PLAYER","PLAYER_VISIBLE_IN_RANGE",now);
            }
            return true;
        }
        if(!worldActions && "HARVEST_TREE".equals(w.goal)){
            if(w.target!=null)level.destroyBlockProgress(dream.getId(),w.target,-1);
            w.target=null;w.stand=null;w.workUntil=0;
            w.goal="WORLD_ACTIONS_DISABLED";
            w.executive.interrupt("WORLD_ACTIONS_DISABLED",now);
            return false;
        }
        if(!worldActions && "COLLECT_DROPS".equals(w.goal)){
            w.goal="WORLD_ACTIONS_DISABLED";
            w.harvestedAt=null;
            w.executive.interrupt("WORLD_ACTIONS_DISABLED",now);
            return false;
        }
        if("EXAMINE_TREE".equals(w.goal) && w.target!=null){
            if(!level.hasChunkAt(w.target) || !level.getBlockState(w.target).is(BlockTags.OVERWORLD_NATURAL_LOGS)){
                w.target=null;w.goal="SEARCH_NEW_RESOURCE";
                w.executive.fail("TARGET_LOG_DISAPPEARED",now);
                return false;
            }
            if(dream.distanceToSqr(w.target.getX()+.5,w.target.getY()+.5,w.target.getZ()+.5)>12.25){
                if(w.stand!=null && (dream.getNavigation().isDone() || now%40==0))
                    dream.getNavigation().moveTo(w.stand.getX()+.5,w.stand.getY(),w.stand.getZ()+.5,1.0);
                if(now-w.decisionTick>400){
                    w.target=null;w.goal="EXAMINE_PATH_FAILED";
                    w.executive.fail("NO_PATH_TO_INSPECTION",now);
                }
                return true;
            }
            dream.getNavigation().stop();
            dream.getLookControl().setLookAt(w.target.getX()+.5,w.target.getY()+1,w.target.getZ()+.5);
            if(now-w.decisionTick>=60){
                w.goal="RESOURCE_IDENTIFIED";
                w.plan="RECOGNIZED_LOG_NO_WORLD_CHANGES";
                w.target=null;
                w.nextScan=now+320;
                // Inspect each actual observed tree only once until LRU eviction.
                // Don't keep returning to the exact same trunk.
                w.inspectedLogs.add(w.targetPositionForInspection);
                while(w.inspectedLogs.size()>64)w.inspectedLogs.remove(w.inspectedLogs.iterator().next());
                if(w.executive.phase()==DreamExecutive.Phase.COMMITTED)
                    w.executive.complete("OBSERVED_NATURAL_LOG_IN_WORLD",now);
                reinforcement(level,w,.5f);
            }
            return true;
        }
        if(!worldActions)return false;
        if(w.target!=null && "HARVEST_TREE".equals(w.goal)){
            if(!DreamLeash.withinLimit(w.target.distSqr(anchor.blockPosition()))
               || !level.hasChunkAt(w.target)
               || !level.getBlockState(w.target).is(BlockTags.OVERWORLD_NATURAL_LOGS)){
                level.destroyBlockProgress(dream.getId(),w.target,-1);
                w.target=null;w.goal="SEARCHING";reinforcement(level,w,-.4f);
                w.executive.fail("TARGET_NOT_AVAILABLE",now);
                return false;
            }
            if(dream.distanceToSqr(w.target.getX()+.5,w.target.getY()+.5,w.target.getZ()+.5)>12.25){
                if(w.stand!=null && (dream.getNavigation().isDone() || now%40==0))
                    dream.getNavigation().moveTo(w.stand.getX()+.5,w.stand.getY(),w.stand.getZ()+.5,1);
                if(now>w.startTick+450){
                    w.target=null;w.goal="REPLAN_UNREACHABLE";reinforcement(level,w,-.6f);
                    w.executive.fail("REACH_TARGET_STALLED",now);
                }
                return true;
            }
            dream.getNavigation().stop();
            // Start the harvest clock only after the body reached its destination.
            if(w.workUntil==0){
                w.startTick=now;
                w.workUntil=now+100;
            }
            dream.getLookControl().setLookAt(w.target.getX()+.5,w.target.getY()+.5,w.target.getZ()+.5);
            if(now<w.workUntil){
                int progress=(int)Math.min(9,(10*(now-w.startTick))/Math.max(1,w.workUntil-w.startTick));
                level.destroyBlockProgress(dream.getId(),w.target,progress);
                return true;
            }
            level.destroyBlockProgress(dream.getId(),w.target,-1);
            BlockPos harvested=w.target;
            w.target=null;
            if(level.destroyBlock(harvested,true,dream)){
                w.goal="COLLECT_DROPS";w.plan="PICKUP_LOG";w.harvestedAt=harvested;
                w.gathered++;
                w.executive.progress("PICKUP_PENDING","BLOCK_REMOVED_VERIFIED",now);
                reinforcement(level,w,.25f);
                w.decisionTick=now;
            } else {
                w.goal="REPLAN_FAILED";reinforcement(level,w,-.8f);
                w.executive.fail("SERVER_DENIED_BLOCK_BREAK",now);
            }
            return true;
        }
        if("COLLECT_DROPS".equals(w.goal)){
            int count=pickupWood(level,dream,w.harvestedAt);
            if(count>0){
                w.goal="CRAFT_PENDING";w.harvestedAt=null;
                if(w.executive.phase()==DreamExecutive.Phase.COMMITTED)
                    w.executive.complete("ITEM_TRANSFERRED_TO_REAL_INVENTORY",now);
                reinforcement(level,w,.8f);
                return false;
            }
            if(now-w.decisionTick<90)return true; // drops require a few ticks to settle
            w.goal="SEARCHING";w.harvestedAt=null;
            w.executive.fail("DROPS_NOT_COLLECTED_WITHIN_TIMEOUT",now);
            return false;
        }
        return false;
    }
    public static void pause(ServerLevel level,FakePlayerEntity dream,String reason){
        WorkState w=forEntity(dream);
        if(w.target!=null)level.destroyBlockProgress(dream.getId(),w.target,-1);
        w.executive.interrupt(reason,level.getGameTime());
        w.target=null;w.stand=null;w.harvestedAt=null;
        w.goal="SAFETY_INTERRUPT";w.plan=reason;
        w.save(dream,level.getGameTime());
    }
    /** Returns true when the agent has committed to a non-wandering plan. */
    public static boolean think(ServerLevel level,FakePlayerEntity dream,
                                ServerPlayer anchor,boolean worldActions){
        WorkState w=forEntity(dream);
        long now=level.getGameTime();
        if(w.executive.active(now)){
            int action=w.executive.action();
            if(action==0) { // environment inquiry requires observed progress
                if(!dream.getNavigation().isDone()){
                    w.goal="SCOUTING";w.plan="VISIT_NEW_TERRAIN -> COMPARE_OBSERVATIONS";
                    return true;
                }
                double distance=w.explorationOrigin==null?0:
                    Math.sqrt(dream.blockPosition().distSqr(w.explorationOrigin));
                if(distance>=4 && level.hasChunkAt(dream.blockPosition().below())){
                    BlockPos nowAt=dream.blockPosition();
                    String material=net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .getKey(level.getBlockState(nowAt.below()).getBlock()).toString();
                    boolean newCell=!w.previouslySurveyed(nowAt);
                    boolean changedMaterial=!material.equals(w.initialMaterial);
                    boolean hasScene=w.perception!=null
                        && w.perception.observedTick>=w.executive.lastChosenTick()
                        && w.perception.knownRays>=12;
                    if(hasScene && (newCell||changedMaterial)){
                        w.recordSurvey(nowAt);
                        DreamData.get(level.getServer()).discovered(material);
                        w.executive.complete("NEW_SCENE_VERIFIED_"+
                            (newCell?"CELL":"MATERIAL")+"_RAYS_"+w.perception.knownRays,now);
                        reinforcement(level,w,.5f);
                        w.goal="SCOUT_COMPLETED";
                        return true;
                    }
                }
                if(w.executive.age(now)>=180) {
                    w.executive.fail("SCOUT_DID_NOT_REACH_NEW_AREA",now);
                    reinforcement(level,w,-.35f);
                }else return true;
            }else if(action==4 && "WATCH_PLAYER".equals(w.goal))return true;
            else if(action==1 && w.target!=null)return true;
        }
        if(w.executive.phase()==DreamExecutive.Phase.DEFERRED){
            w.target=null;w.stand=null;
        }
        if(!w.executive.ready(now)) {
            w.goal=w.executive.phase()==DreamExecutive.Phase.DEFERRED?"RECONSIDERING_PLAN":"WAIT_FOR_RESULT";
            w.plan=w.executive.evidence();
            return true;
        }

        FakePlayerInventory inventory=dream.getInventory();
        int logs=count(inventory,true),planks=count(inventory,false);
        BlockPos tree=null,stand=null;
        if(logs==0 && planks<4 && now>=w.nextScan){
            var found=findTree(level,dream,anchor,w);
            if(found!=null){tree=found[0];stand=found[1];}
            int population=DreamData.get(level.getServer()).livingCount();
            w.nextScan=now+(population<=4?200:population<=16?400:800);
        }
        float[] sensors=new float[EmbeddedH01.INPUTS];
        sensors[0]=tree!=null?1:0;
        sensors[1]=logs>0?1:0;
        sensors[2]=planks>=4?1:0;
        sensors[3]=dream.distanceToSqr(anchor)<100?1:0;
        sensors[4]=(float)Math.min(1,dream.getHealth()/Math.max(1,dream.getMaxHealth()));
        sensors[5]=dream.isInWater()?1:0;
        sensors[6]=(float)Math.min(1,dream.distanceToSqr(anchor)/10000);
        sensors[7]=worldActions?1:0;
        sensors[8]=w.finishedTable?1:0;
        sensors[9]=(float)Math.min(1,w.crafted/8.);
        sensors[10]=(float)Math.min(1,w.gathered/4.);
        sensors[11]=level.isDay()?1:0;
        boolean[] options={
            w.executive.canChooseAction(0,now),
            tree!=null && w.executive.canChooseAction(1,now),
            worldActions && logs>0 && w.executive.canChooseAction(2,now),
            worldActions && planks>=4 && !w.finishedTable
                         && w.executive.canChooseAction(3,now),
            dream.distanceToSqr(anchor)<49 && w.executive.canChooseAction(4,now)
        };
        // BrainFly-compatible retina/body/proprio channels drive the recurrent neural
        // runtime, rather than only the 12-element handcrafted resource vector.
        // Inherit the latest continuous sensory frame. Resource affordances are
        // updated here from the inventory and verified tree scan, not fabricated.
        if(w.perception==null || now-w.lastSensedTick>120){
            w.perception=DreamSensory.observe(level,dream,anchor,sensors,w.perception,
                                               !dream.getNavigation().isDone());
            w.sensoryFrames++;
            w.lastSensedTick=now;
        }
        System.arraycopy(sensors,0,w.perception.values,0,12);
        int choice=w.cortex.choose(w.perception.values,options);
        w.decision=choice;
        if(choice<0)return true;
        w.intentDecisions++;
        long commitment=choice==1?640:choice==4?260:choice==0?260:160;
        w.executive.commit(choice,EmbeddedH01.ACTION_NAMES[choice],now,commitment);
        if(choice==1 && tree!=null){
            if(!worldActions){
                w.goal="EXAMINE_TREE";
                w.plan="LOCATE -> APPROACH -> OBSERVE -> LEARN";
                w.target=tree;w.stand=stand;w.decisionTick=now;
                w.targetPositionForInspection=tree.asLong();
                dream.getNavigation().moveTo(stand.getX()+.5,stand.getY(),stand.getZ()+.5,1.05);
                return true;
            }
            w.goal="HARVEST_TREE";w.plan="REACH_LOG -> BREAK -> PICKUP -> CRAFT";
            w.target=tree;w.stand=stand;
            w.startTick=now;w.workUntil=0; // five-second mining begins on arrival
            dream.getNavigation().moveTo(stand.getX()+.5,stand.getY(),stand.getZ()+.5,1.05);
            return true;
        }
        if(choice==2){
            w.goal="CRAFT_PLANKS";w.plan="1 LOG -> 4 PLANKS";
            if(craftPlanks(inventory)){
                w.crafted++;
                w.executive.complete("LOG_CONSUMED_AND_PLANKS_IN_INVENTORY",now);
                reinforcement(level,w,1f);
            }else{
                w.executive.fail("CRAFT_NOT_POSSIBLE_OR_INVENTORY_FULL",now);
                reinforcement(level,w,-.6f);
            }
            w.nextScan=now+80;
            return true;
        }
        if(choice==3){
            w.goal="BUILD_WORKBENCH";w.plan="4 PLANKS -> CRAFTING_TABLE";
            if(placeTable(level,dream,anchor,inventory)){
                w.finishedTable=true;w.built++;
                w.executive.complete("CRAFTING_TABLE_BLOCK_EXISTS_IN_WORLD",now);
                reinforcement(level,w,1f);
                w.goal="WORKBENCH_BUILT";w.plan="FIND_NEXT_TASK";
            }else{
                w.executive.fail("TABLE_LOCATION_OR_MATERIAL_INVALID",now);
                reinforcement(level,w,-.5f);
            }
            return true;
        }
        if(choice==4){
            w.goal="WATCH_PLAYER";w.plan="SOCIAL_OBSERVATION";
            w.socialFrames=0;w.observedPlayerMovement=0;
            w.decisionTick=now;
            dream.getNavigation().stop();
            dream.getLookControl().setLookAt(anchor,30,30);
            return true;
        }
        w.goal="SCOUT_NEW_AREA";
        w.plan="NAVIGATE -> OBSERVE -> VERIFY_VISIT -> LEARN";
        w.explorationOrigin=dream.blockPosition();
        w.recordSurvey(dream.blockPosition());
        if(level.hasChunkAt(dream.blockPosition().below()))
            w.initialMaterial=net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(level.getBlockState(dream.blockPosition().below()).getBlock()).toString();
        return false;
    }
    public static String senses(FakePlayerEntity entity){
        WorkState w=forEntity(entity);
        DreamSensory.Frame f=w.perception;
        if(f==null)return "No sensory frame yet (wait for a decision)";
        return "Dream Sensors | "+DreamSensory.channels()+" named channels"
            +" | observed="+f.retinaRays+" rays"
            +" | known="+f.knownRays+" unknown="+f.unloadedRays
            +" | voxels="+f.voxelReads
            +" | reliability="+String.format(Locale.ROOT,"%.2f",f.reliability)
            +" | floor_unknown="+f.value("visual_underfoot_unknown")
            +" | front_obstacle="+f.value("obstacle_forward")
            +" | cliff="+f.value("cliff_forward")
            +" | head_submerged="+f.value("head_submerged")
            +" | motion_error="+String.format(Locale.ROOT,"%.2f",f.value("proprio_motion_error"))
            +" | resources_seen="+f.salientResources
            +" | danger_seen="+f.salientThreats;
    }
    public static String debug(FakePlayerEntity entity){
        WorkState w=forEntity(entity);
        int logs=count(entity.getInventory(),true);
        int planks=count(entity.getInventory(),false);
        return "embedded=H01-sensory "+w.cortex.modules()+"x"+EmbeddedH01.COLUMNS
            +" synapses="+w.cortex.synapseCount()
            +" steps="+w.cortex.steps()+" updates="+w.cortex.reinforcementCount()
            +" changed="+w.cortex.changedWeights()
            +" activation="+String.format(Locale.ROOT,"%.3f",w.cortex.activation())
            +" goal="+w.goal+" plan="+w.plan
            +" logs="+logs+" planks="+planks
            +" mined="+w.gathered+" crafted="+w.crafted+" built="+w.built
            +" "+w.executive.debug(entity.level().getGameTime())
            +" social_frames="+w.socialFrames+" moves_seen="+w.observedPlayerMovement
            +" mapped_cells="+w.surveyedCells.size()+" inspected_logs="+w.inspectedLogs.size()
            +" sharedNeural="+DreamData.get(entity.getServer()).neuralContributions()
            +" sensed="+(w.perception==null?"none":
                 w.sensoryFrames+"frames/"+DreamSensory.channels()+"channels/"
                 +w.perception.retinaRays+"rays/"+w.perception.voxelReads+"voxels")
            +" vision_reliability="+(w.perception==null?"n/a":
                 String.format(Locale.ROOT,"%.2f",w.perception.reliability));
    }
    private static void reinforcement(ServerLevel level, WorkState w, float result){
        w.cortex.reward(result);
        if(result>=.5f) DreamData.get(level.getServer()).learnCortex(w.cortex.learnedReadout());
    }
    private static int count(FakePlayerInventory inv,boolean logs) {
        int total=0;
        for(int i=0;i<inv.getContainerSize();i++){
            ItemStack s=inv.getItem(i);
            if((logs&&s.is(ItemTags.LOGS))||(!logs&&s.is(ItemTags.PLANKS)))total+=s.getCount();
        }
        return total;
    }
    private static Item planksFor(Item log) {
        if(log==Items.OAK_LOG||log==Items.STRIPPED_OAK_LOG)return Items.OAK_PLANKS;
        if(log==Items.BIRCH_LOG||log==Items.STRIPPED_BIRCH_LOG)return Items.BIRCH_PLANKS;
        if(log==Items.SPRUCE_LOG||log==Items.STRIPPED_SPRUCE_LOG)return Items.SPRUCE_PLANKS;
        if(log==Items.JUNGLE_LOG||log==Items.STRIPPED_JUNGLE_LOG)return Items.JUNGLE_PLANKS;
        if(log==Items.ACACIA_LOG||log==Items.STRIPPED_ACACIA_LOG)return Items.ACACIA_PLANKS;
        if(log==Items.DARK_OAK_LOG||log==Items.STRIPPED_DARK_OAK_LOG)return Items.DARK_OAK_PLANKS;
        if(log==Items.CHERRY_LOG||log==Items.STRIPPED_CHERRY_LOG)return Items.CHERRY_PLANKS;
        if(log==Items.MANGROVE_LOG||log==Items.STRIPPED_MANGROVE_LOG)return Items.MANGROVE_PLANKS;
        return null;
    }
    private static boolean craftPlanks(FakePlayerInventory inv) {
        for(int slot=0;slot<inv.getContainerSize();slot++){
            ItemStack stack=inv.getItem(slot);
            Item planks=planksFor(stack.getItem());
            if(planks==null || stack.isEmpty())continue;
            ItemStack remainder=inv.addItem(new ItemStack(planks,4));
            if(!remainder.isEmpty())return false; // no wood consumed unless output fit
            inv.removeItem(slot,1);
            return true;
        }
        return false;
    }
    private static int pickupWood(ServerLevel level,FakePlayerEntity entity,BlockPos harvested) {
        if(harvested==null)return 0;
        int n=0;
        var items=level.getEntitiesOfClass(ItemEntity.class,
            new AABB(harvested).inflate(1.75));
        for(ItemEntity e:items){
            if(!e.isAlive() || e.tickCount>80 || !e.getItem().is(ItemTags.LOGS))continue;
            int original=e.getItem().getCount();
            ItemStack left=entity.getInventory().addItem(e.getItem().copy());
            int transferred=original-left.getCount();
            if(transferred<=0)continue;
            n+=transferred;
            if(left.isEmpty())e.discard();else e.setItem(left);
        }
        return n;
    }
    private static boolean placeTable(ServerLevel level,FakePlayerEntity mob,ServerPlayer player,
                                      FakePlayerInventory inv) {
        BlockPos origin=mob.blockPosition();
        BlockPos place=null;
        for(Direction direction:Direction.Plane.HORIZONTAL){
            BlockPos candidate=origin.relative(direction,2);
            if(!level.hasChunkAt(candidate) || !DreamLeash.withinLimit(
                    candidate.distSqr(player.blockPosition())))continue;
            if(level.getBlockState(candidate).isAir()
              && level.getBlockState(candidate.above()).isAir()
              && level.getBlockState(candidate.below()).isFaceSturdy(level,candidate.below(),Direction.UP)
              && level.getWorldBorder().isWithinBounds(candidate)){
                place=candidate;break;
            }
        }
        if(place==null)return false;
        int required=4;
        for(int i=0;i<inv.getContainerSize()&&required>0;i++){
            ItemStack held=inv.getItem(i);
            if(!held.is(ItemTags.PLANKS))continue;
            int take=Math.min(required,held.getCount());
            required-=take;
        }
        if(required>0)return false;
        if(!level.setBlockAndUpdate(place,Blocks.CRAFTING_TABLE.defaultBlockState()))return false;
        required=4;
        for(int i=0;i<inv.getContainerSize()&&required>0;i++){
            ItemStack held=inv.getItem(i);
            if(!held.is(ItemTags.PLANKS))continue;
            int take=Math.min(required,held.getCount());
            inv.removeItem(i,take); required-=take;
        }
        return true;
    }
    private static BlockPos[] findTree(ServerLevel level,FakePlayerEntity mob,
                                       ServerPlayer player,WorkState state) {
        BlockPos at=mob.blockPosition();
        BlockPos best=null,stand=null;
        double bestDist=Double.POSITIVE_INFINITY;
        // Strict work cap: 360 positions and 12 costly natural-tree tests per scan.
        int blockReads=0,logProbes=0;
        for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++){
            if(Math.abs(dx)+Math.abs(dz)>6)continue;
            for(int dy=-1;dy<=2;dy++){
                if(++blockReads>360 || logProbes>12)return best==null?null:new BlockPos[]{best,stand};
                BlockPos log=at.offset(dx,dy,dz);
                if(!level.hasChunkAt(log) || state.inspectedLogs.contains(log.asLong())
                    || !level.getBlockState(log).is(BlockTags.OVERWORLD_NATURAL_LOGS))continue;
                logProbes++;
                if(!isNaturalLog(level,log))continue;
                if(!DreamLeash.withinLimit(log.distSqr(player.blockPosition())))continue;
                for(Direction side:Direction.Plane.HORIZONTAL){
                    BlockPos p=log.relative(side);
                    for(int standY=-1;standY<=1;standY++){
                        BlockPos foot=p.offset(0,standY,0);
                        if(!safe(level,foot))continue;
                        double d=mob.distanceToSqr(foot.getX()+.5,foot.getY(),foot.getZ()+.5);
                        if(d<bestDist){bestDist=d;best=log;stand=foot;}
                    }
                }
            }
        }
        return best==null?null:new BlockPos[]{best,stand};
    }
    private static boolean isNaturalLog(ServerLevel level,BlockPos log){
        if(!level.hasChunkAt(log)||!level.getBlockState(log).is(BlockTags.OVERWORLD_NATURAL_LOGS))
            return false;
        // Restrict to leaves-bearing living trees to avoid most buildings and player inventories.
        for(int dy=0;dy<=5;dy++)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            if(Math.abs(dx)+Math.abs(dz)>3)continue;
            BlockPos p=log.offset(dx,dy,dz);
            if(level.hasChunkAt(p)&&level.getBlockState(p).is(BlockTags.LEAVES))return true;
        }
        return false;
    }
    private static boolean safe(ServerLevel level,BlockPos at){
        return !level.isOutsideBuildHeight(at) && !level.isOutsideBuildHeight(at.above())
            && level.hasChunkAt(at) && level.hasChunkAt(at.below())
            && level.getWorldBorder().isWithinBounds(at)
            && level.getFluidState(at).isEmpty() && level.getFluidState(at.above()).isEmpty()
            && level.getBlockState(at).getCollisionShape(level,at).isEmpty()
            && level.getBlockState(at.above()).getCollisionShape(level,at.above()).isEmpty()
            && level.getBlockState(at.below()).isFaceSturdy(level,at.below(),Direction.UP);
    }
}
