import java.util.*;

/**
 * The computer opponent in Solo Battle. There is no Swing here: the battle screen asks
 * {@link #play} for one move at a time and shows the result.
 *
 * The bot tries every move it is allowed to make on a copy of the battle, scores the board
 * each move leaves behind with {@link #score}, and plays the move that improves the score the
 * most. Moves that end the turn (a champion attacking, Mimic, Time Wizard) wait until nothing
 * else helps. Because the moves go through {@link BattleRules}, the bot can only do what a
 * player could.
 */
class BotPlayer {

    /** The bot's player name. It has a space, so no real account can have it. */
    static final String NAME = "Ronno Bot";

    /** The most moves the bot makes in one turn, so it can never loop forever. */
    static final int MAX_MOVES_PER_TURN = 30;

    static final int DECK_SIZE = 20;

    /** Abilities the bot doesn't know how to use: they need several picks (Scrap, a bot to summon, a card to copy). */
    private static final Set<String> SKIPPED_ABILITIES = new HashSet<>(Arrays.asList(
        CardIds.FURNACE_BOT, CardIds.IRON_TUSKS_BOT, CardIds.CONSTRUCTOR_BOT, CardIds.ECHO_SPIRIT,
        CardIds.MIMIC_CHAMP));

    /** A move has to improve the score by more than this to be worth making. */
    private static final double MIN_GAIN = 0.5;

    /** What the bot did, for the battle log, and the rules' result. */
    static final class Move {
        final String              text;
        final BattleRules.Outcome outcome;

        Move(String text, BattleRules.Outcome outcome) {
            this.text = text;
            this.outcome = outcome;
        }
    }

    // ── Setting up ───────────────────────────────────────────────────────────

    /**
     * A random deck from every card in the game, shaped like a sensible deck: mostly cheap cards,
     * a few big ones. Tokens and cards whose ability the bot can't use are left out.
     */
    static List<String> buildDeck() {
        List<Card> cheap = new ArrayList<>(), mid = new ArrayList<>(), big = new ArrayList<>(), huge = new ArrayList<>();
        for (Card c : GameData.allCards()) {
            String id = c.getId();
            if (id.startsWith("__") || CardIds.WORKER_ANT_2.equals(id) || SKIPPED_ABILITIES.contains(id)) continue;
            int cost = c.getCost();
            (cost <= 2 ? cheap : cost <= 4 ? mid : cost <= 7 ? big : huge).add(c);
        }
        List<String> deck = new ArrayList<>();
        pick(cheap, 8, deck);
        pick(mid,   6, deck);
        pick(big,   4, deck);
        pick(huge,  2, deck);
        return deck;
    }

    private static void pick(List<Card> pool, int count, List<String> deck) {
        if (pool.isEmpty()) return;
        Random rnd = new Random();
        for (int i = 0; i < count; i++) deck.add(pool.get(rnd.nextInt(pool.size())).getId());
    }

    /** A random champion line. */
    static String pickChampionLine() {
        List<String> lines = new ArrayList<>(GameData.championLines().keySet());
        return lines.isEmpty() ? "" : lines.get(new Random().nextInt(lines.size()));
    }

    // ── Playing ──────────────────────────────────────────────────────────────

    /**
     * Makes the bot's best move on st and returns it, or null when no move helps and the bot
     * should end its turn. The caller ends the turn when the move's outcome says so.
     */
    static Move play(BattleState st, Map<String, Card> cardMap, Map<String, ChampionLine> champLines,
                     boolean botIsP1) {
        double base = score(st, cardMap, champLines, botIsP1);
        Candidate best = null, bestEnding = null;
        for (Candidate c : candidates(st, cardMap, champLines, botIsP1)) {
            BattleState trial = st.copy();
            BattleRules.Outcome o = c.apply(new BattleRules(trial, cardMap, champLines));
            if (o == null || !o.changed) continue;
            c.gain = score(trial, cardMap, champLines, botIsP1) - base;
            if (c.gain <= MIN_GAIN) continue;
            if (o.endsTurn && !"finished".equals(trial.phase)) {
                if (bestEnding == null || c.gain > bestEnding.gain) bestEnding = c;
            } else if (best == null || c.gain > best.gain) {
                best = c;
            }
        }
        Candidate chosen = best != null ? best : bestEnding;
        if (chosen == null) return null;
        BattleRules.Outcome o = chosen.apply(new BattleRules(st, cardMap, champLines));
        if (o == null || !o.changed) return null;   // a coin flip went differently; try again next move
        return new Move(chosen.text, o);
    }

