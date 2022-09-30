package main.app;

import com.monitorjbl.xlsx.StreamingReader;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;


import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import javax.naming.PartialResultException;
import javax.swing.*;

import static java.lang.Math.round;

public class DF {
    private char delim;
    private String path;
    public String[][] df;
    public String[] header;
    public int ncol;
    public int nrow;

    // CONSTRUCTORS
    public DF (String path, char delim, String encoding) {
        if (encoding.equals("default")) encoding = "UTF-8";
        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true, delim);
        settings.trimValues(true);
        try(Reader inputReader = new InputStreamReader(Files.newInputStream(
                new File(path).toPath()), encoding)){
            CsvParser parser = new CsvParser(settings);
            List<String[]> parsedRows = parser.parseAll(inputReader);
            Iterator<String[]> rows = parsedRows.iterator();
            header = rows.next();
            nrow = parsedRows.size()-1;
            ncol = parsedRows.get(0).length;
            df = new String[nrow][ncol];
            int i = 0;
            while(rows.hasNext()) {
                int j = 0;
                String[] parsedRow = rows.next();
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
    public DF (String path, Object sheet_n) throws IOException {

        InputStream is = Files.newInputStream(new File(path).toPath());
        Workbook workbook = StreamingReader.builder()
            .rowCacheSize(1)      // number of rows to keep in memory (defaults to 10)
            .bufferSize(4096)     // buffer size to use when reading InputStream to file (defaults to 1024)
            .open(is);

        if(sheet_n.getClass().getName().equals("java.lang.Integer")) {
            sheet_n = workbook.getSheetName((int) sheet_n);
        }
        Sheet sheet = workbook.getSheet((String) sheet_n);

        Iterator<Row> rowIter = sheet.rowIterator();    // make row iterator
        Row row = rowIter.next();                       // get first row
        ncol = row.getLastCellNum();                    // get ncol
        nrow = sheet.getLastRowNum();                    // get nrow
        header = new String[row.getLastCellNum()];      // init header array
        int col_count = 0;                              // from 0
        for (Cell c : row) {                            // iterate
            header[col_count] = c.getStringCellValue(); // fill header
            col_count++;                                // count columns
        }
        df = new String[nrow][ncol];                    // -1 header

        int l = 0,k = 0;
        while(rowIter.hasNext()) {
            row = rowIter.next();
            k = 0;
            for (Cell c : row) {
                if(c.getCellTypeEnum().name().equals("FORMULA")) {
                    if(c.getCachedFormulaResultTypeEnum().name().equals("ERROR")) {
                        df[l][k] = c.getCellFormula();      // bad formula
                    } else {
                        df[l][k] = c.getStringCellValue();  // good formula
                    }
                } else {
                    df[l][k] = c.getStringCellValue();      // no formula
                }
                k++;
            }
            l++;
        }
    }
    public DF (DF df_old, boolean[] keep_bool, double startTime) {
        header = df_old.header;
        nrow = this.sum_boolean(keep_bool);
        ncol = df_old.df[0].length;

        String[][] rowsToKeep = new String[nrow][ncol];

        int j = 0;
        for (int i = 0; i < df_old.nrow; i++) {
            if (keep_bool[i]) {
                rowsToKeep[j] = df_old.r(i);
                j++;
            }
        }

        this.df = rowsToKeep;
    }

    // DATA
    public String[] r(int index){
        return df[index];
    }
    public String[] c(int index){

        String[] column = new String[df.length];
        for(int i=0; i<column.length; i++){
            column[i] = df[i][index];
        }
        return column;
    }
    public String[] c(String name){
        int width = df[0].length;
        int index = -1;
        for (int i=0; i<width; i++) {
            if (Objects.equals(header[i], name)){ index = i;}
        }
        String[] column = new String[df.length];
        for(int i=0; i<column.length; i++){
            column[i] = df[i][index];
        }
        return column;
    }
    public void keep_cols( boolean[] keep) {
        String[][] df_new = new String[nrow][sum_boolean(keep)];
        String[] header_new = new String[sum_boolean(keep)];
        for (int i = 0; i < nrow; i++) {
            String[] row = df[i];
            int k = 0;
            for (int j = 0; j < ncol; j++) {
                if(keep[j]) {
                    df_new[i][k] = df[i][j];
                    k++;
                }
            }
        }
        int k = 0;
        for (int j = 0; j < ncol; j++) {
            if(keep[j]) {
                header_new[k] = header[j];
                k++;
            }
        }
        df = df_new;
        header = header_new;
        ncol = df[0].length;
    }
    public void keep_rows( boolean[] keep) {
        int sum = sum_boolean(keep);
        if (sum == 0){
            df = null;
            return;
        }
        String[][] df_new = new String[sum][ncol];
        int[] w = which(keep);
        int j = 0;
        for (int i : w) {
            df_new[j] = df[i];
            j++;
        }
        df = df_new;
        nrow = sum;
    }
    // PRINT
    public void print() {
        int i = 0;
        for (String[] row : df) {
            if (i<10) System.out.println(Arrays.toString(row));
            i++;
        }
    }
    public void print(int rows) {
        int i = 0;
        for (String[] row : df) {
            if (i<rows) System.out.println(Arrays.toString(row));
            i++;
        }
    }
    public void printgrille() {
        int max = Math.min(nrow, 100);
        boolean[] keep;
        keep = this.dna();
        for (int j = 0; j < ncol; j++) {
            if (keep[j]) {
                System.out.print(header[j] + "  | ");
            }
        }
        for (int i = 0; i < max; i++) {
            String[] row = df[i];
            for (int j = 0; j < ncol; j++) {
                if (keep[j]) {
                    System.out.print(row[j] + " | ");
                }
            }
            System.out.println("\n");
        }
    }

    // VECTORS
    public int sum_boolean(boolean[] vector_boolean) {
        int sum = 0;
        for(boolean b : vector_boolean) {
            sum += b ? 1 : 0;
        }
        return sum;
    }

    // GRILLES
    public boolean[] dna() {
        boolean[] keep = new boolean[ncol];
        Arrays.fill(keep, false);
        for (int i = 0; i < nrow; i++) {
            String[] row = df[i];
            for (int j = 0; j < ncol; j++) {
                keep[j] = keep[j] | !row[j].equals("N.A.");
            }
        }
        return keep;
    }

    // CONTROLES
    public int c811() {
        long startTime = System.nanoTime();

        boolean[] vec = new boolean[nrow];
        int result = 0;




        System.out.println(round((System.nanoTime() - startTime)/1e7f)/100.0);
        return result;
    }
    public int[] which (boolean[] bool) {
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
    public boolean[] find_in_arr ( String[] arr, String value) {
        final int len = arr.length;
        boolean[] out = new boolean[len];
        for (int i = 0; i < len; i++) {
            out[i] = arr[i].equals(value);
        }
        return out;
    }
}
