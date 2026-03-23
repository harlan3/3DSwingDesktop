package orbisoftware;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public final class JarSwingAppLoader {
    private JarSwingAppLoader() {
    }

    public static SwingJarAppHandle load(Path jarPath, int width, int height) {
        String displayName = jarPath.getFileName().toString();
        JPanel livePanel;
        URLClassLoader classLoader = null;
        try {
            JarMetadata metadata = readMetadata(jarPath.toFile());
            classLoader = buildClassLoader(jarPath, metadata);
            Object instance = instantiateMain(metadata.mainClassName, classLoader);
            Container contentPane = extractContentPane(instance);

            // Keep the embedded in-process Swing root hidden and non-focusable.
            // Minimizing it can also iconify the main launcher because it lives in the same JVM.
            if (instance instanceof Window window) {
                window.setAutoRequestFocus(false);
                window.setFocusableWindowState(false);
                if (window instanceof Frame frame) {
                    frame.setState(Frame.NORMAL);
                }
                if (window instanceof JFrame frame) {
                    frame.setExtendedState(JFrame.NORMAL);
                }
                window.setVisible(false);
            }

            displayName = instance.getClass().getSimpleName();
            livePanel = wrapContent(contentPane, width, height);
            return new SwingJarAppHandle(jarPath.toString(), displayName, livePanel,
                    new OffscreenSwingApp(livePanel, width, height), null, classLoader);
        } catch (Exception ex) {
            
            // Get the original exception that was wrapped
            Throwable cause = ex.getCause();
            System.err.println("Underlying cause: " + cause.getMessage());
            cause.printStackTrace(); // Print the stack trace of the original cause
            
            closeQuietly(classLoader);
            livePanel = HelloWorldFactory.buildFallbackPanel(displayName, width, height, ex.getMessage());
            return new SwingJarAppHandle(jarPath.toString(), displayName, livePanel,
                    new OffscreenSwingApp(livePanel, width, height), null, null);
        }
    }

    public static Process launch(Path jarPath) {
        try {
            String javaBin = Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString();
            return new ProcessBuilder(javaBin, "-jar", jarPath.toString())
                    .directory(jarPath.getParent() == null ? null : jarPath.getParent().toFile())
                    .start();
        } catch (IOException ex) {
            return null;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static JarMetadata readMetadata(File jarFile) throws IOException {
        try (JarFile jf = new JarFile(jarFile)) {
            Manifest manifest = jf.getManifest();
            if (manifest == null) {
                throw new IOException("Manifest not found: " + jarFile.getName());
            }
            Attributes attrs = manifest.getMainAttributes();
            String main = attrs.getValue("Main-Class");
            if (main == null || main.isBlank()) {
                throw new IOException("Main-Class not found in manifest: " + jarFile.getName());
            }
            String classPath = attrs.getValue("Class-Path");
            return new JarMetadata(main.trim(), classPath == null ? "" : classPath.trim());
        }
    }

    private static URLClassLoader buildClassLoader(Path jarPath, JarMetadata metadata) throws IOException {
        Set<URL> urls = new LinkedHashSet<>();
        urls.add(toUrl(jarPath));

        Path jarDir = jarPath.toAbsolutePath().getParent();
        if (jarDir != null && !metadata.classPathValue.isBlank()) {
            for (String entry : metadata.classPathValue.split("\\s+")) {
                if (entry == null || entry.isBlank()) {
                    continue;
                }
                Path resolved = jarDir.resolve(entry).normalize();
                if (Files.exists(resolved)) {
                    urls.add(toUrl(resolved));
                }
            }
        }

        return new URLClassLoader(urls.toArray(new URL[0]), JarSwingAppLoader.class.getClassLoader());
    }

    private static Object instantiateMain(String mainClassName, ClassLoader classLoader) throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(classLoader);
            Class<?> clazz = Class.forName(mainClassName, true, classLoader);
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private static URL toUrl(Path path) throws MalformedURLException {
        return path.toAbsolutePath().normalize().toUri().toURL();
    }

    private static Container extractContentPane(Object instance) {
        if (instance instanceof JFrame frame) {
            return frame.getContentPane();
        }
        if (instance instanceof JDialog dialog) {
            return dialog.getContentPane();
        }
        if (instance instanceof JPanel panel) {
            return panel;
        }
        throw new IllegalArgumentException("Unsupported Swing root type: " + instance.getClass().getName());
    }

    private static JPanel wrapContent(Container content, int width, int height) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(true);
        wrapper.setBackground(new Color(245, 245, 245));
        wrapper.setPreferredSize(new Dimension(width, height));
        wrapper.setSize(width, height);

        if (content instanceof JComponent jc) {
            jc.setPreferredSize(new Dimension(width, height));
            wrapper.add(jc, BorderLayout.CENTER);
        } else {
            JPanel host = new JPanel(new BorderLayout());
            host.add(content, BorderLayout.CENTER);
            wrapper.add(host, BorderLayout.CENTER);
        }
        wrapper.doLayout();
        return wrapper;
    }

    private static void closeQuietly(URLClassLoader classLoader) {
        if (classLoader == null) {
            return;
        }
        try {
            classLoader.close();
        } catch (IOException ignored) {
        }
    }

    private record JarMetadata(String mainClassName, String classPathValue) {
    }
}
