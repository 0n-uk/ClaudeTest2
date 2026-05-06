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
            JPanel grid = new JPanel(new GridLayout(0, 3, 12, 12));
            grid.setBorder(new EmptyBorder(8, 14, 14, 14));
            grid.setBackground(new Color(30, 30, 40));
            for (Card card : cards) grid.add(buildCardPanel(card));

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
        return buildCardPanel(card, 200, 260);
    }

    static JPanel buildCardPanel(Card card, int w, int h) {
        int symSize  = Math.max(14, w / 13);
        int topFont  = Math.max(9,  w / 17);
        int imgSize  = Math.max(40, (int)(h * 0.44));
        int statFont = Math.max(10, w / 18);

        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBackground(new Color(50, 50, 70));
        panel.setPreferredSize(new Dimension(w, h));
        panel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(typeColor(card.getType()), 2, true),
                new EmptyBorder(6, 8, 6, 8)));

        // Top row: type symbol + name + cost
        JPanel top = new JPanel(new BorderLayout(4, 0));
        top.setOpaque(false);
        JLabel symL = new JLabel(TypeSymbolLoader.get(card.getType(), symSize, symSize));
        JLabel nameL = new JLabel(card.getName(), SwingConstants.CENTER);
        nameL.setFont(new Font("SansSerif", Font.BOLD, topFont));
        nameL.setForeground(Color.WHITE);
        JLabel costL = new JLabel(String.valueOf(card.getCost()), SwingConstants.CENTER);
        costL.setFont(new Font("SansSerif", Font.BOLD, topFont));
        costL.setForeground(new Color(100, 160, 220));
        top.add(symL,  BorderLayout.WEST);
        top.add(nameL, BorderLayout.CENTER);
        top.add(costL, BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);

        // Center: card image
        JLabel imgL = new JLabel(CardImageLoader.get(card.getId(), imgSize, imgSize));
        imgL.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(imgL, BorderLayout.CENTER);

        // Bottom row: ATK + info button + HP
        JPanel bot = new JPanel(new BorderLayout(4, 0));
        bot.setOpaque(false);
        JLabel atkL = new JLabel("⚔ " + card.getAttack());
        atkL.setFont(new Font("SansSerif", Font.BOLD, statFont));
        atkL.setForeground(new Color(220, 80, 80));
        JLabel hpL = new JLabel(card.getHp() + " ♥", SwingConstants.RIGHT);
        hpL.setFont(new Font("SansSerif", Font.BOLD, statFont));
        hpL.setForeground(new Color(80, 200, 100));
        JButton infoBtn = new JButton("ℹ");
        infoBtn.setFont(new Font("SansSerif", Font.BOLD, statFont));
        infoBtn.setForeground(new Color(220, 200, 120));
        infoBtn.setBackground(new Color(50, 50, 70));
        infoBtn.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
        infoBtn.setContentAreaFilled(false);
        infoBtn.setFocusPainted(false);
        infoBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        final String abilText = card.getAbility().isEmpty() ? "No ability." : card.getAbility();
        final String cardNameStr = card.getName();
        infoBtn.addActionListener(e -> JOptionPane.showMessageDialog(
                infoBtn,
                "<html><b>" + cardNameStr + "</b><br><br>" + abilText + "</html>",
                "Ability", JOptionPane.INFORMATION_MESSAGE));
        bot.add(atkL,    BorderLayout.WEST);
        bot.add(infoBtn, BorderLayout.CENTER);
        bot.add(hpL,     BorderLayout.EAST);
        panel.add(bot, BorderLayout.SOUTH);

        return panel;
    }

    static JLabel label(String text, int style, int size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", style, size));
        l.setForeground(color);
        return l;
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
            case "bug":       return new Color(140, 200,  60);
            case "moon":      return new Color(100,  80, 200);
            case "bot":       return new Color(120, 160, 180);
            case "champion":  return new Color(220, 180,  60);
            case "item":      return new Color(190, 160, 100);
            case "creature":  return new Color(160, 120,  80);
            default:          return new Color(140, 140, 160);
        }
    }
}
