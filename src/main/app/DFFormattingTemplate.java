package main.app;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;

public interface DFFormattingTemplate {
    void formatHeaders(JTable table, float frameHeight);
    class GreenThemeTemplate implements DFFormattingTemplate {
        @Override
        public void formatHeaders(JTable table, float frameHeight) {
            JTableHeader header = table.getTableHeader();

            // Set header height
            header.setPreferredSize(new Dimension(header.getPreferredSize().width, (int)(frameHeight * 0.02)));

            // Set header renderer to achieve your formatting requirements
            header.setDefaultRenderer(new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                    // Font, background, and foreground colors
                    setFont(getFont().deriveFont((float)(frameHeight * 0.02))); // Assuming you meant font size = header height
                    setBackground(new Color(1, 108, 125)); // Background color
                    setForeground(Color.WHITE); // Font color
                    setBorder(BorderFactory.createLineBorder(new Color(1, 41, 36))); // Outline color

                    return this;
                }
            });
        }
    }

}
