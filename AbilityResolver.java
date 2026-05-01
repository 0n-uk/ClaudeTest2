import java.util.*;

/**
 * Resolves champion abilities.
 *
 * Trigger points called from BattleScreen:
 *   onChampDeath(bs, champId, isP1, cardMap)  – before stage advances
 *   applyPassives(bs, amP1, cardMap)           – called after each action to apply passive effects
 *   activeAbility(bs, champId, isP1, cardMap, ui) – called when player activates champion ability
 *
 * High-complexity abilities (Puppet Master, Fool martyr, Demonic Shade mimic,
 * Heavenly Shade nullify) are stubbed with a visible notice; all others are
 * fully implemented.
 */
public class AbilityResolver {

    // ── Scrap token ───────────────────────────────────────────────────────────

    public static final String SCRAP_ID   = "__SCRAP__";
    public static final Card   SCRAP_CARD = new Card(SCRAP_ID, "Scrap", "bot", 0, 1, 0, "Token");

    // ── On-death triggers ─────────────────────────────────────────────────────

    /**
     * Called when a champion stage is defeated (hp drops to 0).
     * @param bs      current battle state (will be mutated)
     * @param champId champion id of the defeated stage (e.g. "B1")
     * @param isP1    whether the champion belongs to player 1
     * @param cardMap full card lookup map
     * @return message to display, or "" if none
     */
    public static String onChampDeath(BattleState bs, String champId, boolean isP1,
                                       Map<String, Card> cardMap) {
        switch (champId) {
            case "B1": return dewDrop(bs, isP1, cardMap);
            case "B2": return molt(bs, isP1);
            case "T1": return hatch(bs, isP1);
            case "D4": // Foolish Alter death – no on-death effect (passive only)
            default:   return "";
        }
    }

    // Larva – give a random friendly card +5 HP
    private static String dewDrop(BattleState bs, boolean isP1, Map<String, Card> cardMap) {
        String[] front = isP1 ? bs.p1Front : bs.p2Front;
        String[] back  = isP1 ? bs.p1Back  : bs.p2Back;
        List<int[]> slots = new ArrayList<>(); // [0]=rowFlag(0=front,1=back), [1]=idx
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

    // Cacoon – draw 3 cards
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

    // Egg Bot – summon 2 Scrap tokens
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

    // ── Active ability: called when player clicks "Use Ability" on champion ───

    /**
     * @return result message, or null if the ability requires a UI interaction
     *         that BattleScreen must handle separately (see BattleScreen for those cases)
     */
    public static String activeAbility(BattleState bs, String champId, boolean isP1,
                                        Map<String, Card> cardMap) {
        switch (champId) {
            case "D1": return peek(bs, isP1, cardMap);
            case "D2": return sacForSoulCap(bs, isP1);
            case "M1": return null; // passive, handled in applyPassives
            case "M2": return null; // passive
            case "M4": return null; // passive
            case "M5": return null; // passive
            case "B4": return null; // passive
            case "D4": return null; // passive
            case "B5": return breed(bs, isP1);
            // High-complexity stubs
            case "D3": return "[Steal not yet implemented]";
            case "D5": return "[Martyr not yet implemented]";
            case "M3": return "[Mimic not yet implemented]";
            case "T3": return null; // handled in BattleScreen (needs target selection)
            case "T4": return overclock(bs, isP1);
            default:   return "No active ability.";
        }
    }

    // Seer – reveal top card of enemy deck
    private static String peek(BattleState bs, boolean isP1, Map<String, Card> cardMap) {
        List<String> enemyDeck = isP1 ? bs.p2Deck : bs.p1Deck;
        if (enemyDeck.isEmpty()) return "Peek: enemy deck is empty.";
        String id = enemyDeck.get(0);
        Card c = cardMap.get(id);
        String name = c != null ? c.getName() + " (" + c.getType() + ", ATK " + c.getAttack() + ", HP " + c.getHp() + ")" : id;
        return "Peek: top of enemy deck is " + name + ".";
    }

    // Ritual Magician – spend 3 souls for +1 soul cap
    private static String sacForSoulCap(BattleState bs, boolean isP1) {
        int souls = isP1 ? bs.p1Souls : bs.p2Souls;
        if (souls < 3) return "Sac: need 3 souls (have " + souls + ").";
        if (isP1) { bs.p1Souls -= 3; bs.p1SoulCap++; }
        else      { bs.p2Souls -= 3; bs.p2SoulCap++; }
        return "Sac: spent 3 souls, soul cap is now " + (isP1 ? bs.p1SoulCap : bs.p2SoulCap) + "!";
    }

    // Queen – freeplay top deck card
    private static String breed(BattleState bs, boolean isP1) {
        List<String> deck  = isP1 ? bs.p1Deck  : bs.p2Deck;
        List<String> hand  = isP1 ? bs.p1Hand  : bs.p2Hand;
        if (deck.isEmpty()) return "Breed: deck is empty.";
        String id = deck.remove(0);
        hand.add(id);
        // Mark the card as free-to-play this turn by adding a special token
        bs.freeplayCards.add(id + "_" + (isP1 ? "p1" : "p2"));
        return "Breed: top card added to hand and costs 0 souls this turn!";
    }

    // T-Bot Mega – spend 2 Scrap for 2 extra actions
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
        // Remove one scrap token
        String[] row1 = scrapFront ? front : back;
        row1[scrapSlot[1]] = "";
        // Find and remove second scrap
        int removed = 0;
        for (int pass = 0; pass < 2 && removed < 2; pass++) {
            String[] row = pass == 0 ? front : back;
            for (int i = 0; i < 5 && removed < 2; i++) {
                if (SCRAP_ID.equals(BattleState.slotId(row[i]))) {
                    row[i] = ""; removed++;
                }
            }
        }
        // Grant 2 extra actions by removing 2 used-action marks from non-champion field cards
        // We add them to the extraActions set instead
        if (isP1) bs.p1ExtraActions += 2; else bs.p2ExtraActions += 2;
        return "Overclock: 2 Scrap consumed, gained 2 extra actions!";
    }

