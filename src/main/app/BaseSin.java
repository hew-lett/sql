package main.app;

import com.monitorjbl.xlsx.StreamingReader;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.apache.poi.ss.usermodel.*;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.min;
import static main.app.App.*;
import static main.app.App.ref_prog;
import static main.app.DF.Col_types.SKP;

public class BaseSin extends BaseAccum {
    Object[] refProgrammesRow;
    private static Connection connection;
    private int lastID = 0;
    private static final int BATCH_SIZE = 10000;
    Map<String, ArrayList<Object[]>> dfMapped = new HashMap<>();
    Map<String, Integer> nrowMapped = new HashMap<>();
    private static String[] currentHeaderRef = null;
    char delim = ';';
    String pays = "";
    File path;
    public static void main(String[] args) throws IOException, SQLException {

        long startTime = System.nanoTime();long endTime;long duration;long minutes;long seconds;
//        List<File> fileList = Arrays.asList(Objects.requireNonNull(new File(wd + "source SIN/SPB France/").listFiles()));
//        List<BaseSin> sinFrance = new ArrayList<>();
//        for (File file : fileList) {
//            sinFrance.add(new BaseSin(file,"France", "SPB France / Wakam"));
//        }
        ref_prog = new DF(wd+"Référentiel programmes.csv", ';',true);

        List<File> fileList = Arrays.asList(Objects.requireNonNull(new File(wd + "source SIN/SPB Italie/").listFiles()));
        List<BaseSin> sinItalie = new ArrayList<>();
//        File base = fileList.get(0);
//        BaseSin ita = new BaseSin(base,"Italie", "SPB Italie", true);
//        ita.print();
//        ita.printPivotTable();
        int index = 0;
        for (File file : fileList) {
            sinItalie.add(new BaseSin(file,"Italie", "SPB Italie"));
            sinItalie.get(index).printPivotTable();
            index++;
        }
        endTime = System.nanoTime();
        long elapsedTime = endTime - startTime;

//        System.out.println(elapsedTime/1000000);
        minutes = TimeUnit.NANOSECONDS.toMinutes(elapsedTime);
        seconds = TimeUnit.NANOSECONDS.toSeconds(elapsedTime) - TimeUnit.MINUTES.toSeconds(minutes);

        System.out.println("Elapsed Time: " + minutes + " minutes " + seconds + " seconds");
    }
    public BaseSin(String path) throws IOException {
        this.fullPath = path;
        // Extracting the filename and keys to find the matching referential row
        fileName = new File(path).getName().replace(".xlsm", "")
                .replace(".xlsx", "").toLowerCase();
        System.out.println(fileName);

        String[] keys = fileName.split("_");
        key_sin = keys[0];
        referentialRow = getReferentialRow(keys);

        InputStream is = Files.newInputStream(new File(path).toPath());
        Workbook workbook = StreamingReader.builder()
                .rowCacheSize(1)
                .bufferSize(4096)
                .open(is);

        String sheet_name;
        if (workbook.getNumberOfSheets() == 1) {
            sheet_name = workbook.getSheetName(0);
        } else {
            sheet_name = null;
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                String tempSheetName = workbook.getSheetName(i);
                if (tempSheetName != null && tempSheetName.toLowerCase().contains("sinistre")) {
                    sheet_name = tempSheetName;
                    break;
                }
            }

            if (sheet_name == null) {
                sheet_name = workbook.getSheetName(0);
            }
        }
        Sheet sheet = workbook.getSheet(sheet_name);
        Iterator<Row> rowIter = sheet.rowIterator();
        Row row = rowIter.next();
        nrow = sheet.getLastRowNum();
        ncol = row.getLastCellNum();
        header = new String[ncol];

        int i = 0;
        for (Cell c : row) {
            header[i] = c.getStringCellValue().toLowerCase();
            i++;
        }
        String[] cols = Arrays.copyOf(ref_triangle.header, ref_triangle.header.length);
        String[] cols_to_take = getColsToTake();

        coltypes = new Col_types[ncol];
        for (i = 0; i < ncol; i++) {
            if (check_in(header[i], cols_to_take)) {
                int exactPosition = find_in_arr_first_index(referentialRow,header[i]);
                coltypes[i] = cols[exactPosition].startsWith("date") ? Col_types.DAT : cols[exactPosition].startsWith("montant") ? Col_types.DBL : Col_types.STR;
            } else {
                coltypes[i] = SKP;
            }
        }

