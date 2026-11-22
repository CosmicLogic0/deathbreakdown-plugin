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

    /** How many hits the hover shows; the rest are in the book. Keeps the tooltip on screen. */
    static final int HOVER_HITS = 6;

    static String hoverText(Recap r, RecapConfig v) {
        StringBuilder b = new StringBuilder("&6&lDeath Breakdown\n&7").append(r.deathMessage);
        String tip = v.showTip ? RecapText.tip(r) : "";
        if (!tip.isEmpty()) b.append("\n&e").append(tip);
        boolean header = true;
        for (String s : RecapText.hoverLines(r, v.hearts, HOVER_HITS)) {
            if (s.isEmpty()) { header = true; continue; }
            if (s.startsWith("(")) { b.append("\n\n&8").append(s); header = true; continue; }
            if (header) { b.append("\n\n").append(s.startsWith("FINAL BLOW") ? "&6" : "&f").append(s); header = false; continue; }
            if (!v.showArmor && s.startsWith("Armor")) continue;
            if (!v.showEnchants && s.startsWith("Enchants")) continue;
            if (!v.showOther && (s.startsWith("Resistance") || s.startsWith("Absorption") || s.startsWith("Shield"))) continue;
            b.append("\n &7").append(s);
        }
        return LogicText.color(b.toString());
    }

    /** Copy-to-clipboard clicks exist from Minecraft 1.15; returns null on older servers. */
    static TextComponent copyLink(String label, String hover, String text) {
        try {
            ClickEvent.Action copy = ClickEvent.Action.valueOf("COPY_TO_CLIPBOARD");
            TextComponent c = new TextComponent(LogicText.color(label));
            c.setClickEvent(new ClickEvent(copy, text));
            c.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(LogicText.color(hover))));
            return c;
        } catch (Throwable t) {
            return null;
        }
    }

    static void sendCopy(Player p, Recap r, RecapConfig v) {
        TextComponent c = copyLink("&b[Click here to copy your last recap]", "&7Copies it as text, for Discord or a bug report",
                RecapText.plainText(r, v.hearts));
        if (c == null) {
            LogicText.send(p, DeathBreakdownPlugin.PREFIX, "&7Copying needs Minecraft 1.15 or newer. Here it is as text:");
            for (String line : RecapText.plainText(r, v.hearts).split("\n")) p.sendMessage(line);
            return;
        }
        p.spigot().sendMessage(c);
    }

    /** "/db history": one clickable line per saved death, newest first. */
    static void sendHistory(Player p, List<Recap> history, RecapConfig v) {
        long now = System.currentTimeMillis();
        LogicText.send(p, DeathBreakdownPlugin.PREFIX, "&fYour last " + history.size() + (history.size() == 1 ? " death" : " deaths") + " &7(click to open):");
        for (int i = 0; i < history.size(); i++) {
            Recap r = history.get(history.size() - 1 - i);
            String line = "&7" + (i + 1) + ". " + RecapText.ago(r.timeMs, now) + ": &f" + RecapText.summary(r, v.hearts);
            try {
                TextComponent c = new TextComponent(TextComponent.fromLegacyText(LogicText.color(line)));
                c.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/deathbreakdown show " + (i + 1)));
                c.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(LogicText.color("&7Open this recap"))));
                p.spigot().sendMessage(c);
            } catch (Throwable t) {
                LogicText.send(p, line + " &8(/db show " + (i + 1) + ")");
            }
        }
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
        TextComponent copy = copyLink(" &7[copy]", "&7Copy this recap as text (for Discord or a bug report)", RecapText.plainText(r, v.hearts));
        if (copy != null) cb.append(copy);
        return cb.create();
    }
}
