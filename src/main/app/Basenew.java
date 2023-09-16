package main.app;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.util.Map;

import static main.app.App.*;
import static main.app.DFnew.ColTypes.STR;
import static main.app.Estimatenew.gapsMap;
import static main.app.Estimatenew.minMaxDateSousMapEstimate;

public class Basenew extends DFnew {
    public static String[] currentHeaderRef = null;
    char delim = ';';
    String pays;
    public static final String LAPARISIENNE = "LaParisienne";
    public static final String MEDIA = "DBCLAIMS";
    public static final String STATUT_FICTIF_FIC = "Comptable";
    public static final char DEFAULT_DELIMITER = ';';
    public static final char TAB_DELIMITER = '\t';
    static final int MAX_ANNEE = 2026;
    static final int MIN_ANNEE = 2013;
    static final int yearN = MAX_ANNEE - MIN_ANNEE + 1;
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
        calendar.set(Calendar.YEAR, MAX_ANNEE);
        calendar.set(Calendar.MONTH, Calendar.DECEMBER);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        MAX_PREVI_DATE = calendar.getTime();

        Calendar calendar2 = Calendar.getInstance();
        calendar2.set(Calendar.YEAR, MIN_ANNEE);
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
    public Map<String, Map<Date, Map<Date, Double>>> pivotTable = new HashMap<>();
    public Map<String, Map<Date, Map<Integer, Double>>> pivotTableYearly = new HashMap<>();
    public Map<String, Map<Date, Double>> pivotTableTotal = new HashMap<>();
    public Map<String, Map<Date, Map<Date, Integer>>> pivotTableN = new HashMap<>();
    public Map<String, Map<Date, Map<Integer, Integer>>> pivotTableYearlyN = new HashMap<>();
    public Map<String, Map<Date, Integer>> pivotTableTotalN = new HashMap<>();

    public Map<Date, Map<Date, Double>> pivotTableAllStatuts = new HashMap<>();
    public Map<Date, Map<Integer, Double>> pivotTableAllStatutsYearly = new HashMap<>();
    public Map<Date, Double> pivotTableAllStatutsTotal = new HashMap<>();
    public Map<Date, Map<Date, Integer>> pivotTableAllStatutsN = new HashMap<>();
    public Map<Date, Map<Integer, Integer>> pivotTableAllStatutsYearlyN = new HashMap<>();
    public Map<Date, Integer> pivotTableAllStatutsTotalN = new HashMap<>();


    public Map<String, Map<String, Map<Date, Map<Date, Double>>>> pivotTableFic = new HashMap<>();
    public Map<String, Map<String, Map<Date, Map<Integer, Double>>>> pivotTableYearlyFic = new HashMap<>();
    public Map<String, Map<String, Map<Date, Double>>> pivotTableTotalFic = new HashMap<>();
    public Map<String, Map<String, Map<Date, Map<Date, Integer>>>> pivotTableFicN = new HashMap<>();
    public Map<String, Map<String, Map<Date, Map<Integer, Integer>>>> pivotTableFicYearlyN = new HashMap<>();
    public Map<String, Map<String, Map<Date, Integer>>> pivotTableFicTotalN = new HashMap<>();

    public double coutMoyenEnCours;
    public double coutMoyenEnCoursAccepte;
    public Map<Date, List<Integer>> nEnCours;
    public Map<Date, List<Integer>> nEnCoursAccepte;

