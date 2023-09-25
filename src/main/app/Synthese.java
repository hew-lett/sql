package main.app;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.Math.min;
import static main.app.App.*;
import static main.app.DF.ColTypes.*;
import static main.app.Estimate.isComm;

public class Synthese extends DF {
    private List<Integer> refMapping;
    private ArrayList<Boolean> bu;
    public static Synthese syntAncien;
    private ArrayList<Integer> mapToAncien;
    public static final String[] INTEGER_COLUMNS;
    public static final String[] DOUBLE_COLUMNS;
    public static final String[] PERCENTAGE_COLUMNS;
    private ArrayList<Double> primeColumn = new ArrayList<>();
    private ArrayList<Integer> indexes = new ArrayList<>();
    // column formatting types initialization block
    static {
        INTEGER_COLUMNS = new String[] {
                "Nombre Adhésions",
                "Nombre Dossier En Cours"
        };

        DOUBLE_COLUMNS = new String[] {
                "Montant Total HT",
                "Montant Total Net Compagnie",
                "Montant Total Prime Assureur",
                "Montant Total Commission ICI",
                "Montant Total Prime",
                "Prime Acquise à date",
                "Participation aux Benefices",
                "Total Sinistres Comptable",
                "Total Sinistres Technique",
                "Ecart sinistres Technique - Comptable",
                "Provision Sinistre Connu 2013",
                "Provision Sinistre Connu 2014",
                "Provision Sinistre Connu 2015",
                "Provision Sinistre Connu 2016",
                "Provision Sinistre Connu 2017",
                "Provision Sinistre Connu 2018",
                "Provision Sinistre Connu 2019",
                "Provision Sinistre Connu 2020",
                "Provision Sinistre Connu 2021",
                "Provision Sinistre Connu 2022",
                "Provision Sinistre Connu 2023",
                "Provision Sinistre Connu 2024",
                "Provision Sinistre Connu 2025",
                "Provision Sinistre Connu 2026",
                "Total Provision Sinistre Connu",
                "Prime émise réelle",
                "Solde comptable émis",
                "Solde comptable acquis",
                "Solde technique émis",
                "Solde technique acquis",
                "Solde technique provisionné emis",
                "Solde technique provisionné acquis",
                "Sinistre Ultime",
                "Prime à l'ultime",
                "PB pour S/P acquis"
        };

        PERCENTAGE_COLUMNS = new String[] {
                "Taux primes émise réelle",
                "Taux d'acquisition des primes",
                "S/P comptable émis",
                "S/P comptable acquis",
                "S/P technique émis",
                "S/P technique acquis",
                "S/P technique provisionné émis",
                "S/P technique provisionné acquis",
                "S/P Comptable à l'ultime"
        };
    }
    protected Map<String, ArrayList<Integer>> frequencies = new LinkedHashMap<>();

    public static void main(String[] args) throws IOException {
        Stopwatch st = new Stopwatch();
        st.start();



        st.printElapsedTime();
    }
    public Synthese(String path) throws IOException {
        char delim = ';';
        headers = new ArrayList<>();
        subheaders = new ArrayList<>();
        columns = new ArrayList<>();

        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true, delim);
        settings.trimValues(false);
        settings.setMaxColumns(9000);
        settings.setMaxCharsPerColumn(256);
        CsvParser parser = new CsvParser(settings);

        List<String[]> allRows = parser.parseAll(new FileReader(path, encodingDefault));
        nrow = allRows.size() - 1;

        String[] subHeaderRow = allRows.get(0);
        for (String subHeaderX : subHeaderRow) {
            subheaders.add(Objects.requireNonNullElse(subHeaderX, "").trim());
        }

        String[] headerRow = allRows.get(1);
        for (String header : headerRow) {
            headers.add(Objects.requireNonNullElse(header, "").trim());
        }

        ArrayList<ColTypes> types = new ArrayList<>();
        int lastString = headers.indexOf("Statut Financier");
        if (lastString == -1) lastString = headers.indexOf("DATE COMPTABLE");

        for (int i = 0; i < headers.size(); i++) {
            if (i <= lastString) {
                if (headers.get(i).equals("NOMBRE TOTAL ADHESIONS")) {
                    types.add(INT);
                } else if (headers.get(i).startsWith("MONTANT")) {
                    types.add(DBL);
                } else {
                    types.add(STR);
                }
            } else {
                if (subheaders.get(i).equals("Nombre En cours total")) {
                    types.add(INT);
                } else {
                    types.add(DBL);
                }
            }
        }

