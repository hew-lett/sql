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
    public static void main(String[] args) {
        System.out.println("hello");
        long startTime = System.nanoTime();
        String[][] df = new DF(';',"C:/Users/ozhukov/Desktop/b.csv").df;
        System.out.println(df[1][1]);
        double elapsedTime = round((System.nanoTime() - startTime)/1e7f)/100.0;
        System.out.println(elapsedTime);
    }
}