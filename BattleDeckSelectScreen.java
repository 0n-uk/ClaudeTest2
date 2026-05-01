import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

public class BattleDeckSelectScreen {

    private static final Color BG      = new Color(20, 20, 30);
    private static final Color CARD_BG = new Color(35, 35, 52);
    private static final Color ACCENT  = new Color(100, 160, 220);

    public static JPanel buildPanel(User user, Runnable onBack, Consumer<String> onDeckSelected) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(20, 20, 30));
        header.setBorder(new EmptyBorder(12, 14, 8, 14));

        JButton backBtn = MenuScreen.menuButton("Back", new Color(180, 180, 200));
        backBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        backBtn.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(180, 180, 200), 2, true),
                new EmptyBorder(6, 18, 6, 18)));
        backBtn.addActionListener(e -> onBack.run());
        header.add(backBtn, BorderLayout.WEST);

        JLabel title = new JLabel("Choose Your Deck", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.CENTER);

        panel.add(header, BorderLayout.NORTH);

        List<String> deckNames = user.getDeckNames();

        if (deckNames.isEmpty()) {
            JPanel empty = new JPanel(new GridBagLayout());
            empty.setBackground(BG);
            JLabel msg = new JLabel("You need to build a deck first.", SwingConstants.CENTER);
            msg.setFont(new Font("SansSerif", Font.ITALIC, 16));
            msg.setForeground(new Color(140, 140, 165));
            empty.add(msg);
            panel.add(empty, BorderLayout.CENTER);
        } else {
            JPanel listPanel = new JPanel();
            listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
            listPanel.setBackground(BG);
            listPanel.setBorder(new EmptyBorder(16, 40, 16, 40));

            for (String deckName : deckNames) {
                List<Card> cards = user.loadDeck(deckName);
                String label = deckName + "  (" + cards.size() + " cards)";

                JButton btn = MenuScreen.menuButton(label, ACCENT);
                btn.setFont(new Font("SansSerif", Font.BOLD, 16));
                btn.setBackground(CARD_BG);
                btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, btn.getPreferredSize().height + 8));
                btn.setAlignmentX(Component.CENTER_ALIGNMENT);
                btn.addActionListener(e -> onDeckSelected.accept(deckName));

                listPanel.add(btn);
                listPanel.add(Box.createVerticalStrut(10));
            }

            JScrollPane scroll = new JScrollPane(listPanel);
            scroll.getVerticalScrollBar().setUnitIncrement(20);
            scroll.setBorder(null);
            scroll.setBackground(BG);
            scroll.getViewport().setBackground(BG);
            panel.add(scroll, BorderLayout.CENTER);
        }

        return panel;
    }
}
