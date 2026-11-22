package com.cosmiclogic.deathbreakdown.core;

import java.util.ArrayList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

/**
 * Automatic tests for the core. No libraries needed: run with
 *   java -cp out com.cosmiclogic.deathbreakdown.core.CoreTest
 * Every public static void test*() method runs; the process exits 1 if any fails.
 */
public final class CoreTest {
    static int passed, failed;

    public static void main(String[] args) throws Exception {
        for (Method m : CoreTest.class.getDeclaredMethods()) {
            if (!m.getName().startsWith("test")) continue;
            try {
                m.invoke(null);
                passed++;
                System.out.println("PASS " + m.getName());
            } catch (Throwable t) {
                failed++;
                Throwable c = t.getCause() != null ? t.getCause() : t;
                System.out.println("FAIL " + m.getName() + ": " + c);
            }
        }
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    static void eq(Object exp, Object got) {
        if (exp == null ? got != null : !exp.equals(got)) throw new AssertionError("expected <" + exp + "> got <" + got + ">");
    }
    static void near(double exp, double got) {
        if (Math.abs(exp - got) > 0.001) throw new AssertionError("expected " + exp + " got " + got);
    }
    static void yes(boolean b, String msg) { if (!b) throw new AssertionError(msg); }

    static Hit hit(long t, String src, float raw, float dealt) {
        return new Hit(t, src, "", Hit.Kind.MOB, "minecraft:mob_attack", raw, 0, 0, 0, 0, 0, dealt, 20 - dealt, false);
    }

    // ---- DamageMath: numbers checked against the Minecraft Wiki's armor and enchantment tables ----

    public static void testArmorFullDiamond() {
        // Full diamond: 20 armor, 8 toughness, 10 damage -> 20 - 10/4 = 17.5 effective -> 70% stopped -> 3 left
        near(3.0, DamageMath.afterArmor(10, 20, 8));
    }

    public static void testArmorMinimumIsTwentyPercent() {
        // Huge hit vs 10 armor: effective is floored at armor*0.2 = 2 -> 8% stopped
        near(100 * (1 - 2 / 25.0), DamageMath.afterArmor(100, 10, 0));
    }

    public static void testArmorCapsAtEighty() {
        near(1 * (1 - 20 / 25.0), DamageMath.afterArmor(1, 30, 20));
    }

    public static void testNoArmorNoChange() {
        near(7, DamageMath.afterArmor(7, 0, 0));
    }

    public static void testProtectionCapsAtTwentyPoints() {
        near(10 * 0.2, DamageMath.afterEnchants(10, 32));   // 4x Protection IV = 16 EPF, more than 20 caps
        near(10 * (1 - 16 / 25.0), DamageMath.afterEnchants(10, 16));
    }

    public static void testResistance() {
        near(8, DamageMath.afterResistance(10, 0));   // Resistance I = 20%
        near(0, DamageMath.afterResistance(10, 4));   // Resistance V = immune
        near(10, DamageMath.afterResistance(10, -1));
    }

    public static void testSplitAddsUp() {
        DamageMath.Split s = DamageMath.split(12, 15, 2, 8, 0, 2, false, false, false);
        near(12, s.armor + s.effects + s.enchants + s.absorbed + s.dealt);
        yes(s.armor > 0 && s.effects > 0 && s.enchants > 0, "every layer should stop something");
        near(2, s.absorbed);
    }

    public static void testSplitBypassArmor() {
        DamageMath.Split s = DamageMath.split(6, 20, 8, 0, -1, 0, true, false, false);
        near(0, s.armor);
        near(6, s.dealt);
    }

    public static void testFallWithFeatherFalling() {
        // Fall ignores armor; Feather Falling IV (12 EPF) + nothing else -> 48% stopped
        DamageMath.Split s = DamageMath.split(10, 20, 0, 12, -1, 0, true, false, false);
        near(0, s.armor);
        near(4.8, s.enchants);
        near(5.2, s.dealt);
    }

    // ---- HitLog ----

    public static void testWindowDropsOldHits() {
        HitLog log = new HitLog(10_000);
        UUID p = UUID.randomUUID();
        log.add(p, hit(0, "Zombie", 3, 3));          // 12s before death -> dropped
        log.add(p, hit(5_000, "Skeleton", 4, 4));
        log.add(p, hit(12_000, "Creeper", 10, 10));
        Recap r = log.onDeath(p, "Steve", "Steve was blown up", 12_000, null);
        eq(2, r.hits.size());
        eq("Skeleton", r.hits.get(0).source);
        yes(r.hits.get(1).finalBlow, "newest hit is the final blow");
        yes(!r.hits.get(0).finalBlow, "only one final blow");
    }

    public static void testFallbackWhenNoHits() {
        HitLog log = new HitLog(10_000);
        UUID p = UUID.randomUUID();
        Hit fb = new Hit(500, "Void", "", Hit.Kind.ENVIRONMENT, "minecraft:out_of_world", 20, 0, 0, 0, 0, 0, 20, 0, false);
        Recap r = log.onDeath(p, "Alex", "Alex fell out of the world", 500, fb);
        eq(1, r.hits.size());
        yes(r.finalBlow().finalBlow, "fallback is the final blow");
    }

    public static void testLastRecapKeptAndReset() {
        HitLog log = new HitLog(10_000);
        UUID p = UUID.randomUUID();
        log.add(p, hit(1000, "Spider", 2, 2));
        log.onDeath(p, "Steve", "", 1000, null);
        yes(log.last(p) != null, "last recap kept for the reopen key");
        eq(0, log.trackedPlayers());                  // live hits cleared after death
        log.forget(p);
        yes(log.last(p) != null, "logging out keeps the history (1.1.0: /deathbreakdown works after a relog)");
        log.forgetAll(p);
        yes(log.last(p) == null, "forgetAll clears everything");
    }

    public static void testHitCap() {
        HitLog log = new HitLog(60_000);
        UUID p = UUID.randomUUID();
        for (int i = 0; i < 500; i++) log.add(p, hit(i, "Bee", 0.5F, 0.5F));
        Recap r = log.onDeath(p, "Steve", "", 500, null);
        eq(HitLog.MAX_HITS, r.hits.size());
    }

    public static void testWindowChangeApplies() {
        HitLog log = new HitLog(10_000);
        log.setWindowMs(3_000);
        UUID p = UUID.randomUUID();
        log.add(p, hit(0, "A", 1, 1));
        log.add(p, hit(8_000, "B", 1, 1));
        eq(1, log.onDeath(p, "S", "", 9_000, null).hits.size());
    }

    // ---- Codec ----

    public static void testCodecRoundTrip() {
        HitLog log = new HitLog(10_000);
        UUID p = UUID.randomUUID();
        log.add(p, new Hit(100, "Skel\tetor\nweird\\name", "Bow", Hit.Kind.PROJECTILE, "minecraft:arrow",
                5.5F, 2.25F, 0.5F, 0, 1, 0, 1.75F, 18.25F, false));
        log.add(p, new Hit(900, "Lava", "", Hit.Kind.ENVIRONMENT, "minecraft:lava", 4, 0, 0, 0, 0, 0, 4, 0, false));
        Recap r = log.onDeath(p, "Steve", "Steve tried to swim in lava", 900, null);
        Recap back = RecapCodec.decode(RecapCodec.encode(r));
        yes(back != null, "decodes");
        eq(r.victim, back.victim);
        eq(r.deathMessage, back.deathMessage);
        eq(2, back.hits.size());
        Hit a = back.hits.get(0);
        eq("Skel\tetor\nweird\\name", a.source);
        eq("Bow", a.weapon);
        eq(Hit.Kind.PROJECTILE, a.kind);
        near(2.25, a.armor);
        near(18.25, a.healthAfter);
        yes(back.hits.get(1).finalBlow, "final blow survives the trip");
    }

    public static void testCodecRejectsJunk() {
        yes(RecapCodec.decode(null) == null, "null");
        yes(RecapCodec.decode("") == null, "empty");
        yes(RecapCodec.decode("hello\nworld") == null, "wrong header");
        yes(RecapCodec.decode("DB1\nonly\tthree\tfields") == null, "bad recap line");
    }

    public static void testCodecSizeCap() {
        java.util.List<Hit> hits = new java.util.ArrayList<Hit>();
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 2000; i++) longName.append('x');
        for (int i = 0; i < 64; i++) hits.add(new Hit(i, longName.toString(), "", Hit.Kind.MOB, "x", 1, 0, 0, 0, 0, 0, 1, 1, i == 63));
        String s = RecapCodec.encode(new Recap("S", "", 64, 10000, hits));
        yes(s.length() <= RecapCodec.MAX_CHARS, "capped: " + s.length());
        Recap back = RecapCodec.decode(s);
        yes(back.finalBlow().finalBlow, "the newest hits are the ones kept");
    }

