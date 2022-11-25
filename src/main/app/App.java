package main.app;

import com.monitorjbl.xlsx.StreamingReader;
import com.opencsv.CSVWriter;
import com.opencsv.CSVWriterBuilder;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.apache.poi.ss.usermodel.Workbook;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.sql.SQLOutput;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static java.lang.Math.round;
import static java.util.Arrays.fill;

public class App {

    public static final String wd = "C:/Users/ozhukov/Desktop/";
    public static final String path_grilles = wd + "grilles/";
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
    public static DF mapping_sin_g;
    public static DF mapping_adh_g;
    public static DF grille_gen_g;
    public static String Police_en_cours = "default";
    public static String Police_en_cours_maj = "default";
    public static String Controle_en_cours = "default";
    public static String Flux_en_cours = "default";
    public static ArrayList<ArrayList<String>> Rapport = new ArrayList<>();
    public static HashMap<String, DF.Col_types> coltypes_G = new HashMap<String, DF.Col_types>();
    public static HashMap<String, DF.Col_types> coltypes_B = new HashMap<String, DF.Col_types>();
    public static HashMap<String, DF> grilles_G = new HashMap<String, DF>();
    public static HashMap<String, Method> controles_G = new HashMap<>();
    public static HashMap<String, Boolean> params_G = new HashMap<>();

    public static void main(String[] args) throws IOException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        {
            String encoding = "UTF-8";
            CsvParserSettings settings = new CsvParserSettings();
            settings.setDelimiterDetectionEnabled(true, ',');
            settings.trimValues(true);
            try(Reader inputReader = new InputStreamReader(Files.newInputStream(
                    new File(wd+"coltypes.csv").toPath()), encoding)){
                CsvParser parser = new CsvParser(settings);
                List<String[]> parsedRows = parser.parseAll(inputReader);
                Iterator<String[]> rows = parsedRows.iterator();
                int i = 0;
                while(rows.hasNext()) {
                    String[] values = rows.next();
                    switch (values[1]) {
                        case "1":
                            coltypes_G.put(values[0],DF.Col_types.STR);
                            break;
                        case "2":
                            coltypes_G.put(values[0],DF.Col_types.DBL);
                            break;
                        case "3":
                            coltypes_G.put(values[0],DF.Col_types.DAT);
                            break;
                        case "4":
                            coltypes_G.put(values[0],DF.Col_types.SKP);
                    }
                }
            }
        } // get coltypes for grille
        {
            String encoding = "UTF-8";
            CsvParserSettings settings = new CsvParserSettings();
            settings.setDelimiterDetectionEnabled(true, ',');
            settings.trimValues(true);
            try(Reader inputReader = new InputStreamReader(Files.newInputStream(
                    new File(wd+"coltypes_base.csv").toPath()), encoding)){
                CsvParser parser = new CsvParser(settings);
                List<String[]> parsedRows = parser.parseAll(inputReader);
                Iterator<String[]> rows = parsedRows.iterator();
                int i = 0;
                while(rows.hasNext()) {
                    String[] values = rows.next();
                    switch (values[1]) {
                        case "1":
                            coltypes_B.put(values[0],DF.Col_types.STR);
                            break;
                        case "2":
                            coltypes_B.put(values[0],DF.Col_types.DBL);
                            break;
                        case "3":
                            coltypes_B.put(values[0],DF.Col_types.DAT);
                            break;
                        case "4":
                            coltypes_B.put(values[0],DF.Col_types.SKP);
                    }
                }
            }
        } // get coltypes for base
        grilles_import();
        String[] rapport_cols = {"Police","Controle","ID"};
        for (int i = 0; i < rapport_cols.length; i++) {
            Rapport.add(new ArrayList<>());
            Rapport.get(i).add(rapport_cols[i]);
        }

