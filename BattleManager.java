import java.io.*;
import java.util.*;

public class BattleManager {

    private static final String QUEUE_FILE = "battles/queue.txt";

    // ── Queue / matchmaking ───────────────────────────────────────────────────

    public static String joinQueue(String username, String deckName) {
        new File(BattleState.BATTLES_DIR).mkdirs();
        new File(BattleState.ACTIVE_DIR).mkdirs();

        File qf = new File(QUEUE_FILE);
        if (qf.exists()) {
            String existing = readFile(qf);
            if (existing != null && !existing.isEmpty()) {
                String[] parts = existing.split(":", 2);
                String waitingUser = parts[0].trim();
                String waitingDeck = parts.length > 1 ? parts[1].trim() : "";
                if (!waitingUser.equals(username)) {
                    qf.delete();
                    String battleId = username + "_vs_" + waitingUser + "_" + System.currentTimeMillis();
                    createBattle(battleId, waitingUser, waitingDeck, username, deckName);
                    return battleId;
                }
            }
        }
        writeFile(qf, username + ":" + deckName);
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
                                     String p1, String p1DeckName,
                                     String p2, String p2DeckName) {
        BattleState bs = new BattleState();
        bs.battleId    = battleId;
        bs.player1     = p1;
        bs.player2     = p2;
        bs.currentTurn = (Math.random() < 0.5) ? p1 : p2;
        bs.p1SoulCap   = 1;
        bs.p1Souls     = 1;
        bs.p2SoulCap   = 1;
        bs.p2Souls     = 1;
        bs.phase       = "active";
        bs.winner      = "";

        bs.p1Back[BattleState.CHAMP_SLOT] = BattleState.makeSlot(BattleState.CHAMP_ID, BattleState.CHAMP_HP);
        bs.p2Back[BattleState.CHAMP_SLOT] = BattleState.makeSlot(BattleState.CHAMP_ID, BattleState.CHAMP_HP);

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

    // ── Card map ──────────────────────────────────────────────────────────────

    public static Map<String, Card> buildCardMap() {
        Map<String, Card> map = new HashMap<>();
        map.put(BattleState.CHAMP_ID, BattleState.CHAMPION);
        try {
            List<Card> cards = CardViewer.loadCards("cards.txt");
            for (Card c : cards) map.put(c.getId(), c);
        } catch (Exception ignored) {}
        return map;
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
