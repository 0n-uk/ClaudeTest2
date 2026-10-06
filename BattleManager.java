import java.io.*;
import java.util.*;

public class BattleManager {

    private static final String QUEUE_FILE    = GamePaths.QUEUE_FILE;
    private static final String HEARTBEAT_DIR = GamePaths.HEARTBEAT_DIR;
    private static final long   HEARTBEAT_TTL = 30_000; // ms before a player is considered disconnected

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
            if (bs == null || isSolo(bs)) continue;
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
        setUpBattle(battleId, p1, cardIds(new User(p1).loadDeck(p1DeckName)), p1ChampLine,
                              p2, cardIds(new User(p2).loadDeck(p2DeckName)), p2ChampLine).save();
    }

    /**
     * A new battle, not yet saved: each deck is shuffled and deals 4 cards, each stage-1
     * champion goes in the middle of its back row, and a random player goes first.
     */
    static BattleState setUpBattle(String battleId,
                                   String p1, List<String> p1Deck, String p1ChampLine,
                                   String p2, List<String> p2Deck, String p2ChampLine) {
        Map<String, ChampionLine> champLines = GameData.championLines();

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
        deal(p1Deck, bs.p1Hand, bs.p1Deck);
        deal(p2Deck, bs.p2Hand, bs.p2Deck);
        return bs;
    }

    /** Shuffles a deck, puts the first 4 cards in the hand and the rest in the draw pile. */
    private static void deal(List<String> cards, List<String> hand, List<String> drawPile) {
        List<String> shuffled = new ArrayList<>(cards);
        Collections.shuffle(shuffled);
        for (int i = 0; i < shuffled.size(); i++) (i < 4 ? hand : drawPile).add(shuffled.get(i));
    }

    private static List<String> cardIds(List<Card> cards) {
        List<String> ids = new ArrayList<>();
        for (Card c : cards) ids.add(c.getId());
        return ids;
    }

    // ── Solo battles against the bot ──────────────────────────────────────────

    /**
     * Starts a battle against the bot and returns its id. Solo battles live in the same folder as
     * online ones so the battle screen can load them, but matchmaking skips them. Any solo battle
     * this player left unfinished (for example by closing the window) is removed first.
     */
    static String createSoloBattle(String username, String deckName, String champLine) {
        File[] old = new File(BattleState.ACTIVE_DIR).listFiles((d, n) -> n.endsWith(".txt"));
        if (old != null) {
            for (File f : old) {
                BattleState bs = BattleState.load(f.getName().replaceAll("\\.txt$", ""));
                if (bs != null && isSolo(bs) && username.equals(bs.player1)) f.delete();
            }
        }
        String battleId = "solo_" + username + "_" + System.currentTimeMillis();
        setUpBattle(battleId,
                    username,       cardIds(new User(username).loadDeck(deckName)), champLine,
                    BotPlayer.NAME, BotPlayer.buildDeck(),                         BotPlayer.pickChampionLine())
            .save();
        return battleId;
    }

    /** A battle against the bot. The bot's name has a space, which real usernames can't. */
    static boolean isSolo(BattleState bs) {
        return BotPlayer.NAME.equals(bs.player1) || BotPlayer.NAME.equals(bs.player2);
    }

    /** Deletes a finished solo battle's file. */
    static void endSoloBattle(String battleId) {
        new File(BattleState.ACTIVE_DIR + "/" + battleId + ".txt").delete();
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
        if (!f.exists()) return true; // never connected yet — don't forfeit on absence alone
        String ts = readFile(f);
        if (ts == null) return true;
        try {
            return System.currentTimeMillis() - Long.parseLong(ts.trim()) < HEARTBEAT_TTL;
        } catch (NumberFormatException e) {
            return true;
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
