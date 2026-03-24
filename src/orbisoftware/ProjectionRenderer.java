package orbisoftware;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ProjectionRenderer {
	
	private static boolean displayFPS = false;
	private static long lastFPScheck = System.nanoTime();
	private static int totalFrames = 0;
	private static int currentFPS = 0;
    private static final int PLANE_COLS = 28;
    private static final int PLANE_ROWS = 14;
    
    private ProjectionRenderer() {
    }
    
    public static void drawTilted(Graphics2D g2, BufferedImage img, Rectangle bounds, double tilt, double sizeScale, boolean left) {
        Point2D.Double[] quad = buildTiltedQuad(bounds.x, bounds.y, bounds.width, bounds.height, tilt, sizeScale, left);

        Polygon poly = new Polygon();
        for (Point2D.Double pt : quad) {
            poly.addPoint((int) Math.round(pt.x), (int) Math.round(pt.y));
        }

        g2.setColor(new Color(0, 0, 0, 70));
        Polygon shadow = new Polygon();
        for (Point2D.Double pt : quad) {
            shadow.addPoint((int) Math.round(pt.x + 12), (int) Math.round(pt.y + 10));
        }
        g2.fillPolygon(shadow);

        drawImageOnQuad(g2, img, quad);

        g2.setColor(new Color(255, 255, 255, 40));
        g2.fillPolygon(poly);
        g2.setColor(new Color(80, 80, 100, 180));
        g2.drawPolygon(poly);
    }
    
    private static void drawImageOnQuad(Graphics2D g2, BufferedImage img, Point2D.Double[] quad) {
        Shape oldClip = g2.getClip();
        Polygon poly = new Polygon();
        for (Point2D.Double pt : quad) {
            poly.addPoint((int) Math.round(pt.x), (int) Math.round(pt.y));
        }
        g2.setClip(poly);

        int srcW = img.getWidth();
        int srcH = img.getHeight();
        for (int row = 0; row < PLANE_ROWS; row++) {
            double v0 = row / (double) PLANE_ROWS;
            double v1 = (row + 1) / (double) PLANE_ROWS;
            for (int col = 0; col < PLANE_COLS; col++) {
                double u0 = col / (double) PLANE_COLS;
                double u1 = (col + 1) / (double) PLANE_COLS;

                Point2D.Double p00 = bilinear(quad, u0, v0);
                Point2D.Double p10 = bilinear(quad, u1, v0);
                Point2D.Double p11 = bilinear(quad, u1, v1);
                Point2D.Double p01 = bilinear(quad, u0, v1);

                double sx0 = u0 * srcW;
                double sy0 = v0 * srcH;
                double sx1 = u1 * srcW;
                double sy1 = v1 * srcH;

                drawImageTriangle(g2, img,
                        sx0, sy0, sx1, sy0, sx1, sy1,
                        p00.x, p00.y, p10.x, p10.y, p11.x, p11.y);
                drawImageTriangle(g2, img,
                        sx0, sy0, sx1, sy1, sx0, sy1,
                        p00.x, p00.y, p11.x, p11.y, p01.x, p01.y);
            }
        }

        g2.setClip(oldClip);
    }
    
    private static Point2D.Double bilinear(Point2D.Double[] q, double u, double v) {
        double x = (1 - u) * (1 - v) * q[0].x + u * (1 - v) * q[1].x + u * v * q[2].x + (1 - u) * v * q[3].x;
        double y = (1 - u) * (1 - v) * q[0].y + u * (1 - v) * q[1].y + u * v * q[2].y + (1 - u) * v * q[3].y;
        return new Point2D.Double(x, y);
    }
    
	private static void drawImageTriangle(Graphics2D g2, BufferedImage img, double sx1, double sy1, double sx2,
			double sy2, double sx3, double sy3, double dx1, double dy1, double dx2, double dy2, double dx3,
			double dy3) {
		Shape oldClip = g2.getClip();
		Path2D.Double clip = new Path2D.Double();
		clip.moveTo(dx1, dy1);
		clip.lineTo(dx2, dy2);
		clip.lineTo(dx3, dy3);
		clip.closePath();
		g2.clip(clip);

		double det = sx1 * (sy2 - sy3) + sx2 * (sy3 - sy1) + sx3 * (sy1 - sy2);
		if (Math.abs(det) < 1e-6) {
			g2.setClip(oldClip);
			return;
		}

		double m00 = (dx1 * (sy2 - sy3) + dx2 * (sy3 - sy1) + dx3 * (sy1 - sy2)) / det;
		double m01 = (dx1 * (sx3 - sx2) + dx2 * (sx1 - sx3) + dx3 * (sx2 - sx1)) / det;
		double m02 = (dx1 * (sx2 * sy3 - sx3 * sy2) + dx2 * (sx3 * sy1 - sx1 * sy3) + dx3 * (sx1 * sy2 - sx2 * sy1))
				/ det;

		double m10 = (dy1 * (sy2 - sy3) + dy2 * (sy3 - sy1) + dy3 * (sy1 - sy2)) / det;
		double m11 = (dy1 * (sx3 - sx2) + dy2 * (sx1 - sx3) + dy3 * (sx2 - sx1)) / det;
		double m12 = (dy1 * (sx2 * sy3 - sx3 * sy2) + dy2 * (sx3 * sy1 - sx1 * sy3) + dy3 * (sx1 * sy2 - sx2 * sy1))
				/ det;

		AffineTransform at = new AffineTransform(m00, m10, m01, m11, m02, m12);
		g2.drawImage(img, at, null);
		g2.setClip(oldClip);
	}
 
    private static Point2D.Double[] buildTiltedQuad(int x, int y, int w, int h, double tilt, double sizeScale, boolean left) {
        double t = Math.max(0.0, Math.min(85.0, tilt));
        double strength = Math.sin(Math.toRadians(t));
        double scale = Math.max(0.05, sizeScale);

        double scaledW = Math.max(1.0, w * scale);
        double scaledH = Math.max(1.0, h * scale);
        double offsetX = (w - scaledW) * 0.5;
        double offsetY = (h - scaledH) * 0.5;
        double sx = x + offsetX;
        double sy = y + offsetY;

        // Treat the XML tilt as a rotation around the vertical (Y) axis.
        // The hinged side stays fixed and the opposite side contracts inward
        // along X so the GUI appears to recede toward a vanishing point.
        double contractedWidth = Math.max(24.0, scaledW * Math.cos(Math.toRadians(t)));
        double inset = Math.max(0.0, scaledW - contractedWidth);

        // Add a small center pull so the free edge shifts toward the visual center
        // of the panel while keeping both vertical edges upright.
        double centerDrift = inset * 0.10 * strength;

        if (left) {
            double farX = sx + scaledW - inset;
            return new Point2D.Double[] {
                    new Point2D.Double(sx, sy),
                    new Point2D.Double(farX, sy + centerDrift),
                    new Point2D.Double(farX, sy + scaledH - centerDrift),
                    new Point2D.Double(sx, sy + scaledH)
            };
        }

        double nearX = sx + inset;
        return new Point2D.Double[] {
                new Point2D.Double(nearX, sy + centerDrift),
                new Point2D.Double(sx + scaledW, sy),
                new Point2D.Double(sx + scaledW, sy + scaledH),
                new Point2D.Double(nearX, sy + scaledH - centerDrift)
        };
    }
    
    public static void drawCube(Graphics2D g,
                                BufferedImage[] faces,
                                Rectangle bounds,
                                int frontFaceIndex,
                                double sizeScale,
                                double yawDeg,
                                double pitchDeg,
                                double zoom,
                                boolean interactive) {
        if (faces == null || faces.length == 0) {
            return;
        }

        double side = Math.min(bounds.width, bounds.height) * 0.34 * sizeScale * zoom;
        double cx = bounds.getCenterX();
        double cy = bounds.getCenterY();
        double cameraDistance = side * 4.5;

        Vec3[] vertices = new Vec3[]{
                new Vec3(-side, -side, -side), // 0
                new Vec3(side, -side, -side),  // 1
                new Vec3(side, side, -side),   // 2
                new Vec3(-side, side, -side),  // 3
                new Vec3(-side, -side, side),  // 4
                new Vec3(side, -side, side),   // 5
                new Vec3(side, side, side),    // 6
                new Vec3(-side, side, side)    // 7
        };

        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);

        Vec3[] rotated = new Vec3[vertices.length];
        Point[] projected = new Point[vertices.length];

        for (int i = 0; i < vertices.length; i++) {
            Vec3 v = rotateY(vertices[i], yaw);
            v = rotateX(v, pitch);
            rotated[i] = v;
            projected[i] = project(v, cx, cy, cameraDistance);
        }

        int[][] faceVerts = new int[][]{
                {4, 5, 6, 7}, // front  (+z)
                {1, 0, 3, 2}, // back   (-z)
                {0, 4, 7, 3}, // left   (-x)
                {5, 1, 2, 6}, // right  (+x)
                {0, 1, 5, 4}, // top    (-y)
                {7, 6, 2, 3}  // bottom (+y)
        };

        Vec3[] faceNormals = new Vec3[]{
                new Vec3(0, 0, 1),
                new Vec3(0, 0, -1),
                new Vec3(-1, 0, 0),
                new Vec3(1, 0, 0),
                new Vec3(0, -1, 0),
                new Vec3(0, 1, 0)
        };

        List<RenderedFace> visibleFaces = new ArrayList<>();
        for (int i = 0; i < faceVerts.length; i++) {
            Vec3 normal = rotateY(faceNormals[i], yaw);
            normal = rotateX(normal, pitch);

            // Camera sits at negative Z looking toward positive Z,
            // so visible faces point generally toward -Z.
            if (normal.z >= -0.02) {
                continue;
            }

            Point[] quad = new Point[]{
                    projected[faceVerts[i][0]],
                    projected[faceVerts[i][1]],
                    projected[faceVerts[i][2]],
                    projected[faceVerts[i][3]]
            };

            int imageIndex = (frontFaceIndex + i) % faces.length;
            if (imageIndex < 0) {
                imageIndex += faces.length;
            }

            double avgZ = (rotated[faceVerts[i][0]].z
                    + rotated[faceVerts[i][1]].z
                    + rotated[faceVerts[i][2]].z
                    + rotated[faceVerts[i][3]].z) / 4.0;

            boolean blackFace = (i == 4 || i == 5); // top and bottom

            visibleFaces.add(new RenderedFace(
                    quad,
                    blackFace ? null : faces[imageIndex],
                    avgZ,
                    blackFace ? 1.0f : brightnessFor(normal),
                    i,
                    blackFace
            ));
        }

        visibleFaces.sort(Comparator.comparingDouble((RenderedFace f) -> f.avgZ).reversed());

        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Object oldInterp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                interactive ? RenderingHints.VALUE_ANTIALIAS_OFF : RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                interactive ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                        : RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        for (RenderedFace f : visibleFaces) {
            if (f.blackFace) {
                fillBlackFace(g, f.quad);
            } else {
                drawImageToQuadOriented(g, f.image, f.quad, f.faceIndex);
                shade(g, f.quad, f.brightness);
            }
            if (!interactive) {
                drawOutline(g, f.quad);
            }
        }

        if (oldAA != null) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
        } else {
            g.getRenderingHints().remove(RenderingHints.KEY_ANTIALIASING);
        }

        if (oldInterp != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterp);
        } else {
            g.getRenderingHints().remove(RenderingHints.KEY_INTERPOLATION);
        }
        
        // Calculate frames per second
        totalFrames++;
        if (System.nanoTime() - lastFPScheck >= 1000000000) {
            currentFPS = totalFrames;
            totalFrames = 0;
            lastFPScheck = System.nanoTime();
            if (displayFPS)
            	System.out.println("FPS: " + currentFPS);
        }
    }

    public static void drawImageToQuadOriented(Graphics2D g,
                                               BufferedImage image,
                                               Point[] quad,
                                               int faceIndex) {
        if (image == null || quad == null || quad.length != 4) {
            return;
        }

        int cols = Math.max(18, image.getWidth() / 24);
        int rows = Math.max(12, image.getHeight() / 24);

        for (int row = 0; row < rows; row++) {
            double v0 = row / (double) rows;
            double v1 = (row + 1) / (double) rows;

            for (int col = 0; col < cols; col++) {
                double u0 = col / (double) cols;
                double u1 = (col + 1) / (double) cols;

                UV a = mapFaceUv(faceIndex, u0, v0);
                UV b = mapFaceUv(faceIndex, u1, v0);
                UV c = mapFaceUv(faceIndex, u1, v1);
                UV d = mapFaceUv(faceIndex, u0, v1);

                Point p00 = bilerp(quad, u0, v0);
                Point p10 = bilerp(quad, u1, v0);
                Point p11 = bilerp(quad, u1, v1);
                Point p01 = bilerp(quad, u0, v1);

                double minU = Math.min(Math.min(a.u, b.u), Math.min(c.u, d.u));
                double maxU = Math.max(Math.max(a.u, b.u), Math.max(c.u, d.u));
                double minV = Math.min(Math.min(a.v, b.v), Math.min(c.v, d.v));
                double maxV = Math.max(Math.max(a.v, b.v), Math.max(c.v, d.v));

                int sx0 = Math.min(image.getWidth() - 1, Math.max(0, (int) Math.floor(minU * image.getWidth())));
                int sx1 = Math.min(image.getWidth(), Math.max(sx0 + 1, (int) Math.ceil(maxU * image.getWidth())));
                int sy0 = Math.min(image.getHeight() - 1, Math.max(0, (int) Math.floor(minV * image.getHeight())));
                int sy1 = Math.min(image.getHeight(), Math.max(sy0 + 1, (int) Math.ceil(maxV * image.getHeight())));

                BufferedImage tile = image.getSubimage(
                        sx0,
                        sy0,
                        Math.max(1, sx1 - sx0),
                        Math.max(1, sy1 - sy0)
                );

                Polygon poly = new Polygon(
                        new int[]{p00.x, p10.x, p11.x, p01.x},
                        new int[]{p00.y, p10.y, p11.y, p01.y},
                        4
                );

                Rectangle bounds = poly.getBounds();
                if (bounds.width <= 0 || bounds.height <= 0) {
                    continue;
                }

                Shape oldClip = g.getClip();
                g.setClip(poly);
                g.drawImage(image,
                        bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height,
                        sx0, sy0, sx1, sy1,
                        null);
                g.setClip(oldClip);
            }
        }
    }

    private static void fillBlackFace(Graphics2D g, Point[] quad) {
        Path2D path = new Path2D.Double();
        path.moveTo(quad[0].x, quad[0].y);
        for (int i = 1; i < quad.length; i++) {
            path.lineTo(quad[i].x, quad[i].y);
        }
        path.closePath();

        g.setColor(Color.BLACK);
        g.fill(path);
    }

    private static UV mapFaceUv(int faceIndex, double u, double v) {
        return switch (faceIndex) {
            case 0 -> new UV(1.0 - u, v);         // front
            case 1 -> new UV(1.0 - u, v);         // back
            case 2 -> new UV(1.0 - u, v);         // left
            case 3 -> new UV(1.0 - u, v);         // right
            case 4 -> new UV(v, 1.0 - u);         // top
            case 5 -> new UV(1.0 - v, u);         // bottom
            default -> new UV(u, v);
        };
    }

    private static Point bilerp(Point[] quad, double u, double v) {
        double topX = quad[0].x + (quad[1].x - quad[0].x) * u;
        double topY = quad[0].y + (quad[1].y - quad[0].y) * u;

        double botX = quad[3].x + (quad[2].x - quad[3].x) * u;
        double botY = quad[3].y + (quad[2].y - quad[3].y) * u;

        int x = (int) Math.round(topX + (botX - topX) * v);
        int y = (int) Math.round(topY + (botY - topY) * v);

        return new Point(x, y);
    }

    private static float brightnessFor(Vec3 normal) {
        Vec3 n = normalize(normal);
        Vec3 light = normalize(new Vec3(-0.35, -0.30, -1.0));

        double lambert = dot(n, light);
        double brightness = 0.85 + 0.15 * Math.max(0.0, lambert);

        return (float) Math.max(0.78, Math.min(1.0, brightness));
    }

    private static void shade(Graphics2D g, Point[] quad, float brightness) {
        float darken = Math.max(0.0f, (1.0f - brightness) * 0.18f);
        if (darken <= 0.001f) {
            return;
        }

        Composite old = g.getComposite();
        Path2D path = new Path2D.Double();
        path.moveTo(quad[0].x, quad[0].y);
        for (int i = 1; i < quad.length; i++) {
            path.lineTo(quad[i].x, quad[i].y);
        }
        path.closePath();

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, darken));
        g.setColor(Color.BLACK);
        g.fill(path);
        g.setComposite(old);
    }

    private static void drawOutline(Graphics2D g, Point[] quad) {
        Path2D path = new Path2D.Double();
        path.moveTo(quad[0].x, quad[0].y);
        for (int i = 1; i < quad.length; i++) {
            path.lineTo(quad[i].x, quad[i].y);
        }
        path.closePath();

        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(40, 40, 40, 90));
        g.draw(path);
        g.setStroke(old);
    }

    private static Vec3 rotateY(Vec3 v, double angle) {
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        return new Vec3(
                v.x * c + v.z * s,
                v.y,
                -v.x * s + v.z * c
        );
    }

    private static Vec3 rotateX(Vec3 v, double angle) {
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        return new Vec3(
                v.x,
                v.y * c - v.z * s,
                v.y * s + v.z * c
        );
    }

    private static Point project(Vec3 v, double cx, double cy, double cameraDistance) {
        double z = v.z + cameraDistance;
        double scale = cameraDistance / Math.max(1.0, z);

        int px = (int) Math.round(cx + v.x * scale);
        int py = (int) Math.round(cy + v.y * scale);

        return new Point(px, py);
    }

    private static double dot(Vec3 a, Vec3 b) {
        return a.x * b.x + a.y * b.y + a.z * b.z;
    }

    private static Vec3 normalize(Vec3 v) {
        double len = Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z);
        if (len < 1e-9) {
            return new Vec3(0, 0, 1);
        }
        return new Vec3(v.x / len, v.y / len, v.z / len);
    }

    private record Vec3(double x, double y, double z) {
    }

    private record UV(double u, double v) {
    }

    private record RenderedFace(Point[] quad,
                                BufferedImage image,
                                double avgZ,
                                float brightness,
                                int faceIndex,
                                boolean blackFace) {
    }
}