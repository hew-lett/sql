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

public class DF {
    private char delim;
    private String path;
    public String[][] df;
    String[] header;
    int ncol;

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
            df = new String[parsedRows.size()-1][parsedRows.get(0).length];
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
        header = new String[row.getLastCellNum()];      // init header array
        int col_count = 0;                              // from 0
        for (Cell c : row) {                            // iterate
            header[col_count] = c.getStringCellValue(); // fill header
            col_count++;                                // count columns
        }
        df = new String[sheet.getLastRowNum()][col_count]; // -1 header

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

//    public String[][] f(String colname, String value) {
//        String[] a = this.c(colname);
//        Boolean[] b = new Boolean[a.length];
//        for (int i = 0; i < a.length; i++) {
//            b[i] = a[i].equals(value);
//        }
//        return a;
//    }
    public String[][] removeRows(final boolean[] which) {

        int nrow = this.sum_boolean(which);
        String[][] rowsToKeep = new String[nrow][ncol];

        int i = 0;
        for (String[] row : df) {
            if (which[i]) {
                rowsToKeep[i] = row;
                i++;
            }
        }

        return rowsToKeep;
    }

    public int sum_boolean(boolean[] vector_boolean) {
        int sum = 0;
        for(boolean b : vector_boolean) {
            sum += b ? 1 : 0;
        }
        return sum;
    }
    public int whichf (String[] arr, String t)
    {

        // if array is Null
        if (arr == null) {
            return -1;
        }

        // find length of array
        int len = arr.length;
        int i = 0;

        // traverse in the array
        while (i < len) {

            // if the i-th element is t
            // then return the index
            if (Objects.equals(arr[i], t)) {
                return i;
            }
            else {
                i = i + 1;
            }
        }
        return -1;
    }
//    public Object[] which(String arr, String t)
//    {
//        Vector<Integer> out = new Vector<>();
//
//        if (arr == null) {
//            return out.toArray();
//        }
//
//        String[] arr = this.df.c()
//        int len = arr.length;
//        int i = 0;
//
//        while (i < len) {
//            if (Objects.equals(arr[i], t)) {out.add(i);}
//            i++;
//        }
////        if (vectInt.size() > 0) {
//            return (out.toArray());
////        }
//    }
}
