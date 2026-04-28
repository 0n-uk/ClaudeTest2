import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.List;

public class MenuScreen {

    private static final Color BG        = new Color(20, 20, 30);
    private static final Color BTN_BG    = new Color(50, 50, 75);
    private static final Color BTN_HOVER = new Color(70, 70, 105);

    public static void main(String[] args) {
        LoginScreen.main(args);
    }

    static JPanel buildMenuPanel(JPanel root, CardLayout layout, JFrame frame, User user) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(BG);

        JLabel title = new JLabel("Card Game", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 42));
        title.setForeground(Color.WHITE);

        JLabel welcome = new JLabel("Welcome, " + user.getUsername() + "!", SwingConstants.CENTER);
        welcome.setFont(new Font("SansSerif", Font.ITALIC, 16));
        welcome.setForeground(new Color(160, 160, 180));

        JButton viewAllBtn  = menuButton("View Cards",   new Color(80, 180, 220));
        JButton viewOwnBtn  = menuButton("View Owned",   new Color(220, 160, 80));
        JButton createBtn   = menuButton("Create Card",  new Color(100, 220, 130));

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
        gbc.gridy = 4; gbc.insets = new Insets(0,  0, 12, 0); panel.add(createBtn,  gbc);

        return panel;
    }

    static JButton menuButton(String text, Color accent) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.BOLD, 18));
        btn.setForeground(Color.WHITE);
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
}
