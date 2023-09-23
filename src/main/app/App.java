package main.app;

import com.univocity.parsers.csv.CsvParserSettings;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static java.lang.Math.abs;
import static main.app.Base.MIN_PREVI_DATE;
import static main.app.Base.createStatutMap;
import static main.app.DF.*;
import static main.app.DF.ColTypes.DBL;
import static main.app.Estimate.isComm;
import static main.app.Synthese.roundToTwoDecimals;
import static main.app.Synthese.syntAncien;

public class App {

//    public static final String wd = "C:/Users/ozhukov/Documents/wd/";
    public static final String wd = "E:/202305/wd/";
    public static final String outputFolder = wd + "output/";
    public static final String refFolder = wd + "refs/";
    public static final String tdbFolder = wd + "TDB/";
    public static final String basesFolder = wd + "bases/";
    public static String encoding = "UTF-8";
    public static CsvParserSettings csv_settings = new CsvParserSettings();
    public static Map<String,Map<Integer, Double>> mapSPprevi = new HashMap<>();
    public static Map<String,Map<String, Double>> mapPB = new HashMap<>();
    public static SimpleDateFormat dateDefault = new SimpleDateFormat("dd/MM/yyyy");
    public static Map<String, Map<String, List<Date>>> policeStatutDateRangeMap = new HashMap<>();
    public static Map<String, List<Date>> globalStatutDateRangeMap = new HashMap<>();
    public static Date globalMinDate = new Date(Long.MAX_VALUE); // Initializing to the max possible date
    public static Date globalMaxDate = new Date(Long.MIN_VALUE);
    private static final String CURRENT_MONTH;
    private static final String PREVIOUS_MONTH;
    public static final Date TODAY_01;
    static {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        LocalDate now = LocalDate.now();

        CURRENT_MONTH = now.format(formatter);
        PREVIOUS_MONTH = now.minusMonths(1).format(formatter);
//        static final String LOG_FILE_PATH;
//        LOG_FILE_PATH = outputFolder +"logfile_" + getCurrentDateTime() + ".txt";

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        TODAY_01 = calendar.getTime();
    }
    private static final LocalDate TODAY = LocalDate.now();
    public static char delim = ';';
    public static Map<String, Base> baseMapNew = new HashMap<>();
    public static Map<String, Base> ficMapNew = new HashMap<>();
    public static List<String> statutsForTreatment;
    public static Map<String, String> globalStatutMap = new HashMap<>();
    public static Map<String, String> globalStatutCollect = new HashMap<>();
    public static final String POLICE = "police";
    public static final String DATE_SOUS = "date_sous";
    public static final String DATE_SURV = "date_surv";
    public static final String DATE_DECLA = "date_decla";
    public static final String MONTANT = "montant";
    public static final String STATUT = "statut";
    public static final String YEAR_SURV = "ys_added";
    public static DF PB;
    public static DF refProg;
    public static DF refCols;
    public static DF refSource;
    public static DF mapping;
    public static DF SPprevi;
    public static DF mapStatuts;
    public static DF grilleTarif;
    public static DF coefPM;
    public static final Set<String> aChercherDansCoefPM = new HashSet<>();
    public static final Map<String,Date> policesComm = new HashMap<>();
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
        aChercherDansCoefPM.add("ICIMOPCKIT22");

