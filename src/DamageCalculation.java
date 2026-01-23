import java.text.DecimalFormat;
import java.util.Scanner;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class DamageCalculation {
    // ============================================================================
    // CONSTANTS - Default values and configuration
    // ============================================================================
    private static final String DEFAULT_ATTACKER_NAME = "Attacker";
    private static final String DEFAULT_DEFENDER_NAME = "Defender";
    private static final String DEFAULT_SKILL_NAME = "Basic";

    // Default stat values
    private static final double DEFAULT_ATTACKER_ATK = 1000.0;
    private static final double DEFAULT_ATTACKER_HP = 4000.0;
    private static final double DEFAULT_ATTACKER_DEF = 500.0;
    private static final double DEFAULT_ATTACKER_SPD = 100.0;
    private static final double DEFAULT_DEFENDER_HP = 8000.0;
    private static final double DEFAULT_DEFENDER_DEF = 800.0;

    // Default skill coefficients
    private static final double DEFAULT_ATK_COEF = 1.7;
    private static final double DEFAULT_DEF_COEF = 3.6;
    private static final double DEFAULT_HP_COEF = 0.19;
    private static final double DEFAULT_ACOEF = 1.7;
    private static final double DEFAULT_DCOEF = 2.9;
    private static final double DEFAULT_SPD_ADD = 60.0;
    private static final double DEFAULT_SPD_DIV = 620.0;

    // Elemental bonuses/penalties
    private static final double ELEMENT_STRONGER_DAMAGE_MUL = 1.05;
    private static final double ELEMENT_STRONGER_CRIT_DELTA = 0.15;
    private static final double ELEMENT_WEAKER_CRIT_DELTA = -0.15;
    private static final double ELEMENT_WEAKER_GLANCE_PROB = 0.5;
    private static final double ELEMENT_WEAKER_NORMAL_MUL = 0.95;
    private static final double ELEMENT_WEAKER_GLANCE_MUL = 0.70 * 0.84; // 0.588

    // Defense formula constants
    private static final double GENERIC_DEF_DIVISOR = 100.0;
    private static final double EPSILON = 1e-9;

    // ============================================================================
    // ENUMS
    // ============================================================================

    public enum FormulaType {
        GENERIC,
        SUMMONER_WAR_LIKE
    }

    // Flexible scaling modes
    public enum ScalingMode {
        ATK_COEF,
        DEF_COEF,
        HP_COEF,
        ATK_DEF_COMBO,
        SPD_WITH_ATK,
        SPD_WITH_DEF,
        SPD_WITH_HP,
        NORMAL_ATK // fallback: ATK * multiplier
    }

    // Elements
    public enum Element {
        NONE,
        FIRE,
        WIND,
        WATER,
        LIGHT,
        DARK
    }

    // Elemental relation
    private enum ElemRelation {
        NEUTRAL,
        STRONGER,
        WEAKER
    }

    public static class Unit {
        public String name;

        // element
        public Element element = Element.NONE;

        // base stats (from monster base)
        public double baseAtk = 0.0;
        public double baseHp = 0.0;
        public double baseDef = 0.0;
        public double baseSpd = 0.0;

        // bonus stats (from runes/artifacts)
        public double bonusAtk = 0.0;
        public double bonusHp = 0.0;
        public double bonusDef = 0.0;
        public double bonusSpd = 0.0;

        // attacker buffs / debuffs (expressed as decimals: 0.25 = +25%)
        public double attackBuffPercent = 0.0;
        public double flatAttack = 0.0;

        // critical
        public double critRate = 0.0;
        public double critDamage = 0.5;

        // defense-related effects (attacker-side)
        public double defenseBreakPercent = 0.0;   // reduces target DEF by percent
        public double ignoreDefensePercent = 0.0;  // removes a portion of DEF

        // global damage amplification / reduction
        public double damageAmplifyPercent = 0.0;
        public double damageReductionPercent = 0.0;

        public Unit(String name) { this.name = name; }

        public double totalAtk() { return baseAtk + bonusAtk; }
        public double totalHp() { return baseHp + bonusHp; }
        public double totalDef() { return baseDef + bonusDef; }
        public double totalSpd() { return baseSpd + bonusSpd; }
    }

    public static class Skill {
        public String name;
        public double multiplier = 1.0; // applied after base scaling value
        public double flatDamage = 0.0;
        public ScalingMode mode = ScalingMode.NORMAL_ATK;

        // multi-hit
        public int hits = 1;                  // number of hits; default 1
        public boolean ignoreDefense = false; // if true, skill bypasses defense entirely

        // coefficients for different scaling modes (tweakable / user input)
        public double coef = 1.0;        // used for ATK_COEF, DEF_COEF, HP_COEF
        public double aCoef = 1.0;       // used for ATK_DEF_COMBO
        public double dCoef = 0.0;       // used for ATK_DEF_COMBO

        // SPD pairing constants
        public double spdAdd = 60.0;     // the "+60.0" in (SPD + 60.0)
        public double spdDiv = 620.0;    // the "/620" divisor

        public Skill(String name, double multiplier, ScalingMode mode) {
            this.name = name;
            this.multiplier = multiplier;
            this.mode = mode;
        }
    }

    // Helper: determine relation of attacker element vs defender element
    private static ElemRelation elementRelation(Element attacker, Element defender) {
        if (attacker == Element.NONE || defender == Element.NONE) return ElemRelation.NEUTRAL;
        // triangle: FIRE -> WIND -> WATER -> FIRE
        if (attacker == Element.FIRE && defender == Element.WIND) return ElemRelation.STRONGER;
        if (attacker == Element.WIND && defender == Element.WATER) return ElemRelation.STRONGER;
        if (attacker == Element.WATER && defender == Element.FIRE) return ElemRelation.STRONGER;
        // two-way pair: LIGHT <-> DARK (one beats the other depending on attacker)
        if (attacker == Element.LIGHT && defender == Element.DARK) return ElemRelation.STRONGER;
        if (attacker == Element.DARK && defender == Element.LIGHT) return ElemRelation.STRONGER;

        // check opposite (defender stronger than attacker)
        if (defender == Element.FIRE && attacker == Element.WIND) return ElemRelation.WEAKER;
        if (defender == Element.WIND && attacker == Element.WATER) return ElemRelation.WEAKER;
        if (defender == Element.WATER && attacker == Element.FIRE) return ElemRelation.WEAKER;
        if (defender == Element.LIGHT && attacker == Element.DARK) return ElemRelation.WEAKER;
        if (defender == Element.DARK && attacker == Element.LIGHT) return ElemRelation.WEAKER;

        return ElemRelation.NEUTRAL;
    }

    // returns {noCritTotal, critTotal, averageTotal} — totals already summed across hits
    public static double[] calculateDamage(Unit attacker, Unit defender, Skill skill, FormulaType formulaType) {
        // Totals
        double atkTot = attacker.totalAtk();
        double defTot = defender.totalDef();
        double hpTot = attacker.totalHp();
        double spdTot = attacker.totalSpd();

        // Effective attack after attack buffs and flat (used when scaling involves ATK)
        double effectiveAttack = (atkTot * (1.0 + attacker.attackBuffPercent)) + attacker.flatAttack;

        // Compute baseScaledValue per hit according to skill.mode
        double baseScaledPerHit = 0.0;
        switch (skill.mode) {
            case ATK_COEF:
                baseScaledPerHit = skill.coef * effectiveAttack;
                break;
            case DEF_COEF:
                baseScaledPerHit = skill.coef * defender.totalDef();
                break;
            case HP_COEF:
                baseScaledPerHit = skill.coef * attacker.totalHp();
                break;
            case ATK_DEF_COMBO:
                baseScaledPerHit = skill.aCoef * effectiveAttack + skill.dCoef * defender.totalDef();
                break;
            case SPD_WITH_ATK:
                baseScaledPerHit = effectiveAttack * ((spdTot + skill.spdAdd) / skill.spdDiv);
                break;
            case SPD_WITH_DEF:
                baseScaledPerHit = defender.totalDef() * ((spdTot + skill.spdAdd) / skill.spdDiv);
                break;
            case SPD_WITH_HP:
                baseScaledPerHit = attacker.totalHp() * ((spdTot + skill.spdAdd) / skill.spdDiv);
                break;
            case NORMAL_ATK:
            default:
                baseScaledPerHit = effectiveAttack;
                break;
        }

        // Apply skill multiplier and flat damage (flatDamage is assumed per-hit here)
        double damageBeforeCritAndDefPerHit = baseScaledPerHit * skill.multiplier + skill.flatDamage;

        // -------------------------
        // Elemental interactions
        // -------------------------
        ElemRelation rel = elementRelation(attacker.element, defender.element);

        double elemDamageMul = 1.0;     // multiplicative damage bonus from element (e.g., +5% -> 1.05)
        double elemCritDelta = 0.0;     // additive change to crit rate in decimal (e.g., -0.15)
        double glancingProb = 0.0;      // probability of glancing
        double nonGlanceMultiplier = 1.0; // multiplier for non-glancing hits (for weaker gives x0.95)
        double glancingMultiplier = 1.0; // multiplier applied on glancing hit (e.g., 0.70 * 0.84)

        if (rel == ElemRelation.STRONGER) {
            elemDamageMul = ELEMENT_STRONGER_DAMAGE_MUL;     // +5% damage
            elemCritDelta = ELEMENT_STRONGER_CRIT_DELTA;     // +15% crit rate
            glancingProb = 0.0;
            nonGlanceMultiplier = 1.0;
            glancingMultiplier = 1.0;
        } else if (rel == ElemRelation.WEAKER) {
            elemDamageMul = 1.0;
            elemCritDelta = ELEMENT_WEAKER_CRIT_DELTA;    // -15% crit rate (always)
            glancingProb = ELEMENT_WEAKER_GLANCE_PROB;       // 50% chance to be a glancing hit
            // Non-glancing (the other 50%): normal/crit hits are reduced by 5%
            nonGlanceMultiplier = ELEMENT_WEAKER_NORMAL_MUL;
            // glancing reduces damage by 30% and an additional 16% on top when attacker is weaker
            glancingMultiplier = ELEMENT_WEAKER_GLANCE_MUL;
        } else {
            // NEUTRAL: no element effects
            elemDamageMul = 1.0;
            elemCritDelta = 0.0;
            glancingProb = 0.0;
            nonGlanceMultiplier = 1.0;
            glancingMultiplier = 1.0;
        }

        // -------------------------
        // Crit handling with element modifier
        // -------------------------
        double adjustedCritRate = clamp(attacker.critRate + elemCritDelta, 0.0, 1.0);
        double critMultiplier = 1.0 + attacker.critDamage; // e.g., critDamage=0.5 => x1.5

        double avgCritFactor_nonGlance = 1.0 + adjustedCritRate * (critMultiplier - 1.0);
        double avgCritFactor_glance = 1.0 + adjustedCritRate * (critMultiplier - 1.0);
        // Note: crit multiplier itself is not reduced here; the non-glance 5% or glance reductions apply multiplicatively below.

        // -------------------------
        // Apply attacker/defender global amplify/reduction (multiplicative)
        // -------------------------
        double netDamageMul = (1.0 + attacker.damageAmplifyPercent) * (1.0 - defender.damageReductionPercent);

        // Combine netDamageMul with elemDamageMul into a pre-defense per-hit base
        double perHitBase = damageBeforeCritAndDefPerHit * netDamageMul * elemDamageMul;
        // perHitBase is the non-glancing, non-crit per-hit amount before applying the non-glance or glancing multipliers and defense

        // Per-hit variants - incorporate non-glance / glancing multipliers
        double perHitNoCrit_nonGlance = perHitBase * nonGlanceMultiplier;
        double perHitNoCrit_glance = perHitBase * glancingMultiplier;

        double perHitCrit_nonGlance = perHitNoCrit_nonGlance * critMultiplier;
        double perHitCrit_glance = perHitNoCrit_glance * critMultiplier;

        // -------------------------
        // Compute effective defense after defense break and ignore (only apply if we are NOT skipping defense entirely)
        // -------------------------
        double effectiveDef = defender.totalDef() * (1.0 - attacker.defenseBreakPercent);
        effectiveDef = effectiveDef * (1.0 - attacker.ignoreDefensePercent);
        if (effectiveDef < 0) effectiveDef = 0;

        // Apply defense formula (unless skill ignores defense)
        double afterNoCrit_nonGlance, afterCrit_nonGlance, afterAvg_nonGlance;
        double afterNoCrit_glance, afterCrit_glance, afterAvg_glance;

        if (skill.ignoreDefense) {
            // Defense bypassed entirely - no defense factor applied
            afterNoCrit_nonGlance = perHitNoCrit_nonGlance;
            afterCrit_nonGlance = perHitCrit_nonGlance;
            afterAvg_nonGlance = perHitBase * nonGlanceMultiplier * avgCritFactor_nonGlance;

            afterNoCrit_glance = perHitNoCrit_glance;
            afterCrit_glance = perHitCrit_glance;
            afterAvg_glance = perHitBase * glancingMultiplier * avgCritFactor_glance;
        } else {
            // Compute defense factor depending on formula
            double factorGen = GENERIC_DEF_DIVISOR / (GENERIC_DEF_DIVISOR + effectiveDef);
            double factorSW = 1.0;
            if (effectiveAttack + effectiveDef > 0) {
                factorSW = effectiveAttack / (effectiveAttack + effectiveDef + EPSILON);
            } else {
                factorSW = 0.0;
            }

            double factorToUse = (formulaType == FormulaType.SUMMONER_WAR_LIKE) ? factorSW : factorGen;

            afterNoCrit_nonGlance = perHitNoCrit_nonGlance * factorToUse;
            afterCrit_nonGlance = perHitCrit_nonGlance * factorToUse;
            afterAvg_nonGlance = (perHitBase * nonGlanceMultiplier) * avgCritFactor_nonGlance * factorToUse;

            afterNoCrit_glance = perHitNoCrit_glance * factorToUse;
            afterCrit_glance = perHitCrit_glance * factorToUse;
            afterAvg_glance = (perHitBase * glancingMultiplier) * avgCritFactor_glance * factorToUse;
        }

        // Combine glancing probability into totals across hits
        double totalNoCrit = ( (1.0 - glancingProb) * afterNoCrit_nonGlance + glancingProb * afterNoCrit_glance ) * skill.hits;
        double totalCrit   = ( (1.0 - glancingProb) * afterCrit_nonGlance   + glancingProb * afterCrit_glance ) * skill.hits;
        double totalAvg    = ( (1.0 - glancingProb) * afterAvg_nonGlance    + glancingProb * afterAvg_glance ) * skill.hits;

        totalNoCrit = Math.max(0.0, totalNoCrit);
        totalCrit = Math.max(0.0, totalCrit);
        totalAvg = Math.max(0.0, totalAvg);

        return new double[] { totalNoCrit, totalCrit, totalAvg };
    }

    // ============================================================================
    // HELPER METHODS - Input Processing
    // ============================================================================

    private static double parseDoubleOrDefault(String s, double def) {
        if (s == null || s.isEmpty()) return def;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static double promptDouble(Scanner sc, String prompt, double defaultValue) {
        System.out.print(prompt + " [" + defaultValue + "]: ");
        return parseDoubleOrDefault(sc.nextLine().trim(), defaultValue);
    }

    private static int promptInt(Scanner sc, String prompt, int defaultValue) {
        System.out.print(prompt + " [" + defaultValue + "]: ");
        return (int) parseDoubleOrDefault(sc.nextLine().trim(), defaultValue);
    }

    private static String promptString(Scanner sc, String prompt, String defaultValue) {
        System.out.print(prompt + " [" + defaultValue + "]: ");
        String input = sc.nextLine().trim();
        return input.isEmpty() ? defaultValue : input;
    }

    private static boolean promptBoolean(Scanner sc, String prompt) {
        System.out.print(prompt + " (y/N): ");
        String input = sc.nextLine().trim().toLowerCase();
        return input.equals("y") || input.equals("yes");
    }

    private static double clamp(double v, double a, double b) {
        return Math.max(a, Math.min(b, v));
    }

    private static void printResult(String title, double[] r) {
        DecimalFormat df = new DecimalFormat("#,##0.00");
        System.out.println("=== " + title + " ===");
        System.out.println("Min (no crit): " + df.format(r[0]));
        System.out.println("Max (crit):    " + df.format(r[1]));
        System.out.println("Average:       " + df.format(r[2]));
        System.out.println();
    }

    private static ScalingMode chooseModeFromInt(int i) {
        switch (i) {
            case 1: return ScalingMode.ATK_COEF;
            case 2: return ScalingMode.DEF_COEF;
            case 3: return ScalingMode.HP_COEF;
            case 4: return ScalingMode.ATK_DEF_COMBO;
            case 5: return ScalingMode.SPD_WITH_ATK;
            case 6: return ScalingMode.SPD_WITH_DEF;
            case 7: return ScalingMode.SPD_WITH_HP;
            default: return ScalingMode.NORMAL_ATK;
        }
    }

    private static Element chooseElementFromInt(int i) {
        switch (i) {
            case 1: return Element.FIRE;
            case 2: return Element.WIND;
            case 3: return Element.WATER;
            case 4: return Element.LIGHT;
            case 5: return Element.DARK;
            default: return Element.NONE;
        }
    }

    // ============================================================================
    // HELPER METHODS - Unit Input
    // ============================================================================

    private static Unit promptAttacker(Scanner sc) {
        String name = promptString(sc, "Attacker name", DEFAULT_ATTACKER_NAME);
        Unit attacker = new Unit(name);

        System.out.println("Choose attacker element: 1=FIRE, 2=WIND, 3=WATER, 4=LIGHT, 5=DARK, else NONE");
        String elemInput = sc.nextLine().trim();
        attacker.element = elemInput.isEmpty() ? Element.NONE : chooseElementFromInt(Integer.parseInt(elemInput));

        attacker.baseAtk = promptDouble(sc, "Attacker base ATK", DEFAULT_ATTACKER_ATK);
        attacker.bonusAtk = promptDouble(sc, "Attacker bonus ATK", 0.0);
        attacker.baseHp = promptDouble(sc, "Attacker base HP", DEFAULT_ATTACKER_HP);
        attacker.bonusHp = promptDouble(sc, "Attacker bonus HP", 0.0);
        attacker.baseDef = promptDouble(sc, "Attacker base DEF", DEFAULT_ATTACKER_DEF);
        attacker.bonusDef = promptDouble(sc, "Attacker bonus DEF", 0.0);
        attacker.baseSpd = promptDouble(sc, "Attacker base SPD", DEFAULT_ATTACKER_SPD);
        attacker.bonusSpd = promptDouble(sc, "Attacker bonus SPD", 0.0);

        attacker.attackBuffPercent = promptDouble(sc, "Attacker attack buff percent", 0.0) / 100.0;
        attacker.flatAttack = promptDouble(sc, "Attacker flat attack addition", 0.0);
        attacker.critRate = promptDouble(sc, "Attacker crit rate percent", 0.0) / 100.0;
        attacker.critDamage = promptDouble(sc, "Attacker crit damage percent", 50.0) / 100.0;
        attacker.defenseBreakPercent = promptDouble(sc, "Attacker defense break percent", 0.0) / 100.0;
        attacker.ignoreDefensePercent = promptDouble(sc, "Attacker ignore defense percent", 0.0) / 100.0;
        attacker.damageAmplifyPercent = promptDouble(sc, "Attacker damage amplify percent", 0.0) / 100.0;

        return attacker;
    }

    private static Unit promptDefender(Scanner sc) {
        String name = promptString(sc, "Defender name", DEFAULT_DEFENDER_NAME);
        Unit defender = new Unit(name);

        System.out.println("Choose defender element: 1=FIRE, 2=WIND, 3=WATER, 4=LIGHT, 5=DARK, else NONE");
        String elemInput = sc.nextLine().trim();
        defender.element = elemInput.isEmpty() ? Element.NONE : chooseElementFromInt(Integer.parseInt(elemInput));

        defender.baseHp = promptDouble(sc, "Defender base HP", DEFAULT_DEFENDER_HP);
        defender.bonusHp = promptDouble(sc, "Defender bonus HP", 0.0);
        defender.baseDef = promptDouble(sc, "Defender base DEF", DEFAULT_DEFENDER_DEF);
        defender.bonusDef = promptDouble(sc, "Defender bonus DEF", 0.0);
        defender.damageReductionPercent = promptDouble(sc, "Defender damage reduction percent", 0.0) / 100.0;

        return defender;
    }

    private static Skill promptSkill(Scanner sc) {
        String name = promptString(sc, "Skill name", DEFAULT_SKILL_NAME);

        System.out.println("\nChoose scaling mode:");
        System.out.println("1) ATK_COEF        (coef * ATK)");
        System.out.println("2) DEF_COEF        (coef * DEF)");
        System.out.println("3) HP_COEF         (coef * MAX_HP)");
        System.out.println("4) ATK_DEF_COMBO   (aCoef*ATK + dCoef*DEF)");
        System.out.println("5) SPD_WITH_ATK    (ATK * (SPD + add) / div)");
        System.out.println("6) SPD_WITH_DEF    (DEF * (SPD + add) / div)");
        System.out.println("7) SPD_WITH_HP     (MAX_HP * (SPD + add) / div)");
        System.out.println("Any other -> NORMAL_ATK");

        int modeChoice = promptInt(sc, "Scaling mode", 1);
        ScalingMode mode = chooseModeFromInt(modeChoice);

        double multiplier = promptDouble(sc, "Skill multiplier", 1.0);
        double flatDamage = promptDouble(sc, "Skill flat damage", 0.0);
        int hits = Math.max(1, promptInt(sc, "Number of hits", 1));
        boolean ignoreDef = promptBoolean(sc, "Skill ignores defense entirely?");

        Skill skill = new Skill(name, multiplier, mode);
        skill.flatDamage = flatDamage;
        skill.hits = hits;
        skill.ignoreDefense = ignoreDef;

        // Prompt for coefficients based on mode
        switch (mode) {
            case ATK_COEF:
                skill.coef = promptDouble(sc, "Coefficient for ATK (coef * ATK)", DEFAULT_ATK_COEF);
                break;
            case DEF_COEF:
                skill.coef = promptDouble(sc, "Coefficient for DEF (coef * DEF)", DEFAULT_DEF_COEF);
                break;
            case HP_COEF:
                skill.coef = promptDouble(sc, "Coefficient for MAX_HP (coef * MAX_HP)", DEFAULT_HP_COEF);
                break;
            case ATK_DEF_COMBO:
                skill.aCoef = promptDouble(sc, "aCoef for ATK (aCoef * ATK)", DEFAULT_ACOEF);
                skill.dCoef = promptDouble(sc, "dCoef for DEF (dCoef * DEF)", DEFAULT_DCOEF);
                break;
            case SPD_WITH_ATK:
            case SPD_WITH_DEF:
            case SPD_WITH_HP:
                skill.spdAdd = promptDouble(sc, "SPD addition (SPD + add)", DEFAULT_SPD_ADD);
                skill.spdDiv = promptDouble(sc, "SPD divisor (/div)", DEFAULT_SPD_DIV);
                break;
            default:
                break;
        }

        return skill;
    }

    public static void main(String[] args) {
        System.out.println("Damage Calculator (primary-stat scaling, SPD pairing allowed, multi-hit skills, elemental interactions).");
        System.out.println();

        Scanner sc = new Scanner(System.in);
        try {
            // Attacker
            System.out.print("Attacker name [Attacker]: ");
            String an = sc.nextLine().trim();
            if (an.isEmpty()) an = "Attacker";
            Unit attacker = new Unit(an);

            System.out.println("Choose attacker element: 1=FIRE, 2=WIND, 3=WATER, 4=LIGHT, 5=DARK, else NONE [none]: ");
            String ae = sc.nextLine().trim();
            attacker.element = ae.isEmpty() ? Element.NONE : chooseElementFromInt(Integer.parseInt(ae));

            System.out.print("Attacker base ATK [1000]: ");
            attacker.baseAtk = parseDoubleOrDefault(sc.nextLine().trim(), 1000.0);
            System.out.print("Attacker bonus ATK [0]: ");
            attacker.bonusAtk = parseDoubleOrDefault(sc.nextLine().trim(), 0.0);

            System.out.print("Attacker base HP [4000]: ");
            attacker.baseHp = parseDoubleOrDefault(sc.nextLine().trim(), 4000.0);
            System.out.print("Attacker bonus HP [0]: ");
            attacker.bonusHp = parseDoubleOrDefault(sc.nextLine().trim(), 0.0);

            System.out.print("Attacker base DEF [500]: ");
            attacker.baseDef = parseDoubleOrDefault(sc.nextLine().trim(), 500.0);
            System.out.print("Attacker bonus DEF [0]: ");
            attacker.bonusDef = parseDoubleOrDefault(sc.nextLine().trim(), 0.0);

            System.out.print("Attacker base SPD [100]: ");
            attacker.baseSpd = parseDoubleOrDefault(sc.nextLine().trim(), 100.0);
            System.out.print("Attacker bonus SPD [0]: ");
            attacker.bonusSpd = parseDoubleOrDefault(sc.nextLine().trim(), 0.0);

            System.out.print("Attacker attack buff percent (0 for none) [0]: ");
            attacker.attackBuffPercent = parseDoubleOrDefault(sc.nextLine().trim(), 0.0) / 100.0;

            System.out.print("Attacker flat attack addition [0]: ");
            attacker.flatAttack = parseDoubleOrDefault(sc.nextLine().trim(), 0.0);

            System.out.print("Attacker crit rate percent [0]: ");
            attacker.critRate = parseDoubleOrDefault(sc.nextLine().trim(), 0.0) / 100.0;

            System.out.print("Attacker crit damage percent (e.g. 100 => +100%) [50]: ");
            attacker.critDamage = parseDoubleOrDefault(sc.nextLine().trim(), 50.0) / 100.0;

            System.out.print("Attacker defense break percent [0]: ");
            attacker.defenseBreakPercent = parseDoubleOrDefault(sc.nextLine().trim(), 0.0) / 100.0;

            System.out.print("Attacker ignore defense percent [0]: ");
            attacker.ignoreDefensePercent = parseDoubleOrDefault(sc.nextLine().trim(), 0.0) / 100.0;

            System.out.print("Attacker damage amplify percent [0]: ");
            attacker.damageAmplifyPercent = parseDoubleOrDefault(sc.nextLine().trim(), 0.0) / 100.0;

            System.out.println();

            // Defender
            System.out.print("Defender name [Defender]: ");
            String dn = sc.nextLine().trim();
            if (dn.isEmpty()) dn = "Defender";
            Unit defender = new Unit(dn);

            System.out.println("Choose defender element: 1=FIRE, 2=WIND, 3=WATER, 4=LIGHT, 5=DARK, else NONE [none]: ");
            String de = sc.nextLine().trim();
            defender.element = de.isEmpty() ? Element.NONE : chooseElementFromInt(Integer.parseInt(de));

            System.out.print("Defender base HP [8000]: ");
            defender.baseHp = parseDoubleOrDefault(sc.nextLine().trim(), 8000.0);
            System.out.print("Defender bonus HP [0]: ");
            defender.bonusHp = parseDoubleOrDefault(sc.nextLine().trim(), 0.0);

            System.out.print("Defender base DEF [800]: ");
            defender.baseDef = parseDoubleOrDefault(sc.nextLine().trim(), 800.0);
            System.out.print("Defender bonus DEF [0]: ");
            defender.bonusDef = parseDoubleOrDefault(sc.nextLine().trim(), 0.0);

            System.out.print("Defender damage reduction percent [0]: ");
            defender.damageReductionPercent = parseDoubleOrDefault(sc.nextLine().trim(), 0.0) / 100.0;

            System.out.println();

            // Skill input
            System.out.print("Skill name [Basic]: ");
            String sn = sc.nextLine().trim();
            if (sn.isEmpty()) sn = "Basic";

            System.out.println("Choose scaling mode:");
            System.out.println("1) ATK_COEF        (coef * ATK)         e.g. coef=1.7 -> 1.7*ATK");
            System.out.println("2) DEF_COEF        (coef * DEF)         e.g. coef=3.6 -> 3.6*DEF");
            System.out.println("3) HP_COEF         (coef * MAX_HP)      e.g. coef=0.19 -> 0.19*HP");
            System.out.println("4) ATK_DEF_COMBO   (aCoef*ATK + dCoef*DEF) e.g. 1.7*ATK + 2.9*DEF");
            System.out.println("5) SPD_WITH_ATK    (ATK * (SPD + add) / div)  e.g. SPD pairing");
            System.out.println("6) SPD_WITH_DEF    (DEF * (SPD + add) / div)");
            System.out.println("7) SPD_WITH_HP     (MAX_HP * (SPD + add) / div) e.g. MAX_HP*(SPD+60)/620");
            System.out.println("Any other -> NORMAL_ATK (ATK * multiplier)");
            System.out.print("Scaling mode [1]: ");
            String modeStr = sc.nextLine().trim();
            int modeInt = modeStr.isEmpty() ? 1 : Integer.parseInt(modeStr);
            ScalingMode mode = chooseModeFromInt(modeInt);

            System.out.print("Skill multiplier (applied after base scaled value) [1.0]: ");
            double sMult = parseDoubleOrDefault(sc.nextLine().trim(), 1.0);

            System.out.print("Skill flat damage [0]: ");
            double sFlat = parseDoubleOrDefault(sc.nextLine().trim(), 0.0);

            System.out.print("Number of hits (multi-hit) [1]: ");
            int hits = (int) parseDoubleOrDefault(sc.nextLine().trim(), 1.0);

            System.out.print("Skill ignores defense entirely? (y/N): ");
            String ign = sc.nextLine().trim().toLowerCase();
            boolean ignoreDef = ign.equals("y") || ign.equals("yes");

            Skill skill = new Skill(sn, sMult, mode);
            skill.flatDamage = sFlat;
            skill.hits = Math.max(1, hits);
            skill.ignoreDefense = ignoreDef;

            // coefficients prompt depending on mode
            switch (mode) {
                case ATK_COEF:
                    System.out.print("Coefficient for ATK (coef * ATK) [1.7]: ");
                    skill.coef = parseDoubleOrDefault(sc.nextLine().trim(), 1.7);
                    break;
                case DEF_COEF:
                    System.out.print("Coefficient for DEF (coef * DEF) [3.6]: ");
                    skill.coef = parseDoubleOrDefault(sc.nextLine().trim(), 3.6);
                    break;
                case HP_COEF:
                    System.out.print("Coefficient for MAX_HP (coef * MAX_HP) [0.19]: ");
                    skill.coef = parseDoubleOrDefault(sc.nextLine().trim(), 0.19);
                    break;
                case ATK_DEF_COMBO:
                    System.out.print("aCoef for ATK (aCoef * ATK) [1.7]: ");
                    skill.aCoef = parseDoubleOrDefault(sc.nextLine().trim(), 1.7);
                    System.out.print("dCoef for DEF (dCoef * DEF) [2.9]: ");
                    skill.dCoef = parseDoubleOrDefault(sc.nextLine().trim(), 2.9);
                    break;
                case SPD_WITH_ATK:
                case SPD_WITH_DEF:
                case SPD_WITH_HP:
                    System.out.print("SPD addition (SPD + add) [60.0]: ");
                    skill.spdAdd = parseDoubleOrDefault(sc.nextLine().trim(), 60.0);
                    System.out.print("SPD divisor (/div) [620.0]: ");
                    skill.spdDiv = parseDoubleOrDefault(sc.nextLine().trim(), 620.0);
                    break;
                default:
                    // nothing
                    break;
            }

            System.out.println();
            System.out.println("Choose defense formula:");
            System.out.println("1) GENERIC");
            System.out.println("2) SUMMONER_WAR_LIKE");
            System.out.print("Choice [1]: ");
            String fStr = sc.nextLine().trim();
            int fChoice = fStr.isEmpty() ? 1 : Integer.parseInt(fStr);
            FormulaType formula = FormulaType.GENERIC;
            if (fChoice == 2) formula = FormulaType.SUMMONER_WAR_LIKE;

            System.out.println();
            System.out.println("Computing...");

            double[] result = calculateDamage(attacker, defender, skill, formula);

            printSummary(attacker, defender, skill);
            printResult("Damage result (" + formula + ")", result);

            // show pre-defense baseScaled for clarity
            DecimalFormat df = new DecimalFormat("#,##0.##");
            double preScaled = baseScaledForDisplay(attacker, defender, skill);
            System.out.println("Pre-defense base scaled (per-hit, no crit): " + df.format(preScaled));
            System.out.println("Total hits: " + skill.hits);
            System.out.println("Pre-defense total (no crit): " + df.format(preScaled * skill.hits));
            System.out.println("Crit multiplier: x" + df.format(1.0 + attacker.critDamage) + " (base crit rate " + (int)(attacker.critRate * 100) + "%)");
            // show elemental relation
            ElemRelation rel = elementRelation(attacker.element, defender.element);
            System.out.println("Element interaction: Attacker " + attacker.element + " vs Defender " + defender.element + " -> " + rel);
            System.out.println();

        } catch (Exception ex) {
            System.out.println("Error: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            sc.close();
        }
    }

    private static double baseScaledForDisplay(Unit attacker, Unit defender, Skill skill) {
        double atkTot = attacker.totalAtk();
        double effectiveAttack = (atkTot * (1.0 + attacker.attackBuffPercent)) + attacker.flatAttack;
        switch (skill.mode) {
            case ATK_COEF: return skill.coef * effectiveAttack * skill.multiplier + skill.flatDamage;
            case DEF_COEF: return skill.coef * defender.totalDef() * skill.multiplier + skill.flatDamage;
            case HP_COEF: return skill.coef * attacker.totalHp() * skill.multiplier + skill.flatDamage;
            case ATK_DEF_COMBO: return (skill.aCoef * effectiveAttack + skill.dCoef * defender.totalDef()) * skill.multiplier + skill.flatDamage;
            case SPD_WITH_ATK: return effectiveAttack * ((attacker.totalSpd() + skill.spdAdd) / skill.spdDiv) * skill.multiplier + skill.flatDamage;
            case SPD_WITH_DEF: return defender.totalDef() * ((attacker.totalSpd() + skill.spdAdd) / skill.spdDiv) * skill.multiplier + skill.flatDamage;
            case SPD_WITH_HP: return attacker.totalHp() * ((attacker.totalSpd() + skill.spdAdd) / skill.spdDiv) * skill.multiplier + skill.flatDamage;
            default: return effectiveAttack * skill.multiplier + skill.flatDamage;
        }
    }

    // Improved summary printer
    private static void printSummary(Unit attacker, Unit defender, Skill skill) {
        DecimalFormat df = new DecimalFormat("#,##0.##");
        System.out.println();
        System.out.println("=== Combat Summary ===");

        // Attacker summary
        System.out.println("Attacker: " + attacker.name + "  (Element: " + attacker.element + ")");
        System.out.println("  ATK:   " + df.format(attacker.totalAtk())
                + " (base " + df.format(attacker.baseAtk) + " + bonus " + df.format(attacker.bonusAtk) + ")");
        System.out.println("  SPD:   " + df.format(attacker.totalSpd())
                + " (base " + df.format(attacker.baseSpd) + " + bonus " + df.format(attacker.bonusSpd) + ")");
        System.out.println("  HP:    " + df.format(attacker.totalHp())
                + " (base " + df.format(attacker.baseHp) + " + bonus " + df.format(attacker.bonusHp) + ")");
        System.out.println("  Attack buff: " + (int)(attacker.attackBuffPercent * 100) + "%, Flat ATK: " + df.format(attacker.flatAttack));
        System.out.println("  Crit:  " + (int)(attacker.critRate * 100) + "%  |  Crit Dmg: +" + (int)(attacker.critDamage * 100) + "%");
        System.out.println("  DEF break: " + (int)(attacker.defenseBreakPercent * 100) + "%  |  Ignore DEF%: " + (int)(attacker.ignoreDefensePercent * 100) + "%");
        System.out.println("  Damage amplify: " + (int)(attacker.damageAmplifyPercent * 100) + "%");
        System.out.println();

        // Defender summary
        System.out.println("Defender: " + defender.name + "  (Element: " + defender.element + ")");
        System.out.println("  DEF:    " + df.format(defender.totalDef())
                + " (base " + df.format(defender.baseDef) + " + bonus " + df.format(defender.bonusDef) + ")");
        System.out.println("  HP:     " + df.format(defender.totalHp()));
        System.out.println("  Damage reduction: " + (int)(defender.damageReductionPercent * 100) + "%");
        System.out.println();

        // Skill summary
        System.out.println("Skill: " + skill.name);
        System.out.println("  Scaling mode: " + skill.mode);
        System.out.println("  Multiplier:   " + skill.multiplier + "   Flat dmg per hit: " + df.format(skill.flatDamage));
        if (skill.hits > 1) {
            System.out.println("  Hits:         " + skill.hits + " (multi-hit)");
        } else {
            System.out.println("  Hits:         1");
        }
        System.out.println("  Ignores DEF:  " + (skill.ignoreDefense ? "YES (skill bypasses defense)" : "no"));

        // Mode-specific details
        switch (skill.mode) {
            case ATK_COEF:
                System.out.println("  Formula:      " + skill.coef + " * ATK");
                break;
            case DEF_COEF:
                System.out.println("  Formula:      " + skill.coef + " * target DEF");
                break;
            case HP_COEF:
                System.out.println("  Formula:      " + skill.coef + " * MAX HP");
                break;
            case ATK_DEF_COMBO:
                System.out.println("  Formula:      " + skill.aCoef + " * ATK  +  " + skill.dCoef + " * target DEF");
                break;
            case SPD_WITH_ATK:
                System.out.println("  Formula:      ATK * (SPD + " + skill.spdAdd + ") / " + skill.spdDiv);
                break;
            case SPD_WITH_DEF:
                System.out.println("  Formula:      target DEF * (SPD + " + skill.spdAdd + ") / " + skill.spdDiv);
                break;
            case SPD_WITH_HP:
                System.out.println("  Formula:      MAX HP * (SPD + " + skill.spdAdd + ") / " + skill.spdDiv);
                break;
            case NORMAL_ATK:
            default:
                System.out.println("  Formula:      ATK * multiplier");
                break;
        }

        // Elemental summary
        ElemRelation rel = elementRelation(attacker.element, defender.element);
        System.out.println();
        System.out.println("Element interaction: Attacker " + attacker.element + " vs Defender " + defender.element + " -> " + rel);
        if (rel == ElemRelation.STRONGER) {
            System.out.println("  Element effect: Attacker is stronger -> +5% damage, +15% crit rate");
        } else if (rel == ElemRelation.WEAKER) {
            System.out.println("  Element effect: Attacker is weaker -> always -15% crit rate");
            System.out.println("   - 50% chance glancing: -30% dmg, plus additional -16% when weaker (total x0.588)");
            System.out.println("   - 50% chance non-glancing: normal/crit hits are reduced by 5% (x0.95)");
        } else {
            System.out.println("  Element effect: Neutral -> no bonus/penalty");
        }

        // Helpful quick-calcs
        double perHitPreDef = baseScaledForDisplay(attacker, defender, skill);
        System.out.println();
        System.out.println("Pre-defense (per-hit, no crit): " + df.format(perHitPreDef));
        System.out.println("Pre-defense (total, no crit):   " + df.format(perHitPreDef * skill.hits));
        System.out.println();
    }

}