        Class<DF> classobj = DF.class;
        Method[] methods = classobj.getMethods();
        for (Method method : methods) {
            String name = method.getName();
            if(name.startsWith("controle")) {
                controles_G.put(name,method);
                Class<?>[] types = method.getParameterTypes();
                if (types.length > 0)  {
                    params_G.put(name,true);
                } else {
                    params_G.put(name,false);
                }
            }
        }

        Flux_en_cours = "Sinistre";
        String encode = "UTF-8";
        String path_mapping = "Mapping des flux adhésion et sinistre gestionnaire.xlsx";
        String mapping_sin_onglet = "Mapping bases sinistres";
        String mapping_adh_onglet = "Mapping bases adhésions";
        mapping_sin_g = new DF(wd + path_mapping,mapping_sin_onglet,true,false);
        mapping_sin_g.mapping_traitement();

        String path_gg = "Grille Générique.csv";
        char delim_gg = ';';
        grille_gen_g = new DF(wd + path_gg,delim_gg,encode,false);

        long startTime = System.nanoTime();

        String path_sin = "Sinistre_Historique_ICIMM101_303_20221106.txt";
        char delim_sin = '|';
        DF base = new DF(wd + path_sin,delim_sin,encode,true);

        String path_adh = "Adhesion_Historique_ICIMM101_303_20221102.txt";
        char delim_adh = '|';
        DF base_adh = new DF(wd + path_adh,delim_adh,encode,true);
        Police_en_cours_maj = get_name_fr(path_sin);
        Police_en_cours = Police_en_cours_maj.toLowerCase();

        String mapping_sin_col = "SPB France / Wakam";
        DF map_sin = mapping_filtre(mapping_sin_col);
        base.subst_columns(map_sin);
        String mapping_adh_col = "SPB France / Wakam";
        DF map_adh = mapping_filtre(mapping_adh_col);
        base.subst_columns(map_adh);

        boolean[] keep = find_in_arr(grille_gen_g.c("Numero_Police"), Police_en_cours_maj);
        boolean[] keep2 = find_in_arr(grille_gen_g.c("Flux"), Flux_en_cours);

        boolean[] crit = b_and(keep,keep2);
        base.grille_gen = new DF(grille_gen_g,crit);
        base.grille_gen.printgrille();
        System.out.println(((System.nanoTime() - startTime)/1e7f)/100.0+ "sssssss");

        startTime = System.nanoTime();

//        controles_G.get("controle_805").invoke(base);

        for (Map.Entry<String, Method> set : controles_G.entrySet()) {
            System.out.println(set.getKey());
            if (params_G.get(set.getKey())) {
                set.getValue().invoke(base,base_adh);
            } else {
                set.getValue().invoke(base);
            }
        }

//        rapport_print();
        System.out.println(((System.nanoTime() - startTime)/1e7f)/100.0);

}
    public static void rapport_print () {
        for (int i = 0; i < Rapport.get(0).size(); i++) {
            System.out.print("| ");
            for (int j = 0; j < Rapport.size(); j++) {
                System.out.print(Rapport.get(j).get(i) + " | ");
            }
            System.out.println();
        }
    }
    public static DF mapping_filtre(String col) {
        boolean[] vec = logvec(mapping_sin_g.ncol,false);
        int ind = find_in_arr_first_index(mapping_sin_g.header, col);
        assert(ind != -1);
        vec[0] = true; // sous condition que la colonne format ICI était toujours la premiere
        vec[ind] = true;
        return new DF(mapping_sin_g,vec,true);
    }
    public static String get_name_fr (String path) {
        ArrayList<Integer> ind = get_all_occurences(path,'_');
        if (ind.isEmpty()) {
            err("pb naming france: " + path);
            return "";
        } else {
            return path.substring(ind.get(1)+1,ind.get(2));
        }
    }
    public static ArrayList<Integer> get_all_occurences (String str,char c) {
        ArrayList<Integer> ind = new ArrayList<>();
        for (int i = 0; i < str.length(); i++) {
            if(str.charAt(i)==c) {
                ind.add(i);
            }
        }
        return ind;
    }
