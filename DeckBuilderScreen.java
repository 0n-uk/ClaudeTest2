import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;

public class DeckBuilderScreen {

    private static final Color BG       = new Color(20, 20, 30);
    private static final Color LEFT_BG  = new Color(25, 25, 38);
    private static final Color RIGHT_BG = new Color(30, 30, 45);
    private static final Color HDR_BG   = new Color(20, 20, 32);
    private static final int   MAX_DECK  = 20;
    private static final int   MAX_DECKS = 20;

    private enum Sort { NAME, TYPE, ATK, HP, ID }

    // ── Entry point ───────────────────────────────────────────────────────────

    public static JPanel buildPanel(User user, Runnable onBack) {

        // ── Card lookup (populated lazily from owned + deck files) ─────────────
        Map<String, Card> cardById = new LinkedHashMap<>();
        for (Card c : user.getOwnedCards()) cardById.put(c.getId(), c);

        // ── Mutable state ─────────────────────────────────────────────────────
        Map<String, Integer> leftCounts  = new LinkedHashMap<>();
        Map<String, Integer> rightCounts = new LinkedHashMap<>();
        String[]  activeDeck = { null };
        boolean[] skipCombo  = { false };
        Sort[]    sortBy     = { Sort.NAME };
        String[]  search     = { "" };
        JButton[] sortBtns   = new JButton[Sort.values().length];

        // ── Shared grid panels ────────────────────────────────────────────────
        JPanel leftGrid  = new JPanel();
        leftGrid.setBackground(LEFT_BG);
        leftGrid.setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel rightGrid = new JPanel(new GridLayout(0, 4, 5, 5));
        rightGrid.setBackground(RIGHT_BG);
        rightGrid.setBorder(new EmptyBorder(8, 8, 8, 8));

        JLabel countLabel = new JLabel("0 / " + MAX_DECK, SwingConstants.CENTER);
        countLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        countLabel.setForeground(Color.WHITE);

        JButton saveBtn  = smallButton("Save Deck",  new Color(100, 180, 255));
        JButton clearBtn = smallButton("Clear Deck", new Color(220, 80, 80));
        JButton newBtn   = smallButton("New Deck",   new Color(100, 220, 130));

        JComboBox<String> deckCombo = styledCombo();

        // ── Initialise decks ──────────────────────────────────────────────────
        skipCombo[0] = true;
        List<String> deckNames = new ArrayList<>(user.getDeckNames());
        if (deckNames.isEmpty()) {
            user.saveDeck("deck1", Collections.emptyList());
            deckNames.add("deck1");
        }
        for (String n : deckNames) deckCombo.addItem(n);

        String first = deckNames.get(0);
        activeDeck[0] = first;
        deckCombo.setSelectedItem(first);

        for (Card c : user.getUnassignedCards()) {
            cardById.putIfAbsent(c.getId(), c);
            leftCounts.merge(c.getId(), 1, Integer::sum);
        }
        for (Card c : user.loadDeck(first)) {
            cardById.putIfAbsent(c.getId(), c);
            rightCounts.merge(c.getId(), 1, Integer::sum);
        }
        skipCombo[0] = false;

        // ── Refresh closure ───────────────────────────────────────────────────
        Runnable[] ref = { null };
        ref[0] = () -> {
            int total    = rightCounts.values().stream().mapToInt(i -> i).sum();
            boolean full = total >= MAX_DECK;

            // Left grid
            List<Card> visible = cardById.values().stream()
                .filter(c -> leftCounts.getOrDefault(c.getId(), 0) > 0)
                .filter(c -> c.getName().toLowerCase().contains(search[0].toLowerCase()))
                .collect(Collectors.toList());

            Comparator<Card> cmp;
            switch (sortBy[0]) {
                case ID:   cmp = Comparator.comparing(Card::getId);                   break;
                case ATK:  cmp = Comparator.comparingInt(Card::getAttack).reversed(); break;
                case HP:   cmp = Comparator.comparingInt(Card::getHp).reversed();     break;
                case TYPE: cmp = Comparator.comparing(Card::getType);                 break;
                default:   cmp = Comparator.comparing(Card::getName);                 break;
            }
            visible.sort(cmp);

            leftGrid.removeAll();
            if (leftCounts.isEmpty()) {
                leftGrid.setLayout(new GridBagLayout());
                JLabel msg = new JLabel(
                    "<html><center>All your cards are in decks.<br>"
                    + "Clear a deck to free them up.</center></html>",
                    SwingConstants.CENTER);
                msg.setFont(new Font("SansSerif", Font.ITALIC, 13));
                msg.setForeground(new Color(140, 140, 165));
                leftGrid.add(msg);
            } else {
                leftGrid.setLayout(new GridLayout(0, 2, 8, 8));
                for (Card c : visible) {
                    int cnt = leftCounts.getOrDefault(c.getId(), 0);
                    if (cnt <= 0) continue;
                    leftGrid.add(leftCardPanel(c, cnt, !full, leftCounts, rightCounts, ref));
                }
            }
            leftGrid.revalidate();
            leftGrid.repaint();

            // Sort button styles
            Sort[] vals = Sort.values();
            for (int i = 0; i < vals.length; i++) {
                if (sortBtns[i] == null) continue;
                boolean active = vals[i] == sortBy[0];
                sortBtns[i].setBackground(active ? new Color(70, 70, 110) : new Color(40, 40, 60));
                sortBtns[i].setForeground(active ? Color.WHITE : new Color(160, 160, 185));
                sortBtns[i].setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(active ? new Color(100, 140, 255) : new Color(65, 65, 95), 1, true),
                    new EmptyBorder(3, 10, 3, 10)));
            }

