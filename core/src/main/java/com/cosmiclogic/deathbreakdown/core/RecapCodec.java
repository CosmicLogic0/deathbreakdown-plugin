package com.cosmiclogic.deathbreakdown.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Turns a recap into one string and back. The server sends this string to the client in a single packet, so the
 * network code is the same tiny "send a String" on every loader and version.
 * Format: header line "DB1", recap line, then one line per hit; fields separated by tabs, with \t \n \\ escaped.
 * Since 1.1.0 the recap line has a 5th field: "D" for a death that just happened, "R" for a replay (/deathbreakdown,
 * history). 1.0.0 readers ignore it, and a missing flag reads as "unknown".
 */
public final class RecapCodec {
    private RecapCodec() {}

    public static final String HEADER = "DB1";
    /** Packets bigger than this are cut (oldest hits first) so a weird modded spam can't build a huge packet. */
    public static final int MAX_CHARS = 30000;

    /** A recap of a death that just happened (the client shows the death summary). */
    public static String encodeDeath(Recap r) { return encode(r, "D"); }

    /** A recap asked for again (/deathbreakdown, history): the client just opens it. */
    public static String encodeReplay(Recap r) { return encode(r, "R"); }

    /** 1.0.0 format (no flag). */
    public static String encode(Recap r) { return encode(r, null); }

    /** true = fresh death, false = replay, null = unknown (sent by Death Breakdown 1.0.0). */
    public static Boolean isDeath(String s) {
        try {
            if (s == null) return null;
            int a = s.indexOf('\n');
            int b = s.indexOf('\n', a + 1);
            if (a < 0) return null;
            String[] top = s.substring(a + 1, b < 0 ? s.length() : b).split("\t", -1);
            if (top.length < 5) return null;
            if ("D".equals(top[4])) return Boolean.TRUE;
            if ("R".equals(top[4])) return Boolean.FALSE;
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String encode(Recap r, String flag) {
        List<Hit> hits = new ArrayList<Hit>(r.hits);
        while (true) {
            StringBuilder b = new StringBuilder();
            b.append(HEADER).append('\n');
            b.append(esc(r.victim)).append('\t').append(esc(r.deathMessage)).append('\t').append(r.timeMs).append('\t').append(r.windowMs);
            if (flag != null) b.append('\t').append(flag);
            b.append('\n');
            for (Hit h : hits) {
                b.append(h.timeMs).append('\t').append(esc(h.source)).append('\t').append(esc(h.weapon)).append('\t')
                        .append(h.kind.name()).append('\t').append(esc(h.typeId)).append('\t')
                        .append(f(h.raw)).append('\t').append(f(h.armor)).append('\t').append(f(h.enchants)).append('\t')
                        .append(f(h.effects)).append('\t').append(f(h.absorbed)).append('\t').append(f(h.blocked)).append('\t')
                        .append(f(h.dealt)).append('\t').append(f(h.healthAfter)).append('\t').append(h.finalBlow ? '1' : '0').append('\n');
            }
            if (b.length() <= MAX_CHARS || hits.size() <= 1) return b.toString();
            hits.remove(0);
        }
    }

    /** Returns null for anything that isn't a valid recap (never throws). */
    public static Recap decode(String s) {
        try {
            if (s == null) return null;
            String[] lines = s.split("\n", -1);
            if (lines.length < 2 || !HEADER.equals(lines[0])) return null;
            String[] top = lines[1].split("\t", -1);
            List<Hit> hits = new ArrayList<Hit>();
            for (int i = 2; i < lines.length; i++) {
                if (lines[i].isEmpty()) continue;
                String[] p = lines[i].split("\t", -1);
                if (p.length < 14) continue;
                Hit.Kind kind;
                try { kind = Hit.Kind.valueOf(p[3]); } catch (IllegalArgumentException e) { kind = Hit.Kind.OTHER; }
                hits.add(new Hit(Long.parseLong(p[0]), un(p[1]), un(p[2]), kind, un(p[4]),
                        Float.parseFloat(p[5]), Float.parseFloat(p[6]), Float.parseFloat(p[7]), Float.parseFloat(p[8]),
                        Float.parseFloat(p[9]), Float.parseFloat(p[10]), Float.parseFloat(p[11]), Float.parseFloat(p[12]),
                        "1".equals(p[13])));
            }
            return new Recap(un(top[0]), un(top[1]), Long.parseLong(top[2]), Long.parseLong(top[3]),
                    Collections.unmodifiableList(hits));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String f(float v) { return String.format(Locale.ROOT, "%.3f", v); }

    static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') b.append("\\\\");
            else if (c == '\t') b.append("\\t");
            else if (c == '\n') b.append("\\n");
            else if (c == '\r') { /* drop */ }
            else b.append(c);
        }
        return b.toString();
    }

    static String un(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                b.append(n == 't' ? '\t' : n == 'n' ? '\n' : n);
            } else b.append(c);
        }
        return b.toString();
    }
}
