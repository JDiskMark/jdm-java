package jdiskmark;

import java.util.EnumMap;
import java.util.Map;
import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JRadioButtonMenuItem;

/**
 * Self-contained "Graph Palette" submenu built entirely from the
 * {@link Gui.Palette} enum.  Adding a new palette requires only:
 * <ol>
 *   <li>A new constant in {@link Gui.Palette} (with display name).</li>
 *   <li>A corresponding {@code apply()} implementation in the enum.</li>
 * </ol>
 * No changes to {@link MainFrame}, its {@code .form} file, or any
 * NetBeans-generated code are necessary.
 */
public class GraphPaletteMenu extends JMenu {

    private final Map<Gui.Palette, JRadioButtonMenuItem> items =
            new EnumMap<>(Gui.Palette.class);

    public GraphPaletteMenu() {
        super("Graph Palette");
        ButtonGroup group = new ButtonGroup();

        for (Gui.Palette p : Gui.Palette.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(p.displayName());
            group.add(item);
            item.addActionListener(e -> {
                p.apply();
                App.saveConfig();
            });
            add(item);
            items.put(p, item);
        }
    }

    /**
     * Selects the radio button matching the current palette and applies
     * the colour scheme.  Called from {@link MainFrame#refreshConfig()}.
     */
    public void syncFromModel() {
        Gui.Palette current = Gui.palette;
        JRadioButtonMenuItem item = items.get(current);
        if (item != null) {
            item.setSelected(true);
        }
        current.apply();
    }
}
