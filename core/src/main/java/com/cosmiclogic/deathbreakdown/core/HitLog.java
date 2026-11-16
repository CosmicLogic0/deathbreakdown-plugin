package com.cosmiclogic.deathbreakdown.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps each player's recent hits and the last recap. Thread-safe so it can be used from Paper's async events.
 * Hits older than the window are dropped as new ones arrive, so memory stays tiny.
 */
public final class HitLog {
    /** Hard cap on hits kept per player, in case something hits hundreds of times a second. */
    public static final int MAX_HITS = 64;

    private final Map<UUID, Deque<Hit>> recent = new HashMap<UUID, Deque<Hit>>();
    private final Map<UUID, Recap> last = new HashMap<UUID, Recap>();
    private volatile long windowMs;

    public HitLog(long windowMs) { this.windowMs = windowMs; }

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
        last.put(player, r);
        return r;
    }

    public synchronized Recap last(UUID player) { return last.get(player); }

    /** Clears a player's live hits (respawn without death, e.g. changing dimension through the End portal). */
    public synchronized void reset(UUID player) { recent.remove(player); }

    /** Forget everything about a player who left. */
    public synchronized void forget(UUID player) { recent.remove(player); last.remove(player); }

    public synchronized int trackedPlayers() { return recent.size(); }

    private void prune(Deque<Hit> q, long nowMs) {
        long cutoff = nowMs - windowMs;
        while (!q.isEmpty() && q.peekFirst().timeMs < cutoff) q.removeFirst();
    }
}
