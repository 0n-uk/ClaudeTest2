import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.*;

/**
 * The battle screen: the board, your hand and the buttons, and what happens when you click them.
 * The rules themselves live in {@link BattleRules}.
 */
public class BattleScreen {

    private static final Color BG        = UI.BG;
    private static final Color HDR_BG    = new Color(15, 15, 25);
    private static final Color SIDE_BG   = new Color(18, 18, 28);
    private static final Color LINE      = new Color(60, 60, 90);
    private static final Color OPP_BG    = new Color(45, 25, 25);
    private static final Color MY_BG     = new Color(25, 42, 28);
    private static final Color EMPTY_BG  = new Color(28, 28, 45);
    private static final Color SLOT_BG   = new Color(42, 42, 65);
    private static final Color EMPTY_LINE = new Color(85, 85, 120);
    private static final Color SEL_ATK   = new Color(80, 160, 255);
    private static final Color SEL_TGT   = new Color(220, 60,  60);
    private static final Color SEL_ABL   = new Color(220, 180, 60);
    private static final Color CHAMP_CLR = new Color(220, 180, 60);
    private static final Color OPP_NAME  = new Color(230, 110, 110);
    private static final Color MY_NAME   = new Color(110, 220, 140);
    private static final Color SOUL_CLR  = new Color(210, 170, 90);
    private static final Color MUTED     = new Color(150, 150, 175);
    private static final Color MSG_CLR   = new Color(220, 200, 100);
    private static final Color PAPER     = new Color(245, 242, 235);   // light rows, so the hand-drawn symbols show

    private static final int SIDE_W   = 240;   // left panel when shown
    private static final int BOTTOM_H = 140;   // your hand, info and buttons
    private static final int INFO_W   = 150;   // your info on the left and the buttons on the right, so the hand is centred

    // Left panel views
    private static final String LOG   = "Log";
    private static final String DECK  = "Deck";
    private static final String GRAVE = "Graveyard";

    /** The abilities that take more than one click, and which click they are waiting for. */
    private enum Step { SCRAP_SELECT, BOT_TARGET, ECHO_COPY_SELECT, MIMIC_SELECT, COPY_TARGET_SELECT }

    /** What clicking a board slot would do right now, and how the slot is highlighted for it. */
    private enum SlotMode {
        NONE      (null,                    null,                      false),
        PLACE     (null,                    new Color(100, 220, 130),  true),
        SELECT    (null,                    new Color(100, 160, 255),  false),
        SELECTED  (SEL_ATK,                 SEL_ATK,                   true),
        ATTACK    (new Color(55, 25, 25),   SEL_TGT,                   true),
        ABILITY   (new Color(55, 50, 20),   SEL_ABL,                   true),
        BOT       (new Color(55, 50, 20),   SEL_ABL,                   true),
        SCRAP     (new Color(50, 45, 20),   new Color(200, 160, 40),   true),
        SCRAP_ON  (new Color(80, 60, 20),   new Color(255, 200, 60),   true),
        ECHO      (new Color(20, 55, 55),   new Color(60, 200, 200),   true),
        MIMIC     (new Color(55, 20, 55),   new Color(180, 80, 220),   true),
        COPY      (new Color(55, 40, 10),   new Color(220, 160, 60),   true);

        final Color   bg;      // null keeps the normal slot colour
        final Color   border;  // null keeps the card's own accent
        final boolean thick;

        SlotMode(Color bg, Color border, boolean thick) {
            this.bg = bg;
            this.border = border;
            this.thick = thick;
        }
    }

    /** Everything one open battle screen needs: the battle, who is playing, and what is selected. */
    private static final class BattleView {
        final User     user;
        final String   battleId;
        final Runnable onComplete;
        final Map<String, Card>         cardMap    = GameData.cardMap();
        final Map<String, ChampionLine> champLines = GameData.championLines();

        final JPanel    wrapper = new JPanel(new BorderLayout());
        final JTextArea logArea = new JTextArea();
        JScrollPane     logScroll;
        String          lastLogged = "";
        javax.swing.Timer pollTimer, heartbeatTimer;

        BattleState st;
        String      lastFileText;      // battle file as last read; null forces a reload on the next tick

        // Selection
        int     selHand = -1;          // index in hand of the card being placed
        String  selField;              // posKey of the card about to attack or use its ability
        String  abilitySource;         // posKey of a card waiting for its ability target
        String  abilityTgtType;        // card type that ability must target (null = any)
        int     abilityChoice;         // Upgrade Bot: 0 = ATK, 1 = HP
        boolean bypass;                // T3's Bypass is switched on
        String  msg = "";

        // Left panel
        boolean sideHidden;
        String  sideTab = LOG;

        // Abilities that take several clicks (Furnace Bot, Iron Tusks Bot, Echo Spirit, Mimic)
        Step   step;
        String stepCard;               // posKey of the card that started it
        String copiedCard;             // card id whose ability Echo or Mimic is copying
        final List<String> scrapSelected = new ArrayList<>();

        BattleView(User user, String battleId, Runnable onComplete) {
            this.user = user;
            this.battleId = battleId;
            this.onComplete = onComplete;
        }

        boolean     amP1()   { return user.getUsername().equals(st.player1); }
        boolean     myTurn() { return user.getUsername().equals(st.currentTurn); }
        BattleRules rules()  { return new BattleRules(st, cardMap, champLines); }

        void clearAbilityMode() {
            abilitySource  = null;
            abilityTgtType = null;
            step           = null;
            stepCard       = null;
            copiedCard     = null;
            scrapSelected.clear();
        }

        /** Redraws now, and re-reads the battle file on the next tick. */
        void refresh() {
            lastFileText = null;
            rebuild(this);
        }

        /** Shows the result of a rules action: saves if the battle changed and ends the turn if needed. */
        void apply(BattleRules.Outcome o) {
            if (o == null) { refresh(); return; }
            if (o.log != null) msg = o.log;
            if (o.changed) {
                selHand  = -1;
                selField = null;
                clearAbilityMode();
                if (o.endsTurn) { endTurn(); return; }
                st.save();
            }
            refresh();
        }

