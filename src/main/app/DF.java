package main.app;

import com.monitorjbl.xlsx.StreamingReader;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import static java.lang.Math.*;
import static main.app.App.*;
import static main.app.DF.Col_types.*;

public class DF implements Serializable {
    public static final String wd = "C:/Users/ozhukov/Downloads/wd/";
    public static final SimpleDateFormat dateDefault = new SimpleDateFormat("dd/MM/yyyy");
    public ArrayList<Object[]> df;
    public Col_types[] coltypes;
    public String[] header;
    public int ncol;
    public int nrow;
    public static SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy");
    public String fileName;
    public String fullPath;
    public String tableName;
    static Connection connection;
    private static final int BATCH_SIZE = 10000;

    public static void main(String[] args) throws Exception {
//        tdb2_ref = new DF(tdb2);
//        printMemoryUsage();
//        tdb2 = new DF(wd + "TDB Hors France.csv",';',0);
//        tdb2.populateFromGrilleTarif();
//        tdb2.saveToCSVFile_simple("populated");
//
//        tdb2fr = new DF(wd + "TDB France.csv",';',0);
//        tdb2fr.populateFromGrilleTarif();
//        tdb2fr.saveToCSVFile_simple("populated");

//        tdb2 = new DF(wd + "TDB Hors France_populated.csv",';',0);
//        tdb2coef = new DF(tdb2, 0);
//        tdb2coef.checkSumOfColumns();
//        tdb2coef.saveToCSVFile_sortedCoef("coef");


        tdb2fr = new DF(wd + "TDB France_populated.csv",';',0);
        tdb2coef = new DF(tdb2fr, 0);
        tdb2coef.checkSumOfColumns();
        tdb2coef.saveToCSVFile_sortedCoef("coef");


//        tdb2 = new DF(wd + "TDB Hors France_populated.csv",';',0);
//        tdb2coef.checkSumOfColumns();
//        tdb2coef.saveToCSVFile_sortedCoef("coef");

    }
    public DF(String path, char delim, Double sql) {
        fileName = path.substring(path.lastIndexOf("/") + 1);
        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true, delim);
        settings.trimValues(true);

