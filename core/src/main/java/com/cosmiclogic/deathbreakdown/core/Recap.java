package com.cosmiclogic.deathbreakdown.core;

import java.util.List;

/** Everything shown for one death. Immutable. */
public final class Recap {
    public final String victim;
    public final String deathMessage;
    public final long timeMs;
    public final long windowMs;
    public final List<Hit> hits; // oldest first; the last one is the final blow

    public Recap(String victim, String deathMessage, long timeMs, long windowMs, List<Hit> hits) {
        this.victim = victim == null ? "" : victim;
        this.deathMessage = deathMessage == null ? "" : deathMessage;
        this.timeMs = timeMs;
        this.windowMs = windowMs;
        this.hits = hits;
    }

    public Hit finalBlow() { return hits.isEmpty() ? null : hits.get(hits.size() - 1); }

    public float totalRaw() { float t = 0; for (Hit h : hits) t += h.raw; return t; }
    public float totalDealt() { float t = 0; for (Hit h : hits) t += h.dealt; return t; }
    public float totalArmor() { float t = 0; for (Hit h : hits) t += h.armor; return t; }
    public float totalEnchants() { float t = 0; for (Hit h : hits) t += h.enchants; return t; }
    public float totalOther() { float t = 0; for (Hit h : hits) t += h.effects + h.absorbed + h.blocked; return t; }

    /** The source that took the most health overall, which may not be the final blow. */
    public String biggestSource() {
        java.util.Map<String, Float> sum = new java.util.LinkedHashMap<String, Float>();
        for (Hit h : hits) {
            Float v = sum.get(h.source);
            sum.put(h.source, (v == null ? 0 : v) + h.dealt);
        }
        String best = null; float bestV = -1;
        for (java.util.Map.Entry<String, Float> e : sum.entrySet()) {
            if (e.getValue() > bestV) { best = e.getKey(); bestV = e.getValue(); }
        }
        return best == null ? "" : best;
    }
}
