package orbisoftware;

import java.awt.*;
import java.awt.image.BufferedImage;

public final class PlaceholderImages {
    private PlaceholderImages() {}

    public static BufferedImage generic(int w, int h, String label) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(50, 80, 150), w, h, new Color(20, 30, 60)));
        g.fillRect(0, 0, w, h);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        g.drawString(label, 24, 48);
        g.dispose();
        return img;
    }

    public static BufferedImage win32Placeholder(int w, int h, String label) {
        BufferedImage img = generic(w, h, label);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(255, 255, 255, 210));
        g.fillRoundRect(40, 80, w - 80, h - 130, 24, 24);
        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 32));
        if (label.contains("Calculator")) {
            g.drawString("123 + 456 =", 60, 140);
            g.drawString("579", 60, 190);
        } else if (label.contains("Command")) {
            g.drawString("C:\\> dir", 60, 140);
            g.drawString("Volume in drive C...", 60, 190);
        }
        g.dispose();
        return img;
    }
}
