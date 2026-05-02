import java.io.*;
import java.util.*;

public class BattleState {

    static final String BATTLES_DIR = "battles";
    static final String ACTIVE_DIR  = "battles/active";
    static final int    CHAMP_SLOT  = 2;

    String battleId, player1, player2, currentTurn;
    String phase  = "";
    String winner = "";

    // Champion lines chosen by each player (e.g. "B", "D")
    String p1ChampLine = "";
    String p2ChampLine = "";

    int p1SoulCap, p1Souls, p2SoulCap, p2Souls;

    String[] p1Front = new String[5];
    String[] p1Back  = new String[5];
    String[] p2Front = new String[5];
    String[] p2Back  = new String[5];

    Set<String>     actionsUsed         = new HashSet<>();
    Set<String>     abilityUsedThisTurn = new HashSet<>(); // field posKeys that used an ability this turn
    Set<String>     freeplayCards       = new HashSet<>(); // "cardId_p1"/"cardId_p2" tokens for Queen
    int             p1ExtraActions      = 0;
    int             p2ExtraActions      = 0;
    Map<String,Integer> fieldAtkBonus   = new HashMap<>(); // posKey -> bonus ATK from buffs
    Set<String>     turtleBotCharged    = new HashSet<>(); // posKeys with active Turtle Bot +5 ATK buff
    Set<String>     mantisSecondAttack  = new HashSet<>(); // posKeys where Mantis Bot has used first attack

    List<String> p1Hand    = new ArrayList<>();
    List<String> p2Hand    = new ArrayList<>();
    List<String> p1Deck    = new ArrayList<>();
    List<String> p2Deck    = new ArrayList<>();
    List<String> p1Discard = new ArrayList<>();
    List<String> p2Discard = new ArrayList<>();