    // ---- Labels ----

    public static void testLabels() {
        eq("Arrow", DamageLabels.label("minecraft:arrow"));
        eq("Arrow", DamageLabels.label("arrow"));              // 1.12-1.19 msgId
        eq("Arrow", DamageLabels.label("PROJECTILE"));         // Bukkit cause
        eq("Fire", DamageLabels.label("inFire"));
        eq("Burning", DamageLabels.label("minecraft:on_fire"));
        eq("Campfire", DamageLabels.label("minecraft:campfire"));
        eq("Acid Rain", DamageLabels.label("mymod:acid_rain")); // modded: readable fallback
        eq("Entity Attack Plus", DamageLabels.label("ENTITY_ATTACK_PLUS"));
        eq(Hit.Kind.EXPLOSION, DamageLabels.kind("ENTITY_EXPLOSION"));
        eq(Hit.Kind.OTHER, DamageLabels.kind("mymod:acid_rain"));
        eq("Damage", DamageLabels.label(""));
    }

    // ---- Text ----

    public static void testAmounts() {
        eq("1.5❤", RecapText.amount(3, true));
        eq("3", RecapText.amount(3, false));
        eq("0.25❤", RecapText.amount(0.5F, true));
        eq("2.33", RecapText.amount(2.3333F, false));
        eq("0", RecapText.amount(0, false));
    }

