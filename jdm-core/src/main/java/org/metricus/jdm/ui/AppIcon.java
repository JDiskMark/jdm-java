package org.metricus.jdm.ui;

import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.ImageIcon;

/**
 * Branding icon variants for the application window, taskbar, and installer.
 * Each variant declares the PNG sizes available in jdm-core resources.
 * Change {@link #active} to switch the icon across all display contexts.
 */
public enum AppIcon {

    /** Blue/orange circle — the beta brand. Single resolution. */
    BETA(new String[] { "/icons/icon-jdm-beta.png" }),
    /** Custom JDiskMark turtle logo — optimized for Ubuntu. */
    TURTLE(new String[] {
            "/icons/jdm-turtle-logo-16x16.png",
            "/icons/jdm-turtle-logo-20x20.png",
            "/icons/jdm-turtle-logo-24x24.png",
            "/icons/jdm-turtle-logo-32x32.png",
            "/icons/jdm-turtle-logo-40x40.png",
            "/icons/jdm-turtle-logo-48x48.png",
            "/icons/jdm-turtle-logo-64x64.png",
            "/icons/jdm-turtle-logo-96x96.png",
            "/icons/jdm-turtle-logo-128x128.png",
            "/icons/jdm-turtle-logo-256x256.png",
            "/icons/jdm-turtle-logo-512x512.png",
            "/icons/jdm-turtle-logo-1024x1024.png"
    }),
    /** Duke, the BSD-licensed Java mascot from the OpenJDK project. */
    DUKE(new String[] { "/icons/icon-duke.png" });

    /**
     * Active branding icon — change this single line to switch the icon
     * used for the window title bar, taskbar, and About dialog.
     */
    public static AppIcon active = TURTLE;

    /** All resource paths for this icon variant, from smallest to largest. */
    public final String[] resourcePaths;

    AppIcon(String[] resourcePaths) {
        this.resourcePaths = resourcePaths;
    }

    /**
     * Load all available sizes as a list of Images for use with
     * {@link java.awt.Window#setIconImages(java.util.List)}.
     * Java picks the best-fit size per display context (title bar, taskbar, Alt+Tab).
     * Missing resources are silently skipped.
     *
     * @return list of images, never {@code null}
     */
    public List<Image> loadAll() {
        List<Image> images = new ArrayList<>();
        for (String path : resourcePaths) {
            try (InputStream is = AppIcon.class.getResourceAsStream(path)) {
                if (is != null) {
                    images.add(new ImageIcon(is.readAllBytes()).getImage());
                }
            } catch (IOException e) {
                Logger.getLogger(AppIcon.class.getName()).log(
                        Level.WARNING, "Could not load icon: " + path, e);
            }
        }
        return images;
    }

    /**
     * Load all available sizes as tinted images.
     * Dark ink pixels (mid-luminance) lerp toward {@code primary};
     * light body pixels (high-luminance) lerp toward {@code secondary};
     * the very dark background pixels are left unchanged.
     * If either color is {@code null}, returns plain {@link #loadAll()}.
     *
     * @param primary  tint for dark ink/outlines
     * @param secondary tint for light body fill
     * @return list of tinted images, never {@code null}
     */
    public List<Image> loadAllTinted(Color primary, Color secondary) {
        if (primary == null || secondary == null) return loadAll();
        List<Image> images = new ArrayList<>();
        for (String path : resourcePaths) {
            try (InputStream is = AppIcon.class.getResourceAsStream(path)) {
                if (is != null) {
                    BufferedImage src = javax.imageio.ImageIO.read(is);
                    if (src != null) images.add(tintImage(src, primary, secondary));
                }
            } catch (IOException e) {
                Logger.getLogger(AppIcon.class.getName()).log(
                        Level.WARNING, "Could not load tinted icon: " + path, e);
            }
        }
        return images.isEmpty() ? loadAll() : images;
    }

    /**
     * Load the largest available size as a tinted ImageIcon (used by the About dialog).
     * Falls back to {@link #load()} if tinting fails or either color is {@code null}.
     */
    public ImageIcon loadTinted(Color primary, Color secondary) {
        if (primary == null || secondary == null) return load();
        String path = resourcePaths[resourcePaths.length - 1];
        try (InputStream is = AppIcon.class.getResourceAsStream(path)) {
            if (is == null) return load();
            BufferedImage src = javax.imageio.ImageIO.read(is);
            if (src == null) return load();
            return new ImageIcon(tintImage(src, primary, secondary));
        } catch (IOException e) {
            Logger.getLogger(AppIcon.class.getName()).log(
                    Level.WARNING, "Could not tint icon: " + path, e);
            return load();
        }
    }

    /**
     * Load the best pre-rendered PNG at or nearest to {@code targetSize} pixels,
     * then apply a two-tone luminance tint.
     * Falls back to {@link #loadSize(int)} if tinting fails or either color is {@code null}.
     */
    public ImageIcon loadSizeTinted(int targetSize, Color primary, Color secondary) {
        if (primary == null || secondary == null) return loadSize(targetSize);
        Pattern sizePattern = Pattern.compile("-(\\d+)x\\d+\\.png$");
        String bestPath = resourcePaths[resourcePaths.length - 1];
        int bestDiff = Integer.MAX_VALUE;
        for (String path : resourcePaths) {
            Matcher m = sizePattern.matcher(path);
            if (m.find()) {
                int size = Integer.parseInt(m.group(1));
                int diff = size - targetSize;
                if (diff >= 0 && diff < bestDiff) {
                    bestDiff = diff;
                    bestPath = path;
                }
            }
        }
        try (InputStream is = AppIcon.class.getResourceAsStream(bestPath)) {
            if (is == null) return loadSize(targetSize);
            BufferedImage src = javax.imageio.ImageIO.read(is);
            if (src == null) return loadSize(targetSize);
            return new ImageIcon(tintImage(src, primary, secondary));
        } catch (IOException e) {
            Logger.getLogger(AppIcon.class.getName()).log(
                    Level.WARNING, "Could not tint icon: " + bestPath, e);
            return loadSize(targetSize);
        }
    }

