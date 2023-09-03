package main.app;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static java.lang.Math.min;
import static main.app.App.wd;

public class Synthese {
    private final ArrayList<Column<?>> columns;
    private final ArrayList<String> headers;
    private ArrayList<String> subheaders;
    public static SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
    public static char delim = ';';
    public static Synthese refProg = new Synthese(wd+"Référentiel programmes.csv",delim,false,true,true);
    private List<Integer> refMapping;
    public static void main(String[] args) {
        Stopwatch stopwatch = new Stopwatch();
        stopwatch.start();

//        List<String> columnsToCheck = Arrays.asList("A", "AD", "AU", "AX", "EKV", "ETN", "GCH", "GTU", "GTV", "GTW", "GUN", "GUO", "GUP", "HDJ", "HDN", "HDO", "HDM", "M", "RZ", "S");
//        wf.printColumnHeaders(columnsToCheck);
        Synthese wf = new Synthese(wd+"TDB estimate par gestionnaire/SPB Italie_extended.csv",delim,false,true,true);
        refProg.analyzeDataframe();
        Synthese synt = new Synthese(wf);
        synt.print();
        stopwatch.printElapsedTime();
    }
    public Synthese(String path, char delim, boolean toLower, boolean subHeader, boolean detectColtypes) {
        headers = new ArrayList<>();
        columns = new ArrayList<>();

        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true, delim);
        settings.trimValues(true);
        settings.setMaxColumns(6000);
        settings.setMaxCharsPerColumn(256);

