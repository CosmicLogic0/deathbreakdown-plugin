package com.cosmiclogic.deathbreakdown.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the words and numbers shown in the recap. The screen, chat and book all use these, so they always agree.
 * Color hints are given as 0xRRGGBB so each platform can turn them into its own text style.
 */
public final class RecapText {
    private RecapText() {}

    public static final int RED = 0xFF5555, GOLD = 0xFFAA00, BLUE = 0x55AAFF, PURPLE = 0xC08CFF, GRAY = 0xAAAAAA,
            WHITE = 0xFFFFFF, GREEN = 0x55FF55, DARK_RED = 0xAA0000;

    /** A column the recap can show. */
    public enum Col {
        TIME("Time"), SOURCE("Hit by"), TYPE("Type"), DAMAGE("Damage"), ARMOR("Armor"), ENCHANTS("Enchants"),
        OTHER("Other"), HEALTH("Health left");
        public final String title;
        Col(String title) { this.title = title; }
    }

    public static List<Col> columns(RecapConfig c) {
        List<Col> cols = new ArrayList<Col>();
        if (c.showTime) cols.add(Col.TIME);
        if (c.showSource) cols.add(Col.SOURCE);
        if (c.showType) cols.add(Col.TYPE);
        if (c.showDamage) cols.add(Col.DAMAGE);
        if (c.showArmor) cols.add(Col.ARMOR);
        if (c.showEnchants) cols.add(Col.ENCHANTS);
        if (c.showOther) cols.add(Col.OTHER);
        if (c.showHealthLeft) cols.add(Col.HEALTH);
        return cols;
    }

    /** "3", "1.5", "0.25" → trimmed, max 2 decimals. Hearts mode divides by 2 and adds ❤. */
    public static String amount(float points, boolean hearts) {
        float v = hearts ? points / 2.0F : points;
        String s = String.format(Locale.ROOT, "%.2f", v);
        while (s.contains(".") && (s.endsWith("0") || s.endsWith("."))) s = s.substring(0, s.length() - 1);
        if (s.equals("-0")) s = "0";
        return hearts ? s + "❤" : s;
    }

    /** Seconds before death, e.g. "-3.2s"; the final blow shows "0.0s". */
    public static String time(Hit h, Recap r) {
        float sec = (r.timeMs - h.timeMs) / 1000.0F;
        if (sec < 0) sec = 0;
        return String.format(Locale.ROOT, "-%.1fs", sec);
    }

    public static String source(Hit h) {
        if (h.weapon.isEmpty() || h.weapon.equalsIgnoreCase(h.source)) return h.source;
        return h.source + " (" + h.weapon + ")";
    }

    public static String cell(Col c, Hit h, Recap r, boolean hearts) {
        switch (c) {
            case TIME: return time(h, r);
            case SOURCE: return source(h);
            case TYPE: return DamageLabels.label(h.typeId);
            case DAMAGE: return amount(h.raw, hearts);
            case ARMOR: return h.armor > 0.005F ? "-" + amount(h.armor, hearts) : "";
            case ENCHANTS: return h.enchants > 0.005F ? "-" + amount(h.enchants, hearts) : "";
            case OTHER: {
                float o = h.effects + h.absorbed + h.blocked;
                return o > 0.005F ? "-" + amount(o, hearts) : "";
            }
            case HEALTH: return amount(Math.max(0, h.healthAfter), hearts);
            default: return "";
        }
    }

    public static int color(Col c, Hit h) {
        switch (c) {
            case SOURCE: return h.finalBlow ? GOLD : WHITE;
            case TYPE: return kindColor(h.kind);
            case DAMAGE: return RED;
            case ARMOR: return GRAY;
            case ENCHANTS: return PURPLE;
            case OTHER: return BLUE;
            case HEALTH: return h.healthAfter <= 0.01F ? DARK_RED : GREEN;
            default: return GRAY;
        }
    }

    public static int kindColor(Hit.Kind k) {
        switch (k) {
            case PLAYER: return 0x55FFFF;
            case MOB: return 0xFF7777;
            case PROJECTILE: return 0xFFDD55;
            case EXPLOSION: return 0xFF9944;
            case ENVIRONMENT: return 0x88CC66;
            case MAGIC: return PURPLE;
            default: return GRAY;
        }
    }

    /** One line for chat, e.g. "Killed by Skeleton (Bow): 9❤ in 4 hits. Armor stopped 3❤, enchants 1❤." */
    public static String summary(Recap r, boolean hearts) {
        Hit fb = r.finalBlow();
        if (fb == null) return "No damage recorded in the last " + (r.windowMs / 1000) + " seconds.";
        StringBuilder b = new StringBuilder();
        b.append("Killed by ").append(source(fb)).append(": ").append(amount(r.totalRaw(), hearts))
                .append(" in ").append(r.hits.size()).append(r.hits.size() == 1 ? " hit" : " hits").append('.');
        float a = r.totalArmor(), e = r.totalEnchants(), o = r.totalOther();
        if (a > 0.005F || e > 0.005F || o > 0.005F) {
            b.append(" Stopped:");
            boolean first = true;
            if (a > 0.005F) { b.append(" armor ").append(amount(a, hearts)); first = false; }
            if (e > 0.005F) { b.append(first ? " " : ", ").append("enchants ").append(amount(e, hearts)); first = false; }
            if (o > 0.005F) { b.append(first ? " " : ", ").append("other ").append(amount(o, hearts)); }
            b.append('.');
        }
        String big = r.biggestSource();
        if (!big.isEmpty() && !big.equals(fb.source)) b.append(" Most damage: ").append(big).append('.');
        return b.toString();
    }

