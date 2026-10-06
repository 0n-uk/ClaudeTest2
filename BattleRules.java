import java.util.*;
import java.util.function.Consumer;

/**
 * The battle rules that aren't in AbilityResolver: placing, attacking, cards dying,
 * the abilities that need more than one step, and end of turn. There is no Swing here.
 * Each action changes the BattleState and returns an {@link Outcome} with the text for the
 * battle log; BattleScreen decides when to save and how to show it.
 */
class BattleRules {

    /** What an action did: the log text, whether the battle state changed, and whether the turn ends. */
    static final class Outcome {
        final String  log;       // null leaves the current message as it is
        final boolean changed;
        final boolean endsTurn;

        private Outcome(String log, boolean changed, boolean endsTurn) {
            this.log = log;
            this.changed = changed;
            this.endsTurn = endsTurn;
        }

        static Outcome refused(String log)     { return new Outcome(log, false, false); }
        static Outcome done(String log)        { return new Outcome(log, true,  false); }
        static Outcome doneEndTurn(String log) { return new Outcome(log, true,  true);  }
    }

    private final BattleState st;
    private final Map<String, Card> cardMap;
    private final Map<String, ChampionLine> champLines;

    BattleRules(BattleState st, Map<String, Card> cardMap, Map<String, ChampionLine> champLines) {
        this.st = st;
        this.cardMap = cardMap;
        this.champLines = champLines;
    }

    // ── Which moves are allowed ──────────────────────────────────────────────
    // The battle screen and the bot both ask these, so they follow the same rules.

    /** Whether the card at key can attack or use its ability now: it is there, has an action left and isn't frozen. */
    boolean canAct(String key) {
        return st.cardIdAt(key) != null
            && st.hasAction(BattleState.posKeyIsP1(key), BattleState.posKeyIsFront(key), BattleState.posKeyIdx(key))
            && !st.isFrozen(key);
    }

    /**
     * Whether the card at atkKey may attack the enemy card in the target slot. Bat Eye can't be hit
     * from the frontline, Catapult only hits the backline, and Bypass or Dream Wanderer reach past
     * the frontline.
     */
    boolean canAttack(String atkKey, boolean tgtIsP1, boolean tgtFront, int tgtIdx, boolean bypass) {
        String atkId = st.cardIdAt(atkKey);
        String tgtId = st.cardIdAt(BattleState.posKey(tgtIsP1, tgtFront, tgtIdx));
        if (atkId == null || tgtId == null || tgtIsP1 == BattleState.posKeyIsP1(atkKey)) return false;
        if (CardIds.BAT_EYE.equals(tgtId) && BattleState.posKeyIsFront(atkKey)) return false;
        if (CardIds.CATAPULT.equals(atkId))
            return !tgtFront && st.isTargetableBypass(tgtIsP1, tgtFront, tgtIdx);
        if (bypass || CardIds.DREAM_WANDERER.equals(atkId))
            return st.isTargetableBypass(tgtIsP1, tgtFront, tgtIdx);
        return st.isTargetable(tgtIsP1, tgtFront, tgtIdx);
    }

    /**
     * Whether the ability of cardId, used by the amP1 side (directly, or copied by Echo Spirit or
     * Mimic), may aim at this slot. Cursed Daruma aims at an empty enemy slot; every other ability
     * aims at a card on the side and of the type it names.
     */
    boolean canAbilityTarget(String cardId, boolean amP1, boolean tgtIsP1, boolean tgtFront, int tgtIdx) {
        boolean mine = tgtIsP1 == amP1;
        String  tgtId = st.cardIdAt(BattleState.posKey(tgtIsP1, tgtFront, tgtIdx));
        if (AbilityResolver.TARGETS_EMPTY_SLOT.contains(cardId)) return tgtId == null && !mine;

        Card card = tgtId != null ? cardMap.get(tgtId) : null;
        boolean targetsEnemy = "enemy".equals(AbilityResolver.TARGET_SIDE.get(cardId));
        if (card == null || mine == targetsEnemy) return false;
        if (!mine && AbilityResolver.isImmuneToAbilities(tgtIsP1, st)) return false;   // Sovereign
        if (!mine && !tgtFront && st.isShieldedByGreatEnt(tgtIsP1, tgtIdx)) return false;
        String requiredType = AbilityResolver.TARGET_TYPE.get(cardId);
        return requiredType == null || requiredType.equals(card.getType().toLowerCase());
    }

    // ── Placing a card ───────────────────────────────────────────────────────

