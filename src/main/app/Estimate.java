package main.app;

import com.monitorjbl.xlsx.StreamingReader;
import org.apache.poi.ss.usermodel.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import java.util.Date;

import static java.lang.Math.max;
import static main.app.App.*;
import static main.app.Base.STATUT_FICTIF_FIC;

public class Estimate extends DF {
    String key;
    public String[] subheader;
    public List<Date> headerCalcul;
    int baseNcol = 0;
    int lastAppendSize = 0;
    String[] totalPA;
    String[] totalPAaDate;
    String[] tauxAcquisition;
    Double[] colSPprevi;
    Double[] colPB;
    Double[] colSinUltime;

    boolean[] mask_col;
    protected Stopwatch stopwatch = new Stopwatch();
    public Set<String> uniqueStatutsEstimate;
    public Set<String> uniqueNumPoliceEstimate = new HashSet<>();
    public static Map<String, Map<String, Date>> minMaxDateSousMapEstimate = new HashMap<>();

    public static final HashMap<String, Integer> monthMap = new HashMap<>() {{
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
        generateMinMaxDateSousMap();
        formatDP();
        deleteRegul();
        if (path.contains("France")) {
            deleteDBP();
        }
        baseNcol = ncol;
        mask_col = new boolean[ncol];
        Arrays.fill(mask_col, true);
    } //file_sin

    public void getUniqueStatutsFromMap() {
        List<String> sortedStatuts = new ArrayList<>(globalStatutDateRangeMap.keySet());

        // Add the required strings if they are not present in the list
        List<String> requiredStatuts = Arrays.asList("en cours", "en cours - accepté", "en attente de prescription");
        for (String reqStatut : requiredStatuts) {
            if (!sortedStatuts.contains(reqStatut)) {
                sortedStatuts.add(reqStatut);
            }
        }

        // Sort the list so that "terminé - accepté" is at the beginning and others as required
        sortedStatuts.sort((statut1, statut2) -> {
            if (statut1.equalsIgnoreCase("terminé - accepté")) {
                return -1;
            } else if (statut2.equalsIgnoreCase("terminé - accepté")) {
                return 1;
            }
            // You can add additional sorting rules for "en cours", "en cours - accepté",
            // and "en attente de prescription" if required
            return statut1.compareTo(statut2);
        });

        this.uniqueStatutsEstimate = new LinkedHashSet<>(sortedStatuts);
    }

    public void getUniqueNumPoliceEstimate() {
        int contratIndex = find_in_arr_first_index(header, "Contrat");
        for (int i = 0; i < nrow; i++) {
            Object[] row = r(i);
            String contratValue = (String) row[contratIndex];
            if (contratValue != null && !contratValue.trim().isEmpty()) {
                uniqueNumPoliceEstimate.add(contratValue);
            }
        }
    }

    public void populateMonthSin(List<Base> bases, String statut) throws ParseException {
        int ind_datePeriode = find_in_arr_first_index(this.header, "Date Periode");
        int begin = ncol - lastAppendSize;

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        Date defaultDate = sdf.parse("1/11/2013");

        List<Date> interMap = globalStatutDateRangeMap.getOrDefault(statut, Arrays.asList(defaultDate, defaultDate));

        Date minDateForStatus = interMap.get(0) != null ? interMap.get(0) : defaultDate;
        Date maxDateForStatus = interMap.get(1) != null ? interMap.get(1) : defaultDate;


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
    public void populateYearSin(List<Base> bases, String statut) throws ParseException {
        int ind_datePeriode = find_in_arr_first_index(this.header, "Date Periode");
        int begin = ncol - lastAppendSize;

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        Date defaultDate = sdf.parse("1/11/2013");

        List<Date> interMap = globalStatutDateRangeMap.getOrDefault(statut, Arrays.asList(defaultDate, defaultDate));

        Date minDateForStatus = interMap.get(0) != null ? interMap.get(0) : defaultDate;
        Date maxDateForStatus = interMap.get(1) != null ? interMap.get(1) : defaultDate;

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

    public void populateMonthSinN(List<Base> bases, String statut) throws ParseException {
        int ind_datePeriode = find_in_arr_first_index(this.header, "Date Periode");
        int begin = ncol - lastAppendSize;

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        Date defaultDate = sdf.parse("1/11/2013");

        List<Date> interMap = globalStatutDateRangeMap.getOrDefault(statut, Arrays.asList(defaultDate, defaultDate));

        Date minDateForStatus = interMap.get(0) != null ? interMap.get(0) : defaultDate;
        Date maxDateForStatus = interMap.get(1) != null ? interMap.get(1) : defaultDate;

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
                    this.df.get(col)[row] = getSumN(currentPivotTableN, datePeriode, this.header[col]);
                }
            }
        }
    }
    public void populateYearSinN(List<Base> bases, String statut) throws ParseException {
        int ind_datePeriode = find_in_arr_first_index(this.header, "Date Periode");
        int begin = ncol - lastAppendSize;

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        Date defaultDate = sdf.parse("1/11/2013");

        List<Date> interMap = globalStatutDateRangeMap.getOrDefault(statut, Arrays.asList(defaultDate, defaultDate));

        Date minDateForStatus = interMap.get(0) != null ? interMap.get(0) : defaultDate;
        Date maxDateForStatus = interMap.get(1) != null ? interMap.get(1) : defaultDate;

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
    public void populateMonthFicN(Base base) {
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
            Map<String, Map<String, Integer>> pivotForPolice = base.pivotTableFicN.get(police).get(STATUT_FICTIF_FIC);

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
                    this.df.get(col)[row] = getSumFicN(pivotForPolice, datePeriode, this.header[col]);
                }
            }
        }
    }
    public void populateYearFicN(Base base) {
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
            Map<String, Map<String, Integer>> pivotForPolice = base.pivotTableFicYearlyN.get(police).get(STATUT_FICTIF_FIC);

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
                    this.df.get(col)[row] = getSumFicN(pivotForPolice, datePeriode, this.header[col]);
                }
            }
        }
    }
    public void populateTotalFicN(Base base) {
        int ind_datePeriode = find_in_arr_first_index(this.header, "Date Periode");
        int totalCol = ncol - 1;

        this.mask_col[totalCol] = true;

        for (String police : base.uniqueNumPoliceValues) {

            Map<String, Integer> pivotForPolice = base.pivotTableFicTotalN.get(police).get(STATUT_FICTIF_FIC);

            boolean[] mask_row = new boolean[this.nrow];
            for (int row = 0; row < this.nrow; row++) {
                if (police.equalsIgnoreCase((String) this.c("Contrat")[row])) {
                    mask_row[row] = true;
                }
            }

            for (int row = 0; row < this.nrow; row++) {
                if (!mask_row[row]) continue;

                String datePeriode = (String) this.df.get(ind_datePeriode)[row];
                this.df.get(totalCol)[row] = getTotalN(pivotForPolice, datePeriode);
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

    public void deleteRegul() {
        Object[] regul = this.c("Régularisation");
        Object[] contrat = this.c("Contrat");
        Object[] date = this.c("Date Periode");
        ArrayList<Integer> rowsToDelete = new ArrayList<>();
        for (int i = 0; i < this.nrow; i++) {
            if (regul[i].equals("OUI")) {
                int origin = -1;
                if (date[i].equals(date[i-1]) && contrat[i].equals(contrat[i-1])) {
                    origin = i - 1;
                } else {
                    for (int j = 0; j < this.nrow; j++) {
                        if (date[i].equals(date[j]) && contrat[i].equals(contrat[j])) {
                            origin = j;
                            break;
                        }
                    }
                }
                if(origin == -1) {
                    System.out.println("REGUL ERROR");
                }
                for (int col = 0; col < this.ncol; col++) {
                    if (header[col].startsWith("MONTANT") || header[col].startsWith("NOMBRE")) {
                        double valueI = safeParseDouble(this.c(col)[i].toString());
                        double valueOrigin = safeParseDouble(this.c(col)[origin].toString());
                        this.c(col)[origin] = String.valueOf(valueI + valueOrigin);
                    }
                }
                rowsToDelete.add(i);
            }
        }
        deleteRows(rowsToDelete);
    }
    public void deleteDBP() {
        Object[] contrat = this.c("Contrat");
        Object[] date = this.c("Date Periode");
        ArrayList<Integer> rowsToDelete = new ArrayList<>();
        for (int i = 0; i < this.nrow; i++) {
            if (contrat[i].equals("ICIDBP17-1") || contrat[i].equals("ICIDBP17-2")) {
                int origin = -1;
                for (int j = 0; j < this.nrow; j++) {
                    if (date[i].equals(date[j]) && "ICIDBP17".equals(contrat[j])) {
                        origin = j;
                        break;
                    }
                }
                if (origin == -1) {
                    contrat[i] = "ICIDBP17";
                    continue;
                }
                for (int col = 0; col < this.ncol; col++) {
                    if (header[col].startsWith("MONTANT") || header[col].startsWith("NOMBRE")) {
                        double valueI = safeParseDouble(this.c(col)[i].toString());
                        double valueOrigin = safeParseDouble(this.c(col)[origin].toString());
                        this.c(col)[origin] = String.valueOf(valueI + valueOrigin);                    }
                }
                rowsToDelete.add(i);
            }
        }
        deleteRows(rowsToDelete);
    }
    private double safeParseDouble(String s) {
        if (s == null || s.isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public void deleteRows(ArrayList<Integer> rowsToDelete) {
        if (rowsToDelete.isEmpty()) {
            return;
        }

        // Sort in descending order to ensure that we're removing indices from the end first
        rowsToDelete.sort((a, b) -> b - a);

        // Iterate over columns in df
        for (int colIndex = 0; colIndex < ncol; colIndex++) {
            Object[] column = df.get(colIndex);
            for (int rowIndex : rowsToDelete) {
                if (rowIndex >= 0 && rowIndex < nrow) {
                    column[rowIndex] = null; // Mark for deletion
                }
            }

            // Create a new column without the null (deleted) values
            Object[] newColumn = new Object[nrow - rowsToDelete.size()];
            int newIndex = 0;
            for (Object value : column) {
                if (value != null) {
                    newColumn[newIndex++] = value;
                }
            }

            // Replace the old column with the new column in df
            df.set(colIndex, newColumn);
        }

        // Update nrow since we've removed rows
        nrow -= rowsToDelete.size();
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

    public void generateMinMaxDateSousMap() {
        // Get "Date Periode" and "Contrat" columns
        Object[] datePeriodes = c("Date Periode");
        Object[] contrats = c("Contrat");

        for (int i = 0; i < nrow; i++) {
            Date date = (Date) datePeriodes[i];
            String contrat = (String) contrats[i];

            // Update the map for the "Contrat" value
            minMaxDateSousMapEstimate.putIfAbsent(contrat, new HashMap<>());
            Map<String, Date> currentDateMap = minMaxDateSousMapEstimate.get(contrat);

            // Update min and max dates
            Date currentMinDate = currentDateMap.getOrDefault("min", date);
            Date currentMaxDate = currentDateMap.getOrDefault("max", date);

            if (!currentDateMap.containsKey("min")) {
                currentDateMap.put("min", date);
            }
            if (!currentDateMap.containsKey("max")) {
                currentDateMap.put("max", date);
            }
            if (date.before(currentMinDate)) {
                currentDateMap.put("min", date);
            }
            if (date.after(currentMaxDate)) {
                currentDateMap.put("max", date);
            }
        }
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
    public String getSumFicN(Map<String, Map<String, Integer>> pivotTable, String date_sous, String date_surv) {
        // Check for date_sous existence
        Map<String, Integer> innerMap = pivotTable.get(date_sous);
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
//        System.arraycopy(coltypes, 0, newColtypes, 0, ncol);
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
    public void addMois() {
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
        addMois();
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

        begin = ncol;
        tableName_ind = header.length;
        addMois();
        subheader[tableName_ind] = status + " mensuel nombre";
        appendUpdate(begin);
        populateMonthFicN(baseFic);

        begin = ncol;
        tableName_ind = header.length;
        addAnnees();
        subheader[tableName_ind] = status + " annuel nombre";
        appendUpdate(begin);
        populateYearFicN(baseFic);

        begin = ncol;
        tableName_ind = header.length;
        addTotal();
        subheader[tableName_ind] = status + " total nombre";
        appendUpdate(begin);
        populateTotalFicN(baseFic);

    }
    public void addSinMAT(List<Base> bases) throws ParseException {
        String statut = "Sinistre";
        int begin;
        int tableName_ind;

        begin = ncol;
        tableName_ind = header.length;
        addMois();
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
            if (statutEs.isEmpty()) continue;
            begin = ncol;
            tableName_ind = header.length;
            addMois();
            subheader[tableName_ind] = "Statut " + statutEs + " mensuel";
            appendUpdate(begin);
            populateMonthSin(bases, statutEs);

            begin = ncol;
            tableName_ind = header.length;
            addAnnees();
            subheader[tableName_ind] = "Statut " + statutEs + " annuel";
            appendUpdate(begin);
            populateYearSin(bases, statutEs);

            begin = ncol;
            tableName_ind = header.length;
            addTotal();
            subheader[tableName_ind] = "Statut " + statutEs + " total";
            appendUpdate(begin);
            populateTotalSin(bases, statutEs);
        }

        statut = "Sinistre Nombre";

        begin = ncol;
        tableName_ind = header.length;
        addMois();
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
            if (statutEs.isEmpty()) continue;

            begin = ncol;
            tableName_ind = header.length;
            addMois();
            subheader[tableName_ind] = "Nombre " + statutEs + " mensuel";
            appendUpdate(begin);
            populateMonthSinN(bases, statutEs);

            begin = ncol;
            tableName_ind = header.length;
            addAnnees();
            subheader[tableName_ind] = "Nombre " + statutEs + " annuel";
            appendUpdate(begin);
            populateYearSinN(bases, statutEs);

            begin = ncol;
            tableName_ind = header.length;
            addTotal();
            subheader[tableName_ind] = "Nombre " + statutEs + " total";
            appendUpdate(begin);
            populateTotalSinN(bases, statutEs);
        }
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
        this.addCol("","Total provisions");

        for (int col = begin; col < ncol; col++) {
            if (subheader[col] == null) subheader[col] = "";
        }
        appendUpdateKeepAll(begin);
    }
    public void addPrimesAcquises() {
        int begin = ncol;
        addMois();
        subheader[begin] = "Primes acquises mensuel";
        appendUpdateKeepAll(begin);
        populatePrimesAcquisesMonthTotal();

        begin = ncol;
        addAnnees();
        subheader[begin] = "Primes acquises annuel";
        appendUpdateKeepAll(begin);
        populatePrimesAcquisesYearly();

        begin = ncol;
        this.df.add(totalPA);
        this.df.add(totalPAaDate);
        ncol+=2;
        // Extend and populate header and subheader
        header = Arrays.copyOf(header, ncol);
        subheader = Arrays.copyOf(subheader, ncol);
        subheader[ncol - 2] = "Total primes acquises";
        subheader[ncol - 1] = "Total PA à date";
        header[ncol - 2] = ""; header[ncol - 1] = "";
        appendUpdateKeepAll(begin);

    }
    public void populatePrimesAcquisesMonthTotal() {
        int ind_datePeriode = find_in_arr_first_index(this.header, "Date Periode");
        int ind_contrat = find_in_arr_first_index(this.header, "Contrat");
        int begin = ncol - lastAppendSize;
        int monthBegin;
        double[] total = new double[nrow];
        double[] total_aDate = new double[nrow];
        double[] taux = new double[nrow];

        // Create a map to store contractKey and its corresponding count of missing dateKeys.
        Map<String, Integer> warningMap = new HashMap<>();

        // Get today's month and year
        Calendar today = Calendar.getInstance();
        int currentYear = today.get(Calendar.YEAR);
        int currentMonth = today.get(Calendar.MONTH) + 1;  // Calendar.MONTH is zero-based

        for (int i = 0; i < nrow; i++) {
            String contractKey = (String) this.c(ind_contrat)[i];
            String dateKey = (String) this.c(ind_datePeriode)[i];
            String combinedKey = contractKey + "_" + dateKey;

            List<Object> values = TableCoefAcquisition.getResultMap().get(combinedKey.toLowerCase());
            if (values == null) {
                // Update the count for the contractKey in the warning map.
                warningMap.put(contractKey, warningMap.getOrDefault(contractKey, 0) + 1);
                System.out.println("didnt find coef acquis for " + combinedKey);
                continue;
            }

            Double prime = (Double) values.get(1);
            float[] coefs = (float[]) values.get(2);

            // Extract the year and month from the dateKey which is in "MM-yyyy" format.
            String[] parts = dateKey.split("-");
            int month = Integer.parseInt(parts[0]);
            int year = Integer.parseInt(parts[1]);

            monthBegin = -1;
            for (int col = begin; col < ncol; col++) {
                if (header[col].equals(dateKey)) {
                    monthBegin = col; break;
                }
            }

            for (int col = monthBegin, coefInd = 0; col < ncol && coefInd < coefs.length; col++, coefInd++) {
                double value = prime * coefs[coefInd];
                this.c(col)[i] = String.format("%.2f", value);

                // Add to the total
                total[i] += value;

                // If the date is less than or equal to today's month/year, add to totalsToToday
                if (year < currentYear || (year == currentYear && month <= currentMonth)) {
                    total_aDate[i] += value;
                }

                // Adjust the month and year for the next iteration
                month++;
                if (month > 12) {
                    month = 1;
                    year++;
                }
            }
        }

        totalPA = convertToStringArrayWithTwoDecimals(total);
        totalPAaDate = convertToStringArrayWithTwoDecimals(total_aDate);
        for (int i = 0; i < nrow; i++) {
            taux[i] = total_aDate[i] / total[i];
        }
        tauxAcquisition = convertToStringArrayWithTwoDecimals(taux);

        // Print out the warning messages after iterating through all rows.
        for (Map.Entry<String, Integer> entry : warningMap.entrySet()) {
            System.out.println("Warning pour Police " + entry.getKey() + ": coef non trouvé pour " + entry.getValue() + " mois.");
        }
    }
    public void populatePrimesAcquisesYearly() {
        int ind_datePeriode = find_in_arr_first_index(this.header, "Date Periode");
        int ind_contrat = find_in_arr_first_index(this.header, "Contrat");
        int begin = ncol - lastAppendSize;

        // Create a map to store contractKey and its corresponding count of missing dateKeys.
        Map<String, Integer> warningMap = new HashMap<>();

        for (int i = 0; i < nrow; i++) {
            String contractKey = (String) this.c(ind_contrat)[i];
            String dateKey = (String) this.c(ind_datePeriode)[i];
            String combinedKey = contractKey + "_" + dateKey;

            // Extract the year and month from the dateKey which is in "MM-yyyy" format.
            String[] parts = dateKey.split("-");
            int month = Integer.parseInt(parts[0]);
            String yearKey = parts[1];

            List<Object> values = TableCoefAcquisition.getResultMap().get(combinedKey.toLowerCase());
            if (values == null) {
                // Update the count for the contractKey in the warning map.
                warningMap.put(contractKey, warningMap.getOrDefault(contractKey, 0) + 1);
                continue;
            }

            Double prime = (Double) values.get(1);
            float[] coefs = (float[]) values.get(2);

            int coefIndex = 0;
            while (coefIndex < coefs.length) {
                int monthsRemaining = 12 - month + 1;  // Including the current month
                float accumulatedCoefficient = 0f;
                for (int j = 0; j < monthsRemaining && coefIndex < coefs.length; j++) {
                    accumulatedCoefficient += coefs[coefIndex];
                    coefIndex++;
                }

                int yearColumnIndex = -1;
                for (int col = begin; col < ncol; col++) {
                    if (header[col].equals(yearKey)) {
                        yearColumnIndex = col;
                        break;
                    }
                }

                if (yearColumnIndex == -1) {
//                    System.out.println("Error: No column found for year " + yearKey);
//                    break;
                    continue;
                }

                double value = prime * accumulatedCoefficient;
                this.c(yearColumnIndex)[i] = String.format("%.2f", value);

                // Reset for the next year
                yearKey = String.valueOf(Integer.parseInt(yearKey) + 1);
                month = 1;
            }
        }

        // Print out the warning messages after iterating through all rows.
        for (Map.Entry<String, Integer> entry : warningMap.entrySet()) {
            System.out.println("Warning pour Police " + entry.getKey() + ": coef non trouvé pour " + entry.getValue() + " annees");
        }
    }
/*    S/P previ hors PB
    S/P si pas réel acquis avec provision
    S/P si pas reel ultime avant PB
    S/P si pas reel ultime apres PB
            LastTriangle*/

    public void addSP() {
        int begin = ncol;

        String[] colsToAdd = new String[] {"PB","S/P previ hors PB","S/P si pas réel acquis avec provision",
                "S/P si pas reel ultime avant PB","S/P si pas reel ultime apres PB", "Sinistre Ultime"};
        addColumnsString(colsToAdd);
        int startTriangle = ncol;
        addMois();
        appendUpdateKeepAll(begin);
        subheader[startTriangle] = "SINISTRES PREVISIONNEL SI PAS REEL comptable";

        populatePBetSPprevi();
        populateLT(startTriangle);
        populateCoefsSP();
    }
    private void populateLT (int startCol) {
        int trianglePAindex = find_in_arr_first_index(subheader, "Primes acquises mensuel");
        int triangleComptaIndex = find_in_arr_first_index(subheader, "Comptable mensuel");
        int cmEncours = find_in_arr_first_index(subheader, "Cout Moyen: En cours");
        int cmEncoursAcc = find_in_arr_first_index(subheader, "Cout Moyen: En cours - accepté");
        int nEnCours = find_in_arr_first_index(subheader, "Statut en cours mensuel");
        int nEnAttente = find_in_arr_first_index(subheader, "Statut en attente de prescription mensuel");
        int nEnCoursAcc = find_in_arr_first_index(subheader, "Statut en cours - accepté mensuel");
        int regulIndex = find_in_arr_first_index(header, "Régularisation");

        int[] indexes = {trianglePAindex,triangleComptaIndex,cmEncours,cmEncoursAcc,nEnCours,nEnAttente,nEnCoursAcc,regulIndex};

        for (int index : indexes) {
            if (index == -1) {
                throw new RuntimeException("Impossible de calculer SP, une des colonnes absente");
            }
        }

        colSinUltime = new Double[nrow]; // Initialize the SinUltime array to store the sums.

        boolean regul;
        for (int i = 0; i < nrow; i++) {
            double rowSum = 0.0; // To keep track of the sum for the current row.

            regul = "oui".equalsIgnoreCase((String) this.c(regulIndex)[i]);
            if (regul) {
                for (int col = startCol; col < ncol; col++) {
                    this.c(col)[i] = 0;
                }
            } else {
                for (int col = startCol, offset = 0; col < ncol; col++, offset++) {
                    double currentCellValue;

                    if (isMonthAfterOrEQCurrent(header[col])) {
                        currentCellValue = colSPprevi[i] * parseValueAt(trianglePAindex + offset, i);
                    } else {
                        double triangleComptaValue = parseValueAt(triangleComptaIndex + offset, i);
                        double cmEncoursValue = parseValueAt(cmEncours, i);
                        double nEnCoursValue = parseValueAt(nEnCours + offset, i);
                        double nEnAttenteValue = parseValueAt(nEnAttente + offset, i);
                        double cmEncoursAccValue = parseValueAt(cmEncoursAcc, i);
                        double nEnCoursAccValue = parseValueAt(nEnCoursAcc + offset, i);

                        currentCellValue = triangleComptaValue + cmEncoursValue * (nEnCoursValue + nEnAttenteValue) + cmEncoursAccValue * nEnCoursAccValue;
                    }

                    this.c(col)[i] = String.format("%.2f", currentCellValue);  // Assigning the computed value to the cell.
                    rowSum += currentCellValue; // Add the current cell's value to the row's running sum.
                }
            }

            colSinUltime[i] = rowSum; // Store the sum for the current row in the SinUltime array.
        }

    }
    private void populateCoefsSP () {
        int indSinUltime = find_in_arr_first_index(subheader,"Sinistre Ultime");
        int indSPprevi = find_in_arr_first_index(subheader,"S/P previ hors PB");
        int indSPAaP = find_in_arr_first_index(subheader,"S/P si pas réel acquis avec provision");
        int indSPUavantPB = find_in_arr_first_index(subheader,"S/P si pas reel ultime avant PB");
        int indSPUapresPB = find_in_arr_first_index(subheader,"S/P si pas reel ultime apres PB");
        int indexPAaDate = find_in_arr_first_index(subheader, "Total PA à date");
        int indexPB = find_in_arr_first_index(subheader, "PB");

        int indexCMenCours = find_in_arr_first_index(subheader, "Cout Moyen: En cours");
        int indexEncours = find_in_arr_first_index(subheader, "Nombre en cours total");
        int indexEnAttente = find_in_arr_first_index(subheader, "Nombre en attente de prescription total");
        int indexSumFic = find_in_arr_first_index(subheader, "Comptable total");

        int indexMTPA = find_in_arr_first_index(header, "MONTANT TOTAL PRIME ASSUREUR");

        for (int i = 0; i < nrow; i++) {
            this.c(indSPprevi)[i] = String.format("%.2f", colSPprevi[i]);
            this.c(indexPB)[i] = String.format("%.2f", colPB[i]);
            this.c(indSinUltime)[i] = String.format("%.2f", colSinUltime[i]);

            double sumFic = parseObjectToDouble(this.c(indexSumFic)[i]);
            double enAttente = parseObjectToDouble(this.c(indexEnAttente)[i]);
            double enCours = parseObjectToDouble(this.c(indexEncours)[i]);
            double cMenCours = parseObjectToDouble(this.c(indexCMenCours)[i]);
            double pAaDate = parseObjectToDouble(this.c(indexPAaDate)[i]);
            double tauxAcq = parseObjectToDouble(tauxAcquisition[i]);

            double calculatedValue = max((sumFic + (enAttente + enCours) * cMenCours) +
                    (pAaDate + colPB[i] * tauxAcq), 0.0);
            this.c(indSPAaP)[i] = Double.isNaN(calculatedValue) ? "0.00" : String.format("%.2f", calculatedValue);


            Double mtpa = parseObjectToDouble(this.c(indexMTPA)[i]);
            this.c(indSPUavantPB)[i] = String.format("%.2f", mtpa.equals(0.0) ? 0 : colSinUltime[i] / mtpa);
            this.c(indSPUapresPB)[i] = String.format("%.2f", mtpa.equals(0.0) ? 0 : colSinUltime[i] / (mtpa + colPB[i]));
        }
    }
    private double parseValueAt(int col, int row) {
        Object value = this.c(col)[row];
        if (value == null) {
            return 0.0;
        }

        try {
            return Double.parseDouble(((String) value).replace(',','.'));
        } catch (NumberFormatException e) {
            // You can choose to log this exception or just return a default value
            return 0.0;
        }
    }
    private void populatePBetSPprevi() {
        int indexContrat = find_in_arr_first_index(header, "Contrat");
        int indexDP = find_in_arr_first_index(header, "Date Periode");
        colSPprevi = new Double[nrow];
        colPB = new Double[nrow];
        String contrat; String date;
        for (int i = 0; i < nrow; i++) {
            contrat = ((String) this.c(indexContrat)[i]).toLowerCase();
            date = (String) this.c(indexDP)[i];

            // Extract year from the date
            String[] dateParts = date.split("-");
            double year = Double.parseDouble(dateParts[1]);

            Map<Double, Double> spPreviMap = mapSPprevi.get(contrat);
            if (spPreviMap != null) {
                colSPprevi[i] = spPreviMap.getOrDefault(year, 0.0);
            } else {
                // Handle or set default value if not found
                colSPprevi[i] = 0.0;  // or any default value
            }

            Map<String, Double> pbMap = mapPB.get(contrat);
            if (pbMap != null) {
                colPB[i] = pbMap.getOrDefault(year, 0.0);
            } else {
                // Handle or set default value if not found
                colPB[i] = 0.0;  // or any default value
            }
        }
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
        this.addCol("","Cout Moyen: " + label);
    }

    private void updateHeaderForProvisions(String statut) {
        List<String> newHeaders = new ArrayList<>(Arrays.asList(header));
        for (int year = 2013; year <= 2026; year++) {
            newHeaders.add(String.valueOf(year));
        }
        header = newHeaders.toArray(new String[0]);

        int index = subheader.length;
        subheader = Arrays.copyOf(subheader, header.length);
        subheader[index] = "Provisions: " + statut;
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
    private void appendUpdateKeepAll(int begin) {
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
        Path path = Paths.get(fullPath);
        String newPath = outputFolder + path.getFileName().toString();
        String filePath = newPath.replace(".xlsx", "_fichier_de_travail.csv");

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
            try {
                writer.write(row[colIndex]);
            } catch (NullPointerException npe) {
                writer.write("NOTHINGGGGGGGGGGGGGG");
            }
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
    public static String[] subarray(String[] array, int begin) {
        if (array == null || begin >= array.length) {
            return new String[0];
        }

        return Arrays.copyOfRange(array, begin, array.length);
    }
    public void addCol(String newHeader, String newSubheader) {
        // Increment the column count
        ncol++;

        // Extend and populate header and subheader
        header = Arrays.copyOf(header, ncol);
        subheader = Arrays.copyOf(subheader, ncol);
        header[ncol - 1] = newHeader;
        subheader[ncol - 1] = newSubheader;

    }
    public void addColumnsString(String[] colsToAdd) {
        // Extend the header and subheader
        int ncols = colsToAdd.length;
        ncol += ncols;
        header = Arrays.copyOf(header, ncol);
        subheader = Arrays.copyOf(subheader, ncol);

        // Initialize the new columns in the dataframe
        for (int cta = 0; cta < colsToAdd.length; cta++) {
            this.df.add(new String[nrow]);
            header[ncol - ncols + cta] = "";
            subheader[ncol - ncols + cta] = colsToAdd[cta];
        }
    }
    public static String[] convertToStringArrayWithTwoDecimals(double[] input) {
        String[] result = new String[input.length];
        for (int i = 0; i < input.length; i++) {
            result[i] = String.format("%.2f", input[i]);
        }
        return result;
    }
    public static double parseObjectToDouble(Object value) {
        if (value == null) {
            return 0.0;
        }

        String stringValue = (String) value;

        try {
            return Double.parseDouble(stringValue.replace(',', '.'));
        } catch (NumberFormatException e) {
            // You can choose to log this exception or just return a default value
            return 0.0;
        }
    }


}
