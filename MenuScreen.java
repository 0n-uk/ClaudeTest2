import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.List;

public class MenuScreen {

    private static final Color BG       = new Color(20, 20, 30);
    private static final Color BTN_BG   = new Color(50, 50, 75);
    private static final Color BTN_HOVER = new Color(70, 70, 105);

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Card Game");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(900, 650);
            frame.setLocationRelativeTo(null);

            CardLayout layout = new CardLayout();
            JPanel root = new JPanel(layout);

            root.add(buildMenuPanel(root, layout, frame), "menu");
            layout.show(root, "menu");

            frame.add(root);
            frame.setVisible(true);
        });
    }

    static JPanel buildMenuPanel(JPanel root, CardLayout layout, JFrame frame) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(BG);

        JLabel title = new JLabel("Card Game", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 42));
        title.setForeground(Color.WHITE);

        JLabel subtitle = new JLabel("What would you like to do?", SwingConstants.CENTER);
        subtitle.setFont(new Font("SansSerif", Font.ITALIC, 16));
        subtitle.setForeground(new Color(160, 160, 180));

        JButton viewBtn   = menuButton("View Cards",   new Color(80, 180, 220));
        JButton createBtn = menuButton("Create Card",  new Color(100, 220, 130));

        viewBtn.addActionListener(e -> {
            List<Card> cards = CardViewer.loadCards("cards.txt");
            JPanel viewerPanel = CardViewer.buildPanel(cards, () -> layout.show(root, "menu"), root, layout);
            root.add(viewerPanel, "viewer");
            layout.show(root, "viewer");
            frame.revalidate();
        });

        createBtn.addActionListener(e -> {
            JPanel creatorPanel = CardCreatorScreen.buildPanel(
                () -> layout.show(root, "menu"),
                () -> {
                    List<Card> cards = CardViewer.loadCards("cards.txt");
                    JPanel viewerPanel = CardViewer.buildPanel(cards, () -> layout.show(root, "menu"), root, layout);
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
        gbc.gridx = 0; gbc.insets = new Insets(12, 0, 12, 0);

        gbc.gridy = 0; panel.add(title,     gbc);
        gbc.gridy = 1; panel.add(subtitle,  gbc);
        gbc.gridy = 2; gbc.insets = new Insets(30, 0, 12, 0); panel.add(viewBtn,   gbc);
        gbc.gridy = 3; gbc.insets = new Insets(0, 0, 12, 0);  panel.add(createBtn, gbc);

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
