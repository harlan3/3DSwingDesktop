package orbisoftware;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DesktopSurface extends JPanel {
    private static final double CUBE_DRAG_SENSITIVITY = 0.28;
    private static final double CUBE_WHEEL_ZOOM_FACTOR = 1.05;
    private static final long DRAG_REPAINT_INTERVAL_NANOS = 33_333_333L;

    private final DesktopConfig config;
    private final List<List<DesktopWindow>> desktops = new ArrayList<>();
    private int currentDesktop = 0;
    private boolean prefer3d = true;
    private int cubeFaceIndex = 0;
    private int flatWindowIndex = 0;
    private boolean firstDesktopFullMode = true;
    private final Runnable onStateChanged;

    private double cubeYawDeg = -28.0;
    private double cubePitchDeg = 18.0;
    private double cubeZoom = 1.0;

    private Point lastMousePoint;
    private boolean draggingCube = false;
    private long lastDragRepaintNanos = 0L;

    private DesktopWindow persistentSwtBrowserWindow;
    private int persistentSwtBrowserDesktopIndex = -1;

    public DesktopSurface(DesktopConfig config, Runnable onStateChanged) {
        this.config = config;
        setLayout(null);
        setFocusable(false);
        this.onStateChanged = onStateChanged;

        for (DesktopConfig.DesktopDefinition desktopDefinition : config.desktops) {
            List<DesktopWindow> windows = new ArrayList<>();
            for (DesktopConfig.JavaWindowAppDefinition app : desktopDefinition.apps()) {
                DesktopWindow window = new DesktopWindow(desktopDefinition, app, this);
                window.initialize();
                windows.add(window);
            }
            desktops.add(windows);
        }

        locatePersistentSwtBrowser();

        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastMousePoint = e.getPoint();
                draggingCube = currentDesktopSupportsAnimation() && isAnimate3d() && findCubeWindowAt(e.getPoint()) != null;
                if (draggingCube) {
                    lastDragRepaintNanos = 0L;
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!draggingCube || lastMousePoint == null) {
                    return;
                }
                int dx = e.getX() - lastMousePoint.x;
                int dy = e.getY() - lastMousePoint.y;
                cubeYawDeg += dx * CUBE_DRAG_SENSITIVITY;
                cubePitchDeg -= dy * CUBE_DRAG_SENSITIVITY;
                cubePitchDeg = Math.max(-89.0, Math.min(89.0, cubePitchDeg));
                lastMousePoint = e.getPoint();
                long now = System.nanoTime();
                if (lastDragRepaintNanos == 0L || now - lastDragRepaintNanos >= DRAG_REPAINT_INTERVAL_NANOS) {
                    lastDragRepaintNanos = now;
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                draggingCube = false;
                lastMousePoint = null;
                lastDragRepaintNanos = 0L;
                repaint();
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                DesktopWindow cube = findCubeWindowAt(e.getPoint());
                if (cube == null || !isAnimate3d()) {
                    return;
                }
                double factor = e.getWheelRotation() < 0 ? CUBE_WHEEL_ZOOM_FACTOR : (1.0 / CUBE_WHEEL_ZOOM_FACTOR);
                cubeZoom *= factor;
                cubeZoom = Math.max(0.55, Math.min(2.25, cubeZoom));
                repaint();
            }
        };

        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
        addMouseWheelListener(mouseHandler);

        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                layoutVisibleComponents();
            }

            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                layoutVisibleComponents();
            }
        });

        initializePersistentSwtBrowser();
        syncVisibleComponents();
    }

    public Path resolveArtifactPath(String fileName) {
        Path direct = Paths.get(fileName);
        if (direct.isAbsolute() && direct.toFile().exists()) {
            return direct.normalize();
        }

        Path[] candidates = new Path[] {
                direct,
                Paths.get("appjars", fileName),
                Paths.get("3DSwingDesktop", "appjars", fileName),
                Paths.get(System.getProperty("user.dir"), "appjars", fileName),
                Paths.get(System.getProperty("user.dir"), "3DSwingDesktop", "appjars", fileName)
        };

        for (Path candidate : candidates) {
            if (candidate != null && candidate.toFile().exists()) {
                return candidate.normalize();
            }
        }
        return Paths.get("3DSwingDesktop", "appjars", fileName).normalize();
    }

    public List<List<DesktopWindow>> getDesktops() { return desktops; }
    public int getCurrentDesktop() { return currentDesktop; }
    public DesktopConfig.DesktopDefinition getCurrentDesktopDefinition() { return config.desktops.get(currentDesktop); }
    public boolean currentDesktopSupportsAnimation() { return getCurrentDesktopDefinition().animSupport(); }

    public boolean currentDesktopHasBrowser() {
        for (DesktopWindow w : desktops.get(currentDesktop)) {
            if (w.swtBrowserPanel != null) return true;
        }
        return false;
    }

    public boolean currentDesktopHasSwingWindows() {
        for (DesktopWindow w : desktops.get(currentDesktop)) {
            if (!w.swingApps.isEmpty()) return true;
        }
        return false;
    }

    public boolean currentDesktopHasCubeWindow() {
        for (DesktopWindow w : desktops.get(currentDesktop)) {
            if ("cube".equalsIgnoreCase(w.definition.projectionShape()) && !w.swingApps.isEmpty()) return true;
        }
        return false;
    }

    public String currentFlatWindowLabel() {
        WindowSelection sel = getCurrentFlatSelection();
        return sel == null ? "" : sel.window.appNameForIndex(sel.appIndex);
    }

    public void setCurrentDesktop(int currentDesktop) {
        int nextDesktop = Math.max(0, Math.min(desktops.size() - 1, currentDesktop));
        this.currentDesktop = nextDesktop;
        flatWindowIndex = 0;
        syncVisibleComponents();
        repaint();
        onStateChanged.run();
    }

    public void toggleAnimate3d() {
        if (!currentDesktopSupportsAnimation()) {
            return;
        }
        prefer3d = !prefer3d;
        syncVisibleComponents();
        repaint();
    }

    public boolean isAnimate3d() { return currentDesktopSupportsAnimation() && prefer3d; }

    public boolean isFirstDesktop2dMode() {
        return currentDesktop == 0
                && getCurrentDesktopDefinition().isSwingDesktop()
                && !isAnimate3d();
    }

    public boolean isFirstDesktopFullMode() {
        return firstDesktopFullMode;
    }

    public void toggleFirstDesktopSizeMode() {
        if (!isFirstDesktop2dMode()) {
            return;
        }
        firstDesktopFullMode = !firstDesktopFullMode;
        syncVisibleComponents();
        repaint();
        onStateChanged.run();
    }

    public void nextWindow() {
        if (isAnimate3d()) {
            nextCubeFace();
            return;
        }
        int total = totalFlatSwingWindows();
        if (total <= 0) {
            return;
        }
        flatWindowIndex = (flatWindowIndex + 1) % total;
        syncVisibleComponents();
        repaint();
        onStateChanged.run();
    }

    public void nextCubeFace() {
        int maxFaces = 1;
        for (DesktopWindow w : desktops.get(currentDesktop)) {
            if ("cube".equalsIgnoreCase(w.definition.projectionShape()) && !w.swingApps.isEmpty()) {
                maxFaces = Math.max(maxFaces, w.swingApps.size());
            }
        }
        cubeFaceIndex = (cubeFaceIndex + 1) % maxFaces;
        syncVisibleComponents();
        repaint();
    }

    public void restoreCurrentDesktopLayout() {
        if (currentDesktop != 2) return;
        DesktopConfig.DesktopDefinition desktopDefinition = getCurrentDesktopDefinition();
        if (!desktopDefinition.isWin32Desktop()) return;
        for (DesktopWindow window : desktops.get(currentDesktop)) {
            window.restoreToInitialBounds();
        }
        layoutVisibleComponents();
        repaint();
        onStateChanged.run();
    }

    public void shutdownPersistentResources() {
        for (List<DesktopWindow> desktop : desktops) {
            for (DesktopWindow window : desktop) {
                window.shutdown();
            }
        }
    }

    private void locatePersistentSwtBrowser() {
        for (int desktopIndex = 0; desktopIndex < desktops.size(); desktopIndex++) {
            for (DesktopWindow window : desktops.get(desktopIndex)) {
                if (window.swtBrowserPanel != null) {
                    persistentSwtBrowserWindow = window;
                    persistentSwtBrowserDesktopIndex = desktopIndex;
                    return;
                }
            }
        }
    }

    private DesktopWindow findCubeWindowAt(Point p) {
        for (DesktopWindow window : desktops.get(currentDesktop)) {
            if ("cube".equalsIgnoreCase(window.definition.projectionShape()) && window.bounds.contains(p)) {
                return window;
            }
        }
        return null;
    }

    public void syncVisibleComponents() {
        updateSwtBrowserVisibility();

        Set<Component> desiredComponents = new HashSet<>();
        if (persistentSwtBrowserWindow != null && persistentSwtBrowserWindow.swtBrowserPanel != null) {
            desiredComponents.add(persistentSwtBrowserWindow.swtBrowserPanel);
        }

        List<DesktopWindow> windows = desktops.get(currentDesktop);
        WindowSelection flatSelection = getCurrentFlatSelection();
        for (DesktopWindow window : windows) {
            if (shouldShowLiveSwing(window, flatSelection)) {
                JPanel panel = panelForDisplay(window, flatSelection);
                if (panel != null) desiredComponents.add(panel);
            }
            if (window.swtBrowserPanel != null && window != persistentSwtBrowserWindow && shouldShowLiveSwt(window)) {
                desiredComponents.add(window.swtBrowserPanel);
            }
        }

        for (Component component : getComponents()) {
            if (!desiredComponents.contains(component)) {
                remove(component);
            }
        }

        for (DesktopWindow window : windows) {
            if (window.swtBrowserPanel != null) {
                if (window == persistentSwtBrowserWindow) {
                    if (window.swtBrowserPanel.getParent() != this) add(window.swtBrowserPanel);
                    window.swtBrowserPanel.setBounds(persistentSwtBrowserBounds());
                } else if (shouldShowLiveSwt(window)) {
                    Rectangle r = clampToSurface(window.bounds);
                    if (r.width > 0 && r.height > 0) {
                        window.swtBrowserPanel.setBounds(r);
                        if (window.swtBrowserPanel.getParent() != this) add(window.swtBrowserPanel);
                    }
                }
            }

            if (shouldShowLiveSwing(window, flatSelection)) {
                JPanel panel = panelForDisplay(window, flatSelection);
                if (panel != null) {
                    Rectangle r = livePanelBounds(window);
                    panel.setBounds(r);
                    if (panel.getParent() != this) add(panel);
                }
            }
        }

        revalidate();
        repaint();
    }

    public void layoutVisibleComponents() {
        updateSwtBrowserVisibility();

        if (persistentSwtBrowserWindow != null && persistentSwtBrowserWindow.swtBrowserPanel != null
                && persistentSwtBrowserWindow.swtBrowserPanel.getParent() == this) {
            persistentSwtBrowserWindow.swtBrowserPanel.setBounds(persistentSwtBrowserBounds());
        }

        WindowSelection flatSelection = getCurrentFlatSelection();
        for (DesktopWindow window : desktops.get(currentDesktop)) {
            if (window.swtBrowserPanel != null && window != persistentSwtBrowserWindow && window.swtBrowserPanel.getParent() == this) {
                window.swtBrowserPanel.setBounds(shouldShowLiveSwt(window) ? clampToSurface(window.bounds) : hiddenBoundsFor(window.bounds));
            }
            JPanel panel = panelForDisplay(window, flatSelection);
            if (panel != null && panel.getParent() == this) {
                Rectangle r = livePanelBounds(window);
                panel.setBounds(r);
            }
        }

        revalidate();
        repaint();
    }

    private void initializePersistentSwtBrowser() {
        if (persistentSwtBrowserWindow == null || persistentSwtBrowserWindow.swtBrowserPanel == null) return;
        SwingSwtBrowserPanel browserPanel = persistentSwtBrowserWindow.swtBrowserPanel;
        if (browserPanel.getParent() != this) {
            browserPanel.setBounds(persistentSwtBrowserBounds());
            add(browserPanel);
        }
        updateSwtBrowserVisibility();
    }

    private void updateSwtBrowserVisibility() {
        for (int desktopIndex = 0; desktopIndex < desktops.size(); desktopIndex++) {
            List<DesktopWindow> windows = desktops.get(desktopIndex);
            for (DesktopWindow window : windows) {
                if (window.swtBrowserPanel == null) {
                    continue;
                }

                boolean shouldBeVisible =
                        desktopIndex == currentDesktop
                        && (!currentDesktopSupportsAnimation()
                        || "na".equalsIgnoreCase(window.definition.projectionShape()));

                window.swtBrowserPanel.setBrowserHostVisible(shouldBeVisible);
            }
        }
    }

    private Rectangle persistentSwtBrowserBounds() {
        if (persistentSwtBrowserWindow == null) return new Rectangle(-1, -1, 1, 1);
        if (currentDesktop == persistentSwtBrowserDesktopIndex && shouldShowLiveSwt(persistentSwtBrowserWindow)) {
            return clampToSurface(persistentSwtBrowserWindow.bounds);
        }
        return hiddenBoundsFor(persistentSwtBrowserWindow.bounds);
    }

    private boolean shouldShowLiveSwing(DesktopWindow window, WindowSelection selection) {
        if (window.swingApps.isEmpty()) return false;
        return !isAnimate3d() ? selection != null && selection.window == window : "na".equalsIgnoreCase(window.definition.projectionShape());
    }

    private boolean shouldShowLiveSwt(DesktopWindow window) {
        return window.swtBrowserPanel != null && (!isAnimate3d() || "na".equalsIgnoreCase(window.definition.projectionShape()));
    }

    private JPanel panelForDisplay(DesktopWindow window, WindowSelection selection) {
        if (window.swingApps.isEmpty()) return null;
        if (isAnimate3d()) {
            return "na".equalsIgnoreCase(window.definition.projectionShape()) ? window.panelForIndex(0) : null;
        }
        return selection != null && selection.window == window ? window.panelForIndex(selection.appIndex) : null;
    }

    private WindowSelection getCurrentFlatSelection() {
        List<WindowSelection> selections = flattenedSwingWindows();
        if (selections.isEmpty()) return null;
        int idx = Math.max(0, Math.min(flatWindowIndex, selections.size() - 1));
        return selections.get(idx);
    }

    private List<WindowSelection> flattenedSwingWindows() {
        List<WindowSelection> selections = new ArrayList<>();
        for (DesktopWindow window : desktops.get(currentDesktop)) {
            for (int i = 0; i < window.appCount(); i++) {
                selections.add(new WindowSelection(window, i));
            }
        }
        return selections;
    }

    private int totalFlatSwingWindows() { return flattenedSwingWindows().size(); }

    private Rectangle livePanelBounds(DesktopWindow window) {
        if (isFirstDesktop2dMode()) {
            if (firstDesktopFullMode) {
                return new Rectangle(0, 0, getWidth(), getHeight());
            }
            return clampToSurface(new Rectangle(0, 0, window.bounds.width, window.bounds.height));
        }

        if (isAnimate3d()) {
            return clampToSurface(window.bounds);
        }

        return new Rectangle(0, 0, getWidth(), getHeight());
    }

    private Rectangle clampToSurface(Rectangle source) {
        Rectangle r = new Rectangle(source);
        int maxW = Math.max(0, getWidth() - r.x);
        int maxH = Math.max(0, getHeight() - r.y);
        r.width = Math.min(r.width, maxW);
        r.height = Math.min(r.height, maxH);
        return r;
    }

    private Rectangle hiddenBoundsFor(Rectangle source) {
        return new Rectangle(-Math.max(1, source.width) - 100, -Math.max(1, source.height) - 100, Math.max(1, source.width), Math.max(1, source.height));
    }

    private void paintBackground(Graphics2D g2) {
        GradientPaint gp = new GradientPaint(0, 0, Java2DDesktopLauncher.BACKGROUND_COLOR, 0, getHeight(), new Color(147, 181, 235));
        g2.setPaint(gp);
        g2.fillRect(0, 0, getWidth(), getHeight());

        g2.setColor(new Color(120, 170, 233, 80));
        for (int j=0; j < 4; j++) {
	        for (int i = 0; i < 27; i++) {
	            int x = 30 + i * 70;
	            int y = (10 + (i % 5) * 12) + (j * 250);
	            g2.fillOval(x, y, 70, 70);
	        }
        }
    }
    
    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        paintBackground((Graphics2D) graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            WindowSelection selection = getCurrentFlatSelection();
            for (DesktopWindow window : desktops.get(currentDesktop)) {
                if (shouldShowLiveSwing(window, selection) || shouldShowLiveSwt(window)) continue;
                if (isFirstDesktop2dMode() && window.appCount() > 0) continue;
                window.paint(g, isAnimate3d(), cubeFaceIndex, cubeYawDeg, cubePitchDeg, cubeZoom, draggingCube);
            }
        } finally {
            g.dispose();
        }
    }

    private record WindowSelection(DesktopWindow window, int appIndex) {}
}