        void endTurn() {
            msg = rules().endTurn(amP1(), msg);
            selHand  = -1;
            selField = null;
            clearAbilityMode();
            st.save();
            refresh();
        }
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public static JPanel buildPanel(User user, String battleId, Runnable onComplete) {
        BattleView v = new BattleView(user, battleId, onComplete);
        for (Card c : user.getOwnedCards()) v.cardMap.putIfAbsent(c.getId(), c);
        v.st = BattleState.load(battleId);
        v.lastFileText = BattleState.readRaw(battleId);
        v.wrapper.setBackground(BG);
        v.logScroll = battleLog(v.logArea);

        MusicPlayer.playBattle();

        // Heartbeat: tell the opponent we are still here
        BattleManager.writeHeartbeat(user.getUsername());
        v.heartbeatTimer = new javax.swing.Timer(2000, e -> BattleManager.writeHeartbeat(user.getUsername()));
        v.heartbeatTimer.start();

        v.pollTimer = new javax.swing.Timer(600, e -> poll(v));
        v.pollTimer.start();

        rebuild(v);
        return v.wrapper;
    }

    /** Every 600 ms: check the opponent is still connected, and redraw only if the battle file changed. */
    private static void poll(BattleView v) {
        if (v.st != null && "active".equals(v.st.phase)) {
            String oppName = v.st.playerName(!v.amP1());
            if (!BattleManager.isAlive(oppName)) {
                BattleManager.forfeitBattle(v.battleId, oppName);
                v.msg = oppName + " disconnected — you win!";
                v.lastFileText = null;
            }
        }
        String text = BattleState.readRaw(v.battleId);
        if (text != null && text.equals(v.lastFileText)) return;
        BattleState fresh = BattleState.load(v.battleId);
        if (fresh != null) v.st = fresh;
        v.lastFileText = text;
        rebuild(v);
    }

