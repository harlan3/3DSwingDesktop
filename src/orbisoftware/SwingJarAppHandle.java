package orbisoftware;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URLClassLoader;

public final class SwingJarAppHandle {
    private final String jarFile;
    private final String displayName;
    private final JPanel livePanel;
    private final OffscreenSwingApp offscreenApp;
    private final Process process;
    private final URLClassLoader classLoader;

    public SwingJarAppHandle(String jarFile, String displayName, JPanel livePanel,
                             OffscreenSwingApp offscreenApp, Process process, URLClassLoader classLoader) {
        this.jarFile = jarFile;
        this.displayName = displayName;
        this.livePanel = livePanel;
        this.offscreenApp = offscreenApp;
        this.process = process;
        this.classLoader = classLoader;
    }

    public String jarFile() { return jarFile; }
    public String displayName() { return displayName; }
    public JPanel livePanel() { return livePanel; }
    public Process process() { return process; }
    public URLClassLoader classLoader() { return classLoader; }

    public void startOffscreen() {
        if (offscreenApp != null) {
            offscreenApp.start();
        }
    }

    public BufferedImage getCurrentFrame() {
        return offscreenApp == null ? null : offscreenApp.getCurrentFrame();
    }

    public void resize(int width, int height) {
        if (livePanel != null) {
            livePanel.setPreferredSize(new Dimension(width, height));
            livePanel.setSize(width, height);
            livePanel.doLayout();
        }
    }

    public void destroyProcess() {
        if (process != null && process.isAlive()) {
            process.destroy();
        }
        if (classLoader != null) {
            try {
                classLoader.close();
            } catch (IOException ignored) {
            }
        }
    }
}
