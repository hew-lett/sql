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
import org.openxmlformats.schemas.spreadsheetml.x2006.main.STSourceType;

import javax.naming.PartialResultException;
import javax.swing.*;

import static java.lang.Math.addExact;
import static java.lang.Math.round;
import static java.util.stream.IntStream.range;
import static main.app.App.*;

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
            header = rows.next();
            nrow = parsedRows.size()-1;
            assert (coltypes.length == parsedRows.get(0).length);
            ncol = get_len(coltypes);
            df = new ArrayList<>(get_len(coltypes));
            df = df_populate(df,coltypes);
//            System.out.println(Arrays.toString(header));
//            System.out.println((Arrays.toString(coltypes)));
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
        } catch (IOException ignored) {
        }
        this.header_refactor();

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
        this.header_refactor();

        df = new ArrayList<>(ncol);
        df = df_populate(df,coltypes);

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
//                        System.out.println(c.getStringCellValue());
//                        System.out.println(coltypes[ct_iterator]);
//                        System.out.println(df.get(col_iterator).getClass());
                        df.get(col_iterator)[row_number] = get_cell_of_type(c.getStringCellValue(),coltypes[ct_iterator]);      // no formula
                    }
                    col_iterator++;
                }
                ct_iterator++;
            }
            row_number++;
        }
    }
    public DF ( ArrayList<Object[]> base) {
        this.df = base;
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
    public void print_cols() {
       for (int i = 0; i < this.ncol; i++) {
           System.out.println(Arrays.toString(this.df.get(i)));
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
                try {
                    out = Double.parseDouble(cell.replace(",","."));
                } catch (NumberFormatException ignored) {
                    out = NA_DBL;
                }
                break;
            case DAT:
                try {
                    out = format.parse(cell);
                }
                catch (NullPointerException | ParseException ignored) {
                    out = NA_DAT;
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

        header = keep_from_array(header,keep_vec);

        coltypes = keep_from_array(coltypes,keep_vec);

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
        for (int i = 0; i < ncol; i++) {

                Object[] col = this.c(i);
                switch (coltypes[i]) {
                    case STR:
                        for (int j = 0; j < nrow; j++) {
                            if (!col[j].equals(NA_STR)) {
                                keep[i] = true;
                                break;
                            }
                        }
                        break;
                    case DAT:
                        for (int j = 0; j < nrow; j++) {
                            if (!col[j].equals(NA_DAT)) {
                                keep[i] = true;
                                break;
                            }
                        }
                        break;
                    case DBL:
                        for (int j = 0; j < nrow; j++) {
                            if (!col[j].equals(NA_DBL)) {
                                keep[i] = true;
                                break;
                            }
                        }
                        break;
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

    public int c811(DF grille) {
        boolean[] vec = new boolean[nrow];
        int dim = grille.nrow;
        ArrayList<Integer> reste_gen = new ArrayList<>(dim);
        for(int r = 0; r < dim; r++){
            reste_gen.add(r);
        }
        String[] crit = {"Statut_Technique_Sinistre","SKU","Type_Indemnisation","Statut_Technique_Sinistre_2","Libellé_Garantie","Critère_Identification_Bien_Garanti_2","Critère_Identification_Bien_Garanti_6",
                "Critère_Tarifaire_1","Statut_Sogedep" };
        for (int i = 0; i < nrow; i++) {
//            boolean[] vec_loc = new boolean[dim];
//            Arrays.fill(vec_loc,false);
            ArrayList<Integer> reste = new ArrayList<>(reste_gen);
            Object cell_base;
            Double cell_base_dbl;
            String cell_base_str;
            Object cell_grille;
            Double cell_grille_dbl;
            for (String col : crit) {
                cell_base = this.c(col)[i];

                if (cell_base != null) {
                    for (int r : reste) {
                        cell_grille = grille.c(col)[r];
                        if(!(cell_grille.equals(cell_base) | cell_grille.equals("{ renseigné }") | cell_grille == NA_STR)) {
                            reste.remove(Integer.valueOf(r));
                        }
                    }
//                    reste = which(vec_loc);
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        break;
                    }
                } else {
                    for (int r : reste) {
                        cell_grille = grille.c(col)[r];
                        if(!(cell_grille.equals("") | cell_grille.equals("{ vide }") | cell_grille == NA_STR)) {
                            reste.remove(Integer.valueOf(r));
                        }
                    }
//                    reste = which(vec_loc);
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        break;
                    }
                }
            }
            if (reste.isEmpty()) {
                continue;
            }

            String col = "Valeur_Catalogue";
            String colg = "Valeur_Catalogue Borne haute";
            cell_base_dbl = (Double) this.c(col)[i];
            if (cell_base_dbl != null) {
                for (int r : reste) {
                    cell_grille_dbl = (Double) grille.c(colg)[r];
                    if(!(cell_grille_dbl >= cell_base_dbl | cell_grille_dbl.equals(NA_DBL))){
                        reste.remove(Integer.valueOf(r));
                    }
                }
//                reste = which(vec_loc);
                if (reste.isEmpty()) {
                    vec[i] = true;
                    continue;
                }
            } else {
                for (int r : reste) {
                    cell_grille_dbl = (Double) grille.c(colg)[r];
                    if(!cell_grille_dbl.equals(NA_DBL)) {
                        reste.remove(Integer.valueOf(r));
                    }
                }
//                reste = which(vec_loc);
                if (reste.isEmpty()) {
                    vec[i] = true;
                    continue;
                }
            }

            col = "Valeur_Catalogue";
            cell_base_dbl = (Double) this.c(col)[i];
            colg = "Valeur_Catalogue Borne basse";
            if (cell_base_dbl != null) {
                for (int r : reste) {
                    cell_grille_dbl = (Double) grille.c(colg)[r];
                    if(!(cell_grille_dbl <= cell_base_dbl | cell_grille_dbl.equals(NA_DBL))) {
                        reste.remove(Integer.valueOf(r));
                    }
                }
//                reste = which(vec_loc);
                if (reste.isEmpty()) {
                    vec[i] = true;
                    continue;
                }
            } else {
                for (int r : reste) {
                    cell_grille_dbl = (Double) grille.c(colg)[r];
                    if(!(cell_grille_dbl.equals(NA_DBL))) {
                        reste.remove(Integer.valueOf(r));
                    }
                }
//                reste = which(vec_loc);
                if (reste.isEmpty()) {
                    vec[i] = true;
                    continue;
                }
            }

            col = "Code_Client";
            colg = "Retraitement Code_Client";
            cell_base_str = (String) this.c(col)[i];
            if (cell_base_str != null) {
                if (cell_base_str.matches(regex_digits)) {
                    for (int r : reste) {
                        cell_grille = grille.c(colg)[r];
                        if(!(cell_grille.equals("professionnel") | cell_grille == NA_STR)) {
                            reste.remove(Integer.valueOf(r));
                        }
                    }
//                    reste = which(vec_loc);
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }

                } else {
                    for (int r : reste) {
                        cell_grille = grille.c(colg)[r];
                        if(!(cell_grille.equals("particulier") | cell_grille == NA_STR)) {
                            reste.remove(Integer.valueOf(r));
                        }
                    }
//                    reste = which(vec_loc);
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                }
            } else {
                for (int r : reste) {
                    if(!(grille.c(colg)[r] == NA_STR)) {
                        reste.remove(Integer.valueOf(r));
                    }
                }
//                reste = which(vec_loc);
                if (reste.isEmpty()) {
                    vec[i] = true;
                    continue;
                }
            }

            col = "Critère_Identification_Bien_Garanti_1";
            colg = "Référentiel Marque";
            cell_base_str = (String) this.c(col)[i];
            switch (cell_base_str) {
                case "apple":
                    for (int r : reste) {
                        cell_grille = grille.c(colg)[r];
                        if(!(cell_grille.equals(1) | cell_grille.equals(8) | cell_grille == NA_DBL)) {
                            reste.remove(Integer.valueOf(r));
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                    break;
                case "samsung":
                    for (int r : reste) {
                        cell_grille = grille.c(colg)[r];
                        if(!(cell_grille.equals(2) | cell_grille.equals(4) | cell_grille.equals(8) | cell_grille == NA_DBL)) {
                            reste.remove(Integer.valueOf(r));
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                    break;
                case "huawei":
                    for (int r : reste) {
                        cell_grille = grille.c(colg)[r];
                        if(!(cell_grille.equals(3) | cell_grille.equals(4) | cell_grille.equals(5) | cell_grille.equals(8) | cell_grille == NA_DBL)) {
                            reste.remove(Integer.valueOf(r));
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                    break;
                case "dyson":
                    for (int r : reste) {
                        cell_grille = grille.c(colg)[r];
                        if(!(cell_grille.equals(4) | cell_grille.equals(5) | cell_grille.equals(6) | cell_grille.equals(7) | cell_grille == NA_DBL)) {
                            reste.remove(Integer.valueOf(r));
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
                    break;
                default:
                    for (int r : reste) {
                        cell_grille = grille.c(colg)[r];
                        if(!(cell_grille.equals(4) | cell_grille.equals(5) | cell_grille.equals(6) | cell_grille.equals(8) | cell_grille == NA_DBL)) {
                            reste.remove(Integer.valueOf(r));
                        }
                    }
                    if (reste.isEmpty()) {
                        vec[i] = true;
                        continue;
                    }
            }



        }


        return sum_boolean(vec);
    }

}
