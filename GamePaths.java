// Every file and folder the game reads or writes, relative to the folder the game is run from.
// If assets move again, only this class needs to change.
public class GamePaths {

    public static final String RESOURCES   = "resources";

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

    // Fonts
    public static final String FONTS_DIR = RESOURCES + "/fonts";
    public static final String CARD_FONT = FONTS_DIR + "/PixelifySans.ttf";   // SIL Open Font License, see OFL.txt

    // Music
    public static final String MUSIC_DIR    = RESOURCES + "/music";
    public static final String MENU_MUSIC   = MUSIC_DIR + "/Simple Scales.wav";
    public static final String BATTLE_MUSIC = MUSIC_DIR + "/Escelator.wav";

    // Battles (live match state, not moved into resources)
    public static final String BATTLES_DIR   = "battles";
    public static final String ACTIVE_DIR    = BATTLES_DIR + "/active";
    public static final String QUEUE_FILE    = BATTLES_DIR + "/queue.txt";
    public static final String HEARTBEAT_DIR = BATTLES_DIR + "/heartbeat";
}
