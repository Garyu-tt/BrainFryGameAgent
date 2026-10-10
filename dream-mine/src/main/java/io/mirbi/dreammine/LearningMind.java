package io.mirbi.dreammine;

import java.util.Arrays;
import java.util.random.RandomGenerator;

/**
 * Small deterministic, testable policy layer. NOT the HCP800 neural runtime.
 * It supplies a bounded emergency/exploration fallback until the external
 * neural adapter and bidirectional control have been validated.
 */
public final class LearningMind {
    public static final String[] ACTIONS = {
        "north", "north_east", "east", "south_east",
        "south", "south_west", "west", "north_west"
    };
    private final double[] rewards = new double[ACTIONS.length];
    private final int[] observations = new int[ACTIONS.length];

    public LearningMind(double[] prior) {
        if (prior != null) {
            if (prior.length != ACTIONS.length) throw new IllegalArgumentException("bad prior size");
            for (int i=0; i<prior.length; i++) rewards[i] = clamp(prior[i], -2, 2);
        }
    }

    public static double clamp(double v, double low, double high) {
        return Double.isFinite(v) ? Math.max(low, Math.min(high, v)) : 0;
    }

    /** Blocked candidates MUST be supplied as false. Returns -1 when all blocked. */
    public int choose(boolean[] safe, RandomGenerator random) {
        return choose(safe,random,null);
    }
    /** Shared local knowledge policy is consulted live, not only at birth. */
    public int choose(boolean[] safe, RandomGenerator random,double[] shared) {
        if(safe==null||safe.length!=ACTIONS.length)throw new IllegalArgumentException("bad safe mask");
        int chosen=-1;
        double best=Double.NEGATIVE_INFINITY;
        for(int i=0;i<safe.length;i++){
            if(!safe[i])continue;
            double sharedScore=shared!=null && shared.length==ACTIONS.length?clamp(shared[i],-2,2):rewards[i];
            double score=0.65*rewards[i]+0.35*sharedScore
                         +0.45/Math.sqrt(1+observations[i]) +random.nextDouble()*0.34;
            if(score>best){best=score;chosen=i;}
        }
        return chosen;
    }
    public void restorePersonal(double[] scores,int[] counts) {
        if(scores==null||counts==null||scores.length!=rewards.length||counts.length!=observations.length)return;
        for(int i=0;i<rewards.length;i++){
            rewards[i]=clamp(scores[i],-2,2);
            observations[i]=Math.max(0,Math.min(1_000_000,counts[i]));
        }
    }

    public void observe(int action, double reward) {
        if (action < 0 || action >= rewards.length) throw new IllegalArgumentException("action");
        double r = clamp(reward, -1, 1);
        int n = ++observations[action];
        double lr = 1.0 / Math.min(20, n);
        rewards[action] = clamp((1-lr)*rewards[action] + lr*r, -2, 2);
    }

    public double[] scores() { return Arrays.copyOf(rewards, rewards.length); }
    public int[] attempts() { return Arrays.copyOf(observations, observations.length); }
}