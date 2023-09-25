package main.app;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;

public class DFViewer {
    private JFrame frame;
    private JTable table;
    private JPanel sidePanel;
    private DefaultTableModel model;

    public DFViewer() {
        frame = new JFrame("DF Viewer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Get the usable screen size excluding taskbar and other OS elements
        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        int adjustedHeight = (int) ((int) bounds.getHeight() * 0.95);  // Reduce height by 40 pixels or any suitable value
        frame.setSize((int) bounds.getWidth(), adjustedHeight);
        frame.setLayout(new BorderLayout());

        // Setup table
        model = new DefaultTableModel();
        table = new JTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); // Set auto resize off
        JScrollPane scrollPane = new JScrollPane(table);
        frame.add(scrollPane, BorderLayout.CENTER);

        // Setup side panel
        sidePanel = new JPanel();
        sidePanel.setLayout(new BoxLayout(sidePanel, BoxLayout.Y_AXIS));
        frame.add(sidePanel, BorderLayout.WEST);
    }

    public void addDF(DF df, String name, DFFormattingTemplate formatTemplate) {
        JButton button = new JButton(name);
        button.addActionListener(e -> {
            loadDFIntoTable(df);
            formatTemplate.formatHeaders(table, frame.getHeight());
            reevaluateColumnWidths(table); // Call the new helper method here
        });
        sidePanel.add(button);
    }
    private void loadDFIntoTable(DF df) {
        displayDF(df);
    }
    public void displayDF(DF dataFrame) {
        DefaultTableModel tableModel = new DefaultTableModel();
        for (String header : dataFrame.headers) {
            tableModel.addColumn(header);
        }
        for (int i = 0; i < dataFrame.nrow; i++) {
            tableModel.addRow(dataFrame.getRow(i).toArray());
        }
        table.setModel(tableModel);

        table.setDefaultRenderer(Object.class, new CustomRenderer());

//        adjustColumnWidths_old(table);
        table.revalidate();
        table.repaint();
    }
    private void reevaluateColumnWidths(JTable table) {
        FontMetrics cellFM = table.getFontMetrics(table.getFont());
        FontMetrics headerFM = table.getFontMetrics(table.getTableHeader().getFont());

        // Get the column index for "Nombre Adhésions"
        int stoppingColumn;
        try {
            stoppingColumn = table.getColumnModel().getColumnIndex("Nombre Adhésions");
        } catch (IllegalArgumentException e) {
            return;
        }

        // Iterate through columns up to "Nombre Adhésions"
        for (int column = 0; column < stoppingColumn; column++) {
            int maxColumnWidth = 0;

            // Check header's width
            Object headerValue = table.getColumnModel().getColumn(column).getHeaderValue();
            int headerWidth = headerFM.stringWidth(headerValue != null ? headerValue.toString() : "") + table.getIntercellSpacing().width;
            maxColumnWidth = Math.max(maxColumnWidth, headerWidth);

            // Calculate max width among the first 15 rows
            for (int row = 0; row < Math.min(30, table.getRowCount()); row++) {
                Object value = table.getValueAt(row, column);
                int cellWidth = cellFM.stringWidth(value != null ? value.toString() : "") + table.getIntercellSpacing().width;
                maxColumnWidth = Math.max(maxColumnWidth, cellWidth);
            }

            // Multiply max width by 1.2 and set it for the column
            int adjustedWidth = (int) (maxColumnWidth * 2);
            table.getColumnModel().getColumn(column).setPreferredWidth(adjustedWidth);
        }

        // Iterate through columns starting from "Nombre Adhésions" and adjust width based only on the header width
        for (int column = stoppingColumn; column < table.getColumnCount(); column++) {
            Object headerValue = table.getColumnModel().getColumn(column).getHeaderValue();
            int headerWidth = headerFM.stringWidth(headerValue != null ? headerValue.toString() : "") + table.getIntercellSpacing().width;
            int adjustedWidth = (int) (headerWidth * 1.5);
            table.getColumnModel().getColumn(column).setPreferredWidth(adjustedWidth);
        }
    }

