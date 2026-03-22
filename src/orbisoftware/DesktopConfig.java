package orbisoftware;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class DesktopConfig {
    public static final String TYPE_SWING = "Swing";
    public static final String TYPE_SWT = "SWT";
    public static final String TYPE_WIN32 = "Win32";

    public final int numberDesktops;
    public final List<DesktopDefinition> desktops;

    public DesktopConfig(int numberDesktops, List<DesktopDefinition> desktops) {
        this.numberDesktops = numberDesktops;
        this.desktops = desktops;
    }

    public static DesktopConfig load(InputStream in) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
        doc.getDocumentElement().normalize();

        Element root = doc.getDocumentElement();
        int numberDesktops = Integer.parseInt(childText(root, "numberDesktops", "1"));
        List<DesktopDefinition> desktops = new ArrayList<>();

        for (int i = 1; i <= numberDesktops; i++) {
            Element desktopEl = directChild(root, "desktop" + i);
            if (desktopEl == null) {
                continue;
            }

            String desktopWindowType = normalizeType(childText(desktopEl, "desktopWindowType", TYPE_SWING));
            boolean desktopAnimSupport = yesNo(childText(desktopEl, "animSupport", "no"));

            List<JavaWindowAppDefinition> apps = new ArrayList<>();
            for (Element appEl : directChildren(desktopEl, "javaWindowsApp")) {
                List<String> classNames = new ArrayList<>();
                for (Element classEl : directChildren(appEl, "javaClassName")) {
                    String className = classEl.getTextContent().trim();
                    if (!className.isEmpty()) {
                        classNames.add(className);
                    }
                }

                List<String> jarFiles = new ArrayList<>();
                for (Element jarEl : directChildren(appEl, "jarFile")) {
                    String jarFile = jarEl.getTextContent().trim();
                    if (!jarFile.isEmpty()) {
                        jarFiles.add(jarFile);
                    }
                }

                apps.add(new JavaWindowAppDefinition(
                        Integer.parseInt(childText(appEl, "windowIndex", String.valueOf(apps.size() + 1))),
                        childText(appEl, "projectionShape", "na"),
                        Integer.parseInt(childText(appEl, "width", "640")),
                        Integer.parseInt(childText(appEl, "height", "480")),
                        Integer.parseInt(childText(appEl, "x", "0")),
                        Integer.parseInt(childText(appEl, "y", "0")),
                        Integer.parseInt(childText(appEl, "tilt", "65")),
                        Double.parseDouble(childText(appEl, "size", "1.0")),
                        classNames,
                        jarFiles
                ));
            }

            desktops.add(new DesktopDefinition(i - 1, desktopWindowType, desktopAnimSupport, apps));
        }

        return new DesktopConfig(numberDesktops, desktops);
    }

    private static boolean yesNo(String value) {
        return "yes".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
    }

    private static String normalizeType(String rawType) {
        if (rawType == null) {
            return TYPE_SWING;
        }
        if (TYPE_SWT.equalsIgnoreCase(rawType)) {
            return TYPE_SWT;
        }
        if (TYPE_WIN32.equalsIgnoreCase(rawType)) {
            return TYPE_WIN32;
        }
        return TYPE_SWING;
    }

    private static String childText(Element parent, String tag, String defaultValue) {
        Element child = directChild(parent, tag);
        return child == null ? defaultValue : child.getTextContent().trim();
    }

    private static Element directChild(Element parent, String tag) {
        NodeList nl = parent.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node node = nl.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tag.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }

    private static List<Element> directChildren(Element parent, String tag) {
        List<Element> result = new ArrayList<>();
        NodeList nl = parent.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node node = nl.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tag.equals(node.getNodeName())) {
                result.add((Element) node);
            }
        }
        return result;
    }

    public record DesktopDefinition(
            int index,
            String desktopWindowType,
            boolean animSupport,
            List<JavaWindowAppDefinition> apps) {

        public boolean isSwingDesktop() { return TYPE_SWING.equalsIgnoreCase(desktopWindowType); }
        public boolean isSwtDesktop() { return TYPE_SWT.equalsIgnoreCase(desktopWindowType); }
        public boolean isWin32Desktop() { return TYPE_WIN32.equalsIgnoreCase(desktopWindowType); }
    }

    public record JavaWindowAppDefinition(
            int windowIndex,
            String projectionShape,
            int width,
            int height,
            int x,
            int y,
            int tilt,
            double size,
            List<String> javaClassNames,
            List<String> jarFiles) {

        public boolean hasJarFiles() { return jarFiles != null && !jarFiles.isEmpty(); }
    }
}
