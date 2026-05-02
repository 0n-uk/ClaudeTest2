import java.util.*;

/**
 * Unified resolver for all card and champion abilities.
 *
 * Add new abilities by inserting entries into the static registration maps
 * inside the static{} block — no other methods need to change.
 *
 * Trigger points called from BattleScreen:
 *   onDeath(bs, cardId, wasP1, cardMap)          – any card or champion stage death
 *   executeActive(bs, cardId, isP1, cardMap)     – no-target active ability
 *   executeTargeted(bs, cardId, isP1, ...)       – targeted active ability
 *   applyPassives / effectiveCost / etc.         – passive helpers (unchanged)
 *
 * Champion-specific helpers (onChampDeath, activeAbility, canBypass, …)
 * are kept for BattleScreen compatibility but now delegate to the maps.
 */
public class AbilityResolver {

    // ── Scrap token ───────────────────────────────────────────────────────────

    public static final String SCRAP_ID   = "__SCRAP__";
    public static final Card   SCRAP_CARD = new Card(SCRAP_ID, "Scrap", "bot", 0, 1, 0, "Token");

    // ── Functional interfaces ─────────────────────────────────────────────────

    @FunctionalInterface
    public interface OnDeathFn {
        String resolve(BattleState bs, boolean wasP1, Map<String, Card> cardMap);
    }

    @FunctionalInterface
    public interface ActiveFn {
        String resolve(BattleState bs, boolean isP1, Map<String, Card> cardMap);
    }

    @FunctionalInterface
    public interface TargetedFn {
        String resolve(BattleState bs, boolean isP1, boolean tgtIsP1,
                       boolean tgtFront, int tgtIdx, int choice, Map<String, Card> cardMap);
    }

    // ── Registration maps ─────────────────────────────────────────────────────

    public static final Map<String, OnDeathFn>  ON_DEATH        = new HashMap<>();
    public static final Map<String, ActiveFn>   ACTIVE          = new HashMap<>();
    public static final Map<String, TargetedFn> TARGETED_ACTIVE = new HashMap<>();
    /** posKey → damage reduction amount (passive) */
    public static final Map<String, Integer>    PASSIVE_REDUCTION = new HashMap<>();
    /** cardId → soul cost to activate ability */
    public static final Map<String, Integer>    SOUL_COST       = new HashMap<>();
    /** cardId → required target type for targeted abilities */
    public static final Map<String, String>     TARGET_TYPE     = new HashMap<>();

    static {
        // ── On-death: regular cards ───────────────────────────────────────
        ON_DEATH.put("gb001",  (bs, wasP1, cm) -> glowBugDeath(bs, wasP1));
        ON_DEATH.put("rod001", (bs, wasP1, cm) -> rodentDeath(bs, wasP1));

        // ── On-death: champion stages ─────────────────────────────────────
        ON_DEATH.put("B1", (bs, wasP1, cm) -> largeDewDrop(bs, wasP1, cm));
        ON_DEATH.put("B2", (bs, wasP1, cm) -> molt(bs, wasP1));
        ON_DEATH.put("T1", (bs, wasP1, cm) -> hatch(bs, wasP1));

        // ── Active (no-target): regular cards ─────────────────────────────
        ACTIVE.put("sb001",  (bs, isP1, cm) -> fabricate(bs, isP1));
        ACTIVE.put("wsp001", (bs, isP1, cm) -> drift(bs, isP1));

        // ── Active (no-target): champion stages ───────────────────────────
        ACTIVE.put("D1", (bs, isP1, cm) -> peek(bs, isP1, cm));
        ACTIVE.put("D2", (bs, isP1, cm) -> sacForSoulCap(bs, isP1));
        ACTIVE.put("B5", (bs, isP1, cm) -> breed(bs, isP1));
        ACTIVE.put("T4", (bs, isP1, cm) -> overclock(bs, isP1));

        // ── Active (targeted) ─────────────────────────────────────────────
        TARGETED_ACTIVE.put("hd001",  (bs, isP1, ti, tf, tidx, ch, cm) -> dewDrop(bs, isP1, ti, tf, tidx, cm));
        TARGETED_ACTIVE.put("upb001", (bs, isP1, ti, tf, tidx, ch, cm) -> upgrade(bs, ti, tf, tidx, ch, cm));

        // ── Passive: damage reduction ─────────────────────────────────────
        PASSIVE_REDUCTION.put("smi001", 1);

        // ── Soul costs ────────────────────────────────────────────────────
        SOUL_COST.put("hd001",  1);
        SOUL_COST.put("wsp001", 1);
        SOUL_COST.put("D2",     3);

        // ── Target type restrictions ──────────────────────────────────────
        TARGET_TYPE.put("hd001",  "bug");
        TARGET_TYPE.put("upb001", "bot");
    }