    // ── Passive effects ───────────────────────────────────────────────────────

    /**
     * Returns the effective soul cost for placing a card, considering passives.
     */
    public static int effectiveCost(Card card, BattleState bs, boolean isP1,
                                     Map<String, ChampionLine> lines) {
        int cost = card.getCost();
        // Elder Mothling (B4) – all your cards cost 1 less
        String champId = currentChampId(bs, isP1);
        if ("B4".equals(champId)) cost = Math.max(0, cost - 1);
        // Queen (B5) – freeplay token check
        String freeKey = card.getId() + "_" + (isP1 ? "p1" : "p2");
        if (bs.freeplayCards.contains(freeKey)) { cost = 0; }
        return cost;
    }

    /**
     * Called whenever any ability is used; handles Shade's Harvest passive.
     */
    public static String onAbilityUsed(BattleState bs, boolean isP1) {
        // Shade (M1) – gain 1 soul when any ability used
        String myChamp = currentChampId(bs, isP1);
        if ("M1".equals(myChamp)) {
            if (isP1) bs.p1Souls = Math.min(bs.p1Souls + 1, bs.p1SoulCap);
            else      bs.p2Souls = Math.min(bs.p2Souls + 1, bs.p2SoulCap);
            return "Harvest: gained 1 soul!";
        }
        return "";
    }

    /**
     * Returns effective attack for Evolved Shade (M2) – 2x vs ability-used enemies.
     */
    public static int effectiveAttack(Card attacker, String atkPosKey, String atkChampId,
                                       String tgtPosKey, BattleState bs) {
        int atk = attacker.getAttack() + bs.fieldAtkBonus.getOrDefault(atkPosKey, 0);
        if ("M2".equals(atkChampId) && bs.abilityUsedThisTurn.contains(tgtPosKey)) {
            atk *= 2;
        }
        return atk;
    }

    /**
     * Handles Foolish Alter (D4) passive on any card death.
     * @param deadOwnerIsP1 which player owned the card that died
     */
    public static String onCardDeath(BattleState bs, boolean deadOwnerIsP1) {
        // D4: enemy deaths → +1 soul cap; friendly deaths → +1 additional soul cap
        String p1Champ = currentChampId(bs, true);
        String p2Champ = currentChampId(bs, false);
        StringBuilder sb = new StringBuilder();
        if ("D4".equals(p1Champ)) {
            if (!deadOwnerIsP1) { bs.p1SoulCap++; sb.append("Curse: P1 soul cap +1! "); }
            else                { bs.p1SoulCap++; sb.append("Curse: P1 soul cap +1 (friendly death bonus)! "); }
        }
        if ("D4".equals(p2Champ)) {
            if (deadOwnerIsP1)  { bs.p2SoulCap++; sb.append("Curse: P2 soul cap +1! "); }
            else                { bs.p2SoulCap++; sb.append("Curse: P2 soul cap +1 (friendly death bonus)! "); }
        }
        return sb.toString().trim();
    }

    /**
     * Returns whether a given card's ability is nullified by enemy Heavenly Shade (M4).
     */
    public static boolean isAbilityNullified(boolean cardOwnerIsP1, BattleState bs) {
        // Heavenly Shade – if enemy has M4 on field, abilities are nullified
        String enemyChamp = currentChampId(bs, !cardOwnerIsP1);
        return "M4".equals(enemyChamp);
    }

    /**
     * Returns whether attacker is immune to enemy abilities (King Shade M5).
     */
    public static boolean isImmuneToAbilities(boolean cardOwnerIsP1, BattleState bs) {
        String champId = currentChampId(bs, cardOwnerIsP1);
        return "M5".equals(champId);
    }

    // ── T-Bot bypass (handled specially in BattleScreen) ─────────────────────

    /**
     * Checks if T-Bot (T3) bypass is available: current champ is T3 and at least 1 Scrap exists.
     */
    public static boolean canBypass(BattleState bs, boolean isP1) {
        if (!"T3".equals(currentChampId(bs, isP1))) return false;
        return scrapCount(bs, isP1) >= 1;
    }

    /**
     * Consumes 1 Scrap for T-Bot bypass and returns message.
     */
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
}
