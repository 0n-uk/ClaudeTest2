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
    private static final Color OPP_BG    = new Color(35, 22, 22);
    private static final Color MY_BG     = new Color(22, 32, 22);
    private static final Color EMPTY_BG  = new Color(28, 28, 45);
    private static final Color SLOT_BG   = new Color(42, 42, 65);
    private static final Color SEL_ATK   = new Color(80, 160, 255);
    private static final Color SEL_TGT   = new Color(220, 60,  60);
    private static final Color SEL_ABL   = new Color(220, 180, 60);
    private static final Color CHAMP_CLR = new Color(220, 180, 60);

    private static final Font FONT_BOLD_10  = new Font("SansSerif", Font.BOLD,   10);
    private static final Font FONT_ITALIC_8 = new Font("SansSerif", Font.ITALIC,  8);

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
        scroll.setPreferredSize(new Dimension(175, 0));
        scroll.setBorder(BorderFactory.createTitledBorder(
                new LineBorder(new Color(60, 60, 90), 1), "Battle Log",
                TitledBorder.CENTER, TitledBorder.TOP,
                font(Font.BOLD, 12), new Color(160, 160, 200)));
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

        boolean amP1 = v.amP1();
        wrapper.add(header(st.playerName(!amP1), st.souls(!amP1), st.soulCap(!amP1),
                           st.deck(!amP1).size(), v.myTurn(), v.msg), BorderLayout.NORTH);

        JPanel field = new JPanel();
        field.setLayout(new BoxLayout(field, BoxLayout.Y_AXIS));
        field.setBackground(BG);
        field.setBorder(new EmptyBorder(4, 8, 4, 8));
        field.add(fieldRow(v, !amP1, false, OPP_BG));
        field.add(Box.createVerticalStrut(3));
        field.add(fieldRow(v, !amP1, true,  OPP_BG));
        JPanel sep = new JPanel();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 4));
        sep.setBackground(new Color(60, 60, 90));
        field.add(Box.createVerticalStrut(5));
        field.add(sep);
        field.add(Box.createVerticalStrut(5));
        field.add(fieldRow(v, amP1, true,  MY_BG));
        field.add(Box.createVerticalStrut(3));
        field.add(fieldRow(v, amP1, false, MY_BG));

        wrapper.add(v.logScroll, BorderLayout.WEST);
        wrapper.add(field, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(0, 4));
        south.setBackground(BG);
        south.setBorder(new EmptyBorder(4, 8, 8, 8));
        south.add(handPanel(v), BorderLayout.CENTER);
        south.add(controls(v),  BorderLayout.SOUTH);
        wrapper.add(south, BorderLayout.SOUTH);

        wrapper.revalidate();
        wrapper.repaint();
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private static JPanel header(String oppName, int oppSouls, int oppSoulCap,
                                 int oppDeck, boolean myTurn, String msg) {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setBackground(HDR_BG);
        p.setBorder(new EmptyBorder(8, 14, 8, 14));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        left.setOpaque(false);
        left.add(lbl("Opponent: " + oppName, font(Font.BOLD, 14), new Color(220, 100, 100)));
        left.add(lbl("Soul " + oppSouls + "/" + oppSoulCap, font(Font.PLAIN, 12), new Color(200, 160, 80)));
        left.add(lbl("Deck: " + oppDeck, font(Font.PLAIN, 12), new Color(140, 140, 165)));

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        JLabel turn = lbl(myTurn ? "YOUR TURN" : "Opponent's Turn", font(Font.BOLD, 15),
                          myTurn ? new Color(100, 220, 130) : new Color(220, 100, 100));
        turn.setHorizontalAlignment(SwingConstants.CENTER);
        center.add(turn, BorderLayout.CENTER);
        if (!msg.isEmpty()) {
            JLabel msgL = lbl(msg, font(Font.ITALIC, 11), new Color(220, 200, 100));
            msgL.setHorizontalAlignment(SwingConstants.CENTER);
            center.add(msgL, BorderLayout.SOUTH);
        }

        p.add(left,   BorderLayout.WEST);
        p.add(center, BorderLayout.CENTER);
        return p;
    }

    // ── Board ─────────────────────────────────────────────────────────────────

    private static JPanel fieldRow(BattleView v, boolean fieldIsP1, boolean isFront, Color bg) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setBackground(bg);
        row.setBorder(new EmptyBorder(3, 0, 3, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 176)); // 170 card + 3+3 border
        for (int i = 0; i < 5; i++) {
            if (i > 0) row.add(Box.createHorizontalStrut(4));
            row.add(slot(v, fieldIsP1, isFront, i));
        }
        return row;
    }

    private static JPanel slot(BattleView v, boolean fieldIsP1, boolean isFront, int idx) {
        BattleState st  = v.st;
        String  sv      = st.getRow(fieldIsP1, isFront)[idx];
        boolean empty   = sv == null || sv.isEmpty();
        String  cardId  = empty ? null : BattleState.slotId(sv);
        Card    card    = cardId != null ? v.cardMap.get(cardId) : null;
        boolean isChamp = card instanceof Champion;
        String  posKey  = BattleState.posKey(fieldIsP1, isFront, idx);
        SlotMode mode   = slotMode(v, fieldIsP1, isFront, idx, cardId, card);

        Color accent = isChamp ? CHAMP_CLR
                     : AbilityResolver.SCRAP_ID.equals(cardId) ? new Color(140, 140, 160)
                     : card != null ? CardRenderer.typeColor(card.getType()) : new Color(80, 80, 110);
        Color bg     = mode.bg     != null ? mode.bg     : (empty ? EMPTY_BG : SLOT_BG);
        Color border = mode.border != null ? mode.border : (empty ? new Color(40, 40, 62) : accent);

        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(bg);
        p.setPreferredSize(new Dimension(170, 170));
        p.setMaximumSize(new Dimension(170, 170));
        p.setBorder(new LineBorder(border, mode.thick ? 2 : 1, true));

        if (empty) {
            if (mode == SlotMode.PLACE) {
                JLabel pl = lbl("Place here", font(Font.ITALIC, 11), new Color(100, 220, 130));
                pl.setHorizontalAlignment(SwingConstants.CENTER);
                p.add(pl, BorderLayout.CENTER);
            }
            if (st.sealedSlots.containsKey(posKey)) {
                JLabel sealL = lbl("Sealed(" + st.sealedSlots.get(posKey) + ")", FONT_ITALIC_8, new Color(210, 70, 70));
                sealL.setHorizontalAlignment(SwingConstants.CENTER);
                p.add(sealL, BorderLayout.SOUTH);
            }
        } else if (card != null) {
            // CENTER: unified card renderer (name, type, ATK, HP, cost, info button)
            int atkBonus = st.fieldAtkBonus.getOrDefault(posKey, 0);
            String stage = isChamp ? "S" + ((Champion) card).getStage() : "";
            p.add(CardRenderer.buildBattleCard(card, 170, BattleState.slotHp(sv), card.getAttack() + atkBonus,
                                               atkBonus, isChamp, stage), BorderLayout.CENTER);
            p.add(statusBadges(v, fieldIsP1, isFront, idx, posKey), BorderLayout.SOUTH);
        }

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

    /** Status effects shown under a card, one small line each. */
    private static JPanel statusBadges(BattleView v, boolean fieldIsP1, boolean isFront, int idx, String posKey) {
        BattleState st = v.st;
        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.setOpaque(false);
        int txTurns = st.transformCounters.getOrDefault(posKey, 0);
        badge(south, st.isFrozen(posKey),                       "Frozen(" + st.frozenCards.get(posKey) + ")",  new Color(100, 200, 255));
        badge(south, st.burnedCards.containsKey(posKey),        "Burned",                                      new Color(255, 130, 50));
        badge(south, st.poisonedCards.contains(posKey),         "Poisoned",                                    new Color(140, 200, 80));
        badge(south, st.sporedCards.containsKey(posKey),        "Spored(" + st.sporedCards.get(posKey) + ")",  new Color(180, 140, 255));
        badge(south, st.decayedCards.containsKey(posKey),       "Decay(" + st.decayedCards.get(posKey) + ")",  new Color(180, 120, 40));
        badge(south, st.sealedSlots.containsKey(posKey),        "Sealed(" + st.sealedSlots.get(posKey) + ")",  new Color(210, 70, 70));
        badge(south, st.focusedCards.contains(posKey),          "Focus!",                                      new Color(255, 220, 80));
        badge(south, txTurns > 0,                               "→" + txTurns + "t",                           new Color(160, 220, 100));
        badge(south, st.fieldLockedCards.contains(posKey),      "Locked",                                      new Color(200, 160, 60));
        badge(south, !isFront && st.isShieldedByGreatEnt(fieldIsP1, idx), "Shielded",                          new Color(100, 200, 100));
        badge(south, fieldIsP1 == v.amP1() && !st.hasAction(fieldIsP1, isFront, idx), "Used",                  new Color(100, 100, 120));
        return south;
    }

    private static void badge(JPanel panel, boolean show, String text, Color color) {
        if (!show) return;
        JLabel l = lbl(text, FONT_ITALIC_8, color);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(l);
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

    // ── Hand ──────────────────────────────────────────────────────────────────

    private static JPanel handPanel(BattleView v) {
        BattleState st = v.st;
        boolean amP1   = v.amP1();
        boolean myTurn = v.myTurn();
        List<String> handIds = new ArrayList<>(st.hand(amP1));
        int souls = st.souls(amP1);

        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        p.setBackground(new Color(20, 22, 35));
        p.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(new Color(50, 50, 80), 1),
            new EmptyBorder(4, 6, 4, 6)));

        p.add(lbl("Hand (" + handIds.size() + ")  ·  Soul " + souls + "/" + st.soulCap(amP1)
                  + "  ·  Deck " + st.deck(amP1).size(),
                  font(Font.BOLD, 12), new Color(160, 160, 190)));

        for (int i = 0; i < handIds.size(); i++) {
            final int fi = i;
            Card c = v.cardMap.get(handIds.get(i));
            if (c == null) continue;
            int     cost      = AbilityResolver.effectiveCost(c, st, amP1, v.champLines);
            boolean canAfford = souls >= cost;
            boolean sel       = v.selHand == i;
            // Disable hand selection when in ability targeting mode
            boolean clickable = canAfford && myTurn && v.abilitySource == null;
            Color accent  = CardRenderer.typeColor(c.getType());
            Color bgColor = sel ? SEL_ATK : (canAfford && myTurn ? SLOT_BG : EMPTY_BG);
            Color border  = sel ? SEL_ATK : (canAfford && myTurn ? accent : new Color(55, 55, 75));

            JPanel card = new JPanel(new BorderLayout(0, 0));
            card.setBackground(bgColor);
            card.setPreferredSize(new Dimension(110, 96));
            card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(border, sel ? 2 : 1, true), new EmptyBorder(4, 5, 4, 5)));
            if (clickable) card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            Color nameClr = canAfford && myTurn ? Color.WHITE : new Color(100, 100, 120);
            Color costClr = canAfford ? new Color(100, 160, 220) : new Color(220, 80, 80);
            boolean isFree = st.freeplayCards.contains(c.getId() + "_" + (amP1 ? "p1" : "p2"));
            String costTxt = isFree ? "FREE" : String.valueOf(cost);

            boolean isConglamorat = CardIds.CONGLAMORAT.equals(c.getId());
            int handBaseAtk = isConglamorat ? cost : c.getAttack();
            int handBaseHp  = isConglamorat ? cost : c.getHp();

            // NORTH: type symbol + name + cost
            JPanel hTop = new JPanel(new BorderLayout(2, 0));
            hTop.setOpaque(false);
            hTop.setAlignmentX(Component.LEFT_ALIGNMENT);
            hTop.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
            JLabel hName = new JLabel(c.getName(), SwingConstants.CENTER);
            hName.setFont(FONT_BOLD_10);
            hName.setForeground(nameClr);
            JLabel hCost = new JLabel(costTxt, SwingConstants.RIGHT);
            hCost.setFont(FONT_BOLD_10);
            hCost.setForeground(costClr);
            hTop.add(new JLabel(Images.typeSymbol(c.getType(), 15, 15)), BorderLayout.WEST);
            hTop.add(hName, BorderLayout.CENTER);
            hTop.add(hCost, BorderLayout.EAST);
            card.add(hTop, BorderLayout.NORTH);

            // CENTER: card image scaled to fill
            card.add(new ScaledImagePanel(Images.cardArt(c.getId()), bgColor), BorderLayout.CENTER);

            // SOUTH: ATK + info button + HP
            JPanel hBot = new JPanel(new BorderLayout(2, 0));
            hBot.setOpaque(false);
            JLabel hAtk = new JLabel("⚔" + handBaseAtk);
            hAtk.setFont(FONT_BOLD_10);
            hAtk.setForeground(new Color(220, 80, 80));
            JLabel hHp = new JLabel(handBaseHp + "♥", SwingConstants.RIGHT);
            hHp.setFont(FONT_BOLD_10);
            hHp.setForeground(new Color(80, 200, 100));
            hBot.add(hAtk, BorderLayout.WEST);
            hBot.add(infoButton(bgColor, c.getName(), c.getAbility()), BorderLayout.CENTER);
            hBot.add(hHp,  BorderLayout.EAST);
            card.add(hBot, BorderLayout.SOUTH);

            if (clickable) {
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

    // ── Buttons ───────────────────────────────────────────────────────────────

    private static JPanel controls(BattleView v) {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setBackground(BG);
        p.setBorder(new EmptyBorder(4, 0, 0, 0));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);

        if (v.myTurn()) {
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

            JButton endBtn = smallButton("End Turn", new Color(100, 180, 255));
            endBtn.addActionListener(e -> {
                v.msg = "";
                v.endTurn();
            });
            btns.add(endBtn);
        }

        JButton forfeit = smallButton("Forfeit", new Color(220, 80, 80));
        forfeit.addActionListener(e -> {
            int answer = JOptionPane.showConfirmDialog(forfeit, "Forfeit this battle? Your opponent wins.",
                                                       "Forfeit", JOptionPane.YES_NO_OPTION);
            if (answer != JOptionPane.YES_OPTION) return;
            v.rules().forfeit(v.amP1());
            v.st.save();
            v.refresh();
        });
        btns.add(forfeit);

        p.add(btns, BorderLayout.EAST);
        return p;
    }

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

    private static JButton infoButton(Color bg, String cardName, String abilityText) {
        JButton btn = new JButton("ℹ");
        btn.setFont(FONT_BOLD_10);
        btn.setForeground(new Color(220, 200, 120));
        btn.setBackground(bg);
        btn.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        String ability = abilityText.isEmpty() ? "No ability." : abilityText;
        btn.addActionListener(e -> CardRenderer.showAbilityPopup(btn, cardName, ability));
        return btn;
    }

    /** A pop-up with one button per option. Returns the chosen index, or -1 if closed. */
    private static int choose(String question, String title, String[] options) {
        return JOptionPane.showOptionDialog(null, question, title, JOptionPane.DEFAULT_OPTION,
                                            JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
    }

    private static class ScaledImagePanel extends JPanel {
        private final java.awt.image.BufferedImage img;
        ScaledImagePanel(java.awt.image.BufferedImage img, Color bg) {
            this.img = img;
            setBackground(bg);
            setOpaque(true);
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (img != null) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(img, 0, 0, getWidth(), getHeight(), null);
            }
        }
    }
}
