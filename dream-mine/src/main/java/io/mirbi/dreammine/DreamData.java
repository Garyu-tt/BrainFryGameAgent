package io.mirbi.dreammine;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/** World-global saved memory, always stored in the Overworld's data storage. */
public final class DreamData extends SavedData {
    private static final String KEY = "dreammine_knowledge_v1";
    private final Set<UUID> living = new LinkedHashSet<>();
    private final Map<String, Integer> visitedBlocks = new LinkedHashMap<>();
    private final Map<String, Integer> playerEncounters = new LinkedHashMap<>();
    private final Set<String> optedOutPlayers = new LinkedHashSet<>();
    private final Map<UUID,Boolean> diagnosticsConsent = new LinkedHashMap<>();
    private final double[] sharedPolicy = new double[LearningMind.ACTIONS.length];
    private final float[] sharedCortexReadout = new float[EmbeddedH01.INPUTS*EmbeddedH01.ACTIONS];
    private int corticalContributions=0;
    private final int[] attempts = new int[LearningMind.ACTIONS.length];
    private UUID autoId; // exactly one automatic Dream per world
    private boolean automaticSpawning = true;
    // Terrain changes are OFF until an administrator opts in for this world.
    private boolean worldActionsEnabled = false;
    private long nextSpawnTick = 0L;
    private int generations = 0;
    private long observations = 0;

