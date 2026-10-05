import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.swing.*;
import javax.swing.border.*;

public class LoginScreen {

    private static final Color BG            = UI.BG;
    private static final Color CARD_BG       = UI.CARD_BG;
    private static final Color ACCENT        = new Color(100, 140, 255);
    private static final Color SUCCESS_GREEN = new Color(100, 220, 130);
    private static final Color ERROR_RED     = new Color(220, 80, 80);
    private static final Color SUB_FG        = new Color(140, 140, 165);
    private static final Color LABEL_FG      = new Color(180, 180, 200);
    private static final Color FIELD_BG      = new Color(45, 45, 65);
    private static final Color FIELD_BORDER  = new Color(80, 80, 110);
    private static final Color LINK_FG       = new Color(120, 160, 255);

    /** Set when user logs in; used by window-close handler to clean up state. */
    static volatile User currentUser = null;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame(UI.GAME_TITLE);
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    if (currentUser != null) {
                        BattleManager.cancelQueue(currentUser.getUsername());
                        BattleManager.removeHeartbeat(currentUser.getUsername());
                    }
                    System.exit(0);
                }
            });
            frame.setSize(900, 650);
            frame.setLocationRelativeTo(null);

            CardLayout layout = new CardLayout();
            JPanel root = new JPanel(layout);
            root.setBackground(BG);

            root.add(buildLoginPanel(root, layout, frame),    "login");
            root.add(buildRegisterPanel(root, layout, frame), "register");
            layout.show(root, "login");

            frame.add(root);
            frame.setVisible(true);
            MusicPlayer.play();
        });
    }

    // ── Login panel ──────────────────────────────────────────────────────────

    static JPanel buildLoginPanel(JPanel root, CardLayout layout, JFrame frame) {
        JTextField     userField = inputField();
        JPasswordField passField = passField();
        JLabel error = messageLabel(ERROR_RED);

        JButton loginBtn = bigButton("Log In", ACCENT);
        loginBtn.addActionListener(e -> {
            String user = userField.getText().trim();
            String pass = new String(passField.getPassword());
            if (user.isEmpty() || pass.isEmpty()) {
                error.setText("Please enter your username and password.");
                return;
            }
            if (authenticate(user, pass)) {
                navigateToMenu(root, layout, frame, new User(user));
            } else {
                error.setText("Incorrect username or password.");
                passField.setText("");
            }
        });
        submitOnEnter(loginBtn, userField, passField);

        JButton toRegister = linkButton("Don't have an account? Register");
        toRegister.addActionListener(e -> {
            clearForm(new JLabel[]{ error }, userField, passField);
            layout.show(root, "register");
        });

        return centeredForm(
            heading("Welcome Back"), sub("Sign in to your account"), spacer(10),
            labelFor("Username"), userField,
            labelFor("Password"), passField,
            spacer(4), error, loginBtn, spacer(4), toRegister);
    }

    // ── Register panel ───────────────────────────────────────────────────────

    static JPanel buildRegisterPanel(JPanel root, CardLayout layout, JFrame frame) {
        JTextField     userField  = inputField();
        JPasswordField passField  = passField();
        JPasswordField pass2Field = passField();
        JLabel error   = messageLabel(ERROR_RED);
        JLabel success = messageLabel(SUCCESS_GREEN);

        JButton registerBtn = bigButton("Register", SUCCESS_GREEN);
        registerBtn.addActionListener(e -> {
            error.setText(" "); success.setText(" ");
            String user  = userField.getText().trim();
            String pass  = new String(passField.getPassword());
            String pass2 = new String(pass2Field.getPassword());

            if (user.isEmpty() || pass.isEmpty()) {
                error.setText("Username and password cannot be empty."); return;
            }
            if (user.length() < 3) {
                error.setText("Username must be at least 3 characters."); return;
            }
            // ':' separates the name from the password in the accounts file
            if (user.contains(":") || user.matches(".*\\s.*")) {
                error.setText("Username cannot contain spaces or ':'."); return;
            }
            if (pass.length() < 4) {
                error.setText("Password must be at least 4 characters."); return;
            }
            if (!pass.equals(pass2)) {
                error.setText("Passwords do not match."); return;
            }
            if (userExists(user)) {
                error.setText("Username already taken."); return;
            }
            if (!register(user, pass)) {
                error.setText("Could not save your account. Please try again."); return;
            }
            registerBtn.setEnabled(false);
            success.setText("Account created! Logging you in...");
            Timer t = new Timer(1000, ev -> navigateToMenu(root, layout, frame, new User(user)));
            t.setRepeats(false); t.start();
        });
        submitOnEnter(registerBtn, userField, passField, pass2Field);

        JButton toLogin = linkButton("Already have an account? Log In");
        toLogin.addActionListener(e -> {
            clearForm(new JLabel[]{ error, success }, userField, passField, pass2Field);
            layout.show(root, "login");
        });

        return centeredForm(
            heading("Create Account"), sub("Join the game"), spacer(10),
            labelFor("Username"),         userField,
            labelFor("Password"),         passField,
            labelFor("Confirm Password"), pass2Field,
            spacer(4), error, success, registerBtn, spacer(4), toLogin);
    }

    // ── Navigation ───────────────────────────────────────────────────────────

    private static void navigateToMenu(JPanel root, CardLayout layout, JFrame frame, User user) {
        currentUser = user;
        UI.showScreen(root, layout, frame, "menu", MenuScreen.buildMenuPanel(root, layout, frame, user));
    }

    // ── Credential storage ───────────────────────────────────────────────────

    private static final String ACCOUNTS_FILE = GamePaths.ACCOUNTS_FILE;

    static boolean userExists(String username) {
        return findPasswordHash(username) != null;
    }

    /** Saves a new account. Returns false if the accounts file could not be written. */
    static boolean register(String username, String password) {
        try (BufferedWriter w = new BufferedWriter(new FileWriter(ACCOUNTS_FILE, true))) {
            w.write(username + ":" + hash(password));
            w.newLine();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    static boolean authenticate(String username, String password) {
        return hash(password).equals(findPasswordHash(username));
    }

    /** Returns the saved password hash for this username (case-insensitive), or null if there is none. */
    private static String findPasswordHash(String username) {
        try (BufferedReader r = new BufferedReader(new FileReader(ACCOUNTS_FILE))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] parts = line.split(":", 2);
                if (parts[0].equalsIgnoreCase(username)) return parts.length == 2 ? parts[1] : "";
            }
        } catch (IOException ignored) {}
        return null;
    }

    private static String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            // Never fall back to saving the plain password
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    // ── UI helpers ───────────────────────────────────────────────────────────

    /** Centres a form box on a full-screen background and fills it with the given rows. */
    private static JPanel centeredForm(Component... rows) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(BG);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(ACCENT, 2, true),
                new EmptyBorder(30, 40, 30, 40)));
        addRows(card, rows);

        panel.add(card, new GridBagConstraints());
        return panel;
    }

    private static JLabel label(String text, int style, int size, Color color, boolean centered) {
        JLabel l = new JLabel(text, centered ? SwingConstants.CENTER : SwingConstants.LEADING);
        l.setFont(new Font("SansSerif", style, size));
        l.setForeground(color);
        return l;
    }

    private static JLabel heading(String text) {
        return label(text, Font.BOLD, 26, Color.WHITE, true);
    }

    private static JLabel sub(String text) {
        return label(text, Font.PLAIN, 13, SUB_FG, true);
    }

    private static JLabel labelFor(String text) {
        return label(text, Font.BOLD, 13, LABEL_FG, false);
    }

    /** An empty message line; " " keeps its height so the form doesn't jump when text appears. */
    private static JLabel messageLabel(Color color) {
        return label(" ", Font.PLAIN, 12, color, true);
    }

    private static JTextField inputField() {
        JTextField f = new JTextField();
        style(f);
        return f;
    }

    private static JPasswordField passField() {
        JPasswordField f = new JPasswordField();
        style(f);
        return f;
    }

    private static void style(JTextField f) {
        f.setBackground(FIELD_BG);
        f.setForeground(Color.WHITE);
        f.setCaretColor(Color.WHITE);
        f.setFont(new Font("SansSerif", Font.PLAIN, 14));
        f.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(FIELD_BORDER, 1),
                new EmptyBorder(6, 10, 6, 10)));
        f.setPreferredSize(new Dimension(300, 38));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
    }

    /** Pressing Enter in any of the fields clicks the button. */
    private static void submitOnEnter(JButton button, JTextField... fields) {
        for (JTextField f : fields) f.addActionListener(e -> button.doClick());
    }

    private static void clearForm(JLabel[] messages, JTextField... fields) {
        for (JLabel m : messages) m.setText(" ");
        for (JTextField f : fields) f.setText("");
    }

    /** The standard button, full width with white text. */
    private static JButton bigButton(String text, Color accent) {
        JButton b = UI.button(text, accent, 15, Color.WHITE, new Insets(10, 0, 10, 0));
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        return b;
    }

    private static JButton linkButton(String text) {
        JButton b = new JButton("<html><u>" + text + "</u></html>");
        b.setFont(new Font("SansSerif", Font.PLAIN, 12));
        b.setForeground(LINK_FG);
        b.setBackground(CARD_BG);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private static Component spacer(int h) {
        return Box.createVerticalStrut(h);
    }

    /**
     * Stacks the rows top to bottom. Every row is left-aligned and labels and
     * buttons stretch to the full width (centring their own text), because
     * BoxLayout lines rows up badly when their alignments are mixed.
     */
    private static void addRows(JPanel card, Component... components) {
        for (Component c : components) {
            if (c instanceof JComponent) {
                JComponent jc = (JComponent) c;
                jc.setAlignmentX(Component.LEFT_ALIGNMENT);
                if (jc instanceof JLabel || jc instanceof JButton) {
                    jc.setMaximumSize(new Dimension(Integer.MAX_VALUE, jc.getPreferredSize().height));
                }
            }
            card.add(c);
            if (c instanceof JTextField) {
                card.add(Box.createVerticalStrut(10));
            }
        }
    }
}
