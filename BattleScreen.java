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
    private static final Color SEL_ABL   = new Color(220, 180, 60);  // ability target highlight
    private static final Color CHAMP_CLR = new Color(220, 180, 60);

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
            File audioFile = new File("5382405984223232.wav");
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

        rebuildRef[0] = () -> {
            BattleState st = stRef[0];
            if (st == null) return;
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
        JPanel row = new JPanel(new GridLayout(1, 5, 4, 0));
        row.setBackground(bg);
        row.setBorder(new EmptyBorder(3, 0, 3, 0));
        for (int i = 0; i < 5; i++)
            row.add(slot(st, cardMap, fieldIsP1, isFront, i, amP1, myTurn,
                         selHand, selField, abilitySource, abilityTgtType, abilityChoice, msg,
                         stRef, user, wrapper, battleId, onComplete, rebuildRef, bypass, champLines,
                         multiStepPhase, multiStepCard, scrapSelected, echoCopiedCard));
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
        // Bat Eye: cannot be targeted by enemy frontline cards
        boolean atkIsFrontline = selField[0] != null && selField[0].charAt(2) == 'f';
        boolean batEyeBlocked  = "bte001".equals(cardId) && atkIsFrontline;

        boolean anyNewPhase = inEchoCopySelect || inMimicSelect || inCopyTargetSelect;
        boolean canPlace  = isMyField && empty  && myTurn && selHand[0] >= 0 && abilitySource[0] == null && !inScrapSelect && !inBotTarget && !anyNewPhase;
        boolean canSelect = isMyField && !empty && myTurn && hasAct && !isFrozenCard && selHand[0] < 0 && abilitySource[0] == null && !inScrapSelect && !inBotTarget && !anyNewPhase;
        boolean canTarget = !isMyField && !empty && myTurn && selField[0] != null && abilitySource[0] == null
                             && !inScrapSelect && !inBotTarget && !anyNewPhase && !batEyeBlocked
                             && ((bypass[0] || atkIsDreamWanderer)
                                 ? st.isTargetableBypass(fieldIsP1, isFront, idx)
                                 : st.isTargetable(fieldIsP1, isFront, idx));

        // Ability targeting mode — supports both friendly and enemy targets depending on ability
        String abilitySourceCardId = abilitySource[0] != null ? getCardIdAtPosKey(st, abilitySource[0]) : null;
        boolean abilityTargetsEnemy = "enemy".equals(AbilityResolver.TARGET_SIDE.get(abilitySourceCardId));
        boolean tgtSideImmune    = !isMyField && AbilityResolver.isImmuneToAbilities(fieldIsP1, st);
        boolean shieldedByEnt    = !isFront && !isMyField && st.isShieldedByGreatEnt(fieldIsP1, idx);
        boolean canAbilityTarget = !empty && myTurn && abilitySource[0] != null
                && card != null
                && !tgtSideImmune
                && !shieldedByEnt
                && !anyNewPhase
                && (abilityTargetsEnemy ? !isMyField : isMyField)
                && (abilityTgtType[0] == null || abilityTgtType[0].equals(card.getType().toLowerCase()));

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

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(bgColor);
        p.setPreferredSize(new Dimension(108, 120));
        boolean anyClickable = canPlace || canSelect || canTarget || canAbilityTarget
                               || canSelectScrap || isSelectedScrap || canBotTarget
                               || canEchoCopy || canMimicTarget || canCopyTarget;
        p.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(border, (isSel || canTarget || canPlace || canAbilityTarget
                                    || isSelectedScrap || canSelectScrap || canBotTarget
                                    || canEchoCopy || canMimicTarget || canCopyTarget) ? 2 : 1, true),
            new EmptyBorder(4, 5, 4, 5)));
        if (anyClickable)
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
            JLabel imgL = new JLabel(CardImageLoader.get(card.getId(), 44, 44));
            imgL.setAlignmentX(Component.CENTER_ALIGNMENT);
            p.add(imgL);
            p.add(Box.createVerticalStrut(2));

            JLabel nameL = lbl(card.getName(), Font.BOLD, 10, isChamp ? CHAMP_CLR : Color.WHITE);
            nameL.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel hpL = lbl("HP " + hp + "/" + card.getHp(), Font.PLAIN, 9,
                              hp <= card.getHp() / 3 + 1 ? new Color(220, 80, 80) : new Color(80, 200, 100));
            hpL.setAlignmentX(Component.LEFT_ALIGNMENT);
            String atkText = atkBonus > 0 ? "ATK " + displayAtk + " (+" + atkBonus + ")" : "ATK " + displayAtk;
            JLabel atkL = lbl(atkText, Font.PLAIN, 9, atkBonus > 0 ? new Color(140, 220, 140) : new Color(220, 120, 80));
            atkL.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.add(nameL); p.add(hpL); p.add(atkL);

            if (isChamp) {
                Champion ch = (Champion) card;
                JLabel stageL = lbl("Stage " + ch.getStage(), Font.ITALIC, 8, CHAMP_CLR);
                stageL.setAlignmentX(Component.LEFT_ALIGNMENT);
                p.add(stageL);
            }
            if (isFrozenCard) {
                JLabel frozenL = lbl("Frozen(" + st.frozenCards.get(posKey) + ")", Font.ITALIC, 8, new Color(100, 200, 255));
                frozenL.setAlignmentX(Component.LEFT_ALIGNMENT);
                p.add(frozenL);
            }
            if (isBurnedCard) {
                JLabel burnL = lbl("Burned", Font.ITALIC, 8, new Color(255, 130, 50));
                burnL.setAlignmentX(Component.LEFT_ALIGNMENT);
                p.add(burnL);
            }
            if (st.focusedCards.contains(posKey)) {
                JLabel focL = lbl("Focus!", Font.ITALIC, 8, new Color(255, 220, 80));
                focL.setAlignmentX(Component.LEFT_ALIGNMENT);
                p.add(focL);
            }
            int txTurns = st.transformCounters.getOrDefault(posKey, 0);
            if (txTurns > 0) {
                JLabel txL = lbl("→" + txTurns + "t", Font.ITALIC, 8, new Color(160, 220, 100));
                txL.setAlignmentX(Component.LEFT_ALIGNMENT);
                p.add(txL);
            }
            if (st.fieldLockedCards.contains(posKey)) {
                JLabel lockL = lbl("Locked", Font.ITALIC, 8, new Color(200, 160, 60));
                lockL.setAlignmentX(Component.LEFT_ALIGNMENT);
                p.add(lockL);
            }
            if (isShieldedSlot) {
                JLabel shL = lbl("Shielded", Font.ITALIC, 8, new Color(100, 200, 100));
                shL.setAlignmentX(Component.LEFT_ALIGNMENT);
                p.add(shL);
            }
            if (isMyField && !hasAct) {
                JLabel used = lbl("Used", Font.ITALIC, 8, new Color(100, 100, 120));
                used.setAlignmentX(Component.LEFT_ALIGNMENT);
                p.add(used);
            }
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
                        // Water Spirit: all cards on field gain +2 HP when placed
                        if ("wts001".equals(id)) {
                            waterSpiritBuff(st2);
                            msg[0] = "Water Spirit: all field cards gained +2 HP!";
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
                        String harvestMsg = AbilityResolver.onAbilityUsed(stRef[0], amP1);
                        msg[0] = result + (harvestMsg.isEmpty() ? "" : " " + harvestMsg);
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
                            String harvestMsg = AbilityResolver.onAbilityUsed(stRef[0], amP1);
                            msg[0] = "Echo: copied " + (card != null ? card.getName() : cardId)
                                     + " — " + result + (harvestMsg.isEmpty() ? "" : " " + harvestMsg);
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
                            String harvestMsg = AbilityResolver.onAbilityUsed(stRef[0], amP1);
                            msg[0] = "Mimic: stole " + (card != null ? card.getName() : cardId)
                                     + " — " + result + (harvestMsg.isEmpty() ? "" : " " + harvestMsg);
                            echoCopiedCard[0] = null;
                            multiStepPhase[0] = null; multiStepCard[0] = null;
                            selField[0] = null;
                            stRef[0].save();
                            doEndTurn(amP1, stRef, selHand, selField, msg, rebuildRef, cardMap);
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
                        String harvestMsg = AbilityResolver.onAbilityUsed(stRef[0], amP1);
                        String prefix = isEchoSrc ? "Echo: " : "Mimic: ";
                        msg[0] = prefix + result + (harvestMsg.isEmpty() ? "" : " " + harvestMsg);
                        echoCopiedCard[0] = null;
                        multiStepPhase[0] = null; multiStepCard[0] = null;
                        selField[0] = null;
                        stRef[0].save();
                        if (isEchoSrc) {
                            rebuildRef[0].run();
                        } else {
                            doEndTurn(amP1, stRef, selHand, selField, msg, rebuildRef, cardMap);
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
                atkRow[atkIdx] = "";
                st.fieldAtkBonus.remove(atkPosKey);
                st.burnedCards.remove(atkPosKey);
                st.frozenCards.remove(atkPosKey);
                st.focusedCards.remove(atkPosKey);
                st.transformCounters.remove(atkPosKey);
                st.fieldLockedCards.remove(atkPosKey);
                if (!"item".equals(atkC.getType())) {
                    if (atkIsP1) st.p1SoulCap++; else st.p2SoulCap++;
                }
                String spikeDeathMsg = AbilityResolver.onDeath(st, atkId, atkIsP1, cardMap);
                if (atkIsP1) st.p1Discard.add(atkId); else st.p2Discard.add(atkId);
                selField[0] = null;
                st.useAction(atkIsP1, atkFront, atkIdx);
                msg[0] += "Spike Dragon retaliates! " + atkC.getName() + " destroyed!"
                           + (spikeDeathMsg.isEmpty() ? "" : " " + spikeDeathMsg);
                // still apply the attack damage to Spike Dragon before exiting
                if (newTgtHp <= 0) {
                    tgtRow[tgtIdx] = "";
                    st.fieldAtkBonus.remove(tgtPosKey);
                    st.burnedCards.remove(tgtPosKey);
                    st.frozenCards.remove(tgtPosKey);
                    st.focusedCards.remove(tgtPosKey);
                    st.transformCounters.remove(tgtPosKey);
                    st.fieldLockedCards.remove(tgtPosKey);
                    if (!(tgtC instanceof Champion)) {
                        if (!"item".equals(tgtC.getType())) {
                            if (amP1) st.p2SoulCap++; else st.p1SoulCap++;
                        }
                        AbilityResolver.onDeath(st, tgtId, tgtIsP1, cardMap);
                        if (tgtIsP1) st.p1Discard.add(tgtId); else st.p2Discard.add(tgtId);
                    }
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
                atkRow[atkIdx] = "";
                st.fieldAtkBonus.remove(atkPosKey);
                st.burnedCards.remove(atkPosKey);
                st.frozenCards.remove(atkPosKey);
                st.focusedCards.remove(atkPosKey);
                st.transformCounters.remove(atkPosKey);
                st.fieldLockedCards.remove(atkPosKey);
                if (!"item".equals(atkC.getType())) {
                    if (atkIsP1) st.p1SoulCap++; else st.p2SoulCap++;
                }
                String thornDeathMsg = AbilityResolver.onDeath(st, atkId, atkIsP1, cardMap);
                if (atkIsP1) st.p1Discard.add(atkId); else st.p2Discard.add(atkId);
                selField[0] = null;
                st.useAction(atkIsP1, atkFront, atkIdx);
                msg[0] += "Thorns: " + atkC.getName() + " destroyed!"
                           + (thornDeathMsg.isEmpty() ? "" : " " + thornDeathMsg);
                // Still apply the attack damage to Thorny Bushy
                if (newTgtHp <= 0) {
                    tgtRow[tgtIdx] = "";
                    st.fieldAtkBonus.remove(tgtPosKey);
                    st.burnedCards.remove(tgtPosKey);
                    st.frozenCards.remove(tgtPosKey);
                    st.focusedCards.remove(tgtPosKey);
                    st.transformCounters.remove(tgtPosKey);
                    st.fieldLockedCards.remove(tgtPosKey);
                    if (!(tgtC instanceof Champion)) {
                        if (!"item".equals(tgtC.getType())) {
                            if (amP1) st.p2SoulCap++; else st.p1SoulCap++;
                        }
                        AbilityResolver.onDeath(st, tgtId, tgtIsP1, cardMap);
                        if (tgtIsP1) st.p1Discard.add(tgtId); else st.p2Discard.add(tgtId);
                    }
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

        // Dual-strike: Mantis Bot, Large Mantis, Eye Shrew — first attack doesn't consume action
        boolean isDualStrike = "mtb001".equals(atkId) || "lmt001".equals(atkId) || "esr001".equals(atkId);
        if (isDualStrike && !st.mantisSecondAttack.contains(atkPosKey)) {
            st.mantisSecondAttack.add(atkPosKey);
            // Don't call useAction — card gets a second attack
        } else {
            st.mantisSecondAttack.remove(atkPosKey);
            st.useAction(atkIsP1, atkFront, atkIdx);
        }
        selField[0] = null;

        if (newTgtHp <= 0) {
            tgtRow[tgtIdx] = "";
            st.fieldAtkBonus.remove(tgtPosKey);
            st.burnedCards.remove(tgtPosKey);
            st.frozenCards.remove(tgtPosKey);
            st.focusedCards.remove(tgtPosKey);
            st.transformCounters.remove(tgtPosKey);
            st.fieldLockedCards.remove(tgtPosKey);

            if (!(tgtC instanceof Champion)) {
                if (!"item".equals(tgtC.getType())) {
                if (amP1) st.p2SoulCap++; else st.p1SoulCap++;
                }
                String onDeathMsg = AbilityResolver.onDeath(st, tgtId, tgtIsP1, cardMap);
                if (tgtIsP1) st.p1Discard.add(tgtId); else st.p2Discard.add(tgtId);
                msg[0] += tgtC.getName() + " defeated! " + onDeathMsg;
                // Great Ent: shielded backline card advances to frontline
                if ("gen001".equals(tgtId) && tgtFront) {
                    String[] backRow = tgtIsP1 ? st.p1Back : st.p2Back;
                    if (backRow[tgtIdx] != null && !backRow[tgtIdx].isEmpty()) {
                        String shPk = BattleState.posKey(tgtIsP1, false, tgtIdx);
                        tgtRow[tgtIdx] = backRow[tgtIdx]; backRow[tgtIdx] = "";
                        Integer a = st.fieldAtkBonus.remove(shPk);    if (a != null) st.fieldAtkBonus.put(tgtPosKey, a);
                        Integer b = st.burnedCards.remove(shPk);       if (b != null) st.burnedCards.put(tgtPosKey, b);
                        Integer f = st.frozenCards.remove(shPk);       if (f != null) st.frozenCards.put(tgtPosKey, f);
                        if (st.focusedCards.remove(shPk)) st.focusedCards.add(tgtPosKey);
                        Integer t = st.transformCounters.remove(shPk); if (t != null) st.transformCounters.put(tgtPosKey, t);
                        if (st.fieldLockedCards.remove(shPk)) st.fieldLockedCards.add(tgtPosKey);
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
                                sRow[sIdx] = "";
                                st.fieldAtkBonus.remove(sPosKey); st.burnedCards.remove(sPosKey);
                                st.frozenCards.remove(sPosKey);   st.focusedCards.remove(sPosKey);
                                st.transformCounters.remove(sPosKey); st.fieldLockedCards.remove(sPosKey);
                                if (!"item".equals(sCard.getType())) {
                                    if (amP1) st.p2SoulCap++; else st.p1SoulCap++;
                                }
                                AbilityResolver.onDeath(st, sId, tgtIsP1, cardMap);
                                if (tgtIsP1) st.p1Discard.add(sId); else st.p2Discard.add(sId);
                                msg[0] += " Tongue Grapple: " + actualSplash + " splash → " + sCard.getName() + " defeated!";
                                if ("gen001".equals(sId) && sFront2) {
                                    String[] sBackRow = tgtIsP1 ? st.p1Back : st.p2Back;
                                    if (sBackRow[sIdx] != null && !sBackRow[sIdx].isEmpty()) {
                                        String shPk2 = BattleState.posKey(tgtIsP1, false, sIdx);
                                        sRow[sIdx] = sBackRow[sIdx]; sBackRow[sIdx] = "";
                                        Integer a2 = st.fieldAtkBonus.remove(shPk2); if (a2 != null) st.fieldAtkBonus.put(sPosKey, a2);
                                        Integer b2 = st.burnedCards.remove(shPk2);   if (b2 != null) st.burnedCards.put(sPosKey, b2);
                                        Integer f2 = st.frozenCards.remove(shPk2);   if (f2 != null) st.frozenCards.put(sPosKey, f2);
                                        if (st.focusedCards.remove(shPk2)) st.focusedCards.add(sPosKey);
                                        Integer t2 = st.transformCounters.remove(shPk2); if (t2 != null) st.transformCounters.put(sPosKey, t2);
                                        if (st.fieldLockedCards.remove(shPk2)) st.fieldLockedCards.add(sPosKey);
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
            // Ice Dragon: apply Freeze on hit (1 round = 2 turns)
            if ("icd001".equals(atkId)) {
                st.frozenCards.put(tgtPosKey, Math.max(st.frozenCards.getOrDefault(tgtPosKey, 0), 2));
                msg[0] += " " + tgtC.getName() + " is Frozen for 1 round!";
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
                Integer av = st.fieldAtkBonus.remove(atkPosKey);       if (av != null) st.fieldAtkBonus.put(newPosKey, av);
                Integer bv = st.burnedCards.remove(atkPosKey);          if (bv != null) st.burnedCards.put(newPosKey, bv);
                Integer fv = st.frozenCards.remove(atkPosKey);          if (fv != null) st.frozenCards.put(newPosKey, fv);
                if (st.focusedCards.remove(atkPosKey))       st.focusedCards.add(newPosKey);
                if (st.mantisSecondAttack.remove(atkPosKey)) st.mantisSecondAttack.add(newPosKey);
                if (st.actionsUsed.remove(atkPosKey))        st.actionsUsed.add(newPosKey);
                if (st.abilityUsedThisTurn.remove(atkPosKey)) st.abilityUsedThisTurn.add(newPosKey);
                msg[0] += " Pawn dashes to the " + (targetFront ? "frontline" : "backline") + "!";
            }
        }

        if (atkC instanceof Champion) {
            doEndTurn(amP1, stRef, selHand, selField, msg, rebuildRef, cardMap);
        } else {
            st.save(); stRef[0] = st;
            rebuildRef[0].run();
        }
    }

    // ── Card active ability (no-target) ───────────────────────────────────────

    private static void doCardAbilityNoTarget(BattleState st, Map<String, Card> cardMap,
                                               String posKey, boolean amP1,
                                               BattleState[] stRef, int[] selHand,
                                               String[] selField, String[] msg,
                                               Runnable[] rebuildRef) {
        boolean isP1Front = posKey.charAt(1) == '1';
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
        String result = AbilityResolver.executeActive(st, cardId, amP1, cardMap);
        if (result != null) {
            st.useAction(fieldIsP1, isFront, idx);
            st.abilityUsedThisTurn.add(posKey);
            String harvestMsg = AbilityResolver.onAbilityUsed(st, amP1);
            msg[0] = result + (harvestMsg.isEmpty() ? "" : " " + harvestMsg);
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

        String result = AbilityResolver.executeTargeted(st, srcCardId, amP1,
                                                          tgtIsP1, tgtFront, tgtIdx,
                                                          abilityChoice, cardMap);
        st.useAction(srcIsP1, srcFront, srcIdx);
        st.abilityUsedThisTurn.add(sourceKey);
        String harvestMsg = AbilityResolver.onAbilityUsed(st, amP1);
        msg[0] = result + (harvestMsg.isEmpty() ? "" : " " + harvestMsg);
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
            msg[0] = result;
            st.abilityUsedThisTurn.add(BattleState.posKey(amP1, false, BattleState.CHAMP_SLOT));
            String harvestMsg = AbilityResolver.onAbilityUsed(st, amP1);
            if (!harvestMsg.isEmpty()) msg[0] += " " + harvestMsg;
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
                                   Map<String, Card> cardMap) {
        BattleState st = stRef[0];
        selHand[0] = -1; selField[0] = null;

        // ── Apply burn damage (1 dmg per burned card) ──────────────────────
        for (String posKey : new ArrayList<>(st.burnedCards.keySet())) {
            boolean posIsP1 = posKey.startsWith("p1");
            boolean posFront = posKey.charAt(2) == 'f';
            int posIdx = Character.getNumericValue(posKey.charAt(3));
            String[] row = posFront ? (posIsP1 ? st.p1Front : st.p2Front)
                                    : (posIsP1 ? st.p1Back  : st.p2Back);
            if (row[posIdx] == null || row[posIdx].isEmpty()) {
                st.burnedCards.remove(posKey); continue;
            }
            String cId = BattleState.slotId(row[posIdx]);
            int    chp = BattleState.slotHp(row[posIdx]);
            Card   bc  = cardMap.get(cId);
            int    newHp = chp - 1;
            if (newHp <= 0 && !(bc instanceof Champion)) {
                row[posIdx] = "";
                st.burnedCards.remove(posKey);
                st.fieldAtkBonus.remove(posKey);
                st.frozenCards.remove(posKey);
                st.focusedCards.remove(posKey);
                st.turtleBotCharged.remove(posKey);
                st.transformCounters.remove(posKey);
                st.fieldLockedCards.remove(posKey);
                if (bc != null && !"item".equals(bc.getType())) {
                    if (posIsP1) st.p1SoulCap++; else st.p2SoulCap++;
                }
                String deathMsg = AbilityResolver.onDeath(st, cId, posIsP1, cardMap);
                if (posIsP1) st.p1Discard.add(cId); else st.p2Discard.add(cId);
                String burnNotice = (bc != null ? bc.getName() : cId) + " burned to death!";
                if (!deathMsg.isEmpty()) burnNotice += " " + deathMsg;
                if ("gen001".equals(cId) && posFront) {
                    String[] backRow = posIsP1 ? st.p1Back : st.p2Back;
                    if (backRow[posIdx] != null && !backRow[posIdx].isEmpty()) {
                        String shPk = BattleState.posKey(posIsP1, false, posIdx);
                        row[posIdx] = backRow[posIdx]; backRow[posIdx] = "";
                        Integer a = st.fieldAtkBonus.remove(shPk);    if (a != null) st.fieldAtkBonus.put(posKey, a);
                        Integer b = st.burnedCards.remove(shPk);       if (b != null) st.burnedCards.put(posKey, b);
                        Integer f = st.frozenCards.remove(shPk);       if (f != null) st.frozenCards.put(posKey, f);
                        if (st.focusedCards.remove(shPk)) st.focusedCards.add(posKey);
                        Integer t = st.transformCounters.remove(shPk); if (t != null) st.transformCounters.put(posKey, t);
                        if (st.fieldLockedCards.remove(shPk)) st.fieldLockedCards.add(posKey);
                        burnNotice += " Shielded card advances!";
                    }
                }
                msg[0] = burnNotice;
            } else {
                row[posIdx] = BattleState.makeSlot(cId, Math.max(1, newHp));
            }
        }

        // ── Decrement frozen turns ─────────────────────────────────────────
        for (String posKey : new ArrayList<>(st.frozenCards.keySet())) {
            int turns = st.frozenCards.get(posKey) - 1;
            if (turns <= 0) st.frozenCards.remove(posKey);
            else st.frozenCards.put(posKey, turns);
        }

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

        // ── Druid aura: all Pod cards +1 ATK & HP each turn ───────────────
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

            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBackground(bgColor);
            card.setPreferredSize(new Dimension(93, 78));
            card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(border, sel ? 2 : 1, true), new EmptyBorder(4, 5, 4, 5)));
            if (clickable) card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            Color nameClr = canAfford && myTurn ? Color.WHITE : new Color(100, 100, 120);
            Color costClr = canAfford ? new Color(200, 160, 60) : new Color(220, 80, 80);
            boolean isFree = stRef[0].freeplayCards.contains(c.getId() + "_" + (amP1 ? "p1" : "p2"));
            String costTxt = isFree ? "FREE" : "Cost:" + cost;

            String statStr = "cng001".equals(c.getId())
                    ? "A:" + cost + " H:" + cost + " (=souls)"
                    : "A:" + c.getAttack() + " H:" + c.getHp();
            JLabel n = lbl(c.getName(), Font.BOLD,  9, nameClr); n.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel o = lbl(costTxt,     Font.PLAIN, 9, costClr); o.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel s = lbl(statStr,     Font.PLAIN, 9, new Color(140,140,165)); s.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(n); card.add(o); card.add(s);

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
                        String harvestMsg = AbilityResolver.onAbilityUsed(stRef[0], amP1);
                        msg[0] = result + (harvestMsg.isEmpty() ? "" : " " + harvestMsg);
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
                                : AbilityResolver.canUseAbility(srcCardId, mySouls));
                        String lblTxt = srcCard.getName() + ": Use Ability"
                                        + (voidBlocked ? " [Void]" : (ablEnabled ? "" : " (need soul)"));
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
                                String harvestMsg = AbilityResolver.onAbilityUsed(stRef[0], amP1);
                                msg[0] = result + (harvestMsg.isEmpty() ? "" : " " + harvestMsg);
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
                                doCardAbilityNoTarget(stRef[0], cardMap, selField[0], amP1,
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
                doEndTurn(amP1, stRef, selHand, selField, msg, rebuildRef, cardMap);
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

    private static void waterSpiritBuff(BattleState st) {
        String[][] rows = { st.p1Front, st.p1Back, st.p2Front, st.p2Back };
        for (String[] row : rows) {
            for (int i = 0; i < 5; i++) {
                if (row[i] != null && !row[i].isEmpty()) {
                    row[i] = BattleState.makeSlot(BattleState.slotId(row[i]),
                                                   BattleState.slotHp(row[i]) + 2);
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