    public static void testSummary() {
        HitLog log = new HitLog(10_000);
        UUID p = UUID.randomUUID();
        log.add(p, new Hit(0, "Lava", "", Hit.Kind.ENVIRONMENT, "minecraft:lava", 16, 0, 0, 0, 0, 0, 16, 4, false));
        log.add(p, new Hit(2000, "Skeleton", "Bow", Hit.Kind.PROJECTILE, "minecraft:arrow", 6, 1.5F, 0.5F, 0, 0, 0, 4, 0, false));
        Recap r = log.onDeath(p, "Steve", "", 2000, null);
        String s = RecapText.summary(r, false);
        eq("Killed by Skeleton (Bow): 22 in 2 hits. Stopped: armor 1.5, enchants 0.5. Most damage: Lava.", s);
        eq("-2.0s", RecapText.time(r.hits.get(0), r));
        eq("-1.5", RecapText.cell(RecapText.Col.ARMOR, r.hits.get(1), r, false));
        eq("", RecapText.cell(RecapText.Col.ARMOR, r.hits.get(0), r, false));
        List<String> d = RecapText.details(r.hits.get(1), r, true);
        yes(d.get(0).startsWith("FINAL BLOW"), "details mark the final blow");
    }

    public static void testEmptySummary() {
        Recap r = new Recap("S", "", 0, 10_000, java.util.Collections.<Hit>emptyList());
        eq("No damage recorded in the last 10 seconds.", RecapText.summary(r, true));
    }

    // ---- Config ----

