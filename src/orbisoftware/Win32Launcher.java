package orbisoftware;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;

import java.awt.Rectangle;
import java.io.IOException;

public class Win32Launcher {
    private static final int SW_RESTORE = 9;
    private static final int SWP_NOZORDER = 0x0004;
    private static final int SWP_NOACTIVATE = 0x0010;
    private static final int SWP_SHOWWINDOW = 0x0040;

    private final String[] command;
    private final String windowTitle;
    private Process process;
    private volatile HWND cachedWindowHandle;

    public Win32Launcher(String[] command, String windowTitle) {
        this.command = command;
        this.windowTitle = windowTitle;
    }

    public void launch() {
        try {
            process = new ProcessBuilder(command).start();
            cachedWindowHandle = null;
        } catch (IOException e) {
            throw new RuntimeException("Unable to launch " + String.join(" ", command), e);
        }
    }

    public void focusWindow() {
        HWND hwnd = getWindowHandle();
        if (hwnd != null) {
            User32.INSTANCE.ShowWindow(hwnd, SW_RESTORE);
            User32.INSTANCE.SetForegroundWindow(hwnd);
        }
    }

    public void positionWindow(Rectangle bounds) {
        HWND hwnd = getWindowHandle();
        if (hwnd != null && bounds != null) {
            User32.INSTANCE.ShowWindow(hwnd, SW_RESTORE);
            User32.INSTANCE.SetWindowPos(
                    hwnd,
                    null,
                    bounds.x,
                    bounds.y,
                    bounds.width,
                    bounds.height,
                    SWP_NOZORDER | SWP_NOACTIVATE | SWP_SHOWWINDOW
            );
            bringToFront(hwnd);
        }
    }

    public boolean isAlive() {
        return process != null && process.isAlive();
    }

    public static void bringToFront(HWND hwnd) {
        User32.INSTANCE.SetForegroundWindow(hwnd);
    }
    
    public void minimizeWindow() {
    	HWND hwnd = getWindowHandle();
        User32.INSTANCE.ShowWindow(hwnd, User32.SW_MINIMIZE);
    }

    public HWND getWindowHandle() {
        HWND hwnd = cachedWindowHandle;
        if (isUsableWindow(hwnd)) {
            return hwnd;
        }

        hwnd = findWindowHandleByProcess();
        if (hwnd != null) {
            cachedWindowHandle = hwnd;
            return hwnd;
        }

        if (windowTitle != null && !windowTitle.isBlank()) {
            hwnd = findWindowHandleByTitle();
            if (hwnd != null) {
                cachedWindowHandle = hwnd;
                return hwnd;
            }
        }
        return null;
    }

    private HWND findWindowHandleByProcess() {
        if (process == null) {
            return null;
        }

        long pidLong = process.pid();
        if (pidLong <= 0L || pidLong > Integer.MAX_VALUE) {
            return null;
        }
        final int targetPid = (int) pidLong;

        for (int attempt = 0; attempt < 40; attempt++) { // Poll up to ~2 seconds (40 * 50ms)
            final HWND[] result = new HWND[1];
            User32.INSTANCE.EnumWindows(new WinUser.WNDENUMPROC() {
                @Override
                public boolean callback(HWND hwnd, Pointer data) {
                    if (!isCandidateTopLevelWindow(hwnd)) {
                        return true;
                    }

                    IntByReference pidRef = new IntByReference();
                    User32.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef);
                    if (pidRef.getValue() != targetPid) {
                        return true;
                    }

                    result[0] = hwnd;
                    return false;
                }
            }, null);

            if (result[0] != null) {
                return result[0];
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private HWND findWindowHandleByTitle() {
        for (int i = 0; i < 20; i++) {
            HWND hwnd = User32.INSTANCE.FindWindow(null, windowTitle);
            if (isUsableWindow(hwnd)) {
                return hwnd;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private boolean isCandidateTopLevelWindow(HWND hwnd) {
        if (hwnd == null || !User32.INSTANCE.IsWindow(hwnd) || !User32.INSTANCE.IsWindowVisible(hwnd)) {
            return false;
        }
        HWND owner = User32.INSTANCE.GetWindow(hwnd, new WinDef.DWORD(WinUser.GW_OWNER));
        if (owner != null) {
            return false;
        }
        char[] title = new char[512];
        int len = User32.INSTANCE.GetWindowText(hwnd, title, title.length);
        return len > 0;
    }

    public void closeWindow() {
        if (process == null) {
            return;
        }

        try {
            HWND hwnd = getWindowHandle();
            if (hwnd != null) {
                User32.INSTANCE.PostMessage(hwnd, WinUser.WM_CLOSE, null, null);
            }
        } catch (Exception ignored) {
        }

        try {
            process.waitFor(1000, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (process.isAlive()) {
            process.destroy();

            try {
                process.waitFor(1000, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }
    
    private boolean isUsableWindow(HWND hwnd) {
        return hwnd != null && User32.INSTANCE.IsWindow(hwnd);
    }
}