    /**
     * One short tip about the death, or "" if nothing stands out, e.g. "Lava did 89% of the damage." or
     * "Your gear stopped 45% of the damage."
     */
    public static String tip(Recap r) {
        float dealt = r.totalDealt(), raw = r.totalRaw();
        if (r.hits.size() >= 2 && dealt > 0.01F) {
            java.util.Map<String, Float> sum = new java.util.LinkedHashMap<String, Float>();
            for (Hit h : r.hits) {
                Float v = sum.get(h.source);
                sum.put(h.source, (v == null ? 0 : v) + h.dealt);
            }
            if (sum.size() >= 2) {
                String best = r.biggestSource();
                int pct = Math.round(100F * sum.get(best) / dealt);
                if (pct >= 50 && pct < 100) return best + " did " + pct + "% of the damage.";
            }
        }
        float gear = r.totalArmor() + r.totalEnchants();
        if (raw > 0.01F && gear / raw >= 0.25F) {
            return "Your gear stopped " + Math.round(100F * gear / raw) + "% of the damage.";
        }
        if (r.hits.size() == 1 && r.finalBlow() != null && r.finalBlow().healthAfter <= 0.01F
                && r.finalBlow().dealt > 0.01F && r.finalBlow().dealt == r.totalDealt() && raw >= 20F) {
            return "One hit: " + amount(raw, true) + " in one go.";
        }
        return "";
    }

    /**
     * Lines for a chat hover: the death message, then the details of the newest {@code maxHits} hits (the final blow
     * last). Older hits are summed up in one line, so the tooltip always fits on screen.
     */
    public static List<String> hoverLines(Recap r, boolean hearts, int maxHits) {
        List<String> out = new ArrayList<String>();
        int skip = Math.max(0, r.hits.size() - Math.max(1, maxHits));
        if (skip > 0) out.add("(" + skip + " earlier " + (skip == 1 ? "hit" : "hits") + " in the full recap)");
        for (int i = skip; i < r.hits.size(); i++) {
            if (!out.isEmpty()) out.add("");
            out.addAll(details(r.hits.get(i), r, hearts));
        }
        return out;
    }

    /** The whole recap as plain text, for copying into Discord or a bug report. */
    public static String plainText(Recap r, boolean hearts) {
        StringBuilder b = new StringBuilder();
        b.append("Death Breakdown: ").append(r.deathMessage.isEmpty() ? r.victim + " died" : r.deathMessage).append('\n');
        b.append(summary(r, hearts)).append('\n');
        String tip = tip(r);
        if (!tip.isEmpty()) b.append(tip).append('\n');
        for (Hit h : r.hits) {
            b.append(h.finalBlow ? "> " : "  ").append(time(h, r)).append("  ").append(source(h))
                    .append("  [").append(DamageLabels.label(h.typeId)).append("]  ").append(amount(h.raw, hearts));
            if (h.armor > 0.005F) b.append("  armor -").append(amount(h.armor, hearts));
            if (h.enchants > 0.005F) b.append("  enchants -").append(amount(h.enchants, hearts));
            float o = h.effects + h.absorbed + h.blocked;
            if (o > 0.005F) b.append("  other -").append(amount(o, hearts));
            b.append("  left ").append(amount(Math.max(0, h.healthAfter), hearts)).append('\n');
        }
        return b.toString();
    }

    /** Explains the columns under the table; split into as many lines as the screen needs. */
    public static final String LEGEND = "Armor = armor & toughness   Enchants = Protection-type   Other = Resistance, absorption, shield";

    /** Measures text in pixels (the game's font). */
    public interface Width { int of(String s); }

    /**
     * Splits the legend into lines without breaking an entry ("Other = Resistance, absorption, shield" stays whole).
     * An entry wider than {@code max} on its own gets its own line (the caller can word-wrap it).
     */
    public static java.util.List<String> legendLines(Width w, int max) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        String cur = "";
        for (String part : LEGEND.split("   ")) {
            String t = cur.isEmpty() ? part : cur + "   " + part;
            if (!cur.isEmpty() && w.of(t) > max) { out.add(cur); cur = part; } else cur = t;
        }
        if (!cur.isEmpty()) out.add(cur);
        return out;
    }

    /** "2 min ago", "just now", for the history list. */
    public static String ago(long thenMs, long nowMs) {
        long s = Math.max(0, (nowMs - thenMs) / 1000);
        if (s < 10) return "just now";
        if (s < 60) return s + " s ago";
        if (s < 3600) return (s / 60) + " min ago";
        if (s < 86400) return (s / 3600) + " h ago";
        return (s / 86400) + " d ago";
    }

    /** Detail lines for a hover tooltip or a book page. */
    public static List<String> details(Hit h, Recap r, boolean hearts) {
        List<String> out = new ArrayList<String>();
        out.add((h.finalBlow ? "FINAL BLOW: " : "") + source(h));
        out.add("Type: " + DamageLabels.label(h.typeId) + "   " + time(h, r));
        out.add("Damage: " + amount(h.raw, hearts));
        if (h.armor > 0.005F) out.add("Armor stopped: " + amount(h.armor, hearts));
        if (h.enchants > 0.005F) out.add("Enchants stopped: " + amount(h.enchants, hearts));
        if (h.effects > 0.005F) out.add("Resistance stopped: " + amount(h.effects, hearts));
        if (h.absorbed > 0.005F) out.add("Absorption took: " + amount(h.absorbed, hearts));
        if (h.blocked > 0.005F) out.add("Shield stopped: " + amount(h.blocked, hearts));
        out.add("Health lost: " + amount(h.dealt, hearts) + "  left: " + amount(Math.max(0, h.healthAfter), hearts));
        return out;
    }
}
