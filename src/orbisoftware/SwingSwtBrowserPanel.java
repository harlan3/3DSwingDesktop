package orbisoftware;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.awt.SWT_AWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationAdapter;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabFolder2Adapter;
import org.eclipse.swt.custom.CTabFolderEvent;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Panel;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DirectColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class SwingSwtBrowserPanel extends Panel {
    public interface TabStateListener {
        void onTabStateChanged(List<String> tabLabels, int selectedIndex, String currentUrl);
    }

    private final Canvas swtCanvas = new Canvas();

    private volatile Display display;
    private volatile Shell shell;
    private volatile CTabFolder tabFolder;
    private volatile Thread swtThread;

    private final Object initLock = new Object();
    private volatile boolean starting = false;
    private volatile boolean disposeRequested = false;

    private volatile String pendingUrl = "https://www.google.com";
    private volatile TabStateListener tabStateListener;

    public SwingSwtBrowserPanel() {
        super(new BorderLayout());

        setBackground(Color.DARK_GRAY);
        swtCanvas.setBackground(Color.DARK_GRAY);
        swtCanvas.setFocusable(false);
        add(swtCanvas, BorderLayout.CENTER);

        swtCanvas.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                syncShellSize();
            }

            @Override
            public void componentShown(ComponentEvent e) {
                syncShellSize();
            }
        });

        addHierarchyBoundsListener(new java.awt.event.HierarchyBoundsAdapter() {
            @Override
            public void ancestorResized(java.awt.event.HierarchyEvent e) {
                syncShellSize();
            }

            @Override
            public void ancestorMoved(java.awt.event.HierarchyEvent e) {
                syncShellSize();
            }
        });
    }

    public void setTabStateListener(TabStateListener listener) {
        this.tabStateListener = listener;
        notifyTabStateChangedAsync();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        startBrowserIfNeeded();
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
    }

    public void addNewTab() {
        startBrowserIfNeeded();
        Display d = display;
        if (d != null && !d.isDisposed()) {
            final String url = normalizeUrl(pendingUrl);
            d.asyncExec(() -> {
                if (tabFolder != null && !tabFolder.isDisposed()) {
                    createTab(url, true);
                    notifyTabStateChanged();
                }
            });
        }
    }

    public void selectTab(int index) {
        Display d = display;
        if (d != null && !d.isDisposed()) {
            d.asyncExec(() -> {
                if (tabFolder == null || tabFolder.isDisposed()) {
                    return;
                }
                CTabItem[] items = tabFolder.getItems();
                if (index >= 0 && index < items.length) {
                    tabFolder.setSelection(items[index]);
                    notifyTabStateChanged();
                }
            });
        }
    }

    public List<String> getTabLabels() {
        AtomicReference<List<String>> ref = new AtomicReference<>(new ArrayList<>());
        Display d = display;
        if (d != null && !d.isDisposed()) {
            d.syncExec(() -> {
                if (tabFolder == null || tabFolder.isDisposed()) {
                    return;
                }
                List<String> labels = new ArrayList<>();
                for (CTabItem item : tabFolder.getItems()) {
                    labels.add(item.getText());
                }
                ref.set(labels);
            });
        }
        return ref.get();
    }

    public int getSelectedTabIndex() {
        AtomicReference<Integer> ref = new AtomicReference<>(-1);
        Display d = display;
        if (d != null && !d.isDisposed()) {
            d.syncExec(() -> {
                if (tabFolder == null || tabFolder.isDisposed()) {
                    return;
                }
                ref.set(tabFolder.getSelectionIndex());
            });
        }
        return ref.get();
    }

    public void navigateTo(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        pendingUrl = normalizeUrl(url);
        Display d = display;
        if (d != null && !d.isDisposed()) {
            d.asyncExec(() -> {
                Browser selected = selectedBrowser();
                if (selected != null && !selected.isDisposed()) {
                    selected.setUrl(pendingUrl);
                }
            });
        }
    }

    public void goBack() {
        Display d = display;
        if (d != null && !d.isDisposed()) {
            d.asyncExec(() -> {
                Browser selected = selectedBrowser();
                if (selected != null && !selected.isDisposed() && selected.isBackEnabled()) {
                    selected.back();
                }
            });
        }
    }

    public String getCurrentUrl() {
        AtomicReference<String> ref = new AtomicReference<>(pendingUrl);
        Display d = display;
        if (d != null && !d.isDisposed()) {
            d.syncExec(() -> {
                Browser selected = selectedBrowser();
                if (selected != null && !selected.isDisposed()) {
                    String url = selected.getUrl();
                    if (url != null && !url.isBlank()) {
                        ref.set(url);
                    }
                }
            });
        }
        return ref.get();
    }

    private void startBrowserIfNeeded() {
        Dimension size = swtCanvas.getSize();
        if (size.width <= 0 || size.height <= 0) {
            EventQueue.invokeLater(this::startBrowserIfNeeded);
            return;
        }

        synchronized (initLock) {
            if (starting || swtThread != null || !swtCanvas.isDisplayable()) {
                return;
            }
            starting = true;
            disposeRequested = false;
        }

        swtThread = new Thread(() -> {
            Display localDisplay = null;
            Shell localShell = null;
            CTabFolder localTabFolder = null;
            try {
                localDisplay = new Display();
                display = localDisplay;

                localShell = SWT_AWT.new_Shell(localDisplay, swtCanvas);
                shell = localShell;
                localShell.setLayout(new FillLayout());

                localTabFolder = new CTabFolder(localShell, SWT.BORDER);
                localTabFolder.setSimple(false);
                localTabFolder.setUnselectedCloseVisible(true);
                localTabFolder.setBorderVisible(true);
                tabFolder = localTabFolder;

                localTabFolder.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
                    @Override
                    public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {
                        notifyTabStateChanged();
                    }
                });

                localTabFolder.addCTabFolder2Listener(new CTabFolder2Adapter() {
                    @Override
                    public void close(CTabFolderEvent event) {
                        if (!(event.item instanceof CTabItem)) {
                            return;
                        }
                        CTabItem item = (CTabItem) event.item;
                        Control control = item.getControl();
                        if (control != null && !control.isDisposed()) {
                            control.dispose();
                        }
                        item.dispose();
                        if (tabFolder != null && !tabFolder.isDisposed() && tabFolder.getItemCount() == 0) {
                            createTab(normalizeUrl(pendingUrl), true);
                        }
                        notifyTabStateChanged();
                    }
                });

                createTab(normalizeUrl(pendingUrl), true);

                localShell.setSize(Math.max(1, size.width), Math.max(1, size.height));
                localShell.open();
                localShell.layout(true, true);
                notifyTabStateChanged();

                while (!disposeRequested && !localShell.isDisposed()) {
                    if (!localDisplay.readAndDispatch()) {
                        localDisplay.sleep();
                    }
                }
            } catch (SWTError err) {
                err.printStackTrace();
            } catch (Throwable t) {
                t.printStackTrace();
            } finally {
                try {
                    if (localTabFolder != null && !localTabFolder.isDisposed()) {
                        for (CTabItem item : localTabFolder.getItems()) {
                            Control control = item.getControl();
                            if (control != null && !control.isDisposed()) {
                                control.dispose();
                            }
                        }
                        localTabFolder.dispose();
                    }
                } catch (Throwable ignored) {
                }
                try {
                    if (localShell != null && !localShell.isDisposed()) {
                        localShell.dispose();
                    }
                } catch (Throwable ignored) {
                }
                try {
                    if (localDisplay != null && !localDisplay.isDisposed()) {
                        localDisplay.dispose();
                    }
                } catch (Throwable ignored) {
                }

                tabFolder = null;
                shell = null;
                display = null;
                synchronized (initLock) {
                    swtThread = null;
                    starting = false;
                }
            }
        }, "SWT-Browser-Thread");

        swtThread.setDaemon(true);
        swtThread.start();
    }

    private void createTab(String url, boolean select) {
        if (tabFolder == null || tabFolder.isDisposed()) {
            return;
        }
        CTabItem item = new CTabItem(tabFolder, SWT.CLOSE);
        item.setText(labelForUrl(url));

        Browser newBrowser = new Browser(tabFolder, SWT.EDGE);
        item.setControl(newBrowser);
        hookBrowserListeners(item, newBrowser);
        newBrowser.setUrl(url);

        if (select) {
            tabFolder.setSelection(item);
        }
        tabFolder.layout(true, true);
    }

    private void hookBrowserListeners(CTabItem item, Browser browser) {
        browser.addLocationListener(new LocationAdapter() {
            @Override
            public void changed(LocationEvent event) {
                String url = browser.getUrl();
                String label = labelForUrl(url);
                if (!item.isDisposed()) {
                    item.setText(label);
                }
                pendingUrl = normalizeUrl(url);
                notifyTabStateChanged();
            }
        });
    }

    private Browser selectedBrowser() {
        CTabFolder folder = tabFolder;
        if (folder == null || folder.isDisposed()) {
            return null;
        }
        CTabItem item = folder.getSelection();
        if (item == null) {
            return null;
        }
        Control control = item.getControl();
        return control instanceof Browser ? (Browser) control : null;
    }

    private void syncShellSize() {
        Display d = display;
        if (d == null || d.isDisposed()) {
            return;
        }

        Dimension size = swtCanvas.getSize();
        int w = Math.max(1, size.width);
        int h = Math.max(1, size.height);

        d.asyncExec(() -> {
            if (shell != null && !shell.isDisposed()) {
                shell.setSize(w, h);
                shell.layout(true, true);
            }
        });
    }

    public void setBrowserHostVisible(boolean visible) {
        EventQueue.invokeLater(() -> {
            super.setVisible(visible);
            swtCanvas.setVisible(visible);
            if (visible) {
                syncShellSize();
            }
        });

        Display d = display;
        if (d != null && !d.isDisposed()) {
            d.asyncExec(() -> {
                if (shell != null && !shell.isDisposed()) {
                    shell.setVisible(visible);
                    if (visible) {
                        Dimension size = swtCanvas.getSize();
                        int w = Math.max(1, size.width);
                        int h = Math.max(1, size.height);
                        shell.setSize(w, h);
                        shell.layout(true, true);
                    }
                }
            });
        }
    }

    public void disposeBrowser() {
        disposeRequested = true;
        Display d = display;
        if (d != null && !d.isDisposed()) {
            d.asyncExec(() -> {
                if (shell != null && !shell.isDisposed()) {
                    shell.dispose();
                }
            });
            d.wake();
        }
    }

    private void notifyTabStateChangedAsync() {
        Display d = display;
        if (d != null && !d.isDisposed()) {
            d.asyncExec(this::notifyTabStateChanged);
        }
    }

    private void notifyTabStateChanged() {
        TabStateListener listener = tabStateListener;
        if (listener == null) {
            return;
        }
        List<String> labels = new ArrayList<>();
        int selectedIndex = -1;
        String currentUrl = pendingUrl;

        if (tabFolder != null && !tabFolder.isDisposed()) {
            CTabItem[] items = tabFolder.getItems();
            for (CTabItem item : items) {
                labels.add(item.getText());
            }
            selectedIndex = tabFolder.getSelectionIndex();
            Browser selected = selectedBrowser();
            if (selected != null && !selected.isDisposed() && selected.getUrl() != null && !selected.getUrl().isBlank()) {
                currentUrl = selected.getUrl();
            }
        }

        final List<String> labelsSnapshot = labels;
        final int selectedSnapshot = selectedIndex;
        final String urlSnapshot = currentUrl;
        EventQueue.invokeLater(() -> listener.onTabStateChanged(labelsSnapshot, selectedSnapshot, urlSnapshot));
    }

    public BufferedImage getCurrentFrame() {
        final AtomicReference<BufferedImage> ref = new AtomicReference<>();
        Display d = display;
        if (d != null && !d.isDisposed()) {
            d.syncExec(() -> {
                Browser selected = selectedBrowser();
                if (selected != null && !selected.isDisposed() && shell != null && !shell.isDisposed()) {
                    org.eclipse.swt.graphics.Rectangle b = selected.getBounds();
                    if (b.width <= 0 || b.height <= 0) {
                        ref.set(null);
                        return;
                    }
                    Image swtImage = new Image(d, b.width, b.height);
                    GC gc = new GC(swtImage);
                    selected.print(gc);
                    gc.dispose();
                    BufferedImage bi = convertToAWT(swtImage.getImageData());
                    swtImage.dispose();
                    ref.set(bi);
                }
            });
        }
        return ref.get();
    }

    private static String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return "https://www.google.com";
        }
        String trimmed = url.trim();
        if (!trimmed.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            return "https://" + trimmed;
        }
        return trimmed;
    }

    private static String labelForUrl(String url) {
        try {
            URI uri = URI.create(normalizeUrl(url));
            String host = uri.getHost();
            if (host != null && !host.isBlank()) {
                host = host.toLowerCase();
                if (host.startsWith("www.")) {
                    host = host.substring(4);
                }
                return host;
            }
        } catch (Throwable ignored) {
        }
        String normalized = normalizeUrl(url);
        return normalized.length() > 20 ? normalized.substring(0, 20) : normalized;
    }

    private BufferedImage convertToAWT(ImageData data) {
        java.awt.image.ColorModel colorModel;
        PaletteData palette = data.palette;
        if (palette.isDirect) {
            colorModel = new DirectColorModel(data.depth, palette.redMask, palette.greenMask, palette.blueMask);
            BufferedImage bufferedImage = new BufferedImage(colorModel, colorModel.createCompatibleWritableRaster(data.width, data.height), false, null);
            WritableRaster raster = bufferedImage.getRaster();
            int[] pixelArray = new int[3];
            for (int y = 0; y < data.height; y++) {
                for (int x = 0; x < data.width; x++) {
                    int pixel = data.getPixel(x, y);
                    RGB rgb = palette.getRGB(pixel);
                    pixelArray[0] = rgb.red;
                    pixelArray[1] = rgb.green;
                    pixelArray[2] = rgb.blue;
                    raster.setPixels(x, y, 1, 1, pixelArray);
                }
            }
            return bufferedImage;
        } else {
            RGB[] rgbs = palette.getRGBs();
            byte[] red = new byte[rgbs.length];
            byte[] green = new byte[rgbs.length];
            byte[] blue = new byte[rgbs.length];
            for (int i = 0; i < rgbs.length; i++) {
                RGB rgb = rgbs[i];
                red[i] = (byte) rgb.red;
                green[i] = (byte) rgb.green;
                blue[i] = (byte) rgb.blue;
            }
            colorModel = data.transparentPixel != -1
                    ? new IndexColorModel(data.depth, rgbs.length, red, green, blue, data.transparentPixel)
                    : new IndexColorModel(data.depth, rgbs.length, red, green, blue);
            BufferedImage bufferedImage = new BufferedImage(colorModel, colorModel.createCompatibleWritableRaster(data.width, data.height), false, null);
            WritableRaster raster = bufferedImage.getRaster();
            int[] pixelArray = new int[1];
            for (int y = 0; y < data.height; y++) {
                for (int x = 0; x < data.width; x++) {
                    int pixel = data.getPixel(x, y);
                    pixelArray[0] = pixel;
                    raster.setPixel(x, y, pixelArray);
                }
            }
            return bufferedImage;
        }
    }
}
