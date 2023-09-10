package main.app;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.Math.min;
import static java.lang.Math.round;
import static main.app.App.*;
import static main.app.App.NA_DAT;
import static main.app.Estimate.parseObjectToDouble;
import static main.app.Synthese.ColTypes.*;


public class Synthese {
    private final ArrayList<Column<?>> columns;
    private final ArrayList<String> headers;
    private ArrayList<String> subheaders;
    public static SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");  // For example: 20230907_125959

    public static char delim = ';';
    public static Synthese refProg = new Synthese(wd+"Référentiel programmes.csv",delim,false,false,true);
    private List<Integer> refMapping;
    private ArrayList<Boolean> bu;
    public static Synthese syntAncien;

//    static {
//        try {
//            syntAncien = new Synthese(wd+"TDB Part 1 Assureur synthèse 202212 avec ICI.xlsx","Synthèse année mois",false,false,false);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    }

    private ArrayList<Integer> mapToAncien;
    public static final String[] INTEGER_COLUMNS;
    public static final String[] DOUBLE_COLUMNS;
    public static final String[] PERCENTAGE_COLUMNS;

    // column formatting types initialization block
    static {
        INTEGER_COLUMNS = new String[] {
                "Nombre Adhésions",
                "Nombre Dossier En Cours"
        };

        DOUBLE_COLUMNS = new String[] {
                "Montant Total HT",
                "Montant Total Net Compagnie",
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
                "Solde comptable émis\nyc ICI",
                "Solde comptable acquis\nyc ICI",
                "Solde technique émis\nyc ICI",
                "Solde technique acquis\nyc ICI",
                "Solde technique provisionné emis\nyc ICI",
                "Solde technique provisionné acquis\nyc ICI",
                "Sinistre Ultime",
                "Prime à l'ultime"
        };

        PERCENTAGE_COLUMNS = new String[] {
                "Taux primes émise réelle",
                "Taux d'acquisition des primes",
                "PB pour S/P acquis",
                "S/P comptable émis\nyc ICI",
                "S/P comptable acquis\nyc ICI",
                "S/P technique émis\nyc ICI",
                "S/P technique acquis\nyc ICI",
                "S/P technique provisionné émis\nyc ICI",
                "S/P technique provisionné acquis\nyc ICI",
                "S/P Comptable à l'ultime\nyc ICI"
        };
    }
    protected Map<String, ArrayList<Integer>> frequencies = new LinkedHashMap<>();
    private void writeToLogFile(String message) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_FILE_PATH, true))) { //true means append mode
            writer.write(message);
            writer.newLine(); // for new line
        } catch (IOException e) {
            System.err.println("Failed to write to log file: " + e.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
        printMemoryUsage();
        Synthese mapStatut = new Synthese(wd + "map_statuts.csv",delim,true,false,false);
        mapStatut.print();
    }
    public Synthese(String path, char delim, boolean toLower, boolean subHeader, boolean detectColtypes) {
        System.out.println(path + " debug");
        headers = new ArrayList<>();
        columns = new ArrayList<>();

        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true, delim);
        settings.trimValues(true);
        settings.setMaxColumns(8000);
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
//                    if (header.matches("\\d{2}-\\d{4}") || header.matches("\\d{4}") || cell.matches("[\\d.,\\s]+")) {
//                        columns.add(new Column<>(new ArrayList<>(), DBL));
//                    } else
                    if (cell == null) {
                        columns.add(new Column<>(new ArrayList<>(), ColTypes.STR));
                    } else if (cell.matches("\\d{2}/\\d{2}/\\d{4}")) {
                        columns.add(new Column<>(new ArrayList<>(), ColTypes.DAT));
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
    public Synthese(String path, String sheetName, boolean toLower, boolean subHeader, boolean detectColtypes) throws IOException {
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
            String headerValue = cell.getStringCellValue();
            headers.add(toLower ? headerValue.trim().toLowerCase() : headerValue.trim());
        }

        // Determine column types
        ColTypes[] detectedTypes;
        if (detectColtypes && rows.hasNext()) {
            Row typeRow = rows.next();
            detectedTypes = new ColTypes[headers.size()];

            for (int i = 0; i < headers.size(); i++) {
                String header = headers.get(i);
                Cell cell = typeRow.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                String cellValue = cell != null ? cell.getStringCellValue() : null;

                if (header.matches("\\d{2}-\\d{4}")) {
                    columns.add(new Column<>(new ArrayList<>(), ColTypes.DBL));
                    detectedTypes[i] = ColTypes.DBL;
                } else if (cellValue == null) {
                    columns.add(new Column<>(new ArrayList<>(), ColTypes.STR));
                    detectedTypes[i] = ColTypes.STR;
                } else if (cellValue.matches("\\d{2}/\\d{2}/\\d{4}")) {
                    columns.add(new Column<>(new ArrayList<>(), ColTypes.DAT));
                    detectedTypes[i] = ColTypes.DAT;
                } else if (cellValue.matches("[\\d.,\\s]+")) {
                    columns.add(new Column<>(new ArrayList<>(), ColTypes.DBL));
                    detectedTypes[i] = ColTypes.DBL;
                } else {
                    columns.add(new Column<>(new ArrayList<>(), ColTypes.STR));
                    detectedTypes[i] = ColTypes.STR;
                }
            }

            // Process this type row for actual data too
            addRowToColumns(typeRow, toLower);

        } else {
            detectedTypes = new ColTypes[headers.size()];
            Arrays.fill(detectedTypes, ColTypes.STR);
            for (int i = 0; i < headers.size(); i++) {
                columns.add(new Column<>(new ArrayList<>(), STR));
            }
        }

        // Handle subheaders
        if (subHeader && rows.hasNext()) {
            subheaders = new ArrayList<>();
            Row subHeaderRow = rows.next();
            for (Cell cell : subHeaderRow) {
                String subHeaderValue = cell.getStringCellValue();
                subheaders.add(toLower ? subHeaderValue.toLowerCase() : subHeaderValue);
            }
        }

        while (rows.hasNext()) {
            Row currentRow = rows.next();
            String[] parsedRow = new String[headers.size()];
            for (int i = 0; i < headers.size(); i++) {

                Cell currentCell = currentRow.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (currentCell != null) {
                    Object cellValue = parseCell(currentCell, detectedTypes[i], dateFormat);
                    if (cellValue != null) {
                        parsedRow[i] = cellValue.toString();
                    } else {
                        parsedRow[i] = ""; // or a default value you'd like to use in case of null
                    }
                } else {
                    parsedRow[i] = "";
                }

            }
            addRowToColumns(parsedRow, toLower);
        }

        cleanDate();
        cleanAnnees();
        dropSKPColumns();
    }
    public Synthese(Synthese external, String parMois, Synthese syntAncien) {
        headers = new ArrayList<>();
        columns = new ArrayList<>();
        refMapping = new ArrayList<>();
        bu = new ArrayList<>();

        populateRefMapping(external);
        createBUList(external);
        populateAssureur(); //prerequis refMapping!

        // Helper method for adding mapped columns
        addMappedColumn(external, "GESTIONNAIRE 1", "Gestionnaire", ColTypes.STR);
        addMappedColumn(external, "LIBELLE DISTRIBUTEUR", "Distributeur", ColTypes.STR);
        addMappedColumn(external, "Contrat", "Contrat", ColTypes.STR);
        addMappedColumn(external, "Date Periode", "Date Periode", ColTypes.STR);
        extractYearFromPeriode(); // Année
        swapColumns("Date Periode", "Année");
        cleanDistributeur();

//        List<String> sortOrder = Arrays.asList("Assureur", "Gestionnaire", "Distributeur", "Contrat", "Année", "Date Periode");
//        generalSort(sortOrder);
        insertSummaryRows();

        addMappedColumnSummed(external, "NOMBRE TOTAL ADHESIONS", "Nombre Adhésions", DBL, "Contrat");
        addMappedColumnSummed(external, "MONTANT TOTAL HT", "Montant Total HT", DBL,"Contrat");
        addMappedColumnSummed(external, "MONTANT TOTAL NET COMPAGNIE", "Montant Total Net Compagnie", DBL,"Contrat");

        // Helper for adding empty columns
        addEmptyColumn(getColumn("Contrat").size());

        // Adding columns from subheader
        addDataFromSubheaderSummed(external, "Total PA à date", "Prime Acquise à date", DBL,"Contrat");
        addDataFromSubheaderSummed(external, "PB", "Participation aux Benefices", DBL,"Contrat");
        addDataFromSubheaderSummed(external, "Comptable total", "Total Sinistres Comptable", DBL,"Contrat");

        // Add "Total Sinistres Technique" from extern's subheader "Sinistre Nombre total"
        addDataFromSubheaderSummed(external, "Sinistre total", "Total Sinistres Technique", DBL,"Contrat");

        calculateEcartSinistres();

        // Add "Nombre Dossier En Cours" from extern's subheader "Nombre en cours total"
        addDataFromSubheaderSummed(external, "Nombre en cours total", "Nombre Dossier En Cours", DBL,"Contrat");
        appendBlockSubheaderSummed(external, "Provisions: En cours", "Provision Sinistre Connu", true,"Contrat");

        populatePrimeEmiseReelle(external,"Contrat");

        calculateColumnRatio("Taux primes émise réelle", "Prime émise réelle", "Montant Total Net Compagnie");
        calculateColumnRatio("Taux d'acquisition des primes", "Prime Acquise à date", "Montant Total Net Compagnie");

        calculatePBpourSPacquis();
        calculateSPcomptableEmisYComprisICI();
        calculateSoldeComptableEmisYComprisICI();
        calculateSPcomptableAcquisYComprisICI();
        addSoldeComptableAcquisColumn();
        addSPTechniqueEmisColumn();
        addSoldeTechniqueEmisColumn();
        addSPTechniqueAcquisColumn();
        addSoldeTechniqueAcquisColumn();
        addSPTechniqueProvisionneEmisColumn();
        addSoldeTechniqueProvisionneEmisColumn();
        addSPTechniqueProvisionneAcquisColumn();
        addSoldeTechniqueProvisionneAcquisColumn();
        addDataFromSubheaderSummed(external, "Sinistre Ultime", "Sinistre Ultime", DBL,"Contrat");
        addMappedColumnSummed(external, "MONTANT TOTAL NET COMPAGNIE", "Prime à l'ultime", DBL,"Contrat");
        addSPComptableUltimateColumn();
        populateStatutContrat();


        mapToAncien = mapThisToExtern(syntAncien);
        compareColumns(syntAncien, "ADHESIONS COMPTABLE","Nombre Adhésions", "Variation adhesions comptable",false);
        compareColumns(syntAncien, "MONTANT TOTAL NET COMPAGNIE", "Montant Total Net Compagnie", "Variation des Primes émises",false);
        compareColumns(syntAncien, "PRIME ACQUISE A DATE","Prime Acquise à date", "Variation primes acquises", false);
        compareColumns(syntAncien, "Taux d'acquisition des primes","Taux d'acquisition des primes", "Variation Taux d'Acquisition", true);
        compareColumns(syntAncien, "TOTAL SINISTRES COMPTABLE", "Total Sinistres Comptable","Variation des Sinistres Comptable", false);
        compareColumns(syntAncien, "TOTAL SINISTRE TECHNIQUE", "Total Sinistres Technique","Variation des Sinistres Technique", false);
        compareColumns(syntAncien, "Provisions sur sinistres connus", "Total Provision Sinistre Connu","Variation des Provisions sur Sinistre", false);
        compareColumns(syntAncien, "S/P comptable acquis\n" + "yc ICI","S/P comptable acquis\n" + "yc ICI", "Variation S/P comptable acquis\n" + "yc ICI", true);
        compareColumns(syntAncien, "S/P technique acquis\n" + "yc ICI","S/P technique acquis\n" + "yc ICI", "Variation S/P technique acquis\n" + "yc ICI", true);
        compareColumns(syntAncien, "S/P technique provisionné acquis\n" + "yc ICI","S/P technique provisionné acquis\n" + "yc ICI", "Variation S/P technique provisionné acquis\n" + "yc ICI", true);
        compareColumns(syntAncien, "Sinistre Ultime","Sinistre Ultime", "Variation Sinistre Ultime", false);
        compareColumns(syntAncien, "S/P Comptable à l'ultime yc ICI","S/P Comptable à l'ultime\n" + "yc ICI", "Variation S/P Comptable à l'ultime\n" + "yc ICI", true);

    }
    public Synthese(Synthese external, int pourDistrib, Synthese syntAncien) {
        headers = new ArrayList<>();
        columns = new ArrayList<>();
        refMapping = new ArrayList<>();
        bu = new ArrayList<>();

        populateRefMapping(external);
        createBUList(external);
        populateAssureur(); //prerequis refMapping!

        // Helper method for adding mapped columns
        addMappedColumn(external, "GESTIONNAIRE 1", "Gestionnaire", ColTypes.STR);
        addMappedColumn(external, "LIBELLE DISTRIBUTEUR", "Distributeur", ColTypes.STR);
        addMappedColumn(external, "Contrat", "Contrat", ColTypes.STR);
        addMappedColumn(external, "Date Periode", "Date Periode", ColTypes.STR);
        extractYearFromPeriode(); // Année
        swapColumns("Date Periode", "Année");
        cleanDistributeur();

        List<String> sortOrder = Arrays.asList("Assureur", "Gestionnaire", "Distributeur", "Année", "Contrat", "Date Periode");
        generalSort(sortOrder);
        insertSummaryRowsDistrib();

        addMappedColumnSummed(external, "NOMBRE TOTAL ADHESIONS", "Nombre Adhésions", DBL,"Distributeur");
        addMappedColumnSummed(external, "MONTANT TOTAL HT", "Montant Total HT", DBL,"Distributeur");
        addMappedColumnSummed(external, "MONTANT TOTAL NET COMPAGNIE", "Montant Total Net Compagnie", DBL,"Distributeur");

        // Helper for adding empty columns
        addEmptyColumn(getColumn("Contrat").size());

        // Adding columns from subheader
        addDataFromSubheaderSummed(external, "Total PA à date", "Prime Acquise à date", DBL,"Distributeur");
        addDataFromSubheaderSummed(external, "PB", "Participation aux Benefices", DBL,"Distributeur");
        addDataFromSubheaderSummed(external, "Comptable total", "Total Sinistres Comptable", DBL,"Distributeur");

        // Add "Total Sinistres Technique" from extern's subheader "Sinistre Nombre total"
        addDataFromSubheaderSummed(external, "Sinistre total", "Total Sinistres Technique", DBL,"Distributeur");

        calculateEcartSinistres();

        // Add "Nombre Dossier En Cours" from extern's subheader "Nombre en cours total"
        addDataFromSubheaderSummed(external, "Nombre en cours total", "Nombre Dossier En Cours", DBL,"Distributeur");
        appendBlockSubheaderSummed(external, "Provisions: En cours", "Provision Sinistre Connu", true,"Distributeur");

        populatePrimeEmiseReelle(external,"Distributeur");

        calculateColumnRatio("Taux primes émise réelle", "Prime émise réelle", "Montant Total Net Compagnie");
        calculateColumnRatio("Taux d'acquisition des primes", "Prime Acquise à date", "Montant Total Net Compagnie");

        calculatePBpourSPacquis();
        calculateSPcomptableEmisYComprisICI();
        calculateSoldeComptableEmisYComprisICI();
        calculateSPcomptableAcquisYComprisICI();
        addSoldeComptableAcquisColumn();
        addSPTechniqueEmisColumn();
        addSoldeTechniqueEmisColumn();
        addSPTechniqueAcquisColumn();
        addSoldeTechniqueAcquisColumn();
        addSPTechniqueProvisionneEmisColumn();
        addSoldeTechniqueProvisionneEmisColumn();
        addSPTechniqueProvisionneAcquisColumn();
        addSoldeTechniqueProvisionneAcquisColumn();
        addDataFromSubheaderSummed(external, "Sinistre Ultime", "Sinistre Ultime", DBL,"Distributeur");
        addMappedColumnSummed(external, "MONTANT TOTAL NET COMPAGNIE", "Prime à l'ultime", DBL,"Distributeur");
        addSPComptableUltimateColumn();
        populateStatutContrat();


        mapToAncien = mapThisToExtern(syntAncien);
        compareColumns(syntAncien, "ADHESIONS COMPTABLE","Nombre Adhésions", "Variation adhesions comptable",false);
        compareColumns(syntAncien, "MONTANT TOTAL NET COMPAGNIE", "Montant Total Net Compagnie", "Variation des Primes émises",false);
        compareColumns(syntAncien, "PRIME ACQUISE A DATE","Prime Acquise à date", "Variation primes acquises", false);
        compareColumns(syntAncien, "Taux d'acquisition des primes","Taux d'acquisition des primes", "Variation Taux d'Acquisition", true);
        compareColumns(syntAncien, "TOTAL SINISTRES COMPTABLE", "Total Sinistres Comptable","Variation des Sinistres Comptable", false);
        compareColumns(syntAncien, "TOTAL SINISTRE TECHNIQUE", "Total Sinistres Technique","Variation des Sinistres Technique", false);
        compareColumns(syntAncien, "Provisions sur sinistres connus", "Total Provision Sinistre Connu","Variation des Provisions sur Sinistre", false);
        compareColumns(syntAncien, "S/P comptable acquis\n" + "yc ICI","S/P comptable acquis\n" + "yc ICI", "Variation S/P comptable acquis\n" + "yc ICI", true);
        compareColumns(syntAncien, "S/P technique acquis\n" + "yc ICI","S/P technique acquis\n" + "yc ICI", "Variation S/P technique acquis\n" + "yc ICI", true);
        compareColumns(syntAncien, "S/P technique provisionné acquis\n" + "yc ICI","S/P technique provisionné acquis\n" + "yc ICI", "Variation S/P technique provisionné acquis\n" + "yc ICI", true);
        compareColumns(syntAncien, "Sinistre Ultime","Sinistre Ultime", "Variation Sinistre Ultime", false);
        compareColumns(syntAncien, "S/P Comptable à l'ultime yc ICI","S/P Comptable à l'ultime\n" + "yc ICI", "Variation S/P Comptable à l'ultime\n" + "yc ICI", true);

    }
    public Synthese(Synthese external, double pourGest, Synthese syntAncien) {
        headers = new ArrayList<>();
        columns = new ArrayList<>();
        refMapping = new ArrayList<>();
        bu = new ArrayList<>();

        populateRefMapping(external);
        createBUList(external);
        populateAssureur(); //prerequis refMapping!

        // Helper method for adding mapped columns
        addMappedColumn(external, "GESTIONNAIRE 1", "Gestionnaire", ColTypes.STR);
        addMappedColumn(external, "LIBELLE DISTRIBUTEUR", "Distributeur", ColTypes.STR);
        addMappedColumn(external, "Contrat", "Contrat", ColTypes.STR);
        addMappedColumn(external, "Date Periode", "Date Periode", ColTypes.STR);
        extractYearFromPeriode(); // Année
        swapColumns("Date Periode", "Année");
        cleanDistributeur();

        List<String> sortOrder = Arrays.asList("Assureur", "Gestionnaire", "Année", "Distributeur", "Contrat", "Date Periode");
        generalSort(sortOrder);
        insertSummaryRowsByGestionnaire();

        addMappedColumnSummed(external, "NOMBRE TOTAL ADHESIONS", "Nombre Adhésions", DBL,"Gestionnaire");
        addMappedColumnSummed(external, "MONTANT TOTAL HT", "Montant Total HT", DBL,"Gestionnaire");
        addMappedColumnSummed(external, "MONTANT TOTAL NET COMPAGNIE", "Montant Total Net Compagnie", DBL,"Gestionnaire");

        // Helper for adding empty columns
        addEmptyColumn(getColumn("Contrat").size());

        // Adding columns from subheader
        addDataFromSubheaderSummed(external, "Total PA à date", "Prime Acquise à date", DBL,"Gestionnaire");
        addDataFromSubheaderSummed(external, "PB", "Participation aux Benefices", DBL,"Gestionnaire");
        addDataFromSubheaderSummed(external, "Comptable total", "Total Sinistres Comptable", DBL,"Gestionnaire");

        // Add "Total Sinistres Technique" from extern's subheader "Sinistre Nombre total"
        addDataFromSubheaderSummed(external, "Sinistre total", "Total Sinistres Technique", DBL,"Gestionnaire");

        calculateEcartSinistres();

        // Add "Nombre Dossier En Cours" from extern's subheader "Nombre en cours total"
        addDataFromSubheaderSummed(external, "Nombre en cours total", "Nombre Dossier En Cours", DBL,"Gestionnaire");
        appendBlockSubheaderSummed(external, "Provisions: En cours", "Provision Sinistre Connu", true,"Gestionnaire");

        populatePrimeEmiseReelle(external,"Gestionnaire");

        calculateColumnRatio("Taux primes émise réelle", "Prime émise réelle", "Montant Total Net Compagnie");
        calculateColumnRatio("Taux d'acquisition des primes", "Prime Acquise à date", "Montant Total Net Compagnie");

        calculatePBpourSPacquis();
        calculateSPcomptableEmisYComprisICI();
        calculateSoldeComptableEmisYComprisICI();
        calculateSPcomptableAcquisYComprisICI();
        addSoldeComptableAcquisColumn();
        addSPTechniqueEmisColumn();
        addSoldeTechniqueEmisColumn();
        addSPTechniqueAcquisColumn();
        addSoldeTechniqueAcquisColumn();
        addSPTechniqueProvisionneEmisColumn();
        addSoldeTechniqueProvisionneEmisColumn();
        addSPTechniqueProvisionneAcquisColumn();
        addSoldeTechniqueProvisionneAcquisColumn();
        addDataFromSubheaderSummed(external, "Sinistre Ultime", "Sinistre Ultime", DBL,"Gestionnaire");
        addMappedColumnSummed(external, "MONTANT TOTAL NET COMPAGNIE", "Prime à l'ultime", DBL,"Gestionnaire");
        addSPComptableUltimateColumn();
        populateStatutContrat();


        mapToAncien = mapThisToExtern(syntAncien);
        compareColumns(syntAncien, "ADHESIONS COMPTABLE","Nombre Adhésions", "Variation adhesions comptable",false);
        compareColumns(syntAncien, "MONTANT TOTAL NET COMPAGNIE", "Montant Total Net Compagnie", "Variation des Primes émises",false);
        compareColumns(syntAncien, "PRIME ACQUISE A DATE","Prime Acquise à date", "Variation primes acquises", false);
        compareColumns(syntAncien, "Taux d'acquisition des primes","Taux d'acquisition des primes", "Variation Taux d'Acquisition", true);
        compareColumns(syntAncien, "TOTAL SINISTRES COMPTABLE", "Total Sinistres Comptable","Variation des Sinistres Comptable", false);
        compareColumns(syntAncien, "TOTAL SINISTRE TECHNIQUE", "Total Sinistres Technique","Variation des Sinistres Technique", false);
        compareColumns(syntAncien, "Provisions sur sinistres connus", "Total Provision Sinistre Connu","Variation des Provisions sur Sinistre", false);
        compareColumns(syntAncien, "S/P comptable acquis\n" + "yc ICI","S/P comptable acquis\n" + "yc ICI", "Variation S/P comptable acquis\n" + "yc ICI", true);
        compareColumns(syntAncien, "S/P technique acquis\n" + "yc ICI","S/P technique acquis\n" + "yc ICI", "Variation S/P technique acquis\n" + "yc ICI", true);
        compareColumns(syntAncien, "S/P technique provisionné acquis\n" + "yc ICI","S/P technique provisionné acquis\n" + "yc ICI", "Variation S/P technique provisionné acquis\n" + "yc ICI", true);
        compareColumns(syntAncien, "Sinistre Ultime","Sinistre Ultime", "Variation Sinistre Ultime", false);
        compareColumns(syntAncien, "S/P Comptable à l'ultime yc ICI","S/P Comptable à l'ultime\n" + "yc ICI", "Variation S/P Comptable à l'ultime\n" + "yc ICI", true);

    }
    @SuppressWarnings("unchecked")
    public Synthese(Synthese other, String synthesePolice, boolean agreg, Synthese syntAncien) {
        // Deep copy headers, subheaders
        this.headers = new ArrayList<>(other.headers);
        this.columns = new ArrayList<>();

        // Initialize columns with deep copies
        for (Column<?> col : other.columns) {
            if (col.getType() == ColTypes.STR) {
                this.columns.add(new Column<>(new ArrayList<String>(), ColTypes.STR));
            } else if (col.getType() == ColTypes.DAT) {
                this.columns.add(new Column<>(new ArrayList<Date>(), ColTypes.DAT));
            } else if (col.getType() == ColTypes.DBL) {
                this.columns.add(new Column<>(new ArrayList<Double>(), ColTypes.DBL));
            }
            // ... Add similar blocks for FLT, SKP, or any other types you might have ...
        }

        int anneeIndex = other.headers.indexOf("Année");
        ArrayList<String> anneeColumn = null;
        if (anneeIndex != -1) {
            anneeColumn = other.getColumn("Année");
        }

        // Iterate over rows and copy only rows where "Année" starts with "Total"
        for (int rowIndex = 0; rowIndex < other.getColumnByIndex(0).size(); rowIndex++) {
            if (anneeColumn != null && anneeColumn.get(rowIndex).startsWith("Total ")) {
                // Copy this row for all columns
                for (int colIndex = 0; colIndex < other.columns.size(); colIndex++) {
                    Column<?> column = this.columns.get(colIndex);
                    Column<?> otherColumn = other.columns.get(colIndex);
                    if (column.getType() == ColTypes.STR) {
                        ((ArrayList<String>)column.getData()).add(((ArrayList<String>)otherColumn.getData()).get(rowIndex));
                    } else if (column.getType() == ColTypes.DAT) {
                        ((ArrayList<Date>)column.getData()).add(((ArrayList<Date>)otherColumn.getData()).get(rowIndex));
                    } else if (column.getType() == ColTypes.DBL) {
                        ((ArrayList<Double>)column.getData()).add(((ArrayList<Double>)otherColumn.getData()).get(rowIndex));
                    }
                    // ... Add similar blocks for other types ...
                }
            }
        }

        // Remove the "Date Periode" column
        int datePeriodeIndex = this.headers.indexOf("Date Periode");
        if (datePeriodeIndex != -1) {
            this.headers.remove(datePeriodeIndex);
            this.columns.remove(datePeriodeIndex);
        }

        // Adjust the "Année" column to remove "Total "
        if (anneeIndex != -1) {
            ArrayList<String> adjustedAnneeColumn = this.getColumn("Année");
            adjustedAnneeColumn.replaceAll(s -> s.replace("Total ", ""));
        }

        // Copy other necessary attributes
        this.refMapping = new ArrayList<>(other.refMapping);
        this.bu = new ArrayList<>(other.bu);
        // ... Copy any other necessary attributes here ...
    }
    @SuppressWarnings("unchecked")
    public Synthese(Synthese other, int syntheseDistrib, boolean agreg) {
        // Deep copy headers, subheaders
        this.headers = new ArrayList<>(other.headers);
        this.columns = new ArrayList<>();

        // Initialize columns with deep copies
        for (Column<?> col : other.columns) {
            if (col.getType() == ColTypes.STR) {
                this.columns.add(new Column<>(new ArrayList<String>(), ColTypes.STR));
            } else if (col.getType() == ColTypes.DAT) {
                this.columns.add(new Column<>(new ArrayList<Date>(), ColTypes.DAT));
            } else if (col.getType() == ColTypes.DBL) {
                this.columns.add(new Column<>(new ArrayList<Double>(), ColTypes.DBL));
            }
            // ... Add similar blocks for FLT, SKP, or any other types you might have ...
        }

        int anneeIndex = other.headers.indexOf("Année");
        ArrayList<String> anneeColumn = null;
        if (anneeIndex != -1) {
            anneeColumn = other.getColumn("Année");
        }

        // Iterate over rows and copy only rows where "Année" starts with "Total"
        for (int rowIndex = 0; rowIndex < other.getColumnByIndex(0).size(); rowIndex++) {
            if (anneeColumn != null && anneeColumn.get(rowIndex).startsWith("Total ")) {
                // Copy this row for all columns
                for (int colIndex = 0; colIndex < other.columns.size(); colIndex++) {
                    Column<?> column = this.columns.get(colIndex);
                    Column<?> otherColumn = other.columns.get(colIndex);
                    if (column.getType() == ColTypes.STR) {
                        ((ArrayList<String>)column.getData()).add(((ArrayList<String>)otherColumn.getData()).get(rowIndex));
                    } else if (column.getType() == ColTypes.DAT) {
                        ((ArrayList<Date>)column.getData()).add(((ArrayList<Date>)otherColumn.getData()).get(rowIndex));
                    } else if (column.getType() == ColTypes.DBL) {
                        ((ArrayList<Double>)column.getData()).add(((ArrayList<Double>)otherColumn.getData()).get(rowIndex));
                    }
                    // ... Add similar blocks for other types ...
                }
            }
        }

        String[] columnsToRemove = {"Date Periode", "Contrat"};

        for (String columnName : columnsToRemove) {
            int columnIndex = this.headers.indexOf(columnName);
            if (columnIndex != -1) {
                this.headers.remove(columnIndex);
                this.columns.remove(columnIndex);
            }
        }


        // Adjust the "Année" column to remove "Total "
        if (anneeIndex != -1) {
            ArrayList<String> adjustedAnneeColumn = this.getColumn("Année");
            adjustedAnneeColumn.replaceAll(s -> s.replace("Total ", ""));
        }

        // Copy other necessary attributes
        this.refMapping = new ArrayList<>(other.refMapping);
        this.bu = new ArrayList<>(other.bu);
        // ... Copy any other necessary attributes here ...
    }
    @SuppressWarnings("unchecked")
    public Synthese(Synthese other, double syntheseGest, boolean agreg) {
        // Deep copy headers, subheaders
        this.headers = new ArrayList<>(other.headers);
        this.columns = new ArrayList<>();

        // Initialize columns with deep copies
        for (Column<?> col : other.columns) {
            if (col.getType() == ColTypes.STR) {
                this.columns.add(new Column<>(new ArrayList<String>(), ColTypes.STR));
            } else if (col.getType() == ColTypes.DAT) {
                this.columns.add(new Column<>(new ArrayList<Date>(), ColTypes.DAT));
            } else if (col.getType() == ColTypes.DBL) {
                this.columns.add(new Column<>(new ArrayList<Double>(), ColTypes.DBL));
            }
            // ... Add similar blocks for FLT, SKP, or any other types you might have ...
        }

        int anneeIndex = other.headers.indexOf("Année");
        ArrayList<String> anneeColumn = null;
        if (anneeIndex != -1) {
            anneeColumn = other.getColumn("Année");
        }

        // Iterate over rows and copy only rows where "Année" starts with "Total"
        for (int rowIndex = 0; rowIndex < other.getColumnByIndex(0).size(); rowIndex++) {
            if (anneeColumn != null && anneeColumn.get(rowIndex).startsWith("Total ")) {
                // Copy this row for all columns
                for (int colIndex = 0; colIndex < other.columns.size(); colIndex++) {
                    Column<?> column = this.columns.get(colIndex);
                    Column<?> otherColumn = other.columns.get(colIndex);
                    if (column.getType() == ColTypes.STR) {
                        ((ArrayList<String>)column.getData()).add(((ArrayList<String>)otherColumn.getData()).get(rowIndex));
                    } else if (column.getType() == ColTypes.DAT) {
                        ((ArrayList<Date>)column.getData()).add(((ArrayList<Date>)otherColumn.getData()).get(rowIndex));
                    } else if (column.getType() == ColTypes.DBL) {
                        ((ArrayList<Double>)column.getData()).add(((ArrayList<Double>)otherColumn.getData()).get(rowIndex));
                    }
                    // ... Add similar blocks for other types ...
                }
            }
        }

        String[] columnsToRemove = {"Date Periode", "Contrat", "Distributeur"};

        for (String columnName : columnsToRemove) {
            int columnIndex = this.headers.indexOf(columnName);
            if (columnIndex != -1) {
                this.headers.remove(columnIndex);
                this.columns.remove(columnIndex);
            }
        }


        // Adjust the "Année" column to remove "Total "
        if (anneeIndex != -1) {
            ArrayList<String> adjustedAnneeColumn = this.getColumn("Année");
            adjustedAnneeColumn.replaceAll(s -> s.replace("Total ", ""));
        }

        // Copy other necessary attributes
        this.refMapping = new ArrayList<>(other.refMapping);
        this.bu = new ArrayList<>(other.bu);
        // ... Copy any other necessary attributes here ...
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
        ArrayList<String> externColumnStr = extern.getColumn(externColName);

        ArrayList<Double> externColumn = new ArrayList<>();
        for (String val : externColumnStr) {
            try {
                externColumn.add(Double.parseDouble(val.replace(",", ".")));
            } catch (NumberFormatException e) {
                externColumn.add(0.0);
            }
        }

        ArrayList<Integer> mapToExtern = mapThisToExtern(extern);
        ArrayList<String> deltaColumn = new ArrayList<>();

        for (int i = 0; i < thisColumn.size(); i++) {
            int externIndex = mapToExtern.get(i);
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
                        deltaColumn.add("+" + Double.toString(roundToTwoDecimals(diff)));
                    } else if (diff < 0) {
                        deltaColumn.add(Double.toString(roundToTwoDecimals(diff)));
                    } else {
                        deltaColumn.add("0");
                    }
                }
            }
        }


        this.addColumn(newColName, deltaColumn, ColTypes.STR);
    }
    @SuppressWarnings("unchecked")
    public void generalSort(List<String> sortOrder) {
        ArrayList<Integer> indices = IntStream.range(0, columns.get(0).getData().size()).boxed().sorted(new Comparator<Integer>() {
            @Override
            public int compare(Integer index1, Integer index2) {
                for (String colName : sortOrder) {
                    int colIndex = headers.indexOf(colName);
                    if (colIndex == -1) continue;

                    ColTypes colType = columns.get(colIndex).getType();

                    if ("Année".equals(colName) && colType == ColTypes.STR) {
                        Integer year1 = Integer.parseInt((String) getColumnByIndex(colIndex).get(index1));
                        Integer year2 = Integer.parseInt((String) getColumnByIndex(colIndex).get(index2));
                        int result = year1.compareTo(year2);
                        if (result != 0) return result;
                    } else if ("Date Periode".equals(colName) && colType == ColTypes.STR) {
                        try {
                            Date date1 = new SimpleDateFormat("MM-yyyy").parse((String) getColumnByIndex(colIndex).get(index1));
                            Date date2 = new SimpleDateFormat("MM-yyyy").parse((String) getColumnByIndex(colIndex).get(index2));
                            int result = date1.compareTo(date2);
                            if (result != 0) return result;
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                    } else if (colType == ColTypes.STR) {
                        String val1 = (String) getColumnByIndex(colIndex).get(index1);
                        String val2 = (String) getColumnByIndex(colIndex).get(index2);
                        int result = val1.compareTo(val2);
                        if (result != 0) return result;
                    }
                }
                return 0;
            }
        }).collect(Collectors.toCollection(ArrayList::new));

// Sort the indices

        // Reorder the data in all columns using the sorted order of indices
        for (Column<?> column : columns) {
            ArrayList<Object> originalData = new ArrayList<>(column.getData());
            for (int i = 0; i < indices.size(); i++) {
                ((ArrayList<Object>) column.getData()).set(i, originalData.get(indices.get(i)));
            }
        }

    }



    // SUMMARIES
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

    // CELL OPERATIONS
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
    private Object getCellOfType(Cell cell, ColTypes type, boolean toLower) {
        switch (type) {
            case STR -> {
                String strValue = cell.toString();
                return strValue == null ? "" : (toLower ? strValue.toLowerCase().trim() : strValue.trim());
            }
            case DBL -> {
                try {
                    return cell.getNumericCellValue();
                } catch (Exception ignored) {
                    return 0d;
                }
            }
            case DAT -> {
                try {
                    return cell.getDateCellValue();
                } catch (Exception e) {
                    return null;
                }
            }
            default -> {
                return cell.toString();
            }
        }
    } // EXCEL
    public Object getCellOfType(String cell, ColTypes type, SimpleDateFormat dateFormatter) {
        Object out = "";
        switch (type) {
            case STR -> {
                if (cell == null) return "";
                return cell.trim();
            }
            case DBL -> {
                if (cell == null) return 0d;
                try {
                    return Double.parseDouble(cell.replace(",", ".").replace(" €", ""));
                } catch (NumberFormatException ignored) {
                    return NA_DBL;
                }
            }
            case DAT -> {
                if (cell == null) return NA_DAT;
                // Purify the cell if the date format is "#yyyy-MM-dd#"

                if (cell.length() == 5) {
                    try {
                        // If the purified cell has exactly 5 characters, interpret it as a numeric Excel date
                        double dateValue = Double.parseDouble(cell);
                        return DateUtil.getJavaDate(dateValue);
                    } catch (NumberFormatException ignored) {
                        return NA_DAT;
                    }
                } else {
                    try {
                        // Otherwise, try to parse the date using the specified format
                        return dateFormatter.parse(cell);
                    } catch (ParseException ignored) {
                        return NA_DAT;
                    }
                }
            }
        }
        return out;
    } // EXCEL
    private String formatCell(String cell, int width) {
        if (cell.length() > width) {
            return cell.substring(0, width);  // Trim to the fixed width
        } else {
            return String.format("%-" + width + "s", cell);  // Pad with spaces to the fixed width
        }
    }
    public Object parseCell_old(Cell cell_i, ColTypes colType, SimpleDateFormat dateFormatter) {
        Object cellValue = null;
        if (cell_i.getCellType() == CellType.FORMULA) {
            switch (cell_i.getCachedFormulaResultType()) {
                case ERROR -> cellValue = getCellOfType(cell_i.getCellFormula(), colType, dateFormatter); // bad formula
                case STRING ->
                        cellValue = getCellOfType(cell_i.getStringCellValue(), colType, dateFormatter); // good formula

                // You may handle other formula result types if needed.
            }
        } else {
            if (cell_i.getCellType() == CellType.NUMERIC) {
                if (DateUtil.isCellDateFormatted(cell_i)) {
                    return cell_i.getDateCellValue();
                } else {
                    // Handle numeric cells based on the expected Col_types
                    if (colType == DBL) {
                        return cell_i.getNumericCellValue(); // return the numeric value directly for DBL type
                    } else if (colType == STR) {
                        return Double.toString(cell_i.getNumericCellValue()); // convert to string for STR type
                    }
                }
            } else {
                cellValue = getCellOfType(cell_i.getStringCellValue(), colType, dateFormatter); // no formula
            }
        }
        return cellValue;
    } // EXCEL
    public Object parseCell(Cell cell_i, ColTypes colType, SimpleDateFormat dateFormatter) {
        switch (cell_i.getCellType()) {
            case FORMULA -> {
                return switch (cell_i.getCachedFormulaResultType()) {
                    case ERROR -> getCellOfType(cell_i.getCellFormula(), colType, dateFormatter);
                    case STRING -> getCellOfType(cell_i.getStringCellValue(), colType, dateFormatter);
                    case BOOLEAN ->
                            cell_i.getBooleanCellValue();  // or however you want to handle boolean formula results
                    // ... handle other formula result types if needed ...
                    default -> null; // or some default value
                };
            }
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell_i)) {
                    return cell_i.getDateCellValue();
                } else if (colType == DBL) {
                    return cell_i.getNumericCellValue();
                } else if (colType == STR) {
                    return Double.toString(cell_i.getNumericCellValue());
                }
                return null; // or some default value
            }
            case STRING -> {
                return getCellOfType(cell_i.getStringCellValue(), colType, dateFormatter);
            }
            case BOOLEAN -> {
                return cell_i.getBooleanCellValue();  // or convert it to string or whatever suits your need
            }
            case BLANK -> {
                return "";  // or whatever your default value for blank cells is
            }
            case ERROR -> {
                return "ERROR";  // or handle in a specific way if needed
            }
            default -> {
                return null; // or some default value
            }
        }
    }

    // ROWS AND COLS
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
    private void cleanDate() {
        ArrayList<String> dateColumn = this.getColumn("date");

        // This list will hold the cleaned dates in the MM-yyyy format.
        ArrayList<String> cleanedDates = new ArrayList<>();

        // Define the source format based on the existing format in the column.
        SimpleDateFormat sourceDateFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.ENGLISH);

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
            columns.set(dateColIndex, new Column<>(cleanedDates, ColTypes.STR));
        }
    }
    private void cleanAnnees() {
        ArrayList<String> anneesColumn = this.getColumn("Années");

        // Create a new list to store the cleaned entries.
        ArrayList<String> cleanedAnnees = new ArrayList<>();

        for (String entry : anneesColumn) {
            if (!entry.startsWith("Total")) {
                entry = entry.replace(".0", "");
            }
            cleanedAnnees.add(entry);
        }

        // Replace the original "Années" column with the cleaned entries.
        int anneesColIndex = headers.indexOf("Années");
        if (anneesColIndex != -1) {
            columns.set(anneesColIndex, new Column<>(cleanedAnnees, ColTypes.STR));
        }
    }
    private ColTypes[] detectColumnTypesXlsx(Row headerRow, int size) {
        ColTypes[] detectedTypes = new ColTypes[size];
        Arrays.fill(detectedTypes, ColTypes.STR);  // Default all columns to STR

        for (Cell c : headerRow) {
            CellType cellType = c.getCellType();
            if (cellType == CellType.NUMERIC) {
                if (DateUtil.isCellDateFormatted(c)) {
                    detectedTypes[c.getColumnIndex()] = ColTypes.DAT;
                } else {
                    detectedTypes[c.getColumnIndex()] = DBL;
                }
            } // No need for an 'else' branch, as the array is already filled with STR
        }

        return detectedTypes;
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
    private <T> void addRowToColumns(String[] row, boolean toLower) {
        for (int i = 0; i < min(row.length, columns.size()); i++) {
            Column<T> col = (Column<T>) columns.get(i);
            T cell = (T) getCellOfType(row[i], col.getType(), toLower);
            col.getData().add(cell);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void addRowToColumns(Row row, boolean toLower) {
        for (Cell cell : row) {
            int columnIndex = cell.getColumnIndex();
            if (columnIndex >= columns.size()) continue; // skip if there's no corresponding column
            Column<T> col = (Column<T>) columns.get(columnIndex);
            T cellValue = (T) getCellOfType(cell, col.getType(), toLower);
            col.getData().add(cellValue);
        }
    } // EXCEL

    // PRINTING
    public void print() {
        print(10);  // Default to 10 rows
    }
    public void print(int numRows) {
        final int fixedWidth = 20;

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
                row.add(col.getData().get(i).toString());
            }
            printRow(row, fixedWidth);
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
    public void printMeanForDoubleColumns() {
        for (int i = 0; i < columns.size(); i++) {
            Column<?> currentColumn = columns.get(i);

            if (currentColumn.getType() == ColTypes.DBL) {
                ArrayList<Double> columnData = getColumnByIndex(i);

                // Calculate the mean value for the current column
                double sum = 0.0;
                for (Double value : columnData) {
                    sum += value;
                }
                double meanValue = sum / columnData.size();

                // Print column name and its mean value
                System.out.println("Column: " + headers.get(i) + ", Mean Value: " + meanValue);
            }
        }
    }
    public void printRowsForContrat(String contratValue, String userColumn) {
        int contratIndex = headers.indexOf("Contrat");
        int datePeriodeIndex = headers.indexOf("Date Periode");
        int userColumnIndex = headers.indexOf(userColumn);

        if (contratIndex == -1) {
            System.out.println("Contrat column not found.");
            return;
        }

        if (datePeriodeIndex == -1) {
            System.out.println("Date Periode column not found.");
            return;
        }

        if (userColumnIndex == -1) {
            System.out.println(userColumn + " column not found.");
            return;
        }

        final int fixedWidth = 20;

        // Print headers for Date Periode and user column
        System.out.println(padRight("Date Periode", fixedWidth) + padRight(userColumn, fixedWidth));

        // Retrieve the column for "Contrat"
        ArrayList<String> contratColumn = getColumnByIndex(contratIndex);
        ArrayList<String> datePeriodeColumn = getColumnByIndex(datePeriodeIndex);
        ArrayList<String> userInputColumn = getColumnByIndex(userColumnIndex);

        // Iterate over each row
        for (int rowIndex = 0; rowIndex < contratColumn.size(); rowIndex++) {
            if (contratColumn.get(rowIndex).equals(contratValue)) {
                String datePeriodeValue = datePeriodeColumn.get(rowIndex);
                String userInputValue = String.valueOf(userInputColumn.get(rowIndex));
                System.out.println(padRight(datePeriodeValue, fixedWidth) + padRight(userInputValue, fixedWidth));
            }
        }
    }


    private void printRow(List<String> row, int width) {
        for (String cell : row) {
            System.out.print(padRight(cell, width));
        }
        System.out.println();
    }
    private String padRight(String s, int width) {
        return String.format("%-" + width + "s", s);
    }


    // MATH
    private double safeDivision(double numerator, double denominator) {
        double result = 0.0;
        if (denominator != 0) {
            result = numerator / denominator;
            if (Double.isNaN(result) || Double.isInfinite(result)) {
                result = 0.0;
            }
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

    // ACTUARIAT
    private void createBUList(Synthese external) {
        ArrayList<String> fluxData = external.getColumn("Flux");
        for (String fluxValue : fluxData) {
            bu.add("BU".equals(fluxValue));
        }
    }
    private void populateRefMapping () {
        // Get the Contrat data first and generate its mapping to refProg
        ArrayList<String> contratData = getColumn("Contrat");
        for (String contrat : contratData) {
            int refIndex = refProg.getColumn("N°CONTRAT").indexOf(contrat);
            refMapping.add(refIndex);
        }
    }
    private void populateRefMapping (Synthese external) {
        // Get the Contrat data first and generate its mapping to refProg
        ArrayList<String> contratData = external.getColumn("Contrat");
        for (String contrat : contratData) {
            int refIndex = refProg.getColumn("N°CONTRAT").indexOf(contrat);
            refMapping.add(refIndex);
        }
    }
    private void populateAssureur() {
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
    }
    private void extractYearFromPeriode() {
        ArrayList<String> datePeriodeData = this.getColumn("Date Periode");
        ArrayList<String> anneesData = new ArrayList<>();
        for (String date : datePeriodeData) {
            String year = date.split("-")[1];
            anneesData.add(year);
        }
        this.addColumn("Année", anneesData, ColTypes.STR);
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

    private void calculateColumnRatio(String columnName, String numeratorColumn, String denominatorColumn) {
        ArrayList<Double> numeratorData = this.getColumn(numeratorColumn);
        ArrayList<Double> denominatorData = this.getColumn(denominatorColumn);
        ArrayList<Double> ratioData = new ArrayList<>();

        for (int i = 0; i < numeratorData.size(); i++) {
            double value;
            try {
                value = numeratorData.get(i) / denominatorData.get(i);
                if (Double.isNaN(value) || Double.isInfinite(value)) {
                    value = 0.0;
                } else if ("Taux d'acquisition des primes".equals(columnName) && value > 1) {
                    value = 1.0;
                }
            } catch (Exception e) {
                value = 0.0;
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
            result.add(BigDecimal.valueOf(participationBeneficesData.get(i) * tauxAcquisitionPrimesData.get(i)).setScale(4, RoundingMode.HALF_UP).doubleValue());
        }

        this.addColumn("PB pour S/P acquis", result, DBL);
    }
    private void calculateSPcomptableEmisYComprisICI() {
        ArrayList<Double> totalSinistresComptableData = getColumn("Total Sinistres Comptable");
        ArrayList<Double> montantTotalNetCompagnieData = getColumn("Montant Total Net Compagnie");
        ArrayList<Double> primeAcquiseAdateData = getColumn("Prime Acquise à date");
        ArrayList<Double> participationBeneficesData = getColumn("Participation aux Benefices");

        ArrayList<Double> result = new ArrayList<>();

        for (int i = 0; i < totalSinistresComptableData.size(); i++) {
            double denominator = montantTotalNetCompagnieData.get(i) + primeAcquiseAdateData.get(i) + participationBeneficesData.get(i);
            double value = totalSinistresComptableData.get(i) / denominator;
            if (Double.isInfinite(value) || Double.isNaN(value)) {
                result.add(0.0); // or whatever default value you'd like to use
            } else {
                result.add(new BigDecimal(value).setScale(4, RoundingMode.HALF_UP).doubleValue());
            }

        }

        this.addColumn("S/P comptable émis\n" + "yc ICI", result, DBL);
    }
    private void calculateSoldeComptableEmisYComprisICI() {
        ArrayList<Double> montantTotalNetCompagnieData = getColumn("Montant Total Net Compagnie");
        ArrayList<Double> participationBeneficesData = getColumn("Participation aux Benefices");
        ArrayList<Double> totalSinistresComptableData = getColumn("Total Sinistres Comptable");

        ArrayList<Double> result = new ArrayList<>();

        for (int i = 0; i < montantTotalNetCompagnieData.size(); i++) {
            double value = montantTotalNetCompagnieData.get(i) + participationBeneficesData.get(i) - totalSinistresComptableData.get(i);
            if (Double.isInfinite(value) || Double.isNaN(value)) {
                result.add(0.0); // or whatever default value you'd like to use
            } else {
                result.add(new BigDecimal(value).setScale(4, RoundingMode.HALF_UP).doubleValue());
            }
        }

        this.addColumn("Solde comptable émis\n" + "yc ICI", result, DBL);
    }
    private void calculateSPcomptableAcquisYComprisICI() {
        ArrayList<Double> totalSinistresComptableData = getColumn("Total Sinistres Comptable");
        ArrayList<Double> primeAcquiseAdateData = getColumn("Prime Acquise à date");
        ArrayList<Double> pbPourSPacquisData = getColumn("PB pour S/P acquis");

        ArrayList<Double> result = new ArrayList<>();

        for (int i = 0; i < totalSinistresComptableData.size(); i++) {
            double denominator = primeAcquiseAdateData.get(i) + pbPourSPacquisData.get(i);
            double value = totalSinistresComptableData.get(i) / denominator;
            if (Double.isInfinite(value) || Double.isNaN(value)) {
                result.add(0.0); // or whatever default value you'd like to use
            } else {
                result.add(new BigDecimal(value).setScale(4, RoundingMode.HALF_UP).doubleValue());
            }
        }

        this.addColumn("S/P comptable acquis\n" + "yc ICI", result, DBL);
    }
    private void addSoldeComptableAcquisColumn() {
        ArrayList<Double> primeAcquiseAdateData = this.getColumn("Prime Acquise à date");
        ArrayList<Double> participationAuxBeneficesData = this.getColumn("Participation aux Benefices");
        ArrayList<Double> totalSinistresComptableData = this.getColumn("Total Sinistres Comptable");
        ArrayList<Double> soldeComptableAcquisData = new ArrayList<>();

        for (int i = 0; i < primeAcquiseAdateData.size(); i++) {
            double value = primeAcquiseAdateData.get(i) + participationAuxBeneficesData.get(i) - totalSinistresComptableData.get(i);
            soldeComptableAcquisData.add(roundToFourDecimals(value));
        }
        this.addColumn("Solde comptable acquis\n" + "yc ICI", soldeComptableAcquisData, DBL);
    }
    private void addSPTechniqueEmisColumn() {
        ArrayList<Double> totalSinistreTechniqueData = this.getColumn("Total Sinistres Technique");
        ArrayList<Double> montantTotalNetCompagnieData = this.getColumn("Montant Total Net Compagnie");
        ArrayList<Double> participationAuxBeneficesData = this.getColumn("Participation aux Benefices");
        ArrayList<Double> spTechniqueEmisData = new ArrayList<>();

        for (int i = 0; i < totalSinistreTechniqueData.size(); i++) {
            double denominator = montantTotalNetCompagnieData.get(i) + participationAuxBeneficesData.get(i);
            double value = safeDivision(totalSinistreTechniqueData.get(i), denominator);
            spTechniqueEmisData.add(roundToFourDecimals(value));
        }
        this.addColumn("S/P technique émis\n" + "yc ICI", spTechniqueEmisData, DBL);
    }
    private void addSoldeTechniqueEmisColumn() {
        ArrayList<Double> montantTotalNetCompagnieData = this.getColumn("Montant Total Net Compagnie");
        ArrayList<Double> participationAuxBeneficesData = this.getColumn("Participation aux Benefices");
        ArrayList<Double> totalSinistreTechniqueData = this.getColumn("Total Sinistres Technique");
        ArrayList<Double> soldeTechniqueEmisData = new ArrayList<>();

        for (int i = 0; i < montantTotalNetCompagnieData.size(); i++) {
            double value = montantTotalNetCompagnieData.get(i) + participationAuxBeneficesData.get(i) - totalSinistreTechniqueData.get(i);
            soldeTechniqueEmisData.add(roundToFourDecimals(value));
        }
        this.addColumn("Solde technique émis\n" + "yc ICI", soldeTechniqueEmisData, DBL);
    }
    private void addSPTechniqueAcquisColumn() {
        ArrayList<Double> totalSinistreTechniqueData = this.getColumn("Total Sinistres Technique");
        ArrayList<Double> primeAcquiseAdateData = this.getColumn("Prime Acquise à date");
        ArrayList<Double> pbPourSPAcquisData = this.getColumn("PB pour S/P acquis");
        ArrayList<Double> spTechniqueAcquisData = new ArrayList<>();

        for (int i = 0; i < totalSinistreTechniqueData.size(); i++) {
            double denominator = primeAcquiseAdateData.get(i) + pbPourSPAcquisData.get(i);
            double value = safeDivision(totalSinistreTechniqueData.get(i), denominator);
            spTechniqueAcquisData.add(roundToFourDecimals(value));
        }
        this.addColumn("S/P technique acquis\n" + "yc ICI", spTechniqueAcquisData, DBL);
    }
    private void addSoldeTechniqueAcquisColumn() {
        ArrayList<Double> primeAcquiseAdateData = this.getColumn("Prime Acquise à date");
        ArrayList<Double> participationAuxBeneficesData = this.getColumn("Participation aux Benefices");
        ArrayList<Double> totalSinistreTechniqueData = this.getColumn("Total Sinistres Technique");
        ArrayList<Double> soldeTechniqueAcquisData = new ArrayList<>();

        for (int i = 0; i < primeAcquiseAdateData.size(); i++) {
            double value = primeAcquiseAdateData.get(i) + participationAuxBeneficesData.get(i) - totalSinistreTechniqueData.get(i);
            soldeTechniqueAcquisData.add(roundToFourDecimals(value));
        }
        this.addColumn("Solde technique acquis\n" + "yc ICI", soldeTechniqueAcquisData, DBL);
    }
    private void addSPTechniqueProvisionneEmisColumn() {
        ArrayList<Double> totalSinistreTechniqueData = this.getColumn("Total Sinistres Technique");
        ArrayList<Double> provisionSinistreConnuData = this.getColumn("Total Provision Sinistre Connu");
        ArrayList<Double> montantTotalNetCompagnieData = this.getColumn("Montant Total Net Compagnie");
        ArrayList<Double> participationAuxBeneficesData = this.getColumn("Participation aux Benefices");
        ArrayList<Double> spTechniqueProvisionneEmisData = new ArrayList<>();

        for (int i = 0; i < totalSinistreTechniqueData.size(); i++) {
            double numerator = totalSinistreTechniqueData.get(i) + provisionSinistreConnuData.get(i);
            double denominator = montantTotalNetCompagnieData.get(i) + participationAuxBeneficesData.get(i);
            double value = safeDivision(numerator, denominator);
            spTechniqueProvisionneEmisData.add(roundToFourDecimals(value));
        }
        this.addColumn("S/P technique provisionné émis\n" + "yc ICI", spTechniqueProvisionneEmisData, DBL);
    }
    private void addSoldeTechniqueProvisionneEmisColumn() {
        ArrayList<Double> montantTotalNetCompagnieData = this.getColumn("Montant Total Net Compagnie");
        ArrayList<Double> participationAuxBeneficesData = this.getColumn("Participation aux Benefices");
        ArrayList<Double> totalSinistreTechniqueData = this.getColumn("Total Sinistres Technique");
        ArrayList<Double> provisionSinistreConnuData = this.getColumn("Total Provision Sinistre Connu");
        ArrayList<Double> soldeTechniqueProvisionneEmisData = new ArrayList<>();

        for (int i = 0; i < montantTotalNetCompagnieData.size(); i++) {
            double value = montantTotalNetCompagnieData.get(i) + participationAuxBeneficesData.get(i) - totalSinistreTechniqueData.get(i) - provisionSinistreConnuData.get(i);
            soldeTechniqueProvisionneEmisData.add(roundToFourDecimals(value));
        }
        this.addColumn("Solde technique provisionné emis\n" + "yc ICI", soldeTechniqueProvisionneEmisData, DBL);
    }
    private void addSPTechniqueProvisionneAcquisColumn() {
        ArrayList<Double> totalSinistreTechniqueData = this.getColumn("Total Sinistres Technique");
        ArrayList<Double> provisionSinistreConnuData = this.getColumn("Total Provision Sinistre Connu");
        ArrayList<Double> montantTotalNetCompagnieData = this.getColumn("Montant Total Net Compagnie");
        ArrayList<Double> pbPourSPAcquisData = this.getColumn("PB pour S/P acquis");
        ArrayList<Double> spTechniqueProvisionneAcquisData = new ArrayList<>();

        for (int i = 0; i < totalSinistreTechniqueData.size(); i++) {
            double numerator = totalSinistreTechniqueData.get(i) + provisionSinistreConnuData.get(i);
            double denominator = montantTotalNetCompagnieData.get(i) + pbPourSPAcquisData.get(i);
            double value = safeDivision(numerator, denominator);
            spTechniqueProvisionneAcquisData.add(roundToFourDecimals(value));
        }
        this.addColumn("S/P technique provisionné acquis\n" + "yc ICI", spTechniqueProvisionneAcquisData, DBL);
    }
    private void addSoldeTechniqueProvisionneAcquisColumn() {
        ArrayList<Double> montantTotalNetCompagnieData = this.getColumn("Montant Total Net Compagnie");
        ArrayList<Double> pbPourSPAcquisData = this.getColumn("PB pour S/P acquis");
        ArrayList<Double> totalSinistreTechniqueData = this.getColumn("Total Sinistres Technique");
        ArrayList<Double> provisionSinistreConnuData = this.getColumn("Total Provision Sinistre Connu");
        ArrayList<Double> soldeTechniqueProvisionneAcquisData = new ArrayList<>();

        for (int i = 0; i < montantTotalNetCompagnieData.size(); i++) {
            double value = montantTotalNetCompagnieData.get(i) + pbPourSPAcquisData.get(i) - totalSinistreTechniqueData.get(i) - provisionSinistreConnuData.get(i);
            soldeTechniqueProvisionneAcquisData.add(roundToFourDecimals(value));
        }
        this.addColumn("Solde technique provisionné acquis\n" + "yc ICI", soldeTechniqueProvisionneAcquisData, DBL);
    }
    private void addSPComptableUltimateColumn() {
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
        this.addColumn("S/P Comptable à l'ultime\n" + "yc ICI", spComptableUltimateData, DBL);
    }
    private void populateStatutContrat() {
        // Use the mapping to generate the Assureur column
        this.populateRefMapping();
        ArrayList<String> assureurData = new ArrayList<>();
        for (int refIndex : refMapping) {
            if (refIndex != -1) {
                assureurData.add((String) refProg.getColumn("STATUT CONTRAT COMPTABLE").get(refIndex));
            } else {
                assureurData.add("");  // or some default value if not found
            }
        }

        // Insert Assureur before Contrat
        this.addColumn("Statut contrat", assureurData, ColTypes.STR);
    }
    private void cleanDistributeur() {
        ArrayList<String> distributeurData = this.getColumn("Distributeur");
        ArrayList<String> contratData = this.getColumn("Contrat");

        for (int i = 0; i < distributeurData.size(); i++) {
            String currentDistributeur = distributeurData.get(i);

            // Check if current Distributeur value is missing or empty
            if (currentDistributeur == null || currentDistributeur.isEmpty()) {
                String currentContrat = contratData.get(i);

                // Find another non-empty Distributeur for the same Contrat
                String newDistributeur = null;
                for (int j = 0; j < contratData.size(); j++) {
                    if (contratData.get(j).equals(currentContrat) && !distributeurData.get(j).isEmpty()) {
                        newDistributeur = distributeurData.get(j);
                        break;
                    }
                }

                // If still no Distributeur found, get from refProg
                if (newDistributeur == null) {
                    int refIndex = refMapping.get(i);  // Assuming refMapping has been populated prior to this
                    if (refIndex != -1) {
                        newDistributeur = (String) refProg.getColumn("LIBELLE_DISTRIBUTEUR").get(refIndex);
                    }
                }

                // Update the Distributeur value
                distributeurData.set(i, newDistributeur != null ? newDistributeur : "");  // Default to empty if still null
            }
        }
    }

    // DATA POPULATION
    private void addEmptyColumn(int size) {
        ArrayList<String> fillerData = new ArrayList<>(Collections.nCopies(size, ""));
        this.addColumn("xxx", fillerData, ColTypes.STR);
    }
    private void addMappedColumn(Synthese external, String sourceColName, String targetColName, ColTypes type) {
        ArrayList<?> data = external.getColumn(sourceColName);
        this.addColumn(targetColName, new ArrayList<>(data), type);
    }
    private void addMappedColumnSummed(Synthese external, String sourceColName, String targetColName, ColTypes type, String colToAggregare) {
        List<String> anneeCol = this.getColumn("Année");
        List<String> contratCol = this.getColumn(colToAggregare);

        ArrayList<Double> sourceData = external.getColumn(sourceColName); // Assuming the source data are of type Double

        ArrayList<Double> targetData = new ArrayList<>(); // to store augmented data
        double currentSum = 0.0;
        double sumOfSums = 0.0;

        for (int i = 0, j = 0; i < anneeCol.size(); i++) {
            Double currentValue = (j < sourceData.size()) ? parseObjectToDouble(sourceData.get(j)) : 0.0; // Protect against index out of bounds

            // Check for the "Total" prefix and reset values as needed
            if (anneeCol.get(i).startsWith("Total")) {
                targetData.add(roundToTwoDecimals(currentSum));
                sumOfSums += currentSum;
                currentSum = 0.0; // reset current sum
            } else if (contratCol.get(i).startsWith("Total")) {
                targetData.add(roundToTwoDecimals(sumOfSums));
                currentSum = 0.0; // reset current sum
                sumOfSums = 0.0;  // reset sum of sums
            } else {
                currentSum += currentValue;
                targetData.add(currentValue);
                j++; // Increment the iterator for the sourceData
            }
        }

        this.addColumn(targetColName, new ArrayList<>(targetData), type);
    }
    private void addDataFromSubheader(Synthese external, String subheaderName, String newColumnName, ColTypes type) {
        int columnIndex = external.subheaders.indexOf(subheaderName);
        if (columnIndex == -1) return;

        ColTypes externalColumnType = external.columns.get(columnIndex).getType();
        ArrayList<?> rawData = external.getColumnByIndex(columnIndex);

        if (type == DBL && externalColumnType == ColTypes.STR) {
            ArrayList<Double> transformedData = new ArrayList<>();

            for (Object item : rawData) {
                String value = (String) item;

                try {
                    transformedData.add(Double.parseDouble(value));
                } catch (NumberFormatException e) {
                    transformedData.add(0.0);  // Default to 0 for non-numeric or empty values
                }
            }

            this.addColumn(newColumnName, transformedData, type);
        } else {
            this.addColumn(newColumnName, new ArrayList<>(rawData), type);
        }
    }
    private void addDataFromSubheaderSummed(Synthese external, String subheaderName, String newColumnName, ColTypes type, String colToAggregare) {
        int columnIndex = external.subheaders.indexOf(subheaderName);
        if (columnIndex == -1) return;

        ColTypes externalColumnType = external.columns.get(columnIndex).getType();
        ArrayList<?> rawData = external.getColumnByIndex(columnIndex);
        ArrayList<Double> transformedData = new ArrayList<>();

        List<String> anneeCol = this.getColumn("Année");
        List<String> contratCol = this.getColumn(colToAggregare);

        double currentSum = 0.0;
        double sumOfSums = 0.0;

        int i = 0;
        int j = 0;
        while (i < anneeCol.size()) {
            Double currentValue;
            if (type == DBL && externalColumnType == ColTypes.STR) {
                String value = (String) rawData.get(j);
                try {
                    currentValue = Double.parseDouble(value.replace(',','.'));
                } catch (NumberFormatException e) {
                    currentValue = 0.0;  // Default to 0 for non-numeric or empty values
                }
            } else {
                currentValue = (Double) rawData.get(j);  // Assuming other columns are all of type Double
            }

            if (anneeCol.get(i).startsWith("Total")) {
                transformedData.add(roundToTwoDecimals(currentSum));
                sumOfSums += currentSum;
                currentSum = 0.0; // reset current sum
                i++;
            } else if (contratCol.get(i).startsWith("Total")) {
                transformedData.add(roundToTwoDecimals(sumOfSums));
                currentSum = 0.0; // reset current sum
                sumOfSums = 0.0;  // reset sum of sums
                i++;
            } else {
                currentSum += currentValue;
                transformedData.add(currentValue);
                i++;
                j++;
            }
        }

        this.addColumn(newColumnName, transformedData, type);
    }
    private void appendBlockSubheader(Synthese external, String subheaderName, String prefix, boolean total) {
        int startColumnIndex = external.subheaders.indexOf(subheaderName);

        if (startColumnIndex == -1) {
            throw new IllegalArgumentException("Subheader: " + subheaderName + " not found.");
        }

        ArrayList<Double> totalValues = new ArrayList<>();

        int rowCount = external.getColumnByIndex(startColumnIndex).size();
        for (int i = 0; i < rowCount; i++) {
            totalValues.add(0.0);  // Initialize sum values
        }

        for (int columnIndex = startColumnIndex; columnIndex < external.subheaders.size(); columnIndex++) {
            // If we encounter a non-empty subheader, break the loop
            if (!external.subheaders.get(columnIndex).isEmpty() && columnIndex != startColumnIndex) {
                break;
            }

            // Fetch the corresponding header (in yyyy format) for the current column
            String year = external.headers.get(columnIndex);

            // Create a new column in the main Synthese object
            String newColumnName = prefix + " " + year;

            ArrayList<Double> transformedData = new ArrayList<>();

            ColTypes currentColumnType = external.columns.get(columnIndex).getType();

            for (int i = 0; i < rowCount; i++) {
                Object item = external.getColumnByIndex(columnIndex).get(i);
                double value = 0.0;

                if (currentColumnType == ColTypes.STR) {
                    try {
                        value = Double.parseDouble((String) item);
                    } catch (NumberFormatException e) {
                        value = 0.0;  // Default to 0 for non-numeric or empty values
                    }
                } else if (currentColumnType == DBL) {
                    value = (Double) item;  // If already double, no conversion needed
                }
                // Add other type conversions as needed, e.g., for FLT, DAT, etc.

                transformedData.add(value);

                // If total is true, accumulate the value to totalValues
                if (total) {
                    totalValues.set(i, totalValues.get(i) + value);
                }
            }

            this.addColumn(newColumnName, transformedData, DBL);
        }

        // If total is true, add the "Total" column
        if (total) {
            String totalColumnName = "Total " + prefix;
            this.addColumn(totalColumnName, totalValues, DBL);
        }
    }
    private void appendBlockSubheaderSummed(Synthese external, String subheaderName, String prefix, boolean total, String colToAggregare) {
        int startColumnIndex = external.subheaders.indexOf(subheaderName);

        if (startColumnIndex == -1) {
            throw new IllegalArgumentException("Subheader: " + subheaderName + " not found.");
        }

        ArrayList<Double> totalValues = new ArrayList<>();

        List<String> anneeCol = this.getColumn("Année");
        List<String> contratCol = this.getColumn(colToAggregare);

        // Initialize totalValues to 0.0 based on anneeCol's size
        for (int i = 0; i < anneeCol.size(); i++) {
            totalValues.add(0.0);
        }

        double currentSum = 0.0;
        double sumOfSums = 0.0;

        for (int columnIndex = startColumnIndex; columnIndex < external.subheaders.size(); columnIndex++) {
            // If we encounter a non-empty subheader, break the loop
            if (!external.subheaders.get(columnIndex).isEmpty() && columnIndex != startColumnIndex) {
                break;
            }

            // Fetch the corresponding header (in yyyy format) for the current column
            String year = external.headers.get(columnIndex);

            // Create a new column in the main Synthese object
            String newColumnName = prefix + " " + year;

            ArrayList<Double> transformedData = new ArrayList<>();

            ColTypes currentColumnType = external.columns.get(columnIndex).getType();

            for (int i = 0, j = 0; i < anneeCol.size(); i++) {
                Double value;
                if (currentColumnType == ColTypes.STR) {
                    String item = (String) external.getColumnByIndex(columnIndex).get(j);
                    try {
                        value = Double.parseDouble(item);
                    } catch (NumberFormatException e) {
                        value = 0.0;  // Default to 0 for non-numeric or empty values
                    }
                } else {
                    value = (j < external.getColumnByIndex(columnIndex).size()) ? (Double) external.getColumnByIndex(columnIndex).get(j) : 0.0;
                }

                if (anneeCol.get(i).startsWith("Total")) {
                    transformedData.add(roundToTwoDecimals(currentSum));
                    totalValues.set(i, totalValues.get(i) + roundToTwoDecimals(currentSum));  // Update totalValues
                    sumOfSums += currentSum;
                    currentSum = 0.0;
                } else if (contratCol.get(i).startsWith("Total")) {
                    transformedData.add(roundToTwoDecimals(sumOfSums));
                    totalValues.set(i, totalValues.get(i) + roundToTwoDecimals(sumOfSums));  // Update totalValues
                    currentSum = 0.0;
                    sumOfSums = 0.0;
                } else {
                    currentSum += value;
                    transformedData.add(value);
                    totalValues.set(i, totalValues.get(i) + value);  // Update totalValues
                    j++; // increment the external data index
                }
            }

            this.addColumn(newColumnName, transformedData, DBL);
        }

        // If total is true, add the "Total" column
        if (total) {
            String totalColumnName = "Total " + prefix;
            this.addColumn(totalColumnName, totalValues, DBL);
        }
    }
    private void populatePrimeEmiseReelle(Synthese external, String colToAggregare) {
        ArrayList<Double> montantTotalPrimeAssureurData = external.getColumn("MONTANT TOTAL PRIME ASSUREUR");
        ArrayList<Double> primeEmiseReelleData = new ArrayList<>();

        List<String> anneeCol = this.getColumn("Année");
        List<String> contratCol = this.getColumn(colToAggregare);

        double currentSum = 0.0;
        double sumOfSums = 0.0;

        for (int i = 0, j = 0; i < anneeCol.size(); i++) {
            Double currentValue = (j < montantTotalPrimeAssureurData.size() && bu.get(j)) ? parseObjectToDouble(montantTotalPrimeAssureurData.get(j)) : 0.0;

            if (anneeCol.get(i).startsWith("Total")) {
                primeEmiseReelleData.add(roundToTwoDecimals(currentSum));
                sumOfSums += currentSum;
                currentSum = 0.0; // reset current sum
            } else if (contratCol.get(i).startsWith("Total")) {
                primeEmiseReelleData.add(roundToTwoDecimals(sumOfSums));
                currentSum = 0.0; // reset current sum
                sumOfSums = 0.0;  // reset sum of sums
            } else {
                currentSum += currentValue;
                primeEmiseReelleData.add(currentValue);
                j++; // Increment the iterator for the montantTotalPrimeAssureurData
            }
        }

        this.addColumn("Prime émise réelle", primeEmiseReelleData, DBL);
    }

    // OUTPUT
    public void exportToExcel_old(String fileName) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Synthese Data");

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

                // Applying format based on column type
                if (isInArray(headers.get(i), INTEGER_COLUMNS)) {
                    cell.setCellValue(Double.parseDouble(value.toString()));
                    CellStyle style = workbook.createCellStyle();
                    style.setDataFormat(workbook.createDataFormat().getFormat("0"));
                    cell.setCellStyle(style);
                } else if (isInArray(headers.get(i), DOUBLE_COLUMNS)) {
                    cell.setCellValue(Double.parseDouble(value.toString()));
                    CellStyle style = workbook.createCellStyle();
                    style.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
                    cell.setCellStyle(style);
                } else if (isInArray(headers.get(i), PERCENTAGE_COLUMNS)) {
                    double percentValue = Double.parseDouble(value.toString()) * 100;
                    cell.setCellValue(percentValue + " %");
                } else {
                    cell.setCellValue(value.toString());
                }
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

        workbook.close();
    }
    public void exportToExcel2(String fileName, String sheetName, Workbook existingWorkbook) throws IOException {
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

                // Simply set cell value as string
                cell.setCellValue(value.toString());
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
    public static String columnNumberToExcelLetter(int col) {
        StringBuilder columnName = new StringBuilder();
        while (col > 0) {
            int rem = (col - 1) % 26;
            columnName.append((char)(rem + 'A'));
            col = (col - rem) / 26;
        }
        return columnName.reverse().toString();
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
    public void saveToCSV(String fileName) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), StandardCharsets.UTF_8))) {
            // Write BOM for UTF-8
            writer.write('\ufeff');
            // Writing the headers
            writer.write(String.join(";", headers));
            writer.newLine();
            writer.write(String.join(";", subheaders));
            writer.newLine();
            // Assuming the number of rows across all columns is consistent
            int numberOfRows = columns.get(0).getData().size();

            // Iterate over rows
            for (int rowIndex = 0; rowIndex < numberOfRows; rowIndex++) {
                List<String> rowValues = new ArrayList<>();

                // Iterate over columns
                for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                    Object value = getColumnByIndex(columnIndex).get(rowIndex);

                    if (value != null) {
                        // Format double values as rounded to 2 decimals and with ',' instead of '.'
                        if (value instanceof Double) {
                            String formattedValue = String.format(Locale.FRANCE, "%.2f", (Double) value);
                            rowValues.add(formattedValue);
                        } else {
                            rowValues.add(value.toString());
                        }
                    } else {
                        rowValues.add(""); // Empty value for null
                    }
                }

                // Writing the row
                writer.write(String.join(";", rowValues));
                writer.newLine();
            }
        }
    }

    private boolean isInArray(String value, String[] array) {
        for (String s : array) {
            if (s.equals(value)) return true;
        }
        return false;
    }

    // FORMAT
    public void formatAndReplaceColumn(String header) {
        int index = headers.indexOf(header);

        if (index == -1) {
            throw new IllegalArgumentException("Column with header: " + header + " not found.");
        }

        Column<?> oldColumn = columns.get(index);
        ArrayList<String> newColumnData = new ArrayList<>(oldColumn.getData().size());

        // Populate newColumnData based on the header and its type
        if (Arrays.asList(INTEGER_COLUMNS).contains(header)) {
            ArrayList<Double> colData = (ArrayList<Double>) oldColumn.getData();
            for (Double value : colData) {
                newColumnData.add(String.valueOf(Math.round(value)));
            }
        } else if (Arrays.asList(DOUBLE_COLUMNS).contains(header)) {
            ArrayList<Double> colData = (ArrayList<Double>) oldColumn.getData();
            for (Double value : colData) {
                newColumnData.add(String.format("%.2f", value));
            }
        } else if (Arrays.asList(PERCENTAGE_COLUMNS).contains(header)) {
            ArrayList<Double> colData = (ArrayList<Double>) oldColumn.getData();
            for (Double value : colData) {
                newColumnData.add(String.format("%.2f %%", value * 100));
            }
        } else { // Already a string type or any other type
            for (Object obj : oldColumn.getData()) {
                newColumnData.add(obj.toString());
            }
        }

        // Replace old column with new formatted column
        Column<String> newColumn = new Column<>(newColumnData, ColTypes.STR);
        columns.set(index, newColumn);
    }
    public void formatAllColumns() {
        for (String header : headers) {
            formatAndReplaceColumn(header);
        }
    }

    public void calculateHeaderFrequencies() {
        Stopwatch stopwatch = new Stopwatch();
        stopwatch.start();
        boolean foundFirst = false;

        for (int i = 0; i < headers.size(); i++) {
            if ("11-2013".equals(headers.get(i))) {
                foundFirst = true;
            }

            if (foundFirst) {
                if (!this.subheaders.get(i).isEmpty()) {
                    String key = this.subheaders.get(i);
                    frequencies.put(key, new ArrayList(List.of(i)));
                    for (int j = i; j < headers.size(); j++) {
                        if ("2013".equals(headers.get(j))) {
                            frequencies.get(key).add(j);
                            i = j;
                            for (int k = j; k < headers.size(); k++) {
                                if ("Total".equals(headers.get(k))) {
                                    frequencies.get(key).add(k);
                                    i = k;
                                    break;
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }
        stopwatch.printElapsedTime("headerFreq");
//        for (Map.Entry<String, ArrayList<Integer>> entry : frequencies.entrySet()) {
//            String key = entry.getKey();
//            ArrayList<Integer> values = entry.getValue();
//            System.out.println(key + " => " + values);
//        }
    }
    public void computeMvAvTvB(Base baseFic) {
        Stopwatch stopwatch = new Stopwatch();
        stopwatch.start();
        for (Map.Entry<String, ArrayList<Integer>> entry : frequencies.entrySet()) {
            ArrayList<Integer> indices = entry.getValue();
            String key = entry.getKey();  // This is the string key you're interested in.

            if (indices.size() == 3) {
                int x = indices.get(0) ;
                int y = indices.get(1);
                int z = indices.get(2);

                computeSumSubtract(x, y - 1, y, z - 1, key);
                computeSumSubtract(y, z - 1, z, z, key);
            }
        }
        computeSumVSBaseSin();
        computeSumVSBaseFic(baseFic);
        stopwatch.printElapsedTime("MATB");
    }
    private void computeSumVSBaseSin () {
        ArrayList<String> contrats = getColumn("Contrat");
        ArrayList<String> dates = getColumn("Date Periode");
        ArrayList<String> totalsString = getColumnSubheader("Sinistre total");
        ArrayList<String> totalsNString = getColumnSubheader("Sinistre Nombre total");
        ArrayList<Double> totals = transformToDoublesRound2(totalsString);
        ArrayList<Double> totalsN = transformToDoublesRound2(totalsNString);

        String currentContrat = ""; Base currentBase = null;
        SimpleDateFormat sdf = new SimpleDateFormat("MM-yyyy");

        Set<String> basesAbsentes = new HashSet<>();
        for (int i = 0; i < totals.size(); i++) {
            String contrat = contrats.get(i);
            if (!Objects.equals(contrat, currentContrat)) {
//                System.out.println("contrat - "+ contrat);
                currentContrat = contrat;
                currentBase = baseMap.get(contrat);
                if (currentBase == null) {
                    basesAbsentes.add(contrat);
                    continue;
                }
            }
            if (currentBase == null) {
                continue;
            }
            Date currentDate;
            try {
                currentDate = sdf.parse(dates.get(i));
            } catch (ParseException e) {
                throw new RuntimeException("Invalid date format.", e);
            }
            double sum = 0;
            int sumN = 0;
            Object[] datesSousBase = currentBase.c("date_sous");
            Object[] montantsIP = currentBase.c("montant_IP");
            for (int j = 0; j < currentBase.nrow; j++) {
                // Ensure proper typecasting
                double montant = (double) montantsIP[j];
                Date date_sous = (Date) datesSousBase[j];

                // Filtering
                if (date_sous.equals(currentDate)) {
                    sum += montant;
                    sumN++;
                }
            }
//            if (sum == 1178.93) {
//                System.out.println("here");
//            }
            sum = roundToTwoDecimals(sum);
            if (totals.get(i) != sum) {
                writeToLogFile("l'écart de charge entre total et base: " + currentContrat + " " + totals.get(i) + " != " + sum);
            }
            if (totalsN.get(i) != sumN) {
                writeToLogFile("l'écart de nombre entre total et base: " + currentContrat + " " + totalsN.get(i) + " != " + sumN);
            }
        }
        for (String s : basesAbsentes) {
            System.out.println(s + " BASE ABSENTE");
        }
    }
    private void computeSumVSBaseFic (Base baseFic) {
        ArrayList<String> contrats = getColumn("Contrat");
        ArrayList<String> dates = getColumn("Date Periode");
        ArrayList<String> totalsString = getColumnSubheader("Comptable total");
        ArrayList<String> totalsNString = getColumnSubheader("Comptable total nombre");
        ArrayList<Double> totals = transformToDoublesRound2(totalsString);
        ArrayList<Double> totalsN = transformToDoublesRound2(totalsNString);

        String currentContrat = "";
        SimpleDateFormat sdf = new SimpleDateFormat("MM-yyyy");

        for (int i = 0; i < totals.size(); i++) {
            String contrat = contrats.get(i);
            Date currentDate;
            try {
                currentDate = sdf.parse(dates.get(i));
            } catch (ParseException e) {
                throw new RuntimeException("Invalid date format.", e);
            }
            double sum = 0;
            int sumN = 0;
            Object[] datesSousBase = baseFic.c("date_sous");
            Object[] contratsFic = baseFic.c("num_police");
            Object[] montantsIP = baseFic.c("montant_IP");
            for (int j = 0; j < baseFic.nrow; j++) {
                // Ensure proper typecasting
                double montant = (double) montantsIP[j];
                Date date_sous = (Date) datesSousBase[j];
                String contratFic = (String) contratsFic[j];
                // Filtering
                if (date_sous.equals(currentDate) && contratFic.equalsIgnoreCase(contrat)) {
                    sum += montant;
                    sumN++;
                }
            }
            sum = roundToTwoDecimals(sum);

            if (totals.get(i) != sum) {
                writeToLogFile("l'écart entre charge total et baseFic: " + currentContrat + " " + totals.get(i) + " != " + sum + " " + currentDate);
            }
            if (totalsN.get(i) != sumN) {
                writeToLogFile("l'écart entre nombre total et baseFic: " + currentContrat + " " + totalsN.get(i) + " != " + sumN + " " + currentDate);
            }
        }
    }
    private void insertColumn(int index, ArrayList<Double> data, String nom) {
        Column<Double> newColumn = new Column<>(data, ColTypes.DBL);
        columns.add(index, newColumn);
        headers.add(index, nom);
        subheaders.add(index, "");
    }
    private void computeSumSubtract(int x1, int x2, int y1, int y2, String key) {
        ArrayList<Double> result = new ArrayList<>();

        int numRows = columns.get(0).getData().size();
        for (int i = 0; i < numRows; i++) {
            double sumX = sumRange(i, x1, x2);
            double sumY = sumRange(i, y1, y2);
            if (round(sumX - sumY) != 0) {
                if (y1 == y2) {
                    writeToLogFile("l'écart entre année et total: " + key + " " + sumX + " != " + sumY);
                } else {
                    writeToLogFile("l'écart entre mois et année: " + key + " " + sumX + " != " + sumY);
                }
            }
        }

    }
    private double sumRange(int row, int startCol, int endCol) {
        double sum = 0;
        for (int col = startCol; col <= endCol; col++) {
            ArrayList<?> colData = getColumnByIndex(col);
            Object value = colData.get(row);

            if (value instanceof String) {
                String stringValue = (String) value;
                if (!stringValue.isEmpty()) {
                    try {
                        sum += Double.parseDouble(stringValue.replace(',', '.'));
                    } catch (NumberFormatException e) {
                        // If it's not a parsable double, continue without adding to sum
                    }
                }
            } else if (value instanceof Number) {
                sum += ((Number) value).doubleValue();
            }
        }
        return sum;
    }
    public void populateStatutMap() {
        ArrayList<String> statuts = getColumn("statut");
        ArrayList<String> statutsReferentiel = getColumn("statut referentiel");

        for (int i = 0; i < statuts.size(); i++) {
            String statut = statuts.get(i);
            String referentiel = statutsReferentiel.get(i);

            // Populate the map. If the key already exists, it won't overwrite the value.
            statutMap.putIfAbsent(statut, referentiel);
        }
    }

    private ArrayList<Boolean> getTreatColumnsList() {
        ArrayList<Boolean> treatColumns = new ArrayList<>(headers.size());
        Pattern pattern = Pattern.compile("(\\d{2}-\\d{4}|\\d{4}|Total)");

        String lastNonEmptySubheader = "";

        for (int i = 0; i < headers.size(); i++) {
            String headerName = headers.get(i);
            String subheader = (i < subheaders.size()) ? subheaders.get(i) : "";

            if (!subheader.trim().isEmpty()) {
                lastNonEmptySubheader = subheader;
            }

            if (lastNonEmptySubheader.startsWith("Statut") || lastNonEmptySubheader.startsWith("Nombre")) {
                treatColumns.add(pattern.matcher(headerName).matches());
            } else {
                treatColumns.add(false); // or true, depending on your default behaviour
            }
        }
        return treatColumns;
    }
    private List<SummaryType> determineSummaryTypes() {
        List<SummaryType> types = new ArrayList<>(headers.size());
        statutsForTreatment = new ArrayList<>(headers.size());
        String lastNonEmptySubheader = "";
        ArrayList<Boolean> treatColumns = getTreatColumnsList();

        for (int i = 0; i < headers.size(); i++) {
            if (!treatColumns.get(i)) {
                types.add(null);  // Add a null entry for columns we don't treat.
                statutsForTreatment.add(null);
                continue;
            }

            String headerName = headers.get(i);
            String subheader = (i < subheaders.size()) ? subheaders.get(i) : "";

            if (!subheader.trim().isEmpty()) {
                lastNonEmptySubheader = subheader;
            }

            // Extract statut
            String statut = lastNonEmptySubheader;
            statut = statut.replace("Statut","").replace("Nombre","").replace("mensuel","").replace("annuel","").replace("total","");
            statut = statut.trim();
            statutsForTreatment.add(statut);

            SummaryType.Frequency freq = SummaryType.Frequency.TOTAL;
            if (headerName.matches("\\d{2}-\\d{4}")) {
                freq = SummaryType.Frequency.MONTHLY;
            } else if (headerName.matches("\\d{4}")) {
                freq = SummaryType.Frequency.YEARLY;
            }

            SummaryType.Calculation calc = SummaryType.Calculation.CHARGE;
            if (lastNonEmptySubheader.startsWith("Nombre")) {
                calc = SummaryType.Calculation.FREQ;
            }

            types.add(new SummaryType(freq, calc));
        }

        return types;  // You might also want to return the statutsForTreatment or make it an instance variable.
    }
    public void computeSumByStatutEtDateSurv() {
        Stopwatch stopwatch = new Stopwatch();
        stopwatch.start();
        ArrayList<String> contratColumn = getColumn("Contrat");
        ArrayList<Boolean> treatColumns = getTreatColumnsList();
        List<SummaryType> types = determineSummaryTypes();

        String contrat;
        Base currentBase;
        Set<String> basesAbsentes = new HashSet<>();
        int beginIndex = 0;

        for (int rowIndex = 0; rowIndex < contratColumn.size(); rowIndex++) {
//            System.out.println("TREATING ROW: " + rowIndex);
            contrat = contratColumn.get(rowIndex);
            if (rowIndex + 1 < contratColumn.size() && Objects.equals(contrat, contratColumn.get(rowIndex + 1))) continue;
            currentBase = baseMap.get(contrat);

            if (currentBase == null) {
                basesAbsentes.add(contrat);
                beginIndex = rowIndex + 1;
                continue;
            }
            for (int colIndex = 0; colIndex < headers.size(); colIndex++) {
                if (!treatColumns.get(colIndex)) {
                    continue; // skip columns that don't require treatment
                }

                String headerName = headers.get(colIndex);
                SummaryType currentType = types.get(colIndex);


                ArrayList<String> calcColumnString = getColumnByIndex(colIndex);
                ArrayList<Double> calcColumn = transformToDoublesRound2(calcColumnString);
                double sum = 0d;
                for (int calcIndex = beginIndex; calcIndex <= rowIndex; calcIndex++) {
                    sum += calcColumn.get(calcIndex);
                }
                sum = roundToTwoDecimals(sum);
                double refValue = 0;

                if (currentType.getCalculation() == SummaryType.Calculation.CHARGE) {

                    refValue = roundToTwoDecimals(currentBase.filterAndSumByCharge(statutsForTreatment.get(colIndex), headerName, currentType.getFrequency()));
                    if (sum != refValue) {
                        writeToLogFile("l'écart de charge entre total par statut et base: " + contrat + " " + sum + " != " +
                                refValue + " pour le mois surv: " + headerName + " du statut: " + statutsForTreatment.get(colIndex));
                    }
                } else {
//                    if (Objects.equals(headerName, "04-2019") && statutsForTreatment.get(colIndex).equalsIgnoreCase("terminé sans suite")) {
//                        System.out.println("here");
//                    }
                    refValue = roundToTwoDecimals(currentBase.filterAndSumByFreq(statutsForTreatment.get(colIndex), headerName, currentType.getFrequency()));
                    if (sum != refValue) {
                        writeToLogFile("l'écart de nombre entre total par statut et base: " + contrat + " " + sum + " != " +
                                refValue + " pour le mois surv: " + headerName + " du statut: " + statutsForTreatment.get(colIndex));
                    }
                }
            }
            beginIndex = rowIndex + 1;
        }
        for (String s : basesAbsentes) {
            System.out.println(s + " BASE ABSENTE");
        }
        stopwatch.printElapsedTime("parStatut");
    }
    public void setCellValue(int rowIndex, int colIndex, Object value) {
        this.getColumnByIndex(colIndex).set(rowIndex, value);
    }
    public static ArrayList<Double> transformToDoublesRound2(ArrayList<String> totals) {
        ArrayList<Double> doubleTotals = new ArrayList<>();
        for (String value : totals) {
            try {
                doubleTotals.add(roundToTwoDecimals(Double.parseDouble(value.replace(',','.'))));
            } catch (NumberFormatException e) {
                doubleTotals.add(0d); // default value for empty or invalid numbers
            }
        }
        return doubleTotals;
    }
    public static String getCurrentDateTime() {
        return LocalDateTime.now().format(formatter);
    }
}
