import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

/**
 * Loads every image in resources/images. Each file is read from disk once and
 * each scaled size is made once; after that the same image is reused.
 */
public class Images {

    private static final Map<String, BufferedImage> originals = new HashMap<>();
    private static final Map<String, ImageIcon>     scaled    = new HashMap<>();

    // ── Named images ─────────────────────────────────────────────────────────

    /** A card's art at full size, or the placeholder art if the card has none. Null if neither exists. */
    static BufferedImage cardArt(String cardId) {
        BufferedImage img = load(GamePaths.CARD_IMAGES_DIR + cardId + ".png");
        return img != null ? img : load(GamePaths.CARD_IMAGES_DIR + "placeholder.png");
    }

    /** A type's symbol (e.g. BugSymbol.png) at the given size, or a transparent square if there is none. */
    static ImageIcon typeSymbol(String type, int width, int height) {
        if (type != null && !type.isEmpty()) {
            String cap = Character.toUpperCase(type.charAt(0)) + type.substring(1).toLowerCase();
            ImageIcon icon = scaled(GamePaths.TYPE_SYMBOLS_DIR + cap + "Symbol.png", width, height);
            if (icon == null) icon = scaled(GamePaths.TYPE_SYMBOLS_DIR + type.toLowerCase() + "Symbol.png", width, height);
            if (icon != null) return icon;
        }
        return new ImageIcon(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB));
    }

    /** An image from the "Buttons & Menus" folder scaled to a width, keeping its shape. Null if missing. */
    static ImageIcon menuImage(String fileName, int width) {
        String path = GamePaths.MENU_IMAGES_DIR + fileName;
        BufferedImage img = load(path);
        if (img == null) return null;
        int height = (int) ((double) img.getHeight() / img.getWidth() * width);
        return scaled(path, width, height);
    }

    static BufferedImage cardBackground() { return load(GamePaths.CARD_BACKGROUND); }
    static BufferedImage cardForeground() { return load(GamePaths.CARD_FOREGROUND); }

    // ── General loading ──────────────────────────────────────────────────────

    /** Reads an image file once and reuses it. Returns null if the file is missing or unreadable. */
    static synchronized BufferedImage load(String path) {
        if (originals.containsKey(path)) return originals.get(path);
        BufferedImage img = null;
        File f = new File(path);
        if (f.exists()) {
            try { img = ImageIO.read(f); } catch (Exception ignored) {}
        }
        originals.put(path, img);
        return img;
    }

    /** An image scaled to an exact size, made once per size. Returns null if the file is missing. */
    static synchronized ImageIcon scaled(String path, int width, int height) {
        String key = path + "@" + width + "x" + height;
        if (scaled.containsKey(key)) return scaled.get(key);
        BufferedImage img = load(path);
        ImageIcon icon = img == null ? null
                : new ImageIcon(img.getScaledInstance(width, height, Image.SCALE_SMOOTH));
        scaled.put(key, icon);
        return icon;
    }
}
