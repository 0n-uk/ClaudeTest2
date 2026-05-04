import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.List;

public class MenuScreen {

    private static final Color BG        = Color.WHITE;
    private static final Color BTN_BG    = new Color(50, 50, 75);
    private static final Color BTN_HOVER = new Color(70, 70, 105);

    public static void main(String[] args) {
        LoginScreen.main(args);
    }

    static JPanel buildMenuPanel(JPanel root, CardLayout layout, JFrame frame, User user) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(BG);

        JLabel title;
        ImageIcon titleIcon = loadScaledIcon("images/Buttons & Menus/GameTitle.png", 400);
        if (titleIcon != null) {
            title = new JLabel(titleIcon);
        } else {
            title = new JLabel("Card Game", SwingConstants.CENTER);
            title.setFont(new Font("SansSerif", Font.BOLD, 42));
            title.setForeground(new Color(30, 30, 60));
        }

        JLabel welcome = new JLabel("Welcome, " + user.getUsername() + "!", SwingConstants.CENTER);
        welcome.setFont(new Font("SansSerif", Font.ITALIC, 16));
        welcome.setForeground(new Color(80, 80, 100));

        JButton viewAllBtn  = menuButton("View Cards",   new Color(80, 180, 220));
        JButton viewOwnBtn  = menuButton("View Owned",   new Color(220, 160, 80));
        JButton packsBtn    = menuButton("Open Packs",   new Color(180, 100, 220));
        JButton deckBtn     = menuButton("Build Deck",   new Color(80, 210, 200));
        JButton battleBtn   = menuButton("Battle",       new Color(220, 80,  80));
        JButton createBtn   = menuButton("Create Card",  new Color(100, 220, 130));

        applyButtonImage(viewAllBtn, "images/Buttons & Menus/ViewCardsButton.png",  300);
        applyButtonImage(viewOwnBtn, "images/Buttons & Menus/ViewOwnedButton.png",  300);
        applyButtonImage(packsBtn,   "images/Buttons & Menus/OpenPacksButton.png",  300);
        applyButtonImage(deckBtn,    "images/Buttons & Menus/BuildDeckButton.png",  300);
        applyButtonImage(battleBtn,  "images/Buttons & Menus/BattleButton.png",     300);
        applyButtonImage(createBtn,  "images/Buttons & Menus/CreateCardsButton.png",300);

        viewAllBtn.addActionListener(e -> {
            List<Card> cards = CardViewer.loadCards("cards.txt");
            JPanel viewerPanel = CardViewer.buildPanel(cards, "Card Collection", () -> layout.show(root, "menu"), root, layout);
            root.add(viewerPanel, "viewer");
            layout.show(root, "viewer");
            frame.revalidate();
        });

        viewOwnBtn.addActionListener(e -> {
            List<Card> owned = user.getOwnedCards();
            JPanel viewerPanel = CardViewer.buildPanel(
                owned,
                owned.isEmpty() ? null : "My Collection",
                () -> layout.show(root, "menu"),
                root, layout
            );
            root.add(viewerPanel, "owned");
            layout.show(root, "owned");
            frame.revalidate();
        });

        packsBtn.addActionListener(e -> {
            JPanel packPanel = PackScreen.buildPanel(user, () -> layout.show(root, "menu"));
            root.add(packPanel, "packs");
            layout.show(root, "packs");
            frame.revalidate();
        });

        deckBtn.addActionListener(e -> {
            JPanel deckPanel = DeckBuilderScreen.buildPanel(user, () -> layout.show(root, "menu"));
            root.add(deckPanel, "deck");
            layout.show(root, "deck");
            frame.revalidate();
        });

