package com.cosmiclogic.deathbreakdown.paper;

import com.cosmiclogic.deathbreakdown.core.Hit;
import com.cosmiclogic.deathbreakdown.core.HitLog;
import com.cosmiclogic.deathbreakdown.core.Recap;
import com.cosmiclogic.deathbreakdown.core.RecapConfig;
import com.cosmiclogic.deathbreakdown.core.RecapStore;
import com.cosmiclogic.deathbreakdown.core.RecapText;
import com.cosmiclogic.logiclib.paper.LogicConfigs;
import com.cosmiclogic.logiclib.paper.LogicText;
import com.cosmiclogic.logiclib.paper.LogicVersion;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Death Breakdown for Paper 1.12.2 - 26.x: the recap in chat (with hover details) or as a book. */
public final class DeathBreakdownPlugin extends JavaPlugin implements Listener {
    static final String PREFIX = "&6[Death Breakdown]&r";

    final HitLog hits = new HitLog(10_000L);
    RecapConfig view = new RecapConfig();
    boolean chatOnDeath = true, bookOnRespawn = false;
    /** What was wrong in config.yml on the last load (shown in the console and by /db reload). */
    final List<String> problems = new ArrayList<String>();

    @Override
    public void onEnable() {
        loadSettings();
        Bukkit.getPluginManager().registerEvents(this, this);
        for (Player p : Bukkit.getOnlinePlayers()) loadHistory(p.getUniqueId()); // after /reload
        getLogger().info("Death Breakdown (Paper) " + getDescription().getVersion() + " running on Minecraft "
                + LogicVersion.current() + " (damage parts: " + PaperDamage.mode() + ")");
    }

    void loadSettings() {
        FileConfiguration c = LogicConfigs.load(this);
        problems.clear();
        RecapConfig v = new RecapConfig();
        v.windowSeconds = intSetting(c, "window-seconds", 10, 1, 60);
        v.hearts = boolSetting(c, "hearts", true);
        v.showArmor = boolSetting(c, "show.armor", true);
        v.showEnchants = boolSetting(c, "show.enchants", true);
        v.showOther = boolSetting(c, "show.other", true);
        v.showHealthLeft = boolSetting(c, "show.health-left", true);
        v.showTip = boolSetting(c, "show-tip", true);
        v.historySize = intSetting(c, "history-size", 5, 1, 20);
        v.saveHistory = boolSetting(c, "save-history", true);
        view = v;
        hits.setWindowMs(v.windowMs());
        hits.setHistorySize(v.historySize);
        chatOnDeath = boolSetting(c, "chat-on-death", true);
        bookOnRespawn = boolSetting(c, "book-on-respawn", false);
        for (String p : problems) getLogger().warning("config.yml: " + p);
    }

    /**
     * Bukkit's getInt turns "banana" into 0 (which 1.0.0 then clamped to a 1-second window). Anything that isn't a
     * number now falls back to the default, with a warning.
     */
    int intSetting(FileConfiguration c, String key, int def, int lo, int hi) {
        Object raw = c.get(key);
        if (raw == null) return def;
        if (!(raw instanceof Number)) {
            problems.add(key + ": '" + raw + "' isn't a number, using " + def);
            return def;
        }
        int v = ((Number) raw).intValue();
        int out = Math.max(lo, Math.min(hi, v));
        if (out != v) problems.add(key + ": " + v + " is outside " + lo + "-" + hi + ", using " + out);
        return out;
    }

    boolean boolSetting(FileConfiguration c, String key, boolean def) {
        Object raw = c.get(key);
        if (raw == null) return def;
        if (raw instanceof Boolean) return (Boolean) raw;
        problems.add(key + ": '" + raw + "' isn't true or false, using " + def);
        return def;
    }

    File historyDir() { return new File(getDataFolder(), "history"); }

    void loadHistory(UUID id) {
        if (view.saveHistory) hits.restore(id, RecapStore.load(historyDir(), id));
    }