    // ── Public dispatch methods ───────────────────────────────────────────────

    /**
     * Called when any card (regular or champion stage) is defeated.
     * Returns a message to display, or "" if none.
     */
    public static String onDeath(BattleState bs, String cardId, boolean wasP1,
                                  Map<String, Card> cardMap) {
        OnDeathFn fn = ON_DEATH.get(cardId);
        String msg = fn != null ? fn.resolve(bs, wasP1, cardMap) : "";

        // Foolish Alter (D4) passive: soul cap on any card death
        String d4msg = onCardDeath(bs, wasP1);
        if (!d4msg.isEmpty()) msg = msg.isEmpty() ? d4msg : msg + " | " + d4msg;

        return msg;
    }

    /**
     * Execute a no-target active ability.
     * Returns a result message, or null if not registered here
     * (BattleScreen handles champion passives / high-complexity cases).
     */
    public static String executeActive(BattleState bs, String cardId, boolean isP1,
                                        Map<String, Card> cardMap) {
        ActiveFn fn = ACTIVE.get(cardId);
        return fn != null ? fn.resolve(bs, isP1, cardMap) : null;
    }

    /**
     * Execute a targeted active ability.
     */
    public static String executeTargeted(BattleState bs, String sourceCardId, boolean userIsP1,
                                          boolean tgtIsP1, boolean tgtFront, int tgtIdx,
                                          int atkChoice, Map<String, Card> cardMap) {
        TargetedFn fn = TARGETED_ACTIVE.get(sourceCardId);
        return fn != null ? fn.resolve(bs, userIsP1, tgtIsP1, tgtFront, tgtIdx, atkChoice, cardMap)
                          : "No targeted ability.";
    }

    /** Returns how much incoming damage is reduced for this card (passive). */
    public static int passiveDamageReduction(String cardId) {
        return PASSIVE_REDUCTION.getOrDefault(cardId, 0);
    }

    /** Returns whether the player can currently afford to use this card's ability. */
    public static boolean canUseAbility(String cardId, int currentSouls) {
        int cost = SOUL_COST.getOrDefault(cardId, 0);
        return currentSouls >= cost;
    }

    /** Returns the ability type string for a card: "active", "targeted", "ondeath", "passive", "none". */
    public static String abilityType(String cardId) {
        if (TARGETED_ACTIVE.containsKey(cardId)) return "targeted";
        if (ACTIVE.containsKey(cardId))          return "active";
        if (ON_DEATH.containsKey(cardId))        return "ondeath";
        if (PASSIVE_REDUCTION.containsKey(cardId)) return "passive";
        return "none";
    }

    /** Whether the active ability requires the player to click a target on the field. */
    public static boolean needsTarget(String cardId) {
        return TARGETED_ACTIVE.containsKey(cardId);
    }

    // ── Champion-specific active ability dispatcher (BattleScreen compat) ─────

    /**
     * Called when player activates a champion ability.
     * @return result message, or null if passive/BattleScreen-handled
     */
    public static String activeAbility(BattleState bs, String champId, boolean isP1,
                                        Map<String, Card> cardMap) {
        switch (champId) {
            // Passives – handled in applyPassives / BattleScreen hooks
            case "M1": case "M2": case "M4": case "M5":
            case "B4": case "D4":
                return null;
            // BattleScreen handles targeting for T3
            case "T3":
                return null;
            // High-complexity stubs
            case "D3": return "[Steal not yet implemented]";
            case "D5": return "[Martyr not yet implemented]";
            case "M3": return "[Mimic not yet implemented]";
            default:
                String msg = executeActive(bs, champId, isP1, cardMap);
                return msg != null ? msg : "No active ability.";
        }
    }

