package main.app;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.apache.poi.ss.formula.functions.Today;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static main.app.App.*;
import static main.app.Base.STATUT_FICTIF_FIC;
import static main.app.Base.MAX_PREVI_DATE;
import static main.app.Base.MIN_PREVI_DATE;
import static main.app.Base.MAX_ANNEE;
import static main.app.Base.MIN_ANNEE;
import static main.app.Base.yearN;
import static main.app.DF.ColTypes.*;

public class Estimate extends DF {
    public static Map<String, Map<String, Date>> minMaxDateSousMapEstimate = new HashMap<>();
    private static final List<String> allDateHeaders = new ArrayList<>();
    private static final List<String> allYearHeaders = new ArrayList<>();
    private static final List<Date> allDates = generateAllDatesAndHeaders();
    private static final List<Integer> allYears = generateAllYearsAndHeaders();
    public static final Map<String, Map<Date, Integer>> gapsMap = new HashMap<>();
    private final ArrayList<Double> plusComm = new ArrayList<>();
    private final ArrayList<Boolean> avecICImask = new ArrayList<>();
    private final ArrayList<Boolean> sansICImask = new ArrayList<>();
    public static void main(String[] args) throws Exception {
    }
    public Estimate(String csvFilePath, char delim, String refFichier) throws IOException, ParseException {
        path = csvFilePath;
        FileConfig config = FileConfig.getInstance();
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
                    colData.add(getCell(cell, colType));
                }
                columns.add(new Column<>(colData, colType));
            } else {
                throw new RuntimeException("column " + expectedHeader + " not found for Estimate");
            }
        }

        subheaders = new ArrayList<>(Collections.nCopies(headers.size(), null));
        trimNullDatePeriodeRows_cleanHeader();
        transformDatePeriodeColumn(refFichier);
        generateMinMaxDateSousMap();
//        printMatchingRows("ICIMKAH22","01/01/2023");
        mergeRegul();
        mergeDBP();
        mergeDuplicates();
        sortTableByContractAndDate();
        findDateGapsFromLastAvailable();
        addComm();
