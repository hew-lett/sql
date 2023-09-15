package main.app;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static main.app.App.*;
import static main.app.Basenew.STATUT_FICTIF_FIC;
import static main.app.Basenew.MAX_PREVI_DATE;
import static main.app.Basenew.MIN_PREVI_DATE;
import static main.app.Basenew.MAX_ANNEE;
import static main.app.Basenew.MIN_ANNEE;
import static main.app.Basenew.yearN;
import static main.app.DFnew.ColTypes.*;

public class Estimatenew extends DFnew {
    int baseNcol = 0;
    int lastAppendSize = 0;
    String[] totalPA;
    String[] totalPAaDate;
    String[] tauxAcquisition;
    Double[] colSPprevi;
    Double[] colPB;
    Double[] colSinUltime;

    boolean[] mask_col;
    protected Stopwatch stopwatch = new Stopwatch();
    public Set<String> uniqueStatutsEstimate;
    public Set<String> uniqueNumPoliceEstimate = new HashSet<>();
    public static Map<String, Map<String, Date>> minMaxDateSousMapEstimate = new HashMap<>();
    private static final List<String> allDateHeaders = new ArrayList<>();
    private static final List<String> allYearHeaders = new ArrayList<>();
    private static final List<Date> allDates = generateAllDatesAndHeaders();
    private static final List<Integer> allYears = generateAllYearsAndHeaders();

    public static void main(String[] args) throws Exception {
        Stopwatch st = new Stopwatch();
        st.start();

        Estimatenew estimate = new Estimatenew(wd+"TDB estimate par gestionnaire/TDB Estimate.csv",';',false);
        for (int i = 0; i < refSource.nrow; i++) {
            boolean a_faire = !(refSource.getColumn("a faire").get(i)).equals("non");
            if (!a_faire) continue;
            String folder = (String) refSource.getColumn("path").get(i);
            String pays = (String) refSource.getColumn("pays_filekey").get(i);
            String mapcol = (String) refSource.getColumn("mapping").get(i);
            String path_fic = (String) refSource.getColumn("path_fic").get(i);
            String map_fic = (String) refSource.getColumn("map_fic").get(i);

            System.out.println(pays);
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

            Basenew baseFic = new Basenew(wd + path_fic,map_fic,false);
            baseMapNew.put(path_fic, baseFic);
        }
        st.printElapsedTime();
        estimate.appendAllPivotsFic();
        st.printElapsedTime();

        estimate.appendAllPivotsSin();
        st.printElapsedTime();

        estimate.addProvisions();
        estimate.saveToCsvWithSuffix("_FDT");
        st.printElapsedTime();
    }
    public Estimatenew(String csvFilePath, char delim, boolean toLower) throws IOException, ParseException {
        path = csvFilePath;
        FileConfig config = FileConfig.getInstance();
        String refFichier = "estimate";
        columnNamesToRead = config.getColumnNamesToRead(refFichier);
        columnTypes = config.getColumnTypes(refFichier);
        columnNamesAttributed = config.getColumnNamesAttributed(refFichier);
        validateColumnInputs(columnNamesToRead, columnTypes, columnNamesAttributed);

        columns = new ArrayList<>();
        headers = new ArrayList<>();

        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true, delim);
        CsvParser parser = new CsvParser(settings);

        List<String[]> allRows = parser.parseAll(new FileReader(csvFilePath, encodingDefault));
        nrow = allRows.size() - 1;

        if (allRows.isEmpty()) {
            throw new IllegalArgumentException("CSV file is empty!");
        }

        String[] headerRow = allRows.get(0);
        for (int i = 0; i < headerRow.length; i++) {
            if (headerRow[i] == null) {
                headerRow[i] = "";
            }
        }

        if (toLower) {
            for (int i = 0; i < headerRow.length; i++) {
                headerRow[i] = headerRow[i].toLowerCase();
            }
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
                throw new RuntimeException("column " + expectedHeader + " not found for Estimate");
            }
        }

        subheaders = new ArrayList<>(Collections.nCopies(headers.size(), null));
        trimNullDatePeriodeRows();
        transformDatePeriodeColumn();
        generateMinMaxDateSousMap();
        mergeRegul();
        mergeDBP();