    public static void testConfigDefaultsWrittenAndClamped() throws Exception {
        File dir = Files.createTempDirectory("dbcfg").toFile();
        File f = new File(dir, "deathbreakdown.properties");
        RecapConfig c = RecapConfig.load(f);
        eq(10, c.windowSeconds);
        yes(f.isFile(), "defaults written");
        Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
        w.write("windowSeconds=999\nshowArmor=false\nhearts=banana\n");
        w.close();
        RecapConfig c2 = RecapConfig.load(f);
        eq(60, c2.windowSeconds);
        eq(false, c2.showArmor);
        eq(true, c2.hearts);                                   // junk value -> default
        String text = new String(Files.readAllBytes(f.toPath()), "UTF-8");
        yes(text.contains("chatSummary=true"), "missing keys are added back");
        eq(RecapText.columns(c2).contains(RecapText.Col.ARMOR), false);
    }

    // ---------------------------------------------------------------- 1.1.0

    public static void testCodecDeathFlag() {
        Recap r = sampleRecap();
        eq(Boolean.TRUE, RecapCodec.isDeath(RecapCodec.encodeDeath(r)));
        eq(Boolean.FALSE, RecapCodec.isDeath(RecapCodec.encodeReplay(r)));
        eq(null, RecapCodec.isDeath(RecapCodec.encode(r)));        // 1.0.0 servers send no flag
        eq(null, RecapCodec.isDeath("junk"));
        eq(null, RecapCodec.isDeath(null));
        Recap back = RecapCodec.decode(RecapCodec.encodeDeath(r)); // the flag doesn't change the recap
        eq(r.hits.size(), back.hits.size());
        eq(r.deathMessage, back.deathMessage);
    }

    public static void testOldClientsReadNewPackets() {
        // A 1.0.0 client reads top[0..3] and ignores the rest: check the flagged line still splits the same way.
        String s = RecapCodec.encodeDeath(sampleRecap());
        String[] top = s.split("\n", -1)[1].split("\t", -1);
        eq(5, top.length);
        eq("Steve", top[0]);
        Long.parseLong(top[2]);
        Long.parseLong(top[3]);
    }

    public static void testHistoryKeepsNewestAndCaps() {
        HitLog log = new HitLog(10_000);
        log.setHistorySize(3);
        UUID p = UUID.randomUUID();
        for (int i = 1; i <= 5; i++) {
            log.add(p, hit(i * 1000L, "Zombie" + i, 2, 2));
            log.onDeath(p, "Steve", "death " + i, i * 1000L, null);
        }
        List<Recap> h = log.history(p);
        eq(3, h.size());
        eq("death 3", h.get(0).deathMessage);
        eq("death 5", h.get(2).deathMessage);
        eq("death 5", log.last(p).deathMessage);
        log.setHistorySize(1);
        eq(1, log.history(p).size());
        eq("death 5", log.last(p).deathMessage);
        eq(0, log.history(UUID.randomUUID()).size());
    }

    public static void testHistoryRestoreMerges() {
        HitLog log = new HitLog(10_000);
        UUID p = UUID.randomUUID();
        log.add(p, hit(9000, "Creeper", 10, 10));
        log.onDeath(p, "Steve", "new", 9000, null);
        List<Recap> saved = new ArrayList<Recap>();
        saved.add(new Recap("Steve", "old1", 1000, 10_000, new ArrayList<Hit>()));
        saved.add(new Recap("Steve", "old2", 2000, 10_000, new ArrayList<Hit>()));
        log.restore(p, saved);
        List<Recap> h = log.history(p);
        eq(3, h.size());
        eq("old1", h.get(0).deathMessage);
        eq("new", h.get(2).deathMessage);       // the newer in-memory death stays newest
        log.restore(p, saved);                   // restoring twice doesn't duplicate
        eq(3, log.history(p).size());
    }

