package com.cosmiclogic.deathbreakdown.core;

/**
 * Vanilla damage reduction, split into the parts the recap shows.
 * Order matches LivingEntity#actuallyHurt: shield, armor, Resistance, enchantments, then absorption hearts.
 * Used where the loader doesn't report the parts itself (Forge, Paper, older versions).
 */
public final class DamageMath {
    private DamageMath() {}

    /** Result of {@link #split}. Every field is in health points. */
    public static final class Split {
        public final float armor, effects, enchants, absorbed, dealt;
        Split(float armor, float effects, float enchants, float absorbed, float dealt) {
            this.armor = armor; this.effects = effects; this.enchants = enchants; this.absorbed = absorbed; this.dealt = dealt;
        }
    }

    /** Vanilla armor formula (CombatRules.getDamageAfterAbsorb, 1.9+). */
    public static float afterArmor(float damage, float armor, float toughness) {
        if (damage <= 0) return 0;
        float f = 2.0F + toughness / 4.0F;
        float effective = clamp(armor - damage / f, armor * 0.2F, 20.0F);
        return damage * (1.0F - effective / 25.0F);
    }

    /** Resistance: each level stops 20%, level 5+ stops everything. amplifier = level - 1, or -1 for none. */
    public static float afterResistance(float damage, int amplifier) {
        if (amplifier < 0 || damage <= 0) return damage;
        int level = amplifier + 1;
        return Math.max(damage * (25 - level * 5) / 25.0F, 0.0F);
    }

    /** Protection enchantments: each EPF point stops 4%, capped at 20 points (80%). */
    public static float afterEnchants(float damage, float protectionPoints) {
        if (protectionPoints <= 0 || damage <= 0) return damage;
        float f = clamp(protectionPoints, 0.0F, 20.0F);
        return damage * (1.0F - f / 25.0F);
    }

    /**
     * Splits one hit into what each layer stopped.
     * @param damage           damage after a shield (if any) and before armor
     * @param armor            armor points (0-30)
     * @param toughness        armor toughness
     * @param protectionPoints summed EPF from enchantments for this damage type
     * @param resistanceAmp    Resistance amplifier, -1 for none
     * @param absorptionHearts absorption health available before the hit
     * @param bypassArmor      damage type ignores armor (fall, fire tick, magic, ...)
     * @param bypassEnchants   damage type ignores enchantments (/kill, void)
     * @param bypassEffects    damage type ignores Resistance (/kill, void)
     */
    public static Split split(float damage, float armor, float toughness, float protectionPoints, int resistanceAmp,
                              float absorptionHearts, boolean bypassArmor, boolean bypassEnchants, boolean bypassEffects) {
        float d = Math.max(0, damage);
        float afterArmor = bypassArmor ? d : afterArmor(d, armor, toughness);
        float afterRes = bypassEffects ? afterArmor : afterResistance(afterArmor, resistanceAmp);
        float afterEnch = bypassEnchants ? afterRes : afterEnchants(afterRes, protectionPoints);
        float absorbed = Math.min(Math.max(0, absorptionHearts), afterEnch);
        float dealt = afterEnch - absorbed;
        return new Split(d - afterArmor, afterArmor - afterRes, afterRes - afterEnch, absorbed, dealt);
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