//        saveUniqueCombinationsToXlsx(outputFolder+"casPrime.xlsx");
    }
    void beginSplit() {
        for (String s : headers) {
            avecICImask.add(true);
            sansICImask.add(true);
        }
    }
    public void saveUniqueCombinationsToXlsx(String outputPath) throws IOException {
        ArrayList<String> fluxColumn = getColumn("Flux");
        ArrayList<Double> montantTotalNetCompagnieColumn = getColumn("MONTANT TOTAL NET COMPAGNIE");
        ArrayList<Double> montantTotalPrimeAssureurColumn = getColumn("MONTANT TOTAL PRIME ASSUREUR");

        // Holds a unique set of row combinations
        Set<String> uniqueCombinations = new HashSet<>();

        // Collecting rows with unique combinations
        ArrayList<ArrayList<Object>> collectedRows = new ArrayList<>();

        for (int i = 0; i < nrow; i++) {
            String fluxValue = fluxColumn.get(i);
            String montantTotalNetCompagnieValue = classifyValue(montantTotalNetCompagnieColumn.get(i));
            String montantTotalPrimeAssureurValue = classifyValue(montantTotalPrimeAssureurColumn.get(i));

            String combinationKey = fluxValue + "_" + montantTotalNetCompagnieValue + "_" + montantTotalPrimeAssureurValue;

            if (!uniqueCombinations.contains(combinationKey)) {
                uniqueCombinations.add(combinationKey);
                collectedRows.add(getRow(i));
            }
        }

        // Save to XLSX
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Unique Combinations");

            // Header Row
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
            }

            // Data Rows
            for (int i = 0; i < collectedRows.size(); i++) {
                ArrayList<Object> row = collectedRows.get(i);
                Row xlsxRow = sheet.createRow(i + 1);
                for (int j = 0; j < row.size(); j++) {
                    Cell cell = xlsxRow.createCell(j);
                    Object value = row.get(j);
                    if (value instanceof Double) {
                        cell.setCellValue((Double) value);
                    } else if (value instanceof Date) {
                        cell.setCellValue(dateDefault.format((Date) value));
                    } else {
                        if (value == null) {
                            cell.setCellValue("");
                        } else {
                            cell.setCellValue(value.toString());
                        }
                    }
                }
            }

            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                workbook.write(fos);
            }
        }
    }
    private void addComm() {
        ArrayList<Double> mtpa = getColumn("MONTANT TOTAL PRIME ASSUREUR");
        ArrayList<Double> comm = getColumn("MONTANT TOTAL COMMISSION ICI");
        for (int i = 0; i < nrow; i++) {
            plusComm.add(mtpa.get(i) + comm.get(i));
        }
    }
    public static boolean isComm(String key, Date inputDate) {
        Date mapDate = policesComm.get(key);
        return mapDate != null && inputDate.after(mapDate);
    }

    private String classifyValue(Double value) {
        if (value == null) {
            return "null";
        } else if (value > 0) {
            return "positive";
        } else if (value < 0) {
            return "negative";
        } else {
            return "zero";
        }
    }
    private void transformDatePeriodeColumn(String refFichier) {
        SimpleDateFormat dateFormatter;
        if (refFichier.equals("estimate")) {
            dateFormatter = new SimpleDateFormat("dd-MM-yyyy");
        } else {
            dateFormatter = dateDefault;
        }

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
                        if (date.get(i).equals(date.get(j)) && contrat.get(i).equals(contrat.get(j)) && regul.get(j).equals("NON")) {
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
    public void mergeDuplicates() {
        ArrayList<String> contrat = getColumn("Contrat");
        ArrayList<Date> date = getColumn("Date Periode");

        Map<String, Integer> firstOccurrenceMap = new HashMap<>();
        ArrayList<Integer> rowsToDelete = new ArrayList<>();

        for (int i = 0; i < contrat.size(); i++) {
            String key = contrat.get(i) + "-" + date.get(i).toString();

            if (firstOccurrenceMap.containsKey(key)) {
                int origin = firstOccurrenceMap.get(key);
                mergeRows(rowsToDelete, i, origin);
            } else {
                firstOccurrenceMap.put(key, i);
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

    public void appendPivotTableFic(Base baseFic, String label) {
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
    public void appendPivotTableYearlyFic(Base baseFic, String label) {
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
    public void appendPivotTableTotalFic(Base baseFic, String label) {
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

    public void appendPivotTableFicN(Base baseFic, String label) {
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
    public void appendPivotTableYearlyFicN(Base baseFic, String label) {
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
    public void appendPivotTableTotalFicN(Base baseFic, String label) {
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
        for (Base baseSin : baseMapNew.values()) {
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
                if (statut.equals("Total")) continue;  // Skip faux statut

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
        for (Base baseFic : ficMapNew.values()) {
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
            Double coutMoyenValue = coutMoyenMap.getOrDefault(contratValue, 0.0);
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
        for (Base base : baseMapNew.values()) {
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

    public void addPrimesAcquises() {
        appendPAmensuel(true);
        appendPAsums(true);

        appendPAmensuel(false);
        appendPAsums(false);
    }
    public void appendPAmensuel(boolean avecICI) {
        String label;
        if (avecICI) {
            label = "Primes Acquises mensuel ";
        } else {
            label = "Primes Acquises mensuel";
        }
        ArrayList<Date> dateColumn = getColumn("Date Periode");
        ArrayList<String> contratColumn = getColumn("Contrat");

        ArrayList<Double> primeColumn = getColumn("MONTANT TOTAL PRIME ASSUREUR");
        if (avecICI) {
            primeColumn = plusComm;
        }

        addLabeledBlock(allDateHeaders, label, DBL, avecICI);

        int tableBegin = headers.size() - allDates.size();

        // Create a map to store contractKey and its corresponding count of missing dateKeys.
        ArrayList<Float> defaultCoefs = new ArrayList<>();
        defaultCoefs.add(1f);

        Map<String, List<Date>> warningMap = new HashMap<>();
        for (int i = 0; i < nrow; i++) {
            String contrat = contratColumn.get(i);
            Date date = dateColumn.get(i);

            Double prime = primeColumn.get(i);
            if (prime == 0d) continue;

            ArrayList<Float> coefs = mapCoefAQ.get(i);
            if (coefs == null) {
                coefs = defaultCoefs;
                if (prime > 0 && isComm(contrat,date)) {
                    warningMap.computeIfAbsent(contrat, k -> new ArrayList<>()).add(date);
                }
            }

            int coefBegin = allDates.indexOf(date);
            if (coefBegin >= 0) {
                int reste = min(allDates.size() - coefBegin, coefs.size());
                coefBegin += tableBegin;
                for (int iterCoef = 0; iterCoef < reste; iterCoef++) {
                    Float coef = coefs.get(iterCoef);
                    if (coef != null) {
                        if (coef == 0f) {
                            System.out.println("ERROR in row " + i);
                        }
                        getColumnByIndex(coefBegin + iterCoef).set(i, prime * coef);
                    }
                }
            }
        }
        if (!avecICI) {
            // print warnings
            for (Map.Entry<String, List<Date>> entry : warningMap.entrySet()) {
                String contrat = entry.getKey();
                List<Date> missingDates = entry.getValue();

                System.out.println("Contrat: " + contrat);
                System.out.println("CoefAcquisition non trouvé pour:");
                for (Date date : missingDates) {
                    System.out.println(dateDefault.format(date));
                }
                System.out.println("-------------"); // Separator for clarity
            }
        }
    }
    public void appendPAsums(boolean avecICI) {
        String label; String labelPA;
        if (avecICI) {
            label = "Primes Acquises annuel ";
            labelPA = "Prime Acquise à date ";
        } else {
            label = "Primes Acquises annuel";
            labelPA = "Prime Acquise à date";
        }
        int tableBegin = columns.size();
        addLabeledBlock(allYearHeaders,label,DBL,avecICI);
        addEmptyColumn("Total",DBL);
        avecICImask.add(avecICI);
        sansICImask.add(!avecICI);
        addEmptyColumn(labelPA,DBL);
        avecICImask.add(avecICI);
        sansICImask.add(!avecICI);

        // Step 1: Create a map to store the number of months for each year
        Map<Integer, Integer> monthsPerYear = new HashMap<>();

        // Step 2: Iterate over all dates
        int totalMonthsTillNow = 0;
        for (Date date : allDates) {
            int year = getYearFromDate(date);
            monthsPerYear.put(year, monthsPerYear.getOrDefault(year, 0) + 1);

            if (!date.after(TODAY_01)) {
                totalMonthsTillNow++;
            }
        }
        // For each row
        for (int rowIndex = 0; rowIndex < nrow; rowIndex++) {
            double rowTotal = 0;
            double primeToDate = 0;
            int monthColumnsBegin = tableBegin - allDates.size();
            int processedMonths = 0; // Track the number of months processed

            for (int yearIndex = 0; yearIndex < allYears.size(); yearIndex++) {
                int yearStart = monthColumnsBegin + processedMonths; // Adjust the year start based on the number of months processed

                int monthsInThisYear = monthsPerYear.get(allYears.get(yearIndex));
                double yearlyTotal = 0;

                for (int monthOffset = 0; monthOffset < monthsInThisYear; monthOffset++) {
                    Double monthlyValue = (Double) getColumnByIndex(yearStart + monthOffset).get(rowIndex);
                    if (monthlyValue != null) {
                        yearlyTotal += monthlyValue;
                        if (processedMonths < totalMonthsTillNow) {
                            primeToDate += monthlyValue;
                        }
                    }
                    processedMonths++;
                }

                getColumnByIndex(tableBegin + yearIndex).set(rowIndex, yearlyTotal);
                rowTotal += yearlyTotal;
            }

            // Set the totals for this row
            getColumnByIndex(headers.size() - 2).set(rowIndex, rowTotal); // -2 because we added a new column
            getColumnByIndex(headers.size() - 1).set(rowIndex, primeToDate); // "Prime Acquise à date"
        }
    }

    // DATE HELPERS
    public int getYearFromDate(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return cal.get(Calendar.YEAR);
    }
    public void findDateGaps() {

        List<String> contractColumn = getColumn("Contrat");
        List<Date> dateColumn = getColumn("Date Periode");

        String currentContract = null;
        Date previousDate = null;

        for (int i = 0; i < nrow; i++) {
            String contract = contractColumn.get(i);
            Date date = dateColumn.get(i);

            if (currentContract == null || !currentContract.equals(contract)) {
                // New contract group
                currentContract = contract;
                previousDate = date;
                continue;
            }

            long differenceInMonths = monthsBetweenDates(previousDate, date);
            if (differenceInMonths > 1) {
                // Found a gap
                if (!gapsMap.containsKey(contract)) {
                    gapsMap.put(contract, new HashMap<>());
                }

                Calendar cal = Calendar.getInstance();
                cal.setTime(previousDate);

                // Populate the gaps
                for (int j = 1; j < differenceInMonths; j++) {
                    cal.add(Calendar.MONTH, 1);  // Increment by 1 month
                    Date missingDate = cal.getTime();
                    gapsMap.get(contract).put(missingDate, (int) differenceInMonths - j);
                }
            }

            previousDate = date;
        }
    }
    public void findDateGapsFromLastAvailable() {
        List<String> contractColumn = getColumn("Contrat");
        List<Date> dateColumn = getColumn("Date Periode");

        String currentContract = null;
        Date previousDate = null;

        for (int i = 0; i < nrow; i++) {
            String contract = contractColumn.get(i);
            Date date = dateColumn.get(i);

            if (currentContract == null || !currentContract.equals(contract)) {
                // New contract group
                currentContract = contract;
                previousDate = date;
                continue;
            }

            long differenceInMonths = monthsBetweenDates(previousDate, date);
            if (differenceInMonths > 1) {
                // Found a gap
                if (!gapsMap.containsKey(contract)) {
                    gapsMap.put(contract, new HashMap<>());
                }

                Calendar cal = Calendar.getInstance();
                cal.setTime(previousDate);

                // Populate the gaps
                for (int j = 1; j < differenceInMonths; j++) {
                    cal.add(Calendar.MONTH, 1);  // Increment by 1 month
                    Date missingDate = cal.getTime();
                    gapsMap.get(contract).put(missingDate, j);  // Using 'j' to count months since the last available date
                }
            }

            previousDate = date;
        }
    }
    // Helper method to compute the difference in months between two dates
    private static long monthsBetweenDates(Date date1, Date date2) {
        Calendar startCalendar = new GregorianCalendar();
        startCalendar.setTime(date1);
        Calendar endCalendar = new GregorianCalendar();
        endCalendar.setTime(date2);

        long monthsBetween = 0;
        while (startCalendar.before(endCalendar)) {
            startCalendar.add(Calendar.MONTH, 1);
            monthsBetween++;
        }

        return monthsBetween;
    }

    // SP
    public void addSP () {
        ArrayList<String> colsToAdd = new ArrayList<>(Arrays.asList(
                "Taux acquisition","Taux acquisition ",
                "PB", "S/P previ hors PB",
                "S/P si pas réel acquis avec provision","S/P si pas réel acquis avec provision ",
                "S/P si pas reel ultime avant PB","S/P si pas reel ultime avant PB ",
                "S/P si pas reel ultime apres PB","S/P si pas reel ultime apres PB ",
                "Sinistre Ultime", "Sinistre Ultime "));
        ArrayList<Boolean> avecICIcolumns = new ArrayList<>(Arrays.asList(false,true,true,true,false,true,false,true,false,true,false,true));
        ArrayList<Boolean> sansICIcolumns = new ArrayList<>(Arrays.asList(true,false,true,true,true,false,true,false,true,false,true,false));
        avecICImask.addAll(avecICIcolumns);
        sansICImask.addAll(sansICIcolumns);

        for (String header : colsToAdd) {
            addEmptyColumn(header,DBL);
        }

        populateSPPreviHorsPB();
        populatePBColumn();

        calculateTauxAcquisition(true);
        calculateTauxAcquisition(false);

        populateSPPasReelAcquisAvecProvision(true);
        populateSPPasReelAcquisAvecProvision(false);

        addTriangleUltime(true);
        addTriangleUltime(false);

        populateSPColumns(true);
        populateSPColumns(false);
    }
    public void populateSPPreviHorsPB() {
        // Get the required columns from the current table
        List<String> contrats = getColumn("Contrat");
        List<Date> datePeriodes = getColumn("Date Periode");

        // Create or get the "S/P previ hors PB" column (assuming it exists)
        ArrayList<Double> spPreviHorsPB = getColumn("S/P previ hors PB");

        // Prepare the mapping from the SPprevi external table
        Map<String, Map<Integer, Double>> externMap = new HashMap<>();

        List<String> externContrats = SPprevi.getColumn("IDENTIFIANT CONTRAT");
        List<Integer> externAnnees = SPprevi.getColumn("ANNEES");
        List<Double> externSPPreviSansICI = SPprevi.getColumn("S/P PREVI SANS ICI");

        // Populate the hashmap with values from the external SPprevi table
        for (int i = 0; i < externContrats.size(); i++) {
            String externContract = externContrats.get(i);
            Integer externYear = externAnnees.get(i);
            Double spValue = externSPPreviSansICI.get(i);

            if (!externMap.containsKey(externContract)) {
                externMap.put(externContract, new HashMap<>());
            }

            externMap.get(externContract).put(externYear, spValue);
        }

        // Iterate through the rows of the current table and populate the "S/P previ hors PB" column
        for (int i = 0; i < contrats.size(); i++) {
            String contract = contrats.get(i);
            Date datePeriode = datePeriodes.get(i);

            // Extract the year from the date
            Calendar cal = Calendar.getInstance();
            cal.setTime(datePeriode);
            int year = cal.get(Calendar.YEAR);

            Map<Integer, Double> yearValueMap = externMap.get(contract);
            if (yearValueMap != null) {
                Double value = yearValueMap.get(year);
                spPreviHorsPB.set(i, value == null ? 0.0d : value);
            } else {
                spPreviHorsPB.set(i, 0.0d);
            }
        }
    }
    public void populatePBColumn() {
        // Get the required columns from the current table
        List<String> contrats = getColumn("Contrat");
        List<Date> datePeriodes = getColumn("Date Periode");

        // Create or get the "PB" column (assuming it exists)
        ArrayList<Double> pbColumn = getColumn("PB");

        // Prepare the mapping from the PB external table
        Map<String, Map<Date, Double>> externMap = new HashMap<>();

        List<String> externContrats = PB.getColumn("Contrat");
        List<Date> externDates = PB.getColumn("Date");
        List<Double> externPBValues = PB.getColumn("PB");

        // Populate the hashmap with values from the external PB table
        for (int i = 0; i < externContrats.size(); i++) {
            String externContract = externContrats.get(i);
            Date externDate = externDates.get(i);
            Double pbValue = externPBValues.get(i);

            if (!externMap.containsKey(externContract)) {
                externMap.put(externContract, new HashMap<>());
            }

            externMap.get(externContract).put(externDate, pbValue);
        }

        // Iterate through the rows of the current table and populate the PB column
        for (int i = 0; i < contrats.size(); i++) {
            String contract = contrats.get(i);
            Date datePeriode = datePeriodes.get(i);

            Map<Date, Double> dateValueMap = externMap.get(contract);
            if (dateValueMap != null) {
                Double value = dateValueMap.get(datePeriode);
                pbColumn.set(i, value == null ? 0.0d : value);
            } else {
                pbColumn.set(i, 0.0d);
            }
        }
    }
    public void calculateTauxAcquisition(boolean avecICI) {
        ArrayList<Double> primeAcquise; ArrayList<Double> montantTotal; ArrayList<Double> tauxAcquisitionColumn;
        if (avecICI) {
            primeAcquise = getColumn("Prime Acquise à date ");
            montantTotal = plusComm;
            tauxAcquisitionColumn = getColumn("Taux acquisition ");
        } else {
            primeAcquise = getColumn("Prime Acquise à date");
            montantTotal = getColumn("MONTANT TOTAL PRIME ASSUREUR");
            tauxAcquisitionColumn = getColumn("Taux acquisition");
        }

        for (int i = 0; i < primeAcquise.size(); i++) {
            Double prime = primeAcquise.get(i);
            Double montant = montantTotal.get(i);

            if (prime == null || montant == null) {
                tauxAcquisitionColumn.set(i, null); // Set null if any of the values are null
                System.out.println("Warning: Found null value at row " + (i+1) + ". Column 'Taux acquisition' set to null.");
                continue;
            }

            if (montant == 0.0) {
                tauxAcquisitionColumn.set(i, 0.0); // Avoid division by zero
            } else {
                double taux = prime / montant;

                if (Double.isNaN(taux)) {
                    tauxAcquisitionColumn.set(i, 0.0);
                } else {
                    tauxAcquisitionColumn.set(i, taux);
                }
            }
        }
    }
    public void populateSPPasReelAcquisAvecProvision(boolean avecICI) {
        ArrayList<Double> primeAcquise; ArrayList<Double> tauxAcquisitionColumn; ArrayList<Double> resultColumn;
        if (avecICI) {
            primeAcquise = getColumn("Prime Acquise à date ");
            tauxAcquisitionColumn = getColumn("Taux acquisition ");
            resultColumn = getColumn("S/P si pas réel acquis avec provision ");
        } else {
            primeAcquise = getColumn("Prime Acquise à date");
            tauxAcquisitionColumn = getColumn("Taux acquisition");
            resultColumn = getColumn("S/P si pas réel acquis avec provision");
        }
        // Fetch all the required columns
        ArrayList<Double> chargeComptable = getColumnSubheader("Charge Comptable totale");
        ArrayList<Double> provisionEnCours = getColumnByDoubleNotation("Provision En Cours", "Total");
        ArrayList<Double> PB = getColumn("PB");

        for (int i = 0; i < chargeComptable.size(); i++) {
            double numerator = (chargeComptable.get(i) == null ? 0 : chargeComptable.get(i)) + provisionEnCours.get(i);
            double denominator = primeAcquise.get(i) + (PB.get(i) * tauxAcquisitionColumn.get(i));

            if (denominator == 0) {
                resultColumn.set(i, 0.0);
            } else {
                resultColumn.set(i, max(numerator / denominator, 0.0));
            }
        }
    }
    public void addTriangleUltime(boolean avecICI) {
        String labelTriangle; int paFirstIndex; int sinUltimeIndex;
        if (avecICI) {
            labelTriangle = "Sinistres Previ si pas Reel comptable ";
            paFirstIndex = subheaders.indexOf("Primes Acquises mensuel ");
            sinUltimeIndex = headers.indexOf("Sinistre Ultime ");
        } else {
            labelTriangle = "Sinistres Previ si pas Reel comptable";
            paFirstIndex = subheaders.indexOf("Primes Acquises mensuel");
            sinUltimeIndex = headers.indexOf("Sinistre Ultime");
        }
        ArrayList<Double> coutMoyen = getColumn("Cout moyen En Cours");
        ArrayList<Double> coutMoyenAcc = getColumn("Cout moyen En Cours Accepté");
        int nFirstIndex = subheaders.indexOf("Nombre En cours mensuel");
        int nAccFirstIndex = subheaders.indexOf("Nombre En cours - accepté mensuel");

        headers.addAll(allDateHeaders);

        for (int i = 0; i < allDateHeaders.size(); i++) {
            ArrayList<Double> columnData = new ArrayList<>(Collections.nCopies(nrow, null));
            columns.add(new Column<>(columnData, ColTypes.DBL));
            avecICImask.add(avecICI);
            sansICImask.add(!avecICI);
        }

        subheaders.add(labelTriangle);
        subheaders.addAll(Collections.nCopies(allDateHeaders.size() - 1, null));

        int beginIndex = headers.size() - allDates.size();

        int comptaFirstIndex = subheaders.indexOf("Charge Comptable mensuelle");
        ArrayList<Double> spColumn = getColumn("S/P previ hors PB");

        ArrayList<Double> sinistreUltimeColumn = new ArrayList<>(Collections.nCopies(nrow, 0.0));

        int offset = 0;
        for (int col = beginIndex; col < headers.size(); col++) {
            if (allDates.get(col-beginIndex).before(TODAY_01)) {
                ArrayList<Double> comptaColumn = getColumnByIndex(comptaFirstIndex + offset);
                ArrayList<Object> nColumn = getColumnByIndex(nFirstIndex + offset);
                ArrayList<Object> nAccColumn = getColumnByIndex(nAccFirstIndex + offset);
                for (int row = 0; row < nrow; row++) {
                    Double compta = comptaColumn.get(row);
                    int n = nColumn.get(row) == null ? 0 : (int) nColumn.get(row);
                    int nAcc = nAccColumn.get(row) == null ? 0 : (int) nAccColumn.get(row);
                    double provision = coutMoyen.get(row) * n + coutMoyenAcc.get(row) * nAcc;
                    Double value = (compta == null ? 0.0 : compta) + provision;
                    getColumnByIndex(col).set(row,value);
                    sinistreUltimeColumn.set(row, sinistreUltimeColumn.get(row) + value);
                }
                offset++;
            } else {
                ArrayList<Double> paColumn = getColumnByIndex(paFirstIndex + offset);
                for (int row = 0; row < nrow; row++) {
                    Double pa = paColumn.get(row);
                    if (pa == null) {
                        getColumnByIndex(col).set(row,0.0);
                    } else {
                        Double value = pa * spColumn.get(row);
                        getColumnByIndex(col).set(row,value);
                        sinistreUltimeColumn.set(row, sinistreUltimeColumn.get(row) + value);
                    }
                }
                offset++;
            }
        }
        columns.set(sinUltimeIndex,new Column<>(sinistreUltimeColumn, DBL));
    }
    public void populateSPColumns(boolean avecICI) {
        ArrayList<Double> sinistreUltimeColumn; ArrayList<Double> primeColumn; ArrayList<Double> avantPB; ArrayList<Double> apresPB;
        if (avecICI) {
            sinistreUltimeColumn = getColumn("Sinistre Ultime ");
            primeColumn = plusComm;
            avantPB = getColumn("S/P si pas reel ultime avant PB ");
            apresPB = getColumn("S/P si pas reel ultime apres PB ");
        } else {
            sinistreUltimeColumn = getColumn("Sinistre Ultime");
            primeColumn = getColumn("MONTANT TOTAL PRIME ASSUREUR");
            avantPB = getColumn("S/P si pas reel ultime avant PB");
            apresPB = getColumn("S/P si pas reel ultime apres PB");
        }

        ArrayList<Double> pbColumn = getColumn("PB");

        for (int i = 0; i < nrow; i++) {
            Double sinistreValue = sinistreUltimeColumn.get(i);
            Double pbValue = pbColumn.get(i);
            Double primeValue = primeColumn.get(i);

            Double denominatorApresPB = primeValue + pbValue;

            // Populate the columns based on the formulas
            if (primeValue == 0.0) {
                avantPB.set(i, 0.0);
            } else {
                avantPB.set(i, sinistreValue / primeValue);
            }

            if (denominatorApresPB == 0) {
                apresPB.set(i, 0.0);
            } else {
                apresPB.set(i, sinistreValue / denominatorApresPB);
            }
        }
    }

    protected void addLabeledBlock(List<String> headersToAdd,String label, ColTypes type, boolean avecICI) {
        headers.addAll(headersToAdd);

        for (int i = 0; i < headersToAdd.size(); i++) {
            ArrayList<Double> columnData = new ArrayList<>(Collections.nCopies(nrow, null));
            columns.add(new Column<>(columnData, type));
            avecICImask.add(avecICI);
            sansICImask.add(!avecICI);
        }

        subheaders.add(label);
        subheaders.addAll(Collections.nCopies(headersToAdd.size() - 1, null));
    }
    protected void saveFDT(boolean avecICI) throws IOException {
        Path originalPath = Paths.get(this.path);
        String filenameWithoutExtension = originalPath.getFileName().toString().replaceFirst("[.][^.]+$", "");
        String fileExtension = originalPath.toString().substring(originalPath.toString().lastIndexOf(".") + 1);
        String newPath;
        if (avecICI) {
            newPath = outputFolder + filenameWithoutExtension + "_FDT_avec ICI." + fileExtension;
        } else {
            newPath = outputFolder + filenameWithoutExtension + "_FDT." + fileExtension;
        }

        // Determine which mask to use based on avecICI
        ArrayList<Boolean> mask = avecICI ? avecICImask : sansICImask;

        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(newPath), StandardCharsets.UTF_8)) {
            // Write BOM for UTF-8
            writer.write('\ufeff');

            // If there are subheaders, write them
            if (subheaders != null && !subheaders.isEmpty()) {
                List<String> filteredSubheaders = new ArrayList<>();
                for (int j = 0; j < subheaders.size(); j++) {
                    if (mask.get(j)) {
                        filteredSubheaders.add(subheaders.get(j) != null ? subheaders.get(j).trim() : "");
                    }
                }
                writer.write(String.join(";", filteredSubheaders));
                writer.newLine();
            }

            // Write headers applying the mask
            List<String> filteredHeaders = IntStream.range(0, headers.size())
                    .filter(mask::get)
                    .mapToObj(index -> headers.get(index).trim())
                    .collect(Collectors.toList());
            writer.write(String.join(";", filteredHeaders));
            writer.newLine();

            // Write data applying the mask
            for (int i = 0; i < nrow; i++) {
                List<String> row = getRow(i).stream()
                        .map(item -> {
                            if (item instanceof Date) {
                                return dateDefault.format((Date) item);
                            } else if (item instanceof Double) {
                                return DECIMAL_FORMAT.format(item).replace('.', ','); // Replace period with comma
                            }
                            return item != null ? item.toString() : "";
                        })
                        .collect(Collectors.toList());

                List<String> filteredRow = IntStream.range(0, row.size())
                        .filter(mask::get)
                        .mapToObj(row::get)
                        .collect(Collectors.toList());

                writer.write(String.join(";", filteredRow));
                writer.newLine();
            }
        }
    }
}
