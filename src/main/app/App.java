package main.app;

import com.univocity.parsers.csv.CsvParserSettings;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static java.lang.Math.round;
import static java.util.Arrays.fill;
import static main.app.Synthese.getCurrentDateTime;

public class App {

//    public static final String wd = "C:/Users/ozhukov/Documents/wd/";
    public static final String wd = "E:/202305/wd/";
    public static final String outputFolder = wd + "output/";
    public static String encoding = "UTF-8";
    public static CsvParserSettings csv_settings = new CsvParserSettings();
    public static final String regex_digits = "[0-9]+";
    public static final String regex_letters = ".*[a-zA-Z].*";
    public static final Double NA_DBL = 9999099d;
    public static final String NA_STR = "n.a.";
    public static SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy");
    public static final Date NA_DAT;
    static {
        try {
            NA_DAT = format.parse("01/01/2100");
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }
    public static final LocalDate NA_LDAT = to_Date(NA_DAT);
    public static ArrayList<ArrayList<String>> Rapport = new ArrayList<>();
    public static ArrayList<ArrayList<String>> Rapport_temps_exec = new ArrayList<>();
    public static ArrayList<ArrayList<String>> Log_err = new ArrayList<>();
    public static String yyyymm = "default";
    public static DF ref_cols;
    public static DF ref_source;
    public static DF ref_prog = new DF(wd+"ref_Programmes.csv", ';', true);
    public static DF mapping;
    public static DF grille_tarif = new DF(wd + "Grille_Tarifaire.csv",';',(Integer)0);
    public static DF tdb2;
    public static DF tdb2coef;
    public static DF tdb2fr;
    public static DF tdb2_ref;
    public static DF SPprevi;
    public static DF PB;
    public static Map<String,Map<Integer, Double>> mapSPprevi = new HashMap<>();
    public static Map<String,Map<String, Double>> mapPB = new HashMap<>();
    public static SimpleDateFormat dateDefault = new SimpleDateFormat("dd/MM/yyyy");
    public static Map<String, Map<String, List<Date>>> policeStatutDateRangeMap = new HashMap<>();
    public static Map<String, List<Date>> globalStatutDateRangeMap = new HashMap<>();
    public static Date globalMinDate = new Date(Long.MAX_VALUE); // Initializing to the max possible date
    public static Date globalMaxDate = new Date(Long.MIN_VALUE);
    private static final String CURRENT_MONTH;
    private static final String PREVIOUS_MONTH;
    static final String LOG_FILE_PATH;
    static {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        LocalDate now = LocalDate.now();

        CURRENT_MONTH = now.format(formatter);
        PREVIOUS_MONTH = now.minusMonths(1).format(formatter);

        LOG_FILE_PATH = outputFolder +"logfile_" + getCurrentDateTime() + ".txt";
    }
    private static final LocalDate TODAY = LocalDate.now();
    public static char delim = ';';
    public static Synthese syntAncien;
//    public static List<Base> basesSin = new ArrayList<>();
    public static Map<String, Base> baseMap = new HashMap<>();
    public static Map<String, Basenew> baseMapNew = new HashMap<>();
    public static Map<String, Basenew> ficMapNew = new HashMap<>();
    public static List<String> statutsForTreatment;
    public static Map<String, String> globalStatutMap = new HashMap<>();
    public static Map<String, String> globalStatutCollect = new HashMap<>();
    public static Map<String, ArrayList<Float>> coefAQmap = new HashMap<>();

    public static void main(String[] args) throws Exception {
        printMemoryUsage();

        Stopwatch stopwatch = new Stopwatch();
        stopwatch.start();
        Synthese mapStatut = new Synthese(wd + "map_statuts.csv",delim,true,false,false);
        ref_prog = new DF(wd+"Référentiel programmes.csv", ';', true);
        ref_cols = new DF(wd + "refRenta.xlsx","ref_cols");
        ref_source = new DF(wd + "refRenta.xlsx","source",true);
        mapping = new DF(wd + "mapping.xlsx","Mapping entrant sinistres");
        PB = new DF(wd + "PB Micromania.csv",';','!');
        PB.mapPoliceToPB();
        SPprevi = new DF(wd + "S SUR P PREVI 2023_01_18.xlsx","Feuil1");
        SPprevi.mapPoliceToSPPrevi();
        getCoefsAcquisition();


        stopwatch.printElapsedTime("refs");
//        for (int i = 0; i < ref_source.nrow; i++) {
//            boolean a_faire = (ref_source.c("a faire")[i]).equals("oui");
//            if (!a_faire) continue;
//            stopwatch.start();
//            Base.currentHeaderRef = null;
//            String folder = (String) ref_source.c("path")[i];
//            String pays = (String) ref_source.c("pays_filekey")[i];
//            String mapcol = (String) ref_source.c("mapping")[i];
//            String estim = (String) ref_source.c("estimate")[i];
//            String path_fic = (String) ref_source.c("path_fic")[i];
//            String map_fic = (String) ref_source.c("map_fic")[i];
//
//            Estimate estimate = new Estimate(wd+"TDB estimate par gestionnaire/" + estim + ".xlsx");
//            Synthese wf = new Synthese(outputFolder + estim +"_fichier_de_travail.csv",delim,false,true,true);
//
//            File[] fileList = Objects.requireNonNull(new File(wd + folder).listFiles());
//
//            for (File file : fileList) {
////                if (!file.toPath().toString().contains("EXTR")) continue;
//                Base base = new Base(file,pays,mapcol);
////                basesSin.add(base);
//                baseMap.put(base.numPolice, base);
//            }
//            if (pays.equals("Italie")) {
//                File[] fileListGS = Objects.requireNonNull(new File(wd + "source SIN/Gamestop/").listFiles());
//                for (File file : fileListGS) {
////                if (!file.toPath().toString().contains("SMIC"))  continue;
//                    Base base = new Base(file,"Gamestop","SPB Italie Gamestop v1");
//                    baseMap.put(base.numPolice, base);
//                }
//                Base baseGPTB = new Base(new File(wd + "aux SIN/SPB Italie_ICIGPTB15.csv"));
//                Base baseMITL = new Base(new File(wd + "aux SIN/SPB Italie_ICIMITL16.csv"));
//                baseMap.put(baseGPTB.numPolice, baseGPTB);
//                baseMap.put(baseMITL.numPolice, baseMITL);
//            }
////
////            for (Base base : basesSin) {
////                policeStatutDateRangeMap.put(base.numPolice, base.statutDateRangeMap); //par police
////                updateStatutDates(base); //global
////            }
////
//            Base baseFic = new Base(wd + path_fic,map_fic);
//
//            wf.calculateHeaderFrequencies();
//            wf.computeMvAvTvB(baseFic);
//            wf.computeSumByStatutEtDateSurv();
//
//            stopwatch.printElapsedTime();
//        }


        for (int i = 0; i < ref_source.nrow; i++) {
            boolean a_faire = (ref_source.c("a faire")[i]).equals("oui");
            if (!a_faire) continue;
            stopwatch.start();
            Base.currentHeaderRef = null;
            String folder = (String) ref_source.c("path")[i];
            String pays = (String) ref_source.c("pays_filekey")[i];
            String mapcol = (String) ref_source.c("mapping")[i];
            String estim = (String) ref_source.c("estimate")[i];
            String path_fic = (String) ref_source.c("path_fic")[i];
            String map_fic = (String) ref_source.c("map_fic")[i];

            Estimate estimate = new Estimate(wd+"TDB estimate par gestionnaire/" + estim + ".xlsx");

            File[] fileList = Objects.requireNonNull(new File(wd + folder).listFiles());
            List<Base> basesSin = new ArrayList<>();

            for (File file : fileList) {
//                if (!file.toPath().toString().contains("EXDI")) continue;
                Base base = new Base(file,pays,mapcol);
                basesSin.add(base);
            }
            if (pays.equals("Italie")) {
                File[] fileListGS = Objects.requireNonNull(new File(wd + "source SIN/Gamestop/").listFiles());
                for (File file : fileListGS) {
//                if (!file.toPath().toString().contains("GS CN"))  continue;
                    Base base = new Base(file,"Gamestop","SPB Italie Gamestop v1");
                    basesSin.add(base);
                }
                basesSin.add(new Base(new File(wd + "aux SIN/SPB Italie_ICIGPTB15.csv")));
                basesSin.add(new Base(new File(wd + "aux SIN/SPB Italie_ICIMITL16.csv")));
            }

            for (Base base : basesSin) {
                policeStatutDateRangeMap.put(base.numPolice, base.statutDateRangeMap); //par police
                updateStatutDates(base); //global
            }

            estimate.getUniqueStatutsFromMap(); // used for triangles
            estimate.getUniqueNumPoliceEstimate(); // used for nothing (?)
            updateGlobalDatesFromStatutMap();

            Base baseFic = new Base(wd + path_fic,map_fic);
            estimate.addFicMAT(baseFic);

            stopwatch.printElapsedTime(pays + " integré");

            estimate.addSinMAT(basesSin);
            estimate.addProvisions(basesSin);
            estimate.addPrimesAcquises();
            estimate.addSP();

            stopwatch.printElapsedTime("calculé");
            estimate.saveToCSVFile(false);

            stopwatch.start();
        }

        for (int i = 0; i < ref_source.nrow; i++) {
            boolean a_faire = (ref_source.c("a faire")[i]).equals("oui");
            if (!a_faire) continue;
            stopwatch.start();
            Base.currentHeaderRef = null;
            String folder = (String) ref_source.c("path")[i];
            String pays = (String) ref_source.c("pays_filekey")[i];
            String mapcol = (String) ref_source.c("mapping")[i];
            String estim = (String) ref_source.c("estimate")[i];
            String path_fic = (String) ref_source.c("path_fic")[i];
            String map_fic = (String) ref_source.c("map_fic")[i];
            Synthese wf = new Synthese(outputFolder+estim + "_fichier_de_travail.csv",delim,false,true,true);

            syntAncien = new Synthese(wd+"TDB Part 1 Assureur synthèse 202212 avec ICI.xlsx","Synthèse année mois",false,false,false);
            Synthese syntPolice = new Synthese(wf,"", syntAncien);
//            syntAncien = new Synthese(wd+"TDB Part 1 Assureur synthèse 202212 avec ICI.xlsx","Synthèse police",false,false,false);
            Synthese syntPoliceagg = new Synthese(syntPolice,"",true, syntAncien);
            syntPolice.formatAllColumns();
            syntPoliceagg.formatAllColumns();

//            syntAncien = new Synthese(wd+"TDB Part 1 Assureur synthèse 202212 avec ICI.xlsx","Synthèse partenaire",false,false,false);
            Synthese syntDistrib = new Synthese(wf,1, syntAncien);
            Synthese syntDistribagg = new Synthese(syntDistrib,1,true);
            syntDistribagg.formatAllColumns();

//            syntAncien = new Synthese(wd+"TDB Part 1 Assureur synthèse 202212 avec ICI.xlsx","Synthèse génération adhé",false,false,false);
            Synthese syntGest = new Synthese(wf,1.0, syntAncien);
            Synthese syntGestagg = new Synthese(syntGest,1.0,true);
            syntGestagg.formatAllColumns();

            String output = outputFolder + estim + "_output.xlsx";
            syntPolice.exportToExcel(output, "Detaillé", null);
            Workbook workbook = new XSSFWorkbook(new FileInputStream(output));
            syntPoliceagg.exportToExcel(output, "Par Police", workbook);
            syntDistribagg.exportToExcel(output, "Par Distributeur", workbook);
            syntGestagg.exportToExcel(output, "Par Gestionnaire", workbook);

            stopwatch.printElapsedTime();
        }

    }
    public static void getCoefsAcquisition() throws IOException, ParseException, ClassNotFoundException {
        String todayString = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String filePath = wd + todayString + "_coefs.dat";

        File file = new File(filePath);

        if (file.exists()) {
            coefAQmap = (Map<String, ArrayList<Float>>) readObjectFromFile(filePath);
        } else {
            coefAQmap.putAll(TableCoefAcquisition.processDF(new DFnew(wd + "TDB Part 2_Hors France_populated_coef.csv", ';', false, "coefsAQ")));
            coefAQmap.putAll(TableCoefAcquisition.processDF(new DFnew(wd + "TDB Part 2_France_populated_coef.csv", ';', false, "coefsAQ")));
            // The accumulator now has the combined map from both files
            System.out.println(FloatArrayDictionary.getTotalArraysPassed() + " total coefs added");
            System.out.println(FloatArrayDictionary.getUniqueArraysStored() + " unique coefs stored");
            // Save to file
            saveObjectToFile(coefAQmap, filePath);
        }
    }
    public static void saveObjectToFile(Object obj, String filePath) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath))) {
            oos.writeObject(obj);
        }
    }

    public static Object readObjectFromFile(String filePath) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filePath))) {
            return ois.readObject();
        }
    }
    public static boolean isMonthAfterOrEQCurrent(String monthYear) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-yyyy");
        YearMonth inputYearMonth = YearMonth.parse(monthYear, formatter);

        // Extract the current year and month from the TODAY variable
        YearMonth currentYearMonth = YearMonth.from(TODAY);

        // Check if inputYearMonth is after currentYearMonth
        return !inputYearMonth.isBefore(currentYearMonth);
    }
    public static void updateStatutDates(Base base) {
        for (Map.Entry<String, List<Date>> entry : base.statutDateRangeMap.entrySet()) {
            String statut = entry.getKey();
            List<Date> baseDates = entry.getValue();

            if (!globalStatutDateRangeMap.containsKey(statut)) {
                // If this statut doesn't exist in the global map, simply put the current base's dates
                globalStatutDateRangeMap.put(statut, new ArrayList<>(baseDates));
            } else {
                // Otherwise, compare and update min and max dates if needed
                List<Date> globalDates = globalStatutDateRangeMap.get(statut);
                Date globalMinDate = globalDates.get(0);
                Date globalMaxDate = globalDates.get(1);
                Date baseMinDate = baseDates.get(0);
                Date baseMaxDate = baseDates.get(1);

                if (baseMinDate.before(globalMinDate)) {
                    globalDates.set(0, baseMinDate);
                }
                if (baseMaxDate.after(globalMaxDate)) {
                    globalDates.set(1, baseMaxDate);
                }
            }
        }
    }
    public static void updateGlobalDatesFromStatutMap() {
        for (List<Date> dates : globalStatutDateRangeMap.values()) {
            Date currentMinDate = dates.get(0);
            Date currentMaxDate = dates.get(1);

            if (currentMinDate.before(globalMinDate)) {
                globalMinDate = currentMinDate;
            }

            if (currentMaxDate.after(globalMaxDate)) {
                globalMaxDate = currentMaxDate;
            }
        }
    }
    public static ArrayList<DF> loadDataFrames(String filePath) {
        ArrayList<DF> loadedDataframes = null;
        try (FileInputStream fis = new FileInputStream(filePath);
             ObjectInputStream ois = new ObjectInputStream(fis)) {
            loadedDataframes = (ArrayList<DF>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return loadedDataframes;
    }
    public static boolean  check_in(String what, String[] arr) {
        for (String where : arr) {
            if (what.equals(where)) {
                return true;
            }
        }
        return false;
    }
    // VECTORS
    public static String[] keep_from_array(String[] arr, boolean[] which) {
        int len = sum_boolean(which);
        String[] out = new String[len];
        int j = 0;
        for (int i = 0; i < which.length; i++) {
            if (which[i]) {
                out[j] = arr[i];
                j++;
            }
        }
        return out;
    }
    public static DF.Col_types[] keep_from_array(DF.Col_types[] arr, boolean[] which) {
        int len = sum_boolean(which);
        DF.Col_types[] out = new DF.Col_types[len];

        int j = 0;
        for (int i = 0; i < which.length; i++) {
            if (which[i]) {
                out[j] = arr[i];
                j++;
            }
        }
        return out;
    }
    public static Object[] unique_of(Object[] arr) {
        if (arr.length == 1) return arr;
        Set<Object> hash = new LinkedHashSet<>(Arrays.asList(Optional.of(arr).orElse(new Object[0]))); //ofNullable bilo ranshe hz
        return hash.toArray(new Object[0]);
    }
    public static int[] unique_of(int[] arr) {
        if (arr.length == 1) return arr;
        HashMap<Integer, Integer> hashmap = new HashMap<Integer, Integer>();
        for (int j = 0; j < arr.length; j++) {
            hashmap.put(arr[j], j);
        }
        Object[] key_arr = hashmap.keySet().toArray();
        int[] int_arr = new int[key_arr.length];
        for (int i = 0; i < key_arr.length; i++) {
            int_arr[i] = (int) key_arr[i];
        }
        return int_arr;
    }
    public static int sum_boolean(boolean[] vector_boolean) {
        int sum = 0;
        for (boolean b : vector_boolean) {
            sum += b ? 1 : 0;
        }
        return sum;
    }
    public static Integer[] arr_concat(Integer[] arr1, Integer[] arr2) {
        int fal = arr1.length;
        int sal = arr2.length;
        Integer[] result = new Integer[fal + sal];
        System.arraycopy(arr1, 0, result, 0, fal);
        System.arraycopy(arr2, 0, result, fal, sal);
        return result;
    }
    public static boolean in(Object str, Object[] arr) {
        for (Object s : arr) {
            if (s.equals(str)) {
                return true;
            }
        }
        return false;
    }
    public static LocalDate to_Date(Date input) {
        return input.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
    public static void write_csv(Integer[] arr) {
        BufferedWriter br = null;
        try {
            br = new BufferedWriter(new FileWriter(wd + "tester.csv"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        StringBuilder sb = new StringBuilder();

        for (Object element : arr) {
            sb.append(element);
            sb.append("\n");
        }

        try {
            br.write(sb.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            br.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static boolean[] logvec(int dim, boolean values) {
        boolean[] out = new boolean[dim];
        Arrays.fill(out, values);
        return out;
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
    public static int getPositionOfStringContaining(Object[] array, String x) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] != null && array[i].toString().contains(x)) {
                return i;
            }
        }
        return -1; // Return -1 if not found
    }
}