package main.app;

import com.monitorjbl.xlsx.StreamingReader;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import java.io.*;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
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

import static java.lang.Math.addExact;
import static java.lang.Math.round;
import static main.app.App.find_in_arr_first_index;
import static main.app.App.keep_from_array;

public class DF {
    private char delim;
    private String path;
    public ArrayList<Object[]> df;
    public Col_types[] coltypes;
    public String[] header;
    public int ncol;
    public int nrow;
    public static SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy");

    // CONSTRUCTORS
    public DF (String path, char delim, String encoding, Col_types[] col_types) {
        if (encoding.equals("default")) encoding = "UTF-8";
        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true, delim);
        settings.trimValues(true);
        try(Reader inputReader = new InputStreamReader(Files.newInputStream(
            new File(path).toPath()), encoding)){
            CsvParser parser = new CsvParser(settings);
            List<String[]> parsedRows = parser.parseAll(inputReader);
            Iterator<String[]> rows = parsedRows.iterator();
            coltypes = col_types;
            header = header_refactor(rows.next());
            nrow = parsedRows.size()-1;
            assert (coltypes.length == parsedRows.get(0).length);
            ncol = get_len(coltypes);
            df = new ArrayList<>(get_len(coltypes));
            df = df_populate(df,coltypes);

            int i = 0;
            while(rows.hasNext()) {
                int j = 0;
                String[] parsedRow = rows.next();
                for (String s : parsedRow) {
                    if (coltypes[j] != Col_types.SKP) {
                    df.get(j)[i] = get_cell_of_type(s,coltypes[j]);
                    j++;
                    }
                }
                i++;
            }
        } catch (IOException ignored) {
        }
    }
    public DF (String path, Object sheet_n, Col_types[] col_types) throws IOException {

        InputStream is = Files.newInputStream(new File(path).toPath());
        Workbook workbook = StreamingReader.builder()
            .rowCacheSize(1)      // number of rows to keep in memory (defaults to 10)
            .bufferSize(4096)     // buffer size to use when reading InputStream to file (defaults to 1024)
            .open(is);

        if(sheet_n.getClass().getName().equals("java.lang.Integer")) {
            sheet_n = workbook.getSheetName((int) sheet_n);
        }
        Sheet sheet = workbook.getSheet((String) sheet_n);

        Iterator<Row> rowIter = sheet.rowIterator();
        Row row = rowIter.next();
        coltypes = col_types;
        ncol = get_len(coltypes);
        nrow = sheet.getLastRowNum();
        header = new String[row.getLastCellNum()];
        int i = 0;
        for (Cell c : row) {
            header[i] = c.getStringCellValue();
            i++;
        }
        header = header_refactor(header);

        df = new ArrayList<>(ncol);
        for (int h = 0; h < header.length; h++) {
            df.add(new String[nrow]);
        }
        int l = 0,k = 0;
        while(rowIter.hasNext()) {
            row = rowIter.next();
            k = 0;
            for (Cell c : row) {
                if(c.getCellTypeEnum().name().equals("FORMULA")) {
                    if(c.getCachedFormulaResultTypeEnum().name().equals("ERROR")) {
                        df.get(k)[l] = c.getCellFormula();      // bad formula
                    } else {
                        df.get(k)[l] = c.getStringCellValue();  // good formula
                    }
                } else {
                    df.get(k)[l] = c.getStringCellValue();      // no formula
                }
                k++;
            }
            l++;
        }
    }

    // PRINT
    public void print() {
        this.print(10);
    }
    public void print(int rows) {
        System.out.println(Arrays.toString(header));
        for (int i = 0; i < rows; i++) {
            System.out.println(Arrays.toString(this.r(i)));
        }
    }
    public void printgrille() {
        int max = Math.min(nrow, 100);
        this.print(max);
    }

    // DATA
    public Object[] r(int index){
        Object[] row = new Object[ncol];
        for(int i=0; i<ncol; i++){
            row[i] = df.get(i)[index];
        }
        return row;
    }
    public Object[] c(Object colname){
        if (colname instanceof Integer) {
            return df.get((Integer) colname);
        }
        int index = find_in_arr_first_index(header, colname);
        return df.get(index);
    }
    public Object[] c(int index){
        return df.get(index);
    }
    public String[] header_refactor(String[] cols) {
        String[] out = new String[get_len(coltypes)];
        Col_types[] coltypes_new = new Col_types[get_len(coltypes)];
        int j = 0;
        for (int i = 0; i < coltypes.length; i++) {
            if (coltypes[i] != Col_types.SKP) {
                out[j] = cols[i];
                coltypes_new[j] = coltypes[i];
                j++;
            }
        }
        this.coltypes = coltypes_new;
        return out;
    }
    public ArrayList<Object[]> df_populate (ArrayList<Object[]> base, Col_types[] coltypes) {
        for (Col_types coltype : coltypes) {
            switch (coltype) {
                case STR:
                    base.add(new String[nrow]);
                    break;
                case DBL:
                    base.add(new Double[nrow]);
                    break;
                case DAT:
                    base.add(new Date[nrow]);
                    break;
                default:
            }
        }
        return base;
    }
    public ArrayList<Object[]> df_populate_with_crit (ArrayList<Object[]> base, Col_types[] coltypes, boolean[] vec) {
        for (int h = 0; h < coltypes.length; h++) {
            if (vec[h]) {
                switch(coltypes[h]){
                    case STR:
                        base.add(new String[nrow]);
                        break;
                    case DBL:
                        base.add(new Double[nrow]);
                        break;
                    case DAT:
                        base.add(new Date[nrow]);
                        break;
                    default:
                }
            }
        }
        return base;
    }
    public Object get_cell_of_type (String cell, Col_types type) {
        Object out = null;
        switch(type){
            case STR:
                out = cell;
                break;
            case DBL:
                out = Double.parseDouble(cell);
                break;
            case DAT:
                try {
                    out = format.parse(cell);
                }
                catch (NullPointerException | ParseException ignored) {
                }
                break;
        }
        return out;
    }

    // FILTER
    public void keep_rows (boolean[] keep_bool) {
        if (sum_boolean(keep_bool) == 0){
            df = null;
            return;
        }
        assert (keep_bool.length == nrow);
        int nrow_old = nrow;
        nrow = sum_boolean(keep_bool);
        ArrayList<Object[]> rowsToKeep = new ArrayList<>(ncol);
        rowsToKeep = df_populate(rowsToKeep,coltypes);
        for (int i = 0; i < ncol; i++) {
            System.out.println(rowsToKeep.get(i).getClass() + " " + this.c(i).getClass());
        }
        int k = 0;
        for (int i = 0; i < nrow_old; i++) {
            if (keep_bool[i]) {
                for (int j = 0; j < ncol; j++){
                    if (j == 9 & k == 0 & i == 15602) {
                        System.out.println(rowsToKeep.get(j)[k]);
                        System.out.println(df.get(j)[i]);

                    }
                    rowsToKeep.get(j)[k] = df.get(j)[i];
                }
                k++;
            }
        }
        this.df = rowsToKeep;
    }
    public void keep_cols( boolean[] keep_vec) {
        header = keep_from_array(header,keep_vec);
        System.out.println(Arrays.toString(coltypes));
        System.out.println(Arrays.toString(keep_vec));
        coltypes = keep_from_array(coltypes,keep_vec);
        System.out.println(Arrays.toString(coltypes));

        int j = 0;
            for (int i = 0; i < ncol; i++) {
                if(!keep_vec[i]) {
                    df.remove(j);
                } else {
                    j++;
                }
            }
        ncol = sum_boolean(keep_vec);
    }
    public DF filter_out(String colname, String crit) {
        boolean[] vec = new boolean[nrow];
        Object[] col = this.c(colname);
        for (int i = 0; i < nrow; i++) {
            vec[i] = col[i].equals(crit);
        }
       DF df_new = this;
       df_new.keep_rows(vec);
       return(df_new);
    }
    public void filter_in(Object colname, String crit) {
        boolean[] vec = new boolean[nrow];
        Object[] col = this.c(colname);
        for (int i = 0; i < nrow; i++) {
            vec[i] = col[i].equals(crit);
        }

        this.keep_rows(vec);
    }

    // VECTORS
    public int sum_boolean(boolean[] vector_boolean) {
        int sum = 0;
        for(boolean b : vector_boolean) {
            sum += b ? 1 : 0;
        }
        return sum;
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
    public boolean[] is_in_arr ( String[] arr, String value) {
        final int len = arr.length;
        boolean[] out = new boolean[len];
        for (int i = 0; i < len; i++) {
            out[i] = arr[i].equals(value);
        }
        return out;
    }

    // GRILLES
    public void dna() {
        boolean[] keep = new boolean[ncol];
        Arrays.fill(keep, false);
        for (int i = 0; i < nrow; i++) {
            Object[] row = this.r(i);
            for (int j = 0; j < ncol; j++) {
                keep[j] = keep[j] | !row[j].equals("N.A.");
            }
        }
        for (int j = 0; j < ncol; j++) {
            keep[j] = keep[j] & !this.header[j].equals("Date de modif");
        }
        this.keep_cols(keep);
    }

//    // CONTROLES
//    public int c811() {
//        long startTime = System.nanoTime();
//
//        boolean[] vec = new boolean[nrow];
//        int result = 0;
//
//
//
//
//        System.out.println(round((System.nanoTime() - startTime)/1e7f)/100.0);
//        return result;
//    }
    // COLTYPES
    public enum Col_types {
        STR,
        DAT,
        DBL,
        SKP
    }
    public int get_len(DF.Col_types[] ct) {
        int j = 0;
        for (DF.Col_types c : ct) {
            if (c != DF.Col_types.SKP) {
                j++;
            }
        }
        return j;
    }

}
