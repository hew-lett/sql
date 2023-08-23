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
    public static Map<String, Map<String, List<Date>>> policeStatutDateRangeMap = new HashMap<>();
    private static Map<String, List<Date>> globalStatutDateRangeMap = new HashMap<>();
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
        Stopwatch stopwatch = new Stopwatch();
        stopwatch.start();

        ref_prog = new DF(wd+"Référentiel programmes.csv", ';', true);
        ref_prog.refProgGetPolice("ICIMWFH18");
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
        Estimate estimate = new Estimate(wd+"TDB estimate par gestionnaire/SPB Italie.xlsx");
        stopwatch.printElapsedTime("estimate");

//        BaseSin base_aux = new BaseSin(wd+"source SIN/SPB Italie/","Italie","SPB Italie", true);
//        stopwatch.printElapsedTime("basesin");

//        Estimate estimate = new Estimate(wd+"TDB estimate par gestionnaire/Garantie Privée.xlsx");
//        stopwatch.printElapsedTime("estimate");
//
//        BaseSin base_aux = new BaseSin(wd+"aux SIN/Garantie Privée.xlsm");
//        stopwatch.printElapsedTime("basesin");

        List<File> fileList = Arrays.asList(Objects.requireNonNull(new File(wd + "source SIN/SPB Italie/").listFiles()));
        List<BaseSin> sinItalie = new ArrayList<>();
//        sinItalie.add(new BaseSin(fileList.get(0),"Italie", "SPB Italie"));
        for (File file : fileList) {
            BaseSin base = new BaseSin(file,"Italie", "SPB Italie");
            sinItalie.add(base);
            if (base.numPolice.equalsIgnoreCase("ICIMWFH18")) {
                base.print("en cours");
            }
        }
        stopwatch.printElapsedTime("bases");


        for (BaseSin base : sinItalie) {
            policeStatutDateRangeMap.put(base.numPolice, base.statutDateRangeMap);
            updateStatutDates(base); //global
        }
        stopwatch.printElapsedTime("maps");

        estimate.getUniqueStatutsFromMap();
        estimate.addColumnByType('M',true);
        stopwatch.printElapsedTime("addcols");

        estimate.populateColumns(sinItalie);

        stopwatch.printElapsedTime("populate");




//        stopwatch.printElapsedTime("calcul");
//
        estimate.saveToCSVFile();
//        stopwatch.printElapsedTime("saved");

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
        formatDP();
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
    public static void updateStatutDates(BaseSin base) {
        for (Map.Entry<String, List<Date>> entry : base.statutDateRangeMap.entrySet()) {
            String statut = entry.getKey();
            List<Date> baseDates = entry.getValue();

            if (!globalStatutDateRangeMap.containsKey(statut)) {
                // If this statut doesn't exist in the global map, simply put the current base's dates
                globalStatutDateRangeMap.put(statut, new ArrayList<>(baseDates));
            } else {
                // Otherwise, compare and update min and max dates if needed
                List<Date> globalDates = globalStatutDateRangeMap.get(statut);
                Date globalMinDate = globalDates.get(0);
                Date globalMaxDate = globalDates.get(1);
                Date baseMinDate = baseDates.get(0);
                Date baseMaxDate = baseDates.get(1);

                if (baseMinDate.before(globalMinDate)) {
                    globalDates.set(0, baseMinDate);
                }
                if (baseMaxDate.after(globalMaxDate)) {
                    globalDates.set(1, baseMaxDate);
                }
            }
        }
    }
    public void getUniqueStatutsFromMap() {
        Set<String> statuts = globalStatutDateRangeMap.keySet();
        this.uniqueStatuts = statuts.toArray(new String[0]);
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
    public void populateColumns(List<BaseSin> bases) {
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
//        stopwatch.printElapsedTime("firstmask");

        for (BaseAccum base : bases) {
            String police = base.numPolice;
            System.out.println(police);
            boolean[] mask_row = new boolean[this.nrow];
            for (int row = 0; row < this.nrow; row++) {
                if (police.equalsIgnoreCase((String) this.c("Contrat")[row])) {
                    mask_row[row] = true;
                }
            }
//            stopwatch.printElapsedTime("maskrow " + police);

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

//            stopwatch.printElapsedTime("calc mask " + police);

            for (int row = 0; row < this.nrow; row++) {
                if (!mask_row[row]) continue;

                for (int col = begin; col < ncol; col++) {
                    if (!this.subheader[col].isEmpty()) {
                        currentStatus = this.subheader[col];
                    }

                    if (!calc_mask[col-begin]) continue;
                    String datePeriode = (String) this.df.get(ind_datePeriode)[row];
//                    if (police.equals("ICIMWFH18") && currentStatus.equals("terminé - accepté") && datePeriode.equals("06-2020") && this.header[col].equals("12-2020")) {
                    if (police.equals("ICIMWFH18") && currentStatus.equals("terminé - accepté") && datePeriode.equals("06-2020")) {
                        System.out.println("there");
                    }
                    this.df.get(col)[row] = getSum(base, currentStatus, datePeriode, this.header[col]);
                }
            }
        }
    }

    public void populateUniqueNumPoliceValues(String colname) {
        Object[] polices = c(colname);
        for (Object obj : polices) {
            uniqueNumPoliceValues.add((String) obj);
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


    // Helper method to determine if a Date is within the same month as the provided month header.
// This method is simplistic and assumes that the date in base_aux is the first day of the month.
    public boolean isSameMonth(Date date, String monthHeader) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        int year = Integer.parseInt("20" + monthHeader.substring(monthHeader.length() - 2));
        int month = Arrays.asList("jan.", "feb.", "mar.", "apr.", "may.", "jun.", "jul.", "aug.", "sep.", "oct.", "nov.", "dec.").indexOf(monthHeader.substring(0, 4));
        return cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month;
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

    public double calculateSum(Date datePeriode, Date monthHeader, String prefix, String police, String status) {
        double result = 0.0;
        boolean rowsFound = false;

        String tableName = prefix + police;

        // Prepare a SQL query
        String query = "SELECT SUM(montant_IP) as total FROM `" + tableName + "` WHERE "
                + "MONTH(date_sous) = ? AND YEAR(date_sous) = ? AND "
                + "MONTH(date_surv) = ? AND YEAR(date_surv) = ? AND "
                + "statut = ?";

        try (PreparedStatement ps = connection.prepareStatement(query)) {
            // Extract month and year from datePeriode
            Calendar calPeriode = Calendar.getInstance();
            calPeriode.setTime(datePeriode);
            int monthPeriode = calPeriode.get(Calendar.MONTH) + 1;  // +1 because Calendar months start from 0
            int yearPeriode = calPeriode.get(Calendar.YEAR);

            // Extract month and year from monthHeader
            Calendar calHeader = Calendar.getInstance();
            calHeader.setTime(monthHeader);
            int monthHeaderVal = calHeader.get(Calendar.MONTH) + 1;
            int yearHeaderVal = calHeader.get(Calendar.YEAR);

            // Set values for prepared statement
            ps.setInt(1, monthPeriode);
            ps.setInt(2, yearPeriode);
            ps.setInt(3, monthHeaderVal);
            ps.setInt(4, yearHeaderVal);
            ps.setString(5, status);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    rowsFound = true;
                    result = rs.getDouble("total");
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }

        if (!rowsFound) {
            return -1.0;
        }
        return result;
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
    public void byMonth(boolean old) {
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
