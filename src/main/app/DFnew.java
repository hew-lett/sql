package main.app;

import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
import org.bouncycastle.util.encoders.UTF8;

import static java.lang.Math.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static main.app.App.*;
import static main.app.DF.Col_types.*;
import static main.app.Synthese.roundToFourDecimals;
import static main.app.Synthese.roundToTwoDecimals;
public class DFnew {
    public static final String wd = "E:/202305/wd/";
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
    public static DFnew coefsAQ;
    public static DFnew mapStatuts;
    static {
        try {
            PB = new DFnew(wd + "PB Micromania.csv",';',false,"PB");
            refProg = new DFnew(wd + "Référentiel programmes.csv",';',false,"refProg");
            refCols = new DFnew(wd + "refRenta.xlsx","ref_cols",false,"refCols");
            refSource = new DFnew(wd + "refRenta.xlsx","source",false,"refSource");
            mapping = new DFnew(wd + "mapping.xlsx","Mapping entrant sinistres",false,"mapping");
            SPprevi = new DFnew(wd + "S SUR P PREVI 2023_01_18.xlsx","Feuil1",false,"SPprevi");
            coefsAQ = new DFnew(wd + "TDB Part 2_Hors France_populated_coef.csv",';',false,"coefsAQ");
            mapStatuts = new DFnew(wd + "mapStatuts.csv",';',false,"mapStatuts");
        } catch (IOException | ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public DFnew() {

    }

    public static void main(String[] args) throws IOException, ParseException {
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
        }
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
                // Handle the case where the expected header doesn't exist in the data.
                // Either skip, log an error or throw an exception based on your requirements.
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
            return null;
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
            return null;
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
    public void populateStatutMap() {
        ArrayList<String> statuts = getColumn("Statut");
        ArrayList<String> statutsReferentiel = getColumn("Statut referentiel");

        for (int i = 0; i < statuts.size(); i++) {
            String statut = statuts.get(i);
            String referentiel = statutsReferentiel.get(i);

            // Populate the map. If the key already exists, it won't overwrite the value.
            statutMap.putIfAbsent(statut, referentiel);
        }
    }
    public void mapPoliceToPB() {
        int identifiantIndex = headers.indexOf("Contrat");
        int indexPB = headers.indexOf("PB");
        int dateIndex = headers.indexOf("Date");

        // Error handling if columns are not found
        if (identifiantIndex == -1 || indexPB == -1 || dateIndex == -1) {
            throw new IllegalArgumentException("Required columns not found in header.");
        }

        SimpleDateFormat sdfOutput = new SimpleDateFormat("MM-yyyy");

        for (int i = 0; i < nrow; i++) {
            String identifiant = (String) getColumnByIndex(identifiantIndex).get(i);
            Date dateValue = (Date) getColumnByIndex(dateIndex).get(i);
            String formattedDate = sdfOutput.format(dateValue);
            Double PBv = Double.parseDouble((String) getColumnByIndex(indexPB).get(i));

            mapPB
                    .computeIfAbsent(identifiant, k -> new HashMap<>())
                    .put(formattedDate, PBv);
        }
    }
    public void mapPoliceToSPPrevi() {
        int identifiantIndex = headers.indexOf("IDENTIFIANT CONTRAT");
        int spPreviIndex = headers.indexOf("S/P PREVI SANS ICI");
        int anneesIndex = headers.indexOf("ANNEES");

        // Error handling if columns are not found
        if (identifiantIndex == -1 || spPreviIndex == -1 || anneesIndex == -1) {
            throw new IllegalArgumentException("Required columns not found in header.");
        }

        for (int i = 0; i < nrow; i++) {
            String identifiant = (String) getColumnByIndex(identifiantIndex).get(i);
            Double annee = (Double) getColumnByIndex(anneesIndex).get(i);
            Double spPrevi = (Double) getColumnByIndex(spPreviIndex).get(i);

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

        return result;
    }
}
