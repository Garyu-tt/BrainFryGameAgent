package io.mirbi.dreammine;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Server-side, explicit-consent-only telemetry. No identifier, seed, address,
 * position, name, inventory, chat, or individual trajectory in the payload.
 * The receiver may still see a network IP; this is de-identified, not
 * mathematically anonymous. No endpoint is embedded in the mod.
 */
public final class DreamTelemetry {
    private DreamTelemetry() {}
    private static final long INTERVAL_TICKS = 12000; // 10 min at 20 TPS
    private static final int MIN_SAMPLES = 10;
    private static final int MAX_SAMPLES = 2000;
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(4)).build();
    private static CompletableFuture<?> inflight;
    private static final int[] attempts = new int[8];
    private static final int[] successes = new int[8];
    private static long lastFlush = 0;
    private static int samples = 0;
    private static volatile boolean consentRevoked = false;

    /** Refuse collection if ANY other non-spectator player nearby has not opted in. */
    public static boolean consentForArea(ServerLevel level,ServerPlayer anchor,DreamData data) {
        if (anchor == null || !Boolean.TRUE.equals(data.diagnosticConsent(anchor.getUUID())))
            return false;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            if (p.distanceToSqr(anchor) > 100*100) continue;
            if (!Boolean.TRUE.equals(data.diagnosticConsent(p.getUUID()))) return false;
        }
        return true;
    }
    public static synchronized void observe(ServerLevel level,ServerPlayer anchor,
                                            DreamData data,int choice,double reward) {
        if (choice<0 || choice>=8 || !consentForArea(level,anchor,data)) return;
        if (samples>=MAX_SAMPLES)return;
        samples++;
        attempts[choice]++;
        if(reward>0) successes[choice]++;
    }
    public static synchronized void revoke() {
        consentRevoked=true;
        samples=0;
        Arrays.fill(attempts,0);
        Arrays.fill(successes,0);
        if(inflight != null && !inflight.isDone()) inflight.cancel(true);
    }
    public static synchronized void allowNewCollection() { consentRevoked=false; }

    private static Properties config() {
        Path path=FMLPaths.CONFIGDIR.get().resolve("dreammine-telemetry.properties");
        Properties props=new Properties();
        if(!Files.exists(path)) return props;
        try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            props.load(reader);
        }catch(Exception ignored){ return new Properties(); }
        return props;
    }
    public static boolean configured() {
        Properties p=config();String url=p.getProperty("endpoint","").trim();
        String token=p.getProperty("token","").trim();
        try{
            URI uri=URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost()!=null
                && uri.getUserInfo()==null && uri.getFragment()==null
                && token.matches("[A-Za-z0-9._~+/-]{20,256}");
        }catch(Exception ignored){return false;}
    }
    /** Sends only post-consent, small aggregated statistics, never a complete brain dump. */
    public static synchronized void maybeSend(MinecraftServer server,DreamData data,long tick) {
        if(tick-lastFlush<INTERVAL_TICKS || samples<MIN_SAMPLES || consentRevoked) return;
        if(inflight!=null && !inflight.isDone()) return;
        Properties props=config();
        if(!configured())return;
        if(data.diagnosticOptIns()==0) { revoke(); return; }
        URI uri=URI.create(props.getProperty("endpoint").trim());
        String token=props.getProperty("token").trim();
        int count=samples;
        int[] perDirection=Arrays.copyOf(attempts,8);
        int[] positive=Arrays.copyOf(successes,8);
        StringBuilder json=new StringBuilder();
        json.append("{\"schema\":\"dreammine.diagnostics.v1\",\"mod\":\"0.3.0\",")
            .append("\"samples\":").append(count)
            .append(",\"attempts\":").append(Arrays.toString(perDirection))
            .append(",\"successes\":").append(Arrays.toString(positive))
            .append("}");
        // Network I/O is async, without ever blocking Minecraft's server thread.
        HttpRequest request=HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(8))
            .header("Content-Type","application/json")
            .header("Authorization","Bearer "+token)
            .POST(HttpRequest.BodyPublishers.ofString(json.toString(),StandardCharsets.UTF_8))
            .build();
        lastFlush=tick;
        inflight=HTTP.sendAsync(request,HttpResponse.BodyHandlers.discarding())
            .thenAccept(result -> { /* receiver controls its own logs; don't log identifiers */ })
            .exceptionally(err -> null);
        // Non-queued fail-closed delivery: if unavailable, never spool private data.
        samples=0;
        Arrays.fill(attempts,0);
        Arrays.fill(successes,0);
    }
    static String payloadForTest(int count,int[] a,int[] s){
        return "{\"schema\":\"dreammine.diagnostics.v1\",\"samples\":" + count
          + ",\"attempts\":" + Arrays.toString(a)
          + ",\"successes\":" + Arrays.toString(s) + "}";
    }
}
