import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class CardRenderer {

    // Reference dimensions at full 170×170 size
    static final int CARD_W   = 170;
    static final int CARD_H   = 170;
    static final int BOX      = 32;    // corner stat box (scales with card)
    static final int SPRITE_X = 5;
    static final int SPRITE_Y = 10;
    static final int NAME_H   = 20;
    static final int INFO_W   = 36;
    static final int INFO_H   = 18;

    static Font handFont;
    private static BufferedImage cardBg;
    private static BufferedImage cardFg;

    static {
        Font loaded = null;
        for (String path : new String[]{
                "PatrickHand.ttf",
                "/usr/share/fonts/truetype/freefont/FreeSerif.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf"}) {
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
        try { cardBg = ImageIO.read(new File("images/cardbackground.png")); } catch (Exception ignored) {}
        try { cardFg = ImageIO.read(new File("images/cardforeground.png")); } catch (Exception ignored) {}
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Full-size 170×170 card for the card viewer and pack reveal. */
    static JPanel buildCard(Card card) {
        return buildCardCore(card, CARD_W, card.getHp(), card.getAttack(), 0, false, "");
    }

    /** Scaled card for the deck builder. */
    static JPanel buildCard(Card card, int size) {
        return buildCardCore(card, size, card.getHp(), card.getAttack(), 0, false, "");
    }

    /**
     * Battle-slot card: shows current HP (coloured by health ratio),
     * effective ATK (coloured when buffed/debuffed), and stage label for champions.
     */
    static JPanel buildBattleCard(Card card, int size, int currentHp,
                                   int displayAtk, int atkBonus,
                                   boolean isChamp, String stageStr) {
        return buildCardCore(card, size, currentHp, displayAtk, atkBonus, isChamp, stageStr);
    }

    // ── Core renderer ─────────────────────────────────────────────────────────

    private static JPanel buildCardCore(Card card, int size,
                                         int currentHp, int displayAtk, int atkBonus,
                                         boolean isChamp, String stageStr) {
        float sc     = (float) size / CARD_W;
        int   box    = Math.round(BOX      * sc);
        int   spX    = Math.round(SPRITE_X * sc);
        int   spY    = Math.round(SPRITE_Y * sc);
        int   spW    = size - spX * 2;
        int   spH    = size - spY;
        int   nameH  = Math.round(NAME_H * sc);
        int   infoW  = Math.round(INFO_W * sc);
        int   infoH  = Math.round(INFO_H * sc);
        int   infoX  = (size - infoW) / 2;
        int   infoY  = size - box - infoH - Math.round(2 * sc);

        BufferedImage sprite = CardImageLoader.getRaw(card.getId());
        String abilityText = card.getAbility().isEmpty() ? "No ability." : card.getAbility();

        // ATK color: green when buffed, orange when normal, red when debuffed
        Color atkColor = atkBonus > 0 ? new Color(100, 220, 100)
                       : atkBonus < 0 ? new Color(220, 60,  60)
                                      : new Color(210, 60,  60);
        // HP color: green when healthy, yellow when hurt, red when critical
        double hpRatio = card.getHp() > 0 ? (double) currentHp / card.getHp() : 1.0;
        Color hpColor = hpRatio > 0.5 ? new Color(60, 185, 80)
                      : hpRatio > 0.25 ? new Color(220, 185, 40)
                                       : new Color(210, 60,  60);

        // Name: champion colour override, otherwise type colour
        Color nameColor = isChamp ? new Color(225, 185, 60) : typeColor(card.getType());

        JPanel panel = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                // 1. Background
                if (cardBg != null) g2.drawImage(cardBg, 0, 0, size, size, null);
                else                drawFallbackBg(g2, size, box);

                // 2. Sprite — nearest-neighbor, aspect-fit, bottom-aligned in sprite area
                if (sprite != null) {
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    int sw = sprite.getWidth(), sh = sprite.getHeight();
                    double imgScale = Math.min((double) spW / sw, (double) spH / sh);
                    int tw = (int)(sw * imgScale), th = (int)(sh * imgScale);
                    g2.drawImage(sprite, spX + (spW - tw) / 2, spY + (spH - th), tw, th, null);
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                }

                // 3. Foreground
                if (cardFg != null) g2.drawImage(cardFg, 0, 0, size, size, null);

                // 4. Type symbol — top-left box
                ImageIcon sym = TypeSymbolLoader.get(card.getType(), box, box);
                if (sym != null) g2.drawImage(sym.getImage(), 0, 0, box, box, null);

                // 5. Name — top center, fitted between corner boxes
                float startPt = Math.max(6f, 13f * sc);
                Font nameFont = fitFont(g2, card.getName(), size - box * 2 - Math.round(6 * sc),
                                        Font.BOLD, startPt, Math.max(4f, 6f * sc));
                g2.setFont(nameFont);
                FontMetrics fm = g2.getFontMetrics(nameFont);
                int nx = box + (size - box * 2 - fm.stringWidth(card.getName())) / 2;
                int ny = (nameH + fm.getAscent() - fm.getDescent()) / 2;
                g2.setColor(new Color(0, 0, 0, 120));
                g2.drawString(card.getName(), nx + 1, ny + 1);
                g2.setColor(nameColor);
                g2.drawString(card.getName(), nx, ny);

                // 6. Stage label for champions (small, in name area)
                if (!stageStr.isEmpty()) {
                    float stagePt = Math.max(4f, 8f * sc);
                    Font stageFont = handFont.deriveFont(Font.ITALIC, stagePt);
                    g2.setFont(stageFont);
                    g2.setColor(new Color(225, 185, 60));
                    g2.drawString(stageStr, size - box - g2.getFontMetrics(stageFont).stringWidth(stageStr) - Math.round(2*sc), Math.round(9*sc));
                }

                // 7. Cost — top-right box (blue)
                drawStat(g2, String.valueOf(card.getCost()), size - box, 0, box, new Color(80, 140, 220));

                // 8. ATK — bottom-left box
                String atkStr = atkBonus != 0 ? String.valueOf(displayAtk) : String.valueOf(card.getAttack());
                drawStat(g2, atkStr, 0, size - box, box, atkColor);

                // 9. HP — bottom-right box
                drawStat(g2, String.valueOf(currentHp), size - box, size - box, box, hpColor);

                g2.dispose();
            }
        };
        panel.setPreferredSize(new Dimension(size, size));
        panel.setOpaque(false);

        // Info button — absolute positioned
        JButton info = new JButton("[i]");
        float infoPt = Math.max(7f, 11f * sc);
        info.setFont(handFont.deriveFont(Font.BOLD, infoPt));
        info.setForeground(new Color(230, 210, 150));
        info.setBackground(new Color(20, 20, 30, 180));
        info.setOpaque(true);
        info.setContentAreaFilled(true);
        info.setBorder(BorderFactory.createLineBorder(new Color(180, 160, 100), 1, true));
        info.setFocusPainted(false);
        info.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        info.setBounds(infoX, infoY, infoW, infoH);
        info.addActionListener(e -> showAbilityPopup(info, card.getName(), abilityText));
        panel.add(info);

        return panel;
    }

    // ── Drawing helpers ───────────────────────────────────────────────────────

    private static void drawStat(Graphics2D g2, String text, int bx, int by, int box, Color color) {
        float pt = text.length() > 2 ? Math.max(4f, box * 0.30f) : Math.max(5f, box * 0.40f);
        Font f = handFont.deriveFont(Font.BOLD, pt);
        g2.setFont(f);
        FontMetrics fm = g2.getFontMetrics(f);
        int tx = bx + (box - fm.stringWidth(text)) / 2;
        int ty = by + (box + fm.getAscent() - fm.getDescent()) / 2;
        g2.setColor(new Color(0, 0, 0, 100));
        g2.drawString(text, tx + 1, ty + 1);
        g2.setColor(color);
        g2.drawString(text, tx, ty);
    }

    private static Font fitFont(Graphics2D g2, String text, int maxWidth,
                                 int style, float startSize, float minSize) {
        float size = startSize;
        Font f = handFont.deriveFont(style, size);
        while (size > minSize) {
            if (g2.getFontMetrics(f).stringWidth(text) <= maxWidth) break;
            size -= 0.5f;
            f = handFont.deriveFont(style, size);
        }
        return f;
    }

    private static void drawFallbackBg(Graphics2D g2, int size, int box) {
        g2.setColor(new Color(245, 242, 235));
        g2.fillRect(0, 0, size, size);
        g2.setColor(new Color(60, 50, 40));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRect(0, 0, size - 1, size - 1);
        g2.drawRect(0, 0, box, box);
        g2.drawRect(size - box - 1, 0, box, box);
        g2.drawRect(0, size - box - 1, box, box);
        g2.drawRect(size - box - 1, size - box - 1, box, box);
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    static void showAbilityPopup(Component parent, String name, String ability) {
        JLabel label = new JLabel("<html><b>" + name + "</b><br><br>" + ability + "</html>");
        label.setFont(handFont.deriveFont(13f));
        JOptionPane.showMessageDialog(parent, label, "Ability", JOptionPane.PLAIN_MESSAGE);
    }

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
