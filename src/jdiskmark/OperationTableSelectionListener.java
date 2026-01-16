package jdiskmark;

import javax.swing.JTable;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import static jdiskmark.BenchmarkPanel.START_TIME_COLUMN;

public class OperationTableSelectionListener implements ListSelectionListener {
    final private JTable table;

    public OperationTableSelectionListener(JTable table) {
        this.table = table;
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        // This prevents the event from firing twice (once for the old selection, once for the new)
        if (e.getValueIsAdjusting()) {
            return;
        }
        
        int selectedRow = table.getSelectedRow();
        if (selectedRow != -1) {
            String timeString = (String) table.getValueAt(selectedRow, START_TIME_COLUMN);
            System.out.println("sel op start=" + timeString);
            BenchmarkOperation operation = App.operations.get(timeString);
            if (operation != null) {
                Gui.loadOperation(operation);
            }
        }
    }
}
