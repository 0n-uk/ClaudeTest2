import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Reads and writes the game's shared text files: the card list, the champion
 * lines and the player accounts. Cards and champions never change while the
 * game runs, so each file is read once and the result is reused.
 *
 * A player's own cards, decks and cooldowns are handled by {@link User}.
 */
public class GameData {

    private static List<Card>                cards;
    private static Map<String, ChampionLine> championLines;

    // ── Cards ────────────────────────────────────────────────────────────────

    /** Every card in cards.txt. Returns a fresh list, so callers may sort or shuffle it. */
    static synchronized List<Card> allCards() {
        if (cards == null) cards = Collections.unmodifiableList(loadCardFile(GamePaths.CARDS_FILE));
        return new ArrayList<>(cards);
    }

    /**
     * Every card and champion stage by id, plus the Scrap token, for looking up
     * the ids stored in battles. Returns a fresh map, so callers may add to it.
     */
    static Map<String, Card> cardMap() {
        Map<String, Card> map = new HashMap<>();
        for (Card c : allCards()) map.put(c.getId(), c);
        for (ChampionLine line : championLines().values())
            for (Champion c : line.getStages()) map.put(c.getId(), c);
        map.put(AbilityResolver.SCRAP_ID, AbilityResolver.SCRAP_CARD);
        return map;
    }

    /** Reads any file of saved cards (cards.txt, a player's collection or a deck), one card per line. */
    static List<Card> loadCardFile(String filename) {
        List<Card> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                line = line.trim();
                if (line.isEmpty()) continue;
                Card card = parseCard(line);
                if (card != null) result.add(card);
                else badLine(filename, lineNo, line);
            }
        } catch (IOException e) {
            System.err.println("Could not load " + filename + ": " + e.getMessage());
        }
        return result;
    }

    private static Card parseCard(String line) {
        try {
            String id      = field(line, "id=", ",").replace("'", "");
            String name    = field(line, "name='", "'");
            String type    = field(line, "type='", "'");
            int attack     = Integer.parseInt(field(line, "attack=", ","));
            int hp         = Integer.parseInt(field(line, "hp=", ","));
            int cost       = Integer.parseInt(field(line, "cost=", ","));
            String ability = lastQuotedField(line, "ability=");
            return new Card(id, name, type, attack, hp, cost, ability);
        } catch (Exception e) {
            return null;
        }
    }

    // ── Champions ────────────────────────────────────────────────────────────

    /** Every champion line in champions.txt, by line id, with stages in order. The map is read-only. */
    static synchronized Map<String, ChampionLine> championLines() {
        if (championLines == null) championLines = Collections.unmodifiableMap(loadChampionFile(GamePaths.CHAMPIONS_FILE));
        return championLines;
    }

    private static Map<String, ChampionLine> loadChampionFile(String filename) {
        Map<String, List<Champion>> buckets = new LinkedHashMap<>();
        try (BufferedReader r = new BufferedReader(new FileReader(filename))) {
            String line;
            int lineNo = 0;
            while ((line = r.readLine()) != null) {
                lineNo++;
                line = line.trim();
                if (line.isEmpty()) continue;
                Champion c = parseChampion(line);
                if (c != null)
                    buckets.computeIfAbsent(c.getLineId(), k -> new ArrayList<>()).add(c);
                else badLine(filename, lineNo, line);
            }
        } catch (IOException e) {
            System.err.println("Could not load " + filename + ": " + e.getMessage());
        }

        Map<String, ChampionLine> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Champion>> e : buckets.entrySet()) {
            List<Champion> sorted = e.getValue();
            sorted.sort(Comparator.comparingInt(Champion::getStage));
            result.put(e.getKey(), new ChampionLine(e.getKey(), sorted));
        }
        return result;
    }

    private static Champion parseChampion(String line) {
        try {
            String id      = field(line, "id='",      "'");
            String name    = field(line, "name='",    "'");
            String type    = field(line, "type='",    "'");
            int    attack  = Integer.parseInt(field(line, "attack=", ","));
            int    hp      = Integer.parseInt(field(line, "hp=",     ","));
            int    stage   = Integer.parseInt(field(line, "stage=",  ","));
            String lineId  = field(line, "line='",   "'");
            String ability = lastQuotedField(line, "ability=");
            return new Champion(id, name, type, attack, hp, stage, lineId, ability);
        } catch (Exception e) {
            return null;
        }
    }

    // ── Line parsing helpers ─────────────────────────────────────────────────

    /** Reports a line that couldn't be read, so a typo in a data file is easy to find. */
    private static void badLine(String filename, int lineNo, String line) {
        System.err.println("Skipping unreadable line " + lineNo + " in " + filename + ": " + line);
    }

    /** The text between {@code after} and the next {@code before}; "" if {@code after} is missing. */
    private static String field(String line, String after, String before) {
        int start = line.indexOf(after);
        if (start < 0) return "";
        start += after.length();
        int end = line.indexOf(before, start);
        if (end < 0) return line.substring(start).trim();
        return line.substring(start, end).trim();
    }

    /**
     * The last field on a line, quoted with ' or ". Reads up to the line's final
     * matching quote, so text like "Can't be targeted" survives.
     */
    private static String lastQuotedField(String line, String key) {
        int start = line.indexOf(key);
        if (start < 0 || start + key.length() >= line.length()) return "";
        start += key.length();
        char quote = line.charAt(start);
        if (quote != '\'' && quote != '"') return field(line, key, ",");
        int end = line.lastIndexOf(quote);
        return end > start ? line.substring(start + 1, end) : "";
    }

    // ── Accounts ─────────────────────────────────────────────────────────────

    static boolean accountExists(String username) {
        return findAccount(username) != null;
    }

    /**
     * Checks a username and password. Returns the username exactly as it was
     * registered, or null if they don't match. Logins are case-insensitive, so
     * the registered spelling keeps "test" and "Test" on the same card and deck files.
     */
    static String login(String username, String password) {
        String[] account = findAccount(username);
        boolean ok = account != null && account.length == 2 && hash(password).equals(account[1]);
        return ok ? account[0] : null;
    }

    /**
     * Checks a new account against the rules. Returns a message to show the
     * player, or null if the account can be created.
     */
    static String checkNewAccount(String username, String password, String confirm) {
        if (username.isEmpty() || password.isEmpty()) return "Username and password cannot be empty.";
        if (username.length() < 3)                     return "Username must be at least 3 characters.";
        // ':' separates the name from the password in the accounts file
        if (username.contains(":") || username.matches(".*\\s.*"))
                                                       return "Username cannot contain spaces or ':'.";
        if (password.length() < 4)                     return "Password must be at least 4 characters.";
        if (!password.equals(confirm))                 return "Passwords do not match.";
        if (accountExists(username))                   return "Username already taken.";
        return null;
    }

    /** Saves a new account. Returns false if the accounts file could not be written. */
    static boolean register(String username, String password) {
        try (BufferedWriter w = new BufferedWriter(new FileWriter(GamePaths.ACCOUNTS_FILE, true))) {
            w.write(username + ":" + hash(password));
            w.newLine();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Returns { savedName, passwordHash } for this username (case-insensitive), or null if there is none. */
    private static String[] findAccount(String username) {
        try (BufferedReader r = new BufferedReader(new FileReader(GamePaths.ACCOUNTS_FILE))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] parts = line.split(":", 2);
                if (parts[0].equalsIgnoreCase(username)) return parts;
            }
        } catch (IOException ignored) {}
        return null;
    }

    private static String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            // Never fall back to saving the plain password
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
