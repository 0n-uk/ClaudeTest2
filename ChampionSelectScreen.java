import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.*;
import java.util.function.Consumer;

public class ChampionSelectScreen {

    private static final Color BG      = new Color(20, 20, 30);
    private static final Color HDR_BG  = new Color(15, 15, 25);
    private static final Color CARD_BG = new Color(38, 38, 58);
    private static final Color SEL_BG  = new Color(50, 60, 90);

    public static JPanel buildPanel(Runnable onBack, Consumer<String> onChampionSelected) {
        Map<String, ChampionLine> lines = ChampionLine.loadAll();

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG);

        // Header
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setBackground(HDR_BG);
        header.setBorder(new EmptyBorder(10, 14, 10, 14));

        JButton backBtn = MenuScreen.menuButton("Back", new Color(180, 180, 200));
        backBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        backBtn.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(180, 180, 200), 2, true),
                new EmptyBorder(6, 18, 6, 18)));
        backBtn.addActionListener(e -> onBack.run());

        JLabel title = new JLabel("Choose Your Champion", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(Color.WHITE);

        header.add(backBtn, BorderLayout.WEST);
        header.add(title,   BorderLayout.CENTER);
        panel.add(header, BorderLayout.NORTH);

        if (lines.isEmpty()) {
            JPanel empty = new JPanel(new GridBagLayout());
            empty.setBackground(BG);
            JLabel msg = new JLabel("No champions found. Check champions.txt.", SwingConstants.CENTER);
            msg.setFont(new Font("SansSerif", Font.ITALIC, 15));
            msg.setForeground(new Color(180, 80, 80));
            empty.add(msg);
            panel.add(empty, BorderLayout.CENTER);
            return panel;
        }

        // Grid of champion lines
        JPanel grid = new JPanel(new GridLayout(0, 2, 14, 14));
        grid.setBackground(BG);
        grid.setBorder(new EmptyBorder(16, 20, 20, 20));

        String[] selected = { null };

        for (ChampionLine line : lines.values()) {
            JPanel card = buildLineCard(line, selected, onChampionSelected);
            grid.add(card);
        }

        JScrollPane scroll = new JScrollPane(grid);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    private static JPanel buildLineCard(ChampionLine line, String[] selected,
                                         Consumer<String> onChampionSelected) {
        Champion first = line.getStageByIndex(0);
        Color accent = first != null ? CardViewer.typeColor(first.getType()) : new Color(140, 140, 165);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(accent, 2, true),
                new EmptyBorder(12, 14, 12, 14)));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Line type badge + name of first stage
        JLabel typeL = lbl(first != null ? first.getType().toUpperCase() : "", Font.BOLD, 11, accent);
        typeL.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(typeL);
        card.add(Box.createVerticalStrut(6));

        // Stage list
        for (Champion c : line.getStages()) {
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

            String stageTag = "S" + c.getStage();
            JLabel stageL = lbl(stageTag, Font.BOLD, 10, new Color(120, 120, 145));
            JLabel nameL  = lbl(c.getName(), Font.BOLD, 12,
                                c.getStage() == 1 ? Color.WHITE : new Color(200, 200, 220));
            JPanel stats  = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            stats.setOpaque(false);
            stats.add(lbl("ATK " + c.getAttack(), Font.PLAIN, 10, new Color(220, 100, 80)));
            stats.add(lbl("HP "  + c.getHp(),     Font.PLAIN, 10, new Color(80,  200, 100)));

            row.add(stageL, BorderLayout.WEST);
            row.add(nameL,  BorderLayout.CENTER);
            row.add(stats,  BorderLayout.EAST);
            card.add(row);

            if (!c.getAbility().isEmpty()) {
                JLabel abl = lbl("  " + c.getAbility(), Font.ITALIC, 9, new Color(200, 180, 100));
                abl.setAlignmentX(Component.LEFT_ALIGNMENT);
                card.add(abl);
            }
            card.add(Box.createVerticalStrut(3));
        }

        card.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                selected[0] = line.getLineId();
                onChampionSelected.accept(line.getLineId());
            }
            public void mouseEntered(java.awt.event.MouseEvent e) {
                card.setBackground(SEL_BG);
            }
            public void mouseExited(java.awt.event.MouseEvent e) {
                card.setBackground(CARD_BG);
            }
        });

        return card;
    }

    private static JLabel lbl(String text, int style, int size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", style, size));
        l.setForeground(color);
        return l;
    }
}
