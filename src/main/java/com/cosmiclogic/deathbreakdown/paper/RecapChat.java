package com.cosmiclogic.deathbreakdown.paper;

import com.cosmiclogic.deathbreakdown.core.Hit;
import com.cosmiclogic.deathbreakdown.core.Recap;
import com.cosmiclogic.deathbreakdown.core.RecapConfig;
import com.cosmiclogic.deathbreakdown.core.RecapText;
import com.cosmiclogic.logiclib.paper.LogicText;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

import java.util.List;

/** The chat version: one summary line, [details] on hover, [Open recap] opens the book. */
final class RecapChat {
    private RecapChat() {}

    static void send(Player p, Recap r, RecapConfig v) {
        try {
            p.spigot().sendMessage(build(r, v));
        } catch (Throwable t) {
            // No BungeeCord chat API on this server: plain text instead.
            LogicText.send(p, DeathBreakdownPlugin.PREFIX, RecapText.summary(r, v.hearts) + " &7(/db for details)");
        }
    }

    /** Every hit as plain chat lines; used when books can't be opened (1.12.2, 1.13). */
    static void sendFull(Player p, Recap r, RecapConfig v) {
        LogicText.send(p, DeathBreakdownPlugin.PREFIX, "&7" + r.deathMessage);
        for (Hit h : r.hits) LogicText.send(p, " " + line(h, r, v));
        LogicText.send(p, " &f" + RecapText.summary(r, v.hearts));
    }

    static String line(Hit h, Recap r, RecapConfig v) {
        StringBuilder b = new StringBuilder();
        b.append("&8").append(RecapText.time(h, r)).append(' ');
        b.append(h.finalBlow ? "&6&l" : "&f").append(RecapText.source(h)).append("&r &c").append(RecapText.amount(h.raw, v.hearts));
        if (v.showArmor && h.armor > 0.005F) b.append(" &7armor -").append(RecapText.amount(h.armor, v.hearts));
        if (v.showEnchants && h.enchants > 0.005F) b.append(" &dench -").append(RecapText.amount(h.enchants, v.hearts));
        float o = h.effects + h.absorbed + h.blocked;
        if (v.showOther && o > 0.005F) b.append(" &9other -").append(RecapText.amount(o, v.hearts));
        return b.toString();
    }

    static String hoverText(Recap r, RecapConfig v) {
        StringBuilder b = new StringBuilder("&6&lDeath Breakdown\n&7").append(r.deathMessage);
        for (Hit h : r.hits) {
            List<String> d = RecapText.details(h, r, v.hearts);
            b.append("\n\n").append(h.finalBlow ? "&6" : "&f").append(d.get(0));
            for (int i = 1; i < d.size(); i++) {
                String s = d.get(i);
                if (!v.showArmor && s.startsWith("Armor")) continue;
                if (!v.showEnchants && s.startsWith("Enchants")) continue;
                if (!v.showOther && (s.startsWith("Resistance") || s.startsWith("Absorption") || s.startsWith("Shield"))) continue;
                b.append("\n &7").append(s);
            }
        }
        return LogicText.color(b.toString());
    }

    static BaseComponent[] build(Recap r, RecapConfig v) {
        ComponentBuilder cb = new ComponentBuilder("");
        cb.append(TextComponent.fromLegacyText(LogicText.color(DeathBreakdownPlugin.PREFIX + " &f" + RecapText.summary(r, v.hearts))));
        TextComponent details = new TextComponent(LogicText.color(" &b[details]"));
        details.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(hoverText(r, v))));
        TextComponent open = new TextComponent(LogicText.color(" &a[Open recap]"));
        open.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/deathbreakdown book"));
        open.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(LogicText.color("&7Open the full recap as a book"))));
        cb.append(details).append(open);
        return cb.create();
    }
}