    public static void main(String[] args) throws Exception {
//        Estimate estimate = new Estimate(wd+"TDB estimate par gestionnaire/SPB France.xlsx");
        Stopwatch stopwatch = new Stopwatch();
        stopwatch.start();

        for (int i = 0; i < refSource.nrow; i++) {
            boolean a_faire = (refSource.getColumn("a faire").get(i)).equals("oui");
            if (!a_faire) continue;
            String folder = (String) refSource.getColumn("path").get(i);
            String pays = (String) refSource.getColumn("pays_filekey").get(i);
            String mapcol = (String) refSource.getColumn("mapping").get(i);
            String estim = (String) refSource.getColumn("estimate").get(i);
            String path_fic = (String) refSource.getColumn("path_fic").get(i);
            String map_fic = (String) refSource.getColumn("map_fic").get(i);

            Estimate estimate = new Estimate(wd+"TDB estimate par gestionnaire/" + estim + ".xlsx");
            File[] fileList = Objects.requireNonNull(new File(wd + folder).listFiles());

            for (File file : fileList) {
//                if (!file.toPath().toString().contains("EXTR")) continue;
                Basenew base = new Basenew(file,pays,mapcol,false);
                baseMapNew.put(base.numPolice, base);
                for (String statut : base.uniqueStatuts) {
                    globalStatutCollect.putIfAbsent(statut, base.numPolice);
                }

            }
            if (pays.equals("Italie")) {
                File[] fileListGS = Objects.requireNonNull(new File(wd + "source SIN/Gamestop/").listFiles());
                for (File file : fileListGS) {
//                if (!file.toPath().toString().contains("SMIC"))  continue;
                    Basenew base = new Basenew(file,"Gamestop","SPB Italie Gamestop v1",false);
                    baseMapNew.put(base.numPolice, base);
                    for (String statut : base.uniqueStatuts) {
                        globalStatutCollect.putIfAbsent(statut, base.numPolice);
                    }
                }
                Basenew baseGPTB = new Basenew(new File(wd + "aux SIN/SPB Italie_ICIGPTB15.csv"),false);
                Basenew baseMITL = new Basenew(new File(wd + "aux SIN/SPB Italie_ICIMITL16.csv"),false);
                baseMapNew.put(baseGPTB.numPolice, baseGPTB);
                baseMapNew.put(baseMITL.numPolice, baseMITL);
                for (String statut : baseGPTB.uniqueStatuts) {
                    globalStatutCollect.putIfAbsent(statut, baseGPTB.numPolice);
                }
                for (String statut : baseMITL.uniqueStatuts) {
                    globalStatutCollect.putIfAbsent(statut, baseMITL.numPolice);
                }
            }

        }
        saveMapToExcel(globalStatutCollect, wd + "statuts_à_revoir.xlsx");
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

        if (refCol.equals("FIC France")) {
            cleanNumPoliceDBP();
        }
        dataTraitementFic();
    } //Fic

    private void dataTraitementSin() {
        this.cleanStatut();
        this.date_autofill();
        this.repairGapsDateSousSIN();
        this.addYearColumns();
        this.createPivotTables();
        this.populateUniqueStatuts();
        this.populateStatutDateRangeMap();
        this.coutMoyenEnCours = calculateCMencours();
        this.coutMoyenEnCoursAccepte = calculateCMencoursAccepte();
        this.nEnCours = countAppearancesByYear("En cours");
        this.nEnCoursAccepte = countAppearancesByYear("En cours - accepté");
    }
    private void dataTraitementFic() {
        date_autofill_agg();
        repairGapsDateSousFIC();
        addYearColumns();
        addStatutFictifFic();
        populateUniqueNumPoliceValues();

        createPivotTablesFic();

        populateNumPoliceDateRangeMap();
    }
    public void addYearColumns() {
        ArrayList<Date> dateSurvColumn = getColumn(DATE_SURV);

        ArrayList<Integer> yearSurvColumn = new ArrayList<>();

        // Extracting the year from date_surv
        for (Date date : dateSurvColumn) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            yearSurvColumn.add(calendar.get(Calendar.YEAR));
        }

        Column<Integer> yearSurvNewColumn = new Column<>(yearSurvColumn, ColTypes.INT);

        columns.add(yearSurvNewColumn);

