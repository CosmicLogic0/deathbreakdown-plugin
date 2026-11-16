package com.cosmiclogic.deathbreakdown.core;

/**
 * One hit the player took. Plain Java (Java 8) so the same class works in every loader and in the Paper plugin.
 * All damage values are in health points (1 heart = 2 points).
 */
public final class Hit {
    /** What kind of thing dealt the hit; decides the icon/color of the "source" column. */
    public enum Kind { PLAYER, MOB, PROJECTILE, EXPLOSION, ENVIRONMENT, MAGIC, OTHER }

    public final long timeMs;          // when the hit landed (server clock)
    public final String source;        // "Skeleton", "Steve", "Lava", "Fall"
    public final String weapon;        // "Bow", "Iron Sword" or "" when none
    public final Kind kind;
    public final String typeId;        // raw damage type id, e.g. "minecraft:arrow" (kept for modded types)
    public final float raw;            // damage before any reduction
    public final float armor;          // stopped by armor points + toughness
    public final float enchants;       // stopped by Protection-type enchantments
    public final float effects;        // stopped by Resistance
    public final float absorbed;       // eaten by absorption hearts (gold hearts)
    public final float blocked;        // stopped by a shield
    public final float dealt;          // health actually lost
    public final float healthAfter;    // health left after this hit
    public final boolean finalBlow;

    public Hit(long timeMs, String source, String weapon, Kind kind, String typeId, float raw, float armor,
               float enchants, float effects, float absorbed, float blocked, float dealt, float healthAfter,
               boolean finalBlow) {
        this.timeMs = timeMs;
        this.source = source == null ? "" : source;
        this.weapon = weapon == null ? "" : weapon;
        this.kind = kind == null ? Kind.OTHER : kind;
        this.typeId = typeId == null ? "" : typeId;
        this.raw = raw;
        this.armor = armor;
        this.enchants = enchants;
        this.effects = effects;
        this.absorbed = absorbed;
        this.blocked = blocked;
        this.dealt = dealt;
        this.healthAfter = healthAfter;
        this.finalBlow = finalBlow;
    }

    public Hit asFinalBlow() {
        return new Hit(timeMs, source, weapon, kind, typeId, raw, armor, enchants, effects, absorbed, blocked, dealt,
                healthAfter, true);
    }

    /** Everything that stopped damage on this hit. */
    public float totalStopped() {
        return armor + enchants + effects + absorbed + blocked;
    }
}