//        DF base = new DF(wd + "Sinistre_Historique_ICICDDP19_677_20221006.txt",'|',"UTF-8");
//
//        grille = new DF(wd + "Grille SS sinistre BI.xlsx","C711");
//
//        startTime = System.nanoTime();
//        grille.dna();
//
//        grille.filter_in(0,"icicddp19");
//
////        System.out.println(grille.cc("Signe Montant_Frais_Annexe").getClass().getName());
////        System.out.println(grille.cc("Date_Clôture borne basse").getClass().getName());
////        System.out.println(grille.cc(70).getClass().getName());
//
////        boolean[] keep = new boolean[base.nrow];
////        Arrays.fill(keep,false);
////        for (int i = 0; i < 100; i++) {
////            keep[i] = true;
////        }
////        base.keep_rows(keep);
////        grille.printgrille();
////        base.print();
//
//        startTime = System.nanoTime();
//        boolean[] x = base.c711(grille);
//        System.out.println(((System.nanoTime() - startTime)/1e7f)/100.0);

//        System.out.println(Arrays.toString(which(x)));
////        DF.Col_types[] coltypes_s = { DF.Col_types.STR,DF.Col_types.DBL,DF.Col_types.STR};
////        DF g811 = new DF("C:/Users/ozhukov/Desktop/test3.xlsx","Лист1",coltypes_s);
//        String[] orders = { "col1", "col2", "col3"};
//        String[] arr = new String[0];
////        Set<Object> hash = new LinkedHashSet<>(Arrays.asList(Optional.of(arr).orElse(new String[0])));
////        System.out.println(hash);
//        System.out.println("hello");
////         g811.printgrille();
//        String[] basic_cols = new String[]{"Statut_Technique_Sinistre", "SKU", "Type_Indemnisation", "Statut_Technique_Sinistre_2", "Libellé_Garantie",
//                                           "Critère_Identification_Bien_Garanti_2", "Critère_Identification_Bien_Garanti_6", "Critère_Tarifaire_1", "Statut_Sogedep"};
//        String[] calc_cols = new String[] {"Signe Montant_Indemnité_Principale","Pourcentage Montant_Indemnité_Principale","Valeur Montant_Indemnité_Principale"};
//        Net x = new Net(g811, coltypes_G, calc_cols);
//
//        System.out.println(Arrays.toString(g811.header));
//        Object[] tmp = arr_merge(g811.header,arr_concat(basic_cols,calc_cols));
//        String[] order =  Arrays.copyOf(tmp, tmp.length, String[].class);
////        Node tree = new Node(grille, order);
//
////        String name = "ICIMM101";
////        System.out.println(c811(base,g811,name));
//////        System.out.println(Arrays.toString(x.child_arr));
//////        System.out.println(Arrays.toString(cut(c811.c(0),find_in_arr(c811.c("Numéro_Police"), "ICICDDP19"))));
//
////        Special_columns_c811 x = Special_columns_c811.get("Valeur_Achat Borne haute");
//        startTime = System.nanoTime();
//        Node x = new Node(g811, order);
//        System.out.println("size " + Node.sizes);


//        System.out.println(x==Special_columns_c811.DEFAULT);
//        String[] col = c811.c(0);
//        Node tree = new Node("root");
//        String[] vec = wunique(c811.c(1));
//        tree.getchilds(vec);
//        for (int i = 1; i < c811.ncol; i++) {
//            Node
//            vec = wunique(c811.c(i));
//            for (int j = 0; j < tree.child_arr.length; j++) {
//                tree.child_arr[j].getchilds(wunique(cut(c811.c(i), find_in_arr(c811.c(i),tree.child_arr[j].value))));
//            }
//
//
//        }

