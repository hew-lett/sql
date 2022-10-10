package main.app;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.w3c.dom.ls.LSOutput;

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
                long startTime = System.nanoTime();

////        DF base = new DF("C:/Users/ozhukov/Desktop/Sinistre_Historique_ICICDDP19_677_20220806.txt",'|',"UTF-8");
//        DF base = new DF("C:/Users/ozhukov/Desktop/Sinistre_Historique_ICICDDP19_677_20220806.txt",'|',"UTF-8");
////        final String[][] table_sin = base.df;
        DF.Col_types[] columnTypes = {
                DF.Col_types.STR,     // 0     Numéro_Police
                DF.Col_types.STR,     // 1     Libellé_Distributeur
                DF.Col_types.STR,    // 2     Numéro_Dossier
                DF.Col_types.STR,     // 3     Libellé_Garantie
                DF.Col_types.STR,    // 4     Numéro_Adhésion
                DF.Col_types.STR,       // 5     Numéro_Extension
                DF.Col_types.DAT, // 6     Date_Souscription_Adhésion
                DF.Col_types.DAT, // 7     Date_Achat_Bien_Garanti
                DF.Col_types.STR,     // 8     Critère_Identification_Bien_Garanti_1
                DF.Col_types.STR,     // 9     Critère_Identification_Bien_Garanti_2
                DF.Col_types.STR,     // 10    Critère_Identification_Bien_Garanti_3
                DF.Col_types.STR,     // 11    Critère_Identification_Bien_Garanti_4
                DF.Col_types.STR,     // 12    Critère_Identification_Bien_Garanti_5
                DF.Col_types.STR,     // 13    Critère_Identification_Bien_Garanti_6
                DF.Col_types.STR,     // 14    Critère_Tarifaire_1
                DF.Col_types.STR,     // 15    Critère_Tarifaire_2
                DF.Col_types.STR,       // 16    Critère_Tarifaire_3
                DF.Col_types.STR,     // 17    Critère_Tarifaire_4
                DF.Col_types.STR,     // 18    Canal_Adhésion
                DF.Col_types.STR,       // 19    Nom_Magasin
                DF.Col_types.DAT,       // 20    Date_Activation
                DF.Col_types.DAT, // 21    Date_Déclaration
                DF.Col_types.DAT, // 22    Date_Survenance
                DF.Col_types.DAT,       // 23    Date_Evénement
                DF.Col_types.STR,     // 24    Statut_Technique_Sinistre
                DF.Col_types.STR,     // 25    Statut_Sogedep
                DF.Col_types.STR,     // 26    Type_Indemnisation
                DF.Col_types.STR,       // 27    Critère_Identification_Bien_Remplacement_1
                DF.Col_types.STR,       // 28    Critère_Identification_Bien_Remplacement_2
                DF.Col_types.STR,     // 29    Motif_Refus
                DF.Col_types.DBL,     // 30    Montant_Indemnité_Principale
                DF.Col_types.DBL,     // 31    Montant_Frais_Annexe
                DF.Col_types.DBL,     // 32    Montant_Reprise
                DF.Col_types.DBL,     // 33    Montant_Total_Règlement
                DF.Col_types.DBL,     // 34    Valeur_Achat
                DF.Col_types.DAT, // 35    Date_Dernier_Acte
                DF.Col_types.STR,     // 36    SKU
                DF.Col_types.STR,     // 37    Qualité_Client
                DF.Col_types.SKP,   // 38    Nom_Client
                DF.Col_types.SKP,     // 39    Prénom_Client
                DF.Col_types.SKP,     // 40    Numéro_Rue_Client
                DF.Col_types.SKP,    // 41    Code_Postal_Client
                DF.Col_types.SKP,     // 42    Ville_Client
                DF.Col_types.SKP,     // 43    Pays_Client
        };
        DF.Col_types[] coltypes_G = new DF.Col_types[84];
        Arrays.fill(coltypes_G,DF.Col_types.STR);
        coltypes_G[57] = DF.Col_types.DBL;
        coltypes_G[60] = DF.Col_types.DAT;
        coltypes_G[61] = DF.Col_types.DAT;
        coltypes_G[62] = DF.Col_types.DBL;
        coltypes_G[63] = DF.Col_types.DBL;
        coltypes_G[71] = DF.Col_types.DBL;
        coltypes_G[81] = DF.Col_types.DBL;
        coltypes_G[82] = DF.Col_types.DBL;

