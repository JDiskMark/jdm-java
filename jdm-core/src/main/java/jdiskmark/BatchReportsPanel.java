package jdiskmark;

import java.awt.BorderLayout;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

/**
 * Bottom tab panel showing saved batch run history.
 * Selecting a row loads the batch results into the Batch top tab.
 */
public class BatchReportsPanel extends JPanel {
    private static final Logger LOG = Logger.getLogger(BatchReportsPanel.class.getName());

    private final JTable historyTable;
    private final List<UUID> batchIds = new ArrayList<>();

    public BatchReportsPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(4, 4, 4, 4));

        historyTable = new JTable(new DefaultTableModel(
                new Object[][]{},
                new String[]{"Date/Time", "Drives", "Profiles", "Runs", "Duration"}
        ) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        });
        historyTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        historyTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelectedBatch();
            }
        });

        add(new JScrollPane(historyTable), BorderLayout.CENTER);
    }

    public void refresh() {
        DefaultTableModel model = (DefaultTableModel) historyTable.getModel();
        model.setRowCount(0);
        batchIds.clear();
        try {
            List<UUID> ids = Benchmark.findDistinctBatchIds();
            for (UUID batchId : ids) {
                List<Benchmark> benchmarks = Benchmark.findByBatchId(batchId);
                if (benchmarks.isEmpty()) continue;

                batchIds.add(batchId);

                String dateTime = benchmarks.getFirst().getStartTimeString();

                String drives = benchmarks.stream()
                        .map(b -> b.getDriveInfo().getDriveModel())
                        .distinct()
                        .collect(Collectors.joining(", "));

                String profiles = benchmarks.stream()
                        .map(b -> b.getConfig().getProfile() != null ? b.getConfig().getProfile().getName() : "—")
                        .distinct()
                        .collect(Collectors.joining(", "));

                int runs = benchmarks.size();

                String duration = "—";
                var first = benchmarks.getFirst().startTime;
                var last = benchmarks.getLast().endTime;
                if (first != null && last != null) {
                    Duration dur = Duration.between(first, last);
                    duration = String.format("%dm %ds", dur.toMinutes(), dur.toSecondsPart());
                }

                model.addRow(new Object[]{dateTime, drives, profiles, runs, duration});
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load batch history", e);
        }
    }

    private void loadSelectedBatch() {
        int row = historyTable.getSelectedRow();
        if (row < 0 || row >= batchIds.size()) return;
        UUID batchId = batchIds.get(row);

        if (Gui.batchPanel != null) {
            Gui.batchPanel.showBatchFromHistory(batchId);
            Gui.selectMainTab(org.metricus.jdm.ui.Tabs.TOP_BATCH);
        }
    }
}
