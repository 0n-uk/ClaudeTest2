import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

/** Look-and-feel helpers shared by every screen. */
public class UI {

    public static final String GAME_TITLE = "RonnoCards";

    // Light theme: the login screen and the menu, drawn to match the hand-drawn black-on-white art
    public static final Color LIGHT_BG = Color.WHITE;
    public static final Color INK      = new Color(30, 30, 30);

    // Dark theme: every other screen
    public static final Color BG          = new Color(20, 20, 30);
    public static final Color CARD_BG     = new Color(35, 35, 52);
    public static final Color BTN_BG      = new Color(50, 50, 75);
    public static final Color BTN_HOVER   = new Color(70, 70, 105);
    public static final Color BACK_ACCENT = new Color(180, 180, 200);

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
        return button(text, accent, 18, Color.WHITE, new Insets(14, 60, 14, 60));
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