        ncol = get_len(coltypes);
        df = new ArrayList<>(ncol);
        this.df_populate(coltypes);

        String dateFormatString = referentialRow[8].toString();
        SimpleDateFormat dateFormatter = getDateFormatter(dateFormatString);

        int col_iterator;
        int row_number = 0;
        while(rowIter.hasNext()) {
            row = rowIter.next();
            col_iterator = 0;
            for (int c = 0; c < this.header.length; c++) {
                if (coltypes[c] != SKP) {
                    Cell cell_i = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (cell_i == null) {
                        switch(coltypes[c]) {
                            case STR -> df.get(col_iterator)[row_number] = "";
                            case DBL -> df.get(col_iterator)[row_number] = 0d;
                            case DAT -> df.get(col_iterator)[row_number] = NA_DAT;
                        }
                        col_iterator++;
                        continue;
                    }
                    //System.out.println(cell_i);
                    df.get(col_iterator)[row_number] = parseCell(cell_i, coltypes[c], dateFormatter);
                    col_iterator++;
                }
            }
            row_number++;
            rowchecker++;

        }
        headerAndColtypesDropSKP();
        header_unify();
        date_autofill_agg();
        populateUniqueStatuts();
        populateUniqueNumPoliceValues();
    }
    public BaseSin(String path, String pays, String mappingColDefault, boolean old) throws IOException {
        this.source = true;
        this.pays = pays;
//        this.path = path;
        this.referentialRow = getReferentialRow(new String[]{"source"});

        List<File> fileList = Arrays.asList(Objects.requireNonNull(new File(path).listFiles()));
        if (fileList.isEmpty()) return;

        if (pays.equals("Pologne")) {
            delim = '\t';
        }
        if (pays.equals("France")) {
            delim = '|';
        }

        int dim = computeDimSIN();
        System.out.println(dim);

        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true, delim);
        settings.trimValues(true);

        int i = 0;
        boolean initialized = false;

        for (File file : fileList) {
            String mapping_col = "";
            if (file.toString().contains("FRMP")) {
                mapping_col = "SPB France / ONEY";
            } else {
                mapping_col = mappingColDefault;
            }
            try (Reader inputReader = Files.newBufferedReader(file.toPath(), Charset.forName(encoding))) {
                CsvParser parser = new CsvParser(settings);
                List<String[]> parsedRows = parser.parseAll(inputReader);
                Iterator<String[]> rows = parsedRows.iterator();

                if (!initialized) {
                    header = rows.next();
                    header = Arrays.stream(header)
                            .filter(h -> h != null && !h.trim().isEmpty())
                            .toArray(String[]::new);
                    ncol = header.length;

                    boolean[] cols_kept = this.mapColnamesAndGetColsKept(mapping_col);
                    header_unify();
                    coltypes_populate(cols_kept);

                    nrow = dim;
                    assert (coltypes.length == parsedRows.get(0).length);
                    ncol = get_len(coltypes);
                    df = new ArrayList<>(get_len(coltypes));
                    this.df_populate(coltypes);

                    initialized = true;
                } else {
                    rows.next(); // Skipping the header for all subsequent files
                }

                while (rows.hasNext()) {
                    int j = 0;
                    int k = 0;
                    String[] parsedRow = rows.next();
                    parsedRow = Arrays.copyOf(parsedRow, header.length);
                    for (String s : parsedRow) {
                        if (coltypes[k] != SKP) {
                            df.get(j)[i] = get_lowercase_cell_of_type(s, coltypes[k], dateDefault);
                            j++;
                        }
                        k++;
                    }
                    i++;
                }

            }
        }
        headerAndColtypesDropSKP();
        date_autofill_agg();
        remove_leading_zeros();
        populateUniqueStatuts();
        populateUniqueNumPoliceValues();
    }
    public BaseSin(File path, String pays, String mappingColDefault, boolean old) throws IOException, SQLException {
        this.source = true;
        this.pays = pays;
//        this.path = path;
        this.referentialRow = getReferentialRow(new String[]{"source"});

        if (pays.equals("Pologne")) {
            delim = '\t';
        }
        if (pays.equals("France")) {
            delim = '|';
        }

        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true, delim);
        settings.trimValues(true);

        String mapping_col = "";

        System.out.println(path.getName());
        numPolice = extractKeyFromFileName(path.getName(),pays);
        tableName = "sin_" + pays + "_" + numPolice;

        if (path.toString().contains("FRMP")) {
            mapping_col = "SPB France / ONEY";
        } else {
            mapping_col = mappingColDefault;
        }

        try (Reader inputReader = Files.newBufferedReader(path.toPath(), Charset.forName(encoding))) {
            CsvParser parser = new CsvParser(settings);
            List<String[]> parsedRows = parser.parseAll(inputReader);
            Iterator<String[]> rows = parsedRows.iterator();
            nrow = csv_get_nrows(path.getPath(), delim);

            header = rows.next();
            header = Arrays.stream(header)
                    .filter(h -> h != null && !h.trim().isEmpty())
                    .toArray(String[]::new);
            boolean[] cols_kept = this.mapColnamesAndGetColsKept(mapping_col);
            header_unify();
            coltypes_populate(cols_kept);
            ncol = get_len(coltypes);
            headerDropSKP();
            if (currentHeaderRef == null) {
                currentHeaderRef = this.header;
            }

            initializeConnection();
            createTable(tableName,currentHeaderRef,coltypes);
            if (validateHeader(currentHeaderRef,header,tableName)) {
                insertData(tableName,parsedRows,header,coltypes,dateDefault);
            } else {
                int[] headerIndexes = matchHeaders(currentHeaderRef,header);
                insertDataWithIndices(tableName,parsedRows,header,coltypes,dateDefault,headerIndexes);
            }

            this.date_autofill_sql();
            closeConnection();

    } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }
    public BaseSin(File path, String pays, String mappingColDefault) throws IOException, SQLException {
        this.source = true;
        this.pays = pays;
        this.referentialRow = getReferentialRow(new String[]{"source"});

        if (pays.equals("Pologne")) {
            delim = '\t';
        }
        if (pays.equals("France")) {
            delim = '|';
        }

        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true, delim);
        settings.trimValues(true);

        String mapping_col = "";

        System.out.println(path.getName());
        numPolice = extractKeyFromFileName(path.getName(),pays);

        if (path.toString().contains("FRMP")) {
            mapping_col = "SPB France / ONEY";
        } else {
            mapping_col = mappingColDefault;
        }

        try (Reader inputReader = Files.newBufferedReader(path.toPath(), Charset.forName(encoding))) {
            CsvParser parser = new CsvParser(settings);
            List<String[]> parsedRows = parser.parseAll(inputReader);
            Iterator<String[]> rows = parsedRows.iterator();

            nrow = csv_get_nrows(path.getPath(), delim);

            header = rows.next();
            header = Arrays.stream(header)
                    .filter(h -> h != null && !h.trim().isEmpty())
                    .toArray(String[]::new);
            boolean[] cols_kept = this.mapColnamesAndGetColsKept(mapping_col);
            header_unify();
            coltypes_populate(cols_kept);
            ncol = get_len(coltypes);
            headerDropSKP();
            if (currentHeaderRef == null) {
                currentHeaderRef = this.header;
            }

            df = new ArrayList<>(get_len(coltypes));
            this.df_populate(coltypes);

            if (validateHeader(currentHeaderRef,header,numPolice)) {
                int i = 0;
                while (rows.hasNext()) {
                    int j = 0;
                    int k = 0;
                    String[] parsedRow = rows.next();
                    for (String s : parsedRow) {
                        if (coltypes[k] != SKP) {
                            df.get(j)[i] = get_lowercase_cell_of_type(s, coltypes[k], dateDefault);
                            j++;
                        }
                        k++;
                    }
                    i++;
                }
            } else {
                int[] headerIndexes = matchHeaders(currentHeaderRef,header);
                this.header = copyArray(currentHeaderRef);
                int i = 0;
                while (rows.hasNext()) {
                    int j = 0;
                    int k = 0;
                    String[] parsedRow = rows.next();
                    for (String s : parsedRow) {
                        if (coltypes[k] != SKP) {
                            df.get(headerIndexes[j])[i] = get_lowercase_cell_of_type(s, coltypes[k], dateDefault);
                            j++;
                        }
                        k++;
                    }
                    i++;
                }
            }
            this.date_autofill();
            this.createPivotTable();
            this.populateUniqueStatuts();
            this.populateStatutDateRangeMap();
        }
    }
    public void printPivotTable() {
        for (Map.Entry<String, Map<String, Map<String, Double>>> outerEntry : pivotTable.entrySet()) {
            String statut = outerEntry.getKey();
            System.out.println("Statut: " + statut);

            Map<String, Map<String, Double>> middleMap = outerEntry.getValue();
            for (Map.Entry<String, Map<String, Double>> middleEntry : middleMap.entrySet()) {
                String date_sous = middleEntry.getKey();
                System.out.println("\tDate Sous: " + date_sous);

                Map<String, Double> innerMap = middleEntry.getValue();
                for (Map.Entry<String, Double> innerEntry : innerMap.entrySet()) {
                    String date_surv = innerEntry.getKey();
                    Double montant_IP = innerEntry.getValue();
                    System.out.println("\t\tDate Surv: " + date_surv + " -> Montant IP: " + montant_IP);
                }
            }
        }
    }
    public void createPivotTable() {
        // define the format to capture only the month and year of a date
        SimpleDateFormat format = new SimpleDateFormat("MM-yyyy");

        // get column data
        Object[] montant_IPs = c("montant_IP");
        Object[] statuts = c("statut");
        Object[] date_sousArray = c("date_sous");
        Object[] date_survArray = c("date_surv");

        // iterate over the rows to populate the pivot map
        for (int i = 0; i < nrow; i++) {
            String statut = (String) statuts[i];
            String date_sous = format.format((Date) date_sousArray[i]);
            String date_surv = format.format((Date) date_survArray[i]);
            Double montant_IP = (Double) montant_IPs[i];

            pivotTable
                    .computeIfAbsent(statut, k -> new HashMap<>())
                    .computeIfAbsent(date_sous, k -> new HashMap<>())
                    .merge(date_surv, montant_IP, Double::sum);
        }

        for (Map.Entry<String, Map<String, Map<String, Double>>> outerEntry : pivotTable.entrySet()) {
            Map<String, Map<String, Double>> middleMap = outerEntry.getValue();

            for (Map.Entry<String, Map<String, Double>> middleEntry : middleMap.entrySet()) {
                Map<String, Double> innerMap = middleEntry.getValue();

                for (Map.Entry<String, Double> innerEntry : innerMap.entrySet()) {
                    double roundedValue = Math.round(innerEntry.getValue() * 100.0) / 100.0; // Round to 2 decimal places
                    innerEntry.setValue(roundedValue);
                }
            }
        }
    }
    void date_autofill() {
        // Indices for required columns in the current DF
        int indexDateSurv = find_in_arr_first_index(header, "date_surv");
        int indexDateSous = find_in_arr_first_index(header, "date_sous");
        int indexDateDecla = find_in_arr_first_index(header, "date_decla");

        // Indices for required columns in the ref_prog DF
        int indexContrat = find_in_arr_first_index(ref_prog.header, "n°contrat");
        int indexDateDebutRef = find_in_arr_first_index(ref_prog.header, "date_debut");
        int indexDateFinRef = find_in_arr_first_index(ref_prog.header, "date_fin");

        Date dateDebut = null; Date dateFin = null;
        for (int i = 0; i < ref_prog.nrow; i++) {
            if (this.numPolice.equalsIgnoreCase(ref_prog.c(indexContrat)[i].toString())) {
                dateDebut = (Date) ref_prog.c(indexDateDebutRef)[i];
                dateFin = (Date) ref_prog.c(indexDateFinRef)[i];
                break;
            }
        }
        if (dateDebut == null || dateFin == null) {
            throw new RuntimeException("ref_prog didn't find dates for " + numPolice);
        }

        for (int i = 0; i < nrow; i++) {
            Date dateSurv = (Date) c(indexDateSurv)[i];
            Date dateSous = (Date) c(indexDateSous)[i];

            if (dateSurv.equals(NA_DAT)) {
                if (!c(indexDateDecla)[i].equals(NA_DAT)) {
                    dateSurv = (Date) c(indexDateDecla)[i];
                } else if (!dateSous.equals(NA_DAT)) {
                    dateSurv = dateSous;
                } else {
                    dateSurv = dateDebut;
                }
            }
            if (dateSous.equals(NA_DAT)) {
                if(!dateSurv.equals(NA_DAT)){
                    dateSous = dateSurv;
                } else {
                    dateSous = dateDebut;
                }
            }

            date_transform(dateSurv, dateDebut, dateFin, indexDateSurv, i);
            date_transform(dateSous, dateDebut, dateFin, indexDateSous, i);
        }
    }
    void print(String statut) {
        Object[] statuts = this.c("statut");
        System.out.println(Arrays.toString(this.header));

        for (int i = 0; i<this.nrow; i++) {
            if (statuts[i].equals(statut)) {
                System.out.println(Arrays.toString(this.r(i)));
            }
        }
    }

    public void printMapped() {
        this.print(min(10,this.nrow));
    }
    public void printMapped(int rows) {
        for (String key : dfMapped.keySet()) {
            System.out.println("Key: " + key); // Print the map key
            ArrayList<Object[]> columns = dfMapped.get(key);

            // Ensure you don't exceed the available number of rows for each key
            int rowsToPrint = rows;
            if (columns.size() > 0) {
                rowsToPrint = Math.min(rows, columns.get(0).length);
            }

            // Print the header only once if it's same for all keys
            if (this.header != null) {
                System.out.println(Arrays.toString(this.header));
            }

            // Print rows for the current key
            for (int i = 0; i < rowsToPrint; i++) {
                Object[] row = new Object[columns.size()];
                for (int j = 0; j < columns.size(); j++) {
                    row[j] = columns.get(j)[i];
                }
                System.out.println(Arrays.toString(row));
            }

            System.out.println(); // Blank line to separate different keys
        }
    }
    private int computeDimSIN() throws IOException {
        File[] files = new File(String.valueOf(path)).listFiles();
        if (files == null || files.length == 0) return 0;

        List<File> fileList = new ArrayList<>(Arrays.asList(files));

        int dim;
        String metadataCurrent = wd + "metadata/sin_" + pays + "_nb_lignes_" + CURRENT_MONTH + ".txt";
        if (new File(metadataCurrent).exists()) {
            return readDimFromMetadata(metadataCurrent);
        }

        dim = getDimFrom0_SIN(fileList, delim);
        writeDimToMetadata(metadataCurrent, dim);

        return dim;
    }
    private int getDimFrom0_SIN(List<File> fileList, char delim) throws IOException {
        int dim = 0;
        for (File file : fileList) {
            dim += csv_get_nrows(file.getPath(), delim);
        }
        return dim;
    }
    public void df_populateMapped (String police, Col_types[] vectypes) {
        for (Col_types coltype : vectypes) {
            switch (coltype) {
                case STR -> this.dfMapped.get(police).add(new String[nrowMapped.get(police)]);
                case DBL -> this.dfMapped.get(police).add(new Double[nrowMapped.get(police)]);
                case DAT -> this.dfMapped.get(police).add(new Date[nrowMapped.get(police)]);
                default -> {
                }
            }
        }
    }
    public String[] headerKeep(String[] header, boolean[] toKeep) {
        int count = 0;
        for (boolean keep : toKeep) {
            if (keep) {
                count++;
            }
        }

        String[] newHeader = new String[count];
        int j = 0;
        for (int i = 0; i < header.length; i++) {
            if (toKeep[i]) {
                newHeader[j] = header[i];
                j++;
            }
        }

        return newHeader;
    }
    public void writeToCSV(String path) {
        // Ensure that there's data to write
        if (df == null || header == null) {
            System.err.println("Data or header is null. Cannot write to CSV.");
            return;
        }

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(path))) {
            // Write the header
            bw.write(String.join(",", header));
            bw.newLine();

            // Determine the number of rows by checking the length of the first column (assuming all columns have the same length)
            int numRows = df.get(0).length;

            // Write the rows
            for (int i = 0; i < numRows; i++) {
                for (int j = 0; j < df.size(); j++) {
                    Object cellValue = df.get(j)[i];
                    if (cellValue != null) {
                        // Write the cell value, followed by a comma unless it's the last cell in the row
                        bw.write(cellValue.toString());
                        if (j < df.size() - 1) {
                            bw.write(",");
                        }
                    }
                }
                bw.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
