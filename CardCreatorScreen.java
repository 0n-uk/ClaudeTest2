import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.io.*;
import java.util.Random;

public class CardCreatorScreen {

    static final String[] TYPES = {
        "creature", "mage", "elemental", "undead", "holy", "nature",
        "assassin", "demon", "aquatic", "beast", "ranger", "warrior",
        "spirit", "construct", "fae", "dragon"
    };

    static final String[] ABILITIES = {
        "Taunt",
        "Stealth",
        "Haste",
        "Lifesteal",
        "Cleave",
        "Piercing Shot",
        "Charge: can attack immediately",
        "Shield: absorbs first 3 damage taken",
        "Spell Shield: immune to spell damage",
        "Evasion: 30% dodge chance",
        "Aerial: cannot be targeted by ground units",
        "Poison: target takes 1 damage per turn",
        "Freeze target for 1 turn",
        "Drain: heals 1 HP on hit",
        "Regenerate 1 HP per turn",
        "Regenerate 2 HP per turn",
        "Backstab: double damage if enemy has full HP",
        "Enrage: gains +2 attack when damaged",
        "Fear: enemy cannot attack next turn",
        "Charm: enemy skips next attack",
        "Stun target on hit for 1 turn",
        "Infect: target loses 1 HP per turn",
        "Rebirth: returns with 2 HP on death once",
        "Chain Lightning: bounces to 2 additional targets",
        "Aura: adjacent allies gain +1 HP per turn",
        "Flood: all ground units take 2 damage",
        "Meteor: deals 4 damage to one enemy, 1 to all others",
        "Purify: removes all debuffs from an ally",
        "Vanish: returns to hand after attacking",
        "Overgrowth: summons a 1/3 Vine Wall"
    };