    // ── On-death (kept for BattleScreen champion-death path) ─────────────────

    public static String onChampDeath(BattleState bs, String champId, boolean isP1,
                                       Map<String, Card> cardMap) {
        return onDeath(bs, champId, isP1, cardMap);
    }

    // ── Passive helpers ───────────────────────────────────────────────────────

    /** Returns the effective soul cost for placing a card, considering passives. */
    public static int effectiveCost(Card card, BattleState bs, boolean isP1,
                                     Map<String, ChampionLine> lines) {
        int cost = card.getCost();
        // Elder Mothling (B4) – all your cards cost 1 less
        if ("B4".equals(currentChampId(bs, isP1))) cost = Math.max(0, cost - 1);
        // Queen (B5) – freeplay token check
        String freeKey = card.getId() + "_" + (isP1 ? "p1" : "p2");
        if (bs.freeplayCards.contains(freeKey)) cost = 0;
        return cost;
    }

    /** Called whenever any ability is used; handles Shade's Harvest passive. */
    public static String onAbilityUsed(BattleState bs, boolean isP1) {
        // Shade (M1) – gain 1 soul when any ability used
        if ("M1".equals(currentChampId(bs, isP1))) {
            if (isP1) bs.p1Souls = Math.min(bs.p1Souls + 1, bs.p1SoulCap);
            else      bs.p2Souls = Math.min(bs.p2Souls + 1, bs.p2SoulCap);
            return "Harvest: gained 1 soul!";
        }
        return "";
    }

    /** Returns effective attack for Evolved Shade (M2) – 2x vs ability-used enemies. */
    public static int effectiveAttack(Card attacker, String atkPosKey, String atkChampId,
                                       String tgtPosKey, BattleState bs) {
        int atk = attacker.getAttack() + bs.fieldAtkBonus.getOrDefault(atkPosKey, 0);
        if ("M2".equals(atkChampId) && bs.abilityUsedThisTurn.contains(tgtPosKey)) {
            atk *= 2;
        }
        return atk;
    }

    /** Handles Foolish Alter (D4) passive on any card death. */
    public static String onCardDeath(BattleState bs, boolean deadOwnerIsP1) {
        String p1Champ = currentChampId(bs, true);
        String p2Champ = currentChampId(bs, false);
        StringBuilder sb = new StringBuilder();
        if ("D4".equals(p1Champ)) {
            bs.p1SoulCap++;
            sb.append(!deadOwnerIsP1 ? "Curse: P1 soul cap +1! " : "Curse: P1 soul cap +1 (friendly death bonus)! ");
        }
        if ("D4".equals(p2Champ)) {
            bs.p2SoulCap++;
            sb.append(deadOwnerIsP1 ? "Curse: P2 soul cap +1! " : "Curse: P2 soul cap +1 (friendly death bonus)! ");
        }
        return sb.toString().trim();
    }

    /** Returns whether a card's ability is nullified by enemy Heavenly Shade (M4). */
    public static boolean isAbilityNullified(boolean cardOwnerIsP1, BattleState bs) {
        return "M4".equals(currentChampId(bs, !cardOwnerIsP1));
    }

    /** Returns whether attacker is immune to enemy abilities (King Shade M5). */
    public static boolean isImmuneToAbilities(boolean cardOwnerIsP1, BattleState bs) {
        return "M5".equals(currentChampId(bs, cardOwnerIsP1));
    }

    // ── T-Bot bypass ─────────────────────────────────────────────────────────

    /** Checks if T-Bot (T3) bypass is available. */
    public static boolean canBypass(BattleState bs, boolean isP1) {
        if (!"T3".equals(currentChampId(bs, isP1))) return false;
        return scrapCount(bs, isP1) >= 1;
    }

