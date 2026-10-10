package io.mirbi.dreammine;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.TooltipFlag;
import dev.duzo.players.entities.FakePlayerEntity;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.ChatFormatting;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mod(DreamMine.MODID)
public final class DreamMine {
    public static final String MODID = "dreammine";
    private static final String OWNED = "dreammine_owned";
    private static final String SUMMONER = "dreammine_summoner";
    private static final String PERSONAL = "dreammine_personal";
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredItem<DreamEggItem> DREAM_EGG =
        ITEMS.register("dream_spawn_egg",() -> new DreamEggItem(new Item.Properties().stacksTo(64)));
    private static final String SLEEP = "dreammine_sleep";
    private static final int[][] DIRECTIONS = {{0,-1},{1,-1},{1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1}};
    private static final Map<UUID, Brain> BRAINS = new ConcurrentHashMap<>();

    private static class Brain {
        final LearningMind mind;
        final ArrayDeque<CompoundTag> privateEpisodes=new ArrayDeque<>();
        int last = -1;
        double lastX, lastZ;
        long next = 0, encounterAt = -10000;
        boolean returning;
        boolean eligibleSample;
        Brain(DreamData data, FakePlayerEntity dream) {
            mind = new LearningMind(data.culture());
            CompoundTag privateMemory=dream.getPersistentData().getCompound(PERSONAL);
            if(privateMemory.contains("scores") && privateMemory.contains("attempts")){
                var scores=privateMemory.getList("scores",net.minecraft.nbt.Tag.TAG_DOUBLE);
                double[] doubles=new double[scores.size()];
                for(int i=0;i<doubles.length;i++)doubles[i]=scores.getDouble(i);
                mind.restorePersonal(doubles, privateMemory.getIntArray("attempts"));
            }
            var episodes=privateMemory.getList("episodes",net.minecraft.nbt.Tag.TAG_COMPOUND);
            for(int i=Math.max(0,episodes.size()-32);i<episodes.size();i++)
                privateEpisodes.addLast(episodes.getCompound(i).copy());
        }
        void remember(FakePlayerEntity dream,int direction,double reward){
            mind.observe(direction,reward);
            CompoundTag outcome=new CompoundTag();
            outcome.putInt("direction",direction);
            outcome.putDouble("reward",reward);
            privateEpisodes.addLast(outcome);
            while(privateEpisodes.size()>32)privateEpisodes.removeFirst();
            persist(dream);
        }
        void persist(FakePlayerEntity dream){
            CompoundTag tag=new CompoundTag();
            net.minecraft.nbt.ListTag scores=new net.minecraft.nbt.ListTag();
            for(double v:mind.scores())scores.add(net.minecraft.nbt.DoubleTag.valueOf(v));
            tag.put("scores",scores);
            tag.putIntArray("attempts",mind.attempts());
            var episodes=new net.minecraft.nbt.ListTag();
            for(var entry:privateEpisodes)episodes.add(entry.copy());
            tag.put("episodes",episodes);
            dream.getPersistentData().put(PERSONAL,tag);
        }
    }