    Outcome place(boolean amP1, int handIdx, boolean isFront, int idx) {
        List<String> hand = st.hand(amP1);
        if (handIdx < 0 || handIdx >= hand.size()) return Outcome.refused(null);
        String id   = hand.get(handIdx);
        Card   c    = cardMap.get(id);
        int    cost = AbilityResolver.effectiveCost(c != null ? c : new Card(id, "?", "?", 0, 1, 0, ""),
                                                    st, amP1, champLines);
        int    souls = st.souls(amP1);
        if (cost > souls) return Outcome.refused("Not enough Souls (need " + cost + ", have " + souls + ")");

        String key = BattleState.posKey(amP1, isFront, idx);
        if (st.sealedSlots.containsKey(key)) return Outcome.refused("That slot is sealed and cannot be used!");
        // Clear any lingering effects from a previous occupant of this slot
        st.clearCardState(key);

        // Conglamorat: HP and ATK equal its cost (= current souls, min 1)
        int hp = c != null ? c.getHp() : 1;
        if (CardIds.CONGLAMORAT.equals(id)) {
            hp = cost;
            st.fieldAtkBonus.put(key, cost);
        }
        st.getRow(amP1, isFront)[idx] = BattleState.makeSlot(id, hp);
        hand.remove(handIdx);
        st.freeplayCards.remove(id + "_" + (amP1 ? "p1" : "p2"));
        st.setSouls(amP1, souls - cost);

        String log = "";
        switch (id) {
            case CardIds.WORKER_ANT:
                hand.add(CardIds.WORKER_ANT_2);
                log = "Colony: Worker Ant2 added to your hand!";
                break;
            case CardIds.WATER_SPIRIT:
                buffAll(amP1, 0, 2);
                log = "Water Spirit: all friendly cards gained +2 HP!";
                break;
            case CardIds.TORCH:
                buffAll(amP1, 2, 2);
                log = "Torch: all friendly cards gained +2 ATK and +2 HP!";
                break;
        }
        // Pod cards: start transform countdown on placement
        int txDelay = AbilityResolver.transformDelay(id);
        if (txDelay > 0) st.transformCounters.put(key, txDelay);
        // Great Ent: cannot be moved once placed
        if (CardIds.GREAT_ENT.equals(id)) st.fieldLockedCards.add(key);

        // Turret Bot: when the opponent places on their frontline, it attacks the placed card
        if (isFront) {
            boolean oppIsP1 = !amP1;
            String[] oppFront = st.getRow(oppIsP1, true);
            for (int ti = 0; ti < 5; ti++) {
                if (CardIds.TURRET_BOT.equals(BattleState.slotId(oppFront[ti]))
                        && st.hasAction(oppIsP1, true, ti)) {
                    Outcome shot = attack(oppIsP1, BattleState.posKey(oppIsP1, true, ti), amP1, true, idx, false);
                    if (shot != null) log = join(log, shot.log);
                    break;
                }
            }
        }
        return Outcome.done(log);
    }

    // ── Attacking ────────────────────────────────────────────────────────────

