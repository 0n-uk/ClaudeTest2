import java.io.*;
import java.util.*;

/**
 * One logged-in player, and their files in resources/user_cards: the cards
 * they own, their named decks and their pack cooldowns.
 *
 * Each file is read the first time it's needed and then kept in memory.
 * Every change is written to the file straight away, so the files stay the
 * saved copy and the memory copy only saves re-reading them.
 */
public class User {

    private static final String DATA_DIR    = GamePaths.PLAYER_DATA_DIR;
    private static final long   COOLDOWN_MS = 12L * 60 * 60 * 1000;

    private final String username;

    // Loaded on first use (null until then)
    private List<Card>        owned;
    private List<String>      deckNames;
    private Map<String, Long> cooldowns;
    private final Map<String, List<Card>> decks = new HashMap<>();   // by file-safe deck name

    public User(String username) {
        this.username = username;
        deckDir().mkdirs();
    }

    public String getUsername() { return username; }

    // ── Owned cards ───────────────────────────────────────────────────────────

    /** Every card the player owns. Returns a fresh list, so callers may change it. */
    public synchronized List<Card> getOwnedCards() {
        if (owned == null) owned = readCards(fileFor(".txt"));
        return new ArrayList<>(owned);
    }

    /** Adds one card to the collection. Returns false if it couldn't be saved. */
    public synchronized boolean addCard(Card card) {
        File file = fileFor(".txt");
        try (BufferedWriter w = new BufferedWriter(new FileWriter(file, true))) {
            if (!endsWithNewline(file)) w.newLine();
            w.write(card.toSaveLine());
            w.newLine();
        } catch (IOException e) {
            return failed("save a card to", file, e);
        }
        if (owned != null) owned.add(card);
        return true;
    }

    // ── Named decks ───────────────────────────────────────────────────────────

    /** The player's deck names, alphabetically. */
    public synchronized List<String> getDeckNames() {
        if (deckNames == null) {
            deckNames = new ArrayList<>();
            File[] files = deckDir().listFiles((d, n) -> n.endsWith(".txt"));
            if (files != null)
                for (File f : files) deckNames.add(f.getName().substring(0, f.getName().length() - 4));
            Collections.sort(deckNames);
        }
        return new ArrayList<>(deckNames);
    }

    /** The cards in one deck (empty if there's no such deck). Returns a fresh list. */
    public synchronized List<Card> loadDeck(String deckName) {
        String key = sanitize(deckName);
        List<Card> cards = decks.get(key);
        if (cards == null) {
            cards = readCards(deckFile(key));
            decks.put(key, cards);
        }
        return new ArrayList<>(cards);
    }

    /** Creates or overwrites one deck. Returns false if it couldn't be saved. */
    public synchronized boolean saveDeck(String deckName, List<Card> cards) {
        String key = sanitize(deckName);
        File file = deckFile(key);
        try (BufferedWriter w = new BufferedWriter(new FileWriter(file))) {
            for (Card c : cards) {
                w.write(c.toSaveLine());
                w.newLine();
            }
        } catch (IOException e) {
            return failed("save deck", file, e);
        }
        decks.put(key, new ArrayList<>(cards));
        if (deckNames != null && !deckNames.contains(key)) {
            deckNames.add(key);
            Collections.sort(deckNames);
        }
        return true;
    }

    /** Owned cards minus every copy already used in a deck, so the deck builder only offers spare copies. */
    public synchronized List<Card> getUnassignedCards() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, Card>    byId   = new LinkedHashMap<>();
        for (Card c : getOwnedCards()) {
            counts.merge(c.getId(), 1, Integer::sum);
            byId.put(c.getId(), c);
        }
        for (String deckName : getDeckNames()) {
            for (Card c : loadDeck(deckName)) {
                int remaining = counts.getOrDefault(c.getId(), 0) - 1;
                if (remaining <= 0) counts.remove(c.getId());
                else counts.put(c.getId(), remaining);
            }
        }
        List<Card> result = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            Card c = byId.get(e.getKey());
            if (c != null) for (int i = 0; i < e.getValue(); i++) result.add(c);
        }
        return result;
    }

    // ── Pack cooldowns ────────────────────────────────────────────────────────

    public boolean canOpenPack(String packId) {
        return msUntilPack(packId) == 0;
    }

    public synchronized long msUntilPack(String packId) {
        long last    = cooldowns().getOrDefault(packId.toUpperCase(), 0L);
        long elapsed = System.currentTimeMillis() - last;
        return Math.max(0, COOLDOWN_MS - elapsed);
    }

    public synchronized void recordPackOpen(String packId) {
        cooldowns().put(packId.toUpperCase(), System.currentTimeMillis());
        File file = fileFor("_cooldowns.txt");
        try (BufferedWriter w = new BufferedWriter(new FileWriter(file))) {
            for (Map.Entry<String, Long> e : cooldowns.entrySet()) {
                w.write(e.getKey() + ":" + e.getValue());
                w.newLine();
            }
        } catch (IOException e) {
            failed("save pack cooldowns to", file, e);
        }
    }

    /** Pack id → when it was last opened, read from the cooldown file once. */
    private Map<String, Long> cooldowns() {
        if (cooldowns != null) return cooldowns;
        cooldowns = new LinkedHashMap<>();
        File file = fileFor("_cooldowns.txt");
        if (!file.exists()) return cooldowns;
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split(":", 2);
                if (p.length != 2) continue;
                try { cooldowns.put(p[0], Long.parseLong(p[1].trim())); }
                catch (NumberFormatException e) { System.err.println("Skipping bad cooldown line in " + file + ": " + line); }
            }
        } catch (IOException e) {
            failed("read", file, e);
        }
        return cooldowns;
    }

    // ── Files ─────────────────────────────────────────────────────────────────

    /** A file in the player data folder named after this player, e.g. fileFor("_cooldowns.txt"). */
    private File fileFor(String suffix) {
        return new File(DATA_DIR, username + suffix);
    }

    private File deckDir() {
        return fileFor("_decks");
    }

    private File deckFile(String safeName) {
        return new File(deckDir(), safeName + ".txt");
    }

    private static List<Card> readCards(File file) {
        return file.exists() ? GameData.loadCardFile(file.getPath()) : new ArrayList<>();
    }

    /** Older saves wrote the line break before each card, so the last line may not end with one. */
    private static boolean endsWithNewline(File file) throws IOException {
        if (!file.exists() || file.length() == 0) return true;
        try (RandomAccessFile f = new RandomAccessFile(file, "r")) {
            f.seek(f.length() - 1);
            return f.read() == '\n';
        }
    }

    private static boolean failed(String action, File file, IOException e) {
        System.err.println("Could not " + action + " " + file + ": " + e.getMessage());
        return false;
    }

    /** Replaces characters that aren't allowed in Windows file names. */
    private static String sanitize(String name) {
        return name.replaceAll("[/\\\\:*?\"<>|]", "_").trim();
    }
}
