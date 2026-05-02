import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.Map;
import java.util.function.Consumer;

public class MatchmakingScreen {

    private static final Color BG      = new Color(20, 20, 30);
    private static final Color CARD_BG = new Color(35, 35, 52);

    public static JPanel buildPanel(User user, String deckName, String champLine,
                                    Runnable onCancel, Consumer<String> onMatchFound) {
        String username = user.getUsername();

        Map<String, ChampionLine> champLines = ChampionLine.loadAll();
        ChampionLine cl = champLines.get(champLine);
        String champName = (cl != null && cl.size() > 0) ? cl.getStageByIndex(0).getName() + " line" : champLine;

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(BG);

        JLabel titleLabel = new JLabel("Searching for Opponent...", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 26));
        titleLabel.setForeground(Color.WHITE);

        JLabel deckLabel = new JLabel("Deck: " + deckName + "  ·  Champion: " + champName, SwingConstants.CENTER);
        deckLabel.setFont(new Font("SansSerif", Font.ITALIC, 16));
        deckLabel.setForeground(new Color(160, 160, 180));

        JLabel waitLabel = new JLabel("Please wait...", SwingConstants.CENTER);
        waitLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        waitLabel.setForeground(new Color(120, 120, 150));

        JButton cancelBtn = MenuScreen.menuButton("Cancel", new Color(200, 80, 80));
        cancelBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        cancelBtn.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(200, 80, 80), 2, true),
                new EmptyBorder(8, 24, 8, 24)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.insets = new Insets(10, 0, 10, 0);

        gbc.gridy = 0; panel.add(titleLabel, gbc);
        gbc.gridy = 1; panel.add(deckLabel,  gbc);
        gbc.gridy = 2; panel.add(waitLabel,  gbc);
        gbc.gridy = 3; gbc.insets = new Insets(24, 0, 0, 0);
        panel.add(cancelBtn, gbc);

        // Dots animation on waitLabel
        javax.swing.Timer dotTimer = new javax.swing.Timer(600, null);
        int[] dotCount = {0};
        dotTimer.addActionListener(e -> {
            dotCount[0] = (dotCount[0] + 1) % 4;
            String dots = ".".repeat(dotCount[0]);
            waitLabel.setText("Please wait" + dots);
        });
        dotTimer.start();

        // Heartbeat timer — keeps our queue slot alive
        BattleManager.writeHeartbeat(username);
        javax.swing.Timer hbTimer = new javax.swing.Timer(2000, e -> BattleManager.writeHeartbeat(username));
        hbTimer.start();

        // Match-found helper
        Runnable[] stopTimers = {null};

        Consumer<String> handleMatch = battleId -> {
            if (stopTimers[0] != null) stopTimers[0].run();
            // Keep heartbeat file alive so BattleScreen can pick it up immediately
            titleLabel.setText("Opponent found!");
            titleLabel.setForeground(new Color(80, 220, 120));
            waitLabel.setVisible(false);
            deckLabel.setVisible(false);
            cancelBtn.setEnabled(false);
            javax.swing.Timer showTimer = new javax.swing.Timer(800, ev -> onMatchFound.accept(battleId));
            showTimer.setRepeats(false);
            showTimer.start();
        };

        // Try to join queue immediately
        String immediateId = BattleManager.joinQueue(username, deckName, champLine);

        if (immediateId != null) {
            // Matched instantly — delay so panel can render first
            javax.swing.Timer delayTimer = new javax.swing.Timer(500, e -> handleMatch.accept(immediateId));
            delayTimer.setRepeats(false);
            delayTimer.start();
            stopTimers[0] = () -> { dotTimer.stop(); hbTimer.stop(); };
        } else {
            // Poll for a match
            javax.swing.Timer pollTimer = new javax.swing.Timer(1000, null);
            pollTimer.addActionListener(e -> {
                String found = BattleManager.pollForMatch(username);
                if (found != null) {
                    pollTimer.stop();
                    handleMatch.accept(found);
                }
            });
            pollTimer.start();
            stopTimers[0] = () -> { dotTimer.stop(); hbTimer.stop(); pollTimer.stop(); };
        }

        cancelBtn.addActionListener(e -> {
            if (stopTimers[0] != null) stopTimers[0].run();
            BattleManager.cancelQueue(username);
            BattleManager.removeHeartbeat(username);
            onCancel.run();
        });

        return panel;
    }
}
