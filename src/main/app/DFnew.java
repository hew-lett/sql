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

    private final ArrayList<Column<?>> columns;
    private final ArrayList<String> headers;
    private ArrayList<String> subheaders;
    private ArrayList<String> columnNamesToRead;
    private ArrayList<ColTypes> columnTypes;
    private ArrayList<String> columnNamesAttributed;
    private static final Charset encodingDefault = UTF_8;
    private static final char delimDefault = ';';
    private static final SimpleDateFormat dateFormatDefault = new SimpleDateFormat("dd/MM/yyyy");
    private static final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");  // For example: 20230907_125959

    public static void main(String[] args) throws IOException, ParseException {
        Stopwatch stopwatch = new Stopwatch();
        stopwatch.start();

        DFnew df = new DFnew(wd + "PB Micromania.csv",';',false,"PB");
        df.print();
        stopwatch.printElapsedTime();
    }
    public DFnew(String csvFilePath, char delim, boolean toLower, String refFichier) throws IOException, ParseException {
        FileConfig config = FileConfig.getInstance();
        if (refFichier != null) {
            columnNamesToRead = config.getColumnNamesToRead(refFichier);
            columnTypes = config.getColumnTypes(refFichier);
            columnNamesAttributed = config.getColumnNamesAttributed(refFichier);
        }



        columns = new ArrayList<>();
        headers = new ArrayList<>();

        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true, delim);
        CsvParser parser = new CsvParser(settings);

        List<String[]> allRows = parser.parseAll(new FileReader(csvFilePath, encodingDefault));

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

        if (columnNamesToRead != null && columnTypes != null && columnNamesToRead.size() != columnTypes.size()) {
            throw new IllegalArgumentException("Mismatch between column names to read and column types provided.");
        }
    }
    public DFnew(String xlsxFilePath, String sheetName, boolean toLower, ArrayList<String> columnNamesToRead, ArrayList<ColTypes> columnTypes, ArrayList<String> columnNamesAttributed) throws IOException, ParseException {
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

        Row headerRowPOI = sheet.getRow(0);
        if (headerRowPOI == null) {
            throw new IllegalArgumentException("XLSX sheet is empty or missing header row!");
        }

        List<String> headerList = new ArrayList<>();
        headerRowPOI.forEach(cell -> headerList.add(cell.toString()));

        if (toLower) {
            headerList.replaceAll(String::toLowerCase);
        }

        for (int i = 0; i < headerList.size(); i++) {
            String header = headerList.get(i);
            if (columnNamesToRead == null || columnNamesToRead.contains(header)) {
                headers.add(columnNamesAttributed != null ? columnNamesAttributed.get(i) : header);

                ArrayList<Object> colData = new ArrayList<>();
                ColTypes colType = (columnTypes == null) ? ColTypes.STR : columnTypes.get(i);

                for (int j = 1; j <= sheet.getLastRowNum(); j++) {
                    Row currentRow = sheet.getRow(j);
                    if (currentRow != null) {
                        Cell cell = currentRow.getCell(i);
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
    private void validateColumnInputs(ArrayList<String> columnNamesToRead, ArrayList<ColTypes> columnTypes, ArrayList<String> columnNamesAttributed) {
        int size = -1;
        if (columnNamesToRead != null) {
            size = columnNamesToRead.size();
        }
        if (columnTypes != null) {
            if (size != -1 && size != columnTypes.size()) {
                throw new IllegalArgumentException("Mismatch between sizes of columnNamesToRead and columnTypes.");
            }
            size = columnTypes.size();
        }
        if (columnNamesAttributed != null) {
            if (size != -1 && size != columnNamesAttributed.size()) {
                throw new IllegalArgumentException("Mismatch between sizes of columnNamesToRead/columnTypes and columnNamesAttributed.");
            }
        }
    }
    public Object getCell(String cell, ColTypes type) throws ParseException {
        return formatCell(cell, type);
    }
    public Object getLowerCell(String cell, ColTypes type) throws ParseException {
        return formatCell(cell.toLowerCase(), type);
    }
    private Object formatCell(String cell, ColTypes type) throws ParseException {
        return switch (type) {
            case STR -> cell;
            case DAT -> dateFormatDefault.parse(cell);
            case DBL -> Double.parseDouble(cell.replace(',', '.'));
            case FLT -> Float.parseFloat(cell.replace(',', '.'));
            default -> null;
        };
    }

    // COLUMNS
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
        SKP
    }
    private static class Column<T> {
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


}
