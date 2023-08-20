package main.app;

import com.monitorjbl.xlsx.StreamingReader;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.apache.poi.ss.usermodel.*;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.min;
import static main.app.App.*;
import static main.app.App.ref_prog;
import static main.app.DF.Col_types.DAT;
import static main.app.DF.Col_types.SKP;

public class BaseSin extends BaseAccum {
    Object[] refProgrammesRow;
    String numPolice = "";
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

        initializeConnection();
        ref_prog = new DF(wd+"Référentiel programmes.csv", ';');

        long startTime = System.nanoTime();long endTime;long duration;long minutes;long seconds;
        List<File> fileList = Arrays.asList(Objects.requireNonNull(new File(wd + "source SIN/SPB France/").listFiles()));
        List<BaseSin> sinFrance = new ArrayList<>();
        for (File file : fileList) {
            sinFrance.add(new BaseSin(file,"France", "SPB France / Wakam"));
        }
//        DF fic_FRA = new BaseFic(wd + "source FIC/SPB France/","FIC France");
//        DF fic_ITA = new BaseFic(wd + "source FIC/SPB Italie/","DB Claims Italie");
//        BaseSin base_aux = new BaseSin(wd+"source SIN/SPB France/","France","SPB France / Wakam",0);
//        base_aux.printMapped(10);

//        DF sin_POL = new BaseSin(wd + "source SIN/SPB Pologne/","Pologne","SPB Pologne");
//        sin_POL.print(10);
//        System.out.println(sin_POL.nrow);
//        System.out.println(Arrays.toString(sin_POL.r(130000)));
        endTime = System.nanoTime();
        long elapsedTime = endTime - startTime;

//        System.out.println(elapsedTime/1000000);
        minutes = TimeUnit.NANOSECONDS.toMinutes(elapsedTime);
        seconds = TimeUnit.NANOSECONDS.toSeconds(elapsedTime) - TimeUnit.MINUTES.toSeconds(minutes);

