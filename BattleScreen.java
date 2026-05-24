import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;
import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.*;

public class BattleScreen {

    private static final Color BG        = new Color(20, 20, 30);
    private static final Color HDR_BG    = new Color(15, 15, 25);
    private static final Color OPP_BG    = new Color(35, 22, 22);
    private static final Color MY_BG     = new Color(22, 32, 22);
    private static final Color EMPTY_BG  = new Color(28, 28, 45);
    private static final Color CARD_BG   = new Color(42, 42, 65);
    private static final Color SEL_ATK   = new Color(80, 160, 255);
    private static final Color SEL_TGT   = new Color(220, 60,  60);
    private static final Color SEL_ABL   = new Color(220, 180, 60);
    private static final Color CHAMP_CLR = new Color(220, 180, 60);

    private static final Font  FONT_BOLD_11   = new Font("SansSerif", Font.BOLD,   11);
    private static final Font  FONT_BOLD_10   = new Font("SansSerif", Font.BOLD,   10);
    private static final Font  FONT_ITALIC_8  = new Font("SansSerif", Font.ITALIC,  8);
    private static final Font  FONT_ITALIC_10 = new Font("SansSerif", Font.ITALIC, 10);

    private static Clip battleMusic;

    // ── Entry point ───────────────────────────────────────────────────────────

    public static JPanel buildPanel(User user, String battleId, Runnable onComplete) {

        Map<String, Card>         cardMap    = BattleManager.buildCardMap();
        Map<String, ChampionLine> champLines = ChampionLine.loadAll();
        for (Card c : user.getOwnedCards()) cardMap.putIfAbsent(c.getId(), c);

        BattleState[] stRef        = { BattleState.load(battleId) };
        int[]         selHand      = { -1 };
        String[]      selField     = { null };  // attack-select mode
        String[]      abilitySource = { null }; // card using an active targeted ability
        String[]      abilityTgtType = { null }; // required target type (null = any/no restriction)
        int[]         abilityChoice = { 0 };    // Upgrade Bot: 0=ATK 1=HP
        String[]      msg          = { "" };
        boolean[]     bypass       = { false };
        // Multi-step ability state (Furnace Bot, Iron Tusks Bot, Echo Spirit, Mimic)
        String[]      multiStepPhase = { null };   // "SCRAP_SELECT", "BOT_TARGET", "ECHO_COPY_SELECT", "MIMIC_SELECT", "COPY_TARGET_SELECT"
        String[]      multiStepCard  = { null };   // posKey of card initiating multi-step
        String[]      echoCopiedCard = { null };   // cardId being copied by Echo or Mimic
        @SuppressWarnings("unchecked")
        List<String>[] scrapSelected = new List[]{ new ArrayList<String>() };

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(BG);

        // Load and play battle music
        try {
            File audioFile = new File("Escelator.wav");
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(audioFile);
            battleMusic = AudioSystem.getClip();
            battleMusic.open(audioInputStream);
            battleMusic.loop(Clip.LOOP_CONTINUOUSLY);
        } catch (Exception e) {
            System.err.println("Error loading battle music: " + e.getMessage());
        }

        javax.swing.Timer[] timerRef   = { null };
        javax.swing.Timer[] hbTimerRef = { null };
        Runnable[]          rebuildRef = { null };

        JTextArea logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(new Color(18, 18, 28));
        logArea.setForeground(new Color(200, 200, 220));
        logArea.setFont(new Font("SansSerif", Font.PLAIN, 11));
        logArea.setWrapStyleWord(true);
        logArea.setLineWrap(true);
        logArea.setMargin(new Insets(6, 8, 6, 8));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setPreferredSize(new Dimension(175, 0));
        logScroll.setBorder(BorderFactory.createTitledBorder(
                new LineBorder(new Color(60, 60, 90), 1), "Battle Log",
                javax.swing.border.TitledBorder.CENTER, javax.swing.border.TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 12), new Color(160, 160, 200)));
        logScroll.getVerticalScrollBar().setUnitIncrement(16);
        String[] lastLoggedMsg = { "" };

        rebuildRef[0] = () -> {
            BattleState st = stRef[0];
            if (st == null) return;
            if (!msg[0].isEmpty() && !msg[0].equals(lastLoggedMsg[0])) {
                logArea.append(msg[0] + "\n");
                lastLoggedMsg[0] = msg[0];
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }
            boolean amP1   = user.getUsername().equals(st.player1);
            boolean myTurn = user.getUsername().equals(st.currentTurn);

            wrapper.removeAll();

            if ("finished".equals(st.phase)) {
                if (timerRef[0]  != null) timerRef[0].stop();
                if (hbTimerRef[0] != null) { hbTimerRef[0].stop(); BattleManager.removeHeartbeat(user.getUsername()); }
                // Stop battle music
                if (battleMusic != null) {
                    battleMusic.stop();
                    battleMusic.close();
                }
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

            field.add(fieldRow(st, cardMap, oppIsP1, false, amP1, myTurn,
                               selHand, selField, abilitySource, abilityTgtType, abilityChoice, msg,
                               stRef, user, wrapper, battleId, onComplete, rebuildRef, bypass, champLines,
                               multiStepPhase, multiStepCard, scrapSelected, echoCopiedCard, OPP_BG));
            field.add(Box.createVerticalStrut(3));
            field.add(fieldRow(st, cardMap, oppIsP1, true, amP1, myTurn,
                               selHand, selField, abilitySource, abilityTgtType, abilityChoice, msg,
                               stRef, user, wrapper, battleId, onComplete, rebuildRef, bypass, champLines,
                               multiStepPhase, multiStepCard, scrapSelected, echoCopiedCard, OPP_BG));

            JPanel sep = new JPanel();
            sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 4));
            sep.setBackground(new Color(60, 60, 90));
            field.add(Box.createVerticalStrut(5));
            field.add(sep);
            field.add(Box.createVerticalStrut(5));

            field.add(fieldRow(st, cardMap, amP1, true, amP1, myTurn,
                               selHand, selField, abilitySource, abilityTgtType, abilityChoice, msg,
                               stRef, user, wrapper, battleId, onComplete, rebuildRef, bypass, champLines,
                               multiStepPhase, multiStepCard, scrapSelected, echoCopiedCard, MY_BG));
            field.add(Box.createVerticalStrut(3));
            field.add(fieldRow(st, cardMap, amP1, false, amP1, myTurn,
                               selHand, selField, abilitySource, abilityTgtType, abilityChoice, msg,
                               stRef, user, wrapper, battleId, onComplete, rebuildRef, bypass, champLines,
                               multiStepPhase, multiStepCard, scrapSelected, echoCopiedCard, MY_BG));

            wrapper.add(logScroll, BorderLayout.WEST);
            wrapper.add(field, BorderLayout.CENTER);

            JPanel south = new JPanel(new BorderLayout(0, 4));
            south.setBackground(BG);
            south.setBorder(new EmptyBorder(4, 8, 8, 8));
            south.add(handPanel(myHand, cardMap, mySouls, mySoulCap, myDeck, myTurn,
                                selHand, selField, abilitySource, msg, stRef, amP1, rebuildRef, champLines),
                      BorderLayout.CENTER);
            south.add(controls(stRef, amP1, myTurn, selHand, selField, abilitySource, abilityTgtType,
                                abilityChoice, msg, rebuildRef, bypass, user, onComplete, champLines, cardMap,
                                multiStepPhase, multiStepCard, scrapSelected, echoCopiedCard),
                      BorderLayout.SOUTH);
            wrapper.add(south, BorderLayout.SOUTH);

