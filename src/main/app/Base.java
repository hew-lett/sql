package main.app;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;

import static java.lang.Math.min;
import static main.app.App.*;
import static main.app.App.ref_prog;
import static main.app.DF.Col_types.DAT;
import static main.app.DF.Col_types.SKP;

public class Base extends BaseAccum {
    Object[] refProgrammesRow;
    private static String[] currentHeaderRef = null;
    char delim = ';';
    String pays;
    public static final String LAPARISIENNE = "LaParisienne";
    public static final String MEDIA = "DBCLAIMS";
    public static void main(String[] args) throws IOException, SQLException {
    }
    public Base(File path, String pays, String mappingColDefault) throws IOException {
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
            this.cleanStatut();
            this.date_autofill();
            this.createPivotTable();
            this.populateUniqueStatuts();
            this.populateStatutDateRangeMap();
        }
    } //Sin
    public Base(String folder, String map_col) throws IOException {
        switch (map_col) {
            case "FIC France" -> {
                this.source = true;
                referentialRow = getReferentialRow(new String[]{"fic france"});

                List<File> fileList = Arrays.asList(Objects.requireNonNull(new File(folder).listFiles()));
                if (fileList.isEmpty()) return;

                int dim = computeDimFICFrance(folder);

                fileList.sort(Comparator.comparing(f -> !f.getName().contains(LAPARISIENNE)));

                File mainFile = fileList.get(0);
                if (!mainFile.getName().contains(LAPARISIENNE)) return;

                CsvParserSettings settings = new CsvParserSettings();
                settings.setDelimiterDetectionEnabled(true, DEFAULT_DELIMITER);
                settings.trimValues(true);

                try (Reader inputReader = Files.newBufferedReader(mainFile.toPath(), Charset.forName(encoding))) {
                    CsvParser parser = new CsvParser(settings);
                    List<String[]> parsedRows = parser.parseAll(inputReader);
                    Iterator<String[]> rows = parsedRows.iterator();
                    header = rows.next();
                    header = Arrays.stream(header)
                            .filter(h -> h != null && !h.trim().isEmpty())
                            .toArray(String[]::new);
                    ncol = header.length;

                    boolean[] cols_kept = header_unify_cols_kept();

                    coltypes_populate(cols_kept);

                    nrow = dim;
                    ncol = header.length;
                    df = new ArrayList<>(ncol);
                    this.df_populate(coltypes);

                    int i = 0;
                    while (rows.hasNext()) {
                        int j = 0;
                        int k = 0;
                        String[] parsedRow = Arrays.copyOf(rows.next(), coltypes.length);
                        for (String s : parsedRow) {
                            if (coltypes[k] != Col_types.SKP) {
                                df.get(j)[i] = get_lowercase_cell_of_type(s, coltypes[k], dateDefault);
                                j++;
                            }
                            k++;
                        }
                        i++;
                    }

                    this.coltypesDropSKP();

                    for (File file : fileList) {
                        if (file.getName().contains(LAPARISIENNE)) continue;

                        try (Reader secondaryInputReader = Files.newBufferedReader(file.toPath(), Charset.forName(encoding))) {
                            settings.setDelimiterDetectionEnabled(true, '\t');
                            parser = new CsvParser(settings);
                            parsedRows = parser.parseAll(secondaryInputReader);
                            rows = parsedRows.iterator();

                            // 1. Header treatment
                            String[] auxHeader = rows.next();  // Cloning to avoid accidental modifications
                            boolean[] auxColsKept = header_unify_cols_kept(auxHeader);
                            Col_types[] auxColtypes = coltypes_populate_aux(auxColsKept,auxHeader);

                            int[] headerIndexes = matchHeaders(header,auxHeader);
                            while (rows.hasNext()) {
                                int j = 0;
                                int k = 0;
                                String[] parsedRow = rows.next();
                                for (String s : parsedRow) {
                                    if (auxColtypes[k] != SKP) {
                                        df.get(headerIndexes[j])[i] = get_lowercase_cell_of_type(s, auxColtypes[k], dateDefault);
                                        j++;
                                    }
                                    k++;
                                }
                                i++;
                            }
                        }
                    }
                }
                date_autofill_agg();
            }
            case "DB Claims Italie" -> {
                this.source = true;
                referentialRow = getReferentialRow(new String[]{"source"});

                List<File> fileList = Arrays.asList(Objects.requireNonNull(new File(folder).listFiles()));
                if (fileList.isEmpty()) return;

                int dim = computeDimFICItaPol(folder, "Italie");
                fileList.sort(Comparator.comparing(f -> !f.getName().contains(MEDIA)));

                CsvParserSettings settings = new CsvParserSettings();
                settings.setDelimiterDetectionEnabled(true, DEFAULT_DELIMITER);
                settings.trimValues(true);

                int i = 0;
                boolean initialized = false;

                for (File file : fileList) {
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
                            boolean[] cols_kept = this.mapColnamesAndGetColsKept(map_col);
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
                                if (coltypes[k] != Col_types.SKP) {
                                    df.get(j)[i] = get_lowercase_cell_of_type(s, coltypes[k], dateDefault);
                                    j++;
                                }
                                k++;
                            }
                            i++;
                        }

                    }
                }

                this.headerAndColtypesDropSKP();
                date_autofill_agg();
                this.remove_leading_zeros();
            }
            case "FIC Pologne" -> {
                this.source = true;
                referentialRow = getReferentialRow(new String[]{"source"});

                List<File> fileList = Arrays.asList(Objects.requireNonNull(new File(folder).listFiles()));
                if (fileList.isEmpty()) return;

                int dim = computeDimFICItaPol(folder, "Pologne");
                System.out.println(dim);
                CsvParserSettings settings = new CsvParserSettings();
                settings.setDelimiterDetectionEnabled(true, TAB_DELIMITER);
                settings.trimValues(true);

                int i = 0;
                boolean initialized = false;

                for (File file : fileList) {

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
                            boolean[] cols_kept = this.mapColnamesAndGetColsKept(map_col);
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
                                if (coltypes[k] != Col_types.SKP) {
                                    df.get(j)[i] = get_lowercase_cell_of_type(s, coltypes[k], dateDefault);
                                    j++;
                                }
                                k++;
                            }
                            i++;
                        }

                    }
                }

                this.headerAndColtypesDropSKP();
                date_autofill_agg();
                this.remove_leading_zeros();
            }
        }
        populateUniqueNumPoliceValues();
    } //Fic
    public String extractKeyFromFileName(String fileName, String pays) {
        int start = -1;
        int end = -1;
        if (pays.equals("France")) {
            start = fileName.indexOf("ICI");
            if (start == -1) {
                start = fileName.indexOf("FRMP");
            }
            end = fileName.indexOf("_", start);
        } else if (pays.equals("Italie") || pays.equals("Pologne")) {
            start = fileName.indexOf("ICI");
            end = fileName.indexOf(".csv", start);
        } else if (pays.equals("Espagne")) {
            start = fileName.indexOf("ICI");
            end = fileName.indexOf("_", start);
        }

        if (start != -1 && end != -1) {
            return fileName.substring(start, end);
        }

        return fileName; // Default to full file name if pattern not found
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
    public void cleanStatut() {
        // Get the "statut" column
        Object[] statuts = this.c("statut");

        // Check if the column exists
        if (statuts == null) {
            return;
        }

        // Iterate through each value in the column and replace big dashes with little dashes
        for (int i = 0; i < statuts.length; i++) {
            String currentStatut = (String) statuts[i];
            statuts[i] = currentStatut.replace("–", "-");
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

    private int[] remapIndices(int[] auxToMainMapping) {
        int[] newMapping = new int[auxToMainMapping.length];
        int countKept = 0;  // Count of columns that are not -1

        for (int i = 0; i < auxToMainMapping.length; i++) {
            if (auxToMainMapping[i] != -1) {
                newMapping[i] = countKept;
                countKept++;
            } else {
                newMapping[i] = -1;
            }
        }
        return newMapping;
    }
    private int computeDimFICFrance(String path) throws IOException {
        File[] files = new File(path).listFiles();
        if (files == null || files.length == 0) return 0;

        List<File> fileList = new ArrayList<>(Arrays.asList(files));

        int dim;
        String metadataCurrent = wd + "metadata/fic_france_nb_lignes_" + CURRENT_MONTH + ".txt";
        if (new File(metadataCurrent).exists()) {
            return readDimFromMetadata(metadataCurrent);
        }

        String metadataPrevious = wd + "metadata/fic_france_nb_lignes_" + PREVIOUS_MONTH + ".txt";

        if (new File(metadataPrevious).exists()) {
            dim = readDimFromMetadata(metadataPrevious);
            for (File file : fileList) {
                if (file.getName().contains(CURRENT_MONTH)) {
                    dim += csv_get_nrows(file.getPath(), '\t');
                }
            }
        } else {
            dim = getDimFrom0_FIC(fileList, "France");
        }
        writeDimToMetadata(metadataCurrent, dim);

        return dim;
    }
    private int computeDimFICItaPol(String path, String pays) throws IOException {
        File[] files = new File(path).listFiles();
        if (files == null || files.length == 0) return 0;

        List<File> fileList = new ArrayList<>(Arrays.asList(files));

        int dim;
        String metadataCurrent = wd + "metadata/fic_" + pays + "_nb_lignes_" + CURRENT_MONTH + ".txt";
        if (new File(metadataCurrent).exists()) {
            return readDimFromMetadata(metadataCurrent);
        }

        dim = getDimFrom0_FIC(fileList, pays);
        writeDimToMetadata(metadataCurrent, dim);

        return dim;
    }
    private int getDimFrom0_FIC(List<File> fileList, String pays) throws IOException {
        int dim = 0;
        char delim = ';'; //ita ;
        if (pays.equals("Pologne")) {
            delim = '\t';
        }
        if (pays.equals("France")) {
            for (File file : fileList) {
                delim = file.getName().contains("LaParisienne") ? ';' : '\t';
                dim += csv_get_nrows(file.getPath(), delim);
            }
        } else {
            for (File file : fileList) {
                dim += csv_get_nrows(file.getPath(), delim);
            }
        }
        return dim;
    }
}
