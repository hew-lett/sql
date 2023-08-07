package main.app;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static main.app.App.*;
import static main.app.DF.Col_types.*;

public class BaseFic extends DF {
    public static final String LAPARISIENNE = "LaParisienne";
    public static final String MEDIA = "DBCLAIMS";
    public static final char DEFAULT_DELIMITER = ';';
    public static final char TAB_DELIMITER = '\t';
    public boolean source = false;
    Object[] referentialRow;
    private static final String CURRENT_MONTH;
    private static final String PREVIOUS_MONTH;
    static {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        LocalDate now = LocalDate.now();

        CURRENT_MONTH = now.format(formatter);
        PREVIOUS_MONTH = now.minusMonths(1).format(formatter);
    }
    public static void main(String[] args) throws IOException {
        long startTime = System.nanoTime();long endTime;long duration;long minutes;long seconds;
        ref_prog = new DF(wd+"Référentiel programmes.csv", ';', true);
        ref_prog.print(10);
        ref_triangle = new DF(wd + "ref_triangle.xlsx");
        mapping = new DF(wd + "mapping.xlsx");
        DF map_filtered = mapping.mappingFiltre("DB Claims Italie");
        map_filtered.print(10);
//        DF fic_FRA = new BaseFic(wd + "source FIC/SPB France/","FIC France");
//        DF fic_ITA = new BaseFic(wd + "source FIC/SPB Italie/","DB Claims Italie");

        DF fic_POL = new BaseFic(wd + "source FIC/SPB Pologne/","FIC Pologne");
        fic_POL.print(10);
        System.out.println(fic_POL.nrow);
//        System.out.println(Arrays.toString(fic_POL.r(350000)));


    }
    public BaseFic(String path, String map_col) throws IOException {
        switch (map_col) {
            case "FIC France" -> {
                this.source = true;
                referentialRow = getReferentialRow(new String[]{"source"});

                List<File> fileList = Arrays.asList(Objects.requireNonNull(new File(path).listFiles()));
                if (fileList.isEmpty()) return;

                int dim = computeTotalRowsFICFrance(path);

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
                    boolean[] cols_kept = this.mapColnamesAndKeepNeededMain(mapping.mappingFiltre(map_col));
                    header_unify();

                    coltypes = new Col_types[ncol];
                    for (int i = 0; i < ncol; i++) {
                        if (cols_kept[i]) {
                            coltypes[i] = Col_types.STR;
                        } else {
                            coltypes[i] = Col_types.SKP;
                        }
                    }
                    List<String> refTriangleHeaders = Arrays.asList(ref_triangle.header);
                    for (int i = 0; i < header.length; i++) {
                        if (refTriangleHeaders.contains(header[i]) && header[i].startsWith("date")) {
                            coltypes[i] = DAT;
                        }
                    }

                    nrow = dim;
                    assert (coltypes.length == parsedRows.get(0).length);
                    ncol = get_len(coltypes);
                    df = new ArrayList<>(get_len(coltypes));
                    this.df_populate(coltypes);

                    int i = 0;
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

                    this.header_refactor();

                    for (File file : fileList) {
                        if (file.getName().contains(LAPARISIENNE)) continue;

                        try (Reader secondaryInputReader = Files.newBufferedReader(file.toPath(), Charset.forName(encoding))) {
                            settings.setDelimiterDetectionEnabled(true, '\t');
                            parser = new CsvParser(settings);
                            parsedRows = parser.parseAll(secondaryInputReader);
                            rows = parsedRows.iterator();

                            // 1. Header treatment
                            String[] auxHeader = rows.next().clone();  // Cloning to avoid accidental modifications
                            boolean[] auxColsKept = this.mapColnamesAndKeepNeededAux(auxHeader, mapping.mappingFiltre(map_col));

                            Col_types[] auxColtypes = new Col_types[auxHeader.length];
                            for (int j = 0; j < auxHeader.length; j++) {
                                if (auxColsKept[j]) {
                                    auxColtypes[j] = Col_types.STR;
                                } else {
                                    auxColtypes[j] = Col_types.SKP;
                                }
                            }

                            String[] auxUnifiedHeader = this.header_unify_return(auxHeader);

                            // 2. Map positions from auxiliary header to main header
                            int[] auxToMainMapping = new int[auxUnifiedHeader.length];
                            for (int j = 0; j < auxUnifiedHeader.length; j++) {
                                int ind = find_in_arr_first_index(header, auxUnifiedHeader[j]);
                                if (ind != -1 && coltypes[ind] != Col_types.SKP) {
                                    auxToMainMapping[j] = ind;
                                } else {
                                    auxToMainMapping[j] = -1;
                                }
                            }
                            auxToMainMapping = remapIndices(auxToMainMapping);

                            // 3. Populate main data frame based on mapped positions
                            while (rows.hasNext()) {
                                String[] parsedRow = rows.next();
                                for (int j = 0, k = 0; j < parsedRow.length; j++) {
                                    if (auxColtypes[j] == Col_types.SKP) {
                                        //j++;
                                        continue;
                                    }
                                    int mainIndex = auxToMainMapping[j];
                                    if (mainIndex != -1) {
                                        df.get(mainIndex)[i] = get_lowercase_cell_of_type(parsedRow[j], coltypes[mainIndex], dateDefault);
                                    }
                                    k++;
                                }
                                i++;
                            }
                        }
                    }
                }
                date_autofill_agg();
                this.remove_leading_zeros();
            }
            case "DB Claims Italie" -> {
                this.source = true;
                referentialRow = getReferentialRow(new String[]{"source"});

                List<File> fileList = Arrays.asList(Objects.requireNonNull(new File(path).listFiles()));
                if (fileList.isEmpty()) return;

                int dim = computeTotalRowsFICItalie(path);
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
                            DF map_current = mapping.mappingFiltre(map_col);
                            boolean[] cols_kept = this.mapColnamesAndKeepNeededMain(map_current);
                            header_unify();

                            coltypes = new Col_types[ncol];
                            for (int colIndex = 0; colIndex < ncol; colIndex++) {
                                if (cols_kept[colIndex]) {
                                    coltypes[colIndex] = Col_types.STR;
                                } else {
                                    coltypes[colIndex] = Col_types.SKP;
                                }
                            }
                            List<String> refTriangleHeaders = Arrays.asList(ref_triangle.header);
                            for (int colIndex = 0; colIndex < header.length; colIndex++) {
                                if (refTriangleHeaders.contains(header[colIndex]) && header[colIndex].startsWith("date")) {
                                    coltypes[colIndex] = DAT;
                                }
                            }

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

                this.header_refactor();
                date_autofill_agg();
                this.remove_leading_zeros();
            }
            case "FIC Pologne" -> {
                this.source = true;
                referentialRow = getReferentialRow(new String[]{"source"});

                List<File> fileList = Arrays.asList(Objects.requireNonNull(new File(path).listFiles()));
                if (fileList.isEmpty()) return;

                int dim = computeTotalRowsFICPologne(path);

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
                            boolean[] cols_kept = this.mapColnamesAndKeepNeededMain(mapping.mappingFiltre(map_col));
                            header_unify();

                            coltypes = new Col_types[ncol];
                            for (int colIndex = 0; colIndex < ncol; colIndex++) {
                                if (cols_kept[colIndex]) {
                                    coltypes[colIndex] = Col_types.STR;
                                } else {
                                    coltypes[colIndex] = Col_types.SKP;
                                }
                            }
                            List<String> refTriangleHeaders = Arrays.asList(ref_triangle.header);
                            for (int colIndex = 0; colIndex < header.length; colIndex++) {
                                if (refTriangleHeaders.contains(header[colIndex]) && header[colIndex].startsWith("date")) {
                                    coltypes[colIndex] = DAT;
                                }
                            }

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

                this.header_refactor();
                date_autofill_agg();
                this.remove_leading_zeros();
            }
        }
        findAndStoreStatuts();
    }
    private void date_autofill_agg() {
        // Indices for required columns in the current DF
        int indexDateSurv = find_in_arr_first_index(header, "date_surv");
        int indexDateSous = find_in_arr_first_index(header, "date_sous");
        int indexDateDecla = find_in_arr_first_index(header, "date_decla");
        int indexNumPolice = find_in_arr_first_index(header, "num_police");

        // Indices for required columns in the ref_prog DF
        int indexContrat = find_in_arr_first_index(ref_prog.header, "n°contrat");
        int indexDateDebutRef = find_in_arr_first_index(ref_prog.header, "date_debut");
        int indexDateFinRef = find_in_arr_first_index(ref_prog.header, "date_fin");

        // Return early if the num_police column doesn't exist
        if (indexNumPolice == -1) return;

        // If date_surv column doesn't exist, create it
        if (indexDateSurv == -1) {
            indexDateSurv = ncol;
            Object[] newColumn = new Object[nrow];
            Arrays.fill(newColumn, NA_DAT);
            df.add(newColumn);
            ncol++;
        }

        // If date_sous column doesn't exist, create it
        if (indexDateSous == -1) {
            indexDateSous = ncol;
            Object[] newColumn = new Object[nrow];
            Arrays.fill(newColumn, NA_DAT);
            df.add(newColumn);
            ncol++;
        }

        // Cache for quick lookup of ref_prog data based on num_police/n°contrat
        Map<String, Date[]> refprogLookup = new HashMap<>();
        for (int i = 0; i < ref_prog.nrow; i++) {
            String contrat = ref_prog.c(indexContrat)[i].toString();
            Date dateDebut = (Date) ref_prog.c(indexDateDebutRef)[i];
            Date dateFin = (Date) ref_prog.c(indexDateFinRef)[i];
            refprogLookup.put(contrat, new Date[]{dateDebut, dateFin});
        }

        for (int i = 0; i < nrow; i++) {
            System.out.println("Processing row " + i + " of " + nrow + c(indexNumPolice)[i]);
            String currentNumPolice = c(indexNumPolice)[i].toString();
            Date[] refDates = refprogLookup.get(currentNumPolice.toLowerCase());
            if (refDates == null) {
                System.out.println("Warning: No ref_prog data found for num_police " + currentNumPolice);
                continue;
            }

            Date dateDebutRef = refDates[0];
            Date dateFinRef = refDates[1];

            Date dateSurv = (Date) c(indexDateSurv)[i];
            Date dateSous = (Date) c(indexDateSous)[i];

            // Date filling logic...
            if (dateSurv.equals(NA_DAT)) {
                if (indexDateDecla != -1 && !c(indexDateDecla)[i].equals(NA_DAT)) {
                    dateSurv = (Date) c(indexDateDecla)[i];
                } else if (!dateSous.equals(NA_DAT)) {
                    dateSurv = dateSous;
                } else {
                    dateSurv = dateDebutRef;
                }
            }
            if (dateSous.equals(NA_DAT)) {
                if(!dateSurv.equals(NA_DAT)){
                    dateSous = dateSurv;
                } else {
                    dateSous = dateDebutRef;
                }
            }

            // Apply transformations...
            date_transform(dateSurv, dateDebutRef, dateFinRef, indexDateSurv, i);
            date_transform(dateSous, dateDebutRef, dateFinRef, indexDateSous, i);
        }
    }
    private void date_transform (Date date, Date dateDebutRef, Date dateFinRef, int columnIndex, int rowIndex) {
        // Control that dates are in the desired interval
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

        // Update the dates in the DF
        df.get(columnIndex)[rowIndex] = date;
    }
    public void remove_leading_zeros() {
        String[] cols = {"Numéro_Dossier"};
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

    public boolean[] mapColnamesAndKeepNeededMain (DF mapping) {

        boolean[] columnsKept = new boolean[header.length];

        for (int i = 0; i < header.length; i++) {
            columnsKept[i] = false;

            for (int j = 0; j < mapping.nrow; j++) {
                // Getting the Format ICI value (from the first column) and the desired format (from the second column)
                String formatICI = (String) mapping.df.get(0)[j];
                String desiredFormat = (String) mapping.df.get(1)[j];

                // If either value is null, continue to next iteration
                if (Objects.equals(formatICI, "") || desiredFormat.equals("")) continue;

                // Check if the header matches the desired format (ignoring case and special characters)
                if (normalize(header[i]).equalsIgnoreCase(normalize(desiredFormat))) {
                    // Check if the Format ICI value is present in referentialRow
                    if (Arrays.asList(referentialRow).contains(formatICI)) {
                        header[i] = formatICI;
                        columnsKept[i] = true; // We keep this column
                        break; // No need to continue searching for this header
                    }
                }
            }
        }
        return columnsKept;
    }
    public boolean[] mapColnamesAndKeepNeededAux (String[] localHeader, DF mapping) {
        boolean[] columnsKept = new boolean[localHeader.length];

        for (int i = 0; i < localHeader.length; i++) {
            columnsKept[i] = false;

            for (int j = 0; j < mapping.nrow; j++) {
                String formatICI = (String) mapping.df.get(0)[j];
                String desiredFormat = (String) mapping.df.get(1)[j];

                // If either value is null, continue to next iteration
                if (Objects.equals(formatICI, "") || desiredFormat.equals("")) continue;

                if (normalize(localHeader[i]).equalsIgnoreCase(normalize(desiredFormat))) {
                    if (Arrays.asList(referentialRow).contains(formatICI)) {
                        localHeader[i] = formatICI;
                        columnsKept[i] = true;
                        break;
                    }
                }
            }
        }
        return columnsKept;
    }
    // Helper method to normalize strings (ignoring special characters and case)
    private String normalize(String input) {
        return input.replace('é', 'e').toLowerCase();
    }
    private String[] header_unify_return(String[] inputHeader) {
        String[] unifiedHeader = new String[inputHeader.length];
        for (int i = 0; i < inputHeader.length; i++) {
            int ind = find_in_arr_first_index(this.referentialRow, inputHeader[i].toLowerCase());
            if (ind != -1) {
                unifiedHeader[i] = ref_triangle.header[ind];
            } else {
                unifiedHeader[i] = inputHeader[i];
            }
        }
        return unifiedHeader;
    }

    private void header_unify() {
        for (int i = 0; i < ncol; i++) {
            int ind = find_in_arr_first_index(this.referentialRow, header[i].toLowerCase());
            if (ind != -1) {
                header[i] = ref_triangle.header[ind];
            }
        }
    }
    private Object[] getReferentialRow(String[] keys) {
        String gestionnaire = keys[0];
        String precision = keys.length > 1 ? keys[1] : null;

        for (int rowIndex = 0; rowIndex < ref_triangle.nrow; rowIndex++) {
            Object[] row = ref_triangle.r(rowIndex);
            if (row[0].equals(gestionnaire)) {
                // If precision is not provided or matches the referential, return the row
                if (precision == null || row[1].equals(precision)) {
                    return row;
                }
            }
        }

        throw new RuntimeException("Referential row not found for keys: " + Arrays.toString(keys));
    }
    private int computeTotalRowsFICFrance(String path) throws IOException {
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
            dim += computeRowsForCurrentMonth(fileList);
            writeDimToMetadata(metadataCurrent, dim);
        } else {
            dim = computeRowsForAllFiles(fileList, "France");
            writeDimToMetadata(metadataCurrent, dim);
        }

        return dim;
    }
    private int computeTotalRowsFICItalie(String path) throws IOException {
        File[] files = new File(path).listFiles();
        if (files == null || files.length == 0) return 0;

        List<File> fileList = new ArrayList<>(Arrays.asList(files));

        int dim;
        String metadataCurrent = wd + "metadata/fic_italie_nb_lignes_" + CURRENT_MONTH + ".txt";
        if (new File(metadataCurrent).exists()) {
            return readDimFromMetadata(metadataCurrent);
        }

        dim = computeRowsForAllFiles(fileList, "Italie");
        writeDimToMetadata(metadataCurrent, dim);

        return dim;
    }
    private int computeTotalRowsFICPologne(String path) throws IOException {
        File[] files = new File(path).listFiles();
        if (files == null || files.length == 0) return 0;

        List<File> fileList = new ArrayList<>(Arrays.asList(files));

        int dim;
        String metadataCurrent = wd + "metadata/fic_pologne_nb_lignes_" + CURRENT_MONTH + ".txt";
        if (new File(metadataCurrent).exists()) {
            return readDimFromMetadata(metadataCurrent);
        }

        dim = computeRowsForAllFiles(fileList, "Pologne");
        writeDimToMetadata(metadataCurrent, dim);

        return dim;
    }
    private int computeRowsForCurrentMonth(List<File> fileList) {
        int dim = 0;
        for (File file : fileList) {
            if (file.getName().contains(CURRENT_MONTH)) {
                dim += csv_get_nrows(file.getPath(), '\t');
            }
        }
        return dim;
    }
    private int computeRowsForAllFiles(List<File> fileList, String pays) throws IOException {
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

    private int readDimFromMetadata(String metadataPath) {
        try (Scanner scanner = new Scanner(new File(metadataPath))) {
            return scanner.nextInt();
        } catch (FileNotFoundException e) {
            // This should not happen since we check the file's existence before calling this method
            return 0;
        }
    }
    private void writeDimToMetadata(String metadataPath, int dim) throws IOException {
        try (FileWriter writer = new FileWriter(metadataPath, false)) {
            writer.write(String.valueOf(dim));
        }
    }
}
