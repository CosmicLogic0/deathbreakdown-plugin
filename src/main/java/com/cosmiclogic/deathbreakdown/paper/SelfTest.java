package com.cosmiclogic.deathbreakdown.paper;

import com.cosmiclogic.deathbreakdown.core.Hit;
import com.cosmiclogic.deathbreakdown.core.HitLog;
import com.cosmiclogic.deathbreakdown.core.Recap;
import com.cosmiclogic.deathbreakdown.core.RecapText;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.UUID;

/**
 * "/deathbreakdown selftest" from the console. The CI runs it on every Paper version to check that everything
 * version-specific works there: the damage modifiers, chat components and the recap book.
 */
final class SelfTest {
    private SelfTest() {}

    static void run(DeathBreakdownPlugin plugin, CommandSender out) {
        StringBuilder report = new StringBuilder();
        try {
            HitLog log = new HitLog(10_000L);
            UUID id = UUID.randomUUID();
            long now = System.currentTimeMillis();
            log.add(id, new Hit(now - 3000, "Skeleton", "Bow", Hit.Kind.PROJECTILE, "PROJECTILE", 6, 2, 1, 0, 0, 0, 3, 17, false));
            log.add(id, new Hit(now - 1000, "Lava", "", Hit.Kind.ENVIRONMENT, "LAVA", 4, 0, 0, 0, 2, 0, 2, 15, false));
            log.add(id, new Hit(now, "Zombie", "Iron Sword", Hit.Kind.MOB, "ENTITY_ATTACK", 20, 5, 2, 1, 0, 0, 12, 0, false));
            Recap r = log.onDeath(id, "Tester", "Tester was slain by Zombie", now, null);
            check(r.hits.size() == 3 && r.finalBlow().finalBlow, "recap built");
            report.append("recap ok; ");

            String summary = RecapText.summary(r, true);
            check(summary.startsWith("Killed by Zombie (Iron Sword)"), "summary: " + summary);

            report.append("damage parts ").append(PaperDamage.mode()).append("; ");

            try {
                BaseComponent[] c = RecapChat.build(r, plugin.view);
                check(c.length > 0, "chat components");
                report.append("chat components ok; ");
            } catch (NoClassDefFoundError e) {
                report.append("chat components unavailable (plain text fallback); ");
            }

            ItemStack book = RecapBook.build(r, plugin.view);
            BookMeta meta = (BookMeta) book.getItemMeta();
            check(meta.getPageCount() == 1 + r.hits.size(), "book pages = " + meta.getPageCount());
            report.append("book ").append(meta.getPageCount()).append(" pages; ");
            boolean canOpen;
            try { org.bukkit.entity.Player.class.getMethod("openBook", ItemStack.class); canOpen = true; } catch (NoSuchMethodException e) { canOpen = false; }
            report.append(canOpen ? "book opens directly" : "no openBook (chat lines instead)").append(';');

            // 1.1.0: capped hover, tip, history saved in the plugin folder, broken config values, copy link
            java.util.List<Hit> many = new java.util.ArrayList<Hit>();
            for (int i = 0; i < 30; i++) many.add(new Hit(now - 3000 + i * 100, "Bee", "", Hit.Kind.MOB, "ENTITY_ATTACK", 1, 0, 0, 0, 0, 0, 1, 20 - i * 0.5F, i == 29));
            Recap swarm = new Recap("Tester", "Tester was stung to death", now, 10_000, many);
            int lines = RecapChat.hoverText(swarm, plugin.view).split("\n").length;
            check(lines < 60, "hover fits on screen (" + lines + " lines)");
            check(!RecapText.tip(r).isEmpty(), "tip for the test death");
            report.append("hover capped (").append(lines).append(" lines); ");

            HitLog hist = new HitLog(10_000L);
            hist.setHistorySize(3);
            for (int i = 0; i < 5; i++) {
                hist.add(id, new Hit(now + i, "Lava", "", Hit.Kind.ENVIRONMENT, "LAVA", 4, 0, 0, 0, 0, 0, 4, 0, false));
                hist.onDeath(id, "Tester", "death " + i, now + i, null);
            }
            java.util.List<Recap> h = hist.history(id);
            check(h.size() == 3 && "death 4".equals(h.get(2).deathMessage), "history keeps the newest 3");
            java.io.File dir = new java.io.File(plugin.getDataFolder(), "selftest-history");
            UUID tmp = UUID.randomUUID();
            check(com.cosmiclogic.deathbreakdown.core.RecapStore.save(dir, tmp, h), "history saved");
            check(com.cosmiclogic.deathbreakdown.core.RecapStore.load(dir, tmp).size() == 3, "history loaded back");
            check(com.cosmiclogic.deathbreakdown.core.RecapStore.fileFor(dir, tmp).delete() && dir.delete(), "test files removed");
            report.append("history + save ok; ");

            org.bukkit.configuration.file.YamlConfiguration bad = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new java.io.StringReader("window-seconds: banana\nhearts: maybe\nhistory-size: 999\n"));
            bad.setDefaults(plugin.getConfig().getDefaults());
            check(com.cosmiclogic.logiclib.paper.LogicConfigs.problems(bad).size() == 2, "LogicLib reports 2 wrong types: "
                    + com.cosmiclogic.logiclib.paper.LogicConfigs.problems(bad));
            check(bad.getInt("window-seconds", 10) == 10, "bad number falls back to 10");
            check(bad.getBoolean("hearts", true), "bad true/false falls back");
            java.util.List<String> saved = new java.util.ArrayList<String>(plugin.problems);
            check(plugin.range(bad, "history-size", 5, 1, 20) == 20, "history-size clamped to 20");
            plugin.problems.clear();
            plugin.problems.addAll(saved);
            check(DeathBreakdownPlugin.atLeast("0.2.0", "0.2.0") && DeathBreakdownPlugin.atLeast("0.10.1", "0.2.0")
                    && DeathBreakdownPlugin.atLeast("1.0", "0.2.0") && !DeathBreakdownPlugin.atLeast("0.1.0", "0.2.0")
                    && !DeathBreakdownPlugin.atLeast("none", "0.2.0"), "LogicLib version check");
            report.append("broken config ok; ");

            boolean copy = RecapChat.copyLink("x", "y", "z") != null;
            report.append(copy ? "copy link ok; " : "no copy links before 1.15 (text instead); ");

            plugin.getLogger().info("Death Breakdown selftest PASS: " + report);
            if (!(out instanceof org.bukkit.command.ConsoleCommandSender)) out.sendMessage("Death Breakdown selftest PASS: " + report);
        } catch (Throwable t) {
            plugin.getLogger().severe("Death Breakdown selftest FAIL: " + t);
            t.printStackTrace();
            if (!(out instanceof org.bukkit.command.ConsoleCommandSender)) out.sendMessage("Death Breakdown selftest FAIL: " + t);
        }
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("check failed: " + what);
    }
}