    /** Consumes 1 Scrap for T-Bot bypass and returns message. */
    public static String consumeBypassScrap(BattleState bs, boolean isP1) {
        String[] front = isP1 ? bs.p1Front : bs.p2Front;
        String[] back  = isP1 ? bs.p1Back  : bs.p2Back;
        for (int pass = 0; pass < 2; pass++) {
            String[] row = pass == 0 ? front : back;
            for (int i = 0; i < 5; i++) {
                if (SCRAP_ID.equals(BattleState.slotId(row[i]))) {
                    row[i] = "";
                    return "Bypass: 1 Scrap consumed.";
                }
            }
        }
        return "";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public static String currentChampId(BattleState bs, boolean isP1) {
        String[] back = isP1 ? bs.p1Back : bs.p2Back;
        String sv = back[BattleState.CHAMP_SLOT];
        return BattleState.slotId(sv);
    }

    private static int scrapCount(BattleState bs, boolean isP1) {
        String[] front = isP1 ? bs.p1Front : bs.p2Front;
        String[] back  = isP1 ? bs.p1Back  : bs.p2Back;
        int count = 0;
        for (String s : front) if (SCRAP_ID.equals(BattleState.slotId(s))) count++;
        for (String s : back)  if (SCRAP_ID.equals(BattleState.slotId(s))) count++;
        return count;
    }

    // ── Ability implementations ───────────────────────────────────────────────
    // Regular card abilities

    // Glow Bug (gb001): owner gains +1 soul cap
    private static String glowBugDeath(BattleState bs, boolean wasP1) {
        if (wasP1) bs.p1SoulCap++; else bs.p2SoulCap++;
        return "Glow: +" + (wasP1 ? "P1" : "P2") + " soul cap → now "
               + (wasP1 ? bs.p1SoulCap : bs.p2SoulCap) + "!";
    }

    // Rodent (rod001): draw 1 card for its owner
    private static String rodentDeath(BattleState bs, boolean wasP1) {
        List<String> deck = wasP1 ? bs.p1Deck : bs.p2Deck;
        List<String> hand = wasP1 ? bs.p1Hand : bs.p2Hand;
        if (deck.isEmpty()) return "Scurry: deck empty, no draw.";
        hand.add(deck.remove(0));
        return "Scurry: drew 1 card!";
    }

    // Scrap Bot (sb001): summon 1 Scrap to own frontline
    private static String fabricate(BattleState bs, boolean isP1) {
        String[] front = isP1 ? bs.p1Front : bs.p2Front;
        for (int i = 0; i < 5; i++) {
            if (front[i] == null || front[i].isEmpty()) {
                front[i] = BattleState.makeSlot(SCRAP_ID, 1);
                return "Fabricate: summoned 1 Scrap!";
            }
        }
        return "Fabricate: frontline is full!";
    }

    // Wisp (wsp001): spend 1 soul to draw 1 card
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

    // Honeydew (hd001): spend 1 soul → target friendly bug +1 ATK & HP
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

    // Upgrade Bot (upb001): give a friendly bot +2 ATK or +2 HP (choice: 0=ATK, 1=HP)
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

    // Champion ability implementations

    // Larva (B1) on death: give a random friendly card +5 HP
    private static String largeDewDrop(BattleState bs, boolean isP1, Map<String, Card> cardMap) {
        String[] front = isP1 ? bs.p1Front : bs.p2Front;
        String[] back  = isP1 ? bs.p1Back  : bs.p2Back;
        List<int[]> slots = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            if (front[i] != null && !front[i].isEmpty()) slots.add(new int[]{0, i});
            if (back[i]  != null && !back[i].isEmpty())  slots.add(new int[]{1, i});
        }
        if (slots.isEmpty()) return "Dew Drop: no friendly cards to heal.";
        int[] pick = slots.get(new Random().nextInt(slots.size()));
        String[] row = pick[0] == 0 ? front : back;
        String sv = row[pick[1]];
        String id = BattleState.slotId(sv);
        int    hp = BattleState.slotHp(sv);
        Card   c  = cardMap.get(id);
        int    maxHp = c != null ? c.getHp() : hp;
        row[pick[1]] = BattleState.makeSlot(id, hp + 5);
        String targetName = c != null ? c.getName() : id;
        return "Dew Drop: " + targetName + " gained +5 HP! (" + (hp+5) + "/" + maxHp + ")";
    }

    // Cacoon (B2) on death: draw 3 cards
    private static String molt(BattleState bs, boolean isP1) {
        List<String> deck = isP1 ? bs.p1Deck : bs.p2Deck;
        List<String> hand = isP1 ? bs.p1Hand : bs.p2Hand;
        int drawn = 0;
        for (int i = 0; i < 3 && !deck.isEmpty(); i++) {
            hand.add(deck.remove(0));
            drawn++;
        }
        return "Molt: drew " + drawn + " card" + (drawn != 1 ? "s" : "") + "!";
    }

