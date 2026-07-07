package jdiskmark;

import java.awt.Image;
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
