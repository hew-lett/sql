package main.app;

import com.monitorjbl.xlsx.StreamingReader;
import org.apache.poi.ss.usermodel.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.Date;

import static main.app.App.*;

import java.sql.*;

public class Estimate extends DF {
    String key;
    public String[] subheader;
    public List<Date> headerCalcul;
    int baseNcol = 0;
    int lastAppendSize = 0;
    protected static Set<String> uniqueNumPoliceValues = new HashSet<>();


    boolean[] mask_col;
    protected Stopwatch stopwatch = new Stopwatch();
    public String[] uniqueStatuts;
    public static final HashMap<String, Integer> monthMap = new HashMap<String, Integer>() {{
        put("jan.", Calendar.JANUARY);
        put("feb.", Calendar.FEBRUARY);
        put("mar.", Calendar.MARCH);
        put("apr.", Calendar.APRIL);
        put("may.", Calendar.MAY);
        put("jun.", Calendar.JUNE);
        put("jul.", Calendar.JULY);
        put("aug.", Calendar.AUGUST);
        put("sep.", Calendar.SEPTEMBER);
        put("oct.", Calendar.OCTOBER);
        put("nov.", Calendar.NOVEMBER);
        put("dec.", Calendar.DECEMBER);
    }};
    public static void main(String[] args) throws IOException, SQLException {

    }
    public Estimate(String path) throws IOException {
        this.fullPath = path;
        fileName = new File(path).getName();
        key = fileName.replace(".xlsx", "").toLowerCase();

        InputStream is = Files.newInputStream(new File(path).toPath());
        Workbook workbook = StreamingReader.builder()
                .rowCacheSize(1)
                .bufferSize(4096)
                .open(is);

        String sheet_name = workbook.getSheetName(0);
        Sheet sheet = workbook.getSheet(sheet_name);
        Iterator<Row> rowIter = sheet.rowIterator();
        Row row = rowIter.next();
        nrow = sheet.getLastRowNum();
        ncol = row.getLastCellNum();
        header = new String[ncol];
        subheader = new String[ncol];
        int i = 0;
        for (Cell c : row) {
            header[i] = c.getStringCellValue();
            subheader[i] = "";
            i++;
        }

        coltypes = new Col_types[ncol];
        for (i = 0; i < ncol; i++) {
            if (header[i].contains("Date Periode")) {
                coltypes[i] = Col_types.DAT;
            } else {
                coltypes[i] = Col_types.STR;
            }
        }

        df = new ArrayList<>(ncol);
        this.df_populate(coltypes);

        format = new SimpleDateFormat("dd-MM-yyyy");

        int col_iterator;
        int row_number = 0;
        while(rowIter.hasNext()) {
            row = rowIter.next();
            col_iterator = 0;

            for (int c = 0; c < ncol; c++) {
                if (coltypes[c] != Col_types.SKP) {
                    Cell cell_i = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (cell_i == null) {
                        switch(coltypes[c]) {
                            case STR -> df.get(col_iterator)[row_number] = "";
                            case DBL -> df.get(col_iterator)[row_number] = NA_DBL;
                            case DAT -> df.get(col_iterator)[row_number] = NA_DAT;
                        }
                        col_iterator++;
                        continue;
                    }
                    //System.out.println(cell_i);
                    df.get(col_iterator)[row_number] = parseCell(cell_i, coltypes[c], format);
                    col_iterator++;
                }
            }
            row_number++;
        }
        headerAndColtypesDropSKP();
        formatDP();
        baseNcol = ncol;
        mask_col = new boolean[ncol];
        Arrays.fill(mask_col, true);
    } //file_sin