    BattleState() {
        Arrays.fill(p1Front, "");
        Arrays.fill(p1Back,  "");
        Arrays.fill(p2Front, "");
        Arrays.fill(p2Back,  "");
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    static BattleState load(String battleId) {
        File f = new File(ACTIVE_DIR + "/" + battleId + ".txt");
        if (!f.exists()) return null;
        BattleState bs = new BattleState();
        bs.battleId = battleId;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) {
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                String key = line.substring(0, eq);
                String val = line.substring(eq + 1);
                switch (key) {
                    case "player1":             bs.player1      = val; break;
                    case "player2":             bs.player2      = val; break;
                    case "currentTurn":         bs.currentTurn  = val; break;
                    case "phase":               bs.phase        = val; break;
                    case "winner":              bs.winner       = val; break;
                    case "p1ChampLine":         bs.p1ChampLine  = val; break;
                    case "p2ChampLine":         bs.p2ChampLine  = val; break;
                    case "p1SoulCap":           bs.p1SoulCap    = parseInt(val); break;
                    case "p1Souls":             bs.p1Souls      = parseInt(val); break;
                    case "p2SoulCap":           bs.p2SoulCap    = parseInt(val); break;
                    case "p2Souls":             bs.p2Souls      = parseInt(val); break;
                    case "p1ExtraActions":      bs.p1ExtraActions = parseInt(val); break;
                    case "p2ExtraActions":      bs.p2ExtraActions = parseInt(val); break;
                    case "p1Front":             bs.p1Front      = parseRow(val); break;
                    case "p1Back":              bs.p1Back       = parseRow(val); break;
                    case "p2Front":             bs.p2Front      = parseRow(val); break;
                    case "p2Back":              bs.p2Back       = parseRow(val); break;
                    case "actionsUsed":         if (!val.isEmpty())
                                                    bs.actionsUsed.addAll(Arrays.asList(val.split(","))); break;
                    case "abilityUsedThisTurn": if (!val.isEmpty())
                                                    bs.abilityUsedThisTurn.addAll(Arrays.asList(val.split(","))); break;
                    case "freeplayCards":       if (!val.isEmpty())
                                                    bs.freeplayCards.addAll(Arrays.asList(val.split(","))); break;
                    case "fieldAtkBonus":       if (!val.isEmpty()) {
                                                    for (String entry : val.split(",")) {
                                                        int c2 = entry.lastIndexOf(':');
                                                        if (c2 > 0) bs.fieldAtkBonus.put(
                                                            entry.substring(0, c2),
                                                            parseInt(entry.substring(c2 + 1)));
                                                    }
                                                } break;
                    case "turtleBotCharged":   if (!val.isEmpty())
                                                    bs.turtleBotCharged.addAll(Arrays.asList(val.split(","))); break;
                    case "mantisSecondAttack": if (!val.isEmpty())
                                                    bs.mantisSecondAttack.addAll(Arrays.asList(val.split(","))); break;
                    case "p1Hand":              bs.p1Hand    = parseList(val); break;
                    case "p2Hand":              bs.p2Hand    = parseList(val); break;
                    case "p1Deck":              bs.p1Deck    = parseList(val); break;
                    case "p2Deck":              bs.p2Deck    = parseList(val); break;
                    case "p1Discard":           bs.p1Discard = parseList(val); break;
                    case "p2Discard":           bs.p2Discard = parseList(val); break;
                }
            }
        } catch (IOException e) {
            return null;
        }
        return bs;
    }

    void save() {
        new File(ACTIVE_DIR).mkdirs();
        File f = new File(ACTIVE_DIR + "/" + battleId + ".txt");
        try (BufferedWriter w = new BufferedWriter(new FileWriter(f))) {
            w.write("player1="             + player1);      w.newLine();
            w.write("player2="             + player2);      w.newLine();
            w.write("currentTurn="         + currentTurn);  w.newLine();
            w.write("phase="               + phase);        w.newLine();
            w.write("winner="              + winner);       w.newLine();
            w.write("p1ChampLine="         + p1ChampLine);  w.newLine();
            w.write("p2ChampLine="         + p2ChampLine);  w.newLine();
            w.write("p1SoulCap="           + p1SoulCap);    w.newLine();
            w.write("p1Souls="             + p1Souls);      w.newLine();
            w.write("p2SoulCap="           + p2SoulCap);    w.newLine();
            w.write("p2Souls="             + p2Souls);      w.newLine();
            w.write("p1ExtraActions="      + p1ExtraActions); w.newLine();
            w.write("p2ExtraActions="      + p2ExtraActions); w.newLine();
            w.write("p1Front="             + rowStr(p1Front)); w.newLine();
            w.write("p1Back="              + rowStr(p1Back));  w.newLine();
            w.write("p2Front="             + rowStr(p2Front)); w.newLine();
            w.write("p2Back="              + rowStr(p2Back));  w.newLine();
            w.write("actionsUsed="         + String.join(",", actionsUsed));          w.newLine();
            w.write("abilityUsedThisTurn=" + String.join(",", abilityUsedThisTurn));  w.newLine();
            w.write("freeplayCards="       + String.join(",", freeplayCards));        w.newLine();
            StringBuilder atkBonusSb = new StringBuilder();
            for (Map.Entry<String,Integer> e : fieldAtkBonus.entrySet()) {
                if (atkBonusSb.length() > 0) atkBonusSb.append(',');
                atkBonusSb.append(e.getKey()).append(':').append(e.getValue());
            }
            w.write("fieldAtkBonus=" + atkBonusSb);                                  w.newLine();
            w.write("turtleBotCharged="   + String.join(",", turtleBotCharged));   w.newLine();
            w.write("mantisSecondAttack=" + String.join(",", mantisSecondAttack)); w.newLine();
            w.write("p1Hand="    + String.join(",", p1Hand));    w.newLine();
            w.write("p2Hand="    + String.join(",", p2Hand));    w.newLine();
            w.write("p1Deck="    + String.join(",", p1Deck));    w.newLine();
            w.write("p2Deck="    + String.join(",", p2Deck));    w.newLine();
            w.write("p1Discard=" + String.join(",", p1Discard)); w.newLine();
            w.write("p2Discard=" + String.join(",", p2Discard)); w.newLine();
        } catch (IOException ignored) {}
    }

    // ── Slot helpers ─────────────────────────────────────────────────────────

    static int slotHp(String slot) {
        if (slot == null || slot.isEmpty()) return 0;
        int c = slot.lastIndexOf(':');
        if (c < 0) return 0;
        try { return Integer.parseInt(slot.substring(c + 1)); }
        catch (NumberFormatException e) { return 0; }
    }

    static String slotId(String slot) {
        if (slot == null || slot.isEmpty()) return null;
        int c = slot.lastIndexOf(':');
        return c < 0 ? null : slot.substring(0, c);
    }

    static String makeSlot(String id, int hp) {
        return id + ":" + hp;
    }

    // ── Position / action helpers ─────────────────────────────────────────────

    static String posKey(boolean isP1, boolean isFront, int slot) {
        return (isP1 ? "p1" : "p2") + (isFront ? "f" : "b") + slot;
    }

    boolean hasAction(boolean isP1, boolean isFront, int slot) {
        String key = posKey(isP1, isFront, slot);
        if (!actionsUsed.contains(key)) return true;
        int extra = isP1 ? p1ExtraActions : p2ExtraActions;
        return extra > 0;
    }

    void useAction(boolean isP1, boolean isFront, int slot) {
        String key = posKey(isP1, isFront, slot);
        if (actionsUsed.contains(key)) {
            if (isP1) p1ExtraActions = Math.max(0, p1ExtraActions - 1);
            else      p2ExtraActions = Math.max(0, p2ExtraActions - 1);
        } else {
            actionsUsed.add(key);
        }
    }

    boolean champActionUsed(boolean isP1) {
        return actionsUsed.contains(posKey(isP1, false, CHAMP_SLOT));
    }

    int frontlineCount(boolean isP1) {
        String[] front = isP1 ? p1Front : p2Front;
        int count = 0;
        for (String s : front) if (s != null && !s.isEmpty()) count++;
        return count;
    }

    boolean isTargetable(boolean targetIsP1, boolean isFront, int slot) {
        String[] row = isFront ? (targetIsP1 ? p1Front : p2Front)
                               : (targetIsP1 ? p1Back  : p2Back);
        if (row[slot] == null || row[slot].isEmpty()) return false;
        if (!isFront && frontlineCount(targetIsP1) > 0) return false;
        return true;
    }

    boolean isTargetableBypass(boolean targetIsP1, boolean isFront, int slot) {
        String[] row = isFront ? (targetIsP1 ? p1Front : p2Front)
                               : (targetIsP1 ? p1Back  : p2Back);
        return row[slot] != null && !row[slot].isEmpty();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private static String[] parseRow(String val) {
        String[] parts = val.split("\\|", -1);
        String[] row = new String[5];
        for (int i = 0; i < 5; i++) row[i] = (i < parts.length) ? parts[i] : "";
        return row;
    }

    private static String rowStr(String[] row) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < row.length; i++) {
            if (i > 0) sb.append('|');
            sb.append(row[i] == null ? "" : row[i]);
        }
        return sb.toString();
    }

    private static List<String> parseList(String val) {
        List<String> list = new ArrayList<>();
        if (val == null || val.isEmpty()) return list;
        for (String s : val.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) list.add(t);
        }
        return list;
    }
}