    public static DreamData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(DreamData::new, DreamData::load), KEY
        );
    }

    public int autoLivingCount() { return autoId == null ? 0 : 1; }
    public int manualLivingCount() { return Math.max(0,living.size()-autoLivingCount()); }
    public boolean isAutomatic(UUID id) { return id != null && id.equals(autoId); }
    public boolean automaticSpawning() { return automaticSpawning; }
    public boolean worldActionsEnabled() { return worldActionsEnabled; }
    public void setWorldActions(boolean enabled) {
        if(worldActionsEnabled==enabled)return;
        worldActionsEnabled=enabled;
        setDirty();
    }

    public long nextSpawnTick() { return nextSpawnTick; }
    public void setAutomaticSpawning(boolean enable) {
        if (automaticSpawning == enable) return;
        automaticSpawning = enable;
        setDirty();
    }
    public void deferSpawn(long now, long delayTicks) {
        nextSpawnTick = DreamPopulation.delayUntil(now, delayTicks);
        setDirty();
    }
    public boolean eligibleToSpawn(long now, int activePlayers) {
        return DreamPopulation.shouldAttempt(now, nextSpawnTick, automaticSpawning,
            autoId != null, activePlayers);
    }
    public int livingCount() { return living.size(); }
    public int generations() { return generations; }
    public long observations() { return observations; }
    public int knownBlockTypes() { return visitedBlocks.size(); }
    public int knownPlayers() { return playerEncounters.size(); }
    /** World-shared frequency map, used to rank unexplored terrain. */
    public int seenBlock(String blockId) { return visitedBlocks.getOrDefault(blockId,0); }

    public Boolean diagnosticConsent(UUID id) { return diagnosticsConsent.get(id); }
    public int diagnosticOptIns() {
        int count=0;
        for(boolean ok: diagnosticsConsent.values()) if(ok)count++;
        return count;
    }
    public void setDiagnosticConsent(UUID id,boolean allowed) {
        if(id==null)return;
        if(!diagnosticsConsent.containsKey(id) && diagnosticsConsent.size()>=10000)return;
        diagnosticsConsent.put(id,allowed);
        setDirty();
    }

    public boolean owns(UUID uuid) { return living.contains(uuid); }
    public Set<UUID> livingIds() { return Set.copyOf(living); }
    public double[] culture() { return Arrays.copyOf(sharedPolicy, sharedPolicy.length); }
    public float[] neuralCulture(){ return Arrays.copyOf(sharedCortexReadout,sharedCortexReadout.length); }
    public int neuralContributions(){ return corticalContributions; }
    public void learnCortex(float[] newReadout){
        if(newReadout==null || newReadout.length!=sharedCortexReadout.length)return;
        float alpha=1f/Math.min(40,Math.max(1,corticalContributions+1));
        for(int i=0;i<sharedCortexReadout.length;i++){
            float value=newReadout[i];
            if(!Float.isFinite(value))return;
            sharedCortexReadout[i]=Math.max(-2,Math.min(2,
                (1-alpha)*sharedCortexReadout[i]+alpha*value));
        }
        corticalContributions=Math.min(1_000_000,corticalContributions+1);
        setDirty();
    }


    public boolean birth(UUID id, boolean automatic) {
        if(id==null || living.contains(id) || (automatic && autoId!=null))return false;
        living.add(id);
        if(automatic)autoId=id;
        generations++;
        setDirty();
        return true;
    }
    /** Only the natural automatic Dream triggers automatic replacement. */
    public boolean death(UUID id) {
        if(!living.remove(id))return false;
        boolean replace=id.equals(autoId);
        if(replace)autoId=null;
        setDirty();
        return replace;
    }

    public void discovered(String blockId) {
        if (blockId == null || blockId.isBlank() || blockId.length() > 128) return;
        if (visitedBlocks.size() >= 256 && !visitedBlocks.containsKey(blockId)) return;
        int count = visitedBlocks.getOrDefault(blockId, 0);
        visitedBlocks.put(blockId, Math.min(100_000, count + 1));
        observations++;
        setDirty();
    }

    public void encounter(String gameName) {
        if (gameName == null || !gameName.matches("[A-Za-z0-9_]{1,16}") || optedOutPlayers.contains(gameName)) return;
        if (playerEncounters.size() >= 100 && !playerEncounters.containsKey(gameName)) return;
        playerEncounters.merge(gameName, 1, (a,b) -> Math.min(100_000, a + b));
        setDirty();
    }

    public void optOut(String gameName) {
        if (gameName == null || !gameName.matches("[A-Za-z0-9_]{1,16}")) return;
        playerEncounters.remove(gameName);
        optedOutPlayers.add(gameName);
        setDirty();
    }

    public void optIn(String gameName) {
        if (optedOutPlayers.remove(gameName)) setDirty();
    }

    public boolean forget(String gameName) {
        if (playerEncounters.remove(gameName) == null) return false;
        setDirty();
        return true;
    }

    public void learn(int direction, double outcome) {
        if (direction < 0 || direction >= sharedPolicy.length) return;
        double r = LearningMind.clamp(outcome, -1, 1);
        int n = ++attempts[direction];
        double learningRate = 1.0 / Math.min(60, n);
        sharedPolicy[direction] = LearningMind.clamp(
            sharedPolicy[direction] * (1-learningRate) + r * learningRate, -2, 2);
        observations++;
        setDirty();
    }

    public static DreamData load(CompoundTag tag, HolderLookup.Provider registries) {
        DreamData result = new DreamData();
        // Backward compatibility: old worlds with a single Dream used auto-spawning.
        // Old saves did not have these flags: default to continuous generations.
        result.automaticSpawning = !tag.contains("autoSpawn") || tag.getBoolean("autoSpawn");
        result.worldActionsEnabled = tag.getBoolean("worldActionsEnabled");
        result.nextSpawnTick = Math.max(0L, tag.getLong("nextSpawnTick"));
        result.generations = Math.max(0, tag.getInt("generations"));
        result.observations = Math.max(0, tag.getLong("observations"));
        for (Tag entry : tag.getList("living", Tag.TAG_STRING)) {
            try { result.living.add(UUID.fromString(entry.getAsString())); }
            catch (IllegalArgumentException ignored) { /* corrupt id cannot confer a spawn slot */ }
        }
        if(tag.hasUUID("autoId")) {
            UUID candidate=tag.getUUID("autoId");
            if(result.living.contains(candidate))result.autoId=candidate;
        }else if(!tag.contains("manualSpawnMigrationV2") && !result.living.isEmpty()) {
            // Version 0.1.2 did not record spawn origin; preserve one existing automatic entity.
            result.autoId=result.living.iterator().next();
        }
        int[] learnt=tag.getIntArray("neuralCulture");
        if(learnt.length==result.sharedCortexReadout.length)
            for(int i=0;i<learnt.length;i++)
                result.sharedCortexReadout[i]=Math.max(-2f,Math.min(2f,learnt[i]/32767f));
        else if(learnt.length==EmbeddedH01.ACTIONS*12) { // pre-sensor migration
            for(int a=0;a<EmbeddedH01.ACTIONS;a++)for(int i=0;i<12;i++)
                result.sharedCortexReadout[a*EmbeddedH01.INPUTS+i]=
                    Math.max(-2f,Math.min(2f,learnt[a*12+i]/32767f));
        }
        result.corticalContributions=Math.max(0,tag.getInt("corticalContributions"));
        CompoundTag blocks = tag.getCompound("blocks");
        for (String key : blocks.getAllKeys()) if (result.visitedBlocks.size() < 256)
            result.visitedBlocks.put(key, Math.max(0, blocks.getInt(key)));
        for (Tag entry : tag.getList("optedOut", Tag.TAG_STRING)) {
            if (result.optedOutPlayers.size() >= 1000) break;
            String name = entry.getAsString();
            if (name.matches("[A-Za-z0-9_]{1,16}")) result.optedOutPlayers.add(name);
        }

        CompoundTag consents = tag.getCompound("diagnosticsConsent");
        for(String uuid : consents.getAllKeys()) {
            if(result.diagnosticsConsent.size()>=10000)break;
            try { result.diagnosticsConsent.put(UUID.fromString(uuid),consents.getBoolean(uuid)); }
            catch(IllegalArgumentException ignored) {}
        }
        CompoundTag players = tag.getCompound("players");
        for (String key : players.getAllKeys()) if (result.playerEncounters.size() < 100)
            result.playerEncounters.put(key, Math.max(0, players.getInt(key)));
        for (int i=0;i<result.sharedPolicy.length;i++) {
            result.sharedPolicy[i] = LearningMind.clamp(tag.getDouble("p"+i), -2, 2);
            result.attempts[i] = Math.max(0, tag.getInt("n"+i));
        }
        return result;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("manualSpawnMigrationV2",true);
        if(autoId!=null)tag.putUUID("autoId",autoId);
        tag.putBoolean("autoSpawn", automaticSpawning);
        tag.putBoolean("worldActionsEnabled",worldActionsEnabled);
        int[] cortical=new int[sharedCortexReadout.length];
        for(int i=0;i<cortical.length;i++)cortical[i]=Math.round(sharedCortexReadout[i]*32767f);
        tag.putIntArray("neuralCulture",cortical);
        tag.putInt("corticalContributions",corticalContributions);

        tag.putLong("nextSpawnTick", nextSpawnTick);
        tag.putInt("generations", generations);
        tag.putLong("observations", observations);
        ListTag ids = new ListTag();
        for (UUID id : living) ids.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));
        tag.put("living", ids);
        CompoundTag blocks = new CompoundTag();
        visitedBlocks.forEach(blocks::putInt);
        tag.put("blocks", blocks);
        CompoundTag players = new CompoundTag();
        playerEncounters.forEach(players::putInt);
        tag.put("players", players);

        CompoundTag consents=new CompoundTag();
        diagnosticsConsent.forEach((id,allowed)-> consents.putBoolean(id.toString(),allowed));
        tag.put("diagnosticsConsent",consents);
        ListTag optouts = new ListTag();
        for (String name : optedOutPlayers) optouts.add(net.minecraft.nbt.StringTag.valueOf(name));
        tag.put("optedOut", optouts);
        for (int i=0;i<sharedPolicy.length;i++) {
            tag.putDouble("p"+i, sharedPolicy[i]);
            tag.putInt("n"+i, attempts[i]);
        }
        return tag;
    }
}