//        baseNcol = ncol;
//        mask_col = new boolean[ncol];
//        Arrays.fill(mask_col, true);
    }
    public void trimNullDatePeriodeRows() {
        ArrayList<Date> datePeriodeColumn = getColumn("Date Periode");
        ArrayList<Integer> rowsToDelete = new ArrayList<>();

        // Start from the end and move to the beginning
        for (int i = datePeriodeColumn.size() - 1; i >= 0; i--) {
            if (datePeriodeColumn.get(i) == null) {
                rowsToDelete.add(i);
            } else {
                // Stop once a non-null value is found
                break;
            }
        }

        // Now, delete those rows
        deleteRows(rowsToDelete);
    }
    private void transformDatePeriodeColumn() {
        SimpleDateFormat dateFormatter = new SimpleDateFormat("dd-MM-yyyy");

        ArrayList<String> datePeriodeStrings = getColumn("Date Periode");
        ArrayList<Date> datePeriodeDates = new ArrayList<>();

        for (String dateString : datePeriodeStrings) {
            try {
                Date parsedDate = dateFormatter.parse(dateString);
                datePeriodeDates.add(parsedDate);
            } catch (ParseException e) {
                System.err.println("Failed to parse date: " + dateString);
                datePeriodeDates.add(null);
            }
        }

        int datePeriodeIndex = headers.indexOf("Date Periode");
        columns.set(datePeriodeIndex, new Column<>(datePeriodeDates, ColTypes.DAT));
    }
    public void generateMinMaxDateSousMap() {
        ArrayList<Date> datePeriodes = getColumn("Date Periode");
        ArrayList<String> contrats = getColumn("Contrat");

        for (int i = 0; i < nrow; i++) {
            Date date = datePeriodes.get(i);
            String contrat = contrats.get(i);

            minMaxDateSousMapEstimate.putIfAbsent(contrat, new HashMap<>());
            Map<String, Date> currentDateMap = minMaxDateSousMapEstimate.get(contrat);

            // Update min and max dates
            Date currentMinDate = currentDateMap.getOrDefault("min", date);
            Date currentMaxDate = currentDateMap.getOrDefault("max", date);

            if (!currentDateMap.containsKey("min")) {
                currentDateMap.put("min", date);
            }
            if (!currentDateMap.containsKey("max")) {
                currentDateMap.put("max", date);
            }
            if (date.before(currentMinDate)) {
                currentDateMap.put("min", date);
            }
            if (date.after(currentMaxDate)) {
                currentDateMap.put("max", date);
            }
        }
    }
    public void mergeRegul() {
        ArrayList<String> regul = getColumn("Régularisation");
        ArrayList<String> contrat = getColumn("Contrat");
        ArrayList<Date> date = getColumn("Date Periode");
        ArrayList<Integer> rowsToDelete = new ArrayList<>();
        for (int i = 0; i < this.nrow; i++) {
            if (regul.get(i).equals("OUI")) {
                int origin = -1;
                if (date.get(i).equals(date.get(i-1)) && contrat.get(i).equals(contrat.get(i-1))) {
                    origin = i - 1;
                } else {
                    for (int j = 0; j < this.nrow; j++) {
                        if (date.get(i).equals(date.get(j)) && contrat.get(i).equals(contrat.get(j))) {
                            origin = j;
                            break;
                        }
                    }
                }
                if(origin == -1) {
                    System.out.println("REGUL ERROR");
                }
                mergeRows(rowsToDelete, i, origin);
            }
        }
        deleteRows(rowsToDelete);
    }
    public void mergeDBP() {
        ArrayList<String> contrat = getColumn("Contrat");
        ArrayList<Date> date = getColumn("Date Periode");
        ArrayList<Integer> rowsToDelete = new ArrayList<>();
        for (int i = 0; i < nrow; i++) {
            if (contrat.get(i).equals("ICIDBP17-1") || contrat.get(i).equals("ICIDBP17-2")) {
                int origin = -1;
                for (int j = 0; j < this.nrow; j++) {
                    if (date.get(i).equals(date.get(j)) && "ICIDBP17".equals(contrat.get(j))) {
                        origin = j;
                        break;
                    }
                }
                if (origin == -1) {
                    contrat.set(i, "ICIDBP17");
                    continue;
                }
                mergeRows(rowsToDelete, i, origin);
            }
        }
        deleteRows(rowsToDelete);
    }

    // This is a static method to generate all dates between MIN_PREVI_DATE and MAX_PREVI_DATE and their corresponding headers
    private static List<Date> generateAllDatesAndHeaders() {
        SimpleDateFormat dateFormatter = new SimpleDateFormat("MM-yyyy");
        Calendar calendar = Calendar.getInstance();
        List<Date> dates = new ArrayList<>();

        calendar.setTime(MIN_PREVI_DATE);
        while (!calendar.getTime().after(MAX_PREVI_DATE)) {
            Date currentDate = calendar.getTime();
            dates.add(currentDate);
            allDateHeaders.add(dateFormatter.format(currentDate));
            calendar.add(Calendar.MONTH, 1); // move to next month
        }
        return dates;
    }
    // Generate a range of years and their corresponding headers
    private static List<Integer> generateAllYearsAndHeaders() {
        List<Integer> years = new ArrayList<>();

        for (int year = MIN_ANNEE; year <= MAX_ANNEE; year++) {
            years.add(year);
            allYearHeaders.add(String.valueOf(year));  // For now, the headers are just the year as a string.
        }

        return years;
    }
    public void appendPivotTable(Map<Date, Map<Date, Double>> pivotTable, String label, String contrat) {
        ArrayList<Date> datePeriodeColumn = getColumn("Date Periode");
        ArrayList<String> contratColumn = getColumn("Contrat");

        int columnIndex = subheaders.indexOf(label); // Find the index of the label in the subheaders

        // If the label is not found in the subheaders, append the new columns
        if (columnIndex == -1) {
            headers.addAll(allDateHeaders);

            for (int i = 0; i < allDateHeaders.size(); i++) {
                ArrayList<Double> columnData = new ArrayList<>(Collections.nCopies(nrow, null));
                columns.add(new Column<>(columnData, ColTypes.DBL));
            }

            subheaders.add(label);
            subheaders.addAll(Collections.nCopies(allDateHeaders.size() - 1, null));

            columnIndex = headers.size() - allDates.size(); // Start index for the new columns
        }

        populatePivotMensuel(pivotTable, contrat, datePeriodeColumn, contratColumn, columnIndex);
    }
    public void appendPivotTableYearly(Map<Date, Map<Integer, Double>> pivotTable, String label, String contrat) {
        ArrayList<Date> datePeriodeColumn = getColumn("Date Periode");
        ArrayList<String> contratColumn = getColumn("Contrat");

        int columnIndex = subheaders.indexOf(label);

        // If label is not found, append new columns
        if (columnIndex == -1) {
            headers.addAll(allYearHeaders);

            for (int i = 0; i < allYearHeaders.size(); i++) {
                ArrayList<Double> columnData = new ArrayList<>(Collections.nCopies(nrow, null));
                columns.add(new Column<>(columnData, ColTypes.DBL));
            }

            subheaders.add(label);
            subheaders.addAll(Collections.nCopies(allYearHeaders.size() - 1, null));

            columnIndex = headers.size() - allYears.size(); // Update columnIndex for new columns
        }

        populatePivotAnnuel(pivotTable, contrat, datePeriodeColumn, contratColumn, columnIndex);
    }
    public void appendPivotTableTotal(Map<Date, Double> pivotTable, String label, String contrat) {
        ArrayList<Date> datePeriodeColumn = getColumn("Date Periode");
        ArrayList<String> contratColumn = getColumn("Contrat");

        int columnIndex = subheaders.indexOf(label);

        // If "Total" is not in headers, append new column
        if (columnIndex == -1) {
            headers.add("Total");

            ArrayList<Double> columnData = new ArrayList<>(Collections.nCopies(nrow, null));
            columns.add(new Column<>(columnData, ColTypes.DBL));

            subheaders.add(label);

            columnIndex = headers.size() - 1; // Update columnIndex for new column
        }

        populatePivotTotal(pivotTable, contrat, datePeriodeColumn, contratColumn, columnIndex);
    }

    public void appendPivotTableN(Map<Date, Map<Date, Integer>> pivotTable, String label, String contrat) {
        ArrayList<Date> datePeriodeColumn = getColumn("Date Periode");
        ArrayList<String> contratColumn = getColumn("Contrat");

        int columnIndex = subheaders.indexOf(label);

        if (columnIndex == -1) {
            headers.addAll(allDateHeaders);

            for (int i = 0; i < allDateHeaders.size(); i++) {
                ArrayList<Integer> columnData = new ArrayList<>(Collections.nCopies(nrow, null));
                columns.add(new Column<>(columnData, ColTypes.INT));
            }

            subheaders.add(label);
            subheaders.addAll(Collections.nCopies(allDateHeaders.size() - 1, null));

            columnIndex = headers.size() - allDates.size();
        }

        populatePivotMensuelN(pivotTable, contrat, datePeriodeColumn, contratColumn, columnIndex);
    }
    public void appendPivotTableYearlyN(Map<Date, Map<Integer, Integer>> pivotTable, String label, String contrat) {
        ArrayList<Date> datePeriodeColumn = getColumn("Date Periode");
        ArrayList<String> contratColumn = getColumn("Contrat");

        int columnIndex = subheaders.indexOf(label);

        if (columnIndex == -1) {
            headers.addAll(allYearHeaders);

            for (int i = 0; i < allYearHeaders.size(); i++) {
                ArrayList<Integer> columnData = new ArrayList<>(Collections.nCopies(nrow, null));
                columns.add(new Column<>(columnData, ColTypes.INT));
            }

            subheaders.add(label);
            subheaders.addAll(Collections.nCopies(allYearHeaders.size() - 1, null));

            columnIndex = headers.size() - allYears.size();
        }

        populatePivotAnnuelN(pivotTable, contrat, datePeriodeColumn, contratColumn, columnIndex);
    }
    public void appendPivotTableTotalN(Map<Date, Integer> pivotTable, String label, String contrat) {
        ArrayList<Date> datePeriodeColumn = getColumn("Date Periode");
        ArrayList<String> contratColumn = getColumn("Contrat");

        int columnIndex = subheaders.indexOf(label);

        if (columnIndex == -1) {
            headers.add("Total");

            ArrayList<Integer> columnData = new ArrayList<>(Collections.nCopies(nrow, null));
            columns.add(new Column<>(columnData, ColTypes.INT));

            subheaders.add(label);

            columnIndex = headers.size() - 1;
        }

        populatePivotTotalN(pivotTable, contrat, datePeriodeColumn, contratColumn, columnIndex);
    }

    public void appendPivotTableFic(Basenew baseFic, String label) {
        ArrayList<Date> datePeriodeColumn = getColumn("Date Periode");
        ArrayList<String> contratColumn = getColumn("Contrat");

        int columnIndex = subheaders.indexOf(label); // Find the index of the label in the subheaders

        if (columnIndex == -1) {
            headers.addAll(allDateHeaders);

            for (int i = 0; i < allDateHeaders.size(); i++) {
                ArrayList<Double> columnData = new ArrayList<>(Collections.nCopies(nrow, null));
                columns.add(new Column<>(columnData, ColTypes.DBL));
            }

            subheaders.add(label);
            subheaders.addAll(Collections.nCopies(allDateHeaders.size() - 1, null));

            columnIndex = headers.size() - allDates.size();
        }

        for (Map.Entry<String, Map<String, Map<Date, Map<Date, Double>>>> entry : baseFic.pivotTableFic.entrySet()) {
            String currentContrat = entry.getKey();
            Map<String, Map<Date, Map<Date, Double>>> statutMap = entry.getValue();

            Map<Date, Map<Date, Double>> innerPivot = statutMap.get(STATUT_FICTIF_FIC);  // Extracting the actual pivot for the current contrat

            // Go through all rows
            populatePivotMensuel(innerPivot, currentContrat, datePeriodeColumn, contratColumn, columnIndex);
        }
    }
    public void appendPivotTableYearlyFic(Basenew baseFic, String label) {
        ArrayList<Date> datePeriodeColumn = getColumn("Date Periode");
        ArrayList<String> contratColumn = getColumn("Contrat");

        int columnIndex = subheaders.indexOf(label); // Find the index of the label in the subheaders

        if (columnIndex == -1) {
            headers.addAll(allYearHeaders);

            for (int i = 0; i < allYearHeaders.size(); i++) {
                ArrayList<Double> columnData = new ArrayList<>(Collections.nCopies(nrow, null));
                columns.add(new Column<>(columnData, ColTypes.DBL));
            }

            subheaders.add(label);
            subheaders.addAll(Collections.nCopies(allYearHeaders.size() - 1, null));

            columnIndex = headers.size() - allYears.size();
        }

        for (Map.Entry<String, Map<String, Map<Date, Map<Integer, Double>>>> entry : baseFic.pivotTableYearlyFic.entrySet()) {
            String currentContrat = entry.getKey();
            Map<String, Map<Date, Map<Integer, Double>>> statutMap = entry.getValue();

            Map<Date, Map<Integer, Double>> innerPivot = statutMap.get(STATUT_FICTIF_FIC);  // Extracting the actual pivot for the current contrat

            populatePivotAnnuel(innerPivot, currentContrat, datePeriodeColumn, contratColumn, columnIndex);
        }
    }
    public void appendPivotTableTotalFic(Basenew baseFic, String label) {
        ArrayList<Date> datePeriodeColumn = getColumn("Date Periode");
        ArrayList<String> contratColumn = getColumn("Contrat");

        int columnIndex = subheaders.indexOf(label); // Find the index of the label in the subheaders

        if (columnIndex == -1) {
            headers.add("Total");
            ArrayList<Double> columnData = new ArrayList<>(Collections.nCopies(nrow, null));
            columns.add(new Column<>(columnData, ColTypes.DBL));

            subheaders.add(label);

            columnIndex = headers.size() - 1;
        }

        for (Map.Entry<String, Map<String, Map<Date, Double>>> entry : baseFic.pivotTableTotalFic.entrySet()) {
            String currentContrat = entry.getKey();
            Map<String, Map<Date, Double>> statutMap = entry.getValue();

            Map<Date, Double> innerPivot = statutMap.get(STATUT_FICTIF_FIC);  // Extracting the actual pivot for the current contrat

            populatePivotTotal(innerPivot, currentContrat, datePeriodeColumn, contratColumn, columnIndex);
        }
    }

    public void appendPivotTableFicN(Basenew baseFic, String label) {
        ArrayList<Date> datePeriodeColumn = getColumn("Date Periode");
        ArrayList<String> contratColumn = getColumn("Contrat");

        int columnIndex = subheaders.indexOf(label); // Find the index of the label in the subheaders

        if (columnIndex == -1) {
            headers.addAll(allDateHeaders);

            for (int i = 0; i < allDateHeaders.size(); i++) {
                ArrayList<Double> columnData = new ArrayList<>(Collections.nCopies(nrow, null));
                columns.add(new Column<>(columnData, ColTypes.INT));
            }

            subheaders.add(label);
            subheaders.addAll(Collections.nCopies(allDateHeaders.size() - 1, null));

            columnIndex = headers.size() - allDates.size();
        }

        for (Map.Entry<String, Map<String, Map<Date, Map<Date, Integer>>>> entry : baseFic.pivotTableFicN.entrySet()) {
            String currentContrat = entry.getKey();
            Map<String, Map<Date, Map<Date, Integer>>> statutMap = entry.getValue();

            Map<Date, Map<Date, Integer>> innerPivot = statutMap.get(STATUT_FICTIF_FIC);  // Extracting the actual pivot for the current contrat

            // Go through all rows
            populatePivotMensuelN(innerPivot, currentContrat, datePeriodeColumn, contratColumn, columnIndex);
        }
    }
    public void appendPivotTableYearlyFicN(Basenew baseFic, String label) {
        ArrayList<Date> datePeriodeColumn = getColumn("Date Periode");
        ArrayList<String> contratColumn = getColumn("Contrat");

        int columnIndex = subheaders.indexOf(label); // Find the index of the label in the subheaders

        if (columnIndex == -1) {
            headers.addAll(allYearHeaders);

            for (int i = 0; i < allYearHeaders.size(); i++) {
                ArrayList<Double> columnData = new ArrayList<>(Collections.nCopies(nrow, null));
                columns.add(new Column<>(columnData, ColTypes.INT));
            }

            subheaders.add(label);
            subheaders.addAll(Collections.nCopies(allYearHeaders.size() - 1, null));

            columnIndex = headers.size() - allYears.size();
        }

        for (Map.Entry<String, Map<String, Map<Date, Map<Integer, Integer>>>> entry : baseFic.pivotTableFicYearlyN.entrySet()) {
            String currentContrat = entry.getKey();
            Map<String, Map<Date, Map<Integer, Integer>>> statutMap = entry.getValue();

            Map<Date, Map<Integer, Integer>> innerPivot = statutMap.get(STATUT_FICTIF_FIC);  // Extracting the actual pivot for the current contrat

            populatePivotAnnuelN(innerPivot, currentContrat, datePeriodeColumn, contratColumn, columnIndex);
        }
    }
    public void appendPivotTableTotalFicN(Basenew baseFic, String label) {
        ArrayList<Date> datePeriodeColumn = getColumn("Date Periode");
        ArrayList<String> contratColumn = getColumn("Contrat");

        int columnIndex = subheaders.indexOf(label); // Find the index of the label in the subheaders

        if (columnIndex == -1) {
            headers.add("Total");

            ArrayList<Double> columnData = new ArrayList<>(Collections.nCopies(nrow, null));
            columns.add(new Column<>(columnData, ColTypes.INT));

            subheaders.add(label);

            columnIndex = headers.size() - 1;
        }

        for (Map.Entry<String, Map<String, Map<Date, Integer>>> entry : baseFic.pivotTableFicTotalN.entrySet()) {
            String currentContrat = entry.getKey();
            Map<String, Map<Date, Integer>> statutMap = entry.getValue();

            Map<Date, Integer> innerPivot = statutMap.get(STATUT_FICTIF_FIC);  // Extracting the actual pivot for the current contrat

            populatePivotTotalN(innerPivot, currentContrat, datePeriodeColumn, contratColumn, columnIndex);
        }
    }

    private void populatePivotMensuel(Map<Date, Map<Date, Double>> pivotTable, String contrat, ArrayList<Date> datePeriodeColumn, ArrayList<String> contratColumn, int columnIndex) {
        for (int i = 0; i < nrow; i++) {
            String currentContrat = contratColumn.get(i);

            if (currentContrat.equals(contrat)) {
                Date datePeriode = datePeriodeColumn.get(i);
                Map<Date, Double> innerMap = pivotTable.get(datePeriode);

                int currentColumnIndex = columnIndex;
                for (Date date : allDates) {
                    Double value = (innerMap != null) ? innerMap.get(date) : null;
                    if (value != null) {
                        getColumnByIndex(currentColumnIndex).set(i, value);
                    }
                    currentColumnIndex++;
                }
            }
        }
    }
    private void populatePivotAnnuel(Map<Date, Map<Integer, Double>> pivotTable, String contrat, ArrayList<Date> datePeriodeColumn, ArrayList<String> contratColumn, int columnIndex) {
        for (int i = 0; i < nrow; i++) {
            String currentContrat = contratColumn.get(i);

            if (currentContrat.equals(contrat)) {
                Date datePeriode = datePeriodeColumn.get(i);
                Map<Integer, Double> innerMap = pivotTable.get(datePeriode);

                int currentColumnIndex = columnIndex;
                for (Integer year : allYears) {
                    Double value = (innerMap != null) ? innerMap.get(year) : null;
                    if (value != null) {
                        getColumnByIndex(currentColumnIndex).set(i, value);
                    }
                    currentColumnIndex++;
                }
            }
        }
    }
    private void populatePivotTotal(Map<Date, Double> pivotTable, String contrat, ArrayList<Date> datePeriodeColumn, ArrayList<String> contratColumn, int columnIndex) {
        for (int i = 0; i < nrow; i++) {
            String currentContrat = contratColumn.get(i);

            if (currentContrat.equals(contrat)) {
                Date datePeriode = datePeriodeColumn.get(i);
                Double value = pivotTable.get(datePeriode);
                if (value != null) {
                    getColumnByIndex(columnIndex).set(i, value);
                }
            }
        }
    }
    private void populatePivotMensuelN(Map<Date, Map<Date, Integer>> pivotTable, String contrat, ArrayList<Date> datePeriodeColumn, ArrayList<String> contratColumn, int columnIndex) {
        for (int i = 0; i < nrow; i++) {
            String currentContrat = contratColumn.get(i);

            if (currentContrat.equals(contrat)) {
                Date datePeriode = datePeriodeColumn.get(i);
                Map<Date, Integer> innerMap = pivotTable.get(datePeriode);

                int currentColumnIndex = columnIndex;
                for (Date date : allDates) {
                    Integer value = (innerMap != null) ? innerMap.get(date) : null;
                    if (value != null) {
                        getColumnByIndex(currentColumnIndex).set(i, value);
                    }
                    currentColumnIndex++;
                }
            }
        }
    }
    private void populatePivotAnnuelN(Map<Date, Map<Integer, Integer>> pivotTable, String contrat, ArrayList<Date> datePeriodeColumn, ArrayList<String> contratColumn, int columnIndex) {
        for (int i = 0; i < nrow; i++) {
            String currentContrat = contratColumn.get(i);

            if (currentContrat.equals(contrat)) {
                Date datePeriode = datePeriodeColumn.get(i);
                Map<Integer, Integer> innerMap = pivotTable.get(datePeriode);

                int currentColumnIndex = columnIndex;
                for (Integer year : allYears) {
                    Integer value = (innerMap != null) ? innerMap.get(year) : null;
                    if (value != null) {
                        getColumnByIndex(currentColumnIndex).set(i, value);
                    }
                    currentColumnIndex++;
                }
            }
        }
    }
    private void populatePivotTotalN(Map<Date, Integer> pivotTable, String contrat, ArrayList<Date> datePeriodeColumn, ArrayList<String> contratColumn, int columnIndex) {
        for (int i = 0; i < nrow; i++) {
            String currentContrat = contratColumn.get(i);

            if (currentContrat.equals(contrat)) {
                Date datePeriode = datePeriodeColumn.get(i);
                Integer value = pivotTable.get(datePeriode);
                if (value != null) {
                    getColumnByIndex(columnIndex).set(i, value);
                }
            }
        }
    }

    public void appendAllPivotsSin() {
        for (Basenew baseSin : baseMapNew.values()) {
            // Append the double-typed pivot tables
            appendPivotTable(baseSin.pivotTableAllStatuts, "Charge sinistre mensuelle", baseSin.numPolice);
            appendPivotTableYearly(baseSin.pivotTableAllStatutsYearly, "Charge sinistre annuelle", baseSin.numPolice);
            appendPivotTableTotal(baseSin.pivotTableAllStatutsTotal, "Charge sinistre totale", baseSin.numPolice);

            // Append the integer-typed pivot tables
            appendPivotTableN(baseSin.pivotTableAllStatutsN, "Nombre sinistre mensuel", baseSin.numPolice);
            appendPivotTableYearlyN(baseSin.pivotTableAllStatutsYearlyN, "Nombre sinistre annuel", baseSin.numPolice);
            appendPivotTableTotalN(baseSin.pivotTableAllStatutsTotalN, "Nombre sinistre total", baseSin.numPolice);

            for (String statut : baseSin.uniqueStatuts) {
                if (statut.isEmpty()) continue;  // Skip if statut is an empty string

                // Replace "sinistre" with the value of statut for the labels
                String monthlyLabel = "Charge " + statut + " mensuel";
                String yearlyLabel = "Charge " + statut + " annuel";
                String totalLabel = "Charge " + statut + " total";
                String monthlyLabelN = "Nombre " + statut + " mensuel";
                String yearlyLabelN = "Nombre " + statut + " annuel";
                String totalLabelN = "Nombre " + statut + " total";

                // Append the double-typed pivot tables
                appendPivotTable(baseSin.pivotTable.get(statut), monthlyLabel, baseSin.numPolice);
                appendPivotTableYearly(baseSin.pivotTableYearly.get(statut), yearlyLabel, baseSin.numPolice);
                appendPivotTableTotal(baseSin.pivotTableTotal.get(statut), totalLabel, baseSin.numPolice);

                // Append the integer-typed pivot tables
                appendPivotTableN(baseSin.pivotTableN.get(statut), monthlyLabelN, baseSin.numPolice);
                appendPivotTableYearlyN(baseSin.pivotTableYearlyN.get(statut), yearlyLabelN, baseSin.numPolice);
                appendPivotTableTotalN(baseSin.pivotTableTotalN.get(statut), totalLabelN, baseSin.numPolice);
            }
        }
    }
    public void appendAllPivotsFic() {
        for (Basenew baseFic : ficMapNew.values()) {
            String statut = STATUT_FICTIF_FIC;
            // Replace "sinistre" with the value of statut for the labels
            String monthlyLabel = "Charge " + statut + " mensuelle";
            String yearlyLabel = "Charge " + statut + " annuelle";
            String totalLabel = "Charge " + statut + " totale";
            String monthlyLabelN = "Nombre " + statut + " mensuel";
            String yearlyLabelN = "Nombre " + statut + " annuel";
            String totalLabelN = "Nombre " + statut + " total";

            // Append the double-typed pivot tables for Fic
            appendPivotTableFic(baseFic, monthlyLabel);
            appendPivotTableYearlyFic(baseFic, yearlyLabel);
            appendPivotTableTotalFic(baseFic, totalLabel);

            // Append the integer-typed pivot tables for Fic
            appendPivotTableFicN(baseFic, monthlyLabelN);
            appendPivotTableYearlyFicN(baseFic, yearlyLabelN);
            appendPivotTableTotalFicN(baseFic, totalLabelN);
        }
    }
    private void populateColumnsFromMaps(String label, Map<String, Double> coutMoyenMap,Map<String, Map<Date, List<Integer>>> nMap,
                                         ArrayList<String> contratColumn,ArrayList<Date> datePeriodeColumn,ArrayList<Double> totalProvisionColumn) {
        // 1. Populate "Cout moyen" column
        ArrayList<Double> coutMoyenColumn = new ArrayList<>();
        for (int i = 0; i < nrow; i++) {
            String contratValue = contratColumn.get(i);
            Double coutMoyenValue = coutMoyenMap.getOrDefault(contratValue, null);
            coutMoyenColumn.add(coutMoyenValue);
        }
        addColumn("Cout moyen " + label, coutMoyenColumn, ColTypes.DBL);

        // 2. Populate the annual distribution columns
        List<ArrayList<Double>> distributionColumns = new ArrayList<>();
        for (int j = 0; j < yearN; j++) {
            distributionColumns.add(new ArrayList<>());
        }
        for (int i = 0; i < nrow; i++) {
            String contrat = contratColumn.get(i);
            Date datePeriode = datePeriodeColumn.get(i);

            // Check if contrat exists in the nMap
            if(nMap.containsKey(contrat)) {
                List<Integer> nForDate = nMap.get(contrat).get(datePeriode);

                // Additionally, check if the datePeriode exists for that contrat
                if (nForDate != null) {
                    Double coutMoyen = coutMoyenColumn.get(i);
                    for (int j = 0; j < yearN; j++) {
                        distributionColumns.get(j).add(nForDate.get(j) * coutMoyen);
                    }
                } else {
                    // Handle cases where datePeriode doesn't exist for a contrat
                    for (int j = 0; j < yearN; j++) {
                        distributionColumns.get(j).add(0.0); // or any default value you want
                    }
                }
            } else {
                // Handle cases where contrat doesn't exist in the map
                for (int j = 0; j < yearN; j++) {
                    distributionColumns.get(j).add(0.0); // or any default value you want
                }
            }
        }

        for (int j = 0; j < yearN; j++) {
            int year = MIN_ANNEE + j;
            if (j == 0) {
                addColumnWithSubheader(String.valueOf(year), "Provision " + label, distributionColumns.get(j), ColTypes.DBL);
            } else {
                addColumn(String.valueOf(year), distributionColumns.get(j), ColTypes.DBL);
            }
        }

        // 3. Add the "Total" column
        ArrayList<Double> totalColumn = new ArrayList<>();
        for (int i = 0; i < nrow; i++) {
            double total = 0;
            for (ArrayList<Double> yearly : distributionColumns) {
                total += yearly.get(i);
            }
            totalColumn.add(total);

            if (i < totalProvisionColumn.size()) {
                totalProvisionColumn.set(i, totalProvisionColumn.get(i) + total);
            } else {
                totalProvisionColumn.add(total);
            }
        }
        addColumn("Total", totalColumn, ColTypes.DBL);
    }

    public void addProvisions() {
        Map<String, Double> coutMoyenEnCoursMap = new HashMap<>();
        Map<String, Double> coutMoyenEnCoursAccepteMap = new HashMap<>();
        Map<String, Map<Date, List<Integer>>> nEnCoursMap = new HashMap<>();
        Map<String, Map<Date, List<Integer>>> nEnCoursAccepteMap = new HashMap<>();
        for (Basenew base : baseMapNew.values()) {
            nEnCoursMap.put(base.numPolice, base.nEnCours);
            nEnCoursAccepteMap.put(base.numPolice, base.nEnCoursAccepte);
            coutMoyenEnCoursMap.put(base.numPolice, base.coutMoyenEnCours);
            coutMoyenEnCoursAccepteMap.put(base.numPolice, base.coutMoyenEnCoursAccepte);
        }

        ArrayList<String> contratColumn = getColumn("Contrat");
        ArrayList<Date> datePeriodeColumn = getColumn("Date Periode");

        ArrayList<Double> totalProvisionColumn = new ArrayList<>();
        populateColumnsFromMaps("En Cours", coutMoyenEnCoursMap, nEnCoursMap, contratColumn, datePeriodeColumn,totalProvisionColumn);
        populateColumnsFromMaps("En Cours Accepté", coutMoyenEnCoursAccepteMap, nEnCoursAccepteMap, contratColumn, datePeriodeColumn,totalProvisionColumn);
        addColumn("Total Provision", totalProvisionColumn, ColTypes.DBL);

    }

}
