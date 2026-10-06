import javax.swing.*;
import java.awt.*;
import java.util.List;

public class MenuScreen {

    private static final Color BG         = Color.WHITE;
    private static final Color TITLE_FG   = new Color(30, 30, 60);
    private static final Color WELCOME_FG = new Color(80, 80, 100);

    private static final int TITLE_WIDTH  = 400;
    private static final int BUTTON_WIDTH = 300;

    static JPanel buildMenuPanel(JPanel root, CardLayout layout, JFrame frame, User user) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(BG);

        JLabel title;
        ImageIcon titleIcon = Images.menuImage("GameTitle.png", TITLE_WIDTH);
        if (titleIcon != null) {
            title = new JLabel(titleIcon);
        } else {
            title = new JLabel(UI.GAME_TITLE, SwingConstants.CENTER);
            title.setFont(new Font("SansSerif", Font.BOLD, 42));
            title.setForeground(TITLE_FG);
        }

        JLabel welcome = new JLabel("Welcome, " + user.getUsername() + "!", SwingConstants.CENTER);
        welcome.setFont(new Font("SansSerif", Font.ITALIC, 16));
        welcome.setForeground(WELCOME_FG);

        Runnable backToMenu = () -> layout.show(root, "menu");

        JButton viewAllBtn = imageMenuButton("View Cards", new Color(80, 180, 220),  "ViewCardsButton.png");
        JButton viewOwnBtn = imageMenuButton("View Owned", new Color(220, 160, 80),  "ViewOwnedButton.png");
        JButton packsBtn   = imageMenuButton("Open Packs", new Color(180, 100, 220), "OpenPacksButton.png");
        JButton deckBtn    = imageMenuButton("Build Deck", new Color(80, 210, 200),  "BuildDeckButton.png");
        JButton battleBtn  = imageMenuButton("Battle",     new Color(220, 80,  80),  "BattleButton.png");

        viewAllBtn.addActionListener(e -> {
            List<Card> cards = GameData.allCards();
            UI.showScreen(root, layout, frame, "viewer",
                CardViewer.buildPanel(cards, "Card Collection", backToMenu, root, layout));
        });

        viewOwnBtn.addActionListener(e -> {
            List<Card> owned = user.getOwnedCards();
            UI.showScreen(root, layout, frame, "owned",
                CardViewer.buildPanel(owned, owned.isEmpty() ? null : "My Collection", backToMenu, root, layout));
        });

        packsBtn.addActionListener(e ->
            UI.showScreen(root, layout, frame, "packs", PackScreen.buildPanel(user, backToMenu)));

        deckBtn.addActionListener(e ->
            UI.showScreen(root, layout, frame, "deck", DeckBuilderScreen.buildPanel(user, backToMenu)));

        battleBtn.addActionListener(e -> openDeckSelect(root, layout, frame, user));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;

        gbc.gridy = 0; gbc.insets = new Insets(12, 0, 4,  0); panel.add(title,   gbc);
        gbc.gridy = 1; gbc.insets = new Insets(0,  0, 30, 0); panel.add(welcome, gbc);

        JButton[] buttons = { viewAllBtn, viewOwnBtn, packsBtn, deckBtn, battleBtn };
        gbc.insets = new Insets(0, 0, 12, 0);
        for (int i = 0; i < buttons.length; i++) {
            gbc.gridy = 2 + i;
            panel.add(buttons[i], gbc);
        }

        return panel;
    }

    // ── Battle flow: deck select → champion select → matchmaking → battle ──

    private static void openDeckSelect(JPanel root, CardLayout layout, JFrame frame, User user) {
        UI.showScreen(root, layout, frame, "deckselect", BattleDeckSelectScreen.buildPanel(
            user,
            () -> layout.show(root, "menu"),
            deckName -> openChampSelect(root, layout, frame, user, deckName)));
    }

    private static void openChampSelect(JPanel root, CardLayout layout, JFrame frame, User user, String deckName) {
        UI.showScreen(root, layout, frame, "champselect", ChampionSelectScreen.buildPanel(
            () -> layout.show(root, "deckselect"),
            champLine -> openMatchmaking(root, layout, frame, user, deckName, champLine)));
    }

    private static void openMatchmaking(JPanel root, CardLayout layout, JFrame frame, User user,
                                        String deckName, String champLine) {
        JPanel matchmaking = MatchmakingScreen.buildPanel(
            user, deckName, champLine,
            backToMenuWithMusic(root, layout),
            battleId -> openBattle(root, layout, frame, user, battleId));
        MusicPlayer.stop();
        UI.showScreen(root, layout, frame, "matchmaking", matchmaking);
    }

    private static void openBattle(JPanel root, CardLayout layout, JFrame frame, User user, String battleId) {
        UI.showScreen(root, layout, frame, "battle",
            BattleScreen.buildPanel(user, battleId, backToMenuWithMusic(root, layout)));
    }

    private static Runnable backToMenuWithMusic(JPanel root, CardLayout layout) {
        return () -> { MusicPlayer.playMenu(); layout.show(root, "menu"); };
    }

    /** A menu button that shows an image from the buttons folder, or plain text if the image is missing. */
    private static JButton imageMenuButton(String text, Color accent, String imageFile) {
        JButton btn = UI.menuButton(text, accent);
        UI.applyButtonImage(btn, imageFile, BUTTON_WIDTH);
        return btn;
    }
}
