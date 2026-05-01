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

        Map<String, Card> cardMap = BattleManager.buildCardMap();
        for (Card c : user.getOwnedCards()) cardMap.putIfAbsent(c.getId(), c);

        BattleState[] stRef   = { BattleState.load(battleId) };
        int[]         selHand = { -1 };
        String[]      selField = { null };
        String[]      msg      = { "" };

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(BG);

        javax.swing.Timer[] timerRef  = { null };
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

            boolean oppIsP1 = !amP1;
            String oppName  = amP1 ? st.player2 : st.player1;
            int oppSouls    = amP1 ? st.p2Souls   : st.p1Souls;
            int oppSoulCap  = amP1 ? st.p2SoulCap : st.p1SoulCap;
            int oppDeck     = (amP1 ? st.p2Deck : st.p1Deck).size();
            int mySouls     = amP1 ? st.p1Souls   : st.p2Souls;
            int mySoulCap   = amP1 ? st.p1SoulCap : st.p2SoulCap;
            int myDeck      = (amP1 ? st.p1Deck : st.p2Deck).size();
            List<String> myHand = new ArrayList<>(amP1 ? st.p1Hand : st.p2Hand);

            wrapper.add(header(oppName, oppSouls, oppSoulCap, oppDeck, myTurn), BorderLayout.NORTH);

            // Field
            JPanel field = new JPanel();
            field.setLayout(new BoxLayout(field, BoxLayout.Y_AXIS));
            field.setBackground(BG);
            field.setBorder(new EmptyBorder(4, 8, 4, 8));

            // Opponent section (their back then front from our view)
            field.add(fieldRow(st, cardMap, oppIsP1, false, amP1, myTurn,
                               selHand, selField, msg, stRef, user, wrapper, battleId,
                               onComplete, rebuildRef, OPP_BG));
            field.add(Box.createVerticalStrut(3));
            field.add(fieldRow(st, cardMap, oppIsP1, true, amP1, myTurn,
                               selHand, selField, msg, stRef, user, wrapper, battleId,
                               onComplete, rebuildRef, OPP_BG));

            // Separator
            JPanel sep = new JPanel();
            sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 4));
            sep.setBackground(new Color(60, 60, 90));
            field.add(Box.createVerticalStrut(5));
            field.add(sep);
            field.add(Box.createVerticalStrut(5));

            // My section (my front then back)
            field.add(fieldRow(st, cardMap, amP1, true, amP1, myTurn,
                               selHand, selField, msg, stRef, user, wrapper, battleId,
                               onComplete, rebuildRef, MY_BG));
            field.add(Box.createVerticalStrut(3));
            field.add(fieldRow(st, cardMap, amP1, false, amP1, myTurn,
                               selHand, selField, msg, stRef, user, wrapper, battleId,
                               onComplete, rebuildRef, MY_BG));

            wrapper.add(field, BorderLayout.CENTER);

            // South: hand + controls
            JPanel south = new JPanel(new BorderLayout(0, 4));
            south.setBackground(BG);
            south.setBorder(new EmptyBorder(4, 8, 8, 8));
            south.add(handPanel(myHand, cardMap, mySouls, mySoulCap, myDeck, myTurn,
                                selHand, selField, msg, stRef, amP1, rebuildRef),
                      BorderLayout.CENTER);
            south.add(controls(stRef, amP1, myTurn, selHand, selField, msg,
                                rebuildRef, user, onComplete),
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
                                  int oppDeck, boolean myTurn) {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setBackground(HDR_BG);
        p.setBorder(new EmptyBorder(8, 14, 8, 14));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        left.setOpaque(false);
        left.add(lbl("Opponent: " + oppName, Font.BOLD, 14, new Color(220, 100, 100)));
        left.add(lbl("Soul " + oppSouls + "/" + oppSoulCap, Font.PLAIN, 12, new Color(200, 160, 80)));
        left.add(lbl("Deck: " + oppDeck, Font.PLAIN, 12, new Color(140, 140, 165)));

        JLabel turn = lbl(myTurn ? "YOUR TURN" : "Opponent's Turn", Font.BOLD, 15,
                          myTurn ? new Color(100, 220, 130) : new Color(220, 100, 100));
        turn.setHorizontalAlignment(SwingConstants.CENTER);

        p.add(left, BorderLayout.WEST);
        p.add(turn, BorderLayout.CENTER);
        return p;
    }

    // ── Field row ─────────────────────────────────────────────────────────────

    private static JPanel fieldRow(BattleState st, Map<String, Card> cardMap,
                                    boolean fieldIsP1, boolean isFront, boolean amP1,
                                    boolean myTurn, int[] selHand, String[] selField,
                                    String[] msg, BattleState[] stRef, User user,
                                    JPanel wrapper, String battleId, Runnable onComplete,
                                    Runnable[] rebuildRef, Color bg) {
        JPanel row = new JPanel(new GridLayout(1, 5, 4, 0));
        row.setBackground(bg);
        row.setBorder(new EmptyBorder(3, 0, 3, 0));
        for (int i = 0; i < 5; i++)
            row.add(slot(st, cardMap, fieldIsP1, isFront, i, amP1, myTurn,
                         selHand, selField, msg, stRef, user, wrapper, battleId,
                         onComplete, rebuildRef));
        return row;
    }

    // ── Slot ──────────────────────────────────────────────────────────────────

    private static JPanel slot(BattleState st, Map<String, Card> cardMap,
                                boolean fieldIsP1, boolean isFront, int idx,
                                boolean amP1, boolean myTurn,
                                int[] selHand, String[] selField, String[] msg,
                                BattleState[] stRef, User user, JPanel wrapper,
                                String battleId, Runnable onComplete, Runnable[] rebuildRef) {

        String[] row = isFront ? (fieldIsP1 ? st.p1Front : st.p2Front)
                               : (fieldIsP1 ? st.p1Back  : st.p2Back);
        String sv      = row[idx];
        boolean empty  = sv == null || sv.isEmpty();
        String  cardId = empty ? null : BattleState.slotId(sv);
        int     hp     = empty ? 0    : BattleState.slotHp(sv);
        Card    card   = (cardId != null) ? cardMap.get(cardId) : null;
        boolean isChamp   = BattleState.CHAMP_ID.equals(cardId);
        boolean isMyField = fieldIsP1 == amP1;
        String  posKey    = BattleState.posKey(fieldIsP1, isFront, idx);
        boolean hasAct    = !empty && st.hasAction(fieldIsP1, isFront, idx);
        boolean isSel     = posKey.equals(selField[0]);

        boolean canPlace  = isMyField && empty  && myTurn && selHand[0] >= 0;
        boolean canSelect = isMyField && !empty && myTurn && hasAct;
        boolean canTarget = !isMyField && !empty && myTurn && selField[0] != null
                             && st.isTargetable(fieldIsP1, isFront, idx);

        Color accent  = isChamp ? CHAMP_CLR : (card != null ? CardViewer.typeColor(card.getType()) : new Color(80,80,110));
        Color bg      = isSel ? SEL_ATK : (canTarget ? new Color(55, 25, 25) : (empty ? EMPTY_BG : CARD_BG));
        Color border  = isSel ? SEL_ATK : (canTarget ? SEL_TGT : (canPlace ? new Color(100,220,130) : (canSelect ? new Color(100,160,255) : (empty ? new Color(40,40,62) : accent))));

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(bg);
        p.setPreferredSize(new Dimension(108, 86));
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
                              hp <= card.getHp() / 3 ? new Color(220, 80, 80) : new Color(80, 200, 100));
            hpL.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel atkL = lbl("ATK " + card.getAttack(), Font.PLAIN, 9, new Color(220, 120, 80));
            atkL.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.add(nameL); p.add(hpL); p.add(atkL);
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
                        String id = hand.get(selHand[0]);
                        Card c = cardMap.get(id);
                        int cost  = c != null ? c.getCost() : 0;
                        int souls = amP1 ? st2.p1Souls : st2.p2Souls;
                        if (cost > souls) {
                            msg[0] = "Not enough Souls (need " + cost + ", have " + souls + ")";
                            rebuildRef[0].run(); return;
                        }
                        String[] targetRow = isFront ? (amP1 ? st2.p1Front : st2.p2Front)
                                                     : (amP1 ? st2.p1Back  : st2.p2Back);
                        targetRow[idx] = BattleState.makeSlot(id, c != null ? c.getHp() : 1);
                        hand.remove(selHand[0]);
                        if (amP1) st2.p1Souls -= cost; else st2.p2Souls -= cost;
                        selHand[0] = -1; msg[0] = "";
                        st2.save(); rebuildRef[0].run();
                    } else if (canSelect) {
                        selField[0] = isSel ? null : posKey;
                        selHand[0] = -1;
                        msg[0] = selField[0] != null ? "Select a target to attack" : "";
                        rebuildRef[0].run();
                    } else { // canTarget
                        doAttack(stRef[0], cardMap, amP1, selField[0],
                                 fieldIsP1, isFront, idx,
                                 stRef, selHand, selField, msg, rebuildRef, user);
                    }
                }
            });
        }
        return p;
    }

    // ── Attack ────────────────────────────────────────────────────────────────

    private static void doAttack(BattleState st, Map<String, Card> cardMap,
                                  boolean amP1, String atkKey,
                                  boolean tgtIsP1, boolean tgtFront, int tgtIdx,
                                  BattleState[] stRef, int[] selHand, String[] selField,
                                  String[] msg, Runnable[] rebuildRef, User user) {
        boolean atkIsP1 = atkKey.startsWith("p1");
        boolean atkFront = atkKey.charAt(2) == 'f';
        int atkIdx = Character.getNumericValue(atkKey.charAt(3));

        String[] atkRow = atkFront ? (atkIsP1 ? st.p1Front : st.p2Front) : (atkIsP1 ? st.p1Back : st.p2Back);
        String[] tgtRow = tgtFront ? (tgtIsP1 ? st.p1Front : st.p2Front) : (tgtIsP1 ? st.p1Back : st.p2Back);

        String atkSv = atkRow[atkIdx], tgtSv = tgtRow[tgtIdx];
        if (atkSv == null || atkSv.isEmpty() || tgtSv == null || tgtSv.isEmpty()) return;

        String atkId = BattleState.slotId(atkSv), tgtId = BattleState.slotId(tgtSv);
        Card atkC = cardMap.get(atkId), tgtC = cardMap.get(tgtId);
        if (atkC == null || tgtC == null) return;

        int newTgtHp = BattleState.slotHp(tgtSv) - atkC.getAttack();
        st.useAction(atkIsP1, atkFront, atkIdx);
        selField[0] = null;

        if (newTgtHp <= 0) {
            tgtRow[tgtIdx] = "";
            if (tgtIsP1) st.p1Discard.add(tgtId); else st.p2Discard.add(tgtId);
            if (amP1) st.p1SoulCap++; else st.p2SoulCap++;
            msg[0] = tgtC.getName() + " defeated!";
            if (BattleState.CHAMP_ID.equals(tgtId)) {
                st.phase = "finished";
                st.winner = user.getUsername();
                st.save(); stRef[0] = st;
                rebuildRef[0].run(); return;
            }
        } else {
            tgtRow[tgtIdx] = BattleState.makeSlot(tgtId, newTgtHp);
            msg[0] = "Hit " + tgtC.getName() + " for " + atkC.getAttack() + "!";
        }

        // Champion using action ends the turn
        if (BattleState.CHAMP_ID.equals(atkId)) {
            doEndTurn(amP1, stRef, selHand, selField, msg, rebuildRef);
        } else {
            st.save(); stRef[0] = st;
            rebuildRef[0].run();
        }
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
        st.currentTurn = nextPlayer;
        msg[0] = "";
        st.save(); stRef[0] = st;
        rebuildRef[0].run();
    }

    // ── Hand ──────────────────────────────────────────────────────────────────

    private static JPanel handPanel(List<String> handIds, Map<String, Card> cardMap,
                                     int souls, int soulCap, int deckCount, boolean myTurn,
                                     int[] selHand, String[] selField, String[] msg,
                                     BattleState[] stRef, boolean amP1, Runnable[] rebuildRef) {
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
            boolean canAfford = souls >= c.getCost();
            boolean sel = selHand[0] == i;
            Color accent = CardViewer.typeColor(c.getType());
            Color bg     = sel ? SEL_ATK : (canAfford && myTurn ? CARD_BG : EMPTY_BG);
            Color border = sel ? SEL_ATK : (canAfford && myTurn ? accent : new Color(55, 55, 75));

            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBackground(bg);
            card.setPreferredSize(new Dimension(93, 78));
            card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(border, sel ? 2 : 1, true), new EmptyBorder(4, 5, 4, 5)));
            if (canAfford && myTurn) card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            Color nameClr = canAfford && myTurn ? Color.WHITE : new Color(100, 100, 120);
            Color costClr = canAfford ? new Color(200, 160, 60) : new Color(220, 80, 80);

            JLabel n = lbl(c.getName(),    Font.BOLD,  9, nameClr); n.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel o = lbl("Cost:" + c.getCost(), Font.PLAIN, 9, costClr); o.setAlignmentX(Component.LEFT_ALIGNMENT);
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
                                    Runnable[] rebuildRef, User user, Runnable onComplete) {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setBackground(BG);
        p.setBorder(new EmptyBorder(4, 0, 0, 0));

        JLabel statusL = lbl(msg[0], Font.ITALIC, 12, new Color(200, 200, 100));
        statusL.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);

        if (myTurn) {
            JButton endBtn = smallBtn("End Turn", new Color(100, 180, 255));
            endBtn.addActionListener(e -> doEndTurn(amP1, stRef, selHand, selField, msg, rebuildRef));
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

        p.add(statusL, BorderLayout.CENTER);
        p.add(btns,    BorderLayout.EAST);
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
