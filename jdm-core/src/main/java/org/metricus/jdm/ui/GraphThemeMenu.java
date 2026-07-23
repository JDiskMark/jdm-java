package org.metricus.jdm.ui;

import java.util.EnumMap;
import java.util.Map;
import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JRadioButtonMenuItem;
import jdiskmark.App;
import jdiskmark.Gui;

/**
 * Self-contained "Window Theme" submenu built entirely from the
 * {@link Theme} enum. Adding a new theme requires only:
 * <ol>
 *   <li>A new constant in {@link Theme} (with display name).</li>
 *   <li>A corresponding {@code apply()} implementation in the enum.</li>
 * </ol>
 * No changes to {@link MainFrame}, its {@code .form} file, or any
 * NetBeans-generated code are necessary.
 */
public class GraphThemeMenu extends JMenu {

    private final Map<Theme, JRadioButtonMenuItem> items =
            new EnumMap<>(Theme.class);

    public GraphThemeMenu() {
        super("Window Theme");
        ButtonGroup group = new ButtonGroup();
        boolean separatorAdded = false;

        for (Theme t : Theme.values()) {
            // Insert a visual divider between the standard FlatLaf themes
            // (Dark, Light, Darcula) and the custom branded themes.
            // hasLinkedPalette() is true for exactly the branded themes and
            // serves as the natural partition — no separate flag needed.
            if (t.hasLinkedPalette() && !separatorAdded) {
                addSeparator();
                separatorAdded = true;
            }
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(t.displayName());
            group.add(item);
            item.addActionListener(e -> {
                Gui.theme = t;
                t.apply();
                App.saveConfig();
            });
            add(item);
            items.put(t, item);
        }
    }

    /**
     * Selects the radio button matching the current theme.
     * Called from {@link MainFrame#loadPropertiesConfig()}.
     */
    public void syncFromModel() {
        JRadioButtonMenuItem item = items.get(Gui.theme);
        if (item != null) {
            item.setSelected(true);
        }
    }
}
