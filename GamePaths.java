import java.io.File;

// Every file and folder the game reads or writes, inside the game's folder.
// If assets move again, only this class needs to change.
public class GamePaths {

    /**
     * The folder holding "resources". Usually the folder the game is run from,
     * but IDEs often start it somewhere else, so it also looks next to the
     * compiled classes and the folders above them.
     */
    public static final String GAME_DIR    = findGameDir();
    public static final String RESOURCES   = GAME_DIR + "/resources";

    // Game data
    public static final String CARDS_FILE     = RESOURCES + "/cards.txt";
    public static final String CHAMPIONS_FILE = RESOURCES + "/champions.txt";
    public static final String ACCOUNTS_FILE  = RESOURCES + "/accounts.txt";
    public static final String PLAYER_DATA_DIR = RESOURCES + "/user_cards";   // cards, decks and cooldowns

    // Images
    public static final String IMAGES_DIR       = RESOURCES + "/images";
    public static final String CARD_IMAGES_DIR  = IMAGES_DIR + "/cards/";
    public static final String TYPE_SYMBOLS_DIR = IMAGES_DIR + "/TypeSymbols/";
    public static final String MENU_IMAGES_DIR  = IMAGES_DIR + "/Buttons & Menus/";   // buttons, title, headings
    public static final String CARD_BACKGROUND  = IMAGES_DIR + "/cardbackground.png";
    public static final String CARD_FOREGROUND  = IMAGES_DIR + "/cardforeground.png";

    // Music
    public static final String MUSIC_DIR    = RESOURCES + "/music";
    public static final String MENU_MUSIC   = MUSIC_DIR + "/Simple Scales.wav";
    public static final String BATTLE_MUSIC = MUSIC_DIR + "/Escelator.wav";

    // Battles (live match state, not moved into resources)
    public static final String BATTLES_DIR   = GAME_DIR + "/battles";
    public static final String ACTIVE_DIR    = BATTLES_DIR + "/active";
    public static final String QUEUE_FILE    = BATTLES_DIR + "/queue.txt";
    public static final String HEARTBEAT_DIR = BATTLES_DIR + "/heartbeat";

    private static String findGameDir() {
        if (new File("resources").isDirectory()) return new File("").getAbsolutePath();
        try {
            File dir = new File(GamePaths.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            for (; dir != null; dir = dir.getParentFile()) {
                if (new File(dir, "resources").isDirectory()) return dir.getAbsolutePath();
            }
        } catch (Exception ignored) {}
        System.err.println("Could not find the resources folder. Run the game from the project folder.");
        return new File("").getAbsolutePath();
    }
}
