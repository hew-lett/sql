package main.app;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static java.lang.Math.round;
import static java.util.Arrays.fill;

public class App {
    public static void main(String[] args) throws IOException {
        System.out.println("hello");
        long startTime = System.nanoTime();
//        DF df = new DF("C:/Users/ozhukov/Desktop/b.csv",';',"UTF-8");
        DF df = new DF("C:/Users/ozhukov/Desktop/test.xlsx",0);

//        DF df = new DF("C:/Users/ozhukov/Desktop/Grille Semi-spécifique sinistre 2022_09_13.xlsx","C811");
//        DF df = new DF("C:/Users/ozhukov/Desktop/Sinistre_Historique_ICICDDP19.xlsx","Sinistre_Historique_ICICDDP19_6");
//        DF df = new DF("C:/Users/ozhukov/Desktop/test.xlsx","Лист1");
        String[][] base = df.df;
//        System.out.println(Arrays.toString(base[1]));
        double elapsedTime = round((System.nanoTime() - startTime)/1e7f)/100.0;
//        System.out.println(Arrays.toString(df.header.toArray()));
//        System.out.println(Arrays.toString(df.c(1)));
        System.out.println(Arrays.toString(df.r(1)));
        System.out.println(Arrays.toString(df.c(1)));
        System.out.println(Arrays.toString(df.header));
//        System.out.println(Arrays.toString(base[1]));

        System.out.println(elapsedTime);
    }


}