    /** The card at atkKey attacks the target slot. Returns null if either slot is empty. */
    Outcome attack(boolean amP1, String atkKey, boolean tgtIsP1, boolean tgtFront, int tgtIdx, boolean bypass) {
        boolean atkIsP1  = BattleState.posKeyIsP1(atkKey);
        boolean atkFront = BattleState.posKeyIsFront(atkKey);
        int     atkIdx   = BattleState.posKeyIdx(atkKey);
        String[] atkRow  = st.getRow(atkIsP1, atkFront);
        String[] tgtRow  = st.getRow(tgtIsP1, tgtFront);

        String atkSv = atkRow[atkIdx], tgtSv = tgtRow[tgtIdx];
        if (isEmpty(atkSv) || isEmpty(tgtSv)) return null;
        String atkId = BattleState.slotId(atkSv), tgtId = BattleState.slotId(tgtSv);
        Card   atkC  = cardMap.get(atkId),         tgtC  = cardMap.get(tgtId);
        if (atkC == null || tgtC == null) return null;

        if (CardIds.CATAPULT.equals(atkId) && tgtFront)
            return Outcome.refused("Catapult can only target the backline!");

        String log    = bypass ? AbilityResolver.consumeBypassScrap(st, amP1) : "";
        String tgtKey = BattleState.posKey(tgtIsP1, tgtFront, tgtIdx);
        boolean dualStrike = CardIds.MANTIS_BOT.equals(atkId) || CardIds.LARGE_MANTIS.equals(atkId)
                          || CardIds.EYE_SHREW.equals(atkId);

        // Metal Wings Bot: coin-flip dodge, returns to its owner's hand
        if (CardIds.METAL_WINGS_BOT.equals(tgtId) && AbilityResolver.coinFlip()) {
            tgtRow[tgtIdx] = "";
            st.clearCardState(tgtKey);
            st.hand(tgtIsP1).add(tgtId);
            finishAttacks(atkKey);
            return Outcome.done(join(log, "Metal Wings Bot dodged and returned to hand!"));
        }
        // Wind Spirit: coin-flip dodge, stays on the field
        if (CardIds.WIND_SPIRIT.equals(tgtId) && AbilityResolver.coinFlip()) {
            finishAttacks(atkKey);
            return Outcome.done(join(log, "Wind Spirit dodged the attack!"));
        }

        boolean wasFocused = st.focusedCards.contains(atkKey);
        int dmg = AbilityResolver.effectiveAttack(atkC, atkKey, AbilityResolver.currentChampId(st, amP1), tgtKey, st);
        // Turtle Bot charge and Focus are used up by the attack
        st.turtleBotCharged.remove(atkKey);
        st.focusedCards.remove(atkKey);

        // Shield Imp passive: less incoming damage
        int reduction = AbilityResolver.passiveDamageReduction(tgtId);
        dmg = Math.max(0, dmg - reduction);
        int newTgtHp = BattleState.slotHp(tgtSv) - dmg;

        // Spike Dragon / Thorny Bushy: hurt the attacker before the hit lands
        int retaliation = CardIds.SPIKE_DRAGON.equals(tgtId) ? 5 : CardIds.THORNY_BUSHY.equals(tgtId) ? 6 : 0;
        if (retaliation > 0) {
            int atkNewHp = BattleState.slotHp(atkSv) - retaliation;
            String text  = tgtC.getName() + " retaliates for " + retaliation + "!";
            if (atkNewHp > 0 || atkC instanceof Champion) {
                atkRow[atkIdx] = BattleState.makeSlot(atkId, Math.max(1, atkNewHp));
                log = join(log, text);
            } else {
                // The attacker dies, but its attack still lands
                finishAttacks(atkKey);
                log = join(log, text, atkC.getName() + " destroyed!", defeat(atkKey, atkC, true));
                if (newTgtHp <= 0) log = join(log, tgtC.getName() + " defeated!", defeat(tgtKey, tgtC, true));
                else               tgtRow[tgtIdx] = BattleState.makeSlot(tgtId, newTgtHp);
                return Outcome.done(log);
            }
        }

        // Damage threshold: Roly Poly blocks <1 dmg; Elder Beetle Warrior blocks <8 dmg
        int threshold = AbilityResolver.damageThreshold(tgtId);
        if (threshold > 0 && dmg < threshold) {
            spendAttack(atkKey, dualStrike);
            log = join(log, atkC.getName() + " attacks " + tgtC.getName() + " but the attack is too weak — blocked!");
            return endTurnIfChampion(atkC, log);
        }

        // Sloth Bear: freezes itself for 1 round once it has attacked
        if (spendAttack(atkKey, dualStrike) && CardIds.SLOTH_BEAR.equals(atkId))
            st.frozenCards.put(atkKey, 2);

        // Grand Wizard of Omerlia: attacks every card in a row instead
        if (CardIds.GRAND_WIZARD.equals(atkId))
            return endTurnIfChampion(atkC, join(log, grandWizardSweep(amP1, atkKey, atkC, atkFront, tgtIsP1, wasFocused)));

        if (newTgtHp <= 0) {
            log = join(log, tgtC.getName() + " defeated!", defeat(tgtKey, tgtC, true));
            if (isFinished()) return Outcome.done(log);
            // Bollywurg: Tongue Grapple splashes the dead card's max HP onto another enemy
            if (CardIds.BOLLYWURG.equals(atkId) && !(tgtC instanceof Champion))
                log = join(log, tongueGrapple(tgtIsP1, tgtC.getHp()));
        } else {
            tgtRow[tgtIdx] = BattleState.makeSlot(tgtId, newTgtHp);
            log = join(log, "Hit " + tgtC.getName() + " for " + dmg
                            + (reduction > 0 ? " (blocked " + reduction + ")" : "") + "!",
                       onHitEffects(atkId, tgtId, tgtC, tgtRow, tgtIdx, tgtKey));
        }

        // Fungal Beast: becomes Fungal Colossal on its 2nd kill
        if (CardIds.FUNGAL_BEAST.equals(atkId) && newTgtHp <= 0 && !(tgtC instanceof Champion)) {
            int kills = st.fungalBeastKills.getOrDefault(atkKey, 0) + 1;
            if (kills >= 2) {
                st.fungalBeastKills.remove(atkKey);
                atkRow[atkIdx] = BattleState.makeSlot(CardIds.FUNGAL_COLOSSAL, 10);
                log = join(log, "Fungal Beast evolved into Fungal Colossal!");
            } else {
                st.fungalBeastKills.put(atkKey, kills);
            }
        }

        // Lancer: also hits the backline card behind a frontline target (not champions)
        if (CardIds.LANCER.equals(atkId) && tgtFront) {
            String backKey = BattleState.posKey(tgtIsP1, false, tgtIdx);
            Card back = cardAt(backKey);
            if (back != null && !(back instanceof Champion)) {
                int raw = atkC.getAttack() + st.fieldAtkBonus.getOrDefault(atkKey, 0);
                log = join(log, "Pierce: " + hit(backKey, raw, true));
            }
        }

        // Pawn: after attacking, moves to the other row of its column if that slot is empty
        if (CardIds.PAWN.equals(atkId) && !st.fieldLockedCards.contains(atkKey)) {
            boolean toFront  = !atkFront;
            String[] otherRow = st.getRow(atkIsP1, toFront);
            if (isEmpty(otherRow[atkIdx])) {
                otherRow[atkIdx] = atkRow[atkIdx];
                atkRow[atkIdx]   = "";
                st.migrateCardState(atkKey, BattleState.posKey(atkIsP1, toFront, atkIdx));
                log = join(log, "Pawn dashes to the " + (toFront ? "frontline" : "backline") + "!");
            }
        }

        return endTurnIfChampion(atkC, log);
    }

    /** A champion attacking ends the turn (unless the game just ended). */
    private Outcome endTurnIfChampion(Card attacker, String log) {
        return attacker instanceof Champion && !isFinished() ? Outcome.doneEndTurn(log) : Outcome.done(log);
    }

    /** Uses up one attack. Dual Strike cards get a second one first. Returns true when the card is done. */
    private boolean spendAttack(String atkKey, boolean dualStrike) {
        if (dualStrike && !st.mantisSecondAttack.contains(atkKey)) {
            st.mantisSecondAttack.add(atkKey);
            return false;
        }
        finishAttacks(atkKey);
        return true;
    }

    private void finishAttacks(String atkKey) {
        st.mantisSecondAttack.remove(atkKey);
        st.useAction(BattleState.posKeyIsP1(atkKey), BattleState.posKeyIsFront(atkKey), BattleState.posKeyIdx(atkKey));
    }