    private static JScrollPane battleLog(JTextArea logArea) {
        logArea.setEditable(false);
        logArea.setBackground(new Color(18, 18, 28));
        logArea.setForeground(new Color(200, 200, 220));
        logArea.setFont(font(Font.PLAIN, 11));
        logArea.setWrapStyleWord(true);
        logArea.setLineWrap(true);
        logArea.setMargin(new Insets(6, 8, 6, 8));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(new LineBorder(LINE, 1));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private static void rebuild(BattleView v) {
        BattleState st = v.st;
        if (st == null) return;
        if (!v.msg.isEmpty() && !v.msg.equals(v.lastLogged)) {
            v.logArea.append(v.msg + "\n");
            v.lastLogged = v.msg;
            v.logArea.setCaretPosition(v.logArea.getDocument().getLength());
        }

        JPanel wrapper = v.wrapper;
        wrapper.removeAll();

        if ("finished".equals(st.phase)) {
            v.pollTimer.stop();
            v.heartbeatTimer.stop();
            BattleManager.removeHeartbeat(v.user.getUsername());
            MusicPlayer.stop();
            wrapper.add(resultPanel(v.user.getUsername().equals(st.winner), v.onComplete), BorderLayout.CENTER);
            wrapper.revalidate();
            wrapper.repaint();
            return;
        }

        JPanel middle = new JPanel(new BorderLayout());
        middle.setBackground(BG);
        middle.add(board(v),       BorderLayout.CENTER);
        middle.add(actionStrip(v), BorderLayout.SOUTH);

        wrapper.add(opponentBar(v), BorderLayout.NORTH);
        wrapper.add(sidePanel(v),   BorderLayout.WEST);
        wrapper.add(middle,         BorderLayout.CENTER);
        wrapper.add(playerBar(v),   BorderLayout.SOUTH);

        wrapper.revalidate();
        wrapper.repaint();
    }

    // ── Top: the opponent ─────────────────────────────────────────────────────

    /** The opponent's name, soul and deck size, across the top. */
    private static JPanel opponentBar(BattleView v) {
        boolean opp = !v.amP1();
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 22, 0));
        p.setBackground(HDR_BG);
        p.setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, LINE), new EmptyBorder(8, 14, 8, 14)));
        p.add(lbl(v.st.playerName(opp), font(Font.BOLD, 16), OPP_NAME));
        p.add(lbl("Soul " + v.st.souls(opp) + "/" + v.st.soulCap(opp), font(Font.BOLD, 13), SOUL_CLR));
        p.add(lbl("Deck " + v.st.deck(opp).size(), font(Font.BOLD, 13), MUTED));
        return p;
    }

    // ── Left: battle log, deck and graveyard ──────────────────────────────────

    /** The left panel: buttons on top switch between the log, your deck and the graveyard; the arrow hides it. */
    private static JPanel sidePanel(BattleView v) {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setBackground(SIDE_BG);
        p.setBorder(new CompoundBorder(new MatteBorder(0, 0, 0, 1, LINE), new EmptyBorder(6, 6, 6, 6)));

        JButton arrow = UI.button(v.sideHidden ? "▶" : "◀", LINE, 12, Color.WHITE, new Insets(4, 6, 4, 6));
        arrow.setToolTipText(v.sideHidden ? "Show the battle log" : "Hide this panel");
        arrow.addActionListener(e -> {
            v.sideHidden = !v.sideHidden;
            rebuild(v);
        });
        if (v.sideHidden) {
            p.add(arrow, BorderLayout.NORTH);
            return p;
        }
        p.setPreferredSize(new Dimension(SIDE_W, 0));

        JPanel tabs = new JPanel(new GridLayout(1, 3, 4, 0));
        tabs.setOpaque(false);
        tabs.add(tabButton(v, LOG));
        tabs.add(tabButton(v, DECK));
        tabs.add(tabButton(v, GRAVE));
        JPanel top = new JPanel(new BorderLayout(4, 0));
        top.setOpaque(false);
        top.add(tabs,  BorderLayout.CENTER);
        top.add(arrow, BorderLayout.EAST);
        p.add(top, BorderLayout.NORTH);

        boolean amP1 = v.amP1();
        switch (v.sideTab) {
            case DECK: {
                JPanel list = listPanel();
                list.add(sectionLabel("Your deck: " + v.st.deck(amP1).size() + " cards"));
                list.add(sectionLabel("(sorted by cost, not draw order)"));
                addCardRows(v, list, v.st.deck(amP1), "Your deck is empty.");
                p.add(scroll(list), BorderLayout.CENTER);
                break;
            }
            case GRAVE: {
                JPanel list = listPanel();
                list.add(sectionLabel("Yours (" + v.st.discard(amP1).size() + ")"));
                addCardRows(v, list, v.st.discard(amP1), "Nothing yet.");
                list.add(Box.createVerticalStrut(10));
                list.add(sectionLabel(v.st.playerName(!amP1) + "'s (" + v.st.discard(!amP1).size() + ")"));
                addCardRows(v, list, v.st.discard(!amP1), "Nothing yet.");
                p.add(scroll(list), BorderLayout.CENTER);
                break;
            }
            default:
                p.add(v.logScroll, BorderLayout.CENTER);
        }
        return p;
    }

    private static JButton tabButton(BattleView v, String name) {
        boolean on = name.equals(v.sideTab);
        JButton b = UI.button(name, on ? SEL_ATK : LINE, 11, on ? Color.WHITE : MUTED, new Insets(4, 0, 4, 0));
        b.addActionListener(e -> {
            v.sideTab = name;
            rebuild(v);
        });
        return b;
    }

    /** One row per card name with how many there are, cheapest first. Clicking a row shows the card's ability. */
    private static void addCardRows(BattleView v, JPanel list, List<String> ids, String emptyText) {
        if (ids.isEmpty()) {
            list.add(sectionLabel(emptyText));
            return;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String id : ids) counts.merge(id, 1, Integer::sum);
        List<String> order = new ArrayList<>(counts.keySet());
        order.sort(Comparator.comparingInt((String id) -> {
                                 Card c = v.cardMap.get(id);
                                 return c != null ? c.getCost() : 99;
                             })
                             .thenComparing(id -> {
                                 Card c = v.cardMap.get(id);
                                 return c != null ? c.getName() : id;
                             }));

        for (String id : order) {
            Card c = v.cardMap.get(id);
            int  n = counts.get(id);
            JPanel row = new JPanel(new BorderLayout(6, 0));
            row.setBackground(PAPER);
            row.setBorder(new CompoundBorder(new MatteBorder(0, 0, 3, 0, SIDE_BG), new EmptyBorder(3, 5, 3, 7)));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.add(new JLabel(Images.typeSymbol(c != null ? c.getType() : "", 18, 18)), BorderLayout.WEST);
            row.add(lbl((c != null ? c.getName() : id) + (n > 1 ? "  ×" + n : ""),
                        font(Font.BOLD, 12), UI.INK), BorderLayout.CENTER);
            if (c != null) {
                row.add(lbl(String.valueOf(c.getCost()), font(Font.BOLD, 12), new Color(50, 100, 190)), BorderLayout.EAST);
                row.setToolTipText("Click to read " + c.getName() + "'s ability");
                row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                row.addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) {
                        CardRenderer.showAbilityPopup(row, c.getName(),
                                c.getAbility().isEmpty() ? "No ability." : c.getAbility());
                    }
                });
            }
            list.add(row);
        }
    }

    private static JPanel listPanel() {
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(SIDE_BG);
        list.setBorder(new EmptyBorder(4, 4, 4, 4));
        return list;
    }

    private static JLabel sectionLabel(String text) {
        JLabel l = lbl(text, font(Font.BOLD, 12), MUTED);
        l.setBorder(new EmptyBorder(2, 0, 4, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    /** Scrolls a list; the list keeps its natural height at the top instead of being stretched. */
    private static JScrollPane scroll(JPanel list) {
        JPanel holder = new JPanel(new BorderLayout());
        holder.setBackground(SIDE_BG);
        holder.add(list, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(holder, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                         ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(new LineBorder(LINE, 1));
        sp.getViewport().setBackground(SIDE_BG);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    // ── Centre: the board ─────────────────────────────────────────────────────

    /**
     * The 4 × 5 grid of slots: the opponent's back and front rows on top, yours below.
     * Every slot is the same square, as big as the space allows, and the grid is centred.
     */
    private static JPanel board(BattleView v) {
        boolean amP1 = v.amP1();
        JPanel p = new JPanel(null) {
            static final int PAD = 10, GAP = 6, MID = 18;   // MID: the extra gap between the two sides

            private int side() {
                return Math.max(10, Math.min((getWidth()  - 2 * PAD - 4 * GAP) / 5,
                                             (getHeight() - 2 * PAD - 3 * GAP - MID) / 4));
            }
            private int x0() { return (getWidth()  - (5 * side() + 4 * GAP)) / 2; }
            private int y0() { return (getHeight() - (4 * side() + 3 * GAP + MID)) / 2; }

            @Override public void doLayout() {
                int s = side();
                for (int i = 0; i < getComponentCount(); i++) {
                    int row = i / 5, col = i % 5;
                    getComponent(i).setBounds(x0() + col * (s + GAP),
                                              y0() + row * (s + GAP) + (row >= 2 ? MID : 0), s, s);
                }
            }

            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int s = side(), w = 5 * s + 4 * GAP + 16, h = 2 * s + GAP + 12;
                int x = x0() - 8, y = y0() - 6;
                g2.setColor(OPP_BG);
                g2.fillRoundRect(x, y, w, h, 16, 16);
                g2.setColor(MY_BG);
                g2.fillRoundRect(x, y + h + MID - 12, w, h, 16, 16);
                g2.dispose();
            }
        };
        p.setBackground(BG);
        boolean[][] rows = { { !amP1, false }, { !amP1, true }, { amP1, true }, { amP1, false } };
        for (boolean[] r : rows)
            for (int i = 0; i < 5; i++) p.add(slot(v, r[0], r[1], i));
        return p;
    }

    private static Slot slot(BattleView v, boolean fieldIsP1, boolean isFront, int idx) {
        BattleState st  = v.st;
        String  sv      = st.getRow(fieldIsP1, isFront)[idx];
        boolean empty   = sv == null || sv.isEmpty();
        String  cardId  = empty ? null : BattleState.slotId(sv);
        Card    card    = cardId != null ? v.cardMap.get(cardId) : null;
        boolean isChamp = card instanceof Champion;
        String  posKey  = BattleState.posKey(fieldIsP1, isFront, idx);
        SlotMode mode   = slotMode(v, fieldIsP1, isFront, idx, cardId, card);

        Color accent  = isChamp ? CHAMP_CLR
                      : AbilityResolver.SCRAP_ID.equals(cardId) ? new Color(140, 140, 160)
                      : card != null ? CardRenderer.typeColor(card.getType()) : EMPTY_LINE;
        Color fill    = mode.bg     != null ? mode.bg     : (empty ? EMPTY_BG : SLOT_BG);
        Color outline = mode.border != null ? mode.border : accent;
        int   stroke  = mode.thick ? 3 : (card != null ? 2 : 1);

        JComponent shown = null;
        if (card != null) {
            int atkBonus = st.fieldAtkBonus.getOrDefault(posKey, 0);
            String stage = isChamp ? "S" + ((Champion) card).getStage() : "";
            shown = CardRenderer.buildBattleCard(card, BattleState.slotHp(sv), card.getAttack() + atkBonus,
                                                 atkBonus, isChamp, stage);
        }
        Slot p = new Slot(shown, fill, outline, stroke);
        if (empty && mode == SlotMode.PLACE) p.hint = "Place here";
        if (card != null) statusBadges(v, p, fieldIsP1, isFront, idx, posKey);
        else p.badge(st.sealedSlots.containsKey(posKey), "Sealed(" + st.sealedSlots.get(posKey) + ")", new Color(230, 90, 90));

        if (mode != SlotMode.NONE) {
            p.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            p.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    onSlotClick(v, mode, fieldIsP1, isFront, idx, cardId, card);
                }
            });
        }
        return p;
    }

    /** Status effects, shown as small labels over the card. */
    private static void statusBadges(BattleView v, Slot slot, boolean fieldIsP1, boolean isFront, int idx, String posKey) {
        BattleState st = v.st;
        int txTurns = st.transformCounters.getOrDefault(posKey, 0);
        slot.badge(st.isFrozen(posKey),                       "Frozen(" + st.frozenCards.get(posKey) + ")",  new Color(100, 200, 255));
        slot.badge(st.burnedCards.containsKey(posKey),        "Burned",                                      new Color(255, 130, 50));
        slot.badge(st.poisonedCards.contains(posKey),         "Poisoned",                                    new Color(140, 200, 80));
        slot.badge(st.sporedCards.containsKey(posKey),        "Spored(" + st.sporedCards.get(posKey) + ")",  new Color(180, 140, 255));
        slot.badge(st.decayedCards.containsKey(posKey),       "Decay(" + st.decayedCards.get(posKey) + ")",  new Color(200, 140, 60));
        slot.badge(st.sealedSlots.containsKey(posKey),        "Sealed(" + st.sealedSlots.get(posKey) + ")",  new Color(230, 90, 90));
        slot.badge(st.focusedCards.contains(posKey),          "Focus!",                                      new Color(255, 220, 80));
        slot.badge(txTurns > 0,                               "→" + txTurns + "t",                           new Color(160, 220, 100));
        slot.badge(st.fieldLockedCards.contains(posKey),      "Locked",                                      new Color(200, 160, 60));
        slot.badge(!isFront && st.isShieldedByGreatEnt(fieldIsP1, idx), "Shielded",                          new Color(100, 200, 100));
        slot.badge(fieldIsP1 == v.amP1() && !st.hasAction(fieldIsP1, isFront, idx), "Used",                  new Color(170, 170, 190));
    }

    /** Works out what clicking this slot would do, given the current selection. */
    private static SlotMode slotMode(BattleView v, boolean fieldIsP1, boolean isFront, int idx,
                                     String cardId, Card card) {
        BattleState st = v.st;
        boolean empty  = cardId == null;
        boolean mine   = fieldIsP1 == v.amP1();
        String  posKey = BattleState.posKey(fieldIsP1, isFront, idx);
        if (!v.myTurn()) return posKey.equals(v.selField) ? SlotMode.SELECTED : SlotMode.NONE;

        if (v.step != null) {
            if (empty) return SlotMode.NONE;
            switch (v.step) {
                case SCRAP_SELECT:
                    if (!mine || !AbilityResolver.SCRAP_ID.equals(cardId)) return SlotMode.NONE;
                    return v.scrapSelected.contains(posKey) ? SlotMode.SCRAP_ON : SlotMode.SCRAP;
                case BOT_TARGET:
                    return mine && card != null && "bot".equalsIgnoreCase(card.getType()) ? SlotMode.BOT : SlotMode.NONE;
                case ECHO_COPY_SELECT:
                    return mine && BattleRules.isCopyable(cardId) ? SlotMode.ECHO : SlotMode.NONE;
                case MIMIC_SELECT:
                    return !mine && BattleRules.isCopyable(cardId) ? SlotMode.MIMIC : SlotMode.NONE;
                case COPY_TARGET_SELECT:
                    boolean enemy = "enemy".equals(AbilityResolver.TARGET_SIDE.get(v.copiedCard));
                    return canAbilityTarget(st, card, mine, enemy, AbilityResolver.TARGET_TYPE.get(v.copiedCard),
                                            fieldIsP1, isFront, idx) ? SlotMode.COPY : SlotMode.NONE;
            }
        }

        if (v.abilitySource != null) {
            String srcId = st.cardIdAt(v.abilitySource);
            boolean ok = AbilityResolver.TARGETS_EMPTY_SLOT.contains(srcId)
                    ? empty && !mine
                    : canAbilityTarget(st, card, mine, "enemy".equals(AbilityResolver.TARGET_SIDE.get(srcId)),
                                       v.abilityTgtType, fieldIsP1, isFront, idx);
            return ok ? SlotMode.ABILITY : SlotMode.NONE;
        }

        if (mine) {
            if (empty) return v.selHand >= 0 ? SlotMode.PLACE : SlotMode.NONE;
            if (posKey.equals(v.selField)) return SlotMode.SELECTED;
            boolean canSelect = st.hasAction(fieldIsP1, isFront, idx) && !st.isFrozen(posKey) && v.selHand < 0;
            return canSelect ? SlotMode.SELECT : SlotMode.NONE;
        }

        if (empty || v.selField == null) return SlotMode.NONE;
        String atkId = st.cardIdAt(v.selField);
        // Bat Eye: cannot be targeted by enemy frontline cards
        if (CardIds.BAT_EYE.equals(cardId) && BattleState.posKeyIsFront(v.selField)) return SlotMode.NONE;
        boolean ok;
        if (CardIds.CATAPULT.equals(atkId))
            ok = !isFront && st.isTargetableBypass(fieldIsP1, isFront, idx);   // backline only, over the frontline
        else if (v.bypass || CardIds.DREAM_WANDERER.equals(atkId))
            ok = st.isTargetableBypass(fieldIsP1, isFront, idx);               // may reach the backline
        else
            ok = st.isTargetable(fieldIsP1, isFront, idx);
        return ok ? SlotMode.ATTACK : SlotMode.NONE;
    }

    /** Whether an ability (own or copied) may target this card. */
    private static boolean canAbilityTarget(BattleState st, Card card, boolean mine, boolean targetsEnemy,
                                            String requiredType, boolean fieldIsP1, boolean isFront, int idx) {
        if (card == null || mine == targetsEnemy) return false;
        if (!mine && AbilityResolver.isImmuneToAbilities(fieldIsP1, st)) return false;   // Sovereign
        if (!mine && !isFront && st.isShieldedByGreatEnt(fieldIsP1, idx)) return false;
        return requiredType == null || requiredType.equals(card.getType().toLowerCase());
    }

    private static void onSlotClick(BattleView v, SlotMode mode, boolean fieldIsP1, boolean isFront, int idx,
                                    String cardId, Card card) {
        boolean amP1   = v.amP1();
        String  posKey = BattleState.posKey(fieldIsP1, isFront, idx);
        switch (mode) {
            case PLACE:
                v.apply(v.rules().place(amP1, v.selHand, isFront, idx));
                break;
            case SELECT:
            case SELECTED:
                v.selField = posKey.equals(v.selField) ? null : posKey;
                v.selHand  = -1;
                v.bypass   = false;
                v.msg = v.selField != null ? "Select a target or use ability" : "";
                v.refresh();
                break;
            case ATTACK: {
                boolean bypass = v.bypass;
                v.bypass = false;
                v.apply(v.rules().attack(amP1, v.selField, fieldIsP1, isFront, idx, bypass));
                break;
            }
            case ABILITY: {
                BattleRules.Outcome o = v.rules().targetedAbility(v.abilitySource, amP1, fieldIsP1, isFront, idx,
                                                                  v.abilityChoice);
                v.clearAbilityMode();
                v.selField = null;
                v.apply(o);
                break;
            }
            case SCRAP:
            case SCRAP_ON:
                if (!v.scrapSelected.remove(posKey)) v.scrapSelected.add(posKey);
                v.msg = v.scrapSelected.size() + " Scrap selected. Press Done when ready.";
                v.refresh();
                break;
            case BOT:
                v.apply(v.rules().smelt(v.stepCard, amP1, v.scrapSelected, isFront, idx));
                break;
            case ECHO:
            case MIMIC: {
                boolean isEcho = mode == SlotMode.ECHO;
                v.copiedCard = cardId;
                if ("active".equals(AbilityResolver.abilityType(cardId))) {
                    v.apply(v.rules().copiedActive(v.stepCard, amP1, cardId, isEcho));
                } else {
                    v.step = Step.COPY_TARGET_SELECT;
                    v.msg = (isEcho ? "Echo" : "Mimic") + ": select a target for "
                            + (card != null ? card.getName() : cardId) + "'s ability";
                    v.refresh();
                }
                break;
            }
            case COPY:
                v.apply(v.rules().copiedTargeted(v.stepCard, amP1, v.copiedCard, fieldIsP1, isFront, idx));
                break;
            default:
                break;
        }
    }

    // ── Below the board: whose turn, the last message, ability buttons ───────

    private static JPanel actionStrip(BattleView v) {
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setBackground(BG);
        p.setBorder(new EmptyBorder(4, 14, 6, 14));
        p.setPreferredSize(new Dimension(0, 46));

        boolean myTurn = v.myTurn();
        p.add(lbl(myTurn ? "YOUR TURN" : "Opponent's turn", font(Font.BOLD, 14),
                  myTurn ? MY_NAME : OPP_NAME), BorderLayout.WEST);
        JLabel msg = lbl(v.msg, font(Font.ITALIC, 12), MSG_CLR);
        msg.setHorizontalAlignment(SwingConstants.CENTER);
        p.add(msg, BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        if (myTurn) {
            if (v.step != null || v.abilitySource != null) {
                JButton cancel = smallButton("Cancel Ability", new Color(180, 120, 60));
                cancel.addActionListener(e -> {
                    v.clearAbilityMode();
                    v.selField = null;
                    v.msg = "";
                    v.refresh();
                });
                btns.add(cancel);
            }

            if (v.step == Step.SCRAP_SELECT) {
                JButton done = smallButton("Done (" + v.scrapSelected.size() + " Scrap)", SEL_ABL);
                done.addActionListener(e -> finishScrapSelect(v));
                btns.add(done);
            }

            JButton cardAbility = cardAbilityButton(v);
            if (cardAbility != null) btns.add(cardAbility);

            JButton champAbility = championButton(v);
            if (champAbility != null) btns.add(champAbility);
        }
        p.add(btns, BorderLayout.EAST);
        return p;
    }

    // ── Bottom: you, your hand, End Turn and Forfeit ─────────────────────────

    private static JPanel playerBar(BattleView v) {
        boolean me = v.amP1();
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setBackground(HDR_BG);
        p.setBorder(new CompoundBorder(new MatteBorder(1, 0, 0, 0, LINE), new EmptyBorder(6, 14, 6, 14)));
        p.setPreferredSize(new Dimension(0, BOTTOM_H));

        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setOpaque(false);
        info.add(lbl(v.user.getUsername(), font(Font.BOLD, 16), MY_NAME));
        info.add(Box.createVerticalStrut(6));
        info.add(lbl("Soul " + v.st.souls(me) + "/" + v.st.soulCap(me), font(Font.BOLD, 13), SOUL_CLR));
        info.add(Box.createVerticalStrut(2));
        info.add(lbl("Deck " + v.st.deck(me).size(), font(Font.BOLD, 13), MUTED));

        JButton endBtn = UI.button("End Turn", new Color(100, 180, 255), 14, Color.WHITE, new Insets(8, 0, 8, 0));
        endBtn.setEnabled(v.myTurn());
        endBtn.addActionListener(e -> {
            v.msg = "";
            v.endTurn();
        });
        JButton forfeit = UI.button("Forfeit", new Color(220, 80, 80), 14, Color.WHITE, new Insets(8, 0, 8, 0));
        forfeit.addActionListener(e -> {
            int answer = JOptionPane.showConfirmDialog(forfeit, "Forfeit this battle? Your opponent wins.",
                                                       "Forfeit", JOptionPane.YES_NO_OPTION);
            if (answer != JOptionPane.YES_OPTION) return;
            v.rules().forfeit(v.amP1());
            v.st.save();
            v.refresh();
        });
        JPanel btns = new JPanel(new GridLayout(2, 1, 0, 10));
        btns.setOpaque(false);
        btns.add(endBtn);
        btns.add(forfeit);

        p.add(column(info, GridBagConstraints.WEST),     BorderLayout.WEST);
        p.add(hand(v),                                    BorderLayout.CENTER);
        p.add(column(btns, GridBagConstraints.CENTER),   BorderLayout.EAST);
        return p;
    }

    /** A fixed-width column with its contents centred vertically. */
    private static JPanel column(JComponent content, int anchor) {
        JPanel col = new JPanel(new GridBagLayout());
        col.setOpaque(false);
        col.setPreferredSize(new Dimension(INFO_W, 0));
        GridBagConstraints c = new GridBagConstraints();
        c.anchor  = anchor;
        c.weightx = 1;
        c.fill    = anchor == GridBagConstraints.CENTER ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        col.add(content, c);
        return col;
    }

    /** Your hand: equal squares in a row, centred, shrinking so every card fits without overlapping. */
    private static JPanel hand(BattleView v) {
        BattleState st = v.st;
        boolean amP1   = v.amP1();
        boolean myTurn = v.myTurn();
        List<String> handIds = new ArrayList<>(st.hand(amP1));
        int souls = st.souls(amP1);

        JPanel p = new JPanel(null) {
            static final int GAP = 8;

            @Override public void doLayout() {
                int n = getComponentCount();
                if (n == 0) return;
                int s = Math.max(10, Math.min(getHeight(), (getWidth() - GAP * (n - 1)) / n));
                int x = (getWidth() - (n * s + (n - 1) * GAP)) / 2;
                int y = (getHeight() - s) / 2;
                for (int i = 0; i < n; i++) getComponent(i).setBounds(x + i * (s + GAP), y, s, s);
            }

            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getComponentCount() > 0) return;
                g.setFont(font(Font.ITALIC, 12));
                g.setColor(MUTED);
                String t = "No cards in hand";
                FontMetrics fm = g.getFontMetrics();
                g.drawString(t, (getWidth() - fm.stringWidth(t)) / 2, getHeight() / 2);
            }
        };
        p.setOpaque(false);

        for (int i = 0; i < handIds.size(); i++) {
            final int fi = i;
            Card c = v.cardMap.get(handIds.get(i));
            if (c == null) continue;
            int     cost      = AbilityResolver.effectiveCost(c, st, amP1, v.champLines);
            boolean canAfford = souls >= cost;
            boolean sel       = v.selHand == i;
            // Disable hand selection when in ability targeting mode
            boolean clickable = canAfford && myTurn && v.abilitySource == null;
            boolean isFree    = st.freeplayCards.contains(c.getId() + "_" + (amP1 ? "p1" : "p2"));

            boolean isConglamorat = CardIds.CONGLAMORAT.equals(c.getId());
            int handAtk = isConglamorat ? cost : c.getAttack();
            int handHp  = isConglamorat ? cost : c.getHp();

            Color outline = sel ? SEL_ATK : (canAfford && myTurn ? CardRenderer.typeColor(c.getType()) : EMPTY_LINE);
            Slot card = new Slot(CardRenderer.buildBattleCard(c, handHp, handAtk, handAtk - c.getAttack(), false, ""),
                                 sel ? new Color(30, 55, 95) : SLOT_BG, outline, sel ? 3 : 2);
            card.dim = !(canAfford && myTurn);
            Color costClr = canAfford ? new Color(120, 180, 240) : new Color(240, 100, 100);
            card.badge(isFree,                          "FREE",          costClr);
            card.badge(!isFree && cost != c.getCost(), "Costs " + cost, costClr);

            if (clickable) {
                card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                card.addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) {
                        v.selField = null;
                        v.selHand  = (v.selHand == fi) ? -1 : fi;
                        v.msg = v.selHand >= 0 ? "Select an empty slot to place this card" : "";
                        v.refresh();
                    }
                });
            }
            p.add(card);
        }
        return p;
    }

    // ── Ability buttons ───────────────────────────────────────────────────────

    /** Done button: Iron Tusks Bot fortifies now; Furnace Bot moves on to picking a bot. */
    private static void finishScrapSelect(BattleView v) {
        if (v.scrapSelected.isEmpty()) {
            v.msg = "Select at least 1 Scrap.";
            v.refresh();
            return;
        }
        String srcId = v.st.cardIdAt(v.stepCard);
        if (CardIds.IRON_TUSKS_BOT.equals(srcId)) {
            v.apply(v.rules().fortify(v.stepCard, v.amP1(), v.scrapSelected));
        } else if (CardIds.FURNACE_BOT.equals(srcId)) {
            v.step = Step.BOT_TARGET;
            v.msg = "Select a bot to receive +" + (v.scrapSelected.size() * 2) + " ATK & HP";
            v.refresh();
        }
    }

    /** "<Card>: Use Ability", shown when a non-champion card with an ability is selected. */
    private static JButton cardAbilityButton(BattleView v) {
        if (v.selField == null || v.abilitySource != null || v.step != null) return null;
        String srcKey = v.selField;
        String srcId  = v.st.cardIdAt(srcKey);
        Card   src    = srcId != null ? v.cardMap.get(srcId) : null;
        if (src == null || src instanceof Champion) return null;

        String  aType       = AbilityResolver.abilityType(srcId);
        boolean isEcho      = CardIds.ECHO_SPIRIT.equals(srcId);
        boolean isSpecial   = isEcho || CardIds.CONSTRUCTOR_BOT.equals(srcId)
                           || CardIds.FURNACE_BOT.equals(srcId) || CardIds.IRON_TUSKS_BOT.equals(srcId);
        if (!"active".equals(aType) && !"targeted".equals(aType) && !isSpecial) return null;

        boolean amP1        = v.amP1();
        boolean voidBlocked = AbilityResolver.isAbilityNullified(amP1, v.st);
        boolean enabled     = !voidBlocked && (isEcho ? v.st.soulCap(amP1) >= 1
                                                      : AbilityResolver.canUseAbility(srcId, v.st, amP1));
        String label = src.getName() + ": Use Ability"
                     + (voidBlocked ? " [Void]" : (enabled ? "" : " (can't afford)"));
        JButton b = smallButton(label, enabled ? SEL_ABL : new Color(80, 80, 100));
        b.setEnabled(enabled);
        b.addActionListener(e -> useCardAbility(v, srcKey, srcId, src));
        return b;
    }

    private static void useCardAbility(BattleView v, String srcKey, String srcId, Card src) {
        switch (srcId) {
            case CardIds.ECHO_SPIRIT:
                // Pick a friendly card to copy
                v.stepCard   = srcKey;
                v.step       = Step.ECHO_COPY_SELECT;
                v.copiedCard = null;
                v.selField   = null;
                v.msg = "Echo: select a friendly card to copy its ability";
                v.refresh();
                return;
            case CardIds.CONSTRUCTOR_BOT:
                constructBot(v, srcKey);
                return;
            case CardIds.FURNACE_BOT:
            case CardIds.IRON_TUSKS_BOT:
                // Pick Scrap tokens, then press Done
                v.stepCard = srcKey;
                v.step     = Step.SCRAP_SELECT;
                v.scrapSelected.clear();
                v.selField = null;
                v.msg = "Select Scraps to use, then press Done";
                v.refresh();
                return;
        }
        if (AbilityResolver.needsTarget(srcId)) {
            if (CardIds.UPGRADE_BOT.equals(srcId)) {
                int choice = choose("Upgrade Bot: choose buff", "Upgrade", new String[]{ "+ 2 ATK", "+ 2 HP" });
                if (choice < 0) return;
                v.abilityChoice = choice;
            }
            v.abilitySource  = srcKey;
            v.abilityTgtType = AbilityResolver.TARGET_TYPE.get(srcId);
            v.selField       = null;
            v.msg = "Select a target for " + src.getName() + "'s ability";
            v.refresh();
            return;
        }
        v.apply(v.rules().cardAbility(srcKey, v.amP1()));
    }

    /** Constructor Bot: pick a bot costing 3 or less from your hand to summon. */
    private static void constructBot(BattleView v, String srcKey) {
        List<Card> eligible = new ArrayList<>();
        for (String id : v.st.hand(v.amP1())) {
            Card c = v.cardMap.get(id);
            if (c != null && "bot".equals(c.getType()) && c.getCost() <= 3) eligible.add(c);
        }
        if (eligible.isEmpty()) {
            v.msg = "Construct: no bots costing ≤3 in hand.";
            v.refresh();
            return;
        }
        String[] options = eligible.stream()
            .map(c -> c.getName() + " (" + c.getCost() + " soul)")
            .toArray(String[]::new);
        int choice = choose("Choose a bot to summon (costs 2 Scrap):", "Constructor Bot", options);
        if (choice < 0) return;
        v.apply(v.rules().construct(srcKey, v.amP1(), eligible.get(choice).getId()));
    }

    /** The champion's ability button, labelled with the ability's name. */
    private static JButton championButton(BattleView v) {
        boolean amP1 = v.amP1();
        String champId = AbilityResolver.currentChampId(v.st, amP1);
        Card champ = champId != null ? v.cardMap.get(champId) : null;
        if (!(champ instanceof Champion) || champ.getAbility().isEmpty()) return null;

        boolean acted       = !v.st.hasAction(amP1, false, BattleState.CHAMP_SLOT);
        boolean voidBlocked = AbilityResolver.isAbilityNullified(amP1, v.st);
        String label = champ.getName() + ": " + champ.getAbility().split("[-–]")[0].trim()
                     + (voidBlocked ? " [Void]" : "");
        JButton b = smallButton(label, v.bypass ? new Color(255, 160, 60) : new Color(220, 180, 60));
        b.setEnabled(!acted && !voidBlocked);
        b.addActionListener(e -> useChampionAbility(v));
        return b;
    }

    private static void useChampionAbility(BattleView v) {
        boolean amP1 = v.amP1();
        String champId = AbilityResolver.currentChampId(v.st, amP1);
        if (champId == null) {
            v.msg = "No champion on field.";
        } else if (!v.st.hasAction(amP1, false, BattleState.CHAMP_SLOT)) {
            v.msg = "Champion has already acted this turn.";
        } else if (AbilityResolver.isAbilityNullified(amP1, v.st)) {
            v.msg = "Void: champion ability nullified by enemy Heavenly Shade!";
        } else if (CardIds.MIMIC_CHAMP.equals(champId)) {
            // Mimic: pick an enemy card to steal its ability
            v.step       = Step.MIMIC_SELECT;
            v.stepCard   = BattleState.posKey(amP1, false, BattleState.CHAMP_SLOT);
            v.copiedCard = null;
            v.msg = "Mimic: select an enemy card to steal its ability!";
        } else if (CardIds.BYPASS_CHAMP.equals(champId)) {
            // Bypass: the next attack may hit the backline, using up a Scrap
            if (!AbilityResolver.canBypass(v.st, amP1)) {
                v.msg = "Bypass: need at least 1 Scrap token.";
            } else {
                v.bypass = !v.bypass;
                v.msg = v.bypass ? "Bypass active — select a backline target!" : "Bypass cancelled.";
            }
        } else {
            v.apply(v.rules().championAbility(champId, amP1));
            return;
        }
        v.refresh();
    }

    // ── Result ────────────────────────────────────────────────────────────────

    private static JPanel resultPanel(boolean won, Runnable onComplete) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(BG);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(UI.CARD_BG);
        Color accent = won ? new Color(100, 220, 130) : new Color(220, 80, 80);
        card.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(accent, 3, true), new EmptyBorder(30, 60, 30, 60)));

        JLabel title = lbl(won ? "Victory!" : "Defeat", font(Font.BOLD, 36), accent);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel sub = lbl(won ? "Your champion prevails!" : "Your champion has fallen.",
                         font(Font.ITALIC, 14), new Color(160, 160, 185));
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);
        JButton btn = UI.menuButton("Return to Menu", accent);
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

    private static final Map<Integer, Font> FONTS = new HashMap<>();

    /** SansSerif in the given style and size, made once and reused across redraws. */
    private static Font font(int style, int size) {
        return FONTS.computeIfAbsent(style * 1000 + size, k -> new Font("SansSerif", style, size));
    }

    private static JLabel lbl(String text, Font font, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        return l;
    }

    private static JButton smallButton(String text, Color accent) {
        return UI.button(text, accent, 13, Color.WHITE, new Insets(6, 16, 6, 16));
    }

    /** A pop-up with one button per option. Returns the chosen index, or -1 if closed. */
    private static int choose(String question, String title, String[] options) {
        return JOptionPane.showOptionDialog(null, question, title, JOptionPane.DEFAULT_OPTION,
                                            JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
    }

    /**
     * A square outline that a card fits in. The card is scaled to fit inside the
     * outline, centred and never stretched. Status labels are drawn over the card.
     */
    private static final class Slot extends JPanel {
        private final JComponent card;      // null for an empty slot
        private final Color fill, outline;
        private final int stroke;
        private final List<String> badges      = new ArrayList<>();
        private final List<Color>  badgeColors = new ArrayList<>();
        String  hint;                       // text in an empty slot, e.g. "Place here"
        boolean dim;                        // darkened, e.g. a card you can't afford

        Slot(JComponent card, Color fill, Color outline, int stroke) {
            super(null);
            setOpaque(false);
            this.card = card;
            this.fill = fill;
            this.outline = outline;
            this.stroke = stroke;
            if (card != null) add(card);
        }

        void badge(boolean show, String text, Color color) {
            if (!show) return;
            badges.add(text);
            badgeColors.add(color);
        }

        /** The square the slot is drawn in: as big as fits, centred. */
        private Rectangle square() {
            int s = Math.min(getWidth(), getHeight());
            return new Rectangle((getWidth() - s) / 2, (getHeight() - s) / 2, s, s);
        }

        @Override public void doLayout() {
            if (card == null) return;
            Rectangle r = square();
            int in = stroke + 3;
            card.setBounds(r.x + in, r.y + in, r.width - 2 * in, r.height - 2 * in);
        }

        @Override protected void paintComponent(Graphics g) {
            Rectangle r = square();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(r.x, r.y, r.width - 1, r.height - 1, 10, 10);
            g2.dispose();
        }

        @Override public void paint(Graphics g) {
            super.paint(g);   // fill, then the card
            Rectangle r = square();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            if (dim) {
                g2.setColor(new Color(10, 10, 20, 140));
                g2.fillRoundRect(r.x, r.y, r.width - 1, r.height - 1, 10, 10);
            }

            if (hint != null) {
                g2.setFont(font(Font.ITALIC, Math.max(9, r.width / 9)));
                g2.setColor(outline);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(hint, r.x + (r.width - fm.stringWidth(hint)) / 2,
                              r.y + (r.height + fm.getAscent() - fm.getDescent()) / 2);
            }

            // Status labels: dark pills stacked down from just under the card's name
            Font bf = font(Font.BOLD, Math.max(9, r.width / 12));
            g2.setFont(bf);
            FontMetrics fm = g2.getFontMetrics();
            int h = fm.getHeight() + 2;
            int y = card != null ? r.y + r.height / 4 : r.y + (r.height - h) / 2;
            for (int i = 0; i < badges.size() && y + h <= r.y + r.height - 4; i++) {
                String t = badges.get(i);
                int w = fm.stringWidth(t) + 10;
                int x = r.x + (r.width - w) / 2;
                g2.setColor(new Color(10, 10, 20, 200));
                g2.fillRoundRect(x, y, w, h, h, h);
                g2.setColor(badgeColors.get(i));
                g2.drawString(t, x + 5, y + 1 + fm.getAscent());
                y += h + 2;
            }

            g2.setColor(outline);
            g2.setStroke(new BasicStroke(stroke));
            int o = stroke / 2;
            g2.drawRoundRect(r.x + o, r.y + o, r.width - 1 - stroke, r.height - 1 - stroke, 10, 10);
            g2.dispose();
        }
    }

}
