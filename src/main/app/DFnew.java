package main.app;

import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.ParseException;
import java.util.*;
import java.io.File;
import java.io.InputStream;
import java.util.Date;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import static java.nio.charset.StandardCharsets.UTF_8;
import static main.app.App.*;
import static main.app.DFnew.ColTypes.*;

public class DFnew {
    public static final String wd = "E:/202305/wd/";
    protected String path;
    protected int nrow = 0;
    protected ArrayList<Column<?>> columns;
    protected ArrayList<String> headers;
    protected ArrayList<String> subheaders;
    protected ArrayList<String> columnNamesToRead;
    protected ArrayList<ColTypes> columnTypes;
    protected ArrayList<String> columnNamesAttributed;
    protected static final Charset encodingDefault = UTF_8;
    private static final char delimDefault = ';';
    protected SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
    private static final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");  // For example: 20230907_125959
    public static DFnew PB;
    public static DFnew refProg;
    public static DFnew refCols;
    public static DFnew refSource;
    public static DFnew mapping;
    public static DFnew SPprevi;
    public static DFnew mapStatuts;
    public static DFnew grilleTarif;
    public static DFnew coefPM;
    private static final Set<String> aChercherDansCoefPM = new HashSet<>();
    public static final int lastM;
    static final Map<Integer, ArrayList<Float>> mapCoefAQ = new HashMap<>();
    static {
        aChercherDansCoefPM.add("ICIPMCD15");
        aChercherDansCoefPM.add("ICIPMCH15");
        aChercherDansCoefPM.add("ICIPMEG15");
        aChercherDansCoefPM.add("ICIPMG17");
        aChercherDansCoefPM.add("ICIPMTT15");
        aChercherDansCoefPM.add("ICIPMDT15");
        aChercherDansCoefPM.add("ICIPMDV15");
        aChercherDansCoefPM.add("ICISMIC19");
        aChercherDansCoefPM.add("ICIMOPEMPPRO22");
    } //PM
    static {
        try {
            PB = new DFnew(wd + "PB Micromania.csv",';',false,"PB");
            refProg = new DFnew(wd + "ref_Programmes.csv",';',false,"refProg");
            refCols = new DFnew(wd + "ref_Renta.xlsx","ref_cols",false,"refCols");
            refSource = new DFnew(wd + "ref_Renta.xlsx","source",false,"refSource");
            mapping = new DFnew(wd + "mapping.xlsx","Mapping entrant sinistres",false,"mapping");
            SPprevi = new DFnew(wd + "S SUR P PREVI 2023_01_18.xlsx","Feuil1",false,"SPprevi");
            mapStatuts = new DFnew(wd + "statuts.xlsx","Statuts",false,"mapStatuts");
            grilleTarif = new DFnew(wd + "Grille_Tarifaire.csv",';',false,"grilleTarif");
            coefPM = new DFnew(wd + "coefPM.csv",';',false,"coefPM");
            mergeRowsOnContratRefProg();
            populateGlobalStatutMap();
            mapPoliceToPB();
            mapPoliceToSPPrevi();
            repairReferenceGT();
            lastM = grilleTarif.findLastNonNullColumnFromM();
        } catch (IOException | ParseException e) {
            throw new RuntimeException(e);
        }

    } //REFS
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy");
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.####"); // Adjust the number of # after the point for precision.
    public DFnew() {

    }

