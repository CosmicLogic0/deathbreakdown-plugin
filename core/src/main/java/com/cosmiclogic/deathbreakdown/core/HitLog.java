package com.cosmiclogic.deathbreakdown.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps each player's recent hits and their last few recaps (the history). Thread-safe so it can be used from Paper's
 * async events. Hits older than the window are dropped as new ones arrive, and the history is capped per player and
 * in total players, so memory stays tiny on big servers.
 */
public final class HitLog {
    /** Hard cap on hits kept per player, in case something hits hundreds of times a second. */
    public static final int MAX_HITS = 64;

    /** Players whose history is kept in memory (least recently used dropped first). */
    public static final int MAX_PLAYERS = 1000;

    private final Map<UUID, Deque<Hit>> recent = new HashMap<UUID, Deque<Hit>>();
    private final LinkedHashMap<UUID, Deque<Recap>> history = new LinkedHashMap<UUID, Deque<Recap>>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Deque<Recap>> eldest) { return size() > MAX_PLAYERS; }
    };
    private volatile long windowMs;
    private volatile int historySize = 5;

    public HitLog(long windowMs) { this.windowMs = windowMs; }

    public void setHistorySize(int n) {
        historySize = Math.max(1, Math.min(20, n));
        synchronized (this) {
            for (Deque<Recap> d : history.values()) while (d.size() > historySize) d.removeFirst();
        }
    }
    public int historySize() { return historySize; }

    public void setWindowMs(long windowMs) { this.windowMs = windowMs; }
    public long windowMs() { return windowMs; }

    public synchronized void add(UUID player, Hit hit) {
        Deque<Hit> q = recent.get(player);
        if (q == null) { q = new ArrayDeque<Hit>(); recent.put(player, q); }
        q.addLast(hit);
        prune(q, hit.timeMs);
        while (q.size() > MAX_HITS) q.removeFirst();
    }

    /**
     * Called on death. Builds the recap from the hits inside the window and marks the newest one as the final blow.
     * If no hit was recorded (e.g. /kill with no damage event), {@code fallback} becomes the only row.
     */
    public synchronized Recap onDeath(UUID player, String victim, String deathMessage, long nowMs, Hit fallback) {
        Deque<Hit> q = recent.remove(player);
        List<Hit> hits = new ArrayList<Hit>();
        if (q != null) {
            prune(q, nowMs);
            hits.addAll(q);
        }
        if (hits.isEmpty() && fallback != null) hits.add(fallback);
        if (!hits.isEmpty()) {
            int i = hits.size() - 1;
            hits.set(i, hits.get(i).asFinalBlow());
        }
        Recap r = new Recap(victim, deathMessage, nowMs, windowMs, Collections.unmodifiableList(hits));
        Deque<Recap> h = history.get(player);
        if (h == null) { h = new ArrayDeque<Recap>(); history.put(player, h); }
        h.addLast(r);
        while (h.size() > historySize) h.removeFirst();
        return r;
    }

    /** The newest recap, or null. */
    public synchronized Recap last(UUID player) {
        Deque<Recap> h = history.get(player);
        return h == null || h.isEmpty() ? null : h.peekLast();
    }

    /** Oldest first; empty if none. */
    public synchronized List<Recap> history(UUID player) {
        Deque<Recap> h = history.get(player);
        return h == null ? new ArrayList<Recap>() : new ArrayList<Recap>(h);
    }

    /** Puts recaps loaded from disk back (oldest first), keeping any newer ones already in memory. */
    public synchronized void restore(UUID player, List<Recap> saved) {
        if (saved == null || saved.isEmpty()) return;
        Deque<Recap> now = history.get(player);
        Deque<Recap> merged = new ArrayDeque<Recap>(saved);
        if (now != null) for (Recap r : now) if (!contains(merged, r)) merged.addLast(r);
        while (merged.size() > historySize) merged.removeFirst();
        history.put(player, merged);
    }

    private static boolean contains(Deque<Recap> d, Recap r) {
        for (Recap x : d) if (x.timeMs == r.timeMs && x.victim.equals(r.victim)) return true;
        return false;
    }

    /** Clears a player's live hits (respawn without death, e.g. changing dimension through the End portal). */
    public synchronized void reset(UUID player) { recent.remove(player); }

    /** A player left: drop their live hits. Their history stays (capped), so /deathbreakdown still works after a relog. */
    public synchronized void forget(UUID player) { recent.remove(player); }

    /** Forget everything about a player, history too. */
    public synchronized void forgetAll(UUID player) { recent.remove(player); history.remove(player); }

    public synchronized int trackedPlayers() { return recent.size(); }

    private void prune(Deque<Hit> q, long nowMs) {
        long cutoff = nowMs - windowMs;
        while (!q.isEmpty() && q.peekFirst().timeMs < cutoff) q.removeFirst();
    }
}
