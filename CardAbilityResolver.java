import java.util.*;

/**
 * Resolves abilities on regular (non-champion) cards.
 *
 * Ability types:
 *   "active"  – player activates manually (spends an action)
 *   "ondeath" – fires automatically when the card is defeated
 *   "passive" – applied continuously during damage calculation
 *   "none"    – no ability
 *
 * Active abilities that need a field target return true from needsTarget().
 * The target type restriction is given by targetType() (null = no target / any).
 */
public class CardAbilityResolver {

    // ── Ability type lookup ───────────────────────────────────────────────────

    public static String abilityType(String cardId) {
        switch (cardId) {
            case "hd001":  // Honeydew
            case "sb001":  // Scrap Bot
            case "upb001": // Upgrade Bot
            case "wsp001": // Wisp
                return "active";
            case "gb001":  // Glow Bug
            case "rod001": // Rodent
                return "ondeath";
            case "smi001": // Shield Imp
                return "passive";
            default:
                return "none";
        }
    }

    /** Whether the active ability requires the player to click a target on the field. */
    public static boolean needsTarget(String cardId) {
        return "hd001".equals(cardId) || "upb001".equals(cardId);
    }

    /**
     * Card type restriction for targeted abilities.
     * Returns null for no restriction / not a targeted ability.
     */
    public static String targetType(String cardId) {
        if ("hd001".equals(cardId))  return "bug";
        if ("upb001".equals(cardId)) return "bot";
        return null;
    }

    // ── Active: no-target ─────────────────────────────────────────────────────

    /**
     * Execute an active ability that does not need a field target.
     * Returns a message to display, or null if the ability was blocked.
     */
    public static String executeActive(BattleState bs, String cardId, boolean isP1,
                                        Map<String, Card> cardMap) {
        switch (cardId) {
            case "sb001": return fabricate(bs, isP1);
            case "wsp001": return drift(bs, isP1);
            default: return null;
        }
    }

    // Scrap Bot: summon 1 Scrap to own frontline
    private static String fabricate(BattleState bs, boolean isP1) {
        String[] front = isP1 ? bs.p1Front : bs.p2Front;
        for (int i = 0; i < 5; i++) {
            if (front[i] == null || front[i].isEmpty()) {
                front[i] = BattleState.makeSlot(AbilityResolver.SCRAP_ID, 1);
                return "Fabricate: summoned 1 Scrap!";
            }
        }
        return "Fabricate: frontline is full!";
    }

    // Wisp: spend 1 soul to draw 1 card
    private static String drift(BattleState bs, boolean isP1) {
        int souls = isP1 ? bs.p1Souls : bs.p2Souls;
        if (souls < 1) return "Drift: need 1 soul (have 0).";
        List<String> deck = isP1 ? bs.p1Deck : bs.p2Deck;
        List<String> hand = isP1 ? bs.p1Hand : bs.p2Hand;
        if (deck.isEmpty()) return "Drift: deck is empty!";
        if (isP1) bs.p1Souls--; else bs.p2Souls--;
        hand.add(deck.remove(0));
        return "Drift: drew 1 card!";
    }

    // ── Active: targeted ─────────────────────────────────────────────────────

    /**
     * Execute a targeted active ability.
     * @param userIsP1    which player is using the ability
     * @param tgtIsP1     which player owns the target slot
     * @param tgtFront    whether target is in frontline
     * @param tgtIdx      slot index of target
     * @param atkChoice   Upgrade Bot: 0=ATK, 1=HP
     */
    public static String executeTargeted(BattleState bs, String sourceCardId, boolean userIsP1,
                                          boolean tgtIsP1, boolean tgtFront, int tgtIdx,
                                          int atkChoice, Map<String, Card> cardMap) {
        switch (sourceCardId) {
            case "hd001": return dewDrop(bs, userIsP1, tgtIsP1, tgtFront, tgtIdx, cardMap);
            case "upb001": return upgrade(bs, tgtIsP1, tgtFront, tgtIdx, atkChoice, cardMap);
            default: return "No targeted ability.";
        }
    }

