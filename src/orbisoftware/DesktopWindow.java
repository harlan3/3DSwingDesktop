package orbisoftware;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DesktopWindow {
    final DesktopConfig.DesktopDefinition desktopDefinition;
    final DesktopConfig.JavaWindowAppDefinition definition;
    final Rectangle bounds;
    final Rectangle initialBounds;
    final List<SwingJarAppHandle> swingApps = new ArrayList<>();
    SwingSwtBrowserPanel swtBrowserPanel;
    Win32Launcher win32Launcher;
    private final DesktopSurface surface;

    public DesktopWindow(DesktopConfig.DesktopDefinition desktopDefinition,
                         DesktopConfig.JavaWindowAppDefinition definition,
                         DesktopSurface surface) {
        this.desktopDefinition = desktopDefinition;
        this.definition = definition;
        this.bounds = new Rectangle(definition.x(), definition.y(), definition.width(), definition.height());
        this.initialBounds = new Rectangle(this.bounds);
        this.surface = surface;
    }

    public void initialize() {
        String desktopType = desktopDefinition.desktopWindowType();
        String classNameString = primaryClassSimpleName();

        if (DesktopConfig.TYPE_SWING.equalsIgnoreCase(desktopType) && definition.hasJarFiles()) {
            for (String jarFile : definition.jarFiles()) {
                Path jarPath = surface.resolveArtifactPath(jarFile);
                Process launched = JarSwingAppLoader.launch(jarPath);
                SwingJarAppHandle handle = JarSwingAppLoader.load(jarPath, definition.width(), definition.height());
                swingApps.add(new SwingJarAppHandle(
                        jarFile,
                        handle.displayName(),
                        handle.livePanel(),
                        new OffscreenSwingApp(handle.livePanel(), definition.width(), definition.height(), surface::repaint),
                        launched,
                        handle.classLoader()));
            }
            for (SwingJarAppHandle handle : swingApps) {
                handle.startOffscreen();
            }
        } else if (DesktopConfig.TYPE_SWT.equalsIgnoreCase(desktopType) && "SwingSwtBrowser".equals(classNameString)) {
            swtBrowserPanel = new SwingSwtBrowserPanel();
            swtBrowserPanel.setBounds(bounds);
        } else if (DesktopConfig.TYPE_WIN32.equalsIgnoreCase(desktopType)) {
            String[] command = null;
            String title = "";
            Random rand = new Random();
            if ("Calculator".equals(classNameString)) {
                command = new String[]{"calc.exe"};
                title = "Calculator";
            } else if ("Command".equals(classNameString)) {
                title = "Command " + rand.nextInt(50000);
                command = new String[]{"cmd.exe", "/c", "start", "\"" + title + "\"", "cmd.exe"};
            } else if ("FileExplorer".equals(classNameString)) {
                command = new String[]{"explorer.exe"};
                title = "Home - File Explorer";
            } else if ("Notepad".equals(classNameString)) {
                command = new String[]{"notepad.exe"};
                title = "Untitled - Notepad";
            }
            if (command != null) {
                win32Launcher = new Win32Launcher(command, title);
                win32Launcher.launch();
                win32Launcher.positionWindow(bounds);
                win32Launcher.minimizeWindow();
            }
        }
    }

    public void paint(Graphics2D g, boolean animate3d, int cubeFaceIndex,
                      double cubeYawDeg, double cubePitchDeg, double cubeZoom,
                      boolean interactive) {
        String shape = definition.projectionShape();
        if (!animate3d || "na".equalsIgnoreCase(shape)) {
            paintFlat(g, cubeFaceIndex);
            return;
        }
        if ("cube".equalsIgnoreCase(shape)) {
            BufferedImage[] faces = swingApps.stream().map(SwingJarAppHandle::getCurrentFrame).toArray(BufferedImage[]::new);
            ProjectionRenderer.drawCube(g, faces, bounds, cubeFaceIndex, definition.size(), cubeYawDeg, cubePitchDeg, cubeZoom, interactive);
        } else if ("windowPlaneLeft".equalsIgnoreCase(shape)) {
        	ProjectionRenderer.drawTilted(g, imageForIndex(0), bounds, definition.tilt(), definition.size(), true);
        } else if ("windowPlaneRight".equalsIgnoreCase(shape)) {
        	ProjectionRenderer.drawTilted(g, imageForIndex(0), bounds, definition.tilt(), definition.size(), false);
        } else {
            paintFlat(g, cubeFaceIndex);
        }
    }

    private void paintFlat(Graphics2D g, int contentIndex) {
        BufferedImage image = imageForIndex(contentIndex);
        g.drawImage(image, bounds.x, bounds.y, bounds.width, bounds.height, null);
        g.setColor(new Color(0, 0, 0, 120));
        g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    public void restoreToInitialBounds() {
        bounds.setBounds(initialBounds);
        if (swtBrowserPanel != null) {
            swtBrowserPanel.setBounds(bounds);
        }
        if (win32Launcher != null) {
            win32Launcher.positionWindow(bounds);
        }
    }

    public JPanel panelForIndex(int idx) {
        if (swingApps.isEmpty()) {
            return null;
        }
        int index = Math.max(0, Math.min(idx, swingApps.size() - 1));
        return swingApps.get(index).livePanel();
    }

    public int appCount() {
        return swingApps.size();
    }

    public String appNameForIndex(int idx) {
        if (swingApps.isEmpty()) {
            return primaryClassSimpleName();
        }
        int index = Math.max(0, Math.min(idx, swingApps.size() - 1));
        return swingApps.get(index).displayName();
    }

    public void shutdown() {
        for (SwingJarAppHandle handle : swingApps) {
            handle.destroyProcess();
        }
        if (swtBrowserPanel != null) {
            swtBrowserPanel.disposeBrowser();
        }
        if (win32Launcher != null) {
            win32Launcher.closeWindow();
        }
    }

    private BufferedImage imageForIndex(int idx) {
        if (swtBrowserPanel != null) {
            BufferedImage img = swtBrowserPanel.getCurrentFrame();
            if (img != null) {
                return img;
            }
        }
        if (!swingApps.isEmpty()) {
            BufferedImage image = swingApps.get(Math.min(idx, swingApps.size() - 1)).getCurrentFrame();
            if (image != null) {
                return image;
            }
        }
        return PlaceholderImages.generic(bounds.width, bounds.height, appNameForIndex(idx));
    }

    private String primaryClassSimpleName() {
        if (definition.javaClassNames().isEmpty()) {
            return "";
        }
        String[] parts = definition.javaClassNames().get(0).split("\\.");
        return parts[parts.length - 1];
    }
}
