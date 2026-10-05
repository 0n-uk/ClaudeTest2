import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

/** Look-and-feel and navigation helpers shared by every screen. */
public class UI {

    public static final String GAME_TITLE = "Card Game";

    // Theme colours
    public static final Color BG          = new Color(20, 20, 30);
    public static final Color CARD_BG     = new Color(35, 35, 52);
    public static final Color BTN_BG      = new Color(50, 50, 75);
    public static final Color BTN_HOVER   = new Color(70, 70, 105);
    public static final Color BACK_ACCENT = new Color(180, 180, 200);

    /**
     * Adds a screen to the root panel under the given name and shows it.
     * Any screen previously added under the same name is removed first, so
     * re-opening a screen replaces it instead of piling up old copies.
     */
    static void showScreen(JPanel root, CardLayout layout, JFrame frame, String name, JPanel screen) {
        for (Component c : root.getComponents()) {
            if (name.equals(c.getName())) root.remove(c);
        }
        screen.setName(name);
        root.add(screen, name);
        layout.show(root, name);
        frame.revalidate();
    }

    /** The standard button: navy background, rounded border in the accent colour, lighter on hover. */
    static JButton button(String text, Color accent, int fontSize, Color textColor, Insets padding) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.BOLD, fontSize));
        btn.setForeground(textColor);
        btn.setBackground(BTN_BG);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(accent, 2, true),
                new EmptyBorder(padding)));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setBackground(BTN_HOVER); }
            public void mouseExited(MouseEvent e)  { btn.setBackground(BTN_BG); }
        });
        return btn;
    }

    /** The standard button at menu size, used by most screens. */
    static JButton menuButton(String text, Color accent) {
        return button(text, accent, 18, Color.DARK_GRAY, new Insets(14, 60, 14, 60));
    }

    /** The standard grey "Back" button used across screens. */
    static JButton backButton() {
        return menuButton("Back", BACK_ACCENT);
    }

    /** Replaces a button's text with a "Buttons & Menus" image, or leaves it as a text button if the image is missing. */
    static void applyButtonImage(JButton btn, String fileName, int width) {
        ImageIcon icon = Images.menuImage(fileName, width);
        if (icon != null) {
            btn.setIcon(icon);
            btn.setText("");
            btn.setContentAreaFilled(false);
            btn.setBorderPainted(false);
        }
    }
}
