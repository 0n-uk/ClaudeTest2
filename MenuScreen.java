import javax.swing.*;
import java.awt.*;
import java.util.List;

public class MenuScreen {

    private static final Color BG         = UI.LIGHT_BG;
    private static final Color TITLE_FG   = new Color(30, 30, 60);
    private static final Color WELCOME_FG = new Color(80, 80, 100);

    private static final int TITLE_WIDTH  = 400;
    private static final int BUTTON_WIDTH = 300;

    static JPanel buildMenuPanel(GameWindow win, User user) {
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

        Runnable backToMenu = win.backTo(GameWindow.MENU);

        JButton viewAllBtn = imageMenuButton("View Cards", new Color(80, 180, 220),  "ViewCardsButton.png");
        JButton viewOwnBtn = imageMenuButton("View Owned", new Color(220, 160, 80),  "ViewOwnedButton.png");
        JButton packsBtn   = imageMenuButton("Open Packs", new Color(180, 100, 220), "OpenPacksButton.png");
        JButton deckBtn    = imageMenuButton("Build Deck", new Color(80, 210, 200),  "BuildDeckButton.png");
        JButton battleBtn  = imageMenuButton("Battle",     new Color(220, 80,  80),  "BattleButton.png");

        viewAllBtn.addActionListener(e -> {
            List<Card> cards = GameData.allCards();
            win.show(GameWindow.VIEWER, CardViewer.buildPanel(cards, "Card Collection", backToMenu));
        });

        viewOwnBtn.addActionListener(e -> {
            List<Card> owned = user.getOwnedCards();
            win.show(GameWindow.OWNED,
                CardViewer.buildPanel(owned, owned.isEmpty() ? null : "My Collection", backToMenu));
        });

        packsBtn.addActionListener(e ->
            win.show(GameWindow.PACKS, PackScreen.buildPanel(user, backToMenu)));

        deckBtn.addActionListener(e ->
            win.show(GameWindow.DECK, DeckBuilderScreen.buildPanel(user, backToMenu)));

        battleBtn.addActionListener(e -> openDeckSelect(win, user));

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

    private static void openDeckSelect(GameWindow win, User user) {
        win.show(GameWindow.DECK_SELECT, BattleDeckSelectScreen.buildPanel(
            user,
            win.backTo(GameWindow.MENU),
            deckName -> openChampSelect(win, user, deckName)));
    }

    private static void openChampSelect(GameWindow win, User user, String deckName) {
        win.show(GameWindow.CHAMP_SELECT, ChampionSelectScreen.buildPanel(
            win.backTo(GameWindow.DECK_SELECT),
            champLine -> openMatchmaking(win, user, deckName, champLine)));
    }

    private static void openMatchmaking(GameWindow win, User user, String deckName, String champLine) {
        JPanel matchmaking = MatchmakingScreen.buildPanel(
            user, deckName, champLine,
            backToMenuWithMusic(win),
            battleId -> openBattle(win, user, battleId));
        MusicPlayer.stop();
        win.show(GameWindow.MATCHMAKING, matchmaking);
    }

    private static void openBattle(GameWindow win, User user, String battleId) {
        win.show(GameWindow.BATTLE, BattleScreen.buildPanel(user, battleId, backToMenuWithMusic(win)));
    }

    private static Runnable backToMenuWithMusic(GameWindow win) {
        return () -> { MusicPlayer.playMenu(); win.show(GameWindow.MENU); };
    }

    /** A menu button that shows an image from the buttons folder, or plain text if the image is missing. */
    private static JButton imageMenuButton(String text, Color accent, String imageFile) {
        JButton btn = UI.menuButton(text, accent);
        UI.applyButtonImage(btn, imageFile, BUTTON_WIDTH);
        return btn;
    }
}
