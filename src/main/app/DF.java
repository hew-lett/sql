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
import javax.swing.*;

import static java.lang.Math.*;
import static java.util.stream.IntStream.range;
import static main.app.App.*;

public class DF {
//    private char delim;
//    private String path;
    public ArrayList<Object[]> df;
    public Col_types[] coltypes;
    public String[] header;
    public int ncol;
    public int nrow;
    public static SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy");

    // CONSTRUCTORS
    public DF (String path, char delim, String encoding) {
        if (encoding.equals("default")) encoding = "UTF-8";
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
                System.out.println(filename);
                coltypes = get_col_types(header, coltypes_G);
            } else {
                coltypes = get_col_types(header, coltypes_B);
            }
            nrow = parsedRows.size()-1;
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
    }
    public DF (String path, Object sheet_n, boolean strings) throws IOException {

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

        if(strings) {
            coltypes = new Col_types[ncol];
            Arrays.fill(coltypes,Col_types.STR);
        }

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
    }
    public DF ( ArrayList<Object[]> base) {
        this.df = base;
    }
    public DF ( DF old_base, boolean[] keep) {
        this.df = old_base.df;
        this.coltypes = old_base.coltypes;
        this.header = old_base.header;
        this.ncol = old_base.ncol;
        this.nrow = old_base.nrow;
        this.keep_rows(keep);
    }
    public DF ( DF old_base, String crit) {
        this.df = old_base.df;
        this.coltypes = old_base.coltypes;
        this.header = old_base.header;
        this.ncol = old_base.ncol;
        this.nrow = old_base.nrow;
        boolean[] keep = new boolean[this.nrow];
        for (int i = 0; i < this.nrow; i++) {
            keep[i] = this.c(0)[i].equals(crit);
        }
        this.keep_rows(keep);
    }
    // PRINT
    public void print() {
        this.print(min(10,this.nrow));
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
        int max = min(nrow, 100);
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
        Object out = null;
        switch(type){
            case STR:
                if (cell == null) return "";
                out = cell.toLowerCase();
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
    public Col_types[] get_col_types (String[] vec,  HashMap<String, DF.Col_types> types) {
        Col_types[] out = new Col_types[vec.length];
        int i = 0;
        for (String s : vec) {
            out[i] = types.get(s);
            if(out[i] == null) {
                out[i] = Col_types.STR;
            }
            i++;
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
       DF df_new = new DF(this, vec);
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
    public boolean check_in(String[] what, String[] where) {
        int counter = 0;
        for (String value : what) {
            for (String ref : where) {
                if (value.equals(ref)) {
                    counter++;
                    break;
                }
            }
        }
        return counter == what.length;
    }
    public boolean check_in(String what, String[] arr) {
        for (String where : arr) {
                if (what.equals(where)) {
                    return true;
                }
            }
        return false;
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
        if (sum_boolean(keep) != 0) {
            this.keep_cols(keep);
        }
    }
    public void err(String msg) {
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
        System.out.println(Police_en_cours);
        boolean[] vec = new boolean[nrow];
        String[] cols = new String[grille.ncol-1];
        for (int i = 1; i < grille.ncol; i++) {
            cols[i-1] = grille.header[i];
        } // si numéro police reste toujours le premier

        if (!check_in(cols,header)) {
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
        System.out.println(sum_boolean(vec));
        return vec;
    }
    public void one_dim_grille(DF grille) {
        String col = grille.header[1];
        String[] refer = Arrays.copyOf(grille.c(col), grille.c(col).length, String[].class);
        boolean[] vec;
        if (!check_in(col,this.header)) {
            err("missing columns");
//            return logvec(this.nrow,true);
            System.out.println(this.nrow);
            return;
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
                vec[i] = !check_in((String) this.c(col)[i],refer);
            }
        }

        System.out.println(sum_boolean(vec));
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

    public boolean[] c811() {
        Controle_en_cours = "C811";
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);

        boolean[] vec = new boolean[nrow];
        String[] cols = {"Numéro_Police","Montant_Indemnité_Principale","Valeur_Achat"};
        if (!check_in(cols,header)) {
            err("missing columns");
            Arrays.fill(vec,true);
            return vec;
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

            col = "Valeur_Catalogue";
            colg = "Valeur_Catalogue Borne basse";
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
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                cell_base_str = (String) this.c(col)[i];
                if (cell_base_str != null) {
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
                            i++;
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
                        i++;
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
                        i++;
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
                        i++;
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
                        i++;
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
                        i++;
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
                        i++;
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

// valeur achat
//            col = "Valeur_Achat";
//            colg = "Valeur_Achat Borne haute";
//            cell_base_dbl = (Double) this.c(col)[i];
//            if (cell_base_dbl != null) {
//                for (int r : reste) {
//                    cell_grille_dbl = (Double) grille.c(colg)[r];
//                    if(!(cell_grille_dbl >= cell_base_dbl | cell_grille_dbl.equals(NA_DBL))){
//                        reste.remove(Integer.valueOf(r));
//                    }
//                }
////                reste = which(vec_loc);
//                if (reste.isEmpty()) {
//                    vec[i] = true;
//                    continue;
//                }
//            } else {
//                for (int r : reste) {
//                    cell_grille_dbl = (Double) grille.c(colg)[r];
//                    if(!cell_grille_dbl.equals(NA_DBL)) {
//                        reste.remove(Integer.valueOf(r));
//                    }
//                }
////                reste = which(vec_loc);
//                if (reste.isEmpty()) {
//                    vec[i] = true;
//                    continue;
//                }
//            }
//
//            col = "Valeur_Achat";
//            colg = "Valeur_Achat Borne basse";
//            cell_base_dbl = (Double) this.c(col)[i];
//            if (cell_base_dbl != null) {
//                for (int r : reste) {
//                    cell_grille_dbl = (Double) grille.c(colg)[r];
//                    if(!(cell_grille_dbl <= cell_base_dbl | cell_grille_dbl.equals(NA_DBL))) {
//                        reste.remove(Integer.valueOf(r));
//                    }
//                }
////                reste = which(vec_loc);
//                if (reste.isEmpty()) {
//                    vec[i] = true;
//                    continue;
//                }
//            } else {
//                for (int r : reste) {
//                    cell_grille_dbl = (Double) grille.c(colg)[r];
//                    if(!(cell_grille_dbl.equals(NA_DBL))) {
//                        reste.remove(Integer.valueOf(r));
//                    }
//                }
////                reste = which(vec_loc);
//                if (reste.isEmpty()) {
//                    vec[i] = true;
//                    continue;
//                }
//            }

//            System.out.println(4);

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
                        i++;
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
                        i++;
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

                colg = "Date_Clôture borne basse";
                if (date_clot != NA_DAT) {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        Date date_clot_ref = (Date) grille.c(colg)[r];
                        if(!(!date_clot_ref.after(date_clot) | grille.c(colg)[r].equals(NA_DAT))){
                            temp[ind] = true;
                        }
                        i++;
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
                        i++;
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

            col = "Date_Souscription_Adhésion";
            String col1 = "Date_Survenance";
            colg = "Age";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(header, col1) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                double age_ref = (double) grille.c(colg)[reste_i];
                LocalDate date_sous = to_Date((Date) this.c(col)[i]);
                LocalDate date_surv = to_Date((Date) this.c(col1)[i]);
                if (date_sous != NA_LDAT & date_surv != NA_LDAT) {
                        String m = (String) grille.c("Date_Souscription_Adhésion borne basse <= Date_Survenance")[reste_i];
                        int months = NumberUtils.toInt(m.replaceAll("\\D+",""),0);
                        if (date_sous.plusMonths(months).isBefore(date_surv)) {
                            vec[i] = true;
                            continue;
                        }
                        int age = Period.between(date_sous.plusMonths(months), date_surv).getYears();
                        if(!(age == age_ref | Objects.equals(age_ref, NA_DBL))) {
                            vec[i] = true;
                            continue;
                        }
                    } else {
                    if(!Objects.equals(age_ref, NA_DBL)) {
                        vec[i] = true;
                        continue;
                    }
                }
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
            if (montant_raw.equals("Valeur_Achat")) {
                montant = (Double) this.c("Valeur_Achat")[i];
            } else {
                montant = Double.parseDouble(montant_raw.replace(",","."));
            }
            mip_ref = Math.round(montant * pourcentage) / 100.0;

            vec[i] = !compa_signe(mip,mip_ref,signe);
        }

        System.out.println(sum_boolean(vec));
        return(vec);
    } // g
    public boolean[] c810() {
        Controle_en_cours = "C810";
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);

        boolean[] vec = new boolean[nrow];
        String[] cols = {"Numéro_Police","Statut_Sogedep"};
        if (!check_in(cols,header)) {
            err("missing columns");
            Arrays.fill(vec,true);
            return vec;
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
//                        System.out.println(col);
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

                colg = "Valeur_Catalogue Borne basse";
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

        System.out.println(sum_boolean(vec));
        return(vec);
    } // g
    public boolean[] c809() {
        Controle_en_cours = "C809";
        boolean[] vec = new boolean[nrow];
        String[] cols = {"Montant_Indemnité_Principale","Montant_Frais_Annexe","Montant_Reprise","Montant_Total_Règlement"};
        if (!check_in(cols,header)) {
            err("missing columns");
            Arrays.fill(vec,true);
            return vec;
        } else {
            Arrays.fill(vec,false);
        }
        Double a;
        double b;
        for (int i = 0; i < nrow; i++) {
            a = Math.round(((double) this.c("Montant_Indemnité_Principale")[i] +
                    (double) this.c("Montant_Frais_Annexe")[i] -
                    (double) this.c("Montant_Reprise")[i]) * 100) / 100d;
            b = Math.round((double) this.c("Montant_Total_Règlement")[i] * 100) / 100d;
            vec[i] = !a.equals(b);
        }
        System.out.println(sum_boolean(vec));
        return(vec);
    }
    public boolean[] c808() {
        Controle_en_cours = "C808";
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);

        boolean[] vec = new boolean[nrow];
        String[] cols = {"Numéro_Police","Montant_Indemnité_Principale","Montant_Reprise","Valeur_Achat"};
        if (!check_in(cols,header)) {
            err("missing columns");
            Arrays.fill(vec,true);
            return vec;
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
//                        System.out.println(col);
                        vec[i] = true;
                        continue;
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
//                        System.out.println(col);
                        vec[i] = true;
                        continue;
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

                col = "Valeur_Catalogue";
                colg = "Valeur_Catalogue Borne basse";
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
                            i++;
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
                            i++;
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
                            i++;
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
                            i++;
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
                            System.out.println(Arrays.toString(grille.header));
                            cell_grille = grille.c(colg)[r];
                            if(!(cell_grille.equals(4) | cell_grille.equals(5) | cell_grille.equals(6) | cell_grille.equals(8) | cell_grille.equals(NA_DBL))) {
                                temp[ind] = true;
                            }
                            i++;
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
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, col) != -1) {
                colg = "Date_Clôture borne haute";
                Date date_clot = (Date) this.c(col)[i];
                if (date_clot != NA_DAT) {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        Date date_clot_ref = (Date) grille.c(colg)[r];
                        if(!(!date_clot_ref.before(date_clot) | grille.c(colg)[r].equals(NA_DAT))){
                            temp[ind] = true;
                        }
                        i++;
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
                        i++;
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

                colg = "Date_Clôture borne basse";
                if (date_clot != NA_DAT) {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        Date date_clot_ref = (Date) grille.c(colg)[r];
                        if(!(!date_clot_ref.after(date_clot) | grille.c(colg)[r].equals(NA_DAT))){
                            temp[ind] = true;
                        }
                        i++;
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
                        i++;
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
                err("error age multiple");
                break;
            }
            int reste_i = reste.get(0);

            col = "Date_Souscription_Adhésion";
            String col1 = "Date_Survenance";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(header, col1) != -1 & find_in_arr_first_index(grille.header, "Date_Souscription_Adhésion borne basse <= Date_Survenance") != -1) {
                double age_ref = (double) grille.c("Age")[reste_i];
                LocalDate date_sous = to_Date((Date) this.c(col)[i]);
                LocalDate date_surv = to_Date((Date) this.c(col1)[i]);
                if (date_sous != NA_LDAT & date_surv != NA_LDAT) {
                    String m = (String) grille.c("Date_Souscription_Adhésion borne basse <= Date_Survenance")[reste_i];
                    int months = NumberUtils.toInt(m.replaceAll("\\D+",""),0);
                    if (date_sous.plusMonths(months).isBefore(date_surv)) {
                        vec[i] = true;
                        continue;
                    }
                    int age = Period.between(date_sous.plusMonths(months), date_surv).getYears();
                    if(!(age == age_ref | Objects.equals(age_ref, NA_DBL))) {
                        vec[i] = true;
                        continue;
                    }
                } else {
                    if(!Objects.equals(age_ref, NA_DBL)) {
                        vec[i] = true;
                        continue;
                    }
                }
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


        System.out.println(sum_boolean(vec));
        return(vec);
    } // g
    public boolean[] c807() {
        Controle_en_cours = "C807";
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);

        boolean[] vec = new boolean[nrow];
        String[] cols = {"Numéro_Police","Montant_Frais_Annexe","Valeur_Achat"};
        if (!check_in(cols,header)) {
            err("missing columns");
            Arrays.fill(vec,true);
            return vec;
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
//                        System.out.println(col);
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
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, col) != -1) {

                String colg = "Valeur_Catalogue Borne haute";
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

                col = "Valeur_Catalogue";
                colg = "Valeur_Catalogue Borne basse";
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
            String colg = "Référentiel Marque";
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
                            i++;
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
                            i++;
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
                            i++;
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
                            i++;
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
                            System.out.println(Arrays.toString(grille.header));
                            cell_grille = grille.c(colg)[r];
                            if(!(cell_grille.equals(4) | cell_grille.equals(5) | cell_grille.equals(6) | cell_grille.equals(8) | cell_grille.equals(NA_DBL))) {
                                temp[ind] = true;
                            }
                            i++;
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
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, col) != -1) {
                colg = "Date_Clôture borne haute";
                Date date_clot = (Date) this.c(col)[i];
                if (date_clot != NA_DAT) {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        Date date_clot_ref = (Date) grille.c(colg)[r];
                        if(!(!date_clot_ref.before(date_clot) | grille.c(colg)[r].equals(NA_DAT))){
                            temp[ind] = true;
                        }
                        i++;
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
                        i++;
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

                colg = "Date_Clôture borne basse";
                if (date_clot != NA_DAT) {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        Date date_clot_ref = (Date) grille.c(colg)[r];
                        if(!(!date_clot_ref.after(date_clot) | grille.c(colg)[r].equals(NA_DAT))){
                            temp[ind] = true;
                        }
                        i++;
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
                        i++;
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

            colg = "Valeur Montant_Indemnité_Principale";
            if(find_in_arr_first_index(grille.header, colg) != -1) {
                col = "Montant_Indemnité_Principale";
                boolean[] temp = new boolean[reste.size()];
                int ind = 0;
                for (int id : reste) {
                    String montant_raw = (String) grille.c(colg)[id];
                    Double mip = (Double) this.c(col)[i];

                    if (!montant_raw.equals(NA_STR)) {
                        Object signe_raw = grille.c("Signe Montant_Indemnité_Principale")[id];
                        short signe = (short) round((Double) signe_raw);
                        Double montant = Double.parseDouble(montant_raw.replace(",", "."));
                        switch (signe) {
                            case 1:
                                temp[ind] = Objects.equals(mip, montant);
                                ind++;
                                continue;
                            case 2:
                                temp[ind] = mip > montant;
                                ind++;
                                continue;
                            case 3:
                                temp[ind] = mip < montant;
                                ind++;
                                continue;
                            case 4:
                                temp[ind] = mip >= montant;
                                ind++;
                                continue;
                            case 5:
                                temp[ind] = mip <= montant;
                                ind++;
                                continue;
                            case 6:
                                temp[ind] = !Objects.equals(mip, montant);
                                ind++;
                                continue;
                            default:
                                System.out.println("erreur signe non-renseignée grille c811");
                        }
                        for (int t = temp.length-1; t >= 0; t--) {
                            if (temp[t]) {
                                reste.remove(t);
                            }
                        }
                    }
                }
        }

            if (reste.size() > 1) {
                vec[i] = true;
                err("error montant multiple");
                continue;
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
        System.out.println(sum_boolean(vec));
        return(vec);
    } // g
    public boolean[] c806() {
        Controle_en_cours = "C806";
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);

        boolean[] vec = new boolean[nrow];
        String[] cols = {"Numéro_Police","Montant_Indemnité_Principale"};
        if (!check_in(cols,header)) {
            err("missing columns");
            Arrays.fill(vec,true);
            return vec;
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
//                        System.out.println(col);
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
//                        System.out.println(col);
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

                col = "Valeur_Catalogue";
                colg = "Valeur_Catalogue Borne basse";
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

        System.out.println(sum_boolean(vec));
        return(vec);
    } // g
    public boolean[] c805() {
        Controle_en_cours = "C805";
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);

        boolean[] vec = new boolean[nrow];
        String[] cols = {"Numéro_Police","Montant_Indemnité_Principale"};
        if (!check_in(cols,header)) {
            err("missing columns");
            Arrays.fill(vec,true);
            return vec;
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
//                        System.out.println(col);
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
//                        System.out.println(col);
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
            String montant_raw = (String) grille.c(col)[reste_i];
            double montant = Double.parseDouble(montant_raw.replace(",",".").replaceAll("[^\\d.]", ""));
            Double mip_ref = Math.round(montant * 100) / 100.0;

            vec[i] = !(mip <= mip_ref | mip_ref.equals(NA_DBL));
        }

        System.out.println(sum_boolean(vec));
        return(vec);
    } // g
    public boolean[] c804() {
        Controle_en_cours = "C804";
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);

        boolean[] vec = new boolean[nrow];
        String[] cols = {"Numéro_Police","Statut_Technique_Sinistre","Motif_Refus"};
        if (!check_in(cols,header)) {
            err("missing columns");
            Arrays.fill(vec,true);
            return vec;
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
//                        System.out.println(col);
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
//                        System.out.println(col);
                        vec[i] = true;
                        break;
                    }
                }
            }
            if (reste.isEmpty()) {
                vec[i] = true;
            }
        }

        return(vec);
    } // g
    public boolean[] c803() {
        Controle_en_cours = "C803";
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);

        boolean[] vec = new boolean[nrow];
        String[] cols = {"Numéro_Police","Montant_Indemnité_Principale"};
        if (!check_in(cols,header)) {
            err("missing columns");
            Arrays.fill(vec,true);
            return vec;
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
//                        System.out.println(col);
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
//                        System.out.println(col);
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

                col = "Valeur_Catalogue";
                colg = "Valeur_Catalogue Borne basse";
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
            System.out.println(montant_raw);
            double montant = Double.parseDouble(montant_raw.replace(",",".").replaceAll("[^\\d.]", ""));
            double mip_ref = Math.round(montant * 100) / 100.0;

            vec[i] = !compa_signe(mip,mip_ref,signe);
        }

        System.out.println(sum_boolean(vec));
        return(vec);
    } // g
    public boolean[] c801() {
        Controle_en_cours = "C801";
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);

        boolean[] vec = new boolean[nrow];
        String[] cols = {"Numéro_Police","Statut_Technique_Sinistre","Type_Indemnisation"};
        if (!check_in(cols,header)) {
            err("missing columns");
            Arrays.fill(vec,true);
            return vec;
        } else {
            Arrays.fill(vec,false);
        }
        String col1 = "Statut_Technique_Sinistre";
        String col2 = "Type_Indemnisation";
        for (int i = 0; i < nrow; i++) {
            int id = find_in_arr_first_index(grille.c(col1),this.c(col1)[i]);
            Object gr_v = grille.c(col2)[id];
            Object base_v = this.c(col2)[i];
            if(base_v != null) {
                vec[i] = gr_v.equals(base_v) | gr_v.equals("{ renseigné }") | gr_v.equals(NA_STR);
            } else {
                vec[i] = gr_v.equals("") | gr_v.equals(NA_STR);
            }
        }
        System.out.println(sum_boolean(vec));
        return(vec);
    } // g
    public boolean[] c712() {
        Controle_en_cours = "C712";
        boolean[] vec = new boolean[nrow];
        String[] cols = {"Statut_Technique_Sinistre","Date_Survenance","Date_Souscription_Adhésion","Date_Evénement"};
        String[] stats = {"en cours - accepté","terminé - accepté","réglé"};
        if (!check_in(cols,header)) {
            err("missing columns");
            return logvec(nrow,true);
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

        return(vec);
    }
    public boolean[] c711() {
        Controle_en_cours = "C711";
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);

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
//                System.out.println(cell_base);
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
//                        System.out.println("casse "+ cell_base + " " + col);
//                        System.out.println(col);
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
//                        System.out.println(col);
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

                col = "Valeur_Catalogue";
                colg = "Valeur_Catalogue Borne basse";
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
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                cell_base_str = (String) this.c(col)[i];
                if (cell_base_str != null) {
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
                            i++;
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
                        i++;
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
                            i++;
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
                            i++;
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
                            i++;
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
                            i++;
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
                            System.out.println(Arrays.toString(grille.header));
                            cell_grille = grille.c(colg)[r];
                            if(!(cell_grille.equals(4) | cell_grille.equals(5) | cell_grille.equals(6) | cell_grille.equals(8) | cell_grille.equals(NA_DBL))) {
                                temp[ind] = true;
                            }
                            i++;
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

// valeur achat
//            col = "Valeur_Achat";
//            colg = "Valeur_Achat Borne haute";
//            cell_base_dbl = (Double) this.c(col)[i];
//            if (cell_base_dbl != null) {
//                for (int r : reste) {
//                    cell_grille_dbl = (Double) grille.c(colg)[r];
//                    if(!(cell_grille_dbl >= cell_base_dbl | cell_grille_dbl.equals(NA_DBL))){
//                        reste.remove(Integer.valueOf(r));
//                    }
//                }
////                reste = which(vec_loc);
//                if (reste.isEmpty()) {
//                    vec[i] = true;
//                    continue;
//                }
//            } else {
//                for (int r : reste) {
//                    cell_grille_dbl = (Double) grille.c(colg)[r];
//                    if(!cell_grille_dbl.equals(NA_DBL)) {
//                        reste.remove(Integer.valueOf(r));
//                    }
//                }
////                reste = which(vec_loc);
//                if (reste.isEmpty()) {
//                    vec[i] = true;
//                    continue;
//                }
//            }
//
//            col = "Valeur_Achat";
//            colg = "Valeur_Achat Borne basse";
//            cell_base_dbl = (Double) this.c(col)[i];
//            if (cell_base_dbl != null) {
//                for (int r : reste) {
//                    cell_grille_dbl = (Double) grille.c(colg)[r];
//                    if(!(cell_grille_dbl <= cell_base_dbl | cell_grille_dbl.equals(NA_DBL))) {
//                        reste.remove(Integer.valueOf(r));
//                    }
//                }
////                reste = which(vec_loc);
//                if (reste.isEmpty()) {
//                    vec[i] = true;
//                    continue;
//                }
//            } else {
//                for (int r : reste) {
//                    cell_grille_dbl = (Double) grille.c(colg)[r];
//                    if(!(cell_grille_dbl.equals(NA_DBL))) {
//                        reste.remove(Integer.valueOf(r));
//                    }
//                }
////                reste = which(vec_loc);
//                if (reste.isEmpty()) {
//                    vec[i] = true;
//                    continue;
//                }
//            }

//            System.out.println(4);
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
                        i++;
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
                        i++;
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

                colg = "Date_Clôture borne basse";
                if (date_clot != NA_DAT) {
                    boolean[] temp = new boolean[reste.size()];
                    int ind = 0;
                    for (int r : reste) {
                        Date date_clot_ref = (Date) grille.c(colg)[r];
                        if(!(!date_clot_ref.after(date_clot) | grille.c(colg)[r].equals(NA_DAT))){
                            temp[ind] = true;
                        }
                        i++;
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
                        i++;
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

            col = "Date_Souscription_Adhésion";
            String col1 = "Date_Survenance";
            String colg1 = "Date_Souscription_Adhésion borne basse <= Date_Survenance";
            String colg2 = "Date_Souscription_Adhésion borne haute >= Date_Survenance";

            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(header, col1) != -1 &
               find_in_arr_first_index(grille.header, colg1) != -1 & find_in_arr_first_index(grille.header, colg2) != -1) {
//                System.out.println(i);
//                System.out.println(this.c(col)[i]);
//                System.out.println(this.c(col)[i].getClass().getName());
                LocalDate date_sous = to_Date((Date) this.c(col)[i]);
                LocalDate date_surv = to_Date((Date) this.c(col1)[i]);
                String m1 = (String) grille.c(colg1)[reste_i];
                String m2 = (String) grille.c(colg2)[reste_i];
                if (date_sous != NA_LDAT & date_surv != NA_LDAT) {
//                    System.out.println(7);

                    int months1 = NumberUtils.toInt(m1.replaceAll("\\D+",""), 0);
                    int months2 = NumberUtils.toInt(m2.replaceAll("\\D+",""), 0);
                    System.out.println(date_sous.plusMonths(months1).isAfter(date_surv) & !m1.equals(NA_STR));
                    System.out.println((date_sous.plusMonths(months2).isBefore(date_surv) & !m2.equals(NA_STR)));
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

        return(vec);
    } // g
    public boolean[] c710(DF base_adh) {
        boolean[] vec = logvec(this.nrow,false);
        String col = "Numéro_Adhésion";
        Integer[] m = match_sans_doublons(this.c(col), base_adh.c(col));
        for (int i = 0; i < this.nrow; i++) {
            vec[i] = m[i].equals(-1);
        }
        return vec;
    }
    public boolean[] c709() {
        Controle_en_cours = "C709";
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);

        String[] cols = {"Numéro_Police","Date_Survenance","Date_Souscription_Adhésion"};
        boolean[] vec;
        if (!check_in(cols,header)) {
            err("missing columns");
            return logvec(nrow,true);
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
//                        System.out.println(col);
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
//                        System.out.println(col);
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
        return(vec);
    } // g
    public boolean[] c708(DF base_adh) {
        Controle_en_cours = "C708";
        boolean[] vec;
        String adh = "Numéro_Adhésion";
        String col = "Date_Survenance";
        String col1 = "Date_Prise_Effet_Résiliation";
        String col2 = "Statut_Technique_Sinistre";
        if (!check_in(new String[]{adh, col},this.header) | !check_in(new String[]{adh, col1},base_adh.header)) {
            err("missing columns");
            return logvec(this.nrow,true);
        } else {
            vec = logvec(this.nrow,false);
        }
        Integer[] m = match_sans_doublons(this.c(adh), base_adh.c(adh));

        for (int i = 0; i < this.nrow; i++) {

            String statut = (String) this.c(col2)[i];
            if (!statut.equals("terminé - refusé avant instruction")) {

                Date surv = (Date) this.c(col)[i];
                if(surv.equals(NA_DAT)) {
                    vec[i] = true;
                    continue;
                }

                Date resil = (Date) this.c(col1)[i];
                if(resil.equals(NA_DAT)) {
                    continue;
                }

                if (m[i] == -1) {
                    vec[i] = true;
                } else {
                    vec[i] = surv.after(resil);
                }
            }
        }
        return vec;
    }
    public boolean[] c707() {
        Controle_en_cours = "C707";
        boolean[] vec;
        String col = "Statut_Technique_Sinistre";
        String col1 = "Date_Survenance";
        String[] cols = {col,col1};
        String[] statut_ref = {"en cours - accepté","terminé - accepté","réglé"};
        if (!check_in(cols,this.header)) {
            err("missing columns");
            return logvec(this.nrow,true);
        } else {
            vec = logvec(this.nrow,false);
        }
        for (int i = 0; i < this.nrow; i++) {
            String statut = (String) this.c(col)[i];
            Date surv = (Date) this.c(col1)[i];
            vec[i] = in(statut,statut_ref) & surv.equals(NA_DAT);
        }
        return vec;
    }
    public boolean[] c706() {
        Controle_en_cours = "C706";
        boolean[] vec;
        String col = "Statut_Technique_Sinistre";
        String col1 = "Date_Survenance";
        String col2 = "Date_Activation";
        String[] cols = {col,col1,col2};
        String[] statut_ref = {"en cours - accepté","terminé - accepté","réglé"};
        if (!check_in(cols,this.header)) {
            err("missing columns");
            return logvec(this.nrow,true);
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
        return vec;
    }
    public boolean[] c705() {
        Controle_en_cours = "C705";
        boolean[] vec;
        String col = "Statut_Technique_Sinistre";
        String col1 = "Date_Survenance";
        String col2 = "Date_Souscription_Adhésion";
        String col3 = "Date_Achat_Bien_Garanti";
        String[] cols = {col,col1,col2,col3};
        String[] statut_ref = {"en cours - accepté","terminé - accepté","réglé"};
        if (!check_in(cols,this.header)) {
            err("missing columns");
            return logvec(this.nrow,true);
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
        return vec;
    }
    public boolean[] c704() {
        Controle_en_cours = "C704";
        boolean[] vec;
        String col = "Statut_Technique_Sinistre";
        String col1 = "Date_Déclaration";
        String col2 = "Date_Survenance";
        String[] cols = {col,col1,col2};
        String[] statut_ref = {"en cours - accepté","terminé - accepté","réglé"};
        if (!check_in(cols,this.header)) {
            err("missing columns");
            return logvec(this.nrow,true);
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
        return vec;
    }
    public boolean[] c703() {
        Controle_en_cours = "C703";
        boolean[] vec;
        String col = "Statut_Technique_Sinistre";
        String col1 = "Date_Déclaration";
        String col2 = "Date_Activation";
        String[] cols = {col,col1,col2};
        String[] statut_ref = {"en cours - accepté","terminé - accepté","réglé"};
        if (!check_in(cols,this.header)) {
            err("missing columns");
            return logvec(this.nrow,true);
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
        return vec;
    }
    public boolean[] c702() {
        Controle_en_cours = "C702";
        boolean[] vec;
        String col = "Statut_Technique_Sinistre";
        String col1 = "Date_Déclaration";
        String col2 = "Date_Souscription_Adhésion";
        String col3 = "Date_Achat_Bien_Garanti";
        String[] cols = {col,col1,col2,col3};
        String[] statut_ref = {"en cours - accepté","terminé - accepté","réglé"};
        if (!check_in(cols,this.header)) {
            err("missing columns");
            return logvec(this.nrow,true);
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
        return vec;
    }
    public boolean[] c701() {
        Controle_en_cours = "C701";
        boolean[] vec;
        String col = "Statut_Technique_Sinistre";
        String col1 = "Date_Déclaration";
        String col2 = "Date_Survenance";
        String[] cols = {col,col1,col2};
        String[] statut_ref = {"en cours - accepté","terminé - accepté","réglé"};
        if (!check_in(cols,this.header)) {
            err("missing columns");
            return logvec(this.nrow,true);
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
        return vec;
    }
    public boolean[] c608() {
        Controle_en_cours = "C608";
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        return simple_grille(grille);
    } // g
    public boolean[] c607() {
        Controle_en_cours = "C607";
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        return simple_grille(grille);
    } // g
    public void c503_519(DF base_adh) {
        String[] controles = new String[]{"503","C504","C505","C506","C507","C508",
                "C509","C510","C511","C512","C513","C514","C515","516","C518","C519"};
        String[] cols = new String[]{"Numéro_Extension","Date_Souscription_Adhésion","Date_Achat_Bien_Garanti",
                "Critère_Identification_Bien_Garanti_1","Critère_Identification_Bien_Garanti_2","Critère_Identification_Bien_Garanti_3",
                "Critère_Identification_Bien_Garanti_4","Critère_Identification_Bien_Garanti_5","Critère_Identification_Bien_Garanti_6",
                "Critère_Tarifaire_1","Critère_Tarifaire_2","Critère_Tarifaire_3","Critère_Tarifaire_4","SKU","Valeur_Achat","Qualité_Client"};
        for (int i = 0; i < controles.length; i++) {
            Controle_en_cours = controles[i];
            this.matcher(base_adh,cols[i]);
        }
    }
    public boolean[] c517() {
        Controle_en_cours = "C517";
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        return simple_grille(grille);
    } // g
    public boolean[] c502() {
        Controle_en_cours = "C502";
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        return simple_grille(grille);
    } // g
    public boolean[] c501() {
        Controle_en_cours = "C501";
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        return simple_grille(grille);
    } // g
    public boolean[] c401() {
        Controle_en_cours = "C401";
        int dim = this.nrow;
        String col = "Date_Prise_Effet_Résiliation";
        String col1 = "Date_Souscription_Adhésion";
        boolean[] vec = new boolean[dim];
        String[] cols = {"Date_Prise_Effet_Résiliation","Date_Souscription_Adhésion"};
        if (!check_in(cols,header)) {
            err("missing columns");
            Arrays.fill(vec,true);
            return vec;
        } else {
            Arrays.fill(vec,false);
        }
        for (int i = 0; i < dim; i++) {
            Date resil = (Date) this.c(col)[i];
            Date sous = (Date) this.c(col1)[i];
            vec[i] = !resil.equals(NA_DAT) & resil.before(sous);
        }
        System.out.println(sum_boolean(vec));
        return vec;
    } // controle adhé
    public boolean[] c309() {
        Controle_en_cours = "C309";
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        boolean[] vec = new boolean[nrow];
        String[] cols = {"Numéro_Police","Critère_Tarifaire_1","SKU","Valeur_Achat"};
        if (!check_in(cols,header)) {
            err("missing columns");
            Arrays.fill(vec,true);
            return vec;
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

        System.out.println(sum_boolean(vec));
        return(vec);
    } // g
    public boolean[] c308() {
        Controle_en_cours = "C308";
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        return simple_grille(grille);
    } // g
    public boolean[] c305() {
        Controle_en_cours = "C305";
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        Double x_raw = (Double) grille.c("Contrôle")[0];
        long x = round(x_raw);
        String col1 = "Date_Activation";
        String col2 = "Date_Souscription_Adhésion";
        String[] cols = {"Numéro_Police",col1,col2};
        boolean[] vec;
        if (!check_in(cols,this.header)) {
            err("missing columns");
            return logvec(this.nrow,true);
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
        return vec;
    } // g
    public boolean[] c304() {
        Controle_en_cours = "C304";
        String col1 = "Date_Activation";
        String col2 = "Date_Souscription_Adhésion";
        String col3 = "Date_Achat_Bien_Garanti";
        String[] cols = {col1,col2,col3};
        boolean[] vec;
        if (!check_in(cols,this.header)) {
            err("missing columns");
            return logvec(this.nrow,true);
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
        return vec;
    }
    public boolean[] c303() {
        Controle_en_cours = "C303";
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);

        String col1 = "Date_Achat_Bien_Garanti";
        String col2 = "Date_Souscription_Adhésion";
        String col3 = "Critère_Identification_Bien_Garanti_4";
        String[] cols = {"Numéro_Police",col1,col2,col3};
        boolean[] vec;
        if (!check_in(cols,this.header)) {
            err("missing columns");
            return logvec(this.nrow,true);
        } else {
            vec = logvec(this.nrow,false);
        }
        for (int i = 0; i < this.nrow; i++) {
            String cell_base = (String) this.c(col3)[i];
            ArrayList<Integer> ind = new ArrayList<>();
            for (int j = 0; j < grille.nrow; j++) {
                if (grille.c(col3)[j].equals(cell_base) | grille.c(col3)[j].equals(NA_STR)) {
                    ind.add(j);
                }
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
        return vec;
    } // g
    public boolean[] c302() {
        Controle_en_cours = "C302";

        String col1 = "Date_Souscription_Adhésion";
        String col2 = "Date_Achat_Bien_Garanti";
        String[] cols = {col1,col2};
        boolean[] vec;
        if (!check_in(cols,this.header)) {
            err("missing columns");
            return logvec(this.nrow,true);
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
        return vec;
    }
    public boolean[] c301() {
        Controle_en_cours = "C301";
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        Date x_raw = (Date) grille.c("Contrôle")[0];

        String col1 = "Date_Souscription_Adhésion";
        String[] cols = {"Numéro_Police",col1};
        boolean[] vec;
        if (!check_in(cols,this.header)) {
            err("missing columns");
            return logvec(this.nrow,true);
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
        return vec;
    } // g
    public boolean[] c205() {
        Controle_en_cours = "C205";
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
        return simple_grille(grille);
    } // g
    public void c202_223() {
        String[] controles = new String[]{"C202","C203","C204","C206",
        "C207","C208","C209","C210","C211","C212","C213","C214","C215",
        "C216","C217","C218","C219","C220","C221","C222","C223"};
        for (String s : controles) {
            Controle_en_cours = s;
            DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);
            this.one_dim_grille(grille);
        }
    }
    public void c201() {
        Controle_en_cours = "C304";
        String col = "Numéro_Police";
        boolean[] vec;
        if (!check_in(col,this.header)) {
            System.out.println(this.nrow);
            return;
        } else {
            vec = logvec(this.nrow,false);
        }
        for (int i = 0; i < this.nrow; i++) {
            vec[i] = this.c(col)[i].equals(Police_en_cours);
        }
        System.out.println(sum_boolean(vec));
    }
    public void c101() {
        Controle_en_cours = "C101";
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);

        String col1 = "Statut_Technique_Sinistre";
        String col2 = "Numéro_Adhésion";
        String colg = "Nombre Numéro_Adhésion";
        String[] cols = {"Numéro_Police",col1,col2};
        boolean[] vec;
        if (!check_in(cols,this.header)) {
            err("missing columns");
            System.out.println(this.nrow);
            return;
        } else {
            vec = logvec(this.nrow,false);
        }

        for (int i = 0; i < this.nrow; i++) {
            int ind = find_in_arr_first_index(grille.c(col1), this.c(col1)[i]);
            if (ind == -1) {
                vec[i] = true;
                continue;
            }
            Double cell_grille = (Double) grille.c(colg)[ind];
            if (cell_grille.equals(NA_DBL)) {
                continue;
            }
            String cell_base = (String) this.c(col2)[i];
            int counter = 0;
            for (int j = 0; (counter <= cell_grille) & (j < this.nrow); j++) {
                if (this.c(col2)[j].equals(cell_base)) {
                    counter++;
                }
            }
            if (counter > cell_grille) {
                vec[i] = true;
            }
        }
        System.out.println(sum_boolean(vec));
    }
    public void c102() {
        Controle_en_cours = "C102";
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);

        String col1 = "Statut_Technique_Sinistre";
        String col2 = "Numéro_Extension";
        String colg = "Nombre Numéro_Extension";
        String[] cols = {"Numéro_Police",col1,col2};
        boolean[] vec;
        if (!check_in(cols,this.header)) {
            err("missing columns");
            System.out.println(this.nrow);
            return;
        } else {
            vec = logvec(this.nrow,false);
        }

        for (int i = 0; i < this.nrow; i++) {
            int ind = find_in_arr_first_index(grille.c(col1), this.c(col1)[i]);
            if (ind == -1) {
                vec[i] = true;
                continue;
            }
            Double cell_grille = (Double) grille.c(colg)[ind];
            if (cell_grille.equals(NA_DBL)) {
                continue;
            }
            String cell_base = (String) this.c(col2)[i];
            int counter = 0;
            for (int j = 0; (counter <= cell_grille) & (j < this.nrow); j++) {
                if (this.c(col2)[j].equals(cell_base)) {
                    counter++;
                }
            }
            if (counter > cell_grille) {
                vec[i] = true;
            }
        }
        System.out.println(sum_boolean(vec));
    }
    public void c103() {
        Controle_en_cours = "C103";
        DF grille = new DF(grilles_G.get(Controle_en_cours), Police_en_cours);

        String col3 = "Statut_Technique_Sinistre";
        String col4 = "Critère_Identification_Bien_Garanti_5";
        String colg = "Nombre Critère_Bien_Garanti_5";
        String[] cols = {"Numéro_Police",col3,col4};
        String[] crit = {"Statut_Technique_Sinistre", "SKU","Canal_Adhésion"};

        boolean[] vec;
        if (!check_in(cols,this.header)) {
            err("missing columns");
            System.out.println(this.nrow);
            return;
        } else {
            vec = logvec(this.nrow,false);
        }

        int dim = grille.nrow;
        ArrayList<Integer> reste_gen = new ArrayList<>(dim);
        for(int r = 0; r < dim; r++){
            reste_gen.add(r);
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
                err("error lignes multiples");
                continue;
            }
            int reste_i = reste.get(0);

            Double cell_grille_dbl = (Double) grille.c(colg)[reste_i];
            String cell_base_str = (String) this.c(col4)[i];
            int counter = 0;
            for (int j = 0; (counter <= cell_grille_dbl) & (j < this.nrow); j++) {
                if (this.c(col4)[j].equals(cell_base_str)) {
                    counter++;
                }
            }
            if (counter > cell_grille_dbl) {
                vec[i] = true;
            }
        }
        System.out.println(sum_boolean(vec));
    }
    public void matcher(DF base_adh, String col) {
        boolean[] vec = logvec(this.nrow,true);
        String adh = "Numéro_Adhésion";
        int[] m = match_first(this.c(adh), base_adh.c(adh));
        for (int i = 0; i < this.nrow; i++) {
            if (m[i] != -1) {
                vec[i] = !this.c(col)[i].equals(base_adh.c(col)[m[i]]);
            }
        }
        System.out.println(sum_boolean(vec));
    }
    public boolean[] logvec(int dim, boolean values) {
        boolean[] out = new boolean[dim];
        Arrays.fill(out,values);
        return out;
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
    }
    public Integer[] match_sans_doublons (Object[] a, Object[] b) {
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
    }
//    int[] temp = which(vec);
//    Integer[] v = new Integer[temp.length];
//        for (int c = 0; c < temp.length; c++) {
//        v[c] = Integer.parseInt((String) this.c("Numéro_Dossier")[temp[c]]);
//    }
//    write_csv(v);
//        System.out.println("result " + sum_boolean(vec));
}
