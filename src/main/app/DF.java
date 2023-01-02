package main.app;

import com.monitorjbl.xlsx.StreamingReader;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import java.io.*;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.poi.ss.formula.functions.T;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.STSourceType;
import org.w3c.dom.ls.LSOutput;

import javax.naming.PartialResultException;
import javax.naming.ldap.Control;
import javax.sound.midi.ControllerEventListener;
import javax.swing.*;

import static java.lang.Math.*;
import static java.util.stream.IntStream.range;
import static main.app.App.*;

public class DF {
//    private char delim;
//    private String path;
    public ArrayList<Object[]> df;
//    public ArrayList<HashMap<Object,List<Integer>>> dff;

    public Col_types[] coltypes;
    public String[] header;
    public int ncol;
    public int nrow;
    public DF grille_gen;
    public static SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy");

    // CONSTRUCTORS
    public DF (String path, char delim, boolean tolower, DF mapping) {
        String filename = path.substring(path.lastIndexOf("/")+1);
        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true, delim);
        settings.trimValues(true);
        try(Reader inputReader = new InputStreamReader(Files.newInputStream(
            new File(path).toPath()), encoding)){
            CsvParser parser = new CsvParser(settings);
            List<String[]> parsedRows = parser.parseAll(inputReader);
            Iterator<String[]> rows = parsedRows.iterator();
            header = rows.next();

            if (filename.length() < 9 & filename.charAt(0) == 'C') {
                coltypes = get_col_types(header, coltypes_G);
            } else {
                this.subst_columns(mapping);
                coltypes = get_col_types(header, coltypes_B);
            }
            nrow = parsedRows.size()-1;
            assert (coltypes.length == parsedRows.get(0).length);
            ncol = get_len(coltypes);
            df = new ArrayList<>(ncol);
            this.df_populate(coltypes);

            if (tolower) {
                int i = 0;
                while(rows.hasNext()) {
                    int j = 0;
                    int k = 0;
                    String[] parsedRow = rows.next();
                    for (String s : parsedRow) {
                        if (coltypes[k] != Col_types.SKP) {
                            df.get(j)[i] = get_lowercase_cell_of_type(s,coltypes[k]);
                            j++;
                        }
                        k++;
                    }
                    i++;
                }
            } else {
                int i = 0;
                while(rows.hasNext()) {
                    int j = 0;
                    int k = 0;
                    String[] parsedRow = rows.next();
                    for (String s : parsedRow) {
                        if (coltypes[k] != Col_types.SKP) {
                            df.get(j)[i] = get_cell_of_type(s,coltypes[k]);
                            j++;
                        }
                        k++;
                    }
                    i++;
                }
            }
        } catch (IOException ignored) {
        }
        this.header_refactor();
        this.remove_leading_zeros();
    }
