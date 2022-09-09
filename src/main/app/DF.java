package main.app;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class DF {
    private char delim;
    private String path;
    String[][] df;
    public DF (char delim, String path) {
        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true, delim);
        settings.trimValues(true);
        try(Reader inputReader = new InputStreamReader(Files.newInputStream(
                new File(path).toPath()), StandardCharsets.UTF_8)){
            CsvParser parser = new CsvParser(settings);
            List<String[]> parsedRows = parser.parseAll(inputReader);
            df = new String[parsedRows.size()][parsedRows.get(0).length];

            int i = 0;
            for (String[] parsedRow : parsedRows) {
                int j = 0;
                for (String s : parsedRow) {
                    df[i][j] = s;
                    j++;
                }
                i++;
            }
        } catch (IOException e) {
            // handle exception
        }
    }
    public DF (char delim, String path, String encoding) {
        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true, delim);
        settings.trimValues(true);
        System.out.println(encoding.toString());
        try(Reader inputReader = new InputStreamReader(Files.newInputStream(
                new File(path).toPath()), encoding)){
            CsvParser parser = new CsvParser(settings);
            List<String[]> parsedRows = parser.parseAll(inputReader);
            df = new String[parsedRows.size()][parsedRows.get(0).length];

            int i = 0;
            for (String[] parsedRow : parsedRows) {
                int j = 0;
                for (String s : parsedRow) {
                    df[i][j] = s;
                    j++;
                }
                i++;
            }
        } catch (IOException e) {
            // handle exception
        }
    }
//    public DF (char delim, String path, StandardCharsets encod = StandardCharsets.UTF_8)
}
