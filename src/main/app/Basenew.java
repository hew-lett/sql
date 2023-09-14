package main.app;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static main.app.App.*;
import static main.app.Baser.MAX_ANNEE;
import static main.app.Baser.MIN_ANNEE;
import static main.app.DF.Col_types.SKP;
import static main.app.DFnew.ColTypes.STR;
import static main.app.Estimate.minMaxDateMapEstimate;

public class Basenew extends DFnew {
    public static String[] currentHeaderRef = null;
    char delim = ';';
    String pays;
    public static final String LAPARISIENNE = "LaParisienne";
    public static final String MEDIA = "DBCLAIMS";
    public static final String STATUT_FICTIF_FIC = "Comptable";
    public static final char DEFAULT_DELIMITER = ';';
    public static final char TAB_DELIMITER = '\t';
    public static final Date MAX_PREVI_DATE;
    public static final Date MIN_PREVI_DATE;
    static final String CURRENT_MONTH;
    static final String PREVIOUS_MONTH;
    static {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        LocalDate now = LocalDate.now();

        CURRENT_MONTH = now.format(formatter);
        PREVIOUS_MONTH = now.minusMonths(1).format(formatter);

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, 2026);
        calendar.set(Calendar.MONTH, Calendar.DECEMBER);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        MAX_PREVI_DATE = calendar.getTime();

        Calendar calendar2 = Calendar.getInstance();
        calendar2.set(Calendar.YEAR, 2013);
        calendar2.set(Calendar.MONTH, Calendar.NOVEMBER);
        calendar2.set(Calendar.DAY_OF_MONTH, 1);
        calendar2.set(Calendar.HOUR_OF_DAY, 0);
        calendar2.set(Calendar.MINUTE, 0);
        calendar2.set(Calendar.SECOND, 0);
        calendar2.set(Calendar.MILLISECOND, 0);

