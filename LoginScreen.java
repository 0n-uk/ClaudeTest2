import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;

public class LoginScreen {

    // White, hand-drawn style to match the menu and the button images
    private static final Color BG            = Color.WHITE;
    private static final Color CARD_BG       = Color.WHITE;
    private static final Color INK           = new Color(30, 30, 30);
    private static final Color ACCENT        = new Color(100, 140, 255);
    private static final Color SUCCESS_GREEN = new Color(30, 150, 70);
    private static final Color ERROR_RED     = new Color(200, 40, 40);
    private static final Color SUB_FG        = new Color(110, 110, 130);
    private static final Color LABEL_FG      = new Color(60, 60, 80);
    private static final Color LINK_FG       = new Color(40, 90, 220);

    private static final int LOGIN_BTN_WIDTH    = 180;
    private static final int REGISTER_BTN_WIDTH = 220;
    private static final int HEADING_IMG_WIDTH  = 280;

    /** Adds the Log In and Register forms to the game window. */
    static void addTo(GameWindow win) {
        win.show(GameWindow.REGISTER, buildRegisterPanel(win));
        win.show(GameWindow.LOGIN,    buildLoginPanel(win));
    }

    // ── Login panel ──────────────────────────────────────────────────────────

    private static JPanel buildLoginPanel(GameWindow win) {
        JTextField     userField = inputField();
        JPasswordField passField = passField();
        JLabel error = messageLabel(ERROR_RED);

        JButton loginBtn = bigButton("Log In", ACCENT);
        UI.applyButtonImage(loginBtn, "LoginButton.png", LOGIN_BTN_WIDTH);
        loginBtn.addActionListener(e -> {
            String user = userField.getText().trim();
            String pass = new String(passField.getPassword());
            if (user.isEmpty() || pass.isEmpty()) {
                error.setText("Please enter your username and password.");
                return;
            }
            String savedName = GameData.login(user, pass);
            if (savedName != null) {
                win.logIn(new User(savedName));
            } else {
                error.setText("Incorrect username or password.");
                passField.setText("");
            }
        });
        submitOnEnter(loginBtn, userField, passField);

        JButton toRegister = linkButton("Don't have an account? Register");
        toRegister.addActionListener(e -> {
            clearForm(new JLabel[]{ error }, userField, passField);
            win.show(GameWindow.REGISTER);
        });

        // The drawn "Welcome! Sign in plz" heading replaces both text lines when the image is there
        ImageIcon headingImg = Images.menuImage("LoginMenu.png", HEADING_IMG_WIDTH);
        Component title    = headingImg != null ? new JLabel(headingImg) : heading("Welcome Back");
        Component subtitle = headingImg != null ? spacer(0)              : sub("Sign in to your account");

        return centeredForm(
            title, subtitle, spacer(10),
            labelFor("Username"), userField,
            labelFor("Password"), passField,
            spacer(4), error, loginBtn, spacer(4), toRegister);
    }

    // ── Register panel ───────────────────────────────────────────────────────

    private static JPanel buildRegisterPanel(GameWindow win) {
        JTextField     userField  = inputField();
        JPasswordField passField  = passField();
        JPasswordField pass2Field = passField();
        JLabel error   = messageLabel(ERROR_RED);
        JLabel success = messageLabel(SUCCESS_GREEN);

        JButton registerBtn = bigButton("Register", SUCCESS_GREEN);
        UI.applyButtonImage(registerBtn, "RegisterButton.png", REGISTER_BTN_WIDTH);
        registerBtn.addActionListener(e -> {
            error.setText(" "); success.setText(" ");
            String user  = userField.getText().trim();
            String pass  = new String(passField.getPassword());
            String pass2 = new String(pass2Field.getPassword());

            String problem = GameData.checkNewAccount(user, pass, pass2);
            if (problem != null) {
                error.setText(problem); return;
            }
            if (!GameData.register(user, pass)) {
                error.setText("Could not save your account. Please try again."); return;
            }
            registerBtn.setEnabled(false);
            success.setText("Account created! Logging you in...");
            Timer t = new Timer(1000, ev -> win.logIn(new User(user)));
            t.setRepeats(false); t.start();
        });
        submitOnEnter(registerBtn, userField, passField, pass2Field);

        JButton toLogin = linkButton("Already have an account? Log In");
        toLogin.addActionListener(e -> {
            clearForm(new JLabel[]{ error, success }, userField, passField, pass2Field);
            win.show(GameWindow.LOGIN);
        });

        return centeredForm(
            heading("Create Account"), sub("Join the game"), spacer(10),
            labelFor("Username"),         userField,
            labelFor("Password"),         passField,
            labelFor("Confirm Password"), pass2Field,
            spacer(4), error, success, registerBtn, spacer(4), toLogin);
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
                new LineBorder(INK, 3, true),
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
        return label(text, Font.BOLD, 26, INK, true);
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
        f.setBackground(Color.WHITE);
        f.setForeground(INK);
        f.setCaretColor(INK);
        f.setFont(new Font("SansSerif", Font.PLAIN, 14));
        f.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(INK, 2),
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
