package com.cosmiclogic.deathbreakdown.paper;

import com.cosmiclogic.deathbreakdown.core.Hit;
import com.cosmiclogic.deathbreakdown.core.Recap;
import com.cosmiclogic.deathbreakdown.core.RecapConfig;
import com.cosmiclogic.deathbreakdown.core.RecapText;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** The book version: a summary page, then one page per hit. Opened directly, nothing goes into the inventory. */
final class RecapBook {
    private RecapBook() {}

    private static final int MAX_PAGES = 50;

    /** Opens the book. Returns false where the server can't open books (Paper 1.12.2 / 1.13). */
    static boolean open(Player p, Recap r, RecapConfig v) {
        Method m = openBookMethod(p);
        if (m == null) return false;
        try {
            m.invoke(p, build(r, v));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    static Method openBookMethod(Object player) {
        try {
            return player.getClass().getMethod("openBook", ItemStack.class);
        } catch (Throwable t) {
            return null;
        }
    }

    static ItemStack build(Recap r, RecapConfig v) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle("Death Breakdown");
        meta.setAuthor("Death Breakdown");
        for (String page : pages(r, v)) meta.addPage(page);
        book.setItemMeta(meta);
        return book;
    }

    static List<String> pages(Recap r, RecapConfig v) {
        List<String> pages = new ArrayList<String>();
        StringBuilder first = new StringBuilder();
        first.append("§6§lDeath Breakdown§r\n\n");
        first.append("§8").append(cut(r.deathMessage, 120)).append("§r\n\n");
        Hit fb = r.finalBlow();
        if (fb != null) first.append("§0Final blow:\n§4").append(cut(RecapText.source(fb), 40)).append("§r\n\n");
        first.append("§0Hits: ").append(r.hits.size()).append('\n');
        first.append("§0Damage: §c").append(RecapText.amount(r.totalRaw(), v.hearts)).append('\n');
        if (v.showArmor) first.append("§0Armor: §8-").append(RecapText.amount(r.totalArmor(), v.hearts)).append('\n');
        if (v.showEnchants) first.append("§0Enchants: §5-").append(RecapText.amount(r.totalEnchants(), v.hearts)).append('\n');
        if (v.showOther && r.totalOther() > 0.005F) first.append("§0Other: §1-").append(RecapText.amount(r.totalOther(), v.hearts)).append('\n');
        pages.add(first.toString());

        int n = 1;
        for (Hit h : r.hits) {
            if (pages.size() >= MAX_PAGES) break;
            StringBuilder b = new StringBuilder();
            b.append(h.finalBlow ? "§4§lFINAL BLOW§r\n" : "§0§lHit " + n + "§r\n");
            b.append("§8").append(RecapText.time(h, r)).append("§r\n\n");
            b.append("§0").append(cut(RecapText.source(h), 40)).append("\n§8").append(com.cosmiclogic.deathbreakdown.core.DamageLabels.label(h.typeId)).append("§r\n\n");
            b.append("§0Damage: §c").append(RecapText.amount(h.raw, v.hearts)).append('\n');
            if (v.showArmor && h.armor > 0.005F) b.append("§0Armor: §8-").append(RecapText.amount(h.armor, v.hearts)).append('\n');
            if (v.showEnchants && h.enchants > 0.005F) b.append("§0Enchants: §5-").append(RecapText.amount(h.enchants, v.hearts)).append('\n');
            if (v.showOther) {
                if (h.effects > 0.005F) b.append("§0Resistance: §1-").append(RecapText.amount(h.effects, v.hearts)).append('\n');
                if (h.absorbed > 0.005F) b.append("§0Absorption: §6-").append(RecapText.amount(h.absorbed, v.hearts)).append('\n');
                if (h.blocked > 0.005F) b.append("§0Shield: §1-").append(RecapText.amount(h.blocked, v.hearts)).append('\n');
            }
            b.append("§0Lost: §4").append(RecapText.amount(h.dealt, v.hearts)).append('\n');
            if (v.showHealthLeft) b.append("§0Left: §2").append(RecapText.amount(Math.max(0, h.healthAfter), v.hearts));
            pages.add(b.toString());
            n++;
        }
        return pages;
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