    public void getUniqueStatutsFromMap() {
        Set<String> statuts = globalStatutDateRangeMap.keySet();
        List<String> sortedStatuts = new ArrayList<>(statuts);

        // Sort the list so that "terminé - accepté" is at the beginning
        sortedStatuts.sort((statut1, statut2) -> {
            if (statut1.equalsIgnoreCase("terminé - accepté")) {
                return -1;
            } else if (statut2.equalsIgnoreCase("terminé - accepté")) {
                return 1;
            }
            return statut1.compareTo(statut2);
        });

        this.uniqueStatuts = sortedStatuts.toArray(new String[0]);
    }
    public void populateMonthStatut(List<BaseSin> bases) {
        int ind_datePeriode = find_in_arr_first_index(this.header, "Date Periode");
        int begin = ncol - lastAppendSize;
        Stopwatch stopwatch = new Stopwatch();
        stopwatch.start();
        String currentStatus = null;
        Date minDateForStatus = null;
        Date maxDateForStatus = null;
        for (int col = begin; col < ncol; col++) {
            if (!this.subheader[col].isEmpty()) {
                currentStatus = this.subheader[col];
                minDateForStatus = globalStatutDateRangeMap.get(currentStatus).get(0);
                maxDateForStatus = globalStatutDateRangeMap.get(currentStatus).get(1);
            }
            if (minDateForStatus == null || maxDateForStatus == null) continue;

            if (!(isHigherMonthSvD(this.header[col], maxDateForStatus) || isLowerMonthSvD(this.header[col], minDateForStatus)) ||
                    !this.subheader[col].isEmpty()) {
                this.mask_col[col] = true;
            }
        } // showing mask

        for (BaseAccum base : bases) {
            String police = base.numPolice;
            System.out.print(police + " | ");
            boolean[] mask_row = new boolean[this.nrow];
            for (int row = 0; row < this.nrow; row++) {
                if (police.equalsIgnoreCase((String) this.c("Contrat")[row])) {
                    mask_row[row] = true;
                }
            }

            List<Date> minmax = null;
            boolean[] calc_mask = new boolean[lastAppendSize];
            for (int col = begin; col < ncol; col++) {
                if (!this.subheader[col].isEmpty()) {
                    currentStatus = this.subheader[col];
                    minmax = policeStatutDateRangeMap.get(police).get(currentStatus);
                    if (minmax == null) continue;
                    minDateForStatus = minmax.get(0);
                    maxDateForStatus = minmax.get(1);
                }
                if (minmax == null) continue;
                if (minDateForStatus == null || maxDateForStatus == null) continue;
                if (!(isHigherMonthSvD(this.header[col], maxDateForStatus) || isLowerMonthSvD(this.header[col], minDateForStatus))) {
                    calc_mask[col-begin] = true;
                }
            }

            for (int row = 0; row < this.nrow; row++) {
                if (!mask_row[row]) continue;

                for (int col = begin; col < ncol; col++) {
                    if (!this.subheader[col].isEmpty()) {
                        currentStatus = this.subheader[col];
                    }

                    if (!calc_mask[col-begin]) continue;
                    String datePeriode = (String) this.df.get(ind_datePeriode)[row];
                    this.df.get(col)[row] = getSum(base, currentStatus, datePeriode, this.header[col]);
                }
            }
        }
    }

    public Map<String, ArrayList<String>> getFilteredHeadersAndSubheaders() {
        Map<String, ArrayList<String>> result = new LinkedHashMap<>();
        result.put("base", new ArrayList<>());
        for (int i = 0; i < baseNcol; i++) {
            result.get("base").add(header[i]);
        }
        String currentSubheader = null;

        for (int i = baseNcol; i < subheader.length; i++) {
            if (!subheader[i].isEmpty()) {
                currentSubheader = subheader[i];
                result.put(currentSubheader, new ArrayList<>());
            }

            if (mask_col[i-baseNcol]) {
                result.get(currentSubheader).add(header[i]);
            }
        }

        return result;
    }
    public void printFilteredHeadersAndSubheaders() {
        Map<String, ArrayList<String>> filteredHeadersAndSubheaders = getFilteredHeadersAndSubheaders();

        for (Map.Entry<String, ArrayList<String>> entry : filteredHeadersAndSubheaders.entrySet()) {
            String subheader = entry.getKey();
            ArrayList<String> headers = entry.getValue();

            String headersString = String.join(", ", headers);
            System.out.println(subheader + ": " + headersString);
        }
    }


