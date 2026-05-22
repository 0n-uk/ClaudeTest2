import javax.swing.*;
import javax.swing.border.*;
// Use explicit type to resolve Timer ambiguity with java.util.Timer
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PackScreen {

    private static final Color BG       = new Color(20, 20, 30);
    private static final Color CARD_BG  = new Color(35, 35, 52);
    private static final Color REVEAL_BG = new Color(25, 25, 38);

    // ── Entry point ───────────────────────────────────────────────────────────

    public static JPanel buildPanel(User user, Runnable onBack) {
        List<javax.swing.Timer> timers = new ArrayList<>();
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(BG);

        Runnable stopAndBack = () -> {
            timers.forEach(javax.swing.Timer::stop);
            timers.clear();
            onBack.run();
        };

        showSelection(wrapper, user, stopAndBack, timers);
        return wrapper;
    }

    // ── Pack selection screen ─────────────────────────────────────────────────

    private static void showSelection(JPanel wrapper, User user, Runnable onBack, List<javax.swing.Timer> timers) {
        timers.forEach(javax.swing.Timer::stop);
        timers.clear();
        wrapper.removeAll();

        List<Card> all = CardViewer.loadCards("cards.txt");

        Set<String> starterIds = new HashSet<>(Arrays.asList(
            "gb001", "hd001", "stk001", "__SCRAP__", "sb001",
            "bb001", "shb001", "upb001", "wsp001", "rod001", "smi001",
            "shs001", "drw001", "lmt001", "glm001", "bte001", "esr001",
            "frs001", "ics001", "wts001", "nts001", "ers001", "wns001",
            "cld001", "icd001", "spd001",
            "ecs001", "cng001",
            "pwn001", "trp001",
            // Bug cards
            "wka001", "wka002", "fly001", "rpl001", "spl001",
            "msp001", "lcp001", "ebw001",
            // Mage cards
            "bgm001", "prd001", "frm001", "tmw001", "dod001", "gwz001", "ssk001",
            // Cryptid, Beast, Knight, Spirit new cards
            "asl001", "glc001", "mth001", "cro001", "soo001", "cdr001", "gwg001",
            "slb001", "lcr001", "rok001", "cat001", "ssp001", "trc001"));

        Set<String> scrapIds = new HashSet<>(Arrays.asList(
            "sb001", "bb001", "shb001",
            "cnb001", "mtb001", "tlb001", "mnb001", "fnb001", "trb001",
            "mwb001", "itb001", "svb001", "ttb001", "rcb001"));

        Set<String> endoraIds = new HashSet<>(Arrays.asList(
            "psh001", "pod001", "sen001", "sbu001",
            "gen001", "dru001", "gbu001", "tbu001",
            "ima001", "trp001", "cmf001", "blw001",
            // Flame and Fungal pod lines
            "ffp001", "fbl001",
            "fgp001", "fgs001", "fgb001", "fhm001", "fgc001",
            // Knight cards
            "bgm001", "prd001", "frm001", "tmw001", "dod001", "gwz001",
            "lcr001", "rok001", "cat001"));

        Set<String> pinewoodsIds = new HashSet<>(Arrays.asList(
            // Spirit cards
            "wsp001", "shs001", "drw001", "glm001", "frs001", "ics001",
            "wts001", "nts001", "ers001", "wns001", "cld001", "ecs001",
            "cng001", "ssk001", "ssp001",
            // Cryptid cards
            "asl001", "glc001", "mth001", "cro001", "soo001", "cdr001",
            "gwg001", "trc001",
            // High-cost beasts
            "blw001", "slb001"));

        List<Card> starter    = new ArrayList<>();
        List<Card> scrap      = new ArrayList<>();
        List<Card> endora     = new ArrayList<>();
        List<Card> pinewoods  = new ArrayList<>();
        for (Card c : all) {
            if (starterIds.contains(c.getId()))       starter.add(c);
            else if (scrapIds.contains(c.getId()))    scrap.add(c);
            else if (endoraIds.contains(c.getId()))   endora.add(c);
            else if (pinewoodsIds.contains(c.getId())) pinewoods.add(c);
        }

        JPanel header     = buildHeader("Open Packs", onBack);
        JPanel packsPanel = new JPanel(new GridLayout(1, 4, 18, 0));
        packsPanel.setBackground(BG);
        packsPanel.setBorder(new EmptyBorder(30, 40, 60, 40));

        packsPanel.add(packCard("STARTER", "Starter Pack",
            "Low-cost cards for beginners — bugs, bots, and basic creatures.",
            new Color(100, 200, 160), starter, user, wrapper, onBack, timers));

        packsPanel.add(packCard("SCRAP", "Scrap Pack",
            "Mechanical bots powered by scrap — build, recycle, and overwhelm.",
            new Color(160, 130, 80), scrap, user, wrapper, onBack, timers));

        packsPanel.add(packCard("ENDORA", "Endora Pack",
            "Ancient pod creatures that transform and evolve over time.",
            new Color(120, 80, 200), endora, user, wrapper, onBack, timers));

        packsPanel.add(packCard("PINEWOODS", "The Pinewoods",
            "Beware the pines...",
            new Color(60, 130, 80), pinewoods, user, wrapper, onBack, timers));

        wrapper.add(header,     BorderLayout.NORTH);
        wrapper.add(packsPanel, BorderLayout.CENTER);
        wrapper.revalidate();
        wrapper.repaint();
    }

    private static JPanel packCard(String packId, String packName, String description,
                                    Color accent, List<Card> pool,
                                    User user, JPanel wrapper, Runnable onBack, List<javax.swing.Timer> timers) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(CARD_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(accent, 2, true),
                new EmptyBorder(28, 20, 28, 20)));

        JLabel nameLabel  = centeredLabel(packName,  Font.BOLD,   24, Color.WHITE);
        JLabel descLabel  = htmlLabel(description,   13, new Color(170, 170, 195));
        JLabel countLabel = centeredLabel("Contains " + pool.size() + " cards  •  Gives 5 per open",
                                          Font.ITALIC, 12, new Color(130, 130, 155));

        JButton openBtn = MenuScreen.menuButton("Open Pack", accent);
        openBtn.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(accent, 2, true),
                new EmptyBorder(10, 36, 10, 36)));
        openBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel timerLabel = centeredLabel(" ", Font.BOLD, 13, new Color(220, 165, 55));

        Runnable refresh = () -> {
            boolean ready = user.canOpenPack(packId);
            openBtn.setEnabled(ready);
            if (ready) {
                timerLabel.setText("Ready to open!");
                timerLabel.setForeground(new Color(100, 220, 130));
            } else {
                long ms = user.msUntilPack(packId);
                long h  = ms / 3_600_000;
                long m  = (ms % 3_600_000) / 60_000;
                long s  = (ms % 60_000)    / 1_000;
                timerLabel.setText(String.format("Next open in  %02d:%02d:%02d", h, m, s));
                timerLabel.setForeground(new Color(220, 165, 55));
            }
        };
        refresh.run();

        javax.swing.Timer tick = new javax.swing.Timer(1000, e -> refresh.run());
        tick.start();
        timers.add(tick);

        openBtn.addActionListener(e -> {
            if (!user.canOpenPack(packId) || pool.isEmpty()) return;
            tick.stop();
            List<Card> shuffled = new ArrayList<>(pool);
            Collections.shuffle(shuffled);
            List<Card> obtained = new ArrayList<>(shuffled.subList(0, Math.min(5, shuffled.size())));
            user.recordPackOpen(packId);
            for (Card c : obtained) user.addCard(c);
            showReveal(wrapper, packName, accent, obtained, user, onBack, timers);
        });

        panel.add(nameLabel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(descLabel);
        panel.add(Box.createVerticalStrut(14));
        panel.add(countLabel);
        panel.add(Box.createVerticalStrut(24));
        panel.add(openBtn);
        panel.add(Box.createVerticalStrut(10));
        panel.add(timerLabel);

        return panel;
    }

    // ── Reveal screen ─────────────────────────────────────────────────────────

    private static void showReveal(JPanel wrapper, String packName, Color accent,
                                    List<Card> obtained, User user, Runnable onBack, List<javax.swing.Timer> timers) {
        timers.forEach(javax.swing.Timer::stop);
        timers.clear();
        wrapper.removeAll();

        Runnable goBack = () -> showSelection(wrapper, user, onBack, timers);

        JPanel header = buildHeader(packName + " Opened!", goBack);

        JLabel subtitle = centeredLabel("You received " + obtained.size() + " new cards — added to your collection!",
                Font.ITALIC, 14, new Color(160, 160, 185));
        subtitle.setBorder(new EmptyBorder(14, 0, 4, 0));

        JPanel cardsRow = new JPanel(new GridLayout(1, obtained.size(), 12, 0));
        cardsRow.setBackground(REVEAL_BG);
        cardsRow.setBorder(new EmptyBorder(16, 24, 16, 24));
        for (Card c : obtained) cardsRow.add(highlightCard(c, accent));

        JButton returnBtn = MenuScreen.menuButton("Return to Packs", accent);
        returnBtn.addActionListener(e -> goBack.run());

        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER));
        south.setBackground(BG);
        south.setBorder(new EmptyBorder(10, 0, 30, 0));
        south.add(returnBtn);

        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(REVEAL_BG);
        center.add(subtitle,  BorderLayout.NORTH);
        center.add(cardsRow,  BorderLayout.CENTER);

        wrapper.add(header, BorderLayout.NORTH);
        wrapper.add(center, BorderLayout.CENTER);
        wrapper.add(south,  BorderLayout.SOUTH);
        wrapper.revalidate();
        wrapper.repaint();
    }

    private static JPanel highlightCard(Card card, Color glowColor) {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(REVEAL_BG);
        wrap.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(glowColor, 3, true),
                new EmptyBorder(3, 3, 3, 3)));
        wrap.add(CardViewer.buildCardPanel(card));
        return wrap;
    }

    // ── Shared header ─────────────────────────────────────────────────────────

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

    // ── Label helpers ─────────────────────────────────────────────────────────

    private static JLabel centeredLabel(String text, int style, int size, Color color) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(new Font("SansSerif", style, size));
        l.setForeground(color);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    private static JLabel htmlLabel(String text, int size, Color color) {
        JLabel l = new JLabel("<html><center>" + text + "</center></html>", SwingConstants.CENTER);
        l.setFont(new Font("SansSerif", Font.PLAIN, size));
        l.setForeground(color);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }
}