    // Honeydew: spend 1 soul → target friendly bug +1 ATK & HP
    private static String dewDrop(BattleState bs, boolean userIsP1,
                                   boolean tgtIsP1, boolean tgtFront, int tgtIdx,
                                   Map<String, Card> cardMap) {
        int souls = userIsP1 ? bs.p1Souls : bs.p2Souls;
        if (souls < 1) return "Dew Drop: need 1 soul (have 0).";
        if (userIsP1) bs.p1Souls--; else bs.p2Souls--;

        String[] row = tgtFront ? (tgtIsP1 ? bs.p1Front : bs.p2Front)
                                : (tgtIsP1 ? bs.p1Back  : bs.p2Back);
        String sv = row[tgtIdx];
        if (sv == null || sv.isEmpty()) return "Dew Drop: no card at target.";
        String id = BattleState.slotId(sv);
        int    hp = BattleState.slotHp(sv);
        Card   c  = cardMap.get(id);
        if (c == null || !"bug".equals(c.getType())) return "Dew Drop: target must be a bug.";

        String posKey = BattleState.posKey(tgtIsP1, tgtFront, tgtIdx);
        bs.fieldAtkBonus.merge(posKey, 1, Integer::sum);
        row[tgtIdx] = BattleState.makeSlot(id, hp + 1);
        return "Dew Drop: " + c.getName() + " gained +1 ATK & HP!";
    }

    // Upgrade Bot: give a friendly bot +2 ATK or +2 HP (choice: 0=ATK, 1=HP)
    private static String upgrade(BattleState bs, boolean tgtIsP1, boolean tgtFront, int tgtIdx,
                                   int choice, Map<String, Card> cardMap) {
        String[] row = tgtFront ? (tgtIsP1 ? bs.p1Front : bs.p2Front)
                                : (tgtIsP1 ? bs.p1Back  : bs.p2Back);
        String sv = row[tgtIdx];
        if (sv == null || sv.isEmpty()) return "Upgrade: no card at target.";
        String id = BattleState.slotId(sv);
        int    hp = BattleState.slotHp(sv);
        Card   c  = cardMap.get(id);
        if (c == null || !"bot".equals(c.getType())) return "Upgrade: target must be a bot.";

        String posKey = BattleState.posKey(tgtIsP1, tgtFront, tgtIdx);
        if (choice == 0) {
            bs.fieldAtkBonus.merge(posKey, 2, Integer::sum);
            return "Upgrade: " + c.getName() + " gained +2 ATK!";
        } else {
            row[tgtIdx] = BattleState.makeSlot(id, hp + 2);
            return "Upgrade: " + c.getName() + " gained +2 HP!";
        }
    }

    // ── On-death triggers ─────────────────────────────────────────────────────

    /**
     * Called when a non-champion card is defeated.
     * @param wasP1 which player owned the card
     */
    public static String onDeath(BattleState bs, String cardId, boolean wasP1) {
        switch (cardId) {
            case "gb001": return glowBugDeath(bs, wasP1);
            case "rod001": return rodentDeath(bs, wasP1);
            default: return "";
        }
    }

    // Glow Bug: owner gains +1 soul cap
    private static String glowBugDeath(BattleState bs, boolean wasP1) {
        if (wasP1) bs.p1SoulCap++; else bs.p2SoulCap++;
        return "Glow: +" + (wasP1 ? "P1" : "P2") + " soul cap → now "
               + (wasP1 ? bs.p1SoulCap : bs.p2SoulCap) + "!";
    }

    // Rodent: draw 1 card for its owner
    private static String rodentDeath(BattleState bs, boolean wasP1) {
        List<String> deck = wasP1 ? bs.p1Deck : bs.p2Deck;
        List<String> hand = wasP1 ? bs.p1Hand : bs.p2Hand;
        if (deck.isEmpty()) return "Scurry: deck empty, no draw.";
        hand.add(deck.remove(0));
        return "Scurry: drew 1 card!";
    }

    // ── Passive: damage reduction ─────────────────────────────────────────────

    /**
     * Returns how much incoming damage is reduced for the target card (passive Shield Imp).
     */
    public static int passiveDamageReduction(String targetCardId) {
        if ("smi001".equals(targetCardId)) return 1;
        return 0;
    }
}
