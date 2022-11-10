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

import org.apache.poi.ss.formula.functions.T;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.STSourceType;
import org.w3c.dom.ls.LSOutput;

import javax.naming.PartialResultException;
import javax.swing.*;

import static java.lang.Math.*;
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
            coltypes = get_col_types(header);
            nrow = parsedRows.size()-1;
            assert (coltypes.length == parsedRows.get(0).length);
            ncol = get_len(coltypes);
            df = new ArrayList<>(get_len(coltypes));
            this.df_populate(coltypes);
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
        coltypes = get_col_types(header);

//        this.header_refactor();

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
    public Col_types[] get_col_types (String[] vec) {
        Col_types[] out = new Col_types[vec.length];
        int i = 0;
        for (String s : vec) {
            out[i] = coltypes_G.get(s);
            assert out[i] != null : "coltype not found" + s;
            i++;
        }
        return out;
    }
    public LocalDate to_Date (Date input) {
        return input.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
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
    public void err(String msg) {
        System.out.println(msg);
        System.out.println(Police_en_cours);
        System.out.println(Controle_en_cours);
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
    public boolean[] c811(DF grille) {
        Controle_en_cours = "C811";
        boolean[] vec = new boolean[nrow];
        String[] cols = {"Montant_Indemnité_Principale","Valeur_Achat"};
        if (!check_in(cols,header)) {
            System.out.println("missing columns for grille C811");
            Arrays.fill(vec,false);
            return vec;
        } else {
            Arrays.fill(vec,false);
        }
        int dim = grille.nrow;
        String date_sous_col = "";
        for (int i = 0; i < grille.ncol; i++) {
            if (grille.header[i].contains("Date_Souscription_Adhésion borne basse")) {
                date_sous_col = grille.header[i];
            }
        }
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

                if (cell_base != null) {
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
                    break;
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
                    break;
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
                    break;
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
                    break;
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
                            break;
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
                            break;
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
                        break;
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
                        break;
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
                        break;
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
                        break;
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
                        break;
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
                        break;
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
                if (date_clot != null) {
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
                        break;
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
                        break;
                    }
                }

                colg = "Date_Clôture borne basse";
                if (date_clot != null) {
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
                        break;
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
                        break;
                    }
                }
            }

            if (reste.size() > 1) {
                vec[i] = true;
                err("error lignes multiples");
                break;
            }
            int reste_i = reste.get(0);

            col = "Date_Souscription_Adhésion";
            String col1 = "Date_Survenance";
            colg = "Age";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(header, col1) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                double age_ref = (double) grille.c(colg)[reste_i];
                LocalDate date_sous = (LocalDate) this.c(col)[i];
                LocalDate date_surv = (LocalDate) this.c(col1)[i];
                if (date_sous != null & date_surv != null) {
                        String m = (String) grille.c(date_sous_col)[reste_i];
                        int months = Integer.parseInt(m.replaceAll("\\D+",""));
                        if (date_sous.plusMonths(months).isBefore(date_surv)) {
                            vec[i] = true;
                            break;
                        }
                        int age = Period.between(date_sous.plusMonths(months), date_surv).getYears();
                        if(!(age == age_ref | Objects.equals(age_ref, NA_DBL))) {
                            vec[i] = true;
                            break;
                        }
                    } else {
                    if(!Objects.equals(age_ref, NA_DBL)) {
                        vec[i] = true;
                        break;
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
    public boolean[] c810(DF grille) {
        Controle_en_cours = "C810";
        boolean[] vec = new boolean[nrow];
        String[] cols = {"Statut_Sogedep"};
        System.out.println(Arrays.toString(header));
        if (!check_in(cols,header)) {
            System.out.println("missing columns for grille C810");
            Arrays.fill(vec,false);
            return vec;
        } else {
            Arrays.fill(vec,false);
        }
        int dim = grille.nrow;
        String date_sous_col = "";
        for (int i = 0; i < grille.ncol; i++) {
            if (grille.header[i].contains("Date_Souscription_Adhésion borne basse")) {
                date_sous_col = grille.header[i];
            }
        }
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

                if (cell_base != null) {
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
                        break;
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
                        break;
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
                        break;
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
                        break;
                    }
                }
            }

            col = "Date_Déclaration";
            colg = "Valeur Date_Déclaration";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
                cell_base_dat = (Date) this.c(col)[i];
                if (cell_base_dat != null) {
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
                        break;
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
                        break;
                    }
                }
            }

            if (reste.size() > 1) {
                vec[i] = true;
                err("error lignes multiples");
                break;
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
    public boolean[] c808(DF grille) {
        Controle_en_cours = "C808";
        boolean[] vec = new boolean[nrow];
        String[] cols = {"Montant_Indemnité_Principale","Montant_Reprise","Valeur_Achat"};
        if (!check_in(cols,header)) {
            System.out.println("missing columns for grille C808");
            Arrays.fill(vec,false);
            return vec;
        } else {
            Arrays.fill(vec,false);
        }
        int dim = grille.nrow;
        System.out.println(Arrays.toString(grille.header));
        String date_sous_col = "";
        for (int i = 0; i < grille.ncol; i++) {
            if (grille.header[i].contains("Date_Souscription_Adhésion borne basse")) {
                date_sous_col = grille.header[i];
            }
        }
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

                if (cell_base != null) {
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
                        break;
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
                        break;
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
                        break;
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
                        break;
                    }
                }
            }

