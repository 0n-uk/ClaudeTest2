import java.io.*;
import java.util.*;

public class BattleManager {

    private static final String QUEUE_FILE    = "battles/queue.txt";
    private static final String HEARTBEAT_DIR = "battles/heartbeat";
    private static final long   HEARTBEAT_TTL = 6000; // ms before a player is considered disconnected

    // ── Queue / matchmaking ───────────────────────────────────────────────────

    public static String joinQueue(String username, String deckName, String champLine) {
        new File(BattleState.BATTLES_DIR).mkdirs();
        new File(BattleState.ACTIVE_DIR).mkdirs();

        File qf = new File(QUEUE_FILE);
        if (qf.exists()) {
            String existing = readFile(qf);
            if (existing != null && !existing.isEmpty()) {
                String[] parts = existing.split(":", 3);
                String waitingUser  = parts[0].trim();
                String waitingDeck  = parts.length > 1 ? parts[1].trim() : "";
                String waitingChamp = parts.length > 2 ? parts[2].trim() : "B";
                if (!waitingUser.equals(username)) {
                    if (!isAlive(waitingUser)) {
                        // Stale queue entry — replace with ours
                        writeFile(qf, username + ":" + deckName + ":" + champLine);
                        return null;
                    }
                    qf.delete();
                    String battleId = username + "_vs_" + waitingUser + "_" + System.currentTimeMillis();
                    createBattle(battleId,
                                 waitingUser, waitingDeck, waitingChamp,
                                 username,    deckName,    champLine);
                    return battleId;
                }
            }
        }
        writeFile(qf, username + ":" + deckName + ":" + champLine);
        return null;
    }

    public static String pollForMatch(String username) {
        File dir = new File(BattleState.ACTIVE_DIR);
        File[] files = dir.listFiles((d, n) -> n.endsWith(".txt"));
        if (files == null) return null;
        for (File f : files) {
            String battleId = f.getName().replaceAll("\\.txt$", "");
            BattleState bs = BattleState.load(battleId);
            if (bs == null) continue;
            if ("active".equals(bs.phase)
                    && (username.equals(bs.player1) || username.equals(bs.player2))) {
                return battleId;
            }
        }
        return null;
    }

    public static void cancelQueue(String username) {
        File qf = new File(QUEUE_FILE);
        if (!qf.exists()) return;
        String existing = readFile(qf);
        if (existing != null && existing.startsWith(username + ":")) {
            qf.delete();
        }
    }

    // ── Battle creation ───────────────────────────────────────────────────────

    private static void createBattle(String battleId,
                                      String p1, String p1DeckName, String p1ChampLine,
                                      String p2, String p2DeckName, String p2ChampLine) {
        Map<String, ChampionLine> champLines = ChampionLine.loadAll();

        BattleState bs  = new BattleState();
        bs.battleId     = battleId;
        bs.player1      = p1;
        bs.player2      = p2;
        bs.currentTurn  = (Math.random() < 0.5) ? p1 : p2;
        bs.p1SoulCap    = 1;
        bs.p1Souls      = 1;
        bs.p2SoulCap    = 1;
        bs.p2Souls      = 1;
        bs.phase        = "active";
        bs.winner       = "";
        bs.p1ChampLine  = p1ChampLine;
        bs.p2ChampLine  = p2ChampLine;

        placeChampion(bs, true,  champLines, p1ChampLine);
        placeChampion(bs, false, champLines, p2ChampLine);

        List<Card> p1Cards = new User(p1).loadDeck(p1DeckName);
        Collections.shuffle(p1Cards);
        for (int i = 0; i < Math.min(4, p1Cards.size()); i++) bs.p1Hand.add(p1Cards.get(i).getId());
        for (int i = 4; i < p1Cards.size(); i++)              bs.p1Deck.add(p1Cards.get(i).getId());

        List<Card> p2Cards = new User(p2).loadDeck(p2DeckName);
        Collections.shuffle(p2Cards);
        for (int i = 0; i < Math.min(4, p2Cards.size()); i++) bs.p2Hand.add(p2Cards.get(i).getId());
        for (int i = 4; i < p2Cards.size(); i++)              bs.p2Deck.add(p2Cards.get(i).getId());

        bs.save();
    }

    private static void placeChampion(BattleState bs, boolean isP1,
                                       Map<String, ChampionLine> lines, String lineId) {
        ChampionLine line = lines.get(lineId);
        if (line == null || line.size() == 0) return;
        Champion stage1 = line.getStageByIndex(0);
        if (stage1 == null) return;
        String[] back = isP1 ? bs.p1Back : bs.p2Back;
        back[BattleState.CHAMP_SLOT] = BattleState.makeSlot(stage1.getId(), stage1.getHp());
    }

    // ── Card map ──────────────────────────────────────────────────────────────

    public static Map<String, Card> buildCardMap() {
        Map<String, Card> map = new HashMap<>();
        try {
            List<Card> cards = CardViewer.loadCards("cards.txt");
            for (Card c : cards) map.put(c.getId(), c);
        } catch (Exception ignored) {}
        Map<String, ChampionLine> lines = ChampionLine.loadAll();
        for (ChampionLine line : lines.values())
            for (Champion c : line.getStages()) map.put(c.getId(), c);
        map.put(AbilityResolver.SCRAP_ID, AbilityResolver.SCRAP_CARD);
        return map;
    }

    // ── Heartbeat ─────────────────────────────────────────────────────────────

    public static void writeHeartbeat(String username) {
        new File(HEARTBEAT_DIR).mkdirs();
        writeFile(new File(HEARTBEAT_DIR + "/" + username + ".txt"),
                  String.valueOf(System.currentTimeMillis()));
    }

    public static void removeHeartbeat(String username) {
        new File(HEARTBEAT_DIR + "/" + username + ".txt").delete();
    }

    public static boolean isAlive(String username) {
        File f = new File(HEARTBEAT_DIR + "/" + username + ".txt");
        if (!f.exists()) return false;
        String ts = readFile(f);
        if (ts == null) return false;
        try {
            return System.currentTimeMillis() - Long.parseLong(ts.trim()) < HEARTBEAT_TTL;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Marks the battle as finished and awards a win to the surviving player. */
    public static void forfeitBattle(String battleId, String forfeiterUsername) {
        BattleState bs = BattleState.load(battleId);
        if (bs == null || "finished".equals(bs.phase)) return;
        bs.phase  = "finished";
        bs.winner = forfeiterUsername.equals(bs.player1) ? bs.player2 : bs.player1;
        bs.save();
    }

    // ── File I/O helpers ──────────────────────────────────────────────────────

    private static String readFile(File f) {
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString().trim();
        } catch (IOException e) {
            return null;
        }
    }

    private static void writeFile(File f, String content) {
        try (BufferedWriter w = new BufferedWriter(new FileWriter(f))) {
            w.write(content);
        } catch (IOException ignored) {}
    }
}
