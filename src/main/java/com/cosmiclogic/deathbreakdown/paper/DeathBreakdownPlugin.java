package com.cosmiclogic.deathbreakdown.paper;

import com.cosmiclogic.deathbreakdown.core.Hit;
import com.cosmiclogic.deathbreakdown.core.HitLog;
import com.cosmiclogic.deathbreakdown.core.Recap;
import com.cosmiclogic.deathbreakdown.core.RecapConfig;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Death Breakdown for Paper 1.12.2 - 26.x: the recap in chat (with hover details) or as a book. */
public final class DeathBreakdownPlugin extends JavaPlugin implements Listener {
    static final String PREFIX = "&6[Death Breakdown]&r";

    final HitLog hits = new HitLog(10_000L);
    RecapConfig view = new RecapConfig();
    boolean chatOnDeath = true, bookOnRespawn = false;

    @Override
    public void onEnable() {
        loadSettings();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("Death Breakdown (Paper) " + getDescription().getVersion() + " running on Minecraft "
                + LogicVersion.current() + " (damage parts: " + PaperDamage.mode() + ")");
    }

    void loadSettings() {
        FileConfiguration c = LogicConfigs.load(this);
        RecapConfig v = new RecapConfig();
        v.windowSeconds = Math.max(1, Math.min(60, c.getInt("window-seconds", 10)));
        v.hearts = c.getBoolean("hearts", true);
        v.showArmor = c.getBoolean("show.armor", true);
        v.showEnchants = c.getBoolean("show.enchants", true);
        v.showOther = c.getBoolean("show.other", true);
        v.showHealthLeft = c.getBoolean("show.health-left", true);
        view = v;
        hits.setWindowMs(v.windowMs());
        chatOnDeath = c.getBoolean("chat-on-death", true);
        bookOnRespawn = c.getBoolean("book-on-respawn", false);
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
    public void onQuit(PlayerQuitEvent e) {
        hits.forget(e.getPlayer().getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(java.util.Locale.ROOT) : "";
        if (sub.equals("reload")) {
            if (!sender.hasPermission("deathbreakdown.reload")) { LogicText.send(sender, PREFIX, "&cNo permission."); return true; }
            loadSettings();
            LogicText.send(sender, PREFIX, "&aConfig reloaded.");
            return true;
        }
        if (sub.equals("selftest")) {
            if (!sender.isOp() && sender instanceof Player) { LogicText.send(sender, PREFIX, "&cNo permission."); return true; }
            SelfTest.run(this, sender);
            return true;
        }
        if (!(sender instanceof Player)) { LogicText.send(sender, PREFIX, "Only players have a death recap."); return true; }
        Player p = (Player) sender;
        Recap r = hits.last(p.getUniqueId());
        if (r == null) { LogicText.send(p, PREFIX, "&7No death recap yet. You haven't died since joining."); return true; }
        if (sub.equals("chat")) RecapChat.send(p, r, view);
        else if (!RecapBook.open(p, r, view)) RecapChat.sendFull(p, r, view);
        return true;
    }
}