//            col = "Code_Client";
//            colg = "Retraitement Code_Client";
//            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, colg) != -1) {
//                cell_base_str = (String) this.c(col)[i];
//                if (cell_base_str != null) {
//                    if (cell_base_str.matches(regex_digits)) {
//                        boolean[] temp = new boolean[reste.size()];
//                        int ind = 0;
//                        for (int r : reste) {
//                            cell_grille = grille.c(colg)[r];
//                            if(!(cell_grille.equals("professionnel") | cell_grille.equals(NA_STR))) {
//                                temp[ind] = true;
//                            }
//                            ind++;
//                        }
//                        for (int t = temp.length-1; t >= 0; t--) {
//                            if (temp[t]) {
//                                reste.remove(t);
//                            }
//                        }
//                        if (reste.isEmpty()) {
//                            vec[i] = true;
//                            break;
//                        }
//                    } else {
//                        boolean[] temp = new boolean[reste.size()];
//                        int ind = 0;
//                        for (int r : reste) {
//                            cell_grille = grille.c(colg)[r];
//                            if(!(cell_grille.equals("particulier") | cell_grille.equals(NA_STR))) {
//                                temp[ind] = true;
//                            }
//                            i++;
//                        }
//                        for (int t = temp.length-1; t >= 0; t--) {
//                            if (temp[t]) {
//                                reste.remove(t);
//                            }
//                        }
//                        if (reste.isEmpty()) {
//                            vec[i] = true;
//                            break;
//                        }
//                    }
//                } else {
//                    boolean[] temp = new boolean[reste.size()];
//                    int ind = 0;
//                    for (int r : reste) {
//                        if(!(grille.c(colg)[r].equals(NA_STR))) {
//                            temp[ind] = true;
//                        }
//                        i++;
//                    }
//                    for (int t = temp.length-1; t >= 0; t--) {
//                        if (temp[t]) {
//                            reste.remove(t);
//                        }
//                    }
//                    if (reste.isEmpty()) {
//                        vec[i] = true;
//                        break;
//                    }
//                }
//            }

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
                            break;
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
                            break;
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
                            break;
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
                            break;
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
                            break;
                        }
                }
            }

            col = "Date_Clôture";
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, col) != -1) {
                colg = "Date_Clôture borne haute";
                Date date_clot = (Date) this.c(col)[i];
                if (date_clot != null) {
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
                        break;
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
                        break;
                    }
                }

                colg = "Date_Clôture borne basse";
                if (date_clot != null) {
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
                        break;
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
                        break;
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
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(header, col1) != -1 & find_in_arr_first_index(grille.header, date_sous_col) != -1) {
                double age_ref = (double) grille.c("Age")[reste_i];
                LocalDate date_sous = to_Date((Date) this.c(col)[i]);
                LocalDate date_surv = to_Date((Date) this.c(col1)[i]);
                if (date_sous != null & date_surv != null) {
                    String m = (String) grille.c(date_sous_col)[reste_i];
                    int months = Integer.parseInt(m.replaceAll("\\D+",""));
                    if (date_sous.plusMonths(months).isBefore(date_surv)) {
                        vec[i] = true;
                        break;
                    }
                    int age = Period.between(date_sous.plusMonths(months), date_surv).getYears();
                    if(!(age == age_ref | Objects.equals(age_ref, NA_DBL))) {
                        vec[i] = true;
                        break;
                    }
                } else {
                    if(!Objects.equals(age_ref, NA_DBL)) {
                        vec[i] = true;
                        break;
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
            Double mr_ref;
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
    public boolean[] c807(DF grille) {
        Controle_en_cours = "C807";
        boolean[] vec = new boolean[nrow];
        String[] cols = {"Montant_Frais_Annexe","Valeur_Achat"};
        if (!check_in(cols,header)) {
            System.out.println("missing columns for grille C807");
            Arrays.fill(vec,false);
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

                if (cell_base != null) {
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
                        break;
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
                        break;
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
                        break;
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
                        break;
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
                            break;
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
                            break;
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
                            break;
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
                            break;
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
                            break;
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
            if(find_in_arr_first_index(header, col) != -1 & find_in_arr_first_index(grille.header, col) != -1) {
                colg = "Date_Clôture borne haute";
                Date date_clot = (Date) this.c(col)[i];
                if (date_clot != null) {
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
                        break;
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
                        break;
                    }
                }

                colg = "Date_Clôture borne basse";
                if (date_clot != null) {
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
                        break;
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
                        break;
                    }
                }
            }

//            if (reste.size() > 1) {
//                vec[i] = true;
//                System.out.println("error age multiple");
//                break;
//            }
//            int reste_i = reste.get(0);
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
                break;
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
    public boolean[] c806(DF grille) {
        Controle_en_cours = "C806";
        boolean[] vec = new boolean[nrow];
        String[] cols = {"Montant_Indemnité_Principale"};
        if (!check_in(cols,header)) {
            System.out.println("missing columns for grille C806");
            Arrays.fill(vec,false);
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

                if (cell_base != null) {
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
                        break;
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
                        break;
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
                        break;
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
                        break;
                    }
                }
            }

            if (reste.size() > 1) {
                vec[i] = true;
                err("error lignes multiples");
                break;
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
    public boolean[] c805(DF grille) {
        Controle_en_cours = "C806";
        boolean[] vec = new boolean[nrow];
        String[] cols = {"Montant_Indemnité_Principale"};
        if (!check_in(cols,header)) {
            System.out.println("missing columns for grille C805");
            Arrays.fill(vec,false);
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
            Double cell_base_dbl;
            Object cell_grille;
            Double cell_grille_dbl;
            for (String col : crit) {

                if(find_in_arr_first_index(header, col) == -1 | find_in_arr_first_index(grille.header, col) == -1) {
                    continue;
                }
                cell_base = this.c(col)[i];

                if (cell_base != null) {
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
                break;
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
    public boolean[] c809(DF grille) {
        Controle_en_cours = "C809";
        boolean[] vec = new boolean[nrow];
        String[] cols = {"Montant_Indemnité_Principale","Montant_Frais_Annexe","Montant_Reprise","Montant_Total_Règlement"};
        if (!check_in(cols,header)) {
            System.out.println("missing columns for grille C809");
            Arrays.fill(vec,false);
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
}
