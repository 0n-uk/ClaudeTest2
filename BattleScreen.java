import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class BattleScreen {

    private static final Color BG        = new Color(20, 20, 30);
    private static final Color HDR_BG    = new Color(15, 15, 25);
    private static final Color OPP_BG    = new Color(35, 22, 22);
    private static final Color MY_BG     = new Color(22, 32, 22);
    private static final Color EMPTY_BG  = new Color(28, 28, 45);
    private static final Color CARD_BG   = new Color(42, 42, 65);
    private static final Color SEL_ATK   = new Color(80, 160, 255);
    private static final Color SEL_TGT   = new Color(220, 60,  60);
    private static final Color CHAMP_CLR = new Color(220, 180, 60);

    // ── Entry point ───────────────────────────────────────────────────────────

    public static JPanel buildPanel(User user, String battleId, Runnable onComplete) {

        Map<String, Card>         cardMap   = BattleManager.buildCardMap();
        Map<String, ChampionLine> champLines = ChampionLine.loadAll();
        for (Card c : user.getOwnedCards()) cardMap.putIfAbsent(c.getId(), c);

        BattleState[] stRef    = { BattleState.load(battleId) };
        int[]         selHand  = { -1 };
        String[]      selField = { null };
        String[]      msg      = { "" };
        boolean[]     bypass   = { false }; // T-Bot bypass mode active

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(BG);

        javax.swing.Timer[] timerRef   = { null };
        Runnable[]          rebuildRef = { null };

        rebuildRef[0] = () -> {
            BattleState st = stRef[0];
            if (st == null) return;
            boolean amP1   = user.getUsername().equals(st.player1);
            boolean myTurn = user.getUsername().equals(st.currentTurn);

            wrapper.removeAll();

            if ("finished".equals(st.phase)) {
                if (timerRef[0] != null) timerRef[0].stop();
                wrapper.add(resultPanel(user.getUsername().equals(st.winner), onComplete),
                            BorderLayout.CENTER);
                wrapper.revalidate(); wrapper.repaint();
                return;
            }

            String oppName  = amP1 ? st.player2 : st.player1;
            int oppSouls    = amP1 ? st.p2Souls   : st.p1Souls;
            int oppSoulCap  = amP1 ? st.p2SoulCap : st.p1SoulCap;
            int oppDeck     = (amP1 ? st.p2Deck : st.p1Deck).size();
            int mySouls     = amP1 ? st.p1Souls   : st.p2Souls;
            int mySoulCap   = amP1 ? st.p1SoulCap : st.p2SoulCap;
            int myDeck      = (amP1 ? st.p1Deck : st.p2Deck).size();
            List<String> myHand = new ArrayList<>(amP1 ? st.p1Hand : st.p2Hand);

            wrapper.add(header(oppName, oppSouls, oppSoulCap, oppDeck, myTurn, msg[0]), BorderLayout.NORTH);

            JPanel field = new JPanel();
            field.setLayout(new BoxLayout(field, BoxLayout.Y_AXIS));
            field.setBackground(BG);
            field.setBorder(new EmptyBorder(4, 8, 4, 8));

            boolean oppIsP1 = !amP1;

            // Opponent section (their back then front from our perspective)
            field.add(fieldRow(st, cardMap, oppIsP1, false, amP1, myTurn,
                               selHand, selField, msg, stRef, user, wrapper, battleId,
                               onComplete, rebuildRef, bypass, champLines, OPP_BG));
            field.add(Box.createVerticalStrut(3));
            field.add(fieldRow(st, cardMap, oppIsP1, true, amP1, myTurn,
                               selHand, selField, msg, stRef, user, wrapper, battleId,
                               onComplete, rebuildRef, bypass, champLines, OPP_BG));

            JPanel sep = new JPanel();
            sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 4));
            sep.setBackground(new Color(60, 60, 90));
            field.add(Box.createVerticalStrut(5));
            field.add(sep);
            field.add(Box.createVerticalStrut(5));

            // My section (my front then back)
            field.add(fieldRow(st, cardMap, amP1, true, amP1, myTurn,
                               selHand, selField, msg, stRef, user, wrapper, battleId,
                               onComplete, rebuildRef, bypass, champLines, MY_BG));
            field.add(Box.createVerticalStrut(3));
            field.add(fieldRow(st, cardMap, amP1, false, amP1, myTurn,
                               selHand, selField, msg, stRef, user, wrapper, battleId,
                               onComplete, rebuildRef, bypass, champLines, MY_BG));

            wrapper.add(field, BorderLayout.CENTER);

            JPanel south = new JPanel(new BorderLayout(0, 4));
            south.setBackground(BG);
            south.setBorder(new EmptyBorder(4, 8, 8, 8));
            south.add(handPanel(myHand, cardMap, mySouls, mySoulCap, myDeck, myTurn,
                                selHand, selField, msg, stRef, amP1, rebuildRef, champLines),
                      BorderLayout.CENTER);
            south.add(controls(stRef, amP1, myTurn, selHand, selField, msg,
                                rebuildRef, bypass, user, onComplete, champLines, cardMap),
                      BorderLayout.SOUTH);
            wrapper.add(south, BorderLayout.SOUTH);

            wrapper.revalidate();
            wrapper.repaint();
        };

        timerRef[0] = new javax.swing.Timer(600, e -> {
            BattleState fresh = BattleState.load(battleId);
            if (fresh != null) stRef[0] = fresh;
            rebuildRef[0].run();
        });
        timerRef[0].start();

        rebuildRef[0].run();
        return wrapper;
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private static JPanel header(String oppName, int oppSouls, int oppSoulCap,
                                  int oppDeck, boolean myTurn, String msg) {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setBackground(HDR_BG);
        p.setBorder(new EmptyBorder(8, 14, 8, 14));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        left.setOpaque(false);
        left.add(lbl("Opponent: " + oppName, Font.BOLD, 14, new Color(220, 100, 100)));
        left.add(lbl("Soul " + oppSouls + "/" + oppSoulCap, Font.PLAIN, 12, new Color(200, 160, 80)));
        left.add(lbl("Deck: " + oppDeck, Font.PLAIN, 12, new Color(140, 140, 165)));

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        JLabel turn = lbl(myTurn ? "YOUR TURN" : "Opponent's Turn", Font.BOLD, 15,
                          myTurn ? new Color(100, 220, 130) : new Color(220, 100, 100));
        turn.setHorizontalAlignment(SwingConstants.CENTER);
        center.add(turn, BorderLayout.CENTER);
        if (!msg.isEmpty()) {
            JLabel msgL = lbl(msg, Font.ITALIC, 11, new Color(220, 200, 100));
            msgL.setHorizontalAlignment(SwingConstants.CENTER);
            center.add(msgL, BorderLayout.SOUTH);
        }

        p.add(left,   BorderLayout.WEST);
        p.add(center, BorderLayout.CENTER);
        return p;
    }

    // ── Field row ─────────────────────────────────────────────────────────────

    private static JPanel fieldRow(BattleState st, Map<String, Card> cardMap,
                                    boolean fieldIsP1, boolean isFront, boolean amP1,
                                    boolean myTurn, int[] selHand, String[] selField,
                                    String[] msg, BattleState[] stRef, User user,
                                    JPanel wrapper, String battleId, Runnable onComplete,
                                    Runnable[] rebuildRef, boolean[] bypass,
                                    Map<String, ChampionLine> champLines, Color bg) {
        JPanel row = new JPanel(new GridLayout(1, 5, 4, 0));
        row.setBackground(bg);
        row.setBorder(new EmptyBorder(3, 0, 3, 0));
        for (int i = 0; i < 5; i++)
            row.add(slot(st, cardMap, fieldIsP1, isFront, i, amP1, myTurn,
                         selHand, selField, msg, stRef, user, wrapper, battleId,
                         onComplete, rebuildRef, bypass, champLines));
        return row;
    }

    // ── Slot ──────────────────────────────────────────────────────────────────

    private static JPanel slot(BattleState st, Map<String, Card> cardMap,
                                boolean fieldIsP1, boolean isFront, int idx,
                                boolean amP1, boolean myTurn,
                                int[] selHand, String[] selField, String[] msg,
                                BattleState[] stRef, User user, JPanel wrapper,
                                String battleId, Runnable onComplete, Runnable[] rebuildRef,
                                boolean[] bypass, Map<String, ChampionLine> champLines) {

        String[] row   = isFront ? (fieldIsP1 ? st.p1Front : st.p2Front)
                                 : (fieldIsP1 ? st.p1Back  : st.p2Back);
        String sv      = row[idx];
        boolean empty  = sv == null || sv.isEmpty();
        String  cardId = empty ? null : BattleState.slotId(sv);
        int     hp     = empty ? 0    : BattleState.slotHp(sv);
        Card    card   = (cardId != null) ? cardMap.get(cardId) : null;
        boolean isChamp   = card instanceof Champion;
        boolean isMyField = fieldIsP1 == amP1;
        String  posKey    = BattleState.posKey(fieldIsP1, isFront, idx);
        boolean hasAct    = !empty && st.hasAction(fieldIsP1, isFront, idx);
        boolean isSel     = posKey.equals(selField[0]);

        boolean canPlace  = isMyField && empty  && myTurn && selHand[0] >= 0;
        boolean canSelect = isMyField && !empty && myTurn && hasAct;
        // Champions can be selected for ability use via separate button; regular cards select for attack
        boolean canTarget = !isMyField && !empty && myTurn && selField[0] != null
                             && (bypass[0] ? st.isTargetableBypass(fieldIsP1, isFront, idx)
                                           : st.isTargetable(fieldIsP1, isFront, idx));

        Color accent  = isChamp ? CHAMP_CLR
                      : (AbilityResolver.SCRAP_ID.equals(cardId) ? new Color(140,140,160)
                      : (card != null ? CardViewer.typeColor(card.getType()) : new Color(80,80,110)));
        Color bgColor = isSel ? SEL_ATK : (canTarget ? new Color(55, 25, 25) : (empty ? EMPTY_BG : CARD_BG));
        Color border  = isSel ? SEL_ATK
                      : (canTarget ? SEL_TGT
                      : (canPlace  ? new Color(100,220,130)
                      : (canSelect ? new Color(100,160,255)
                      : (empty     ? new Color(40,40,62) : accent))));

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(bgColor);
        p.setPreferredSize(new Dimension(108, 90));
        p.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(border, (isSel || canTarget || canPlace) ? 2 : 1, true),
            new EmptyBorder(4, 5, 4, 5)));
        if (canPlace || canSelect || canTarget)
            p.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        if (empty) {
            if (canPlace) {
                JLabel pl = lbl("Place here", Font.ITALIC, 9, new Color(100, 220, 130));
                pl.setAlignmentX(Component.CENTER_ALIGNMENT);
                p.add(Box.createVerticalGlue());
                p.add(pl);
                p.add(Box.createVerticalGlue());
            }
        } else if (card != null) {
            JLabel nameL = lbl(card.getName(), Font.BOLD, 10, isChamp ? CHAMP_CLR : Color.WHITE);
            nameL.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel hpL = lbl("HP " + hp + "/" + card.getHp(), Font.PLAIN, 9,
                              hp <= card.getHp() / 3 + 1 ? new Color(220, 80, 80) : new Color(80, 200, 100));
            hpL.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel atkL = lbl("ATK " + card.getAttack(), Font.PLAIN, 9, new Color(220, 120, 80));
            atkL.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.add(nameL); p.add(hpL); p.add(atkL);

            if (isChamp) {
                Champion ch = (Champion) card;
                JLabel stageL = lbl("Stage " + ch.getStage(), Font.ITALIC, 8, CHAMP_CLR);
                stageL.setAlignmentX(Component.LEFT_ALIGNMENT);
                p.add(stageL);
            }

            if (isMyField && !hasAct) {
                JLabel used = lbl("Used", Font.ITALIC, 8, new Color(100, 100, 120));
                used.setAlignmentX(Component.LEFT_ALIGNMENT);
                p.add(used);
            }
        }

        if (canPlace || canSelect || canTarget) {
            p.addMouseListener(new java.awt.event.MouseAdapter() {
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    BattleState st2 = stRef[0];
                    if (canPlace) {
                        List<String> hand = amP1 ? st2.p1Hand : st2.p2Hand;
                        if (selHand[0] >= hand.size()) return;
                        String id   = hand.get(selHand[0]);
                        Card   c    = cardMap.get(id);
                        int    cost = AbilityResolver.effectiveCost(c != null ? c : new Card(id,"?","?",0,1,0,""), st2, amP1, champLines);
                        int    souls = amP1 ? st2.p1Souls : st2.p2Souls;
                        if (cost > souls) {
                            msg[0] = "Not enough Souls (need " + cost + ", have " + souls + ")";
                            rebuildRef[0].run(); return;
                        }
                        String[] targetRow = isFront ? (amP1 ? st2.p1Front : st2.p2Front)
                                                     : (amP1 ? st2.p1Back  : st2.p2Back);
                        targetRow[idx] = BattleState.makeSlot(id, c != null ? c.getHp() : 1);
                        hand.remove(selHand[0]);
                        // Consume freeplay token if used
                        st2.freeplayCards.remove(id + "_" + (amP1 ? "p1" : "p2"));
                        if (amP1) st2.p1Souls -= cost; else st2.p2Souls -= cost;
                        selHand[0] = -1; msg[0] = "";
                        st2.save(); rebuildRef[0].run();
                    } else if (canSelect) {
                        selField[0] = isSel ? null : posKey;
                        selHand[0] = -1;
                        bypass[0]  = false;
                        msg[0] = selField[0] != null ? "Select a target to attack" : "";
                        rebuildRef[0].run();
                    } else { // canTarget
                        doAttack(stRef[0], cardMap, champLines, amP1, selField[0],
                                 fieldIsP1, isFront, idx,
                                 stRef, selHand, selField, msg, rebuildRef, bypass, user);
                    }
                }
            });
        }
        return p;
    }

    // ── Attack ────────────────────────────────────────────────────────────────

    private static void doAttack(BattleState st, Map<String, Card> cardMap,
                                  Map<String, ChampionLine> champLines,
                                  boolean amP1, String atkKey,
                                  boolean tgtIsP1, boolean tgtFront, int tgtIdx,
                                  BattleState[] stRef, int[] selHand, String[] selField,
                                  String[] msg, Runnable[] rebuildRef, boolean[] bypass,
                                  User user) {
        boolean atkIsP1  = atkKey.startsWith("p1");
        boolean atkFront = atkKey.charAt(2) == 'f';
        int     atkIdx   = Character.getNumericValue(atkKey.charAt(3));

        String[] atkRow = atkFront ? (atkIsP1 ? st.p1Front : st.p2Front) : (atkIsP1 ? st.p1Back : st.p2Back);
        String[] tgtRow = tgtFront ? (tgtIsP1 ? st.p1Front : st.p2Front) : (tgtIsP1 ? st.p1Back : st.p2Back);

        String atkSv = atkRow[atkIdx], tgtSv = tgtRow[tgtIdx];
        if (atkSv == null || atkSv.isEmpty() || tgtSv == null || tgtSv.isEmpty()) return;

        String atkId = BattleState.slotId(atkSv), tgtId = BattleState.slotId(tgtSv);
        Card atkC = cardMap.get(atkId), tgtC = cardMap.get(tgtId);
        if (atkC == null || tgtC == null) return;

        // If bypass was active, consume 1 Scrap now
        if (bypass[0]) {
            String bypassMsg = AbilityResolver.consumeBypassScrap(st, amP1);
            msg[0] = bypassMsg + " ";
        }
        bypass[0] = false;

        // Evolved Shade 2x damage check
        String myChampId = AbilityResolver.currentChampId(st, amP1);
        int dmg = AbilityResolver.effectiveAttack(atkC, myChampId,
                                                   BattleState.posKey(tgtIsP1, tgtFront, tgtIdx), st);

        int newTgtHp = BattleState.slotHp(tgtSv) - dmg;
        st.useAction(atkIsP1, atkFront, atkIdx);
        selField[0] = null;

        if (newTgtHp <= 0) {
            tgtRow[tgtIdx] = "";
            if (tgtIsP1) st.p1Discard.add(tgtId); else st.p2Discard.add(tgtId);

            // Non-champion card death
            if (!(tgtC instanceof Champion)) {
                if (amP1) st.p1SoulCap++; else st.p2SoulCap++;
                String deathPassive = AbilityResolver.onCardDeath(st, tgtIsP1);
                msg[0] += tgtC.getName() + " defeated! " + deathPassive;
            } else {
                // Champion defeated — advance stage or end game
                Champion deadChamp = (Champion) tgtC;
                String lineId = deadChamp.getLineId();
                ChampionLine line = champLines.get(lineId);
                String deathAbilityMsg = AbilityResolver.onChampDeath(st, atkId, !tgtIsP1, cardMap);

                if (line != null && !deadChamp.isFinalStage(line)) {
                    // Advance to next stage
                    Champion next = line.getStageByIndex(deadChamp.getStage()); // stage is 1-based, index is 0-based
                    if (next != null) {
                        tgtRow[tgtIdx] = BattleState.makeSlot(next.getId(), next.getHp());
                        // Remove action used mark for champion slot so new stage can act this turn
                        st.actionsUsed.remove(BattleState.posKey(tgtIsP1, tgtFront, tgtIdx));
                        msg[0] += deadChamp.getName() + " evolved to " + next.getName() + "! " + deathAbilityMsg;
                    }
                } else {
                    // Final stage defeated — game over
                    st.phase  = "finished";
                    st.winner = user.getUsername();
                    st.save(); stRef[0] = st;
                    rebuildRef[0].run(); return;
                }
            }
        } else {
            tgtRow[tgtIdx] = BattleState.makeSlot(tgtId, newTgtHp);
            msg[0] += "Hit " + tgtC.getName() + " for " + dmg + "!";
        }

        // Champion using its attack action ends the turn
        if (atkC instanceof Champion) {
            doEndTurn(amP1, stRef, selHand, selField, msg, rebuildRef);
        } else {
            st.save(); stRef[0] = st;
            rebuildRef[0].run();
        }
    }

    // ── Champion ability ──────────────────────────────────────────────────────

    private static void doChampionAbility(BattleState st, Map<String, Card> cardMap,
                                           Map<String, ChampionLine> champLines,
                                           boolean amP1, BattleState[] stRef,
                                           int[] selHand, String[] selField, String[] msg,
                                           Runnable[] rebuildRef, boolean[] bypass) {
        String champId = AbilityResolver.currentChampId(st, amP1);
        if (champId == null) { msg[0] = "No champion on field."; rebuildRef[0].run(); return; }

        // Check action availability for champion
        if (!st.hasAction(amP1, false, BattleState.CHAMP_SLOT)) {
            msg[0] = "Champion has already acted this turn.";
            rebuildRef[0].run(); return;
        }

        // T-Bot (T3) bypass — toggle bypass mode for next attack
        if ("T3".equals(champId)) {
            if (!AbilityResolver.canBypass(st, amP1)) {
                msg[0] = "Bypass: need at least 1 Scrap token."; rebuildRef[0].run(); return;
            }
            bypass[0] = !bypass[0];
            msg[0] = bypass[0] ? "Bypass active — select a backline target!" : "Bypass cancelled.";
            rebuildRef[0].run();
            return;
        }

        String result = AbilityResolver.activeAbility(st, champId, amP1, cardMap);
        if (result == null) {
            msg[0] = "This champion has a passive ability.";
        } else {
            msg[0] = result;
            // Track ability used for Shade/Evolved Shade
            st.abilityUsedThisTurn.add(BattleState.posKey(amP1, false, BattleState.CHAMP_SLOT));
            // Shade harvest
            String harvestMsg = AbilityResolver.onAbilityUsed(st, amP1);
            if (!harvestMsg.isEmpty()) msg[0] += " " + harvestMsg;
            // Mark champion as having acted — champion ability always ends turn
            st.useAction(amP1, false, BattleState.CHAMP_SLOT);
            st.save(); stRef[0] = st;
            doEndTurn(amP1, stRef, selHand, selField, msg, rebuildRef);
            return;
        }
        rebuildRef[0].run();
    }

    // ── End turn ──────────────────────────────────────────────────────────────

    private static void doEndTurn(boolean amP1, BattleState[] stRef,
                                   int[] selHand, String[] selField,
                                   String[] msg, Runnable[] rebuildRef) {
        BattleState st = stRef[0];
        selHand[0] = -1; selField[0] = null;
        boolean nextIsP1   = !amP1;
        String  nextPlayer = amP1 ? st.player2 : st.player1;
        List<String> nextDeck = nextIsP1 ? st.p1Deck : st.p2Deck;
        List<String> nextHand = nextIsP1 ? st.p1Hand : st.p2Hand;
        if (!nextDeck.isEmpty()) nextHand.add(nextDeck.remove(0));
        if (nextIsP1) st.p1Souls = st.p1SoulCap; else st.p2Souls = st.p2SoulCap;
        st.actionsUsed.clear();
        st.abilityUsedThisTurn.clear();
        st.freeplayCards.clear();
        st.p1ExtraActions = 0;
        st.p2ExtraActions = 0;
        st.currentTurn = nextPlayer;
        st.save(); stRef[0] = st;
        rebuildRef[0].run();
    }

    // ── Hand panel ────────────────────────────────────────────────────────────

    private static JPanel handPanel(List<String> handIds, Map<String, Card> cardMap,
                                     int souls, int soulCap, int deckCount, boolean myTurn,
                                     int[] selHand, String[] selField, String[] msg,
                                     BattleState[] stRef, boolean amP1, Runnable[] rebuildRef,
                                     Map<String, ChampionLine> champLines) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        p.setBackground(new Color(20, 22, 35));
        p.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(new Color(50, 50, 80), 1),
            new EmptyBorder(4, 6, 4, 6)));

        p.add(lbl("Hand (" + handIds.size() + ")  ·  Soul " + souls + "/" + soulCap
                  + "  ·  Deck " + deckCount,
                  Font.BOLD, 12, new Color(160, 160, 190)));

        for (int i = 0; i < handIds.size(); i++) {
            final int fi = i;
            Card c = cardMap.get(handIds.get(i));
            if (c == null) continue;
            int  cost      = AbilityResolver.effectiveCost(c, stRef[0], amP1, champLines);
            boolean canAfford = souls >= cost;
            boolean sel       = selHand[0] == i;
            Color accent = CardViewer.typeColor(c.getType());
            Color bgColor = sel ? SEL_ATK : (canAfford && myTurn ? CARD_BG : EMPTY_BG);
            Color border  = sel ? SEL_ATK : (canAfford && myTurn ? accent : new Color(55, 55, 75));

            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBackground(bgColor);
            card.setPreferredSize(new Dimension(93, 78));
            card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(border, sel ? 2 : 1, true), new EmptyBorder(4, 5, 4, 5)));
            if (canAfford && myTurn) card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            Color nameClr = canAfford && myTurn ? Color.WHITE : new Color(100, 100, 120);
            Color costClr = canAfford ? new Color(200, 160, 60) : new Color(220, 80, 80);

            // Show free tag if freeplay token active
            boolean isFree = stRef[0].freeplayCards.contains(c.getId() + "_" + (amP1 ? "p1" : "p2"));
            String costTxt = isFree ? "FREE" : "Cost:" + cost;

            JLabel n = lbl(c.getName(),                  Font.BOLD,  9, nameClr); n.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel o = lbl(costTxt,                      Font.PLAIN, 9, costClr); o.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel s = lbl("A:" + c.getAttack() + " H:" + c.getHp(), Font.PLAIN, 9, new Color(140,140,165)); s.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(n); card.add(o); card.add(s);

            if (canAfford && myTurn) {
                card.addMouseListener(new java.awt.event.MouseAdapter() {
                    public void mouseClicked(java.awt.event.MouseEvent e) {
                        selField[0] = null;
                        selHand[0] = (selHand[0] == fi) ? -1 : fi;
                        msg[0] = selHand[0] >= 0 ? "Select an empty slot to place this card" : "";
                        rebuildRef[0].run();
                    }
                });
            }
            p.add(card);
        }
        return p;
    }

    // ── Controls ──────────────────────────────────────────────────────────────

    private static JPanel controls(BattleState[] stRef, boolean amP1, boolean myTurn,
                                    int[] selHand, String[] selField, String[] msg,
                                    Runnable[] rebuildRef, boolean[] bypass,
                                    User user, Runnable onComplete,
                                    Map<String, ChampionLine> champLines,
                                    Map<String, Card> cardMap) {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setBackground(BG);
        p.setBorder(new EmptyBorder(4, 0, 0, 0));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);

        if (myTurn) {
            // Champion ability button
            String champId = AbilityResolver.currentChampId(stRef[0], amP1);
            Card champCard = champId != null ? cardMap.get(champId) : null;
            if (champCard instanceof Champion) {
                Champion ch = (Champion) champCard;
                boolean hasAbility = !ch.getAbility().isEmpty();
                boolean champActed = !stRef[0].hasAction(amP1, false, BattleState.CHAMP_SLOT);
                if (hasAbility) {
                    String btnLabel = ch.getName() + ": " + ch.getAbility().split("[-–]")[0].trim();
                    Color  btnColor = bypass[0] ? new Color(255, 160, 60) : new Color(220, 180, 60);
                    JButton abilityBtn = smallBtn(btnLabel, btnColor);
                    abilityBtn.setEnabled(!champActed);
                    abilityBtn.addActionListener(e ->
                        doChampionAbility(stRef[0], cardMap, champLines, amP1,
                                          stRef, selHand, selField, msg, rebuildRef, bypass));
                    btns.add(abilityBtn);
                }
            }

            JButton endBtn = smallBtn("End Turn", new Color(100, 180, 255));
            endBtn.addActionListener(e -> {
                msg[0] = "";
                doEndTurn(amP1, stRef, selHand, selField, msg, rebuildRef);
            });
            btns.add(endBtn);
        }

        JButton forfeit = smallBtn("Forfeit", new Color(220, 80, 80));
        forfeit.addActionListener(e -> {
            BattleState st = stRef[0];
            st.phase  = "finished";
            st.winner = amP1 ? st.player2 : st.player1;
            st.save(); stRef[0] = st;
            rebuildRef[0].run();
        });
        btns.add(forfeit);

        p.add(btns, BorderLayout.EAST);
        return p;
    }

    // ── Result ────────────────────────────────────────────────────────────────

    private static JPanel resultPanel(boolean won, Runnable onComplete) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(BG);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(new Color(35, 35, 52));
        Color accent = won ? new Color(100, 220, 130) : new Color(220, 80, 80);
        card.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(accent, 3, true), new EmptyBorder(30, 60, 30, 60)));

        JLabel title = lbl(won ? "Victory!" : "Defeat", Font.BOLD, 36, accent);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel sub = lbl(won ? "Your champion prevails!" : "Your champion has fallen.",
                         Font.ITALIC, 14, new Color(160, 160, 185));
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);
        JButton btn = MenuScreen.menuButton("Return to Menu", accent);
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.addActionListener(e -> onComplete.run());

        card.add(title);
        card.add(Box.createVerticalStrut(10));
        card.add(sub);
        card.add(Box.createVerticalStrut(24));
        card.add(btn);
        p.add(card);
        return p;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static JLabel lbl(String text, int style, int size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", style, size));
        l.setForeground(color);
        return l;
    }

    private static JButton smallBtn(String text, Color accent) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.BOLD, 13));
        b.setForeground(Color.WHITE);
        b.setBackground(new Color(38, 38, 58));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(accent, 2, true), new EmptyBorder(6, 16, 6, 16)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
}
