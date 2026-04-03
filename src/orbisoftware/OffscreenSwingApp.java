package orbisoftware;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class OffscreenSwingApp {
    private final int width;
    private final int height;
    private final JComponent content;
    private final Runnable onFrameUpdated;
    private final AtomicReference<BufferedImage> currentFrame = new AtomicReference<>();
    private final AtomicBoolean captureQueued = new AtomicBoolean(false);

    public OffscreenSwingApp(JComponent content, int width, int height) {
        this(content, width, height, null);
    }

    public OffscreenSwingApp(JComponent content, int width, int height, Runnable onFrameUpdated) {
        this.content = content;
        this.width = width;
        this.height = height;
        this.onFrameUpdated = onFrameUpdated;
    }

    public void start() {
        SwingUtilities.invokeLater(() -> {
            prepareComponentTreeIfNeeded();
            UiDirtyRepaintManager.installIfNeeded().trackRoot(content, this::scheduleRenderFrame);
            renderFrame();
        });
    }

    public BufferedImage getCurrentFrame() {
        BufferedImage image = currentFrame.get();
        if (image == null) {
            BufferedImage blank = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = blank.createGraphics();
            try {
                g.setColor(new Color(235, 235, 235));
                g.fillRect(0, 0, width, height);
                g.setColor(Color.DARK_GRAY);
                g.drawString("Initializing...", 16, 24);
            } finally {
                g.dispose();
            }
            return blank;
        }
        return image;
    }

    private void prepareComponentTreeIfNeeded() {
        if (content.getWidth() <= 0 || content.getHeight() <= 0) {
            content.setSize(width, height);
        }
        if (content.getPreferredSize() == null || content.getPreferredSize().width <= 0 || content.getPreferredSize().height <= 0) {
            content.setPreferredSize(new Dimension(width, height));
        }
        layoutRecursively(content);
    }

    private void layoutRecursively(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container c) {
                c.doLayout();
                layoutRecursively(c);
            }
        }
    }

    private void scheduleRenderFrame() {
        if (!captureQueued.compareAndSet(false, true)) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            captureQueued.set(false);
            renderFrame();
        });
    }

    private void renderFrame() {
        int renderWidth = content.getWidth() > 0 ? content.getWidth() : width;
        int renderHeight = content.getHeight() > 0 ? content.getHeight() : height;

        BufferedImage rendered = new BufferedImage(renderWidth, renderHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D rg = rendered.createGraphics();
        try {
            rg.setColor(new Color(245, 245, 245));
            rg.fillRect(0, 0, renderWidth, renderHeight);
			try {
				content.printAll(rg);
			} catch (Exception e) { }
        } finally {
            rg.dispose();
        }

        BufferedImage target = rendered;
        if (renderWidth != width || renderHeight != height) {
            target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = target.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setColor(new Color(245, 245, 245));
                g.fillRect(0, 0, width, height);
                g.drawImage(rendered, 0, 0, width, height, null);
            } finally {
                g.dispose();
            }
        }

        currentFrame.set(target);
        if (onFrameUpdated != null) {
            onFrameUpdated.run();
        }
    }
}
