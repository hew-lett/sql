package main.app;

import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import org.bouncycastle.util.encoders.UTF8;

import static java.lang.Math.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static main.app.App.*;
import static main.app.DFnew.ColTypes.*;
import static main.app.Synthese.roundToFourDecimals;
import static main.app.Synthese.roundToTwoDecimals;
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
    static {
        try {
            PB = new DFnew(wd + "PB Micromania.csv",';',false,"PB");
            refProg = new DFnew(wd + "ref_Programmes.csv",';',false,"refProg");
            refCols = new DFnew(wd + "ref_Renta.xlsx","ref_cols",false,"refCols");
            refSource = new DFnew(wd + "ref_Renta.xlsx","source",false,"refSource");
            mapping = new DFnew(wd + "mapping.xlsx","Mapping entrant sinistres",false,"mapping");
            SPprevi = new DFnew(wd + "S SUR P PREVI 2023_01_18.xlsx","Feuil1",false,"SPprevi");
            mapStatuts = new DFnew(wd + "statuts.xlsx","Statuts",false,"mapStatuts");
            mergeRowsOnContratRefProg();
            populateGlobalStatutMap();
            mapPoliceToPB();
            mapPoliceToSPPrevi();
        } catch (IOException | ParseException e) {
            throw new RuntimeException(e);
        }
    }
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy");
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##"); // Adjust the number of # after the point for precision.
    public DFnew() {

    }

    public static void main(String[] args) throws IOException, ParseException, Exception {
        Stopwatch stopwatch = new Stopwatch();
        stopwatch.start();

        DFnew SPprevi = new DFnew(wd + "S SUR P PREVI 2023_01_18.xlsx","Feuil1",false,"SPprevi");
        SPprevi.print();
//        mapStatuts.populateStatutMap();

        stopwatch.printElapsedTime();
    }
    public DFnew(String csvFilePath, char delim, boolean toLower, String refFichier) throws IOException, ParseException {
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
    public Object getCell(String cell, ColTypes type) throws ParseException {
        if (cell == null) {
            if (type == DBL || type == FLT || type == INT) {
                return 0.0;
            } else {
                return null;
            }
        }
        return formatCell(cell, type);
    }
    public Object getLowerCell(String cell, ColTypes type) throws ParseException {
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
                case DBL -> Double.parseDouble(cell.replace(',', '.'));
                case FLT -> Float.parseFloat(cell.replace(',', '.'));
                case INT -> (int) Double.parseDouble(cell);
                default -> null;
            };
        } catch (NumberFormatException e) {
            // Handle other potential parse errors for numerical types
            return 0.0;
        }
    }

    // COLUMNS
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
        SKP
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
    public void setColumns(ArrayList<Column<?>> columns) {
        this.columns = columns;
    }
    public void setHeaders(ArrayList<String> headers) {
        this.headers = headers;
    }
    public <T> void addColumn(String header, ArrayList<T> columnData, ColTypes type) {
        columns.add(new Column<T>(columnData, type));
        headers.add(header);
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


    // CSV WRITER
    protected void saveToCsvWithSuffix(String suffix) throws IOException {
        Path originalPath = Paths.get(this.path);
        String filenameWithoutExtension = originalPath.getFileName().toString().replaceFirst("[.][^.]+$", "");
        String fileExtension = originalPath.toString().substring(originalPath.toString().lastIndexOf(".") + 1);
        String newPath = originalPath.getParent() + "/" + filenameWithoutExtension + suffix + "." + fileExtension;

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
