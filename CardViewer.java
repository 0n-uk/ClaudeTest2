import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.*;

public class CardViewer {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            List<Card> cards = loadCards("cards.txt");
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
        header.setBackground(new Color(20, 20, 30));
        header.setBorder(new EmptyBorder(12, 14, 8, 14));

        if (onBack != null) {
            JButton backBtn = MenuScreen.menuButton("Back", new Color(180, 180, 200));
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

    static List<Card> loadCards(String filename) {
        List<Card> cards = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                Card card = parseLine(line);
                if (card != null) cards.add(card);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Could not load " + filename + ": " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
        return cards;
    }

    private static Card parseLine(String line) {
        try {
            String id      = extract(line, "id=", ",").replace("'", "");
            String name    = extract(line, "name='", "'");
            String type    = extract(line, "type='", "'");
            int attack     = Integer.parseInt(extract(line, "attack=", ","));
            int hp         = Integer.parseInt(extract(line, "hp=", ","));
            int cost       = Integer.parseInt(extract(line, "cost=", ","));
            String ability = extract(line, "ability='", "'");
            return new Card(id, name, type, attack, hp, cost, ability);
        } catch (Exception e) {
            return null;
        }
    }

    private static String extract(String line, String after, String before) {
        int start = line.indexOf(after) + after.length();
        int end = line.indexOf(before, start);
        return line.substring(start, end).trim();
    }

    static JPanel buildCardPanel(Card card) {
        return CardRenderer.buildCard(card);
    }

    static Color typeColor(String type) {
        return CardRenderer.typeColor(type);
    }
}