    // Egg Bot (T1) on death: summon 2 Scrap tokens
    private static String hatch(BattleState bs, boolean isP1) {
        String[] front = isP1 ? bs.p1Front : bs.p2Front;
        int placed = 0;
        for (int i = 0; i < 5 && placed < 2; i++) {
            if (front[i] == null || front[i].isEmpty()) {
                front[i] = BattleState.makeSlot(SCRAP_ID, 1);
                placed++;
            }
        }
        return "Hatch: summoned " + placed + " Scrap token" + (placed != 1 ? "s" : "") + "!";
    }

    // Seer (D1): reveal top card of enemy deck
    private static String peek(BattleState bs, boolean isP1, Map<String, Card> cardMap) {
        List<String> enemyDeck = isP1 ? bs.p2Deck : bs.p1Deck;
        if (enemyDeck.isEmpty()) return "Peek: enemy deck is empty.";
        String id = enemyDeck.get(0);
        Card c = cardMap.get(id);
        String name = c != null ? c.getName() + " (" + c.getType() + ", ATK " + c.getAttack() + ", HP " + c.getHp() + ")" : id;
        return "Peek: top of enemy deck is " + name + ".";
    }

    // Ritual Magician (D2): spend 3 souls for +1 soul cap
    private static String sacForSoulCap(BattleState bs, boolean isP1) {
        int souls = isP1 ? bs.p1Souls : bs.p2Souls;
        if (souls < 3) return "Sac: need 3 souls (have " + souls + ").";
        if (isP1) { bs.p1Souls -= 3; bs.p1SoulCap++; }
        else      { bs.p2Souls -= 3; bs.p2SoulCap++; }
        return "Sac: spent 3 souls, soul cap is now " + (isP1 ? bs.p1SoulCap : bs.p2SoulCap) + "!";
    }

    // Queen (B5): freeplay top deck card
    private static String breed(BattleState bs, boolean isP1) {
        List<String> deck = isP1 ? bs.p1Deck  : bs.p2Deck;
        List<String> hand = isP1 ? bs.p1Hand  : bs.p2Hand;
        if (deck.isEmpty()) return "Breed: deck is empty.";
        String id = deck.remove(0);
        hand.add(id);
        bs.freeplayCards.add(id + "_" + (isP1 ? "p1" : "p2"));
        return "Breed: top card added to hand and costs 0 souls this turn!";
    }

    // T-Bot Mega (T4): spend 2 Scrap for 2 extra actions
    private static String overclock(BattleState bs, boolean isP1) {
        String[] front = isP1 ? bs.p1Front : bs.p2Front;
        String[] back  = isP1 ? bs.p1Back  : bs.p2Back;
        int scrapCount = 0;
        int[] scrapSlot = null;
        boolean scrapFront = false;
        outer:
        for (int pass = 0; pass < 2; pass++) {
            String[] row = pass == 0 ? front : back;
            for (int i = 0; i < 5; i++) {
                if (SCRAP_ID.equals(BattleState.slotId(row[i]))) {
                    scrapCount++;
                    if (scrapCount == 1) { scrapSlot = new int[]{pass, i}; scrapFront = (pass == 0); }
                    if (scrapCount >= 2) break outer;
                }
            }
        }
        if (scrapCount < 2) return "Overclock: need 2 Scrap tokens (have " + scrapCount + ").";
        // Remove first scrap
        String[] row1 = scrapFront ? front : back;
        row1[scrapSlot[1]] = "";
        // Remove second scrap
        int removed = 0;
        for (int pass = 0; pass < 2 && removed < 2; pass++) {
            String[] row = pass == 0 ? front : back;
            for (int i = 0; i < 5 && removed < 2; i++) {
                if (SCRAP_ID.equals(BattleState.slotId(row[i]))) {
                    row[i] = ""; removed++;
                }
            }
        }
        if (isP1) bs.p1ExtraActions += 2; else bs.p2ExtraActions += 2;
        return "Overclock: 2 Scrap consumed, gained 2 extra actions!";
    }
}