        headers.add(YEAR_SURV);
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
        ArrayList<String> statuts = this.getColumn(STATUT);
        for (int i = 0; i < statuts.size(); i++) {
            if(statuts.get(i) != null) {
                String currentStatut = statuts.get(i).replace("–", "-");
                if (globalStatutMap.containsKey(currentStatut)) {
                    statuts.set(i, globalStatutMap.get(currentStatut));
                }
            } else {
                statuts.set(i, "");
            }
        }
    }
    public void cleanNumPoliceGS() {
        ArrayList<String> polices = this.getColumn(POLICE);
        polices.replaceAll(value -> value.replace(" ", ""));
    }
    private void cleanNumPoliceDBP() {
        ArrayList<String> polices = getColumn(POLICE);
        for (int i = 0; i < nrow; i++) {
            if (polices.get(i).equals("ICICDBP17")) {
                polices.set(i, "ICIDBP17");
            }
        }
    }

    void date_autofill() {
        // Indices for required columns in the current DF
        int indexDateSurv = headers.indexOf(DATE_SURV);
        int indexDateSous = headers.indexOf(DATE_SOUS);
        ArrayList<Date> colDateSurv = getColumn(DATE_SURV);
        ArrayList<Date> colDateSous = getColumn(DATE_SOUS);
        ArrayList<Date> colDateDecla = getColumn(DATE_DECLA);

        // Indices for required columns in the refProg DF
        ArrayList<String> colContrat = refProg.getColumn("Contrat");
        ArrayList<Date> colDateDebutRef = refProg.getColumn("Date Debut");
        ArrayList<Date> colDateFinRef = refProg.getColumn("Date Fin");
        ArrayList<String> colAQ = refProg.getColumn("Acquisition des Primes");

        Date dateDebut = null;
        Date dateFin = null;
        boolean mensu = false;
        for (int i = 0; i < refProg.nrow; i++) {
            if (this.numPolice.equalsIgnoreCase(colContrat.get(i))) {
                dateDebut = colDateDebutRef.get(i);
                dateFin = colDateFinRef.get(i);
                mensu =  colAQ.get(i).equals("Mensuel");
                break;
            }
        }
        if (dateDebut == null) {
            throw new RuntimeException("refProg didn't find dates for " + numPolice);
        }
        Date minDateSousTDB = minMaxDateSousMapEstimate.get(numPolice).get("min");
        Date maxDateSousTDB = minMaxDateSousMapEstimate.get(numPolice).get("max");

        List<Date> dates = Arrays.asList(MAX_PREVI_DATE, dateFin, maxDateSousTDB);
        Date dateMaxSous = dates.stream().min(Date::compareTo).orElse(null);
        dates = Arrays.asList(MIN_PREVI_DATE, dateDebut, minDateSousTDB);
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
            repairDates(indexDateSurv, indexDateSous, colDateSurv, colDateSous, colDateDecla, i, dateMinSous, dateMaxSous, dateMinSurv, dateMaxSurv, mensu);
        }
    }
    void date_autofill_agg() {
        int indexDateSurv = headers.indexOf(DATE_SURV);
        int indexDateSous = headers.indexOf(DATE_SOUS);
        ArrayList<Date> colDateSurv = getColumn(DATE_SURV);
        ArrayList<Date> colDateSous = getColumn(DATE_SOUS);
        ArrayList<Date> colDateDecla = getColumn(DATE_DECLA);
        ArrayList<String> colPolice = getColumn(POLICE);

        // Indices for required columns in the refProg DF
        ArrayList<String> colContrat = refProg.getColumn("Contrat");
        ArrayList<Date> colDateDebutRef = refProg.getColumn("Date Debut");
        ArrayList<Date> colDateFinRef = refProg.getColumn("Date Fin");
        ArrayList<String> colAQ = refProg.getColumn("Acquisition des Primes");

        // Cache for quick lookup of refProg data based on num_police/n°contrat
        Map<String, Date[]> refprogLookup = new HashMap<>();
        Map<String, Boolean> mensuMap = new HashMap<>();
        for (int i = 0; i < refProg.nrow; i++) {
            String contrat = colContrat.get(i);
            Date dateDebut = colDateDebutRef.get(i);
            Date dateFin = colDateFinRef.get(i);

            Map<String, Date> contratMap = minMaxDateSousMapEstimate.get(contrat.toUpperCase());
            if (contratMap == null) {
                // contrat n'existe pas dans TDB mais existe dans le fic
                refprogLookup.put(contrat, new Date[]{MIN_PREVI_DATE, MAX_PREVI_DATE, MIN_PREVI_DATE, MAX_PREVI_DATE});
                continue;
            }
            Date minDateSousTDB = contratMap.get("min");
            Date maxDateSousTDB = contratMap.get("max");
            if (dateDebut == null) { // si absent dans ref programmes, on definit que les bornes de la date souscription
                Date dateMaxSous;
                if (MAX_PREVI_DATE.after(maxDateSousTDB)) {
                    dateMaxSous = maxDateSousTDB;
                } else {
                    dateMaxSous = MAX_PREVI_DATE;
                }
                Date dateMinSous;
                if (MIN_PREVI_DATE.before(minDateSousTDB)) {
                    dateMinSous = minDateSousTDB;
                } else {
                    dateMinSous = MIN_PREVI_DATE;
                }
                // toute l'intervalle dispo pour la date survenance
                refprogLookup.put(contrat, new Date[]{dateMinSous, dateMaxSous, MIN_PREVI_DATE, MAX_PREVI_DATE});
            } else {
                List<Date> dates = Arrays.asList(MAX_PREVI_DATE, dateFin, maxDateSousTDB);
                Date dateMaxSous = dates.stream().min(Date::compareTo).orElse(null);
                dates = Arrays.asList(MIN_PREVI_DATE, dateDebut, minDateSousTDB);
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
                refprogLookup.put(contrat, new Date[]{dateMinSous, dateMaxSous, dateMinSurv, dateMaxSurv});
            }
            mensuMap.putIfAbsent(contrat,colAQ.get(i).equals("Mensuel"));
        }

        Set<String> missing_refprog = new HashSet<>();
        for (int i = 0; i < nrow; i++) {
            String currentNumPolice = colPolice.get(i);
            Date[] refDates = refprogLookup.get(currentNumPolice);
            if (refDates == null) {
                if (!missing_refprog.contains(currentNumPolice)) {
                    System.out.println("Warning: No refProg data found for num_police " + currentNumPolice);
                    missing_refprog.add(currentNumPolice);
                }
                continue;
            }

            Date dateMinSous = refDates[0];
            Date dateMaxSous = refDates[1];
            Date dateMinSurv = refDates[2];
            Date dateMaxSurv = refDates[3];

            boolean mensu = mensuMap.get(currentNumPolice);
            repairDates(indexDateSurv, indexDateSous, colDateSurv, colDateSous, colDateDecla, i, dateMinSous, dateMaxSous, dateMinSurv, dateMaxSurv, mensu);
        }
    }
    public void repairGapsDateSousSIN() {
        if (gapsMap.containsKey(numPolice)) {
            Map<Date, Integer> map = gapsMap.get(numPolice);
            List<Date> dateSousColumn = getColumn(DATE_SOUS);

            for (int i = 0; i < nrow; i++) {
                Date date = dateSousColumn.get(i);
                // If the specific date is in the map, adjust it
                if (map.containsKey(date)) {
                    int monthsToSubtract = map.get(date);

                    Calendar cal = Calendar.getInstance();
                    cal.setTime(date);
                    cal.add(Calendar.MONTH, -monthsToSubtract);  // Subtract the months
                    dateSousColumn.set(i, cal.getTime());
                }
            }
        }
    }
    public void repairGapsDateSousFIC() {
        List<Date> dateSousColumn = getColumn(DATE_SOUS);
        List<String> policeColumn = getColumn(POLICE);

        for (int i = 0; i < nrow; i++) {
            String numPolice = policeColumn.get(i);

            if (gapsMap.containsKey(numPolice)) {
                Date date = dateSousColumn.get(i);

                // If the specific date of that 'numPolice' is in the map, adjust it
                if (gapsMap.get(numPolice).containsKey(date)) {
                    int monthsToSubtract = gapsMap.get(numPolice).get(date);

                    Calendar cal = Calendar.getInstance();
                    cal.setTime(date);
                    cal.add(Calendar.MONTH, -monthsToSubtract);  // Subtract the months
                    dateSousColumn.set(i, cal.getTime());
                }
            }
        }
    }
    private void repairDates(int indexDateSurv, int indexDateSous, ArrayList<Date> colDateSurv,
                             ArrayList<Date> colDateSous, ArrayList<Date> colDateDecla, int i,
                             Date dateMinSous, Date dateMaxSous, Date dateMinSurv, Date dateMaxSurv, boolean mensu) {
        Date dateSurv = colDateSurv.get(i);
        Date dateSous = colDateSous.get(i);

        if (dateSurv == null) {
            if (!(colDateDecla.get(i) == null)) {
                dateSurv = colDateDecla.get(i);
            } else if (!(dateSous == null)) {
                dateSurv = dateSous;
            } else {
                dateSurv = dateMinSous;
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
    void date_transform (Date date, int columnIndex, int rowIndex) {
        // Change the date to the 1st day of the month
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        date = cal.getTime();

        // Update the dates in the DF
        getColumnByIndex(columnIndex).set(rowIndex,date);
    }
    public void createPivotTables() {
        List<Double> montant_IPs = getColumn(MONTANT);
        List<String> statuts = getColumn(STATUT);
        List<Date> date_sousArray = getColumn(DATE_SOUS);
        List<Date> date_survArray = getColumn(DATE_SURV);
        List<Integer> year_survArray = getColumn(YEAR_SURV);

        for (int i = 0; i < nrow; i++) {
            String statut = statuts.get(i);
            Date date_sous = date_sousArray.get(i);
            Date date_surv = date_survArray.get(i);
            Double montant_IP = montant_IPs.get(i);
            Integer year_surv = year_survArray.get(i);

            // Update the original pivot table for sum
            pivotTable
                    .computeIfAbsent(statut, k -> new HashMap<>())
                    .computeIfAbsent(date_sous, k -> new HashMap<>())
                    .merge(date_surv, montant_IP, Double::sum);

            // Update the yearly pivot table for sum
            pivotTableYearly
                    .computeIfAbsent(statut, k -> new HashMap<>())
                    .computeIfAbsent(date_sous, k -> new HashMap<>())
                    .merge(year_surv, montant_IP, Double::sum);

            // Update the total pivot table for sum
            pivotTableTotal
                    .computeIfAbsent(statut, k -> new HashMap<>())
                    .merge(date_sous, montant_IP, Double::sum);

            // Update the original pivot table for counts
            pivotTableN
                    .computeIfAbsent(statut, k -> new HashMap<>())
                    .computeIfAbsent(date_sous, k -> new HashMap<>())
                    .merge(date_surv, 1, Integer::sum); // Merging function simply increments the count

            // Update the yearly pivot table for counts
            pivotTableYearlyN
                    .computeIfAbsent(statut, k -> new HashMap<>())
                    .computeIfAbsent(date_sous, k -> new HashMap<>())
                    .merge(year_surv, 1, Integer::sum);

            // Update the total pivot table for counts
            pivotTableTotalN
                    .computeIfAbsent(statut, k -> new HashMap<>())
                    .merge(date_sous, 1, Integer::sum);
            // Update pivotTableAllStatuts
            pivotTableAllStatuts
                    .computeIfAbsent(date_sous, k -> new HashMap<>())
                    .merge(date_surv, montant_IP, Double::sum);

            // Update pivotTableAllStatutsYearly
            pivotTableAllStatutsYearly
                    .computeIfAbsent(date_sous, k -> new HashMap<>())
                    .merge(year_surv, montant_IP, Double::sum);

            // Update pivotTableAllStatutsTotal
            pivotTableAllStatutsTotal
                    .merge(date_sous, montant_IP, Double::sum);

            // Update pivotTableAllStatutsN with count frequency (assuming a frequency of 1 for each row)
            pivotTableAllStatutsN
                    .computeIfAbsent(date_sous, k -> new HashMap<>())
                    .merge(date_surv, 1, Integer::sum);

            // Update pivotTableAllStatutsYearlyN with count frequency
            pivotTableAllStatutsYearlyN
                    .computeIfAbsent(date_sous, k -> new HashMap<>())
                    .merge(year_surv, 1, Integer::sum);

            // Update pivotTableAllStatutsTotalN with count frequency
            pivotTableAllStatutsTotalN
                    .merge(date_sous, 1, Integer::sum);
        }
        roundValuesInPivots();
    }
    public void roundValuesInPivots() {
        // Round values for pivotTable
        pivotTable.forEach((statut, dateSousMap) ->
                dateSousMap.forEach((dateSous, dateSurvMap) ->
                        dateSurvMap.replaceAll((dateSurv, value) -> roundToTwoDecimals(value))));

        // Round values for pivotTableYearly
        pivotTableYearly.forEach((statut, dateSousMap) ->
                dateSousMap.forEach((dateSous, yearSurvMap) ->
                        yearSurvMap.replaceAll((yearSurv, value) -> roundToTwoDecimals(value))));

        // Round values for pivotTableTotal
        pivotTableTotal.forEach((statut, dateSousMap) ->
                dateSousMap.replaceAll((dateSous, value) -> roundToTwoDecimals(value)));

        // Rounding for pivotTableAllStatuts
        pivotTableAllStatuts.forEach((dateSous, dateSurvMap) ->
                dateSurvMap.replaceAll((dateSurv, value) -> roundToTwoDecimals(value)));

        // Rounding for pivotTableAllStatutsYearly
        pivotTableAllStatutsYearly.forEach((dateSous, yearSurvMap) ->
                yearSurvMap.replaceAll((yearSurv, value) -> roundToTwoDecimals(value)));

        // Rounding for pivotTableAllStatutsTotal
        pivotTableAllStatutsTotal.replaceAll((dateSous, value) -> roundToTwoDecimals(value));

    }
    public void createPivotTablesFic() {
        List<Double> montant_IPs = getColumn(MONTANT);
        List<String> statuts = getColumn(STATUT);
        List<String> num_polices = getColumn(POLICE);
        List<Date> date_sousArray = getColumn(DATE_SOUS);
        List<Date> date_survArray = getColumn(DATE_SURV);
        List<Integer> year_survArray = getColumn(YEAR_SURV);

        for (int i = 0; i < nrow; i++) {
            String statut = statuts.get(i);
            String num_police = num_polices.get(i);
            Date date_sous = date_sousArray.get(i);
            Date date_surv = date_survArray.get(i);
            Double montant_IP = montant_IPs.get(i);
            Integer year_surv = year_survArray.get(i);

            // Update the pivot table Fic for sum
            pivotTableFic
                    .computeIfAbsent(num_police, k -> new HashMap<>())
                    .computeIfAbsent(statut, k -> new HashMap<>())
                    .computeIfAbsent(date_sous, k -> new HashMap<>())
                    .merge(date_surv, montant_IP, Double::sum);

            // Update the yearly pivot table Fic for sum
            pivotTableYearlyFic
                    .computeIfAbsent(num_police, k -> new HashMap<>())
                    .computeIfAbsent(statut, k -> new HashMap<>())
                    .computeIfAbsent(date_sous, k -> new HashMap<>())
                    .merge(year_surv, montant_IP, Double::sum);

            // Update the total pivot table Fic for sum
            pivotTableTotalFic
                    .computeIfAbsent(num_police, k -> new HashMap<>())
                    .computeIfAbsent(statut, k -> new HashMap<>())
                    .merge(date_sous, montant_IP, Double::sum);

            // Update the pivot table Fic for counts
            pivotTableFicN
                    .computeIfAbsent(num_police, k -> new HashMap<>())
                    .computeIfAbsent(statut, k -> new HashMap<>())
                    .computeIfAbsent(date_sous, k -> new HashMap<>())
                    .merge(date_surv, 1, Integer::sum);

            // Update the yearly pivot table Fic for counts
            pivotTableFicYearlyN
                    .computeIfAbsent(num_police, k -> new HashMap<>())
                    .computeIfAbsent(statut, k -> new HashMap<>())
                    .computeIfAbsent(date_sous, k -> new HashMap<>())
                    .merge(year_surv, 1, Integer::sum);

            // Update the total pivot table Fic for counts
            pivotTableFicTotalN
                    .computeIfAbsent(num_police, k -> new HashMap<>())
                    .computeIfAbsent(statut, k -> new HashMap<>())
                    .merge(date_sous, 1, Integer::sum);
        }
        roundValuesInPivotsFic();
    }
    private void roundValuesInPivotsFic() {
        // Rounding for pivotTableFic
        pivotTableFic.forEach((num_police, statutMap) ->
                statutMap.forEach((statut, dateSousMap) ->
                        dateSousMap.forEach((dateSous, dateSurvMap) ->
                                dateSurvMap.replaceAll((dateSurv, value) -> roundToTwoDecimals(value))
                        )
                )
        );

        // Rounding for pivotTableYearlyFic
        pivotTableYearlyFic.forEach((num_police, statutMap) ->
                statutMap.forEach((statut, dateSousMap) ->
                        dateSousMap.forEach((dateSous, yearSurvMap) ->
                                yearSurvMap.replaceAll((yearSurv, value) -> roundToTwoDecimals(value))
                        )
                )
        );

        // Rounding for pivotTableTotalFic
        pivotTableTotalFic.forEach((num_police, statutMap) ->
                statutMap.forEach((statut, dateSousMap) ->
                        dateSousMap.replaceAll((dateSous, value) -> roundToTwoDecimals(value))
                )
        );

    }
    private Double roundToTwoDecimals(Double value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public void populateUniqueStatuts() {
        uniqueStatuts.addAll(getColumn(STATUT));
    }
    public void populateUniqueNumPoliceValues() {
        uniqueNumPoliceValues.addAll(getColumn(POLICE));
    }
    public void populateStatutDateRangeMap() {
        for (String statut : uniqueStatuts) {
            Date minDate = null;
            Date maxDate = null;

            Map<Date, Map<Date, Double>> middleMap = pivotTable.get(statut);
            if (middleMap != null) {
                for (Map<Date, Double> innerMap : middleMap.values()) {
                    for (Date date_surv : innerMap.keySet()) {
                        try {
                            if (minDate == null || date_surv.before(minDate)) {
                                minDate = date_surv;
                            }
                            if (maxDate == null || date_surv.after(maxDate)) {
                                maxDate = date_surv;
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
        for (String num_police : uniqueNumPoliceValues) {
            Date minDate = null;
            Date maxDate = null;

            Map<String, Map<Date, Map<Date, Double>>> outerMap = pivotTableFic.get(num_police);
            if (outerMap != null) {
                for (Map<Date, Map<Date, Double>> middleMap : outerMap.values()) {
                    for (Map<Date, Double> innerMap : middleMap.values()) {
                        for (Date date_surv : innerMap.keySet()) {
                            try {
                                if (minDate == null || date_surv.before(minDate)) {
                                    minDate = date_surv;
                                }
                                if (maxDate == null || date_surv.after(maxDate)) {
                                    maxDate = date_surv;
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

        ArrayList<String> statutCol = getColumn(STATUT);
        ArrayList<Double> montantCol = getColumn(MONTANT);
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

        ArrayList<String> statutCol = getColumn(STATUT);
        ArrayList<Double> montantCol = getColumn(MONTANT);
        for (int i = 0; i < nrow; i++) {
            if (statutCol.get(i).equals("Terminé - accepté")) {
                sum += montantCol.get(i);
                count++;
            }
        }

        return count > 0 ? sum / count : 0.0;
    }
    public Map<Date, List<Integer>> countAppearancesByYear(String statutX) {
        // Initialize the final output map
        Map<Date, List<Integer>> finalCount = new HashMap<>();

        // Extract the date_sous, year_surv, and statut columns
        ArrayList<Date> dateSousColumn = getColumn(DATE_SOUS);
        ArrayList<Integer> yearSurvColumn = getColumn(YEAR_SURV);
        ArrayList<String> statutColumn = getColumn(STATUT);

        for (int i = 0; i < nrow; i++) {
            String statut = statutColumn.get(i);

            if (statut.equals(statutX)) {
                Date dateSous = dateSousColumn.get(i);
                Integer yearSurv = yearSurvColumn.get(i);

                if (yearSurv >= MIN_ANNEE && yearSurv <= MAX_ANNEE) {
                    finalCount.computeIfAbsent(dateSous, k -> new ArrayList<>(Collections.nCopies(yearN, 0)))
                            .set(yearSurv - MIN_ANNEE, finalCount.get(dateSous).get(yearSurv - MIN_ANNEE) + 1);
                }
            }
        }
        return finalCount;
    }

    public void addStatutFictifSin() {
        int indStatut = headers.indexOf(STATUT);
        if (indStatut == -1) {
            ArrayList<String> totalValues = new ArrayList<>(Collections.nCopies(nrow, "Total"));
            addColumn(STATUT, totalValues, STR);
        }
    }
    public void addStatutFictifFic() {
        int indStatut = headers.indexOf(STATUT);
        if (indStatut != -1) {
            columns.remove(indStatut);
        }
        ArrayList<String> totalValues = new ArrayList<>(Collections.nCopies(nrow, STATUT_FICTIF_FIC));
        addColumn(STATUT, totalValues, STR);
    }
    public static String getFilenameWithoutExtension(String fullPath) {
        String filename = new java.io.File(fullPath).getName();
        if (filename.endsWith(".csv")) {
            return filename.substring(0, filename.length() - 4); // 4 because ".csv" has 4 characters
        }
        return filename;
    }

    public static void saveMapToExcel(Map<String, String> dataMap, String outputPath) throws IOException {
        // Create a new workbook and sheet
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Statuts");

        // Create a header row
        Row headerRow = sheet.createRow(0);
        Cell headerCell1 = headerRow.createCell(0);
        headerCell1.setCellValue(STATUT);
        Cell headerCell2 = headerRow.createCell(1);
        headerCell2.setCellValue("NumPolice");

        // Populate data rows
        int rowIndex = 1;
        for (Map.Entry<String, String> entry : dataMap.entrySet()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue(entry.getValue());
        }

        // Resize columns to fit content
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);

        // Write the output to file
        try (FileOutputStream fileOut = new FileOutputStream(outputPath)) {
            workbook.write(fileOut);
        }

        workbook.close();
    }
    public static void createStatutMap() throws Exception {
        for (int i = 0; i < refSource.nrow; i++) {
            boolean a_faire = !(refSource.getColumn("a faire").get(i)).equals("non");
            if (!a_faire) continue;
            String folder = (String) refSource.getColumn("path").get(i);
            String pays = (String) refSource.getColumn("pays_filekey").get(i);
            String mapcol = (String) refSource.getColumn("mapping").get(i);
            String estim = (String) refSource.getColumn("estimate").get(i);

            Estimate estimate = new Estimate(wd+"TDB estimate par gestionnaire/" + estim + ".xlsx");
            File[] fileList = Objects.requireNonNull(new File(wd + folder).listFiles());

            for (File file : fileList) {
                Basenew base = new Basenew(file,pays,mapcol,false);
                baseMapNew.put(base.numPolice, base);
                for (String statut : base.uniqueStatuts) {
                    globalStatutCollect.putIfAbsent(statut, base.numPolice);
                }

            }
            if (pays.equals("Italie")) {
                File[] fileListGS = Objects.requireNonNull(new File(wd + "source SIN/Gamestop/").listFiles());
                for (File file : fileListGS) {
                    Basenew base = new Basenew(file,"Gamestop","SPB Italie Gamestop v1",false);
                    baseMapNew.put(base.numPolice, base);
                    for (String statut : base.uniqueStatuts) {
                        globalStatutCollect.putIfAbsent(statut, base.numPolice);
                    }
                }
                Basenew baseGPTB = new Basenew(new File(wd + "aux SIN/SPB Italie_ICIGPTB15.csv"),false);
                Basenew baseMITL = new Basenew(new File(wd + "aux SIN/SPB Italie_ICIMITL16.csv"),false);
                baseMapNew.put(baseGPTB.numPolice, baseGPTB);
                baseMapNew.put(baseMITL.numPolice, baseMITL);
                for (String statut : baseGPTB.uniqueStatuts) {
                    globalStatutCollect.putIfAbsent(statut, baseGPTB.numPolice);
                }
                for (String statut : baseMITL.uniqueStatuts) {
                    globalStatutCollect.putIfAbsent(statut, baseMITL.numPolice);
                }
            }

        }
        saveMapToExcel(globalStatutCollect, wd + "statuts.xlsx");
    }
}
