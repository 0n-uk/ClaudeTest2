import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.swing.*;

public class CardRenderer {

    // Reference dimensions at full 170×170 size
    static final int CARD_W   = 170;
    static final int CARD_H   = 170;
    static final int BOX      = 32;    // corner stat box (scales with card)
    static final int SPRITE_X = 5;
    static final int SPRITE_Y = 10;
    static final int NAME_H   = 20;
    static final int INFO_W   = 44;
    static final int INFO_H   = 24;

    /** The pixel font every card's text is drawn in (falls back to Monospaced if the file is missing). */
    static Font cardFont;
    private static BufferedImage cardBg;
    private static BufferedImage cardFg;

    static {
        Font loaded = null;
        try {
            loaded = Font.createFont(Font.TRUETYPE_FONT, new File(GamePaths.CARD_FONT));
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(loaded);
        } catch (Exception e) {
            System.err.println("Could not load card font " + GamePaths.CARD_FONT + ": " + e.getMessage());
        }
        cardFont = (loaded != null) ? loaded : new Font(Font.MONOSPACED, Font.PLAIN, 12);
        cardBg = Images.cardBackground();
        cardFg = Images.cardForeground();
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
     * Battle card: shows current HP (coloured by health ratio),
     * effective ATK (coloured when buffed/debuffed), and stage label for champions.
     * The card has no fixed size: it is drawn as the biggest square that fits its
     * panel, centred, so it never stretches.
     */
    static JPanel buildBattleCard(Card card, int currentHp,
                                   int displayAtk, int atkBonus,
                                   boolean isChamp, String stageStr) {
        return buildCardCore(card, 0, currentHp, displayAtk, atkBonus, isChamp, stageStr);
    }

    // ── Core renderer ─────────────────────────────────────────────────────────

    /** Builds a card drawn at {@code fixedSize} pixels, or fitted to its panel when fixedSize is 0. */
    private static JPanel buildCardCore(Card card, int fixedSize,
                                         int currentHp, int displayAtk, int atkBonus,
                                         boolean isChamp, String stageStr) {
        BufferedImage sprite = Images.cardArt(card.getId());
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

        // Name: champion colour override, otherwise type colour, made much darker so it reads on the light card
        Color nameColor = darker(isChamp ? new Color(225, 185, 60) : typeColor(card.getType()));

        JButton info = new JButton("i");
        info.setForeground(new Color(230, 210, 150));
        info.setBackground(new Color(45, 38, 30));
        info.setOpaque(true);
        info.setContentAreaFilled(true);
        info.setBorder(BorderFactory.createLineBorder(new Color(180, 160, 100), 1, true));
        info.setFocusPainted(false);
        info.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        info.addActionListener(e -> showAbilityPopup(info, card.getName(), abilityText));

        JPanel panel = new JPanel(null) {
            /** The side of the square the card is drawn in. */
            private int side() {
                return fixedSize > 0 ? fixedSize : Math.min(getWidth(), getHeight());
            }
            /** Where the card's square starts: the corner for a fixed size, centred when fitted. */
            private int originX() { return fixedSize > 0 ? 0 : (getWidth()  - side()) / 2; }
            private int originY() { return fixedSize > 0 ? 0 : (getHeight() - side()) / 2; }

            @Override
            public void doLayout() {
                int   size  = side();
                float sc    = (float) size / CARD_W;
                int   infoW = Math.round(INFO_W * sc);
                int   infoH = Math.round(INFO_H * sc);
                info.setFont(cardFont.deriveFont(Font.PLAIN, pixelSize(Math.max(9f, 16f * sc))));
                info.setBounds(originX() + (size - infoW) / 2,
                               originY() + size - infoH - Math.round(2 * sc), infoW, infoH);
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                int size = side();
                if (size <= 0) return;
                float sc  = (float) size / CARD_W;
                int   box = Math.round(BOX      * sc);
                int   spX = Math.round(SPRITE_X * sc);
                int   spY = Math.round(SPRITE_Y * sc);
                int   spW = size - spX * 2;
                int   spH = size - spY;
                int   nameH = Math.round(NAME_H * sc);

                Graphics2D g2 = (Graphics2D) g.create();
                g2.translate(originX(), originY());
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
                ImageIcon sym = Images.typeSymbol(card.getType(), box, box);
                if (sym != null) g2.drawImage(sym.getImage(), 0, 0, box, box, null);

                // 5. Name — top center between the corner boxes; a long name wraps onto a second line
                drawName(g2, card.getName(), box + Math.round(3 * sc), size - box * 2 - Math.round(6 * sc),
                         nameH, box, Math.max(9f, 15f * sc), nameColor);

                // 6. Stage label for champions (small, in name area)
                if (!stageStr.isEmpty()) {
                    float stagePt = Math.max(7f, 9f * sc);
                    Font stageFont = cardFont.deriveFont(Font.PLAIN, pixelSize(stagePt));
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
        if (fixedSize > 0) panel.setPreferredSize(new Dimension(fixedSize, fixedSize));
        panel.setOpaque(false);
        panel.add(info);
        return panel;
    }

    // ── Drawing helpers ───────────────────────────────────────────────────────

    private static void drawStat(Graphics2D g2, String text, int bx, int by, int box, Color color) {
        float pt = text.length() > 2 ? Math.max(7f, box * 0.40f) : Math.max(8f, box * 0.55f);
        Font f = cardFont.deriveFont(Font.PLAIN, pixelSize(pt));
        g2.setFont(f);
        FontMetrics fm = g2.getFontMetrics(f);
        int tx = bx + (box - fm.stringWidth(text)) / 2;
        int ty = by + (box + fm.getAscent() - fm.getDescent()) / 2;
        // A dark outline so the coloured number stands out on the light card (just a shadow when small)
        g2.setColor(new Color(30, 25, 20));
        int r = box >= 24 ? 1 : 0;
        for (int dx = -r; dx <= 1; dx++)
            for (int dy = -r; dy <= 1; dy++)
                if (dx != 0 || dy != 0) g2.drawString(text, tx + dx, ty + dy);
        g2.setColor(color);
        g2.drawString(text, tx, ty);
    }

    /**
     * Draws a card's name in a box {@code w} wide starting at {@code x}. It shrinks to fit on one line,
     * down to the smallest readable size; if it still doesn't fit it wraps onto two lines (using up to
     * {@code maxH} pixels of height), and only if that fails too is it cut short.
     */
    private static void drawName(Graphics2D g2, String name, int x, int w, int lineH, int maxH,
                                 float startPt, Color color) {
        final float minPt = 8f;
        g2.setColor(color);
        Font one = fitFont(g2, name, w, startPt, minPt);
        FontMetrics fm = g2.getFontMetrics(one);
        int space = name.lastIndexOf(' ');
        if (fm.stringWidth(name) <= w || space < 0) {
            String t = shorten(name, fm, w);
            g2.setFont(one);
            g2.drawString(t, x + (w - fm.stringWidth(t)) / 2, (lineH + fm.getAscent() - fm.getDescent()) / 2);
            return;
        }

        // Break at the space that makes the longer line as short as possible
        String[] lines = null;
        int best = Integer.MAX_VALUE;
        for (int i = name.indexOf(' '); i >= 0; i = name.indexOf(' ', i + 1)) {
            String a = name.substring(0, i).trim(), b = name.substring(i + 1).trim();
            int longer = Math.max(fm.stringWidth(a), fm.stringWidth(b));
            if (longer < best) { best = longer; lines = new String[]{ a, b }; }
        }

        // Biggest size where both lines fit across and together fit down
        Font f = cardFont.deriveFont(Font.PLAIN, minPt);
        for (float pt = pixelSize(startPt); pt >= minPt; pt--) {
            Font t = cardFont.deriveFont(Font.PLAIN, pt);
            FontMetrics tm = g2.getFontMetrics(t);
            int textH = tm.getAscent() - tm.getDescent();
            if (Math.max(tm.stringWidth(lines[0]), tm.stringWidth(lines[1])) <= w
                    && textH * 2 + Math.max(1, textH / 4) <= maxH - 2) {
                f = t;
                break;
            }
        }
        g2.setFont(f);
        fm = g2.getFontMetrics(f);
        int textH = fm.getAscent() - fm.getDescent();
        int gap   = Math.max(1, textH / 4);
        int top   = Math.max(1, (maxH - (textH * 2 + gap)) / 2);
        for (int i = 0; i < 2; i++) {
            String t = shorten(lines[i], fm, w);
            int tw = fm.stringWidth(t), tx = x + (w - tw) / 2, ty = top + textH + i * (textH + gap);
            if (i == 1) {
                // The second line sits over the art, so give it a light backing like the name strip
                g2.setColor(new Color(250, 243, 205, 230));
                g2.fillRoundRect(tx - 2, ty - textH - gap / 2, tw + 4, textH + gap, 4, 4);
                g2.setColor(color);
            }
            g2.drawString(t, tx, ty);
        }
    }

    /** Cuts text short with ".." so it fits in maxWidth. */
    private static String shorten(String text, FontMetrics fm, int maxWidth) {
        if (fm.stringWidth(text) <= maxWidth) return text;
        String t = text;
        while (t.length() > 1 && fm.stringWidth(t.trim() + "..") > maxWidth) t = t.substring(0, t.length() - 1);
        return t.trim() + "..";
    }

    private static Color darker(Color c) {
        return new Color(c.getRed() * 2 / 5, c.getGreen() * 2 / 5, c.getBlue() * 2 / 5);
    }

    private static Font fitFont(Graphics2D g2, String text, int maxWidth, float startSize, float minSize) {
        float size = pixelSize(startSize);
        Font f = cardFont.deriveFont(Font.PLAIN, size);
        while (size > minSize) {
            if (g2.getFontMetrics(f).stringWidth(text) <= maxWidth) break;
            size -= 1f;
            f = cardFont.deriveFont(Font.PLAIN, size);
        }
        return f;
    }

    /** Pixel fonts only look sharp at whole-number sizes. */
    private static float pixelSize(float pt) {
        return Math.max(1, Math.round(pt));
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
        label.setFont(cardFont.deriveFont(Font.PLAIN, 16f));
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