//        DF base = new DF("C:/Users/ozhukov/Desktop/Sinistre_Historique_ICICDDP19_677_20220806.txt",'|',"UTF-8",columnTypes);
////        boolean[] which = filtre(c811, "Numéro_Police", "ICICDDV15-1");
////        DF grille = new DF(c811, which, startTime);
////        base.print();
//        startTime = System.nanoTime();
         DF g811 = new DF("C:/Users/ozhukov/Desktop/Grille Semi-spécifique sinistre 2022_09_13.xlsx","C811",coltypes_G);
         g811.dna();
        g811.filter_in(0,"ICICDDP19");
        System.out.println("hello");
         g811.printgrille();
        String[] basic_cols = new String[]{"Statut_Technique_Sinistre", "SKU", "Type_Indemnisation", "Statut_Technique_Sinistre_2", "Libellé_Garantie",
                                           "Critère_Identification_Bien_Garanti_2", "Critère_Identification_Bien_Garanti_6", "Critère_Tarifaire_1", "Statut_Sogedep"};
        String[] calc_cols = new String[] {"Signe Montant_Indemnité_Principale","Pourcentage Montant_Indemnité_Principale","Valeur Montant_Indemnité_Principale"};
        System.out.println(Arrays.toString(g811.header));
        System.out.println(Arrays.toString(arr_concat(basic_cols, calc_cols)));
        Object[] tmp = arr_merge(g811.header,arr_concat(basic_cols,calc_cols));
        String[] order =  Arrays.copyOf(tmp, tmp.length, String[].class);
//        Node tree = new Node(grille, order);

//        String name = "ICIMM101";
//        System.out.println(c811(base,g811,name));
////        System.out.println(Arrays.toString(x.child_arr));
////        System.out.println(Arrays.toString(cut(c811.c(0),find_in_arr(c811.c("Numéro_Police"), "ICICDDP19"))));

//        Special_columns_c811 x = Special_columns_c811.get("Valeur_Achat Borne haute");
        startTime = System.nanoTime();
        Node x = new Node(g811, order);
        System.out.println(((System.nanoTime() - startTime)/1e7f)/100.0);


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

    }

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
        for (int i = 0; i < len; i++) {
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
        return null;
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
        Set<Object> hash = new LinkedHashSet<>(Arrays.asList(Optional.ofNullable(arr).orElse(new String[0])));
        return hash.toArray(new Object[0]);
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


//    public static int c811(DF base, DF grille, String name) throws IOException {
//        boolean[] vec = new boolean[base.nrow];
//        grille.keep_rows(find_in_arr(grille.c("Numéro_Police"), name));
//        grille.keep_cols(grille.dna());
//          assert(arr1_contains_arr2(grille.header,basic_cols));
////        assert(arr1_contains_arr2(grille.header,calc_cols));
//        String[] order = arr_merge(basic_cols,grille.header);
//        Node tree = new Node(grille, order);
////        for (String[] row : base.df) {
////            for (int i = 0; i < basic_cols.length; i++) {
////                String value = row[find_in_arr_first(base.header, basic_cols[i])];
//////                vec[i] = tree.find(row,base.header);
//////                Node tmp = basic_grille(value,tree);
////            }
////        }
//
//        return sum_boolean(vec);
//    }
//    //    public static Node basic_grille(String value, Node tree) {
////        boolean[] vec;
////        if (Objects.equals(value, "")) {
////            vec = find_in_arr3(tree.childs, value, "N.A.","{ vide }");
////        } else {
////            vec = find_in_arr3(tree.childs, value, "N.A.","{ renseigné }");
////        }
////        tree.vec = vec;
////        tree.childs = keep_from_array(tree.childs, vec);
////        return tree;
////    }
////    public static Node basic_grille_vr(String value, Node tree) {
////        boolean[] vec;
////        if (Objects.equals(value, "")) {
////            vec = find_in_arr3(tree.childs, value, "N.A.","{ vide }");
////        } else {
////            vec = find_in_arr3(tree.childs, value, "N.A.","{ renseigné }");
////        }
////        tree.keep_from_node(vec);
////        return tree;
////    }
//
//
//

}