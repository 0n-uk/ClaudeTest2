import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.*;

public class TypeSymbolLoader {

    private static final String SYM_DIR = System.getProperty("user.dir") + File.separator
            + "images" + File.separator + "TypeSymbols" + File.separator;
    private static final Map<String, ImageIcon> cache = new HashMap<>();

    public static ImageIcon get(String type, int width, int height) {
        if (type == null || type.isEmpty()) return blankIcon(width, height);
        String key = type.toLowerCase() + "_" + width + "x" + height;
        if (cache.containsKey(key)) return cache.get(key);

        // Try capitalised form first (e.g. "BugSymbol.png"), then lowercase
        String cap = Character.toUpperCase(type.charAt(0)) + type.substring(1).toLowerCase();
        File f = new File(SYM_DIR + cap + "Symbol.png");
        if (!f.exists()) f = new File(SYM_DIR + type.toLowerCase() + "Symbol.png");

        ImageIcon icon;
        try {
            BufferedImage img = ImageIO.read(f);
            if (img == null) throw new Exception("unreadable");
            Image scaled = img.getScaledInstance(width, height, Image.SCALE_SMOOTH);
            icon = new ImageIcon(scaled);
        } catch (Exception e) {
            icon = blankIcon(width, height);
        }

        cache.put(key, icon);
        return icon;
    }

    private static ImageIcon blankIcon(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        return new ImageIcon(img);
    }
}
