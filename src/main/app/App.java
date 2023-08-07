package main.app;

import com.univocity.parsers.csv.CsvParserSettings;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.Math.round;
import static java.util.Arrays.fill;

public class App {

    public static final String wd = "C:/Users/jukov/Downloads/202305/202305/wd/";
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
    public static DF ref_triangle;
    public static DF ref_prog;
    public static DF mapping;
    public static SimpleDateFormat dateDefault = new SimpleDateFormat("dd/MM/yyyy");
    public static int rowchecker = 0;
    public static Map<String, ArrayList<String>> statuts = new HashMap<>();
    public static ArrayList<String> cols_triangle = new ArrayList<>();
    public static ArrayList<DF> bases_sinistres = new ArrayList<>();
    private static final String CURRENT_MONTH;
    private static final String PREVIOUS_MONTH;

    static {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        LocalDate now = LocalDate.now();

        CURRENT_MONTH = now.format(formatter);
        PREVIOUS_MONTH = now.minusMonths(1).format(formatter);
    }
    public static final HashMap<String, Integer> monthMap = new HashMap<String, Integer>() {{
        put("jan.", Calendar.JANUARY);
        put("feb.", Calendar.FEBRUARY);
        put("mar.", Calendar.MARCH);
        put("apr.", Calendar.APRIL);
        put("may.", Calendar.MAY);
        put("jun.", Calendar.JUNE);
        put("jul.", Calendar.JULY);
        put("aug.", Calendar.AUGUST);
        put("sep.", Calendar.SEPTEMBER);
        put("oct.", Calendar.OCTOBER);
        put("nov.", Calendar.NOVEMBER);
        put("dec.", Calendar.DECEMBER);
    }};
    public static void main(String[] args) throws IOException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, InterruptedException {
        long startTime = System.nanoTime();long endTime;long duration;long minutes;long seconds;
        ref_prog = new DF(wd+"Référentiel programmes.csv", ';');
        ref_triangle = new DF(wd + "ref_triangle.xlsx");
        mapping = new DF(wd + "mapping.xlsx");
        mapping.printDataFrame();
//        String path_sin = wd + "SINISTRE par gestionnaire pour les triangles/";
//        ArrayList<DF> bases_sinistres = loadDataFrames(wd + "dataframes.ser");
//        ArrayList<DF> dataframes = getDataFramesFromFolder(path_sin);
//        saveDataFrames(dataframes, wd + "dataframes.ser");
//        statuts = groupAndCombineStatuts(bases_sinistres);
//        cols_triangle = generateColumnNames();

//        Estimate estimate = new Estimate(wd+"TDB estimate par gestionnaire/Garantie Privée.xlsx");
//        processFilesInFolder(wd + "TDB estimate par gestionnaire/");
//        for (Map.Entry<String, ArrayList<String>> entry : statuts.entrySet()) {
//            String key = entry.getKey();
//            ArrayList<String> statut = entry.getValue(); // Changed from Set<String> to ArrayList<String>
//
//            System.out.println("Key: " + key);
//            System.out.println("Statuts:");
//            for (String s : statut) {
//                System.out.println("\t" + s);
//            }
//        }

//        for (DF d: dataframes) {
//            System.out.println(d.fileName);
//            d.print(10);
//        }


    }
    public static void processFilesInFolder(String folderPath) {
        // Get the start time
        long startTime = System.currentTimeMillis();

        File folder = new File(folderPath);
        File[] listOfFiles = folder.listFiles();

        if (listOfFiles != null) {
            for (File file : listOfFiles) {
                if (file.isFile() && file.getName().endsWith(".xlsx")) {
                    try {
                        // Call the constructor for each Excel file
                        Estimate estimate = new Estimate(file.getAbsolutePath());
                        // You can also call other methods on the "estimate" object here if needed
                    } catch (IOException e) {
                        System.err.println("An error occurred while processing the file: " + file.getAbsolutePath());
                        e.printStackTrace();
                    }
                }
            }
        } else {
            System.err.println("The specified folder does not exist or is not a directory: " + folderPath);
        }

        // Get the end time
        long endTime = System.currentTimeMillis();

        // Print the total time of execution
        System.out.println("Total execution time: " + (endTime - startTime) + " milliseconds");
    }
    public static ArrayList<String> generateColumnNames() {
        ArrayList<String> columnNames = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");

        Calendar start = Calendar.getInstance();
        Calendar end = Calendar.getInstance();

        // Set the start and end dates
        start.set(2013, Calendar.NOVEMBER, 1); // 01/11/2013
        end.set(2026, Calendar.DECEMBER, 1); // 01/12/2026

        while (start.before(end)) {
            // Add the current date to the list
            columnNames.add(sdf.format(start.getTime()));

            // Increment the month by 1
            start.add(Calendar.MONTH, 1);
        }

        // Add the final date to the list
        columnNames.add(sdf.format(end.getTime()));

        return columnNames;
    }
    public static Map<String, ArrayList<String>> groupAndCombineStatuts(ArrayList<DF> dataFrames) {
        // Map to hold the groups keyed by the substring of filename up to the first "_"
        Map<String, Set<String>> groupedStatuts = new HashMap<>();

        for (DF df : dataFrames) {
            // Extract the key from filename (substring until the first appearance of "_")
            String key = df.fileName.split("_")[0];

            // Get the existing unique statuts for this key, or create a new set if not present
            Set<String> uniqueStatuts = groupedStatuts.getOrDefault(key, new HashSet<>());

            // Add all unique statuts from the current DF object
            uniqueStatuts.addAll(df.statut_unique);

            // Put the updated unique statuts back in the map
            groupedStatuts.put(key, uniqueStatuts);
        }

        // Convert the Set values to ArrayList
        Map<String, ArrayList<String>> result = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : groupedStatuts.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        return result;
    }
    public static void printTime(long startTime) {
        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1_000_000; // in milliseconds
        long minutes = TimeUnit.MILLISECONDS.toMinutes(duration);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(duration) - TimeUnit.MINUTES.toSeconds(minutes);
        System.out.println(minutes + " minutes " + seconds + " seconds");
    }
    public static ArrayList<DF> getBasesFromFolder(String path) {
        ArrayList<DF> dataframes = new ArrayList<>();
        try {
            Files.list(Paths.get(path))
                    .filter(Files::isRegularFile)
                    .forEach(filePath -> {
                        try {
                            DF dataframe = new BaseSin(filePath.toString());
                            dataframes.add(dataframe);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
        return dataframes;
    }
    public static void saveDataFrames(ArrayList<DF> dataframes, String filePath) {
        try (FileOutputStream fos = new FileOutputStream(filePath);
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(dataframes);
        } catch (IOException e) {
            e.printStackTrace();
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
    public static void checkMissingMontantIP(ArrayList<DF> dataFrames) {
        for (DF df : dataFrames) {
            System.out.println("Filename: " + df.fileName);

            int columnIndex = -1;
            for (int i = 0; i < df.header.length; i++) {
                if (df.header[i].equals("montant_IP")) {
                    columnIndex = i;
                    break;
                }
            }

            if (columnIndex == -1) {
                System.out.println("Column 'montant_IP' not found");
                continue;
            }

            Object[] column = df.c(columnIndex);
            List<Integer> missingRows = new ArrayList<>();

            for (int rowIndex = 0; rowIndex < column.length; rowIndex++) {
                if (column[rowIndex].equals(NA_DBL)) {
                    missingRows.add(rowIndex);
                }
            }

            if (missingRows.isEmpty()) {
                System.out.println("All values in 'montant_IP' are ok");
            } else {
                System.out.println("Rows with missing 'montant_IP' values: " + missingRows);
            }
        }
    }
    public static void checkexcel(String path) {
        try {
            FileInputStream fileInputStream = new FileInputStream(path);
            Workbook workbook = new XSSFWorkbook(fileInputStream);
            Sheet sheet = workbook.getSheetAt(0); // Get the first sheet

            Iterator<Row> rowIterator = sheet.rowIterator();

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                Iterator<Cell> cellIterator = row.cellIterator();

                while (cellIterator.hasNext()) {
                    Cell cell = cellIterator.next();

                    // Check the cell type and print accordingly
                    switch (cell.getCellTypeEnum()) {
                        case STRING:
                            System.out.print(cell.getStringCellValue() + "\t");
                            break;
                        case NUMERIC:
                            System.out.print(cell.getNumericCellValue() + "\t");
                            break;
                        case BOOLEAN:
                            System.out.print(cell.getBooleanCellValue() + "\t");
                            break;
                        default:
                            break;
                    }
                }
                System.out.println(); // Print a new line for each row
            }

            workbook.close();
            fileInputStream.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void checkMissingValues(ArrayList<DF> dataFrames) {
        for (DF df : dataFrames) {
            System.out.println("Filename: " + df.fileName);

            boolean allValuesOk = true;

            for (int i = 0; i < df.ncol; i++) {
                int missingCount = 0;
                Object[] column = df.c(i);

                switch (df.coltypes[i]) {
                    case DBL -> {
                        for (Object value : column) {
                            if (value.equals(NA_DBL)) {
                                missingCount++;
                            }
                        }
                    }
                    case STR -> {
                        for (Object value : column) {
                            if (value.equals(NA_STR)) {
                                missingCount++;
                            }
                        }
                    }
                    case DAT -> {
                        for (Object value : column) {
                            if (value.equals(NA_DAT)) {
                                missingCount++;
                            }
                        }
                    }
                }

                if (missingCount > 0) {
                    allValuesOk = false;
                    double percentageMissing = ((double) missingCount / df.nrow) * 100;
                    System.out.println("Column " + df.header[i] + " has " + percentageMissing + "% data missing");
                }
            }

            if (allValuesOk) {
                System.out.println("All values are ok");
            }
        }
    }
    public static void checkMissingValues_print(ArrayList<DF> dataFrames) {
        for (DF df : dataFrames) {
            System.out.println("Filename: " + df.fileName);

            boolean allValuesOk = true;

            for (int i = 0; i < df.ncol; i++) {
                int missingCount = 0;
                Object[] column = df.c(i);
                List<Integer> missingRows = new ArrayList<>();

                switch (df.coltypes[i]) {
                    case DBL -> {
                        for (int rowIndex = 0; rowIndex < column.length; rowIndex++) {
                            Object value = column[rowIndex];
                            if (value.equals(NA_DBL)) {
                                missingCount++;
                                missingRows.add(rowIndex);
                            }
                        }
                    }
                    case STR -> {
                        for (int rowIndex = 0; rowIndex < column.length; rowIndex++) {
                            Object value = column[rowIndex];
                            if (value.equals(NA_STR)) {
                                missingCount++;
                                missingRows.add(rowIndex);
                            }
                        }
                    }
                    case DAT -> {
                        for (int rowIndex = 0; rowIndex < column.length; rowIndex++) {
                            Object value = column[rowIndex];
                            if (value.equals(NA_DAT)) {
                                missingCount++;
                                missingRows.add(rowIndex);
                            }
                        }
                    }
                }

                if (missingCount > 0) {
                    allValuesOk = false;
                    double percentageMissing = ((double) missingCount / df.nrow) * 100;
                    System.out.println("Column " + df.header[i] + " has " + percentageMissing + "% data missing. Missing rows: " + missingRows);
                }
            }

            if (allValuesOk) {
                System.out.println("All values are ok");
            }
        }
    }
    public static ArrayList<String> filter_out (ArrayList<ArrayList<String>> df, String crit1, String val1, String crit2, String val2, String field) {
        ArrayList<String> out = new ArrayList<>();
        int ncol = df.size();
        int nrow = df.get(0).size();
        int ind1 = -1; int ind2 = -1; int ind3 = -1;
        for (int i = 0; i < ncol; i++) {
            if (Objects.equals(df.get(i).get(0), crit1)) {
                ind1 = i;
            }
            if (Objects.equals(df.get(i).get(0), crit2)) {
                ind2 = i;
            }
            if (Objects.equals(df.get(i).get(0), field)) {
                ind3 = i;
            }
        }
        for (int i = 0; i < nrow; i++) {
            if(df.get(ind1).get(i).equals(val1)) {
                if(df.get(ind2).get(i).equals(val2)) {
                    out.add(df.get(ind3).get(i));
                }
            }
        }
        return out;
    }
    public static char get_delim(String delim) {
        if(delim.length() > 1) {
            return '\t';
        } else {
            return delim.charAt(0);
        }
    }
    public static void get_yyyymm() {
        Date today = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(today);
        int month = cal.get(Calendar.MONTH) - 1;
        int year = cal.get(Calendar.YEAR);
        yyyymm = year +  String.format("%02d", month);
    }
    public static void write_temps_exec(String quoi, String flux, String controle, String temps) {
        Rapport_temps_exec.get(0).add(quoi);
        Rapport_temps_exec.get(1).add(flux);
        Rapport_temps_exec.get(2).add(controle);
        Rapport_temps_exec.get(3).add(temps);
    }
    public static void rapport_init() {
        String[] rapport_cols = {"Police", "Flux", "Controle", "ID"};
        for (int i = 0; i < rapport_cols.length; i++) {
            Rapport.add(new ArrayList<>());
            Rapport.get(i).add(rapport_cols[i]);
        }

        String[] rapport_log_cols = {"Police", "Flux", "Controle", "Commentaire"};
        for (int i = 0; i < rapport_log_cols.length; i++) {
            Log_err.add(new ArrayList<>());
            Log_err.get(i).add(rapport_log_cols[i]);
        }

        String[] temps_exec_cols = {"Police", "Flux", "Controle", "Temps"};
        for (int i = 0; i < temps_exec_cols.length; i++) {
            Rapport_temps_exec.add(new ArrayList<>());
            Rapport_temps_exec.get(i).add(temps_exec_cols[i]);
        }
    }
    public static void rapport_print() {
        for (int i = 0; i < Rapport.get(0).size(); i++) {
            System.out.print("| ");
            for (ArrayList<String> strings : Rapport) {
                System.out.print(strings.get(i) + " | ");
            }
            System.out.println();
        }
    }
    public static void log_err_save() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM HH.mm");
        LocalDateTime now = LocalDateTime.now();
        BufferedWriter br = null;
        try {
            br = new BufferedWriter(new FileWriter(wd + "Rapports/log "+dtf.format(now)+".csv"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < Log_err.get(0).size(); i++) {
            for (ArrayList<String> col : Log_err) {
                sb.append(col.get(i));
                sb.append(';');
            }
            sb.replace(sb.length() - 1, sb.length(), "\r\n");
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
    public static void temps_exec_save() {
        int nrow = Rapport_temps_exec.get(0).size();

        boolean[] rem = logvec(nrow,false);

        for (int i = 1; i < nrow; i++) {
            if (Rapport_temps_exec.get(2).get(i).equals("prep") & (i != nrow-1)) {
                for (int j = i+1; j < nrow; j++) {
                    if (Rapport_temps_exec.get(2).get(j).equals("prep") & Rapport_temps_exec.get(0).get(j).equals(Rapport_temps_exec.get(0).get(i))) {
                        double v1 = Double.parseDouble(Rapport_temps_exec.get(3).get(i));
                        double v2 = Double.parseDouble(Rapport_temps_exec.get(3).get(j));
                        double v3 = v1 + v2;
                        Rapport_temps_exec.get(3).set(i,v3 + "");
                        rem[j] = true;
                    }
                }
            }
        }
        for (int i = nrow-1; i > -1; i--) {
            if (rem[i]) {
                for (int k = 0; k < 4; k++) {
                    Rapport_temps_exec.get(k).remove(i);
                }
            }
        }

        Object[] controles_obj = unique_of(Rapport_temps_exec.get(2).toArray());
        Object[] polices_obj = unique_of(Rapport_temps_exec.get(0).toArray());
        String[] controles = Arrays.stream(controles_obj).map(Object::toString).
                toArray(String[]::new);
        String[] polices = Arrays.stream(polices_obj).map(Object::toString).
                toArray(String[]::new);
        int ncol = polices.length + 1;
        nrow = controles.length;
        ArrayList<ArrayList<String>> df = new ArrayList<>();
        df.add(new ArrayList<String>());
        for (int i = 0; i < nrow; i++) {
            df.get(0).add(controles[i]);
        }

        for (int i = 1; i < ncol; i++) {
//            if (Objects.equals(polices[i - 1], "Police")) continue;
            df.add(new ArrayList<String>());
            if (i == ncol-1) {
                df.get(i).add("total");
            } else {
                df.get(i).add(polices[i]);
            }
            for (int j = 1; j < nrow; j++) {
                df.get(i).add("");
            }
        }

        int nrow_1 = Rapport_temps_exec.get(0).size();
        for (int i = 1; i < nrow; i++) {
            for (int j = 1; j < ncol-1; j++) {
                String pol = polices[j];
                String controle = controles[i];
//                if (Objects.equals(pol, "Police") | Objects.equals(controle, "Controle")) continue;
                for (int k = 1; k < nrow_1; k++) {
                    if(Rapport_temps_exec.get(0).get(k).equals(pol) & Rapport_temps_exec.get(2).get(k).equals(controle)) {
                        df.get(j).set(i,Rapport_temps_exec.get(3).get(k));
                    }
                }
            }
            double sum = 0;
            for (int j = 1; j < ncol-1; j++) {
                if (!Objects.equals(df.get(j).get(i), "")) {
                    sum += Double.parseDouble(df.get(j).get(i));
                }
            }
            df.get(ncol-1).set(i, String.valueOf(sum));
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM HH.mm");
        LocalDateTime now = LocalDateTime.now();
        BufferedWriter br = null;
        try {
            br = new BufferedWriter(new FileWriter(wd + "Rapports/temps exec "+dtf.format(now)+".csv"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        StringBuilder sb = new StringBuilder();


        for (int i = 0; i < nrow; i++) {
            for (ArrayList<String> col : df) {
                sb.append(col.get(i));
                sb.append(';');
            }
            sb.replace(sb.length() - 1, sb.length(), "\r\n");
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

    // DATA
    public static ArrayList <String> not_in(String[] what, String[] where) {
        ArrayList <String> notin = new ArrayList<>();

        for (String value : what) {
            boolean check = false;
            for (String ref : where) {
                if (value.equals(ref)) {
                    check = true;
                }
            }
            if (!check) notin.add(value);
        }
        return notin;
    }
    public static ArrayList <String> not_in(String what, String[] where) {
        ArrayList <String> notin = new ArrayList<>();

        boolean check = false;
        for (String ref : where) {
            if (what.equals(ref)) {
                check = true;
            }
        }
        if (!check) notin.add(what);
        return notin;
    }
    public static boolean  check_in(String[] what, String[] where) {
        int counter = 0;
        for (String value : what) {
            for (String ref : where) {
                if (value.equals(ref)) {
                    counter++;
                    break;
                }
            }
        }
        return counter == what.length;
    }
    public static boolean  check_in(String what, String[] arr) {
        for (String where : arr) {
            if (what.equals(where)) {
                return true;
            }
        }
        return false;
    }
    public static boolean  check_in(Object what, Object[] arr) {
        for (Object where : arr) {
            if (what.equals(where)) {
                return true;
            }
        }
        return false;
    }
    public static ArrayList<Integer> get_all_occurences(String str, char c) {
        ArrayList<Integer> ind = new ArrayList<>();
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == c) {
                ind.add(i);
            }
        }
        return ind;
    }

    // VECTORS
    public static String[] swap(String[] array, int a, int b) {
        int dim = array.length;
        String[] out = new String[dim];
        System.arraycopy(array, 0, out, 0, dim);
        if (a >= dim | b >= dim | a < 0 | b < 0) {
            return array;
        } else {
            out[a] = array[b];
            out[b] = array[a];
        }
        return out;
    }
    public boolean[] is_in_arr ( String[] arr, String value) {
        final int len = arr.length;
        boolean[] out = new boolean[len];
        for (int i = 0; i < len; i++) {
            out[i] = arr[i].equals(value);
        }
        return out;
    }
    public static String[] filter_array_by(String[] arr, String by) {
        return filter_array_by(arr, by, false);
    }
    public static String[] filter_array_by_containing(String[] arr, String by) {
        ArrayList<String> out_list = new ArrayList<>();
        for (String s : arr) {
            if (s.contains(by)) {
                out_list.add(s);
            }
        }
        return(out_list.toArray(new String[0]));
    }
    public static String[] filter_array_by(String[] arr, String by, boolean filter_out) {
        int j = 0;
        int i = 0;
        for (; i < arr.length; i++) {
            if (Objects.equals(arr[i], by) ^ filter_out) {
                arr[j] = arr[i];
                j++;
            }
        }
        if (j == i) {
            return arr;
        }
        String[] out = new String[j];
        System.arraycopy(arr, 0, out, 0, j);
        return out;
    }
    public static Object[] keep_from_array(Object[] arr, boolean[] which) {
        int len = sum_boolean(which);
        Object[] out = new Object[len];
        int j = 0;
        for (int i = 0; i < which.length; i++) {
            if (which[i]) {
                out[j] = arr[i];
                j++;
            }
        }
        return out;
    }
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
    public static int[] keep_from_array(int[] arr, boolean[] which) {
        int len = sum_boolean(which);
        int[] out = new int[len];
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
    public static int[] which(boolean[] bool) {
        int sum = sum_boolean(bool);
        if (sum == 0) {
            return null;
        }
        int[] vec = new int[sum];
        int j = 0;
        for (int i = 0; i < bool.length; i++) {
            if (bool[i]) {
                vec[j] = i;
                j++;
            }
        }
        return vec;
    }
    public static Object whichf(boolean[] bool) {
        for (int i = 0; i < bool.length; i++) {
            if (bool[i]) {
                return i;
            }
        }
        return -1;
    }
    public static boolean[] find_in_arr(Object[] arr, Object value) {
        final int len = arr.length;
        boolean[] out = new boolean[len];
        for (int i = 0; i < len; i++) {
            out[i] = arr[i].equals(value);
        }
        return out;
    }
    public static boolean[] find_in_arr2(String[] arr, String value1, String value2) {
        final int len = arr.length;
        boolean[] out = new boolean[len];
        for (int i = 0; i < len; i++) {
            out[i] = arr[i].equals(value1) | arr[i].equals(value2);
        }
        return out;
    }
    public static boolean[] find_in_arr3(String[] arr, String value1, String value2, String value3) {
        final int len = arr.length;
        boolean[] out = new boolean[len];
        for (int i = 0; i < len; i++) {
            out[i] = arr[i].equals(value1) | arr[i].equals(value2) | arr[i].equals(value3);
        }
        return out;
    }
    public static int find_in_arr_first_index(Object[] arr, Object value) {
        final int len = arr.length;
        int out = -1;
        for (int i = 0; i < len; i++) {
            if (arr[i] == null) continue;
            if (arr[i].equals(value)) {
                out = i;
                break;
            }
        }
        return out;
    }
    public static int which_contains_first_index(String[] arr, String value) {
        final int len = arr.length;
        int out = -1;
        for (int i = 0; i < len; i++) {
            if (arr[i] == null) continue;
            if (arr[i].contains(value)) {
                out = i;
            }
        }
        return out;
    }
    public static int which_contains_first_index(Object[] arr, String value) {
        final int len = arr.length;
        int out = -1;
        for (int i = 0; i < len; i++) {
            if (arr[i] == null) continue;
            if (((String) arr[i]).contains(value)) {
                out = i;
            }
        }
        return out;
    }
    public static Object[] unique_of(Object[] arr) {
        if (arr.length == 1) return arr;
        Set<Object> hash = new LinkedHashSet<>(Arrays.asList(Optional.of(arr).orElse(new Object[0]))); //ofNullable bilo ranshe hz
        return hash.toArray(new Object[0]);
    }
    public static String[] unique_of(String[] arr) {
        if (arr.length == 1) return arr;
        Set<String> hash = new LinkedHashSet<>(Arrays.asList(Optional.of(arr).orElse(new String[0]))); //ofNullable bilo ranshe hz
        return hash.toArray(new String[0]);
    }
    public static Integer[] unique_of(Integer[] arr) {
        if (arr.length == 1) return arr;
        Set<Integer> hash = new LinkedHashSet<>(Arrays.asList(Optional.of(arr).orElse(new Integer[0]))); //ofNullable bilo ranshe hz
        return hash.toArray(new Integer[0]);
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
    public static boolean[] unique_bool(Object[] arr) {
        Set<Object> hash = new LinkedHashSet<>();
        boolean[] out = new boolean[arr.length];
        for (int i = 0; i < arr.length; i++) {
            out[i] = hash.add(arr[i]);
        }
        return out;
    }
    public static int sum_boolean(boolean[] vector_boolean) {
        int sum = 0;
        for (boolean b : vector_boolean) {
            sum += b ? 1 : 0;
        }
        return sum;
    }
    public static boolean[] b_and(boolean[] arr1, boolean[] arr2) {
        assert (arr1.length == arr2.length);
        boolean[] out = new boolean[arr1.length];
        for (int i = 0; i < arr1.length; i++) {
            out[i] = arr1[i] & arr2[i];
        }
        return out;
    }
    public static boolean[] a_and_b_and_c(boolean[] arr1, boolean[] arr2, boolean[] arr3) {
        assert (arr1.length == arr2.length);
        boolean[] out = new boolean[arr1.length];
        for (int i = 0; i < arr1.length; i++) {
            out[i] = arr1[i] & arr2[i] & arr3[i];
        }
        return out;
    }
    public static Integer[] push_to_end_ind(String[] arr, String[] pushed) {
        Integer[] out = new Integer[arr.length];
        int j = 0;
        int k = arr.length - pushed.length;
        for (int i = 0; i < arr.length; i++) {
            if (!in(arr[i], pushed)) {
                out[j] = i;
                j++;
            } else {
                out[k] = i;
                k++;
            }
        }
        return out;
    }
    public static Integer[] arr_concat(Integer[] arr1, Integer[] arr2) {
        int fal = arr1.length;
        int sal = arr2.length;
        Integer[] result = new Integer[fal + sal];
        System.arraycopy(arr1, 0, result, 0, fal);
        System.arraycopy(arr2, 0, result, fal, sal);
        return result;
    }
    public static Object[] arr_concat(Object[] arr1, Object[] arr2) {
        int fal = arr1.length;
        int sal = arr2.length;
        Object[] result = new Object[fal + sal];
        System.arraycopy(arr1, 0, result, 0, fal);
        System.arraycopy(arr2, 0, result, fal, sal);
        return result;
    }
    public static Object[] arr_merge(Object[] arr1, Object[] arr2) {
        int fal = arr1.length;
        int sal = arr2.length;
        Object[] result = new Object[fal + sal];
        System.arraycopy(arr1, 0, result, 0, fal);
        System.arraycopy(arr2, 0, result, fal, sal);
        result = unique_of(result);
        return result;
    }
    public static int[] arr_merge(int[] arr1, int[] arr2) {
        int fal = arr1.length;
        int sal = arr2.length;
        int[] result = new int[fal + sal];
        System.arraycopy(arr1, 0, result, 0, fal);
        System.arraycopy(arr2, 0, result, fal, sal);
        result = unique_of(result);
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
    public static boolean arr1_contains_arr2(String[] arr1, String[] arr2) {
        for (String s : arr2) {
            if (!in(s, arr1)) {
                return false;
            }
        }
        return true;
    }
    public static Integer[] sortIndices(int[] input, boolean descending) {

        Integer[] indices = new Integer[input.length];

        for (int i = 0; i < input.length; i++)
            indices[i] = i;
        if (descending) {
            Arrays.sort(indices, new Comparator<Integer>() {
                @Override
                public int compare(Integer o1, Integer o2) {
                    return input[o2] - input[o1];
                }
            });
        } else {
            Arrays.sort(indices, new Comparator<Integer>() {
                @Override
                public int compare(Integer o1, Integer o2) {
                    return input[o1] - input[o2];
                }
            });
        }

        return indices;
    }
    public static Integer[] sort_by_2_vars(int[] arr1, int[] arr2, int interval, int total) {
        int[] var1 = Arrays.copyOf(arr1, arr1.length);
        int[] var2 = Arrays.copyOf(arr2, arr2.length);

        Integer[] first_sort = sortIndices(var1, false);
        Integer[] second_sort = new Integer[0];
        var2 = shuffle(var2, first_sort);
        Arrays.sort(var1);

        int len = var1.length;
        int interval_counter = 1;
        int debut = 0;
        int fin;
        for (int i = 0; i < len; i++) {
            if (i == len - 1) {
                fin = i + 1;
                int[] var2_tranche = Arrays.copyOfRange(var2, debut, fin);
                second_sort = arr_concat(second_sort, sortIndices(var2_tranche, false));
                break;
            }
            if (var1[i] * 100.0 / total > interval * interval_counter) {
                fin = i - 1;
                int[] var2_tranche = Arrays.copyOfRange(var2, debut, fin);
                second_sort = arr_concat(second_sort, sortIndices(var2_tranche, false));
                debut = fin + 1;
                interval_counter++;
            }

        }
        for (int i = 0; i < len; i++) {
            second_sort[i] = first_sort[second_sort[i]];
        }

        return second_sort;
    }
    public static Object[] shuffle(Object[] arr, Integer[] idx) {
        int len = arr.length;
        Object[] out = new Object[len];
        for (int i = 0; i < len; i++) {
            out[i] = arr[idx[i]];
        }
        return out;
    }
    public static int[] shuffle(int[] arr, Integer[] idx) {
        int len = arr.length;
        int[] out = new int[len];
        for (int i = 0; i < len; i++) {
            out[i] = arr[idx[i]];
        }
        return out;
    }
    public static String[] shuffle(String[] arr, Integer[] idx) {
        int len = arr.length;
        String[] out = new String[len];
        for (int i = 0; i < len; i++) {
            out[i] = arr[idx[i]];
        }
        return out;
    }
    public static Integer[] shuffle(Integer[] arr, Integer[] idx) {
        int len = arr.length;
        Integer[] out = new Integer[len];
        for (int i = 0; i < len; i++) {
            out[i] = arr[idx[i]];
        }
        return out;
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