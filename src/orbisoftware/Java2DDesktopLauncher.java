package orbisoftware;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Java2DDesktopLauncher extends JFrame {
    private static final int TOOLBAR_HEIGHT = 60;
    private final DesktopConfig config;
    private final DesktopSurface desktopSurface;

    private final TextField urlField = new TextField();
    private final JButton backButton = new JButton("Back");
    private final JButton nextButton = new JButton("Next");
    private final JButton fullDefaultButton = new JButton("Default");
    private final JButton toggle2d3dButton = new JButton("3D");
    private final JButton newTabButton = new JButton("New Tab");
    private final JButton layoutButton = new JButton("Layout");
    private final JButton exitButton = new JButton("Exit");
    private final JLabel swingWindowLabel = new JLabel("");
    private final JPanel browserTabsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 14));
    private DesktopIndicatorPanel desktopIndicatorPanel = null;
    private JPanel toolbarPanel;

    private static final String DEFAULT_CONFIG_PATH = "config/3d_desktop.xml";
    public static final Color BACKGROUND_COLOR = new Color(79, 128, 235);
    public static final Color TOOLBAR_COLOR = new Color(0, 0, 0);

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                Path xmlPath = Paths.get(DEFAULT_CONFIG_PATH);
                DesktopConfig config = DesktopConfig.load(Files.newInputStream(xmlPath));
                Java2DDesktopLauncher launcher = new Java2DDesktopLauncher(config);
                launcher.setVisible(true);
                SwingUtilities.invokeLater(() -> {
                    launcher.desktopSurface.syncVisibleComponents();
                    launcher.desktopSurface.revalidate();
                    launcher.desktopSurface.repaint();
                    launcher.syncToolbarUrlFromActiveDesktop();
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(null, "Failed to start Java2D desktop:\n" + ex.getMessage(), "Startup Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    public Java2DDesktopLauncher(DesktopConfig config) {
        super("Java 3D Desktop");
        this.config = config;
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setBackground(new Color(20, 24, 28));

        desktopSurface = new DesktopSurface(config, this::syncToolbarUrlFromActiveDesktop);
        for (java.util.List<DesktopWindow> desktop : desktopSurface.getDesktops()) {
            for (DesktopWindow window : desktop) {
                if (window.swtBrowserPanel != null) {
                    window.swtBrowserPanel.setTabStateListener((labels, selectedIndex, currentUrl) -> syncToolbarUrlFromActiveDesktop());
                }
            }
        }

        JPanel toolbar = buildToolbar();
        add(desktopSurface, BorderLayout.CENTER);
        add(toolbar, BorderLayout.NORTH);

        setUndecorated(true);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setMinimumSize(new Dimension(1024, 700));
        installKeyBindings();
        syncToolbarUrlFromActiveDesktop();
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { applyDesktopShape(); }
            @Override public void componentMoved(ComponentEvent e) { applyDesktopShape(); }
            @Override public void componentShown(ComponentEvent e) { applyDesktopShape(); }
        });
        SwingUtilities.invokeLater(this::applyDesktopShape);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { desktopSurface.shutdownPersistentResources(); }
        });
    }

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout());
        this.toolbarPanel = toolbar;
        toolbar.setPreferredSize(new Dimension(10, TOOLBAR_HEIGHT));
        toolbar.setBackground(TOOLBAR_COLOR);

        JPanel leftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 14));
        leftControls.setBackground(TOOLBAR_COLOR);

        backButton.setPreferredSize(new Dimension(70, 26));
        urlField.setPreferredSize(new Dimension(275, 22));
        nextButton.setPreferredSize(new Dimension(70, 26));
        fullDefaultButton.setPreferredSize(new Dimension(80, 26));
        toggle2d3dButton.setPreferredSize(new Dimension(70, 26));
        newTabButton.setPreferredSize(new Dimension(100, 26));
        layoutButton.setPreferredSize(new Dimension(80, 26));
        exitButton.setPreferredSize(new Dimension(70, 26));
        swingWindowLabel.setForeground(Color.WHITE);
        browserTabsPanel.setBackground(TOOLBAR_COLOR);

        leftControls.add(backButton);
        leftControls.add(urlField);
        leftControls.add(nextButton);
        leftControls.add(fullDefaultButton);
        leftControls.add(toggle2d3dButton);
        leftControls.add(newTabButton);
        leftControls.add(layoutButton);
        leftControls.add(swingWindowLabel);
        leftControls.add(browserTabsPanel);
        leftControls.add(exitButton);

        desktopIndicatorPanel = new DesktopIndicatorPanel(config.desktops.size(), desktopSurface.getCurrentDesktop(), this::activateDesktopFromIndicator);
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(TOOLBAR_COLOR);
        centerPanel.add(leftControls, BorderLayout.WEST);
        JPanel rightWrap = new JPanel(new BorderLayout());
        rightWrap.setBackground(TOOLBAR_COLOR);
        rightWrap.add(desktopIndicatorPanel, BorderLayout.EAST);

        toolbar.add(centerPanel, BorderLayout.CENTER);
        toolbar.add(rightWrap, BorderLayout.EAST);

        backButton.addActionListener(this::onBack);
        nextButton.addActionListener(this::onNext);
        fullDefaultButton.addActionListener(this::onFullDefault);
        toggle2d3dButton.addActionListener(this::onToggle2d3d);
        newTabButton.addActionListener(this::onNewTab);
        layoutButton.addActionListener(this::onLayout);
        exitButton.addActionListener(this::onExit);
        urlField.addActionListener(this::onUrlEntered);
        return toolbar;
    }

    private void activateDesktopFromIndicator(int index) {
        desktopSurface.setCurrentDesktop(index);
        syncToolbarUrlFromActiveDesktop();
        applyDesktopShape();
        desktopSurface.requestFocusInWindow();
        desktopSurface.revalidate();
        desktopSurface.repaint();
    }

    private void onBack(ActionEvent e) {
        desktopSurface.getDesktops().get(desktopSurface.getCurrentDesktop()).stream().filter(w -> w.swtBrowserPanel != null).findFirst().ifPresent(w -> w.swtBrowserPanel.goBack());
    }

    private void onNext(ActionEvent e) { desktopSurface.nextWindow(); }
    private void onFullDefault(ActionEvent e) { desktopSurface.toggleFirstDesktopSizeMode(); }
    private void onToggle2d3d(ActionEvent e) { desktopSurface.toggleAnimate3d(); syncToolbarUrlFromActiveDesktop(); applyDesktopShape(); }
    private void onLayout(ActionEvent e) { desktopSurface.restoreCurrentDesktopLayout(); applyDesktopShape(); }
    private void onExit(ActionEvent e) { dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)); }

    private void onNewTab(ActionEvent e) {
        DesktopWindow browserWindow = activeBrowserWindow();
        if (browserWindow != null && browserWindow.swtBrowserPanel != null) browserWindow.swtBrowserPanel.addNewTab();
    }

    private void onUrlEntered(ActionEvent e) {
        String url = urlField.getText().trim();
        if (!url.isBlank()) {
            desktopSurface.getDesktops().get(desktopSurface.getCurrentDesktop()).stream().filter(w -> w.swtBrowserPanel != null).findFirst().ifPresent(w -> w.swtBrowserPanel.navigateTo(url));
        }
    }

    private void installKeyBindings() {
        getRootPane().registerKeyboardAction(e -> {
            if (shouldLetFocusedComponentHandleSpace()) {
                return;
            }
            desktopSurface.toggleAnimate3d();
            syncToolbarUrlFromActiveDesktop();
            applyDesktopShape();
        }, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private boolean shouldLetFocusedComponentHandleSpace() {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focusOwner == null) {
            return false;
        }
        if (focusOwner == desktopSurface) {
            return false;
        }
        return SwingUtilities.isDescendingFrom(focusOwner, desktopSurface)
                && !SwingUtilities.isDescendingFrom(focusOwner, toolbarPanel);
    }

    private void syncToolbarUrlFromActiveDesktop() {
        int currentDesktop = desktopSurface.getCurrentDesktop();
        DesktopConfig.DesktopDefinition desktopDefinition = config.desktops.get(currentDesktop);
        boolean isSwingDesktop = desktopDefinition.isSwingDesktop();
        boolean isSwtDesktop = desktopDefinition.isSwtDesktop();
        boolean isWin32Desktop = desktopDefinition.isWin32Desktop();
        boolean hasBrowser = isSwtDesktop && desktopSurface.currentDesktopHasBrowser();
        boolean supportsAnimation = isSwingDesktop && desktopSurface.currentDesktopSupportsAnimation();
        boolean hasSwingWindows = isSwingDesktop && desktopSurface.currentDesktopHasSwingWindows();
        boolean showFullDefault = desktopSurface.isFirstDesktop2dMode() && hasSwingWindows;

        backButton.setVisible(isSwtDesktop);
        urlField.setVisible(isSwtDesktop);
        newTabButton.setVisible(isSwtDesktop);
        browserTabsPanel.setVisible(false);
        nextButton.setVisible(isSwingDesktop);
        fullDefaultButton.setVisible(showFullDefault);
        toggle2d3dButton.setVisible(isSwingDesktop);
        swingWindowLabel.setVisible(isSwingDesktop && !desktopSurface.isAnimate3d());
        layoutButton.setVisible(isWin32Desktop && currentDesktop == 2);
        exitButton.setVisible(isSwingDesktop || isSwtDesktop || isWin32Desktop);

        nextButton.setEnabled(hasSwingWindows);
        fullDefaultButton.setEnabled(showFullDefault);
        fullDefaultButton.setText(desktopSurface.isFirstDesktopFullMode() ? "Default" : "Full");
        toggle2d3dButton.setEnabled(supportsAnimation);
        toggle2d3dButton.setText(desktopSurface.isAnimate3d() ? "2D" : "3D");
        urlField.setEnabled(hasBrowser);
        urlField.setEditable(hasBrowser);
        if (!hasBrowser) urlField.setText("");
        backButton.setEnabled(hasBrowser);
        newTabButton.setEnabled(hasBrowser);
        layoutButton.setEnabled(isWin32Desktop && currentDesktop == 2);
        swingWindowLabel.setText(desktopSurface.isAnimate3d() ? "" : desktopSurface.currentFlatWindowLabel());

        if (hasBrowser) {
            for (DesktopWindow w : desktopSurface.getDesktops().get(currentDesktop)) {
                if (w.swtBrowserPanel != null) {
                    String current = w.swtBrowserPanel.getCurrentUrl();
                    if (current != null && !current.isBlank()) urlField.setText(current);
                    break;
                }
            }
        }

        if (desktopIndicatorPanel != null) {
            desktopIndicatorPanel.setSelectedIndex(currentDesktop);
            desktopIndicatorPanel.revalidate();
            desktopIndicatorPanel.repaint();
        }
        if (toolbarPanel != null) {
            toolbarPanel.revalidate();
            toolbarPanel.repaint();
            SwingUtilities.invokeLater(() -> {
                if (desktopIndicatorPanel != null) {
                    desktopIndicatorPanel.revalidate();
                    desktopIndicatorPanel.repaint();
                }
                toolbarPanel.repaint();
            });
        }
    }

    private DesktopWindow activeBrowserWindow() {
        int currentDesktop = desktopSurface.getCurrentDesktop();
        for (DesktopWindow w : desktopSurface.getDesktops().get(currentDesktop)) if (w.swtBrowserPanel != null) return w;
        return null;
    }

    private void applyDesktopShape() {
        int frameWidth = getWidth();
        int frameHeight = getHeight();
        if (frameWidth <= 0 || frameHeight <= 0) return;
        Area shape = new Area(new Rectangle(0, 0, frameWidth, frameHeight));
        if (desktopSurface != null && desktopSurface.isShowing()) {
            Point surfacePoint = SwingUtilities.convertPoint(desktopSurface, 0, 0, getLayeredPane());
            Point surfaceScreenPoint = new Point(0, 0);
            SwingUtilities.convertPointToScreen(surfaceScreenPoint, desktopSurface);
            for (DesktopWindow window : desktopSurface.getDesktops().get(desktopSurface.getCurrentDesktop())) {
                if (window.win32Launcher == null || window.win32Launcher.getWindowHandle() == null) continue;
                Rectangle hole = new Rectangle(window.bounds);
                Rectangle clampedHole = new Rectangle(hole);
                int maxW = Math.max(0, desktopSurface.getWidth() - clampedHole.x);
                int maxH = Math.max(0, desktopSurface.getHeight() - clampedHole.y);
                clampedHole.width = Math.min(clampedHole.width, maxW);
                clampedHole.height = Math.min(clampedHole.height, maxH);
                if (clampedHole.width <= 0 || clampedHole.height <= 0) continue;
                Rectangle translated = new Rectangle(surfacePoint.x + clampedHole.x, surfacePoint.y + clampedHole.y, clampedHole.width, clampedHole.height);
                shape.subtract(new Area(translated));
                Rectangle screenBounds = new Rectangle(surfaceScreenPoint.x + clampedHole.x, surfaceScreenPoint.y + clampedHole.y, clampedHole.width, clampedHole.height);
                window.win32Launcher.positionWindow(screenBounds);
            }
        }
        shape.add(new Area(new Rectangle(0, 0, frameWidth, TOOLBAR_HEIGHT)));
        setShape(shape);
    }

    private static final class DesktopIndicatorPanel extends JPanel {
        interface DesktopSelectionListener {
            void onDesktopSelected(int index);
        }

        private final int count;
        private int selectedIndex;
        private final DesktopSelectionListener listener;

        DesktopIndicatorPanel(int count, int selectedIndex, DesktopSelectionListener listener) {
            this.count = Math.max(0, count);
            this.selectedIndex = Math.max(0, selectedIndex);
            this.listener = listener;

            setOpaque(true);
            setBackground(Java2DDesktopLauncher.TOOLBAR_COLOR);
            setPreferredSize(new Dimension(Math.max(96, 30 * this.count + 20), TOOLBAR_HEIGHT));
            setMinimumSize(getPreferredSize());

            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    int idx = hitTest(e.getPoint().x, e.getPoint().y);
                    if (idx >= 0 && idx < count && listener != null) {
                        listener.onDesktopSelected(idx);
                    }
                }
            });
        }

        void setSelectedIndex(int selectedIndex) {
            if (count <= 0) {
                this.selectedIndex = 0;
            } else {
                this.selectedIndex = Math.max(0, Math.min(count - 1, selectedIndex));
            }
            repaint();
        }

        private int hitTest(int x, int y) {
            for (int i = 0; i < count; i++) {
                Shape s = ellipseAt(i);
                if (s.getBounds2D().contains(x - 4, y - 4, 8, 8)) {
                    return i;
                }
            }
            return -1;
        }

        private Shape ellipseAt(int index) {
            int diameter = 18;
            int gap = 12;
            int totalWidth = count * diameter + Math.max(0, count - 1) * gap;
            int startX = Math.max(10, (getWidth() - totalWidth) / 2);
            int y = Math.max(8, (getHeight() - diameter) / 2);
            int x = startX + index * (diameter + gap);
            return new Ellipse2D.Double(x, y, diameter, diameter);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                for (int i = 0; i < count; i++) {
                    Shape ellipse = ellipseAt(i);
                    if (i == selectedIndex) {
                        g2.setColor(new Color(220, 225, 235));
                        g2.fill(ellipse);
                        g2.setColor(new Color(80, 90, 105));
                        g2.draw(ellipse);
                    } else {
                        g2.setColor(new Color(110, 118, 130));
                        g2.fill(ellipse);
                        g2.setColor(new Color(70, 76, 86));
                        g2.draw(ellipse);
                    }
                }
            } finally {
                g2.dispose();
            }
        }
    }
}