        Date lastDateMK;
        try {
            lastDateMK = dateDefault.parse("02/07/2022");
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        policesComm.put("Surcommission1", MIN_PREVI_DATE);
        policesComm.put("Surcommission2", MIN_PREVI_DATE);
        policesComm.put("PB-2020", MIN_PREVI_DATE);
        policesComm.put("ICIGSCOM21", MIN_PREVI_DATE);
        policesComm.put("ICIGSCOM20", MIN_PREVI_DATE);
        policesComm.put("ICICDREG22", MIN_PREVI_DATE);
        policesComm.put("ICIMMWB-0719", MIN_PREVI_DATE);
        policesComm.put("ICIMMWB-0720", MIN_PREVI_DATE);
        policesComm.put("ICIMMWB-0721", MIN_PREVI_DATE);
        policesComm.put("ICIMKPA16", lastDateMK);
        policesComm.put("ICIMKPR16", lastDateMK);
        policesComm.put("ICIMKTLM22", lastDateMK);

    } //PM
    static {
        try {
            PB = new DF(refFolder + "PB Micromania.csv",';',false,"PB");
            refProg = new DF(refFolder + "ref_Programmes.csv",';',false,"refProg");
            refCols = new DF(refFolder + "ref_Renta.xlsx","ref_cols",false,"refCols");
            refSource = new DF(refFolder + "ref_Renta.xlsx","source",false,"refSource");
            mapping = new DF(refFolder + "mapping.xlsx","Mapping entrant sinistres",false,"mapping");
            SPprevi = new DF(refFolder + "S SUR P PREVI 2023_01_18.xlsx","Feuil1",false,"SPprevi");
            mapStatuts = new DF(refFolder + "statuts.xlsx","Statuts",false,"mapStatuts");
            grilleTarif = new DF(refFolder + "Grille_Tarifaire.csv",';',false,"grilleTarif");
            coefPM = new DF(refFolder + "coefPM.csv",';',false,"coefPM");
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
    public static void main(String[] args) throws Exception {
        printMemoryUsage();
        Stopwatch st = new Stopwatch();
        st.start();
//
//        Estimate estimate = new Estimate(tdbFolder+"TDB Estimate.csv",';',"estimate12");
//        getCoefsAcquisition(false,estimate);
//        st.printElapsedTime();
//        createFDT(estimate);
//        st.printElapsedTime();
//        createSynthese("TDB Estimate_FDT.csv","TDB Part 1 Assureur synthèse 202210.xlsx");
//        Synthese syntAncien1 = new Synthese(outputFolder + "Synthèse_202309.xlsx", "Synthèse Année-Mois");
        Synthese fdt = new Synthese(outputFolder + "TDB Estimate_FDT.csv");
        syntAncien = new Synthese(tdbFolder+"TDB Part 1 Assureur synthèse 202210.xlsx","Synthèse année mois");
        Synthese syntAncien1 = new Synthese(fdt,"Contrat", syntAncien,false);

        Synthese syntAncien2 = new Synthese(tdbFolder + "TDB Part 1 Assureur synthèse 202212.xlsx","Synthèse année mois");
        compareKeys(syntAncien1,syntAncien2,fdt,false);
        compareKeys(syntAncien2,syntAncien1,fdt,true);
        compareKeys(syntAncien1,fdt,false);
        compareKeys(syntAncien2,fdt,true);
//        compareSynthese(syntAncien1,syntAncien2);
//        syntAncien1.print();
//        syntAncien2.print();
//
//        st.printElapsedTime();

    }
    public static void createFDT(Estimate estimate) throws Exception {
        for (int i = 0; i < refSource.nrow; i++) {
            boolean a_faire = !(refSource.getColumn("a faire").get(i)).equals("non");
            if (!a_faire) continue;
            String folder = (String) refSource.getColumn("path").get(i);
            String pays = (String) refSource.getColumn("pays_filekey").get(i);
            String mapcol = (String) refSource.getColumn("mapping").get(i);
            String path_fic = (String) refSource.getColumn("path_fic").get(i);
            String map_fic = (String) refSource.getColumn("map_fic").get(i);

            System.out.println(pays);
            File[] fileList = Objects.requireNonNull(new File(basesFolder + folder).listFiles());

            for (File file : fileList) {
                Base base = new Base(file,pays,mapcol,false);
                baseMapNew.put(base.numPolice, base);
                for (String statut : base.uniqueStatuts) {
                    globalStatutCollect.putIfAbsent(statut, base.numPolice);
                }

            }
            if (pays.equals("Italie")) {
                File[] fileListGS = Objects.requireNonNull(new File(basesFolder + "source SIN/Gamestop/").listFiles());
                for (File file : fileListGS) {
                    Base base = new Base(file,"Gamestop","SPB Italie Gamestop v1",false);
                    baseMapNew.put(base.numPolice, base);
                    for (String statut : base.uniqueStatuts) {
                        globalStatutCollect.putIfAbsent(statut, base.numPolice);
                    }
                }
                Base baseGPTB = new Base(new File(basesFolder + "aux SIN/SPB Italie_ICIGPTB15.csv"),false);
                Base baseMITL = new Base(new File(basesFolder + "aux SIN/SPB Italie_ICIMITL16.csv"),false);
                baseMapNew.put(baseGPTB.numPolice, baseGPTB);
                baseMapNew.put(baseMITL.numPolice, baseMITL);
                for (String statut : baseGPTB.uniqueStatuts) {
                    globalStatutCollect.putIfAbsent(statut, baseGPTB.numPolice);
                }
                for (String statut : baseMITL.uniqueStatuts) {
                    globalStatutCollect.putIfAbsent(statut, baseMITL.numPolice);
                }
            }

            Base baseFic = new Base(basesFolder + path_fic,map_fic,false);
            ficMapNew.put(path_fic, baseFic);
        }
        Base baseGDM = new Base(basesFolder+"aux SIN/Advise.csv");
        Base baseADV = new Base(basesFolder+"aux SIN/Guy Demarle.csv");
        Base baseGP = new Base(basesFolder+"aux SIN/Garantie Privée.csv");
        Base baseSUP = new Base(basesFolder+"aux SIN/Supporter.csv");
        ficMapNew.put(baseGDM.numPolice,baseGDM);
        ficMapNew.put(baseADV.numPolice,baseADV);
        baseMapNew.put(baseGP.numPolice,baseGP);
        baseMapNew.put(baseSUP.numPolice,baseSUP);

        createStatutMap();

        estimate.appendAllPivotsFic();
        estimate.appendAllPivotsSin();
        estimate.addProvisions();

        estimate.beginSplit();
        estimate.addPrimesAcquises();
        estimate.addSP();

        estimate.saveFDT(false);
    }
    public static void createSynthese(String syntPath, String syntAncienPath) throws IOException, ParseException {
        String output = outputFolder + "Synthèse_" + CURRENT_MONTH + ".xlsx";
        Synthese fdt = new Synthese(outputFolder + syntPath);
        syntAncien = new Synthese(tdbFolder+syntAncienPath,"Synthèse année mois");

        Synthese parMois = new Synthese(fdt,"Contrat", syntAncien,false);
        Synthese parAnnee = new Synthese(parMois,"Contrat");
        parMois.formatAllColumns();
        parAnnee.formatAllColumns();

        Synthese parDistrib = new Synthese(fdt,"Distributeur", syntAncien,false);
        Synthese parDistribAgg = new Synthese(parDistrib,"Distributeur");
        parDistribAgg.formatAllColumns();

        Synthese parGest = new Synthese(fdt,"Gestionnaire", syntAncien,false);
        Synthese parGestAgg = new Synthese(parGest,"Gestionnaire");
        parGestAgg.formatAllColumns();

        parMois.exportToExcel(output, "Synthèse Année-Mois", null);
        try (FileInputStream fileIn = new FileInputStream(output)) {
            Workbook existingWorkbook = new XSSFWorkbook(fileIn);
            // Second call: use the loaded workbook to append the data
            parAnnee.exportToExcel(output, "Synthèse Police", existingWorkbook);
            parDistribAgg.exportToExcel(output, "Synthèse Partenaire", existingWorkbook);
            parGestAgg.exportToExcel(output, "Synthèse Gestionnaire", existingWorkbook);
        }
    }
    public static String formatMemory(long bytes) {
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unit = 0;
        double converted = bytes;

        while (converted >= 1024 && unit < units.length - 1) {
            converted /= 1024;
            unit++;
        }

        return String.format("%.2f %s", converted, units[unit]);
    }
    public static void printMemoryUsage() {
        long heapSize = Runtime.getRuntime().totalMemory();
        long heapMaxSize = Runtime.getRuntime().maxMemory();
        long heapFreeSize = Runtime.getRuntime().freeMemory();

        System.out.println("Heap Size = " + formatMemory(heapSize));
        System.out.println("Max Heap Size = " + formatMemory(heapMaxSize));
        System.out.println("Free Heap Size = " + formatMemory(heapFreeSize));
    }
    public static void compareSynthese(Synthese A,Synthese B) {
        B.headers.replaceAll(s -> s.replace("\n", ""));
        B.headers.replaceAll(String::trim);

        // Step 1: Create a mapping
        String[] headersAa = "Gestionnaire;Distributeur;Assureur;Contrat;Date Periode;Année;Nombre Adhésions;Montant Total HT;Montant Total Prime Assureur;Prime Acquise à date;Participation aux Benefices;Total Sinistres Comptable;Total Sinistres Technique;Ecart sinistres Technique - Comptable;Nombre Dossier En Cours;Total Provision Sinistre Connu;Provision Sinistre Connu 2013;Provision Sinistre Connu 2014;Provision Sinistre Connu 2015;Provision Sinistre Connu 2016;Provision Sinistre Connu 2017;Provision Sinistre Connu 2018;Provision Sinistre Connu 2019;Provision Sinistre Connu 2020;Provision Sinistre Connu 2021;Provision Sinistre Connu 2022;Provision Sinistre Connu 2023;Provision Sinistre Connu 2024;Provision Sinistre Connu 2025;Provision Sinistre Connu 2026;Prime émise réelle;Taux primes émise réelle;Taux d'acquisition des primes;PB pour S/P acquis;S/P comptable émis;Solde comptable émis;S/P comptable acquis;Solde comptable acquis;S/P technique émis;Solde technique émis;S/P technique acquis;Solde technique acquis;S/P technique provisionné émis;Solde technique provisionné emis;S/P technique provisionné acquis;Solde technique provisionné acquis;Sinistre Ultime;Prime à l'ultime;S/P Comptable à l'ultime;Variation adhesions comptable;Variation des Primes émises;Variation primes acquises;Variation Taux d'Acquisition;Variation des Sinistres Comptable;Variation des Sinistres Technique;Variation des Provisions sur Sinistre;Variation S/P comptable acquis;Variation S/P technique acquis;Variation S/P technique provisionné acquis;Variation Sinistre Ultime;Variation S/P Comptable à l'ultime".split(";");
        String[] headersBa = "GESTIONNAIRE;DISTRIBUTEUR;ASSUREUR;CONTRAT;date;Années;ADHESIONS COMPTABLE;MONTANT TOTAL HT;MONTANT TOTAL PRIME ASSUREUR;PRIME ACQUISE A DATE;PARTICIPATION AUX BENEFICES;TOTAL SINISTRES COMPTABLE;TOTAL SINISTRE TECHNIQUE;Ecart Sinistres Technique - Comptable;nb de dossier en cours;Provisions sur sinistres connus;Provision sinsitre connu 2013;Provision sinsitre connu 2014;Provision sinsitre connu 2015;Provision sinsitre connu 2016;Provision sinsitre connu 2017;Provision sinsitre connu 2018;Provision sinsitre connu 2019;Provision sinsitre connu 2020;Provision sinsitre connu 2021;Provision sinsitre connu 2022;Provision sinsitre connu 2023;Provision sinsitre connu 2024;Provision sinsitre connu 2025;Provision sinsitre connu 2026;Primes émise réelle;Taux primes émise réelle;Taux d'acquisition des primes;PB POUR S/P ACQUIS;S/P comptable emis;Solde comptable emis;S/P comptable acquis;Solde comptable acquis;S/P technique emis;Solde technique emis;S/P technique acquis;Solde technique acquis;S/P technique provisionné emis;Solde technique provisionné emis;S/P technique provisionné acquis;Solde technique provisionné acquis;Sinistre Ultime;Prime à l'ultime;S/P Comptable à l'ultime;Variation adhesions comptable;Variation des Primes émise; Variation primes acquise; Variation Taux d'Acquisition;Variation des Sinistre Comptable;Variation des Sinistre Technique;Variation des Provisions sur Sinistre;Variation S/P comptable acquis;Variation S/P technique acquis;Variation S/P technique provisionné acquis;Variation Sinistre Ultime;Variation S/P Comptable à l'ultime".split(";");
        ArrayList<String> headersA = new ArrayList<>(List.of(headersAa));
        ArrayList<String> headersB = new ArrayList<>(List.of(headersBa));
        int nrowA = A.getColumnByIndex(0).size();
        int nrowB = B.getColumnByIndex(0).size();

        Map<String,Integer> map = new HashMap<>();

        for (int j = 0; j < nrowB; j++) {
            String keyB = B.getColumn("CONTRAT").get(j) + "-" + B.getColumn("date").get(j);
            map.put(keyB,j);
        }

        for (int i = 0; i < nrowA; i++) {
            String keyA = A.getColumn("Contrat").get(i) + "-" + A.getColumn("Date Periode").get(i);
            Integer indexB = map.get(keyA);
            if (indexB == null) {
                System.out.println("key not found: " + keyA);
                continue;
            }
            for (int j = 0; j < A.headers.size(); j++) {
                String currentHeader = A.headers.get(j);
                if (headersA.contains(currentHeader)) {
                    if (A.columns.get(j).getType().equals(DBL)) {
                        Double valueA = (Double) A.getColumn(currentHeader).get(i);
                        Double valueB = (Double) B.getColumn(headersB.get(headersA.indexOf(currentHeader))).get(indexB);
                        double diff = abs(roundToTwoDecimals(valueA-valueB));
                        if (diff > 0.01) {
                            System.out.println("problem in " + currentHeader + " key: " + keyA + " A: " + valueA + " B: " + valueB);
                        }
                    }
//                    if (A.columns.get(i).getType().equals(INT)) {
//                        Integer valueA = (Integer) A.getColumn(A.headers.get(i)).get(i);
//                        Integer valueB = (Integer) B.getColumn(headersB.get(headersA.indexOf(A.headers.get(i)))).get(indexB);
//                        int diff = abs(valueA-valueB);
//                        if (diff > 0) {
//                            System.out.println("problem in " + A.headers.get(i) + " key: " + keyA + " diff: " );
//                        }
//                    }
                }
            }
        }
    }
    public static void compareKeys(Synthese A, Synthese B, Synthese estimate, boolean inv) throws ParseException {
        A.headers.replaceAll(s -> s.replace("\n", ""));
        A.headers.replaceAll(String::trim);
        B.headers.replaceAll(s -> s.replace("\n", ""));
        B.headers.replaceAll(String::trim);
        estimate.headers.replaceAll(s -> s.replace("\n", ""));
        estimate.headers.replaceAll(String::trim);

        String contratA; String dateA; String contratB; String dateB;
        if (inv) {
            contratA = "CONTRAT";
            dateA = "date";
            contratB = "Contrat";
            dateB = "Date Periode";
        } else {
            contratB = "CONTRAT";
            dateB = "date";
            contratA = "Contrat";
            dateA = "Date Periode";

        }
        String dateEstimate = "Date Periode";
        String contratEstimate = "Contrat";

        SimpleDateFormat sdfA = new SimpleDateFormat("MM-yyyy");
        SimpleDateFormat sdfEstimate = new SimpleDateFormat("dd/MM/yyyy");
        Calendar cal = Calendar.getInstance();

        Map<String, Integer> mapA = new HashMap<>();
        Map<String, Integer> mapB = new HashMap<>();
        Map<String, Integer> mapEstimate = new HashMap<>();

        int countA = 0;
        for (int i = 0; i < A.getColumnByIndex(0).size(); i++) {
            String dateStrA = (String) A.getColumn(dateA).get(i);
            if (!dateStrA.isEmpty()) {
                Date parsedDateA = sdfA.parse(dateStrA);
                String contratAvalue = (String) A.getColumn(contratA).get(i);
                String keyA = contratAvalue + "_" + dateStrA;
                if (!isComm(contratAvalue, parsedDateA)) {
                    mapA.put(keyA, i);
                    countA++;
                }
            }
        }
        System.out.println("Number of non-empty dates in A: " + countA);

        int countB = 0;
        for (int j = 0; j < B.getColumnByIndex(0).size(); j++) {
            String dateStrB = (String) B.getColumn(dateB).get(j);
            if (!dateStrB.isEmpty()) {
                Date parsedDateB = sdfA.parse(dateStrB);
                String contratBvalue = (String) B.getColumn(contratB).get(j);
                String keyB = contratBvalue + "_" + dateStrB;
                if (!isComm(contratBvalue, parsedDateB)) {
                    mapB.put(keyB, j);
                    countB++;
                }
            }
        }
        System.out.println("Number of non-empty dates in B: " + countB);

        int countEstimate = 0;
        for (int j = 0; j < estimate.getColumnByIndex(0).size(); j++) {
            try {
                String dateStrEstimate = (String) estimate.getColumn(dateEstimate).get(j);
                Date parsedDateEstimate = sdfEstimate.parse(dateStrEstimate);
                String contratEstimateValue = (String) estimate.getColumn(contratEstimate).get(j);
                String keyEstimate = contratEstimateValue + "_" + sdfA.format(parsedDateEstimate);
                if (!isComm(contratEstimateValue, parsedDateEstimate)) {
                    mapEstimate.put(keyEstimate, j);
                    countEstimate++;
                }
            } catch (ParseException e) {
                // Ignore dates that couldn't be parsed
            }
        }
        System.out.println("Number of parsable dates in Estimate: " + countEstimate);

        Set<String> combinedKeys = new HashSet<>(mapB.keySet());
        combinedKeys.addAll(mapEstimate.keySet());

        String currentContract = null;
        Date currentStartDate = null;
        Date previousDate = null;

        for (String key : combinedKeys) {
            if (!mapA.containsKey(key) && mapB.containsKey(key) && mapEstimate.containsKey(key)) {
                String[] parts = key.split("_");
                String contract = parts[0];
                String dateStr = parts[1];

                Date date = sdfA.parse(dateStr);

                if (currentContract == null) {
                    currentContract = contract;
                    currentStartDate = date;
                    previousDate = date;
                } else if (!contract.equals(currentContract)) {
                    // New contract, so print the current interval
                    printInterval(currentContract, currentStartDate, previousDate, sdfA);
                    currentContract = contract;
                    currentStartDate = date;
                    previousDate = date;
                } else {
                    cal.setTime(previousDate);
                    cal.add(Calendar.MONTH, 1);
                    Date expectedNextDate = cal.getTime();
                    if (!date.equals(expectedNextDate)) {
                        // Break in the sequence, print the current interval
                        printInterval(currentContract, currentStartDate, previousDate, sdfA);
                        currentStartDate = date;
                        previousDate = date;
                    } else {
                        // Still in sequence; just update the previous date
                        previousDate = date;
                    }
                }
            }
        }

        if (currentContract != null && currentStartDate != null) {
            printInterval(currentContract, currentStartDate, previousDate, sdfA);
        }
    }
    public static void compareKeys(Synthese A, Synthese estimate, boolean inv) throws ParseException {
        A.headers.replaceAll(s -> s.replace("\n", ""));
        A.headers.replaceAll(String::trim);
        estimate.headers.replaceAll(s -> s.replace("\n", ""));
        estimate.headers.replaceAll(String::trim);

        String contratA;
        String dateA;
        if (inv) {
            contratA = "CONTRAT";
            dateA = "date";
        } else {
            contratA = "Contrat";
            dateA = "Date Periode";
        }
        String dateEstimate = "Date Periode";
        String contratEstimate = "Contrat";

        SimpleDateFormat sdfA = new SimpleDateFormat("MM-yyyy");
        SimpleDateFormat sdfEstimate = new SimpleDateFormat("dd/MM/yyyy");

        Map<String, Integer> mapA = new HashMap<>();
        Map<String, Integer> mapEstimate = new HashMap<>();

        int countA = 0;
        for (int i = 0; i < A.getColumnByIndex(0).size(); i++) {
            String dateStrA = (String) A.getColumn(dateA).get(i);
            if (!dateStrA.isEmpty()) {
                Date parsedDateA = sdfA.parse(dateStrA);
                String contratAvalue = (String) A.getColumn(contratA).get(i);
                String keyA = contratAvalue + "_" + dateStrA;
                if (!isComm(contratAvalue, parsedDateA)) {
                    mapA.put(keyA, i);
                    countA++;
                }
            }
        }
        System.out.println("Number of non-empty dates in A: " + countA);

        int countEstimate = 0;
        for (int j = 0; j < estimate.getColumnByIndex(0).size(); j++) {
            try {
                String dateStrEstimate = (String) estimate.getColumn(dateEstimate).get(j);
                Date parsedDateEstimate = sdfEstimate.parse(dateStrEstimate);
                String contratEstimateValue = (String) estimate.getColumn(contratEstimate).get(j);
                String keyEstimate = contratEstimateValue + "_" + sdfA.format(parsedDateEstimate);
                if (!isComm(contratEstimateValue, parsedDateEstimate)) {
                    mapEstimate.put(keyEstimate, j);
                    countEstimate++;
                }
            } catch (ParseException e) {
                // Ignore dates that couldn't be parsed
            }
        }
        System.out.println("Number of parsable dates in Estimate: " + countEstimate);

        Set<String> keysInEstimate = new HashSet<>(mapEstimate.keySet());

        String currentContract = null;
        Date currentStartDate = null;
        Date previousDate = null;
        Calendar cal = Calendar.getInstance();

        for (String key : keysInEstimate) {
            if (!mapA.containsKey(key) && mapEstimate.containsKey(key)) {
                String[] parts = key.split("_");
                String contract = parts[0];
                String dateStr = parts[1];

                Date date = sdfA.parse(dateStr);

                if (currentContract == null) {
                    currentContract = contract;
                    currentStartDate = date;
                    previousDate = date;
                } else if (!contract.equals(currentContract)) {
                    printInterval(currentContract, currentStartDate, previousDate, sdfA);
                    currentContract = contract;
                    currentStartDate = date;
                    previousDate = date;
                } else {
                    cal.setTime(previousDate);
                    cal.add(Calendar.MONTH, 1);
                    Date expectedNextDate = cal.getTime();
                    if (!date.equals(expectedNextDate)) {
                        printInterval(currentContract, currentStartDate, previousDate, sdfA);
                        currentStartDate = date;
                        previousDate = date;
                    } else {
                        previousDate = date;
                    }
                }
            }
        }

        if (currentContract != null && currentStartDate != null) {
            printInterval(currentContract, currentStartDate, previousDate, sdfA);
        }
    }

    private static void printInterval(String contract, Date start, Date end, SimpleDateFormat sdf) {
        if (start.equals(end)) {
            System.out.println("Contract: " + contract + ", date missing - " + sdf.format(start));
        } else {
            System.out.println("Contract: " + contract + ", dates missing - " + sdf.format(start) + " - " + sdf.format(end));
        }
    }


}