    public void formatDP() {
        SimpleDateFormat format = new SimpleDateFormat("MM-yyyy");

        Object[] datePeriodeColumn = c("Date Periode");
        Object[] datePeriodeColumnOutput = new Object[this.nrow];
        for (int i = 0; i < datePeriodeColumn.length; i++) {
            Date date = (Date) datePeriodeColumn[i]; // Cast the object to Date
            String formattedDate = format.format(date); // Convert Date to String using the format
            datePeriodeColumnOutput[i] = formattedDate; // Replace the Date with its formatted string
        }

        // Update the column in the dataframe
        int index = find_in_arr_first_index(header, "Date Periode");
        df.set(index, datePeriodeColumnOutput);
    }
    public String getSum(BaseAccum base, String status, String date_sous, String date_surv) {
        Map<String, Map<String, Map<String, Double>>> pivotTable = base.pivotTable;

        // Check for status existence
        Map<String, Map<String, Double>> middleMap = pivotTable.get(status);
        if (middleMap == null) {
            return "";  // or some default value if status doesn't exist
        }

        // Check for date_sous existence
        Map<String, Double> innerMap = middleMap.get(date_sous);
        if (innerMap == null) {
            return "";  // or some default value if date_sous doesn't exist
        }

        // Check for date_surv existence and get the value
        Double result = innerMap.get(date_surv);
        if (result == null) {
            return "";  // or some default value if date_surv doesn't exist
        }

        return String.format("%.2f", result);
    }

    public void appendTable(ArrayList<String> columnNames, String tableName) {
        appendMultipleTables(columnNames, new ArrayList<>(Collections.singletonList(tableName)));
    }
    public void appendMultipleTables(ArrayList<String> columnNames, ArrayList<String> tableNames) {
        int nNewColumns = columnNames.size() * tableNames.size();
        int newNcol = ncol + nNewColumns;

        String[] newHeader = new String[newNcol];
        String[] newSubheader = new String[newNcol];
        Col_types[] newColtypes = new Col_types[newNcol];
        ArrayList<Object[]> newDf = new ArrayList<>(newNcol);

        // Copy old values
        System.arraycopy(header, 0, newHeader, 0, ncol);
        System.arraycopy(subheader, 0, newSubheader, 0, ncol);
        System.arraycopy(coltypes, 0, newColtypes, 0, ncol);
        newDf.addAll(df);

        // Initialize new values
        for (int i = ncol, k = 0; i < newNcol; i += columnNames.size(), k++) {
            for (int j = 0; j < columnNames.size(); j++) {
                if(j == 0) {
                    newSubheader[i + j] = tableNames.get(k); // name of the table
                } else {
                    newSubheader[i + j] = ""; // blank for others
                }
                newHeader[i + j] = columnNames.get(j); // name of the columns
                newColtypes[i + j] = Col_types.STR; // assign STR type
                newDf.add(new Object[nrow]); // Initialize column data
            }
        }

        // Update class properties
        header = newHeader;
        subheader = newSubheader;
        coltypes = newColtypes;
        df = newDf;
        ncol = newNcol;
    }
    public void addTotal() {
        ArrayList<String> columnNames = new ArrayList<>(Collections.singletonList("Total"));
        appendTable(columnNames, "Total");
    }
    public void addAnnees() {
        ArrayList<String> columnNames = new ArrayList<>();
        ArrayList<String> subHeaderNames = new ArrayList<>();
        for (int year = 2013; year <= 2026; year++) {
            columnNames.add(String.valueOf(year));  // Blank for header
            subHeaderNames.add(""); // Years for subheader
        }
        appendMultipleTables(columnNames, subHeaderNames);
    }
    public void byMonth() {
        ArrayList<String> columnNames = new ArrayList<>();
        SimpleDateFormat format = new SimpleDateFormat("MM-yyyy");

        // Define the months and years
        String[] months = {"01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12"};

        // Manually adding November and December for 2013
        columnNames.add("11-2013");
        columnNames.add("12-2013");

        // Now loop through all months for the subsequent years
        for (int year = 2014; year <= 2026; year++) {
            for (String month : months) {
                columnNames.add(month + "-" + year);
            }
        }
        appendMultipleTables(columnNames, new ArrayList<>(Collections.singletonList("Monthly")));
    }
    public void addColumnByType(char type, boolean dispatchByStatus) {
        int begin = ncol;
        if (dispatchByStatus) {
            for (String status : uniqueStatuts) {
                // Assuming the status is to be added to the header, just once
                int status_ind = header.length;
                switch (type) {
                    case 'T' -> addTotal();
                    case 'Y' -> addAnnees();
                    case 'M' -> byMonth();
                }
                subheader[status_ind] = status;
            }
        } else {
            switch (type) {
                case 'T' -> addTotal();
                case 'Y' -> addAnnees();
                case 'M' -> byMonth();
            }
        }
        int end = ncol;
        this.lastAppendSize = end - begin;
        boolean[] newMaskCol = new boolean[ncol];
        System.arraycopy(this.mask_col, 0, newMaskCol, 0, this.mask_col.length);
        this.mask_col = newMaskCol;
        for (int i = begin; i < end; i++) {
           if(!this.subheader[i].isEmpty()) {
               this.mask_col[i] = true;
           }
        }
    }