        for (int i = 0; i < headers.size(); i++) { // Iterate over the configuration list
            ArrayList<Object> colData = new ArrayList<>();
            ColTypes colType = types.get(i);

            for (int j = 2; j < allRows.size(); j++) {
                String cell = allRows.get(j)[i];
                Object formattedCell = getCell(cell, colType);;
                colData.add(formattedCell);
            }
            columns.add(new Column<>(colData, colType));
        }
    } //FDT
    public Synthese(String path, String sheetName) throws IOException, ParseException {
        headers = new ArrayList<>();
        columns = new ArrayList<>();

        InputStream is = Files.newInputStream(new File(path).toPath());

        // Use Apache POI directly to open the workbook
        Workbook workbook = new XSSFWorkbook(is);

        Sheet sheet = workbook.getSheet(sheetName);
        Iterator<Row> rows = sheet.rowIterator();

        // Handle headers
        Row headerRow = rows.next();
        for (Cell cell : headerRow) {
            headers.add(cell.getStringCellValue().replace("hors ICI","").replace("yc ICI","").replace("\n","").trim());
        }

        ArrayList<ColTypes> columnTypes = new ArrayList<>();
        boolean keyColumnFound = false;
        for (String header : headers) {
            if (!keyColumnFound) {
                if (header.equals("ADHESIONS COMPTABLE")) {
                    columnTypes.add(INT);
                    keyColumnFound = true;
                } else {
                    columnTypes.add(STR);
                }
            } else {
                columnTypes.add(DBL);
            }
        }
        for (ColTypes type : columnTypes) {
            columns.add(new Column<>(new ArrayList<>(), type));
        }
        while (rows.hasNext()) {
            Row currentRow = rows.next();
            for (int i = 0; i < headers.size(); i++) {
//                if(headers.get(i).equals("date")) {
//                    System.out.println("here");
//                }
                Cell currentCell = currentRow.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                getColumnByIndex(i).add(parseCell(currentCell, columnTypes.get(i)));
            }
        }
        cleanAnnees();
        cleanDate();
        nrow = getColumnByIndex(0).size();
//        dropSKPColumns();
    } //ancien
    public Synthese(Synthese external, String grouping2, Synthese syntAncien, boolean avecICI) throws ParseException {
        System.out.println("Synthèse par " + grouping2 + "...");
        headers = new ArrayList<>();
        columns = new ArrayList<>();
        refMapping = new ArrayList<>();
        bu = new ArrayList<>();

        populateRefMapping(external);
        createBUList(external);
        populateAssureur(); //prerequis refMapping!

        // Helper method for adding mapped columns
        addMappedColumn(external, "GESTIONNAIRE 1", "Gestionnaire", STR);
        addMappedColumn(external, "LIBELLE DISTRIBUTEUR", "Distributeur", STR);
        addMappedColumn(external, "Contrat", "Contrat", STR);
        addMappedColumn(external, "Date Periode", "Date Periode", STR);
        reformatDatePeriode();
        extractYearFromPeriode(); // Année
        swapColumns("Date Periode", "Année");
        cleanDistributeurAndGestionnaire();

        String grouping1 = "Année";

        List<String> sortOrder = Arrays.asList("Gestionnaire", "Distributeur", "Contrat","Année", "Date Periode");
        if (grouping2.equals("Distributeur")) {
            sortOrder = Arrays.asList("Gestionnaire", "Distributeur", "Année");
            indexes = generalSort(sortOrder);
            deleteComm();
            insertSummaryRowsDistrib();

        } else if (grouping2.equals("Gestionnaire")) {
            sortOrder = Arrays.asList("Gestionnaire", "Année");
            indexes = generalSort(sortOrder);
            deleteComm();
            insertSummaryRowsByGestionnaire();
        } else {
            indexes = generalSort(sortOrder);
            deleteComm();
            insertSummaryRows();
        }

        addMappedColumnSummedINT(external, "NOMBRE TOTAL ADHESIONS", "Nombre Adhésions", grouping1,grouping2);
        addMappedColumnSummedDBL(external, "MONTANT TOTAL HT", "Montant Total HT", grouping1,grouping2);
        addMappedColumnSummedDBL(external, "MONTANT TOTAL NET COMPAGNIE", "Montant Total Net Compagnie", grouping1,grouping2);
        if (avecICI) {
            addMappedColumnSummedDBL(external, "MONTANT TOTAL PRIME ASSUREUR", "Montant Total Prime Assureur", grouping1,grouping2);
            addMappedColumnSummedDBL(external, "MONTANT TOTAL COMMISSION ICI","Montant Total Commission ICI", grouping1,grouping2);
            calculatePrime(external, "Montant Total Prime", grouping1,grouping2);
        } else {
            addMappedColumnSummedDBL(external, "MONTANT TOTAL PRIME ASSUREUR", "Montant Total Prime Assureur", grouping1,grouping2);
            primeColumn = getColumn("Montant Total Prime Assureur");
        }


        addMappedColumnSummedDBL(external, "Prime Acquise à date", "Prime Acquise à date", grouping1,grouping2);
        addMappedColumnSummedDBL(external, "PB", "Participation aux Benefices", grouping1,grouping2);
        addDataFromSubheaderSummedDBL(external, "Charge Comptable totale", "Total Sinistres Comptable", grouping1,grouping2);
        addDataFromSubheaderSummedDBL(external, "Charge sinistre totale", "Total Sinistres Technique", grouping1,grouping2);

        calculateEcartSinistres();

        addDataFromSubheaderSummedINT(external, "Nombre En cours total", "Nombre Dossier En Cours", grouping1,grouping2);
        appendBlockSubheaderSummed(external, grouping1,grouping2);

        calculatePrimeEmiseReelle(grouping1,grouping2);

        calculatePrimeRatio("Taux primes émise réelle", "Prime émise réelle");
        calculatePrimeRatio("Taux d'acquisition des primes", "Prime Acquise à date");

        calculatePBpourSPacquis();
        calculateSPSolde();
        calculateSPSoldeProv();

        addMappedColumnSummedDBL(external, "Sinistre Ultime", "Sinistre Ultime", grouping1,grouping2);
        addColumn("Prime à l'ultime", primeColumn, DBL);
        calculateSPComptableUltimateColumn();

        mapToAncien = mapThisToExtern(syntAncien);
        compareColumns(syntAncien, "ADHESIONS COMPTABLE","Nombre Adhésions", "Variation adhesions comptable");
        comparePrimes(syntAncien,avecICI);
        compareColumns(syntAncien, "PRIME ACQUISE A DATE","Prime Acquise à date", "Variation primes acquises", false);
        compareColumns(syntAncien, "Taux d'acquisition des primes","Taux d'acquisition des primes", "Variation Taux d'Acquisition", true);
        compareColumns(syntAncien, "TOTAL SINISTRES COMPTABLE", "Total Sinistres Comptable","Variation des Sinistres Comptable", false);
        compareColumns(syntAncien, "TOTAL SINISTRE TECHNIQUE", "Total Sinistres Technique","Variation des Sinistres Technique", false);
        compareColumns(syntAncien, "Provisions sur sinistres connus", "Total Provision Sinistre Connu","Variation des Provisions sur Sinistre", false);
        compareColumns(syntAncien, "S/P comptable acquis","S/P comptable acquis", "Variation S/P comptable acquis", true);
        compareColumns(syntAncien, "S/P technique acquis","S/P technique acquis", "Variation S/P technique acquis", true);
        compareColumns(syntAncien, "S/P technique provisionné acquis","S/P technique provisionné acquis", "Variation S/P technique provisionné acquis", true);
        compareColumns(syntAncien, "Sinistre Ultime","Sinistre Ultime", "Variation Sinistre Ultime", false);
        compareColumns(syntAncien, "S/P Comptable à l'ultime","S/P Comptable à l'ultime", "Variation S/P Comptable à l'ultime", true);

    }

    private void deleteComm() throws ParseException {
        ArrayList<String> date = getColumn("Date Periode");
        ArrayList<String> contrat = getColumn("Contrat");
        SimpleDateFormat sdf = new SimpleDateFormat("MM-yyyy");
        for (int i = indexes.size() - 1; i >= 0; i--) {
            if (isComm(contrat.get(i), sdf.parse(date.get(i)))) {
                indexes.remove(i);
                removeRowNoCheck(i);
            }
        }
    }

    public Synthese(Synthese other, String grouping) {
        // Deep copy headers, subheaders
        this.headers = new ArrayList<>(other.headers);
        this.columns = new ArrayList<>();

        // Initialize columns with deep copies
        for (Column<?> col : other.columns) {
            if (col.getType() == STR) {
                this.columns.add(new Column<>(new ArrayList<String>(), STR));
            } else if (col.getType() == DAT) {
                this.columns.add(new Column<>(new ArrayList<Date>(), DAT));
            } else if (col.getType() == DBL) {
                this.columns.add(new Column<>(new ArrayList<Double>(), DBL));
            } else if (col.getType() == INT) {
                this.columns.add(new Column<>(new ArrayList<Integer>(), INT));
            }
            // ... Add similar blocks for FLT, SKP, or any other types you might have ...
        }

        ArrayList<String> anneeColumn = other.getColumn("Année");
        ArrayList<String> groupingColumn = other.getColumn(grouping);

        // Iterate over rows and copy only rows where "Année" starts with "Total"
        for (int rowIndex = 0; rowIndex < other.getColumnByIndex(0).size(); rowIndex++) {
            if (groupingColumn.get(rowIndex).startsWith("Total ") || anneeColumn.get(rowIndex).startsWith("Total ")) {
                for (int colIndex = 0; colIndex < other.columns.size(); colIndex++) {
                    this.getColumnByIndex(colIndex).add(other.getColumnByIndex(colIndex).get(rowIndex));
                }
            }
        }

        String[] columnsToRemove;
        if (grouping.equals("Gestionnaire")) {
            columnsToRemove = new String[] {"Date Periode", "Contrat", "Distributeur"};
        } else if (grouping.equals("Distributeur")) {
            columnsToRemove = new String[] {"Date Periode", "Contrat"};
        } else {
            columnsToRemove = new String[] {"Date Periode"};
        }
        for (String columnName : columnsToRemove) {
            int columnIndex = this.headers.indexOf(columnName);
            if (columnIndex != -1) {
                this.headers.remove(columnIndex);
                this.columns.remove(columnIndex);
            }
        }

        ArrayList<String> adjustedAnneeColumn = this.getColumn("Année");
        adjustedAnneeColumn.replaceAll(s -> s.replace("Total ", ""));

    }

    // CELL OPERATIONS

    private void cleanAnnees() {
        ArrayList<String> anneesColumn = this.getColumn("Années");

        // Create a new list to store the cleaned entries.
        ArrayList<String> cleanedAnnees = new ArrayList<>();

        for (String entry : anneesColumn) {
            if (entry == null) {
                cleanedAnnees.add("");
                continue;
            }
            if (!entry.startsWith("Total")) {
                entry = entry.replace(".0", "");
            }
            cleanedAnnees.add(entry);
        }

        // Replace the original "Années" column with the cleaned entries.
        int anneesColIndex = headers.indexOf("Années");
        if (anneesColIndex != -1) {
            columns.set(anneesColIndex, new Column<>(cleanedAnnees, STR));
        }
    }
    private void cleanDate() throws ParseException {
        ArrayList<String> dateColumn = this.getColumn("date");

        // This list will hold the cleaned dates in the MM-yyyy format.
        ArrayList<String> cleanedDates = new ArrayList<>();

        // Define the source format based on the existing format in the column.
        SimpleDateFormat sourceDateFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.ENGLISH);
        if (dateDefault.parse(dateColumn.get(0)) != null) {
            sourceDateFormat = dateDefault;
        }
        // Define a new date format for MM-yyyy.
        SimpleDateFormat targetDateFormat = new SimpleDateFormat("MM-yyyy");

        for (String dateEntry : dateColumn) {
            if (dateEntry == null || dateEntry.isEmpty()) {
                cleanedDates.add("");
            } else {
                try {
                    Date parsedDate = sourceDateFormat.parse(dateEntry);
                    cleanedDates.add(targetDateFormat.format(parsedDate));
                } catch (ParseException e) {
                    // If parsing fails for some reason, add an empty string.
                    cleanedDates.add("");
                }
            }
        }

        // Replace the original date column with the cleaned dates.
        int dateColIndex = headers.indexOf("date");
        if (dateColIndex != -1) {
            columns.set(dateColIndex, new Column<>(cleanedDates, STR));
        }
    }
    private void populateRefMapping (Synthese external) {
        ArrayList<String> contratData = external.getColumn("Contrat");
        for (String contrat : contratData) {
            int refIndex = refProg.getColumn("Contrat").indexOf(contrat);
            refMapping.add(refIndex);
        }
    }
    private void createBUList(Synthese external) {
        ArrayList<String> fluxData = external.getColumn("Flux");
        for (String fluxValue : fluxData) {
            bu.add("BU".equals(fluxValue));
        }
    }
    private void populateAssureur() {
        // Use the mapping to generate the Assureur column
        ArrayList<String> assureurData = new ArrayList<>();
        for (int refIndex : refMapping) {
            if (refIndex != -1) {
                assureurData.add((String) refProg.getColumn("Compagnie").get(refIndex));
            } else {
                assureurData.add("");  // or some default value if not found
            }
        }

        // Insert Assureur before Contrat
        this.addColumn("Assureur", assureurData, STR);
    }
    private void addMappedColumn(Synthese external, String sourceColName, String targetColName, ColTypes type) {
        ArrayList<?> data = external.getColumn(sourceColName);
        this.addColumn(targetColName, new ArrayList<>(data), type);
    }
    private void extractYearFromPeriode() {
        ArrayList<String> datePeriodeData = this.getColumn("Date Periode");
        ArrayList<String> anneesData = new ArrayList<>();
        for (String date : datePeriodeData) {
            String year = date.split("-")[1];
            anneesData.add(year);
        }
        this.addColumn("Année", anneesData, STR);
    }
    public void swapColumns(String colName1, String colName2) {
        int index1 = headers.indexOf(colName1);
        int index2 = headers.indexOf(colName2);

        if (index1 == -1 || index2 == -1) {
            throw new IllegalArgumentException("One or both column names are invalid.");
        }

        // Swap headers
        Collections.swap(headers, index1, index2);

        // Swap data
        Collections.swap(columns, index1, index2);
    }
    private void cleanDistributeurAndGestionnaire() {
        ArrayList<String> distributeurData = this.getColumn("Distributeur");
        ArrayList<String> gestionnaireData = this.getColumn("Gestionnaire");
        ArrayList<String> contratData = this.getColumn("Contrat");

        for (int i = 0; i < distributeurData.size(); i++) {
            substValuesDisGes(distributeurData, contratData, i, "Distributeur");
            substValuesDisGes(gestionnaireData, contratData, i, "Gestionnaire");
        }
    }
    private void substValuesDisGes(ArrayList<String> columnData, ArrayList<String> contratData, int index, String columnName) {
        String currentValue = columnData.get(index);

        // Check if current value is missing or empty
        if (currentValue == null || currentValue.isEmpty()) {
            String currentContrat = contratData.get(index);

            // Find another non-empty value for the same Contrat
            String newValue = null;
            for (int j = 0; j < contratData.size(); j++) {
                if (contratData.get(j).equals(currentContrat) && columnData.get(j) != null) {
                    newValue = columnData.get(j);
                    break;
                }
            }

            // If still no value found, get from refProg
            if (newValue == null) {
                int refIndex = refMapping.get(index);  // Assuming refMapping has been populated prior to this
                if (refIndex != -1) {
                    newValue = (String) refProg.getColumn(columnName).get(refIndex);
                }
            }
            newValue = (newValue == null) ? "" : (newValue.startsWith("Garantie") ? "Garantie Privée" : newValue);
            // Update the column value
            columnData.set(index, newValue);  // Default to empty if still null
        }
    }

    public void insertSummaryRows() {
        int i = 0;
        while (i < getColumn("Année").size() - 1) { // Using size() - 1 as we'll compare with the next element
            if (!getColumn("Année").get(i).equals(getColumn("Année").get(i + 1)) || !getColumn("Contrat").get(i).equals(getColumn("Contrat").get(i + 1))) {
                duplicateRowWithTwoEmptyColumns(i, "Année", "Date Periode", null, "Total ");
                bu.add(i + 1, true); // Insert true at the correct position in bu
                i++; // Increase to skip the row we just added
            }
            i++;
        }

        i = 0;
        while (i < getColumn("Contrat").size() - 1) {
            if (!getColumn("Contrat").get(i).equals(getColumn("Contrat").get(i + 1))) {
                duplicateRowWithTwoEmptyColumns(i, "Contrat", "Date Periode", "Année", "Total ");
                bu.add(i + 1, true); // Insert true at the correct position in bu
                i++;
            }
            i++;
        }
    }
    public void insertSummaryRowsDistrib() {
        int i = 0;
        while (i < getColumn("Année").size() - 1) {
            if (!getColumn("Année").get(i).equals(getColumn("Année").get(i + 1))) {
                duplicateRowWithTwoEmptyColumns(i, "Année", "Date Periode", "Contrat", "Total ");
                bu.add(i + 1, true);
                i++;
            }
            i++;
        }

        i = 0;
        while (i < getColumn("Distributeur").size() - 1) {
            if (!getColumn("Distributeur").get(i).equals(getColumn("Distributeur").get(i + 1))) {
                duplicateRowWithThreeEmptyColumns(i, "Distributeur", "Date Periode", "Année", "Contrat", "Total Distributeur ");
                bu.add(i + 1, true);
                i++;
            }
            i++;
        }
    }
    public void insertSummaryRowsByGestionnaire() {
        int i = 0;
        // Handle changes in Année
        while (i < getColumn("Année").size() - 1) {
            if (!getColumn("Année").get(i).equals(getColumn("Année").get(i + 1))) {
                duplicateRowWithThreeEmptyColumns(i, "Année", "Distributeur","Date Periode", "Contrat", "Total ");
                bu.add(i + 1, true);
                i++;
            }
            i++;
        }

        i = 0;
        // Handle changes in Gestionnaire
        while (i < getColumn("Gestionnaire").size() - 1) {
            if (!getColumn("Gestionnaire").get(i).equals(getColumn("Gestionnaire").get(i + 1))) {
                duplicateRowWithFourEmptyColumns(i, "Gestionnaire", "Distributeur", "Date Periode", "Année", "Contrat", "Total Gestionnaire ");
                bu.add(i + 1, true);
                i++;
            }
            i++;
        }
    }
    private void duplicateRowWithFourEmptyColumns(int rowIndex, String prefixColumn, String emptyColumn1, String emptyColumn2, String emptyColumn3, String emptyColumn4, String prefix) {
        for (int colIndex = 0; colIndex < columns.size(); colIndex++) {
            ArrayList<Object> columnData = (ArrayList<Object>) columns.get(colIndex).getData();
            if (headers.get(colIndex).equals(prefixColumn)) {
                columnData.add(rowIndex + 1, prefix + columnData.get(rowIndex));
            } else if (headers.get(colIndex).equals(emptyColumn1) || headers.get(colIndex).equals(emptyColumn2) || headers.get(colIndex).equals(emptyColumn3) || headers.get(colIndex).equals(emptyColumn4)) {
                columnData.add(rowIndex + 1, "");
            } else {
                columnData.add(rowIndex + 1, columnData.get(rowIndex));
            }
        }
    }
    /**
     * This method duplicates a row and modifies some of its values
     *
     * @param rowIndex       The index of the row to duplicate
     * @param prefixColumn   The column where the prefix will be added
     * @param emptyColumn1   The first column that will be set to empty
     * @param emptyColumn2   The second column that will be set to empty (can be null if no second column should be emptied)
     * @param prefix         The prefix to add
     */
    @SuppressWarnings("unchecked")
    private void duplicateRowWithTwoEmptyColumns(int rowIndex, String prefixColumn, String emptyColumn1, String emptyColumn2, String prefix) {
        for (int colIndex = 0; colIndex < columns.size(); colIndex++) {
            ArrayList<Object> columnData = (ArrayList<Object>) columns.get(colIndex).getData();
            if (headers.get(colIndex).equals(prefixColumn)) {
                columnData.add(rowIndex + 1, prefix + columnData.get(rowIndex));
            } else if (headers.get(colIndex).equals(emptyColumn1) || (emptyColumn2 != null && headers.get(colIndex).equals(emptyColumn2))) {
                columnData.add(rowIndex + 1, "");
            } else {
                columnData.add(rowIndex + 1, columnData.get(rowIndex));
            }
        }
    }
    private void duplicateRowWithThreeEmptyColumns(int rowIndex, String prefixColumn, String emptyColumn1, String emptyColumn2, String emptyColumn3, String prefix) {
        for (int colIndex = 0; colIndex < columns.size(); colIndex++) {
            ArrayList<Object> columnData = (ArrayList<Object>) columns.get(colIndex).getData();
            if (headers.get(colIndex).equals(prefixColumn)) {
                columnData.add(rowIndex + 1, prefix + columnData.get(rowIndex));
            } else if (headers.get(colIndex).equals(emptyColumn1) || headers.get(colIndex).equals(emptyColumn2) || headers.get(colIndex).equals(emptyColumn3)) {
                columnData.add(rowIndex + 1, "");
            } else {
                columnData.add(rowIndex + 1, columnData.get(rowIndex));
            }
        }
    }

    // ADD DATA
    private void addMappedColumnSummedDBL(Synthese external, String sourceColName, String targetColName, String colToAggregare1, String colToAggregare2) {
        ArrayList<Double> sourceData = external.getColumn(sourceColName); // Assuming the source data are of type Double

        addDataSummedDBL(targetColName, colToAggregare1, colToAggregare2, sourceData);
    }
    private void addMappedColumnSummedINT(Synthese external, String sourceColName, String targetColName, String colToAggregare1, String colToAggregare2) {
        ArrayList<Integer> sourceData = external.getColumn(sourceColName); // Assuming the source data are of type Double

        addDataSummedINT(targetColName, colToAggregare1, colToAggregare2, sourceData);
    }
    private void addDataFromSubheaderSummedDBL(Synthese external, String subheaderName, String newColumnName, String colToAggregare1, String colToAggregare2) {
        int columnIndex = external.subheaders.indexOf(subheaderName);
        if (columnIndex == -1) return;
        ArrayList<Double> sourceData = external.getColumnByIndex(columnIndex);

        addDataSummedDBL(newColumnName, colToAggregare1, colToAggregare2, sourceData);
    }
    private void addDataFromSubheaderSummedINT(Synthese external, String subheaderName, String newColumnName, String colToAggregare1, String colToAggregare2) {
        int columnIndex = external.subheaders.indexOf(subheaderName);
        if (columnIndex == -1) return;
        ArrayList<Integer> sourceData = external.getColumnByIndex(columnIndex);
        addDataSummedINT(newColumnName, colToAggregare1, colToAggregare2, sourceData);
    }
    private void addDataSummedDBL(String targetColName, String colToAggregare1, String colToAggregare2, ArrayList<Double> sourceData) {
        List<String> firstLevel = this.getColumn(colToAggregare1);
        List<String> secondLevel = this.getColumn(colToAggregare2);
        ArrayList<Double> targetData = new ArrayList<>(); // to store augmented data

        double currentSum = 0.0;
        double sumOfSums = 0.0;

        for (int i = 0, j = 0; i < firstLevel.size(); i++) {
            double currentValue = sourceData.get(indexes.get(j)) == null ? 0.0d : sourceData.get(indexes.get(j)); // Protect against index out of bounds

            // Check for the "Total" prefix and reset values as needed
            if (firstLevel.get(i).startsWith("Total")) {
                targetData.add(currentSum);
                sumOfSums += currentSum;
                currentSum = 0.0; // reset current sum
            } else if (secondLevel.get(i).startsWith("Total")) {
                targetData.add(sumOfSums);
                currentSum = 0.0; // reset current sum
                sumOfSums = 0.0;  // reset sum of sums
            } else {
                currentSum += currentValue;
                targetData.add(currentValue);
                j++; // Increment the iterator for the sourceData
            }
        }

        this.addColumn(targetColName, new ArrayList<>(targetData), DBL);
    }
    private void addDataSummedINT(String targetColName, String colToAggregare1, String colToAggregare2, ArrayList<Integer> sourceData) {
        List<String> firstLevel = this.getColumn(colToAggregare1);
        List<String> secondLevel = this.getColumn(colToAggregare2);
        ArrayList<Integer> targetData = new ArrayList<>(); // to store augmented data

        int currentSum = 0;
        int sumOfSums = 0;

        for (int i = 0, j = 0; i < firstLevel.size(); i++) {
            int currentValue = sourceData.get(indexes.get(j)) == null ? 0 : sourceData.get(indexes.get(j)); // Protect against index out of bounds

            // Check for the "Total" prefix and reset values as needed
            if (firstLevel.get(i).startsWith("Total")) {
                targetData.add(currentSum);
                sumOfSums += currentSum;
                currentSum = 0; // reset current sum
            } else if (secondLevel.get(i).startsWith("Total")) {
                targetData.add(sumOfSums);
                currentSum = 0; // reset current sum
                sumOfSums = 0;  // reset sum of sums
            } else {
                currentSum += currentValue;
                targetData.add(currentValue);
                j++; // Increment the iterator for the sourceData
            }
        }

        this.addColumn(targetColName, new ArrayList<>(targetData), INT);
    }
    private void appendBlockSubheaderSummed(Synthese external, String colToAggregare1, String colToAggregare2) {
        int startColumnIndex1 = external.subheaders.indexOf("Provision En Cours");
        int startColumnIndex2 = external.subheaders.indexOf("Provision En Cours Accepté");

        ArrayList<Double> totalValues = new ArrayList<>();

        List<String> firstLevel = this.getColumn(colToAggregare1);
        List<String> secondLevel = this.getColumn(colToAggregare2);

        // Initialize totalValues to 0.0 based on firstLevel's size
        for (int i = 0; i < firstLevel.size(); i++) {
            totalValues.add(0.0);
        }

        double currentSum = 0.0;
        double sumOfSums = 0.0;

        for (int columnIndex1 = startColumnIndex1,columnIndex2 = startColumnIndex2; columnIndex1 < external.subheaders.size(); columnIndex1++,columnIndex2++) {
            // If we encounter a non-empty subheader, break the loop
            if (external.headers.get(columnIndex1).equals("Total")) {
                break;
            }
            String year = external.headers.get(columnIndex1);
            String newColumnName = "Provision Sinistre Connu " + year;

            ArrayList<Double> targetColumn = new ArrayList<>();
            ArrayList<Double> externalColumn1 = external.getColumnByIndex(columnIndex1);
            ArrayList<Double> externalColumn2 = external.getColumnByIndex(columnIndex2);

            for (int i = 0, j = 0; i < firstLevel.size(); i++) {
                Double value = (externalColumn1.get(indexes.get(j)) == null ? 0.0 : externalColumn1.get(indexes.get(j))) +
                                (externalColumn2.get(indexes.get(j)) == null ? 0.0 : externalColumn2.get(indexes.get(j)));

                if (firstLevel.get(i).startsWith("Total")) {
                    targetColumn.add(roundToTwoDecimals(currentSum));
                    totalValues.set(i, totalValues.get(i) + roundToTwoDecimals(currentSum));  // Update totalValues
                    sumOfSums += currentSum;
                    currentSum = 0.0;
                } else if (secondLevel.get(i).startsWith("Total")) {
                    targetColumn.add(roundToTwoDecimals(sumOfSums));
                    totalValues.set(i, totalValues.get(i) + roundToTwoDecimals(sumOfSums));  // Update totalValues
                    currentSum = 0.0;
                    sumOfSums = 0.0;
                } else {
                    currentSum += value;
                    targetColumn.add(value);
                    totalValues.set(i, totalValues.get(i) + value);  // Update totalValues
                    j++; // increment the external data index
                }
            }

            this.addColumn(newColumnName, targetColumn, DBL);
        }

        String totalColumnName = "Total " + "Provision Sinistre Connu";
        this.addColumn(totalColumnName, totalValues, DBL);
    }
    // CALCULATE DATA
    private void calculatePrime(Synthese external, String targetColName, String colToAggregare1, String colToAggregare2) {
        List<String> firstLevel = this.getColumn(colToAggregare1);
        List<String> secondLevel = this.getColumn(colToAggregare2);

        ArrayList<Double> sourceData1 = external.getColumn("MONTANT TOTAL PRIME ASSUREUR");
        ArrayList<Double> sourceData2 = external.getColumn("MONTANT TOTAL COMMISSION ICI");

        ArrayList<Double> targetData = new ArrayList<>(); // to store augmented data
        double currentSum = 0.0;
        double sumOfSums = 0.0;

        for (int i = 0, j = 0; i < firstLevel.size(); i++) {
            double currentValue1 = sourceData1.get(j) == null ? 0.0d : sourceData1.get(j); // Protect against index out of bounds
            double currentValue2 = sourceData2.get(j) == null ? 0.0d : sourceData2.get(j); // Protect against index out of bounds

            double currentValue = currentValue1 + currentValue2;

            // Check for the "Total" prefix and reset values as needed
            if (firstLevel.get(i).startsWith("Total")) {
                targetData.add(currentSum);
                primeColumn.add(currentSum);
                sumOfSums += currentSum;
                currentSum = 0.0; // reset current sum
            } else if (secondLevel.get(i).startsWith("Total")) {
                targetData.add(sumOfSums);
                primeColumn.add(sumOfSums);
                currentSum = 0.0; // reset current sum
                sumOfSums = 0.0;  // reset sum of sums
            } else {
                currentSum += currentValue;
                targetData.add(currentValue);
                primeColumn.add(currentValue);
                j++; // Increment the iterator for the sourceData
            }
        }

        this.addColumn(targetColName, new ArrayList<>(targetData), DBL);
    }
    private void calculateEcartSinistres() {
        ArrayList<Double> totalSinistreTechniqueData = this.getColumn("Total Sinistres Technique");
        ArrayList<Double> totalSinistreComptableData = this.getColumn("Total Sinistres Comptable");
        ArrayList<Double> ecartSinistresData = new ArrayList<>();

        for (int i = 0; i < totalSinistreTechniqueData.size(); i++) {
            ecartSinistresData.add(totalSinistreTechniqueData.get(i) - totalSinistreComptableData.get(i));
        }
        this.addColumn("Ecart sinistres Technique - Comptable", ecartSinistresData, DBL);
    }
    private void calculatePrimeEmiseReelle(String colToAggregare1,String colToAggregare2) {
        ArrayList<Double> primeEmiseReelleData = new ArrayList<>();

        List<String> firstLevel = this.getColumn(colToAggregare1);
        List<String> secondLevel = this.getColumn(colToAggregare2);

        double currentSum = 0.0;
        double sumOfSums = 0.0;

        for (int i = 0, j = 0; i < firstLevel.size(); i++) {
            double currentValue = primeColumn.get(j);

            if (firstLevel.get(i).startsWith("Total")) {
                primeEmiseReelleData.add(currentSum);
                sumOfSums += currentSum;
                currentSum = 0.0; // reset current sum
            } else if (secondLevel.get(i).startsWith("Total")) {
                primeEmiseReelleData.add(sumOfSums);
                currentSum = 0.0; // reset current sum
                sumOfSums = 0.0;  // reset sum of sums
            } else {
                currentSum += currentValue;
                primeEmiseReelleData.add(currentValue);
                j++;
            }
        }

        this.addColumn("Prime émise réelle", primeEmiseReelleData, DBL);
    }
    private void calculatePrimeRatio(String columnName, String numeratorColumn) {
        ArrayList<Double> numeratorData = getColumn(numeratorColumn);
        ArrayList<Double> denominatorData = primeColumn;
        ArrayList<Double> ratioData = new ArrayList<>();

        for (int i = 0; i < numeratorData.size(); i++) {
            double value = numeratorData.get(i) / denominatorData.get(i);
            
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                value = 0.0;
            } else if (value > 1) {
                value = 1.0;
            }
            ratioData.add(Math.round(value * 10000.0) / 10000.0);
        }
        this.addColumn(columnName, ratioData, DBL);
    }
    private void calculatePBpourSPacquis() {
        ArrayList<Double> participationBeneficesData = getColumn("Participation aux Benefices");
        ArrayList<Double> tauxAcquisitionPrimesData = getColumn("Taux d'acquisition des primes");

        ArrayList<Double> result = new ArrayList<>();

        for (int i = 0; i < participationBeneficesData.size(); i++) {
            result.add(participationBeneficesData.get(i) * tauxAcquisitionPrimesData.get(i));
        }

        this.addColumn("PB pour S/P acquis", result, DBL);
    }
    private void calculateSPSolde() {
        ArrayList<Double> totalFic = getColumn("Total Sinistres Comptable");
        ArrayList<Double> totalSin = this.getColumn("Total Sinistres Technique");
        ArrayList<Double> pb = getColumn("Participation aux Benefices");
        ArrayList<Double> primeAdate = getColumn("Prime Acquise à date");
        ArrayList<Double> pbAcquis = getColumn("PB pour S/P acquis");

        ArrayList<Double> spFic = new ArrayList<>();
        ArrayList<Double> soldeFic = new ArrayList<>();
        ArrayList<Double> spAqFic = new ArrayList<>();
        ArrayList<Double> soldeAqFic = new ArrayList<>();
        ArrayList<Double> spSin = new ArrayList<>();
        ArrayList<Double> soldeSin = new ArrayList<>();
        ArrayList<Double> spAqSin = new ArrayList<>();
        ArrayList<Double> soldeAqSin = new ArrayList<>();

        for (int i = 0; i < totalFic.size(); i++) {
            spFic.add(safeDivision(totalFic.get(i),primeColumn.get(i) + pb.get(i)));
            soldeFic.add(primeColumn.get(i) + pb.get(i) - totalFic.get(i));

            spAqFic.add(safeDivision(totalFic.get(i), primeAdate.get(i) + pbAcquis.get(i)));
            soldeAqFic.add(primeAdate.get(i) + pb.get(i) - totalFic.get(i));

            spSin.add(safeDivision(totalSin.get(i),primeColumn.get(i) + pb.get(i)));
            soldeSin.add(primeColumn.get(i) + pb.get(i) - totalSin.get(i));

            spAqSin.add(safeDivision(totalSin.get(i), primeAdate.get(i) + pbAcquis.get(i)));
            soldeAqSin.add(primeAdate.get(i) + pb.get(i) - totalSin.get(i));
        }
        
        this.addColumn("S/P comptable émis", spFic, DBL);
        this.addColumn("Solde comptable émis", soldeFic, DBL);
        this.addColumn("S/P comptable acquis", spAqFic, DBL);
        this.addColumn("Solde comptable acquis", soldeAqFic, DBL);
        this.addColumn("S/P technique émis", spSin, DBL);
        this.addColumn("Solde technique émis", soldeSin, DBL);
        this.addColumn("S/P technique acquis", spAqSin, DBL);
        this.addColumn("Solde technique acquis", soldeAqSin, DBL);
    }
    private void calculateSPSoldeProv() {
        ArrayList<Double> totalSin = this.getColumn("Total Sinistres Technique");
        ArrayList<Double> provSin = this.getColumn("Total Provision Sinistre Connu");
        ArrayList<Double> pb = this.getColumn("Participation aux Benefices");
        ArrayList<Double> primeAdate = getColumn("Prime Acquise à date");
        ArrayList<Double> pbAcquis = this.getColumn("PB pour S/P acquis");

        ArrayList<Double> spProv = new ArrayList<>();
        ArrayList<Double> soldeProv = new ArrayList<>();
        ArrayList<Double> spAqProv = new ArrayList<>();
        ArrayList<Double> soldeAqProv = new ArrayList<>();

        for (int i = 0; i < totalSin.size(); i++) {
            double sinProv = totalSin.get(i) + provSin.get(i);
            double primePb = primeColumn.get(i) + pb.get(i);
            double primePbAq = primeAdate.get(i) + pbAcquis.get(i);

            spProv.add(safeDivision(sinProv, primePb));
            soldeProv.add(primePb - sinProv);
            spAqProv.add(safeDivision(sinProv, primePbAq));
            soldeAqProv.add(primePbAq - sinProv);
        }
        this.addColumn("S/P technique provisionné émis", spProv, DBL);
        this.addColumn("Solde technique provisionné emis", soldeProv, DBL);
        this.addColumn("S/P technique provisionné acquis", spAqProv, DBL);
        this.addColumn("Solde technique provisionné acquis", soldeAqProv, DBL);

    }
    private void calculateSPComptableUltimateColumn() {
        ArrayList<Double> sinistreUltimeData = this.getColumn("Sinistre Ultime");
        ArrayList<Double> primeUltimateData = this.getColumn("Prime à l'ultime");
        ArrayList<Double> participationAuxBeneficesData = this.getColumn("Participation aux Benefices");
        ArrayList<Double> spComptableUltimateData = new ArrayList<>();

        for (int i = 0; i < sinistreUltimeData.size(); i++) {
            double numerator = sinistreUltimeData.get(i);
            double denominator = primeUltimateData.get(i) + participationAuxBeneficesData.get(i);
            double value = safeDivision(numerator, denominator);
            spComptableUltimateData.add(roundToFourDecimals(value));
        }
        this.addColumn("S/P Comptable à l'ultime", spComptableUltimateData, DBL);
    }
    private ArrayList<Integer> mapThisToExtern(Synthese extern) {
        ArrayList<String> thisKeys = new ArrayList<>();
        ArrayList<String> externKeys = new ArrayList<>();

        ArrayList<String> thisContrat = this.getColumn("Contrat");
        ArrayList<String> thisDatePeriode = this.getColumn("Date Periode");

        ArrayList<String> externContrat = extern.getColumn("CONTRAT");
        ArrayList<String> externDate = extern.getColumn("date");

        for (int i = 0; i < thisContrat.size(); i++) {
            thisKeys.add(thisContrat.get(i) + thisDatePeriode.get(i));
        }

        for (int i = 0; i < externContrat.size(); i++) {
            externKeys.add(externContrat.get(i) + externDate.get(i));
        }

        ArrayList<Integer> mapToExtern = new ArrayList<>();
        for (String key : thisKeys) {
            mapToExtern.add(externKeys.indexOf(key));
        }

        return mapToExtern;
    }

    private void compareColumns(Synthese extern, String externColName, String thisColName, String newColName, boolean percentage) {
        ArrayList<Double> thisColumn = this.getColumn(thisColName);
        ArrayList<Double> externColumn = extern.getColumn(externColName);

        ArrayList<String> deltaColumn = new ArrayList<>();

        for (int i = 0; i < thisColumn.size(); i++) {
            int externIndex = mapToAncien.get(i);
            if (externIndex == -1) {
                deltaColumn.add("-");
            } else {
                double diff = thisColumn.get(i) - externColumn.get(externIndex);
                if (percentage) {
                    diff *= 100;
                    if (diff > 0) {
                        deltaColumn.add("+" + roundToTwoDecimals(diff) + " pts");
                    } else {
                        deltaColumn.add(roundToTwoDecimals(diff) + " pts");
                    }
                } else {
                    if (diff > 0) {
                        deltaColumn.add("+" + roundToTwoDecimals(diff));
                    } else if (diff < 0) {
                        deltaColumn.add(Double.toString(roundToTwoDecimals(diff)));
                    } else {
                        deltaColumn.add("0");
                    }
                }
            }
        }

        this.addColumn(newColName, deltaColumn, STR);
    }
    private void compareColumns(Synthese extern, String externColName, String thisColName, String newColName) {
        ArrayList<Integer> thisColumn = this.getColumn(thisColName);
        ArrayList<Integer> externColumn = extern.getColumn(externColName);
        ArrayList<String> deltaColumn = new ArrayList<>();

        for (int i = 0; i < thisColumn.size(); i++) {
            int externIndex = mapToAncien.get(i);
            if (externIndex == -1) {
                deltaColumn.add("-");
            } else {
                int diff = thisColumn.get(i) - externColumn.get(externIndex);

                if (diff > 0) {
                    deltaColumn.add("+" + diff);
                } else if (diff < 0) {
                    deltaColumn.add(String.valueOf(diff));
                } else {
                    deltaColumn.add("0");
                }
            }
        }

        this.addColumn(newColName, deltaColumn, STR);
    }
    private void comparePrimes(Synthese extern, boolean avecICI) {
        ArrayList<Double> thisColumn;
        ArrayList<Double> externColumn;
        if (avecICI) {
            thisColumn = primeColumn;
            externColumn = extern.getColumn("MONTANT TOTAL NET COMPAGNIE");
        } else {
            thisColumn = this.getColumn("Montant Total Prime Assureur");
            externColumn = extern.getColumn("MONTANT TOTAL PRIME ASSUREUR");
        }
        String newColName = "Variation des Primes émises";

        ArrayList<String> deltaColumn = new ArrayList<>();

        for (int i = 0; i < thisColumn.size(); i++) {
            int externIndex = mapToAncien.get(i);
            if (externIndex == -1) {
                deltaColumn.add("-");
            } else {
                double diff = thisColumn.get(i) - externColumn.get(externIndex);

                if (diff > 0) {
                    deltaColumn.add("+" + diff);
                } else if (diff < 0) {
                    deltaColumn.add(String.valueOf(diff));
                } else {
                    deltaColumn.add("0");
                }
            }
        }

        this.addColumn(newColName, deltaColumn, STR);
    }
    // FORMAT EXPORT
    public void formatAndReplaceColumn(String header) {
        int index = headers.indexOf(header);

        if (index == -1) {
            throw new IllegalArgumentException("Column with header: " + header + " not found.");
        }

        Column<?> oldColumn = columns.get(index);
        ArrayList<String> newColumnData = new ArrayList<>(oldColumn.getData().size());

        // Populate newColumnData based on the header and its type
        if (Arrays.asList(INTEGER_COLUMNS).contains(header)) {
            ArrayList<Integer> colData = (ArrayList<Integer>) oldColumn.getData();
            for (Integer value : colData) {
                if (value == null) {
                    newColumnData.add("");
                } else {
                    newColumnData.add(String.valueOf(value));
                }
            }
        } else if (Arrays.asList(DOUBLE_COLUMNS).contains(header)) {
            ArrayList<Double> colData = (ArrayList<Double>) oldColumn.getData();
            for (Double value : colData) {
                if (value == null) {
                    newColumnData.add("");
                } else {
                    newColumnData.add(String.format("%.2f", value));
                }
            }
        } else if (Arrays.asList(PERCENTAGE_COLUMNS).contains(header)) {
            ArrayList<Double> colData = (ArrayList<Double>) oldColumn.getData();
            for (Double value : colData) {
                if (value == null) {
                    newColumnData.add("");
                } else {
                    newColumnData.add(String.format("%.2f %%", value * 100));
                }
            }
        } else { // Already a string type or any other type
            for (Object obj : oldColumn.getData()) {
                if (obj == null) {
                    newColumnData.add("");
                } else {
                    newColumnData.add(obj.toString());
                }
            }
        }

        // Replace old column with new formatted column
        Column<String> newColumn = new Column<>(newColumnData, STR);
        columns.set(index, newColumn);
    }
    public void formatAllColumns() {
        for (String header : headers) {
            formatAndReplaceColumn(header);
        }
    }
    public void exportToExcel(String fileName, String sheetName, Workbook existingWorkbook) throws IOException {
        Workbook workbook = existingWorkbook != null ? existingWorkbook : new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(sheetName);

        // Creating header row
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell headerCell = headerRow.createCell(i);
            headerCell.setCellValue(headers.get(i));
        }

        // Filling in data
        for (int i = 0; i < columns.size(); i++) {
            ArrayList<?> columnData = getColumnByIndex(i);
            for (int j = 0; j < columnData.size(); j++) {
                Row row = sheet.getRow(j + 1);
                if (row == null) {
                    row = sheet.createRow(j + 1);
                }
                Cell cell = row.createCell(i);
                Object value = columnData.get(j);

                if (value != null) { // Check if value is not null
                    if (value instanceof Double) {
                        cell.setCellValue((Double) value);  // Set value as numeric
                    } else {
                        cell.setCellValue(value.toString());  // Otherwise, set value as string
                    }
                }
                // if value is null, the cell will remain empty
            }
        }

        // Auto sizing the columns
        for (int i = 0; i < headers.size(); i++) {
            sheet.autoSizeColumn(i);
        }

        // Writing to the file
        try (FileOutputStream fileOut = new FileOutputStream(fileName)) {
            workbook.write(fileOut);
        }

        if (existingWorkbook == null) {
            // Writing to the file
            try (FileOutputStream fileOut = new FileOutputStream(fileName)) {
                workbook.write(fileOut);
            }
            workbook.close();
        }
    }
    private void reformatDatePeriode() {
        SimpleDateFormat inputFormat = new SimpleDateFormat("dd/MM/yyyy");
        SimpleDateFormat outputFormat = new SimpleDateFormat("MM-yyyy");

        ArrayList<String> datePeriode = this.getColumn("Date Periode");
        ArrayList<String> reformattedDates = new ArrayList<>();

        for (String dates : datePeriode) {
            try {
                Date date = inputFormat.parse(dates);
                String reformattedDate = outputFormat.format(date);
                reformattedDates.add(reformattedDate);
            } catch (Exception e) {
                System.out.println("Error reformatting date: " + dates);
                reformattedDates.add("");  // Or handle as necessary
            }
        }

        // Assuming you have a method to set a column, you can replace the old column with the reformatted one
        this.setColumn("Date Periode", reformattedDates,STR);
    }
    @SuppressWarnings("unchecked")
    public ArrayList<Integer> generalSort(List<String> sortOrder) {
        ArrayList<Integer> indices = IntStream.range(0, columns.get(0).getData().size())
                .boxed()
                .sorted(new Comparator<Integer>() {
                    @Override
                    public int compare(Integer index1, Integer index2) {
                        for (String colName : sortOrder) {
                            int colIndex = headers.indexOf(colName);
                            if (colIndex == -1) continue;

                            ColTypes colType = columns.get(colIndex).getType();

                            if ("Année".equals(colName) && colType == STR) {
                                Integer year1 = Integer.parseInt((String) getColumnByIndex(colIndex).get(index1));
                                Integer year2 = Integer.parseInt((String) getColumnByIndex(colIndex).get(index2));
                                int result = year1.compareTo(year2);
                                if (result != 0) return result;
                            } else if ("Date Periode".equals(colName) && colType == STR) {
                                try {
                                    Date date1 = new SimpleDateFormat("MM-yyyy").parse((String) getColumnByIndex(colIndex).get(index1));
                                    Date date2 = new SimpleDateFormat("MM-yyyy").parse((String) getColumnByIndex(colIndex).get(index2));
                                    int result = date1.compareTo(date2);
                                    if (result != 0) return result;
                                } catch (ParseException e) {
                                    e.printStackTrace();
                                }
                            } else if (colType == STR) {
                                String val1 = (String) getColumnByIndex(colIndex).get(index1);
                                String val2 = (String) getColumnByIndex(colIndex).get(index2);
                                int result = val1.compareTo(val2);
                                if (result != 0) return result;
                            }
                        }
                        return 0;
                    }
                })
                .collect(Collectors.toCollection(ArrayList::new));

        // Reorder the data in all columns using the sorted order of indices
        for (Column<?> column : columns) {
            ArrayList<Object> originalData = new ArrayList<>(column.getData());
            for (int i = 0; i < indices.size(); i++) {
                ((ArrayList<Object>) column.getData()).set(i, originalData.get(indices.get(i)));
            }
        }

        // Reorder the bu list using the sorted order of indices
        ArrayList<Boolean> originalBu = new ArrayList<>(bu);
        for (int i = 0; i < indices.size(); i++) {
            bu.set(i, originalBu.get(indices.get(i)));
        }

        // Return the indices mapping
        return indices;
    }

    // MATH
    private double safeDivision(double numerator, double denominator) {
        double result = numerator / denominator;
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            return 0.0;
        }
        return result;
    }
    // Helper to round values to 4 decimal places
    static double roundToFourDecimals(double value) {
        return new BigDecimal(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
    static double roundToTwoDecimals(double value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
