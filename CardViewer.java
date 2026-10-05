import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.*;

public class CardViewer {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            List<Card> cards = GameData.allCards();
            JFrame frame = new JFrame("Card Viewer");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(900, 650);
            frame.setLocationRelativeTo(null);
            frame.add(buildPanel(cards, "Card Collection", null, null, null));
            frame.setVisible(true);
        });
    }

    static JPanel buildPanel(List<Card> cards, String customTitle, Runnable onBack, JPanel root, CardLayout layout) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(30, 30, 40));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UI.BG);
        header.setBorder(new EmptyBorder(12, 14, 8, 14));

        if (onBack != null) {
            JButton backBtn = UI.backButton();
            backBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
            backBtn.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(180, 180, 200), 2, true),
                    new EmptyBorder(6, 18, 6, 18)));
            backBtn.addActionListener(e -> onBack.run());
            header.add(backBtn, BorderLayout.WEST);
        }

        String titleText = (customTitle != null ? customTitle : "Cards") + " (" + cards.size() + ")";
        JLabel title = new JLabel(titleText, SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.CENTER);

        panel.add(header, BorderLayout.NORTH);

        if (cards.isEmpty()) {
            JPanel empty = new JPanel(new GridBagLayout());
            empty.setBackground(new Color(30, 30, 40));
            JLabel msg = new JLabel("<html><center>You don't own any cards yet.<br>Cards can be obtained from packs.</center></html>", SwingConstants.CENTER);
            msg.setFont(new Font("SansSerif", Font.ITALIC, 16));
            msg.setForeground(new Color(140, 140, 165));
            empty.add(msg);
            panel.add(empty, BorderLayout.CENTER);
        } else {
            JPanel grid = new JPanel(new GridLayout(0, 4, 10, 10));
            grid.setBorder(new EmptyBorder(12, 14, 14, 14));
            grid.setBackground(new Color(30, 30, 40));
            for (Card card : cards) grid.add(CardRenderer.buildCard(card));

            JScrollPane scroll = new JScrollPane(grid);
            scroll.getVerticalScrollBar().setUnitIncrement(20);
            scroll.setBorder(null);
            panel.add(scroll, BorderLayout.CENTER);
        }

        return panel;
    }

    static JPanel buildCardPanel(Card card) {
        return CardRenderer.buildCard(card);
    }

    static Color typeColor(String type) {
        return CardRenderer.typeColor(type);
    }
}
