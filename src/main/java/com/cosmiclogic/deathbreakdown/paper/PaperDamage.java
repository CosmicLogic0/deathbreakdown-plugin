package com.cosmiclogic.deathbreakdown.paper;

import com.cosmiclogic.deathbreakdown.core.DamageLabels;
import com.cosmiclogic.deathbreakdown.core.Hit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Turns a Bukkit damage event into a {@link Hit}. The armor / enchantment / Resistance / absorption parts come from
 * the event's damage modifiers, which every Paper version from 1.12.2 to today still fills in. If a future version
 * drops them, the hit is still recorded (damage and health lost), only the split is left empty.
 */
final class PaperDamage {
    private PaperDamage() {}

    private static Boolean modifiersWork;

    static String mode() {
        return modifiersAvailable() ? "exact" : "basic";
    }

    @SuppressWarnings("deprecation")
    static boolean modifiersAvailable() {
        if (modifiersWork == null) {
            try {
                Class.forName("org.bukkit.event.entity.EntityDamageEvent$DamageModifier");
                EntityDamageEvent.class.getMethod("getDamage", EntityDamageEvent.DamageModifier.class);
                modifiersWork = Boolean.TRUE;
            } catch (Throwable t) {
                modifiersWork = Boolean.FALSE;
            }
        }
        return modifiersWork;
    }

    @SuppressWarnings("deprecation")
    static Hit toHit(Player p, EntityDamageEvent e) {
        float raw = (float) e.getDamage();
        float dealt = (float) e.getFinalDamage();
        float armor = 0, enchants = 0, effects = 0, absorbed = 0, blocked = 0;
        if (modifiersAvailable()) {
            try {
                armor = part(e, EntityDamageEvent.DamageModifier.ARMOR) + part(e, EntityDamageEvent.DamageModifier.HARD_HAT);
                enchants = part(e, EntityDamageEvent.DamageModifier.MAGIC);
                effects = part(e, EntityDamageEvent.DamageModifier.RESISTANCE);
                absorbed = part(e, EntityDamageEvent.DamageModifier.ABSORPTION);
                blocked = part(e, EntityDamageEvent.DamageModifier.BLOCKING);
                // getFinalDamage already has every modifier taken off (absorption too), so it is the health lost.
            } catch (Throwable t) {
                modifiersWork = Boolean.FALSE;
            }
        }
        if (raw <= 0 && dealt <= 0) return null;
        float healthLost = Math.max(0, dealt);
        float healthAfter = (float) Math.max(0, p.getHealth() - healthLost);
        String cause = e.getCause().name();
        String source = DamageLabels.label(cause), weapon = "";
        Hit.Kind kind = DamageLabels.kind(cause);
        if (e instanceof EntityDamageByEntityEvent) {
            Entity damager = ((EntityDamageByEntityEvent) e).getDamager();
            Entity attacker = damager;
            if (damager instanceof Projectile) {
                ProjectileSource shooter = ((Projectile) damager).getShooter();
                if (shooter instanceof Entity) attacker = (Entity) shooter;
                kind = Hit.Kind.PROJECTILE;
            }
            source = name(attacker);
            weapon = held(attacker);
            if (weapon.isEmpty() && attacker != damager) weapon = name(damager);
            if (attacker instanceof Player) kind = Hit.Kind.PLAYER;
            else if (kind == Hit.Kind.OTHER || kind == Hit.Kind.ENVIRONMENT) kind = Hit.Kind.MOB;
        } else if (e instanceof EntityDamageByBlockEvent && ((EntityDamageByBlockEvent) e).getDamager() != null) {
            String block = DamageLabels.pretty(((EntityDamageByBlockEvent) e).getDamager().getType().name());
            if (!DamageLabels.known(cause) || cause.equals("CONTACT")) source = block;
        }
        return new Hit(System.currentTimeMillis(), source, weapon, kind, cause, raw, armor, enchants, effects, absorbed,
                blocked, healthLost, healthAfter, false);
    }

    /** Modifiers are negative numbers ("-3 armor"); the recap shows them as positive amounts stopped. */
    @SuppressWarnings("deprecation")
    private static float part(EntityDamageEvent e, EntityDamageEvent.DamageModifier m) {
        if (!e.isApplicable(m)) return 0;
        return (float) Math.max(0, -e.getDamage(m));
    }

    /** Used when a player died without any recorded hit (e.g. /kill on some versions). */
    static Hit fallback(Player p) {
        EntityDamageEvent last = p.getLastDamageCause();
        String cause = last == null ? "CUSTOM" : last.getCause().name();
        String source = DamageLabels.label(cause);
        if (last instanceof EntityDamageByEntityEvent) source = name(((EntityDamageByEntityEvent) last).getDamager());
        return new Hit(System.currentTimeMillis(), source, "", DamageLabels.kind(cause), cause, 0, 0, 0, 0, 0, 0, 0, 0, false);
    }

    static String name(Entity e) {
        if (e == null) return "";
        if (e instanceof Player) return ((Player) e).getName();
        String custom = e.getCustomName();
        if (custom != null && !custom.isEmpty()) return custom;
        return DamageLabels.pretty(e.getType().name());
    }

    static String held(Entity e) {
        if (!(e instanceof LivingEntity)) return "";
        try {
            EntityEquipment eq = ((LivingEntity) e).getEquipment();
            if (eq == null) return "";
            ItemStack it = eq.getItemInMainHand();
            if (it == null || it.getType().name().endsWith("AIR")) return "";
            if (it.hasItemMeta() && it.getItemMeta().hasDisplayName()) return it.getItemMeta().getDisplayName();
            return DamageLabels.pretty(it.getType().name());
        } catch (Throwable t) {
            return "";
        }
    }
}
