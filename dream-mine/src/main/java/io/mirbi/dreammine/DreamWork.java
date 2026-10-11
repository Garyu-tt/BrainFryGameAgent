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
        public void save(FakePlayerEntity e,long tick){
            CompoundTag data=new CompoundTag();
            data.put("neural",cortex.save());
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
        if(!worldActions && "HARVEST_TREE".equals(w.goal)){
            if(w.target!=null)level.destroyBlockProgress(dream.getId(),w.target,-1);
            w.target=null;w.stand=null;w.workUntil=0;
            w.goal="WORLD_ACTIONS_DISABLED";
            return false;
        }
        if(!worldActions && "COLLECT_DROPS".equals(w.goal)){
            w.goal="WORLD_ACTIONS_DISABLED";
            w.harvestedAt=null;
            return false;
        }
        if("EXAMINE_TREE".equals(w.goal) && w.target!=null){
            if(!level.hasChunkAt(w.target) || !level.getBlockState(w.target).is(BlockTags.OVERWORLD_NATURAL_LOGS)){
                w.target=null;w.goal="SEARCH_NEW_RESOURCE";
                return false;
            }
            if(dream.distanceToSqr(w.target.getX()+.5,w.target.getY()+.5,w.target.getZ()+.5)>12.25){
                if(w.stand!=null && (dream.getNavigation().isDone() || now%40==0))
                    dream.getNavigation().moveTo(w.stand.getX()+.5,w.stand.getY(),w.stand.getZ()+.5,1.0);
                if(now-w.decisionTick>400){w.target=null;w.goal="EXAMINE_PATH_FAILED";}
                return true;
            }
            dream.getNavigation().stop();
            dream.getLookControl().setLookAt(w.target.getX()+.5,w.target.getY()+1,w.target.getZ()+.5);
            if(now-w.decisionTick>=60){
                w.goal="RESOURCE_IDENTIFIED";
                w.plan="RECOGNIZED_LOG_NO_WORLD_CHANGES";
                w.target=null;
                w.nextScan=now+320;
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
                return false;
            }
            if(dream.distanceToSqr(w.target.getX()+.5,w.target.getY()+.5,w.target.getZ()+.5)>12.25){
                if(w.stand!=null && (dream.getNavigation().isDone() || now%40==0))
                    dream.getNavigation().moveTo(w.stand.getX()+.5,w.stand.getY(),w.stand.getZ()+.5,1);
                if(now>w.startTick+450){
                    w.target=null;w.goal="REPLAN_UNREACHABLE";reinforcement(level,w,-.6f);
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
                w.gathered++;reinforcement(level,w,.85f);
                w.decisionTick=now;
            } else {w.goal="REPLAN_FAILED";reinforcement(level,w,-.8f);}
            return true;
        }
        if("COLLECT_DROPS".equals(w.goal)){
            int count=pickupWood(level,dream,w.harvestedAt);
            if(count>0){
                w.goal="CRAFT_PENDING";w.harvestedAt=null;
                reinforcement(level,w,.8f);
                return false;
            }
            if(now-w.decisionTick<90)return true; // drops require a few ticks to settle
            w.goal="SEARCHING";w.harvestedAt=null;
            return false;
        }
        return false;
    }
    /** Returns true when the agent has committed to a non-wandering plan. */
    public static boolean think(ServerLevel level,FakePlayerEntity dream,
                                ServerPlayer anchor,boolean worldActions){
        WorkState w=forEntity(dream);
        long now=level.getGameTime();
        if(w.target!=null && "HARVEST_TREE".equals(w.goal))return true;
        FakePlayerInventory inventory=dream.getInventory();
        int logs=count(inventory,true),planks=count(inventory,false);
        BlockPos tree=null,stand=null;
        if(logs==0 && planks<4 && now>=w.nextScan){
            var found=findTree(level,dream,anchor);
            if(found!=null){tree=found[0];stand=found[1];}
            w.nextScan=now+100;
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
        boolean[] options={true,tree!=null,worldActions&&logs>0,
                           worldActions&&planks>=4&&!w.finishedTable,
                           dream.distanceToSqr(anchor)<49};
        int choice=w.cortex.choose(sensors,options);
        w.decision=choice;
        if(choice==1 && tree!=null){
            if(!worldActions){
                w.goal="EXAMINE_TREE";
                w.plan="LOCATE -> APPROACH -> OBSERVE -> LEARN";
                w.target=tree;w.stand=stand;w.decisionTick=now;
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
                w.crafted++;reinforcement(level,w,1f);
            }else{reinforcement(level,w,-.6f);}
            w.nextScan=now+80;
            return true;
        }
        if(choice==3){
            w.goal="BUILD_WORKBENCH";w.plan="4 PLANKS -> CRAFTING_TABLE";
            if(placeTable(level,dream,anchor,inventory)){
                w.finishedTable=true;w.built++;reinforcement(level,w,1f);
                w.goal="WORKBENCH_BUILT";w.plan="FIND_NEXT_TASK";
            }else{reinforcement(level,w,-.5f);}
            return true;
        }
        if(choice==4){
            w.goal="WATCH_PLAYER";w.plan="SOCIAL_OBSERVATION";
            dream.getLookControl().setLookAt(anchor,30,30);
            reinforcement(level,w,.05f);
            return true;
        }
        w.goal=worldActions?"SCOUT_FOR_RESOURCES":"OBSERVE_WORLD";
        w.plan=worldActions?"DISCOVER -> IDENTIFY -> GATHER":"NONDESTRUCTIVE_EXPLORATION";
        return false;
    }
    public static String debug(FakePlayerEntity entity){
        WorkState w=forEntity(entity);
        int logs=count(entity.getInventory(),true);
        int planks=count(entity.getInventory(),false);
        return "embedded=H01-lite "+w.cortex.modules()+"x"+EmbeddedH01.COLUMNS
            +" synapses="+w.cortex.synapseCount()
            +" steps="+w.cortex.steps()+" updates="+w.cortex.reinforcementCount()
            +" changed="+w.cortex.changedWeights()
            +" activation="+String.format(Locale.ROOT,"%.3f",w.cortex.activation())
            +" goal="+w.goal+" plan="+w.plan
            +" logs="+logs+" planks="+planks
            +" mined="+w.gathered+" crafted="+w.crafted+" built="+w.built
            +" sharedNeural="+DreamData.get(entity.getServer()).neuralContributions();
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
    private static BlockPos[] findTree(ServerLevel level,FakePlayerEntity mob,ServerPlayer player) {
        BlockPos at=mob.blockPosition();
        BlockPos best=null,stand=null;
        double bestDist=Double.POSITIVE_INFINITY;
        // Bound scanning to ~750 loaded positions, no forced chunk loads.
        for(int dx=-6;dx<=6;dx++)for(int dz=-6;dz<=6;dz++){
            if(Math.abs(dx)+Math.abs(dz)>9)continue;
            for(int dy=-2;dy<=5;dy++){
                BlockPos log=at.offset(dx,dy,dz);
                if(!level.hasChunkAt(log)||!isNaturalLog(level,log))continue;
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
