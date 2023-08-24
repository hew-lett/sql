package main.app;

import com.univocity.parsers.csv.CsvParserSettings;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static java.lang.Math.round;
import static java.util.Arrays.fill;

public class App {

    public static final String wd = "E:/202305/wd/";
    public static String encoding = "UTF-8";
    public static CsvParserSettings csv_settings = new CsvParserSettings();
    public static final String regex_digits = "[0-9]+";
    public static final String regex_letters = ".*[a-zA-Z].*";
    public static final Double NA_DBL = 9999099d;
    public static final String NA_STR = "n.a.";
    public static final SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy");
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
    public static DF ref_prog;
    public static DF mapping;
    public static SimpleDateFormat dateDefault = new SimpleDateFormat("dd/MM/yyyy");
    public static Map<String, Map<String, List<Date>>> policeStatutDateRangeMap = new HashMap<>();
    public static Map<String, List<Date>> globalStatutDateRangeMap = new HashMap<>();
    private static final String CURRENT_MONTH;
    private static final String PREVIOUS_MONTH;
    static {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        LocalDate now = LocalDate.now();

        CURRENT_MONTH = now.format(formatter);
        PREVIOUS_MONTH = now.minusMonths(1).format(formatter);
    }

    public static void main(String[] args) throws IOException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, InterruptedException {
        Stopwatch stopwatch = new Stopwatch();
        stopwatch.start();

        ref_prog = new DF(wd+"Référentiel programmes.csv", ';', true);
        ref_cols = new DF(wd + "ref_triangle.xlsx","ref_cols");
        ref_source = new DF(wd + "ref_triangle.xlsx","source",true);
        mapping = new DF(wd + "mapping.xlsx","Mapping entrant sinistres");

        Base base = new Base(wd + "Source FIC/SPB France/","FIC France");
        base.print(20);
//        for (int i = 0; i < ref_source.nrow; i++) {
//            boolean a_faire = (ref_source.c("a faire")[i]).equals("oui");
//            if (!a_faire) continue;
//
//            String folder = (String) ref_source.c("path")[i];
//            String pays = (String) ref_source.c("pays")[i];
//            String mapcol = (String) ref_source.c("mapping")[i];
//            String estim = (String) ref_source.c("estimate")[i];
//
//            Estimate estimate = new Estimate(wd+"TDB estimate par gestionnaire/" + estim + ".xlsx");
//
//            File[] fileList = Objects.requireNonNull(new File(wd + folder).listFiles());
//            List<Base> listBases = new ArrayList<>();
//
//            for (File file : fileList) {
//                Base base = new Base(file,pays,mapcol);
//                listBases.add(base);
//            }
//            for (Base base : listBases) {
//                policeStatutDateRangeMap.put(base.numPolice, base.statutDateRangeMap); //par police
//                updateStatutDates(base); //global
//            }
//            stopwatch.printElapsedTime("integration success");
//
//            estimate.getUniqueStatutsFromMap();
//            estimate.addColumnByType('M',true);
//            estimate.populateMonthStatut(listBases);
//
//            stopwatch.printElapsedTime("calculated");
//            estimate.saveToCSVFile();
//
//        }

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
}