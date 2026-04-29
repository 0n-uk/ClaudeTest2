import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class DeckBuilderScreen {

    private static final Color BG       = new Color(20, 20, 30);
    private static final Color LIB_BG   = new Color(25, 25, 38);
    private static final Color DECK_BG  = new Color(30, 30, 45);
    private static final Color ENTRY_BG = new Color(42, 42, 62);
    private static final int   MAX_DECK   = 20;
    private static final int   MAX_COPIES = 4;

    private enum Sort { NAME, TYPE, ATK, HP, ID, AMOUNT }

    // ── Entry point ───────────────────────────────────────────────────────────

    public static JPanel buildPanel(User user, Runnable onBack) {
        List<Card> allOwned = user.getOwnedCards();

        Map<String, Card>    byId  = new LinkedHashMap<>();
        Map<String, Integer> owned = new LinkedHashMap<>();
        for (Card c : allOwned) {
            byId.putIfAbsent(c.getId(), c);
            owned.merge(c.getId(), 1, Integer::sum);
        }

        Map<String, Integer> deck   = new LinkedHashMap<>();
        Sort[]               sortBy = { Sort.NAME };
        String[]             search = { "" };

        // ── Containers refreshed on every state change ────────────────────────
        JPanel libGrid  = new JPanel(new GridLayout(0, 2, 8, 8));
        libGrid.setBackground(LIB_BG);
        libGrid.setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel deckList = new JPanel();
        deckList.setLayout(new BoxLayout(deckList, BoxLayout.Y_AXIS));
        deckList.setBackground(DECK_BG);
        deckList.setBorder(new EmptyBorder(6, 6, 6, 6));

        JLabel countLabel = new JLabel("0 / " + MAX_DECK, SwingConstants.CENTER);
        countLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        countLabel.setForeground(Color.WHITE);

        JButton[] sortBtnArr = new JButton[Sort.values().length];

        // ── Refresh closure ───────────────────────────────────────────────────
        Runnable[] ref = new Runnable[1];
        ref[0] = () -> {
            List<Card> cards = byId.values().stream()
                .filter(c -> c.getName().toLowerCase().contains(search[0].toLowerCase()))
                .collect(Collectors.toList());

            Comparator<Card> cmp;
            switch (sortBy[0]) {
                case ID:     cmp = Comparator.comparing(Card::getId); break;
                case ATK:    cmp = Comparator.comparingInt(Card::getAttack).reversed(); break;
                case HP:     cmp = Comparator.comparingInt(Card::getHp).reversed(); break;
                case TYPE:   cmp = Comparator.comparing(Card::getType); break;
                case AMOUNT: cmp = Comparator.comparingInt((Card c) -> owned.getOrDefault(c.getId(), 0)).reversed(); break;
                default:     cmp = Comparator.comparing(Card::getName); break;
            }
            cards.sort(cmp);

            int deckTotal = deck.values().stream().mapToInt(Integer::intValue).sum();

            // Rebuild library
            libGrid.removeAll();
            if (byId.isEmpty()) {
                JLabel empty = new JLabel("<html><center>You don't own any cards.<br>Open some packs first!</center></html>", SwingConstants.CENTER);
                empty.setFont(new Font("SansSerif", Font.ITALIC, 14));
                empty.setForeground(new Color(140, 140, 165));
                libGrid.setLayout(new GridBagLayout());
                libGrid.add(empty);
            } else {
                libGrid.setLayout(new GridLayout(0, 2, 8, 8));
                for (Card c : cards) {
                    int ownCnt = owned.getOrDefault(c.getId(), 0);
                    int inDeck = deck.getOrDefault(c.getId(), 0);
                    boolean canAdd = inDeck < MAX_COPIES && deckTotal < MAX_DECK;
                    libGrid.add(libCard(c, ownCnt, inDeck, canAdd, deck, ref));
                }
            }
            libGrid.revalidate();
            libGrid.repaint();

            // Update sort button highlights
            Sort[] vals = Sort.values();
            for (int i = 0; i < vals.length; i++) {
                if (sortBtnArr[i] == null) continue;
                boolean active = vals[i] == sortBy[0];
                sortBtnArr[i].setBackground(active ? new Color(70, 70, 110) : new Color(40, 40, 60));
                sortBtnArr[i].setForeground(active ? Color.WHITE : new Color(160, 160, 185));
                sortBtnArr[i].setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(active ? new Color(100, 140, 255) : new Color(65, 65, 95), 1, true),
                        new EmptyBorder(3, 10, 3, 10)));
            }

            // Rebuild deck list
            deckList.removeAll();
            boolean full = deckTotal >= MAX_DECK;
            countLabel.setText(deckTotal + " / " + MAX_DECK);
            countLabel.setForeground(full ? new Color(220, 80, 80) : Color.WHITE);

            boolean first = true;
            for (Map.Entry<String, Integer> e : deck.entrySet()) {
                if (e.getValue() <= 0) continue;
                Card c = byId.get(e.getKey());
                if (c == null) continue;
                if (!first) deckList.add(Box.createVerticalStrut(4));
                deckList.add(deckEntry(c, e.getValue(), deck, ref));
                first = false;
            }
            deckList.add(Box.createVerticalGlue());
            deckList.revalidate();
            deckList.repaint();
        };

        // ── Controls bar ──────────────────────────────────────────────────────
        JPanel controls = buildControls(sortBy, search, ref, sortBtnArr);

        JScrollPane libScroll = new JScrollPane(libGrid);
        libScroll.setBorder(null);
        libScroll.getVerticalScrollBar().setUnitIncrement(20);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBackground(LIB_BG);
        leftPanel.add(controls,  BorderLayout.NORTH);
        leftPanel.add(libScroll, BorderLayout.CENTER);

        // ── Deck panel ────────────────────────────────────────────────────────
        JScrollPane deckScroll = new JScrollPane(deckList);
        deckScroll.setBorder(null);
        deckScroll.getVerticalScrollBar().setUnitIncrement(16);

        JButton saveBtn = smallButton("Save Deck", new Color(100, 180, 255));
        saveBtn.addActionListener(e -> saveDeck(user, byId, deck, countLabel));

        JButton clearBtn = smallButton("Clear", new Color(220, 80, 80));
        clearBtn.addActionListener(e -> { deck.clear(); ref[0].run(); });

        JPanel deckTopRow = new JPanel(new BorderLayout(8, 0));
        deckTopRow.setBackground(new Color(20, 20, 32));
        deckTopRow.setBorder(new EmptyBorder(10, 12, 10, 12));

        JLabel deckTitle = new JLabel("Your Deck", SwingConstants.LEFT);
        deckTitle.setFont(new Font("SansSerif", Font.BOLD, 15));
        deckTitle.setForeground(Color.WHITE);

        JPanel deckTitleRow = new JPanel(new BorderLayout(6, 0));
        deckTitleRow.setOpaque(false);
        deckTitleRow.add(deckTitle,  BorderLayout.WEST);
        deckTitleRow.add(countLabel, BorderLayout.CENTER);

        JPanel deckBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        deckBtns.setOpaque(false);
        deckBtns.add(clearBtn);
        deckBtns.add(saveBtn);

        deckTopRow.add(deckTitleRow, BorderLayout.CENTER);
        deckTopRow.add(deckBtns,     BorderLayout.EAST);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(DECK_BG);
        rightPanel.add(deckTopRow,  BorderLayout.NORTH);
        rightPanel.add(deckScroll,  BorderLayout.CENTER);

        // ── Split ─────────────────────────────────────────────────────────────
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        split.setDividerLocation(570);
        split.setDividerSize(5);
        split.setBorder(null);
        split.setBackground(BG);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(BG);
        wrapper.add(buildHeader("Deck Builder", onBack), BorderLayout.NORTH);
        wrapper.add(split, BorderLayout.CENTER);

        ref[0].run();
        return wrapper;
    }

    // ── Library card panel ────────────────────────────────────────────────────

    private static JPanel libCard(Card card, int ownedCount, int inDeck, boolean canAdd,
                                   Map<String, Integer> deck, Runnable[] ref) {
        Color accent = CardViewer.typeColor(card.getType());
        Color bg     = canAdd ? new Color(45, 45, 65) : new Color(35, 35, 52);
        Color border = canAdd ? accent : new Color(60, 60, 80);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(bg);
        panel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(border, canAdd ? 2 : 1, true),
                new EmptyBorder(6, 8, 6, 8)));
        if (canAdd) panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        Color nameClr = canAdd ? Color.WHITE : new Color(110, 110, 130);
        Color typeClr = canAdd ? accent      : new Color(70, 70, 90);

        panel.add(lbl(card.getName(), Font.BOLD, 13, nameClr));
        panel.add(Box.createVerticalStrut(2));
        panel.add(lbl(card.getType(), Font.ITALIC, 10, typeClr));
        panel.add(Box.createVerticalStrut(5));

        JPanel stats = new JPanel(new GridLayout(1, 3, 2, 0));
        stats.setOpaque(false);
        stats.add(miniStat("ATK", card.getAttack(), new Color(220, 80, 80)));
        stats.add(miniStat("HP",  card.getHp(),     new Color(80, 200, 100)));
        stats.add(miniStat("CST", card.getCost(),   new Color(100, 160, 220)));
        panel.add(stats);
        panel.add(Box.createVerticalStrut(5));

        String ownedStr = "Owned: " + ownedCount;
        String deckStr  = inDeck > 0 ? "   In deck: " + inDeck + "/" + MAX_COPIES : "";
        panel.add(lbl(ownedStr + deckStr, Font.PLAIN, 10,
                canAdd ? new Color(150, 150, 175) : new Color(80, 80, 100)));

        if (!canAdd) {
            String reason = inDeck >= MAX_COPIES ? "MAX COPIES IN DECK" : "DECK FULL";
            panel.add(lbl(reason, Font.BOLD, 9, new Color(200, 90, 70)));
        }

        if (canAdd) {
            panel.addMouseListener(new java.awt.event.MouseAdapter() {
                final Color orig = bg;
                public void mouseEntered(java.awt.event.MouseEvent e) { panel.setBackground(new Color(60, 60, 88)); }
                public void mouseExited(java.awt.event.MouseEvent e)  { panel.setBackground(orig); }
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    deck.merge(card.getId(), 1, Integer::sum);
                    ref[0].run();
                }
            });
        }

        return panel;
    }

    // ── Deck entry row ────────────────────────────────────────────────────────

    private static JPanel deckEntry(Card card, int count,
                                     Map<String, Integer> deck, Runnable[] ref) {
        Color accent = CardViewer.typeColor(card.getType());

        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setBackground(ENTRY_BG);
        panel.setAlignmentX(0f);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        panel.setPreferredSize(new Dimension(260, 58));
        panel.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 5, 0, 0, accent),
                new EmptyBorder(6, 10, 6, 8)));

        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setOpaque(false);
        info.add(lbl(card.getName(), Font.BOLD, 13, Color.WHITE));
        info.add(lbl(card.getType() + "  ·  ATK " + card.getAttack() + "  HP " + card.getHp(),
                     Font.PLAIN, 10, new Color(145, 145, 170)));

        JLabel countLbl = new JLabel("×" + count, SwingConstants.CENTER);
        countLbl.setFont(new Font("SansSerif", Font.BOLD, 18));
        countLbl.setForeground(new Color(220, 200, 100));
        countLbl.setPreferredSize(new Dimension(36, 0));

        JButton removeBtn = new JButton("−");
        removeBtn.setFont(new Font("SansSerif", Font.BOLD, 18));
        removeBtn.setForeground(new Color(220, 80, 80));
        removeBtn.setBackground(ENTRY_BG);
        removeBtn.setBorderPainted(false);
        removeBtn.setFocusPainted(false);
        removeBtn.setContentAreaFilled(false);
        removeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        removeBtn.addActionListener(e -> {
            int cur = deck.getOrDefault(card.getId(), 0);
            if (cur <= 1) deck.remove(card.getId());
            else          deck.put(card.getId(), cur - 1);
            ref[0].run();
        });

        JPanel right = new JPanel(new BorderLayout(4, 0));
        right.setOpaque(false);
        right.add(countLbl,  BorderLayout.CENTER);
        right.add(removeBtn, BorderLayout.EAST);

        panel.add(info,  BorderLayout.CENTER);
        panel.add(right, BorderLayout.EAST);
        return panel;
    }

    // ── Controls bar ──────────────────────────────────────────────────────────

    private static JPanel buildControls(Sort[] sortBy, String[] search,
                                         Runnable[] ref, JButton[] sortBtnArr) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(new Color(20, 20, 32));
        panel.setBorder(new EmptyBorder(10, 10, 8, 10));

        JLabel libTitle = new JLabel("Your Cards");
        libTitle.setFont(new Font("SansSerif", Font.BOLD, 16));
        libTitle.setForeground(Color.WHITE);

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
        topRow.add(libTitle,    BorderLayout.WEST);
        topRow.add(searchField, BorderLayout.CENTER);

        JPanel sortRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        sortRow.setOpaque(false);

        JLabel sortLbl = new JLabel("Sort by:");
        sortLbl.setFont(new Font("SansSerif", Font.BOLD, 11));
        sortLbl.setForeground(new Color(130, 130, 155));
        sortRow.add(sortLbl);

        String[] labels = { "Name", "Type", "ATK", "HP", "ID", "Owned" };
        Sort[]   vals   = Sort.values();
        for (int i = 0; i < vals.length; i++) {
            final Sort s  = vals[i];
            JButton btn   = new JButton(labels[i]);
            btn.setFont(new Font("SansSerif", Font.BOLD, 11));
            btn.setFocusPainted(false);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.addActionListener(e -> { sortBy[0] = s; ref[0].run(); });
            sortBtnArr[i] = btn;
            sortRow.add(btn);
        }

        panel.add(topRow,  BorderLayout.NORTH);
        panel.add(sortRow, BorderLayout.SOUTH);
        return panel;
    }

    // ── Save deck ─────────────────────────────────────────────────────────────

    private static void saveDeck(User user, Map<String, Card> byId,
                                  Map<String, Integer> deck, JLabel countLabel) {
        new File("user_cards").mkdirs();
        File file = new File("user_cards/" + user.getUsername() + "_deck.txt");
        try (BufferedWriter w = new BufferedWriter(new FileWriter(file))) {
            for (Map.Entry<String, Integer> e : deck.entrySet()) {
                if (e.getValue() <= 0) continue;
                Card c = byId.get(e.getKey());
                if (c == null) continue;
                for (int i = 0; i < e.getValue(); i++) {
                    w.write(c.toString());
                    w.newLine();
                }
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, "Failed to save deck: " + ex.getMessage());
            return;
        }
        String prev = countLabel.getText();
        Color  prevClr = countLabel.getForeground();
        countLabel.setForeground(new Color(100, 220, 130));
        countLabel.setText("Saved!");
        javax.swing.Timer t = new javax.swing.Timer(1500,
                e -> { countLabel.setText(prev); countLabel.setForeground(prevClr); });
        t.setRepeats(false);
        t.start();
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private static JPanel buildHeader(String text, Runnable onBack) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(20, 20, 30));
        header.setBorder(new EmptyBorder(14, 14, 8, 14));

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
        top.setForeground(new Color(140, 140, 160));
        JLabel val = new JLabel(String.valueOf(value), SwingConstants.CENTER);
        val.setFont(new Font("SansSerif", Font.BOLD, 13));
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
