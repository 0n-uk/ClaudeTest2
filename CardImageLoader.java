import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.*;

public class CardImageLoader {

private static final String IMG_DIR = System.getProperty("user.dir") + File.separator + "images" + File.separator + "cards" + File.separator;    private static final Map<String, ImageIcon> cache = new HashMap<>();
    private static final Map<String, BufferedImage> rawCache = new HashMap<>();

    public static ImageIcon get(String cardId, int width, int height) {
        String key = cardId + "_" + width + "x" + height;
        if (cache.containsKey(key)) return cache.get(key);

        File f = new File(IMG_DIR + cardId + ".png");
        if (!f.exists()) f = new File(IMG_DIR + "placeholder.png");

        ImageIcon icon;
        try {
            BufferedImage img = ImageIO.read(f);
            if (img == null) throw new Exception("unreadable image");
            Image scaled = img.getScaledInstance(width, height, Image.SCALE_SMOOTH);
            icon = new ImageIcon(scaled);
        } catch (Exception e) {
            icon = blankIcon(width, height);
        }

        cache.put(key, icon);
        return icon;
    }

    /** Returns the raw (unscaled) BufferedImage for use in paint-scaled panels. */
    public static BufferedImage getRaw(String cardId) {
        if (rawCache.containsKey(cardId)) return rawCache.get(cardId);
        File f = new File(IMG_DIR + cardId + ".png");
        if (!f.exists()) f = new File(IMG_DIR + "placeholder.png");
        try {
            BufferedImage img = ImageIO.read(f);
            rawCache.put(cardId, img);
            return img;
        } catch (Exception e) {
            rawCache.put(cardId, null);
            return null;
        }
    }

    private static ImageIcon blankIcon(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(50, 50, 70));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(80, 80, 100));
        g.drawRect(0, 0, w - 1, h - 1);
        g.dispose();
        return new ImageIcon(img);
    }
}