    public DreamMine(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if(event.getTabKey().equals(ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                ResourceLocation.withDefaultNamespace("spawn_eggs"))))
                event.accept(DREAM_EGG.get());
        });
        NeoForge.EVENT_BUS.addListener(DreamMine::commands);
        NeoForge.EVENT_BUS.addListener(DreamMine::joined);
        NeoForge.EVENT_BUS.addListener(DreamMine::died);
        NeoForge.EVENT_BUS.addListener(DreamMine::tick);
        NeoForge.EVENT_BUS.addListener(DreamMine::loggedIn);
    }
    private static boolean owned(Entity entity) {
        return entity instanceof FakePlayerEntity && entity.getPersistentData().getBoolean(OWNED);
    }
    private static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("dreammine")
            .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
            .then(Commands.literal("spawn").requires(src->src.hasPermission(2))
                .executes(ctx -> manualSpawn(ctx.getSource())))
            .then(Commands.literal("auto").requires(src->src.hasPermission(2))
                .then(Commands.literal("on").executes(ctx -> auto(ctx.getSource(),true)))
                .then(Commands.literal("off").executes(ctx -> auto(ctx.getSource(),false))))
            .then(Commands.literal("name")
                .then(Commands.argument("nick",StringArgumentType.word())
                    .executes(ctx-> appearance(ctx.getSource(),
                        StringArgumentType.getString(ctx,"nick"),null))))
            .then(Commands.literal("skin")
                .then(Commands.argument("account",StringArgumentType.word())
                    .executes(ctx-> appearance(ctx.getSource(),null,
                        StringArgumentType.getString(ctx,"account")))))
            .then(Commands.literal("telemetry")
                .then(Commands.literal("yes").executes(ctx -> telemetry(ctx.getSource(),true)))
                .then(Commands.literal("no").executes(ctx -> telemetry(ctx.getSource(),false)))
                .then(Commands.literal("status").executes(ctx -> telemetryStatus(ctx.getSource()))))
            .then(Commands.literal("forgetme").executes(ctx -> forgetMe(ctx.getSource()))));
    }
    private static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        if(DreamData.get(player.server).diagnosticConsent(player.getUUID())!=null)return;
        player.sendSystemMessage(Component.literal("[Dream Mine] Делитесь ли вы обезличенной диагностикой Dream?")
            .withStyle(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("Передаются лишь суммарные попытки и успехи обучения. Ники, чаты, координаты и UUID не отправляются. Сервер-получатель может видеть IP сервера.")
            .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(
            Component.literal("[ДА]").withStyle(style -> style.withColor(ChatFormatting.GREEN)
               .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/dreammine telemetry yes")))
            .append(Component.literal("    "))
            .append(Component.literal("[НЕТ]").withStyle(style -> style.withColor(ChatFormatting.RED)
               .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/dreammine telemetry no")))));
    }
    private static int telemetry(CommandSourceStack src,boolean yes) {
        if(!(src.getEntity() instanceof ServerPlayer player))return 0;
        DreamData data=DreamData.get(src.getServer());
        data.setDiagnosticConsent(player.getUUID(),yes);
        if(yes) {
            DreamTelemetry.allowNewCollection();
            src.sendSuccess(()->Component.literal("Dream Mine: согласие сохранено. Отправка возможна, только если администратор настроил защищенный HTTPS-приёмник. /dreammine telemetry no — отзыв."),false);
        }else {
            DreamTelemetry.revoke();
            src.sendSuccess(()->Component.literal("Dream Mine: отказ сохранён. Новые данные не собираются от вашего взаимодействия. /dreammine telemetry yes — включить."),false);
        }
        return 1;
    }
    private static int telemetryStatus(CommandSourceStack src) {
        if(!(src.getEntity() instanceof ServerPlayer player))return 0;
        Boolean consent=DreamData.get(src.getServer()).diagnosticConsent(player.getUUID());
        String state=consent==null?"не отвечено":consent?"разрешено":"запрещено";
        src.sendSuccess(()->Component.literal("Диагностика: "+state+". HTTPS-приёмник сервера: "+(DreamTelemetry.configured()?"настроен":"не настроен")),false);
        return 1;
    }
    private static int status(CommandSourceStack src) {
        DreamData data = DreamData.get(src.getServer());
        src.sendSuccess(() -> Component.literal("Dream Mine | alive " + data.livingCount()
            + " (auto " + data.autoLivingCount() + "/1, eggs " + data.manualLivingCount() + ") | lives " + data.generations()
            + " | discoveries " + data.knownBlockTypes()
            + " | experiences " + data.observations()
            + " | auto " + (data.automaticSpawning()?"on":"off")),false);
        return 1;
    }
    private static int auto(CommandSourceStack src, boolean enabled) {
        DreamData.get(src.getServer()).setAutomaticSpawning(enabled);
        src.sendSuccess(() -> Component.literal("Dream auto-spawn: "+enabled),true);
        return 1;
    }
    private static int appearance(CommandSourceStack src,String nick,String skin) {
        if(!(src.getEntity() instanceof ServerPlayer player))return 0;
        FakePlayerEntity dream=nearestManualDream(player.serverLevel(),player,8);
        if(dream==null){src.sendFailure(Component.literal("No owned manual Dream within 8 blocks"));return 0;}
        if(nick!=null){
            if(!DreamAppearance.validName(nick)){
                src.sendFailure(Component.literal("Nickname must contain 1-32 letters, numbers or _"));return 0;
            }
            dream.setNameWithoutSkin(Component.literal(nick));
        }
        if(skin!=null){
            if(!DreamAppearance.validSkin(skin)){
                src.sendFailure(Component.literal("Skin must be an existing Minecraft username (1-16 chars)"));return 0;
            }
            dream.setSkin(skin);
        }
        src.sendSuccess(()->Component.literal("Dream updated. Skin: "+(skin==null?"unchanged":skin)),false);
        return 1;
    }
    private static FakePlayerEntity nearestManualDream(ServerLevel level,ServerPlayer player,double radius){
        FakePlayerEntity best=null;
        double nearest=radius*radius;
        DreamData state=DreamData.get(level.getServer());
        for(UUID id:state.livingIds()){
            if(state.isAutomatic(id))continue;
            Entity entity=level.getEntity(id);
            if(!(entity instanceof FakePlayerEntity dream)||!owned(dream)||!dream.isAlive())continue;
            if(!player.hasPermissions(2) && !player.getUUID().toString().equals(
                 dream.getPersistentData().getString(SUMMONER)))continue;
            double d=dream.distanceToSqr(player);
            if(d<=nearest){nearest=d;best=dream;}
        }
        return best;
    }
    private static int forgetMe(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer player)) return 0;
        DreamData.get(src.getServer()).optOut(player.getGameProfile().getName());
        src.sendSuccess(() -> Component.literal("Your name has been removed from Dream knowledge"),false);
        return 1;
    }
    private static int manualSpawn(CommandSourceStack src) {
        DreamData data = DreamData.get(src.getServer());
        BlockPos near = BlockPos.containing(src.getPosition());
        if (!safe(src.getLevel(),near)) {
            src.sendFailure(Component.literal("Stand on solid ground with clear space to spawn Dream"));
            return 0;
        }
        if (!birth(src.getLevel(),near,data,false,"Dream","Dream",src.getEntity() instanceof ServerPlayer p?p:null)) {
            src.sendFailure(Component.literal("Spawn failed (unsafe placement)")); return 0;
        }
        src.sendSuccess(() -> Component.literal("Dream generation "+data.generations()+" created"),true);
        return 1;
    }
    /** Auto is limited to one; manually spawned eggs are unlimited. */
    public static boolean birth(ServerLevel level,BlockPos at,DreamData data,
                                boolean automatic,String nick,String skin,ServerPlayer owner){
        if ((automatic && data.autoLivingCount()!=0) || !safe(level,at))return false;
        FakePlayerEntity dream=new FakePlayerEntity(level);
        dream.moveTo(at.getX()+0.5,at.getY()+0.1,at.getZ()+0.5,level.random.nextFloat()*360,0);
        dream.setNameWithoutSkin(Component.literal(nick));
        dream.setCustomNameVisible(true);
        dream.setSkin(skin); // Uses Fake Players' normal skin service/cache.
        dream.setNoAi(false);
        dream.setPersistenceRequired();
        dream.getPersistentData().putBoolean(OWNED,true);
        dream.getPersistentData().putBoolean("dreammine_auto",automatic);
        if(owner!=null)dream.getPersistentData().putString(SUMMONER,owner.getUUID().toString());
        if(!level.addFreshEntity(dream))return false;
        if(!data.birth(dream.getUUID(),automatic)) {dream.discard();return false;}
        dream.getPersistentData().putInt("dreammine_generation",data.generations());
        BRAINS.put(dream.getUUID(),new Brain(data,dream));
        if(automatic)data.deferSpawn(level.getServer().overworld().getGameTime(),DreamPopulation.RETRY_DELAY_TICKS);
        if(!automatic && owner!=null && data.manualLivingCount()>=8
            && data.manualLivingCount()%8==0)
            owner.sendSystemMessage(Component.translatable(
                "message.dreammine.lag",data.manualLivingCount()).withStyle(ChatFormatting.RED));
        return true;
    }
    private static void joined(EntityJoinLevelEvent e) {
        if (!owned(e.getEntity()) || !(e.getLevel() instanceof ServerLevel level)) return;
        DreamData data = DreamData.get(level.getServer());
        if (data.owns(e.getEntity().getUUID()))
            BRAINS.putIfAbsent(e.getEntity().getUUID(),new Brain(data,(FakePlayerEntity)e.getEntity()));
    }
    private static void died(LivingDeathEvent e) {
        if (e.isCanceled() || !(e.getEntity().level() instanceof ServerLevel level)
            || !owned(e.getEntity())) return;
        DreamData data = DreamData.get(level.getServer());
        if (data.death(e.getEntity().getUUID())) {
            data.deferSpawn(level.getServer().overworld().getGameTime(),
                DreamPopulation.REPLACEMENT_DELAY_TICKS);
        }
        BRAINS.remove(e.getEntity().getUUID());
    }
    private static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        DreamData data = DreamData.get(server);
        long tick = server.overworld().getGameTime();
        if (tick % 12000 == 0) DreamTelemetry.maybeSend(server,data,tick);
        if (tick % DreamPopulation.SCAN_INTERVAL_TICKS == 0
            && data.eligibleToSpawn(tick,server.getPlayerCount())) spawnAutomatic(server,data);
        if ((tick % 4)!=0)return;
        for (ServerLevel level : server.getAllLevels()) for (UUID id : data.livingIds()) {
            Entity entity = level.getEntity(id);
            if (!(entity instanceof FakePlayerEntity dream) || !owned(dream)
                || !dream.isAlive()) continue;
            Brain brain = BRAINS.computeIfAbsent(id,k -> new Brain(data,dream));
            ServerPlayer nearest = nearest(level,dream);
            if (nearest==null) {
                if (!dream.getPersistentData().getBoolean(SLEEP)) {
                    dream.getNavigation().stop();
                    dream.getPersistentData().putBoolean(SLEEP,true);
                    dream.setNoAi(true);
                }
                brain.returning=false; brain.last=-1;
                continue;
            }
            if (dream.getPersistentData().getBoolean(SLEEP)) {
                dream.getPersistentData().remove(SLEEP);
                dream.setNoAi(false);
            }
            if (dream.isNoAi()) continue;
            DreamLeash.Decision leash = DreamLeash.decide(dream.distanceToSqr(nearest),true,brain.returning);
            if (leash==DreamLeash.Decision.RELOCATE) {
                brain.last=-1; brain.returning=true;
                dream.getNavigation().stop();
                if (relocate(level,dream,nearest)) {
                    brain.returning=false; brain.next=level.getGameTime()+20;
                } else dream.getNavigation().moveTo(nearest,1.15);
                continue;
            }
            if (leash==DreamLeash.Decision.RETURN) {
                brain.returning=true; brain.last=-1;
                if (dream.getNavigation().isDone() || tick%40==0)
                    dream.getNavigation().moveTo(nearest,1.15);
                continue;
            }
            brain.returning=false;
            if (level.getGameTime() < brain.next) continue;
            brain.next=level.getGameTime()+80;
            think(level,dream,brain,data,nearest);
        }
    }
    private static ServerPlayer nearest(ServerLevel level,FakePlayerEntity dream) {
        ServerPlayer result=null;double best=Double.POSITIVE_INFINITY;
        for(ServerPlayer p:level.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            double d=dream.distanceToSqr(p);
            if(d<best){best=d;result=p;}
        }
        return result;
    }
    private static void spawnAutomatic(MinecraftServer server,DreamData data) {
        List<ServerPlayer> players=server.getPlayerList().getPlayers();
        if(players.isEmpty()) return;
        int start=server.overworld().random.nextInt(players.size());
        for(int offset=0;offset<players.size();offset++) {
            ServerPlayer p=players.get((offset+start)%players.size());
            if(p.isSpectator() || !p.isAlive()) continue;
            ServerLevel level=p.serverLevel();
            for(int i=0;i<40;i++) {
                double a=level.random.nextDouble()*Math.PI*2;
                int r=16+level.random.nextInt(17);
                BlockPos column=p.blockPosition().offset((int)(r*Math.cos(a)),0,(int)(r*Math.sin(a)));
                if(!level.hasChunkAt(column)) continue;
                for(int dy: new int[]{0,-1,1,-2,2,-3,3,4,-4,5,-5,6,-6}) {
                    BlockPos pos=column.offset(0,dy,0);
                    if(safe(level,pos) && birth(level,pos,data,true,"Dream","Dream",null)) return;
                }
            }
        }
        data.deferSpawn(server.overworld().getGameTime(),DreamPopulation.RETRY_DELAY_TICKS);
    }
    private static boolean relocate(ServerLevel level,FakePlayerEntity dream,ServerPlayer anchor) {
        BlockPos origin=anchor.blockPosition();
        for(int i=0;i<35;i++) {
            int radius=8+level.random.nextInt(17);
            double angle=level.random.nextDouble()*Math.PI*2;
            BlockPos column=origin.offset((int)(Math.cos(angle)*radius),0,
                (int)(Math.sin(angle)*radius));
            if(!level.hasChunkAt(column))continue;
            for(int dy:new int[]{0,1,-1,2,-2,3,-3,4,-4,5,-5}) {
                BlockPos spot=column.offset(0,dy,0);
                if (DreamLeash.withinLimit(spot.distSqr(origin)) && safe(level,spot)) {
                    dream.teleportTo(spot.getX()+0.5,spot.getY()+0.1,spot.getZ()+0.5);
                    return true;
                }
            }
        }
        return false;
    }
    private static void think(ServerLevel level, FakePlayerEntity dream,Brain brain,
                              DreamData data,ServerPlayer anchor) {
        if(brain.last>=0) {
            double traveled=Math.hypot(dream.getX()-brain.lastX,dream.getZ()-brain.lastZ);
            double reward=traveled>=0.8?1:-0.5;
            brain.remember(dream,brain.last,reward);
            data.learn(brain.last,reward);
            if(brain.eligibleSample) DreamTelemetry.observe(level,anchor,data,brain.last,reward);
            brain.eligibleSample=false;
            brain.last=-1;
        }
        BlockPos here=dream.blockPosition();
        if (dream.isInWater()) {
            for(int[] dir:DIRECTIONS) {
                BlockPos dry=here.offset(dir[0]*2,0,dir[1]*2);
                if(safe(level,dry)) {
                    dream.getNavigation().moveTo(dry.getX()+0.5,dry.getY(),dry.getZ()+0.5,1.1);
                    return;
                }
            }
        }
        if(!level.hasChunkAt(here.below())) return;
        data.discovered(BuiltInRegistries.BLOCK.getKey(level.getBlockState(here.below()).getBlock()).toString());
        if(dream.distanceToSqr(anchor)<64 && level.getGameTime()-brain.encounterAt>1200) {
            data.encounter(anchor.getGameProfile().getName());
            brain.encounterAt=level.getGameTime();
        }
        if (!dream.getNavigation().isDone())return;
        boolean[] mask=new boolean[8];
        BlockPos[] spots=new BlockPos[8];
        for(int i=0;i<8;i++) {
            BlockPos at=here.offset(DIRECTIONS[i][0]*5,0,DIRECTIONS[i][1]*5);
            spots[i]=at;
            mask[i]=safe(level,at) && DreamLeash.allowWander(at.distSqr(anchor.blockPosition()));
        }
        int choice=brain.mind.choose(mask,new Random(level.random.nextLong()),data.culture());
        if(choice<0)return;
        BlockPos to=spots[choice];
        brain.last=choice;brain.lastX=dream.getX();brain.lastZ=dream.getZ();
        brain.eligibleSample=DreamTelemetry.consentForArea(level,anchor,data);
        if(!dream.getNavigation().moveTo(to.getX()+0.5,to.getY(),to.getZ()+0.5,0.95)) {
            brain.remember(dream,choice,-1);data.learn(choice,-1);brain.last=-1;
        }
    }
    private static boolean safe(ServerLevel level,BlockPos at) {
        if(level.isOutsideBuildHeight(at) || level.isOutsideBuildHeight(at.above())
            || !level.getWorldBorder().isWithinBounds(at)
            || !level.hasChunkAt(at) || !level.hasChunkAt(at.below()))return false;
        if(!level.getFluidState(at).isEmpty() || !level.getFluidState(at.above()).isEmpty())return false;
        if(!level.getBlockState(at).getCollisionShape(level,at).isEmpty()
            || !level.getBlockState(at.above()).getCollisionShape(level,at.above()).isEmpty())return false;
        var below=level.getBlockState(at.below());
        if(below.is(Blocks.MAGMA_BLOCK) || below.is(Blocks.CAMPFIRE)
            || below.is(Blocks.SOUL_CAMPFIRE) || below.is(Blocks.CACTUS))return false;
        return below.isFaceSturdy(level,at.below(),Direction.UP);
    }
}
