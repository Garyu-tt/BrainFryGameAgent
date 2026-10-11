package io.mirbi.dreammine;

import dev.duzo.players.entities.FakePlayerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import java.util.*;

/**
 * Not a neural intention: narrowly scoped, auditable reflex authority.
 * Emergency fluid escape / hostile avoidance overrides autonomous plans.
 * The command source is explicitly INFRASTRUCTURE_SAFETY, not HCP800.
 */
public final class DreamReflex {
    private static final Map<UUID, ReflexState> STATE=new HashMap<>();
    private static final int[][] CARDINAL={{1,0},{-1,0},{0,1},{0,-1},{1,1},{-1,-1},{1,-1},{-1,1}};
    private DreamReflex(){}
    private static final class ReflexState {
        long nextAttempt;
        String trigger="NONE";
        BlockPos lastThreat;
        long expiresAt;
    }
    public static boolean handle(ServerLevel world,FakePlayerEntity dream,
                                 ServerPlayer anchor,long tick) {
        UUID id=dream.getUUID();
        ReflexState state=STATE.computeIfAbsent(id,uid->new ReflexState());
        boolean submerged=dream.isUnderWater();
        boolean burning=dream.isOnFire() || dream.isInLava();
        boolean critical=dream.getHealth()<Math.max(5,dream.getMaxHealth()*.28f);
        Monster hostile=null;
        // Hostile proximity is checked only once a second and not for dormant entities.
        if(tick%20==0){
            var mobs=world.getEntitiesOfClass(Monster.class,new AABB(dream.blockPosition()).inflate(6));
            double nearest=36;
            for(Monster mob:mobs){
                if(!mob.isAlive())continue;
                double d=mob.distanceToSqr(dream);
                if(d<nearest){nearest=d;hostile=mob;}
            }
            if(hostile!=null) {
                state.trigger="HOSTILE_CLOSE";
                state.lastThreat=hostile.blockPosition();
                state.expiresAt=tick+60;
            }
        }
        String trigger=submerged?"HEAD_SUBMERGED":burning?"HEAT_DAMAGE":
            critical?"CRITICAL_HEALTH":
                (tick<=state.expiresAt && "HOSTILE_CLOSE".equals(state.trigger))
                    ?"HOSTILE_CLOSE":null;
        if(trigger==null) {
            state.trigger="NONE";
            state.lastThreat=null;
            return false;
        }
        if(!trigger.equals(state.trigger)) {
            state.trigger=trigger;
            state.nextAttempt=0;
        }
        if(DreamWork.forEntity(dream).executive.active(world.getGameTime()))
            DreamWork.pause(world,dream,"REFLEX_"+trigger);
        if(tick<state.nextAttempt)return true;
        state.nextAttempt=tick+20;
        if(submerged){
            dream.getJumpControl().jump();
            dream.getLookControl().setLookAt(dream.getX(),dream.getY()+4,dream.getZ());
        }
        BlockPos at=dream.blockPosition();
        BlockPos best=null;
        double score=-Double.MAX_VALUE;
        for(int[] direction:CARDINAL){
            for(int span=2;span<=5;span+=3){
                for(int rise=0;rise<=2;rise++){
                    BlockPos dest=at.offset(direction[0]*span,rise,direction[1]*span);
                    if(!safe(world,dest) || !DreamLeash.withinLimit(
                         dest.distSqr(anchor.blockPosition())))continue;
                    double distance=state.lastThreat==null?0:dest.distSqr(state.lastThreat);
                    double safety=distance-(submerged?0:2*dest.distSqr(at))
                        +world.random.nextDouble()*.05;
                    if(safety>score){score=safety;best=dest;}
                }
            }
        }
        if(best!=null){
            dream.getNavigation().moveTo(best.getX()+.5,best.getY(),best.getZ()+.5,1.18);
        }
        return true;
    }
    public static void release(FakePlayerEntity dream){STATE.remove(dream.getUUID());}
    private static boolean safe(ServerLevel level,BlockPos at){
        if(level.isOutsideBuildHeight(at)||level.isOutsideBuildHeight(at.above())
           ||!level.hasChunkAt(at)||!level.hasChunkAt(at.below())
           ||!level.getWorldBorder().isWithinBounds(at))return false;
        if(!level.getFluidState(at).isEmpty()||!level.getFluidState(at.above()).isEmpty())
            return false;
        if(!level.getBlockState(at).getCollisionShape(level,at).isEmpty()
           ||!level.getBlockState(at.above()).getCollisionShape(level,at.above()).isEmpty())
            return false;
        var below=level.getBlockState(at.below());
        return below.isFaceSturdy(level,at.below(),Direction.UP)
            && !below.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
            && !below.is(net.minecraft.world.level.block.Blocks.CACTUS);
    }
}
