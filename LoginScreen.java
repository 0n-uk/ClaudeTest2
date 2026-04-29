import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.swing.*;
import javax.swing.border.*;

//this is a test comment to trigger a commit
public class LoginScreen {

    private static final Color BG      = new Color(20, 20, 30);
    private static final Color CARD_BG = new Color(35, 35, 52);
    private static final Color ACCENT  = new Color(100, 140, 255);

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Card Game");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
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
        });
    }

    // ── Login panel ──────────────────────────────────────────────────────────

    static JPanel buildLoginPanel(JPanel root, CardLayout layout, JFrame frame) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(BG);

        JPanel card = card();

        JLabel title = heading("Welcome Back");
        JLabel sub   = sub("Sign in to your account");

        JTextField  userField = inputField("Username");
        JPasswordField passField = passField("Password");
        JLabel error = errorLabel();

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

        JButton toRegister = linkButton("Don't have an account? Register");
        toRegister.addActionListener(e -> {
            error.setText(" ");
            userField.setText("");
            passField.setText("");
            layout.show(root, "register");
        });

        addRows(card,
            title, sub, spacer(10),
            labelFor("Username"), userField,
            labelFor("Password"), passField,
            spacer(4), error, loginBtn, spacer(4), toRegister);

        GridBagConstraints gbc = new GridBagConstraints();
        panel.add(card, gbc);
        return panel;
    }

    // ── Register panel ───────────────────────────────────────────────────────

    static JPanel buildRegisterPanel(JPanel root, CardLayout layout, JFrame frame) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(BG);

        JPanel card = card();

        JLabel title = heading("Create Account");
        JLabel sub   = sub("Join the game");

        JTextField   userField    = inputField("Username");
        JPasswordField passField  = passField("Password");
        JPasswordField pass2Field = passField("Confirm Password");
        JLabel error = errorLabel();
        JLabel success = successLabel();

        JButton registerBtn = bigButton("Register", new Color(100, 220, 130));
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
            if (pass.length() < 4) {
                error.setText("Password must be at least 4 characters."); return;
            }
            if (!pass.equals(pass2)) {
                error.setText("Passwords do not match."); return;
            }
            if (userExists(user)) {
                error.setText("Username already taken."); return;
            }
            register(user, pass);
            success.setText("Account created! Logging you in...");
            Timer t = new Timer(1000, ev -> navigateToMenu(root, layout, frame, new User(user)));
            t.setRepeats(false); t.start();
        });

        JButton toLogin = linkButton("Already have an account? Log In");
        toLogin.addActionListener(e -> {
            error.setText(" "); success.setText(" ");
            userField.setText(""); passField.setText(""); pass2Field.setText("");
            layout.show(root, "login");
        });

        addRows(card,
            title, sub, spacer(10),
            labelFor("Username"),         userField,
            labelFor("Password"),         passField,
            labelFor("Confirm Password"), pass2Field,
            spacer(4), error, success, registerBtn, spacer(4), toLogin);

        GridBagConstraints gbc = new GridBagConstraints();
        panel.add(card, gbc);
        return panel;
    }

    // ── Navigation ───────────────────────────────────────────────────────────

    private static void navigateToMenu(JPanel root, CardLayout layout, JFrame frame, User user) {
        JPanel menuPanel = MenuScreen.buildMenuPanel(root, layout, frame, user);
        root.add(menuPanel, "menu");
        layout.show(root, "menu");
        frame.revalidate();
    }

    // ── Credential storage ───────────────────────────────────────────────────

    private static final String ACCOUNTS_FILE = "accounts.txt";

    static boolean userExists(String username) {
        try (BufferedReader r = new BufferedReader(new FileReader(ACCOUNTS_FILE))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.split(":")[0].equalsIgnoreCase(username)) return true;
            }
        } catch (IOException ignored) {}
        return false;
    }

    static void register(String username, String password) {
        try (BufferedWriter w = new BufferedWriter(new FileWriter(ACCOUNTS_FILE, true))) {
            w.write(username + ":" + hash(password));
            w.newLine();
        } catch (IOException ignored) {}
    }

    static boolean authenticate(String username, String password) {
        String hashed = hash(password);
        try (BufferedReader r = new BufferedReader(new FileReader(ACCOUNTS_FILE))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2
                        && parts[0].equalsIgnoreCase(username)
                        && parts[1].equals(hashed)) return true;
            }
        } catch (IOException ignored) {}
        return false;
    }

    private static String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return input;
        }
    }

    // ── UI helpers ───────────────────────────────────────────────────────────

    private static JPanel card() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(CARD_BG);
        p.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(ACCENT, 2, true),
                new EmptyBorder(30, 40, 30, 40)));
        p.setPreferredSize(new Dimension(380, 0));
        return p;
    }

    private static JLabel heading(String text) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(new Font("SansSerif", Font.BOLD, 26));
        l.setForeground(Color.WHITE);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    private static JLabel sub(String text) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(new Font("SansSerif", Font.PLAIN, 13));
        l.setForeground(new Color(140, 140, 165));
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    private static JLabel labelFor(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.BOLD, 13));
        l.setForeground(new Color(180, 180, 200));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JTextField inputField(String placeholder) {
        JTextField f = new JTextField();
        style(f);
        return f;
    }

    private static JPasswordField passField(String placeholder) {
        JPasswordField f = new JPasswordField();
        style(f);
        return f;
    }

    private static void style(JTextField f) {
        f.setBackground(new Color(45, 45, 65));
        f.setForeground(Color.WHITE);
        f.setCaretColor(Color.WHITE);
        f.setFont(new Font("SansSerif", Font.PLAIN, 14));
        f.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(80, 80, 110), 1),
                new EmptyBorder(6, 10, 6, 10)));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        f.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private static JButton bigButton(String text, Color accent) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.BOLD, 15));
        b.setForeground(Color.WHITE);
        b.setBackground(new Color(50, 50, 75));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(accent, 2, true),
                new EmptyBorder(10, 0, 10, 0)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            Color orig = b.getBackground();
            public void mouseEntered(java.awt.event.MouseEvent e) { b.setBackground(new Color(70, 70, 105)); }
            public void mouseExited(java.awt.event.MouseEvent e)  { b.setBackground(orig); }
        });
        return b;
    }

    private static JButton linkButton(String text) {
        JButton b = new JButton("<html><u>" + text + "</u></html>");
        b.setFont(new Font("SansSerif", Font.PLAIN, 12));
        b.setForeground(new Color(120, 160, 255));
        b.setBackground(CARD_BG);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        return b;
    }

    private static JLabel errorLabel() {
        JLabel l = new JLabel(" ", SwingConstants.CENTER);
        l.setFont(new Font("SansSerif", Font.PLAIN, 12));
        l.setForeground(new Color(220, 80, 80));
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    private static JLabel successLabel() {
        JLabel l = new JLabel(" ", SwingConstants.CENTER);
        l.setFont(new Font("SansSerif", Font.PLAIN, 12));
        l.setForeground(new Color(100, 220, 130));
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    private static Component spacer(int h) {
        return Box.createVerticalStrut(h);
    }

    private static void addRows(JPanel card, Component... components) {
        for (Component c : components) {
            card.add(c);
            if (c instanceof JTextField || c instanceof JPasswordField) {
                card.add(Box.createVerticalStrut(10));
            }
        }
    }
}