    /** One move the bot could make. */
    private abstract static class Candidate {
        final String text;
        double gain;

        Candidate(String text) { this.text = text; }

        abstract BattleRules.Outcome apply(BattleRules rules);
    }

    /** Every move the rules allow the bot right now. Placements the rules refuse are dropped when tried. */
    private static List<Candidate> candidates(BattleState st, Map<String, Card> cardMap,
                                              Map<String, ChampionLine> champLines, boolean me) {
        BattleRules rules = new BattleRules(st, cardMap, champLines);
        List<Candidate> list = new ArrayList<>();

        // Placing a card from hand. Equal cards are only tried once.
        List<String> hand = st.hand(me);
        Set<String> tried = new HashSet<>();
        for (int h = 0; h < hand.size(); h++) {
            String id = hand.get(h);
            if (!tried.add(id)) continue;
            final int handIdx = h;
            for (boolean front : new boolean[]{ true, false }) {
                for (int i = 0; i < 5; i++) {
                    if (st.cardIdAt(BattleState.posKey(me, front, i)) != null) continue;
                    final boolean f = front;
                    final int idx = i;
                    list.add(new Candidate("placed " + name(cardMap, id) + (front ? " in its frontline" : " in its backline")) {
                        BattleRules.Outcome apply(BattleRules r) { return r.place(me, handIdx, f, idx); }
                    });
                }
            }
        }

        String champId = AbilityResolver.currentChampId(st, me);
        String champKey = BattleState.posKey(me, false, BattleState.CHAMP_SLOT);
        boolean nullified = AbilityResolver.isAbilityNullified(me, st);
        boolean canBypass = CardIds.BYPASS_CHAMP.equals(champId) && !nullified
                         && AbilityResolver.canBypass(st, me) && st.hasAction(me, false, BattleState.CHAMP_SLOT);

        for (boolean front : new boolean[]{ true, false }) {
            for (int i = 0; i < 5; i++) {
                String key = BattleState.posKey(me, front, i);
                if (!rules.canAct(key)) continue;
                String id = st.cardIdAt(key);
                String who = name(cardMap, id);

                // Attacks
                for (boolean bypass : canBypass ? new boolean[]{ false, true } : new boolean[]{ false }) {
                    for (boolean tf : new boolean[]{ true, false }) {
                        for (int t = 0; t < 5; t++) {
                            if (!rules.canAttack(key, !me, tf, t, bypass)) continue;
                            if (bypass && rules.canAttack(key, !me, tf, t, false)) continue;   // no need to spend Scrap
                            final boolean b = bypass, tFront = tf;
                            final int tIdx = t;
                            String target = name(cardMap, st.cardIdAt(BattleState.posKey(!me, tf, t)));
                            list.add(new Candidate(who + " attacked " + target + (bypass ? " (Bypass)" : "")) {
                                BattleRules.Outcome apply(BattleRules r) { return r.attack(me, key, !me, tFront, tIdx, b); }
                            });
                        }
                    }
                }

                // The champion's ability
                if (key.equals(champKey)) {
                    Card champ = cardMap.get(id);
                    if (champ instanceof Champion && !champ.getAbility().isEmpty() && !nullified
                            && !SKIPPED_ABILITIES.contains(id) && !CardIds.BYPASS_CHAMP.equals(id)) {
                        list.add(new Candidate("used " + who + "'s ability") {
                            BattleRules.Outcome apply(BattleRules r) { return r.championAbility(id, me); }
                        });
                    }
                    continue;
                }

                // A card's ability
                if (nullified || SKIPPED_ABILITIES.contains(id) || !AbilityResolver.canUseAbility(id, st, me)) continue;
                String type = AbilityResolver.abilityType(id);
                if ("active".equals(type)) {
                    list.add(new Candidate("used " + who + "'s ability") {
                        BattleRules.Outcome apply(BattleRules r) { return r.cardAbility(key, me); }
                    });
                } else if ("targeted".equals(type)) {
                    int choices = CardIds.UPGRADE_BOT.equals(id) ? 2 : 1;
                    for (boolean side : new boolean[]{ true, false }) {
                        for (boolean tf : new boolean[]{ true, false }) {
                            for (int t = 0; t < 5; t++) {
                                if (!rules.canAbilityTarget(id, me, side, tf, t)) continue;
                                String tgtId = st.cardIdAt(BattleState.posKey(side, tf, t));
                                String on = tgtId != null ? " on " + name(cardMap, tgtId) : "";
                                for (int ch = 0; ch < choices; ch++) {
                                    final boolean tSide = side, tFront = tf;
                                    final int tIdx = t, choice = ch;
                                    list.add(new Candidate("used " + who + "'s ability" + on) {
                                        BattleRules.Outcome apply(BattleRules r) {
                                            return r.targetedAbility(key, me, tSide, tFront, tIdx, choice);
                                        }
                                    });
                                }
                            }
                        }
                    }
                }
            }
        }
        return list;
    }