        MIN_PREVI_DATE = calendar2.getTime();
    }
    public String numPolice = "";
    protected Set<String> uniqueStatuts = new HashSet<>();
    protected Set<String> uniqueNumPoliceValues = new HashSet<>();
    protected Map<String, List<Date>> statutDateRangeMap = new HashMap<>();
    protected Map<String, List<Date>> numPoliceDateRangeMap = new HashMap<>();
    protected Date globalMinDateFic = null;
    protected Date globalMaxDateFic = null;
    protected ArrayList<Object> referentialRow;
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
    public Map<String, Map<String, Map<String, Map<String, Integer>>>> pivotTableFicN = new HashMap<>();
    public Map<String, Map<String, Map<String, Map<String, Integer>>>> pivotTableFicYearlyN = new HashMap<>();
    public Map<String, Map<String, Map<String, Integer>>> pivotTableFicTotalN = new HashMap<>();

    public double coutMoyenEnCours;
    public double coutMoyenEnCoursAccepte;
    public Map<String, List<Integer>> nEnCours;
    public Map<String, List<Integer>> nEnCoursAccepte;

    public static void main(String[] args) throws IOException, ParseException {
        Estimate estimate = new Estimate(wd+"TDB estimate par gestionnaire/SPB France.xlsx");
        Stopwatch stopwatch = new Stopwatch();
        stopwatch.start();
//        File file = new File(wd + "Source SIN/SPB France/Sinistre_Historique_ICICDDV15-1_785_20230803.txt");
//        Basenew base = new Basenew(file,"France","SPB France / Wakam",false);
//        base.print();
//        System.out.println(base.getRow(730));
        Basenew base = new Basenew(wd + "Source FIC/SPB France/","FIC France",false);
        Basenew base1 = new Basenew(wd + "Source FIC/SPB Pologne/","FIC Pologne",false);
        Basenew base2 = new Basenew(wd + "Source FIC/SPB Espagne/","FIC Espagne",false);
        Basenew base3 = new Basenew(wd + "Source FIC/SPB Italie/","FIC Italie",false);
        stopwatch.printElapsedTime();
    }
    public Basenew(File path, String pays, String mappingColDefault, boolean toLower) throws IOException, ParseException {
        this.pays = pays;
        this.referentialRow = getReferentialRow("Source");

        String refFichier = "base";
        FileConfig config = FileConfig.getInstance();
        columnNamesToRead = config.getColumnNamesToRead(refFichier);
        columnTypes = config.getColumnTypes(refFichier);
        columnNamesAttributed = config.getColumnNamesAttributed(refFichier);
        validateColumnInputs(columnNamesToRead, columnTypes, columnNamesAttributed);

        if (pays.equals("Pologne")) {
            delim = '\t';
        }
        if (pays.equals("France")) {
            delim = '|';
        }

        String fileName = path.getName();
        System.out.println(fileName);
        numPolice = extractKeyFromFileName(fileName,pays);

        String mapping_col;
        if (path.toString().contains("FRMP")) {
            mapping_col = "SPB France / ONEY";
        } else {
            mapping_col = mappingColDefault;
        }

        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true, delim);
        settings.trimValues(true);
        CsvParser parser = new CsvParser(settings);

        List<String[]> allRows = parser.parseAll(new FileReader(path, encodingDefault));

        if (allRows.isEmpty()) {
            throw new IllegalArgumentException("CSV file is empty!");
        }

        nrow = allRows.size() - 1;
        columns = new ArrayList<>();
        headers = new ArrayList<>();

        String[] headerRow = mapAndUnifyColnames(mapping_col,allRows.get(0));
        if (pays.equals("Gamestop")) {
            dateFormat = new SimpleDateFormat("#yyyy-MM-dd#");
            headerRow = Arrays.stream(headerRow)
                    .map(h -> h.equals("Date_Declaration") ? "Date_Déclaration" : h)
                    .toArray(String[]::new);
        }

        for (int i = 0; i < columnNamesToRead.size(); i++) { // Iterate over the configuration list
            String expectedHeader = columnNamesToRead.get(i);
            int actualIndex = Arrays.asList(headerRow).indexOf(expectedHeader);

            if (actualIndex != -1) { // If the header exists in the actual data
                String header = headerRow[actualIndex];
                headers.add(columnNamesAttributed != null ? columnNamesAttributed.get(i) : header);

                ArrayList<Object> colData = new ArrayList<>();
                ColTypes colType = (columnTypes == null) ? STR : columnTypes.get(i);

                for (int j = 1; j < allRows.size(); j++) {
                    String cell = allRows.get(j)[actualIndex];
//                    System.out.println(headers.get(i) + " " + j + " " + cell);
                    Object formattedCell;
                    if (toLower) {
                        formattedCell = getLowerCell(cell, colType);
                    } else {
                        formattedCell = getCell(cell, colType);
                    }
                    colData.add(formattedCell);
                }
                columns.add(new Column<>(colData, colType));
            } else {
                throw new RuntimeException("column " + expectedHeader + " not found for base: " + fileName);
            }
        }

        if(pays.equals("Gamestop")) {
            this.cleanNumPoliceGS();
        }
        dataTraitementSin();
    } //Sin
    public Basenew(File path, boolean toLower) throws Exception {
        System.out.println(path);
        String fileName = path.getName();
        numPolice = extractKeyFromFileName(fileName,"aux");
        this.referentialRow = getReferentialRowByPolice(numPolice);

        String refFichier = "base";
        FileConfig config = FileConfig.getInstance();
        columnNamesToRead = config.getColumnNamesToRead(refFichier);
        columnTypes = config.getColumnTypes(refFichier);
        columnNamesAttributed = config.getColumnNamesAttributed(refFichier);
        validateColumnInputs(columnNamesToRead, columnTypes, columnNamesAttributed);

        dateFormat = new SimpleDateFormat((String) referentialRow.get(referentialRow.size() - 1));

        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true, delim);
        settings.trimValues(true);
        CsvParser parser = new CsvParser(settings);

        List<String[]> allRows = parser.parseAll(new FileReader(path, encodingDefault));
        if (allRows.isEmpty()) {
            throw new IllegalArgumentException("CSV file is empty!");
        }
        nrow = allRows.size() - 1;

        columns = new ArrayList<>();
        headers = new ArrayList<>();

        String[] headerRow = unifyColnames(allRows.get(0));
        for (int i = 0; i < columnNamesToRead.size(); i++) { // Iterate over the configuration list
            String expectedHeader = columnNamesToRead.get(i);
            int actualIndex = Arrays.asList(headerRow).indexOf(expectedHeader);

            if (actualIndex != -1) { // If the header exists in the actual data
                String header = headerRow[actualIndex];
                headers.add(columnNamesAttributed != null ? columnNamesAttributed.get(i) : header);

                ArrayList<Object> colData = new ArrayList<>();
                ColTypes colType = (columnTypes == null) ? STR : columnTypes.get(i);

                for (int j = 1; j < allRows.size(); j++) {
                    String cell = allRows.get(j)[actualIndex];
//                    System.out.println(headers.get(i) + " " + j + " " + cell);
                    Object formattedCell;
                    if (toLower) {
                        formattedCell = getLowerCell(cell, colType);
                    } else {
                        formattedCell = getCell(cell, colType);
                    }
                    colData.add(formattedCell);
                }
                columns.add(new Column<>(colData, colType));
            } else {
                throw new RuntimeException("column " + expectedHeader + " not found for base: " + fileName);
            }
        }

        dataTraitementSin();
    } //Sin_aux
    public Basenew(String path, boolean toLower) throws Exception {
        System.out.println(path);
        String fileName = getFilenameWithoutExtension(path);
        if (fileName.equals("Advise")) {
            numPolice = "ICICDAV17";
        }
        if (fileName.equals("Guy Demarle")) {
            numPolice = "ICIGDEG14";
        }
        if (fileName.equals("Garantie Privée")) {
            numPolice = "ICICEDV16";
        }
        this.referentialRow = getReferentialRow(numPolice);

        String refFichier = "base";
        FileConfig config = FileConfig.getInstance();
        columnNamesToRead = config.getColumnNamesToRead(refFichier);
        columnTypes = config.getColumnTypes(refFichier);
        columnNamesAttributed = config.getColumnNamesAttributed(refFichier);
        validateColumnInputs(columnNamesToRead, columnTypes, columnNamesAttributed);

        dateFormat = new SimpleDateFormat((String) referentialRow.get(referentialRow.size() - 1));

        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true, delim);
        settings.trimValues(true);
        CsvParser parser = new CsvParser(settings);

        List<String[]> allRows = parser.parseAll(new FileReader(path, encodingDefault));
        if (allRows.isEmpty()) {
            throw new IllegalArgumentException("CSV file is empty!");
        }
        nrow = allRows.size() - 1;

        columns = new ArrayList<>();
        headers = new ArrayList<>();

        String[] headerRow = unifyColnames(allRows.get(0));
        for (int i = 0; i < columnNamesToRead.size(); i++) { // Iterate over the configuration list
            String expectedHeader = columnNamesToRead.get(i);
            int actualIndex = Arrays.asList(headerRow).indexOf(expectedHeader);

            if (actualIndex != -1) { // If the header exists in the actual data
                String header = headerRow[actualIndex];
                headers.add(columnNamesAttributed != null ? columnNamesAttributed.get(i) : header);

                ArrayList<Object> colData = new ArrayList<>();
                ColTypes colType = (columnTypes == null) ? STR : columnTypes.get(i);

                for (int j = 1; j < allRows.size(); j++) {
                    String cell = allRows.get(j)[actualIndex];
                    Object formattedCell;
                    if (toLower) {
                        formattedCell = getLowerCell(cell, colType);
                    } else {
                        formattedCell = getCell(cell, colType);
                    }
                    colData.add(formattedCell);
                }
                columns.add(new Column<>(colData, colType));
            } else {
                throw new RuntimeException("column " + expectedHeader + " not found for base: " + fileName);
            }
        }

        this.addStatutFictifSin();
        dataTraitementSin();
    } //Sin_aux
    public Basenew(String folder, String refCol, boolean toLower) throws IOException, ParseException {
        List<File> fileList = Arrays.asList(Objects.requireNonNull(new File(folder).listFiles()));
        if (fileList.isEmpty()) {
            throw new IllegalArgumentException("FIC folder is empty!");
        }

        String refFichier = "baseFic";
        FileConfig config = FileConfig.getInstance();
        columnNamesToRead = config.getColumnNamesToRead(refFichier);
        columnTypes = config.getColumnTypes(refFichier);
        columnNamesAttributed = config.getColumnNamesAttributed(refFichier);
        validateColumnInputs(columnNamesToRead, columnTypes, columnNamesAttributed);

        CsvParserSettings settings = new CsvParserSettings();
        settings.trimValues(true);

        referentialRow = getReferentialRow(refCol);

        if (columns == null) columns = new ArrayList<>();
        if (headers == null) headers = new ArrayList<>();

        for (File file : fileList) {
            String fileName = file.getName();
            System.out.println(fileName);

            switch (refCol) {
                case "FIC France" -> settings.setDelimiterDetectionEnabled(true, file.getName().contains(LAPARISIENNE) ? ';' : '\t');
                case "FIC Pologne" -> settings.setDelimiterDetectionEnabled(true, '\t');
                default -> settings.setDelimiterDetectionEnabled(true, delim);
            }
            CsvParser parser = new CsvParser(settings);

            List<String[]> allRows = parser.parseAll(new FileReader(file, encodingDefault));

            if (allRows.isEmpty()) {
                throw new IllegalArgumentException("CSV file is empty!");
            }

            nrow += allRows.size() - 1;
            String[] headerRow = allRows.get(0);
            if (fileName.contains("GS DB")) {
                headerRow = Arrays.stream(headerRow)
                        .map(h -> h.equals("Montant_reglement") ? "Montant_reglement (frais transport inclus)" :
                                  h.equals("Date_declaration") ? "Date_Déclaration" : h)
                        .toArray(String[]::new);
            }
            unifyColnames(headerRow);

            if (columns.isEmpty()) {  // If it's the first file, initialize the columns
                for (int i = 0; i < columnNamesToRead.size(); i++) {
                    String expectedHeader = columnNamesToRead.get(i);
                    int actualIndex = Arrays.asList(headerRow).indexOf(expectedHeader);

                    if (actualIndex != -1) {
                        String header = headerRow[actualIndex];
                        headers.add(columnNamesAttributed != null ? columnNamesAttributed.get(i) : header);

                        ArrayList<Object> colData = new ArrayList<>();
                        ColTypes colType = (columnTypes == null) ? STR : columnTypes.get(i);

                        for (int j = 1; j < allRows.size(); j++) {
                            String cell = allRows.get(j)[actualIndex];
                            Object formattedCell;
                            if (toLower) {
                                formattedCell = getLowerCell(cell, colType);
                            } else {
                                formattedCell = getCell(cell, colType);
                            }
                            colData.add(formattedCell);
                        }
                        columns.add(new Column<>(colData, colType));
                    } else {
                        throw new RuntimeException("column " + expectedHeader + " not found for base: " + fileName);
                    }
                }
            } else {  // If columns are already initialized, append data from this file
                for (int i = 0; i < columnNamesToRead.size(); i++) {
                    String expectedHeader = columnNamesToRead.get(i);
                    int actualIndex = Arrays.asList(headerRow).indexOf(expectedHeader);

                    if (actualIndex != -1) {
                        ArrayList<Object> existingColData = getColumnByIndex(i);
                        ColTypes colType = (columnTypes == null) ? STR : columnTypes.get(i);

                        for (int j = 1; j < allRows.size(); j++) {
                            String cell = allRows.get(j)[actualIndex];
                            Object formattedCell;
                            if (toLower) {
                                formattedCell = getLowerCell(cell, colType);
                            } else {
                                formattedCell = getCell(cell, colType);
                            }
                            existingColData.add(formattedCell);
                        }
                    } else {
                        throw new RuntimeException("column " + expectedHeader + " not found for base: " + fileName);
                    }
                }
            }
        }


//        coltypesDropSKP();
//        date_autofill_agg();
//        addStatutFictif();
//        populateUniqueNumPoliceValues();
//        createPivotTableFic();
//        createYearlyPivotTableFic();
//        createTotalPivotTableFic();
//        createPivotTableFicN();
//        createYearlyPivotTableFicN();
//        createTotalPivotTableFicN();
//        populateNumPoliceDateRangeMap();
    } //Fic
    private void dataTraitementSin() {
        this.cleanStatut();
        this.date_autofill();
        this.createPivotsSin();
        this.populateUniqueStatuts();
        this.populateStatutDateRangeMap();
        this.coutMoyenEnCours = calculateCMencours();
        this.coutMoyenEnCoursAccepte = calculateCMencoursAccepte();
        this.nEnCours = countAppearancesByYear("En cours");
        this.nEnCoursAccepte = countAppearancesByYear("En cours - accepté");
    }
    public void createPivotsSin() {
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
    }

    private ArrayList<Object> getReferentialRow(String key) {
        for (int rowIndex = 0; rowIndex < refCols.nrow; rowIndex++) {
            ArrayList<Object> row = refCols.getRow(rowIndex);
            if (row.get(0).equals(key)) {
                return row;
            }
        }

        throw new RuntimeException("Referential row not found for key: " + key);
    }
    private ArrayList<Object> getReferentialRowByPolice(String key) {
        for (int rowIndex = 0; rowIndex < refCols.nrow; rowIndex++) {
            ArrayList<Object> row = refCols.getRow(rowIndex);
            if (row.get(1).equals(key)) {
                return row;
            }
        }

        throw new RuntimeException("Referential row not found for key: " + key);
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
    private String[] mapAndUnifyColnames(String mapping_col, String[] headerRow) {
        DFnew mapFiltered = mapping.mappingFiltre(mapping_col);

        // Iterate over all headers
        for (int i = 0; i < headerRow.length; i++) {

            for (int j = 0; j < mapFiltered.nrow; j++) {
                String formatICI = (String) mapFiltered.getColumn("Format ICI").get(j);
                String formatGestionnaire = (String) mapFiltered.getColumnByIndex(1).get(j);

                if (formatICI.isEmpty() || formatGestionnaire.isEmpty()) continue;

                if (deleteEaccent(headerRow[i]).equalsIgnoreCase(deleteEaccent(formatGestionnaire))) {
                    int index = referentialRow.indexOf(formatICI);
                    if (index!= -1) {
                        headerRow[i] = refCols.headers.get(index);
                        break;
                    }
                }
            }
        }
        return headerRow;
    }
    private String[] unifyColnames(String[] headerRow) {
        for (int i = 0; i < headerRow.length; i++) {
            if (headerRow[i] == null) continue;
//            if (headerRow[i].equals("Montant_reglement (frais transport inclus)")) {
//                System.out.println("here");
//            }
            int index = referentialRow.indexOf(headerRow[i]);
            if (index!= -1) {
                headerRow[i] = refCols.headers.get(index);
            }
        }
        return headerRow;
    }
    private String deleteEaccent(String input) {
        return input.replace("é", "e");
    }
    public void cleanStatut() {
        ArrayList<String> statuts = this.getColumn("statut");
        for (int i = 0; i < statuts.size(); i++) {
            String currentStatut = statuts.get(i).replace("–", "-");
            if (statutMap.containsKey(currentStatut)) {
                statuts.set(i, statutMap.get(currentStatut));
            }
        }
    }
    public void cleanNumPoliceGS() {
        // Get the "num_police" column
        ArrayList<String> polices = this.getColumn("num_police");

        // Iterate through each value in the column and replace spaces
        polices.replaceAll(value -> value.replace(" ", ""));
    }
    void date_autofill() {
        // Indices for required columns in the current DF
        int indexDateSurv = headers.indexOf("date_surv");
        int indexDateSous = headers.indexOf("date_sous");
        int indexDateDecla = headers.indexOf("date_decla");

        // Indices for required columns in the refProg DF
        int indexContrat = refProg.headers.indexOf("Contrat");
        int indexDateDebutRef = refProg.headers.indexOf("Date Debut");
        int indexDateFinRef = refProg.headers.indexOf("Date Fin");
        int indexAQ = refProg.headers.indexOf("Acquisition des Primes");

        Date dateDebut = null;
        Date dateFin = null;
        boolean mensu = false;
        for (int i = 0; i < refProg.nrow; i++) {
            if (this.numPolice.equalsIgnoreCase(refProg.getColumnByIndex(indexContrat).get(i).toString())) {
                dateDebut = (Date) refProg.getColumnByIndex(indexDateDebutRef).get(i);
                dateFin = (Date) refProg.getColumnByIndex(indexDateFinRef).get(i);
                mensu =  refProg.getColumnByIndex(indexAQ).get(i).equals("Mensuel");
                break;
            }
        }
        if (dateDebut == null) {
            throw new RuntimeException("refProg didn't find dates for " + numPolice);
        }
        Date minTDB = minMaxDateMapEstimate.get(numPolice).get("min");
        Date maxTDB = minMaxDateMapEstimate.get(numPolice).get("max");

        List<Date> dates = Arrays.asList(MAX_PREVI_DATE, dateFin, maxTDB);
        Date dateMaxSous = dates.stream().min(Date::compareTo).orElse(null);
        dates = Arrays.asList(MIN_PREVI_DATE, dateDebut, minTDB);
        Date dateMinSous = dates.stream().max(Date::compareTo).orElse(null);
        Date dateMaxSurv;
        if (MAX_PREVI_DATE.after(dateFin)) {
            dateMaxSurv = dateFin;
        } else {
            dateMaxSurv = MAX_PREVI_DATE;
        }
        Date dateMinSurv;
        if (MIN_PREVI_DATE.before(dateDebut)) {
            dateMinSurv = dateDebut;
        } else {
            dateMinSurv = MIN_PREVI_DATE;
        }

        for (int i = 0; i < nrow; i++) {
            Date dateSurv = (Date) getColumnByIndex(indexDateSurv).get(i);
            Date dateSous = (Date) getColumnByIndex(indexDateSous).get(i);

            if (dateSurv == null) {
                if (!(getColumnByIndex(indexDateDecla).get(i) == null)) {
                    dateSurv = (Date) getColumnByIndex(indexDateDecla).get(i);
                } else if (!(dateSous == null)) {
                    dateSurv = dateSous;
                } else {
                    dateSurv = dateDebut;
                }
            }
            if (dateSous == null || mensu) {
                dateSous = dateSurv;
            }
            if (dateSous.after(dateMaxSous)) {
                dateSous = dateMaxSous;
            }
            if (dateSous.before(dateMinSous)) {
                dateSous = dateMinSous;
            }
            if (dateSurv.after(dateMaxSurv)) {
                dateSurv = dateMaxSurv;
            }
            if (dateSurv.before(dateMinSurv)) {
                dateSurv = dateMinSurv;
            }

            if (dateSurv.before(dateSous)) {
                dateSurv = dateSous;
            }
            date_transform(dateSurv, indexDateSurv, i);
            date_transform(dateSous, indexDateSous, i);
        }
    }
//    void date_autofill_agg() {
//        // Indices for required columns in the current DF
//        int indexDateSurv = find_in_arr_first_index(header, "date_surv");
//        int indexDateSous = find_in_arr_first_index(header, "date_sous");
//        int indexDateDecla = find_in_arr_first_index(header, "date_decla");
//        int indexNumPolice = find_in_arr_first_index(header, "num_police");
//
//        // Indices for required columns in the refProg DF
//        int indexContrat = find_in_arr_first_index(refProg.header, "n°contrat");
//        int indexDateDebutRef = find_in_arr_first_index(refProg.header, "date_debut");
//        int indexDateFinRef = find_in_arr_first_index(refProg.header, "date_fin");
//        int indexAQ = find_in_arr_first_index(refProg.header, "acquisition des primes");
//
//        // Return early if the num_police column doesn't exist
//        if (indexNumPolice == -1) return;
//
//        // If date_surv column doesn't exist, create it
//        if (indexDateSurv == -1) {
//            indexDateSurv = ncol;
//            Object[] newColumn = new Object[nrow];
//            Arrays.fill(newColumn, NA_DAT);
//            df.add(newColumn);
//            ncol++;
//        }
//
//        // If date_sous column doesn't exist, create it
//        if (indexDateSous == -1) {
//            indexDateSous = ncol;
//            Object[] newColumn = new Object[nrow];
//            Arrays.fill(newColumn, NA_DAT);
//            df.add(newColumn);
//            ncol++;
//        }
//
//        // Cache for quick lookup of refProg data based on num_police/n°contrat
//        Map<String, Date[]> refprogLookup = new HashMap<>();
//        Map<String, Boolean> mensuMap = new HashMap<>();
//        for (int i = 0; i < refProg.nrow; i++) {
//            String contrat = refProg.getColumnByIndex(indexContrat).get(i).toString();
//            Date dateDebut = (Date) refProg.getColumnByIndex(indexDateDebutRef).get(i);
//            Date dateFin = (Date) refProg.getColumnByIndex(indexDateFinRef).get(i);
//            Map<String, Date> contratMap = minMaxDateMapEstimate.get(contrat.toUpperCase());
//            if (contratMap == null) continue;
//            Date minTDB = contratMap.get("min");
//            Date maxTDB = contratMap.get("max");
//            if (dateDebut == null) {
//                Date dateMaxSous;
//                if (MAX_PREVI_DATE.after(maxTDB)) {
//                    dateMaxSous = maxTDB;
//                } else {
//                    dateMaxSous = MAX_PREVI_DATE;
//                }
//                Date dateMinSous;
//                if (MIN_PREVI_DATE.before(minTDB)) {
//                    dateMinSous = minTDB;
//                } else {
//                    dateMinSous = MIN_PREVI_DATE;
//                }
//
//                refprogLookup.put(contrat, new Date[]{null, dateFin, dateMinSous,dateMaxSous, MIN_PREVI_DATE, MAX_PREVI_DATE});
//            } else {
//                List<Date> dates = Arrays.asList(MAX_PREVI_DATE, dateFin, maxTDB);
//                Date dateMaxSous = dates.stream().min(Date::compareTo).orElse(null);
//                dates = Arrays.asList(MIN_PREVI_DATE, dateDebut, minTDB);
//                Date dateMinSous = dates.stream().max(Date::compareTo).orElse(null);
//                Date dateMaxSurv;
//                if (MAX_PREVI_DATE.after(dateFin)) {
//                    dateMaxSurv = dateFin;
//                } else {
//                    dateMaxSurv = MAX_PREVI_DATE;
//                }
//                Date dateMinSurv;
//                if (MIN_PREVI_DATE.before(dateDebut)) {
//                    dateMinSurv = dateDebut;
//                } else {
//                    dateMinSurv = MIN_PREVI_DATE;
//                }
//                refprogLookup.put(contrat, new Date[]{dateDebut, dateFin, dateMinSous,dateMaxSous,dateMinSurv,dateMaxSurv});
//            }
//            mensuMap.putIfAbsent(contrat,refProg.getColumnByIndex(indexAQ).get(i).equals("mensuel"));
//
//        }
//
//        Set<String> missing_refprog = new HashSet<>();
//        for (int i = 0; i < nrow; i++) {
//
////            System.out.println("Processing row " + i + " of " + nrow + getColumnByIndex(indexNumPolice).get(i));
//            String currentNumPolice = getColumnByIndex(indexNumPolice).get(i).toString();
//            Date[] refDates = refprogLookup.get(currentNumPolice.toLowerCase());
//            if (refDates == null) {
//                if (!missing_refprog.contains(currentNumPolice)) {
//                    System.out.println("Warning: No refProg data found for num_police " + currentNumPolice);
//                    missing_refprog.add(currentNumPolice);
//                }
//                continue;
//            }
//
//            Date dateDebut = refDates[0];
//            Date dateFin = refDates[1];
//            Date dateMinSous = refDates[2];
//            Date dateMaxSous = refDates[3];
//            Date dateMinSurv = refDates[4];
//            Date dateMaxSurv = refDates[5];
//            boolean mensu = mensuMap.get(currentNumPolice);
//
//            Date dateSurv = (Date) getColumnByIndex(indexDateSurv).get(i);
//            Date dateSous = (Date) getColumnByIndex(indexDateSous).get(i);
//
//            if (dateSurv == null) {
//                if (!getColumnByIndex(indexDateDecla).get(i) == null) {
//                    dateSurv = (Date) getColumnByIndex(indexDateDecla).get(i);
//                } else if (!dateSous == null) {
//                    dateSurv = dateSous;
//                } else {
//                    dateSurv = dateDebut;
//                }
//            }
//            if (dateSous == null || mensu) {
//                dateSous = dateSurv;
//            }
//            if (dateSous.after(dateMaxSous)) {
//                dateSous = dateMaxSous;
//            }
//            if (dateSous.before(dateMinSous)) {
//                dateSous = dateMinSous;
//            }
//            if (dateSurv.after(dateMaxSurv)) {
//                dateSurv = dateMaxSurv;
//            }
//            if (dateSurv.before(dateMinSurv)) {
//                dateSurv = dateMinSurv;
//            }
//
//            if (dateSurv.before(dateSous)) {
//                dateSurv = dateSous;
//            }
//            date_transform(dateSurv, indexDateSurv, i);
//            date_transform(dateSous, indexDateSous, i);
//        }
//    }
    void date_transform (Date date, int columnIndex, int rowIndex) {
        // Change the date to the 1st day of the month
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        date = cal.getTime();

        // Update the dates in the DF
        getColumnByIndex(columnIndex).set(rowIndex,date);
    }
    public void createPivotTable() {
        // define the format to capture only the month and year of a date
        SimpleDateFormat format = new SimpleDateFormat("MM-yyyy");

        // get columns using getColumn method
        List<Object> montant_IPs = getColumn("montant_IP");
        List<Object> statuts = getColumn("statut");
        List<Object> date_sousArray = getColumn("date_sous");
        List<Object> date_survArray = getColumn("date_surv");

        // iterate over the rows to populate the pivot map
        for (int i = 0; i < nrow; i++) {
            String statut = (String) statuts.get(i);
            String date_sous = format.format((Date) date_sousArray.get(i));
            String date_surv = format.format((Date) date_survArray.get(i));
            Double montant_IP = (Double) montant_IPs.get(i);

            pivotTable
                    .computeIfAbsent(statut, k -> new HashMap<>())
                    .computeIfAbsent(date_sous, k -> new HashMap<>())
                    .merge(date_surv, montant_IP, Double::sum);
        }

        roundValuesPivot(pivotTable);
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
        roundValuesPivotInner(pivotTableAllStatuts);
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

    public void createPivotTableN() {
        // Define the format to capture only the month and year of a date
        SimpleDateFormat format = new SimpleDateFormat("MM-yyyy");

        // Get column data using getColumn
        List<Object> statuts = getColumn("statut");
        List<Object> date_sousArray = getColumn("date_sous");
        List<Object> date_survArray = getColumn("date_surv");

        // Iterate over the rows to populate the pivot map
        for (int i = 0; i < nrow; i++) {
            String statut = (String) statuts.get(i);
            String date_sous = format.format((Date) date_sousArray.get(i));
            String date_surv = format.format((Date) date_survArray.get(i));

            pivotTableN
                    .computeIfAbsent(statut, k -> new HashMap<>())
                    .computeIfAbsent(date_sous, k -> new HashMap<>())
                    .merge(date_surv, 1, Integer::sum); // Increase the counter by 1 for each appearance
        }
        // No rounding needed since we're only counting occurrences.
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

    public void createPivotTableFic() {
        // Define the format to capture only the month and year of a date
        SimpleDateFormat format = new SimpleDateFormat("MM-yyyy");

        // Get column data using getColumn
        List<Object> montant_IPs = getColumn("montant_IP");
        List<Object> statuts = getColumn("statut");
        List<Object> date_sousArray = getColumn("date_sous");
        List<Object> date_survArray = getColumn("date_surv");
        List<Object> polices = getColumn("num_police");

        // Iterate over the rows to populate the pivot map
        for (int i = 0; i < nrow; i++) {
            String police = (String) polices.get(i);
            String statut = (String) statuts.get(i);
            String date_sous = format.format((Date) date_sousArray.get(i));
            String date_surv = format.format((Date) date_survArray.get(i));
            Double montant_IP = (Double) montant_IPs.get(i);

            pivotTableFic
                    .computeIfAbsent(police, p -> new HashMap<>()) // External layer for police
                    .computeIfAbsent(statut, k -> new HashMap<>())
                    .computeIfAbsent(date_sous, k -> new HashMap<>())
                    .merge(date_surv, montant_IP, Double::sum);
        }

        // Iterate over the pivot map to round the values
        for (Map.Entry<String, Map<String, Map<String, Map<String, Double>>>> outermostEntry : pivotTableFic.entrySet()) {
            Map<String, Map<String, Map<String, Double>>> outerMap = outermostEntry.getValue();

            roundValuesPivot(outerMap);
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

            roundValuesPivot(outerMap);
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

    public void createPivotTableFicN() {
        SimpleDateFormat format = new SimpleDateFormat("MM-yyyy");

        // Get column data using getColumn
        List<Object> statuts = getColumn("statut");
        List<Object> date_sousArray = getColumn("date_sous");
        List<Object> date_survArray = getColumn("date_surv");
        List<Object> polices = getColumn("num_police");

        for (int i = 0; i < nrow; i++) {
            String police = (String) polices.get(i);
            String statut = (String) statuts.get(i);
            String date_sous = format.format((Date) date_sousArray.get(i));
            String date_surv = format.format((Date) date_survArray.get(i));

            pivotTableFicN
                    .computeIfAbsent(police, p -> new HashMap<>())
                    .computeIfAbsent(statut, k -> new HashMap<>())
                    .computeIfAbsent(date_sous, k -> new HashMap<>())
                    .merge(date_surv, 1, Integer::sum); // Increment by 1 for each appearance
        }
    }
    public void createYearlyPivotTableFicN() {
        SimpleDateFormat format = new SimpleDateFormat("MM-yyyy");
        SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy");

        for (Map.Entry<String, Map<String, Map<String, Map<String, Integer>>>> outermostEntry : pivotTableFicN.entrySet()) {
            String police = outermostEntry.getKey();
            Map<String, Map<String, Map<String, Integer>>> outerMap = outermostEntry.getValue();

            for (Map.Entry<String, Map<String, Map<String, Integer>>> outerEntry : outerMap.entrySet()) {
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
                            String year = yearFormat.format(date);

                            pivotTableFicYearlyN
                                    .computeIfAbsent(police, p -> new HashMap<>())
                                    .computeIfAbsent(statut, k -> new HashMap<>())
                                    .computeIfAbsent(date_sous, k -> new HashMap<>())
                                    .merge(year, value, Integer::sum);

                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }
    public void createTotalPivotTableFicN() {
        for (Map.Entry<String, Map<String, Map<String, Map<String, Integer>>>> outermostEntry : pivotTableFicYearlyN.entrySet()) {
            String police = outermostEntry.getKey();
            Map<String, Map<String, Map<String, Integer>>> outerMap = outermostEntry.getValue();

            for (Map.Entry<String, Map<String, Map<String, Integer>>> outerEntry : outerMap.entrySet()) {
                String statut = outerEntry.getKey();
                Map<String, Map<String, Integer>> middleMap = outerEntry.getValue();

                for (Map.Entry<String, Map<String, Integer>> middleEntry : middleMap.entrySet()) {
                    String date_sous = middleEntry.getKey();
                    Map<String, Integer> innerMap = middleEntry.getValue();

                    int yearlyTotal = 0;
                    for (Integer count : innerMap.values()) {
                        yearlyTotal += count;
                    }

                    pivotTableFicTotalN
                            .computeIfAbsent(police, p -> new HashMap<>())
                            .computeIfAbsent(statut, k -> new HashMap<>())
                            .put(date_sous, yearlyTotal);
                }
            }
        }
    }

    private void roundValuesPivot(Map<String, Map<String, Map<String, Double>>> pivotTable) {
        for (Map.Entry<String, Map<String, Map<String, Double>>> outerEntry : pivotTable.entrySet()) {
            Map<String, Map<String, Double>> middleMap = outerEntry.getValue();

            roundValuesPivotInner(middleMap);
        }
    }
    private void roundValuesPivotInner(Map<String, Map<String, Double>> middleMap) {
        for (Map.Entry<String, Map<String, Double>> middleEntry : middleMap.entrySet()) {
            Map<String, Double> innerMap = middleEntry.getValue();

            for (Map.Entry<String, Double> innerEntry : innerMap.entrySet()) {
                double roundedValue = Math.round(innerEntry.getValue() * 100.0) / 100.0; // Round to 2 decimal places
                innerEntry.setValue(roundedValue);
            }
        }
    }
    public void populateUniqueStatuts() {
        uniqueStatuts.addAll(getColumn("statut"));
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
    public double calculateCMencours() {
        ArrayList<String> excludedStatuses = new ArrayList<>(Arrays.asList("En attente de prescription", "En cours"));

        double sum = 0.0;
        int count = 0;

        ArrayList<String> statutCol = getColumn("statut");
        ArrayList<Double> montantCol = getColumn("montant_IP");
        for (int i = 0; i < nrow; i++) {

            // Check if the status is not in the excluded list
            if (!excludedStatuses.contains(statutCol.get(i))) {
                sum += montantCol.get(i);
                count++;
            }
        }

        return count > 0 ? sum / count : 0.0;
    }
    public double calculateCMencoursAccepte() {
        double sum = 0.0;
        int count = 0;

        ArrayList<String> statutCol = getColumn("statut");
        ArrayList<Double> montantCol = getColumn("montant_IP");
        for (int i = 0; i < nrow; i++) {
            if (statutCol.get(i).equals("Terminé - accepté")) {
                sum += montantCol.get(i);
                count++;
            }
        }

        return count > 0 ? sum / count : 0.0;
    }
    public Map<String, List<Integer>> countAppearancesByYear(String statutX) {
        // Initialize the final output map
        Map<String, List<Integer>> finalCount = new HashMap<>();

        // Extract the date_sous and date_surv columns
        ArrayList<Date> dateSousColumn = getColumn("date_sous");
        ArrayList<Date> dateSurvColumn = getColumn("date_surv");
        ArrayList<String> statutColumn = getColumn("statut");

        // Create a date formatter
        SimpleDateFormat dateFormatter = new SimpleDateFormat("MM-yyyy");

        Calendar calendar = Calendar.getInstance();

        for (int i = 0; i < nrow; i++) {
            String statut = statutColumn.get(i);

            if (statut.equals(statutX)) {
                Date dateSous = dateSousColumn.get(i);
                Date dateSurv = dateSurvColumn.get(i);

                String dateSousFormatted = dateFormatter.format(dateSous);
                calendar.setTime(dateSurv);
                int year = calendar.get(Calendar.YEAR);

                if (year >= MIN_ANNEE && year <= MAX_ANNEE) {
                    finalCount.computeIfAbsent(dateSousFormatted, k -> new ArrayList<>(Collections.nCopies(14, 0)))
                            .set(year - MIN_ANNEE, finalCount.get(dateSousFormatted).get(year - MIN_ANNEE) + 1);
                }
            }
        }

        return finalCount;
    }
    public void addStatutFictifSin() {
        int indStatut = headers.indexOf("statut");
        if (indStatut == -1) {
            ArrayList<String> totalValues = new ArrayList<>(Collections.nCopies(nrow, "Total"));
            addColumn("statut", totalValues, STR);
        }
    }
    public static String getFilenameWithoutExtension(String fullPath) {
        String filename = new java.io.File(fullPath).getName();
        if (filename.endsWith(".csv")) {
            return filename.substring(0, filename.length() - 4); // 4 because ".csv" has 4 characters
        }
        return filename;
    }
}
