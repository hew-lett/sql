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
//        System.out.println("hello");
////        DF base = new DF("C:/Users/ozhukov/Desktop/Sinistre_Historique_ICICDDP19_677_20220806.txt",'|',"UTF-8");
//        DF base = new DF("C:/Users/ozhukov/Desktop/Sinistre_Historique_ICICDDP19_677_20220806.txt",'|',"UTF-8");
////        final String[][] table_sin = base.df;
////        DF c811 = new DF("C:/Users/ozhukov/Desktop/test3.xlsx",0);
////        boolean[] which = filtre(c811, "Numéro_Police", "ICICDDV15-1");
////        DF grille = new DF(c811, which, startTime);
////        base.print();
//        DF g811 = new DF("C:/Users/ozhukov/Desktop/Grille Semi-spécifique sinistre 2022_09_13.xlsx","C811");
//
//        long startTime = System.nanoTime();
//
//        String name = "ICIMM101";
//        System.out.println(c811(base,g811,name));
////        Node x = new Node(c811, order);
////        System.out.println(Arrays.toString(x.child_arr));
////        System.out.println(Arrays.toString(cut(c811.c(0),find_in_arr(c811.c("Numéro_Police"), "ICICDDP19"))));
//        System.out.println(((System.nanoTime() - startTime)/1e7f)/100.0);

        Special_columns_c811 x = Special_columns_c811.get("Valeur_Achat Borne haute");

        System.out.println(x==Special_columns_c811.DEFAULT);
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
//        System.out.println(round((System.nanoTime() - startTime)/1e7f)/100.0);
    }

    public static String[] filter_array(String[] arr, String by) {
        return filter_array(arr,by,false);
    }
    public static String[] filter_array(String[] arr, String by, boolean filter_out) {
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
    public static String[] keep_from_array(String[] arr, boolean[] which) {
        int len = sum_boolean(which);
        String[] out = new String[len];
        int j = 0;
        for (int i = 0; i < len; i++) {
            if (which[i]) {
                out[j] = arr[i];
                j++;
            }
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
    public static int find_in_arr_first(String[] arr, String value) {
        final int len = arr.length;
        int out = -1;
        for (int i = 0; i < len; i++) {
            if(arr[i].equals(value)){
                out = i;
            };
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
    public static int c811(DF base, DF grille, String name) throws IOException {
        boolean[] vec = new boolean[base.nrow];
        grille.keep_rows(find_in_arr(grille.c("Numéro_Police"), name));
        grille.keep_cols(grille.dna());
        String[] basic_cols = new String[]{"Statut_Technique_Sinistre", "SKU", "Type_Indemnisation", "Statut_Technique_Sinistre_2", "Libellé_Garantie", "Critère_Identification_Bien_Garanti_2", "Critère_Identification_Bien_Garanti_6",
                "Critère_Tarifaire_1", "Statut_Sogedep"};
        String[] calc_cols = new String[] {"Signe Montant_Indemnité_Principale","Pourcentage Montant_Indemnité_Principale","Valeur Montant_Indemnité_Principale"};
//        assert(arr1_contains_arr2(grille.header,basic_cols));
//        assert(arr1_contains_arr2(grille.header,calc_cols));
        String[] order = arr_merge(basic_cols,grille.header);
        Node tree = new Node(grille, order);
        for (String[] row : base.df) {
            for (int i = 0; i < basic_cols.length; i++) {
                String value = row[find_in_arr_first(base.header, basic_cols[i])];
//                vec[i] = tree.find(row,base.header);
//                Node tmp = basic_grille(value,tree);
            }
        }

        return sum_boolean(vec);
    }
    //    public static Node basic_grille(String value, Node tree) {
//        boolean[] vec;
//        if (Objects.equals(value, "")) {
//            vec = find_in_arr3(tree.childs, value, "N.A.","{ vide }");
//        } else {
//            vec = find_in_arr3(tree.childs, value, "N.A.","{ renseigné }");
//        }
//        tree.vec = vec;
//        tree.childs = keep_from_array(tree.childs, vec);
//        return tree;
//    }
//    public static Node basic_grille_vr(String value, Node tree) {
//        boolean[] vec;
//        if (Objects.equals(value, "")) {
//            vec = find_in_arr3(tree.childs, value, "N.A.","{ vide }");
//        } else {
//            vec = find_in_arr3(tree.childs, value, "N.A.","{ renseigné }");
//        }
//        tree.keep_from_node(vec);
//        return tree;
//    }
    public static String[] arr_merge(String[] arr1, String[] arr2) {
        int fal = arr1.length;
        int sal = arr2.length;
        String[] result = new String[fal + sal];
        System.arraycopy(arr1, 0, result, 0, fal);
        System.arraycopy(arr2, 0, result, fal, sal);
        return result;
    }
    public static boolean arr1_contains_arr2 (String[] arr1, String[] arr2){
        for (String s : arr2) {
            if (!in(s, arr1)) {
                return false;
            }
        }
        return true;

    }
    public static boolean in (String str, String[] arr) {
        for (String s : arr) {
            if (s.equals(str)) {
                return true;
            }
        }
        return false;
    }
    public enum Special_columns_c811 {
        VCBH("Valeur_Catalogue Borne haute"),
        VCBB("Valeur_Catalogue Borne basse"),
        VABH("Valeur_Achat Borne haute"),
        VABB("Valeur_Achat Borne basse"),
        DSBB("Date_Souscription_Adhésion borne basse"),
        DCBH("Date_Clôture borne haute"),
        DCBB("Date_Clôture borne basse"),
        SMIP("Signe Montant_Indemnité_Principale"),
        PMIP("Pourcentage Montant_Indemnité_Principale"),
        MIP("Valeur Montant_Indemnité_Principale"),
        A("Age"),
        RCC("Retraitement Code_Client"),
        RM("Référentiel Marque"),
        DEFAULT("DEFAULT");
        private final String col;

        Special_columns_c811(String col) {
            this.col = col;
        }

        public String getCol() {
            return col;
        }

        public static Special_columns_c811 get(String ref) {
            for (Special_columns_c811 s : Special_columns_c811.values()) {
                if(s.col.equals(ref)) return s;
            }
            return Special_columns_c811.DEFAULT;
        }
    }
}