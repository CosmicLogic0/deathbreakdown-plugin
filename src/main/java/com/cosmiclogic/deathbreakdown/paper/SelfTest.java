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
