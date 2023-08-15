package main.app;

import com.monitorjbl.xlsx.StreamingReader;
import org.apache.poi.ss.usermodel.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.concurrent.TimeUnit;

import static main.app.App.*;

public class Estimate extends DF {
    String key;
    public String[] subheader;
    int baseNcol = 0;
    int lastAppendSize = 0;
    protected Set<String> uniqueNumPoliceValues = new HashSet<>();
    boolean[] mask_col;

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
    public static void main(String[] args) throws IOException {
        Stopwatch stopwatch = new Stopwatch();
        stopwatch.start();

        ref_prog = new DF(wd+"Référentiel programmes.csv", ';', true);
        ref_triangle = new DF(wd + "ref_triangle.xlsx");
        mapping = new DF(wd + "mapping.xlsx");
//        DF map_filtered = mapping.mappingFiltre("DB Claims Italie");
        stopwatch.printElapsedTime("bases");
//        DF fic_FRA = new BaseFic(wd + "source FIC/SPB France/","FIC France");
//        DF fic_ITA = new BaseFic(wd + "source FIC/SPB Italie/","DB Claims Italie");
//        DF fic_POL = new BaseFic(wd + "source FIC/SPB Pologne/","FIC Pologne");
//        Estimate estimate = new Estimate(wd+"TDB estimate par gestionnaire/SPB France.xlsx");
//        stopwatch.printElapsedTime("estimate");
//
//        BaseSin base_aux = new BaseSin(wd+"source SIN/SPB France/","France","SPB France / Wakam");
//        stopwatch.printElapsedTime("basesin");

        Estimate estimate = new Estimate(wd+"TDB estimate par gestionnaire/Garantie Privée.xlsx");
        stopwatch.printElapsedTime("estimate");

        BaseSin base_aux = new BaseSin(wd+"aux SIN/Garantie Privée.xlsm");
        stopwatch.printElapsedTime("basesin");

        estimate.addColumnByType('M',true, base_aux);
        stopwatch.printElapsedTime("addcols");
        estimate.printFilteredHeadersAndSubheaders();
        estimate.populateColumns(base_aux);
        stopwatch.printElapsedTime("calcul");

        estimate.saveToCSVFile();
        stopwatch.printElapsedTime("saved");


//        Estimate estimate = new Estimate(wd+"TDB estimate par gestionnaire/SPB France.xlsx");
//        BaseSin base_aux = new BaseSin(wd+"aux SIN/SPB France_cdiscount.xlsm");
//        estimate.addColumnByType('M',true, base_aux);
//        estimate.populateColumns(base_aux);
//        estimate.saveToFile();


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
        populateUniqueNumPoliceValues("Contrat");
        baseNcol = ncol;
        mask_col = new boolean[ncol];
        Arrays.fill(mask_col, true);
    } //file_sin
//    public void populateColumnss(BaseAccum base_aux) {
//        // Assuming your Estimate object is 'this' (the current instance).
//        // Also, the code assumes you have a method to convert "dec.23" into an appropriate Date object.
//        // This conversion will depend on the exact structure of your base_aux data.
//        int ind = find_in_arr_first_index(this.header, "Date Periode");
////        boolean skipper = true;
//
//        for (String police : this.uniqueNumPoliceValues) {
//            if (police.equals("ICICEDV16")) {
//                System.out.println("here");
//            }
////            if (skipper) {
////                continue;
////            }
////            System.out.println(police);
//            boolean[] mask_row = new boolean[this.nrow];
//            for (int row = 0; row < this.nrow; row++) {
//                if (this.c("Contrat")[row].equals(police)) {
//                    mask_row[row] = true;
//                }
//            }
//            boolean[] mask_col = new boolean[this.ncol];
//
//            // Get the min and max dates for this police from the maps.
//            Date minDateForPolice = base_aux.minDateMap.get(police);
//            Date maxDateForPolice = base_aux.maxDateMap.get(police);
//            if (minDateForPolice == null || maxDateForPolice == null) {
//                continue;
//            }
//            for (int col = baseNcol; col < ncol; col++) {
//                if (isHigherMonthSvD(this.header[col], minDateForPolice) && isLowerMonthSvD(this.header[col], maxDateForPolice)) {
//                    mask_col[col] = true;
//                }
//            }
//            String currentStatus = null;
//            for (int col = baseNcol; col < ncol; col++) {
//                // Detect a new status.
//                if (!this.subheader[col].isEmpty()) {
//                    currentStatus = this.subheader[col];
//                }
//                if (!mask_col[col]) continue;
//                // Only proceed if we have a valid status and a month header.
//                if (currentStatus != null && !this.header[col].isEmpty()) {
//                    String monthHeader = this.header[col];
//
//                    // Iterate through each row.
//                    for (int row = 0; row < this.nrow; row++) {
//                        Date datePeriode = (Date) this.df.get(ind)[row];
//                        if (mask_row[row]) {
//                            double sum = base_aux.calculateSum(datePeriode, police, monthHeader, currentStatus);
//                            if (sum != 0.0) {
//                                this.df.get(col)[row] = String.format("%.2f", sum);
//                            }
//                        }
//                    }
//                }
//            }
//        }
//    }
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
    public void populateColumns(BaseAccum base_aux) {
        int ind = find_in_arr_first_index(this.header, "Date Periode");
        int begin = ncol - lastAppendSize;
        for (String police : this.uniqueNumPoliceValues) {
            if (police.equals("ICICEDV16")) {
                System.out.println("here");
            } else {
                continue;
            }

            boolean[] mask_row = new boolean[this.nrow];
            for (int row = 0; row < this.nrow; row++) {
                if (this.c("Contrat")[row].equals(police)) {
                    mask_row[row] = true;
                }
            }

            String currentStatus = null;
            Date minDateForStatus = null;
            Date maxDateForStatus = null;

            boolean[] temp_mask_col = new boolean[lastAppendSize];
            int maskIndex = -1;
            //begin = 100 //ncol = 300
            //col = 100,101,102...300
            //maskIndex = 0,1,2...200
            for (int col = begin; col < ncol; col++) {
                if (!this.subheader[col].isEmpty()) {
                    currentStatus = this.subheader[col];
                    minDateForStatus = base_aux.overallMinDateByStatut.get(currentStatus);
                    maxDateForStatus = base_aux.overallMaxDateByStatut.get(currentStatus);
                    if (minDateForStatus == null || maxDateForStatus == null) continue;

                    if (isHigherMonthSvD(this.header[col], minDateForStatus) && isLowerMonthSvD(this.header[col], maxDateForStatus)) {
                        temp_mask_col[0] = true;
                    } //1 subheader
                    for (int c = col+1; c < ncol; c++) {
                        if (!this.subheader[c].isEmpty()) break;
                        if (isHigherMonthSvD(this.header[c], minDateForStatus) && isLowerMonthSvD(this.header[c], maxDateForStatus)) {
                            temp_mask_col[c-begin] = true;
                            this.mask_col[c] = true;
                        }
                    }
                } // update status, create mask_col
                maskIndex++;
                if (!temp_mask_col[maskIndex]) continue;

                for (int row = 0; row < this.nrow; row++) {
                    if (!mask_row[row]) continue;

                    Date datePeriode = (Date) this.df.get(ind)[row];
                    double sum = base_aux.calculateSum(datePeriode, police, this.header[col], currentStatus);
                    if (sum != -1.0) {
                        if (sum == 0.0) {
                            this.df.get(col)[row] = "0";
                        } else {
                            this.df.get(col)[row] = String.format("%.2f", sum);
                        }
                    }
                }
            }
            System.out.println("hello");
        }
    }