        try (Reader inputReader = new InputStreamReader(Files.newInputStream(new File(path).toPath()), StandardCharsets.UTF_8)) {
            CsvParser parser = new CsvParser(settings);
            List<String[]> parsedRows = parser.parseAll(inputReader);
            Iterator<String[]> rows = parsedRows.iterator();

            // Handle headers
            String[] headerRow = rows.next();
            for (String header : headerRow) {
                if (header == null) {
                    headers.add("");
                } else {
                    headers.add(toLower ? header.toLowerCase() : header);
                }
            }

            // Handle subheaders
            if (subHeader && rows.hasNext()) {
                subheaders = new ArrayList<>();
                String[] subHeaderRow = rows.next();
                for (String subHeaderX : subHeaderRow) {
                    if (subHeaderX == null) {
                        subheaders.add("");
                    } else {
                        subheaders.add(toLower ? subHeaderX.toLowerCase() : subHeaderX);
                    }
                }
            }

            // Determine column types
            if (detectColtypes && rows.hasNext()) {
                String[] typeRow = rows.next();

                for (int i = 0; i < typeRow.length; i++) {
                    String header = headers.get(i);
                    String cell = typeRow[i];

                    // Check if the header matches the "06-2020" pattern
                    if (header.matches("\\d{2}-\\d{4}")) {
                        columns.add(new Column<>(new ArrayList<>(), ColTypes.DBL));
                    } else if (cell == null) {
                        columns.add(new Column<>(new ArrayList<>(), ColTypes.STR));
                    } else if (cell.matches("\\d{2}/\\d{2}/\\d{4}")) {
                        columns.add(new Column<>(new ArrayList<>(), ColTypes.DAT));
                    } else if (cell.matches("[\\d.,\\s]+")) {
                        columns.add(new Column<>(new ArrayList<>(), ColTypes.DBL));
                    } else {
                        columns.add(new Column<>(new ArrayList<>(), ColTypes.STR));
                    }
                }

                // Process this type row for actual data too
                addRowToColumns(typeRow, toLower);
            } else {
                for (int i = 0; i < headers.size(); i++) {
                    columns.add(new Column<>(new ArrayList<>(), ColTypes.STR));
                }
            }



            while (rows.hasNext()) {
                String[] parsedRow = rows.next();
                addRowToColumns(parsedRow, toLower);
            }

            dropSKPColumns();

        } catch (IOException ignored) {
            // Handle the exception appropriately
        }
    }
    public Synthese(Synthese external) {
        headers = new ArrayList<>();
        columns = new ArrayList<>();
        refMapping = new ArrayList<>();

        // Use LinkedHashMap to preserve the order of insertion
        Map<String, String> columnsToExtract = new LinkedHashMap<>();
        columnsToExtract.put("GESTIONNAIRE 1", "Gestionnaire");
        columnsToExtract.put("LIBELLE DISTRIBUTEUR", "Distributeur");
        columnsToExtract.put("Contrat", "Contrat");
        columnsToExtract.put("Date Periode", "Date Periode");

        // Get the Contrat data first and generate its mapping to refProg
        ArrayList<String> contratData = external.getColumn("Contrat");
        for (String contrat : contratData) {
            int refIndex = refProg.getColumn("N°CONTRAT").indexOf(contrat);
            refMapping.add(refIndex);
        }

        // Use the mapping to generate the Assureur column
        ArrayList<String> assureurData = new ArrayList<>();
        for (int refIndex : refMapping) {
            if (refIndex != -1) {
                assureurData.add((String) refProg.getColumn("COMPAGNIE").get(refIndex));
            } else {
                assureurData.add("");  // or some default value if not found
            }
        }

        // Insert Assureur before Contrat
        this.addColumn("Assureur", assureurData, ColTypes.STR);

        // Iterate over the specified columns and populate the new Synthese object
        for (Map.Entry<String, String> entry : columnsToExtract.entrySet()) {
            String externalColumnName = entry.getKey();
            String newColumnName = entry.getValue();

            // Extract the data from the external Synthese object
            ArrayList<String> externalData = external.getColumn(externalColumnName);

            // Add the extracted data to the new Synthese object
            this.addColumn(newColumnName, new ArrayList<>(externalData), ColTypes.STR);
        }
    }

    private void mapToRefProg() {
        ArrayList<String> contrats = getColumn("Contrat");
        ArrayList<String> refContrats = refProg.getColumn("N°CONTRAT");

        for (String contrat : contrats) {
            int refIndex = refContrats.indexOf(contrat);
            refMapping.add(refIndex);
        }
    }
    private String getAssureurFromRefProg(int rowIndex) {
        int refRowIndex = refMapping.get(rowIndex);
        if (refRowIndex != -1) {
            return (String) refProg.getColumn("COMPAGNIE").get(refRowIndex);
        } else {
            return "";  // or some default value if not found
        }
    }


    @SuppressWarnings("unchecked")
    private <T> void addRowToColumns(String[] row, boolean toLower) {
        for (int i = 0; i < min(row.length, columns.size()); i++) {
            Column<T> col = (Column<T>) columns.get(i);
            T cell = (T) getCellOfType(row[i], col.getType(), toLower);
            col.getData().add(cell);
        }
    }
    private Object getCellOfType(String cell, ColTypes type, boolean toLower) {
        switch (type) {
            case STR -> {
                return cell == null ? "" : (toLower ? cell.toLowerCase().trim() : cell.trim());
            }
            case DBL -> {
                if (cell == null) return 0d;
                try {
                    return Double.parseDouble(cell.replace(",", ".").replace(" €", ""));
                } catch (NumberFormatException ignored) {
                    return 0d;
                }
            }
            case DAT -> {
                if (cell == null) return null; // Handle null case appropriately
                try {
                    return dateFormat.parse(cell);
                } catch (ParseException e) {
                    return null; // Handle date parsing exception appropriately
                }
            }
            default -> {
                return "";
            }
        }
    }
    private void dropSKPColumns() {
        for (int i = columns.size() - 1; i >= 0; i--) {
            if (columns.get(i).getType() == ColTypes.SKP) {
                columns.remove(i);
                headers.remove(i);
            }
        }
    }
    public <T> void addColumn(String header, ArrayList<T> columnData, ColTypes type) {
        columns.add(new Column<T>(columnData, type));
        headers.add(header);
    }
    @SafeVarargs
    public final <T> void addMultipleColumns(ColTypes type, T... headers) {
        for (T header : headers) {
            this.addColumn(header.toString(), new ArrayList<>(), type);
        }
    }
    @SuppressWarnings("unchecked")
    public <T> ArrayList<T> getColumn(String header) {
        int index = headers.indexOf(header);
        if (index != -1) {
            return ((Column<T>) columns.get(index)).getData();
        } else {
            throw new IllegalArgumentException("Column with header: " + header + " not found.");
        }
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
        // Print headers
        System.out.println(String.join("\t", headers));

        // If subheaders are defined, print them
        if (subheaders != null && !subheaders.isEmpty()) {
            System.out.println(String.join("\t", subheaders));
        }

        // Print rows
        for (int i = 0; i < min(numRows, columns.get(0).getData().size()); i++) {
            for (Column<?> col : columns) {
                System.out.print(col.getData().get(i) + "\t");
            }
            System.out.println();  // Move to the next line after printing each row
        }
    }
    public void analyzeDataframe() {
        // Initializing counters for each type
        int strCount = 0;
        int dblCount = 0;
        int datCount = 0;

        // Lists to store examples for each type
        List<String> strExamples = new ArrayList<>();
        List<String> dblExamples = new ArrayList<>();
        List<String> datExamples = new ArrayList<>();

        for (int i = 0; i < columns.size(); i++) {
            Column<?> col = columns.get(i);
            String example = headers.get(i) + " : " + (col.getData().isEmpty() ? "EMPTY" : col.getData().get(0));

            switch (col.getType()) {
                case STR:
                    strCount++;
                    if (strExamples.size() < 5) {
                        strExamples.add(example);
                    }
                    break;
                case DBL:
                    dblCount++;
                    if (dblExamples.size() < 5) {
                        dblExamples.add(example);
                    }
                    break;
                case DAT:
                    datCount++;
                    if (datExamples.size() < 5) {
                        datExamples.add(example);
                    }
                    break;
            }
        }

        // Print results
        System.out.println("Number of STR columns: " + strCount);
        System.out.println("Examples of STR columns:");
        for (String ex : strExamples) {
            System.out.println("\t" + ex);
        }

        System.out.println("Number of DBL columns: " + dblCount);
        System.out.println("Examples of DBL columns:");
        for (String ex : dblExamples) {
            System.out.println("\t" + ex);
        }

        System.out.println("Number of DAT columns: " + datCount);
        System.out.println("Examples of DAT columns:");
        for (String ex : datExamples) {
            System.out.println("\t" + ex);
        }
    }
    public void printColumnDetails(List<String> columnNames) {
        for (String columnName : columnNames) {
            int index = getColumnIndex(columnName);
            if (index >= 0 && index < columns.size()) {
                String header = headers.get(index);
                String subheader = subheaders != null && !subheaders.isEmpty() ? subheaders.get(index) : "";
                Object firstRowValue = columns.get(index).getData().isEmpty() ? "" : columns.get(index).getData().get(0);

                System.out.println("Column: " + columnName);
                System.out.println("Header: " + header);
                System.out.println("Subheader: " + subheader);
                System.out.println("First Row Value: " + firstRowValue);
                System.out.println("------------------------------");
            } else {
                System.out.println("Column: " + columnName + " is out of bounds.");
                System.out.println("------------------------------");
            }
        }
    }
    public void printColumnHeaders(List<String> columnNames) {
        for (String columnName : columnNames) {
            int index = getColumnIndex(columnName);
            if (index >= 0 && index < columns.size()) {
                String subheader = subheaders != null && !subheaders.isEmpty() ? subheaders.get(index) : "";
                System.out.println("Header: " + subheader);
            } else {
                System.out.println("Column: " + columnName + " is out of bounds.");
                System.out.println("------------------------------");
            }
        }
    }

    // EXCEL REFERENCING
    public static String getColumnId(int index) {
        StringBuilder columnId = new StringBuilder();
        while (index > 0) {
            int remainder = (index - 1) % 26;
            columnId.append((char) (remainder + 'A'));
            index = (index - 1) / 26;
        }
        return columnId.reverse().toString();
    }
    private static int getColumnIndex(String columnId) {
        int index = 0;
        for (char c : columnId.toCharArray()) {
            index = index * 26 + (c - 'A' + 1);
        }
        return index - 1; // we subtract 1 because array indexing starts from 0
    }
}
