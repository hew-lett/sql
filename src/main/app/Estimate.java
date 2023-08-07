package main.app;

import com.monitorjbl.xlsx.StreamingReader;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.FileOutputStream;
import java.util.concurrent.TimeUnit;

import static main.app.App.*;
import static main.app.App.rowchecker;

public class Estimate extends DF {
    String key;
    public String[] subheader;
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
//        long startTime = System.nanoTime();long endTime;long duration;long minutes;long seconds;
        ref_prog = new DF(wd+"Référentiel programmes.csv", ';', true);
        ref_prog.print(10);
        ref_triangle = new DF(wd + "ref_triangle.xlsx");
        mapping = new DF(wd + "mapping.xlsx");
        DF map_filtered = mapping.mappingFiltre("DB Claims Italie");
        map_filtered.print(10);
//        DF fic_FRA = new BaseFic(wd + "source FIC/SPB France/","FIC France");
//        DF fic_ITA = new BaseFic(wd + "source FIC/SPB Italie/","DB Claims Italie");
//        DF fic_POL = new BaseFic(wd + "source FIC/SPB Pologne/","FIC Pologne");

        Estimate estimate = new Estimate(wd+"TDB estimate par gestionnaire/Garantie Privée.xlsx");
        BaseSin base_aux = new BaseSin(wd+"aux SIN/Garantie Privée.xlsm");
        estimate.addColumnByType('M',true, base_aux);
        estimate.populateColumns(base_aux);

        estimate.saveToFile();

        long startTime = System.nanoTime();

//        Estimate estimate = new Estimate(wd+"TDB estimate par gestionnaire/SPB France.xlsx");
//        BaseSin base_aux = new BaseSin(wd+"aux SIN/SPB France_cdiscount.xlsm");
//        estimate.addColumnByType('M',true, base_aux);
//        estimate.populateColumns(base_aux);
//        estimate.saveToFile();

        long endTime = System.nanoTime();
        long elapsedTime = endTime - startTime;

// Convert the elapsed time to minutes and seconds.
        long minutes = TimeUnit.NANOSECONDS.toMinutes(elapsedTime);
        long seconds = TimeUnit.NANOSECONDS.toSeconds(elapsedTime) - TimeUnit.MINUTES.toSeconds(minutes);

        System.out.println("Elapsed Time: " + minutes + " minutes " + seconds + " seconds");

    }
    public Estimate(String path) throws IOException {
        this.fullPath = path;
        fileName = new File(path).getName();
        key = fileName.replace(".xlsx", "").toLowerCase();
        this.statut_unique = statuts.get(key);
        if (this.statut_unique == null) {
            this.statut_unique = new ArrayList<>();
        }
        if (this.statut_unique.size() == 0) {
            this.statut_unique.add("Total");
        }

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
        header_refactor();
    } //file_sin
    public void populateColumns(BaseFic base_aux) {
        // Assuming your Estimate object is 'this' (the current instance).
        // Also, the code assumes you have a method to convert "dec.23" into an appropriate Date object.
        // This conversion will depend on the exact structure of your base_aux data.

        String currentStatus = null;
        for (int col = 0; col < this.ncol; col++) {
            // Detect a new status.
            if (!this.subheader[col].isEmpty()) {
                currentStatus = this.subheader[col];
            }

            // Only proceed if we have a valid status and a month header.
            if (currentStatus != null && !this.header[col].isEmpty()) {
                String monthHeader = this.header[col];

                // Iterate through each row.
                for (int row = 0; row < this.nrow; row++) {
                    Date datePeriode = (Date) this.df.get(find_in_arr_first_index(this.header, "Date Periode"))[row];

                    // Calculate sum of montant_IP based on the given filters.
                    double sum = base_aux.calculateSum(datePeriode, monthHeader, currentStatus);

                    // If sum is non-zero, populate the cell; else, leave it blank.
                    if (sum != 0.0) {
                        this.df.get(col)[row] = String.valueOf(sum);
                    }
                }
            }
        }
    }
    public void populateColumns(BaseSin base_aux) {
        // Assuming your Estimate object is 'this' (the current instance).
        // Also, the code assumes you have a method to convert "dec.23" into an appropriate Date object.
        // This conversion will depend on the exact structure of your base_aux data.

        String currentStatus = null;
        for (int col = 0; col < this.ncol; col++) {
            // Detect a new status.
            if (!this.subheader[col].isEmpty()) {
                currentStatus = this.subheader[col];
            }

            // Only proceed if we have a valid status and a month header.
            if (currentStatus != null && !this.header[col].isEmpty()) {
                String monthHeader = this.header[col];

                // Iterate through each row.
                for (int row = 0; row < this.nrow; row++) {
                    Date datePeriode = (Date) this.df.get(find_in_arr_first_index(this.header, "Date Periode"))[row];

                    // Calculate sum of montant_IP based on the given filters.
                    double sum = base_aux.calculateSum(datePeriode, monthHeader, currentStatus);

                    // If sum is non-zero, populate the cell; else, leave it blank.
                    if (sum != 0.0) {
                        this.df.get(col)[row] = String.valueOf(sum);
                    }
                }
            }
        }
    }
    // This calculateSum method should be in your BaseFic (and/or BaseSin) class.


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
    public void addColumnByType(char type, boolean dispatchByStatus, BaseFic base_aux) {
        if (dispatchByStatus) {
            for (String status : base_aux.statut_unique) {
                // Assuming the status is to be added to the header, just once
                header[header.length - 1] = status;
                switch (type) {
                    case 'T':
                        addTotal();
                        break;
                    case 'Y':
                        addAnnees();
                        break;
                    case 'M':
                        byMonth();
                        break;
                }
            }
        } else {
            switch (type) {
                case 'T':
                    addTotal();
                    break;
                case 'Y':
                    addAnnees();
                    break;
                case 'M':
                    byMonth();
                    break;
            }
        }
    }
    public void addColumnByType(char type, boolean dispatchByStatus, BaseSin base_aux) {
        if (dispatchByStatus) {
            for (String status : base_aux.statut_unique) {
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

}