//    public DF (String path, char delim, boolean tolower, DF mapping) {
//        String filename = path.substring(path.lastIndexOf("/")+1);
//        CsvParserSettings settings = new CsvParserSettings();
//        settings.setDelimiterDetectionEnabled(true, delim);
//        settings.trimValues(true);
//        try(Reader inputReader = new InputStreamReader(Files.newInputStream(
//                new File(path).toPath()), encoding)){
//            CsvParser parser = new CsvParser(settings);
//            List<String[]> parsedRows = parser.parseAll(inputReader);
//            Iterator<String[]> rows = parsedRows.iterator();
//            header = rows.next();
//
//            if (filename.length() < 9 & filename.charAt(0) == 'C') {
//                coltypes = get_col_types(header, coltypes_G);
//            } else {
//                this.subst_columns(mapping);
//                coltypes = get_col_types(header, coltypes_B);
//            }
//            nrow = parsedRows.size()-1;
//            assert (coltypes.length == parsedRows.get(0).length);
//            ncol = get_len(coltypes);
////            df = new ArrayList<>(get_len(coltypes));
////            this.df_populate(coltypes);
//            this.dff = new ArrayList<>();
//            for (int i = 0; i < ncol; i++) {
//                this.dff.add(new HashMap<Object, List<Integer>>());
//            }
//
//            if (tolower) {
//                int i = 0;
//                while(rows.hasNext()) {
//                    System.out.println(i);
//                    int j = 0;
//                    int k = 0;
//                    String[] parsedRow = rows.next();
//                    for (String s : parsedRow) {
//                        if (coltypes[k] != Col_types.SKP) {
//                            switch (coltypes[k]) {
//                                case STR -> {
//                                    if (s == null) {
//                                        dff.get(j).computeIfAbsent("", k1 -> new ArrayList<>());
//                                        dff.get(j).get("").add(i);
//                                    } else {
//                                        dff.get(j).computeIfAbsent(s, k1 -> new ArrayList<>());
//                                        dff.get(j).get(s).add(i);
//                                    }
//                                }
//                                case DBL -> {
//                                    Double value;
//                                    try {
//                                        value = Double.parseDouble(s.replace(",", "."));
//                                    } catch (NullPointerException | NumberFormatException e) {
//                                        value = NA_DBL;
//                                    }
//                                    dff.get(j).computeIfAbsent(value, k1 -> new ArrayList<>());
//                                    dff.get(j).get(value).add(i);
//                                }
//                                case DAT -> {
//                                    Date value_date;
//                                    try {
//                                        value_date = format.parse(s);
//                                    } catch (NullPointerException | ParseException e) {
//                                        value_date = NA_DAT;
//                                    }
//                                    dff.get(j).computeIfAbsent(value_date, k1 -> new ArrayList<>());
//                                    dff.get(j).get(value_date).add(i);
//                                }
//                            }
//                            j++;
//                        }
//                        k++;
//                    }
//                    i++;
//                }
//            }
//        } catch (IOException ignored) {
//        }
//        this.header_refactor();
////        this.remove_leading_zeros();
//    }
    public DF (String path, char delim, boolean tolower) {
        String filename = path.substring(path.lastIndexOf("/")+1);
        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true, delim);
        settings.trimValues(true);
        try(Reader inputReader = new InputStreamReader(Files.newInputStream(
                new File(path).toPath()), encoding)){
            CsvParser parser = new CsvParser(settings);
            List<String[]> parsedRows = parser.parseAll(inputReader);
            Iterator<String[]> rows = parsedRows.iterator();
            header = rows.next();

            if (filename.length() < 9 & filename.charAt(0) == 'C') {
                coltypes = get_col_types(header, coltypes_G);
            } else {
                coltypes = get_col_types(header, coltypes_B);
            }
            nrow = parsedRows.size()-1;
            assert (coltypes.length == parsedRows.get(0).length);
            ncol = get_len(coltypes);
            df = new ArrayList<>(get_len(coltypes));
            this.df_populate(coltypes);

            if (tolower) {
                int i = 0;
                while(rows.hasNext()) {
                    int j = 0;
                    int k = 0;
                    String[] parsedRow = rows.next();
                    for (String s : parsedRow) {
                        if (coltypes[k] != Col_types.SKP) {
                            df.get(j)[i] = get_lowercase_cell_of_type(s,coltypes[k]);
                            j++;
                        }
                        k++;
                    }
                    i++;
                }
            } else {
                int i = 0;
                while(rows.hasNext()) {
                    int j = 0;
                    int k = 0;
                    String[] parsedRow = rows.next();
                    for (String s : parsedRow) {
                        if (coltypes[k] != Col_types.SKP) {
                            df.get(j)[i] = get_cell_of_type(s,coltypes[k]);
                            j++;
                        }
                        k++;
                    }
                    i++;
                }
            }
        } catch (IOException ignored) {
        }
        this.header_refactor();
        this.remove_leading_zeros();
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

        Iterator<Row> rowIter = sheet.rowIterator();
        Row row = rowIter.next();
        nrow = sheet.getLastRowNum();
        ncol = row.getLastCellNum();
        header = new String[ncol];
        int i = 0;
        for (Cell c : row) {
            header[i] = c.getStringCellValue();
            i++;
        }
        this.header_problems();

        coltypes = get_col_types(header,coltypes_G);

        df = new ArrayList<>(ncol);
        this.df_populate(coltypes);

        int col_iterator;
        int ct_iterator;
        int row_number = 0;
        while(rowIter.hasNext()) {
            row = rowIter.next();
            col_iterator = 0;
            ct_iterator = 0;
            for (Cell c : row) {
                if (coltypes[ct_iterator] != Col_types.SKP) {
                    if(c.getCellTypeEnum().name().equals("FORMULA")) {
                        if(c.getCachedFormulaResultTypeEnum().name().equals("ERROR")) {
                            df.get(col_iterator)[row_number] = get_cell_of_type(c.getCellFormula(),coltypes[ct_iterator]);      // bad formula
                        } else {
                            df.get(col_iterator)[row_number] = get_cell_of_type(c.getStringCellValue(),coltypes[ct_iterator]);  // good formula
                        }
                    } else {
                        df.get(col_iterator)[row_number] = get_cell_of_type(c.getStringCellValue(),coltypes[ct_iterator]);      // no formula
                    }
                    col_iterator++;
                }
                ct_iterator++;
            }
            row_number++;
        }

        this.header_refactor();
        this.remove_leading_zeros();
    }
    public DF (String path, Object sheet_n, boolean all_strings, boolean tolower) throws IOException {

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
        nrow = sheet.getLastRowNum();
        ncol = row.getLastCellNum();
        header = new String[ncol];
        int i = 0;
        for (Cell c : row) {
            header[i] = c.getStringCellValue();
            i++;
        }
        this.header_problems();

        if(all_strings) {
            coltypes = new Col_types[ncol];
            Arrays.fill(coltypes,Col_types.STR);
        } else {
            coltypes = get_col_types(header,coltypes_G);
        }

        df = new ArrayList<>(ncol);
        this.df_populate(coltypes);

        System.out.println("--------------------------------------------mapping");
        System.out.println(path);
        int col_iterator;
        int row_number = 0;
        while(rowIter.hasNext()) {
            row = rowIter.next();
            if (i == 73) {
                System.out.println("+++++++++++++++++++++++");
                System.out.println(row);
            }
            int cell_number = row.getLastCellNum()-1;
            col_iterator = 0;
            if (tolower) {

                for (int c = 0; c <= cell_number; c++) {
                    if (coltypes[c] != Col_types.SKP) {
                        Cell cell_i = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        if (cell_i == null) {
                            switch(coltypes[c]) {
                                case STR -> df.get(col_iterator)[row_number] = "";
                                case DBL -> df.get(col_iterator)[row_number] = NA_DBL;
                                case DAT -> df.get(col_iterator)[row_number] = NA_DAT;
                            }
                            col_iterator++;
                            continue;
                        }
                            if(cell_i.getCellTypeEnum().name().equals("FORMULA")) {
                                if(cell_i.getCachedFormulaResultTypeEnum().name().equals("ERROR")) {
                                    df.get(col_iterator)[row_number] = get_lowercase_cell_of_type(cell_i.getCellFormula(),coltypes[c]);      // bad formula
                                } else {
                                    df.get(col_iterator)[row_number] = get_lowercase_cell_of_type(cell_i.getStringCellValue(),coltypes[c]);  // good formula
                                }
                            } else {
                                df.get(col_iterator)[row_number] = get_lowercase_cell_of_type(cell_i.getStringCellValue(),coltypes[c]);      // no formula
                            }
                            col_iterator++;
                        }
                }
            } else {
                for (int c = 0; c <= cell_number; c++) {
                    if (coltypes[c] != Col_types.SKP) {
                        Cell cell_i = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        if (cell_i == null) {
                            switch(coltypes[c]) {
                                case STR -> df.get(col_iterator)[row_number] = "";
                                case DBL -> df.get(col_iterator)[row_number] = NA_DBL;
                                case DAT -> df.get(col_iterator)[row_number] = NA_DAT;
                            }
                            col_iterator++;
                            continue;
                        }
                        if(cell_i.getCellTypeEnum().name().equals("FORMULA")) {
                            if(cell_i.getCachedFormulaResultTypeEnum().name().equals("ERROR")) {
                                df.get(col_iterator)[row_number] = get_cell_of_type(cell_i.getCellFormula(),coltypes[c]);      // bad formula
                            } else {
                                df.get(col_iterator)[row_number] = get_cell_of_type(cell_i.getStringCellValue(),coltypes[c]);  // good formula
                            }
                        } else {
                            df.get(col_iterator)[row_number] = get_cell_of_type(cell_i.getStringCellValue(),coltypes[c]);      // no formula
                        }
                        col_iterator++;
                    }
                }
            }

            row_number++;
        }
        this.header_refactor();
        this.remove_leading_zeros();
    }
    public DF (ArrayList<Object[]> base) {
        this.df = base;
    }
    public DF (DF old_base, boolean[] keep) {
        this.coltypes = old_base.coltypes;
        this.header = old_base.header;
        this.ncol = old_base.ncol;
        this.nrow = old_base.nrow;
        this.df = new ArrayList<>();
        this.df_populate(this.coltypes);
        for (int i = 0; i < this.ncol; i++) {
            System.arraycopy(old_base.df.get(i), 0,this.df.get(i),0,this.nrow);
        }
        this.keep_rows(keep);
    }

    public DF (DF old_base, boolean[] keep, boolean keep_cols) {
        this.coltypes = old_base.coltypes;
        this.header = old_base.header;
        this.ncol = old_base.ncol;
        this.nrow = old_base.nrow;
        this.df = new ArrayList<>();
        this.df_populate(this.coltypes);
        for (int i = 0; i < this.ncol; i++) {
            System.arraycopy(old_base.df.get(i), 0,this.df.get(i),0,this.nrow);
        }
        this.keep_cols(keep);
    }
    public DF (String path, DF mapping) throws IOException {
        String[] listdir = new File(path).list();
        if (listdir == null) return;
        int dim;

        try {
            Scanner scanner = new Scanner(new File(wd+"fic_france_nb_lignes_M-1.txt"));
            dim = scanner.nextInt();
                for (String name : listdir) {
                    if (name.contains(yyyymm)) {
                        dim += csv_get_nrows(path + name, '\t');
                    }
                }
        } catch (NoSuchElementException e) {
            dim = 0;
            for (String file : listdir) {
                char delim = ';';
                if (!file.contains("LaParisienne")) delim = '\t';
                dim += csv_get_nrows(path+file, delim);
            }
            FileWriter writer = new FileWriter(wd+"fic_france_nb_lignes_M-1.txt",false);
            writer.write(dim);
            writer.close();
        }

        for (int i = 0; i < listdir.length; i++) {
            if(listdir[i].contains("LaParisienne")) {
                listdir = swap(listdir, 0, i);
            }
        } // swap LPA gros fichier en premier

        String file_main = listdir[0];
        if (!file_main.contains("LaParisienne")) return;
        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true, ';');
        settings.trimValues(true);

        Reader inputReader = new InputStreamReader(Files.newInputStream(new File(path+file_main).toPath()), encoding);
        CsvParser parser = new CsvParser(settings);
        List<String[]> parsedRows = parser.parseAll(inputReader);
        Iterator<String[]> rows = parsedRows.iterator();
        header = rows.next();
        String[] header_temp = header;
        this.subst_columns(mapping);
        coltypes = get_col_types(header, coltypes_B);

        nrow = dim;
        assert (coltypes.length == parsedRows.get(0).length);
        ncol = get_len(coltypes);
        df = new ArrayList<>(get_len(coltypes));
        this.df_populate(coltypes);

        int i = 0;
        while(rows.hasNext()) {
            int j = 0;
            int k = 0;
            String[] parsedRow = rows.next();
            for (String s : parsedRow) {
                if (coltypes[k] != Col_types.SKP) {
                    df.get(j)[i] = get_lowercase_cell_of_type(s,coltypes[k]);
                    j++;
                }
                k++;
            }
            i++;
        }

        String[] header_ref = this.header_refactor(header_temp);

        for (String file : listdir) {
            if(file.contains("LaParisienne")) continue;
            inputReader = new InputStreamReader(Files.newInputStream(new File(path+file).toPath()), encoding);
            settings.setDelimiterDetectionEnabled(true, '\t');
            parser = new CsvParser(settings);
            parsedRows = parser.parseAll(inputReader);
            rows = parsedRows.iterator();
            header_temp = subst_columns(mapping,rows.next());
//            Col_types[] coltypes_temp = get_col_types(header_temp, coltypes_B);

            while(rows.hasNext()) {
                    int k = 0;
                    int j = 0;
                    String[] parsedRow = rows.next();
                    for (String s : parsedRow) {
                        if(this.coltypes[j] == Col_types.SKP) {
                            j++;
                            continue;
                        }
                        int index = find_in_arr_first_index(header_ref,header_temp[k]);
                        if (index != -1) {
                            df.get(index)[i] = get_lowercase_cell_of_type(s,coltypes[index]);
                        }
                        k++;
                    }
                    i++;
                }
        }


        this.remove_leading_zeros();
    }
    public DF (DF old_base, String crit) {
        this.coltypes = old_base.coltypes;
        this.header = old_base.header;
        this.ncol = old_base.ncol;
        this.nrow = old_base.nrow;
        this.df = new ArrayList<>();
        this.df_populate(this.coltypes);
        for (int i = 0; i < this.ncol; i++) {
            System.arraycopy(old_base.df.get(i), 0,this.df.get(i),0,this.nrow);
        }
        boolean[] keep = new boolean[this.nrow];
        for (int i = 0; i < this.nrow; i++) {
            keep[i] = this.c(0)[i].equals(crit);
        }
        this.keep_rows(keep);
    }
    public DF (DF old_base) {
        this.coltypes = old_base.coltypes;
        this.header = old_base.header;
        this.ncol = old_base.ncol;
        this.nrow = old_base.nrow;
        this.df = new ArrayList<>();
        this.df_populate(this.coltypes);
        for (int i = 0; i < this.ncol; i++) {
            System.arraycopy(old_base.df.get(i), 0,this.df.get(i),0,this.nrow);
        }
    }
    public DF () {
        
    }
    // PRINT
    public void print() {
        this.print(min(10,this.nrow));
    }
    public void print(int rows) {
        System.out.println(Arrays.toString(this.header));
        rows = Math.min(rows,this.nrow);

        for (int i = 0; i < rows; i++) {
            System.out.println(Arrays.toString(this.r(i)));
        }
        int ncoll = this.df.size();
        int nrowl = this.df.get(0).length;
    }
    public void print_cols() {
       for (int i = 0; i < this.ncol; i++) {
           System.out.println(Arrays.toString(this.df.get(i)));
       }
    }
    public void printgrille() {
        int max = min(nrow, 100);
        this.print(max);
    }

    // DATA
    public Object[] r(int index){
        Object[] row = new Object[ncol];
        for(int i=0; i<this.ncol; i++){
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
    public Object[] c_filtre(String colname, String col_filtre, String value){
        int index = find_in_arr_first_index(header, colname);
        int counter = 0;
        for (int i = 0; i < this.nrow; i++) {
            if(this.c(col_filtre)[i].equals(value)) counter++;
        }
        if (counter == 0) return null;

        Object[] out = new Object[counter];
        int j = 0;
        for (int i = 0; i < this.nrow; i++) {
            if(this.c(col_filtre)[i].equals(value)) {
                out[j] = this.c(colname)[i];
                j++;
            }
        }
        return out;
    }
    public Object[] c_filtre_2(String colname, String col_filtre_1, String value_1, String col_filtre_2, String value_2){
        int index = find_in_arr_first_index(header, colname);
        int counter = 0;
        for (int i = 0; i < this.nrow; i++) {
            if(this.c(col_filtre_1)[i].equals(value_1) & this.c(col_filtre_2)[i].equals(value_2)) counter++;
        }
        if (counter == 0) return null;

        Object[] out = new Object[counter];
        int j = 0;
        for (int i = 0; i < this.nrow; i++) {
            if(this.c(col_filtre_1)[i].equals(value_1) & this.c(col_filtre_2)[i].equals(value_2)) {
                out[j] = this.c(colname)[i];
                j++;
            }
        }
        return out;
    }
    public String c_filtre_2_crit_1_value(String colname, String col_filtre_1, String value_1, String col_filtre_2, String value_2){
        for (int i = 0; i < this.nrow; i++) {
            if(this.c(col_filtre_1)[i].equals(value_1) & this.c(col_filtre_2)[i].equals(value_2)) {
                return (String) this.c(colname)[i];
            }
        }
        return null;
    }
    public int ind_filtre_2_crit_1_value(String col_filtre_1, String value_1, String col_filtre_2, String value_2){
        for (int i = 0; i < this.nrow; i++) {
            if(this.c(col_filtre_1)[i].equals(value_1) & this.c(col_filtre_2)[i].equals(value_2)) {
                return i;
            }
        }
        return -1;
    }
    public Object[] c(int index){
        return df.get(index);
    }
    public int csv_get_nrows(String path, char delim) {
        int out = 0;
        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true, delim);

        try(Reader inputReader = new InputStreamReader(Files.newInputStream(
                new File(path).toPath()), encoding)){
            CsvParser parser = new CsvParser(settings);
            List<String[]> parsedRows = parser.parseAll(inputReader);
            out = parsedRows.size()-1;
        } catch (IOException ignored) {}
        return out;
    }
    public void header_refactor() {
        String[] header_new = new String[get_len(coltypes)];
        Col_types[] coltypes_new = new Col_types[get_len(coltypes)];
        int j = 0;
        for (int i = 0; i < coltypes.length; i++) {
            if (coltypes[i] != Col_types.SKP) {
                header_new[j] = header[i];
                coltypes_new[j] = coltypes[i];
                j++;
            }
        }
        this.coltypes = coltypes_new;
        this.header = header_new;
    }
    public String[] header_refactor(String[] head) {
        String[] header_new = new String[get_len(coltypes)];
        Col_types[] coltypes_new = new Col_types[get_len(coltypes)];
        String[] header_temp_new = new String[get_len(coltypes)];
        int j = 0;
        for (int i = 0; i < coltypes.length; i++) {
            if (coltypes[i] != Col_types.SKP) {
                header_new[j] = header[i];
                coltypes_new[j] = coltypes[i];
                header_temp_new[j] = head[i];
                j++;
            }
        }
        this.coltypes = coltypes_new;
        this.header = header_new;
        return header_temp_new;
    }
    public void df_populate (Col_types[] vectypes) {
        for (Col_types coltype : vectypes) {
            switch (coltype) {
                case STR:
                    this.df.add(new String[nrow]);
                    break;
                case DBL:
                    this.df.add(new Double[nrow]);
                    break;
                case DAT:
                    this.df.add(new Date[nrow]);
                    break;
                default:
            }
        }
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
        if (cell.contains("Lieu_de")) {
            System.out.println(cell.trim() + "--------------");
        }
        Object out = null;
        switch (type) {
            case STR -> {
                if (cell == null) return "";
                return cell.trim();
            }
            case DBL -> {
                if (cell == null) return NA_DBL;
                try {
                    return Double.parseDouble(cell.replace(",", "."));
                } catch (NumberFormatException ignored) {
                    return NA_DBL;
                }
            }
            case DAT -> {
                if (cell == null) return NA_DAT;
                try {
                    return format.parse(cell);
                } catch (NullPointerException | ParseException ignored) {
                    return NA_DAT;
                }
            }
        }
        return out;
    }
    public Object get_lowercase_cell_of_type (String cell, Col_types type) {
        Object out = "";
        switch (type) {
            case STR -> {
                if (cell == null) return "";
                return cell.toLowerCase().trim();
            }
            case DBL -> {
                if (cell == null) return NA_DBL;
                try {
                    return Double.parseDouble(cell.replace(",", "."));
                } catch (NumberFormatException ignored) {
                    return NA_DBL;
                }
            }
            case DAT -> {
                if (cell == null) return NA_DAT;
                try {
                    return format.parse(cell);
                } catch (NullPointerException | ParseException ignored) {
                    return NA_DAT;
                }
            }
        }
        return out;
    }
    public Col_types[] get_col_types (String[] head,  HashMap<String, DF.Col_types> types) {
        Col_types[] out = new Col_types[head.length];
        int i = 0;
        for (String s : head) {
            if(s == null) {
                out[i] = Col_types.SKP;
            } else {
                out[i] = types.get(s);
                if(out[i] == null) {
                    out[i] = Col_types.STR;
                }
            }
            i++;
        }
        return out;
    }
    public void remove_leading_zeros() {
        String[] cols = {"Numéro_Dossier","Numéro_Adhésion"};
        for(String col : cols) {
            if(check_in(col,this.header)) {
                for (int i = 0; i < this.nrow; i++) {
                    String val = (String) this.c(col)[i];
                    if (val != null) {
                        this.c(col)[i] = val.replaceFirst("^0+", "");
                    }
                }
            }
        }
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
        int k = 0;
        for (int i = 0; i < nrow_old; i++) {
            if (keep_bool[i]) {
                for (int j = 0; j < ncol; j++){
                    rowsToKeep.get(j)[k] = df.get(j)[i];
                }
                k++;
            }
        }
        this.df = rowsToKeep;
    }
    public void keep_cols( boolean[] keep_vec) {
        this.header = keep_from_array(header,keep_vec);
        this.coltypes = keep_from_array(coltypes,keep_vec);
        int j = 0;
            for (int i = 0; i < ncol; i++) {
                if(!keep_vec[i]) {
                    df.remove(j);
                } else {
                    j++;
                }
            }
        this.ncol = sum_boolean(keep_vec);
    }
    public DF filter_out(String colname, String crit) {
        boolean[] vec = new boolean[nrow];
        Object[] col = this.c(colname);
        for (int i = 0; i < nrow; i++) {
            vec[i] = col[i].equals(crit);
        }
        return(new DF(this, vec));
    }
    public void filter_in(Object colname, String crit) {
        boolean[] vec = new boolean[nrow];
        Object[] col = this.c(colname);
        for (int i = 0; i < nrow; i++) {
            vec[i] = col[i].equals(crit);
        }
        this.keep_rows(vec);

    }


    // GRILLES
    public void get_grille_gen() {
        boolean[] keep = find_in_arr(grille_gen_g.c("Numero_Police"), Police_en_cours_maj);
        boolean[] keep2 = find_in_arr(grille_gen_g.c("Flux"), Flux_en_cours);
        boolean[] crit = b_and(keep, keep2);
        this.grille_gen = new DF(grille_gen_g, crit);
    }

    public void dna() {
        boolean[] keep = new boolean[ncol];
        Arrays.fill(keep, false);
        for (int i = 0; i < ncol; i++) {
                Object[] col = this.c(i);
            switch (coltypes[i]) {
                case STR -> {
                    for (int j = 0; j < nrow; j++) {
                        if (!col[j].equals(NA_STR)) {
                            keep[i] = true;
                            break;
                        }
                    }
                }
                case DAT -> {
                    for (int j = 0; j < nrow; j++) {
                        if (!col[j].equals(NA_DAT)) {
                            keep[i] = true;
                            break;
                        }
                    }
                }
                case DBL -> {
                    for (int j = 0; j < nrow; j++) {
                        if (!col[j].equals(NA_DBL)) {
                            keep[i] = true;
                            break;
                        }
                    }
                }
            }
            }

        for (int j = 0; j < ncol; j++) {
            keep[j] = keep[j] & !this.header[j].equals("Date de modif");
        }
        if (sum_boolean(keep) != 0) {
            this.keep_cols(keep);
        }
    }
    public void err(String msg) {
//        System.err.println(n);
        System.out.println(msg);
        System.out.println(Police_en_cours);
        System.out.println(Controle_en_cours);
    }
    public void header_problems() {
        for (int i = 0; i < this.header.length; i++) {
            if (this.header[i].contains("Date_Souscription_Adhésion borne basse")) {
                this.header[i] = "Date_Souscription_Adhésion borne basse <= Date_Survenance";
            } else if (this.header[i].contains("Date_Souscription_Adhésion borne haute")) {
                this.header[i] = "Date_Souscription_Adhésion borne haute >= Date_Survenance";
            }
        }
    }
    public boolean[] simple_grille(DF grille) {
        if (grille.df == null) {
            err("grille absente");
            return logvec(this.nrow,true);
        }
        boolean[] vec = new boolean[nrow];
        String[] cols = new String[grille.ncol-1];
        for (int i = 1; i < grille.ncol; i++) {
            cols[i-1] = grille.header[i];
        } // si numéro police reste toujours le premier

        if (!App.check_in(cols,header)) {
            err("missing columns");
            Arrays.fill(vec,true);
            return vec;
        } else {
            Arrays.fill(vec,false);
        }
        int dim = grille.nrow;
        ArrayList<Integer> reste_gen = new ArrayList<>(dim);
        for(int r = 0; r < dim; r++) {
            reste_gen.add(r);
        }
        for (int i = 0; i < nrow; i++) {
            ArrayList<Integer> reste = new ArrayList<>(reste_gen);
            Object cell_base;
            Object cell_grille;
            for (String col : cols) {

                if(find_in_arr_first_index(grille.header, col) == -1) {
                    continue;
                }

                cell_base = this.c(col)[i];

                if (cell_base != "") {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille = grille.c(find_in_arr_first_index(grille.header, col))[r];
                        if(!(cell_grille.equals(cell_base) | cell_grille.equals("{ renseigné }") | cell_grille.equals(NA_STR))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        break;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille = grille.c(col)[r];
                        if(!(cell_grille.equals("") | cell_grille.equals("{ vide }") | cell_grille.equals(NA_STR))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        break;
                    }
                }

            }
        }
        for (int i = 0; i < nrow; i++) {
            vec[i] = vec[i] | !this.c("Numéro_Police")[i].equals(Police_en_cours);
        }
        return vec;
    }
    public boolean[] one_dim_grille(DF grille) {
//        System.out.println(Controle_en_cours);
//        grille.printgrille();

        String col = grille.header[1];
        String[] refer = Arrays.copyOf(grille.c(col), grille.c(col).length, String[].class);
        boolean[] vec;
        if (!App.check_in(col,this.header)) {
            err("missing columns");
            return logvec(this.nrow,true);
        } else {
            vec = logvec(this.nrow,false);
        }

        if (refer.length == 1) {
            if (refer[0].equals("{ renseigné }")) {
                for (int i = 0; i < this.nrow; i++) {
                    vec[i] = this.c(col)[i].equals("");
                }
            } else {
                for (int i = 0; i < this.nrow; i++) {
                    vec[i] = !this.c(col)[i].equals(refer[0]);
                }
            }
        } else {
            for (int i = 0; i < this.nrow; i++) {
                vec[i] = !App.check_in((String) this.c(col)[i],refer);
            }
        }

        return vec;
    }
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
//        int j = 0;
//        for (int i = 0; i < ct.length; i++) {
//            if (ct[i] != DF.Col_types.SKP & this.header[i] != null) {
//                j++;
//            }
//        }
//        return j;
    } // gets number of non-SKIP columns
    public boolean compa_signe(Double a, Double b, short signe) {
        switch (signe) {
            case 1:
                return Objects.equals(a, b);
            case 2:
                return a > b;
            case 3:
                return a < b;
            case 4:
                return a >= b;
            case 5:
                return a <= b;
            case 6:
                return !Objects.equals(a, b);
            default:
                err("erreur signe non-renseignée");
                return false;
        }
    } // true = OK, selon signe
    public boolean compa_signe(Date a, Date b, short signe) {
        switch (signe) {
            case 1:
                return Objects.equals(a, b);
            case 2:
                return a.after(b);
            case 3:
                return a.before(b);
            case 4:
                return !a.before(b);
            case 5:
                return !a.after(b);
            case 6:
                return !Objects.equals(a, b);
            default:
                err("erreur signe non-renseignée");
                return false;
        }
    } // true = OK, selon signe

    public void certif() {

    }
    public void controle_811() {
        Controle_en_cours = "C811";
        if (grille_gen_controle_absent()) return;
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        if (grille.df == null) {
            err("grille absente");
            return;
        }

        boolean[] vec = new boolean[nrow];
        String[] cols = {"Numéro_Police","Montant_Indemnité_Principale","Valeur_Achat"};
        if (!App.check_in(cols,header)) {
            err("missing columns");
            Arrays.fill(vec,true);
            this.err_vec_handle(vec);
            return;
        } else {
            Arrays.fill(vec,false);
        }
        int dim = grille.nrow;
        ArrayList<Integer> reste_gen = new ArrayList<>(dim);
        for(int r = 0; r < dim; r++){
            reste_gen.add(r);
        }

        String[] crit = {"Statut_Technique_Sinistre","SKU","Type_Indemnisation","Statut_Technique_Sinistre_2","Libellé_Garantie","Critère_Identification_Bien_Garanti_2","Critère_Identification_Bien_Garanti_6",
                "Critère_Tarifaire_1","Statut_Sogedep" };

        for (int i = 0; i < this.nrow; i++) {

            ArrayList<Integer> reste = new ArrayList<>(reste_gen);
            Object cell_base;
            Double cell_base_dbl;
            String cell_base_str;
            Object cell_grille;
            Double cell_grille_dbl;
            for (String col : crit) {

                if(find_in_arr_first_index(header, col) == -1 | find_in_arr_first_index(grille.header, col) == -1) {
                    continue;
                }
                cell_base = this.c(col)[i];

                if (cell_base != "") {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {

                      cell_grille = grille.c(find_in_arr_first_index(grille.header, col))[r];
                       if(!(cell_grille.equals(cell_base) | cell_grille.equals("{ renseigné }") | cell_grille.equals(NA_STR))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        break;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille = grille.c(col)[r];
                        if(!(cell_grille.equals("") | cell_grille.equals("{ vide }") | cell_grille.equals(NA_STR))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                  for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        break;
                    }
                }
            }
            if (reste.isEmpty()) {
                vec[i] = true;
                continue;
            }

            String col = "Valeur_Catalogue";
            String colg = "Valeur_Catalogue Borne haute";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                cell_base_dbl = (Double) this.c(col)[i];
                if (cell_base_dbl != null) {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille_dbl = (Double) grille.c(colg)[r];
                        if(!(cell_grille_dbl >= cell_base_dbl | cell_grille_dbl.equals(NA_DBL))){
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille_dbl = (Double) grille.c(colg)[r];
                        if(!cell_grille_dbl.equals(NA_DBL)) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                }
            }

            col = "Valeur_Catalogue";
            colg = "Valeur_Catalogue Borne basse";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                cell_base_dbl = (Double) this.c(col)[i];
                if (cell_base_dbl != null) {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille_dbl = (Double) grille.c(colg)[r];
                        if(!(cell_grille_dbl <= cell_base_dbl | cell_grille_dbl.equals(NA_DBL))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille_dbl = (Double) grille.c(colg)[r];
                        if(!(cell_grille_dbl.equals(NA_DBL))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                }
            }

            col = "Code_Client";
            colg = "Retraitement Code_Client";
            if(find_in_arr_first_index(grille.header, colg) != -1) {
                if(find_in_arr_first_index(header, col) == -1) {
                    cell_base_str = "";
                } else {
                    cell_base_str = (String) this.c(col)[i];
                }
                if (!Objects.equals(cell_base_str, "")) {
                    if (cell_base_str.matches(regex_digits)) {
                        boolean[] temp = new boolean[reste.size()];
                        int ind = 0;
                        for (int r : reste) {
                            cell_grille = grille.c(colg)[r];
                            if(!(cell_grille.equals("professionnel") | cell_grille.equals(NA_STR))) {
                                temp[ind] = true;
                            }
                            ind++;
                        }
                        for (int t = temp.length-1; t >= 0; t--) {
                            if (temp[t]) {
                                reste.remove(t);
                            }
                        }
                        if (reste.isEmpty()) {
                            vec[i] = true;
                            continue;
                        }
                    } else {
                        boolean[] temp = new boolean[reste.size()];
                        int ind = 0;
                        for (int r : reste) {
                            cell_grille = grille.c(colg)[r];
                            if(!(cell_grille.equals("particulier") | cell_grille.equals(NA_STR))) {
                                temp[ind] = true;
                            }
                            ind++;
                        }
                        for (int t = temp.length-1; t >= 0; t--) {
                            if (temp[t]) {
                                reste.remove(t);
                            }
                        }
                        if (reste.isEmpty()) {
                            vec[i] = true;
                            continue;
                        }
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        if(!(grille.c(colg)[r].equals(NA_STR))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                }
            }

            col = "Critère_Identification_Bien_Garanti_1";
            colg = "Référentiel Marque";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
            cell_base_str = (String) this.c(col)[i];
            switch (cell_base_str) {
                case "apple":
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille = grille.c(colg)[r];
                        if(!(cell_grille.equals(1) | cell_grille.equals(8) | cell_grille.equals(NA_DBL))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                    break;
                case "samsung":
                    temp = new boolean[reste.size()];
                    ind = 0;
                    for (int r : reste) {
                        cell_grille = grille.c(colg)[r];
                        if(!(cell_grille.equals(2) | cell_grille.equals(4) | cell_grille.equals(8) | cell_grille.equals(NA_DBL))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                    break;
                case "huawei":
                    temp = new boolean[reste.size()];
                    ind = 0;
                    for (int r : reste) {
                        cell_grille = grille.c(colg)[r];
                        if(!(cell_grille.equals(3) | cell_grille.equals(4) | cell_grille.equals(5) | cell_grille.equals(8) | cell_grille.equals(NA_DBL))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                    break;
                case "dyson":
                    temp = new boolean[reste.size()];
                    ind = 0;
                    for (int r : reste) {
                        cell_grille = grille.c(colg)[r];
                        if(!(cell_grille.equals(4) | cell_grille.equals(5) | cell_grille.equals(6) | cell_grille.equals(7) | cell_grille.equals(NA_DBL))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                    break;
                default:
                    temp = new boolean[reste.size()];
                    ind = 0;
                    for (int r : reste) {
                        cell_grille = grille.c(colg)[r];
                        if(!(cell_grille.equals(4) | cell_grille.equals(5) | cell_grille.equals(6) | cell_grille.equals(8) | cell_grille.equals(NA_DBL))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
            }
            }

            col = "Date_Clôture";
            colg = "Date_Clôture borne haute";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                Date date_clot = (Date) this.c(col)[i];
                if (date_clot != NA_DAT) {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        Date date_clot_ref = (Date) grille.c(colg)[r];
                        if(!(!date_clot_ref.before(date_clot) | grille.c(colg)[r].equals(NA_DAT))){
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        if(!grille.c(colg)[r].equals(NA_DAT)) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                }
            }

            colg = "Date_Clôture borne basse";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                Date date_clot = (Date) this.c(col)[i];
                if (date_clot != NA_DAT) {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        Date date_clot_ref = (Date) grille.c(colg)[r];
                        if(!(!date_clot_ref.after(date_clot) | grille.c(colg)[r].equals(NA_DAT))){
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        if(!grille.c(colg)[r].equals(NA_DAT)) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                }
            }

            col = "Date_Souscription_Adhésion";
            String col1 = "Date_Survenance";
            colg = "Age";
            int reste_age = reste.get(0);
            int reste_i = -1;
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(header, col1) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                double age_ref = (double) grille.c(colg)[reste_age];
                if(!Objects.equals(age_ref, NA_DBL)) {
                    LocalDate date_sous = to_Date((Date) this.c(col)[i]);
                    LocalDate date_surv = to_Date((Date) this.c(col1)[i]);
                    if (date_sous != NA_LDAT & date_surv != NA_LDAT) {
                            String m = (String) grille.c("Date_Souscription_Adhésion borne basse <= Date_Survenance")[reste_age];
                            int months = NumberUtils.toInt(m.replaceAll("\\D+",""),0);
                            if (date_sous.plusMonths(months).isBefore(date_surv)) {
                                vec[i] = true;
                                continue;
                            }
                            int age = Period.between(date_sous.plusMonths(months), date_surv).getYears();
                            for (int r : reste) {
                                if (age == (double) grille.c(colg)[r]) {
                                    reste_i = r;
                                    break;
                                }
                            }
//                            if(!(age == age_ref | Objects.equals(age_ref, NA_DBL))) {
//                                vec[i] = true;
//                            }
                    } else {
                        vec[i] = true;
                        continue;
                    }
                }
            }

            if (reste_i == -1) {
                vec[i] = true;
//                err("error age");
                continue;
            }

            col = "Montant_Indemnité_Principale";
            Double mip = (Double) this.c(col)[i];
            if (mip.equals(NA_DBL)) {
                vec[i] = true;
                continue;
            }
            colg = "Valeur Montant_Indemnité_Principale";
            Object pourcent_raw = grille.c("Pourcentage Montant_Indemnité_Principale")[reste_i];
            Object signe_raw = grille.c("Signe Montant_Indemnité_Principale")[reste_i];
            short signe = (short) round((Double) signe_raw);
            Double montant;
            Double pourcentage;
            double mip_ref;
            if(pourcent_raw.equals(NA_DBL)) {
                pourcentage = 100d;
            } else {
                pourcentage = (Double) pourcent_raw;
            }
            String montant_raw = (String) grille.c(colg)[reste_i];
            if (montant_raw.equals("valeur_achat")) {
                montant = (Double) this.c("Valeur_Achat")[i];
            } else {
                montant = Double.valueOf(montant_raw.replace(",","."));
            }
            mip_ref = Math.round(montant * pourcentage) / 100.0;

            vec[i] = !compa_signe(mip,mip_ref,signe);
        }
        this.err_vec_handle(vec);
    } // g
    public void controle_810() {
        Controle_en_cours = "C810";
        if (grille_gen_controle_absent()) return;
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        if (grille.df == null) {
            err("grille absente");
            return;
        }

        boolean[] vec = new boolean[nrow];
        String[] cols = {"Numéro_Police","Statut_Sogedep"};
        if (!App.check_in(cols,header)) {
            err("missing columns");
            Arrays.fill(vec,true);
            this.err_vec_handle(vec);
            return;
        } else {
            Arrays.fill(vec,false);
        }
        int dim = grille.nrow;
        ArrayList<Integer> reste_gen = new ArrayList<>(dim);
        for(int r = 0; r < dim; r++){
            reste_gen.add(r);
        }

        String[] crit = {"Statut_Technique_Sinistre","Statut_Sogedep","Type_Indemnisation"};
        for (int i = 0; i < nrow; i++) {

            ArrayList<Integer> reste = new ArrayList<>(reste_gen);
            Object cell_base;
            Double cell_base_dbl;
            Date cell_base_dat;
            Object cell_grille;
            Double cell_grille_dbl;
            Date cell_grille_dat;

            for (String col : crit) {

                if(find_in_arr_first_index(header, col) == -1 | find_in_arr_first_index(grille.header, col) == -1) {
                    continue;
                }
                cell_base = this.c(col)[i];

                if (cell_base != "") {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {

                        cell_grille = grille.c(find_in_arr_first_index(grille.header, col))[r];
                        if(!(cell_grille.equals(cell_base) | cell_grille.equals("{ renseigné }") | cell_grille.equals(NA_STR))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        break;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille = grille.c(col)[r];
                        if(!(cell_grille.equals("") | cell_grille.equals("{ vide }") | cell_grille.equals(NA_STR))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        break;
                    }
                }
            }
            if (reste.isEmpty()) {
                vec[i] = true;
                continue;
            }

            String col = "Valeur_Catalogue";
            String colg = "Valeur_Catalogue Borne haute";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                cell_base_dbl = (Double) this.c(col)[i];
                if (cell_base_dbl != null) {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille_dbl = (Double) grille.c(colg)[r];
                        if(!(cell_grille_dbl >= cell_base_dbl | cell_grille_dbl.equals(NA_DBL))){
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille_dbl = (Double) grille.c(colg)[r];
                        if(!cell_grille_dbl.equals(NA_DBL)) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                }
            }

            col = "Valeur_Catalogue";
            colg = "Valeur_Catalogue Borne basse";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                cell_base_dbl = (Double) this.c(col)[i];
                if (cell_base_dbl != null) {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille_dbl = (Double) grille.c(colg)[r];
                        if(!(cell_grille_dbl <= cell_base_dbl | cell_grille_dbl.equals(NA_DBL))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille_dbl = (Double) grille.c(colg)[r];
                        if(!(cell_grille_dbl.equals(NA_DBL))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                }
            }

            col = "Date_Déclaration";
            colg = "Valeur Date_Déclaration";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                cell_base_dat = (Date) this.c(col)[i];
                if (cell_base_dat != NA_DAT) {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille_dat = (Date) grille.c(colg)[r];
                        short signe = (short) grille.c("Signe Date_Déclaration")[r];
                        if(compa_signe(cell_base_dat,cell_grille_dat,signe) | cell_grille_dat.equals(NA_DAT)){
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille_dat = (Date) grille.c(colg)[r];
                        if(!cell_grille_dat.equals(NA_DAT)) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                }
            }

            if (reste.size() > 1) {
                vec[i] = true;
                err("error lignes multiples");
                continue;
            }
            int reste_i = reste.get(0);

            col = "Montant_Indemnité_Principale";
            Double mip = (Double) this.c(col)[i];
            if (mip.equals(NA_DBL)) {
                vec[i] = true;
                continue;
            }
            colg = "Valeur Montant_Indemnité_Principale";
            Object signe_raw = grille.c("Signe Montant_Indemnité_Principale")[reste_i];
            short signe = (short) round((Double) signe_raw);
            String montant_raw = (String) grille.c(colg)[reste_i];
            double montant = Double.parseDouble(montant_raw.replace(",",".").replaceAll("[^\\d.]", ""));
            double mip_ref = Math.round(montant * 100) / 100.0;

            vec[i] = !compa_signe(mip,mip_ref,signe);
        }

        this.err_vec_handle(vec);
    } // g
    public void controle_809() {
        Controle_en_cours = "C809";
        if (grille_gen_controle_absent()) return;
        boolean[] vec = new boolean[nrow];
        String[] cols = {"Montant_Indemnité_Principale","Montant_Frais_Annexe","Montant_Reprise","Montant_Total_Règlement"};
        if (Gestionnaire_en_cours.equals("SPB Espagne")) {
            cols = new String[] {"Montant_Indemnité_Principale", "Montant_Reprise", "Montant_Total_Règlement"};
        }
        if (!App.check_in(cols,header)) {
            err("missing columns");
            Arrays.fill(vec,true);
            this.err_vec_handle(vec);
            return;
        } else {
            Arrays.fill(vec,false);
        }
        Double a;
        double b;
        if (Gestionnaire_en_cours.equals("SPB Espagne")) {
            for (int i = 0; i < nrow; i++) {
                a = Math.round(((double) this.c("Montant_Indemnité_Principale")[i] -
                        (double) this.c("Montant_Reprise")[i]) * 100) / 100d;
                b = Math.round((double) this.c("Montant_Total_Règlement")[i] * 100) / 100d;
                vec[i] = !a.equals(b);
            }
        } else {
            for (int i = 0; i < nrow; i++) {
                a = Math.round(((double) this.c("Montant_Indemnité_Principale")[i] +
                        (double) this.c("Montant_Frais_Annexe")[i] -
                        (double) this.c("Montant_Reprise")[i]) * 100) / 100d;
                b = Math.round((double) this.c("Montant_Total_Règlement")[i] * 100) / 100d;
                vec[i] = !a.equals(b);
            }
        }

        this.err_vec_handle(vec);
    }
    public void controle_808() {
        Controle_en_cours = "C808";
        if (grille_gen_controle_absent()) return;
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        if (grille.df == null) {
            err("grille absente");
            return;
        }

        boolean[] vec = new boolean[nrow];
        String[] cols = {"Numéro_Police","Montant_Indemnité_Principale","Montant_Reprise","Valeur_Achat"};
        if (!App.check_in(cols,header)) {
            err("missing columns");
            Arrays.fill(vec,true);
            this.err_vec_handle(vec);
            return;
        } else {
            Arrays.fill(vec,false);
        }
        int dim = grille.nrow;
        ArrayList<Integer> reste_gen = new ArrayList<>(dim);
        for(int r = 0; r < dim; r++){
            reste_gen.add(r);
        }
        String[] crit = {"Statut_Technique_Sinistre","Statut_Technique_Sinistre_2","SKU","Type_Indemnisation","Libellé_Garantie","Critère_Identification_Bien_Garanti_1","Critère_Identification_Bien_Garanti_2",
                "Critère_Identification_Bien_Garanti_6"};
        for (int i = 0; i < nrow; i++) {

            ArrayList<Integer> reste = new ArrayList<>(reste_gen);
            Object cell_base;
            Double cell_base_dbl;
            String cell_base_str;
            Object cell_grille;
            Double cell_grille_dbl;
            for (String col : crit) {

                if(find_in_arr_first_index(header, col) == -1 | find_in_arr_first_index(grille.header, col) == -1) {
                    continue;
                }
                cell_base = this.c(col)[i];

                if (cell_base != "") {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {

                        cell_grille = grille.c(find_in_arr_first_index(grille.header, col))[r];
                        if(!(cell_grille.equals(cell_base) | cell_grille.equals("{ renseigné }") | cell_grille.equals(NA_STR))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        break;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille = grille.c(col)[r];
                        if(!(cell_grille.equals("") | cell_grille.equals("{ vide }") | cell_grille.equals(NA_STR))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        break;
                    }
                }
            }
            if (reste.isEmpty()) {
                vec[i] = true;
                continue;
            }

            String col = "Valeur_Catalogue";
            String colg = "Valeur_Catalogue Borne haute";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                cell_base_dbl = (Double) this.c(col)[i];
                if (cell_base_dbl != null) {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille_dbl = (Double) grille.c(colg)[r];
                        if(!(cell_grille_dbl >= cell_base_dbl | cell_grille_dbl.equals(NA_DBL))){
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille_dbl = (Double) grille.c(colg)[r];
                        if(!cell_grille_dbl.equals(NA_DBL)) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                }
            }

            col = "Valeur_Catalogue";
            colg = "Valeur_Catalogue Borne basse";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                cell_base_dbl = (Double) this.c(col)[i];
                if (cell_base_dbl != null) {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille_dbl = (Double) grille.c(colg)[r];
                        if(!(cell_grille_dbl <= cell_base_dbl | cell_grille_dbl.equals(NA_DBL))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille_dbl = (Double) grille.c(colg)[r];
                        if(!(cell_grille_dbl.equals(NA_DBL))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                }
            }

            col = "Critère_Identification_Bien_Garanti_1";
            colg = "Référentiel Marque";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                cell_base_str = (String) this.c(col)[i];
                switch (cell_base_str) {
                    case "apple":
                        boolean[] temp = new boolean[reste.size()];
                        int ind = 0;
                        for (int r : reste) {
                            cell_grille = grille.c(colg)[r];
                            if(!(cell_grille.equals(1) | cell_grille.equals(8) | cell_grille.equals(NA_DBL))) {
                                temp[ind] = true;
                            }
                            ind++;
                        }
                        for (int t = temp.length-1; t >= 0; t--) {
                            if (temp[t]) {
                                reste.remove(t);
                            }
                        }
                        if (reste.isEmpty()) {
                            vec[i] = true;
                            continue;
                        }
                        break;
                    case "samsung":
                        temp = new boolean[reste.size()];
                        ind = 0;
                        for (int r : reste) {
                            cell_grille = grille.c(colg)[r];
                            if(!(cell_grille.equals(2) | cell_grille.equals(4) | cell_grille.equals(8) | cell_grille.equals(NA_DBL))) {
                                temp[ind] = true;
                            }
                            ind++;
                        }
                        for (int t = temp.length-1; t >= 0; t--) {
                            if (temp[t]) {
                                reste.remove(t);
                            }
                        }
                        if (reste.isEmpty()) {
                            vec[i] = true;
                            continue;
                        }
                        break;
                    case "huawei":
                        temp = new boolean[reste.size()];
                        ind = 0;
                        for (int r : reste) {
                            cell_grille = grille.c(colg)[r];
                            if(!(cell_grille.equals(3) | cell_grille.equals(4) | cell_grille.equals(5) | cell_grille.equals(8) | cell_grille.equals(NA_DBL))) {
                                temp[ind] = true;
                            }
                            ind++;
                        }
                        for (int t = temp.length-1; t >= 0; t--) {
                            if (temp[t]) {
                                reste.remove(t);
                            }
                        }
                        if (reste.isEmpty()) {
                            vec[i] = true;
                            continue;
                        }
                        break;
                    case "dyson":
                        temp = new boolean[reste.size()];
                        ind = 0;
                        for (int r : reste) {
                            cell_grille = grille.c(colg)[r];
                            if(!(cell_grille.equals(4) | cell_grille.equals(5) | cell_grille.equals(6) | cell_grille.equals(7) | cell_grille.equals(NA_DBL))) {
                                temp[ind] = true;
                            }
                            ind++;
                        }
                        for (int t = temp.length-1; t >= 0; t--) {
                            if (temp[t]) {
                                reste.remove(t);
                            }
                        }
                        if (reste.isEmpty()) {
                            vec[i] = true;
                            continue;
                        }
                        break;
                    default:
                        temp = new boolean[reste.size()];
                        ind = 0;
                        for (int r : reste) {
                            cell_grille = grille.c(colg)[r];
                            if(!(cell_grille.equals(4) | cell_grille.equals(5) | cell_grille.equals(6) | cell_grille.equals(8) | cell_grille.equals(NA_DBL))) {
                                temp[ind] = true;
                            }
                            ind++;
                        }
                        for (int t = temp.length-1; t >= 0; t--) {
                            if (temp[t]) {
                                reste.remove(t);
                            }
                        }
                        if (reste.isEmpty()) {
                            vec[i] = true;
                            continue;
                        }
                }
            }

            col = "Date_Clôture";
            colg = "Date_Clôture borne haute";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                Date date_clot = (Date) this.c(col)[i];
                if (date_clot != NA_DAT) {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        Date date_clot_ref = (Date) grille.c(colg)[r];
                        if(!(!date_clot_ref.before(date_clot) | grille.c(colg)[r].equals(NA_DAT))){
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        if(!grille.c(colg)[r].equals(NA_DAT)) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                }
            }

            colg = "Date_Clôture borne basse";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                Date date_clot = (Date) this.c(col)[i];
                if (date_clot != NA_DAT) {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        Date date_clot_ref = (Date) grille.c(colg)[r];
                        if(!(!date_clot_ref.after(date_clot) | grille.c(colg)[r].equals(NA_DAT))){
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        if(!grille.c(colg)[r].equals(NA_DAT)) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                }
            }

            col = "Date_Souscription_Adhésion";
            String col1 = "Date_Survenance";
            colg = "Age";
            int reste_age = reste.get(0);
            int reste_i = -1;
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(header, col1) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                double age_ref = (double) grille.c(colg)[reste_age];
                if(!Objects.equals(age_ref, NA_DBL)) {
                    LocalDate date_sous = to_Date((Date) this.c(col)[i]);
                    LocalDate date_surv = to_Date((Date) this.c(col1)[i]);
                    if (date_sous != NA_LDAT & date_surv != NA_LDAT) {
                        String m = (String) grille.c("Date_Souscription_Adhésion borne basse <= Date_Survenance")[reste_age];
                        int months = NumberUtils.toInt(m.replaceAll("\\D+",""),0);
                        if (date_sous.plusMonths(months).isBefore(date_surv)) {
                            vec[i] = true;
                            continue;
                        }
                        int age = Period.between(date_sous.plusMonths(months), date_surv).getYears();
                        for (int r : reste) {
                            if (age == (double) grille.c(colg)[r]) {
                                reste_i = r;
                                break;
                            }
                        }
//                            if(!(age == age_ref | Objects.equals(age_ref, NA_DBL))) {
//                                vec[i] = true;
//                            }
                    } else {
                        vec[i] = true;
                        continue;
                    }
                }
            }

            if (reste_i == -1) {
                vec[i] = true;
//                err("error age");
                continue;
            }

            col = "Montant_Reprise";
            colg = "Valeur Montant_Reprise";

            Object pourcent_raw;
            try {
                pourcent_raw = grille.c("Pourcentage Montant_Reprise")[reste_i];
            }
            catch(Exception e) {
                pourcent_raw = NA_DBL;
            }
            Object signe_raw = grille.c("Signe Montant_Reprise")[reste_i];
            short signe = (short) round((Double) signe_raw);
            Double montant;
            Double pourcentage;
            double mr_ref;
            Double mr;
            if(pourcent_raw.equals(NA_DBL)) {
                pourcentage = 100d;
            } else {
                pourcentage = (Double) pourcent_raw;
            }
            String montant_raw = (String) grille.c(colg)[reste_i];
            if (!montant_raw.matches(regex_letters)) {
                montant = Double.parseDouble(montant_raw.replace(",","."));
            }else if (montant_raw.equals("Valeur_Achat")) {
                montant = (Double) this.c("Valeur_Achat")[i];
            } else if (montant_raw.equals("VA / 1,2")) {
                montant = (Double) this.c("Valeur_Achat")[i] / 1.2;
            } else {
                montant = (Double) this.c("Montant_Indemnité_Principale")[i] / 1.2;
            }

            mr_ref = Math.round(montant * pourcentage) / 100.0;
            mr = (Double) this.c(col)[i];
            vec[i] = !compa_signe(mr,mr_ref,signe);
        }

        this.err_vec_handle(vec);
    } // g
    public void controle_807() {
        Controle_en_cours = "C807";
        if (grille_gen_controle_absent()) return;
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        if (grille.df == null) {
            err("grille absente");
            return;
        }

        boolean[] vec = new boolean[nrow];
        String[] cols = {"Numéro_Police","Montant_Frais_Annexe","Valeur_Achat"};
        if (!App.check_in(cols,header)) {
            err("missing columns");
            Arrays.fill(vec,true);
            this.err_vec_handle(vec);
            return;
        } else {
            Arrays.fill(vec,false);
        }
        int dim = grille.nrow;
        ArrayList<Integer> reste_gen = new ArrayList<>(dim);
        for(int r = 0; r < dim; r++){
            reste_gen.add(r);
        }

        String[] crit = {"Statut_Technique_Sinistre","SKU","Type_Indemnisation","Libellé_Garantie","Critère_Identification_Bien_Garanti_2",
                "Critère_Identification_Bien_Garanti_6","Statut_Sogedep","Statut_Technique_Sinistre_2"};
        for (int i = 0; i < nrow; i++) {

            ArrayList<Integer> reste = new ArrayList<>(reste_gen);
            Object cell_base;
            Double cell_base_dbl;
            String cell_base_str;
            Object cell_grille;
            Double cell_grille_dbl;
            for (String col : crit) {

                if(find_in_arr_first_index(header, col) == -1 | find_in_arr_first_index(grille.header, col) == -1) {
                    continue;
                }
                cell_base = this.c(col)[i];

                if (cell_base != "") {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {

                        cell_grille = grille.c(find_in_arr_first_index(grille.header, col))[r];
                       if(!(cell_grille.equals(cell_base) | cell_grille.equals("{ renseigné }") | cell_grille.equals(NA_STR))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        break;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille = grille.c(col)[r];
                        if(!(cell_grille.equals("") | cell_grille.equals("{ vide }") | cell_grille.equals(NA_STR))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        break;
                    }
                }
            }
            if (reste.isEmpty()) {
                vec[i] = true;
                continue;
            }

            String col = "Valeur_Catalogue";
            String colg = "Valeur_Catalogue Borne haute";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                cell_base_dbl = (Double) this.c(col)[i];
                if (cell_base_dbl != null) {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille_dbl = (Double) grille.c(colg)[r];
                        if(!(cell_grille_dbl >= cell_base_dbl | cell_grille_dbl.equals(NA_DBL))){
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille_dbl = (Double) grille.c(colg)[r];
                        if(!cell_grille_dbl.equals(NA_DBL)) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                }
            }

            col = "Valeur_Catalogue";
            colg = "Valeur_Catalogue Borne basse";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                cell_base_dbl = (Double) this.c(col)[i];
                if (cell_base_dbl != null) {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille_dbl = (Double) grille.c(colg)[r];
                        if(!(cell_grille_dbl <= cell_base_dbl | cell_grille_dbl.equals(NA_DBL))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille_dbl = (Double) grille.c(colg)[r];
                        if(!(cell_grille_dbl.equals(NA_DBL))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                }
            }

            col = "Critère_Identification_Bien_Garanti_1";
            colg = "Référentiel Marque";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                cell_base_str = (String) this.c(col)[i];
                switch (cell_base_str) {
                    case "apple":
                        boolean[] temp = new boolean[reste.size()];
                        int ind = 0;
                        for (int r : reste) {
                            cell_grille = grille.c(colg)[r];
                            if(!(cell_grille.equals(1) | cell_grille.equals(8) | cell_grille.equals(NA_DBL))) {
                                temp[ind] = true;
                            }
                            ind++;
                        }
                        for (int t = temp.length-1; t >= 0; t--) {
                            if (temp[t]) {
                                reste.remove(t);
                            }
                        }
                        if (reste.isEmpty()) {
                            vec[i] = true;
                            continue;
                        }
                        break;
                    case "samsung":
                        temp = new boolean[reste.size()];
                        ind = 0;
                        for (int r : reste) {
                            cell_grille = grille.c(colg)[r];
                            if(!(cell_grille.equals(2) | cell_grille.equals(4) | cell_grille.equals(8) | cell_grille.equals(NA_DBL))) {
                                temp[ind] = true;
                            }
                            ind++;
                        }
                        for (int t = temp.length-1; t >= 0; t--) {
                            if (temp[t]) {
                                reste.remove(t);
                            }
                        }
                        if (reste.isEmpty()) {
                            vec[i] = true;
                            continue;
                        }
                        break;
                    case "huawei":
                        temp = new boolean[reste.size()];
                        ind = 0;
                        for (int r : reste) {
                            cell_grille = grille.c(colg)[r];
                            if(!(cell_grille.equals(3) | cell_grille.equals(4) | cell_grille.equals(5) | cell_grille.equals(8) | cell_grille.equals(NA_DBL))) {
                                temp[ind] = true;
                            }
                            ind++;
                        }
                        for (int t = temp.length-1; t >= 0; t--) {
                            if (temp[t]) {
                                reste.remove(t);
                            }
                        }
                        if (reste.isEmpty()) {
                            vec[i] = true;
                            continue;
                        }
                        break;
                    case "dyson":
                        temp = new boolean[reste.size()];
                        ind = 0;
                        for (int r : reste) {
                            cell_grille = grille.c(colg)[r];
                            if(!(cell_grille.equals(4) | cell_grille.equals(5) | cell_grille.equals(6) | cell_grille.equals(7) | cell_grille.equals(NA_DBL))) {
                                temp[ind] = true;
                            }
                            ind++;
                        }
                        for (int t = temp.length-1; t >= 0; t--) {
                            if (temp[t]) {
                                reste.remove(t);
                            }
                        }
                        if (reste.isEmpty()) {
                            vec[i] = true;
                            continue;
                        }
                        break;
                    default:
                        temp = new boolean[reste.size()];
                        ind = 0;
                        for (int r : reste) {
                            cell_grille = grille.c(colg)[r];
                            if(!(cell_grille.equals(4) | cell_grille.equals(5) | cell_grille.equals(6) | cell_grille.equals(8) | cell_grille.equals(NA_DBL))) {
                                temp[ind] = true;
                            }
                            ind++;
                        }
                        for (int t = temp.length-1; t >= 0; t--) {
                            if (temp[t]) {
                                reste.remove(t);
                            }
                        }
                        if (reste.isEmpty()) {
                            vec[i] = true;
                            continue;
                        }
                }
            }

            col = "Date_Clôture";
            colg = "Date_Clôture borne haute";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                Date date_clot = (Date) this.c(col)[i];
                if (date_clot != NA_DAT) {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        Date date_clot_ref = (Date) grille.c(colg)[r];
                        if(!(!date_clot_ref.before(date_clot) | grille.c(colg)[r].equals(NA_DAT))){
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        if(!grille.c(colg)[r].equals(NA_DAT)) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                }
            }

            colg = "Date_Clôture borne basse";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                Date date_clot = (Date) this.c(col)[i];
                if (date_clot != NA_DAT) {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        Date date_clot_ref = (Date) grille.c(colg)[r];
                        if(!(!date_clot_ref.after(date_clot) | grille.c(colg)[r].equals(NA_DAT))){
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        if(!grille.c(colg)[r].equals(NA_DAT)) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                }
            }

            col = "Montant_Indemnité_Principale";
            colg = "Valeur Montant_Indemnité_Principale";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                boolean[] temp = new boolean[reste.size()];
                int ind = 0;
                for (int id : reste) {
                    String montant_raw = (String) grille.c(colg)[id];
                    Double mip = (Double) this.c(col)[i];

                    if (!montant_raw.equals(NA_STR)) {
                        Object signe_raw = grille.c("Signe Montant_Indemnité_Principale")[id];
                        short signe = (short) round((Double) signe_raw);
                        Double montant = Double.parseDouble(montant_raw.replace(",", "."));
                        temp[ind] = !compa_signe(mip,montant,signe);
                        ind++;
                    } else {
                        temp[ind] = false;
                    }
                }
                for (int t = temp.length-1; t >= 0; t--) {
                    if (temp[t]) {
                        reste.remove(t);
                    }
                }

        }

            if (reste.size() > 1) {
                vec[i] = true;
                System.out.println("ERROR");
                for (int ii : reste) {
                    System.out.println(Arrays.toString(grille.r(ii)));
                }
                System.out.println(reste);
                System.out.println(i);
                break;
//                err("error montant multiple");
//                continue;
            }

            col = "Montant_Frais_Annexe";
            colg = "Valeur Montant_Frais_Annexe";
            int reste_i = reste.get(0);
            Object pourcent_raw;
            try {
                pourcent_raw = grille.c("Pourcentage Montant_Frais_Annexe")[reste_i];
            }
            catch(Exception e) {
                pourcent_raw = NA_DBL;
            }
            Object signe_raw = grille.c("Signe Montant_Frais_Annexe")[reste_i];
            short signe = (short) round((Double) signe_raw);
            Double montant;
            Double pourcentage;
            double mr_ref;
            Double mr;
            if(pourcent_raw.equals(NA_DBL)) {
                pourcentage = 100d;
            } else {
                pourcentage = (Double) pourcent_raw;
            }
            String montant_raw = (String) grille.c(colg)[reste_i];
            if (!montant_raw.matches(regex_letters)) {
                montant = Double.parseDouble(montant_raw.replace(",","."));
            }else {
                montant = (Double) this.c("Valeur_Achat")[i];
            }

            mr_ref = Math.round(montant * pourcentage) / 100.0;
            mr = (Double) this.c(col)[i];
            vec[i] = !compa_signe(mr,mr_ref,signe);
        }
        this.err_vec_handle(vec);
    } // g
    public void controle_806() {
        Controle_en_cours = "C806";
        if (grille_gen_controle_absent()) return;
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        if (grille.df == null) {
            err("grille absente");
            return;
        }

        boolean[] vec = new boolean[nrow];
        String[] cols = {"Numéro_Police","Montant_Indemnité_Principale"};
        if (!App.check_in(cols,header)) {
            err("missing columns");
            Arrays.fill(vec,true);
            this.err_vec_handle(vec);
            return;
        } else {
            Arrays.fill(vec,false);
        }
        int dim = grille.nrow;
        ArrayList<Integer> reste_gen = new ArrayList<>(dim);
        for(int r = 0; r < dim; r++){
            reste_gen.add(r);
        }

        String[] crit = {"Type_Indemnisation","Critère_Identification_Bien_Remplacement_1","Critère_Identification_Bien_Remplacement_2"};
        for (int i = 0; i < nrow; i++) {

            ArrayList<Integer> reste = new ArrayList<>(reste_gen);
            Object cell_base;
            Double cell_base_dbl;
            Object cell_grille;
            Double cell_grille_dbl;
            for (String col : crit) {

                if(find_in_arr_first_index(header, col) == -1 | find_in_arr_first_index(grille.header, col) == -1) {
                    continue;
                }
                cell_base = this.c(col)[i];

                if (cell_base != "") {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {

                        cell_grille = grille.c(find_in_arr_first_index(grille.header, col))[r];
                        if(!(cell_grille.equals(cell_base) | cell_grille.equals("{ renseigné }") | cell_grille.equals(NA_STR))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        break;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille = grille.c(col)[r];
                        if(!(cell_grille.equals("") | cell_grille.equals("{ vide }") | cell_grille.equals(NA_STR))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        break;
                    }
                }
            }
            if (reste.isEmpty()) {
                vec[i] = true;
                continue;
            }

            String col = "Valeur_Catalogue";
            String colg = "Valeur_Catalogue Borne haute";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                cell_base_dbl = (Double) this.c(col)[i];
                if (cell_base_dbl != null) {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille_dbl = (Double) grille.c(colg)[r];
                        if(!(cell_grille_dbl >= cell_base_dbl | cell_grille_dbl.equals(NA_DBL))){
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille_dbl = (Double) grille.c(colg)[r];
                        if(!cell_grille_dbl.equals(NA_DBL)) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                }
            }

            col = "Valeur_Catalogue";
            colg = "Valeur_Catalogue Borne basse";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                cell_base_dbl = (Double) this.c(col)[i];
                if (cell_base_dbl != null) {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille_dbl = (Double) grille.c(colg)[r];
                        if(!(cell_grille_dbl <= cell_base_dbl | cell_grille_dbl.equals(NA_DBL))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille_dbl = (Double) grille.c(colg)[r];
                        if(!(cell_grille_dbl.equals(NA_DBL))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                }
            }

            if (reste.size() > 1) {
                vec[i] = true;
                err("error lignes multiples");
                continue;
            }
            int reste_i = reste.get(0);

            col = "Montant_Indemnité_Principale";
            Double mip = (Double) this.c(col)[i];
            if (mip.equals(NA_DBL)) {
                vec[i] = true;
                continue;
            }
            colg = "Valeur Montant_Indemnité_Principale";
            Object signe_raw = grille.c("Signe Montant_Indemnité_Principale")[reste_i];
            short signe = (short) round((Double) signe_raw);
            String montant_raw = (String) grille.c(colg)[reste_i];
            double montant = Double.parseDouble(montant_raw.replace(",",".").replaceAll("[^\\d.]", ""));
            double mip_ref = Math.round(montant * 100) / 100.0;

            vec[i] = !compa_signe(mip,mip_ref,signe);
        }

        this.err_vec_handle(vec);
    } // g
    public void controle_805() {
        Controle_en_cours = "C805";
        if (grille_gen_controle_absent()) return;
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        if (grille.df == null) {
            err("grille absente");
            return;
        }

        boolean[] vec = new boolean[nrow];
        String[] cols = {"Numéro_Police","Montant_Indemnité_Principale"};
        if (!App.check_in(cols,header)) {
            err("missing columns");
            Arrays.fill(vec,true);
            this.err_vec_handle(vec);
            return;
        } else {
            Arrays.fill(vec,false);
        }
        int dim = grille.nrow;
        ArrayList<Integer> reste_gen = new ArrayList<>(dim);
        for(int r = 0; r < dim; r++){
            reste_gen.add(r);
        }

        String[] crit = {"Critère_Identification_Bien_Garanti_4","Critère_Tarifaire_1"};
        for (int i = 0; i < nrow; i++) {

            ArrayList<Integer> reste = new ArrayList<>(reste_gen);
            Object cell_base;
            Object cell_grille;
            for (String col : crit) {

                if(find_in_arr_first_index(header, col) == -1 | find_in_arr_first_index(grille.header, col) == -1) {
                    continue;
                }
                cell_base = this.c(col)[i];

                if (cell_base != "") {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {

                        cell_grille = grille.c(find_in_arr_first_index(grille.header, col))[r];
                        if(!(cell_grille.equals(cell_base) | cell_grille.equals("{ renseigné }") | cell_grille.equals(NA_STR))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        break;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille = grille.c(col)[r];
                        if(!(cell_grille.equals("") | cell_grille.equals("{ vide }") | cell_grille.equals(NA_STR))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        break;
                    }
                }
            }
            if (reste.isEmpty()) {
                vec[i] = true;
                continue;
            }

            if (reste.size() > 1) {
                vec[i] = true;
                err("error lignes multiples");
                continue;
            }
            int reste_i = reste.get(0);

            String col = "Montant_Indemnité_Principale";
            Double mip = (Double) this.c(col)[i];
            if (mip.equals(NA_DBL)) {
                vec[i] = true;
                continue;
            }
            Double montant = (Double) grille.c(col)[reste_i];
            if (montant.equals(NA_DBL)) {
                continue;
            }
            double mip_ref = Math.round(montant * 100) / 100.0;

            vec[i] = !(mip <= mip_ref);
        }

        this.err_vec_handle(vec);
    } // g
    public void controle_804() {
        Controle_en_cours = "C804";
        if (grille_gen_controle_absent()) return;
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        if (grille.df == null) {
            err("grille absente");
            return;
        }

        boolean[] vec = new boolean[nrow];
        String[] cols = {"Numéro_Police","Statut_Technique_Sinistre","Motif_Refus"};
        if (!App.check_in(cols,header)) {
            err("missing columns");
            Arrays.fill(vec,true);
            this.err_vec_handle(vec);
            return;
        } else {
            Arrays.fill(vec,false);
        }
        int dim = grille.nrow;
        ArrayList<Integer> reste_gen = new ArrayList<>(dim);
        for(int r = 0; r < dim; r++){
            reste_gen.add(r);
        }

        String[] crit = {"Statut_Technique_Sinistre","Motif_Refus"};
        for (int i = 0; i < nrow; i++) {

            ArrayList<Integer> reste = new ArrayList<>(reste_gen);
            Object cell_base;
            Object cell_grille;
            for (String col : crit) {

                if(find_in_arr_first_index(header, col) == -1 | find_in_arr_first_index(grille.header, col) == -1) {
                    continue;
                }
                cell_base = this.c(col)[i];

                if (cell_base != "") {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {

                        cell_grille = grille.c(find_in_arr_first_index(grille.header, col))[r];
                        if(!(cell_grille.equals(cell_base) | cell_grille.equals("{ renseigné }") | cell_grille.equals(NA_STR))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        break;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille = grille.c(col)[r];
                        if(!(cell_grille.equals("") | cell_grille.equals("{ vide }") | cell_grille.equals(NA_STR))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        break;
                    }
                }
            }
            if (reste.isEmpty()) {
                vec[i] = true;
            }
        }

        this.err_vec_handle(vec);
    } // g
    public void controle_803() {
        Controle_en_cours = "C803";
        if (grille_gen_controle_absent()) return;
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        if (grille.df == null) {
            err("grille absente");
            return;
        }

        boolean[] vec = new boolean[nrow];
        String[] cols = {"Numéro_Police","Montant_Indemnité_Principale"};
        if (!App.check_in(cols,header)) {
            err("missing columns");
            Arrays.fill(vec,true);
            this.err_vec_handle(vec);
            return;
        } else {
            Arrays.fill(vec,false);
        }
        int dim = grille.nrow;
        ArrayList<Integer> reste_gen = new ArrayList<>(dim);
        for(int r = 0; r < dim; r++){
            reste_gen.add(r);
        }

        String[] crit = {"Statut_Technique_Sinistre","Statut_Sogedep","Type_Indemnisation","Critère_Identification_Bien_Remplacement_1","Critère_Identification_Bien_Remplacement_2"};
        for (int i = 0; i < nrow; i++) {

            ArrayList<Integer> reste = new ArrayList<>(reste_gen);
            Object cell_base;
            Double cell_base_dbl;
            String cell_base_str;
            Object cell_grille;
            Double cell_grille_dbl;
            for (String col : crit) {

                if(find_in_arr_first_index(header, col) == -1 | find_in_arr_first_index(grille.header, col) == -1) {
                    continue;
                }
                cell_base = this.c(col)[i];

                if (cell_base != "") {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {

                        cell_grille = grille.c(find_in_arr_first_index(grille.header, col))[r];
                        if(!(cell_grille.equals(cell_base) | cell_grille.equals("{ renseigné }") | cell_grille.equals(NA_STR))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        break;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille = grille.c(col)[r];
                        if(!(cell_grille.equals("") | cell_grille.equals("{ vide }") | cell_grille.equals(NA_STR))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        break;
                    }
                }
            }
            if (reste.isEmpty()) {
                vec[i] = true;
                continue;
            }

            String col = "Valeur_Catalogue";
            String colg = "Valeur_Catalogue Borne haute";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                cell_base_dbl = (Double) this.c(col)[i];
                if (cell_base_dbl != null) {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille_dbl = (Double) grille.c(colg)[r];
                        if(!(cell_grille_dbl >= cell_base_dbl | cell_grille_dbl.equals(NA_DBL))){
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille_dbl = (Double) grille.c(colg)[r];
                        if(!cell_grille_dbl.equals(NA_DBL)) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                }
            }

            col = "Valeur_Catalogue";
            colg = "Valeur_Catalogue Borne basse";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                cell_base_dbl = (Double) this.c(col)[i];
                if (cell_base_dbl != null) {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille_dbl = (Double) grille.c(colg)[r];
                        if(!(cell_grille_dbl <= cell_base_dbl | cell_grille_dbl.equals(NA_DBL))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille_dbl = (Double) grille.c(colg)[r];
                        if(!(cell_grille_dbl.equals(NA_DBL))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                }
            }

            if (reste.size() > 1) {
                vec[i] = true;
                err("error lignes multiples");
                continue;
            }
            int reste_i = reste.get(0);

            col = "Montant_Indemnité_Principale";
            Double mip = (Double) this.c(col)[i];
            if (mip.equals(NA_DBL)) {
                vec[i] = true;
                continue;
            }
            colg = "Valeur Montant_Indemnité_Principale";
            Object signe_raw = grille.c("Signe Montant_Indemnité_Principale")[reste_i];
            short signe = (short) round((Double) signe_raw);
            String montant_raw = (String) grille.c(colg)[reste_i];
            double montant = Double.parseDouble(montant_raw.replace(",",".").replaceAll("[^\\d.]", ""));
            double mip_ref = Math.round(montant * 100) / 100.0;

            vec[i] = !compa_signe(mip,mip_ref,signe);
        }

        this.err_vec_handle(vec);
    } // g
    public void controle_801() {
        Controle_en_cours = "C801";
        if (grille_gen_controle_absent()) return;
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        if (grille.df == null) {
            err("grille absente");
            return;
        }

        boolean[] vec = new boolean[nrow];
        String[] cols = {"Numéro_Police","Statut_Technique_Sinistre","Type_Indemnisation"};
        if (!App.check_in(cols,header) | !App.check_in(cols,grille.header)) {
            err("missing columns");
            Arrays.fill(vec,true);
            this.err_vec_handle(vec);
            return;
        } else {
            Arrays.fill(vec,false);
        }

        String col1 = "Statut_Technique_Sinistre";
        String col2 = "Type_Indemnisation";
        for (int i = 0; i < nrow; i++) {
            int id = find_in_arr_first_index(grille.c(col1),this.c(col1)[i]);
            if(id == -1) {
                vec[i] = true;
                continue;
            }
            Object gr_v = grille.c(col2)[id];
            Object base_v = this.c(col2)[i];
            if(base_v != null) {
                vec[i] = !(gr_v.equals(base_v) | gr_v.equals("{ renseigné }") | gr_v.equals(NA_STR));
            } else {
                vec[i] = !(gr_v.equals("") | gr_v.equals(NA_STR));
            }
        }
//        System.out.println(sum_boolean(vec));
        this.err_vec_handle(vec);
    } // g
    public void controle_712() {
        Controle_en_cours = "C712";
        if (grille_gen_controle_absent()) return;
        boolean[] vec = new boolean[nrow];
        String[] cols = {"Statut_Technique_Sinistre","Date_Survenance","Date_Souscription_Adhésion","Date_Evénement"};
        String[] stats = {"en cours - accepté","terminé - accepté","réglé"};
        if (!App.check_in(cols,header)) {
            err("missing columns");
            this.err_vec_handle(logvec(nrow,true));
            return;
        } else {
            Arrays.fill(vec,false);
        }

        for (int i = 0; i < nrow; i++) {
            Date dat1 = (Date) this.c("Date_Survenance")[i];
            Date dat2 = (Date) this.c("Date_Souscription_Adhésion")[i];
            Date dat3 = (Date) this.c("Date_Evénement")[i];
            if (dat1.equals(NA_DAT) | dat2.equals(NA_DAT) | dat3.equals(NA_DAT)) {
                vec[i] = true;
                continue;
            }
            boolean a = in(this.c("Statut_Technique_Sinistre")[i],stats);
            boolean b = dat1.before(dat2);
            boolean c = dat1.after(dat3);
            vec[i] = a & (b | c);
        }

        this.err_vec_handle(vec);
    }
    public void controle_711() {
        Controle_en_cours = "C711";
        if (grille_gen_controle_absent()) return;
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        if (grille.df == null) {
            err("grille absente");
            return;
        }

        boolean[] vec = logvec(nrow,false);
        int dim = grille.nrow;
        ArrayList<Integer> reste_gen = new ArrayList<>(dim);
        for(int r = 0; r < dim; r++){
            reste_gen.add(r);
        }
        String[] crit = {"Statut_Technique_Sinistre","SKU","Libellé_Garantie","Critère_Identification_Bien_Garanti_3","Critère_Identification_Bien_Garanti_6","Critère_Tarifaire_2"};
        for (int i = 0; i < this.nrow; i++) {
            ArrayList<Integer> reste = new ArrayList<>(reste_gen);
            Object cell_base;
            Double cell_base_dbl;
            String cell_base_str;
            Object cell_grille;
            Double cell_grille_dbl;
            for (String col : crit) {

                if(find_in_arr_first_index(header, col) == -1 | find_in_arr_first_index(grille.header, col) == -1) {
                    continue;
                }
                cell_base = this.c(col)[i];
                if (cell_base != "") {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {

                        cell_grille = grille.c(find_in_arr_first_index(grille.header, col))[r];
                        if(!(cell_grille.equals(cell_base) | cell_grille.equals("{ renseigné }") | cell_grille.equals(NA_STR))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        break;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille = grille.c(col)[r];
                        if(!(cell_grille.equals("") | cell_grille.equals("{ vide }") | cell_grille.equals(NA_STR))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        break;
                    }
                }
            }
            if (reste.isEmpty()) {
                vec[i] = true;
                continue;
            }
            String col = "Valeur_Catalogue";
            String colg = "Valeur_Catalogue Borne haute";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                cell_base_dbl = (Double) this.c(col)[i];
                if (cell_base_dbl != null) {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille_dbl = (Double) grille.c(colg)[r];
                        if(!(cell_grille_dbl >= cell_base_dbl | cell_grille_dbl.equals(NA_DBL))){
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille_dbl = (Double) grille.c(colg)[r];
                        if(!cell_grille_dbl.equals(NA_DBL)) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                }
            }

            col = "Valeur_Catalogue";
            colg = "Valeur_Catalogue Borne basse";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                cell_base_dbl = (Double) this.c(col)[i];
                if (!Objects.equals(cell_base_dbl, NA_DBL)) {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille_dbl = (Double) grille.c(colg)[r];
                        if(!(cell_grille_dbl <= cell_base_dbl | cell_grille_dbl.equals(NA_DBL))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille_dbl = (Double) grille.c(colg)[r];
                        if(!(cell_grille_dbl.equals(NA_DBL))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                }
            }

            col = "Code_Client";
            colg = "Retraitement Code_Client";
            if(find_in_arr_first_index(grille.header, colg) != -1) {
                if(find_in_arr_first_index(header, col) == -1) {
                    cell_base_str = "";
                } else {
                    cell_base_str = (String) this.c(col)[i];
                }
                if (!Objects.equals(cell_base_str, "")) {
                    if (cell_base_str.matches(regex_digits)) {
                        boolean[] temp = new boolean[reste.size()];
                        int ind = 0;
                        for (int r : reste) {
                            cell_grille = grille.c(colg)[r];
                            if(!(cell_grille.equals("professionnel") | cell_grille.equals(NA_STR))) {
                                temp[ind] = true;
                            }
                            ind++;
                        }
                        for (int t = temp.length-1; t >= 0; t--) {
                            if (temp[t]) {
                                reste.remove(t);
                            }
                        }
                        if (reste.isEmpty()) {
                            vec[i] = true;
                            continue;
                        }
                    } else {
                        boolean[] temp = new boolean[reste.size()];
                        int ind = 0;
                        for (int r : reste) {
                            cell_grille = grille.c(colg)[r];
                            if(!(cell_grille.equals("particulier") | cell_grille.equals(NA_STR))) {
                                temp[ind] = true;
                            }
                            ind++;
                        }
                        for (int t = temp.length-1; t >= 0; t--) {
                            if (temp[t]) {
                                reste.remove(t);
                            }
                        }
                        if (reste.isEmpty()) {
                            vec[i] = true;
                            continue;
                        }
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        if(!(grille.c(colg)[r].equals(NA_STR))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                }
            }

            col = "Critère_Identification_Bien_Garanti_1";
            colg = "Référentiel Marque";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                cell_base_str = (String) this.c(col)[i];
                switch (cell_base_str) {
                    case "apple":
                        boolean[] temp = new boolean[reste.size()];
                        int ind = 0;
                        for (int r : reste) {
                            cell_grille = grille.c(colg)[r];
                            if(!(cell_grille.equals(1) | cell_grille.equals(8) | cell_grille.equals(NA_DBL))) {
                                temp[ind] = true;
                            }
                            ind++;
                        }
                        for (int t = temp.length-1; t >= 0; t--) {
                            if (temp[t]) {
                                reste.remove(t);
                            }
                        }
                        if (reste.isEmpty()) {
                            vec[i] = true;
                            continue;
                        }
                        break;
                    case "samsung":
                        temp = new boolean[reste.size()];
                        ind = 0;
                        for (int r : reste) {
                            cell_grille = grille.c(colg)[r];
                            if(!(cell_grille.equals(2) | cell_grille.equals(4) | cell_grille.equals(8) | cell_grille.equals(NA_DBL))) {
                                temp[ind] = true;
                            }
                            ind++;
                        }
                        for (int t = temp.length-1; t >= 0; t--) {
                            if (temp[t]) {
                                reste.remove(t);
                            }
                        }
                        if (reste.isEmpty()) {
                            vec[i] = true;
                            continue;
                        }
                        break;
                    case "huawei":
                        temp = new boolean[reste.size()];
                        ind = 0;
                        for (int r : reste) {
                            cell_grille = grille.c(colg)[r];
                            if(!(cell_grille.equals(3) | cell_grille.equals(4) | cell_grille.equals(5) | cell_grille.equals(8) | cell_grille.equals(NA_DBL))) {
                                temp[ind] = true;
                            }
                            ind++;
                        }
                        for (int t = temp.length-1; t >= 0; t--) {
                            if (temp[t]) {
                                reste.remove(t);
                            }
                        }
                        if (reste.isEmpty()) {
                            vec[i] = true;
                            continue;
                        }
                        break;
                    case "dyson":
                        temp = new boolean[reste.size()];
                        ind = 0;
                        for (int r : reste) {
                            cell_grille = grille.c(colg)[r];
                            if(!(cell_grille.equals(4) | cell_grille.equals(5) | cell_grille.equals(6) | cell_grille.equals(7) | cell_grille.equals(NA_DBL))) {
                                temp[ind] = true;
                            }
                            ind++;
                        }
                        for (int t = temp.length-1; t >= 0; t--) {
                            if (temp[t]) {
                                reste.remove(t);
                            }
                        }
                        if (reste.isEmpty()) {
                            vec[i] = true;
                            continue;
                        }
                        break;
                    default:
                        temp = new boolean[reste.size()];
                        ind = 0;
                        for (int r : reste) {
                            cell_grille = grille.c(colg)[r];
                            if(!(cell_grille.equals(4) | cell_grille.equals(5) | cell_grille.equals(6) | cell_grille.equals(8) | cell_grille.equals(NA_DBL))) {
                                temp[ind] = true;
                            }
                            ind++;
                        }
                        for (int t = temp.length-1; t >= 0; t--) {
                            if (temp[t]) {
                                reste.remove(t);
                            }
                        }
                        if (reste.isEmpty()) {
                            vec[i] = true;
                            continue;
                        }
                }
            }

            col = "Date_Clôture";
            colg = "Date_Clôture borne haute";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                Date date_clot = (Date) this.c(col)[i];
                if (date_clot != NA_DAT) {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        Date date_clot_ref = (Date) grille.c(colg)[r];
                        if(!(!date_clot_ref.before(date_clot) | grille.c(colg)[r].equals(NA_DAT))){
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        if(!grille.c(colg)[r].equals(NA_DAT)) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                }
            }

            colg = "Date_Clôture borne basse";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                Date date_clot = (Date) this.c(col)[i];
                if (date_clot != NA_DAT) {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        Date date_clot_ref = (Date) grille.c(colg)[r];
                        if(!(!date_clot_ref.after(date_clot) | grille.c(colg)[r].equals(NA_DAT))){
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        if(!grille.c(colg)[r].equals(NA_DAT)) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                }
            }

            if (reste.size() > 1) {
                vec[i] = true;
                System.out.println("ERROR");
                System.out.println(Controle_en_cours);
                for (int ii : reste) {
                    System.out.println(Arrays.toString(grille.r(ii)));
                }
                System.out.println(reste);
                System.out.println(i);
                break;
            }
            int reste_i = reste.get(0);

            col = "Date_Souscription_Adhésion";
            String col1 = "Date_Survenance";
            String colg1 = "Date_Souscription_Adhésion borne basse <= Date_Survenance";
            String colg2 = "Date_Souscription_Adhésion borne haute >= Date_Survenance";

            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(header, col1) != -1 &
               find_in_arr_first_index(grille.header, colg1) != -1 & find_in_arr_first_index(grille.header, colg2) != -1) {

                LocalDate date_sous = to_Date((Date) this.c(col)[i]);
                LocalDate date_surv = to_Date((Date) this.c(col1)[i]);
                String m1 = (String) grille.c(colg1)[reste_i];
                String m2 = (String) grille.c(colg2)[reste_i];
                if (date_sous != NA_LDAT & date_surv != NA_LDAT) {

                    int months1 = NumberUtils.toInt(m1.replaceAll("\\D+",""), 0);
                    int months2 = NumberUtils.toInt(m2.replaceAll("\\D+",""), 0);
                    if ((date_sous.plusMonths(months1).isAfter(date_surv) & !m1.equals(NA_STR)) |
                        (date_sous.plusMonths(months2).isBefore(date_surv) & !m2.equals(NA_STR))) {
                        vec[i] = true;
                        continue;
                    }
                } else {
                    vec[i] = !(m1.equals(NA_STR) & m2.equals(NA_STR));
                    continue;

                }
            }
        }

        this.err_vec_handle(vec);
    } // g
    public void controle_710(DF base_adh) {
        Controle_en_cours = "C710";
        if (grille_gen_controle_absent()) return;
        boolean[] vec = logvec(this.nrow,false);
        String col = "Numéro_Adhésion";
        Integer[] m = match_sans_doublons(this.c(col), base_adh.c(col));
        for (int i = 0; i < this.nrow; i++) {
            vec[i] = m[i] == null;
        }
        this.err_vec_handle(vec);
    }
    public void controle_709() {
        Controle_en_cours = "C709";
        if (grille_gen_controle_absent()) return;

        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        if (grille.df == null) {
            err("grille absente");
            return;
        }

        String[] cols = {"Numéro_Police","Date_Survenance","Date_Souscription_Adhésion"};
        boolean[] vec;
        if (!App.check_in(cols,header)) {
            err("missing columns");
            this.err_vec_handle(logvec(nrow,true));
            return;
        } else {
            vec = logvec(nrow,true);
        }
        int dim = grille.nrow;
        ArrayList<Integer> reste_gen = new ArrayList<>(dim);
        for(int r = 0; r < dim; r++){
            reste_gen.add(r);
        }

        String[] crit = {"Statut_Technique_Sinistre","Critère_Identification_Bien_Garanti_1","Critère_Identification_Bien_Garanti_3","Critère_Identification_Bien_Garanti_4"};
        for (int i = 0; i < nrow; i++) {

            ArrayList<Integer> reste = new ArrayList<>(reste_gen);
            Object cell_base;
            Object cell_grille;
            for (String col : crit) {

                if(find_in_arr_first_index(header, col) == -1 | find_in_arr_first_index(grille.header, col) == -1) {
                    continue;
                }
                cell_base = this.c(col)[i];

                if (cell_base != "") {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {

                        cell_grille = grille.c(find_in_arr_first_index(grille.header, col))[r];
                        if(!(cell_grille.equals(cell_base) | cell_grille.equals("{ renseigné }") | cell_grille.equals(NA_STR))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        break;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille = grille.c(col)[r];
                        if(!(cell_grille.equals("") | cell_grille.equals("{ vide }") | cell_grille.equals(NA_STR))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        break;
                    }
                }
            }
            if (reste.isEmpty()) {
                vec[i] = true;
                continue;
            }

            if (reste.size() > 1) {
                vec[i] = true;
                err("error lignes multiples");
                continue;
            }
            int reste_i = reste.get(0);

            String colg = "Date_Survenance";
            String col = "Date_Souscription_Adhésion";
            String colg_aux = "Date_Survenance_Nombre";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(header, colg) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {

                String date_surv_ref = (String) grille.c(colg)[reste_i];
                if (date_surv_ref.equals(NA_STR)) {
                    continue;
                }

                LocalDate date_sous = to_Date((Date) this.c(col)[i]);
                LocalDate date_surv = to_Date((Date) this.c(colg)[i]);
                if (date_sous == NA_LDAT | date_surv == NA_LDAT) {
                    vec[i] = true;
                    continue;
                }

                long number = round((Double) grille.c(colg_aux)[reste_i]);
                vec[i] = date_sous.plusMonths(number).isAfter(date_surv);
            }

        }
        this.err_vec_handle(vec);
    } // g
    public void controle_708(DF base_adh) {
        Controle_en_cours = "C708";
        if (grille_gen_controle_absent()) return;

        boolean[] vec;
        String adh = "Numéro_Adhésion";
        String col = "Date_Survenance";
        String col1 = "Date_Prise_Effet_Résiliation";
        String col2 = "Statut_Technique_Sinistre";
        if (!App.check_in(new String[]{adh, col},this.header) | !App.check_in(new String[]{adh, col1},base_adh.header)) {
            err("missing columns");
            this.err_vec_handle(logvec(this.nrow,true));
            return;
        } else {
            vec = logvec(this.nrow,false);
        }

        Integer[] m = match_sans_doublons(this.c(adh), base_adh.c(adh));

        for (int i = 0; i < this.nrow; i++) {
            String statut = (String) this.c(col2)[i];
            if (!statut.equals("terminé - refusé avant instruction")) {
                if (m[i] == null) {
                    vec[i] = true;
                    continue;
                }

                Date surv = (Date) this.c(col)[i];
                if(surv.equals(NA_DAT)) {
                    vec[i] = true;
                    continue;
                }

                Date resil = (Date) base_adh.c(col1)[i];
                if(resil.equals(NA_DAT)) {
                    continue;
                }

                vec[i] = surv.after(resil);

            }
        }
        this.err_vec_handle(vec);
    }
    public void controle_707() {
        Controle_en_cours = "C707";
        if (grille_gen_controle_absent()) return;

        boolean[] vec;
        String col = "Statut_Technique_Sinistre";
        String col1 = "Date_Survenance";
        String[] cols = {col,col1};
        String[] statut_ref = {"en cours - accepté","terminé - accepté","réglé"};
        if (!App.check_in(cols,this.header)) {
            err("missing columns");
            this.err_vec_handle(logvec(nrow,true));
            return;
        } else {
            vec = logvec(this.nrow,false);
        }
        for (int i = 0; i < this.nrow; i++) {
            String statut = (String) this.c(col)[i];
            Date surv = (Date) this.c(col1)[i];
            vec[i] = in(statut,statut_ref) & surv.equals(NA_DAT);
        }
        this.err_vec_handle(vec);
    }
    public void controle_706() {
        Controle_en_cours = "C706";
        if (grille_gen_controle_absent()) return;

        boolean[] vec;
        String col = "Statut_Technique_Sinistre";
        String col1 = "Date_Survenance";
        String col2 = "Date_Activation";
        String[] cols = {col,col1,col2};
        String[] statut_ref = {"en cours - accepté","terminé - accepté","réglé"};
        if (!App.check_in(cols,this.header)) {
            err("missing columns");
            this.err_vec_handle(logvec(nrow,true));
            return;
        } else {
            vec = logvec(this.nrow,false);
        }
        for (int i = 0; i < this.nrow; i++) {
            String statut = (String) this.c(col)[i];
            Date surv = (Date) this.c(col1)[i];
            Date activ = (Date) this.c(col2)[i];
            if (!(surv.equals(NA_DAT) | activ.equals(NA_DAT))) {
                vec[i] = in(statut,statut_ref) & surv.before(activ);
            } else {
                vec[i] = in(statut,statut_ref);
            }
        }
        this.err_vec_handle(vec);
    }
    public void controle_705() {
        Controle_en_cours = "C705";
        if (grille_gen_controle_absent()) return;

        boolean[] vec;
        String col = "Statut_Technique_Sinistre";
        String col1 = "Date_Survenance";
        String col2 = "Date_Souscription_Adhésion";
        String col3 = "Date_Achat_Bien_Garanti";
        String[] cols = {col,col1,col2,col3};
        String[] statut_ref = {"en cours - accepté","terminé - accepté","réglé"};
        if (!App.check_in(cols,this.header)) {
            err("missing columns");
            this.err_vec_handle(logvec(nrow,true));
            return;
        } else {
            vec = logvec(this.nrow,false);
        }
        for (int i = 0; i < this.nrow; i++) {
            String statut = (String) this.c(col)[i];
            Date surv = (Date) this.c(col1)[i];
            Date sous = (Date) this.c(col2)[i];
            Date achat = (Date) this.c(col3)[i];
            if (!(surv.equals(NA_DAT) | sous.equals(NA_DAT) | achat.equals(NA_DAT))) {
                vec[i] = in(statut,statut_ref) & (surv.before(sous) | surv.before(achat));
            } else {
                vec[i] = in(statut,statut_ref);
            }
        }
        this.err_vec_handle(vec);
    }
    public void controle_704() {
        Controle_en_cours = "C704";
        if (grille_gen_controle_absent()) return;

        boolean[] vec;
        String col = "Statut_Technique_Sinistre";
        String col1 = "Date_Déclaration";
        String col2 = "Date_Survenance";
        String[] cols = {col,col1,col2};
        String[] statut_ref = {"en cours - accepté","terminé - accepté","réglé"};
        if (!App.check_in(cols,this.header)) {
            err("missing columns");
            this.err_vec_handle(logvec(nrow,true));
            return;
        } else {
            vec = logvec(this.nrow,false);
        }
        for (int i = 0; i < this.nrow; i++) {
            String statut = (String) this.c(col)[i];
            LocalDate decla = to_Date((Date) this.c(col1)[i]);
            LocalDate surv = to_Date((Date) this.c(col2)[i]);
            if (!(decla.equals(NA_LDAT) | surv.equals(NA_LDAT))) {
                vec[i] = in(statut,statut_ref) & (decla.isAfter(surv.plusDays(5)));
            } else {
                vec[i] = in(statut,statut_ref);
            }
        }
        this.err_vec_handle(vec);
    }
    public void controle_703() {
        Controle_en_cours = "C703";
        if (grille_gen_controle_absent()) return;

        boolean[] vec;
        String col = "Statut_Technique_Sinistre";
        String col1 = "Date_Déclaration";
        String col2 = "Date_Activation";
        String[] cols = {col,col1,col2};
        String[] statut_ref = {"en cours - accepté","terminé - accepté","réglé"};
        if (!App.check_in(cols,this.header)) {
            err("missing columns");
            this.err_vec_handle(logvec(nrow,true));
            return;
        } else {
            vec = logvec(this.nrow,false);
        }
        for (int i = 0; i < this.nrow; i++) {
            String statut = (String) this.c(col)[i];
            Date decla = (Date) this.c(col1)[i];
            Date activ = (Date) this.c(col2)[i];
            if (!(decla.equals(NA_DAT) | activ.equals(NA_DAT))) {
                vec[i] = in(statut,statut_ref) & (decla.before(activ));
            } else {
                vec[i] = in(statut,statut_ref);
            }
        }
        this.err_vec_handle(vec);
    }
    public void controle_702() {
        Controle_en_cours = "C702";
        if (grille_gen_controle_absent()) return;

        boolean[] vec;
        String col = "Statut_Technique_Sinistre";
        String col1 = "Date_Déclaration";
        String col2 = "Date_Souscription_Adhésion";
        String col3 = "Date_Achat_Bien_Garanti";
        String[] cols = {col,col1,col2,col3};
        String[] statut_ref = {"en cours - accepté","terminé - accepté","réglé"};
        if (!App.check_in(cols,this.header)) {
            err("missing columns");
            this.err_vec_handle(logvec(nrow,true));
            return;
        } else {
            vec = logvec(this.nrow,false);
        }
        for (int i = 0; i < this.nrow; i++) {
            String statut = (String) this.c(col)[i];
            Date decla = (Date) this.c(col1)[i];
            Date sous = (Date) this.c(col2)[i];
            Date achat = (Date) this.c(col3)[i];
            if (!(decla.equals(NA_DAT) | sous.equals(NA_DAT) | achat.equals(NA_DAT))) {
                vec[i] = in(statut,statut_ref) & (decla.before(sous) | decla.before(achat));
            } else {
                vec[i] = in(statut,statut_ref);
            }
        }
        this.err_vec_handle(vec);
    }
    public void controle_701() {
        Controle_en_cours = "C701";
        if (grille_gen_controle_absent()) return;

        boolean[] vec;
        String col = "Statut_Technique_Sinistre";
        String col1 = "Date_Déclaration";
        String col2 = "Date_Survenance";
        String[] cols = {col,col1,col2};
        String[] statut_ref = {"en cours - accepté","terminé - accepté","réglé"};
        if (!App.check_in(cols,this.header)) {
            err("missing columns");
            this.err_vec_handle(logvec(nrow,true));
            return;
        } else {
            vec = logvec(this.nrow,false);
        }
        for (int i = 0; i < this.nrow; i++) {
            String statut = (String) this.c(col)[i];
            Date decla = (Date) this.c(col1)[i];
            Date surv = (Date) this.c(col2)[i];
            if (!(decla.equals(NA_DAT) | surv.equals(NA_DAT))) {
                vec[i] = in(statut,statut_ref) & decla.before(surv);
            } else {
                vec[i] = in(statut,statut_ref);
            }
        }
        this.err_vec_handle(vec);
    }
    public void controle_608() {
        Controle_en_cours = "C608";
        if (grille_gen_controle_absent()) return;

        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);

        this.err_vec_handle(simple_grille(grille));
    } // g
    public void controle_607() {
        Controle_en_cours = "C607";
        if (grille_gen_controle_absent()) return;

        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        this.err_vec_handle(simple_grille(grille));
    } // g
    public void controle_503_519(DF base_adh) {
        String[] controles = new String[]{"C503","C504","C505","C506","C507","C508",
                "C509","C510","C511","C512","C513","C514","C515","C516","C518","C519"};
        String[] cols = new String[]{"Numéro_Extension","Date_Souscription_Adhésion","Date_Achat_Bien_Garanti",
                "Critère_Identification_Bien_Garanti_1","Critère_Identification_Bien_Garanti_2","Critère_Identification_Bien_Garanti_3",
                "Critère_Identification_Bien_Garanti_4","Critère_Identification_Bien_Garanti_5","Critère_Identification_Bien_Garanti_6",
                "Critère_Tarifaire_1","Critère_Tarifaire_2","Critère_Tarifaire_3","Critère_Tarifaire_4","Valeur_Achat","SKU","Qualité_Client"};
        for (int i = 0; i < controles.length; i++) {
            Controle_en_cours = controles[i];

            if (grille_gen_controle_absent()) continue;

            this.err_vec_handle(this.matcher_adh(base_adh,cols[i]));
        }
    }
    public void controle_517() {
        Controle_en_cours = "C517";
        if (grille_gen_controle_absent()) return;

        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        this.err_vec_handle(simple_grille(grille));
    } // g
    public void controle_502() {
        Controle_en_cours = "C502";
        if (grille_gen_controle_absent()) return;

        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        this.err_vec_handle(simple_grille(grille));
    } // g
    public void controle_501() {
        Controle_en_cours = "C501";
        if (grille_gen_controle_absent()) return;

        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        this.err_vec_handle(simple_grille(grille));
    } // g
    public void controle_401() {
        Controle_en_cours = "C401";
        if (grille_gen_controle_absent()) return;

        int dim = this.nrow;
        String col = "Date_Prise_Effet_Résiliation";
        String col1 = "Date_Souscription_Adhésion";
        boolean[] vec = new boolean[dim];
        String[] cols = {"Date_Prise_Effet_Résiliation","Date_Souscription_Adhésion"};
        if (!App.check_in(cols,header)) {
            err("missing columns");
            Arrays.fill(vec,true);
            this.err_vec_handle(vec);
            return;
        } else {
            Arrays.fill(vec,false);
        }
        for (int i = 0; i < dim; i++) {
            Date resil = (Date) this.c(col)[i];
            Date sous = (Date) this.c(col1)[i];
            vec[i] = !resil.equals(NA_DAT) & resil.before(sous);
        }
        this.err_vec_handle(vec);
    } // controle adhé
    public void controle_309() {
        Controle_en_cours = "C309";
        if (grille_gen_controle_absent()) return;

        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        if (grille.df == null) {
            err("grille absente");
            return;
        }

        boolean[] vec = new boolean[nrow];
        String[] cols = {"Numéro_Police","Critère_Tarifaire_1","Valeur_Achat"};
        if (!App.check_in(cols,header)) {
            err("missing columns");
            Arrays.fill(vec,true);
            this.err_vec_handle(vec);
            return;
        } else {
            Arrays.fill(vec,false);
        }
        int dim = grille.nrow;
        ArrayList<Integer> reste_gen = new ArrayList<>(dim);
        for(int r = 0; r < dim; r++){
            reste_gen.add(r);
        }

        String[] crit = {"Critère_Tarifaire_1","SKU"};
        for (int i = 0; i < nrow; i++) {

            ArrayList<Integer> reste = new ArrayList<>(reste_gen);
            Object cell_base;
            Object cell_grille;
            for (String col : crit) {

                if(find_in_arr_first_index(header, col) == -1 | find_in_arr_first_index(grille.header, col) == -1) {
                    continue;
                }
                cell_base = this.c(col)[i];

                if (cell_base != "") {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {

                        cell_grille = grille.c(find_in_arr_first_index(grille.header, col))[r];
                        if(!(cell_grille.equals(cell_base) | cell_grille.equals("{ renseigné }") | cell_grille.equals(NA_STR))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        break;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille = grille.c(col)[r];
                        if(!(cell_grille.equals("") | cell_grille.equals("{ vide }") | cell_grille.equals(NA_STR))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        break;
                    }
                }
            }
            if (reste.isEmpty()) {
                vec[i] = true;
                continue;
            }

            if (reste.size() > 1) {
                vec[i] = true;
                err("error lignes multiples");
                continue;
            }
            int reste_i = reste.get(0);

            Double val = (Double) this.c("Valeur_Achat")[i];
            Double borne_haute = (Double) grille.c("Valeur_Achat Borne haute")[reste_i];
            Double borne_basse = (Double) grille.c("Valeur_Achat Borne basse")[reste_i];

            vec[i] = !(borne_basse <= val & val <= borne_haute);
        }

        this.err_vec_handle(vec);
    } // g
    public void controle_308() {
        Controle_en_cours = "C308";
        if (grille_gen_controle_absent()) return;

        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        this.err_vec_handle(simple_grille(grille));
    } // g
    public void controle_305() {
        Controle_en_cours = "C305";
        if (grille_gen_controle_absent()) return;

        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        if (grille.df == null) {
            err("grille absente");
            return;
        }

        Double x_raw = (Double) grille.c("Contrôle")[0];
        long x = round(x_raw);
        String col1 = "Date_Activation";
        String col2 = "Date_Souscription_Adhésion";
        String[] cols = {"Numéro_Police",col1,col2};
        boolean[] vec;
        if (!App.check_in(cols,this.header)) {
            err("missing columns");
            this.err_vec_handle(logvec(nrow,true));
            return;
        } else {
            vec = logvec(this.nrow,false);
        }
        for (int i = 0; i < this.nrow; i++) {
            LocalDate activ = to_Date((Date) this.c(col1)[i]);
            if (activ != NA_LDAT) {
                LocalDate sous = to_Date((Date) this.c(col2)[i]);
                if (sous != NA_LDAT) {
                    vec[i] = activ.isAfter(sous.plusDays(x));
                } else {
                    vec[i] = true;
                }
            } else {
                vec[i] = false;
            }

        }
        this.err_vec_handle(vec);
    } // g
    public void controle_304() {
        Controle_en_cours = "C304";
        if (grille_gen_controle_absent()) return;

        String col1 = "Date_Activation";
        String col2 = "Date_Souscription_Adhésion";
        String col3 = "Date_Achat_Bien_Garanti";
        String[] cols = {col1,col2,col3};
        boolean[] vec;
        if (!App.check_in(cols,this.header)) {
            err("missing columns");
            this.err_vec_handle(logvec(nrow,true));
            return;
        } else {
            vec = logvec(this.nrow,false);
        }
        for (int i = 0; i < this.nrow; i++) {
            Date activ = (Date) this.c(col1)[i];
            Date sous = (Date) this.c(col2)[i];
            Date achat = (Date) this.c(col3)[i];
            if (!activ.equals(NA_DAT)) {
                if (sous.equals(NA_DAT) | achat.equals(NA_DAT)) {
                    vec[i] = true;
                } else {
                    vec[i] = activ.before(sous) | activ.before(achat);
                }
            }
        }
        this.err_vec_handle(vec);
    }
    public void controle_303() {
        Controle_en_cours = "C303";
        if (grille_gen_controle_absent()) return;

        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        if (grille.df == null) {
            err("grille absente");
            return;
        }

        String col1 = "Date_Achat_Bien_Garanti";
        String col2 = "Date_Souscription_Adhésion";
        String col3 = "Critère_Identification_Bien_Garanti_4";
        String[] cols = {"Numéro_Police",col1,col2,col3};
        boolean[] vec;
        if (!App.check_in(cols,this.header)) {
            err("missing columns");
            this.err_vec_handle(logvec(nrow,true));
            return;
        } else {
            vec = logvec(this.nrow,false);
        }
        for (int i = 0; i < this.nrow; i++) {
            if(this.c("Numéro_Police")[i] != Police_en_cours){
                vec[i] = true;
                continue;
            }

            String cell_base = (String) this.c(col3)[i];
            ArrayList<Integer> ind = new ArrayList<>();
            for (int j = 0; j < grille.nrow; j++) {
                if (grille.c(col3)[j].equals(cell_base) | grille.c(col3)[j].equals(NA_STR)) {
                    ind.add(j);
                }
            }
            if (ind.size() == 0) {
                vec[i] = true;
                continue;
            }
            if (ind.size() > 1) {
                err("grille logic");
                vec[i] = true;
                continue;
            }

            Double x_raw = (Double) grille.c("Contrôle")[ind.get(0)];
            long x = round(x_raw);
            LocalDate achat = to_Date((Date) this.c(col1)[i]);
            LocalDate sous = to_Date((Date) this.c(col2)[i]);
            if (achat == NA_LDAT | sous == NA_LDAT) {
                vec[i] = true;
            } else {
                vec[i] = sous.isAfter(achat.plusDays(x));
            }

        }
        this.err_vec_handle(vec);
    } // g
    public void controle_302() {
        Controle_en_cours = "C302";
        if (grille_gen_controle_absent()) return;

        String col1 = "Date_Souscription_Adhésion";
        String col2 = "Date_Achat_Bien_Garanti";
        String[] cols = {col1,col2};
        boolean[] vec;
        if (!App.check_in(cols,this.header)) {
            err("missing columns");
            this.err_vec_handle(logvec(nrow,true));
            return;
        } else {
            vec = logvec(this.nrow,false);
        }
        for (int i = 0; i < this.nrow; i++) {
            Date sous = (Date) this.c(col1)[i];
            Date achat = (Date) this.c(col2)[i];
            if (sous == NA_DAT | achat == NA_DAT) {
                vec[i] = true;
            } else  {
                vec[i] = sous.before(achat);
            }
        }
        this.err_vec_handle(vec);
    }
    public void controle_301() {
        Controle_en_cours = "C301";
        if (grille_gen_controle_absent()) return;

        String col1 = "Date_Souscription_Adhésion";

        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        if (grille.df == null) {
            err("grille absente");
            return;
        }

        Date x_raw = (Date) grille.c(col1)[0];

        String[] cols = {"Numéro_Police",col1};
        boolean[] vec;
        if (!App.check_in(cols,this.header)) {
            err("missing columns");
            this.err_vec_handle(logvec(nrow,true));
            return;
        } else {
            vec = logvec(this.nrow,false);
        }
        for (int i = 0; i < this.nrow; i++) {
            Date cell_base = (Date) this.c(col1)[i];
            if (cell_base == NA_DAT) {
                vec[i] = true;
            } else {
                vec[i] = cell_base.before(x_raw);
            }
        }
        this.err_vec_handle(vec);
    } // g
    public void controle_205() {
        Controle_en_cours = "C205";
        if (grille_gen_controle_absent()) return;

        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        this.err_vec_handle(simple_grille(grille));
    } // g
    public void controle_202_223() {
        String[] controles = new String[]{"C202","C203","C204","C206",
        "C207","C208","C209","C210","C211","C212","C213","C214","C215",
        "C216","C217","C218","C219","C220","C221","C222","C223"};
        for (String s : controles) {
            Controle_en_cours = s;
            if (grille_gen_controle_absent()) continue; // pochemu ne prohodit
//            grilles_G.forEach((key, value) -> System.out.println(key + " " + value));
            DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
            if (grille.df == null) continue;
            this.err_vec_handle(this.one_dim_grille(grille));
        }
    }
    public void controle_201() {
        Controle_en_cours = "C201";
        if (grille_gen_controle_absent()) return;

        String col = "Numéro_Police";
        boolean[] vec;
        if (!App.check_in(col,this.header)) {
            this.err_vec_handle(logvec(this.nrow,true));
            return;
        } else {
            vec = logvec(this.nrow,false);
        }
        for (int i = 0; i < this.nrow; i++) {
            vec[i] = this.c(col)[i].equals(Police_en_cours);
        }
        this.err_vec_handle(vec);
    }
    public void controle_107() {
        Controle_en_cours = "C107";
        if (grille_gen_controle_absent()) return;

        String col = "Critère_Identification_Bien_Garanti_5";
        boolean[] vec;
        if (!App.check_in(col,this.header)) {
            err("missing columns");
            this.err_vec_handle(logvec(nrow,true));
            return;
        } else {
            vec = doublons_by_col(col);
        }
        this.err_vec_handle(vec);
    }
    public void controle_106() {
        Controle_en_cours = "C106";
        if (grille_gen_controle_absent()) return;

        String col = "Numéro_Extension";
        boolean[] vec;
        if (!App.check_in(col,this.header)) {
            err("missing columns");
            this.err_vec_handle(logvec(nrow,true));
            return;
        } else {
            vec = doublons_by_col(col);
        }
        this.err_vec_handle(vec);
    }
    public void controle_105() {
        Controle_en_cours = "C105";
        if (grille_gen_controle_absent()) return;

        String col = "Numéro_Adhésion";
        boolean[] vec;
        if (!App.check_in(col,this.header)) {
            err("missing columns");
            this.err_vec_handle(logvec(nrow,true));
            return;
        } else {
            vec = doublons_by_col(col);
        }
        this.err_vec_handle(vec);
    }
    public void controle_104() {
        Controle_en_cours = "C104";
        if (grille_gen_controle_absent()) return;

        String col = "Numéro_Dossier";
        boolean[] vec;
        if (!App.check_in(col,this.header)) {
            err("missing columns");
            this.err_vec_handle(logvec(nrow,true));
            return;
        } else {
            vec = doublons_by_col(col);
        }
        this.err_vec_handle(vec);
    }
    public void controle_103() {
        Controle_en_cours = "C103";
        if (grille_gen_controle_absent()) return;

        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        if (grille.df == null) {
            err("grille absente");
            return;
        }

        String col3 = "Statut_Technique_Sinistre";
        String col4 = "Critère_Identification_Bien_Garanti_5";
        String colg = "Nombre Critère_Bien_Garanti_5";
        String[] cols = {"Numéro_Police",col3,col4};
        String[] crit = {"Statut_Technique_Sinistre", "Libellé_Garantie","SKU","Canal_Adhésion"};

        boolean[] vec;
        if (!App.check_in(cols,this.header)) {
            err("missing columns");
            this.err_vec_handle(logvec(nrow,true));
            return;
        } else {
            vec = logvec(this.nrow,false);
        }
        int dim = grille.nrow;
        ArrayList<Integer> reste_gen = new ArrayList<>(dim);
        for(int r = 0; r < dim; r++){
            reste_gen.add(r);
        }
        HashMap<String, Integer> map = new HashMap<>();
        for (int i = 0; i < this.nrow; i++) {
            String cibg = (String) this.c(col4)[i];
            if (map.put(cibg,1) != null) {
                map.merge(cibg, 1, Integer::sum);
            }
        }

        for (int i = 0; i < this.nrow; i++) {
            ArrayList<Integer> reste = new ArrayList<>(reste_gen);
            Object cell_base;
            Object cell_grille;
            for (String col : crit) {

                if(find_in_arr_first_index(header, col) == -1 | find_in_arr_first_index(grille.header, col) == -1) {
                    continue;
                }
                cell_base = this.c(col)[i];

                if (cell_base != "") {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {

                        cell_grille = grille.c(find_in_arr_first_index(grille.header, col))[r];
                        if(!(cell_grille.equals(cell_base) | cell_grille.equals("{ renseigné }") | cell_grille.equals(NA_STR))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        break;
                    }
                } else {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        cell_grille = grille.c(col)[r];
                        if(!(cell_grille.equals("") | cell_grille.equals("{ vide }") | cell_grille.equals(NA_STR))) {
                            temp[ind] = true;
                        }
                        ind++;
                    }
                    for (int t = temp.length-1; t >= 0; t--) {
                        if (temp[t]) {
                            reste.remove(t);
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        break;
                    }
                }
            }
            if (reste.isEmpty()) {
                vec[i] = true;
                continue;
            }

            if (reste.size() > 1) {
                vec[i] = true;
                for (int rr : reste) {
                    System.out.println(Arrays.toString(grille.r(rr)));
                }
                System.out.println(i);
                err("error lignes multiples");
                continue;
            }
            int reste_i = reste.get(0);

            Double cell_grille_dbl = (Double) grille.c(colg)[reste_i];
            String cell_base_str = (String) this.c(col4)[i];
            if (cell_grille_dbl.equals(NA_DBL)) {
                vec[i] = false;
            } else {
                vec[i] = map.get(cell_base_str) > cell_grille_dbl;
            }
        }
        this.err_vec_handle(vec);
    } // g
    public void controle_102() {
        Controle_en_cours = "C102";
        if (grille_gen_controle_absent()) return;

        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        if (grille.df == null) {
            err("grille absente");
            return;
        }

        String col1 = "Statut_Technique_Sinistre";
        String col2 = "Numéro_Extension";
        String colg = "Nombre Numéro_Extension";
        String[] cols = {"Numéro_Police",col1,col2};
        boolean[] vec;
        if (!App.check_in(cols,this.header)) {
            err("missing columns");
            this.err_vec_handle(logvec(nrow,true));
            return;
        } else {
            vec = logvec(this.nrow,false);
        }

        HashMap<String, Integer> map = new HashMap<>();
        for (int i = 0; i < this.nrow; i++) {
            String cibg = (String) this.c(col2)[i];
            if (map.put(cibg,1) != null) {
                map.merge(cibg, 1, Integer::sum);
            }
        }

        for (int i = 0; i < this.nrow; i++) {
            int ind = find_in_arr_first_index(grille.c(col1), this.c(col1)[i]);
            if (ind == -1) {
                vec[i] = true;
                continue;
            }
            Double cell_grille = (Double) grille.c(colg)[ind];

            String cell_base = (String) this.c(col2)[i];
            if (cell_grille.equals(NA_DBL)) {
                vec[i] = false;
            } else {
                vec[i] = map.get(cell_base) > cell_grille;
            }
        }
        this.err_vec_handle(vec);
    } // g
    public void controle_101() {
        Controle_en_cours = "C101";
        if (grille_gen_controle_absent()) return;

        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        if (grille.df == null) {
            err("grille absente");
            return;
        }

        String col1 = "Statut_Technique_Sinistre";
        String col2 = "Numéro_Adhésion";
        String colg = "Nombre Numéro_Adhésion";
        String[] cols = {"Numéro_Police",col1,col2};
        boolean[] vec;
        if (!App.check_in(cols,this.header)) {
            err("missing columns");
            this.err_vec_handle(logvec(nrow,true));
            return;
        } else {
            vec = logvec(this.nrow,false);
        }

        HashMap<String, Integer> map = new HashMap<>();
        for (int i = 0; i < this.nrow; i++) {
            String cibg = (String) this.c(col2)[i];
            if (map.put(cibg,1) != null) {
                map.merge(cibg, 1, Integer::sum);
            }
        }

        for (int i = 0; i < this.nrow; i++) {
            int ind = find_in_arr_first_index(grille.c(col1), this.c(col1)[i]);
            if (ind == -1) {
                vec[i] = true;
                continue;
            }
            Double cell_grille = (Double) grille.c(colg)[ind];
            String cell_base = (String) this.c(col2)[i];
            if (cell_grille.equals(NA_DBL)) {
                vec[i] = false;
            } else {
                vec[i] = map.get(cell_base) > cell_grille;
            }
        }
        this.err_vec_handle(vec);
    } // g
    public void controle_002_035() {
        String[] controles = new String[]{"C002","C003","C004","C005","C006","C007","C008","C009","C010",
                "C011","C012","C014","C015","C016","C017","C018","C019","C020","C021","C022",
                "C023","C024","C025","C026","C027","C028","C029","C030","C031","C032","C033","C034","C035"};
        String[] cols = new String[]{"Libellé_Distributeur","Libellé_Produit","Numéro_Adhésion","Numéro_Extension","Date_Souscription_Adhésion",
                "Statut_Adhésion","Date_Achat_Bien_Garanti","Critère_Identification_Bien_Garanti_1","Critère_Identification_Bien_Garanti_2",
                "Critère_Identification_Bien_Garanti_3","Critère_Identification_Bien_Garanti_4","Critère_Identification_Bien_Garanti_6",
                "Critère_Tarifaire_1","Critère_Tarifaire_2","Cotisation_TTC","Canal_Adhésion","Nom_Magasin","Période_Bordereau_Cotisation",
                "Numéro_Dossier","Libellé_Garantie","Date_Déclaration","Statut_Technique_Sinistre","Montant_Indemnité_Principale",
                "Montant_Frais_Annexe","Montant_Reprise","Montant_Total_Règlement","Critère_Tarifaire_3","Critère_Tarifaire_4",
                "Valeur_Achat","Date_Dernier_Acte","SKU","Date_Evénement","Qualité_Client"};
            for (int i = 0; i < controles.length; i++) {
                Controle_en_cours = controles[i];
                if (grille_gen_controle_absent()) return;

                if (App.check_in(cols[i], header)) {
                    boolean[] vec = this.check_vides(cols[i]);
                    this.err_vec_handle(vec);
                } else {
                    err("cvides colonne manque");
                    this.err_vec_handle(logvec(this.nrow,true));
                }
            }
    }
    public void fic_hors_la_liste_controle_K0(DF map_fic) {
        Controle_en_cours = "K0";
        if (grille_gen_controle_absent()) return;

        int ind = find_in_arr_first_index(map_fic.c(0), "FIC_Clé");
        String fic_cle = (String) map_fic.c(mapping_fic_col)[ind];
        fic_cle = fic_cle.substring(fic_cle.indexOf(':')+1);
        //.replaceAll("\\s+","")
        String[] cols = fic_cle.split("&");
        for (int i = 0; i < cols.length; i++) {
            cols[i] = cols[i].trim();
        }
        DF map = mapping_filtre_fic();
        for (int i = 0; i < cols.length; i++) {
            if(App.check_in(cols[i],map.c(1))) {
                ind = find_in_arr_first_index(map.c(1),cols[i]);
                cols[i] = (String) map.c(0)[ind];
            }
        }
//        System.out.println(Arrays.toString(cols));
//        System.out.println(Arrays.toString(this.header));
        if (!App.check_in(cols,this.header)) {
            err("missing columns");
            this.err_vec_handle(logvec(this.nrow,true));
            return;
        }

        String[] values = new String[this.nrow];
        Arrays.fill(values,"");
        for(int i = 0; i < this.nrow; i++) {
            for (String col : cols) {
                values[i] = values[i] + this.c(col)[i];
            }
        }
        this.err_vec_handle(doublons(values));
    }
    public void fic_controle_K1(DF base_sin) {
        Controle_en_cours = "K1";
        if (grille_gen_controle_absent()) return;

        String col = "Numéro_Dossier";
        boolean[] vec = logvec(nrow,true);
        if (!App.check_in(col,this.header) | !App.check_in(col,base_sin.header)) {
            err("missing columns");
            this.err_vec_handle(vec);
            return;
        }

        Integer[] match = match_sans_doublons(this.c(col), base_sin.c(col));

        for (int i = 0; i < this.nrow; i++) {
            if (match[i] != -1) {
                vec[i] = this.c(col)[i].equals(base_sin.c(col)[match[i]]);
            }
        }
        err_vec_handle(vec);
    }
    public void fic_controle_K2(DF base_sin) {
        Controle_en_cours = "K2";
        if (grille_gen_controle_absent()) return;

        String num_dossier = "Numéro_Dossier";
        String col_fic = "FIC_Montant_reglement";
        String col_sin = "Montant_Total_Règlement";
        String[] cols_sin = {num_dossier,col_sin};
        String[] cols_fic = {num_dossier,col_fic};
        boolean[] vec = logvec(nrow,true);
        if (!App.check_in(cols_fic,this.header) | !App.check_in(cols_sin,base_sin.header)) {
            err("missing columns");
            this.err_vec_handle(vec);
            return;
        }

        Integer[] match = match_sans_doublons(this.c(num_dossier), base_sin.c(num_dossier));

        HashMap<String, Double> freqs = new HashMap<>();
        String id;
        Double value;
        for (int i = 0; i < this.nrow; i++) {
            id = (String) this.c(num_dossier)[i];
            value = (Double) this.c(col_fic)[i];
            if(freqs.containsKey(id)) {
                freqs.put(id,freqs.get(id) + value);
            } else {
                freqs.put(id,value);
            }
        }

        for (int i = 0; i < this.nrow; i++) {
            if (match[i] != -1) {
                vec[i] = freqs.get( (String) this.c(num_dossier)[i] ).equals(base_sin.c(col_sin)[match[i]]);
            }
        }
        err_vec_handle(vec);
    }
    public void fic_controle_K3(DF base_sin) {
        Controle_en_cours = "K3";
        if (grille_gen_controle_absent()) return;

        String col = "Date_Souscription_Adhésion";
        err_vec_handle(this.matcher_fic(col,base_sin));
    }
    public void fic_controle_K4(DF base_sin) {
        Controle_en_cours = "K4";
        if (grille_gen_controle_absent()) return;

        String col = "Date_Déclaration";
        err_vec_handle(this.matcher_fic(col,base_sin));
    }
    public void fic_controle_K5(DF base_sin) {
        Controle_en_cours = "K5";
        if (grille_gen_controle_absent()) return;

        String col = "Date_Survenance";
        err_vec_handle(this.matcher_fic(col,base_sin));
    }
    public void fic_controle_K6(DF base_sin) {
        Controle_en_cours = "K6";
        if (grille_gen_controle_absent()) return;

        String col = "Libellé_Garantie";
        err_vec_handle(this.matcher_fic(col,base_sin));
    }
    public void fic_controle_K7(DF base_sin) {
        Controle_en_cours = "K7";
        if (grille_gen_controle_absent()) return;

        String col = "Numéro_Adhésion";
        err_vec_handle(this.matcher_fic(col,base_sin));
    }
    public void fic_controle_K8(DF base_sin) {
        Controle_en_cours = "K8";
        if (grille_gen_controle_absent()) return;
        String col = "Valeur_Achat";
        err_vec_handle(this.matcher_fic(col,base_sin));
    }
    public void fic_controle_K9(DF base_sin) {
        Controle_en_cours = "K9";
        if (grille_gen_controle_absent()) return;

        String col = "SKU";
        err_vec_handle(this.matcher_fic(col,base_sin));
    }
    public boolean[] matcher_fic(String col, DF base_sin) {
        String num_dossier = "Numéro_Dossier";

        String[] cols = {num_dossier,col};
        boolean[] vec = logvec(nrow,true);
        if (!App.check_in(cols,this.header) | !App.check_in(cols,base_sin.header)) {
            err("missing columns");
            return vec;
        }

        Integer[] match = match_sans_doublons(this.c(num_dossier), base_sin.c(num_dossier));

        for (int i = 0; i < this.nrow; i++) {
            if (match[i] != -1) {
                vec[i] = this.c(col)[i].equals(base_sin.c(col)[match[i]]);
            }
        }
        return vec;
    }
    public boolean[] check_vides (String col) {
        boolean[] vec = logvec(this.nrow,false);
        switch (this.coltypes[find_in_arr_first_index(this.header,col)]) {
            case STR:
                for (int i = 0; i < this.nrow; i++) {
                    vec[i] = this.c(col)[i] == "";
                }
                break;
            case DBL:
                for (int i = 0; i < this.nrow; i++) {
                    vec[i] = this.c(col)[i] == NA_DBL;
                }
                break;
            case DAT:
                for (int i = 0; i < this.nrow; i++) {
                    vec[i] = this.c(col)[i] == NA_DAT;
                }
                break;
            default:
                err("check_vides type inexistant");
            }
        return vec;
    }
    public boolean[] doublons_by_col(String col) {
        boolean[] vec = logvec(this.nrow,false);
        HashMap<String, Integer> map = new HashMap<>();
        for (int i = 0; i < this.nrow; i++) {
            if (map.put((String) this.c(col)[i],i) != null) {
                vec[i] = true;
            }
        }

        map = new HashMap<>();
        for (int i = this.nrow; i > 0; i--) {
            if (map.put((String) this.c(col)[i-1],i) != null) {
                vec[i-1] = true;
            }
        }
        return vec;
    }
    public boolean[] doublons(String[] col) {
        boolean[] vec = logvec(this.nrow,false);
        HashMap<String, Integer> map = new HashMap<>();
        for (int i = 0; i < this.nrow; i++) {
            if (map.put(col[i],i) != null) {
                vec[i] = true;
            }
        }

        map = new HashMap<>();
        for (int i = this.nrow; i > 0; i--) {
            if (map.put(col[i-1],i) != null) {
                vec[i-1] = true;
            }
        }
        return vec;
    }

    public boolean[] oshibka_doublons(String col) {
        boolean[] vec = logvec(this.nrow,false);
        Set<String> map = new HashSet<>();
        for (int i = 0; i < this.nrow; i++) {
            vec[i] = !map.add((String) this.c(col)[i]);
        }
        for (int i = 0; i < this.nrow; i++) {
            if (vec[i]) {
                System.out.println(this.c(col)[i]);
            }
        }

        map = new HashSet<>();
        for (int i = this.nrow; i > 0; i--) {
            vec[i-1] = !map.add((String) this.c(col)[i-1]);
        }
        return vec;
    } // pochemu ne rabotaet

    public boolean[] matcher_adh(DF base_adh, String col) {
        boolean[] vec = logvec(this.nrow,true);
        if(!check_in(col,this.header) | !check_in(col,base_adh.header)) {
            err("missing column");
            return vec;
        }
        String adh = "Numéro_Adhésion";
        Integer[] m = match_sans_doublons(this.c(adh), base_adh.c(adh));
        for (int i = 0; i < this.nrow; i++) {
            if (m[i] != -1) {
                vec[i] = !this.c(col)[i].equals(base_adh.c(col)[m[i]]);
            }
        }
        return vec;
    }
    public int[] match_first (Object[] a, Object[] b) {
        int[] out = new int[a.length];
        Arrays.fill(out,-1);
        for (int i = 0; i < a.length; i++) {
            if (a[i] != "") {
                for (int j = 0; j < b.length; j++) {
                    if (a[i].equals(b[j])) {
                        out[i] = j;
                        break;
                    }
                }
            }
        }

        return out;
    } // ploho napisan medlenii pizdec
    public Integer[] match_sans_doublonss (Object[] a, Object[] b) {
        Integer[] out = new Integer[a.length];
        Arrays.fill(out,-1);

        for (int i = 0; i < a.length; i++) {
            boolean found = false;
            if (a[i] != "") {
                for (int j = 0; j < b.length; j++) {
                    if (a[i].equals(b[j])) {
                        if(!found) {
                            out[i] = j;
                        }
                        found = true;
                    }
                }
            }
        }
        return out;
    } // kajetsa toje medlennii
    public Integer[] match_sans_doublons (Object[] a, Object[] b) {
        Integer[] out = new Integer[a.length];
        Arrays.fill(out,-1);

        HashMap<String, Integer> map = new HashMap<>();
        for (int i = 0; i < b.length; i++) {
            String v = (String) b[i];
            if (map.put(v,i) != null) {
                map.put(v,-1);
            } else {
                map.put(v,i);
            }
        }
//        for (String name : map.keySet()) {
//            System.out.println(name);
//        }
//        for (String key : map.keySet()) {
//            if (map.get(key) == -1) {
//                map.remove(key);
//            }
//        }
        for (int i = 0; i < a.length; i++) {
            String v = (String) a[i];
            if(map.containsKey(v)) {
                out[i] = map.get(v);
            }
        }

        return out;
    }
    public Integer[] match_sans_doublons_dans_le_source (Object[] a, Object[] b) {
        Integer[] out = new Integer[a.length];
//        Arrays.fill(out,-1);

        HashMap<String, Integer> map = new HashMap<>();
        for (int i = 0; i < b.length; i++) {
            String v = (String) b[i];
            if (map.put(v,i) != null) {
                map.put(v,-1);
            }
        }
        for (String key : map.keySet()) {
            if (map.get(key) == -1) {
                map.remove(key);
            }
        }
        for (int i = 0; i < a.length; i++) {
            out[i] = map.get((String) a[i]);
        }

        return out;
    } // delete?
    public void subst_columns(DF map) {
//        System.out.println("checker");
//        System.out.println(Arrays.toString(map.c(1)));
//        System.out.println(Arrays.toString(map.c(1)));
        for (int i = 0; i < this.header.length; i++) {
            if (header[i].contains("Lieu")) {
                System.out.println("/" + header[i] + "/");
            }
        }
        for (int i = 0; i < this.header.length; i++) {
            if(this.header[i] == null) continue;
            int ind = find_in_arr_first_index(map.c(1),this.header[i]);
            if (ind != -1) {
                String value = (String) map.c(0)[ind];
                if(!Objects.equals(value, "")) {
                    this.header[i] = value;
                }
            } else {
                if(!this.header[i].startsWith("Periode_reglement")) {
                    err_simple("col not found mapping " + this.header[i]);
                }
            }
        }

    }
    public String[] subst_columns(DF map, String[] head) {
        String[] out = new String[head.length];
        for (int i = 0; i < head.length; i++) {
            int ind = find_in_arr_first_index(map.c(1),head[i]);
            if (ind != -1) {
                String value = (String) map.c(0)[ind];
                if(!Objects.equals(value, "")) {
                    out[i] = value;
                } else {
                    out[i] = head[i];
                }
            } else {
                err("col not found " + this.header[i]);
            }
        }
        return out;
    }
    public boolean gg_check_controle(String label) {
        int ind = find_in_arr_first_index(this.c("Contrôle"), label);
        return this.c("Etat")[ind].equals("oui");
    }
    public boolean gg_check_bloquant(String label) {
        int ind = find_in_arr_first_index(this.c("Contrôle"), label);
        return this.c("Bloquant")[ind].equals("oui");
    }
    public void err_vec_handle(boolean[] vec) {
//        System.out.println("controle" + Controle_en_cours);
//        System.out.println(sum_boolean(vec));
        for (int i = 0; i < this.nrow; i++) {
            if(vec[i]) {
                Rapport.get(0).add(Police_en_cours_maj);
                Rapport.get(1).add(Flux_en_cours);
                Rapport.get(2).add(Controle_en_cours);
                Rapport.get(3).add((String) this.c("Numéro_Dossier")[i]);
            }
        }
    }
    public boolean grille_gen_controle_absent() {
        if (this.grille_gen == null) return true;
        int ind = find_in_arr_first_index(this.grille_gen.c("Contrôle"),Controle_en_cours);

        if (ind == -1) return true;
        return !this.grille_gen.c("Etat")[ind].equals("Oui");
    }
    public void delete_blanks_first_col() {
        boolean[] vec = logvec(this.nrow, false);
        for (int i = 0; i < this.nrow; i++) {
           vec[i] = this.c(0)[i] != "";
        }
        this.keep_rows(vec);
    }
    public boolean[] bool_filtre(String colname, String value) {
        boolean[] out = logvec(this.nrow,false);
        for (int i = 0; i < this.nrow; i++) {
            out[i] = this.c(colname)[i].equals(value);
        }
        return out;
    }
//    int[] temp = which(vec);
//    Integer[] v = new Integer[temp.length];
//        for (int c = 0; c < temp.length; c++) {
//        v[c] = Integer.parseInt((String) this.c("Numéro_Dossier")[temp[c]]);
//    }
//    write_csv(v);
//        System.out.println("result " + sum_boolean(vec));
}