    public void populateUniqueNumPoliceValues(String colname) {
        Object[] polices = c(colname);
        for (Object obj : polices) {
            uniqueNumPoliceValues.add((String) obj);
        }
    }


    // Helper method to determine if a Date is within the same month as the provided month header.
// This method is simplistic and assumes that the date in base_aux is the first day of the month.
    public boolean isSameMonth(Date date, String monthHeader) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        int year = Integer.parseInt("20" + monthHeader.substring(monthHeader.length() - 2));
        int month = Arrays.asList("jan.", "feb.", "mar.", "apr.", "may.", "jun.", "jul.", "aug.", "sep.", "oct.", "nov.", "dec.").indexOf(monthHeader.substring(0, 4));
        return cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month;
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
            columnNames.add("");  // Blank for header
            subHeaderNames.add(String.valueOf(year)); // Years for subheader
        }
        appendMultipleTables(columnNames, subHeaderNames);
    }
    public void byMonth() {
        ArrayList<String> columnNames = new ArrayList<>();
        String[] months = {"jan.", "feb.", "mar.", "apr.", "may.", "jun.", "jul.", "aug.", "sep.", "oct.", "nov.", "dec."};

        // Manually adding November and December for 2013
        columnNames.add("nov.13");
        columnNames.add("dec.13");

        // Now loop through all months for the subsequent years
        for (int year = 2014; year <= 2026; year++) {
            for (String month : months) {
                columnNames.add(month + String.valueOf(year).substring(2));
            }
        }
        appendMultipleTables(columnNames, new ArrayList<>(Collections.singletonList("Monthly")));
    }
    public void addColumnByType(char type, boolean dispatchByStatus, BaseAccum base_aux) {
        int begin = ncol;
        if (dispatchByStatus) {
            for (String status : base_aux.uniqueStatuts) {
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
    public void saveToFile() throws IOException {
        // Create a new workbook
        Workbook workbook = new XSSFWorkbook();

        // Create a sheet
        Sheet sheet = workbook.createSheet();

        // Create header row
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < ncol; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(header[i]);
        }

        // Create subheader row if needed
        Row subheaderRow = sheet.createRow(1);
        for (int i = 0; i < ncol; i++) {
            Cell cell = subheaderRow.createCell(i);
            cell.setCellValue(subheader[i]);
        }

        // Write data rows
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        for (int rowIndex = 0; rowIndex < nrow; rowIndex++) {
            Row row = sheet.createRow(rowIndex + 2); // Adding 2 to skip header and subheader rows
            for (int colIndex = 0; colIndex < ncol; colIndex++) {
                Cell cell = row.createCell(colIndex);
                Object value = df.get(colIndex)[rowIndex];
                if (value != null) {
                    switch (coltypes[colIndex]) {
                        case DAT:
                            try {
                                Date dateValue = (Date) value; // Assuming value is stored as java.util.Date
                                cell.setCellValue(sdf.format(dateValue));
                            } catch (ClassCastException e) {
                                // Handle date casting error
                                cell.setCellValue(value.toString()); // Convert to string as fallback
                            }
                            break;

                        case DBL:
                            try {
                                Double doubleValue = (Double) value; // Assuming value is stored as Double
                                cell.setCellValue(doubleValue);
                            } catch (ClassCastException e) {
                                // Handle double casting error
                                cell.setCellValue(value.toString()); // Convert to string as fallback
                            }
                            break;

                        case SKP: // If you want to skip
                            break;

                        default:
                            cell.setCellValue(value.toString()); // Default to string conversion
                            break;
                    }
                }
            }
        }

        // Get the extended file path
        String extendedPath = fullPath.replace(".xlsx", "_extended.xlsx");

        // Write the workbook to the file
        try (FileOutputStream fileOut = new FileOutputStream(extendedPath)) {
            workbook.write(fileOut);
        }

        workbook.close();
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