    /**
     * Applies a two-tone luminance-based tint to a {@link BufferedImage}.
     * Three luminance zones:
     * <ul>
     *   <li>&lt; 0.12 — very dark background: left unchanged</li>
     *   <li>0.12 – 0.65 — dark ink/outlines: lerp toward {@code primary}</li>
     *   <li>&gt; 0.65 — light body fill: lerp toward {@code secondary}</li>
     * </ul>
     */
    private static Image tintImage(BufferedImage src, Color primary, Color secondary) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int pr = primary.getRed(),   pg = primary.getGreen(),   pb = primary.getBlue();
        int sr = secondary.getRed(), sg = secondary.getGreen(), sb = secondary.getBlue();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb  = src.getRGB(x, y);
                int alpha = (argb >> 24) & 0xFF;
                int r     = (argb >> 16) & 0xFF;
                int g     = (argb >>  8) & 0xFF;
                int b     =  argb        & 0xFF;
                // sRGB luminance (perceptual)
                double lum = 0.2126 * r / 255.0 + 0.7152 * g / 255.0 + 0.0722 * b / 255.0;
                int nr, ng, nb;
                if (lum < 0.12) {
                    // very dark background — leave alone
                    nr = r; ng = g; nb = b;
                } else if (lum <= 0.65) {
                    // dark ink zone — lerp pixel toward primary
                    // t=0 at lum=0.12 (darkest ink), t=1 at lum=0.65 (lightest ink edge)
                    double t = (lum - 0.12) / (0.65 - 0.12);
                    double strength = 0.85; // max blend factor
                    double blend = strength * (1.0 - t * 0.3); // darker ink blends more strongly
                    nr = clamp((int)(r + blend * (pr - r)));
                    ng = clamp((int)(g + blend * (pg - g)));
                    nb = clamp((int)(b + blend * (pb - b)));
                } else {
                    // light body zone — lerp pixel toward secondary
                    double t = (lum - 0.65) / (1.0 - 0.65);
                    double blend = 0.55 * t; // gentle tint on white areas
                    nr = clamp((int)(r + blend * (sr - r)));
                    ng = clamp((int)(g + blend * (sg - g)));
                    nb = clamp((int)(b + blend * (sb - b)));
                }
                out.setRGB(x, y, (alpha << 24) | (nr << 16) | (ng << 8) | nb);
            }
        }
        return out;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /**
     * Load the largest available size as an ImageIcon (used by the About dialog).
     * Returns {@code null} if no resource is found.
     *
     * @return ImageIcon, or {@code null}
     */
    public ImageIcon load() {
        String path = resourcePaths[resourcePaths.length - 1];
        try (InputStream is = AppIcon.class.getResourceAsStream(path)) {
            if (is == null) {
                Logger.getLogger(AppIcon.class.getName()).log(
                        Level.WARNING, "Icon resource not found: {0}", path);
                return null;
            }
            return new ImageIcon(is.readAllBytes());
        } catch (IOException e) {
            Logger.getLogger(AppIcon.class.getName()).log(
                    Level.WARNING, "Could not load icon: " + path, e);
            return null;
        }
    }

    /**
     * Load the best pre-rendered PNG at or nearest to {@code targetSize}
     * pixels. Prefers the smallest size that is &gt;= targetSize; falls
     * back to the largest available. For single-resolution variants the
     * only image is returned as-is. Returns {@code null} if no resource is
     * found.
     *
     * @param targetSize desired pixel width
     * @return ImageIcon, or {@code null}
     */
    public ImageIcon loadSize(int targetSize) {
        // Parse pixel widths from filenames like "/icons/jdm-turtle-logo-256x256.png".
        // For paths without a size suffix (e.g. "/icons/icon-jdm-beta.png") the regex
        // won't match and the path is treated as an unknown size.
        Pattern sizePattern = Pattern.compile("-(\\d+)x\\d+\\.png$");
        String bestPath = resourcePaths[resourcePaths.length - 1]; // default: largest
        int bestDiff = Integer.MAX_VALUE;
        for (String path : resourcePaths) {
            Matcher m = sizePattern.matcher(path);
            if (m.find()) {
                int size = Integer.parseInt(m.group(1));
                int diff = size - targetSize;
                // Prefer smallest size >= targetSize; accept smaller only if nothing larger found.
                if (diff >= 0 && diff < bestDiff) {
                    bestDiff = diff;
                    bestPath = path;
                }
            }
        }
        try (InputStream is = AppIcon.class.getResourceAsStream(bestPath)) {
            if (is == null) {
                Logger.getLogger(AppIcon.class.getName()).log(
                        Level.WARNING, "Icon resource not found: {0}", bestPath);
                return null;
            }
            return new ImageIcon(is.readAllBytes());
        } catch (IOException e) {
            Logger.getLogger(AppIcon.class.getName()).log(
                    Level.WARNING, "Could not load icon: " + bestPath, e);
            return null;
        }
    }
}
