package orbisoftware;

import javax.swing.*;
import java.awt.*;

public final class HelloWorldFactory {
    private HelloWorldFactory() {
    }

    public static JPanel buildFallbackPanel(String title, int width, int height, String detail) {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setPreferredSize(new Dimension(width, height));
        root.setBackground(new Color(245, 245, 245));

        JLabel header = new JLabel(title, SwingConstants.CENTER);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 20f));
        header.setBorder(BorderFactory.createEmptyBorder(16, 16, 0, 16));
        root.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(0, 2, 10, 10));
        center.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        center.add(new JLabel("Status:"));
        center.add(new JLabel("Jar could not be loaded"));
        center.add(new JLabel("Detail:"));
        center.add(new JTextField(detail == null ? "Unknown error" : detail));
        center.add(new JLabel("Action:"));
        center.add(new JButton("Retry"));
        center.add(new JLabel("Mode:"));
        center.add(new JComboBox<>(new String[]{"2D", "3D", "Fallback"}));
        center.add(new JLabel("Level:"));
        center.add(new JSlider(0, 100, 50));
        root.add(center, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(new JButton("Close"));
        root.add(south, BorderLayout.SOUTH);
        return root;
    }
}
