package main.app;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Date;

import static java.lang.Math.min;
import static main.app.App.*;
import static main.app.App.ref_prog;
import static main.app.DF.Col_types.*;
import static main.app.DF.Col_types.DBL;

public class Base extends DF {
    Object[] refProgrammesRow;
    protected boolean source = false;
    public static String[] currentHeaderRef = null;
    char delim = ';';
    String pays;
    public static final String LAPARISIENNE = "LaParisienne";
    public static final String MEDIA = "DBCLAIMS";
    public static final String STATUT_FICTIF_FIC = "Comptable";
    public static final char DEFAULT_DELIMITER = ';';
    public static final char TAB_DELIMITER = '\t';
    static final String CURRENT_MONTH;
    static final String PREVIOUS_MONTH;
    static {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        LocalDate now = LocalDate.now();

        CURRENT_MONTH = now.format(formatter);
        PREVIOUS_MONTH = now.minusMonths(1).format(formatter);
    }
    String numPolice = "";
    protected Set<String> uniqueStatuts = new HashSet<>();
    protected Set<String> uniqueNumPoliceValues = new HashSet<>();
    protected Map<String, List<Date>> statutDateRangeMap = new HashMap<>();
    protected Map<String, List<Date>> numPoliceDateRangeMap = new HashMap<>();
    protected Date globalMinDateFic = null;
    protected Date globalMaxDateFic = null;
    protected Object[] referentialRow;
    public Map<String, Map<String, Map<String, Double>>> pivotTable = new HashMap<>();
    public Map<String, Map<String, Map<String, Double>>> pivotTableYearly = new HashMap<>();
    public Map<String, Map<String, Double>> pivotTableTotal = new HashMap<>();

    public Map<String, Map<String, Double>> pivotTableAllStatuts = new HashMap<>();
    public Map<String, Map<String, Double>> pivotTableAllStatutsYearly = new HashMap<>();
    public Map<String, Double> pivotTableAllStatutsTotal = new HashMap<>();

    public Map<String, Map<String, Map<String, Map<String, Double>>>> pivotTableFic = new HashMap<>();
    public Map<String, Map<String, Map<String, Map<String, Double>>>> pivotTableYearlyFic = new HashMap<>();
    public Map<String, Map<String, Map<String, Double>>> pivotTableTotalFic = new HashMap<>();

    public Map<String, Map<String, Map<String, Integer>>> pivotTableN = new HashMap<>();
    public Map<String, Map<String, Map<String, Integer>>> pivotTableYearlyN = new HashMap<>();
    public Map<String, Map<String, Integer>> pivotTableTotalN = new HashMap<>();
    public Map<String, Map<String, Integer>> pivotTableAllStatutsN = new HashMap<>();
    public Map<String, Map<String, Integer>> pivotTableAllStatutsYearlyN = new HashMap<>();
    public Map<String, Integer> pivotTableAllStatutsTotalN = new HashMap<>();
    public double coutMoyenEnCours;
    public double coutMoyenEnCoursAccepte;
    public Map<String, List<Integer>> nEnCours;
    public Map<String, List<Integer>> nEnCoursAccepte;