        System.out.println("Elapsed Time: " + minutes + " minutes " + seconds + " seconds");
        closeConnection();

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
        computeMinMaxDatesForPolicies();    }
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
        computeMinMaxDatesForPolicies();
    }
    public BaseSin(String path, String pays, String mappingColDefault) throws IOException {
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

//        int dim = computeDimSIN();
//        System.out.println(dim);

        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true, delim);
        settings.trimValues(true);

        int i;
        boolean initialized = false;
        int[] headerIndexes = {0};
        String[] header_aux = {""};
        boolean[] cols_kept_aux;

        for (File file : fileList) {
            System.out.println(file.getName());
            String police = extractKeyFromFileName(file.getName());
            uniqueNumPoliceValues.add(police);
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
                Integer nr = csv_get_nrows(file.getPath(), delim);
                nrowMapped.put(police,nr);


                if (!initialized) {
                    header = rows.next();
                    header = Arrays.stream(header)
                            .filter(h -> h != null && !h.trim().isEmpty())
                            .toArray(String[]::new);
                    cols_kept_aux = this.mapColnamesAndGetColsKept(mapping_col);
                    header_unify();
                    coltypes_populate(cols_kept_aux);
                    ncol = get_len(coltypes);
                    dfMapped.put(police,new ArrayList<>(ncol));
                    df_populateMapped(police,coltypes);
                    headerDropSKP();

                    i = 0;
                    while (rows.hasNext()) {
                        int j = 0; // real cols iter
                        int k = 0; // coltypes iter
                        String[] parsedRow = rows.next();
                        //parsedRow = Arrays.copyOf(parsedRow, header.length);
                        for (String s : parsedRow) {
                            if (coltypes[k] != SKP) {
                                dfMapped.get(police).get(j)[i] = get_lowercase_cell_of_type(s, coltypes[k], dateDefault);
                                j++;
                            }
                            System.out.println(k);
                            k++;
                        }
                        System.out.println(i);
                        i++;
                    }
                    initialized = true;
                } else {
                    header_aux = rows.next();
                    header_aux = Arrays.stream(header_aux)
                            .filter(h -> h != null && !h.trim().isEmpty())
                            .toArray(String[]::new);
                    cols_kept_aux = this.mapColnamesAndGetColsKept(mapping_col,header_aux);
                    header_aux = header_unify_aux(header_aux);
                    Col_types[] coltypes_aux = coltypes_populate_aux(cols_kept_aux,header_aux);
                    header_aux = headerDropSKP(header_aux,coltypes_aux);

                    dfMapped.put(police,new ArrayList<>(ncol));
                    df_populateMapped(police,coltypes);

                    if (!validateHeader(header,header_aux,file.getName())) {
                        headerIndexes = matchHeaders(header,header_aux);
                        for (int index : headerIndexes) {
                            if (index == -1) {
                                System.out.println("Error in matching headers" + police);
                                //throw new IOException();
                            }
                        }
                        i = 0;
                        while (rows.hasNext()) {
                            int j = 0; // real cols INDEX iter
                            int k = 0; // coltypes iter
                            String[] parsedRow = rows.next();
                            for (String s : parsedRow) {
                                if (coltypes_aux[k] != SKP && headerIndexes[j] != -1) {
                                    dfMapped.get(police).get(headerIndexes[j])[i] = get_lowercase_cell_of_type(s, coltypes_aux[k], dateDefault);
                                    j++;
                                }
                                k++;
                            }
                            i++;
                        }
                        continue;
                    } else {
                        i = 0;
                        while (rows.hasNext()) {
                            int j = 0; // real cols iter
                            int k = 0; // coltypes iter
                            String[] parsedRow = rows.next();
                            //parsedRow = Arrays.copyOf(parsedRow, header.length);
                            for (String s : parsedRow) {
                                if (coltypes_aux[k] != SKP) {
                                    dfMapped.get(police).get(j)[i] = get_lowercase_cell_of_type(s, coltypes_aux[k], dateDefault);
//                                    System.out.println("real col " + j);
                                    j++;
                                }
//                                System.out.println("col " + k);
                                k++;
                            }
//                            System.out.println(i);
                            i++;
                        }
                    }
                }
            }
            date_autofill_agg_par_police(dfMapped.get(police));
        }
        populateUniqueStatutsDFMapped();
        computeMinMaxDatesForPoliciesMapped();
    }
    public BaseSin(String path, String pays, String mappingColDefault, int sql) throws IOException, SQLException {
        this.source = true;
        this.pays = pays;
//        this.path = path;
        this.referentialRow = getReferentialRow(new String[]{"source"});
        initializeConnection();

        List<File> fileList = Arrays.asList(Objects.requireNonNull(new File(path).listFiles()));
        if (fileList.isEmpty()) return;

        if (pays.equals("Pologne")) {
            delim = '\t';
        }
        if (pays.equals("France")) {
            delim = '|';
        }

        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true, delim);
        settings.trimValues(true);

        int i;
        boolean initialized = false;
        int[] headerIndexes = {0};
        String[] header_aux = {""};
        boolean[] cols_kept_aux;
        String mapping_col = "";

        for (File file : fileList) {
            System.out.println(file.getName());
            String police = extractKeyFromFileName(file.getName());
            uniqueNumPoliceValues.add(police);

            if (file.toString().contains("FRMP")) {
                mapping_col = "SPB France / ONEY";
            } else {
                mapping_col = mappingColDefault;
            }
//            if (!(file.toString().contains("FRMP") || file.toString().contains("DDP16"))) {
//                continue;
//            }
            try (Reader inputReader = Files.newBufferedReader(file.toPath(), Charset.forName(encoding))) {
                CsvParser parser = new CsvParser(settings);
                List<String[]> parsedRows = parser.parseAll(inputReader);
                Iterator<String[]> rows = parsedRows.iterator();
                Integer nr = csv_get_nrows(file.getPath(), delim);
                nrowMapped.put(police,nr);

                if (!initialized) {
                    header = rows.next();
                    header = Arrays.stream(header)
                            .filter(h -> h != null && !h.trim().isEmpty())
                            .toArray(String[]::new);
                    boolean[] cols_kept = this.mapColnamesAndGetColsKept(mapping_col);
                    header_unify();
                    coltypes_populate(cols_kept);
                    ncol = get_len(coltypes);
                    headerDropSKP();
                    createTable(police,header,coltypes);
                    insertData(police,parsedRows,header,coltypes,dateDefault);
                    initialized = true;
                }
                else {
                    header_aux = rows.next();
                    header_aux = Arrays.stream(header_aux)
                            .filter(h -> h != null && !h.trim().isEmpty())
                            .toArray(String[]::new);
                    cols_kept_aux = this.mapColnamesAndGetColsKept(mapping_col,header_aux);
                    header_aux = header_unify_aux(header_aux);
                    Col_types[] coltypes_aux = coltypes_populate_aux(cols_kept_aux,header_aux);
                    header_aux = headerDropSKP(header_aux,coltypes_aux);

                    if (!validateHeader(header,header_aux,file.getName())) {
                        headerIndexes = matchHeaders(header,header_aux);
                        createTable(police,header,coltypes);
                        insertDataWithIndices(police,parsedRows,header,coltypes,dateDefault,headerIndexes);
                    } else {
                        createTable(police,header,coltypes);
                        insertData(police,parsedRows,header,coltypes,dateDefault);
                    }
                }
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
            date_autofill_agg_par_police(dfMapped.get(police));
        }

//        populateUniqueStatutsDFMapped();
//        computeMinMaxDatesForPoliciesMapped();
        closeConnection();
    }
    public BaseSin(File path, String pays, String mappingColDefault) throws IOException, SQLException {
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
        numPolice = extractKeyFromFileName(path.getName());
        tableName = "sin_" + numPolice;

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
    public void populateUniqueStatutsDFMapped() {
        for (String key : dfMapped.keySet()) {
            Object[] statuts = c("statut", dfMapped.get(key));
            for (Object obj : statuts) {
                uniqueStatuts.add((String) obj);
            }
        }
    }
    public void computeMinMaxDatesForPoliciesMapped() {
        // Initialize dictionaries with extreme Date values for each policy number and each statut
        for (String numPolice : uniqueNumPoliceValues) {
            minDateMap.put(numPolice, new HashMap<>());
            maxDateMap.put(numPolice, new HashMap<>());
            for (String statut : uniqueStatuts) {
                minDateMap.get(numPolice).put(statut, new Date(Long.MAX_VALUE));
                maxDateMap.get(numPolice).put(statut, new Date(Long.MIN_VALUE));
            }
        }

        // Initially set the overall min and max dates for each statut to extreme values
        for (String statut : uniqueStatuts) {
            overallMinDateByStatut.put(statut, new Date(Long.MAX_VALUE));
            overallMaxDateByStatut.put(statut, new Date(Long.MIN_VALUE));
        }

        for (String key : dfMapped.keySet()) {
            Date[] dates = (Date[]) this.c("date_surv", dfMapped.get(key));
            String[] statuts = (String[]) this.c("statut", dfMapped.get(key));

            for (int i = 0; i < dates.length; i++) {
                Date currentDate = dates[i];
                String currentStatut = statuts[i];

                // If current date is before the stored min date for the current policy and statut, update it
                if (currentDate.before(minDateMap.get(key).get(currentStatut))) {
                    minDateMap.get(key).put(currentStatut, currentDate);
                }

                // If current date is after the stored max date for the current policy and statut, update it
                if (currentDate.after(maxDateMap.get(key).get(currentStatut))) {
                    maxDateMap.get(key).put(currentStatut, currentDate);
                }

                // Update overall minimum date for current statut if necessary
                if (currentDate.before(overallMinDateByStatut.get(currentStatut))) {
                    overallMinDateByStatut.put(currentStatut, currentDate);
                }

                // Update overall maximum date for current statut if necessary
                if (currentDate.after(overallMaxDateByStatut.get(currentStatut))) {
                    overallMaxDateByStatut.put(currentStatut, currentDate);
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

}
