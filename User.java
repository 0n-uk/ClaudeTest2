import java.io.*;
import java.util.*;

public class User {

    private static final String CARDS_DIR    = "user_cards";
    static final long           COOLDOWN_MS  = 12L * 60 * 60 * 1000;

    private final String username;

    public User(String username) {
        this.username = username;
        new File(CARDS_DIR).mkdirs();
    }

    public String getUsername() { return username; }

    public List<Card> getOwnedCards() {
        File file = new File(CARDS_DIR + "/" + username + ".txt");
        if (!file.exists()) return new ArrayList<>();
        return CardViewer.loadCards(file.getPath());
    }

    public void addCard(Card card) {
        try (BufferedWriter w = new BufferedWriter(
                new FileWriter(CARDS_DIR + "/" + username + ".txt", true))) {
            w.newLine();
            w.write(card.toString());
        } catch (IOException ignored) {}
    }

    // ── Pack cooldowns ────────────────────────────────────────────────────────

    public boolean canOpenPack(String packId) {
        return msUntilPack(packId) == 0;
    }

    public long msUntilPack(String packId) {
        long last    = readCooldowns().getOrDefault(packId.toUpperCase(), 0L);
        long elapsed = System.currentTimeMillis() - last;
        return Math.max(0, COOLDOWN_MS - elapsed);
    }

    public void recordPackOpen(String packId) {
        Map<String, Long> data = readCooldowns();
        data.put(packId.toUpperCase(), System.currentTimeMillis());
        writeCooldowns(data);
    }

    private Map<String, Long> readCooldowns() {
        Map<String, Long> data = new LinkedHashMap<>();
        File file = cooldownFile();
        if (!file.exists()) return data;
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split(":", 2);
                if (p.length == 2) {
                    try { data.put(p[0], Long.parseLong(p[1])); }
                    catch (NumberFormatException ignored) {}
                }
            }
        } catch (IOException ignored) {}
        return data;
    }

    private void writeCooldowns(Map<String, Long> data) {
        try (BufferedWriter w = new BufferedWriter(new FileWriter(cooldownFile()))) {
            for (Map.Entry<String, Long> e : data.entrySet()) {
                w.write(e.getKey() + ":" + e.getValue());
                w.newLine();
            }
        } catch (IOException ignored) {}
    }

    private File cooldownFile() {
        return new File(CARDS_DIR + "/" + username + "_cooldowns.txt");
    }
}