    public static void testStoreRoundTripAndJunk() throws Exception {
        File dir = new File("build/coretest/store-" + System.nanoTime());
        UUID p = UUID.randomUUID();
        List<Recap> list = new ArrayList<Recap>();
        list.add(sampleRecap());
        list.add(new Recap("Steve", "line\nbreak\tand tab \\ slash", 5, 10_000, new ArrayList<Hit>()));
        yes(RecapStore.save(dir, p, list), "saved");
        List<Recap> back = RecapStore.load(dir, p);
        eq(2, back.size());
        eq("line\nbreak\tand tab \\ slash", back.get(1).deathMessage);
        eq(list.get(0).hits.size(), back.get(0).hits.size());
        yes(!new File(dir, p + ".tmp").exists(), "no temp file left behind");
        eq(0, RecapStore.load(dir, UUID.randomUUID()).size());    // missing file
        java.io.FileOutputStream o = new java.io.FileOutputStream(RecapStore.fileFor(dir, p));
        o.write("DB1\nnot\tvalid\nDB1\n\u0000\u0001garbage".getBytes("UTF-8"));
        o.close();
        eq(0, RecapStore.load(dir, p).size());                     // junk file: nothing, no exception
        byte[] big = new byte[RecapStore.MAX_FILE_BYTES + 10];
        o = new java.io.FileOutputStream(RecapStore.fileFor(dir, p));
        o.write(big);
        o.close();
        eq(0, RecapStore.load(dir, p).size());                     // too big: ignored
    }

    public static void testTips() {
        List<Hit> hits = new ArrayList<Hit>();
        hits.add(new Hit(1000, "Lava", "", Hit.Kind.ENVIRONMENT, "lava", 8, 0, 0, 0, 0, 0, 8, 12, false));
        hits.add(new Hit(2000, "Lava", "", Hit.Kind.ENVIRONMENT, "lava", 8, 0, 0, 0, 0, 0, 8, 4, false));
        hits.add(new Hit(3000, "Zombie", "", Hit.Kind.MOB, "mob", 4, 0, 0, 0, 0, 0, 4, 0, true));
        eq("Lava did 80% of the damage.", RecapText.tip(new Recap("Steve", "", 3000, 10_000, hits)));
        List<Hit> geared = new ArrayList<Hit>();
        geared.add(new Hit(1000, "Skeleton", "Bow", Hit.Kind.PROJECTILE, "arrow", 10, 4, 2, 0, 0, 0, 4, 0, true));
        eq("Your gear stopped 60% of the damage.", RecapText.tip(new Recap("Steve", "", 1000, 10_000, geared)));
        List<Hit> plain = new ArrayList<Hit>();
        plain.add(new Hit(1000, "Spider", "", Hit.Kind.MOB, "mob", 2, 0, 0, 0, 0, 0, 2, 0, true));
        eq("", RecapText.tip(new Recap("Steve", "", 1000, 10_000, plain)));
        eq("", RecapText.tip(new Recap("Steve", "", 1000, 10_000, new ArrayList<Hit>())));
    }

    public static void testHoverLinesCapped() {
        List<Hit> hits = new ArrayList<Hit>();
        for (int i = 0; i < 12; i++) hits.add(new Hit(i * 100L, "Bee", "", Hit.Kind.MOB, "sting", 1, 0, 0, 0, 0, 0, 1, 20 - i, i == 11));
        Recap r = new Recap("Steve", "Steve was stung", 1200, 10_000, hits);
        List<String> lines = RecapText.hoverLines(r, true, 4);
        eq("(8 earlier hits in the full recap)", lines.get(0));
        int finals = 0;
        for (String l : lines) if (l.startsWith("FINAL BLOW")) finals++;
        eq(1, finals);
        yes(lines.size() < 30, "fits on screen: " + lines.size());
        eq(RecapText.hoverLines(r, true, 50).size() > lines.size(), true);
    }

    public static void testPlainText() {
        String t = RecapText.plainText(sampleRecap(), true);
        yes(t.startsWith("Death Breakdown: "), t);
        yes(t.contains("> -0.0s"), "final blow marked: " + t);
        yes(t.split("\n").length >= 3, t);
    }

    public static void testAgo() {
        eq("just now", RecapText.ago(1000, 5000));
        eq("30 s ago", RecapText.ago(0, 30_000));
        eq("2 min ago", RecapText.ago(0, 150_000));
        eq("3 h ago", RecapText.ago(0, 3 * 3600_000L + 5));
        eq("2 d ago", RecapText.ago(0, 2 * 86_400_000L));
        eq("just now", RecapText.ago(5000, 1000));               // clock went backwards
    }

