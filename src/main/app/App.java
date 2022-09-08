package main.app;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import java.io.*;
import java.util.List;

public class App {
    public static void main(String[] args) {
        System.out.println("hello");
        try (Reader inputReader = new InputStreamReader(new FileInputStream(
                new File("C:\\Users\\jukov\\IdeaProjects\\maven\\src\\main\\resources\\SPB_Registro reclami 2022 .csv")), "UTF-8")) {
            CsvParser parser = new CsvParser(new CsvParserSettings());
            List<String[]> parsedRows = parser.parseAll(inputReader);
            for (String[] parsedRow : parsedRows) {
                for (String s : parsedRow) {
                    System.out.print(s);
                }
                System.out.println("\n");
            }


        } catch (IOException e) {
            // handle exception
        }
    }
}