    public static JPanel buildPanel(Runnable onBack, Runnable onSaved) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(20, 20, 30));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(20, 20, 30));
        header.setBorder(new EmptyBorder(14, 14, 6, 14));

        JButton backBtn = MenuScreen.menuButton("Back", new Color(180, 180, 200));
        backBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        backBtn.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(180, 180, 200), 2, true),
                new EmptyBorder(6, 18, 6, 18)));
        backBtn.addActionListener(e -> onBack.run());

        JLabel title = new JLabel("Create a Card", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 26));
        title.setForeground(Color.WHITE);

        header.add(backBtn, BorderLayout.WEST);
        header.add(title,   BorderLayout.CENTER);

        // Form
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(new Color(30, 30, 45));
        form.setBorder(new EmptyBorder(20, 40, 20, 40));

        GridBagConstraints lc = new GridBagConstraints();
        lc.anchor = GridBagConstraints.WEST;
        lc.insets = new Insets(8, 8, 8, 12);
        lc.gridx = 0;

        GridBagConstraints fc = new GridBagConstraints();
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.weightx = 1;
        fc.insets = new Insets(8, 0, 8, 8);
        fc.gridx = 1;

        JTextField nameField = field();
        JComboBox<String> typeBox = new JComboBox<>(TYPES);
        styleCombo(typeBox);
        JSpinner atkSpinner  = spinner();
        JSpinner hpSpinner   = spinner();
        JSpinner costSpinner = spinner();
        JList<String> abilityList = new JList<>(ABILITIES);
        abilityList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        abilityList.setSelectedIndex(0);
        abilityList.setBackground(new Color(40, 40, 60));
        abilityList.setForeground(new Color(220, 200, 120));
        abilityList.setFont(new Font("SansSerif", Font.PLAIN, 13));
        abilityList.setFixedCellHeight(26);
        JScrollPane abilityScroll = new JScrollPane(abilityList);
        abilityScroll.setPreferredSize(new Dimension(300, 160));
        abilityScroll.setBorder(new LineBorder(new Color(80, 80, 110), 1));

        String[][] rows = {{"Name", null}, {"Type", null}, {"Attack", null}, {"HP", null}, {"Cost", null}, {"Ability", null}};
        Component[] fields = {nameField, typeBox, atkSpinner, hpSpinner, costSpinner, abilityScroll};

        for (int i = 0; i < fields.length; i++) {
            lc.gridy = fc.gridy = i;
            form.add(rowLabel(rows[i][0]), lc);
            form.add(fields[i], fc);
        }

        // Preview label
        JLabel preview = new JLabel(" ", SwingConstants.CENTER);
        preview.setFont(new Font("SansSerif", Font.ITALIC, 12));
        preview.setForeground(new Color(120, 220, 120));
        preview.setBorder(new EmptyBorder(6, 0, 0, 0));

        // Save button
        JButton saveBtn = MenuScreen.menuButton("Save Card", new Color(100, 220, 130));
        saveBtn.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                preview.setForeground(new Color(220, 80, 80));
                preview.setText("Name cannot be empty.");
                return;
            }
            String type    = (String) typeBox.getSelectedItem();
            int    attack  = (int) atkSpinner.getValue();
            int    hp      = (int) hpSpinner.getValue();
            int    cost    = (int) costSpinner.getValue();
            String ability = abilityList.getSelectedValue();
            String id      = generateId();

            Card card = new Card(id, name, type, attack, hp, cost, ability);
            if (appendCard(card)) {
                preview.setForeground(new Color(120, 220, 120));
                preview.setText("Card '" + name + "' saved with ID " + id + "!");
                nameField.setText("");
                onSaved.run();
            } else {
                preview.setForeground(new Color(220, 80, 80));
                preview.setText("Failed to save card.");
            }
        });

        GridBagConstraints bc = new GridBagConstraints();
        bc.gridx = 0; bc.gridy = fields.length; bc.gridwidth = 2;
        bc.insets = new Insets(16, 0, 4, 0);
        form.add(saveBtn, bc);

        bc.gridy = fields.length + 1;
        bc.insets = new Insets(0, 0, 0, 0);
        form.add(preview, bc);

        JScrollPane formScroll = new JScrollPane(form);
        formScroll.setBorder(null);
        formScroll.getVerticalScrollBar().setUnitIncrement(16);

        panel.add(header,     BorderLayout.NORTH);
        panel.add(formScroll, BorderLayout.CENTER);
        return panel;
    }

    private static JTextField field() {
        JTextField f = new JTextField();
        f.setBackground(new Color(40, 40, 60));
        f.setForeground(Color.WHITE);
        f.setCaretColor(Color.WHITE);
        f.setFont(new Font("SansSerif", Font.PLAIN, 14));
        f.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(80, 80, 110), 1),
                new EmptyBorder(4, 8, 4, 8)));
        f.setPreferredSize(new Dimension(300, 32));
        return f;
    }

    private static JSpinner spinner() {
        JSpinner s = new JSpinner(new SpinnerNumberModel(1, 0, 99, 1));
        s.setBackground(new Color(40, 40, 60));
        s.setForeground(Color.WHITE);
        s.setFont(new Font("SansSerif", Font.PLAIN, 14));
        JComponent editor = s.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            JTextField tf = ((JSpinner.DefaultEditor) editor).getTextField();
            tf.setBackground(new Color(40, 40, 60));
            tf.setForeground(Color.WHITE);
            tf.setCaretColor(Color.WHITE);
        }
        return s;
    }

    private static void styleCombo(JComboBox<String> box) {
        box.setBackground(new Color(40, 40, 60));
        box.setForeground(Color.WHITE);
        box.setFont(new Font("SansSerif", Font.PLAIN, 14));
        box.setPreferredSize(new Dimension(300, 32));
    }

    private static JLabel rowLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.BOLD, 14));
        l.setForeground(new Color(180, 180, 200));
        return l;
    }

    private static String generateId() {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        Random rng = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) sb.append(chars.charAt(rng.nextInt(chars.length())));
        return sb.toString();
    }

    private static boolean appendCard(Card card) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("cards.txt", true))) {
            writer.newLine();
            writer.write(card.toString());
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
