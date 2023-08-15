package main.app;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static main.app.App.*;
import static main.app.App.NA_DAT;
import static main.app.DF.Col_types.DAT;
import static main.app.DF.Col_types.SKP;

public class BaseAccum extends DF {
    public static final char DEFAULT_DELIMITER = ';';
    public static final char TAB_DELIMITER = '\t';
    static DF ref_prog = new DF(wd+"Référentiel programmes.csv", ';', true);
    static DF ref_triangle;
    static {
        try {
            ref_triangle = new DF(wd + "ref_triangle.xlsx");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    static DF mapping;
    static {
        try {
            mapping = new DF(wd + "mapping.xlsx");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    static final String CURRENT_MONTH;
    static final String PREVIOUS_MONTH;
    static {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        LocalDate now = LocalDate.now();

        CURRENT_MONTH = now.format(formatter);
        PREVIOUS_MONTH = now.minusMonths(1).format(formatter);
    }
    protected Set<String> uniqueStatuts = new HashSet<>();
    protected Set<String> uniqueNumPoliceValues = new HashSet<>();
    protected Object[] referentialRow;
    protected boolean source = false;
    protected String key_sin = "";
    protected Map<String, Map<String, Date>> minDateMap = new HashMap<>();
    protected Map<String, Map<String, Date>> maxDateMap = new HashMap<>();
    protected Map<String, Date> overallMinDateByStatut = new HashMap<>();
    protected Map<String, Date> overallMaxDateByStatut = new HashMap<>();
    void coltypes_populate(boolean[] cols_kept) {
        coltypes = new Col_types[ncol];
        for (int colIndex = 0; colIndex < ncol; colIndex++) {
            if (cols_kept[colIndex]) {
                coltypes[colIndex] = Col_types.STR;
            } else {
                coltypes[colIndex] = SKP;
            }
        }
        List<String> refTriangleHeaders = Arrays.asList(ref_triangle.header);
        for (int colIndex = 0; colIndex < header.length; colIndex++) {
            if (coltypes[colIndex] != SKP && refTriangleHeaders.contains(header[colIndex]) && header[colIndex].startsWith("date")) {
                coltypes[colIndex] = DAT;
            }
        }
    }
    SimpleDateFormat getDateFormatter(String dateFormatString) {
        String pattern = switch (dateFormatString) {
            case "#yyyy-mm-dd#" -> "yyyy-MM-dd";
            case "dd/mm/yyyy" -> "dd/MM/yyyy";
            default -> throw new IllegalArgumentException("Unknown date format: " + dateFormatString);
        };

        return new SimpleDateFormat(pattern);
    }
    void date_autofill_agg() {
        // Indices for required columns in the current DF
        int indexDateSurv = find_in_arr_first_index(header, "date_surv");
        int indexDateSous = find_in_arr_first_index(header, "date_sous");
        int indexDateDecla = find_in_arr_first_index(header, "date_decla");
        int indexNumPolice = find_in_arr_first_index(header, "num_police");

        // Indices for required columns in the ref_prog DF
        int indexContrat = find_in_arr_first_index(ref_prog.header, "n°contrat");
        int indexDateDebutRef = find_in_arr_first_index(ref_prog.header, "date_debut");
        int indexDateFinRef = find_in_arr_first_index(ref_prog.header, "date_fin");

        // Return early if the num_police column doesn't exist
        if (indexNumPolice == -1) return;

        // If date_surv column doesn't exist, create it
        if (indexDateSurv == -1) {
            indexDateSurv = ncol;
            Object[] newColumn = new Object[nrow];
            Arrays.fill(newColumn, NA_DAT);
            df.add(newColumn);
            ncol++;
        }

        // If date_sous column doesn't exist, create it
        if (indexDateSous == -1) {
            indexDateSous = ncol;
            Object[] newColumn = new Object[nrow];
            Arrays.fill(newColumn, NA_DAT);
            df.add(newColumn);
            ncol++;
        }

        // Cache for quick lookup of ref_prog data based on num_police/n°contrat
        Map<String, Date[]> refprogLookup = new HashMap<>();
        for (int i = 0; i < ref_prog.nrow; i++) {
            String contrat = ref_prog.c(indexContrat)[i].toString();
            Date dateDebut = (Date) ref_prog.c(indexDateDebutRef)[i];
            Date dateFin = (Date) ref_prog.c(indexDateFinRef)[i];
            refprogLookup.put(contrat, new Date[]{dateDebut, dateFin});
        }

        for (int i = 0; i < nrow; i++) {
//            System.out.println("Processing row " + i + " of " + nrow + c(indexNumPolice)[i]);
            String currentNumPolice = c(indexNumPolice)[i].toString();
            Date[] refDates = refprogLookup.get(currentNumPolice.toLowerCase());
            if (refDates == null) {
                System.out.println("Warning: No ref_prog data found for num_police " + currentNumPolice);
                continue;
            }

            Date dateDebutRef = refDates[0];
            Date dateFinRef = refDates[1];

            Date dateSurv = (Date) c(indexDateSurv)[i];
            Date dateSous = (Date) c(indexDateSous)[i];

            // Date filling logic...
            if (dateSurv.equals(NA_DAT)) {
                if (indexDateDecla != -1 && !c(indexDateDecla)[i].equals(NA_DAT)) {
                    dateSurv = (Date) c(indexDateDecla)[i];
                } else if (!dateSous.equals(NA_DAT)) {
                    dateSurv = dateSous;
                } else {
                    dateSurv = dateDebutRef;
                }
            }
            if (dateSous.equals(NA_DAT)) {
                if(!dateSurv.equals(NA_DAT)){
                    dateSous = dateSurv;
                } else {
                    dateSous = dateDebutRef;
                }
            }

            // Apply transformations...
            date_transform(dateSurv, dateDebutRef, dateFinRef, indexDateSurv, i);
            date_transform(dateSous, dateDebutRef, dateFinRef, indexDateSous, i);
        }
    }
    void date_transform (Date date, Date dateDebutRef, Date dateFinRef, int columnIndex, int rowIndex) {
        // Control that dates are in the desired interval
        if (date.before(dateDebutRef)) {
            date = dateDebutRef;
        }
        if (date.after(dateFinRef)) {
            date = dateFinRef;
        }

        // Change the date to the 1st day of the month
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        date = cal.getTime();

        // Update the dates in the DF
        df.get(columnIndex)[rowIndex] = date;
    }
    Object[] getRefProgrammesRow (String numPolice, SimpleDateFormat refProgDateFormat) {
        int indexNumContract = find_in_arr_first_index(ref_prog.header, "n°contrat");
        int indexDateDebut = find_in_arr_first_index(ref_prog.header, "date_debut");
        int indexDateFin = find_in_arr_first_index(ref_prog.header, "date_fin");

        for (int i = 0; i < ref_prog.nrow; i++) {
            String contractNumber = (String) ref_prog.c(indexNumContract)[i];
            if (contractNumber != null && contractNumber.equals(numPolice)) {
                Object[] refRow = ref_prog.r(i);
                try {
                    refRow[indexDateDebut] = refProgDateFormat.parse((String) refRow[indexDateDebut]);
                    refRow[indexDateFin] = refProgDateFormat.parse((String) refRow[indexDateFin]);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                return refRow;
            }
        }
        return null;
    }
    Object[] getReferentialRow(String[] keys) {
        String gestionnaire = keys[0];
        String precision = keys.length > 1 ? keys[1] : null;

        for (int rowIndex = 0; rowIndex < ref_triangle.nrow; rowIndex++) {
            Object[] row = ref_triangle.r(rowIndex);
            if (row[0].equals(gestionnaire)) {
                // If precision is not provided or matches the referential, return the row
                if (precision == null || row[1].equals(precision)) {
                    return row;
                }
            }
        }

        throw new RuntimeException("Referential row not found for keys: " + Arrays.toString(keys));
    }
    void header_unify() {
        for (int i = 0; i < ncol; i++) {
            int ind = find_in_arr_first_index(this.referentialRow, header[i].toLowerCase());
            if (ind != -1) {
                header[i] = ref_triangle.header[ind];
            }
        }
    }
    String[] header_unify_return(String[] inputHeader) {
        String[] unifiedHeader = new String[inputHeader.length];
        for (int i = 0; i < inputHeader.length; i++) {
            int ind = find_in_arr_first_index(this.referentialRow, inputHeader[i].toLowerCase());
            if (ind != -1) {
                unifiedHeader[i] = ref_triangle.header[ind];
            } else {
                unifiedHeader[i] = inputHeader[i];
            }
        }
        return unifiedHeader;
    }
    String[] getColsToTake() {
        List<String> colsList = new ArrayList<>();
        for (int i = 2; i < referentialRow.length - 1; i++) {
            String colValue = referentialRow[i].toString().toLowerCase().trim();
            if (!colValue.isEmpty()) {
                colsList.add(colValue);
            }
        }
        return colsList.toArray(new String[0]);
    }
    boolean[] mapColnamesAndKeepNeededMain (String mapping_col) {

        DF map_filtered = mapping.mappingFiltre(mapping_col);

        boolean[] columnsKept = new boolean[header.length];

        for (int i = 0; i < header.length; i++) {
            columnsKept[i] = false;

            for (int j = 0; j < map_filtered.nrow; j++) {
                // Getting the Format ICI value (from the first column) and the desired format (from the second column)
                String formatICI = (String) map_filtered.df.get(0)[j];
                String desiredFormat = (String) map_filtered.df.get(1)[j];

                // If either value is null, continue to next iteration
                if (Objects.equals(formatICI, "") || desiredFormat.equals("")) continue;

                // Check if the header matches the desired format (ignoring case and special characters)
                if (normalize(header[i]).equalsIgnoreCase(normalize(desiredFormat))) {
                    // Check if the Format ICI value is present in referentialRow
                    if (Arrays.asList(referentialRow).contains(formatICI)) {
                        header[i] = formatICI;
                        columnsKept[i] = true; // We keep this column
                        break; // No need to continue searching for this header
                    }
                }
            }
        }
        return columnsKept;
    }
    public void populateUniqueNumPoliceValues() {
        Object[] polices = c("num_police");
        for (Object obj : polices) {
            uniqueNumPoliceValues.add((String) obj);
        }
    }
    boolean[] mapColnamesAndKeepNeededAux (String[] localHeader, DF mapping) {
        boolean[] columnsKept = new boolean[localHeader.length];

        for (int i = 0; i < localHeader.length; i++) {
            columnsKept[i] = false;

            for (int j = 0; j < mapping.nrow; j++) {
                String formatICI = (String) mapping.df.get(0)[j];
                String desiredFormat = (String) mapping.df.get(1)[j];

                // If either value is null, continue to next iteration
                if (Objects.equals(formatICI, "") || desiredFormat.equals("")) continue;

                if (normalize(localHeader[i]).equalsIgnoreCase(normalize(desiredFormat))) {
                    if (Arrays.asList(referentialRow).contains(formatICI)) {
                        localHeader[i] = formatICI;
                        columnsKept[i] = true;
                        break;
                    }
                }
            }
        }
        return columnsKept;
    }
    public void remove_leading_zeros() {
        String[] cols = {"Numéro_Dossier"};
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
    public void populateUniqueStatuts() {
        Object[] statuts = c("statut");
        for (Object obj : statuts) {
            uniqueStatuts.add((String) obj);
        }
    }
    public void computeMinMaxDatesForPolicies() {
        // Initialize dictionaries with extreme Date values for each policy number and each statut
        for (String numPolice : uniqueNumPoliceValues) {
            minDateMap.put(numPolice, new HashMap<>());
            maxDateMap.put(numPolice, new HashMap<>());
            for (String statut : uniqueStatuts) {
                minDateMap.get(numPolice).put(statut, new Date(Long.MAX_VALUE));
                maxDateMap.get(numPolice).put(statut, new Date(Long.MIN_VALUE));
            }
        }

        // Initially set the overall min and max dates for each statut to extreme values
        for (String statut : uniqueStatuts) {
            overallMinDateByStatut.put(statut, new Date(Long.MAX_VALUE));
            overallMaxDateByStatut.put(statut, new Date(Long.MIN_VALUE));
        }

        // Fetch all the dates, policy numbers, and statuts from the dataframe
        Date[] dates = (Date[]) this.c("date_surv");
        String[] numPolices = (String[]) this.c("num_police");
        String[] statuts = (String[]) this.c("statut");

        // Iterate and update the dictionaries
        for (int i = 0; i < dates.length; i++) {
            Date currentDate = dates[i];
            String currentNumPolice = numPolices[i];
            String currentStatut = statuts[i];

            // If current date is before the stored min date for the current policy and statut, update it
            if (currentDate.before(minDateMap.get(currentNumPolice).get(currentStatut))) {
                minDateMap.get(currentNumPolice).put(currentStatut, currentDate);
            }

            // If current date is after the stored max date for the current policy and statut, update it
            if (currentDate.after(maxDateMap.get(currentNumPolice).get(currentStatut))) {
                maxDateMap.get(currentNumPolice).put(currentStatut, currentDate);
            }

            // Update overall minimum date for current statut if necessary
            if (currentDate.before(overallMinDateByStatut.get(currentStatut))) {
                overallMinDateByStatut.put(currentStatut, currentDate);
            }

            // Update overall maximum date for current statut if necessary
            if (currentDate.after(overallMaxDateByStatut.get(currentStatut))) {
                overallMaxDateByStatut.put(currentStatut, currentDate);
            }
        }
    }

}