            wrapper.revalidate();
            wrapper.repaint();
        };

        // Heartbeat: tell the opponent we are still here
        BattleManager.writeHeartbeat(user.getUsername());
        hbTimerRef[0] = new javax.swing.Timer(2000, e -> BattleManager.writeHeartbeat(user.getUsername()));
        hbTimerRef[0].start();

        timerRef[0] = new javax.swing.Timer(600, e -> {
            BattleState fresh = BattleState.load(battleId);
            if (fresh != null) {
                // Detect opponent disconnect while battle is active
                if ("active".equals(fresh.phase)) {
                    String oppName = user.getUsername().equals(fresh.player1) ? fresh.player2 : fresh.player1;
                    if (!BattleManager.isAlive(oppName)) {
                        BattleManager.forfeitBattle(battleId, oppName);
                        fresh = BattleState.load(battleId);
                        msg[0] = oppName + " disconnected — you win!";
                    }
                }
                stRef[0] = fresh;
            }
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
                                    String[] abilitySource, String[] abilityTgtType,
                                    int[] abilityChoice, String[] msg, BattleState[] stRef,
                                    User user, JPanel wrapper, String battleId, Runnable onComplete,
                                    Runnable[] rebuildRef, boolean[] bypass,
                                    Map<String, ChampionLine> champLines,
                                    String[] multiStepPhase, String[] multiStepCard,
                                    List<String>[] scrapSelected, String[] echoCopiedCard, Color bg) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setBackground(bg);
        row.setBorder(new EmptyBorder(3, 0, 3, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 176)); // 170 card + 3+3 border
        for (int i = 0; i < 5; i++) {
            if (i > 0) row.add(Box.createHorizontalStrut(4));
            row.add(slot(st, cardMap, fieldIsP1, isFront, i, amP1, myTurn,
                         selHand, selField, abilitySource, abilityTgtType, abilityChoice, msg,
                         stRef, user, wrapper, battleId, onComplete, rebuildRef, bypass, champLines,
                         multiStepPhase, multiStepCard, scrapSelected, echoCopiedCard));
        }
        return row;
    }

    // ── Slot ──────────────────────────────────────────────────────────────────

    private static JPanel slot(BattleState st, Map<String, Card> cardMap,
                                boolean fieldIsP1, boolean isFront, int idx,
                                boolean amP1, boolean myTurn,
                                int[] selHand, String[] selField,
                                String[] abilitySource, String[] abilityTgtType,
                                int[] abilityChoice, String[] msg,
                                BattleState[] stRef, User user, JPanel wrapper,
                                String battleId, Runnable onComplete, Runnable[] rebuildRef,
                                boolean[] bypass, Map<String, ChampionLine> champLines,
                                String[] multiStepPhase, String[] multiStepCard,
                                List<String>[] scrapSelected, String[] echoCopiedCard) {

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

        boolean inScrapSelect      = "SCRAP_SELECT".equals(multiStepPhase[0]);
        boolean inBotTarget        = "BOT_TARGET".equals(multiStepPhase[0]);
        boolean inEchoCopySelect   = "ECHO_COPY_SELECT".equals(multiStepPhase[0]);
        boolean inMimicSelect      = "MIMIC_SELECT".equals(multiStepPhase[0]);
        boolean inCopyTargetSelect = "COPY_TARGET_SELECT".equals(multiStepPhase[0]);
        boolean isFrozenCard   = !empty && st.isFrozen(posKey);
        boolean isBurnedCard   = !empty && st.burnedCards.containsKey(posKey);
        boolean isShieldedSlot = !isFront && !empty && st.isShieldedByGreatEnt(fieldIsP1, idx);

        // Dream Wanderer: attacker can bypass frontline to hit backline
        boolean atkIsDreamWanderer = selField[0] != null
                && "drw001".equals(getCardIdAtPosKey(st, selField[0]));
        // Catapult: can only target enemy backline, bypassing frontline
        boolean atkIsCatapult = selField[0] != null && "cat001".equals(getCardIdAtPosKey(st, selField[0]));
        // Bat Eye: cannot be targeted by enemy frontline cards
        boolean atkIsFrontline = selField[0] != null && selField[0].charAt(2) == 'f';
        boolean batEyeBlocked  = "bte001".equals(cardId) && atkIsFrontline;

        boolean anyNewPhase = inEchoCopySelect || inMimicSelect || inCopyTargetSelect;
        boolean canPlace  = isMyField && empty  && myTurn && selHand[0] >= 0 && abilitySource[0] == null && !inScrapSelect && !inBotTarget && !anyNewPhase;
        boolean canSelect = isMyField && !empty && myTurn && hasAct && !isFrozenCard && selHand[0] < 0 && abilitySource[0] == null && !inScrapSelect && !inBotTarget && !anyNewPhase;
        boolean canTarget = !isMyField && !empty && myTurn && selField[0] != null && abilitySource[0] == null
                             && !inScrapSelect && !inBotTarget && !anyNewPhase && !batEyeBlocked
                             && (atkIsCatapult
                                 ? (!isFront && st.isTargetableBypass(fieldIsP1, isFront, idx))
                                 : ((bypass[0] || atkIsDreamWanderer)
                                     ? st.isTargetableBypass(fieldIsP1, isFront, idx)
                                     : st.isTargetable(fieldIsP1, isFront, idx)));

        // Ability targeting mode — supports both friendly and enemy targets depending on ability
        String abilitySourceCardId = abilitySource[0] != null ? getCardIdAtPosKey(st, abilitySource[0]) : null;
        boolean abilityTargetsEnemy = "enemy".equals(AbilityResolver.TARGET_SIDE.get(abilitySourceCardId));
        boolean abilityTargetsEmptySlot = AbilityResolver.TARGETS_EMPTY_SLOT.contains(abilitySourceCardId);
        boolean tgtSideImmune    = !isMyField && AbilityResolver.isImmuneToAbilities(fieldIsP1, st);
        boolean shieldedByEnt    = !isFront && !isMyField && st.isShieldedByGreatEnt(fieldIsP1, idx);
        boolean canAbilityTarget = myTurn && abilitySource[0] != null && !anyNewPhase
                && (abilityTargetsEmptySlot
                    ? (empty && !isMyField)
                    : (!empty && card != null && !tgtSideImmune && !shieldedByEnt
                       && (abilityTargetsEnemy ? !isMyField : isMyField)
                       && (abilityTgtType[0] == null || abilityTgtType[0].equals(card.getType().toLowerCase()))));

        // Echo copy select: click a friendly card with a copyable ability
        boolean canEchoCopy = inEchoCopySelect && isMyField && !empty && myTurn
                && isCopyableAbility(cardId);

        // Mimic select: click an enemy card with a copyable ability
        boolean canMimicTarget = inMimicSelect && !isMyField && !empty && myTurn
                && isCopyableAbility(cardId);

        // Copy target select: click appropriate target for the copied ability
        boolean copiedAbilityTargetsEnemy = inCopyTargetSelect
                && "enemy".equals(AbilityResolver.TARGET_SIDE.get(echoCopiedCard[0]));
        String copiedTargetType = inCopyTargetSelect ? AbilityResolver.TARGET_TYPE.get(echoCopiedCard[0]) : null;
        boolean canCopyTarget = inCopyTargetSelect && !empty && myTurn && card != null
                && (copiedAbilityTargetsEnemy ? !isMyField : isMyField)
                && !(copiedAbilityTargetsEnemy && AbilityResolver.isImmuneToAbilities(fieldIsP1, st))
                && !(copiedAbilityTargetsEnemy && !isFront && st.isShieldedByGreatEnt(fieldIsP1, idx))
                && (copiedTargetType == null || copiedTargetType.equals(card.getType().toLowerCase()));

        // Scrap-select mode: click scraps on MY field to toggle selection
        boolean canSelectScrap = inScrapSelect && isMyField && !empty && myTurn
                && AbilityResolver.SCRAP_ID.equals(cardId);
        boolean isSelectedScrap = canSelectScrap && scrapSelected[0].contains(posKey);

        // Bot-target mode for Furnace Bot: click a bot on MY field
        boolean canBotTarget = inBotTarget && isMyField && !empty && myTurn
                && card != null && "bot".equals(card.getType().toLowerCase());

        int atkBonus = st.fieldAtkBonus.getOrDefault(posKey, 0);
        int displayAtk = (card != null ? card.getAttack() : 0) + atkBonus;

        Color accent  = isChamp ? CHAMP_CLR
                      : (AbilityResolver.SCRAP_ID.equals(cardId) ? new Color(140,140,160)
                      : (card != null ? CardViewer.typeColor(card.getType()) : new Color(80,80,110)));
        Color bgColor = isSel ? SEL_ATK
                      : (isSelectedScrap  ? new Color(80, 60, 20)
                      : (canTarget        ? new Color(55, 25, 25)
                      : (canAbilityTarget ? new Color(55, 50, 20)
                      : (canBotTarget     ? new Color(55, 50, 20)
                      : (canSelectScrap   ? new Color(50, 45, 20)
                      : (canEchoCopy      ? new Color(20, 55, 55)
                      : (canMimicTarget   ? new Color(55, 20, 55)
                      : (canCopyTarget    ? new Color(55, 40, 10)
                      : (empty ? EMPTY_BG : CARD_BG)))))))));
        Color border  = isSel ? SEL_ATK
                      : (isSelectedScrap  ? new Color(255, 200, 60)
                      : (canTarget        ? SEL_TGT
                      : (canAbilityTarget ? SEL_ABL
                      : (canBotTarget     ? SEL_ABL
                      : (canSelectScrap   ? new Color(200, 160, 40)
                      : (canEchoCopy      ? new Color(60, 200, 200)
                      : (canMimicTarget   ? new Color(180, 80, 220)
                      : (canCopyTarget    ? new Color(220, 160, 60)
                      : (canPlace  ? new Color(100,220,130)
                      : (canSelect ? new Color(100,160,255)
                      : (empty     ? new Color(40,40,62) : accent)))))))))));

        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(bgColor);
        p.setPreferredSize(new Dimension(170, 170));
        p.setMaximumSize(new Dimension(170, 170));
        boolean anyClickable = canPlace || canSelect || canTarget || canAbilityTarget
                               || canSelectScrap || isSelectedScrap || canBotTarget
                               || canEchoCopy || canMimicTarget || canCopyTarget;
        p.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(border, (isSel || canTarget || canPlace || canAbilityTarget
                                    || isSelectedScrap || canSelectScrap || canBotTarget
                                    || canEchoCopy || canMimicTarget || canCopyTarget) ? 2 : 1, true),
            new EmptyBorder(0, 0, 0, 0)));
        if (anyClickable)
            p.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        if (empty) {
            if (canPlace) {
                JLabel pl = lbl("Place here", Font.ITALIC, 11, new Color(100, 220, 130));
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
            String stageStr = isChamp ? "S" + ((Champion) card).getStage() : "";
            p.add(CardRenderer.buildBattleCard(card, 170, hp, displayAtk, atkBonus, isChamp, stageStr),
                  BorderLayout.CENTER);

            // SOUTH: status badges only
            JPanel southPanel = new JPanel();
            southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));
            southPanel.setOpaque(false);

            if (isFrozenCard) {
                JLabel frozenL = lbl("Frozen(" + st.frozenCards.get(posKey) + ")", FONT_ITALIC_8, new Color(100, 200, 255));
                frozenL.setAlignmentX(Component.LEFT_ALIGNMENT);
                southPanel.add(frozenL);
            }
            if (isBurnedCard) {
                JLabel burnL = lbl("Burned", FONT_ITALIC_8, new Color(255, 130, 50));
                burnL.setAlignmentX(Component.LEFT_ALIGNMENT);
                southPanel.add(burnL);
            }
            if (st.poisonedCards.contains(posKey)) {
                JLabel poisL = lbl("Poisoned", FONT_ITALIC_8, new Color(140, 200, 80));
                poisL.setAlignmentX(Component.LEFT_ALIGNMENT);
                southPanel.add(poisL);
            }
            if (st.sporedCards.containsKey(posKey)) {
                JLabel sporeL = lbl("Spored(" + st.sporedCards.get(posKey) + ")", FONT_ITALIC_8, new Color(180, 140, 255));
                sporeL.setAlignmentX(Component.LEFT_ALIGNMENT);
                southPanel.add(sporeL);
            }
            if (st.decayedCards.containsKey(posKey)) {
                JLabel decayL = lbl("Decay(" + st.decayedCards.get(posKey) + ")", FONT_ITALIC_8, new Color(180, 120, 40));
                decayL.setAlignmentX(Component.LEFT_ALIGNMENT);
                southPanel.add(decayL);
            }
            if (st.sealedSlots.containsKey(posKey)) {
                JLabel sealL = lbl("Sealed(" + st.sealedSlots.get(posKey) + ")", FONT_ITALIC_8, new Color(210, 70, 70));
                sealL.setAlignmentX(Component.LEFT_ALIGNMENT);
                southPanel.add(sealL);
            }
            if (st.focusedCards.contains(posKey)) {
                JLabel focL = lbl("Focus!", FONT_ITALIC_8, new Color(255, 220, 80));
                focL.setAlignmentX(Component.LEFT_ALIGNMENT);
                southPanel.add(focL);
            }
            int txTurns = st.transformCounters.getOrDefault(posKey, 0);
            if (txTurns > 0) {
                JLabel txL = lbl("→" + txTurns + "t", FONT_ITALIC_8, new Color(160, 220, 100));
                txL.setAlignmentX(Component.LEFT_ALIGNMENT);
                southPanel.add(txL);
            }
            if (st.fieldLockedCards.contains(posKey)) {
                JLabel lockL = lbl("Locked", FONT_ITALIC_8, new Color(200, 160, 60));
                lockL.setAlignmentX(Component.LEFT_ALIGNMENT);
                southPanel.add(lockL);
            }
            if (isShieldedSlot) {
                JLabel shL = lbl("Shielded", FONT_ITALIC_8, new Color(100, 200, 100));
                shL.setAlignmentX(Component.LEFT_ALIGNMENT);
                southPanel.add(shL);
            }
            if (isMyField && !hasAct) {
                JLabel used = lbl("Used", FONT_ITALIC_8, new Color(100, 100, 120));
                used.setAlignmentX(Component.LEFT_ALIGNMENT);
                southPanel.add(used);
            }
            p.add(southPanel, BorderLayout.SOUTH);
        }

        if (anyClickable) {
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
                        // Clear any lingering effects from a previous occupant of this slot
                        String newPosKey = BattleState.posKey(amP1, isFront, idx);
                        if (st2.sealedSlots.containsKey(newPosKey)) {
                            msg[0] = "That slot is sealed and cannot be used!";
                            rebuildRef[0].run();
                            return;
                        }
                        st2.burnedCards.remove(newPosKey);
                        st2.frozenCards.remove(newPosKey);
                        st2.focusedCards.remove(newPosKey);
                        // Conglamorat: HP and ATK equal cost (= current souls, min 1)
                        int placeHp = c != null ? c.getHp() : 1;
                        if ("cng001".equals(id)) {
                            placeHp = cost;
                            st2.fieldAtkBonus.put(newPosKey, cost);
                        }
                        targetRow[idx] = BattleState.makeSlot(id, placeHp);
                        hand.remove(selHand[0]);
                        st2.freeplayCards.remove(id + "_" + (amP1 ? "p1" : "p2"));
                        if (amP1) st2.p1Souls -= cost; else st2.p2Souls -= cost;
                        selHand[0] = -1; msg[0] = "";
                        // Worker Ant: add a Worker Ant2 to owner's hand on placement
                        if ("wka001".equals(id)) {
                            hand.add("wka002");
                            msg[0] = "Colony: Worker Ant2 added to your hand!";
                        }
                        // Water Spirit: all friendly cards on field gain +2 HP when placed
                        if ("wts001".equals(id)) {
                            waterSpiritBuff(st2, amP1);
                            msg[0] = "Water Spirit: all friendly cards gained +2 HP!";
                        }
                        // Torch: all friendly cards gain +2 ATK and +2 HP when placed
                        if ("trc001".equals(id)) {
                            torchBuff(st2, amP1);
                            msg[0] = "Torch: all friendly cards gained +2 ATK and +2 HP!";
                        }
                        // Pod cards: start transform countdown on placement
                        int txDelay = AbilityResolver.transformDelay(id);
                        if (txDelay > 0) st2.transformCounters.put(newPosKey, txDelay);
                        // Great Ent: mark as field-locked on placement
                        if ("gen001".equals(id)) st2.fieldLockedCards.add(newPosKey);
                        // Turret Bot: when opponent places on frontline, auto-attack the placed card
                        if (isFront) {
                            boolean oppIsP1 = !amP1;
                            String[] oppFront = oppIsP1 ? st2.p1Front : st2.p2Front;
                            for (int ti = 0; ti < 5; ti++) {
                                if ("ttb001".equals(BattleState.slotId(oppFront[ti]))
                                        && st2.hasAction(oppIsP1, true, ti)) {
                                    doAttack(st2, cardMap, champLines, oppIsP1,
                                             BattleState.posKey(oppIsP1, true, ti),
                                             amP1, true, idx,
                                             stRef, selHand, selField, msg, rebuildRef, bypass, user);
                                    return;
                                }
                            }
                        }
                        st2.save(); rebuildRef[0].run();
                    } else if (canSelect) {
                        selField[0] = isSel ? null : posKey;
                        selHand[0] = -1;
                        bypass[0]  = false;
                        msg[0] = selField[0] != null ? "Select a target or use ability" : "";
                        rebuildRef[0].run();
                    } else if (canTarget) {
                        doAttack(stRef[0], cardMap, champLines, amP1, selField[0],
                                 fieldIsP1, isFront, idx,
                                 stRef, selHand, selField, msg, rebuildRef, bypass, user);
                    } else if (canAbilityTarget) {
                        doTargetedAbility(stRef[0], cardMap, abilitySource[0], amP1,
                                          fieldIsP1, isFront, idx, abilityChoice[0],
                                          stRef, abilitySource, abilityTgtType, abilityChoice,
                                          selHand, selField, msg, rebuildRef);
                    } else if (canSelectScrap || isSelectedScrap) {
                        if (scrapSelected[0].contains(posKey)) scrapSelected[0].remove(posKey);
                        else scrapSelected[0].add(posKey);
                        msg[0] = scrapSelected[0].size() + " Scrap selected. Press Done when ready.";
                        rebuildRef[0].run();
                    } else if (canBotTarget) {
                        // Furnace Bot BOT_TARGET phase
                        String result = AbilityResolver.smelt(stRef[0], amP1, scrapSelected[0],
                                                               isFront, idx, cardMap);
                        boolean srcFront = multiStepCard[0].charAt(2) == 'f';
                        int     srcIdx   = Character.getNumericValue(multiStepCard[0].charAt(3));
                        boolean srcIsP1  = multiStepCard[0].startsWith("p1");
                        stRef[0].useAction(srcIsP1, srcFront, srcIdx);
                        stRef[0].abilityUsedThisTurn.add(multiStepCard[0]);
                        msg[0] = withHarvest(result, AbilityResolver.onAbilityUsed(stRef[0], amP1));
                        multiStepPhase[0] = null; multiStepCard[0] = null; scrapSelected[0].clear();
                        selField[0] = null;
                        stRef[0].save(); rebuildRef[0].run();
                    } else if (canEchoCopy) {
                        // Echo Spirit: selected a friendly card to copy its ability
                        String aType = AbilityResolver.abilityType(cardId);
                        echoCopiedCard[0] = cardId;
                        if ("active".equals(aType)) {
                            // Consume 1 soul cap and execute immediately
                            if (amP1) stRef[0].p1SoulCap = Math.max(0, stRef[0].p1SoulCap - 1);
                            else      stRef[0].p2SoulCap = Math.max(0, stRef[0].p2SoulCap - 1);
                            String result = AbilityResolver.executeActive(stRef[0], cardId, amP1, cardMap);
                            boolean srcFront = multiStepCard[0].charAt(2) == 'f';
                            int     srcIdx   = Character.getNumericValue(multiStepCard[0].charAt(3));
                            boolean srcIsP1  = multiStepCard[0].startsWith("p1");
                            stRef[0].useAction(srcIsP1, srcFront, srcIdx);
                            stRef[0].abilityUsedThisTurn.add(multiStepCard[0]);
                            msg[0] = withHarvest("Echo: copied " + (card != null ? card.getName() : cardId)
                                     + " — " + result, AbilityResolver.onAbilityUsed(stRef[0], amP1));
                            echoCopiedCard[0] = null;
                            multiStepPhase[0] = null; multiStepCard[0] = null;
                            selField[0] = null;
                            stRef[0].save(); rebuildRef[0].run();
                        } else {
                            // Targeted: enter copy target select phase
                            multiStepPhase[0] = "COPY_TARGET_SELECT";
                            msg[0] = "Echo: select a target for "
                                     + (card != null ? card.getName() : cardId) + "'s ability";
                            rebuildRef[0].run();
                        }
                    } else if (canMimicTarget) {
                        // M3 Mimic: selected an enemy card to steal its ability
                        String aType = AbilityResolver.abilityType(cardId);
                        echoCopiedCard[0] = cardId;
                        if ("active".equals(aType)) {
                            // Execute stolen ability immediately (from user's perspective)
                            String result = AbilityResolver.executeActive(stRef[0], cardId, amP1, cardMap);
                            boolean srcFront = multiStepCard[0].charAt(2) == 'f';
                            int     srcIdx   = Character.getNumericValue(multiStepCard[0].charAt(3));
                            boolean srcIsP1  = multiStepCard[0].startsWith("p1");
                            stRef[0].useAction(srcIsP1, srcFront, srcIdx);
                            stRef[0].abilityUsedThisTurn.add(multiStepCard[0]);
                            msg[0] = withHarvest("Mimic: stole " + (card != null ? card.getName() : cardId)
                                     + " — " + result, AbilityResolver.onAbilityUsed(stRef[0], amP1));
                            echoCopiedCard[0] = null;
                            multiStepPhase[0] = null; multiStepCard[0] = null;
                            selField[0] = null;
                            stRef[0].save();
                            doEndTurn(amP1, stRef, selHand, selField, msg, rebuildRef, cardMap, champLines);
                        } else {
                            // Targeted: enter copy target select phase
                            multiStepPhase[0] = "COPY_TARGET_SELECT";
                            msg[0] = "Mimic: select a target for "
                                     + (card != null ? card.getName() : cardId) + "'s ability";
                            rebuildRef[0].run();
                        }
                    } else if (canCopyTarget) {
                        // Resolve the copied/mimicked ability on the selected target
                        String srcCardAtPosKey = getCardIdAtPosKey(stRef[0], multiStepCard[0]);
                        boolean srcFront = multiStepCard[0].charAt(2) == 'f';
                        int     srcIdx   = Character.getNumericValue(multiStepCard[0].charAt(3));
                        boolean srcIsP1  = multiStepCard[0].startsWith("p1");
                        boolean isEchoSrc = "ecs001".equals(srcCardAtPosKey);
                        // Consume soul cap for Echo Spirit
                        if (isEchoSrc) {
                            if (amP1) stRef[0].p1SoulCap = Math.max(0, stRef[0].p1SoulCap - 1);
                            else      stRef[0].p2SoulCap = Math.max(0, stRef[0].p2SoulCap - 1);
                        }
                        String result = AbilityResolver.executeEchoCopy(stRef[0], echoCopiedCard[0],
                                amP1, fieldIsP1, isFront, idx, 0, cardMap);
                        stRef[0].useAction(srcIsP1, srcFront, srcIdx);
                        stRef[0].abilityUsedThisTurn.add(multiStepCard[0]);
                        String prefix = isEchoSrc ? "Echo: " : "Mimic: ";
                        msg[0] = withHarvest(prefix + result, AbilityResolver.onAbilityUsed(stRef[0], amP1));
                        echoCopiedCard[0] = null;
                        multiStepPhase[0] = null; multiStepCard[0] = null;
                        selField[0] = null;
                        stRef[0].save();
                        if (isEchoSrc) {
                            rebuildRef[0].run();
                        } else {
                            doEndTurn(amP1, stRef, selHand, selField, msg, rebuildRef, cardMap, champLines);
                        }
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
        String  atkPosKey = atkKey;

        String[] atkRow = atkFront ? (atkIsP1 ? st.p1Front : st.p2Front) : (atkIsP1 ? st.p1Back : st.p2Back);
        String[] tgtRow = tgtFront ? (tgtIsP1 ? st.p1Front : st.p2Front) : (tgtIsP1 ? st.p1Back : st.p2Back);

        String atkSv = atkRow[atkIdx], tgtSv = tgtRow[tgtIdx];
        if (atkSv == null || atkSv.isEmpty() || tgtSv == null || tgtSv.isEmpty()) return;

        String atkId = BattleState.slotId(atkSv), tgtId = BattleState.slotId(tgtSv);
        Card atkC = cardMap.get(atkId), tgtC = cardMap.get(tgtId);
        if (atkC == null || tgtC == null) return;

        if (bypass[0]) {
            msg[0] = AbilityResolver.consumeBypassScrap(st, amP1) + " ";
        }
        bypass[0] = false;

        // Catapult: can only target backline
        if ("cat001".equals(atkId) && tgtFront) {
            msg[0] = "Catapult can only target the backline!";
            st.save(); stRef[0] = st;
            rebuildRef[0].run(); return;
        }

        String myChampId = AbilityResolver.currentChampId(st, amP1);
        String tgtPosKey = BattleState.posKey(tgtIsP1, tgtFront, tgtIdx);

        // Metal Wings Bot: coin-flip dodge on incoming attack — returns to hand
        if ("mwb001".equals(tgtId) && AbilityResolver.coinFlip()) {
            tgtRow[tgtIdx] = "";
            st.fieldAtkBonus.remove(tgtPosKey);
            st.turtleBotCharged.remove(tgtPosKey);
            st.burnedCards.remove(tgtPosKey);
            st.frozenCards.remove(tgtPosKey);
            List<String> tgtHand = tgtIsP1 ? st.p1Hand : st.p2Hand;
            tgtHand.add(tgtId);
            st.mantisSecondAttack.remove(atkPosKey);
            st.useAction(atkIsP1, atkFront, atkIdx);
            selField[0] = null;
            msg[0] += "Metal Wings Bot dodged and returned to hand!";
            st.save(); stRef[0] = st;
            rebuildRef[0].run(); return;
        }

        // Wind Spirit: coin-flip dodge on incoming attack — stays on field
        if ("wns001".equals(tgtId) && AbilityResolver.coinFlip()) {
            st.mantisSecondAttack.remove(atkPosKey);
            st.useAction(atkIsP1, atkFront, atkIdx);
            selField[0] = null;
            msg[0] += "Wind Spirit dodged the attack!";
            st.save(); stRef[0] = st;
            rebuildRef[0].run(); return;
        }

        int dmg = AbilityResolver.effectiveAttack(atkC, atkPosKey, myChampId, tgtPosKey, st);
        // Clear Turtle Bot charge and Focus buff after the attack resolves
        st.turtleBotCharged.remove(atkPosKey);
        st.focusedCards.remove(atkPosKey);

        // Shield Imp passive: -1 incoming damage
        int reduction = AbilityResolver.passiveDamageReduction(tgtId);
        dmg = Math.max(0, dmg - reduction);

        int newTgtHp = BattleState.slotHp(tgtSv) - dmg;

        // Spike Dragon: retaliate 5 dmg to attacker before main damage is processed
        if ("spd001".equals(tgtId)) {
            int atkCurHp = BattleState.slotHp(atkSv);
            int atkNewHp = atkCurHp - 5;
            if (atkNewHp <= 0 && !(atkC instanceof Champion)) {
                String spikeAtkSporedMsg = handleSporedDeath(st, atkPosKey, atkIsP1, cardMap);
                atkRow[atkIdx] = "";
                st.clearCardState(atkPosKey);
                if (!"item".equals(atkC.getType())) {
                    if (atkIsP1) st.p1SoulCap++; else st.p2SoulCap++;
                    // Soul Sipper: Spike Dragon's side gains 1 soul
                    if (AbilityResolver.hasSoulSipper(st, !amP1)) {
                        int cur = !amP1 ? st.p1Souls : st.p2Souls;
                        int cap = !amP1 ? st.p1SoulCap : st.p2SoulCap;
                        if (!amP1) st.p1Souls = Math.min(cur + 1, cap); else st.p2Souls = Math.min(cur + 1, cap);
                    }
                }
                String spikeDeathMsg = AbilityResolver.onDeath(st, atkId, atkIsP1, cardMap);
                if (atkIsP1) st.p1Discard.add(atkId); else st.p2Discard.add(atkId);
                selField[0] = null;
                st.useAction(atkIsP1, atkFront, atkIdx);
                msg[0] += "Spike Dragon retaliates! " + atkC.getName() + " destroyed!"
                           + (spikeDeathMsg.isEmpty() ? "" : " " + spikeDeathMsg) + spikeAtkSporedMsg;
                // still apply the attack damage to Spike Dragon before exiting
                if (newTgtHp <= 0) {
                    String spikeTgtSporedMsg = handleSporedDeath(st, tgtPosKey, tgtIsP1, cardMap);
                    tgtRow[tgtIdx] = "";
                    st.clearCardState(tgtPosKey);
                    if (!(tgtC instanceof Champion)) {
                        if (!"item".equals(tgtC.getType())) {
                            if (amP1) st.p2SoulCap++; else st.p1SoulCap++;
                            // Soul Sipper: attacker's side gains 1 soul
                            if (AbilityResolver.hasSoulSipper(st, amP1)) {
                                int cur = amP1 ? st.p1Souls : st.p2Souls;
                                int cap = amP1 ? st.p1SoulCap : st.p2SoulCap;
                                if (amP1) st.p1Souls = Math.min(cur + 1, cap); else st.p2Souls = Math.min(cur + 1, cap);
                            }
                        }
                        AbilityResolver.onDeath(st, tgtId, tgtIsP1, cardMap);
                        if (tgtIsP1) st.p1Discard.add(tgtId); else st.p2Discard.add(tgtId);
                    }
                    msg[0] += spikeTgtSporedMsg;
                } else {
                    tgtRow[tgtIdx] = BattleState.makeSlot(tgtId, newTgtHp);
                }
                st.save(); stRef[0] = st;
                rebuildRef[0].run(); return;
            } else {
                atkRow[atkIdx] = BattleState.makeSlot(atkId, Math.max(1, atkNewHp));
                msg[0] += "Spike Dragon retaliates for 5! ";
            }
        }

        // Thorny Bushy: deal 6 damage to attacker when attacked
        if ("tbu001".equals(tgtId)) {
            int atkNewHp = BattleState.slotHp(atkSv) - 6;
            if (atkNewHp <= 0 && !(atkC instanceof Champion)) {
                String thornAtkSporedMsg = handleSporedDeath(st, atkPosKey, atkIsP1, cardMap);
                atkRow[atkIdx] = "";
                st.clearCardState(atkPosKey);
                if (!"item".equals(atkC.getType())) {
                    if (atkIsP1) st.p1SoulCap++; else st.p2SoulCap++;
                    // Soul Sipper: Thorny Bushy's side gains 1 soul
                    if (AbilityResolver.hasSoulSipper(st, !amP1)) {
                        int cur = !amP1 ? st.p1Souls : st.p2Souls;
                        int cap = !amP1 ? st.p1SoulCap : st.p2SoulCap;
                        if (!amP1) st.p1Souls = Math.min(cur + 1, cap); else st.p2Souls = Math.min(cur + 1, cap);
                    }
                }
                String thornDeathMsg = AbilityResolver.onDeath(st, atkId, atkIsP1, cardMap);
                if (atkIsP1) st.p1Discard.add(atkId); else st.p2Discard.add(atkId);
                selField[0] = null;
                st.useAction(atkIsP1, atkFront, atkIdx);
                msg[0] += "Thorns: " + atkC.getName() + " destroyed!"
                           + (thornDeathMsg.isEmpty() ? "" : " " + thornDeathMsg) + thornAtkSporedMsg;
                // Still apply the attack damage to Thorny Bushy
                if (newTgtHp <= 0) {
                    String thornTgtSporedMsg = handleSporedDeath(st, tgtPosKey, tgtIsP1, cardMap);
                    tgtRow[tgtIdx] = "";
                    st.clearCardState(tgtPosKey);
                    if (!(tgtC instanceof Champion)) {
                        if (!"item".equals(tgtC.getType())) {
                            if (amP1) st.p2SoulCap++; else st.p1SoulCap++;
                            // Soul Sipper: attacker's side gains 1 soul
                            if (AbilityResolver.hasSoulSipper(st, amP1)) {
                                int cur = amP1 ? st.p1Souls : st.p2Souls;
                                int cap = amP1 ? st.p1SoulCap : st.p2SoulCap;
                                if (amP1) st.p1Souls = Math.min(cur + 1, cap); else st.p2Souls = Math.min(cur + 1, cap);
                            }
                        }
                        AbilityResolver.onDeath(st, tgtId, tgtIsP1, cardMap);
                        if (tgtIsP1) st.p1Discard.add(tgtId); else st.p2Discard.add(tgtId);
                    }
                    msg[0] += thornTgtSporedMsg;
                } else {
                    tgtRow[tgtIdx] = BattleState.makeSlot(tgtId, newTgtHp);
                }
                st.save(); stRef[0] = st;
                rebuildRef[0].run(); return;
            } else {
                atkRow[atkIdx] = BattleState.makeSlot(atkId, Math.max(1, atkNewHp));
                msg[0] += "Thorns: " + atkC.getName() + " takes 6 retaliation damage! ";
            }
        }

        // Damage threshold: Roly Poly blocks <1 dmg; Elder Beetle Warrior blocks <8 dmg
        int dmgThreshold = AbilityResolver.damageThreshold(tgtId);
        boolean isDualStrike = "mtb001".equals(atkId) || "lmt001".equals(atkId) || "esr001".equals(atkId);
        if (dmgThreshold > 0 && dmg < dmgThreshold) {
            if (isDualStrike && !st.mantisSecondAttack.contains(atkPosKey)) {
                st.mantisSecondAttack.add(atkPosKey);
            } else {
                st.mantisSecondAttack.remove(atkPosKey);
                st.useAction(atkIsP1, atkFront, atkIdx);
            }
            selField[0] = null;
            msg[0] += atkC.getName() + " attacks " + tgtC.getName() + " but the attack is too weak — blocked!";
            if (atkC instanceof Champion) {
                doEndTurn(amP1, stRef, selHand, selField, msg, rebuildRef, cardMap, champLines);
            } else {
                st.save(); stRef[0] = st;
                rebuildRef[0].run();
            }
            return;
        }

        if (isDualStrike && !st.mantisSecondAttack.contains(atkPosKey)) {
            st.mantisSecondAttack.add(atkPosKey);
            // Don't call useAction — card gets a second attack
        } else {
            st.mantisSecondAttack.remove(atkPosKey);
            st.useAction(atkIsP1, atkFront, atkIdx);
            // Sloth Bear: freeze self for 1 round after attacking
            if ("slb001".equals(atkId)) {
                st.frozenCards.put(atkPosKey, 2);
            }
        }
        selField[0] = null;

        // Grand Wizard of Omerlia: attacks all cards in a row
        if ("gwz001".equals(atkId)) {
            // backline attacker → attack enemy frontline; frontline attacker → attack enemy backline
            boolean gwzTargetFront = !atkFront;
            String[] gwzRow = gwzTargetFront ? (tgtIsP1 ? st.p1Front : st.p2Front)
                                             : (tgtIsP1 ? st.p1Back  : st.p2Back);
            int atkBase = atkC.getAttack() + st.fieldAtkBonus.getOrDefault(atkPosKey, 0);
            if (st.focusedCards.contains(atkPosKey)) { atkBase *= 2; st.focusedCards.remove(atkPosKey); }
            StringBuilder gwzSb = new StringBuilder("Grand Wizard attacks "
                + (gwzTargetFront ? "frontline" : "backline") + ":");
            for (int gi = 0; gi < 5; gi++) {
                if (gwzRow[gi] == null || gwzRow[gi].isEmpty()) continue;
                String gId = BattleState.slotId(gwzRow[gi]);
                Card gC = cardMap.get(gId);
                if (gC == null) continue;
                int gReduc = AbilityResolver.passiveDamageReduction(gId);
                int gDmg = Math.max(0, atkBase - gReduc);
                int gNewHp = BattleState.slotHp(gwzRow[gi]) - gDmg;
                String gPk = BattleState.posKey(tgtIsP1, gwzTargetFront, gi);
                if (gNewHp <= 0 && !(gC instanceof Champion)) {
                    String sporedMsg = handleSporedDeath(st, gPk, tgtIsP1, cardMap);
                    gwzRow[gi] = "";
                    st.clearCardState(gPk);
                    boolean decayActive = amP1 ? st.p1MageDecayRounds > 0 : st.p2MageDecayRounds > 0;
                    if (!decayActive) {
                        if (!"item".equals(gC.getType())) {
                            if (tgtIsP1) st.p1SoulCap++; else st.p2SoulCap++;
                        }
                        if (tgtIsP1) st.p1Discard.add(gId); else st.p2Discard.add(gId);
                        AbilityResolver.onDeath(st, gId, tgtIsP1, cardMap);
                    }
                    gwzSb.append(" ").append(gC.getName()).append(" ✕").append(sporedMsg);
                } else if (gNewHp > 0) {
                    gwzRow[gi] = BattleState.makeSlot(gId, gNewHp);
                    gwzSb.append(" ").append(gC.getName()).append("(-").append(gDmg).append(")");
                }
            }
            msg[0] = gwzSb.toString();
            if (atkC instanceof Champion) {
                doEndTurn(amP1, stRef, selHand, selField, msg, rebuildRef, cardMap, champLines);
            } else {
                st.save(); stRef[0] = st;
                rebuildRef[0].run();
            }
            return;
        }

        if (newTgtHp <= 0) {
            String tgtSporedMsg = handleSporedDeath(st, tgtPosKey, tgtIsP1, cardMap);
            tgtRow[tgtIdx] = "";
            st.clearCardState(tgtPosKey);

            if (!(tgtC instanceof Champion)) {
                if (!"item".equals(tgtC.getType())) {
                if (amP1) st.p2SoulCap++; else st.p1SoulCap++;
                // Soul Sipper: gain 1 soul when enemy dies
                if (AbilityResolver.hasSoulSipper(st, amP1)) {
                    int cur = amP1 ? st.p1Souls : st.p2Souls;
                    int cap = amP1 ? st.p1SoulCap : st.p2SoulCap;
                    if (amP1) st.p1Souls = Math.min(cur + 1, cap); else st.p2Souls = Math.min(cur + 1, cap);
                }
                }
                String onDeathMsg = AbilityResolver.onDeath(st, tgtId, tgtIsP1, cardMap);
                if (tgtIsP1) st.p1Discard.add(tgtId); else st.p2Discard.add(tgtId);
                msg[0] += tgtC.getName() + " defeated! " + onDeathMsg + tgtSporedMsg;
                // Great Ent: shielded backline card advances to frontline
                if ("gen001".equals(tgtId) && tgtFront) {
                    String[] backRow = tgtIsP1 ? st.p1Back : st.p2Back;
                    if (backRow[tgtIdx] != null && !backRow[tgtIdx].isEmpty()) {
                        String shPk = BattleState.posKey(tgtIsP1, false, tgtIdx);
                        tgtRow[tgtIdx] = backRow[tgtIdx]; backRow[tgtIdx] = "";
                        st.migrateCardState(shPk, tgtPosKey);
                        msg[0] += " Shielded card advances!";
                    }
                }
                // Bollywurg: Tongue Grapple — splash dead enemy's max HP to a random other enemy
                if ("blw001".equals(atkId)) {
                    int splashDmg = tgtC.getHp();
                    List<String[]> splashPool = new ArrayList<>();
                    String[] sFront = tgtIsP1 ? st.p1Front : st.p2Front;
                    String[] sBack  = tgtIsP1 ? st.p1Back  : st.p2Back;
                    for (int i = 0; i < 5; i++) {
                        if (sFront[i] != null && !sFront[i].isEmpty())
                            splashPool.add(new String[]{"f", String.valueOf(i)});
                        if (sBack[i]  != null && !sBack[i].isEmpty())
                            splashPool.add(new String[]{"b", String.valueOf(i)});
                    }
                    if (!splashPool.isEmpty()) {
                        String[] pick    = splashPool.get((int)(Math.random() * splashPool.size()));
                        boolean sFront2  = "f".equals(pick[0]);
                        int     sIdx     = Integer.parseInt(pick[1]);
                        String[] sRow    = sFront2 ? sFront : sBack;
                        String   sId     = BattleState.slotId(sRow[sIdx]);
                        Card     sCard   = sId != null ? cardMap.get(sId) : null;
                        if (sCard != null) {
                            String sPosKey = BattleState.posKey(tgtIsP1, sFront2, sIdx);
                            int reduction2 = AbilityResolver.passiveDamageReduction(sId);
                            int actualSplash = Math.max(0, splashDmg - reduction2);
                            int splashNewHp = BattleState.slotHp(sRow[sIdx]) - actualSplash;
                            if (splashNewHp <= 0 && !(sCard instanceof Champion)) {
                                String splashSporedMsg = handleSporedDeath(st, sPosKey, tgtIsP1, cardMap);
                                sRow[sIdx] = "";
                                st.clearCardState(sPosKey);
                                if (!"item".equals(sCard.getType())) {
                                    if (amP1) st.p2SoulCap++; else st.p1SoulCap++;
                                    // Soul Sipper: gain 1 soul when enemy dies
                                    if (AbilityResolver.hasSoulSipper(st, amP1)) {
                                        int cur = amP1 ? st.p1Souls : st.p2Souls;
                                        int cap = amP1 ? st.p1SoulCap : st.p2SoulCap;
                                        if (amP1) st.p1Souls = Math.min(cur + 1, cap); else st.p2Souls = Math.min(cur + 1, cap);
                                    }
                                }
                                AbilityResolver.onDeath(st, sId, tgtIsP1, cardMap);
                                if (tgtIsP1) st.p1Discard.add(sId); else st.p2Discard.add(sId);
                                msg[0] += " Tongue Grapple: " + actualSplash + " splash → " + sCard.getName() + " defeated!" + splashSporedMsg;
                                if ("gen001".equals(sId) && sFront2) {
                                    String[] sBackRow = tgtIsP1 ? st.p1Back : st.p2Back;
                                    if (sBackRow[sIdx] != null && !sBackRow[sIdx].isEmpty()) {
                                        String shPk2 = BattleState.posKey(tgtIsP1, false, sIdx);
                                        sRow[sIdx] = sBackRow[sIdx]; sBackRow[sIdx] = "";
                                        st.migrateCardState(shPk2, sPosKey);
                                        msg[0] += " Shielded card advances!";
                                    }
                                }
                            } else {
                                sRow[sIdx] = BattleState.makeSlot(sId, Math.max(1, splashNewHp));
                                msg[0] += " Tongue Grapple: " + actualSplash + " splash → " + sCard.getName() + "!";
                            }
                        }
                    }
                }
            } else {
                if (amP1) st.p2SoulCap++; else st.p1SoulCap++;
                if (tgtIsP1) st.p1Discard.add(tgtId); else st.p2Discard.add(tgtId);
                Champion deadChamp = (Champion) tgtC;
                String lineId = deadChamp.getLineId();
                ChampionLine line = champLines.get(lineId);
                String deathAbilityMsg = AbilityResolver.onChampDeath(st, tgtId, tgtIsP1, cardMap);

                if (line != null && !deadChamp.isFinalStage(line)) {
                    Champion next = line.getStageByIndex(deadChamp.getStage());
                    if (next != null) {
                        tgtRow[tgtIdx] = BattleState.makeSlot(next.getId(), next.getHp());
                        st.actionsUsed.remove(tgtPosKey);
                        msg[0] += deadChamp.getName() + " evolved to " + next.getName() + "! " + deathAbilityMsg;
                    }
                } else {
                    st.phase  = "finished";
                    st.winner = user.getUsername();
                    st.save(); stRef[0] = st;
                    rebuildRef[0].run(); return;
                }
            }
        } else {
            tgtRow[tgtIdx] = BattleState.makeSlot(tgtId, newTgtHp);
            String suffix = reduction > 0 ? " (blocked " + reduction + ")" : "";
            msg[0] += "Hit " + tgtC.getName() + " for " + dmg + suffix + "!";
            // Small Bushy / Great Bushy: gain HP when attacked
            if ("sbu001".equals(tgtId) || "gbu001".equals(tgtId)) {
                int bonus = "sbu001".equals(tgtId) ? 3 : 6;
                int curHp = BattleState.slotHp(tgtRow[tgtIdx]);
                tgtRow[tgtIdx] = BattleState.makeSlot(tgtId, curHp + bonus);
                msg[0] += " " + tgtC.getName() + " absorbed the hit, gaining +" + bonus + " HP!";
            }
            // Fire Spirit: apply Burn on hit
            if ("frs001".equals(atkId)) {
                st.burnedCards.put(tgtPosKey, 1);
                msg[0] += " " + tgtC.getName() + " is now Burned!";
            }
            // Spiderling: apply Poison on hit
            if ("spl001".equals(atkId)) {
                st.poisonedCards.add(tgtPosKey);
                msg[0] += " " + tgtC.getName() + " is Poisoned!";
            }
            // Flame Flower Pod / Flame Bloomling: apply Burn on hit
            if ("ffp001".equals(atkId) || "fbl001".equals(atkId)) {
                st.burnedCards.put(tgtPosKey, 1);
                msg[0] += " " + tgtC.getName() + " is Burned!";
            }
            // Fungal cards: apply Spore (2 rounds) on hit
            if ("fgp001".equals(atkId) || "fgs001".equals(atkId)
                    || "fgb001".equals(atkId) || "fgc001".equals(atkId)) {
                st.sporedCards.put(tgtPosKey, 4); // 2 rounds = 4 turns
                msg[0] += " " + tgtC.getName() + " is Spored!";
            }
            // Ice Dragon: apply Freeze on hit (1 round = 2 turns)
            if ("icd001".equals(atkId)) {
                st.frozenCards.put(tgtPosKey, Math.max(st.frozenCards.getOrDefault(tgtPosKey, 0), 2));
                msg[0] += " " + tgtC.getName() + " is Frozen for 1 round!";
            }
        }

        // Fungal Beast: track kills, transform to Colossal on 2nd kill
        if ("fgb001".equals(atkId) && newTgtHp <= 0 && !(tgtC instanceof Champion)) {
            int kills = st.fungalBeastKills.getOrDefault(atkPosKey, 0) + 1;
            if (kills >= 2) {
                st.fungalBeastKills.remove(atkPosKey);
                atkRow[atkIdx] = BattleState.makeSlot("fgc001", 10);
                msg[0] += " Fungal Beast evolved into Fungal Colossal!";
            } else {
                st.fungalBeastKills.put(atkPosKey, kills);
            }
        }

        // Lancer: also hits backline card in same column when attacking frontline
        if ("lcr001".equals(atkId) && tgtFront) {
            String[] tgtBackRow = tgtIsP1 ? st.p1Back : st.p2Back;
            if (tgtBackRow[tgtIdx] != null && !tgtBackRow[tgtIdx].isEmpty()) {
                String bkId = BattleState.slotId(tgtBackRow[tgtIdx]);
                Card bkC = cardMap.get(bkId);
                if (bkC != null && !(bkC instanceof Champion)) {
                    int bkDmg = Math.max(0, (atkC.getAttack() + st.fieldAtkBonus.getOrDefault(atkPosKey, 0))
                                          - AbilityResolver.passiveDamageReduction(bkId));
                    int bkNewHp = BattleState.slotHp(tgtBackRow[tgtIdx]) - bkDmg;
                    String bkPk = BattleState.posKey(tgtIsP1, false, tgtIdx);
                    if (bkNewHp <= 0) {
                        String sporedMsg = handleSporedDeath(st, bkPk, tgtIsP1, cardMap);
                        tgtBackRow[tgtIdx] = "";
                        st.clearCardState(bkPk);
                        if (!"item".equals(bkC.getType())) {
                            if (tgtIsP1) st.p1SoulCap++; else st.p2SoulCap++;
                            if (AbilityResolver.hasSoulSipper(st, amP1)) {
                                int cur = amP1 ? st.p1Souls : st.p2Souls;
                                int cap = amP1 ? st.p1SoulCap : st.p2SoulCap;
                                if (amP1) st.p1Souls = Math.min(cur+1,cap); else st.p2Souls = Math.min(cur+1,cap);
                            }
                        }
                        AbilityResolver.onDeath(st, bkId, tgtIsP1, cardMap);
                        if (tgtIsP1) st.p1Discard.add(bkId); else st.p2Discard.add(bkId);
                        msg[0] += " Pierce: " + bkC.getName() + " defeated!" + sporedMsg;
                    } else {
                        tgtBackRow[tgtIdx] = BattleState.makeSlot(bkId, bkNewHp);
                        msg[0] += " Pierce: " + bkC.getName() + "(-" + bkDmg + ")";
                    }
                }
            }
        }

        // Pawn: after attacking, move to same-column adjacent row if empty
        if ("pwn001".equals(atkId) && !st.fieldLockedCards.contains(atkPosKey)) {
            boolean targetFront  = !atkFront;
            String[] otherRow    = targetFront ? (atkIsP1 ? st.p1Front : st.p2Front)
                                               : (atkIsP1 ? st.p1Back  : st.p2Back);
            if (otherRow[atkIdx] == null || otherRow[atkIdx].isEmpty()) {
                String newPosKey = BattleState.posKey(atkIsP1, targetFront, atkIdx);
                otherRow[atkIdx] = atkRow[atkIdx];
                atkRow[atkIdx]   = "";
                st.migrateCardState(atkPosKey, newPosKey);
                msg[0] += " Pawn dashes to the " + (targetFront ? "frontline" : "backline") + "!";
            }
        }

        if (atkC instanceof Champion) {
            doEndTurn(amP1, stRef, selHand, selField, msg, rebuildRef, cardMap, champLines);
        } else {
            st.save(); stRef[0] = st;
            rebuildRef[0].run();
        }
    }

    // ── Card active ability (no-target) ───────────────────────────────────────

    private static void doCardAbilityNoTarget(BattleState st, Map<String, Card> cardMap,
                                               Map<String, ChampionLine> champLines,
                                               String posKey, boolean amP1,
                                               BattleState[] stRef, int[] selHand,
                                               String[] selField, String[] msg,
                                               Runnable[] rebuildRef) {
        boolean isFront   = posKey.charAt(2) == 'f';
        int     idx       = Character.getNumericValue(posKey.charAt(3));
        boolean fieldIsP1 = posKey.startsWith("p1");

        String[] row = isFront ? (fieldIsP1 ? st.p1Front : st.p2Front)
                               : (fieldIsP1 ? st.p1Back  : st.p2Back);
        String sv = row[idx];
        if (sv == null || sv.isEmpty()) return;
        String cardId = BattleState.slotId(sv);

        if (AbilityResolver.isAbilityNullified(amP1, st)) {
            msg[0] = "Void: card abilities nullified by enemy Heavenly Shade!";
            rebuildRef[0].run();
            return;
        }

        // ── Soul cap cost check ───────────────────────────────────────────
        int capCost = AbilityResolver.SOUL_CAP_COST.getOrDefault(cardId, 0);
        int mySoulCap2 = amP1 ? st.p1SoulCap : st.p2SoulCap;
        if (capCost > 0 && mySoulCap2 < capCost) {
            msg[0] = "Need " + capCost + " soul cap to use this ability.";
            rebuildRef[0].run(); return;
        }

        // ── Prayerful Deacon soul cost check ─────────────────────────────
        int soulCost2 = AbilityResolver.getEffectiveSoulCost(cardId, st, amP1);
        int mySouls2  = amP1 ? st.p1Souls : st.p2Souls;
        if (soulCost2 > 0 && mySouls2 < soulCost2) {
            msg[0] = "Need " + soulCost2 + " soul to use this ability.";
            rebuildRef[0].run(); return;
        }

        // ── BattleScreen-handled abilities ────────────────────────────────
        if ("bgm001".equals(cardId)) {
            doBeginnerMageAttack(st, cardMap, champLines, posKey, isFront, idx, fieldIsP1, amP1,
                                 stRef, selHand, selField, msg, rebuildRef);
            return;
        }
        if ("frm001".equals(cardId)) {
            doFireMageBlast(st, cardMap, champLines, posKey, isFront, idx, fieldIsP1, amP1,
                            stRef, selHand, selField, msg, rebuildRef);
            return;
        }
        if ("ima001".equals(cardId)) {
            doIceMageApprenticeCast(st, cardMap, posKey, isFront, idx, fieldIsP1, amP1,
                                    stRef, selHand, selField, msg, rebuildRef);
            return;
        }
        if ("tmw001".equals(cardId)) {
            doTimeWizardSkip(st, cardMap, champLines, posKey, isFront, idx, fieldIsP1, amP1,
                             stRef, selHand, selField, msg, rebuildRef);
            return;
        }

        String result = AbilityResolver.executeActive(st, cardId, amP1, cardMap);
        if (result != null) {
            st.useAction(fieldIsP1, isFront, idx);
            st.abilityUsedThisTurn.add(posKey);
            msg[0] = withHarvest(result, AbilityResolver.onAbilityUsed(st, amP1));
            selField[0] = null;
            st.save(); stRef[0] = st;
        }
        rebuildRef[0].run();
    }

    // ── Card active ability (targeted) ────────────────────────────────────────

    private static void doTargetedAbility(BattleState st, Map<String, Card> cardMap,
                                           String sourceKey, boolean amP1,
                                           boolean tgtIsP1, boolean tgtFront, int tgtIdx,
                                           int abilityChoice,
                                           BattleState[] stRef, String[] abilitySource,
                                           String[] abilityTgtType, int[] abilityChoiceArr,
                                           int[] selHand, String[] selField, String[] msg,
                                           Runnable[] rebuildRef) {
        boolean srcFront  = sourceKey.charAt(2) == 'f';
        int     srcIdx    = Character.getNumericValue(sourceKey.charAt(3));
        boolean srcIsP1   = sourceKey.startsWith("p1");

        String srcCardId = BattleState.slotId(
            srcFront ? (srcIsP1 ? st.p1Front : st.p2Front)[srcIdx]
                     : (srcIsP1 ? st.p1Back  : st.p2Back)[srcIdx]);
        if (srcCardId == null) { abilitySource[0] = null; rebuildRef[0].run(); return; }

        if (AbilityResolver.isAbilityNullified(amP1, st)) {
            msg[0] = "Void: card abilities nullified by enemy Heavenly Shade!";
            abilitySource[0] = null; abilityTgtType[0] = null; selField[0] = null;
            rebuildRef[0].run(); return;
        }
        if (tgtIsP1 != amP1 && AbilityResolver.isImmuneToAbilities(tgtIsP1, st)) {
            msg[0] = "Sovereign: the enemy champion makes their cards immune to abilities!";
            abilitySource[0] = null; abilityTgtType[0] = null; selField[0] = null;
            rebuildRef[0].run(); return;
        }

        // ── GreatWing: return enemy card to opponent's hand ───────────────────
        if ("gwg001".equals(srcCardId)) {
            String[] tgtRowR = tgtFront ? (tgtIsP1 ? st.p1Front : st.p2Front)
                                        : (tgtIsP1 ? st.p1Back  : st.p2Back);
            String tgtSvR = tgtRowR[tgtIdx];
            if (tgtSvR == null || tgtSvR.isEmpty()) {
                msg[0] = "GreatWing: no card at target.";
                abilitySource[0] = null; abilityTgtType[0] = null; selField[0] = null;
                rebuildRef[0].run(); return;
            }
            String tgtIdR = BattleState.slotId(tgtSvR);
            Card tgtCR = cardMap.get(tgtIdR);
            if (tgtCR instanceof Champion) {
                msg[0] = "GreatWing: cannot return Champions.";
                abilitySource[0] = null; abilityTgtType[0] = null; selField[0] = null;
                rebuildRef[0].run(); return;
            }
            String tgtPkR = BattleState.posKey(tgtIsP1, tgtFront, tgtIdx);
            tgtRowR[tgtIdx] = "";
            st.clearCardState(tgtPkR);
            List<String> enemyHand = tgtIsP1 ? st.p1Hand : st.p2Hand;
            enemyHand.add(tgtIdR);
            st.useAction(srcIsP1, srcFront, srcIdx);
            st.abilityUsedThisTurn.add(sourceKey);
            msg[0] = withHarvest("GreatWing: " + (tgtCR != null ? tgtCR.getName() : tgtIdR) + " returned to opponent's hand!",
                                  AbilityResolver.onAbilityUsed(st, amP1));
            abilitySource[0] = null; abilityTgtType[0] = null; selField[0] = null;
            st.save(); stRef[0] = st; rebuildRef[0].run(); return;
        }

        // ── Rook: Castle — swap with friendly card, target regains action ──────
        if ("rok001".equals(srcCardId)) {
            String rookPk = sourceKey;
            String tgtPkS = BattleState.posKey(tgtIsP1, tgtFront, tgtIdx);
            String[] rookRow = srcFront ? (srcIsP1 ? st.p1Front : st.p2Front)
                                        : (srcIsP1 ? st.p1Back  : st.p2Back);
            String[] tgtRowS = tgtFront ? (tgtIsP1 ? st.p1Front : st.p2Front)
                                        : (tgtIsP1 ? st.p1Back  : st.p2Back);
            if (rookPk.equals(tgtPkS)) {
                msg[0] = "Castle: cannot swap with yourself.";
                abilitySource[0] = null; abilityTgtType[0] = null; selField[0] = null;
                rebuildRef[0].run(); return;
            }
            String tgtSvS = tgtRowS[tgtIdx];
            Card tgtCS = cardMap.get(BattleState.slotId(tgtSvS));
            // Swap slot values
            String tmp = rookRow[srcIdx]; rookRow[srcIdx] = tgtRowS[tgtIdx]; tgtRowS[tgtIdx] = tmp;
            // Swap all card state between rookPk and tgtPkS
            Integer rookBonus = st.fieldAtkBonus.remove(rookPk);
            Integer tgtBonus  = st.fieldAtkBonus.remove(tgtPkS);
            if (rookBonus != null) st.fieldAtkBonus.put(tgtPkS, rookBonus);
            if (tgtBonus  != null) st.fieldAtkBonus.put(rookPk, tgtBonus);
            Integer rookFrz = st.frozenCards.remove(rookPk);
            Integer tgtFrz  = st.frozenCards.remove(tgtPkS);
            if (rookFrz != null) st.frozenCards.put(tgtPkS, rookFrz);
            if (tgtFrz  != null) st.frozenCards.put(rookPk, tgtFrz);
            Integer rookBrn = st.burnedCards.remove(rookPk);
            Integer tgtBrn  = st.burnedCards.remove(tgtPkS);
            if (rookBrn != null) st.burnedCards.put(tgtPkS, rookBrn);
            if (tgtBrn  != null) st.burnedCards.put(rookPk, tgtBrn);
            boolean rookPsn = st.poisonedCards.remove(rookPk);
            boolean tgtPsn  = st.poisonedCards.remove(tgtPkS);
            if (rookPsn) st.poisonedCards.add(tgtPkS);
            if (tgtPsn)  st.poisonedCards.add(rookPk);
            Integer rookSp = st.sporedCards.remove(rookPk); Integer tgtSp = st.sporedCards.remove(tgtPkS);
            if (rookSp != null) st.sporedCards.put(tgtPkS, rookSp); if (tgtSp != null) st.sporedCards.put(rookPk, tgtSp);
            Integer rookDc = st.decayedCards.remove(rookPk); Integer tgtDc = st.decayedCards.remove(tgtPkS);
            if (rookDc != null) st.decayedCards.put(tgtPkS, rookDc); if (tgtDc != null) st.decayedCards.put(rookPk, tgtDc);
            Integer rookTx = st.transformCounters.remove(rookPk); Integer tgtTx = st.transformCounters.remove(tgtPkS);
            if (rookTx != null) st.transformCounters.put(tgtPkS, rookTx); if (tgtTx != null) st.transformCounters.put(rookPk, tgtTx);
            boolean rookLk = st.fieldLockedCards.remove(rookPk); boolean tgtLk = st.fieldLockedCards.remove(tgtPkS);
            if (rookLk) st.fieldLockedCards.add(tgtPkS); if (tgtLk) st.fieldLockedCards.add(rookPk);
            Integer rookFk = st.fungalBeastKills.remove(rookPk); Integer tgtFk = st.fungalBeastKills.remove(tgtPkS);
            if (rookFk != null) st.fungalBeastKills.put(tgtPkS, rookFk); if (tgtFk != null) st.fungalBeastKills.put(rookPk, tgtFk);
            // actionsUsed: Rook (now at tgtPkS) consumed its action; target (now at rookPk) regains action
            st.actionsUsed.remove(rookPk); st.actionsUsed.remove(tgtPkS);
            st.actionsUsed.add(tgtPkS); // Rook is now at tgtPkS, mark used
            // Target at rookPk gets its action restored (NOT added to actionsUsed)
            st.abilityUsedThisTurn.add(sourceKey);
            Card srcCard = cardMap.get(srcCardId);
            msg[0] = withHarvest("Castle: swapped " + (srcCard != null ? srcCard.getName() : srcCardId) + " with " + (tgtCS != null ? tgtCS.getName() : "target")
                                 + "! " + (tgtCS != null ? tgtCS.getName() : "Target") + " can act again.",
                                  AbilityResolver.onAbilityUsed(st, amP1));
            abilitySource[0] = null; abilityTgtType[0] = null; selField[0] = null;
            st.save(); stRef[0] = st; rebuildRef[0].run(); return;
        }

        String result = AbilityResolver.executeTargeted(st, srcCardId, amP1,
                                                          tgtIsP1, tgtFront, tgtIdx,
                                                          abilityChoice, cardMap);
        st.useAction(srcIsP1, srcFront, srcIdx);
        st.abilityUsedThisTurn.add(sourceKey);
        msg[0] = withHarvest(result, AbilityResolver.onAbilityUsed(st, amP1));
        abilitySource[0]  = null;
        abilityTgtType[0] = null;
        selField[0]       = null;
        st.save(); stRef[0] = st;
        rebuildRef[0].run();
    }

    // ── Champion ability ──────────────────────────────────────────────────────

    private static void doChampionAbility(BattleState st, Map<String, Card> cardMap,
                                           Map<String, ChampionLine> champLines,
                                           boolean amP1, BattleState[] stRef,
                                           int[] selHand, String[] selField, String[] msg,
                                           Runnable[] rebuildRef, boolean[] bypass,
                                           String[] multiStepPhase, String[] multiStepCard,
                                           String[] echoCopiedCard) {
        String champId = AbilityResolver.currentChampId(st, amP1);
        if (champId == null) { msg[0] = "No champion on field."; rebuildRef[0].run(); return; }

        if (!st.hasAction(amP1, false, BattleState.CHAMP_SLOT)) {
            msg[0] = "Champion has already acted this turn."; rebuildRef[0].run(); return;
        }

        if (AbilityResolver.isAbilityNullified(amP1, st)) {
            msg[0] = "Void: champion ability nullified by enemy Heavenly Shade!";
            rebuildRef[0].run(); return;
        }

        if ("M3".equals(champId)) {
            multiStepPhase[0] = "MIMIC_SELECT";
            multiStepCard[0]  = BattleState.posKey(amP1, false, BattleState.CHAMP_SLOT);
            echoCopiedCard[0] = null;
            msg[0] = "Mimic: select an enemy card to steal its ability!";
            st.save(); stRef[0] = st;
            rebuildRef[0].run();
            return;
        }

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
            msg[0] = withHarvest(result, AbilityResolver.onAbilityUsed(st, amP1));
            st.abilityUsedThisTurn.add(BattleState.posKey(amP1, false, BattleState.CHAMP_SLOT));
            st.useAction(amP1, false, BattleState.CHAMP_SLOT);
            st.save(); stRef[0] = st;
            rebuildRef[0].run();
            return;
        }
        rebuildRef[0].run();
    }

    // ── Transform helper ──────────────────────────────────────────────────────

    private static void applyTransform(BattleState st, String posKey,
                                        Map<String, Card> cardMap, String[] msg) {
        boolean posIsP1  = posKey.startsWith("p1");
        boolean posFront = posKey.charAt(2) == 'f';
        int posIdx = Character.getNumericValue(posKey.charAt(3));
        String[] row = posFront ? (posIsP1 ? st.p1Front : st.p2Front)
                                : (posIsP1 ? st.p1Back  : st.p2Back);
        if (row[posIdx] == null || row[posIdx].isEmpty()) return;
        String oldId = BattleState.slotId(row[posIdx]);
        int curHp = BattleState.slotHp(row[posIdx]);
        Card oldC = cardMap.get(oldId);
        if (oldC == null) return;
        boolean heads = AbilityResolver.coinFlip();
        String newId = AbilityResolver.transformTarget(oldId, heads);
        Card newC = newId != null ? cardMap.get(newId) : null;
        if (newC == null) return;
        int damageTaken = oldC.getHp() - curHp;
        int newHp = Math.max(1, newC.getHp() - damageTaken);
        row[posIdx] = BattleState.makeSlot(newId, newHp);
        int delay = AbilityResolver.transformDelay(newId);
        if (delay > 0) st.transformCounters.put(posKey, delay);
        if ("gen001".equals(newId)) st.fieldLockedCards.add(posKey);
        String flip = heads ? "Heads" : "Tails";
        String appendMsg = oldC.getName() + " → " + newC.getName() + " (" + flip + ")!";
        msg[0] = msg[0].isEmpty() ? appendMsg : msg[0] + " | " + appendMsg;
    }

    // ── End turn ──────────────────────────────────────────────────────────────

    private static void doEndTurn(boolean amP1, BattleState[] stRef,
                                   int[] selHand, String[] selField,
                                   String[] msg, Runnable[] rebuildRef,
                                   Map<String, Card> cardMap,
                                   Map<String, ChampionLine> champLines) {
        BattleState st = stRef[0];
        selHand[0] = -1; selField[0] = null;

        applyEndTurnEffects(st, cardMap, champLines, msg);

        boolean nextIsP1   = !amP1;
        String  nextPlayer = amP1 ? st.player2 : st.player1;
        List<String> nextDeck = nextIsP1 ? st.p1Deck : st.p2Deck;
        List<String> nextHand = nextIsP1 ? st.p1Hand : st.p2Hand;
        if (!nextDeck.isEmpty()) nextHand.add(nextDeck.remove(0));
        if (nextIsP1) st.p1Souls = st.p1SoulCap; else st.p2Souls = st.p2SoulCap;
        st.actionsUsed.clear();
        st.abilityUsedThisTurn.clear();
        st.freeplayCards.clear();
        st.mantisSecondAttack.clear();
        st.p1ExtraActions = 0;
        st.p2ExtraActions = 0;
        st.currentTurn = nextPlayer;
        st.save(); stRef[0] = st;
        rebuildRef[0].run();
    }

    // ── Hand panel ────────────────────────────────────────────────────────────

    private static JPanel handPanel(List<String> handIds, Map<String, Card> cardMap,
                                     int souls, int soulCap, int deckCount, boolean myTurn,
                                     int[] selHand, String[] selField, String[] abilitySource,
                                     String[] msg, BattleState[] stRef, boolean amP1,
                                     Runnable[] rebuildRef, Map<String, ChampionLine> champLines) {
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
            // Disable hand selection when in ability targeting mode
            boolean clickable = canAfford && myTurn && abilitySource[0] == null;
            Color accent  = CardViewer.typeColor(c.getType());
            Color bgColor = sel ? SEL_ATK : (canAfford && myTurn ? CARD_BG : EMPTY_BG);
            Color border  = sel ? SEL_ATK : (canAfford && myTurn ? accent : new Color(55, 55, 75));

            JPanel card = new JPanel(new BorderLayout(0, 0));
            card.setBackground(bgColor);
            card.setPreferredSize(new Dimension(110, 96));
            card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(border, sel ? 2 : 1, true), new EmptyBorder(4, 5, 4, 5)));
            if (clickable) card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            Color nameClr = canAfford && myTurn ? Color.WHITE : new Color(100, 100, 120);
            Color costClr = canAfford ? new Color(100, 160, 220) : new Color(220, 80, 80);
            boolean isFree = stRef[0].freeplayCards.contains(c.getId() + "_" + (amP1 ? "p1" : "p2"));
            String costTxt = isFree ? "FREE" : String.valueOf(cost);

            int handBaseAtk = "cng001".equals(c.getId()) ? cost : c.getAttack();
            int handBaseHp  = "cng001".equals(c.getId()) ? cost : c.getHp();

            // NORTH: type symbol + name + cost
            JPanel hTop = new JPanel(new BorderLayout(2, 0));
            hTop.setOpaque(false);
            hTop.setAlignmentX(Component.LEFT_ALIGNMENT);
            hTop.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
            JLabel hSym  = new JLabel(TypeSymbolLoader.get(c.getType(), 15, 15));
            JLabel hName = new JLabel(c.getName(), SwingConstants.CENTER);
            hName.setFont(FONT_BOLD_10);
            hName.setForeground(nameClr);
            JLabel hCost = new JLabel(costTxt, SwingConstants.RIGHT);
            hCost.setFont(FONT_BOLD_10);
            hCost.setForeground(costClr);
            hTop.add(hSym,  BorderLayout.WEST);
            hTop.add(hName, BorderLayout.CENTER);
            hTop.add(hCost, BorderLayout.EAST);
            card.add(hTop, BorderLayout.NORTH);

            // CENTER: card image scaled to fill
            card.add(new ScaledImagePanel(CardImageLoader.getRaw(c.getId()), bgColor), BorderLayout.CENTER);

            // SOUTH: ATK + info button + HP
            JPanel hBot = new JPanel(new BorderLayout(2, 0));
            hBot.setOpaque(false);
            JLabel hAtk = new JLabel("⚔" + handBaseAtk);
            hAtk.setFont(FONT_BOLD_10);
            hAtk.setForeground(new Color(220, 80, 80));
            JLabel hHp = new JLabel(handBaseHp + "♥", SwingConstants.RIGHT);
            hHp.setFont(FONT_BOLD_10);
            hHp.setForeground(new Color(80, 200, 100));
            JButton hInfo = infoButton(bgColor, c.getName(), c.getAbility());
            hBot.add(hAtk,  BorderLayout.WEST);
            hBot.add(hInfo, BorderLayout.CENTER);
            hBot.add(hHp,   BorderLayout.EAST);
            card.add(hBot, BorderLayout.SOUTH);

            if (clickable) {
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
                                    int[] selHand, String[] selField,
                                    String[] abilitySource, String[] abilityTgtType,
                                    int[] abilityChoice, String[] msg,
                                    Runnable[] rebuildRef, boolean[] bypass,
                                    User user, Runnable onComplete,
                                    Map<String, ChampionLine> champLines,
                                    Map<String, Card> cardMap,
                                    String[] multiStepPhase, String[] multiStepCard,
                                    List<String>[] scrapSelected, String[] echoCopiedCard) {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setBackground(BG);
        p.setBorder(new EmptyBorder(4, 0, 0, 0));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);

        if (myTurn) {
            // Cancel multi-step or ability targeting mode
            if (multiStepPhase[0] != null || abilitySource[0] != null) {
                JButton cancelAbl = smallBtn("Cancel Ability", new Color(180, 120, 60));
                cancelAbl.addActionListener(e -> {
                    abilitySource[0]  = null;
                    abilityTgtType[0] = null;
                    multiStepPhase[0] = null;
                    multiStepCard[0]  = null;
                    scrapSelected[0].clear();
                    echoCopiedCard[0] = null;
                    selField[0]       = null;
                    msg[0] = "";
                    rebuildRef[0].run();
                });
                btns.add(cancelAbl);
            }

            // Done button during scrap-select phase
            if ("SCRAP_SELECT".equals(multiStepPhase[0])) {
                JButton doneBtn = smallBtn("Done (" + scrapSelected[0].size() + " Scrap)", SEL_ABL);
                doneBtn.addActionListener(e -> {
                    if (scrapSelected[0].isEmpty()) {
                        msg[0] = "Select at least 1 Scrap."; rebuildRef[0].run(); return;
                    }
                    // Determine which card triggered multi-step
                    boolean srcFront = multiStepCard[0].charAt(2) == 'f';
                    int     srcIdx   = Character.getNumericValue(multiStepCard[0].charAt(3));
                    boolean srcIsP1  = multiStepCard[0].startsWith("p1");
                    String[] srcRow  = srcFront ? (srcIsP1 ? stRef[0].p1Front : stRef[0].p2Front)
                                                : (srcIsP1 ? stRef[0].p1Back  : stRef[0].p2Back);
                    String srcCardId = BattleState.slotId(srcRow[srcIdx]);
                    if ("itb001".equals(srcCardId)) {
                        // Iron Tusks: apply fortify directly
                        String result = AbilityResolver.fortify(stRef[0], amP1, scrapSelected[0], cardMap);
                        stRef[0].useAction(srcIsP1, srcFront, srcIdx);
                        stRef[0].abilityUsedThisTurn.add(multiStepCard[0]);
                        msg[0] = withHarvest(result, AbilityResolver.onAbilityUsed(stRef[0], amP1));
                        multiStepPhase[0] = null; multiStepCard[0] = null; scrapSelected[0].clear();
                        selField[0] = null;
                        stRef[0].save(); rebuildRef[0].run();
                    } else if ("fnb001".equals(srcCardId)) {
                        // Furnace Bot: move to bot-target phase
                        multiStepPhase[0] = "BOT_TARGET";
                        msg[0] = "Select a bot to receive +" + (scrapSelected[0].size()*2) + " ATK & HP";
                        rebuildRef[0].run();
                    }
                });
                btns.add(doneBtn);
            }

            // Card ability button — shown when a regular card with an active ability is selected
            if (selField[0] != null && abilitySource[0] == null && multiStepPhase[0] == null) {
                boolean srcFront = selField[0].charAt(2) == 'f';
                int     srcIdx   = Character.getNumericValue(selField[0].charAt(3));
                boolean srcIsP1  = selField[0].startsWith("p1");
                String[] srcRow  = srcFront ? (srcIsP1 ? stRef[0].p1Front : stRef[0].p2Front)
                                            : (srcIsP1 ? stRef[0].p1Back  : stRef[0].p2Back);
                String srcSv = srcRow[srcIdx];
                if (srcSv != null && !srcSv.isEmpty()) {
                    String srcCardId = BattleState.slotId(srcSv);
                    Card srcCard = cardMap.get(srcCardId);
                    String aType = AbilityResolver.abilityType(srcCardId);
                    boolean isMultiStep = "fnb001".equals(srcCardId) || "itb001".equals(srcCardId);
                    boolean isConstructor = "cnb001".equals(srcCardId);
                    boolean isEcho = "ecs001".equals(srcCardId);
                    if (srcCard != null && ("active".equals(aType) || "targeted".equals(aType) || isMultiStep || isConstructor || isEcho)
                            && !(srcCard instanceof Champion)) {
                        int mySouls   = amP1 ? stRef[0].p1Souls   : stRef[0].p2Souls;
                        int mySoulCap = amP1 ? stRef[0].p1SoulCap : stRef[0].p2SoulCap;
                        boolean voidBlocked = AbilityResolver.isAbilityNullified(amP1, stRef[0]);
                        boolean ablEnabled = !voidBlocked && (isEcho
                                ? mySoulCap >= 1
                                : AbilityResolver.canUseAbility(srcCardId, stRef[0], amP1));
                        String lblTxt = srcCard.getName() + ": Use Ability"
                                        + (voidBlocked ? " [Void]" : (ablEnabled ? "" : " (can't afford)"));
                        JButton ablBtn = smallBtn(lblTxt, ablEnabled ? SEL_ABL : new Color(80, 80, 100));
                        ablBtn.setEnabled(ablEnabled);
                        ablBtn.addActionListener(e -> {
                            if (isEcho) {
                                // Echo Spirit: enter copy select phase
                                multiStepCard[0]  = selField[0];
                                multiStepPhase[0] = "ECHO_COPY_SELECT";
                                echoCopiedCard[0] = null;
                                selField[0] = null;
                                msg[0] = "Echo: select a friendly card to copy its ability";
                                rebuildRef[0].run();
                                return;
                            }
                            if (isConstructor) {
                                // Constructor Bot: pick a bot from hand costing ≤3
                                List<String> hand = amP1 ? stRef[0].p1Hand : stRef[0].p2Hand;
                                List<Card> eligible = new ArrayList<>();
                                for (String id : hand) {
                                    Card c = cardMap.get(id);
                                    if (c != null && "bot".equals(c.getType()) && c.getCost() <= 3)
                                        eligible.add(c);
                                }
                                if (eligible.isEmpty()) {
                                    msg[0] = "Construct: no bots costing ≤3 in hand.";
                                    rebuildRef[0].run(); return;
                                }
                                String[] opts = eligible.stream()
                                    .map(c -> c.getName() + " (" + c.getCost() + " soul)")
                                    .toArray(String[]::new);
                                int choice = JOptionPane.showOptionDialog(null,
                                    "Choose a bot to summon (costs 2 Scrap):", "Constructor Bot",
                                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                                    null, opts, opts[0]);
                                if (choice < 0) return;
                                String result = AbilityResolver.construct(stRef[0], amP1,
                                    eligible.get(choice).getId(), cardMap);
                                stRef[0].useAction(srcIsP1, srcFront, srcIdx);
                                stRef[0].abilityUsedThisTurn.add(selField[0]);
                                msg[0] = withHarvest(result, AbilityResolver.onAbilityUsed(stRef[0], amP1));
                                selField[0] = null;
                                stRef[0].save(); rebuildRef[0].run();
                            } else if (isMultiStep) {
                                // Furnace Bot / Iron Tusks Bot: enter scrap-select mode
                                multiStepCard[0]  = selField[0];
                                multiStepPhase[0] = "SCRAP_SELECT";
                                scrapSelected[0].clear();
                                selField[0] = null;
                                msg[0] = "Select Scraps to use, then press Done";
                                rebuildRef[0].run();
                            } else if (AbilityResolver.needsTarget(srcCardId)) {
                                // Upgrade Bot: ask ATK or HP choice before targeting
                                if ("upb001".equals(srcCardId)) {
                                    String[] opts = {"+ 2 ATK", "+ 2 HP"};
                                    int choice = JOptionPane.showOptionDialog(null,
                                        "Upgrade Bot: choose buff", "Upgrade",
                                        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                                        null, opts, opts[0]);
                                    if (choice < 0) return;
                                    abilityChoice[0] = choice;
                                }
                                abilitySource[0]  = selField[0];
                                abilityTgtType[0] = AbilityResolver.TARGET_TYPE.get(srcCardId);
                                selField[0]       = null;
                                msg[0] = "Select a target for " + srcCard.getName() + "'s ability";
                            } else {
                                doCardAbilityNoTarget(stRef[0], cardMap, champLines, selField[0], amP1,
                                                      stRef, selHand, selField, msg, rebuildRef);
                            }
                            rebuildRef[0].run();
                        });
                        btns.add(ablBtn);
                    }
                }
            }

            // Champion ability button
            String champId = AbilityResolver.currentChampId(stRef[0], amP1);
            Card champCard = champId != null ? cardMap.get(champId) : null;
            if (champCard instanceof Champion) {
                Champion ch = (Champion) champCard;
                if (!ch.getAbility().isEmpty()) {
                    boolean champActed   = !stRef[0].hasAction(amP1, false, BattleState.CHAMP_SLOT);
                    boolean voidBlkChamp = AbilityResolver.isAbilityNullified(amP1, stRef[0]);
                    String btnLabel = ch.getName() + ": " + ch.getAbility().split("[-–]")[0].trim()
                                      + (voidBlkChamp ? " [Void]" : "");
                    Color  btnColor = bypass[0] ? new Color(255, 160, 60) : new Color(220, 180, 60);
                    JButton abilityBtn = smallBtn(btnLabel, btnColor);
                    abilityBtn.setEnabled(!champActed && !voidBlkChamp);
                    abilityBtn.addActionListener(ev ->
                        doChampionAbility(stRef[0], cardMap, champLines, amP1,
                                          stRef, selHand, selField, msg, rebuildRef, bypass,
                                          multiStepPhase, multiStepCard, echoCopiedCard));
                    btns.add(abilityBtn);
                }
            }

            JButton endBtn = smallBtn("End Turn", new Color(100, 180, 255));
            endBtn.addActionListener(e -> {
                msg[0] = "";
                abilitySource[0] = null;
                doEndTurn(amP1, stRef, selHand, selField, msg, rebuildRef, cardMap, champLines);
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

    private static boolean isCopyableAbility(String cardId) {
        String aType = AbilityResolver.abilityType(cardId);
        if (!"active".equals(aType) && !"targeted".equals(aType)) return false;
        // Exclude multi-step and self-referential abilities
        return !"fnb001".equals(cardId) && !"itb001".equals(cardId)
            && !"cnb001".equals(cardId) && !"ecs001".equals(cardId);
    }

    private static String getCardIdAtPosKey(BattleState st, String posKey) {
        if (posKey == null) return null;
        boolean isP1   = posKey.startsWith("p1");
        boolean isFront = posKey.charAt(2) == 'f';
        int idx = Character.getNumericValue(posKey.charAt(3));
        String[] row = isFront ? (isP1 ? st.p1Front : st.p2Front)
                               : (isP1 ? st.p1Back  : st.p2Back);
        return (row != null && idx >= 0 && idx < row.length) ? BattleState.slotId(row[idx]) : null;
    }

    private static void waterSpiritBuff(BattleState st, boolean ownerIsP1) {
        String[][] rows = ownerIsP1
            ? new String[][]{ st.p1Front, st.p1Back }
            : new String[][]{ st.p2Front, st.p2Back };
        for (String[] row : rows) {
            for (int i = 0; i < 5; i++) {
                if (row[i] != null && !row[i].isEmpty()) {
                    row[i] = BattleState.makeSlot(BattleState.slotId(row[i]),
                                                   BattleState.slotHp(row[i]) + 2);
                }
            }
        }
    }

    private static void torchBuff(BattleState st, boolean ownerIsP1) {
        String[][] rows = ownerIsP1
            ? new String[][]{ st.p1Front, st.p1Back }
            : new String[][]{ st.p2Front, st.p2Back };
        for (boolean isFront : new boolean[]{true, false}) {
            String[] row = isFront ? (ownerIsP1 ? st.p1Front : st.p2Front)
                                   : (ownerIsP1 ? st.p1Back  : st.p2Back);
            for (int i = 0; i < 5; i++) {
                if (row[i] != null && !row[i].isEmpty()) {
                    String pk = BattleState.posKey(ownerIsP1, isFront, i);
                    row[i] = BattleState.makeSlot(BattleState.slotId(row[i]),
                                                   BattleState.slotHp(row[i]) + 2);
                    st.fieldAtkBonus.merge(pk, 2, Integer::sum);
                }
            }
        }
    }

    private static JLabel lbl(String text, int style, int size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", style, size));
        l.setForeground(color);
        return l;
    }

    private static JLabel lbl(String text, Font font, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font);
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

    private static JButton infoButton(Color bg, String cardName, String abilityText) {
        JButton btn = new JButton("ℹ");
        btn.setFont(FONT_BOLD_10);
        btn.setForeground(new Color(220, 200, 120));
        btn.setBackground(bg);
        btn.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        String abl = abilityText.isEmpty() ? "No ability." : abilityText;
        btn.addActionListener(ae -> JOptionPane.showMessageDialog(btn,
                "<html><b>" + cardName + "</b><br><br>" + abl + "</html>",
                "Ability", JOptionPane.INFORMATION_MESSAGE));
        return btn;
    }

    private static String withHarvest(String result, String harvestMsg) {
        return harvestMsg.isEmpty() ? result : result + " " + harvestMsg;
    }

    // ── Beginner Mage ability ─────────────────────────────────────────────────

    private static void doBeginnerMageAttack(BattleState st, Map<String, Card> cardMap,
            Map<String, ChampionLine> champLines,
            String posKey, boolean isFront, int idx, boolean fieldIsP1, boolean amP1,
            BattleState[] stRef, int[] selHand, String[] selField, String[] msg, Runnable[] rebuildRef) {
        int cost = AbilityResolver.getEffectiveSoulCost("bgm001", st, amP1);
        if (amP1) st.p1Souls -= cost; else st.p2Souls -= cost;

        boolean enemyIsP1 = !amP1;
        String[] eFront = enemyIsP1 ? st.p1Front : st.p2Front;
        StringBuilder sb = new StringBuilder("Beginner Mage: hit");
        int hits = 0;
        for (int col = idx - 1; col <= idx + 1; col++) {
            if (col < 0 || col > 4) continue;
            if (eFront[col] == null || eFront[col].isEmpty()) continue;
            String tgtId = BattleState.slotId(eFront[col]);
            Card tgtC = cardMap.get(tgtId);
            if (tgtC == null) continue;
            int reduction = AbilityResolver.passiveDamageReduction(tgtId);
            int dmg2 = Math.max(0, 1 - reduction);
            int newHp = BattleState.slotHp(eFront[col]) - dmg2;
            String tgtPk = BattleState.posKey(enemyIsP1, true, col);
            if (newHp <= 0) {
                if (tgtC instanceof Champion) {
                    killOrAdvanceChampion(st, champLines, eFront, col, tgtPk, enemyIsP1, (Champion) tgtC, msg, "killed by Beginner Mage");
                    sb.append(" ").append(tgtC.getName()).append(" ✕");
                } else {
                    String sporedMsg = handleSporedDeath(st, tgtPk, enemyIsP1, cardMap);
                    eFront[col] = "";
                    st.clearCardState(tgtPk);
                    boolean decayActive = amP1 ? st.p1MageDecayRounds > 0 : st.p2MageDecayRounds > 0;
                    if (!decayActive) {
                        if (!"item".equals(tgtC.getType())) {
                            if (enemyIsP1) st.p1SoulCap++; else st.p2SoulCap++;
                            if (AbilityResolver.hasSoulSipper(st, amP1)) {
                                int cur = amP1 ? st.p1Souls : st.p2Souls;
                                int cap = amP1 ? st.p1SoulCap : st.p2SoulCap;
                                if (amP1) st.p1Souls = Math.min(cur + 1, cap); else st.p2Souls = Math.min(cur + 1, cap);
                            }
                        }
                        if (enemyIsP1) st.p1Discard.add(tgtId); else st.p2Discard.add(tgtId);
                        AbilityResolver.onDeath(st, tgtId, enemyIsP1, cardMap);
                    }
                    sb.append(" ").append(tgtC.getName()).append(" ✕").append(sporedMsg);
                }
            } else if (newHp > 0) {
                eFront[col] = BattleState.makeSlot(tgtId, newHp);
                sb.append(" ").append(tgtC.getName()).append("(").append(dmg2).append(")");
            }
            hits++;
        }
        if (hits == 0) { msg[0] = "Beginner Mage: no adjacent enemies!"; rebuildRef[0].run(); return; }
        st.useAction(fieldIsP1, isFront, idx);
        st.abilityUsedThisTurn.add(posKey);
        msg[0] = withHarvest(sb.toString(), AbilityResolver.onAbilityUsed(st, amP1));
        selField[0] = null;
        st.save(); stRef[0] = st;
        rebuildRef[0].run();
    }

    // ── Fire Mage ability ─────────────────────────────────────────────────────

    private static void doFireMageBlast(BattleState st, Map<String, Card> cardMap,
            Map<String, ChampionLine> champLines,
            String posKey, boolean isFront, int idx, boolean fieldIsP1, boolean amP1,
            BattleState[] stRef, int[] selHand, String[] selField, String[] msg, Runnable[] rebuildRef) {
        int cost = AbilityResolver.getEffectiveSoulCost("frm001", st, amP1);
        if (amP1) st.p1Souls -= cost; else st.p2Souls -= cost;

        boolean enemyIsP1 = !amP1;
        String[] eFront = enemyIsP1 ? st.p1Front : st.p2Front;
        StringBuilder sb = new StringBuilder("Fire Mage:");
        int hits = 0;
        for (int col = 0; col < 5 && hits < 3; col++) {
            if (eFront[col] == null || eFront[col].isEmpty()) continue;
            String tgtId = BattleState.slotId(eFront[col]);
            Card tgtC = cardMap.get(tgtId);
            if (tgtC == null) continue;
            int reduction = AbilityResolver.passiveDamageReduction(tgtId);
            int dmg2 = Math.max(0, 3 - reduction);
            int newHp = BattleState.slotHp(eFront[col]) - dmg2;
            String tgtPk = BattleState.posKey(enemyIsP1, true, col);
            if (newHp <= 0) {
                if (tgtC instanceof Champion) {
                    killOrAdvanceChampion(st, champLines, eFront, col, tgtPk, enemyIsP1, (Champion) tgtC, msg, "killed by Fire Mage");
                    sb.append(" ").append(tgtC.getName()).append(" ✕");
                } else {
                    String sporedMsg = handleSporedDeath(st, tgtPk, enemyIsP1, cardMap);
                    eFront[col] = "";
                    st.clearCardState(tgtPk);
                    boolean decayActive = amP1 ? st.p1MageDecayRounds > 0 : st.p2MageDecayRounds > 0;
                    if (!decayActive) {
                        if (!"item".equals(tgtC.getType())) {
                            if (enemyIsP1) st.p1SoulCap++; else st.p2SoulCap++;
                            if (AbilityResolver.hasSoulSipper(st, amP1)) {
                                int cur = amP1 ? st.p1Souls : st.p2Souls;
                                int cap = amP1 ? st.p1SoulCap : st.p2SoulCap;
                                if (amP1) st.p1Souls = Math.min(cur + 1, cap); else st.p2Souls = Math.min(cur + 1, cap);
                            }
                        }
                        if (enemyIsP1) st.p1Discard.add(tgtId); else st.p2Discard.add(tgtId);
                        AbilityResolver.onDeath(st, tgtId, enemyIsP1, cardMap);
                    }
                    sb.append(" ").append(tgtC.getName()).append(" ✕").append(sporedMsg);
                }
            } else if (newHp > 0) {
                eFront[col] = BattleState.makeSlot(tgtId, newHp);
                sb.append(" ").append(tgtC.getName()).append("(-").append(dmg2).append(")");
            }
            hits++;
        }
        if (hits == 0) { msg[0] = "Fire Mage: no enemies in frontline!"; rebuildRef[0].run(); return; }
        st.useAction(fieldIsP1, isFront, idx);
        st.abilityUsedThisTurn.add(posKey);
        msg[0] = withHarvest(sb.toString(), AbilityResolver.onAbilityUsed(st, amP1));
        selField[0] = null;
        st.save(); stRef[0] = st;
        rebuildRef[0].run();
    }

    // ── Ice Mage Apprentice ability ───────────────────────────────────────────

    private static void doIceMageApprenticeCast(BattleState st, Map<String, Card> cardMap,
            String posKey, boolean isFront, int idx, boolean fieldIsP1, boolean amP1,
            BattleState[] stRef, int[] selHand, String[] selField, String[] msg, Runnable[] rebuildRef) {
        int cost = AbilityResolver.getEffectiveSoulCost("ima001", st, amP1);
        if (amP1) st.p1Souls -= cost; else st.p2Souls -= cost;

        boolean enemyIsP1 = !amP1;
        List<String> targets = new ArrayList<>();
        String[] eFront = enemyIsP1 ? st.p1Front : st.p2Front;
        String[] eBack  = enemyIsP1 ? st.p1Back  : st.p2Back;
        for (int i = 0; i < 5; i++) {
            if (eFront[i] != null && !eFront[i].isEmpty())
                targets.add(BattleState.posKey(enemyIsP1, true,  i));
            if (eBack[i]  != null && !eBack[i].isEmpty())
                targets.add(BattleState.posKey(enemyIsP1, false, i));
        }
        if (targets.isEmpty()) {
            // Refund if no targets
            if (amP1) st.p1Souls += cost; else st.p2Souls += cost;
            msg[0] = "Ice Mage: no enemies to freeze!";
            rebuildRef[0].run(); return;
        }

        int frozen = 0;
        for (int flip = 0; flip < 3; flip++) {
            if (Math.random() < 0.5) {
                String pk = targets.get((int)(Math.random() * targets.size()));
                st.frozenCards.put(pk, Math.max(st.frozenCards.getOrDefault(pk, 0), 4));
                boolean pkIsP1  = pk.startsWith("p1");
                boolean pkFront = pk.charAt(2) == 'f';
                int pkIdx = Character.getNumericValue(pk.charAt(3));
                String[] row = pkFront ? (pkIsP1 ? st.p1Front : st.p2Front)
                                       : (pkIsP1 ? st.p1Back  : st.p2Back);
                if (row[pkIdx] != null && !row[pkIdx].isEmpty()) {
                    String tgtId = BattleState.slotId(row[pkIdx]);
                    Card tgtC = cardMap.get(tgtId);
                    int reduction = tgtC != null ? AbilityResolver.passiveDamageReduction(tgtId) : 0;
                    int dmg2 = Math.max(0, 3 - reduction);
                    int newHp = BattleState.slotHp(row[pkIdx]) - dmg2;
                    if (newHp <= 0 && tgtC != null && !(tgtC instanceof Champion)) {
                        String sporedMsg = handleSporedDeath(st, pk, pkIsP1, cardMap);
                        row[pkIdx] = "";
                        st.clearCardState(pk);
                        boolean decayActive = amP1 ? st.p1MageDecayRounds > 0 : st.p2MageDecayRounds > 0;
                        if (!decayActive) {
                            if (!"item".equals(tgtC.getType())) {
                                if (pkIsP1) st.p1SoulCap++; else st.p2SoulCap++;
                                if (AbilityResolver.hasSoulSipper(st, amP1)) {
                                    int cur = amP1 ? st.p1Souls : st.p2Souls;
                                    int cap = amP1 ? st.p1SoulCap : st.p2SoulCap;
                                    if (amP1) st.p1Souls = Math.min(cur + 1, cap); else st.p2Souls = Math.min(cur + 1, cap);
                                }
                            }
                            if (pkIsP1) st.p1Discard.add(tgtId); else st.p2Discard.add(tgtId);
                            AbilityResolver.onDeath(st, tgtId, pkIsP1, cardMap);
                        }
                        targets.remove(pk); // no longer targetable
                    } else if (newHp > 0) {
                        row[pkIdx] = BattleState.makeSlot(tgtId, newHp);
                    }
                }
                frozen++;
            }
        }
        st.useAction(fieldIsP1, isFront, idx);
        st.abilityUsedThisTurn.add(posKey);
        msg[0] = withHarvest(
            frozen == 0 ? "Ice Mage: all tails — no enemies frozen!"
                        : "Ice Mage: froze " + frozen + " enem" + (frozen > 1 ? "ies" : "y") + " and dealt 3 dmg!",
            AbilityResolver.onAbilityUsed(st, amP1));
        selField[0] = null;
        st.save(); stRef[0] = st;
        rebuildRef[0].run();
    }

    // ── Time Wizard ability ───────────────────────────────────────────────────

    private static void doTimeWizardSkip(BattleState st, Map<String, Card> cardMap,
            Map<String, ChampionLine> champLines,
            String posKey, boolean isFront, int idx, boolean fieldIsP1, boolean amP1,
            BattleState[] stRef, int[] selHand, String[] selField, String[] msg, Runnable[] rebuildRef) {
        if (amP1) { st.p1Souls -= 10; st.p1SoulCap -= 10; }
        else       { st.p2Souls -= 10; st.p2SoulCap -= 10; }

        // Apply 6 end-of-turn ticks (3 rounds × 2 turns each)
        String[] tickMsg = { "" };
        for (int round = 0; round < 6; round++) {
            applyEndTurnEffects(st, cardMap, champLines, tickMsg);
            if ("finished".equals(st.phase)) break;
        }

        // Opponent draws 3 cards
        boolean nextIsP1 = !amP1;
        List<String> nextDeck = nextIsP1 ? st.p1Deck : st.p2Deck;
        List<String> nextHand = nextIsP1 ? st.p1Hand : st.p2Hand;
        for (int d = 0; d < 3 && !nextDeck.isEmpty(); d++) nextHand.add(nextDeck.remove(0));

        st.useAction(fieldIsP1, isFront, idx);
        st.abilityUsedThisTurn.add(posKey);
        String baseMsg = "Time Wizard: skipped 3 rounds!";
        if (!tickMsg[0].isEmpty()) baseMsg += " " + tickMsg[0];
        msg[0] = withHarvest(baseMsg, AbilityResolver.onAbilityUsed(st, amP1));
        selField[0] = null;
        // End turn normally
        doEndTurn(amP1, stRef, selHand, selField, msg, rebuildRef, cardMap, champLines);
    }

    // ── End-turn effects helper (extracted for Time Wizard) ───────────────────

    private static void applyEndTurnEffects(BattleState st, Map<String, Card> cardMap,
                                             Map<String, ChampionLine> champLines, String[] msg) {
        // ── Apply burn damage ──────────────────────────────────────────────
        for (String posKey : new ArrayList<>(st.burnedCards.keySet())) {
            boolean posIsP1 = posKey.startsWith("p1");
            boolean posFront = posKey.charAt(2) == 'f';
            int posIdx = Character.getNumericValue(posKey.charAt(3));
            String[] row = posFront ? (posIsP1 ? st.p1Front : st.p2Front)
                                    : (posIsP1 ? st.p1Back  : st.p2Back);
            if (row[posIdx] == null || row[posIdx].isEmpty()) { st.burnedCards.remove(posKey); continue; }
            String cId = BattleState.slotId(row[posIdx]);
            int    chp = BattleState.slotHp(row[posIdx]);
            Card   bc  = cardMap.get(cId);
            int    newHp = chp - 1;
            if (newHp <= 0) {
                if (bc instanceof Champion) {
                    killOrAdvanceChampion(st, champLines, row, posIdx, posKey, posIsP1, (Champion) bc, msg, "burned to death");
                } else {
                    String burnSporedMsg = handleSporedDeath(st, posKey, posIsP1, cardMap);
                    row[posIdx] = "";
                    st.clearCardState(posKey);
                    if (bc != null && !"item".equals(bc.getType())) {
                        if (posIsP1) st.p1SoulCap++; else st.p2SoulCap++;
                        boolean burnKillerIsP1 = !posIsP1;
                        if (AbilityResolver.hasSoulSipper(st, burnKillerIsP1)) {
                            int cur = burnKillerIsP1 ? st.p1Souls : st.p2Souls;
                            int cap = burnKillerIsP1 ? st.p1SoulCap : st.p2SoulCap;
                            if (burnKillerIsP1) st.p1Souls = Math.min(cur + 1, cap);
                            else                st.p2Souls = Math.min(cur + 1, cap);
                        }
                    }
                    String deathMsg = AbilityResolver.onDeath(st, cId, posIsP1, cardMap);
                    if (posIsP1) st.p1Discard.add(cId); else st.p2Discard.add(cId);
                    String burnNotice = (bc != null ? bc.getName() : cId) + " burned to death!";
                    if (!deathMsg.isEmpty()) burnNotice += " " + deathMsg;
                    burnNotice += burnSporedMsg;
                    msg[0] = burnNotice;
                }
            } else {
                row[posIdx] = BattleState.makeSlot(cId, newHp);
            }
        }
        // ── Apply decay damage (−1 ATK and −1 HP per occupied decayed card) ───
        for (String posKey : new ArrayList<>(st.decayedCards.keySet())) {
            boolean posIsP1 = posKey.startsWith("p1");
            boolean posFront = posKey.charAt(2) == 'f';
            int posIdx = Character.getNumericValue(posKey.charAt(3));
            String[] row = posFront ? (posIsP1 ? st.p1Front : st.p2Front)
                                    : (posIsP1 ? st.p1Back  : st.p2Back);
            if (row[posIdx] == null || row[posIdx].isEmpty()) {
                st.decayedCards.remove(posKey); continue;
            }
            int turns = st.decayedCards.get(posKey) - 1;
            if (turns <= 0) st.decayedCards.remove(posKey);
            else st.decayedCards.put(posKey, turns);
            // Apply −1 ATK and −1 HP
            st.fieldAtkBonus.merge(posKey, -1, Integer::sum);
            String cId = BattleState.slotId(row[posIdx]);
            int    chp = BattleState.slotHp(row[posIdx]);
            Card   dc  = cardMap.get(cId);
            int    newHp = chp - 1;
            if (newHp <= 0 && dc != null) {
                if (dc instanceof Champion) {
                    killOrAdvanceChampion(st, champLines, row, posIdx, posKey, posIsP1, (Champion) dc, msg, "decayed to death");
                } else {
                    String sporedMsg = handleSporedDeath(st, posKey, posIsP1, cardMap);
                    row[posIdx] = "";
                    st.clearCardState(posKey);
                    if (!"item".equals(dc.getType())) {
                        if (posIsP1) st.p1SoulCap++; else st.p2SoulCap++;
                    }
                    boolean killerIsP1 = !posIsP1;
                    if (AbilityResolver.hasSoulSipper(st, killerIsP1)) {
                        int cur = killerIsP1 ? st.p1Souls : st.p2Souls;
                        int cap = killerIsP1 ? st.p1SoulCap : st.p2SoulCap;
                        if (killerIsP1) st.p1Souls = Math.min(cur + 1, cap);
                        else             st.p2Souls = Math.min(cur + 1, cap);
                    }
                    String deathMsg = AbilityResolver.onDeath(st, cId, posIsP1, cardMap);
                    if (posIsP1) st.p1Discard.add(cId); else st.p2Discard.add(cId);
                    String notice = dc.getName() + " decayed to death!" + (deathMsg.isEmpty() ? "" : " " + deathMsg) + sporedMsg;
                    msg[0] = msg[0].isEmpty() ? notice : msg[0] + " | " + notice;
                }
            } else if (newHp > 0) {
                row[posIdx] = BattleState.makeSlot(cId, newHp);
            }
        }
        // ── Apply poison damage ────────────────────────────────────────────
        for (String posKey : new ArrayList<>(st.poisonedCards)) {
            boolean posIsP1 = posKey.startsWith("p1");
            boolean posFront = posKey.charAt(2) == 'f';
            int posIdx = Character.getNumericValue(posKey.charAt(3));
            String[] row = posFront ? (posIsP1 ? st.p1Front : st.p2Front)
                                    : (posIsP1 ? st.p1Back  : st.p2Back);
            if (row[posIdx] == null || row[posIdx].isEmpty()) { st.poisonedCards.remove(posKey); continue; }
            String cId = BattleState.slotId(row[posIdx]);
            int    chp = BattleState.slotHp(row[posIdx]);
            Card   pc  = cardMap.get(cId);
            int    newHp = chp - 1;
            if (newHp <= 0) {
                if (pc instanceof Champion) {
                    killOrAdvanceChampion(st, champLines, row, posIdx, posKey, posIsP1, (Champion) pc, msg, "died from poison");
                } else {
                    String poisonSporedMsg = handleSporedDeath(st, posKey, posIsP1, cardMap);
                    row[posIdx] = "";
                    st.clearCardState(posKey);
                    if (pc != null && !"item".equals(pc.getType())) {
                        if (posIsP1) st.p1SoulCap++; else st.p2SoulCap++;
                        boolean poisonKillerIsP1 = !posIsP1;
                        if (AbilityResolver.hasSoulSipper(st, poisonKillerIsP1)) {
                            int cur = poisonKillerIsP1 ? st.p1Souls : st.p2Souls;
                            int cap = poisonKillerIsP1 ? st.p1SoulCap : st.p2SoulCap;
                            if (poisonKillerIsP1) st.p1Souls = Math.min(cur + 1, cap);
                            else                  st.p2Souls = Math.min(cur + 1, cap);
                        }
                    }
                    String deathMsg = AbilityResolver.onDeath(st, cId, posIsP1, cardMap);
                    if (posIsP1) st.p1Discard.add(cId); else st.p2Discard.add(cId);
                    String poisonNotice = (pc != null ? pc.getName() : cId) + " died from poison!";
                    if (!deathMsg.isEmpty()) poisonNotice += " " + deathMsg;
                    poisonNotice += poisonSporedMsg;
                    msg[0] = poisonNotice;
                }
            } else {
                row[posIdx] = BattleState.makeSlot(cId, newHp);
            }
        }
        // ── Flame Bloomling: +2 HP per burned card ─────────────────────────
        int burnedCount = st.burnedCards.size();
        if (burnedCount > 0) {
            String[][] fblRows = {st.p1Front, st.p1Back, st.p2Front, st.p2Back};
            for (int ri = 0; ri < 4; ri++) {
                String[] row = fblRows[ri];
                for (int i = 0; i < 5; i++) {
                    if ("fbl001".equals(BattleState.slotId(row[i]))) {
                        int gain = burnedCount * 2;
                        row[i] = BattleState.makeSlot("fbl001", BattleState.slotHp(row[i]) + gain);
                    }
                }
            }
        }
        // ── Decrement spored turns ─────────────────────────────────────────
        for (String posKey : new ArrayList<>(st.sporedCards.keySet())) {
            int turns = st.sporedCards.get(posKey) - 1;
            if (turns <= 0) st.sporedCards.remove(posKey);
            else st.sporedCards.put(posKey, turns);
        }
        // ── Fungal Domain aura ─────────────────────────────────────────────
        boolean domainActive = st.p1FungalDomain > 0 || st.p2FungalDomain > 0;
        if (st.p1FungalDomain > 0) st.p1FungalDomain--;
        if (st.p2FungalDomain > 0) st.p2FungalDomain--;
        if (domainActive) {
            String domainMsg = AbilityResolver.applyFungalDomainAura(st, cardMap);
            if (!domainMsg.isEmpty()) {
                msg[0] = msg[0].isEmpty() ? domainMsg : msg[0] + " | " + domainMsg;
            }
        }
        // ── Decrement frozen turns ─────────────────────────────────────────
        for (String posKey : new ArrayList<>(st.frozenCards.keySet())) {
            int turns = st.frozenCards.get(posKey) - 1;
            if (turns <= 0) st.frozenCards.remove(posKey);
            else st.frozenCards.put(posKey, turns);
        }
        // ── Decrement sealed slot timers ───────────────────────────────────────
        for (String posKey : new ArrayList<>(st.sealedSlots.keySet())) {
            int turns = st.sealedSlots.get(posKey) - 1;
            if (turns <= 0) st.sealedSlots.remove(posKey);
            else st.sealedSlots.put(posKey, turns);
        }
        // ── Decrement mage decay rounds ────────────────────────────────────
        if (st.p1MageDecayRounds > 0) st.p1MageDecayRounds--;
        if (st.p2MageDecayRounds > 0) st.p2MageDecayRounds--;
        // ── Transform countdown ────────────────────────────────────────────
        for (String txKey : new ArrayList<>(st.transformCounters.keySet())) {
            int turns = st.transformCounters.get(txKey) - 1;
            if (turns <= 0) {
                st.transformCounters.remove(txKey);
                applyTransform(st, txKey, cardMap, msg);
            } else {
                st.transformCounters.put(txKey, turns);
            }
        }
        // ── Druid aura ─────────────────────────────────────────────────────
        for (boolean pSide : new boolean[]{true, false}) {
            String[] frt = pSide ? st.p1Front : st.p2Front;
            String[] bck = pSide ? st.p1Back  : st.p2Back;
            boolean hasDruid = false;
            for (String s : frt) if ("dru001".equals(BattleState.slotId(s))) { hasDruid = true; break; }
            if (!hasDruid)
                for (String s : bck) if ("dru001".equals(BattleState.slotId(s))) { hasDruid = true; break; }
            if (!hasDruid) continue;
            for (int pass = 0; pass < 2; pass++) {
                String[] row = pass == 0 ? frt : bck;
                for (int i = 0; i < 5; i++) {
                    String cid = BattleState.slotId(row[i]);
                    Card c = cid != null ? cardMap.get(cid) : null;
                    if (c == null || !"pod".equals(c.getType())) continue;
                    String pk = BattleState.posKey(pSide, pass == 0, i);
                    st.fieldAtkBonus.merge(pk, 1, Integer::sum);
                    row[i] = BattleState.makeSlot(cid, BattleState.slotHp(row[i]) + 1);
                }
            }
        }
    }

    private static boolean killOrAdvanceChampion(BattleState st, Map<String, ChampionLine> champLines,
                                                   String[] row, int idx, String posKey,
                                                   boolean champIsP1, Champion champ,
                                                   String[] msg, String notice) {
        String lineId = champ.getLineId();
        ChampionLine line = champLines.get(lineId);
        if (line != null && !champ.isFinalStage(line)) {
            Champion next = line.getStageByIndex(champ.getStage());
            if (next != null) {
                row[idx] = BattleState.makeSlot(next.getId(), next.getHp());
                st.actionsUsed.remove(posKey);
                String adv = champ.getName() + " " + notice + " — evolved to " + next.getName() + "!";
                msg[0] = msg[0].isEmpty() ? adv : msg[0] + " | " + adv;
                return false;
            }
        }
        row[idx] = "";
        st.clearCardState(posKey);
        st.phase  = "finished";
        st.winner = champIsP1 ? st.player2 : st.player1;
        return true;
    }

    private static String handleSporedDeath(BattleState st, String posKey, boolean deadOwnerIsP1,
                                             Map<String, Card> cardMap) {
        if (!st.sporedCards.containsKey(posKey)) return "";
        boolean oppIsP1 = !deadOwnerIsP1;
        String[] front = oppIsP1 ? st.p1Front : st.p2Front;
        for (int i = 0; i < 5; i++) {
            if (front[i] == null || front[i].isEmpty()) {
                front[i] = BattleState.makeSlot("fgp001", 2);
                st.transformCounters.put(BattleState.posKey(oppIsP1, true, i), 2);
                return " Spore: a Fungal Pod erupted on the enemy frontline!";
            }
        }
        return " Spore: enemy frontline full, Fungal Pod lost!";
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