    private String onHitEffects(String atkId, String tgtId, Card tgtC, String[] tgtRow, int tgtIdx, String tgtKey) {
        String name = tgtC.getName();
        List<String> parts = new ArrayList<>();
        // Small Bushy / Great Bushy: gain HP when attacked
        if (CardIds.SMALL_BUSHY.equals(tgtId) || CardIds.GREAT_BUSHY.equals(tgtId)) {
            int bonus = CardIds.SMALL_BUSHY.equals(tgtId) ? 3 : 6;
            tgtRow[tgtIdx] = BattleState.makeSlot(tgtId, BattleState.slotHp(tgtRow[tgtIdx]) + bonus);
            parts.add(name + " absorbed the hit, gaining +" + bonus + " HP!");
        }
        switch (atkId) {
            case CardIds.FIRE_SPIRIT:
            case CardIds.FLAME_FLOWER_POD:
            case CardIds.FLAME_BLOOMLING:
                st.burnedCards.put(tgtKey, 1);
                parts.add(name + " is Burned!");
                break;
            case CardIds.SPIDERLING:
                st.poisonedCards.add(tgtKey);
                parts.add(name + " is Poisoned!");
                break;
            case CardIds.FUNGAL_POD:
            case CardIds.FUNGAL_SPIRE:
            case CardIds.FUNGAL_BEAST:
            case CardIds.FUNGAL_COLOSSAL:
                st.sporedCards.put(tgtKey, 4); // 2 rounds = 4 turns
                parts.add(name + " is Spored!");
                break;
            case CardIds.ICE_DRAGON:
                st.frozenCards.put(tgtKey, Math.max(st.frozenCards.getOrDefault(tgtKey, 0), 2));
                parts.add(name + " is Frozen for 1 round!");
                break;
        }
        return join(parts.toArray(new String[0]));
    }

    private String grandWizardSweep(boolean amP1, String atkKey, Card atkC, boolean atkFront,
                                    boolean tgtIsP1, boolean wasFocused) {
        // A backline Grand Wizard hits the enemy frontline, a frontline one hits the backline
        boolean hitFront = !atkFront;
        int damage = atkC.getAttack() + st.fieldAtkBonus.getOrDefault(atkKey, 0);
        if (wasFocused) damage *= 2;
        boolean rewards = !mageDecayActive(amP1);
        StringBuilder sb = new StringBuilder("Grand Wizard attacks " + (hitFront ? "frontline" : "backline") + ":");
        for (int i = 0; i < 5; i++) {
            String h = hit(BattleState.posKey(tgtIsP1, hitFront, i), damage, rewards);
            if (!h.isEmpty()) sb.append(' ').append(h);
        }
        return sb.toString();
    }