    public static void main(String[] args) throws Exception {
    }
    public Base(File path, String pays, String mappingColDefault) throws IOException {
        this.source = true;
        this.pays = pays;
        this.referentialRow = getReferentialRow(new String[]{"source"});
        SimpleDateFormat dateParser = dateDefault; //override obligatoire (gamestop)

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
            if (pays.equals("Gamestop")) {
                dateParser = new SimpleDateFormat("#yyyy-MM-dd#");
                header = Arrays.stream(header)
                        .map(h -> {
                            if (h != null) {
                                String trimmedHeader = h.trim();
                                if ("Date_Declaration".equals(trimmedHeader)) {
                                    return "Date_Déclaration";
                                }
                                return trimmedHeader;
                            }
                            return "";
                        })
                        .toArray(String[]::new);
            } else {
                header = Arrays.stream(header)
                        .map(h -> h != null ? h.trim() : "")
                        .toArray(String[]::new);
            }

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
                            df.get(j)[i] = get_lowercase_cell_of_type(s, coltypes[k], dateParser);
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
                            df.get(headerIndexes[j])[i] = get_lowercase_cell_of_type(s, coltypes[k], dateParser);
                            j++;
                        }
                        k++;
                    }
                    i++;
                }
            }
            this.cleanStatut();
            if(pays.equals("Gamestop")) {
                this.cleanNumPoliceGS();
            }
            this.date_autofill();
            this.createPivotTable();
            this.createYearlyPivotTable();
            this.createTotalPivotTable();
            this.createPivotAllStatuts();
            this.createYearlyPivotAllStatuts();
            this.createTotalPivotAllStatuts();
            this.createPivotTableN();
            this.createYearlyPivotTableN();
            this.createTotalPivotTableN();
            this.createPivotAllStatutsN();
            this.createYearlyPivotAllStatutsN();
            this.createTotalPivotAllStatutsN();
            this.populateUniqueStatuts();
            this.populateStatutDateRangeMap();
            ArrayList<String> statutsOrder = new ArrayList<>(Arrays.asList("en attente de prescription", "en cours", "en cours - accepté"));
            ArrayList<String> firstAndSecondExclude = new ArrayList<>(statutsOrder.subList(0, 2));
            this.coutMoyenEnCours = calculateMeanExcludingStatuses(firstAndSecondExclude);
            this.coutMoyenEnCoursAccepte = calculateMeanExcludingStatuses(statutsOrder);
            this.nEnCours = countAppearancesByYear("en cours");
            this.nEnCoursAccepte = countAppearancesByYear("en cours - accepté");
        }
    } //Sin
    public Base(File path) throws Exception {
        System.out.println(path);
        this.source = false;
        numPolice = extractKeyFromFileName(path.getName(),"aux");
        this.referentialRow = getRefRow(numPolice);
        SimpleDateFormat dateDefault = getDateFormatter((String) referentialRow[referentialRow.length-1]);

        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true, delim);
        settings.trimValues(true);

        try (Reader inputReader = Files.newBufferedReader(path.toPath(), Charset.forName(encoding))) {
            CsvParser parser = new CsvParser(settings);
            List<String[]> parsedRows = parser.parseAll(inputReader);
            Iterator<String[]> rows = parsedRows.iterator();

            nrow = csv_get_nrows(path.getPath(), delim);

            header = rows.next();
            header = Arrays.stream(header)
                    .filter(h -> h != null && !h.trim().isEmpty())
                    .toArray(String[]::new);

            boolean[] cols_kept = header_unify_cols_kept();
            coltypes_populate(cols_kept);

            if (currentHeaderRef == null) {
                currentHeaderRef = this.header;
            }

            ncol = get_len(coltypes);
            df = new ArrayList<>(ncol);
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

            if (numPolice.equals("ICIGPTB15") || numPolice.equals("ICIMITL16")) {
                this.transformNumPoliceValues();
            }
            this.date_autofill();
            this.createPivotTable();
            this.createYearlyPivotTable();
            this.createTotalPivotTable();
            this.createPivotAllStatuts();
            this.createYearlyPivotAllStatuts();
            this.createTotalPivotAllStatuts();
            this.createPivotTableN();
            this.createYearlyPivotTableN();
            this.createTotalPivotTableN();
            this.createPivotAllStatutsN();
            this.createYearlyPivotAllStatutsN();
            this.createTotalPivotAllStatutsN();
            this.populateUniqueStatuts();
            this.populateStatutDateRangeMap();
            ArrayList<String> statutsOrder = new ArrayList<>(Arrays.asList("en attente de prescription", "en cours", "en cours - accepté"));
            ArrayList<String> firstAndSecondExclude = new ArrayList<>(statutsOrder.subList(0, 2));
            this.coutMoyenEnCours = calculateMeanExcludingStatuses(firstAndSecondExclude);
            this.coutMoyenEnCoursAccepte = calculateMeanExcludingStatuses(statutsOrder);
            this.nEnCours = countAppearancesByYear("en cours");
            this.nEnCoursAccepte = countAppearancesByYear("en cours - accepté");
        }
    } //Sin_aux
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
            }
            case "DB Claims Italie" -> {
                this.source = true;
                referentialRow = getReferentialRow(new String[]{"fic italie"});

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
                            boolean[] cols_kept = header_unify_cols_kept();

                            coltypes_populate(cols_kept);

                            nrow = dim;
                            ncol = get_len(coltypes);
                            df = new ArrayList<>(ncol);
                            this.df_populate(coltypes);

                            initialized = true;
                        } else {
                            rows.next(); // Skipping the header for all subsequent files
                        }

                        while (rows.hasNext()) {
                            int j = 0;
                            int k = 0;
                            String[] parsedRow = rows.next();
                            parsedRow = Arrays.copyOf(parsedRow, coltypes.length);
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
            }
            case "FIC Pologne" -> {
                this.source = true;
                referentialRow = getReferentialRow(new String[]{"fic pologne"});

                List<File> fileList = Arrays.asList(Objects.requireNonNull(new File(folder).listFiles()));
                if (fileList.isEmpty()) return;

                int dim = computeDimFICItaPol(folder, "Pologne");
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
                            boolean[] cols_kept = header_unify_cols_kept();

                            coltypes_populate(cols_kept);

                            nrow = dim;
                            ncol = get_len(coltypes);
                            df = new ArrayList<>(ncol);
                            this.df_populate(coltypes);

                            initialized = true;
                        } else {
                            rows.next(); // Skipping the header for all subsequent files
                        }

                        while (rows.hasNext()) {
                            int j = 0;
                            int k = 0;
                            String[] parsedRow = rows.next();
                            parsedRow = Arrays.copyOf(parsedRow, coltypes.length);
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
            }
            case "FIC Espagne" -> {
                this.source = true;
                referentialRow = getReferentialRow(new String[]{"fic espagne"});

                File file = Objects.requireNonNull(new File(folder).listFiles())[0];

                CsvParserSettings settings = new CsvParserSettings();
                settings.setDelimiterDetectionEnabled(true,delim);
                settings.trimValues(true);

                int i = 0;

                try (Reader inputReader = Files.newBufferedReader(file.toPath(), Charset.forName(encoding))) {
                    CsvParser parser = new CsvParser(settings);
                    List<String[]> parsedRows = parser.parseAll(inputReader);
                    Iterator<String[]> rows = parsedRows.iterator();

                    header = rows.next();
                    header = Arrays.stream(header)
                            .filter(h -> h != null && !h.trim().isEmpty())
                            .toArray(String[]::new);
                    ncol = header.length;
                    nrow = csv_get_nrows(String.valueOf(file.toPath()),delim);
                    boolean[] cols_kept = header_unify_cols_kept();

                    coltypes_populate(cols_kept);

                    ncol = get_len(coltypes);
                    df = new ArrayList<>(ncol);
                    this.df_populate(coltypes);

                    while (rows.hasNext()) {
                        int j = 0;
                        int k = 0;
                        String[] parsedRow = rows.next();
                        parsedRow = Arrays.copyOf(parsedRow, coltypes.length);
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
        }
        coltypesDropSKP();
        date_autofill_agg();
        addStatutFictif();
        populateUniqueNumPoliceValues();
        createPivotTableFic();
        createYearlyPivotTableFic();
        createTotalPivotTableFic();
        populateNumPoliceDateRangeMap();
    } //Fic
    public void addStatutFictif() {
        // Create and fill the new column
        Object[] statut_fictif = new Object[this.nrow];
        Arrays.fill(statut_fictif, STATUT_FICTIF_FIC);
        this.df.add(statut_fictif);

        // Enlarge and update the header
        String[] enlargedHeader = new String[this.header.length + 1];
        System.arraycopy(this.header, 0, enlargedHeader, 0, this.header.length);
        enlargedHeader[this.header.length] = "statut";
        this.header = enlargedHeader;
        this.uniqueStatuts.add(STATUT_FICTIF_FIC);
    }
    public String extractKeyFromFileName(String fileName, String pays) {
        int start = -1;
        int end = -1;
        if (pays.equals("France")) {
            start = fileName.indexOf("ICI");
            if (start == -1) {
                start = fileName.indexOf("FRMP");
            }
            end = fileName.indexOf("_", start);
        }
        else if (pays.equals("Italie") || pays.equals("Pologne") || pays.equals("aux")) {
            start = fileName.indexOf("ICI");
            end = fileName.indexOf(".csv", start);
        }
        else if (pays.equals("Espagne")) {
            start = fileName.indexOf("ICI");
            end = fileName.indexOf("_", start);
        }
        else if (pays.equals("Gamestop")) {
            return extractKeyGamestop(fileName);
        }

        if (start != -1 && end != -1) {
            return fileName.substring(start, end);
        }

        return fileName; // Default to full file name if pattern not found
    }
    public static String extractKeyGamestop(String input) {
        int startIndex = input.indexOf("ICI");
        int endIndex = input.indexOf(" at");

        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            return input.substring(startIndex, endIndex).replace(" ","");
        } else {
            return "";
        }
    }
    public void transformNumPoliceValues() {
        // Get the num_police column using your c method
        Object[] statutValues = c("statut");

        // Loop over all the values in the column and apply the transformations
        for (int i = 0; i < statutValues.length; i++) {
            String currentValue = (String) statutValues[i];

            switch (currentValue) {
                case "termine - accepte" -> statutValues[i] = "terminé - accepté";
                case "termine - refuse immediat" -> statutValues[i] = "terminé - refusé avant instruction";
                default -> statutValues[i] = "terminé sans suite";
            }
        }

        // Replace the old column with the transformed one
        int index = find_in_arr_first_index(header, "statut");
        df.set(index, statutValues);
    }
    public Map<Date, Map<Date, Double>> createPivotTableExcludingStatuses(ArrayList<String> excludedStatuses) {
        Map<Date, Map<Date, List<Double>>> accumulator = new HashMap<>();

        for (int i = 0; i < nrow; i++) {
            String currentStatus = (String) c("statut")[i];

            // Check if the status is not in the excluded list
            if (!excludedStatuses.contains(currentStatus)) {
                Date dateSous = (Date) c("date_sous")[i];
                Date dateSurv = (Date) c("date_surv")[i];
                Double montantIp = (Double) c("montant_IP")[i];

                accumulator
                        .computeIfAbsent(dateSous, k -> new HashMap<>())
                        .computeIfAbsent(dateSurv, k -> new ArrayList<>())
                        .add(montantIp);
            }
        }

        Map<Date, Map<Date, Double>> pivotTable = new HashMap<>();
        for (Date dateSous : accumulator.keySet()) {
            Map<Date, Double> innerMap = new HashMap<>();
            for (Date dateSurv : accumulator.get(dateSous).keySet()) {
                List<Double> montants = accumulator.get(dateSous).get(dateSurv);
                double average = montants.stream().mapToDouble(val -> val).average().orElse(0.0);
                innerMap.put(dateSurv, average);
            }
            pivotTable.put(dateSous, innerMap);
        }

        return pivotTable;
    }
    public double calculateMeanExcludingStatuses(ArrayList<String> excludedStatuses) {
        double sum = 0.0;
        int count = 0;

        for (int i = 0; i < nrow; i++) {
            String currentStatus = (String) c("statut")[i];

            // Check if the status is not in the excluded list
            if (!excludedStatuses.contains(currentStatus)) {
                Double montantIp = (Double) c("montant_IP")[i];
                sum += montantIp;
                count++;
            }
        }

        return count > 0 ? sum / count : 0.0;
    }
    public Map<String, List<Integer>> countAppearancesByYear(String status) {
        // Initialize the final output map
        Map<String, List<Integer>> finalCount = new HashMap<>();

        // Extract the date_sous column
        Object[] dateSousColumn = this.c("date_sous");

        // Create a date formatter
        SimpleDateFormat dateFormatter = new SimpleDateFormat("MM-yyyy");

        // Get unique dates from date_sous column
        Set<String> uniqueDateSous = new HashSet<>();
        for (Object date : dateSousColumn) {
            uniqueDateSous.add(dateFormatter.format((Date) date));
        }

        // Loop over each unique date_sous and count appearances by year
        for (String uniqueDateString : uniqueDateSous) {
            Map<Integer, Integer> yearCounts = new TreeMap<>();

            // Initialize the map with all years from 2013 to 2026 with a count of 0
            for (int year = 2013; year <= 2026; year++) {
                yearCounts.put(year, 0);
            }

            for (int i = 0; i < nrow; i++) {
                String currentDateSousString = dateFormatter.format((Date) c("date_sous")[i]);
                if (uniqueDateString.equals(currentDateSousString)) {
                    String currentStatus = (String) c("statut")[i];
                    if (currentStatus.equals(status)) {
                        Date dateSurv = (Date) c("date_surv")[i];
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(dateSurv);
                        int year = cal.get(Calendar.YEAR);

                        // If the year is between 2013 and 2026, increment the count
                        if (yearCounts.containsKey(year)) {
                            yearCounts.put(year, yearCounts.get(year) + 1);
                        }
                    }
                }
            }
            finalCount.put(uniqueDateString, new ArrayList<>(yearCounts.values()));
        }

        return finalCount;
    }
    public SimpleDateFormat getDateParser(String format) {
        switch (format) {
            case "dd/mm/yyyy":
                return new SimpleDateFormat("dd/MM/yyyy");
            case "#yyyy-mm-dd#":
                return new SimpleDateFormat("yyyy-MM-dd");
            default:
                throw new IllegalArgumentException("Unsupported date format: " + format);
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
    public void createYearlyPivotTable() {
        SimpleDateFormat format = new SimpleDateFormat("MM-yyyy");
        SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy");  // To extract the year from date_surv

        for (Map.Entry<String, Map<String, Map<String, Double>>> outerEntry : pivotTable.entrySet()) {
            String statut = outerEntry.getKey();
            Map<String, Map<String, Double>> middleMap = outerEntry.getValue();

            for (Map.Entry<String, Map<String, Double>> middleEntry : middleMap.entrySet()) {
                String date_sous = middleEntry.getKey();
                Map<String, Double> innerMap = middleEntry.getValue();

                for (Map.Entry<String, Double> innerEntry : innerMap.entrySet()) {
                    String date_surv = innerEntry.getKey();
                    Double montant_IP = innerEntry.getValue();

                    try {
                        Date date = format.parse(date_surv);
                        String year = yearFormat.format(date); // Extract the year from the date

                        pivotTableYearly
                                .computeIfAbsent(statut, k -> new HashMap<>())
                                .computeIfAbsent(date_sous, k -> new HashMap<>())
                                .merge(year, montant_IP, Double::sum);

                    } catch (ParseException e) {
                        e.printStackTrace(); // handle parsing exceptions
                    }
                }
            }
        }
    }
    public void createTotalPivotTable() {
        for (Map.Entry<String, Map<String, Map<String, Double>>> outerEntry : pivotTableYearly.entrySet()) {
            String statut = outerEntry.getKey();
            Map<String, Map<String, Double>> middleMap = outerEntry.getValue();

            for (Map.Entry<String, Map<String, Double>> middleEntry : middleMap.entrySet()) {
                String date_sous = middleEntry.getKey();
                Map<String, Double> innerMap = middleEntry.getValue();

                double yearlyTotal = 0.0;
                for (Double montant : innerMap.values()) {
                    yearlyTotal += montant;
                }

                double roundedTotal = Math.round(yearlyTotal * 100.0) / 100.0;

                pivotTableTotal
                        .computeIfAbsent(statut, k -> new HashMap<>())
                        .put(date_sous, roundedTotal);
            }
        }
    }

    public void createPivotAllStatuts() {
        // Iterate over the pivotTable
        for (Map.Entry<String, Map<String, Map<String, Double>>> statutEntry : pivotTable.entrySet()) {
            Map<String, Map<String, Double>> dateSousMap = statutEntry.getValue();

            for (Map.Entry<String, Map<String, Double>> dateSousEntry : dateSousMap.entrySet()) {
                String date_sous = dateSousEntry.getKey();
                Map<String, Double> dateSurvMap = dateSousEntry.getValue();

                for (Map.Entry<String, Double> dateSurvEntry : dateSurvMap.entrySet()) {
                    String date_surv = dateSurvEntry.getKey();
                    Double montant_IP = dateSurvEntry.getValue();

                    pivotTableAllStatuts
                            .computeIfAbsent(date_sous, k -> new HashMap<>())
                            .merge(date_surv, montant_IP, Double::sum);
                }
            }
        }

        // Round off values in the pivotTableTotal
        for (Map.Entry<String, Map<String, Double>> middleEntry : pivotTableAllStatuts.entrySet()) {
            Map<String, Double> innerMap = middleEntry.getValue();

            for (Map.Entry<String, Double> innerEntry : innerMap.entrySet()) {
                double roundedValue = Math.round(innerEntry.getValue() * 100.0) / 100.0; // Round to 2 decimal places
                innerEntry.setValue(roundedValue);
            }
        }
    }
    public void createYearlyPivotAllStatuts() {
        SimpleDateFormat format = new SimpleDateFormat("MM-yyyy");
        SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy");

        for (Map.Entry<String, Map<String, Double>> dateSousEntry : pivotTableAllStatuts.entrySet()) {
            String date_sous = dateSousEntry.getKey();
            Map<String, Double> dateSurvMap = dateSousEntry.getValue();

            for (Map.Entry<String, Double> dateSurvEntry : dateSurvMap.entrySet()) {
                String date_surv = dateSurvEntry.getKey();
                Double montant_IP = dateSurvEntry.getValue();

                try {
                    Date date = format.parse(date_surv);
                    String year = yearFormat.format(date);

                    pivotTableAllStatutsYearly
                            .computeIfAbsent(date_sous, k -> new HashMap<>())
                            .merge(year, montant_IP, Double::sum);

                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    public void createTotalPivotAllStatuts() {
        for (Map.Entry<String, Map<String, Double>> dateSousEntry : pivotTableAllStatutsYearly.entrySet()) {
            String date_sous = dateSousEntry.getKey();
            Map<String, Double> innerMap = dateSousEntry.getValue();

            double yearlyTotal = 0.0;
            for (Double montant : innerMap.values()) {
                yearlyTotal += montant;
            }

            double roundedTotal = Math.round(yearlyTotal * 100.0) / 100.0;
            pivotTableAllStatutsTotal.put(date_sous, roundedTotal);
        }
    }

    public void createPivotTableFic() {
        // define the format to capture only the month and year of a date
        SimpleDateFormat format = new SimpleDateFormat("MM-yyyy");

        // get column data
        Object[] montant_IPs = c("montant_IP");
        Object[] statuts = c("statut");
        Object[] date_sousArray = c("date_sous");
        Object[] date_survArray = c("date_surv");
        Object[] polices = c("num_police"); // Get the police column

        // iterate over the rows to populate the pivot map
        for (int i = 0; i < nrow; i++) {
            String police = (String) polices[i];
            String statut = (String) statuts[i];
            String date_sous = format.format((Date) date_sousArray[i]);
            String date_surv = format.format((Date) date_survArray[i]);
            Double montant_IP = (Double) montant_IPs[i];

            pivotTableFic
                    .computeIfAbsent(police, p -> new HashMap<>()) // External layer for police
                    .computeIfAbsent(statut, k -> new HashMap<>())
                    .computeIfAbsent(date_sous, k -> new HashMap<>())
                    .merge(date_surv, montant_IP, Double::sum);
        }

        // Iterate over the pivot map to round the values
        for (Map.Entry<String, Map<String, Map<String, Map<String, Double>>>> outermostEntry : pivotTableFic.entrySet()) {
            Map<String, Map<String, Map<String, Double>>> outerMap = outermostEntry.getValue();

            for (Map.Entry<String, Map<String, Map<String, Double>>> outerEntry : outerMap.entrySet()) {
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
    }
    public void createYearlyPivotTableFic() {
        // Format to extract only the year from a date
        SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy");

        // Iterate over the existing pivotTableFic
        for (Map.Entry<String, Map<String, Map<String, Map<String, Double>>>> outermostEntry : pivotTableFic.entrySet()) {
            String police = outermostEntry.getKey();
            Map<String, Map<String, Map<String, Double>>> outerMap = outermostEntry.getValue();

            for (Map.Entry<String, Map<String, Map<String, Double>>> secondEntry : outerMap.entrySet()) {
                String statut = secondEntry.getKey();
                Map<String, Map<String, Double>> middleMap = secondEntry.getValue();

                for (Map.Entry<String, Map<String, Double>> thirdEntry : middleMap.entrySet()) {
                    String date_sous = thirdEntry.getKey();
                    Map<String, Double> innerMap = thirdEntry.getValue();

                    for (Map.Entry<String, Double> innerEntry : innerMap.entrySet()) {
                        String date_surv = innerEntry.getKey();
                        try {
                            String year = yearFormat.format(new SimpleDateFormat("MM-yyyy").parse(date_surv));
                            Double montant_IP = innerEntry.getValue();

                            pivotTableYearlyFic
                                    .computeIfAbsent(police, p -> new HashMap<>())
                                    .computeIfAbsent(statut, s -> new HashMap<>())
                                    .computeIfAbsent(date_sous, ds -> new HashMap<>())
                                    .merge(year, montant_IP, Double::sum);
                        } catch (ParseException e) {
                            e.printStackTrace(); // handle parsing exceptions
                        }
                    }
                }
            }
        }

        // Round the values in pivotTableYearlyFic
        for (Map.Entry<String, Map<String, Map<String, Map<String, Double>>>> outermostEntry : pivotTableYearlyFic.entrySet()) {
            Map<String, Map<String, Map<String, Double>>> outerMap = outermostEntry.getValue();

            for (Map.Entry<String, Map<String, Map<String, Double>>> secondEntry : outerMap.entrySet()) {
                Map<String, Map<String, Double>> middleMap = secondEntry.getValue();

                for (Map.Entry<String, Map<String, Double>> thirdEntry : middleMap.entrySet()) {
                    Map<String, Double> innerMap = thirdEntry.getValue();

                    for (Map.Entry<String, Double> innerEntry : innerMap.entrySet()) {
                        double roundedValue = Math.round(innerEntry.getValue() * 100.0) / 100.0; // Round to 2 decimal places
                        innerEntry.setValue(roundedValue);
                    }
                }
            }
        }

        // You can now replace pivotTableFic with pivotTableYearlyFic or keep both as needed.
    }
    public void createTotalPivotTableFic() {
        // Iterate over the existing pivotTableYearlyFic
        for (Map.Entry<String, Map<String, Map<String, Map<String, Double>>>> outermostEntry : pivotTableYearlyFic.entrySet()) {
            String police = outermostEntry.getKey();
            Map<String, Map<String, Map<String, Double>>> outerMap = outermostEntry.getValue();

            for (Map.Entry<String, Map<String, Map<String, Double>>> secondEntry : outerMap.entrySet()) {
                String statut = secondEntry.getKey();
                Map<String, Map<String, Double>> middleMap = secondEntry.getValue();

                for (Map.Entry<String, Map<String, Double>> thirdEntry : middleMap.entrySet()) {
                    String date_sous = thirdEntry.getKey();
                    Map<String, Double> innerMap = thirdEntry.getValue();

                    double total = 0.0; // Variable to keep the total for a given date_sous
                    for (Map.Entry<String, Double> innerEntry : innerMap.entrySet()) {
                        total += innerEntry.getValue();
                    }

                    // Round the total to 2 decimal places
                    double roundedTotal = Math.round(total * 100.0) / 100.0;

                    // Add the total to pivotTableTotalFic
                    pivotTableTotalFic
                            .computeIfAbsent(police, p -> new HashMap<>())
                            .computeIfAbsent(statut, s -> new HashMap<>())
                            .put(date_sous, roundedTotal);
                }
            }
        }
    }

    public void createPivotTableN() {
        // Define the format to capture only the month and year of a date
        SimpleDateFormat format = new SimpleDateFormat("MM-yyyy");

        // Get column data
        Object[] statuts = c("statut");
        Object[] date_sousArray = c("date_sous");
        Object[] date_survArray = c("date_surv");

        // Iterate over the rows to populate the pivot map
        for (int i = 0; i < nrow; i++) {
            String statut = (String) statuts[i];
            String date_sous = format.format((Date) date_sousArray[i]);
            String date_surv = format.format((Date) date_survArray[i]);
//            if (statut.equals("terminé - accepté") && date_sous.equals("11-2022") && date_surv.equals("05-2023")) {
//                System.out.println("here");
//            }
            pivotTableN
                    .computeIfAbsent(statut, k -> new HashMap<>())
                    .computeIfAbsent(date_sous, k -> new HashMap<>())
                    .merge(date_surv, 1, Integer::sum); // Increase the counter by 1 for each appearance
        }
        System.out.println("ended");
        // The rounding part is no longer necessary since you are just counting appearances.
    }
    public void createYearlyPivotTableN() {
        SimpleDateFormat format = new SimpleDateFormat("MM-yyyy");
        SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy");  // To extract the year from date_surv

        for (Map.Entry<String, Map<String, Map<String, Integer>>> outerEntry : pivotTableN.entrySet()) {
            String statut = outerEntry.getKey();
            Map<String, Map<String, Integer>> middleMap = outerEntry.getValue();

            for (Map.Entry<String, Map<String, Integer>> middleEntry : middleMap.entrySet()) {
                String date_sous = middleEntry.getKey();
                Map<String, Integer> innerMap = middleEntry.getValue();

                for (Map.Entry<String, Integer> innerEntry : innerMap.entrySet()) {
                    String date_surv = innerEntry.getKey();
                    Integer value = innerEntry.getValue();

                    try {
                        Date date = format.parse(date_surv);
                        String year = yearFormat.format(date); // Extract the year from the date

                        pivotTableYearlyN
                                .computeIfAbsent(statut, k -> new HashMap<>())
                                .computeIfAbsent(date_sous, k -> new HashMap<>())
                                .merge(year, value, Integer::sum); // Sum the actual value instead of incrementing by 1

                    } catch (ParseException e) {
                        e.printStackTrace(); // handle parsing exceptions
                    }
                }
            }
        }
    }
    public void createTotalPivotTableN() {
        for (Map.Entry<String, Map<String, Map<String, Integer>>> outerEntry : pivotTableYearlyN.entrySet()) {
            String statut = outerEntry.getKey();
            Map<String, Map<String, Integer>> middleMap = outerEntry.getValue();

            for (Map.Entry<String, Map<String, Integer>> middleEntry : middleMap.entrySet()) {
                String date_sous = middleEntry.getKey();
                Map<String, Integer> innerMap = middleEntry.getValue();

                int yearlyTotal = 0;
                for (Integer count : innerMap.values()) {
                    yearlyTotal += count;
                }

                pivotTableTotalN
                        .computeIfAbsent(statut, k -> new HashMap<>())
                        .put(date_sous, yearlyTotal);
            }
        }
    }

    public void createPivotAllStatutsN() {
        // Iterate over pivotTableN
        for (Map.Entry<String, Map<String, Map<String, Integer>>> statutEntry : pivotTableN.entrySet()) {
            Map<String, Map<String, Integer>> dateSousMap = statutEntry.getValue();

            for (Map.Entry<String, Map<String, Integer>> dateSousEntry : dateSousMap.entrySet()) {
                String date_sous = dateSousEntry.getKey();
                Map<String, Integer> dateSurvMap = dateSousEntry.getValue();

                for (Map.Entry<String, Integer> dateSurvEntry : dateSurvMap.entrySet()) {
                    String date_surv = dateSurvEntry.getKey();
                    Integer count = dateSurvEntry.getValue();

                    pivotTableAllStatutsN
                            .computeIfAbsent(date_sous, k -> new HashMap<>())
                            .merge(date_surv, count, Integer::sum); // Sum the actual value instead of counting appearances
                }
            }
        }
        // No need for rounding since we are summing actual values.
    }
    public void createYearlyPivotAllStatutsN() {
        SimpleDateFormat format = new SimpleDateFormat("MM-yyyy");
        SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy");

        for (Map.Entry<String, Map<String, Integer>> dateSousEntry : pivotTableAllStatutsN.entrySet()) {
            String date_sous = dateSousEntry.getKey();
            Map<String, Integer> dateSurvMap = dateSousEntry.getValue();

            for (Map.Entry<String, Integer> dateSurvEntry : dateSurvMap.entrySet()) {
                String date_surv = dateSurvEntry.getKey();
                Integer count = dateSurvEntry.getValue();

                try {
                    Date date = format.parse(date_surv);
                    String year = yearFormat.format(date);

                    pivotTableAllStatutsYearlyN
                            .computeIfAbsent(date_sous, k -> new HashMap<>())
                            .merge(year, count, Integer::sum); // Count appearances

                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    public void createTotalPivotAllStatutsN() {
        for (Map.Entry<String, Map<String, Integer>> dateSousEntry : pivotTableAllStatutsYearlyN.entrySet()) {
            String date_sous = dateSousEntry.getKey();
            Map<String, Integer> yearMap = dateSousEntry.getValue();

            int yearlyTotal = 0;
            for (Integer count : yearMap.values()) {
                yearlyTotal += count;
            }

            pivotTableAllStatutsTotalN.put(date_sous, yearlyTotal);
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
    public void cleanNumPoliceGS() {
        // Get the "statut" column
        Object[] polices = this.c("num_police");

        // Check if the column exists
        if (polices == null) {
            return;
        }

        // Iterate through each value in the column and replace big dashes with little dashes
        for (int i = 0; i < polices.length; i++) {
            String currentValue = (String) polices[i];
            polices[i] = currentValue.replace(" ", "");
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
    void coltypes_populate(boolean[] cols_kept) {
        coltypes = new Col_types[header.length];
        for (int colIndex = 0; colIndex < header.length; colIndex++) {
            if (cols_kept[colIndex]) {
                coltypes[colIndex] = STR;
            } else {
                coltypes[colIndex] = SKP;
            }
        }
        for (int colIndex = 0; colIndex < header.length; colIndex++) {
            if (header[colIndex].startsWith("date")) {
                coltypes[colIndex] = DAT;
            } else if (header[colIndex].startsWith("montant")) {
                coltypes[colIndex] = DBL;
            }
        }
        headerDropSKP();
    }
    Col_types[] coltypes_populate_aux(boolean[] cols_kept, String[] header) {
        Col_types[] coltypes = new Col_types[header.length];
        for (int colIndex = 0; colIndex < header.length; colIndex++) {
            if (cols_kept[colIndex]) {
                coltypes[colIndex] = STR;
            } else {
                coltypes[colIndex] = SKP;
            }
        }
        for (int colIndex = 0; colIndex < coltypes.length; colIndex++) {
            if (header[colIndex].startsWith("date")) {
                coltypes[colIndex] = DAT;
            } else if (header[colIndex].startsWith("montant")) {
                coltypes[colIndex] = DBL;
            }
        }
        return coltypes;
    }
    SimpleDateFormat getDateFormatter(String dateFormatString) {
        String pattern = switch (dateFormatString) {
            case "#yyyy-mm-dd#" -> "yyyy-MM-dd";
            case "dd/mm/yyyy" -> "dd/MM/yyyy";
            default -> throw new IllegalArgumentException("Unknown date format: " + dateFormatString);
        };

        return new SimpleDateFormat(pattern);
    }
    void date_autofill_agg() {
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

        Set<String> missing_refprog = new HashSet<>();
        for (int i = 0; i < nrow; i++) {
//            System.out.println("Processing row " + i + " of " + nrow + c(indexNumPolice)[i]);
            String currentNumPolice = c(indexNumPolice)[i].toString();
            Date[] refDates = refprogLookup.get(currentNumPolice.toLowerCase());
            if (refDates == null) {
                if (!missing_refprog.contains(currentNumPolice)) {
                    System.out.println("Warning: No ref_prog data found for num_police " + currentNumPolice);
                    missing_refprog.add(currentNumPolice);
                }
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
    void date_autofill_agg_par_police(ArrayList<Object[]> df) {
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
//            System.out.println("Processing row " + i + " of " + nrow + c(indexNumPolice)[i]);
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
    void date_transform (Date date, Date dateDebutRef, Date dateFinRef, int columnIndex, int rowIndex) {
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
    Object[] getRefProgrammesRow (String numPolice, SimpleDateFormat refProgDateFormat) {
        int indexNumContract = find_in_arr_first_index(ref_prog.header, "n°contrat");
        int indexDateDebut = find_in_arr_first_index(ref_prog.header, "date_debut");
        int indexDateFin = find_in_arr_first_index(ref_prog.header, "date_fin");

        for (int i = 0; i < ref_prog.nrow; i++) {
            String contractNumber = (String) ref_prog.c(indexNumContract)[i];
            if (contractNumber != null && contractNumber.equals(numPolice)) {
                Object[] refRow = ref_prog.r(i);
                try {
                    refRow[indexDateDebut] = refProgDateFormat.parse((String) refRow[indexDateDebut]);
                    refRow[indexDateFin] = refProgDateFormat.parse((String) refRow[indexDateFin]);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                return refRow;
            }
        }
        return null;
    }
    Object[] getReferentialRow(String[] keys) {
        String gestionnaire = keys[0];
        String precision = keys.length > 1 ? keys[1] : null;

        for (int rowIndex = 0; rowIndex < ref_cols.nrow; rowIndex++) {
            Object[] row = ref_cols.r(rowIndex);
            if (row[0].equals(gestionnaire)) {
                // If precision is not provided or matches the referential, return the row
                if (precision == null || row[1].equals(precision)) {
                    return row;
                }
            }
        }

        throw new RuntimeException("Referential row not found for keys: " + Arrays.toString(keys));
    }
    Object[] getRefRow(String key) throws Exception {
        for (int rowIndex = 0; rowIndex < ref_cols.nrow; rowIndex++) {
            Object[] row = ref_cols.r(rowIndex);
            if (row[1].equals(key.toLowerCase())) {
                return row;
            }
        }
        throw new Exception("cant find ref row by key: " + key);
    }
    void header_unify() {
        for (int i = 0; i < header.length; i++) {
            int ind = find_in_arr_first_index(this.referentialRow, header[i].toLowerCase());
            if (ind != -1) {
                header[i] = ref_cols.header[ind];
            } else {
                header[i] = "filler";
            }
        }
    }
    boolean[] header_unify_cols_kept() {
        boolean[] output = new boolean[header.length];
        for (int i = 0; i < header.length; i++) {
            int ind = find_in_arr_first_index(this.referentialRow, header[i].toLowerCase());
            if (ind != -1) {
                header[i] = ref_cols.header[ind];
                output[i] = true;
            }
        }
        return output;
    }
    boolean[] header_unify_cols_kept(String[] header) {
        boolean[] output = new boolean[header.length];
        for (int i = 0; i < header.length; i++) {
            int ind = find_in_arr_first_index(this.referentialRow, header[i].toLowerCase());
            if (ind != -1) {
                header[i] = ref_cols.header[ind];
                output[i] = true;
            }
        }
        return output;
    }
    String[] header_unify_aux(String[] header) {
        String[] outputHeader = Arrays.copyOf(header, header.length);
        for (int i = 0; i < header.length; i++) {
            int ind = find_in_arr_first_index(this.referentialRow, header[i].toLowerCase());
            if (ind != -1) {
                outputHeader[i] = ref_cols.header[ind];
            }
        }
        return outputHeader;
    }
    String[] header_unify_return(String[] inputHeader) {
        String[] unifiedHeader = new String[inputHeader.length];
        for (int i = 0; i < inputHeader.length; i++) {
            int ind = find_in_arr_first_index(this.referentialRow, inputHeader[i].toLowerCase());
            if (ind != -1) {
                unifiedHeader[i] = ref_cols.header[ind];
            } else {
                unifiedHeader[i] = inputHeader[i];
            }
        }
        return unifiedHeader;
    }
    String[] getColsFromRefRow() {
        List<String> colsList = new ArrayList<>();
        for (int i = 2; i < referentialRow.length - 1; i++) {
            String colValue = referentialRow[i].toString().toLowerCase().trim();
            if (!colValue.isEmpty()) {
                colsList.add(colValue);
            }
        }
        return colsList.toArray(new String[0]);
    }
    boolean[] mapColnamesAndGetColsKept(String mapping_col) {

        DF map_filtered = mapping.mappingFiltre(mapping_col);

        boolean[] columnsKept = new boolean[header.length];

        for (int i = 0; i < header.length; i++) {
            columnsKept[i] = false;

            for (int j = 0; j < map_filtered.nrow; j++) {
                String formatICI = (String) map_filtered.df.get(0)[j];
                String desiredFormat = (String) map_filtered.df.get(1)[j];

                if (Objects.equals(formatICI, "") || desiredFormat.equals("")) continue;

                if (normalize(header[i]).equalsIgnoreCase(normalize(desiredFormat))) {
                    if (Arrays.asList(referentialRow).contains(formatICI)) {
                        header[i] = formatICI;
                        columnsKept[i] = true;
                        break;
                    }
                }
            }
        }
        return columnsKept;
    }
    public void populateUniqueNumPoliceValues() {
        Object[] polices = c("num_police");
        for (Object obj : polices) {
            uniqueNumPoliceValues.add((String) obj);
        }
    }
    public void populateStatutDateRangeMap() {
        SimpleDateFormat format = new SimpleDateFormat("MM-yyyy");

        for (String statut : uniqueStatuts) {
            Date minDate = null;
            Date maxDate = null;

            Map<String, Map<String, Double>> middleMap = pivotTable.get(statut);
            if (middleMap != null) {
                for (Map<String, Double> innerMap : middleMap.values()) {
                    for (String date_surv : innerMap.keySet()) {
                        try {
                            Date currentDate = format.parse(date_surv);
                            if (minDate == null || currentDate.before(minDate)) {
                                minDate = currentDate;
                            }
                            if (maxDate == null || currentDate.after(maxDate)) {
                                maxDate = currentDate;
                            }
                        } catch (Exception e) {
                            e.printStackTrace(); // handle parsing exceptions
                        }
                    }
                }

                List<Date> dateRange = new ArrayList<>();
                dateRange.add(minDate);
                dateRange.add(maxDate);
                statutDateRangeMap.put(statut, dateRange);
            }
        }
    }
    public void populateNumPoliceDateRangeMap() {
        SimpleDateFormat format = new SimpleDateFormat("MM-yyyy");

        for (String num_police : uniqueNumPoliceValues) {
            Date minDate = null;
            Date maxDate = null;

            Map<String, Map<String, Map<String, Double>>> outerMap = pivotTableFic.get(num_police);
            if (outerMap != null) {
                for (Map<String, Map<String, Double>> middleMap : outerMap.values()) {
                    for (Map<String, Double> innerMap : middleMap.values()) {
                        for (String date_surv : innerMap.keySet()) {
                            try {
                                Date currentDate = format.parse(date_surv);
                                if (minDate == null || currentDate.before(minDate)) {
                                    minDate = currentDate;
                                }
                                if (maxDate == null || currentDate.after(maxDate)) {
                                    maxDate = currentDate;
                                }
                            } catch (Exception e) {
                                e.printStackTrace(); // handle parsing exceptions
                            }
                        }
                    }
                }

                List<Date> dateRange = new ArrayList<>();
                dateRange.add(minDate);
                dateRange.add(maxDate);
                numPoliceDateRangeMap.put(num_police, dateRange);

                if (globalMinDateFic == null || minDate.before(globalMinDateFic)) {
                    globalMinDateFic = minDate;
                }
                if (globalMaxDateFic == null || maxDate.after(globalMaxDateFic)) {
                    globalMaxDateFic = maxDate;
                }
            }
        }
    }

    boolean[] mapColnamesAndKeepNeededAux (String[] localHeader, DF mapping) {
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
    public void populateUniqueStatuts() {
        Object[] statuts = c("statut");
        for (Object obj : statuts) {
            uniqueStatuts.add((String) obj);
        }
    }

    public boolean validateHeader(String[] referenceHeader, String[] currentHeader, String fileName)  {
        if (referenceHeader.length != currentHeader.length) {
            System.out.println("Wrong header length " + fileName);
            return false;
        }
        for (int i = 0; i < referenceHeader.length; i++) {
            if (!referenceHeader[i].equals(currentHeader[i])) {
                System.out.println("Wrong header at position " + i + " for the file " + fileName);
                return false;
            }
        }
        return true;
    }
}