        this.tableName = "ref_prog";  // or derive it from filename, e.g., filename.replace(".csv", "")
        try (Reader inputReader = new InputStreamReader(Files.newInputStream(
                new File(path).toPath()), encoding)) {
            CsvParser parser = new CsvParser(settings);
            List<String[]> parsedRows = parser.parseAll(inputReader);
            Iterator<String[]> rows = parsedRows.iterator();
            header = rows.next();
            for (int i = 0; i < header.length; i++) {
                header[i] = header[i].toLowerCase();
            }

            coltypes = new Col_types[header.length];
            String[] strColumns = {
                    "pays", "gestionnaire_1", "n°contrat", "acquisition des primes", "fait generateur", "produit eligible"
            };

            String[] dateColumns = {
                    "date_debut", "date_fin"
            };

            for (int i = 0; i < header.length; i++) {
                if (Arrays.asList(strColumns).contains(header[i])) {
                    coltypes[i] = STR;
                } else if (Arrays.asList(dateColumns).contains(header[i])) {
                    coltypes[i] = DAT; // Assuming you have a DAT enum value for date type columns
                } else {
                    coltypes[i] = SKP;
                }
            }
            nrow = parsedRows.size() - 1;
            ncol = get_len(coltypes);
            this.headerDropSKP();
            try {
                // Initialize database connection
                initializeConnection();

                // Create table in MySQL
                createTable(tableName, header, coltypes);

                // Populate table
                insertData(tableName, parsedRows, header, coltypes, dateDefault);

                this.coltypesDropSKP();
            } catch (SQLException | ParseException ex) {
                ex.printStackTrace();
            } finally {
                try {
                    closeConnection();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException ignored) {
        }
    } //ref prog sql
    public DF(String path, char delim) {
        String filename = path.substring(path.lastIndexOf("/") + 1);
        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true, delim);
        settings.trimValues(true);
        try (Reader inputReader = new InputStreamReader(Files.newInputStream(
                new File(path).toPath()), encoding)) {
            CsvParser parser = new CsvParser(settings);
            List<String[]> parsedRows = parser.parseAll(inputReader);
            Iterator<String[]> rows = parsedRows.iterator();
            header = rows.next();
            for (int i = 0; i < header.length; i++) {
                header[i] = header[i].toLowerCase();
            }

            coltypes = new Col_types[header.length];
            Arrays.fill(coltypes,STR);

            nrow = parsedRows.size() - 1;
            ncol = get_len(coltypes);
            df = new ArrayList<>(get_len(coltypes));
            this.df_populate(coltypes);

            int i = 0;
            while (rows.hasNext()) {
                int j = 0;
                int k = 0;
                String[] parsedRow = rows.next();
                for (String s : parsedRow) {
                    if (coltypes[k] != Col_types.SKP) {
                        df.get(j)[i] = get_lowercase_cell_of_type(s, coltypes[k],dateDefault);
                        j++;
                    }
                    k++;
                }
                i++;
            }
        } catch (IOException ignored) {
        }
        this.headerAndColtypesDropSKP();
    } //ref_prog
    public DF(String path, char delim, int tdb) {
        this.fullPath = path;
        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true, delim);
        settings.trimValues(true);
        try (Reader inputReader = new InputStreamReader(Files.newInputStream(
                new File(path).toPath()), encoding)) {
            CsvParser parser = new CsvParser(settings);
            List<String[]> parsedRows = parser.parseAll(inputReader);
            Iterator<String[]> rows = parsedRows.iterator();
            header = rows.next();
            for (int i = 0; i < header.length; i++) {
                header[i] = header[i].toLowerCase();
            }

            // Predefined array for string columns
            String[] stringColumns = {"identifiant_contrat", "reference"};
            String[] doubleColumns = {"montant_prime_assureur", "nombre_adhesions"};

            coltypes = new Col_types[header.length];
            Arrays.fill(coltypes, SKP);

            for (int i = 0; i < header.length; i++) {
                if (header[i].startsWith("m") && header[i].length() < 6) {
                    coltypes[i] = Col_types.FLT;
                } else if (header[i].startsWith("date_debut")) {
                    coltypes[i] = Col_types.DAT;
                } else if (Arrays.asList(stringColumns).contains(header[i])) {  // Check if the column name exists in the predefined array
                    coltypes[i] = Col_types.STR;
                } else if (Arrays.asList(doubleColumns).contains(header[i])) {  // Check if the column name exists in the predefined array
                    coltypes[i] = Col_types.DBL;
                }
            }

            nrow = parsedRows.size() - 1;
            ncol = get_len(coltypes);
            df = new ArrayList<>(get_len(coltypes));
            this.df_populate(coltypes);

            int i = 0;
            while (rows.hasNext()) {
                int j = 0;
                int k = 0;
                String[] parsedRow = rows.next();
                for (String s : parsedRow) {
                    if (coltypes[k] != Col_types.SKP) {
                        df.get(j)[i] = get_lowercase_cell_of_type(s, coltypes[k],dateDefault,0);
                        j++;
                    }
                    k++;
                }
                i++;
            }
        } catch (IOException ignored) {
        }
        this.headerAndColtypesDropSKP();
    }
    public DF(DF originalDF, int tdbToCoef) throws ParseException {
        // Group the originalDF by 'identifiant_contrat' and 'date_debut_periode_souscription'
        Map<String, List<Integer>> groupedIndices = new HashMap<>();

        for (int i = 0; i < originalDF.nrow; i++) {
            String key = originalDF.df.get(originalDF.getHeaderIndex("identifiant_contrat"))[i] + "_" + originalDF.df.get(originalDF.getHeaderIndex("date_debut_periode_souscription"))[i];
            groupedIndices.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }
        this.fullPath = originalDF.fullPath;
        this.nrow = groupedIndices.size();
        this.ncol = originalDF.ncol - 1;  // Excluding 'reference'
        this.df = new ArrayList<>(this.ncol);

        this.header = Arrays.stream(originalDF.header)
                .filter(h -> !h.equals("reference"))
                .toArray(String[]::new);

        // Initialize df based on coltypes but excluding the 'reference' column
        int referenceIndex = originalDF.getHeaderIndex("reference");
        for (int i = 0; i < originalDF.coltypes.length; i++) {
            if (i == referenceIndex) continue;  // Skip 'reference'

            switch (originalDF.coltypes[i]) {
                case STR -> this.df.add(new String[nrow]);
                case DBL -> this.df.add(new Double[nrow]);
                case FLT -> this.df.add(new Float[nrow]);
                case DAT -> this.df.add(new Date[nrow]);
            }
        }

        // Populate df based on transformation rules
        int newRowIdx = 0;
        for (String key : groupedIndices.keySet()) {
//            if (key.startsWith("icieljvd15_Wed May 01 00:00:00 CEST 2019")) {
//                System.out.println("here");
//            }
            List<Integer> rows = groupedIndices.get(key);
            for (int i = 0; i < this.header.length; i++) {
                int originalIdx = originalDF.getHeaderIndex(this.header[i]);
                switch (originalDF.coltypes[originalIdx]) {
                    case STR, DAT ->
                            this.df.get(i)[newRowIdx] = originalDF.df.get(originalIdx)[rows.get(0)];
                    case DBL -> {
                        double sum = 0;
                        for (int row : rows) {
                            sum += (Double) originalDF.df.get(originalIdx)[row];
                        }
                        if (this.header[i].equals("nombre_adhesions")) {
                            sum = Math.round(sum);  // round to 0 decimal places
                        } else if (this.header[i].equals("montant_prime_assureur")) {
                            sum = Math.round(sum * 100.0) / 100.0;  // round to 2 decimal places
                        }
                        this.df.get(i)[newRowIdx] = sum;
                    }
                    case FLT -> {
                        float avg = 0;
                        for (int row : rows) {
                            avg += (Float) originalDF.df.get(originalIdx)[row];
                        }
                        avg /= rows.size();
                        avg = Math.round(avg * 10000.0f) / 10000.0f;  // round to 4 significant digits
                        this.df.get(i)[newRowIdx] = avg;
                    }
                }
            }
            newRowIdx++;
        }

        // 1. Add two new columns at the beginning
        this.ncol += 2; // Increase column count
        this.df.add(0, new String[nrow]); // "statut contrat comptable"
        this.df.add(1, new String[nrow]); // "acquisition des primes"

        // Modify header accordingly
        this.header = Stream.concat(Stream.of("statut contrat comptable", "acquisition des primes"),
                Arrays.stream(this.header)).toArray(String[]::new);

        // 2. Populate the new columns using ref_prog
        Map<String, List<String>> refMap = new HashMap<>(); // Maps "n°contrat" to a list of values (statut and acquisition)
        for (int i = 0; i < ref_prog.nrow; i++) {
            String contractNo = (String) ref_prog.c("n°contrat")[i];
            String statut = (String) ref_prog.c("statut contrat comptable")[i];
            String acquisition = (String) ref_prog.c("acquisition des primes")[i];

            List<String> values = new ArrayList<>();
            values.add(statut);
            values.add(acquisition);
            refMap.put(contractNo, values);
        }

        Set<String> uniqueContracts = new HashSet<>();
        Map<String, Date> maxDateByContract = new HashMap<>();

        for (int i = 0; i < this.nrow; i++) {
            String contractId = (String) this.c("identifiant_contrat")[i];
            uniqueContracts.add(contractId);

            List<String> refValues = refMap.get(contractId);
            if (refValues != null && refValues.size() == 2) {
                this.c(0)[i] = refValues.get(0);
                this.c(1)[i] = refValues.get(1);
            }

            Date currentDate = (Date) this.c("date_debut_periode_souscription")[i];

            Date existingMaxDate = maxDateByContract.get(contractId);

            if (existingMaxDate == null || (currentDate != null && currentDate.after(existingMaxDate))) {
                maxDateByContract.put(contractId, currentDate);
            }

            // Check run-off status and overwrite the date if necessary
            String status = (String) this.c("statut contrat comptable")[i];
            if ("run off".equals(status)) {
                maxDateByContract.put(contractId, null);
            }
        }


        Set<Date> printedDates = new HashSet<>();

        for (Map.Entry<String, Date> entry : maxDateByContract.entrySet()) {
            if (!printedDates.contains(entry.getValue())) {
                System.out.println("Contract ID: " + entry.getKey() + ", Date: " + entry.getValue());
                printedDates.add(entry.getValue());
            }
        }

        // Define the end date
        Date endDate = dateDefault.parse("01/12/2025");

        // 2. Calculate the total number of fake months/rows to add
        int totalRowsToAdd = 0;
        Map<String, Integer> monthsToAddByContract = new HashMap<>();

        for (Map.Entry<String, Date> entry : maxDateByContract.entrySet()) {
            String contractId = entry.getKey();
            Date maxDate = entry.getValue();

            if (maxDate != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(maxDate);

                int monthsToAdd = 0;
                while (cal.getTime().before(endDate)) {
                    cal.add(Calendar.MONTH, 1);
                    monthsToAdd++;
                }

                monthsToAddByContract.put(contractId, monthsToAdd);
                totalRowsToAdd += monthsToAdd;
            }
        }

        // Adjust df size based on totalRowsToAdd
        for (int col = 0; col < this.ncol; col++) {
            Object[] currentColumn = this.df.get(col);
            currentColumn = Arrays.copyOf(currentColumn, this.nrow + totalRowsToAdd);
            this.df.set(col, currentColumn);
        }

        // 3. Add the rows to the DataFrame
        int currentRowIndex = this.nrow;
        for (String contractId : monthsToAddByContract.keySet()) {
            int monthsToAdd = monthsToAddByContract.get(contractId);
            int baseRowIndex = find_in_arr_first_index(this.c("identifiant_contrat"), contractId);
            Calendar cal = Calendar.getInstance();
            cal.setTime(maxDateByContract.get(contractId));

            for (int month = 0; month < monthsToAdd; month++) {
                cal.add(Calendar.MONTH, 1);

                for (int col = 0; col < this.ncol; col++) {
                    if (this.header[col].equals("date_debut_periode_souscription")) {
                        this.df.get(col)[currentRowIndex] = cal.getTime();
                    } else {
                        this.df.get(col)[currentRowIndex] = this.df.get(col)[baseRowIndex];
                    }
                }

                currentRowIndex++;
            }
        }

        this.nrow += totalRowsToAdd;


    }

    public DF(String path, char delim, Integer gri_tar) {
        String filename = path.substring(path.lastIndexOf("/") + 1);
        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true, delim);
        settings.trimValues(true);
        try (Reader inputReader = new InputStreamReader(Files.newInputStream(
                new File(path).toPath()), encoding)) {
            CsvParser parser = new CsvParser(settings);
            List<String[]> parsedRows = parser.parseAll(inputReader);
            Iterator<String[]> rows = parsedRows.iterator();
            header = rows.next();
            for (int i = 0; i < header.length; i++) {
                header[i] = header[i].toLowerCase();
            }

            coltypes = new Col_types[header.length];
            Arrays.fill(coltypes,STR);
            for (int i = 0; i < header.length; i++) {
                if (header[i].startsWith("m") && header[i].length() < 6) {
                    coltypes[i] = Col_types.FLT;
                } else if (header[i].startsWith("date debut") || header[i].startsWith("date fin")){
                    coltypes[i] = Col_types.DAT;
                }
            }

            nrow = parsedRows.size() - 1;
            ncol = get_len(coltypes);
            df = new ArrayList<>(get_len(coltypes));
            this.df_populate(coltypes);

            int i = 0;
            while (rows.hasNext()) {
                int j = 0;
                int k = 0;
                String[] parsedRow = rows.next();
                for (String s : parsedRow) {
                    if (coltypes[k] != Col_types.SKP) {
                        df.get(j)[i] = get_lowercase_cell_of_type(s, coltypes[k],dateDefault,0);
                        j++;
                    }
                    k++;
                }
                i++;
            }
        } catch (IOException ignored) {
        }
        this.headerAndColtypesDropSKP();
    }
    public DF(String path, char delim, boolean maj) {
        String filename = path.substring(path.lastIndexOf("/") + 1);
        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true, delim);
        settings.trimValues(true);
        try (Reader inputReader = new InputStreamReader(Files.newInputStream(
                new File(path).toPath()), encoding)) {
            CsvParser parser = new CsvParser(settings);
            List<String[]> parsedRows = parser.parseAll(inputReader);
            Iterator<String[]> rows = parsedRows.iterator();
            header = rows.next();
            for (int i = 0; i < header.length; i++) {
                header[i] = header[i].toLowerCase();
            }

            coltypes = new Col_types[header.length];
            String[] strColumns = {
                    "pays", "gestionnaire_1", "n°contrat", "acquisition des primes", "fait generateur", "produit eligible",
                    "statut contrat comptable", "acquisition des primes"
            };

            String[] dateColumns = {
                    "date_debut", "date_fin"
            };

            for (int i = 0; i < header.length; i++) {
                if (Arrays.asList(strColumns).contains(header[i])) {
                    coltypes[i] = STR;
                } else if (Arrays.asList(dateColumns).contains(header[i])) {
                    coltypes[i] = DAT; // Assuming you have a DAT enum value for date type columns
                } else {
                    coltypes[i] = SKP;
                }
            }
            nrow = parsedRows.size() - 1;
            ncol = get_len(coltypes);
            df = new ArrayList<>(get_len(coltypes));
            this.df_populate(coltypes);

            int i = 0;
            while (rows.hasNext()) {
                int j = 0;
                int k = 0;
                String[] parsedRow = rows.next();
                for (String s : parsedRow) {
                    if (coltypes[k] != Col_types.SKP) {
                        df.get(j)[i] = get_lowercase_cell_of_type(s, coltypes[k],dateDefault);
                        j++;
                    }
                    k++;
                }
                i++;
            }
        } catch (IOException ignored) {
        }
        this.headerAndColtypesDropSKP();
        this.remove_leading_zeros();
        this.consolidateContractRows();
    } //ref_prog
    private void consolidateContractRows() {
        Map<String, Integer> contractIndexMap = new HashMap<>(); // Maps "n°contrat" to its index in df
        int dateDebutIndex = -1;
        int dateFinIndex = -1;
        int noContratIndex = -1;

        // Find the indices of the columns
        for (int i = 0; i < header.length; i++) {
            if (header[i].equals("date_debut")) {
                dateDebutIndex = i;
            } else if (header[i].equals("date_fin")) {
                dateFinIndex = i;
            } else if (header[i].equals("n°contrat")) {
                noContratIndex = i;
            }
        }

        List<Integer> rowsToRemove = new ArrayList<>(); // List to track rows to be removed

        for (int i = 0; i < nrow; i++) {
            String noContrat = (String) df.get(noContratIndex)[i];
            if (contractIndexMap.containsKey(noContrat)) {
                // Compare and update the date_debut and date_fin
                Date currentDebut = (Date) df.get(dateDebutIndex)[i];
                Date currentFin = (Date) df.get(dateFinIndex)[i];
                Date existingDebut = (Date) df.get(dateDebutIndex)[contractIndexMap.get(noContrat)];
                Date existingFin = (Date) df.get(dateFinIndex)[contractIndexMap.get(noContrat)];

                if (currentDebut.before(existingDebut)) {
                    df.get(dateDebutIndex)[contractIndexMap.get(noContrat)] = currentDebut;
                }
                if (currentFin.after(existingFin)) {
                    df.get(dateFinIndex)[contractIndexMap.get(noContrat)] = currentFin;
                }

                // Mark this row to be removed
                rowsToRemove.add(i);

            } else {
                // First appearance of this noContrat
                contractIndexMap.put(noContrat, i);
            }
        }

        // Remove marked rows
        for (int i = rowsToRemove.size() - 1; i >= 0; i--) {
            int rowIndex = rowsToRemove.get(i);
            for (int j = 0; j < df.size(); j++) {
                Object[] col = df.get(j);
                Object[] newCol = new Object[col.length - 1];
                for (int k = 0, m = 0; k < col.length; k++) {
                    if (k != rowIndex) {
                        newCol[m++] = col[k];
                    }
                }
                df.set(j, newCol);
            }
            nrow--; // Decrease the number of rows counter
        }
    }
    void refProgGetPolice(String police) {
        Object[] polices = ref_prog.c("n°contrat");
        System.out.println(Arrays.toString(ref_prog.header));

        for (int i = 0; i<ref_prog.nrow; i++) {
            if (police.equalsIgnoreCase((String) polices[i])) {
                System.out.println(Arrays.toString(ref_prog.r(i)));
            }
        }
    }
    public DF (String path, String sheet_name) throws IOException {

        InputStream is = Files.newInputStream(new File(path).toPath());
        Workbook workbook = StreamingReader.builder()
                .rowCacheSize(1)      // number of rows to keep in memory (defaults to 10)
                .bufferSize(4096)     // buffer size to use when reading InputStream to file (defaults to 1024)
                .open(is);

//        String sheet_name = workbook.getSheetName(0);
        Sheet sheet = workbook.getSheet(sheet_name);
        Iterator<Row> rowIter = sheet.rowIterator();
        Row row = rowIter.next();
        nrow = sheet.getLastRowNum();
        ncol = row.getLastCellNum();
        header = new String[ncol];
        int i = 0;
        for (Cell c : row) {
            header[i] = c.getStringCellValue();
            i++;
        }

        Row secondRow = rowIter.hasNext() ? rowIter.next() : null;

        if (secondRow != null) {
            coltypes = detectColumnTypesXlsx(secondRow);
        } else {
            coltypes = new Col_types[ncol];
            Arrays.fill(coltypes, Col_types.STR);  // Default types to STR if there's no second row
        }

        df = new ArrayList<>(ncol);
        this.df_populate(coltypes);

        int row_number = 0;
        // Process the second row
        if (secondRow != null) {
            processRow(secondRow, row_number);
            row_number++;
        }

// Continue processing the remaining rows
        while (rowIter.hasNext()) {
            row = rowIter.next();
            processRow(row, row_number);
            row_number++;
        }
    } //ref_triangle //mapping
    public DF (String path, String sheet_name, boolean uppercase) throws IOException {

        InputStream is = Files.newInputStream(new File(path).toPath());
        Workbook workbook = StreamingReader.builder()
                .rowCacheSize(1)      // number of rows to keep in memory (defaults to 10)
                .bufferSize(4096)     // buffer size to use when reading InputStream to file (defaults to 1024)
                .open(is);

//        String sheet_name = workbook.getSheetName(0);
        Sheet sheet = workbook.getSheet(sheet_name);
        Iterator<Row> rowIter = sheet.rowIterator();
        Row row = rowIter.next();
        nrow = sheet.getLastRowNum();
        ncol = row.getLastCellNum();
        header = new String[ncol];
        int i = 0;
        for (Cell c : row) {
            header[i] = c.getStringCellValue();
            i++;
        }

        coltypes = new Col_types[ncol];
        Arrays.fill(coltypes, STR);

        df = new ArrayList<>(ncol);
        this.df_populate(coltypes);

        int col_iterator;
        int row_number = 0;
        while(rowIter.hasNext()) {
            row = rowIter.next();
            col_iterator = 0;
            for (int c = 0; c < this.ncol; c++) {
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
                df.get(col_iterator)[row_number] = parseCell(cell_i, coltypes[c], dateDefault);
                col_iterator++;
            }
            row_number++;
        }
    } //source
    public DF (ArrayList<Object[]> base) {
        this.df = base;
    }
    public DF (DF old_base, boolean[] keep) {
        this.coltypes = old_base.coltypes;
        this.header = old_base.header;
        this.ncol = old_base.ncol;
        this.nrow = old_base.nrow;
        this.df = new ArrayList<>();
        this.df_populate(this.coltypes);
        for (int i = 0; i < this.ncol; i++) {
            System.arraycopy(old_base.df.get(i), 0,this.df.get(i),0,this.nrow);
        }
        this.keep_rows(keep);
    }
    public DF (DF old_base, boolean[] keep, boolean keep_cols) {
        this.coltypes = old_base.coltypes;
        this.header = old_base.header;
        this.ncol = old_base.ncol;
        this.nrow = old_base.nrow;
        this.df = new ArrayList<>();
        this.df_populate(this.coltypes);
        for (int i = 0; i < this.ncol; i++) {
            System.arraycopy(old_base.df.get(i), 0,this.df.get(i),0,this.nrow);
        }
        this.keep_cols(keep);
    }
    public DF (DF old_base, String crit) {
        this.coltypes = old_base.coltypes;
        this.header = old_base.header;
        this.ncol = old_base.ncol;
        this.nrow = old_base.nrow;
        this.df = new ArrayList<>();
        this.df_populate(this.coltypes);
        for (int i = 0; i < this.ncol; i++) {
            System.arraycopy(old_base.df.get(i), 0,this.df.get(i),0,this.nrow);
        }
        boolean[] keep = new boolean[this.nrow];
        for (int i = 0; i < this.nrow; i++) {
            keep[i] = this.c(0)[i].equals(crit);
        }
        this.keep_rows(keep);
    }
    public DF (DF old_base) {
        this.fullPath = old_base.fullPath;
        this.coltypes = old_base.coltypes;
        this.header = old_base.header;
        this.ncol = old_base.ncol;
        this.nrow = old_base.nrow;
        this.df = new ArrayList<>();
        this.df_populate(this.coltypes);
        for (int i = 0; i < this.ncol; i++) {
            System.arraycopy(old_base.df.get(i), 0,this.df.get(i),0,this.nrow);
        }
    }
    public DF (String[] arr) {
        this.nrow = 0;
        this.ncol = arr.length;
        this.coltypes = new Col_types[ncol];
        Arrays.fill(this.coltypes, STR);
        this.header = arr;
        this.df = new ArrayList<>();
        this.df_populate(this.coltypes);
    }
    public DF () {

    }
    @SuppressWarnings("SqlResolve")
    public void date_autofill_sql() throws SQLException {
        String updateSQL = "UPDATE `" + tableName + "` AS t " +
                "JOIN ref_prog AS r ON t.num_police = r.n°contrat " +
                "SET " +
                "t.date_surv = CASE " +
                "WHEN t.date_surv IS NULL AND t.date_decla IS NOT NULL THEN LAST_DAY(t.date_decla - INTERVAL 1 MONTH) + INTERVAL 1 DAY " +
                "WHEN t.date_surv IS NULL AND t.date_sous IS NOT NULL THEN LAST_DAY(t.date_sous - INTERVAL 1 MONTH) + INTERVAL 1 DAY " +
                "WHEN t.date_surv IS NULL THEN LAST_DAY(r.date_debut - INTERVAL 1 MONTH) + INTERVAL 1 DAY " +
                "WHEN t.date_surv < r.date_debut THEN LAST_DAY(r.date_debut - INTERVAL 1 MONTH) + INTERVAL 1 DAY " +
                "WHEN t.date_surv > r.date_fin THEN LAST_DAY(r.date_fin - INTERVAL 1 MONTH) + INTERVAL 1 DAY " +
                "ELSE LAST_DAY(t.date_surv - INTERVAL 1 MONTH) + INTERVAL 1 DAY " +
                "END, " +
                "t.date_sous = CASE " +
                "WHEN t.date_sous IS NULL AND t.date_surv IS NOT NULL THEN LAST_DAY(t.date_surv - INTERVAL 1 MONTH) + INTERVAL 1 DAY " +
                "WHEN t.date_sous IS NULL THEN LAST_DAY(r.date_debut - INTERVAL 1 MONTH) + INTERVAL 1 DAY " +
                "WHEN t.date_sous < r.date_debut THEN LAST_DAY(r.date_debut - INTERVAL 1 MONTH) + INTERVAL 1 DAY " +
                "WHEN t.date_sous > r.date_fin THEN LAST_DAY(r.date_fin - INTERVAL 1 MONTH) + INTERVAL 1 DAY " +
                "ELSE LAST_DAY(t.date_sous - INTERVAL 1 MONTH) + INTERVAL 1 DAY " +
                "END";

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(updateSQL);
        }
    }
    private Col_types[] detectColumnTypesXlsx(Row headerRow) {
        int totalColumns = headerRow.getLastCellNum();
        Col_types[] detectedTypes = new Col_types[totalColumns];

        // Default all columns to STR
        Arrays.fill(detectedTypes, Col_types.STR);

        for (Cell c : headerRow) {
            CellType cellType = c.getCellTypeEnum();

            if (cellType == CellType.NUMERIC) {
                if (DateUtil.isCellDateFormatted(c)) {
                    detectedTypes[c.getColumnIndex()] = Col_types.DAT;
                } else {
                    detectedTypes[c.getColumnIndex()] = Col_types.DBL;
                }
            } // No need for an 'else' branch, as the array is already filled with STR
        }

        return detectedTypes;
    }
    public void populateFromGrilleTarif() throws Exception {
        Stopwatch stopwatch = new Stopwatch();
        stopwatch.start();

        // Step 1: Indexing using a Map
        Map<String, List<Integer>> contractRefRowIndexMap = new HashMap<>();
        for (int i = 0; i < grille_tarif.nrow; i++) {
            String contract = (String) grille_tarif.c("identifiant_contrat")[i];
            String reference = (String) grille_tarif.c("reference")[i];

            String contractRefKey = contract + "_" + reference;
            contractRefRowIndexMap.computeIfAbsent(contractRefKey, k -> new ArrayList<>()).add(i);
        }
        stopwatch.printElapsedTime("mapped");

        // Map to store matching rows for each row of the current DF
        Map<Integer, Integer> matchingRowMap = new HashMap<>();

        // Populate the matchingRowMap
        for (int i = 0; i < this.nrow; i++) {
            String contract = (String) this.c("identifiant_contrat")[i];
            String reference = (String) this.c("reference")[i];
            Date thisStartDate = (Date) this.c("date_debut_periode_souscription")[i];

            String contractRefKey = contract + "_" + reference;
            List<Integer> rowsToConsider = contractRefRowIndexMap.get(contractRefKey);
            if (rowsToConsider == null) continue;

            for (Integer rowIndex : rowsToConsider) {
                Date startDate = (Date) grille_tarif.c("date debut tarif")[rowIndex];
                Date endDate = (Date) grille_tarif.c("date fin tarif")[rowIndex];

                if (!thisStartDate.before(startDate) && !thisStartDate.after(endDate)) {
                    matchingRowMap.put(i, rowIndex);
                    break;
                }
            }
        }
        stopwatch.printElapsedTime("matched");

        // Find starting column of "m"
        int startIndexTdb = find_in_arr_first_index(this.header,"m");
        int startIndexGt = find_in_arr_first_index(grille_tarif.header,"m");
        if (startIndexTdb == -1 || startIndexGt == -1) throw new Exception("column m not found");

        // Directly assign values from grille_tarif to the current DF using the precomputed row indices
        for (int i = 0; i < this.nrow; i++) {
            System.out.println(i);

            Integer matchingRow = matchingRowMap.get(i);
            if (matchingRow != null) {
                for (int col = 0; col <= 200; col++) {
                    this.df.get(startIndexTdb + col)[i] = grille_tarif.df.get(startIndexGt + col)[matchingRow];
                }
            }
//            else {
//                for (int col = 0; col <= 200; col++) {
//                    this.df.get(startIndexTdb + col)[i] = 0d;
//                }
//            }
        }
    }
    public void checkSumOfColumns() throws Exception {
        // Find starting column of "m"
        int startIndex = find_in_arr_first_index(this.header,"m");

        if (startIndex == -1) {
            throw new Exception("col m not found");
        }

        // Iterate over each row
        for (int i = 0; i < this.nrow; i++) {
            float sum = 0;

            // Sum values from columns "m" to "m+200"
            for (int col = 0; col <= 200 && (startIndex + col) < this.header.length; col++) {
                sum += (float) this.df.get(startIndex + col)[i]; // Assuming they are all floats.
            }
            if(i==90717) {
                System.out.println("here");
            }
            // Round the sum to 3 decimal places
            sum = (float) (Math.round(sum * 100.0) / 100.0);

            // Check if the sum is different from 1
            if (sum != 1.0) {
                System.out.println("Row " + (i + 1) + ": " + this.df.get(0)[i] + ", " + this.df.get(1)[i] + ", " + this.df.get(2)[i]);
            }
        }
    }
    public void filterUnmatchedRows() {
        // Step 1: Populate the HashMap with keys from grille_tarif
        Set<String> grilleKeys = new HashSet<>();
        for (int i = 0; i < grille_tarif.nrow; i++) {
            String contract = (String) grille_tarif.c("identifiant_contrat")[i];
            String reference = (String) grille_tarif.c("reference")[i];
            grilleKeys.add(contract + "_" + reference);
        }

        // Step 2: Check for existence of tdb2 keys in grilleKeys
        boolean[] rowsToDelete = new boolean[nrow];
        for (int j = 0; j < nrow; j++) {
            String contract = (String) c("identifiant_contrat")[j];
            String reference = (String) c("reference")[j];
            if(reference.equalsIgnoreCase("ICIGSAC15_151.01-400")) {
                System.out.println("here");
            }
            if (grilleKeys.contains(contract + "_" + reference)) {
                rowsToDelete[j] = true;
            }
        }

        // Step 3: Recreate the df without deleted rows
        ArrayList<Object[]> newDF = new ArrayList<>();
        for (int col = 0; col < ncol; col++) {
            ArrayList<Object> columnData = new ArrayList<>();
            for (int row = 0; row < nrow; row++) {
                if (!rowsToDelete[row]) {
                    columnData.add(df.get(col)[row]);
                }
            }
            newDF.add(columnData.toArray());
        }

        // Update the dataframe and row count
        df = newDF;
        nrow = df.get(0).length;
    }
    public int getHeaderIndex(String colName) {
        for (int i = 0; i < header.length; i++) {
            if (header[i].equals(colName)) {
                return i;
            }
        }
        return -1;  // Not found
    }
    private void processRow(Row row, int row_number) {
        int col_iterator = 0;
        for (int c = 0; c < this.ncol; c++) {
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
            df.get(col_iterator)[row_number] = parseLowercaseCell(cell_i, coltypes[c], dateDefault);
            col_iterator++;
        }
    }
    private void date_transform(ResultSet rs, Date date, Date dateDebutRef, Date dateFinRef, String columnName) throws SQLException {
        if (date.before(dateDebutRef)) {
            date = dateDebutRef;
        }
        if (date.after(dateFinRef)) {
            date = dateFinRef;
        }

        // Change the date to the 1st day of the month
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        date = cal.getTime();

        // Update the date in the current ResultSet row
        rs.updateDate(columnName, new java.sql.Date(date.getTime()));
        rs.updateRow();
    }
    public static void initializeConnection() throws SQLException {
        String url = "jdbc:mysql://localhost:3306/ici";
        String username = "root";
        String password = "Hewlett77*";
        connection = DriverManager.getConnection(url, username, password);
    }
    public static void closeConnection() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }
    private String mapColTypeToSQLType(Col_types colType) {
        return switch (colType) {
            case DAT -> "DATE";
            case DBL -> "DOUBLE";
            case SKP -> ""; // This might not be necessary in SQL since you are skipping.
            default -> "VARCHAR(255)";
        };
    }
    void createTable(String tableName, String[] headers, Col_types[] coltypes) throws SQLException {
        StringBuilder createTableSQL = new StringBuilder("CREATE TABLE `" + tableName + "` (");
        int h = 0;
        for (Col_types coltype : coltypes) {
            if (coltype != Col_types.SKP) {
                createTableSQL.append("`").append(headers[h]).append("` ").append(mapColTypeToSQLType(coltype)).append(",");
                h++;
            }
        }
        createTableSQL.setLength(createTableSQL.length() - 1);  // remove last comma
        createTableSQL.append(")");

        String sqlToExecute = createTableSQL.toString();
        System.out.println(sqlToExecute);  // Log it or print it for debugging purposes

        Statement stmt = connection.createStatement();
        stmt.execute(sqlToExecute);
    }

    void insertData(String tableName, List<String[]> dataList, String[] headers, Col_types[] coltypes, SimpleDateFormat dateDefault) throws SQLException, ParseException, ParseException {
        dataList.remove(0);//skip header
        StringBuilder insertSQL = new StringBuilder("INSERT INTO `" + tableName + "` (");

        // Adding columns based on coltypes
        int h = 0;
        for (Col_types coltype : coltypes) {
            if (coltype != Col_types.SKP) {
                insertSQL.append("`").append(headers[h]).append("`,");  // Added backticks around column names
                h++;
            }
        }
        insertSQL.setLength(insertSQL.length() - 1);  // remove last comma before adding values
        insertSQL.append(") VALUES (");

        // Add placeholders for values (excluding the ID column)
        for (int i = 0; i < coltypes.length; i++) {
            if (coltypes[i] != Col_types.SKP) {
                insertSQL.append("?,");
            }
        }
        insertSQL.setLength(insertSQL.length() - 1);  // remove last comma
        insertSQL.append(")");

        PreparedStatement pstmt = connection.prepareStatement(insertSQL.toString());
        int rowCount = 0;

        SimpleDateFormat sqlDateFormat = new SimpleDateFormat("yyyy-MM-dd"); // MySQL's DATE format

        for (String[] dataRow : dataList) {
            int parameterIndex = 1;
            for (int i = 0; i < dataRow.length; i++) {
                if (coltypes[i] != Col_types.SKP) {
                    if (coltypes[i] == Col_types.DAT) {
                        if (dataRow[i] != null && !dataRow[i].isEmpty()) {
                            try {
                                // Convert date from dd/MM/yyyy to yyyy-MM-dd
                                Date date = dateDefault.parse(dataRow[i]);
                                pstmt.setString(parameterIndex, sqlDateFormat.format(date));
                            } catch (ParseException e) {
                                pstmt.setNull(parameterIndex, java.sql.Types.DATE);
                            }
                        } else {
                            pstmt.setNull(parameterIndex, java.sql.Types.DATE);
                        }

                    } else {
                        pstmt.setString(parameterIndex, dataRow[i]);
                    }
                    parameterIndex++;
                }
            }
            pstmt.addBatch();
            rowCount++;

            if (rowCount % BATCH_SIZE == 0) {
                pstmt.executeBatch();
                pstmt.clearBatch();
            }
        }
        if (rowCount % BATCH_SIZE != 0) {
            pstmt.executeBatch();
        }
    }
    void insertDataWithIndices(String tableName, List<String[]> dataList, String[] headers, Col_types[] coltypes, SimpleDateFormat dateDefault, int[] indexes) throws SQLException, ParseException, ParseException {
        dataList.remove(0);//skip header
        StringBuilder insertSQL = new StringBuilder("INSERT INTO `" + tableName + "` (");

        // Adding columns based on coltypes
        int h = 0;
        for (Col_types coltype : coltypes) {
            if (coltype != Col_types.SKP) {
                insertSQL.append(headers[h]).append(",");
                h++;
            }
        }
        insertSQL.setLength(insertSQL.length() - 1);  // remove last comma before adding values
        insertSQL.append(") VALUES (");

        // Add placeholders for values (excluding the ID column)
        for (int i = 0; i < coltypes.length; i++) {
            if (coltypes[i] != Col_types.SKP) {
                insertSQL.append("?,");
            }
        }
        insertSQL.setLength(insertSQL.length() - 1);  // remove last comma
        insertSQL.append(")");

        PreparedStatement pstmt = connection.prepareStatement(insertSQL.toString());
        int rowCount = 0;

        SimpleDateFormat sqlDateFormat = new SimpleDateFormat("yyyy-MM-dd"); // MySQL's DATE format

        for (String[] dataRow : dataList) {
            int parameterIndex = 0;
            for (int i = 0; i < dataRow.length; i++) {
                if (coltypes[i] != Col_types.SKP) {
                    if (coltypes[i] == Col_types.DAT) {
                        if (dataRow[i] != null && !dataRow[i].isEmpty()) {
                            try {
                                // Convert date from dd/MM/yyyy to yyyy-MM-dd
                                Date date = dateDefault.parse(dataRow[i]);
                                pstmt.setString(indexes[parameterIndex] + 1, sqlDateFormat.format(date));
                            } catch (ParseException e) {
                                pstmt.setNull(indexes[parameterIndex] + 1, java.sql.Types.DATE);
                            }
                        } else {
                            pstmt.setNull(indexes[parameterIndex] + 1, java.sql.Types.DATE);
                        }

                    } else {
                        pstmt.setString(indexes[parameterIndex] + 1, dataRow[i]);
                    }
                    parameterIndex++;
                }
            }
            pstmt.addBatch();
            rowCount++;

            if (rowCount % BATCH_SIZE == 0) {
                pstmt.executeBatch();
                pstmt.clearBatch();
            }
        }
        if (rowCount % BATCH_SIZE != 0) {
            pstmt.executeBatch();
        }
    }
    public static List<Object> getColumn(String tableName, String columnName, Col_types colType) throws SQLException {
        List<Object> columnData = new ArrayList<>();
        String query = "SELECT " + columnName + " FROM `" + tableName + "`";
        PreparedStatement pstmt = connection.prepareStatement(query);
        ResultSet rs = pstmt.executeQuery();
        while (rs.next()) {
            switch (colType) {
                case STR -> {
                    while (rs.next()) {
                        columnData.add(rs.getString(columnName));
                    }
                }
                case DAT -> {
                    while (rs.next()) {
                        columnData.add(rs.getDate(columnName));
                    }
                }
                case DBL -> {
                    while (rs.next()) {
                        columnData.add(rs.getDouble(columnName));
                    }
                }
                default -> {}
            }
        }
        return columnData;
    }
    public static Object[] getRowByOrder(String tableName, int order) throws SQLException {
        String query = "SELECT * FROM `" + tableName + "` WHERE ID = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setInt(1, order);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Object[] row = new Object[rs.getMetaData().getColumnCount()];
                    for (int i = 0; i < row.length; i++) {
                        row[i] = rs.getObject(i + 1);
                    }
                    return row;
                } else {
                    throw new IllegalArgumentException("No row found with the specified order.");
                }
            }
        }
    }
    public String getNameItaly(String fileName) {
        // Pattern for the first type of file (GS files).
        Pattern pattern1 = Pattern.compile("ICI(.*?)\\sat");

        // Pattern for the second type of file.
        Pattern pattern2 = Pattern.compile("-(IC[A-Z0-9]+)\\.csv$");

        Matcher matcher1 = pattern1.matcher(fileName);
        Matcher matcher2 = pattern2.matcher(fileName);

        if (matcher1.find()) {
            // Remove all spaces from the matched string
            return matcher1.group(1).replaceAll("\\s+", "");
        } else if (matcher2.find()) {
            return matcher2.group(1);
        } else {
            // Return null or an empty string if no match is found.
            return null;
        }
    }
    public DF mappingFiltre(String inputColumn) {
        int formatICIIndex = -1;
        int inputColumnIndex = -1;

        for (int i = 0; i < header.length; i++) {
            if (header[i].equals("Format ICI")) {
                formatICIIndex = i;
            }
            if (header[i].equals(inputColumn)) {
                inputColumnIndex = i;
            }
        }

        if (formatICIIndex == -1) {
            formatICIIndex = 0;
        }

        if (inputColumnIndex == -1) {
            return null;
        }

        ArrayList<Object[]> newDF = new ArrayList<>();
        newDF.add(df.get(formatICIIndex));
        newDF.add(df.get(inputColumnIndex));

        Col_types[] newColTypes = {STR, STR};
        String[] newHeader = {"Format ICI", inputColumn};

        DF result = new DF();
        result.df = newDF;
        result.coltypes = newColTypes;
        result.header = newHeader;
        result.ncol = 2;
        result.nrow = this.nrow;

        return result;
    }
    public void mappingSubstitute(DF mapping) {
        for (int i = 0; i < this.header.length; i++) {
            String currentColumnName = this.header[i];

            for (int j = 0; j < mapping.nrow; j++) {
                String mappingName = (String) mapping.df.get(1)[j];
                String referenceName = (String) mapping.df.get(0)[j];

                if (normalize(currentColumnName).equalsIgnoreCase(normalize(mappingName))) {
                    this.header[i] = referenceName;
                    break;
                }
            }
        }
    }
    public boolean isSameMonth(String monthHeader, Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);

        // Extract the year from monthHeader.
        int year = Integer.parseInt("20" + monthHeader.substring(monthHeader.length() - 2));

        // Get the Calendar month constant from the monthMap.
        Integer month = Estimate.monthMap.get(monthHeader.substring(0, 4));
        if (month == null) {
            return false; // If the monthHeader is not recognized.
        }

        // Check if the year and month of the passed date match the provided month header.
        return cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month;
    }
    public boolean isEarlierSvD(String dateStr, Date dateX, String formatStr) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(formatStr);

        try {
            Date dateFromString = dateFormat.parse(dateStr); // Parse the string to a Date object
            Date formattedDateX = dateFormat.parse(dateFormat.format(dateX));

            return dateFromString.before(formattedDateX);
        } catch (Exception e) {
            e.printStackTrace();
            return false;  // or handle the error differently
        }
    }
    public boolean isLaterSvD(String dateStr, Date dateX, String formatStr) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(formatStr);

        try {
            Date dateFromString = dateFormat.parse(dateStr); // Parse the string to a Date object
            Date formattedDateX = dateFormat.parse(dateFormat.format(dateX));

            return dateFromString.after(formattedDateX);
        } catch (Exception e) {
            e.printStackTrace();
            return false;  // or handle the error differently
        }
    }

    public static String[] copyArray(String[] source) {
        String[] target = new String[source.length];
        System.arraycopy(source, 0, target, 0, source.length);
        return target;
    }
    String normalize(String input) {
        return input.replace("é", "e").toLowerCase();
    }
    public void checkMissingMontantIP() {
        System.out.println("Filename: " + fileName);

        int columnIndex = -1;
        for (int i = 0; i < header.length; i++) {
            if (header[i].equals("montant_IP")) {
                columnIndex = i;
                break;
            }
        }

        Object[] column = c(columnIndex);

        for (int rowIndex = 0; rowIndex < column.length; rowIndex++) {
            if (column[rowIndex].equals(NA_DBL)) {
                Object[] entireRow = r(rowIndex);
                System.out.println("Row with missing 'montant_IP' value at index " + rowIndex + ": " + Arrays.toString(entireRow));
            }
        }
    }
    public Object parseCell(Cell cell_i, Col_types colType, SimpleDateFormat dateFormatter) {
        Object cellValue = null;
        if (cell_i.getCellTypeEnum() == CellType.FORMULA) {
            if (cell_i.getCachedFormulaResultTypeEnum() == CellType.ERROR) {
                cellValue = get_cell_of_type(cell_i.getCellFormula(), colType, dateFormatter); // bad formula
            } else {
                cellValue = get_cell_of_type(cell_i.getStringCellValue(), colType, dateFormatter); // good formula
            }
        } else {
            if (cell_i.getCellTypeEnum() == CellType.NUMERIC) {
                if (DateUtil.isCellDateFormatted(cell_i)) {
                    return cell_i.getDateCellValue();
                } else {
                    // Handle numeric cells based on the expected Col_types
                    if (colType == Col_types.DBL) {
                        return cell_i.getNumericCellValue(); // return the numeric value directly for DBL type
                    } else if (colType == STR) {
                        return Double.toString(cell_i.getNumericCellValue()); // convert to string for STR type
                    }
                }
            } else {
                cellValue = get_cell_of_type(cell_i.getStringCellValue(), colType, dateFormatter); // no formula
            }
        }
        return cellValue;
    }
    public Object parseLowercaseCell(Cell cell_i, Col_types colType, SimpleDateFormat dateFormatter) {
        Object cellValue = null;
        if (cell_i.getCellTypeEnum() == CellType.FORMULA) {
            if (cell_i.getCachedFormulaResultTypeEnum() == CellType.ERROR) {
                cellValue = get_lowercase_cell_of_type(cell_i.getCellFormula(), colType, dateFormatter); // bad formula
            } else {
                cellValue = get_lowercase_cell_of_type(cell_i.getStringCellValue(), colType, dateFormatter); // good formula
            }
        } else {
            if (cell_i.getCellTypeEnum() == CellType.NUMERIC) {
                if (DateUtil.isCellDateFormatted(cell_i)) {
                    return cell_i.getDateCellValue();
                } else {
                    // Handle numeric cells based on the expected Col_types
                    if (colType == Col_types.DBL) {
                        return cell_i.getNumericCellValue(); // return the numeric value directly for DBL type
                    } else if (colType == STR) {
                        return Double.toString(cell_i.getNumericCellValue()); // convert to string for STR type
                    } else if (colType == Col_types.DAT) {
                        cellValue = cell_i.getDateCellValue(); // though it's numeric, you expect it to be a date
                    }
                }
            } else {
                cellValue = get_lowercase_cell_of_type(cell_i.getStringCellValue(), colType, dateFormatter); // no formula
            }
        }
        return cellValue;
    }
    public Object get_cell_of_type(String cell, Col_types type, SimpleDateFormat dateFormatter) {
        Object out = "";
        switch (type) {
            case STR -> {
                if (cell == null) return "";
                return cell.trim();
            }
            case DBL -> {
                if (cell == null) return 0d;
                try {
                    return Double.parseDouble(cell.replace(",", ".").replace(" €", ""));
                } catch (NumberFormatException ignored) {
                    return NA_DBL;
                }
            }
            case DAT -> {
                if (cell == null) return NA_DAT;
                // Purify the cell if the date format is "#yyyy-MM-dd#"
                String purifiedCell = dateFormatter.toPattern().equals("yyyy-MM-dd") ? cell.replaceAll("#", "") : cell;

                if (purifiedCell.length() == 5) {
                    try {
                        // If the purified cell has exactly 5 characters, interpret it as a numeric Excel date
                        double dateValue = Double.parseDouble(purifiedCell);
                        return DateUtil.getJavaDate(dateValue);
                    } catch (NumberFormatException ignored) {
                        return NA_DAT;
                    }
                } else {
                    try {
                        // Otherwise, try to parse the date using the specified format
                        return dateFormatter.parse(purifiedCell);
                    } catch (ParseException ignored) {
                        return NA_DAT;
                    }
                }
            }
        }
        return out;
    }
    public Object get_lowercase_cell_of_type(String cell, Col_types type, SimpleDateFormat dateFormatter) {
        Object out = "";
        switch (type) {
            case STR -> {
                if (cell == null) return "";
                return cell.toLowerCase().trim();
            }
            case DBL -> {
                if (cell == null) return 0d;
                try {
                    return Double.parseDouble(cell.replace(",", ".").replace(" €", ""));
                } catch (NumberFormatException ignored) {
                    return 0d;
                }
            }
            case DAT -> {
                if (cell == null) return NA_DAT;
                // Purify the cell if the date format is "#yyyy-MM-dd#"
                String purifiedCell = dateFormatter.toPattern().equals("yyyy-MM-dd") ? cell.replaceAll("#", "") : cell;

                if (purifiedCell.length() == 5) {
                    try {
                        // If the purified cell has exactly 5 characters, interpret it as a numeric Excel date
                        double dateValue = Double.parseDouble(purifiedCell);
                        return DateUtil.getJavaDate(dateValue);
                    } catch (NumberFormatException ignored) {
                        return NA_DAT;
                    }
                } else {
                    try {
                        // Otherwise, try to parse the date using the specified format
                        return dateFormatter.parse(purifiedCell);
                    } catch (ParseException ignored) {
                        return NA_DAT;
                    }
                }
            }
        }
        return out;
    }
    public Object get_lowercase_cell_of_type(String cell, Col_types type, SimpleDateFormat dateFormatter, boolean cleanDblFormat) {
        Object out = "";
        switch (type) {
            case STR -> {
                if (cell == null) return "";
                return cell.toLowerCase().trim();
            }
            case FLT -> {
                if (cell == null) return 0f;
                try {
                    return Float.parseFloat(cell);
                } catch (NumberFormatException ignored) {
                    return 0f;
                }
            }
            case DBL -> {
                if (cell == null) return 0d;
                try {
                    return Double.parseDouble(cell);
                } catch (NumberFormatException ignored) {
                    return 0d;
                }
            }
            case DAT -> {
                if (cell == null) return NA_DAT;
                // Purify the cell if the date format is "#yyyy-MM-dd#"
                String purifiedCell = dateFormatter.toPattern().equals("yyyy-MM-dd") ? cell.replaceAll("#", "") : cell;

                if (purifiedCell.length() == 5) {
                    try {
                        // If the purified cell has exactly 5 characters, interpret it as a numeric Excel date
                        double dateValue = Double.parseDouble(purifiedCell);
                        return DateUtil.getJavaDate(dateValue);
                    } catch (NumberFormatException ignored) {
                        return NA_DAT;
                    }
                } else {
                    try {
                        // Otherwise, try to parse the date using the specified format
                        return dateFormatter.parse(purifiedCell);
                    } catch (ParseException ignored) {
                        return NA_DAT;
                    }
                }
            }
        }
        return out;
    }
    public Object get_lowercase_cell_of_type(String cell, Col_types type, SimpleDateFormat dateFormatter, int noSpecialChars) {
        Object out = "";
        switch (type) {
            case STR -> {
                if (cell == null) return "";
                return cell.toLowerCase().trim();
            }
            case FLT -> {
                if (cell == null) return 0f;
                try {
                    return Float.parseFloat(cell.replace(",", "."));
                } catch (NumberFormatException ignored) {
                    return 0f;
                }
            }
            case DBL -> {
                if (cell == null) return 0d;
                try {
                    return Double.parseDouble(cell.replace(",", "."));
                } catch (NumberFormatException ignored) {
                    return 0d;
                }
            }
            case DAT -> {
                if (cell == null) return NA_DAT;
                // Purify the cell if the date format is "#yyyy-MM-dd#"
                String purifiedCell = dateFormatter.toPattern().equals("yyyy-MM-dd") ? cell.replaceAll("#", "") : cell;

                if (purifiedCell.length() == 5) {
                    try {
                        // If the purified cell has exactly 5 characters, interpret it as a numeric Excel date
                        double dateValue = Double.parseDouble(purifiedCell);
                        return DateUtil.getJavaDate(dateValue);
                    } catch (NumberFormatException ignored) {
                        return NA_DAT;
                    }
                } else {
                    try {
                        // Otherwise, try to parse the date using the specified format
                        return dateFormatter.parse(purifiedCell);
                    } catch (ParseException ignored) {
                        return NA_DAT;
                    }
                }
            }
        }
        return out;
    }
    public void printDataFrame() {
        // Print header
        for (String column : header) {
            System.out.print(column + "\t");
        }
        System.out.println();

        // Print rows
        for (int rowIndex = 0; rowIndex < nrow; rowIndex++) {
            Object[] row = r(rowIndex);
            for (int colIndex = 0; colIndex < ncol; colIndex++) {
                Object cell = row[colIndex];
                Col_types colType = coltypes[colIndex];

                if (colType == DAT && cell instanceof Date) {
                    System.out.print(format.format((Date) cell) + "\t");
                } else {
                    System.out.print(cell + "\t");
                }
            }
            System.out.println();
        }
    }
    public void writeToFileEstimate() throws IOException {
        String outputPath = fullPath.replace(".xlsx", "_calculé.xlsx").replace(".xlsm", "_calculé.xlsm");
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Output");

        // Create header row
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < header.length; i++) {
            headerRow.createCell(i).setCellValue(header[i]);
        }

        // Create data rows
        for (int i = 0; i < nrow; i++) {
            Row dataRow = sheet.createRow(i + 1);
            Object[] rowData = r(i);
            for (int j = 0; j < rowData.length; j++) {
                Cell cell = dataRow.createCell(j);
                Object value = rowData[j];
                if (value != null) {
                    switch (coltypes[j]) {
                        case STR:
                            cell.setCellValue((String) value);
                            break;
                        case DBL:
                            if (value instanceof Double) {
                                cell.setCellValue((Double) value);
                            } else {
                                cell.setCellValue(value.toString()); // handle NA_DBL case
                            }
                            break;
                        case DAT:
                            if (value instanceof Date) {
                                cell.setCellValue(format.format(value));
                            } else {
                                cell.setCellValue(value.toString()); // handle NA_DAT case
                            }
                            break;
                        default:
                            break;
                    }
                }
            }
        }

        // Write to file
        try (FileOutputStream outputStream = new FileOutputStream(outputPath)) {
            workbook.write(outputStream);
        }

        workbook.close();
    }
    int readDimFromMetadata(String metadataPath) {
        try (Scanner scanner = new Scanner(new File(metadataPath))) {
            return scanner.nextInt();
        } catch (FileNotFoundException e) {
            // This should not happen since we check the file's existence before calling this method
            return 0;
        }
    }
    void writeDimToMetadata(String metadataPath, int dim) throws IOException {
        try (FileWriter writer = new FileWriter(metadataPath, false)) {
            writer.write(String.valueOf(dim));
        }
    }
    // PRINT
    public void print() {
        this.print(min(10,this.nrow));
    }
    public void print(int rows) {
        System.out.println(Arrays.toString(this.header));
        rows = Math.min(rows,this.nrow);

        for (int i = 0; i < rows; i++) {
            System.out.println(Arrays.toString(this.r(i)));
        }
    }
    public void print_cols() {
        for (int i = 0; i < this.ncol; i++) {
            System.out.println(Arrays.toString(this.df.get(i)));
        }
    }
    public void printgrille() {
        int max = min(nrow, 100);
        this.print(max);
    }
    private void get_nrow(String path) {
        int counter = 0;
        try(Reader inputReader = new InputStreamReader(Files.newInputStream(new File(path).toPath()), encoding)){
            CsvParser parser = new CsvParser(csv_settings);
            parser.beginParsing(inputReader);
            for (String[] strings : parser.parseAll()) {
                counter++;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.nrow = counter;
    }
    // DATA
    public Object[] r(int index){
        Object[] row = new Object[ncol];
        for(int i=0; i<this.ncol; i++){
            row[i] = df.get(i)[index];
        }
        return row;
    }
    public Object[] c(Object colname){
        if (colname instanceof Integer) {
            return df.get((Integer) colname);
        }
        int index = find_in_arr_first_index(header, colname);
        return df.get(index);
    }
    public Object[] c(int index){
        return df.get(index);
    }
    public int csv_get_nrows(String path, char delim) {
        int out = 0;
        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true, delim);

        try(Reader inputReader = new InputStreamReader(Files.newInputStream(
                new File(path).toPath()), encoding)){
            CsvParser parser = new CsvParser(settings);
            List<String[]> parsedRows = parser.parseAll(inputReader);
            out = parsedRows.size()-1;
        } catch (IOException ignored) {}
        return out;
    }
    public void headerAndColtypesDropSKP() {
        int newSize = 0;
        for (Col_types type : coltypes) {
            if (type != Col_types.SKP) {
                newSize++;
            }
        }

        String[] headerNew = new String[newSize];
        Col_types[] coltypesNew = new Col_types[newSize];

        int j = 0;
        for (int i = 0; i < coltypes.length; i++) {
            if (coltypes[i] != Col_types.SKP) {
                headerNew[j] = header[i];
                coltypesNew[j] = coltypes[i];
                j++;
            }
        }

        this.coltypes = coltypesNew;
        this.header = headerNew;
    }
    public void headerDropSKP() {
        int newSize = 0;
        for (Col_types type : coltypes) {
            if (type != Col_types.SKP) {
                newSize++;
            }
        }

        String[] headerNew = new String[newSize];

        int j = 0;
        for (int i = 0; i < coltypes.length; i++) {
            if (coltypes[i] != Col_types.SKP) {
                headerNew[j] = header[i];
                j++;
            }
        }

        this.header = headerNew;
    }
    public void coltypesDropSKP() {
        int newSize = 0;
        for (Col_types type : coltypes) {
            if (type != Col_types.SKP) {
                newSize++;
            }
        }

        Col_types[] coltypesNew = new Col_types[newSize];

        int j = 0;
        for (int i = 0; i < coltypes.length; i++) {
            if (coltypes[i] != Col_types.SKP) {
                coltypesNew[j] = coltypes[i];
                j++;
            }
        }

        this.coltypes = coltypesNew;
    }
    public String[] headerDropSKP(String[] header, Col_types[] coltypes) {
        int newSize = 0;
        for (Col_types type : coltypes) {
            if (type != Col_types.SKP) {
                newSize++;
            }
        }

        String[] headerNew = new String[newSize];

        int j = 0;
        for (int i = 0; i < coltypes.length; i++) {
            if (coltypes[i] != Col_types.SKP) {
                headerNew[j] = header[i];
                j++;
            }
        }

        return headerNew;
    }
    public static int countBool(boolean[] array) {
        return (int) IntStream.range(0, array.length)
                .filter(i -> array[i])
                .count();
    }
    public static int[] matchHeaders(String[] A, String[] B) {
        List<Integer> matchedIndices = new ArrayList<>();

        for (int i = 0; i < B.length; i++) {
            int index = find_in_arr_first_index(A, B[i]);
            if (index != -1) {
                matchedIndices.add(index);
            }
        }
        return matchedIndices.stream().mapToInt(Integer::intValue).toArray();
    }
    public String[] headerAndColtypesDropSKP(String[] head) {
        String[] header_new = new String[get_len(coltypes)];
        Col_types[] coltypes_new = new Col_types[get_len(coltypes)];
        String[] header_temp_new = new String[get_len(coltypes)];
        int j = 0;
        for (int i = 0; i < coltypes.length; i++) {
            if (coltypes[i] != Col_types.SKP) {
                header_new[j] = header[i];
                coltypes_new[j] = coltypes[i];
                header_temp_new[j] = head[i];
                j++;
            }
        }
        this.coltypes = coltypes_new;
        this.header = header_new;
        return header_temp_new;
    }
    public void df_populate (Col_types[] vectypes) {
        for (Col_types coltype : vectypes) {
            switch (coltype) {
                case STR -> this.df.add(new String[nrow]);
                case DBL -> this.df.add(new Double[nrow]);
                case DAT -> this.df.add(new Date[nrow]);
                case FLT -> this.df.add(new Float[nrow]);
                default -> {
                }
            }
        }
    }
    public ArrayList<Object[]> df_populate (ArrayList<Object[]> base, Col_types[] coltypes) {
        for (Col_types coltype : coltypes) {
            switch (coltype) {
                case STR:
                    base.add(new String[nrow]);
                    break;
                case DBL:
                    base.add(new Double[nrow]);
                    break;
                case DAT:
                    base.add(new Date[nrow]);
                    break;
                default:
            }
        }
        return base;
    }
    public void remove_leading_zeros() {
        String[] cols = {"Numéro_Dossier","Numéro_Adhésion"};
        for(String col : cols) {
            if(check_in(col,this.header)) {
                for (int i = 0; i < this.nrow; i++) {
                    String val = (String) this.c(col)[i];
                    if (val != null) {
                        this.c(col)[i] = val.replaceFirst("^0+", "");
                    }
                }
            }
        }
    }
    // FILTER
    public void keep_rows (boolean[] keep_bool) {
        if (sum_boolean(keep_bool) == 0){
            df = null;
            return;
        }
        assert (keep_bool.length == nrow);
        int nrow_old = nrow;
        nrow = sum_boolean(keep_bool);
        ArrayList<Object[]> rowsToKeep = new ArrayList<>(ncol);
        rowsToKeep = df_populate(rowsToKeep,coltypes);
        int k = 0;
        for (int i = 0; i < nrow_old; i++) {
            if (keep_bool[i]) {
                for (int j = 0; j < ncol; j++){
                    rowsToKeep.get(j)[k] = df.get(j)[i];
                }
                k++;
            }
        }
        this.df = rowsToKeep;
    }
    public void keep_cols( boolean[] keep_vec) {
        this.header = keep_from_array(header,keep_vec);
        this.coltypes = keep_from_array(coltypes,keep_vec);
        int j = 0;
        for (int i = 0; i < ncol; i++) {
            if(!keep_vec[i]) {
                df.remove(j);
            } else {
                j++;
            }
        }
        this.ncol = sum_boolean(keep_vec);
    }
    public int get_len(DF.Col_types[] ct) {
        int j = 0;
        for (DF.Col_types c : ct) {
            if (c != DF.Col_types.SKP) {
                j++;
            }
        }
        return j;
    }
    public static int find_in_arr_first_index(Object[] arr, Object value) {
        final int len = arr.length;
        int out = -1;
        for (int i = 0; i < len; i++) {
            if (arr[i] == null) continue;
            if (arr[i].equals(value)) {
                out = i;
                break;
            }
        }
        return out;
    }
    public enum Col_types {
        STR,
        DAT,
        DBL,
        FLT,
        SKP
    }
    public void saveToCSVFile_simple(String suffix) throws IOException {
        String filePath = fullPath.replace(".csv", "_" + suffix + ".csv");
        filePath = filePath.replace(".xlsx", "_" + suffix + ".csv");
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
    public void saveToCSVFile_sortedCoef(String suffix) throws IOException {
        String filePath = fullPath.replace(".csv", "_" + suffix + ".csv");
        filePath = filePath.replace(".xlsx", "_" + suffix + ".csv");

        // Sort the DF based on 'identifiant_contrat' and 'date_debut_periode_souscription'
        int idContractIdx = getHeaderIndex("identifiant_contrat");
        int dateIdx = getHeaderIndex("date_debut_periode_souscription");
        SimpleDateFormat sdfSort = new SimpleDateFormat("dd/MM/yyyy");
        List<Integer> sortedIndices = IntStream.range(0, nrow).boxed().collect(Collectors.toList());

        sortedIndices.sort((i1, i2) -> {
            int cmp = ((String) df.get(idContractIdx)[i1]).compareTo((String) df.get(idContractIdx)[i2]);
            if (cmp != 0) return cmp;

            Date date1 = (Date) df.get(dateIdx)[i1];
            Date date2 = (Date) df.get(dateIdx)[i2];
            return date1.compareTo(date2);
        });

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            writer.write('\ufeff');
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");

            // Write header row
            for (int i = 0; i < ncol; i++) {
                if (i > 0) {
                    writer.write(";");
                }
                writer.write(header[i]);
            }
            writer.newLine();

            // Write data rows
            for (int index : sortedIndices) {
                for (int colIndex = 0; colIndex < ncol; colIndex++) {
                    if (colIndex > 0) {
                        writer.write(";");
                    }

                    Object value = df.get(colIndex)[index];
                    if (value != null) {
                        if (value instanceof Date) {
                            writer.write(sdf.format((Date) value));
                        } else if (value instanceof Float) {
                            writer.write(String.format("%.4f", value));  // Explicit formatting for floats
                        } else {
                            writer.write(value.toString());
                        }
                    }
                }
                writer.newLine();
            }
        }
    }

}
