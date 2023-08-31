package main.app;

import com.monitorjbl.xlsx.StreamingReader;
import org.apache.poi.ss.usermodel.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;

import java.util.Date;

import static main.app.App.*;
import static main.app.Base.STATUT_FICTIF_FIC;

public class Estimate extends DF {
    String key;
    public String[] subheader;
    public List<Date> headerCalcul;
    int baseNcol = 0;
    int lastAppendSize = 0;
    protected static Set<String> uniqueNumPoliceValues = new HashSet<>();


    boolean[] mask_col;
    protected Stopwatch stopwatch = new Stopwatch();
    public Set<String> uniqueStatutsEstimate;
    public static final HashMap<String, Integer> monthMap = new HashMap<String, Integer>() {{
        put("jan.", Calendar.JANUARY);
        put("feb.", Calendar.FEBRUARY);
        put("mar.", Calendar.MARCH);
        put("apr.", Calendar.APRIL);
        put("may.", Calendar.MAY);
        put("jun.", Calendar.JUNE);
        put("jul.", Calendar.JULY);
        put("aug.", Calendar.AUGUST);
        put("sep.", Calendar.SEPTEMBER);
        put("oct.", Calendar.OCTOBER);
        put("nov.", Calendar.NOVEMBER);
        put("dec.", Calendar.DECEMBER);
    }};
    public static void main(String[] args) throws IOException, SQLException {

    }
    public Estimate(String path) throws IOException {
        this.fullPath = path;
        fileName = new File(path).getName();
        key = fileName.replace(".xlsx", "").toLowerCase();

        InputStream is = Files.newInputStream(new File(path).toPath());
        Workbook workbook = StreamingReader.builder()
                .rowCacheSize(1)
                .bufferSize(4096)
                .open(is);

        String sheet_name = workbook.getSheetName(0);
        Sheet sheet = workbook.getSheet(sheet_name);
        Iterator<Row> rowIter = sheet.rowIterator();
        Row row = rowIter.next();
        nrow = sheet.getLastRowNum();
        ncol = row.getLastCellNum();
        header = new String[ncol];
        subheader = new String[ncol];
        int i = 0;
        for (Cell c : row) {
            header[i] = c.getStringCellValue();
            subheader[i] = "";
            i++;
        }

        coltypes = new Col_types[ncol];
        for (i = 0; i < ncol; i++) {
            if (header[i].contains("Date Periode")) {
                coltypes[i] = Col_types.DAT;
            } else {
                coltypes[i] = Col_types.STR;
            }
        }

        df = new ArrayList<>(ncol);
        this.df_populate(coltypes);

        format = new SimpleDateFormat("dd-MM-yyyy");

        int col_iterator;
        int row_number = 0;
        while(rowIter.hasNext()) {
            row = rowIter.next();
            col_iterator = 0;

            for (int c = 0; c < ncol; c++) {
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
                    //System.out.println(cell_i);
                    df.get(col_iterator)[row_number] = parseCell(cell_i, coltypes[c], format);
                    col_iterator++;
                }
            }
            row_number++;
        }
        headerAndColtypesDropSKP();
        formatDP();
        baseNcol = ncol;
        mask_col = new boolean[ncol];
        Arrays.fill(mask_col, true);
    } //file_sin

    public void getUniqueStatutsFromMap() {
        List<String> sortedStatuts = new ArrayList<>(globalStatutDateRangeMap.keySet());

        // Sort the list so that "terminé - accepté" is at the beginning
        sortedStatuts.sort((statut1, statut2) -> {
            if (statut1.equalsIgnoreCase("terminé - accepté")) {
                return -1;
            } else if (statut2.equalsIgnoreCase("terminé - accepté")) {
                return 1;
            }
            return statut1.compareTo(statut2);
        });

        this.uniqueStatutsEstimate = new LinkedHashSet<>(sortedStatuts);
    }

    public void populateMonthSin(List<Base> bases, String statut) {
        int ind_datePeriode = find_in_arr_first_index(this.header, "Date Periode");
        int begin = ncol - lastAppendSize;

        Date minDateForStatus = globalStatutDateRangeMap.get(statut).get(0);
        Date maxDateForStatus = globalStatutDateRangeMap.get(statut).get(1);

        for (int col = begin; col < ncol; col++) {
            if (!(isLaterSvD(this.header[col], maxDateForStatus, "MM-yyyy") ||
                    isEarlierSvD(this.header[col], minDateForStatus, "MM-yyyy"))) {
                this.mask_col[col] = true;
            }
        } // showing mask

        for (Base base : bases) {
            String police = base.numPolice;

            boolean[] mask_row = new boolean[this.nrow];
            for (int row = 0; row < this.nrow; row++) {
                if (police.equalsIgnoreCase((String) this.c("Contrat")[row])) {
                    mask_row[row] = true;
                }
            }

            List<Date> minmax = policeStatutDateRangeMap.get(police).get(statut);
            if(minmax == null) continue;
            minDateForStatus = minmax.get(0);
            maxDateForStatus = minmax.get(1);

            Map<String, Map<String, Double>> currentPivotTable = base.pivotTable.get(statut);


            boolean[] calc_mask = new boolean[lastAppendSize];
            for (int col = begin; col < ncol; col++) {
                if (!(isLaterSvD(this.header[col], maxDateForStatus, "MM-yyyy") ||
                        isEarlierSvD(this.header[col], minDateForStatus, "MM-yyyy"))) {
                    calc_mask[col-begin] = true;
                }
            }

            for (int row = 0; row < this.nrow; row++) {
                if (!mask_row[row]) continue;

                for (int col = begin; col < ncol; col++) {
                    if (!calc_mask[col-begin]) continue;

                    String datePeriode = (String) this.df.get(ind_datePeriode)[row];
                    this.df.get(col)[row] = getSum(currentPivotTable, datePeriode, this.header[col]); // getSum method should be modified to work with both Double and Integer types
                }
            }
        }
    }
    public void populateYearSin(List<Base> bases, String statut) {
        int ind_datePeriode = find_in_arr_first_index(this.header, "Date Periode");
        int begin = ncol - lastAppendSize;

        Date minDateForStatus = globalStatutDateRangeMap.get(statut).get(0);
        Date maxDateForStatus = globalStatutDateRangeMap.get(statut).get(1);

        for (int col = begin; col < ncol; col++) {
            if (!(isLaterSvD(this.header[col], maxDateForStatus, "yyyy") ||
                isEarlierSvD(this.header[col], minDateForStatus, "yyyy"))) {
                this.mask_col[col] = true;
            }
        } // showing mask

        for (Base base : bases) {
            String police = base.numPolice;

            boolean[] mask_row = new boolean[this.nrow];
            for (int row = 0; row < this.nrow; row++) {
                if (police.equalsIgnoreCase((String) this.c("Contrat")[row])) {
                    mask_row[row] = true;
                }
            }

            List<Date> minmax = policeStatutDateRangeMap.get(police).get(statut);
            if (minmax == null) continue;
            minDateForStatus = minmax.get(0);
            maxDateForStatus = minmax.get(1);

            Map<String, Map<String, Double>> currentPivotTable = base.pivotTableYearly.get(statut);

            boolean[] calc_mask = new boolean[lastAppendSize];
            for (int col = begin; col < ncol; col++) {
                if (!(isLaterSvD(this.header[col], maxDateForStatus, "yyyy") ||
                    isEarlierSvD(this.header[col], minDateForStatus, "yyyy"))) {
                    calc_mask[col-begin] = true;
                }
            }

            for (int row = 0; row < this.nrow; row++) {
                if (!mask_row[row]) continue;

                for (int col = begin; col < ncol; col++) {
                    if (!calc_mask[col-begin]) continue;

                    String datePeriode = (String) this.df.get(ind_datePeriode)[row];
                    this.df.get(col)[row] = getSum(currentPivotTable, datePeriode, this.header[col]);
                }
            }
        }
    }
    public void populateTotalSin(List<Base> bases, String statut) {
        int ind_datePeriode = find_in_arr_first_index(this.header, "Date Periode");
        int begin = ncol - 1;

        this.mask_col[begin] = true;

        for (Base base : bases) {
            String police = base.numPolice;

            boolean[] mask_row = new boolean[this.nrow];
            for (int row = 0; row < this.nrow; row++) {
                if (police.equalsIgnoreCase((String) this.c("Contrat")[row])) {
                    mask_row[row] = true;
                }
            }

            Map<String, Double> currentPivotTable = base.pivotTableTotal.get(statut);
            if (currentPivotTable == null) continue;
//            System.out.println(police + statut);
            for (int row = 0; row < this.nrow; row++) {
                if (!mask_row[row]) continue;

                String datePeriode = (String) this.df.get(ind_datePeriode)[row];
                this.df.get(begin)[row] = getTotal(currentPivotTable, datePeriode);
            }
        }
    }

    public void populateMonthSinN(List<Base> bases, String statut) {
        System.out.println("POPULATE " + statut);
        System.out.println(this.header[1717] + " " + this.c(1717)[30]);
        int ind_datePeriode = find_in_arr_first_index(this.header, "Date Periode");
        int begin = ncol - lastAppendSize;

        Date minDateForStatus = globalStatutDateRangeMap.get(statut).get(0);
        Date maxDateForStatus = globalStatutDateRangeMap.get(statut).get(1);

        for (int col = begin; col < ncol; col++) {
            if (!(isLaterSvD(this.header[col], maxDateForStatus, "MM-yyyy") ||
                    isEarlierSvD(this.header[col], minDateForStatus, "MM-yyyy"))) {
                this.mask_col[col] = true;
            }
        } // showing mask

        for (Base base : bases) {
            String police = base.numPolice;

            boolean[] mask_row = new boolean[this.nrow];
            for (int row = 0; row < this.nrow; row++) {
                if (police.equalsIgnoreCase((String) this.c("Contrat")[row])) {
                    mask_row[row] = true;
                }
            }

            List<Date> minmax = policeStatutDateRangeMap.get(police).get(statut);
            if(minmax == null) continue;
            minDateForStatus = minmax.get(0);
            maxDateForStatus = minmax.get(1);

            // Notice the difference here. We're accessing pivotTableN instead of pivotTable
            Map<String, Map<String, Integer>> currentPivotTableN = base.pivotTableN.get(statut);

            boolean[] calc_mask = new boolean[lastAppendSize];
            for (int col = begin; col < ncol; col++) {
                if (!(isLaterSvD(this.header[col], maxDateForStatus, "MM-yyyy") ||
                        isEarlierSvD(this.header[col], minDateForStatus, "MM-yyyy"))) {
                    calc_mask[col-begin] = true;
                }
            }

            for (int row = 0; row < this.nrow; row++) {
                if (!mask_row[row]) continue;

                for (int col = begin; col < ncol; col++) {
                    if (!calc_mask[col-begin]) continue;

                    String datePeriode = (String) this.df.get(ind_datePeriode)[row];
                    // Calling the new getSumN method for the Integer type
            if (statut.equals("terminé - accepté") && datePeriode.equals("12-2022") && this.header[col].equals("04-2023")) {
                System.out.println("here");
            }
//            String prev = (String) this.c(1717)[30];
//                    System.out.println("col: "+ col + " row: " + row + this.c(1717)[30]);
//                    if(this.c(1717)[30] != null && this.c(1717)[30].equals("4")) {
//                        System.out.println("here");
//                    }
                this.c(col)[row] = getSumN(currentPivotTableN, datePeriode, this.header[col]);
//                if(this.c(1717)[30] != null) {
//                    if (this.c(1717)[30].equals("4") && prev.equals(null)) {
//                        System.out.println("here");
//                    }
//                }

//                    if (this.c(1717)[30].equals("3")) {
//                System.out.println("here");
//            }
                }
            }
        }
    }
    public void populateYearSinN(List<Base> bases, String statut) {
        int ind_datePeriode = find_in_arr_first_index(this.header, "Date Periode");
        int begin = ncol - lastAppendSize;

        Date minDateForStatus = globalStatutDateRangeMap.get(statut).get(0);
        Date maxDateForStatus = globalStatutDateRangeMap.get(statut).get(1);

        for (int col = begin; col < ncol; col++) {
            if (!(isLaterSvD(this.header[col], maxDateForStatus, "yyyy") ||
                    isEarlierSvD(this.header[col], minDateForStatus, "yyyy"))) {
                this.mask_col[col] = true;
            }
        } // showing mask

        for (Base base : bases) {
            String police = base.numPolice;

            boolean[] mask_row = new boolean[this.nrow];
            for (int row = 0; row < this.nrow; row++) {
                if (police.equalsIgnoreCase((String) this.c("Contrat")[row])) {
                    mask_row[row] = true;
                }
            }

            List<Date> minmax = policeStatutDateRangeMap.get(police).get(statut);
            if (minmax == null) continue;
            minDateForStatus = minmax.get(0);
            maxDateForStatus = minmax.get(1);

            Map<String, Map<String, Integer>> currentPivotTableN = base.pivotTableYearlyN.get(statut);

            boolean[] calc_mask = new boolean[lastAppendSize];
            for (int col = begin; col < ncol; col++) {
                if (!(isLaterSvD(this.header[col], maxDateForStatus, "yyyy") ||
                        isEarlierSvD(this.header[col], minDateForStatus, "yyyy"))) {
                    calc_mask[col-begin] = true;
                }
            }

            for (int row = 0; row < this.nrow; row++) {
                if (!mask_row[row]) continue;

                for (int col = begin; col < ncol; col++) {
                    if (!calc_mask[col-begin]) continue;

                    String datePeriode = (String) this.df.get(ind_datePeriode)[row];
                    this.df.get(col)[row] = getSumN(currentPivotTableN, datePeriode, this.header[col]);
                }
            }
        }
    }
    public void populateTotalSinN(List<Base> bases, String statut) {
        int ind_datePeriode = find_in_arr_first_index(this.header, "Date Periode");
        int begin = ncol - 1;

        this.mask_col[begin] = true;

        for (Base base : bases) {
            String police = base.numPolice;

            boolean[] mask_row = new boolean[this.nrow];
            for (int row = 0; row < this.nrow; row++) {
                if (police.equalsIgnoreCase((String) this.c("Contrat")[row])) {
                    mask_row[row] = true;
                }
            }

            Map<String, Integer> currentPivotTableN = base.pivotTableTotalN.get(statut);
            if (currentPivotTableN == null) continue;

            for (int row = 0; row < this.nrow; row++) {
                if (!mask_row[row]) continue;

                String datePeriode = (String) this.df.get(ind_datePeriode)[row];
                this.df.get(begin)[row] = getTotalN(currentPivotTableN, datePeriode);
            }
        }
    }

    public void populateMonthSinAllStatuts(List<Base> bases) {
        int ind_datePeriode = find_in_arr_first_index(this.header, "Date Periode");
        int begin = ncol - lastAppendSize;
        Stopwatch stopwatch = new Stopwatch();
        stopwatch.start();

        for (int col = begin; col < ncol; col++) {
            if (!(isLaterSvD(this.header[col], globalMaxDate, "MM-yyyy") ||
                    isEarlierSvD(this.header[col], globalMinDate, "MM-yyyy"))) {
                this.mask_col[col] = true;
            }
        } // showing mask

        for (Base base : bases) {
            String police = base.numPolice;
            boolean[] mask_row = new boolean[this.nrow];
            for (int row = 0; row < this.nrow; row++) {
                if (police.equalsIgnoreCase((String) this.c("Contrat")[row])) {
                    mask_row[row] = true;
                }
            }

            boolean[] calc_mask = new boolean[lastAppendSize];
            for (int col = begin; col < ncol; col++) {
                if (!(isLaterSvD(this.header[col], globalMaxDate, "MM-yyyy") ||
                    isEarlierSvD(this.header[col], globalMinDate, "MM-yyyy"))) {
                    calc_mask[col-begin] = true;
                }
            }

            for (int row = 0; row < this.nrow; row++) {
                if (!mask_row[row]) continue;

                for (int col = begin; col < ncol; col++) {
                    if (!calc_mask[col-begin]) continue;

                    String datePeriode = (String) this.df.get(ind_datePeriode)[row];
                    this.df.get(col)[row] = getSum(base.pivotTableAllStatuts, datePeriode, this.header[col]);
                }
            }
        }
    }
    public void populateYearSinAllStatuts(List<Base> bases) {
        int ind_datePeriode = find_in_arr_first_index(this.header, "Date Periode");
        int begin = ncol - lastAppendSize;

        for (int col = begin; col < ncol; col++) {
            if (!(isLaterSvD(this.header[col], globalMaxDate, "yyyy") ||
                isEarlierSvD(this.header[col], globalMinDate, "yyyy"))) {
                this.mask_col[col] = true;
            }
        } // showing mask

        for (Base base : bases) {
            String police = base.numPolice;
            boolean[] mask_row = new boolean[this.nrow];
            for (int row = 0; row < this.nrow; row++) {
                if (police.equalsIgnoreCase((String) this.c("Contrat")[row])) {
                    mask_row[row] = true;
                }
            }

            boolean[] calc_mask = new boolean[lastAppendSize];
            for (int col = begin; col < ncol; col++) {
                if (!(isLaterSvD(this.header[col], globalMaxDate, "yyyy") ||
                    isEarlierSvD(this.header[col], globalMinDate, "yyyy"))) {
                    calc_mask[col-begin] = true;
                }
            }

            for (int row = 0; row < this.nrow; row++) {
                if (!mask_row[row]) continue;

                for (int col = begin; col < ncol; col++) {
                    if (!calc_mask[col-begin]) continue;

                    String datePeriode = (String) this.df.get(ind_datePeriode)[row];
                    this.df.get(col)[row] = getSum(base.pivotTableAllStatutsYearly, datePeriode, this.header[col]);
                }
            }
        }
    }
    public void populateTotalSinAllStatuts(List<Base> bases) {
        int ind_datePeriode = find_in_arr_first_index(this.header, "Date Periode");
        int begin = ncol - 1;

        for (Base base : bases) {
            String police = base.numPolice;

            boolean[] mask_row = new boolean[this.nrow];
            for (int row = 0; row < this.nrow; row++) {
                if (police.equalsIgnoreCase((String) this.c("Contrat")[row])) {
                    mask_row[row] = true;
                }
            }

            for (int row = 0; row < this.nrow; row++) {
                if (!mask_row[row]) continue;

                String datePeriode = (String) this.df.get(ind_datePeriode)[row];
                this.df.get(begin)[row] = getTotal(base.pivotTableAllStatutsTotal, datePeriode);
            }
        }
    }

    public void populateMonthSinAllStatutsN(List<Base> bases) {
        int ind_datePeriode = find_in_arr_first_index(this.header, "Date Periode");
        int begin = ncol - lastAppendSize;

        for (int col = begin; col < ncol; col++) {
            if (!(isLaterSvD(this.header[col], globalMaxDate, "MM-yyyy") ||
                    isEarlierSvD(this.header[col], globalMinDate, "MM-yyyy"))) {
                this.mask_col[col] = true;
            }
        } // showing mask

        for (Base base : bases) {
            String police = base.numPolice;
            boolean[] mask_row = new boolean[this.nrow];
            for (int row = 0; row < this.nrow; row++) {
                if (police.equalsIgnoreCase((String) this.c("Contrat")[row])) {
                    mask_row[row] = true;
                }
            }

            boolean[] calc_mask = new boolean[lastAppendSize];
            for (int col = begin; col < ncol; col++) {
                if (!(isLaterSvD(this.header[col], globalMaxDate, "MM-yyyy") ||
                        isEarlierSvD(this.header[col], globalMinDate, "MM-yyyy"))) {
                    calc_mask[col-begin] = true;
                }
            }

            for (int row = 0; row < this.nrow; row++) {
                if (!mask_row[row]) continue;

                for (int col = begin; col < ncol; col++) {
                    if (!calc_mask[col-begin]) continue;

                    String datePeriode = (String) this.df.get(ind_datePeriode)[row];
                    this.df.get(col)[row] = getSumN(base.pivotTableAllStatutsN, datePeriode, this.header[col]);
                }
            }
        }
    }
    public void populateYearSinAllStatutsN(List<Base> bases) {
        int ind_datePeriode = find_in_arr_first_index(this.header, "Date Periode");
        int begin = ncol - lastAppendSize;

        for (int col = begin; col < ncol; col++) {
            if (!(isLaterSvD(this.header[col], globalMaxDate, "yyyy") ||
                    isEarlierSvD(this.header[col], globalMinDate, "yyyy"))) {
                this.mask_col[col] = true;
            }
        } // showing mask

        for (Base base : bases) {
            String police = base.numPolice;
            boolean[] mask_row = new boolean[this.nrow];
            for (int row = 0; row < this.nrow; row++) {
                if (police.equalsIgnoreCase((String) this.c("Contrat")[row])) {
                    mask_row[row] = true;
                }
            }

            boolean[] calc_mask = new boolean[lastAppendSize];
            for (int col = begin; col < ncol; col++) {
                if (!(isLaterSvD(this.header[col], globalMaxDate, "yyyy") ||
                        isEarlierSvD(this.header[col], globalMinDate, "yyyy"))) {
                    calc_mask[col-begin] = true;
                }
            }

            for (int row = 0; row < this.nrow; row++) {
                if (!mask_row[row]) continue;

                for (int col = begin; col < ncol; col++) {
                    if (!calc_mask[col-begin]) continue;

                    String datePeriode = (String) this.df.get(ind_datePeriode)[row];
                    this.df.get(col)[row] = getSumN(base.pivotTableAllStatutsYearlyN, datePeriode, this.header[col]);
                }
            }
        }
    }
    public void populateTotalSinAllStatutsN(List<Base> bases) {
        int ind_datePeriode = find_in_arr_first_index(this.header, "Date Periode");
        int begin = ncol - 1;

        for (Base base : bases) {
            String police = base.numPolice;

            boolean[] mask_row = new boolean[this.nrow];
            for (int row = 0; row < this.nrow; row++) {
                if (police.equalsIgnoreCase((String) this.c("Contrat")[row])) {
                    mask_row[row] = true;
                }
            }

            for (int row = 0; row < this.nrow; row++) {
                if (!mask_row[row]) continue;

                String datePeriode = (String) this.df.get(ind_datePeriode)[row];
                this.df.get(begin)[row] = getTotalN(base.pivotTableAllStatutsTotalN, datePeriode);
            }
        }
    }


    public void populateMonthFic(Base base) {
        int ind_datePeriode = find_in_arr_first_index(this.header, "Date Periode");
        int begin = ncol - lastAppendSize;

        for (int col = begin; col < ncol; col++) {
            if (!(isLaterSvD(this.header[col], base.globalMaxDateFic,"MM-yyyy") ||
                isEarlierSvD(this.header[col], base.globalMinDateFic,"MM-yyyy"))) {
                this.mask_col[col] = true;
            }
        } // showing mask

        for (String police : base.uniqueNumPoliceValues) {
            Date minDateForPolice = base.numPoliceDateRangeMap.get(police).get(0);
            Date maxDateForPolice = base.numPoliceDateRangeMap.get(police).get(1);
            Map<String, Map<String, Double>> pivotForPolice = base.pivotTableFic.get(police).get(STATUT_FICTIF_FIC);

            boolean[] mask_row = new boolean[this.nrow];
            for (int row = 0; row < this.nrow; row++) {
                if (police.equalsIgnoreCase((String) this.c("Contrat")[row])) {
                    mask_row[row] = true;
                }
            }

            boolean[] calc_mask = new boolean[lastAppendSize];
            for (int col = begin; col < ncol; col++) {
                if (!(isLaterSvD(this.header[col], maxDateForPolice,"MM-yyyy") ||
                    isEarlierSvD(this.header[col], minDateForPolice,"MM-yyyy"))) {
                    calc_mask[col-begin] = true;
                }
            }

            for (int row = 0; row < this.nrow; row++) {
                if (!mask_row[row]) continue;

                for (int col = begin; col < ncol; col++) {
                    if (!calc_mask[col-begin]) continue;

                    String datePeriode = (String) this.df.get(ind_datePeriode)[row];
                    this.df.get(col)[row] = getSumFic(pivotForPolice, datePeriode, this.header[col]);
                }
            }
        }
    }
    public void populateYearFic(Base base) {
        int ind_datePeriode = find_in_arr_first_index(this.header, "Date Periode");
        int begin = ncol - lastAppendSize;

        for (int col = begin; col < ncol; col++) {
            if (!(isLaterSvD(this.header[col], base.globalMaxDateFic,"yyyy") ||
                isEarlierSvD(this.header[col], base.globalMinDateFic,"yyyy"))) {
                this.mask_col[col] = true;
            }
        } // showing mask

        for (String police : base.uniqueNumPoliceValues) {
            Date minDateForPolice = base.numPoliceDateRangeMap.get(police).get(0);
            Date maxDateForPolice = base.numPoliceDateRangeMap.get(police).get(1);
            Map<String, Map<String, Double>> pivotForPolice = base.pivotTableYearlyFic.get(police).get(STATUT_FICTIF_FIC);

            boolean[] mask_row = new boolean[this.nrow];
            for (int row = 0; row < this.nrow; row++) {
                if (police.equalsIgnoreCase((String) this.c("Contrat")[row])) {
                    mask_row[row] = true;
                }
            }

            boolean[] calc_mask = new boolean[lastAppendSize];
            for (int col = begin; col < ncol; col++) {
                if (!(isLaterSvD(this.header[col], maxDateForPolice,"yyyy") ||
                    isEarlierSvD(this.header[col], minDateForPolice,"yyyy"))) {
                    calc_mask[col-begin] = true;
                }
            }

            for (int row = 0; row < this.nrow; row++) {
                if (!mask_row[row]) continue;

                for (int col = begin; col < ncol; col++) {
                    if (!calc_mask[col-begin]) continue;

                    String datePeriode = (String) this.df.get(ind_datePeriode)[row];
                    this.df.get(col)[row] = getSumFic(pivotForPolice, datePeriode, this.header[col]);
                }
            }
        }
    }
    public void populateTotalFic(Base base) {
        int ind_datePeriode = find_in_arr_first_index(this.header, "Date Periode");
        int totalCol = ncol - 1;

        this.mask_col[totalCol] = true;


        for (String police : base.uniqueNumPoliceValues) {

            Map<String, Double> pivotForPolice = base.pivotTableTotalFic.get(police).get(STATUT_FICTIF_FIC);

            boolean[] mask_row = new boolean[this.nrow];
            for (int row = 0; row < this.nrow; row++) {
                if (police.equalsIgnoreCase((String) this.c("Contrat")[row])) {
                    mask_row[row] = true;
                }
            }

            for (int row = 0; row < this.nrow; row++) {
                if (!mask_row[row]) continue;

                String datePeriode = (String) this.df.get(ind_datePeriode)[row];
                this.df.get(totalCol)[row] = getTotal(pivotForPolice, datePeriode);
            }
        }
    }
    public Map<String, ArrayList<String>> getFilteredHeadersAndSubheaders() {
        Map<String, ArrayList<String>> result = new LinkedHashMap<>();
        result.put("base", new ArrayList<>());
        for (int i = 0; i < baseNcol; i++) {
            result.get("base").add(header[i]);
        }
        String currentSubheader = null;

        for (int i = baseNcol; i < subheader.length; i++) {
            if (!subheader[i].isEmpty()) {
                currentSubheader = subheader[i];
                result.put(currentSubheader, new ArrayList<>());
            }

            if (mask_col[i-baseNcol]) {
                result.get(currentSubheader).add(header[i]);
            }
        }

        return result;
    }
    public void printFilteredHeadersAndSubheaders() {
        Map<String, ArrayList<String>> filteredHeadersAndSubheaders = getFilteredHeadersAndSubheaders();

        for (Map.Entry<String, ArrayList<String>> entry : filteredHeadersAndSubheaders.entrySet()) {
            String subheader = entry.getKey();
            ArrayList<String> headers = entry.getValue();

            String headersString = String.join(", ", headers);
            System.out.println(subheader + ": " + headersString);
        }
    }

    public void formatDP() {
        SimpleDateFormat format = new SimpleDateFormat("MM-yyyy");

        Object[] datePeriodeColumn = c("Date Periode");
        Object[] datePeriodeColumnOutput = new Object[this.nrow];
        for (int i = 0; i < datePeriodeColumn.length; i++) {
            Date date = (Date) datePeriodeColumn[i]; // Cast the object to Date
            String formattedDate = format.format(date); // Convert Date to String using the format
            datePeriodeColumnOutput[i] = formattedDate; // Replace the Date with its formatted string
        }

        // Update the column in the dataframe
        int index = find_in_arr_first_index(header, "Date Periode");
        df.set(index, datePeriodeColumnOutput);
    }
    public String getSum(Map<String, Map<String, Double>> pivotTable, String date_sous, String date_surv) {

        // Check for date_sous existence
        Map<String, Double> innerMap = pivotTable.get(date_sous);
        if (innerMap == null) {
            return "";  // or some default value if date_sous doesn't exist
        }

        // Check for date_surv existence and get the value
        Double result = innerMap.get(date_surv);
        if (result == null) {
            return "";  // or some default value if date_surv doesn't exist
        }

        return String.format("%.2f", result);
    }
    public String getTotal(Map<String, Double> pivotTable, String date_sous) {
        Double result = pivotTable.get(date_sous);
        if (result == null) {
            return "";
        }

        return String.format("%.2f", result);
    }
    public String getSumN(Map<String, Map<String, Integer>> pivotTableN, String date_sous, String date_surv) {

        // Check for date_sous existence
        Map<String, Integer> innerMap = pivotTableN.get(date_sous);
        if (innerMap == null) {
            return "";  // or some default value if date_sous doesn't exist
        }

        // Check for date_surv existence and get the value
        Integer result = innerMap.get(date_surv);
        if (result == null) {
            return "";  // or some default value if date_surv doesn't exist
        }

        return String.valueOf(result);
    }
    public String getTotalN(Map<String, Integer> pivotTableN, String date_sous) {
        Integer result = pivotTableN.get(date_sous);
        if (result == null) {
            return "";  // or some default value if date_sous doesn't exist
        }
        return String.valueOf(result);
    }
    public String getSumFic(Map<String, Map<String, Double>> pivotTable, String date_sous, String date_surv) {
        // Check for date_sous existence
        Map<String, Double> innerMap = pivotTable.get(date_sous);
        if (innerMap == null) {
            return "";  // or some default value if date_sous doesn't exist
        }

        // Check for date_surv existence and get the value
        Double result = innerMap.get(date_surv);
        if (result == null) {
            return "";  // or some default value if date_surv doesn't exist
        }

        return String.format("%.2f", result);
    }

    public void appendTable(ArrayList<String> columnNames, String tableName) {
        appendMultipleTables(columnNames, new ArrayList<>(Collections.singletonList(tableName)));
    }
    public void appendMultipleTables(ArrayList<String> columnNames, ArrayList<String> tableNames) {
        int nNewColumns = columnNames.size() * tableNames.size();
        int newNcol = ncol + nNewColumns;

        String[] newHeader = new String[newNcol];
        String[] newSubheader = new String[newNcol];
        Col_types[] newColtypes = new Col_types[newNcol];
        ArrayList<Object[]> newDf = new ArrayList<>(newNcol);

        // Copy old values
        System.arraycopy(header, 0, newHeader, 0, ncol);
        System.arraycopy(subheader, 0, newSubheader, 0, ncol);
        System.arraycopy(coltypes, 0, newColtypes, 0, ncol);
        newDf.addAll(df);

        // Initialize new values
        for (int i = ncol, k = 0; i < newNcol; i += columnNames.size(), k++) {
            for (int j = 0; j < columnNames.size(); j++) {
                if(j == 0) {
                    newSubheader[i + j] = tableNames.get(k); // name of the table
                } else {
                    newSubheader[i + j] = ""; // blank for others
                }
                newHeader[i + j] = columnNames.get(j); // name of the columns
                newColtypes[i + j] = Col_types.STR; // assign STR type
                newDf.add(new Object[nrow]); // Initialize column data
            }
        }

        // Update class properties
        header = newHeader;
        subheader = newSubheader;
        coltypes = newColtypes;
        df = newDf;
        ncol = newNcol;
    }
    public void addTotal() {
        ArrayList<String> columnNames = new ArrayList<>(Collections.singletonList("Total"));
        appendTable(columnNames, "Total");
    }
    public void addAnnees() {
        ArrayList<String> columnNames = new ArrayList<>();
        ArrayList<String> subHeaderNames = new ArrayList<>();
        for (int year = 2013; year <= 2026; year++) {
            columnNames.add(String.valueOf(year));
        }
        subHeaderNames.add(""); // bequille
        appendMultipleTables(columnNames, subHeaderNames);
    }
    public void byMonth() {
        ArrayList<String> columnNames = new ArrayList<>();
        SimpleDateFormat format = new SimpleDateFormat("MM-yyyy");

        // Define the months and years
        String[] months = {"01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12"};

        // Manually adding November and December for 2013
        columnNames.add("11-2013");
        columnNames.add("12-2013");

        // Now loop through all months for the subsequent years
        for (int year = 2014; year <= 2026; year++) {
            for (String month : months) {
                columnNames.add(month + "-" + year);
            }
        }
        appendMultipleTables(columnNames, new ArrayList<>(Collections.singletonList("Monthly")));
    }
    public void addFicMAT(Base baseFic) {
        String status = STATUT_FICTIF_FIC;
        int begin;
        int tableName_ind;

        begin = ncol;
        tableName_ind = header.length;
        byMonth();
        subheader[tableName_ind] = status + " mensuel";
        appendUpdate(begin);
        populateMonthFic(baseFic);

        begin = ncol;
        tableName_ind = header.length;
        addAnnees();
        subheader[tableName_ind] = status + " annuel";
        appendUpdate(begin);
        populateYearFic(baseFic);

        begin = ncol;
        tableName_ind = header.length;
        addTotal();
        subheader[tableName_ind] = status + " total";
        appendUpdate(begin);
        populateTotalFic(baseFic);

    }
    public void addProvisions(List<Base> bases) {
        int begin = ncol;
        double[] total = new double[nrow];

        Map<String, Double> coutMoyEnCoursMap = new HashMap<>();
        Map<String, Double> coutMoyEnCoursAccepteMap = new HashMap<>();
        Map<String, Map<String, List<Integer>>> nEnCoursMap = new HashMap<>();
        Map<String, Map<String, List<Integer>>> nEnCoursAccepteMap = new HashMap<>();
        for (Base base : bases) {
            nEnCoursMap.put(base.numPolice, base.nEnCours);
            nEnCoursAccepteMap.put(base.numPolice, base.nEnCoursAccepte);
            coutMoyEnCoursMap.put(base.numPolice, base.coutMoyenEnCours);
            coutMoyEnCoursAccepteMap.put(base.numPolice, base.coutMoyenEnCoursAccepte);
        }

        Object[] contratColumn = this.c("Contrat");
        Object[] datePeriodeColumn = this.c("Date Periode");

        populateCoutMoyenColumn(contratColumn, coutMoyEnCoursMap, total, "En cours");
        populateProvisionsColumns(contratColumn, datePeriodeColumn, nEnCoursMap, coutMoyEnCoursMap, total,"En cours");

        populateCoutMoyenColumn(contratColumn, coutMoyEnCoursAccepteMap, total,"En cours - accepté");
        populateProvisionsColumns(contratColumn, datePeriodeColumn, nEnCoursAccepteMap, coutMoyEnCoursAccepteMap, total,"En cours - accepté");

        // Add total column
        this.df.add(Arrays.stream(total).mapToObj(val -> String.format("%.2f", val)).toArray(String[]::new));
        header = Arrays.copyOf(header, ncol + 1);
        header[ncol] = "Total provisions";
        ncol++;
        subheader = Arrays.copyOf(subheader, ncol);
        Arrays.fill(subheader, subheader.length - (ncol - begin), subheader.length, "");
        appendUpdateProvisions(begin);
    }
    private void populateProvisionsColumns(Object[] contratColumn, Object[] datePeriodeColumn,
                                           Map<String, Map<String, List<Integer>>> dataMap,
                                           Map<String, Double> coutMoyenMap,
                                           double[] total, String label) {
        int yearDif = 2026 - 2013;
        for (int year = 0; year <= yearDif; year++) {
            this.df.add(new String[nrow]);
        }

        for (int i = 0; i < nrow; i++) {
            String contratValue = (String) contratColumn[i];

            String datePeriodeValue = (String) datePeriodeColumn[i];

            // Fetch the data list for the current contract and date
            Map<String, List<Integer>> dateMap = dataMap.get(contratValue);
            if (dateMap != null && dateMap.containsKey(datePeriodeValue)) {
                List<Integer> yearlyCounts = dateMap.get(datePeriodeValue);

                // Fetch and compute the provision value for each year
                for (int yearNum = 0; yearNum <= yearDif; yearNum++) {
                    int countForYear = (yearNum < yearlyCounts.size()) ? yearlyCounts.get(yearNum) : 0;
                    double provisionValue = countForYear * coutMoyenMap.get(contratValue);
                    this.c(this.ncol + yearNum)[i] = String.format("%.2f", provisionValue);
                    total[i] += provisionValue;
                }
            } else {
                // If there's no data for this contract and date, fill the columns with 0s
                for (int yearNum = 0; yearNum <= yearDif; yearNum++) {
                    this.c(this.ncol + yearNum)[i] = "0";
                }
            }
        }
        this.ncol += yearDif + 1; // add 1 for year difference
        updateHeaderForProvisions(label);
    }
    private void populateCoutMoyenColumn(Object[] contratColumn, Map<String, Double> coutMoyenMap, double[] total, String label) {
        this.df.add(new String[nrow]);
        for (int i = 0; i < nrow; i++) {
            Double value = coutMoyenMap.get((String) contratColumn[i]);
            if(value == null) continue;
            this.c(ncol)[i] = String.format("%.2f", value);
        }
        this.ncol++;
        updateHeaderForCoutMoyen(label);
    }
    private void updateHeaderForCoutMoyen(String statut) {
        List<String> newHeaders = new ArrayList<>(Arrays.asList(header));
        newHeaders.add("Cout Moyen: " + statut);
        header = newHeaders.toArray(new String[0]);
    }
    private void updateHeaderForProvisions(String statut) {
        List<String> newHeaders = new ArrayList<>(Arrays.asList(header));
        for (int year = 2013; year <= 2026; year++) {
            newHeaders.add("Provisions: " + statut + " " + year);
        }
        header = newHeaders.toArray(new String[0]);
    }
    public void addSinMAT(List<Base> bases) {
        String statut = "Sinistre Reglement";
        int begin;
        int tableName_ind;

        begin = ncol;
        tableName_ind = header.length;
        byMonth();
        subheader[tableName_ind] = statut + " mensuel";
        appendUpdate(begin);
        populateMonthSinAllStatuts(bases);

        begin = ncol;
        tableName_ind = header.length;
        addAnnees();
        subheader[tableName_ind] = statut + " annuel";
        appendUpdate(begin);
        populateYearSinAllStatuts(bases);

        begin = ncol;
        tableName_ind = header.length;
        addTotal();
        subheader[tableName_ind] = statut + " total";
        appendUpdate(begin);
        populateTotalSinAllStatuts(bases);

        for (String statutEs : uniqueStatutsEstimate) {
            begin = ncol;
            tableName_ind = header.length;
            byMonth();
            subheader[tableName_ind] = statutEs + " mensuel";
            appendUpdate(begin);
            populateMonthSin(bases, statutEs);

            begin = ncol;
            tableName_ind = header.length;
            addAnnees();
            subheader[tableName_ind] = statutEs + " annuel";
            appendUpdate(begin);
            populateYearSin(bases, statutEs);

            begin = ncol;
            tableName_ind = header.length;
            addTotal();
            subheader[tableName_ind] = statutEs + " total";
            appendUpdate(begin);
            populateTotalSin(bases, statutEs);
        }

        statut = "Sinistre Nombre";

        begin = ncol;
        tableName_ind = header.length;
        byMonth();
        subheader[tableName_ind] = statut + " mensuel";
        appendUpdate(begin);
        populateMonthSinAllStatutsN(bases);

        begin = ncol;
        tableName_ind = header.length;
        addAnnees();
        subheader[tableName_ind] = statut + " annuel";
        appendUpdate(begin);
        populateYearSinAllStatutsN(bases);

        begin = ncol;
        tableName_ind = header.length;
        addTotal();
        subheader[tableName_ind] = statut + " total";
        appendUpdate(begin);
        populateTotalSinAllStatutsN(bases);

        for (String statutEs : uniqueStatutsEstimate) {
            begin = ncol;
            tableName_ind = header.length;
            byMonth();
            subheader[tableName_ind] = statutEs + " mensuel";
            appendUpdate(begin);
            populateMonthSinN(bases, statutEs);

            begin = ncol;
            tableName_ind = header.length;
            addAnnees();
            subheader[tableName_ind] = statutEs + " annuel";
            appendUpdate(begin);
            populateYearSinN(bases, statutEs);
            System.out.println(this.header[1717] + " " + this.c(1717)[30]);
            begin = ncol;
            tableName_ind = header.length;
            addTotal();
            subheader[tableName_ind] = statutEs + " total";
            appendUpdate(begin);
            populateTotalSinN(bases, statutEs);
            System.out.println(this.header[1717] + " " + this.c(1717)[30]);

        }
    }
    private void appendUpdate(int begin) {
        int end;
        boolean[] newMaskCol;
        end = ncol;
        lastAppendSize = end - begin;
        newMaskCol = new boolean[ncol];
        System.arraycopy(mask_col, 0, newMaskCol, 0, mask_col.length);
        mask_col = newMaskCol;
        for (int i = begin; i < end; i++) {
            if(!subheader[i].isEmpty()) {
                mask_col[i] = true;
            }
        }
    }
    private void appendUpdateProvisions(int begin) {
        int end;
        boolean[] newMaskCol;

        end = ncol;
        lastAppendSize = end - begin;

        newMaskCol = new boolean[ncol];
        System.arraycopy(mask_col, 0, newMaskCol, 0, mask_col.length);
        mask_col = newMaskCol;

        Arrays.fill(mask_col, begin, end, true);
    }
    public void saveToCSVFile(boolean applyMask) throws IOException {
        String filePath = fullPath.replace(".xlsx", "_extended.csv");

        // Create a FileWriter and a BufferedWriter to write text to the file in UTF-8 encoding
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(filePath), StandardCharsets.UTF_8))) {

            // Write BOM for UTF-8
            writer.write('\ufeff');
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");

            if (applyMask) {
                // Determine which columns to include in the CSV
                boolean[] includeColumn = new boolean[ncol];
                for (int colIndex = 0; colIndex < ncol; colIndex++) {
                    if (mask_col[colIndex]) {
                        includeColumn[colIndex] = true;
                    }
                }
                // Write header row
                writeRow(writer, header, includeColumn);
                // Write subheader row
                writeRow(writer, subheader, includeColumn);
                // Write data rows
                for (int rowIndex = 0; rowIndex < nrow; rowIndex++) {
                    boolean firstCol = true;
                    for (int colIndex = 0; colIndex < ncol; colIndex++) {
                        if (!includeColumn[colIndex]) {
                            continue; // Skip this column
                        }
                        if (!firstCol) {
                            writer.write(";");
                        }
                        Object value = df.get(colIndex)[rowIndex];
                        if (value != null) {
                            if (value instanceof Date) {
                                writer.write(sdf.format((Date) value));
                            } else {
                                writer.write(value.toString());
                            }
                        }
                        firstCol = false;
                    }
                    writer.newLine(); // Move to the next line
                }
            } else {
                // Write header row
                writeRow(writer, header);
                // Write subheader row
                writeRow(writer, subheader);
                // Write data rows
                for (int rowIndex = 0; rowIndex < nrow; rowIndex++) {
                    boolean firstCol = true;
                    for (int colIndex = 0; colIndex < ncol; colIndex++) {
                        if (!firstCol) {
                            writer.write(";");
                        }
                        Object value = df.get(colIndex)[rowIndex];
                        if (value != null) {
                            if (value instanceof Date) {
                                writer.write(sdf.format((Date) value));
                            } else {
                                writer.write(value.toString());
                            }
                        }
                        firstCol = false;
                    }
                    writer.newLine(); // Move to the next line
                }
            }
        }
    }
    //    public void addColumnByType(char type, boolean dispatchByStatus, Set<String> uniqueStatuts) {
