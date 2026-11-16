package com.cosmiclogic.deathbreakdown.core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a damage type id into a short readable label and a {@link Hit.Kind}.
 * Knows modern ids ("minecraft:arrow"), pre-1.19.4 msgIds ("arrow", "inFire") and Bukkit DamageCause names
 * ("PROJECTILE"). Anything unknown (modded) is turned into a readable label from its id: "mymod:acid_rain" becomes "Acid Rain".
 */
public final class DamageLabels {
    private DamageLabels() {}

    private static final Map<String, String> LABEL = new HashMap<String, String>();
    private static final Map<String, Hit.Kind> KIND = new HashMap<String, Hit.Kind>();

    private static void def(String label, Hit.Kind kind, String... ids) {
        for (String id : ids) {
            String k = norm(id);
            LABEL.put(k, label);
            KIND.put(k, kind);
        }
    }

    static {
        Hit.Kind M = Hit.Kind.MOB, P = Hit.Kind.PROJECTILE, E = Hit.Kind.ENVIRONMENT, X = Hit.Kind.EXPLOSION, G = Hit.Kind.MAGIC;
        def("Melee", M, "mob_attack", "mob_attack_no_aggro", "mob", "ENTITY_ATTACK", "ENTITY_SWEEP_ATTACK", "sting");
        def("Melee", Hit.Kind.PLAYER, "player_attack", "player");
        def("Arrow", P, "arrow", "PROJECTILE");
        def("Trident", P, "trident");
        def("Projectile", P, "mob_projectile", "thrown", "spit", "wind_charge");
        def("Fireball", P, "fireball", "unattributed_fireball");
        def("Wither Skull", P, "wither_skull", "witherSkull");
        def("Fireworks", X, "fireworks");
        def("Explosion", X, "explosion", "player_explosion", "explosion.player", "BLOCK_EXPLOSION", "ENTITY_EXPLOSION",
                "bad_respawn_point", "badRespawnPoint");
        def("Fire", E, "in_fire", "inFire", "FIRE");
        def("Burning", E, "on_fire", "onFire", "FIRE_TICK");
        def("Lava", E, "lava", "LAVA");
        def("Magma Block", E, "hot_floor", "hotFloor", "HOT_FLOOR");
        def("Fall", E, "fall", "FALL");
        def("Ender Pearl", E, "ender_pearl");
        def("Hit a Wall", E, "fly_into_wall", "flyIntoWall", "FLY_INTO_WALL");
        def("Drowning", E, "drown", "DROWNING");
        def("Suffocation", E, "in_wall", "inWall", "SUFFOCATION");
        def("Squished", E, "cramming", "CRAMMING");
        def("Cactus", E, "cactus", "CONTACT");
        def("Berry Bush", E, "sweet_berry_bush", "sweetBerryBush");
        def("Freezing", E, "freeze", "FREEZE");
        def("Starving", E, "starve", "STARVATION");
        def("Lightning", E, "lightning_bolt", "lightningBolt", "LIGHTNING");
        def("Falling Anvil", E, "falling_anvil", "anvil");
        def("Falling Block", E, "falling_block", "fallingBlock", "FALLING_BLOCK");
        def("Stalactite", E, "falling_stalactite", "fallingStalactite");
        def("Stalagmite", E, "stalagmite");
        def("Void", E, "out_of_world", "outOfWorld", "VOID");
        def("World Border", E, "outside_border", "WORLD_BORDER");
        def("Killed", Hit.Kind.OTHER, "generic_kill", "KILL", "SUICIDE");
        def("Damage", Hit.Kind.OTHER, "generic", "CUSTOM");
        def("Magic", G, "magic", "indirect_magic", "indirectMagic", "MAGIC");
        def("Poison", G, "POISON");
        def("Wither", G, "wither", "WITHER");
        def("Dragon Breath", G, "dragon_breath", "dragonBreath", "DRAGON_BREATH");
        def("Sonic Boom", G, "sonic_boom", "SONIC_BOOM");
        def("Thorns", G, "thorns", "THORNS");
        def("Mace Smash", M, "mace_smash");
        def("Melting", E, "MELTING");
        def("Dried Out", E, "dry_out", "DRYOUT");
        def("Campfire", E, "campfire", "CAMPFIRE");
    }

    /** "minecraft:in_fire" → "in_fire"; "PROJECTILE" stays as is (DamageCause names are upper case). */
    private static String norm(String id) {
        if (id == null) return "";
        String s = id.startsWith("minecraft:") ? id.substring(10) : id;
        return s;
    }

    public static String label(String id) {
        String k = norm(id);
        String l = LABEL.get(k);
        if (l != null) return l;
        return pretty(id);
    }

    public static Hit.Kind kind(String id) {
        Hit.Kind k = KIND.get(norm(id));
        return k == null ? Hit.Kind.OTHER : k;
    }

    public static boolean known(String id) { return LABEL.containsKey(norm(id)); }

    /** "mymod:acid_rain" → "Acid Rain", "ENTITY_ATTACK" → "Entity Attack", "inFire" → "In Fire". */
    public static String pretty(String id) {
        if (id == null || id.isEmpty()) return "Damage";
        String s = id;
        int colon = s.indexOf(':');
        if (colon >= 0) s = s.substring(colon + 1);
        s = s.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ').replace('.', ' ').trim();
        StringBuilder b = new StringBuilder();
        for (String w : s.split("\\s+")) {
            if (w.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(w.substring(0, 1).toUpperCase(Locale.ROOT)).append(w.substring(1).toLowerCase(Locale.ROOT));
        }
        return b.length() == 0 ? "Damage" : b.toString();
    }
}
