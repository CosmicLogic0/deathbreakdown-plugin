package com.cosmiclogic.deathbreakdown.core;

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
        yes(log.last(p) == null, "forget clears everything");
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

    // ---- Creator server (Server Promotion Policy) ----

    public static void testCreatorServerOnceAcrossMods() throws Exception {
        java.nio.file.Path game = Files.createTempDirectory("dbgame");
        eq("added", CreatorServer.run(game, true, "deathbreakdown"));
        byte[] list = Files.readAllBytes(game.resolve("servers.dat"));
        yes(ServersDat.has(list, CreatorServer.IP), "server in the list");
        yes(Files.exists(game.resolve(CreatorServer.MARKER)), "shared marker written");
        // the player deletes it: no CosmicLogic mod adds it again
        Files.delete(game.resolve("servers.dat"));
        eq("marker", CreatorServer.run(game, true, "othermod"));
        yes(!Files.exists(game.resolve("servers.dat")), "not re-added");
    }

    public static void testCreatorServerKeepsPlayersServersAndAppends() throws Exception {
        java.nio.file.Path game = Files.createTempDirectory("dbgame2");
        byte[] mine = ServersDat.withServer(ServersDat.withServer(null, "My SMP", "smp.example.com"), "Hypixel", "mc.hypixel.net");
        Files.write(game.resolve("servers.dat"), mine);
        eq("added", CreatorServer.run(game, true, "deathbreakdown"));
        byte[] now = Files.readAllBytes(game.resolve("servers.dat"));
        yes(ServersDat.has(now, "smp.example.com") && ServersDat.has(now, "mc.hypixel.net"), "player's servers kept");
        String s = new String(now, "ISO-8859-1");
        yes(s.indexOf("mc.hypixel.net") < s.indexOf(CreatorServer.IP), "added at the end");
        yes(Files.exists(game.resolve("servers.dat_old")), "backup kept");
    }

    public static void testCreatorServerOffAndAlreadyThere() throws Exception {
        java.nio.file.Path game = Files.createTempDirectory("dbgame3");
        eq("disabled", CreatorServer.run(game, false, "deathbreakdown"));
        yes(!Files.exists(game.resolve(CreatorServer.MARKER)), "no marker when off (modpacks can still ship one)");
        Files.write(game.resolve("servers.dat"), ServersDat.withServer(null, "LS", "PLAY.logicsurvival.fyi:25565"));
        eq("already-there", CreatorServer.run(game, true, "deathbreakdown"));
        yes(Files.exists(game.resolve(CreatorServer.MARKER)), "marker written even when it was already there");
    }
}
