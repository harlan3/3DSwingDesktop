package orbisoftware;

import javax.swing.*;
import java.awt.*;
import java.applet.Applet;
import java.util.LinkedHashMap;
import java.util.Map;

public final class UiDirtyRepaintManager extends RepaintManager {
    private static final Object LOCK = new Object();
    private static UiDirtyRepaintManager instance;

    private final Map<JComponent, Runnable> trackedRoots = new LinkedHashMap<>();
    private boolean dispatchScheduled = false;

    private UiDirtyRepaintManager() {
    }

    public static UiDirtyRepaintManager installIfNeeded() {
        synchronized (LOCK) {
            if (instance == null) {
                RepaintManager current = RepaintManager.currentManager(null);
                if (current instanceof UiDirtyRepaintManager mgr) {
                    instance = mgr;
                } else {
                    instance = new UiDirtyRepaintManager();
                    RepaintManager.setCurrentManager(instance);
                }
            }
            return instance;
        }
    }

    public void trackRoot(JComponent root, Runnable callback) {
        if (root == null || callback == null) {
            return;
        }
        synchronized (trackedRoots) {
            trackedRoots.put(root, callback);
        }
    }

    public void untrackRoot(JComponent root) {
        if (root == null) {
            return;
        }
        synchronized (trackedRoots) {
            trackedRoots.remove(root);
        }
    }

    @Override
    public synchronized void addDirtyRegion(JComponent component, int x, int y, int w, int h) {
        super.addDirtyRegion(component, x, y, w, h);
        notifyIfTracked(component);
    }

    @Override
    public void addDirtyRegion(Window window, int x, int y, int w, int h) {
        super.addDirtyRegion(window, x, y, w, h);
    }

    @Override
    public void addDirtyRegion(Applet applet, int x, int y, int w, int h) {
        super.addDirtyRegion(applet, x, y, w, h);
    }

    private void notifyIfTracked(Component component) {
        if (component == null) {
            return;
        }
        Runnable callback = null;
        synchronized (trackedRoots) {
            for (Map.Entry<JComponent, Runnable> entry : trackedRoots.entrySet()) {
                if (SwingUtilities.isDescendingFrom(component, entry.getKey()) || component == entry.getKey()) {
                    callback = entry.getValue();
                    break;
                }
            }
        }
        if (callback == null) {
            return;
        }
        synchronized (trackedRoots) {
            if (dispatchScheduled) {
                return;
            }
            dispatchScheduled = true;
        }

        final Runnable callbackToRun = callback;
        SwingUtilities.invokeLater(() -> {
            synchronized (trackedRoots) {
                dispatchScheduled = false;
            }
            callbackToRun.run();
        });
    }
}