//        int begin = ncol;
//        if (dispatchByStatus) {
//            for (String status : uniqueStatuts) {
//                // Assuming the status is to be added to the header, just once
//                int tableName_ind = header.length;
//                byMonth();
//                subheader[tableName_ind] = status + " mensuel";
//                tableName_ind = header.length;
//                addAnnees();
//                this.populateYearFic(baseFic);
//                subheader[tableName_ind] = status + " annuel";
//                tableName_ind = header.length;
//                addTotal();
//                subheader[tableName_ind] = status + " total";
//            }
//        } else {
////            int status_ind = header.length;
//            switch (type) {
//                case 'T' -> addTotal();
//                case 'Y' -> addAnnees();
//                case 'M' -> byMonth();
//            }
////            subheader[status_ind] = STATUT_FICTIF_FIC;
//        }
//        int end = ncol;
//        this.lastAppendSize = end - begin;
//        boolean[] newMaskCol = new boolean[ncol];
//        System.arraycopy(this.mask_col, 0, newMaskCol, 0, this.mask_col.length);
//        this.mask_col = newMaskCol;
//        for (int i = begin; i < end; i++) {
//            if(!this.subheader[i].isEmpty()) {
//                this.mask_col[i] = true;
//            }
//        }
//    }
    private void writeRow(BufferedWriter writer, String[] row, boolean[] includeColumn) throws IOException {
        boolean firstCol = true;
        for (int colIndex = 0; colIndex < row.length; colIndex++) {
            if (!includeColumn[colIndex]) {
                continue;
            }

            if (!firstCol) {
                writer.write(";");
            }
            writer.write(row[colIndex]);
            firstCol = false;
        }
        writer.newLine();
    }
    private void writeRow(BufferedWriter writer, String[] row) throws IOException {
        boolean firstCol = true;
        for (String s : row) {
            if (!firstCol) {
                writer.write(";");
            }
            writer.write(s);
            firstCol = false;
        }
        writer.newLine();
    }
}