    public static void testBrokenConfigFallsBack() throws Exception {
        File f = new File("build/coretest/broken-" + System.nanoTime() + ".properties");
        java.io.FileOutputStream o = new java.io.FileOutputStream(f);
        o.write(("windowSeconds=banana\nhearts=maybe\nopenOnDeath=no\nhistorySize=999\nshowTip=OFF\n"
                + "this line has no equals sign\n\u00ff\u00fe\n").getBytes("ISO-8859-1"));
        o.close();
        RecapConfig c = RecapConfig.load(f);
        eq(10, c.windowSeconds);            // bad number: default
        eq(true, c.hearts);                 // bad bool: default
        eq(false, c.openOnDeath);           // "no" is understood
        eq(20, c.historySize);              // clamped
        eq(false, c.showTip);               // "OFF" is understood
        yes(c.problems.size() >= 3, "problems listed: " + c.problems);
        RecapConfig again = RecapConfig.load(f); // the file was rewritten clean
        eq(0, again.problems.size());
        eq(20, again.historySize);
    }

    public static void testBadEscapeInConfig() throws Exception {
        File f = new File("build/coretest/escape-" + System.nanoTime() + ".properties");
        java.io.FileOutputStream o = new java.io.FileOutputStream(f);
        o.write("windowSeconds=20\nhearts=\\uZZZZ\n".getBytes("UTF-8"));
        o.close();
        RecapConfig c = RecapConfig.load(f);  // Properties throws IllegalArgumentException on a bad u-escape
        yes(!c.problems.isEmpty(), "broken line reported");
        eq(true, c.hearts);
    }

    public static void testConfigFolderInsteadOfFile() {
        File dir = new File("build/coretest/dir-" + System.nanoTime() + ".properties");
        yes(dir.mkdirs(), "made folder");
        RecapConfig c = RecapConfig.load(dir);
        eq(10, c.windowSeconds);
        yes(!c.problems.isEmpty(), "folder reported");
    }

    public static void testAllColumnsOffKeepsOne() throws Exception {
        File f = new File("build/coretest/cols-" + System.nanoTime() + ".properties");
        java.io.FileOutputStream o = new java.io.FileOutputStream(f);
        o.write(("showTime=false\nshowSource=false\nshowType=false\nshowDamage=false\nshowArmor=false\n"
                + "showEnchants=false\nshowOther=false\nshowHealthLeft=false\n").getBytes("UTF-8"));
        o.close();
        RecapConfig c = RecapConfig.load(f);
        eq(1, RecapText.columns(c).size());
        eq(RecapText.Col.SOURCE, RecapText.columns(c).get(0));
    }

    public static void testOldConfigKeepsValuesAndGetsNewKeys() throws Exception {
        File f = new File("build/coretest/old-" + System.nanoTime() + ".properties");
        java.io.FileOutputStream o = new java.io.FileOutputStream(f);
        o.write("# 1.0.0 file\nwindowSeconds=15\nhearts=false\nopenOnDeath=false\n".getBytes("UTF-8"));
        o.close();
        RecapConfig c = RecapConfig.load(f);
        eq(15, c.windowSeconds);
        eq(false, c.hearts);
        eq(false, c.openOnDeath);
        eq(5, c.historySize);
        String text = new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8");
        yes(text.contains("historySize=5") && text.contains("showTip=true") && text.contains("windowSeconds=15"), text);
        eq(0, c.problems.size());
    }

    static Recap sampleRecap() {
        List<Hit> hits = new ArrayList<Hit>();
        hits.add(new Hit(1000, "Skeleton", "Bow", Hit.Kind.PROJECTILE, "arrow", 4, 1.5F, 0.3F, 0, 0, 0, 2.2F, 17.8F, false));
        hits.add(new Hit(2000, "Zombie", "Iron Sword", Hit.Kind.MOB, "mob", 9, 3, 1, 0, 0, 0, 5, 0, true));
        return new Recap("Steve", "Steve was slain by Zombie", 2000, 10_000, hits);
    }
}