    private String tongueGrapple(boolean enemyIsP1, int splashDmg) {
        List<String> pool = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            for (boolean front : new boolean[]{ true, false }) {
                if (!isEmpty(st.getRow(enemyIsP1, front)[i])) pool.add(BattleState.posKey(enemyIsP1, front, i));
            }
        }
        if (pool.isEmpty()) return "";
        String key = pool.get((int) (Math.random() * pool.size()));
        Card card = cardAt(key);
        if (card == null) return "";
        int actual = Math.max(0, splashDmg - AbilityResolver.passiveDamageReduction(card.getId()));
        String[] row = st.rowOf(key);
        int idx = BattleState.posKeyIdx(key);
        int newHp = BattleState.slotHp(row[idx]) - actual;
        String text = "Tongue Grapple: " + actual + " splash → " + card.getName();
        if (newHp <= 0 && !(card instanceof Champion))
            return join(text + " defeated!", defeat(key, card, true));
        // A champion survives the splash on 1 HP
        row[idx] = BattleState.makeSlot(card.getId(), Math.max(1, newHp));
        return text + "!";
    }

    // ── Damage and death ─────────────────────────────────────────────────────

    /**
     * Deals raw damage (less the target's passive reduction) to the card at key.
     * Returns "Name(-3)", "Name ✕ ..." if it died, or "" for an empty slot.
     */
    private String hit(String key, int raw, boolean rewards) {
        Card card = cardAt(key);
        if (card == null) return "";
        String[] row = st.rowOf(key);
        int idx = BattleState.posKeyIdx(key);
        int dmg = Math.max(0, raw - AbilityResolver.passiveDamageReduction(card.getId()));
        int newHp = BattleState.slotHp(row[idx]) - dmg;
        if (newHp > 0) {
            row[idx] = BattleState.makeSlot(card.getId(), newHp);
            return card.getName() + "(-" + dmg + ")";
        }
        return join(card.getName() + " ✕", defeat(key, card, rewards));
    }

    /**
     * Removes a card that has reached 0 HP and applies everything that follows: Spore,
     * +1 soul cap for its owner (not items), Soul Sipper, the discard pile, its on-death
     * ability, Great Ent's shielded card advancing, and a champion evolving or losing the game.
     * rewards=false (Mage Decay) banishes the card: no soul cap, discard or on-death ability.
     * Returns the extra log text.
     */
    private String defeat(String key, Card card, boolean rewards) {
        boolean ownerIsP1 = BattleState.posKeyIsP1(key);
        int     idx       = BattleState.posKeyIdx(key);
        String spore = sporeEruption(key, ownerIsP1);
        st.rowOf(key)[idx] = "";
        st.clearCardState(key);

        String deathText = "";
        if (rewards) {
            if (!"item".equals(card.getType())) {
                st.setSoulCap(ownerIsP1, st.soulCap(ownerIsP1) + 1);
                if (!(card instanceof Champion)) gainSoul(!ownerIsP1);
            }
            st.discard(ownerIsP1).add(card.getId());
            deathText = AbilityResolver.onDeath(st, card.getId(), ownerIsP1, cardMap);
        }

        String after = "";
        if (card instanceof Champion)
            after = championFalls(key, (Champion) card);
        else if (CardIds.GREAT_ENT.equals(card.getId()) && BattleState.posKeyIsFront(key))
            after = releaseShielded(ownerIsP1, idx);
        return join(deathText, spore, after);
    }

    /** Soul Sipper: the side that killed a card gains 1 soul, up to its cap. */
    private void gainSoul(boolean isP1) {
        if (!AbilityResolver.hasSoulSipper(st, isP1)) return;
        st.setSouls(isP1, Math.min(st.souls(isP1) + 1, st.soulCap(isP1)));
    }

    /** A champion evolves to its next stage, or if it was the last one, its owner loses. */
    private String championFalls(String key, Champion champ) {
        ChampionLine line = champLines.get(champ.getLineId());
        Champion next = (line != null && !champ.isFinalStage(line)) ? line.getStageByIndex(champ.getStage()) : null;
        if (next != null) {
            st.rowOf(key)[BattleState.posKeyIdx(key)] = BattleState.makeSlot(next.getId(), next.getHp());
            st.actionsUsed.remove(key);
            return champ.getName() + " evolved to " + next.getName() + "!";
        }
        st.phase  = "finished";
        st.winner = st.playerName(!BattleState.posKeyIsP1(key));
        return "";
    }

    /** Great Ent died: the card it shielded moves up from the backline. */
    private String releaseShielded(boolean ownerIsP1, int col) {
        String[] back  = st.getRow(ownerIsP1, false);
        String[] front = st.getRow(ownerIsP1, true);
        if (isEmpty(back[col])) return "";
        front[col] = back[col];
        back[col]  = "";
        st.migrateCardState(BattleState.posKey(ownerIsP1, false, col), BattleState.posKey(ownerIsP1, true, col));
        return "Shielded card advances!";
    }

    /** A Spored card dying spawns a Fungal Pod on the enemy frontline. */
    private String sporeEruption(String key, boolean deadOwnerIsP1) {
        if (!st.sporedCards.containsKey(key)) return "";
        boolean oppIsP1 = !deadOwnerIsP1;
        String[] front = st.getRow(oppIsP1, true);
        for (int i = 0; i < 5; i++) {
            if (isEmpty(front[i])) {
                front[i] = BattleState.makeSlot(CardIds.FUNGAL_POD, 2);
                st.transformCounters.put(BattleState.posKey(oppIsP1, true, i), 2);
                return "Spore: a Fungal Pod erupted on the enemy frontline!";
            }
        }
        return "Spore: enemy frontline full, Fungal Pod lost!";
    }

    private boolean mageDecayActive(boolean isP1) {
        return (isP1 ? st.p1MageDecayRounds : st.p2MageDecayRounds) > 0;
    }

    // ── Card abilities ───────────────────────────────────────────────────────

    /** A card's no-target ability. Returns null if the card has none. */
    Outcome cardAbility(String key, boolean amP1) {
        String cardId = st.cardIdAt(key);
        if (cardId == null) return null;
        if (AbilityResolver.isAbilityNullified(amP1, st))
            return Outcome.refused("Void: card abilities nullified by enemy Heavenly Shade!");
        int capCost = AbilityResolver.SOUL_CAP_COST.getOrDefault(cardId, 0);
        if (capCost > 0 && st.soulCap(amP1) < capCost)
            return Outcome.refused("Need " + capCost + " soul cap to use this ability.");
        int soulCost = AbilityResolver.getEffectiveSoulCost(cardId, st, amP1);
        if (soulCost > 0 && st.souls(amP1) < soulCost)
            return Outcome.refused("Need " + soulCost + " soul to use this ability.");

        int col = BattleState.posKeyIdx(key);
        switch (cardId) {
            case CardIds.BEGINNER_MAGE:
                return mageBlast(key, amP1, cardId, "Beginner Mage", col - 1, col + 1, 3, 1,
                                 "Beginner Mage: no adjacent enemies!");
            case CardIds.FIRE_MAGE:
                return mageBlast(key, amP1, cardId, "Fire Mage", 0, 4, 3, 3,
                                 "Fire Mage: no enemies in frontline!");
            case CardIds.ICE_MAGE_APPRENTICE:
                return iceMageCast(key, amP1);
            case CardIds.TIME_WIZARD:
                return timeWizardSkip(key, amP1);
        }
        String result = AbilityResolver.executeActive(st, cardId, amP1, cardMap);
        return result == null ? null : Outcome.done(finishAbility(key, amP1, result));
    }

    /**
     * Beginner Mage: 1 damage to the enemy front cards in its own and the neighbouring columns.
     * Fire Mage: 3 damage to the first 3 enemy front cards.
     */
    private Outcome mageBlast(String key, boolean amP1, String cardId, String name,
                              int firstCol, int lastCol, int maxHits, int damage, String noTargetText) {
        int cost = AbilityResolver.getEffectiveSoulCost(cardId, st, amP1);
        boolean rewards = !mageDecayActive(amP1);
        StringBuilder sb = new StringBuilder(name + ":");
        int hits = 0;
        for (int col = Math.max(0, firstCol); col <= Math.min(4, lastCol) && hits < maxHits; col++) {
            String h = hit(BattleState.posKey(!amP1, true, col), damage, rewards);
            if (h.isEmpty()) continue;
            sb.append(' ').append(h);
            hits++;
        }
        if (hits == 0) return Outcome.refused(noTargetText);
        st.setSouls(amP1, st.souls(amP1) - cost);
        return Outcome.done(finishAbility(key, amP1, sb.toString()));
    }

    /** Ice Mage Apprentice: 3 coin flips; each heads freezes a random enemy for 2 rounds and hits it for 3. */
    private Outcome iceMageCast(String key, boolean amP1) {
        boolean enemyIsP1 = !amP1;
        List<String> targets = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            for (boolean front : new boolean[]{ true, false }) {
                if (!isEmpty(st.getRow(enemyIsP1, front)[i])) targets.add(BattleState.posKey(enemyIsP1, front, i));
            }
        }
        if (targets.isEmpty()) return Outcome.refused("Ice Mage: no enemies to freeze!");
        st.setSouls(amP1, st.souls(amP1) - AbilityResolver.getEffectiveSoulCost(CardIds.ICE_MAGE_APPRENTICE, st, amP1));

        boolean rewards = !mageDecayActive(amP1);
        int frozen = 0;
        for (int flip = 0; flip < 3 && !targets.isEmpty(); flip++) {
            if (Math.random() >= 0.5) continue;
            String pk = targets.get((int) (Math.random() * targets.size()));
            st.frozenCards.put(pk, Math.max(st.frozenCards.getOrDefault(pk, 0), 4));
            hit(pk, 3, rewards);
            if (st.cardIdAt(pk) == null) targets.remove(pk); // no longer targetable
            frozen++;
        }
        String text = frozen == 0 ? "Ice Mage: all tails — no enemies frozen!"
                    : "Ice Mage: froze " + frozen + " enem" + (frozen > 1 ? "ies" : "y") + " and dealt 3 dmg!";
        return Outcome.done(finishAbility(key, amP1, text));
    }

    /** Time Wizard: pay 10 souls and 10 soul cap, skip 3 rounds of effects, opponent draws 3, turn ends. */
    private Outcome timeWizardSkip(String key, boolean amP1) {
        st.setSouls(amP1, st.souls(amP1) - 10);
        st.setSoulCap(amP1, st.soulCap(amP1) - 10);

        // 3 rounds = 6 end-of-turn ticks
        String ticks = "";
        for (int t = 0; t < 6 && !isFinished(); t++) ticks = endOfTurnEffects(ticks);

        List<String> deck = st.deck(!amP1), hand = st.hand(!amP1);
        for (int d = 0; d < 3 && !deck.isEmpty(); d++) hand.add(deck.remove(0));

        return Outcome.doneEndTurn(finishAbility(key, amP1, join("Time Wizard: skipped 3 rounds!", ticks)));
    }

    /** A card's targeted ability, aimed at a slot. Returns null if the source card is gone. */
    Outcome targetedAbility(String sourceKey, boolean amP1, boolean tgtIsP1, boolean tgtFront, int tgtIdx, int choice) {
        String srcId = st.cardIdAt(sourceKey);
        if (srcId == null) return null;
        if (AbilityResolver.isAbilityNullified(amP1, st))
            return Outcome.refused("Void: card abilities nullified by enemy Heavenly Shade!");
        if (tgtIsP1 != amP1 && AbilityResolver.isImmuneToAbilities(tgtIsP1, st))
            return Outcome.refused("Sovereign: the enemy champion makes their cards immune to abilities!");

        String tgtKey = BattleState.posKey(tgtIsP1, tgtFront, tgtIdx);
        if (CardIds.GREATWING.equals(srcId)) return greatWing(sourceKey, amP1, tgtKey);
        if (CardIds.ROOK.equals(srcId))      return castle(sourceKey, srcId, amP1, tgtKey);

        String result = AbilityResolver.executeTargeted(st, srcId, amP1, tgtIsP1, tgtFront, tgtIdx, choice, cardMap);
        return Outcome.done(finishAbility(sourceKey, amP1, result));
    }

    /** GreatWing: return an enemy card to its owner's hand. */
    private Outcome greatWing(String sourceKey, boolean amP1, String tgtKey) {
        String tgtId = st.cardIdAt(tgtKey);
        if (tgtId == null) return Outcome.refused("GreatWing: no card at target.");
        Card tgtC = cardMap.get(tgtId);
        if (tgtC instanceof Champion) return Outcome.refused("GreatWing: cannot return Champions.");
        st.rowOf(tgtKey)[BattleState.posKeyIdx(tgtKey)] = "";
        st.clearCardState(tgtKey);
        st.hand(BattleState.posKeyIsP1(tgtKey)).add(tgtId);
        return Outcome.done(finishAbility(sourceKey, amP1,
                "GreatWing: " + cardName(tgtId) + " returned to opponent's hand!"));
    }

    /** Rook's Castle: swap places with a friendly card, which can then act again. */
    private Outcome castle(String rookKey, String rookId, boolean amP1, String tgtKey) {
        if (rookKey.equals(tgtKey)) return Outcome.refused("Castle: cannot swap with yourself.");
        String[] rookRow = st.rowOf(rookKey), tgtRow = st.rowOf(tgtKey);
        int a = BattleState.posKeyIdx(rookKey), b = BattleState.posKeyIdx(tgtKey);
        String tgtName = cardName(st.cardIdAt(tgtKey));
        String tmp = rookRow[a]; rookRow[a] = tgtRow[b]; tgtRow[b] = tmp;
        st.swapCardState(rookKey, tgtKey);
        // Rook (now at tgtKey) has used its action; the other card (now at rookKey) gets its action back
        st.actionsUsed.remove(rookKey);
        st.actionsUsed.add(tgtKey);
        st.abilityUsedThisTurn.add(rookKey);
        return Outcome.done(withHarvest("Castle: swapped " + cardName(rookId) + " with " + tgtName
                                        + "! " + tgtName + " can act again.", amP1));
    }

    /** A champion's active ability (Mimic and Bypass are handled by the screen). */
    Outcome championAbility(String champId, boolean amP1) {
        String result = AbilityResolver.activeAbility(st, champId, amP1, cardMap);
        if (result == null) return Outcome.refused("This champion has a passive ability.");
        return Outcome.done(finishAbility(BattleState.posKey(amP1, false, BattleState.CHAMP_SLOT), amP1, result));
    }

    /** Furnace Bot: spend the selected Scrap to buff one bot. */
    Outcome smelt(String sourceKey, boolean amP1, List<String> scraps, boolean botFront, int botIdx) {
        String result = AbilityResolver.smelt(st, amP1, scraps, botFront, botIdx, cardMap);
        return Outcome.done(finishAbility(sourceKey, amP1, result));
    }

    /** Iron Tusks Bot: spend the selected Scrap on Fortify. */
    Outcome fortify(String sourceKey, boolean amP1, List<String> scraps) {
        String result = AbilityResolver.fortify(st, amP1, scraps, cardMap);
        return Outcome.done(finishAbility(sourceKey, amP1, result));
    }

    /** Constructor Bot: summon a bot from hand for 2 Scrap. */
    Outcome construct(String sourceKey, boolean amP1, String botId) {
        String result = AbilityResolver.construct(st, amP1, botId, cardMap);
        return Outcome.done(finishAbility(sourceKey, amP1, result));
    }

    /** Echo Spirit (costs 1 soul cap) or Mimic (ends the turn) using a copied no-target ability. */
    Outcome copiedActive(String sourceKey, boolean amP1, String copiedId, boolean isEcho) {
        if (isEcho) payEchoCost(amP1);
        String result = AbilityResolver.executeActive(st, copiedId, amP1, cardMap);
        String text = (isEcho ? "Echo: copied " : "Mimic: stole ") + cardName(copiedId) + " — " + result;
        String log = finishAbility(sourceKey, amP1, text);
        return isEcho ? Outcome.done(log) : Outcome.doneEndTurn(log);
    }

    /** Echo Spirit or Mimic using a copied targeted ability on the chosen slot. */
    Outcome copiedTargeted(String sourceKey, boolean amP1, String copiedId,
                           boolean tgtIsP1, boolean tgtFront, int tgtIdx) {
        boolean isEcho = CardIds.ECHO_SPIRIT.equals(st.cardIdAt(sourceKey));
        if (isEcho) payEchoCost(amP1);
        String result = AbilityResolver.executeEchoCopy(st, copiedId, amP1, tgtIsP1, tgtFront, tgtIdx, 0, cardMap);
        String log = finishAbility(sourceKey, amP1, (isEcho ? "Echo: " : "Mimic: ") + result);
        return isEcho ? Outcome.done(log) : Outcome.doneEndTurn(log);
    }

    private void payEchoCost(boolean amP1) {
        st.setSoulCap(amP1, Math.max(0, st.soulCap(amP1) - 1));
    }

    /** Whether Echo Spirit or Mimic may copy this card's ability. */
    static boolean isCopyable(String cardId) {
        String aType = AbilityResolver.abilityType(cardId);
        if (!"active".equals(aType) && !"targeted".equals(aType)) return false;
        // Multi-step and self-referential abilities can't be copied
        return !CardIds.FURNACE_BOT.equals(cardId) && !CardIds.IRON_TUSKS_BOT.equals(cardId)
            && !CardIds.CONSTRUCTOR_BOT.equals(cardId) && !CardIds.ECHO_SPIRIT.equals(cardId);
    }

    /** Marks the source card as having acted and used its ability, and adds Shade's Harvest. */
    private String finishAbility(String sourceKey, boolean amP1, String result) {
        st.useAction(BattleState.posKeyIsP1(sourceKey), BattleState.posKeyIsFront(sourceKey),
                     BattleState.posKeyIdx(sourceKey));
        st.abilityUsedThisTurn.add(sourceKey);
        return withHarvest(result, amP1);
    }

    private String withHarvest(String result, boolean amP1) {
        return join(result, AbilityResolver.onAbilityUsed(st, amP1));
    }

    private void buffAll(boolean ownerIsP1, int atk, int hp) {
        for (boolean front : new boolean[]{ true, false }) {
            String[] row = st.getRow(ownerIsP1, front);
            for (int i = 0; i < 5; i++) {
                if (isEmpty(row[i])) continue;
                row[i] = BattleState.makeSlot(BattleState.slotId(row[i]), BattleState.slotHp(row[i]) + hp);
                if (atk != 0) st.fieldAtkBonus.merge(BattleState.posKey(ownerIsP1, front, i), atk, Integer::sum);
            }
        }
    }

    // ── Ending the game and the turn ─────────────────────────────────────────

    void forfeit(boolean amP1) {
        st.phase  = "finished";
        st.winner = st.playerName(!amP1);
    }

    /** Runs end-of-turn effects, the next player draws and refills souls, and the turn passes. */
    String endTurn(boolean amP1, String log) {
        log = endOfTurnEffects(log);
        boolean next = !amP1;
        List<String> deck = st.deck(next);
        if (!deck.isEmpty()) st.hand(next).add(deck.remove(0));
        st.setSouls(next, st.soulCap(next));
        st.actionsUsed.clear();
        st.abilityUsedThisTurn.clear();
        st.freeplayCards.clear();
        st.mantisSecondAttack.clear();
        st.p1ExtraActions = 0;
        st.p2ExtraActions = 0;
        st.currentTurn = st.playerName(next);
        return log;
    }

    /** Everything that happens between turns. Returns the log with any new events added. */
    String endOfTurnEffects(String log) {
        log = damageOverTime(st.burnedCards.keySet(),  "burned to death",  null,            log);
        log = damageOverTime(st.decayedCards.keySet(), "decayed to death", this::decayTick, log);
        log = damageOverTime(st.poisonedCards,         "died from poison", null,            log);

        // Flame Bloomling: +2 HP per burning card
        int burning = st.burnedCards.size();
        if (burning > 0) {
            for (String[] row : new String[][]{ st.p1Front, st.p1Back, st.p2Front, st.p2Back }) {
                for (int i = 0; i < 5; i++) {
                    if (CardIds.FLAME_BLOOMLING.equals(BattleState.slotId(row[i])))
                        row[i] = BattleState.makeSlot(CardIds.FLAME_BLOOMLING, BattleState.slotHp(row[i]) + burning * 2);
                }
            }
        }

        countDown(st.sporedCards);

        // Fungal Domain aura
        boolean domainActive = st.p1FungalDomain > 0 || st.p2FungalDomain > 0;
        if (st.p1FungalDomain > 0) st.p1FungalDomain--;
        if (st.p2FungalDomain > 0) st.p2FungalDomain--;
        if (domainActive) log = addEvent(log, AbilityResolver.applyFungalDomainAura(st, cardMap));

        countDown(st.frozenCards);
        countDown(st.sealedSlots);
        if (st.p1MageDecayRounds > 0) st.p1MageDecayRounds--;
        if (st.p2MageDecayRounds > 0) st.p2MageDecayRounds--;

        // Pod cards grow when their countdown runs out
        for (String key : new ArrayList<>(st.transformCounters.keySet())) {
            int turns = st.transformCounters.get(key) - 1;
            if (turns > 0) {
                st.transformCounters.put(key, turns);
            } else {
                st.transformCounters.remove(key);
                log = addEvent(log, applyTransform(key));
            }
        }

        // Druid: each side's pods gain +1 ATK and +1 HP while it has a Druid
        for (boolean side : new boolean[]{ true, false }) {
            if (!hasCard(side, CardIds.DRUID)) continue;
            for (boolean front : new boolean[]{ true, false }) {
                String[] row = st.getRow(side, front);
                for (int i = 0; i < 5; i++) {
                    String cid = BattleState.slotId(row[i]);
                    Card c = cid != null ? cardMap.get(cid) : null;
                    if (c == null || !"pod".equals(c.getType())) continue;
                    st.fieldAtkBonus.merge(BattleState.posKey(side, front, i), 1, Integer::sum);
                    row[i] = BattleState.makeSlot(cid, BattleState.slotHp(row[i]) + 1);
                }
            }
        }
        return log;
    }

    /** Burn, decay and poison: 1 damage per turn to every affected card. */
    private String damageOverTime(Collection<String> keys, String deathText,
                                  Consumer<String> beforeDamage, String log) {
        for (String key : new ArrayList<>(keys)) {
            String[] row = st.rowOf(key);
            int idx = BattleState.posKeyIdx(key);
            if (isEmpty(row[idx])) { keys.remove(key); continue; }
            if (beforeDamage != null) beforeDamage.accept(key);
            String id = BattleState.slotId(row[idx]);
            Card card = cardMap.get(id);
            int newHp = BattleState.slotHp(row[idx]) - 1;
            if (newHp > 0) {
                row[idx] = BattleState.makeSlot(id, newHp);
            } else if (card == null) {
                row[idx] = "";
                st.clearCardState(key);
            } else {
                log = addEvent(log, join(card.getName() + " " + deathText + "!", defeat(key, card, true)));
            }
        }
        return log;
    }

    /** Decay also counts down and takes 1 ATK each turn. */
    private void decayTick(String key) {
        int turns = st.decayedCards.get(key) - 1;
        if (turns <= 0) st.decayedCards.remove(key);
        else            st.decayedCards.put(key, turns);
        st.fieldAtkBonus.merge(key, -1, Integer::sum);
    }

    /** Pod cards: coin flip, replace the card with its grown form, keeping the damage it has taken. */
    private String applyTransform(String key) {
        String[] row = st.rowOf(key);
        int idx = BattleState.posKeyIdx(key);
        if (isEmpty(row[idx])) return "";
        String oldId = BattleState.slotId(row[idx]);
        Card oldC = cardMap.get(oldId);
        if (oldC == null) return "";
        boolean heads = AbilityResolver.coinFlip();
        String newId = AbilityResolver.transformTarget(oldId, heads);
        Card newC = newId != null ? cardMap.get(newId) : null;
        if (newC == null) return "";
        int damageTaken = oldC.getHp() - BattleState.slotHp(row[idx]);
        row[idx] = BattleState.makeSlot(newId, Math.max(1, newC.getHp() - damageTaken));
        int delay = AbilityResolver.transformDelay(newId);
        if (delay > 0) st.transformCounters.put(key, delay);
        if (CardIds.GREAT_ENT.equals(newId)) st.fieldLockedCards.add(key);
        return oldC.getName() + " → " + newC.getName() + " (" + (heads ? "Heads" : "Tails") + ")!";
    }

    private static void countDown(Map<String, Integer> timers) {
        timers.replaceAll((k, v) -> v - 1);
        timers.values().removeIf(v -> v <= 0);
    }

    // ── Small helpers ────────────────────────────────────────────────────────

    boolean isFinished() {
        return "finished".equals(st.phase);
    }

    private boolean hasCard(boolean isP1, String cardId) {
        for (boolean front : new boolean[]{ true, false })
            for (String s : st.getRow(isP1, front))
                if (cardId.equals(BattleState.slotId(s))) return true;
        return false;
    }

    private Card cardAt(String key) {
        String id = st.cardIdAt(key);
        return id == null ? null : cardMap.get(id);
    }

    private String cardName(String cardId) {
        Card c = cardId != null ? cardMap.get(cardId) : null;
        return c != null ? c.getName() : String.valueOf(cardId);
    }

    private static boolean isEmpty(String slot) {
        return slot == null || slot.isEmpty();
    }

    /** Joins parts of one event with spaces, skipping empty ones. */
    static String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(p.trim());
        }
        return sb.toString();
    }

    /** Adds a separate event to the log with " | ". */
    static String addEvent(String log, String event) {
        if (event == null || event.isEmpty()) return log;
        return log.isEmpty() ? event : log + " | " + event;
    }
}
