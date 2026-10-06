import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/**
 * The game window and the screens inside it. Every screen is a panel stacked
 * in one CardLayout, shown by name. This is also where the game starts.
 */
public class GameWindow {

    // Screen names
    static final String LOGIN        = "login";
    static final String REGISTER     = "register";
    static final String MENU         = "menu";
    static final String VIEWER       = "viewer";
    static final String OWNED        = "owned";
    static final String PACKS        = "packs";
    static final String DECK         = "deck";
    static final String DECK_SELECT  = "deckselect";
    static final String CHAMP_SELECT = "champselect";
    static final String MATCHMAKING  = "matchmaking";
    static final String BATTLE       = "battle";

    private final JFrame     frame  = new JFrame(UI.GAME_TITLE);
    private final CardLayout layout = new CardLayout();
    private final JPanel     root   = new JPanel(layout);

    /** The logged-in player, or null. Read when the window closes to take them out of matchmaking. */
    private volatile User currentUser;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GameWindow().open());
    }

    private void open() {
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                User user = currentUser;
                if (user != null) {
                    BattleManager.cancelQueue(user.getUsername());
                    BattleManager.removeHeartbeat(user.getUsername());
                }
                System.exit(0);
            }
        });
        frame.setSize(900, 650);
        frame.setLocationRelativeTo(null);

        LoginScreen.addTo(this);
        show(LOGIN);

        frame.add(root);
        frame.setVisible(true);
        MusicPlayer.playMenu();
    }

    /** Remembers who logged in and opens the main menu. */
    void logIn(User user) {
        currentUser = user;
        show(MENU, MenuScreen.buildMenuPanel(this, user));
    }

    /** Shows a screen that was added earlier. */
    void show(String name) {
        layout.show(root, name);
    }

    /**
     * Adds a screen under the given name and shows it. Any screen previously
     * added under the same name is removed first, so re-opening a screen
     * replaces it instead of piling up old copies.
     */
    void show(String name, JPanel screen) {
        for (Component c : root.getComponents()) {
            if (name.equals(c.getName())) root.remove(c);
        }
        screen.setName(name);
        root.add(screen, name);
        layout.show(root, name);
        root.revalidate();
    }

    /** A "go back" action for a Back button: shows the named screen. */
    Runnable backTo(String name) {
        return () -> show(name);
    }
}
