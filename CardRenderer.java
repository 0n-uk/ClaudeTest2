import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class CardRenderer {

    static final int CARD_W   = 170;
    static final int CARD_H   = 170;
    static final int BOX      = 32;
    // Sprite: 160x160 bottom-center (x=5, y=10 → bottom of card)
    static final int SPRITE_X = 5;
    static final int SPRITE_Y = 10;
    static final int SPRITE_W = 160;
    static final int SPRITE_H = 160;
    // Name sits in the top 20px (overlaps sprite by 10px)
    static final int NAME_H   = 20;
    // Info button sits just above the bottom stat boxes
    static final int INFO_W   = 36;
    static final int INFO_H   = 18;
    static final int INFO_X   = (CARD_W - INFO_W) / 2;
    static final int INFO_Y   = CARD_H - BOX - INFO_H - 2;

    private static Font handFont;
    private static BufferedImage cardBg;
    private static BufferedImage cardFg;

    static {
        // Try PatrickHand.ttf in project root first, fall back to FreeSerif Italic
        Font loaded = null;
        String[] fontPaths = {
            "PatrickHand.ttf",
            "/usr/share/fonts/truetype/freefont/FreeSerif.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf"
        };
        for (String path : fontPaths) {
            try {
                File f = new File(path);
                if (f.exists()) {
                    loaded = Font.createFont(Font.TRUETYPE_FONT, f);
                    GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(loaded);
                    break;
                }
            } catch (Exception ignored) {}
        }
        handFont = (loaded != null) ? loaded : new Font("Serif", Font.PLAIN, 12);

        try {
            cardBg = ImageIO.read(new File("images/cardbackground.png"));
        } catch (Exception ignored) {}
        try {
            cardFg = ImageIO.read(new File("images/cardforeground.png"));
        } catch (Exception ignored) {}
    }

    // ── Public entry point ────────────────────────────────────────────────────

    static JPanel buildCard(Card card) {
        BufferedImage sprite = CardImageLoader.getRaw(card.getId());
        String abilityText = card.getAbility().isEmpty() ? "No ability." : card.getAbility();

        JPanel panel = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                // 1. Background
                if (cardBg != null) {
                    g2.drawImage(cardBg, 0, 0, CARD_W, CARD_H, null);
                } else {
                    drawFallbackBg(g2);
                }

                // 2. Sprite (nearest-neighbor for pixel art, aspect-fit within 160×160)
                if (sprite != null) {
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    int sw = sprite.getWidth(), sh = sprite.getHeight();
                    double scale = Math.min((double) SPRITE_W / sw, (double) SPRITE_H / sh);
                    int tw = (int)(sw * scale), th = (int)(sh * scale);
                    int sx = SPRITE_X + (SPRITE_W - tw) / 2;
                    int sy = SPRITE_Y + (SPRITE_H - th);   // bottom-align within sprite area
                    g2.drawImage(sprite, sx, sy, tw, th, null);
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                }

                // 3. Foreground overlay (sits above sprite, below stats)
                if (cardFg != null) {
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2.drawImage(cardFg, 0, 0, CARD_W, CARD_H, null);
                }

                // 4. Type symbol in top-left box (32×32)
                ImageIcon sym = TypeSymbolLoader.get(card.getType(), BOX, BOX);
                if (sym != null) g2.drawImage(sym.getImage(), 0, 0, BOX, BOX, null);

                // 5. Name — top center, fitted to width between corner boxes
                Color nameColor = typeColor(card.getType());
                Font nameFont = fitFont(g2, card.getName(), CARD_W - BOX * 2 - 6, Font.BOLD, 13f, 6f);
                g2.setFont(nameFont);
                g2.setColor(nameColor);
                FontMetrics fm = g2.getFontMetrics(nameFont);
                int nameX = BOX + (CARD_W - BOX * 2 - fm.stringWidth(card.getName())) / 2;
                int nameY = (NAME_H + fm.getAscent() - fm.getDescent()) / 2;
                // Subtle dark drop-shadow for legibility on the sprite
                g2.setColor(new Color(0, 0, 0, 120));
                g2.drawString(card.getName(), nameX + 1, nameY + 1);
                g2.setColor(nameColor);
                g2.drawString(card.getName(), nameX, nameY);

                // 6. Cost — top-right box (blue)
                drawStat(g2, String.valueOf(card.getCost()),
                         CARD_W - BOX, 0, new Color(80, 140, 220));

                // 7. ATK — bottom-left box (red)
                drawStat(g2, String.valueOf(card.getAttack()),
                         0, CARD_H - BOX, new Color(210, 60, 60));

                // 8. HP — bottom-right box (green)
                drawStat(g2, String.valueOf(card.getHp()),
                         CARD_W - BOX, CARD_H - BOX, new Color(60, 185, 80));

                g2.dispose();
            }
        };
        panel.setPreferredSize(new Dimension(CARD_W, CARD_H));
        panel.setOpaque(false);

        // Info button — absolute-positioned, bottom center, over sprite
        JButton info = new JButton("[i]");
        info.setFont(handFont.deriveFont(Font.BOLD, 11f));
        info.setForeground(new Color(230, 210, 150));
        info.setBackground(new Color(20, 20, 30, 180));
        info.setOpaque(true);
        info.setContentAreaFilled(true);
        info.setBorder(BorderFactory.createLineBorder(new Color(180, 160, 100), 1, true));
        info.setFocusPainted(false);
        info.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        info.setBounds(INFO_X, INFO_Y, INFO_W, INFO_H);
        info.addActionListener(e -> showAbilityPopup(info, card.getName(), abilityText));
        panel.add(info);

        return panel;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void drawStat(Graphics2D g2, String text, int bx, int by, Color color) {
        float size = text.length() > 2 ? 10f : 13f;
        Font f = handFont.deriveFont(Font.BOLD, size);
        g2.setFont(f);
        FontMetrics fm = g2.getFontMetrics(f);
        int tx = bx + (BOX - fm.stringWidth(text)) / 2;
        int ty = by + (BOX + fm.getAscent() - fm.getDescent()) / 2;
        // Shadow
        g2.setColor(new Color(0, 0, 0, 100));
        g2.drawString(text, tx + 1, ty + 1);
        g2.setColor(color);
        g2.drawString(text, tx, ty);
    }

    // Reduces font size until text fits within maxWidth
    private static Font fitFont(Graphics2D g2, String text, int maxWidth,
                                 int style, float startSize, float minSize) {
        float size = startSize;
        Font f = handFont.deriveFont(style, size);
        while (size > minSize) {
            FontMetrics fm = g2.getFontMetrics(f);
            if (fm.stringWidth(text) <= maxWidth) break;
            size -= 0.5f;
            f = handFont.deriveFont(style, size);
        }
        return f;
    }

    private static void drawFallbackBg(Graphics2D g2) {
        g2.setColor(new Color(245, 242, 235));
        g2.fillRect(0, 0, CARD_W, CARD_H);
        g2.setColor(new Color(60, 50, 40));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRect(0, 0, CARD_W - 1, CARD_H - 1);
        // Corner box outlines
        int b = BOX;
        g2.drawRect(0, 0, b, b);
        g2.drawRect(CARD_W - b - 1, 0, b, b);
        g2.drawRect(0, CARD_H - b - 1, b, b);
        g2.drawRect(CARD_W - b - 1, CARD_H - b - 1, b, b);
    }

    static void showAbilityPopup(Component parent, String name, String ability) {
        JLabel label = new JLabel("<html><b>" + name + "</b><br><br>" + ability + "</html>");
        label.setFont(handFont.deriveFont(13f));
        JOptionPane.showMessageDialog(parent, label, "Ability", JOptionPane.PLAIN_MESSAGE);
    }

    // ── Type colour ───────────────────────────────────────────────────────────

    static Color typeColor(String type) {
        switch (type == null ? "" : type.toLowerCase()) {
            case "beast":    return new Color(190, 145, 55);
            case "spirit":   return new Color(170, 150, 230);
            case "bot":      return new Color(110, 165, 190);
            case "bug":      return new Color(130, 205, 55);
            case "demon":    return new Color(210, 45,  45);
            case "dragon":   return new Color(225, 85,  55);
            case "knight":   return new Color(210, 175, 75);
            case "pod":      return new Color(90,  185, 80);
            case "cryptid":  return new Color(190, 150, 255);
            case "item":     return new Color(195, 165, 100);
            case "champion": return new Color(225, 185, 60);
            default:         return new Color(160, 145, 210);
        }
    }
}
