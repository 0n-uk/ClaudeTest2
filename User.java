import java.io.*;
import java.util.List;

public class User {

    private static final String CARDS_DIR = "user_cards";

    private final String username;

    public User(String username) {
        this.username = username;
        new File(CARDS_DIR).mkdirs();
    }

    public String getUsername() { return username; }

    public List<Card> getOwnedCards() {
        File file = new File(CARDS_DIR + "/" + username + ".txt");
        if (!file.exists()) return new java.util.ArrayList<>();
        return CardViewer.loadCards(file.getPath());
    }

    public void addCard(Card card) {
        try (BufferedWriter w = new BufferedWriter(
                new FileWriter(CARDS_DIR + "/" + username + ".txt", true))) {
            w.newLine();
            w.write(card.toString());
        } catch (IOException ignored) {}
    }
}