    public static void main(String[] args) throws IOException, ParseException, Exception {
        Stopwatch stopwatch = new Stopwatch();
        stopwatch.start();

//        mapStatuts.populateStatutMap();
        stopwatch.printElapsedTime();
    }
    public DFnew(String csvFilePath, char delim, boolean toLower, String refFichier) throws IOException, ParseException {
        path = csvFilePath;
        FileConfig config = FileConfig.getInstance();
        if (refFichier != null) {
            columnNamesToRead = config.getColumnNamesToRead(refFichier);
            columnTypes = config.getColumnTypes(refFichier);
            columnNamesAttributed = config.getColumnNamesAttributed(refFichier);
            validateColumnInputs(columnNamesToRead, columnTypes, columnNamesAttributed);
        }

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
                ColTypes colType = (columnTypes == null) ? ColTypes.STR : columnTypes.get(i);

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
               throw new RuntimeException("column " + expectedHeader + " not found for base: " + csvFilePath);
            }
        }
        trimNullFirstCol();
    }
    public DFnew(String xlsxFilePath, String sheetName, boolean toLower, String refFichier) throws IOException, ParseException {
        FileConfig config = FileConfig.getInstance();
        if (refFichier != null) {
            columnNamesToRead = config.getColumnNamesToRead(refFichier);
            columnTypes = config.getColumnTypes(refFichier);
            columnNamesAttributed = config.getColumnNamesAttributed(refFichier);
        }
        validateColumnInputs(columnNamesToRead, columnTypes, columnNamesAttributed);

        columns = new ArrayList<>();
        headers = new ArrayList<>();

        // Use Apache POI to open the workbook
        InputStream is = Files.newInputStream(new File(xlsxFilePath).toPath());
        Workbook workbook = new XSSFWorkbook(is);
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            throw new IllegalArgumentException("Sheet " + sheetName + " not found in the XLSX file!");
        }
        nrow = sheet.getLastRowNum();

        Row headerRowPOI = sheet.getRow(0);
        if (headerRowPOI == null) {
            throw new IllegalArgumentException("XLSX sheet is empty or missing header row!");
        }

        List<String> headerList = new ArrayList<>();
        headerRowPOI.forEach(cell -> headerList.add(cell.toString().trim()));

        if (toLower) {
            headerList.replaceAll(String::toLowerCase);
        }

        for (int ih = 0; ih < headerList.size(); ih++) {
            String header = headerList.get(ih);
            if (columnNamesToRead == null || columnNamesToRead.contains(header)) {
                int i = Objects.requireNonNull(columnNamesToRead).indexOf(header);
                headers.add(columnNamesAttributed != null ? columnNamesAttributed.get(i) : header);

                ArrayList<Object> colData = new ArrayList<>();
                ColTypes colType = (columnTypes == null) ? ColTypes.STR : columnTypes.get(i);

                for (int j = 1; j <= sheet.getLastRowNum(); j++) {
                    Row currentRow = sheet.getRow(j);
                    if (currentRow != null) {
                        Cell cell = currentRow.getCell(ih);
                        String cellValue = (cell == null) ? "" : cell.toString();
                        Object formattedCell;

                        if (toLower) {
                            formattedCell = getLowerCell(cellValue, colType);
                        } else {
                            formattedCell = getCell(cellValue, colType);
                        }

                        colData.add(formattedCell);
                    }
                }

                columns.add(new Column<>(colData, colType));
            }
        }

        workbook.close();  // Don't forget to close the workbook to release resources

        if (columnNamesToRead != null && columnTypes != null && columnNamesToRead.size() != columnTypes.size()) {
            throw new IllegalArgumentException("Mismatch between column names to read and column types provided.");
        }
        trimNullFirstCol();
    }
    public static void getCoefsAcquisition(boolean populateTDB,Estimatenew estimate) throws IOException, ParseException {

        if(populateTDB) {
            DFnew TDB2 = new DFnew(wd + "TDB part 2.csv",';',false,"TDB2");
            TDB2.populateTDB2();
            TDB2.fill0();
            TDB2.saveTDBtoCSVprecision(wd + "TDB part 2_populated.csv");
        }

        DFnew TDB2populated = new DFnew(wd + "TDB part 2_populated.csv",';',false,"TDB2_populated");

        estimate.populateCoefficientMap(TDB2populated);

        if(areListsSummingToOne(mapCoefAQ,estimate)) {
            System.out.println("All lists sum to 1.0");
        } else {
            System.out.println("Some lists do not sum to 1.0");
        }
        writeMapAndEstimateToCSV(mapCoefAQ,estimate,outputFolder+"coefAcquisition.csv");
    }
    // CSV READER
    protected void validateColumnInputs(ArrayList<String> columnNamesToRead, ArrayList<ColTypes> columnTypes, ArrayList<String> columnNamesAttributed) {
        int size;
        if (columnNamesToRead != null) {
            size = columnNamesToRead.size();
        } else {
            throw new IllegalArgumentException("Columns to read unknown!");
        }
        if (columnTypes != null) {
            if (size != columnTypes.size()) {
                throw new IllegalArgumentException("Mismatch between sizes of columnNamesToRead and columnTypes.");
            }
        }
        if (columnNamesAttributed != null) {
            if (size != columnNamesAttributed.size()) {
                throw new IllegalArgumentException("Mismatch between sizes of columnNamesToRead/columnTypes and columnNamesAttributed.");
            }
        }
    }
    public Object getCell(String cell, ColTypes type) {
        if (cell == null) {
            return switch (type) {
                case DBL -> 0.0;  // Default value for Double
                case FLT -> 0f;   // Default value for Float
                case INT -> 0;    // Default value for Int
                default -> null;
            };
        }
        return formatCell(cell, type);
    }

    public Object getLowerCell(String cell, ColTypes type) {
        if (cell == null) {
            return null;
        }
        return formatCell(cell.toLowerCase(), type);
    }
    private Object formatCell(String cell, ColTypes type) {
        try {
            return switch (type) {
                case STR -> cell;
                case DAT -> {
                    try {
                        yield dateFormat.parse(cell);
                    } catch (ParseException e) {
                        yield null;  // Return null if the date is unparsable
                    }
                }
                case DBL -> {
                    try {
                        yield Double.parseDouble(cell.replace(',', '.'));
                    } catch (NumberFormatException e) {
                        yield 0.0; // Return default for Double
                    }
                }
                case FLT,FLTNULL -> {
                    try {
                        yield Float.parseFloat(cell.replace(',', '.'));
                    } catch (NumberFormatException e) {
                        yield 0f;  // Return default for Float
                    }
                }
                case INT -> {
                    try {
                        yield (int) Double.parseDouble(cell);
                    } catch (NumberFormatException e) {
                        yield 0;  // Return default for Int
                    }
                }
            };
        } catch (Exception e) {
            // Handle any other potential errors and return null (or you can log the error here)
            return null;
        }
    }


    // GETTERS
    @SuppressWarnings("unchecked")
    public <T> ArrayList<T> getColumn(String header) {
        int index = headers.indexOf(header);
        if (index != -1) {
            return ((Column<T>) columns.get(index)).getData();
        } else {
            throw new IllegalArgumentException("Column with header: " + header + " not found.");
        }
    }
    @SuppressWarnings("unchecked")
    public <T> ArrayList<T> getColumnSubheader(String header) {
        int index = subheaders.indexOf(header);
        if (index != -1) {
            return ((Column<T>) columns.get(index)).getData();
        } else {
            throw new IllegalArgumentException("Column with header: " + header + " not found.");
        }
    }
    @SuppressWarnings("unchecked")
    public <T> ArrayList<T> getColumnByIndex(int index) {
        if (index < 0 || index >= columns.size()) {
            throw new IndexOutOfBoundsException("Invalid column index: " + index);
        }
        return ((Column<T>) columns.get(index)).getData();
    }
    public ArrayList<Object> getRow(int rowIndex) {
        if (rowIndex < 0) {
            throw new IndexOutOfBoundsException("Invalid row index: " + rowIndex);
        }

        ArrayList<Object> row = new ArrayList<>();

        for (Column<?> column : columns) {
            ArrayList<?> data = column.getData();
            if (rowIndex >= data.size()) {
                throw new IndexOutOfBoundsException("Row index: " + rowIndex + " is out of bounds for column with size: " + data.size());
            }
            row.add(data.get(rowIndex));
        }

        return row;
    }
    public enum ColTypes {
        STR,
        DAT,
        DBL,
        FLT,
        INT,
        FLTNULL
    }
    static class Column<T> {
        private final ArrayList<T> data;
        private final ColTypes type;

        public Column(ArrayList<T> data, ColTypes type) {
            this.data = data;
            this.type = type;
        }

        public ArrayList<T> getData() {
            return data;
        }

        public ColTypes getType() {
            return type;
        }
    }

    // SETTERS
    protected void setColumns(ArrayList<Column<?>> columns) {
        this.columns = columns;
    }
    protected void setHeaders(ArrayList<String> headers) {
        this.headers = headers;
    }
    protected <T> void addColumn(String header, ArrayList<T> columnData, ColTypes type) {
        columns.add(new Column<T>(columnData, type));
        headers.add(header);
        if(subheaders != null) {
            subheaders.add(null);  // Adding null for subheader only if it makes sense
        }
    }
    protected void addEmptyColumn(String header, ColTypes type) {
        ArrayList<Double> columnData = new ArrayList<>(Collections.nCopies(nrow, null));
        addColumn(header,columnData,type);
    }
    protected <T> void addColumnWithSubheader(String header, String subheader, ArrayList<T> columnData, ColTypes type) {
        columns.add(new Column<T>(columnData, type));
        headers.add(header);
        subheaders.add(subheader);  // Adding the provided subheader
    }
    protected void addLabeledBlock(List<String> headersToAdd,String label, ColTypes type) {
        headers.addAll(headersToAdd);

        for (int i = 0; i < headersToAdd.size(); i++) {
            ArrayList<Double> columnData = new ArrayList<>(Collections.nCopies(nrow, null));
            columns.add(new Column<>(columnData, type));
        }

        subheaders.add(label);
        subheaders.addAll(Collections.nCopies(headersToAdd.size() - 1, null));
    }
    public void addCoefDatColumns() {
        // Start appending columns from M to M+200
        for (int i = 0; i <= 200; i++) {
            String header;
            if (i == 0) {
                header = "M";
            } else {
                header = "M+" + i;
            }
            addEmptyColumn(header, ColTypes.FLT);
        }
//        addEmptyColumn("DATE DEBUT TARIF", ColTypes.DAT);
//        addEmptyColumn("DATE FIN TARIF", ColTypes.DAT);
    }

    // SORT-DELETE
    protected void removeColumn(String header) {
        int columnIndex = headers.indexOf(header);
        if (columnIndex == -1) {
            System.out.println("Warning: Header " + header + " not found.");
            return;
        }

        // Remove column data
        columns.remove(columnIndex);

        // Remove header
        headers.remove(columnIndex);

        // Remove subheader if exists
        if (subheaders != null && !subheaders.isEmpty()) {
            subheaders.remove(columnIndex);
        }
    }
    protected void trimNullDatePeriodeRows() {
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
    protected void trimNullFirstCol() {
        ArrayList<Object> colToTrim = getColumnByIndex(0);
        ArrayList<Integer> rowsToDelete = new ArrayList<>();

        // Start from the end and move to the beginning
        for (int i = colToTrim.size() - 1; i >= 0; i--) {
            if (colToTrim.get(i) == null) {
                rowsToDelete.add(i);
            } else {
                // Stop once a non-null value is found
                break;
            }
        }

        // Now, delete those rows
        deleteRows(rowsToDelete);
    }
    public void deleteRows(ArrayList<Integer> rowsToDelete) {
        // Sort rowsToDelete in descending order to avoid shifting index issues
        rowsToDelete.sort(Collections.reverseOrder());

        // Iterate over every column and delete the rows
        for (int col = 0; col < this.columns.size(); col++) {
            ArrayList<Object> currentColumn = getColumnByIndex(col);

            for (Integer rowIndex : rowsToDelete) {
                if (rowIndex >= 0 && rowIndex < currentColumn.size()) {
                    currentColumn.remove(rowIndex.intValue());
                }
            }
        }
        nrow = nrow - rowsToDelete.size();
    }
    public void deleteRowsUnsafe(ArrayList<Integer> rowsToDelete) {
        // Sort rowsToDelete in descending order to avoid shifting index issues
        rowsToDelete.sort(Collections.reverseOrder());

        // Iterate over every column and delete the rows
        for (int col = 0; col < this.columns.size(); col++) {
            System.out.println(col);
            ArrayList<Object> currentColumn = getColumnByIndex(col);

            for (int rowIndex : rowsToDelete) {
                currentColumn.remove(rowIndex);
            }
        }
        nrow = nrow - rowsToDelete.size();
    }
    public void deleteMatchingRowsVSGrilleTarif() {
        // Build a set of combined keys for the external dataset
        Set<String> externalCombinedKeys = new HashSet<>();
        ArrayList<String> contrats = grilleTarif.getColumn("IDENTIFIANT_CONTRAT");
        ArrayList<String> refs = grilleTarif.getColumn("REFERENCE");
        for (int i = 0; i < grilleTarif.nrow; i++) {
            String key = contrats.get(i) + "_" + refs.get(i);
            externalCombinedKeys.add(key);
        }

        ArrayList<String> identifiantContrat = getColumn("IDENTIFIANT_CONTRAT");
        ArrayList<String> reference = getColumn("REFERENCE");
        // Find the rows to delete from the current dataset
        ArrayList<Integer> rowsToDelete = new ArrayList<>();
        for (int i = 0; i < nrow; i++) {
            String key = identifiantContrat.get(i) + "_" + reference.get(i);
            if (externalCombinedKeys.contains(key)) {
                rowsToDelete.add(i);
            }
        }

        // Delete the identified rows from the current dataset
        deleteRows(rowsToDelete);
    }
    public void sortTableByContractAndDate() {
        List<String> contractColumn = getColumn("Contrat");
        List<Date> dateColumn = getColumn("Date Periode");

        // Create a list of row indices to sort
        List<Integer> rowIndices = new ArrayList<>();
        for (int i = 0; i < nrow; i++) {
            rowIndices.add(i);
        }

        // Sort row indices based on the contract and date columns
        rowIndices.sort(new Comparator<Integer>() {
            @Override
            public int compare(Integer row1, Integer row2) {
                // Compare by contract
                int contractComparison = contractColumn.get(row1).compareTo(contractColumn.get(row2));
                if (contractComparison != 0) {
                    return contractComparison;
                }

                // If contracts are the same, compare by date
                Date date1 = dateColumn.get(row1);
                Date date2 = dateColumn.get(row2);

                // Handling potential nulls in date column
                if (date1 == null && date2 == null) return 0;
                if (date1 == null) return -1;
                if (date2 == null) return 1;

                return date1.compareTo(date2);
            }
        });

        // Reorder the rows based on the sorted row indices
        reorderRows(rowIndices);
    }
    private void reorderRows(List<Integer> sortedIndices) {
        // For each column, create a new list based on the sorted row indices
        for (int col = 0; col < columns.size(); col++) {
            List<Object> oldColumn = getColumnByIndex(col);
            List<Object> newColumn = new ArrayList<>();

            for (Integer index : sortedIndices) {
                newColumn.add(oldColumn.get(index));
            }

            // Replace old column data with new sorted data
            for (int i = 0; i < nrow; i++) {
                oldColumn.set(i, newColumn.get(i));
            }
        }
    }
    void mergeRows(ArrayList<Integer> rowsToDelete, int i, int origin) {
        for (int col = 0; col < this.columns.size(); col++) {
            if(columns.get(col).getType().equals(DBL)) {
                Double valueI = (Double) getColumnByIndex(col).get(i);
                Double valueOrigin = (Double) getColumnByIndex(col).get(origin);
                this.getColumnByIndex(col).set(origin, valueI + valueOrigin);
            } else if(columns.get(col).getType().equals(INT)) {
                Integer valueI = (Integer) getColumnByIndex(col).get(i);
                Integer valueOrigin = (Integer) getColumnByIndex(col).get(origin);
                this.getColumnByIndex(col).set(origin, valueI + valueOrigin);
            }
        }
        rowsToDelete.add(i);
    }

    // PRINTING
    public void print() {
        print(10);  // Default to 10 rows
    }
    public void print(int numRows) {
        final int fixedWidth = 22;  // Adjusted width to 22

        // Print headers
        printRow(headers, fixedWidth);

        // If subheaders are defined, print them
        if (subheaders != null && !subheaders.isEmpty()) {
            printRow(subheaders, fixedWidth);
        }

        // Print rows
        for (int i = 0; i < Math.min(numRows, columns.get(0).getData().size()); i++) {
            List<String> row = new ArrayList<>();
            for (Column<?> col : columns) {
                Object data = col.getData().get(i);
                row.add(data != null ? formatDate(data) : "");
            }
            printRow(row, fixedWidth);
        }
    }
    private void printRow(List<String> row, int width) {
        for (String cell : row) {
            System.out.print(padRight(cell, width));
        }
        System.out.println();
    }
    private String padRight(String s, int width) {
        if (s.length() > width - 2) {
            s = s.substring(0, width - 2);  // Truncate string to fit within width minus padding
        }
        return String.format("%-" + width + "s", s);
    }
    private String formatDate(Object obj) {
        if(obj instanceof Date) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
            return sdf.format((Date) obj);
        }
        return obj.toString();
    }

    // REF
    public static void populateGlobalStatutMap() {
        ArrayList<String> statuts = mapStatuts.getColumn("Statut");
        ArrayList<String> references = mapStatuts.getColumn("Reference");

        if (statuts.size() != references.size()) {
            throw new IllegalArgumentException("Statut and Reference columns have different sizes!");
        }

        for (int i = 0; i < statuts.size(); i++) {
            globalStatutMap.put(statuts.get(i), references.get(i));
        }
    }
    public static void mapPoliceToPB() {
        ArrayList<String> colContrat = PB.getColumn("Contrat");
        ArrayList<Date> colDate = PB.getColumn("Date");
        ArrayList<Double> colPB = PB.getColumn("PB");

        SimpleDateFormat sdfOutput = new SimpleDateFormat("MM-yyyy");

        for (int i = 0; i < PB.nrow; i++) {
            String contrat = colContrat.get(i);
            Date dateValue = colDate.get(i);
            String formattedDate = sdfOutput.format(dateValue);
            Double PBv = colPB.get(i);

            mapPB
                    .computeIfAbsent(contrat, k -> new HashMap<>())
                    .put(formattedDate, PBv);
        }
    }
    public static void mapPoliceToSPPrevi() {
        ArrayList<String> colContrat = SPprevi.getColumn("IDENTIFIANT CONTRAT");
        ArrayList<Integer> colAnnees = SPprevi.getColumn("ANNEES");
        ArrayList<Double> colPrevi = SPprevi.getColumn("S/P PREVI SANS ICI");

        for (int i = 0; i < SPprevi.nrow; i++) {
            String identifiant = colContrat.get(i);
            Integer annee = colAnnees.get(i);
            Double spPrevi = colPrevi.get(i);

            mapSPprevi
                    .computeIfAbsent(identifiant, k -> new HashMap<>())
                    .put(annee, spPrevi);
        }
    }
    DFnew mappingFiltre(String inputColumn) {
        int formatICIIndex = headers.indexOf("Format ICI");
        int inputColumnIndex = headers.indexOf(inputColumn);

        // If the "Format ICI" column isn't found, assume it's at index 0.
        if (formatICIIndex == -1) {
            formatICIIndex = 0;
        }

        // If the input column isn't found, return null.
        if (inputColumnIndex == -1) {
            return null;
        }

        ArrayList<Column<?>> newColumns = new ArrayList<>();
        // Add the "Format ICI" column
        newColumns.add(columns.get(formatICIIndex));
        // Add the input column
        newColumns.add(columns.get(inputColumnIndex));

        ArrayList<String> newHeaders = new ArrayList<>();
        newHeaders.add("Format ICI");
        newHeaders.add(inputColumn);

        // Assuming you have a suitable constructor or setters in DFnew
        DFnew result = new DFnew();
        result.setColumns(newColumns);
        result.setHeaders(newHeaders);
        result.nrow = mapping.nrow;
        return result;
    }
    public static void mergeRowsOnContratRefProg() {
        Map<String, Integer> contratToRowIndex = new HashMap<>();
        ArrayList<Integer> rowsToDelete = new ArrayList<>();

        ArrayList<String> contratColumn = refProg.getColumn("Contrat");
        ArrayList<Date> dateDebutColumn = refProg.getColumn("Date Debut");
        ArrayList<Date> dateFinColumn = refProg.getColumn("Date Fin");

        for (int i = 0; i < refProg.nrow; i++) {
            String contrat = contratColumn.get(i);
            Date dateDebut = dateDebutColumn.get(i);
            Date dateFin = dateFinColumn.get(i);

            // If contrat already processed before
            if (contratToRowIndex.containsKey(contrat)) {
                int existingRowIndex = contratToRowIndex.get(contrat);
                Date existingDateDebut = dateDebutColumn.get(existingRowIndex);
                Date existingDateFin = dateFinColumn.get(existingRowIndex);

                // Update Date Debut and Date Fin if the current date is outside the existing range
                if (dateDebut.before(existingDateDebut)) {
                    dateDebutColumn.set(existingRowIndex, dateDebut);
                }

                if (dateFin.after(existingDateFin)) {
                    dateFinColumn.set(existingRowIndex, dateFin);
                }

                // Mark the current row for deletion
                rowsToDelete.add(i);
            } else {
                // If this contrat is seen for the first time, just store its row index
                contratToRowIndex.put(contrat, i);
            }
        }

        // Delete marked rows
        refProg.deleteRows(rowsToDelete);
    }

    // TDB
    public static void TDBfilterAndMergeSRC() throws IOException, ParseException {
        DFnew TDB2srcF = new DFnew(wd + "TDB France SRC.csv",';',false,"TDB2_src");
        TDB2srcF.deleteMatchingRowsVSGrilleTarif();
        DFnew TDB2srcHF = new DFnew(wd + "TDB Hors France SRC.csv",';',false,"TDB2_src");
        TDB2srcHF.deleteMatchingRowsVSGrilleTarif();
        mergeDatasets(TDB2srcF,TDB2srcHF);
        TDB2srcF.saveTDBtoCSVprecision(wd + "TDB_SRC_merged.csv");
    }
    public static void mergeDatasets(DFnew dataset1, DFnew dataset2) {
        // Append rows from dataset2 to dataset1
        for (int colIndex = 0; colIndex < dataset1.columns.size(); colIndex++) {
            ArrayList<Object> column1 = dataset1.getColumnByIndex(colIndex);
            ArrayList<Object> column2 = dataset2.getColumnByIndex(colIndex);

            column1.addAll(column2);  // Add all rows from column2 to column1
        }

        // Update the row count for dataset1
        dataset1.nrow += dataset2.nrow;
    }
    // Inner class to store the date pair
    public static class DatePair {
        Date startDate;
        Date endDate;

        public DatePair(Date startDate, Date endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
        }
    }
    // Helper method to create a map for direct lookups
    private Map<String, Map<Integer, DatePair>> createMapFromTable(DFnew table) {
        Map<String, Map<Integer, DatePair>> map = new HashMap<>();
        for (int i = 0; i < table.nrow; i++) {
            String identifiant = table.getColumn("IDENTIFIANT_CONTRAT").get(i).toString();
            String reference = table.getColumn("REFERENCE").get(i).toString().replace("\"", "");
            String combinedKey = identifiant + "_" + reference;

            Date startDate = (Date) table.getColumn("DATE DEBUT TARIF").get(i);
            Date endDate = (Date) table.getColumn("DATE FIN TARIF").get(i);

            map.computeIfAbsent(combinedKey, k -> new HashMap<>()).put(i, new DatePair(startDate, endDate));
        }
        return map;
    }
    // Helper function to look up and assign values
    public void populateTDB2() {
        // 1. Add coefficient columns to TDB2
        addCoefDatColumns();

        // 2. Create hashmaps for direct lookups
        Map<String, Map<Integer, DatePair>> tarifMap = createMapFromTable(grilleTarif);
        ArrayList<String> contrats = getColumn("IDENTIFIANT CONTRAT");
        ArrayList<String> refs = getColumn("REFERENCE");
        ArrayList<Date> dates = getColumn("DATE DEBUT PERIODE SOUSCRIPTION");
        ArrayList<Double> montants = getColumn("MONTANT PRIME ASSUREUR");

        int m = headers.indexOf("M");
        int mTarif = grilleTarif.headers.indexOf("M");

        ArrayList<Integer> rowsToTreat = new ArrayList<>();
        // 3. For each row in TDB2, find coefficients
        for (int rowIndex = 0; rowIndex < nrow; rowIndex++) {
            Double montant = montants.get(rowIndex);
            if (montant <= 0.0d) {
                rowsToTreat.add(0);
                continue;
            }
            rowsToTreat.add(1);

            String contrat = contrats.get(rowIndex);
            if (aChercherDansCoefPM.contains(contrat)) continue;

            String reference = refs.get(rowIndex).replace("\"", "").toUpperCase();
            String combinedKey = contrat + "_" + reference;

            Date dateDebutPeriode = dates.get(rowIndex);

            boolean found = lookupAndAssign(tarifMap, grilleTarif, combinedKey, rowIndex, dateDebutPeriode, m, mTarif);
            if (!found) {
                if (contrat.equals("ICIMWTV19")) {
                    found = lookupAndAssign(tarifMap, grilleTarif, contrat + "_" + reference + "_2", rowIndex, dateDebutPeriode, m, mTarif);
                } else if (combinedKey.equals("ICIMWTL18_114771")) {
                    found = lookupAndAssign(tarifMap, grilleTarif, "ICIMWTL18_114773", rowIndex, dateDebutPeriode, m, mTarif);
                } else if (combinedKey.equals("ICIELJVD15_3,66352E+12")) {
                    found = lookupAndAssign(tarifMap, grilleTarif, "ICIELJVD15_3663515117693", rowIndex, dateDebutPeriode, m, mTarif);
                } else if (combinedKey.equals("ICIELJVD15_3,6147E+12")) {
                    found = lookupAndAssign(tarifMap, grilleTarif, "ICIELJVD15_3614700540598", rowIndex, dateDebutPeriode, m, mTarif);
                }
            }

            // If not found in both, print warning
            if (!found) {
                System.out.println("Warning: Key " + combinedKey + " not found in GT pour la date: " + dateDebutPeriode);
            }
        }

        addColumn("aFaire",rowsToTreat,INT);
        removeColumn("REFERENCE");
    }
    public void fill0() {
        // Step 1: Find the column named "M"
        int columnIndexM = headers.indexOf("M");
        ArrayList<Date> columnDate = getColumn("DATE DEBUT PERIODE SOUSCRIPTION");
        ArrayList<String> columnContrat = getColumn("IDENTIFIANT CONTRAT");

        Date currentGroupDate = null;
        String currentContract = null;
        int maxIndexForCurrentGroup = columnIndexM;
        int startOfCurrentGroup = 0;

        for (int rowIndex = 0; rowIndex < nrow; rowIndex++) {
            Date rowDate = columnDate.get(rowIndex);
            String rowContract = columnContrat.get(rowIndex);

            // Check for change in date or contract, or end of the table
            if (currentGroupDate == null || !currentGroupDate.equals(rowDate) || !currentContract.equals(rowContract) || rowIndex == nrow - 1) {

                // If it's not the very first row
                if (currentGroupDate != null) {
                    // Process the detected group
                    for (int i = startOfCurrentGroup; i < rowIndex; i++) {
                        for (int col = columnIndexM; col <= maxIndexForCurrentGroup; col++) {
                            Float value = (Float) getColumnByIndex(col).get(i);
                            if (value == null) {
                                getColumnByIndex(col).set(i, 0.0f);
                            }
                        }
                    }
                    // Reset the max column index for the next group
                    maxIndexForCurrentGroup = columnIndexM;
                }

                // Set the new group's start
                startOfCurrentGroup = rowIndex;
                currentGroupDate = rowDate;
                currentContract = rowContract;
            }

            // Find the last non-null column for current row
            for (int col = columnIndexM; col < headers.size()-1; col++) {
                Float value = (Float) getColumnByIndex(col).get(rowIndex);
                if (value != null) {
                    maxIndexForCurrentGroup = Math.max(maxIndexForCurrentGroup, col);
                }
            }
        }
    }
    public int findLastNonNullColumnFromM() {
        // Find the column named "M"
        int columnIndexM = headers.indexOf("M");
        if (columnIndexM == -1) {
            throw new IllegalArgumentException("Column with header: M not found.");
        }

        int lastNonNullColumn = columnIndexM;

        // Iterate over columns starting from "M"
        for (int col = columnIndexM; col < headers.size(); col++) {
            if (!columns.get(col).type.equals(FLT)) continue;
            ArrayList<Float> columnData = getColumnByIndex(col);  // Assuming columns after M are of type Float. Adjust if needed.

            // Iterate over rows for the current column
            for (Float value : columnData) {
                if (value != null && value != 0f) {
                    lastNonNullColumn = col;  // Update the last non-null column
                    break;  // Move to the next column since we found a non-null value
                }
            }
        }

        return lastNonNullColumn - columnIndexM;
    }

    private boolean lookupAndAssign(Map<String, Map<Integer, DatePair>> tarifMap, DFnew tarifTable,
                                    String key, int rowIndex, Date dateDebutPeriode, int m, int mTarif) {
        Map<Integer, DatePair> rowDatePairs = tarifMap.get(key);
        if (rowDatePairs != null) {
            for (Map.Entry<Integer, DatePair> entry : rowDatePairs.entrySet()) {
                DatePair datePair = entry.getValue();
                if (!dateDebutPeriode.before(datePair.startDate) && !dateDebutPeriode.after(datePair.endDate)) {
                    int tarifRow = entry.getKey();
                    for (int colIndex = m, colTarifIndex = mTarif; colIndex <= m+lastM; colIndex++,colTarifIndex++) {
                        Float value = (Float) tarifTable.getColumnByIndex(colTarifIndex).get(tarifRow);
                        if (value == 0.0f) {
                            value = null;  // Set to null if the value is 0
                        }
                        getColumnByIndex(colIndex).set(rowIndex, value);
                    }
//                    Date value1 = (Date) tarifTable.getColumnByIndex(mTarif+201).get(tarifRow);
//                    getColumnByIndex(m+201).set(rowIndex, value1);
//                    Date value2 = (Date) tarifTable.getColumnByIndex(mTarif+202).get(tarifRow);
//                    getColumnByIndex(m+202).set(rowIndex, value2);

                    return true; // Successfully found and assigned from grilleTarif
                }
            }
        }
        return false;
    }

    public Map<Integer, List<Float>> populateCoefficientMapOld(DFnew externalTable) throws ParseException {
        Map<Integer, List<Float>> coefficientMap = new HashMap<>();

        List<String> contracts = getColumn("Contrat");
        List<Date> datePeriodes = getColumn("Date Periode");
        List<Double> montants = getColumn("MONTANT TOTAL PRIME ASSUREUR");
        List<String> fluxs = getColumn("Flux"); // Track values in the "Flux" column

        List<String> extContracts = externalTable.getColumn("IDENTIFIANT CONTRAT");
        List<Date> extStartDate = externalTable.getColumn("DATE DEBUT PERIODE SOUSCRIPTION");

        ArrayList<String> contratsPM = coefPM.getColumn("CONTRAT");
        ArrayList<Date> datesPM = coefPM.getColumn("DATE");

        int m = externalTable.headers.indexOf("M");
        int mPM = coefPM.headers.indexOf("M");

        List<ArrayList<Float>> lastThreeCoefficients = new ArrayList<>(); // Track the last three coefficient arrays

        for (int i = 0; i < contracts.size(); i++) {
            Double montant = montants.get(i);
            if (montant <= 0.0d) continue;

            String contract = contracts.get(i);
            Date datePeriode = datePeriodes.get(i);

            if (aChercherDansCoefPM.contains(contract)) {
                for (int j = 0; j < contratsPM.size(); j++) {
                    if (contract.equals(contratsPM.get(j)) && datePeriode.equals(datesPM.get(j))) {
                        ArrayList<Float> coefficients = new ArrayList<>();
                        for (int k = mPM; k <= mPM+200; k++) {
                            Float coefValue = (Float) coefPM.getColumnByIndex(k).get(j);
                            coefficients.add(coefValue);
                        }
                        coefficientMap.put(i, FloatArrayDictionary.getOrAdd(coefficients));
                        break;
                    }
                }
                continue;
            }

            List<ArrayList<Float>> matchingCoefficients = new ArrayList<>();

            for (int j = 0; j < extContracts.size(); j++) {
                if (contract.equals(extContracts.get(j)) && datePeriode.equals(extStartDate.get(j))) {
                    ArrayList<Float> coefficients = new ArrayList<>();
                    for (int k = m; k <= m+200; k++) {
                        Float coefValue = (Float) externalTable.getColumnByIndex(k).get(j);
                        coefficients.add(coefValue);
                    }
                    matchingCoefficients.add(coefficients);
                }
            }

            if (matchingCoefficients.isEmpty()) {
                if (!"BU".equals(fluxs.get(i))) {
                    if (!lastThreeCoefficients.isEmpty()) {
                        ArrayList<Float> averagedCoefficients = calculateAverageCoefficients(lastThreeCoefficients);
                        coefficientMap.put(i, FloatArrayDictionary.getOrAdd(averagedCoefficients));
                        // Store the freshly calculated averaged coefficients to the last three coefficients
                        if (lastThreeCoefficients.size() == 3) {
                            lastThreeCoefficients.remove(0);  // Remove the oldest coefficient array
                        }
                        lastThreeCoefficients.add(averagedCoefficients);
                    } else {
                        System.out.println("Warning: cant calculate coef previ for Contrat: " + contract + " and Date Periode: " + datePeriode);
                    }
                } else {
                    System.out.println("Warning: No match found for Contrat: " + contract + " and Date Periode: " + datePeriode);
                }
            } else {
                ArrayList<Float> averagedCoefficients = calculateAverageCoefficients(matchingCoefficients);
                coefficientMap.put(i, FloatArrayDictionary.getOrAdd(averagedCoefficients));
                // Ensure the lastThreeCoefficients list never exceeds a size of 3
                if (lastThreeCoefficients.size() == 3) {
                    lastThreeCoefficients.remove(0);
                }
                lastThreeCoefficients.add(FloatArrayDictionary.getOrAdd(averagedCoefficients));
            }
        }
        return coefficientMap;
    }
    public void populateCoefficientMap(DFnew externalTable) throws ParseException {

        List<String> contracts = getColumn("Contrat");
        List<Date> datePeriodes = getColumn("Date Periode");
        List<Double> montants = getColumn("MONTANT TOTAL PRIME ASSUREUR");
        List<String> fluxs = getColumn("Flux"); // Track values in the "Flux" column

        List<String> extContracts = externalTable.getColumn("IDENTIFIANT CONTRAT");
        List<Date> extStartDate = externalTable.getColumn("DATE DEBUT PERIODE SOUSCRIPTION");
        List<Double> primeColumn = externalTable.getColumn("MONTANT PRIME ASSUREUR");
        List<Integer> bool = externalTable.getColumn("aFaire");

        ArrayList<String> contratsPM = coefPM.getColumn("CONTRAT");
        ArrayList<Date> datesPM = coefPM.getColumn("DATE");

        int m = externalTable.headers.indexOf("M");
        int mPM = coefPM.headers.indexOf("M");

        List<ArrayList<Float>> lastThreeCoefficients = new ArrayList<>(); // Track the last three coefficient arrays
        String previousContract = null;  // Store the previous contract outside the loop

        int jPM = 0;
        int jExt = 0;

        for (int i = 0; i < contracts.size(); i++) {
            Double montant = montants.get(i);
            if (montant <= 0.0d) continue;

            String contract = contracts.get(i);
            Date datePeriode = datePeriodes.get(i);
            // If we have a new contract, reinitialize lastThreeCoefficients
            if (previousContract == null || !previousContract.equals(contract)) {
                lastThreeCoefficients.clear();
                previousContract = contract;  // Update the previous contract
            }

            // Check if the contract is in the HashSet
            if (aChercherDansCoefPM.contains(contract)) {
                // Searching in contratsPM
                while (jPM < contratsPM.size() && (contratsPM.get(jPM).compareTo(contract) < 0 || (contratsPM.get(jPM).equals(contract) && datesPM.get(jPM).compareTo(datePeriode) < 0))) {
                    jPM++;
                }

                if (jPM < contratsPM.size() && contratsPM.get(jPM).equals(contract) && datesPM.get(jPM).equals(datePeriode)) {
                    ArrayList<Float> coefficients = new ArrayList<>();
                    for (int k = mPM; k <= mPM+lastM; k++) {
                        Float coefValue = (Float) coefPM.getColumnByIndex(k).get(jPM);
                        coefficients.add(coefValue);
                    }
                    mapCoefAQ.put(i, FloatArrayDictionary.getOrAdd(coefficients));
                    continue;  // Skip the rest of the loop iteration
                }
            }

            List<Pair<ArrayList<Float>, Double>> matchingCoefficientsWithPrime = new ArrayList<>();

            while (jExt < extContracts.size() && (extContracts.get(jExt).compareTo(contract) < 0 || (extContracts.get(jExt).equals(contract) && extStartDate.get(jExt).compareTo(datePeriode) < 0))) {
                jExt++;
            }
            // Searching in extContracts for multiple matching rows
            while (jExt < extContracts.size() && extContracts.get(jExt).equals(contract) && extStartDate.get(jExt).equals(datePeriode)) {
                if (bool.get(jExt) == 0) { // montant <= 0
                    jExt++;
                    continue;
                }

                ArrayList<Float> coefficients = new ArrayList<>();
                for (int k = m; k <= m+lastM; k++) {
                    Float coefValue = (Float) externalTable.getColumnByIndex(k).get(jExt);
                    coefficients.add(coefValue);
                }
                Double prime = primeColumn.get(jExt);
                matchingCoefficientsWithPrime.add(new Pair<>(coefficients, prime));
                jExt++; // Move to next row in the external table
            }

            if (!matchingCoefficientsWithPrime.isEmpty()) {
                ArrayList<Float> averagedCoefficients = calculateAverageCoefficientsWithPrime(matchingCoefficientsWithPrime);
                mapCoefAQ.put(i, FloatArrayDictionary.getOrAdd(averagedCoefficients));

                if (lastThreeCoefficients.size() == 3) {
                    lastThreeCoefficients.remove(0);
                }
                lastThreeCoefficients.add(FloatArrayDictionary.getOrAdd(averagedCoefficients));
            } else {
                // Logic for unmatched contract
                    if (!lastThreeCoefficients.isEmpty()) {
                        ArrayList<Float> averagedCoefficients = calculateAverageCoefficients(lastThreeCoefficients);
                        mapCoefAQ.put(i, FloatArrayDictionary.getOrAdd(averagedCoefficients));

                        // Store the freshly calculated averaged coefficients to the last three coefficients
                        if (lastThreeCoefficients.size() == 3) {
                            lastThreeCoefficients.remove(0);  // Remove the oldest coefficient array
                        }
                        lastThreeCoefficients.add(averagedCoefficients);
                    } else {
                        System.out.println("Warning: cant calculate coef for Contrat, no data: " + contract + " and Date Periode: " + datePeriode);
                    }
                if ("BU".equals(fluxs.get(i))) {
                    System.out.println("Warning: No match found for BU Contrat: " + contract + " and Date Periode: " + datePeriode);
                }
            }
        }
    }
    private ArrayList<Float> calculateAverageCoefficients(List<ArrayList<Float>> coefficientsList) {
        ArrayList<Float> averagedCoefficients = new ArrayList<>();
        int dim = coefficientsList.size();
        for (int mIter = 0; mIter <= lastM; mIter++) {
            float sum = 0;
            for (ArrayList<Float> coefList : coefficientsList) {
                Float value = coefList.get(mIter);
                if (value != null) {
                    sum += value;
                }
            }
            averagedCoefficients.add(sum == 0 ? null : sum / dim);
        }
        return averagedCoefficients;
    }
    private ArrayList<Float> calculateAverageCoefficientsWithPrime(List<Pair<ArrayList<Float>, Double>> coefficientsWithPrimeList) {
        ArrayList<Float> weightedAverageCoefficients = new ArrayList<>();

        for (int mIter = 0; mIter <= lastM; mIter++) {
            float weightedSum = 0;
            float totalPrime = 0;

            for (Pair<ArrayList<Float>, Double> pair : coefficientsWithPrimeList) {
                ArrayList<Float> coefList = pair.getKey();
                Double prime = pair.getValue();

                Float value = coefList.get(mIter);
                if (value != null && prime != null) {
                    weightedSum += (float) (value * prime);
                    totalPrime += prime;
                }
            }
            weightedAverageCoefficients.add(totalPrime == 0 || weightedSum == 0 ? null : weightedSum / totalPrime);
        }
        return weightedAverageCoefficients;
    }

    public static class Pair<K, V> {
        private final K key;
        private final V value;

        public Pair(K key, V value) {
            this.key = key;
            this.value = value;
        }

        public K getKey() {
            return key;
        }

        public V getValue() {
            return value;
        }
    }
    public static void writeMapAndEstimateToCSV(Map<Integer, ArrayList<Float>> mapCoefAQ, DFnew estimate, String outputPath) throws IOException {
        SimpleDateFormat dateFormatter = new SimpleDateFormat("dd/MM/yyyy");
        DecimalFormat floatFormatter = new DecimalFormat("0.#######"); // Up to 7 decimals

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath), StandardCharsets.UTF_8))) {
            // Write BOM (Byte Order Mark) for UTF-8 Encoding
            writer.write("\uFEFF");

            // Write CSV headers
            writer.write("Contrat;Date Periode");
            for (int i = 0; i <= lastM; i++) {  // Iterate from 0 to lastM inclusive
                writer.write(";M" + (i == 0 ? "" : "+" + i));
            }
            writer.newLine();

            for (Map.Entry<Integer, ArrayList<Float>> entry : mapCoefAQ.entrySet()) {
                Integer rowIndex = entry.getKey();
                List<Float> coefficients = entry.getValue();

                // Fetch "Contrat" and "Date Periode" columns from the row using rowIndex
                String contrat = estimate.getColumn("Contrat").get(rowIndex).toString();
                Date rawDate = (Date) estimate.getColumn("Date Periode").get(rowIndex);
                String datePeriode = dateFormatter.format(rawDate);

                // Write the data to the CSV file
                writer.write(contrat + ";" + datePeriode);

                for (Float coef : coefficients) {
                    if (coef == null) {
                        writer.write(";");
                    } else {
                        writer.write(";" + floatFormatter.format(coef).replace('.', ','));
                    }
                }
                writer.newLine();
            }
        }
    }

    protected void saveTDBtoCSVprecision(String path) throws IOException {
                try (BufferedWriter writer = Files.newBufferedWriter(Path.of(path), StandardCharsets.UTF_8)) {
            // Write BOM for UTF-8
            writer.write('\ufeff');

            // If there are subheaders, write them
            if (subheaders != null && !subheaders.isEmpty()) {
                writer.write(subheaders.stream().map(sh -> sh != null ? sh : "").collect(Collectors.joining(";")));
                writer.newLine();
            }

            // Write headers
            writer.write(String.join(";", headers));
            writer.newLine();

            // Write data
            for (int i = 0; i < nrow; i++) {
                List<String> row = getRow(i).stream().map(item -> {
                    if (item instanceof Float) {
                        return String.format("%.7f", item);
                    }
                    if (item instanceof Date) {
                        return DATE_FORMAT.format((Date) item);
                    }
                    return item != null ? item.toString() : "";
                }).collect(Collectors.toList());

                writer.write(String.join(";", row));
                writer.newLine();
            }
        }
    }
    public static boolean areListsSummingToOne(Map<Integer, ArrayList<Float>> mapCoefAQ, Estimatenew est) {
        final float EPSILON = 1e-5f; // precision
        boolean allListsSumToOne = true; // to track if all lists sum to one

        for (Map.Entry<Integer, ArrayList<Float>> entry : mapCoefAQ.entrySet()) {
            float sum = 0.0f;
            for (Float value : entry.getValue()) {
                if (value != null) {
                    sum += value;
                }
            }

            if (Math.abs(sum - 1.0f) > EPSILON) {
                System.out.println("Bad key: " + entry.getKey() + " " + sum); // print the key for which the list doesn't sum to one
                System.out.println(est.getRow(entry.getKey()));
                allListsSumToOne = false;
            }
        }

        return allListsSumToOne;
    }

    public static void repairReferenceGT() {
        List<String> referenceColumn = grilleTarif.getColumn("REFERENCE");
        List<String> contratColumn = grilleTarif.getColumn("IDENTIFIANT_CONTRAT");

        for (int i = 0; i < referenceColumn.size(); i++) {
            String contrat = contratColumn.get(i);
            String currentValue = referenceColumn.get(i);
            if (currentValue != null) {
                referenceColumn.set(i, currentValue.toUpperCase());
                if (contrat.equals("ICIMOCN22")) {
                    referenceColumn.set(i, extractUpToLast000(currentValue).toUpperCase());
                }
            }
        }
    }
    public static String extractUpToLast000(String input) {
        int lastIndex = input.lastIndexOf("000");

        if (lastIndex != -1) {
            return input.substring(0, lastIndex + 3);  // +3 to include "000" in the result
        }

        // If "000" is not found in the string, return the input as is (or you can return null or any default value)
        return input;
    }

    // CSV WRITER
    protected void saveToCsvWithSuffix(String suffix) throws IOException {
        Path originalPath = Paths.get(this.path);
        String filenameWithoutExtension = originalPath.getFileName().toString().replaceFirst("[.][^.]+$", "");
        String fileExtension = originalPath.toString().substring(originalPath.toString().lastIndexOf(".") + 1);
//        String newPath = originalPath.getParent() + "/" + filenameWithoutExtension + suffix + "." + fileExtension;
        String newPath = outputFolder + filenameWithoutExtension + suffix + "." + fileExtension;

        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(newPath), StandardCharsets.UTF_8)) {
            // Write BOM for UTF-8
            writer.write('\ufeff');

            // If there are subheaders, write them
            if (subheaders != null && !subheaders.isEmpty()) {
                writer.write(subheaders.stream().map(sh -> sh != null ? sh : "").collect(Collectors.joining(";")));
                writer.newLine();
            }

            // Write headers
            writer.write(String.join(";", headers));
            writer.newLine();

            // Write data
            for (int i = 0; i < nrow; i++) {
                List<String> row = getRow(i).stream().map(item -> {
                    if (item instanceof Date) {
                        return DATE_FORMAT.format((Date) item);
                    } else if (item instanceof Double) {
                        return DECIMAL_FORMAT.format(item).replace('.', ','); // Replace period with comma
                    }
                    return item != null ? item.toString() : "";
                }).collect(Collectors.toList());

                writer.write(String.join(";", row));
                writer.newLine();
            }
        }
    }
    private void writeLine(BufferedWriter writer, List<?> values) throws IOException {
        StringBuilder sb = new StringBuilder();

        boolean firstValue = true;
        for (Object value : values) {
            if (!firstValue) {
                sb.append(";");
            }

            if (value != null) {
                sb.append(value);
            }

            firstValue = false;
        }

        writer.write(sb.toString());
        writer.newLine();
    }
}