        battleBtn.addActionListener(e -> {
            JPanel deckSelect = BattleDeckSelectScreen.buildPanel(
                user,
                () -> layout.show(root, "menu"),
                deckName -> {
                    JPanel champSelect = ChampionSelectScreen.buildPanel(
                        () -> layout.show(root, "deckselect"),
                        champLine -> {
                            JPanel matchmaking = MatchmakingScreen.buildPanel(
                                user, deckName, champLine,
                                () -> { MusicPlayer.play(); layout.show(root, "menu"); },
                                battleId -> {
                                    JPanel battle = BattleScreen.buildPanel(
                                        user, battleId,
                                        () -> { MusicPlayer.play(); layout.show(root, "menu"); });
                                    root.add(battle, "battle");
                                    layout.show(root, "battle");
                                    frame.revalidate();
                                }
                            );
                            root.add(matchmaking, "matchmaking");
                            layout.show(root, "matchmaking");
                            MusicPlayer.stop();
                            frame.revalidate();
                        }
                    );
                    root.add(champSelect, "champselect");
                    layout.show(root, "champselect");
                    frame.revalidate();
                }
            );
            root.add(deckSelect, "deckselect");
            layout.show(root, "deckselect");
            frame.revalidate();
        });

        createBtn.addActionListener(e -> {
            JPanel creatorPanel = CardCreatorScreen.buildPanel(
                () -> layout.show(root, "menu"),
                () -> {
                    List<Card> cards = CardViewer.loadCards("cards.txt");
                    JPanel viewerPanel = CardViewer.buildPanel(cards, "Card Collection", () -> layout.show(root, "menu"), root, layout);
                    root.add(viewerPanel, "viewer");
                    layout.show(root, "viewer");
                    frame.revalidate();
                }
            );
            root.add(creatorPanel, "creator");
            layout.show(root, "creator");
            frame.revalidate();
        });

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;

        gbc.gridy = 0; gbc.insets = new Insets(12, 0, 4,  0); panel.add(title,      gbc);
        gbc.gridy = 1; gbc.insets = new Insets(0,  0, 30, 0); panel.add(welcome,    gbc);
        gbc.gridy = 2; gbc.insets = new Insets(0,  0, 12, 0); panel.add(viewAllBtn, gbc);
        gbc.gridy = 3; gbc.insets = new Insets(0,  0, 12, 0); panel.add(viewOwnBtn, gbc);
        gbc.gridy = 4; gbc.insets = new Insets(0,  0, 12, 0); panel.add(packsBtn,   gbc);
        gbc.gridy = 5; gbc.insets = new Insets(0,  0, 12, 0); panel.add(deckBtn,    gbc);
        gbc.gridy = 6; gbc.insets = new Insets(0,  0, 12, 0); panel.add(battleBtn,  gbc);
        gbc.gridy = 7; gbc.insets = new Insets(0,  0, 12, 0); panel.add(createBtn,  gbc);

        return panel;
    }

    static JButton menuButton(String text, Color accent) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.BOLD, 18));
        btn.setForeground(Color.DARK_GRAY);
        btn.setBackground(BTN_BG);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(accent, 2, true),
                new EmptyBorder(14, 60, 14, 60)));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) { btn.setBackground(BTN_HOVER); }
            public void mouseExited(java.awt.event.MouseEvent e)  { btn.setBackground(BTN_BG); }
        });
        return btn;
    }

    private static void applyButtonImage(JButton btn, String path, int width) {
        ImageIcon icon = loadScaledIcon(path, width);
        if (icon != null) {
            btn.setIcon(icon);
            btn.setText("");
            btn.setContentAreaFilled(false);
            btn.setBorderPainted(false);
        }
    }

    private static ImageIcon loadScaledIcon(String path, int width) {
        try {
            ImageIcon raw = new ImageIcon(path);
            if (raw.getIconWidth() <= 0) return null;
            int h = (int)((double) raw.getIconHeight() / raw.getIconWidth() * width);
            return new ImageIcon(raw.getImage().getScaledInstance(width, h, Image.SCALE_SMOOTH));
        } catch (Exception e) {
            return null;
        }
    }
}