    /** Saves off the main thread: a few KB, but never on the tick. */
    void saveHistory(final UUID id) {
        if (!view.saveHistory) return;
        final List<Recap> snapshot = hits.history(id);
        final File dir = historyDir();
        Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override public void run() {
                if (!RecapStore.save(dir, id, snapshot)) getLogger().warning("Couldn't save death history to " + dir);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Hit h = PaperDamage.toHit((Player) e.getEntity(), e);
        if (h != null) hits.add(e.getEntity().getUniqueId(), h);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        final Player p = e.getEntity();
        String msg = e.getDeathMessage();
        Hit fallback = PaperDamage.fallback(p);
        final Recap r = hits.onDeath(p.getUniqueId(), p.getName(), msg == null ? "" : msg, System.currentTimeMillis(), fallback);
        saveHistory(p.getUniqueId());
        if (!chatOnDeath) return;
        // Next tick, so the line shows under the death message.
        Bukkit.getScheduler().runTask(this, new Runnable() {
            @Override public void run() { if (p.isOnline()) RecapChat.send(p, r, view); }
        });
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (!bookOnRespawn) return;
        final Player p = e.getPlayer();
        final Recap r = hits.last(p.getUniqueId());
        if (r == null) return;
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override public void run() { if (p.isOnline()) RecapBook.open(p, r, view); }
        }, 5L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        loadHistory(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        hits.forget(e.getPlayer().getUniqueId()); // live hits only; the history stays for /db after rejoining
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("help")) { help(sender, label); return true; }
        if (sub.equals("reload")) {
            if (!sender.hasPermission("deathbreakdown.reload")) { LogicText.send(sender, PREFIX, "&cNo permission."); return true; }
            loadSettings();
            if (problems.isEmpty()) LogicText.send(sender, PREFIX, "&aConfig reloaded.");
            else {
                LogicText.send(sender, PREFIX, "&eConfig reloaded with " + problems.size() + " fix(es):");
                for (String p : problems) LogicText.send(sender, " &e- " + p);
            }
            return true;
        }
        if (sub.equals("selftest")) {
            if (!sender.isOp() && sender instanceof Player) { LogicText.send(sender, PREFIX, "&cNo permission."); return true; }
            SelfTest.run(this, sender);
            return true;
        }
        if (sub.equals("player")) {
            if (!sender.hasPermission("deathbreakdown.others")) { LogicText.send(sender, PREFIX, "&cNo permission."); return true; }
            if (args.length < 2) { LogicText.send(sender, PREFIX, "&7Usage: /" + label + " player <name>"); return true; }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { LogicText.send(sender, PREFIX, "&c" + args[1] + " isn't online."); return true; }
            Recap r = hits.last(target.getUniqueId());
            if (r == null) { LogicText.send(sender, PREFIX, "&7" + target.getName() + " has no death recap yet."); return true; }
            if (sender instanceof Player) RecapChat.send((Player) sender, r, view);
            else for (String line : RecapText.plainText(r, view.hearts).split("\n")) sender.sendMessage(line);
            return true;
        }
        if (!(sender instanceof Player)) { LogicText.send(sender, PREFIX, "Only players have a death recap. Try /" + label + " player <name>."); return true; }
        Player p = (Player) sender;
        List<Recap> history = hits.history(p.getUniqueId());
        if (history.isEmpty()) { LogicText.send(p, PREFIX, "&7No death recap yet. It appears here after you die (/" + label + " help)."); return true; }
        Recap r = history.get(history.size() - 1);
        if (sub.equals("history")) { RecapChat.sendHistory(p, history, view); return true; }
        if (sub.equals("show")) {
            int n = 1;
            try { n = Integer.parseInt(args.length > 1 ? args[1] : "1"); } catch (NumberFormatException ignored) {}
            if (n < 1 || n > history.size()) {
                LogicText.send(p, PREFIX, "&7You have " + history.size() + (history.size() == 1 ? " death" : " deaths") + " saved. /" + label + " history lists them.");
                return true;
            }
            r = history.get(history.size() - n);
            if (!RecapBook.open(p, r, view)) RecapChat.sendFull(p, r, view);
            return true;
        }
        if (sub.equals("copy")) { RecapChat.sendCopy(p, r, view); return true; }
        if (sub.equals("chat")) RecapChat.send(p, r, view);
        else if (!RecapBook.open(p, r, view)) RecapChat.sendFull(p, r, view);
        return true;
    }

    private void help(CommandSender s, String label) {
        String c = "/" + label;
        LogicText.send(s, PREFIX, "&fSee exactly what killed you.");
        LogicText.send(s, " &b" + c + " &7- your last death recap (a book; in chat on 1.12-1.13)");
        LogicText.send(s, " &b" + c + " chat &7- the one-line summary again, with [details] on hover");
        LogicText.send(s, " &b" + c + " history &7- your last " + view.historySize + " deaths, click one to open it");
        LogicText.send(s, " &b" + c + " show <number> &7- open one from the history (1 = newest)");
        LogicText.send(s, " &b" + c + " copy &7- copy your last recap as text");
        if (s.hasPermission("deathbreakdown.others")) LogicText.send(s, " &b" + c + " player <name> &7- another player's last recap");
        if (s.hasPermission("deathbreakdown.reload")) LogicText.send(s, " &b" + c + " reload &7- reload config.yml");
    }
}
