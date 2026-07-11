package jdiskmark;

import java.util.EnumMap;
import java.util.Map;
import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JRadioButtonMenuItem;

/**
 * Self-contained "Window Theme" submenu built entirely from the
 * {@link Gui.Theme} enum. Adding a new theme requires only:
 * <ol>
 *   <li>A new constant in {@link Gui.Theme} (with display name).</li>
 *   <li>A corresponding {@code apply()} implementation in the enum.</li>
 * </ol>
 * No changes to {@link MainFrame}, its {@code .form} file, or any
 * NetBeans-generated code are necessary.
 */
public class GraphThemeMenu extends JMenu {

    private final Map<Gui.Theme, JRadioButtonMenuItem> items =
            new EnumMap<>(Gui.Theme.class);

    public GraphThemeMenu() {
        super("Window Theme");
        ButtonGroup group = new ButtonGroup();

        for (Gui.Theme t : Gui.Theme.values()) {
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