    public void saveToCSVFile() throws IOException {
        String filePath = fullPath.replace(".xlsx", "_extended.csv");

        // Create a FileWriter and a BufferedWriter to write text to the file in UTF-8 encoding
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(filePath), StandardCharsets.UTF_8))) {

            // Write BOM for UTF-8
            writer.write('\ufeff');
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");

            // Determine which columns to include in the CSV
            boolean[] includeColumn = new boolean[ncol];
            for (int colIndex = 0; colIndex < ncol; colIndex++) {
                if (mask_col[colIndex]) {
                    includeColumn[colIndex] = true;
                }
            }

            // Write header row
            writeRow(writer, header, includeColumn);

            // Write subheader row
            writeRow(writer, subheader, includeColumn);

            // Write data rows
            for (int rowIndex = 0; rowIndex < nrow; rowIndex++) {
                boolean firstCol = true;
                for (int colIndex = 0; colIndex < ncol; colIndex++) {
                    if (!includeColumn[colIndex]) {
                        continue; // Skip this column
                    }

                    if (!firstCol) {
                        writer.write(";");
                    }

                    Object value = df.get(colIndex)[rowIndex];
                    if (value != null) {
                        if (value instanceof Date) {
                            writer.write(sdf.format((Date) value));
                        } else {
                            writer.write(value.toString());
                        }
                    }

                    firstCol = false;
                }
                writer.newLine(); // Move to the next line
            }
        }
    }
    private void writeRow(BufferedWriter writer, String[] row, boolean[] includeColumn) throws IOException {
        boolean firstCol = true;
        for (int colIndex = 0; colIndex < row.length; colIndex++) {
            if (!includeColumn[colIndex]) {
                continue;
            }

            if (!firstCol) {
                writer.write(";");
            }
            writer.write(row[colIndex]);
            firstCol = false;
        }
        writer.newLine();
    }
    public void saveToCSVFile_old() throws IOException {
        String filePath = fullPath.replace(".xlsx", "_extended.csv");
        // Create a FileWriter and a BufferedWriter to write text to the file in UTF-8 encoding
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            // Write BOM for UTF-8
            writer.write('\ufeff');
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");

            // Write header row
            for (int i = 0; i < ncol; i++) {
                if (i > 0) {
                    writer.write(";");
                }
                writer.write(header[i]);
            }
            writer.newLine(); // Move to the next line

            // Write subheader row
            for (int i = 0; i < ncol; i++) {
                if (i > 0) {
                    writer.write(";");
                }
                writer.write(subheader[i]);
            }
            writer.newLine(); // Move to the next line

            // Write data rows
            for (int rowIndex = 0; rowIndex < nrow; rowIndex++) {
                for (int colIndex = 0; colIndex < ncol; colIndex++) {
                    if (colIndex > 0) {
                        writer.write(";");
                    }

                    Object value = df.get(colIndex)[rowIndex];
                    if (value != null) {
                        if (value instanceof Date) {
                            writer.write(sdf.format((Date) value));
                        } else {
                            writer.write(value.toString());
                        }
                    }
                }
                writer.newLine(); // Move to the next line
            }
        }
    }
}