//        startTime = System.nanoTime();
//        c811.keep_rows(find_in_arr(c811.c("Numéro_Police"), "ICICDDP19"));
//        c811.keep_cols(c811.dna());
//        c811.print();
//        Node root = new Node();

//        c811.printgrille();
//        System.out.println(Arrays.toString(which(find_in_arr(c811.c("Numéro_Police"), "ICIMM101"))));
//        System.out.println(base.get_rows());
//        final String[][] c811 = grille.df;
//        DF df = new DF("C:/Users/ozhukov/Desktop/s.csv",';',"UTF-8");

//        DF df = new DF("C:/Users/ozhukov/Desktop/test.xlsx",0);
//        DF tab = new DF(df, new boolean[]{false, true, false, true});

//        DF df = new DF("C:/Users/ozhukov/Desktop/Sinistre_Historique_ICICDDP19.xlsx","Sinistre_Historique_ICICDDP19_6");
//        DF df = new DF("C:/Users/ozhukov/Desktop/test.xlsx","Лист1");
//        System.out.println(Arrays.toString(f(base.c(2),"1434249",false)));

    public static String[] filter_array_by(String[] arr, String by) {
        return filter_array_by(arr,by,false);
    }
    public static String[] filter_array_by(String[] arr, String by, boolean filter_out) {
        int j = 0; int i = 0;
        for (; i<arr.length; i++){
            if(Objects.equals(arr[i], by) ^ filter_out){
                arr[j] = arr[i];
                j++;
            }
        }
        if (j==i) {return arr;}
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
    public static int[] which (boolean[] bool) {
        int sum = sum_boolean(bool);
        if (sum == 0) {
            return null;
        }
        int[] vec = new int[sum];
        int j = 0;
        for (int i = 0; i < bool.length; i++) {
            if(bool[i]) {
                vec[j] = i;
                j++;
            }
        }
        return vec;
    }
    public static Object whichf (boolean[] bool) {
        for (int i = 0; i < bool.length; i++) {
            if(bool[i]) {
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
            if(arr[i].equals(value)){
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
    public static Integer[] unique_of(Integer[] arr) {
        if (arr.length == 1) return arr;
        Set<Integer> hash = new LinkedHashSet<>(Arrays.asList(Optional.of(arr).orElse(new Integer[0]))); //ofNullable bilo ranshe hz
        return hash.toArray(new Integer[0]);
    }
    public static int[] unique_of(int[] arr) {
        if (arr.length == 1) return arr;
        HashMap<Integer,Integer> hashmap = new HashMap<Integer,Integer>();
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
        for(boolean b : vector_boolean) {
            sum += b ? 1 : 0;
        }
        return sum;
    }
    public static boolean[] b_and(boolean[] arr1, boolean[] arr2) {
        assert(arr1.length == arr2.length);
        boolean[] out = new boolean[arr1.length];
        for (int i = 0; i < arr1.length; i++) {
            out[i] = arr1[i] & arr2[i];
        }
        return out;
    }
    public static boolean[] a_and_b_and_c(boolean[] arr1, boolean[] arr2, boolean[] arr3) {
        assert(arr1.length == arr2.length);
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
            if (!in(arr[i],pushed)) {
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
    public static boolean in (Object str, Object[] arr) {
        for (Object s : arr) {
            if (s.equals(str)) {
                return true;
            }
        }
        return false;
    }
    public static boolean arr1_contains_arr2 (String[] arr1, String[] arr2){
        for (String s : arr2) {
            if (!in(s, arr1)) {
                return false;
            }
        }
        return true;
    }
    public static Integer[] sortIndices(int [] input, boolean descending){

        Integer [] indices = new Integer[input.length];

        for (int i = 0; i <input.length ; i++)
            indices[i]=i;
        if (descending) {
            Arrays.sort(indices, new Comparator<Integer>() {
                @Override
                public int compare(Integer o1, Integer o2) {
                    return input[o2]-input[o1];
                }
            });
        } else {
            Arrays.sort(indices, new Comparator<Integer>() {
                @Override
                public int compare(Integer o1, Integer o2) {
                    return input[o1]-input[o2];
                }
            });
        }

        return indices;
    }
    public static Integer[] sort_by_2_vars(int[] arr1, int[] arr2, int interval, int total) {
        int[] var1 = Arrays.copyOf(arr1, arr1.length);
        int[] var2 = Arrays.copyOf(arr2, arr2.length);
        System.out.println(Arrays.toString(var1));
        System.out.println(Arrays.toString(var2));
        Integer[] first_sort = sortIndices(var1, false);
        Integer[] second_sort = new Integer[0];
        var2 = shuffle(var2,first_sort);
        Arrays.sort(var1);

        int len = var1.length;
        int interval_counter = 1;
        int debut = 0;
        int fin;
        for (int i = 0; i < len; i++) {
            if (i == len-1) {
                fin = i+1;
                int[] var2_tranche = Arrays.copyOfRange(var2, debut, fin);
                second_sort = arr_concat(second_sort, sortIndices(var2_tranche, false));
                break;
            }
            if (var1[i]*100.0/total > interval*interval_counter) {
                fin = i-1;
                int[] var2_tranche = Arrays.copyOfRange(var2, debut, fin);
                second_sort = arr_concat(second_sort, sortIndices(var2_tranche, false));
                debut = fin+1;
                interval_counter++;
            }

        }
        for (int i = 0; i < len; i++) {
            second_sort[i] = first_sort[second_sort[i]];
        }
        System.out.println(Arrays.toString(second_sort));
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
    public static LocalDate to_Date (Date input) {
        return input.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
    public static void write_csv(Integer[] arr) {
        BufferedWriter br = null;
        try {
            br = new BufferedWriter(new FileWriter(wd+"tester.csv"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        StringBuilder sb = new StringBuilder();

// Append strings from array
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
    public static void grilles_collect(String path) throws IOException {
        path = wd+path;
        InputStream is = Files.newInputStream(new File(path).toPath());
        Workbook workbook = StreamingReader.builder().rowCacheSize(1).bufferSize(4096).open(is);
        List<String> sheetNames = new ArrayList<>();
        for (int i=0; i<workbook.getNumberOfSheets(); i++) {
            String name = workbook.getSheetName(i);
            if (name.charAt(0) == 'C') {
                if (name.charAt(1) == 'S') {
                    sheetNames.add(name.replace("S",""));
                } else {
                    sheetNames.add(name);
                }
            }
        }

        for (String s : sheetNames) {
            System.out.println(s);
            CSVWriter writer = (CSVWriter) new CSVWriterBuilder(new FileWriter(path_grilles+s+".csv"))
                    .withSeparator('\t')
                    .build();
            DF grille = new DF(path,s,true,true);

            grille.dna();

            writer.writeNext(grille.header);
            for (int i = 0; i < grille.nrow; i++) {
                String[] vec = new String[grille.ncol];
                for (int j = 0; j < grille.ncol; j++) {
                    vec[j] = grille.r(i)[j].toString();
                }
                writer.writeNext(vec);
            }
            writer.close();
        }
    }
    public static void grilles_import() throws IOException {
        File f = new File(path_grilles);
        String[] grilles = f.list();
        if (grilles == null) {
            System.out.println("grilles empty!");
            return;
        }
        for (String g : grilles) {
            String name = g.substring(0,g.indexOf('.'));
            DF df = new DF(path_grilles+g,'\t',"UTF-8",true);
            df.dna();
            grilles_G.put(name,df);
        }
    }
    public static void err(String msg) {
        System.out.println(msg);
        System.out.println(Police_en_cours);
        System.out.println(Controle_en_cours);
    }
    public static boolean[] logvec(int dim, boolean values) {
        boolean[] out = new boolean[dim];
        Arrays.fill(out,values);
        return out;
    }
}