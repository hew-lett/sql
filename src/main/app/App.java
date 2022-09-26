package main.app;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.lang.Math.round;
import static java.util.Arrays.fill;

public class App {
    public static void main(String[] args) throws IOException {
        System.out.println("hello");
        long startTime = System.nanoTime();
//        DF base = new DF("C:/Users/ozhukov/Desktop/Sinistre_Historique_ICICDDV15-1_785_20220806.txt",'|',"UTF-8");
//        final String[][] table_sin = base.df;
//        DF grille = new DF("C:/Users/ozhukov/Desktop/Grille Semi-spécifique sinistre 2022_09_13.xlsx","C811");
//        final String[][] c811 = grille.df;
//        DF df = new DF("C:/Users/ozhukov/Desktop/s.csv",';',"UTF-8");
        DF df = new DF("C:/Users/ozhukov/Desktop/test.xlsx",0);
        String[][] tab = df.removeRows(new boolean[]{false, true, false, true});
//        DF df = new DF("C:/Users/ozhukov/Desktop/Sinistre_Historique_ICICDDP19.xlsx","Sinistre_Historique_ICICDDP19_6");
//        DF df = new DF("C:/Users/ozhukov/Desktop/test.xlsx","Лист1");
        double elapsedTime = round((System.nanoTime() - startTime)/1e7f)/100.0;
//        System.out.println(Arrays.toString(f(base.c(2),"1434249",false)));
//        ;;
////        "Numéro_Dossier"
        System.out.println(Arrays.toString(df.c(2)));
        System.out.println(Arrays.toString(tab.c(2)));
//        System.out.println(base.df[2][1]);
//        System.out.println(Objects.equals(base.df[2][1],"Cdiscsount"));
        System.out.println(elapsedTime);
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
    public int sum_boolean(boolean[] vector_boolean) {
        int sum = 0;
        for(boolean b : vector_boolean) {
            sum += b ? 1 : 0;
        }
        return sum;
    }

}