            // Right grid — expand to individual slots then pad to MAX_DECK
            rightGrid.removeAll();
            List<String> slots = new ArrayList<>();
            for (Map.Entry<String, Integer> e : rightCounts.entrySet()) {
                for (int i = 0; i < e.getValue(); i++) slots.add(e.getKey());
            }
            for (int i = 0; i < MAX_DECK; i++) {
                if (i < slots.size()) {
                    Card c = cardById.get(slots.get(i));
                    rightGrid.add(c != null
                        ? rightCardPanel(c, leftCounts, rightCounts, ref)
                        : emptySlot());
                } else {
                    rightGrid.add(emptySlot());
                }
            }
            rightGrid.revalidate();
            rightGrid.repaint();

            countLabel.setText(total + " / " + MAX_DECK);
            countLabel.setForeground(full ? new Color(220, 80, 80) : Color.WHITE);

            saveBtn.setEnabled(activeDeck[0] != null);
            clearBtn.setEnabled(activeDeck[0] != null && total > 0);
        };

        // ── Combo: switch deck ────────────────────────────────────────────────
        deckCombo.addItemListener(e -> {
            if (e.getStateChange() != java.awt.event.ItemEvent.SELECTED) return;
            if (skipCombo[0]) return;
            String selected = (String) e.getItem();
            activeDeck[0] = selected;
            // Discard unsaved edits; reload from saved state
            leftCounts.clear();
            rightCounts.clear();
            for (Card c : user.getUnassignedCards()) {
                cardById.putIfAbsent(c.getId(), c);
                leftCounts.merge(c.getId(), 1, Integer::sum);
            }
            for (Card c : user.loadDeck(selected)) {
                cardById.putIfAbsent(c.getId(), c);
                rightCounts.merge(c.getId(), 1, Integer::sum);
            }
            ref[0].run();
        });

        // ── Save ──────────────────────────────────────────────────────────────
        saveBtn.addActionListener(e -> {
            if (activeDeck[0] == null) return;
            List<Card> cards = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : rightCounts.entrySet()) {
                Card c = cardById.get(entry.getKey());
                if (c != null) for (int i = 0; i < entry.getValue(); i++) cards.add(c);
            }
            user.saveDeck(activeDeck[0], cards);
            flashLabel(countLabel, "Saved!", new Color(100, 220, 130), 1400);
        });

        // ── Clear ─────────────────────────────────────────────────────────────
        clearBtn.addActionListener(e -> {
            if (activeDeck[0] == null) return;
            for (Map.Entry<String, Integer> entry : rightCounts.entrySet())
                if (entry.getValue() > 0)
                    leftCounts.merge(entry.getKey(), entry.getValue(), Integer::sum);
            rightCounts.clear();
            user.saveDeck(activeDeck[0], Collections.emptyList());
            ref[0].run();
        });

        // ── New Deck ──────────────────────────────────────────────────────────
        newBtn.addActionListener(e -> {
            if (user.getDeckNames().size() >= MAX_DECKS) {
                JOptionPane.showMessageDialog(null,
                    "You have reached the maximum of " + MAX_DECKS + " decks.",
                    "Deck Limit", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String name = JOptionPane.showInputDialog(null,
                "Enter a name for the new deck:", "New Deck", JOptionPane.PLAIN_MESSAGE);
            if (name == null || name.trim().isEmpty()) return;
            name = name.trim();
            if (user.getDeckNames().contains(name)) {
                JOptionPane.showMessageDialog(null,
                    "A deck named \"" + name + "\" already exists.",
                    "Duplicate Name", JOptionPane.WARNING_MESSAGE);
                return;
            }
            final String finalName = name;
            user.saveDeck(finalName, Collections.emptyList());

            // Discard unsaved edits and switch to the new empty deck
            leftCounts.clear();
            rightCounts.clear();
            for (Card c : user.getUnassignedCards())
                leftCounts.merge(c.getId(), 1, Integer::sum);
            activeDeck[0] = finalName;

            skipCombo[0] = true;
            deckCombo.addItem(finalName);
            deckCombo.setSelectedItem(finalName);
            skipCombo[0] = false;

            ref[0].run();
        });

        // ── Left panel ────────────────────────────────────────────────────────
        JScrollPane leftScroll = new JScrollPane(leftGrid);
        leftScroll.setBorder(null);
        leftScroll.getVerticalScrollBar().setUnitIncrement(20);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBackground(LEFT_BG);
        leftPanel.add(buildLeftControls(sortBy, search, ref, sortBtns), BorderLayout.NORTH);
        leftPanel.add(leftScroll, BorderLayout.CENTER);

        // ── Right panel ───────────────────────────────────────────────────────
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        btnRow.setOpaque(false);
        btnRow.add(newBtn);
        btnRow.add(saveBtn);
        btnRow.add(clearBtn);

        JPanel deckRow = new JPanel(new BorderLayout(8, 0));
        deckRow.setOpaque(false);
        JLabel deckLbl = new JLabel("Deck:");
        deckLbl.setFont(new Font("SansSerif", Font.BOLD, 13));
        deckLbl.setForeground(new Color(155, 155, 180));
        deckRow.add(deckLbl,    BorderLayout.WEST);
        deckRow.add(deckCombo,  BorderLayout.CENTER);
        deckRow.add(countLabel, BorderLayout.EAST);

        JPanel rightHeader = new JPanel();
        rightHeader.setLayout(new BoxLayout(rightHeader, BoxLayout.Y_AXIS));
        rightHeader.setBackground(HDR_BG);
        rightHeader.setBorder(new EmptyBorder(10, 12, 10, 12));
        btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        deckRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        rightHeader.add(btnRow);
        rightHeader.add(Box.createVerticalStrut(8));
        rightHeader.add(deckRow);

        JScrollPane rightScroll = new JScrollPane(rightGrid);
        rightScroll.setBorder(null);
        rightScroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(RIGHT_BG);
        rightPanel.add(rightHeader, BorderLayout.NORTH);
        rightPanel.add(rightScroll, BorderLayout.CENTER);

        // ── Split ─────────────────────────────────────────────────────────────
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        split.setDividerLocation(480);
        split.setDividerSize(5);
        split.setBorder(null);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(BG);
        wrapper.add(buildHeader("Deck Builder", onBack), BorderLayout.NORTH);
        wrapper.add(split, BorderLayout.CENTER);

        ref[0].run();
        return wrapper;
    }

    // ── Left card panel (click → move to deck) ────────────────────────────────

    private static JPanel leftCardPanel(Card card, int count, boolean canAdd,
                                         Map<String, Integer> leftCounts,
                                         Map<String, Integer> rightCounts,
                                         Runnable[] ref) {
        Color accent = CardViewer.typeColor(card.getType());
        Color bg     = canAdd ? new Color(45, 45, 65) : new Color(35, 35, 52);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(bg);
        panel.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(canAdd ? accent : new Color(55, 55, 75), canAdd ? 2 : 1, true),
            new EmptyBorder(6, 8, 6, 8)));
        if (canAdd) panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        Color nameClr = canAdd ? Color.WHITE          : new Color(105, 105, 128);
        Color typeClr = canAdd ? accent               : new Color(65, 65, 85);

        panel.add(lbl(card.getName(), Font.BOLD, 13, nameClr));
        panel.add(Box.createVerticalStrut(2));
        panel.add(lbl(card.getType(), Font.ITALIC, 10, typeClr));
        panel.add(Box.createVerticalStrut(5));

        JPanel stats = new JPanel(new GridLayout(1, 3, 2, 0));
        stats.setOpaque(false);
        stats.add(miniStat("ATK", card.getAttack(), canAdd ? new Color(220, 80, 80)   : new Color(120, 60, 60)));
        stats.add(miniStat("HP",  card.getHp(),     canAdd ? new Color(80, 200, 100)  : new Color(60, 110, 70)));
        stats.add(miniStat("CST", card.getCost(),   canAdd ? new Color(100, 160, 220) : new Color(60, 90, 130)));
        panel.add(stats);

        if (count > 1) {
            panel.add(Box.createVerticalStrut(3));
            panel.add(lbl("×" + count + " available", Font.PLAIN, 10,
                canAdd ? new Color(180, 180, 100) : new Color(90, 90, 60)));
        }
        if (!canAdd) {
            panel.add(Box.createVerticalStrut(3));
            panel.add(lbl("DECK FULL", Font.BOLD, 9, new Color(195, 85, 65)));
        }

        if (canAdd) {
            panel.addMouseListener(new java.awt.event.MouseAdapter() {
                public void mouseEntered(java.awt.event.MouseEvent e) { panel.setBackground(new Color(58, 58, 85)); }
                public void mouseExited(java.awt.event.MouseEvent e)  { panel.setBackground(bg); }
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    String id = card.getId();
                    int left = leftCounts.getOrDefault(id, 0);
                    if (left <= 1) leftCounts.remove(id);
                    else leftCounts.put(id, left - 1);
                    rightCounts.merge(id, 1, Integer::sum);
                    ref[0].run();
                }
            });
        }
        return panel;
    }

    // ── Right card panel (click → return to available) ────────────────────────

    private static JPanel rightCardPanel(Card card,
                                          Map<String, Integer> leftCounts,
                                          Map<String, Integer> rightCounts,
                                          Runnable[] ref) {
        Color accent = CardViewer.typeColor(card.getType());
        Color bg     = new Color(42, 42, 62);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(bg);
        panel.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(accent, 2, true),
            new EmptyBorder(5, 7, 5, 7)));
        panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        panel.add(lbl(card.getName(), Font.BOLD, 12, Color.WHITE));
        panel.add(Box.createVerticalStrut(2));
        panel.add(lbl(card.getType(), Font.ITALIC, 9, accent));
        panel.add(Box.createVerticalStrut(3));

        JPanel stats = new JPanel(new GridLayout(1, 3, 2, 0));
        stats.setOpaque(false);
        stats.add(miniStat("ATK", card.getAttack(), new Color(220, 80, 80)));
        stats.add(miniStat("HP",  card.getHp(),     new Color(80, 200, 100)));
        stats.add(miniStat("CST", card.getCost(),   new Color(100, 160, 220)));
        panel.add(stats);

        panel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) { panel.setBackground(new Color(55, 55, 80)); }
            public void mouseExited(java.awt.event.MouseEvent e)  { panel.setBackground(bg); }
            public void mouseClicked(java.awt.event.MouseEvent e) {
                String id = card.getId();
                int right = rightCounts.getOrDefault(id, 0);
                if (right <= 1) rightCounts.remove(id);
                else rightCounts.put(id, right - 1);
                leftCounts.merge(id, 1, Integer::sum);
                ref[0].run();
            }
        });
        return panel;
    }

    // ── Empty deck slot ───────────────────────────────────────────────────────

    private static JPanel emptySlot() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(28, 28, 42));
        panel.setBorder(new LineBorder(new Color(42, 42, 60), 1, true));
        JLabel l = new JLabel("—", SwingConstants.CENTER);
        l.setFont(new Font("SansSerif", Font.PLAIN, 16));
        l.setForeground(new Color(50, 50, 68));
        panel.add(l);
        return panel;
    }

    // ── Left controls (search + sort) ─────────────────────────────────────────

    private static JPanel buildLeftControls(Sort[] sortBy, String[] search,
                                             Runnable[] ref, JButton[] sortBtns) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(HDR_BG);
        panel.setBorder(new EmptyBorder(10, 10, 8, 10));

        JLabel title = new JLabel("Available Cards");
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        title.setForeground(Color.WHITE);

        JTextField searchField = new JTextField();
        searchField.setBackground(new Color(38, 38, 58));
        searchField.setForeground(Color.WHITE);
        searchField.setCaretColor(Color.WHITE);
        searchField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        searchField.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(new Color(65, 65, 95), 1),
            new EmptyBorder(5, 8, 5, 8)));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            void up() { search[0] = searchField.getText(); ref[0].run(); }
            public void insertUpdate(DocumentEvent e)  { up(); }
            public void removeUpdate(DocumentEvent e)  { up(); }
            public void changedUpdate(DocumentEvent e) { up(); }
        });

        JPanel topRow = new JPanel(new BorderLayout(10, 0));
        topRow.setOpaque(false);
        topRow.add(title,       BorderLayout.WEST);
        topRow.add(searchField, BorderLayout.CENTER);

        JPanel sortRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        sortRow.setOpaque(false);
        JLabel sortLbl = new JLabel("Sort:");
        sortLbl.setFont(new Font("SansSerif", Font.BOLD, 11));
        sortLbl.setForeground(new Color(125, 125, 150));
        sortRow.add(sortLbl);

        String[] labels = { "Name", "Type", "ATK", "HP", "ID" };
        Sort[]   vals   = Sort.values();
        for (int i = 0; i < vals.length; i++) {
            final Sort s = vals[i];
            JButton btn = new JButton(labels[i]);
            btn.setFont(new Font("SansSerif", Font.BOLD, 11));
            btn.setFocusPainted(false);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.addActionListener(ev -> { sortBy[0] = s; ref[0].run(); });
            sortBtns[i] = btn;
            sortRow.add(btn);
        }

        panel.add(topRow,  BorderLayout.NORTH);
        panel.add(sortRow, BorderLayout.SOUTH);
        return panel;
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private static JPanel buildHeader(String text, Runnable onBack) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG);
        header.setBorder(new EmptyBorder(12, 14, 8, 14));

        JButton backBtn = MenuScreen.menuButton("Back", new Color(180, 180, 200));
        backBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        backBtn.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(new Color(180, 180, 200), 2, true),
            new EmptyBorder(6, 18, 6, 18)));
        backBtn.addActionListener(e -> onBack.run());

        JLabel title = new JLabel(text, SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 24));
        title.setForeground(Color.WHITE);

        header.add(backBtn, BorderLayout.WEST);
        header.add(title,   BorderLayout.CENTER);
        return header;
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private static void flashLabel(JLabel label, String msg, Color color, int ms) {
        String prev    = label.getText();
        Color  prevClr = label.getForeground();
        label.setText(msg);
        label.setForeground(color);
        javax.swing.Timer t = new javax.swing.Timer(ms,
            e -> { label.setText(prev); label.setForeground(prevClr); });
        t.setRepeats(false);
        t.start();
    }

    private static JComboBox<String> styledCombo() {
        JComboBox<String> box = new JComboBox<>();
        box.setBackground(new Color(38, 38, 58));
        box.setForeground(Color.WHITE);
        box.setFont(new Font("SansSerif", Font.PLAIN, 13));
        box.setRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                setBackground(selected ? new Color(60, 60, 95) : new Color(38, 38, 58));
                setForeground(Color.WHITE);
                setBorder(new EmptyBorder(4, 8, 4, 8));
                return this;
            }
        });
        return box;
    }

    private static JLabel lbl(String text, int style, int size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", style, size));
        l.setForeground(color);
        return l;
    }

    private static JPanel miniStat(String name, int value, Color color) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        JLabel top = new JLabel(name, SwingConstants.CENTER);
        top.setFont(new Font("SansSerif", Font.BOLD, 8));
        top.setForeground(new Color(135, 135, 158));
        JLabel val = new JLabel(String.valueOf(value), SwingConstants.CENTER);
        val.setFont(new Font("SansSerif", Font.BOLD, 12));
        val.setForeground(color);
        p.add(top, BorderLayout.NORTH);
        p.add(val, BorderLayout.CENTER);
        return p;
    }

    private static JButton smallButton(String text, Color accent) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.BOLD, 12));
        b.setForeground(Color.WHITE);
        b.setBackground(new Color(38, 38, 58));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(accent, 2, true),
            new EmptyBorder(5, 14, 5, 14)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
}