    // ── Scoring a board ──────────────────────────────────────────────────────

    /**
     * How good the battle looks for the bot: its cards, hand and soul cap minus the player's,
     * and how much life and how many stages each champion line has left, which matter most
     * because they decide the game. Winning or losing outweighs everything.
     */
    static double score(BattleState st, Map<String, Card> cardMap, Map<String, ChampionLine> champLines,
                        boolean me) {
        if ("finished".equals(st.phase))
            return st.playerName(me).equals(st.winner) ? 100_000 : -100_000;
        return side(st, cardMap, champLines, me) - side(st, cardMap, champLines, !me);
    }

    private static double side(BattleState st, Map<String, Card> cardMap, Map<String, ChampionLine> champLines,
                               boolean isP1) {
        double s = 0;
        for (boolean front : new boolean[]{ true, false }) {
            for (int i = 0; i < 5; i++) {
                String key = BattleState.posKey(isP1, front, i);
                String id  = st.cardIdAt(key);
                Card   c   = id != null ? cardMap.get(id) : null;
                if (c == null) continue;
                int hp  = BattleState.slotHp(st.getRow(isP1, front)[i]);
                int atk = Math.max(0, c.getAttack() + st.fieldAtkBonus.getOrDefault(key, 0));
                if (c instanceof Champion) {
                    s += 3 * lifeLeft((Champion) c, hp, champLines) + 25 * stagesLeft((Champion) c, champLines) + atk;
                    continue;
                }
                s += 1 + hp + 2 * atk;                              // the 1: fewer enemy cards is always better
                s += front ? 0.3 * hp : 0.3 * atk;                  // tough cards in front, strong ones behind
                if (st.isFrozen(key))                  s -= 3;
                if (st.burnedCards.containsKey(key))   s -= 2;
                if (st.poisonedCards.contains(key))    s -= 2;
                if (st.decayedCards.containsKey(key))  s -= 2;
                if (st.sporedCards.containsKey(key))   s -= 1;
            }
        }
        if (st.frontlineCount(isP1) == 0) s -= 8;                   // nothing guards the backline and champion
        s += 2 * st.hand(isP1).size();
        s += 1.5 * st.soulCap(isP1);
        s += 0.5 * st.souls(isP1);
        return s;
    }

    /** The champion's HP plus the full HP of every stage still to come. */
    private static int lifeLeft(Champion champ, int hp, Map<String, ChampionLine> champLines) {
        int life = hp;
        ChampionLine line = champLines.get(champ.getLineId());
        if (line == null) return life;
        for (Champion stage : line.getStages()) if (stage.getStage() > champ.getStage()) life += stage.getHp();
        return life;
    }

    /** How many times this champion can still fall before its owner loses, counting this stage. */
    private static int stagesLeft(Champion champ, Map<String, ChampionLine> champLines) {
        ChampionLine line = champLines.get(champ.getLineId());
        return line == null ? 1 : line.size() - champ.getStage() + 1;
    }

    private static String name(Map<String, Card> cardMap, String id) {
        Card c = id != null ? cardMap.get(id) : null;
        return c != null ? c.getName() : String.valueOf(id);
    }
}