    public void show() {
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        DFViewer viewer = new DFViewer();

        // Add your DF objects here
        // Example:
        // viewer.addDF(yourDFObject, "Name of DF");

        viewer.show();
    }
    private void adjustColumnWidths_old(JTable table) {
        FontMetrics fm = table.getFontMetrics(table.getTableHeader().getFont()); // get the font metrics for header's font
        int padding = fm.charWidth(' '); // width of one symbol (e.g., a space)

        for (int column = 0; column < table.getColumnCount(); column++) {
            TableColumn tableColumn = table.getColumnModel().getColumn(column);
            int preferredWidth = tableColumn.getMinWidth();
            int maxWidth = tableColumn.getMaxWidth();

            TableCellRenderer headerRenderer = table.getTableHeader().getDefaultRenderer();
            Component headerComp = headerRenderer.getTableCellRendererComponent(table, tableColumn.getHeaderValue(), false, false, 0, column);
            preferredWidth = Math.max(preferredWidth, headerComp.getPreferredSize().width + 2 * padding); // add padding to both sides

            for (int row = 0; row < table.getRowCount(); row++) {
                TableCellRenderer cellRenderer = table.getCellRenderer(row, column);
                Component c = table.prepareRenderer(cellRenderer, row, column);
                int width = c.getPreferredSize().width + table.getIntercellSpacing().width;
                preferredWidth = Math.max(preferredWidth, width);

                // We've exceeded the maximum width, no need to check other rows
                if (preferredWidth >= maxWidth) {
                    preferredWidth = maxWidth;
                    break;
                }
            }

            tableColumn.setPreferredWidth(preferredWidth);
        }
    }
    private void adjustColumnWidths(JTable table) {
        for (int column = 0; column < table.getColumnCount(); column++) {
            TableColumn tableColumn = table.getColumnModel().getColumn(column);
            int preferredWidth = tableColumn.getMinWidth();
            int maxWidth = tableColumn.getMaxWidth();

            TableCellRenderer headerRenderer = table.getTableHeader().getDefaultRenderer();
            Component headerComp = headerRenderer.getTableCellRendererComponent(table, tableColumn.getHeaderValue(), false, false, 0, column);
            preferredWidth = Math.max(preferredWidth, headerComp.getPreferredSize().width);

            int limit = Math.min(15, table.getRowCount()); // Limit to first 15 rows or total rows if less than 15

            for (int row = 0; row < limit; row++) {
                TableCellRenderer cellRenderer = table.getCellRenderer(row, column);
                Component c = table.prepareRenderer(cellRenderer, row, column);
                int width = c.getPreferredSize().width + table.getIntercellSpacing().width;
                preferredWidth = Math.max(preferredWidth, width);

                // We've exceeded the maximum width, no need to check other rows
                if (preferredWidth >= maxWidth) {
                    preferredWidth = maxWidth;
                    break;
                }
            }

            tableColumn.setPreferredWidth(preferredWidth);
        }
    }
    private class CustomRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            String annéeValue = null;
            String contratValue = null;
            String distValue = null;
            String gestValue = null;

            // Attempt to get the "Année" column value
            try {
                int colIndexAnnée = table.getColumnModel().getColumnIndex("Année");
                annéeValue = table.getValueAt(row, colIndexAnnée).toString();
            } catch (IllegalArgumentException e) {
                // Column not found
            }

            // Attempt to get the "Contrat" column value
            try {
                int colIndexContrat = table.getColumnModel().getColumnIndex("Contrat");
                contratValue = table.getValueAt(row, colIndexContrat).toString();
            } catch (IllegalArgumentException e) {
                // Column not found
            }

            // Attempt to get the "Année" column value
            try {
                int colIndexDist = table.getColumnModel().getColumnIndex("Distributeur");
                distValue = table.getValueAt(row, colIndexDist).toString();
            } catch (IllegalArgumentException e) {
                // Column not found
            }

            // Attempt to get the "Contrat" column value
            try {
                int colIndexGest = table.getColumnModel().getColumnIndex("Gestionnaire");
                gestValue = table.getValueAt(row, colIndexGest).toString();
            } catch (IllegalArgumentException e) {
                // Column not found
            }
            boolean isTotalRow = (annéeValue != null && annéeValue.startsWith("Total")) ||
                    (contratValue != null && contratValue.startsWith("Total")) ||
                    (distValue != null && distValue.startsWith("Total")) ||
                    (gestValue != null && gestValue.startsWith("Total"));

            // Apply formatting based on the values
            // Apply formatting based on the values
            if (isTotalRow) {
                // Set font to white and bold
                setForeground(Color.WHITE);
                Font boldFont = getFont().deriveFont(Font.BOLD);
                setFont(boldFont.deriveFont(boldFont.getSize() * 1.2f));

                // Individual background colors
                if (annéeValue != null && annéeValue.startsWith("Total")) {
                    setBackground(new Color(61, 169, 186));
                } else if (contratValue != null && contratValue.startsWith("Total")) {
                    setBackground(new Color(7, 148, 70));
                } else if (distValue != null && distValue.startsWith("Total")) {
                    setBackground(new Color(7, 56, 148));
                } else if (gestValue != null && gestValue.startsWith("Total")) {
                    setBackground(new Color(96, 9, 184));
                }
            } else {
                // Ensure cells that don't meet the condition revert to default rendering
                setBackground(table.getBackground());
                setFont(table.getFont());
                setForeground(table.getForeground());
            }

            return this;
        }
    }





}


