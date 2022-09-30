package main.app;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.round;
import static java.util.Arrays.fill;

public class App {
    public static void main(String[] args) throws IOException {
        System.out.println("hello");
//        DF base = new DF("C:/Users/ozhukov/Desktop/Sinistre_Historique_ICICDDP19_677_20220806.txt",'|',"UTF-8");
//        final String[][] table_sin = base.df;
        DF c811 = new DF("C:/Users/ozhukov/Desktop/Grille Semi-spécifique sinistre 2022_09_13.xlsx","C811");
//        DF c811 = new DF("C:/Users/ozhukov/Desktop/test3.xlsx",0);
//        boolean[] which = filtre(c811, "Numéro_Police", "ICICDDV15-1");
//        DF grille = new DF(c811, which, startTime);
//        base.print();
        c811.keep_rows(find_in_arr(c811.c("Numéro_Police"), "ICICDDV19"));
        c811.keep_cols(c811.dna());
        long startTime = System.nanoTime();
        Node x = new Node(c811);
//        System.out.println(Arrays.toString(x.child_arr));
//        System.out.println(Arrays.toString(cut(c811.c(0),find_in_arr(c811.c("Numéro_Police"), "ICICDDP19"))));
        System.out.println(((System.nanoTime() - startTime)/1e7f)/100.0);

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
//        ;;
////        "Numéro_Dossier"
//        System.out.println(Arrays.toString(grille.c(0)));
//        System.out.println(Arrays.toString(tab.c(2)));
//        System.out.println(base.df[2][1]);
//        System.out.println(Objects.equals(base.df[2][1],"Cdiscsount"));
        System.out.println(round((System.nanoTime() - startTime)/1e7f)/100.0);
    }

    public static String[] f(String[] arr, String by) {
        return f(arr,by,false);
    }
    public static String[] f(String[] arr, String by, boolean filter_out) {
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
    public static int sum_boolean(boolean[] vector_boolean) {
        int sum = 0;
        for(boolean b : vector_boolean) {
            sum += b ? 1 : 0;
        }
        return sum;
    }
    public static boolean[] filtre(DF df, String column, String by) {
        boolean[] out = new boolean[df.nrow];
        String[] col = df.c(column);
        for (int i = 0; i < df.nrow; i++) {
            out[i] = col[i].equals(by);
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
        return null;
    }
    public static boolean[] find_in_arr(String[] arr, String value) {
        final int len = arr.length;
        boolean[] out = new boolean[len];
        for (int i = 0; i < len; i++) {
            out[i] = arr[i].equals(value);
        }
        return out;
    }
    public static String[] wunique(String[] arr) {
        Set<String> hash = new LinkedHashSet<>(List.of(arr));
        return hash.toArray(new String[0]);
    }
    public static boolean[] unique(String[] arr) {
        Set<String> hash = new LinkedHashSet<>();
        boolean[] out = new boolean[arr.length];
        for (int i = 0; i < arr.length; i++) {
            out[i] = hash.add(arr[i]);
        }
        return out;
    }
    public static String[] cut (String[] arr, boolean[] bool) {
        assert(arr.length == bool.length);
        String[] out = new String[sum_boolean(bool)];
        int j = 0;
        for (int i = 0; i < arr.length; i++){
            if (bool[i]) {
               out[j] = arr[i];
               j++;
            }
        }
        return out;
    }
    public static boolean[] b_and(boolean[] arr1, boolean[] arr2) {
        assert(arr1.length == arr2.length);
        boolean[] out = new boolean[arr1.length];
        for (int i = 0; i < arr1.length; i++) {
            out[i] = arr1[i] & arr2[i];
        }
        return out;
    }
}