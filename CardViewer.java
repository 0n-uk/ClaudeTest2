import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;

public class CardViewer {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            List<Card> cards = loadCards("cards.txt");
            JFrame frame = new JFrame("Card Viewer");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(900, 650);
            frame.setLocationRelativeTo(null);
            frame.add(buildPanel(cards, null, null, null));
            frame.setVisible(true);
        });
    }

    static JPanel buildPanel(List<Card> cards, Runnable onBack, JPanel root, CardLayout layout) {
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

        JLabel title = new JLabel("Card Collection (" + cards.size() + " cards)", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.CENTER);

        // Grid
        JPanel grid = new JPanel(new GridLayout(0, 3, 12, 12));
        grid.setBorder(new EmptyBorder(8, 14, 14, 14));
        grid.setBackground(new Color(30, 30, 40));
        for (Card card : cards) grid.add(buildCardPanel(card));

        JScrollPane scroll = new JScrollPane(grid);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        scroll.setBorder(null);

        panel.add(header, BorderLayout.NORTH);
        panel.add(scroll,  BorderLayout.CENTER);
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
            JOptionPane.showMessageDialog(null, "Could not load cards.txt: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
        return cards;
    }

    private static Card parseLine(String line) {
        try {
            String id      = extract(line, "id='", "'");
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
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(50, 50, 70));
        panel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(typeColor(card.getType()), 2, true),
                new EmptyBorder(8, 10, 8, 10)));

        panel.add(label(card.getName(), Font.BOLD, 14, Color.WHITE));
        panel.add(Box.createVerticalStrut(4));
        panel.add(label("Type: " + card.getType(), Font.ITALIC, 11, typeColor(card.getType())));
        panel.add(label("ID: " + card.getId(), Font.PLAIN, 10, new Color(150, 150, 160)));
        panel.add(Box.createVerticalStrut(6));

        JPanel stats = new JPanel(new GridLayout(1, 3));
        stats.setOpaque(false);
        stats.add(statBox("ATK",  card.getAttack(), new Color(220, 80, 80)));
        stats.add(statBox("HP",   card.getHp(),     new Color(80, 200, 100)));
        stats.add(statBox("COST", card.getCost(),   new Color(100, 160, 220)));
        panel.add(stats);

        panel.add(Box.createVerticalStrut(6));
        JLabel abilityLabel = new JLabel("<html><i>" + card.getAbility() + "</i></html>");
        abilityLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        abilityLabel.setForeground(new Color(220, 200, 120));
        panel.add(abilityLabel);

        return panel;
    }

    static JLabel label(String text, int style, int size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", style, size));
        l.setForeground(color);
        return l;
    }

    private static JPanel statBox(String label, int value, Color color) {
        JPanel box = new JPanel(new BorderLayout());
        box.setOpaque(false);
        JLabel top = new JLabel(label, SwingConstants.CENTER);
        top.setFont(new Font("SansSerif", Font.BOLD, 9));
        top.setForeground(new Color(180, 180, 190));
        JLabel val = new JLabel(String.valueOf(value), SwingConstants.CENTER);
        val.setFont(new Font("SansSerif", Font.BOLD, 16));
        val.setForeground(color);
        box.add(top, BorderLayout.NORTH);
        box.add(val, BorderLayout.CENTER);
        return box;
    }

    static Color typeColor(String type) {
        switch (type.toLowerCase()) {
            case "dragon":    return new Color(220, 80,  60);
            case "elemental": return new Color(80,  180, 220);
            case "undead":    return new Color(160, 80,  200);
            case "holy":      return new Color(255, 220, 80);
            case "nature":    return new Color(80,  200, 80);
            case "mage":      return new Color(100, 140, 255);
            case "assassin":  return new Color(180, 60,  60);
            case "demon":     return new Color(200, 40,  40);
            case "aquatic":   return new Color(60,  160, 220);
            case "beast":     return new Color(180, 140, 60);
            case "ranger":    return new Color(120, 200, 100);
            case "warrior":   return new Color(220, 120, 40);
            case "spirit":    return new Color(180, 160, 220);
            case "construct": return new Color(160, 160, 160);
            case "fae":       return new Color(220, 160, 220);
            default:          return new Color(140, 140, 160);
        }
    }
}
