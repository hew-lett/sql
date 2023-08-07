package main.app;

import com.monitorjbl.xlsx.StreamingReader;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static main.app.App.*;
import static main.app.App.rowchecker;

public class BaseSin extends DF {
    Object[] referentialRow;
    Object[] refProgrammesRow;
    String numPolice = "";
    public BaseSin(String path) throws IOException {
        this.fullPath = path;
        // Extracting the filename and keys to find the matching referential row
        fileName = new File(path).getName().replace(".xlsm", "")
                .replace(".xlsx", "").toLowerCase();
        System.out.println(fileName);

        String[] keys = fileName.split("_");
        key_sin = keys[0];
        referentialRow = getReferentialRow(keys);

        InputStream is = Files.newInputStream(new File(path).toPath());
        Workbook workbook = StreamingReader.builder()
                .rowCacheSize(1)
                .bufferSize(4096)
                .open(is);
        if (fileName.equals("spb france_cdiscount")) {
            System.out.println("ok");
        }
        //String sheet_name = workbook.getSheetName(0);
        String sheet_name;
        if (workbook.getNumberOfSheets() == 1) {
            sheet_name = workbook.getSheetName(0);
        } else {
            sheet_name = null;
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                String tempSheetName = workbook.getSheetName(i);
                if (tempSheetName != null && tempSheetName.toLowerCase().contains("sinistre")) {
                    sheet_name = tempSheetName;
                    break;
                }
            }

            if (sheet_name == null) {
                sheet_name = workbook.getSheetName(0);
            }
        }
        Sheet sheet = workbook.getSheet(sheet_name);
        Iterator<Row> rowIter = sheet.rowIterator();
        Row row = rowIter.next();
        nrow = sheet.getLastRowNum();
        ncol = row.getLastCellNum();
        header = new String[ncol];

        int i = 0;
        for (Cell c : row) {
            header[i] = c.getStringCellValue().toLowerCase();
            i++;
        }
        String[] cols = Arrays.copyOf(ref_triangle.header, ref_triangle.header.length);
        String[] cols_to_take = getColsToTake();

        coltypes = new Col_types[ncol];
        for (i = 0; i < ncol; i++) {
            if (check_in(header[i], cols_to_take)) {
                int exactPosition = find_in_arr_first_index(referentialRow,header[i]);
                coltypes[i] = cols[exactPosition].startsWith("date") ? Col_types.DAT : cols[exactPosition].startsWith("montant") ? Col_types.DBL : Col_types.STR;
            } else {
                coltypes[i] = Col_types.SKP;
            }
        }


        ncol = get_len(coltypes);
        df = new ArrayList<>(ncol);
        this.df_populate(coltypes);

        String dateFormatString = referentialRow[8].toString();
        SimpleDateFormat dateFormatter = getDateFormatter(dateFormatString);

        int col_iterator;
        int row_number = 0;
        while(rowIter.hasNext()) {
            row = rowIter.next();
            col_iterator = 0;
            for (int c = 0; c < this.header.length; c++) {
                if (coltypes[c] != Col_types.SKP) {
                    Cell cell_i = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (cell_i == null) {
                        switch(coltypes[c]) {
                            case STR -> df.get(col_iterator)[row_number] = "";
                            case DBL -> df.get(col_iterator)[row_number] = 0d;
                            case DAT -> df.get(col_iterator)[row_number] = NA_DAT;
                        }
                        col_iterator++;
                        continue;
                    }
                    //System.out.println(cell_i);
                    df.get(col_iterator)[row_number] = parseCell(cell_i, coltypes[c], dateFormatter);
                    col_iterator++;
                }
            }
            row_number++;
            rowchecker++;

        }
        header_refactor();
        header_unify();
        date_autofill_agg();
        findAndStoreStatuts();
    }

    private void header_unify() {
        for (int i = 0; i < ncol; i++) {
            int ind = find_in_arr_first_index(this.referentialRow, header[i]);
            header[i] = ref_triangle.header[ind];
        }
    }
    private void date_autofill_agg() {
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
            System.out.println("Processing row " + i + " of " + nrow + c(indexNumPolice)[i]);
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
    private void date_transform (Date date, Date dateDebutRef, Date dateFinRef, int columnIndex, int rowIndex) {
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
    private Object[] getRefProgrammesRow (String numPolice, SimpleDateFormat refProgDateFormat) {
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
    private Object[] getReferentialRow(String[] keys) {
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
    public String[] getColsToTake() {
        List<String> colsList = new ArrayList<>();
        for (int i = 2; i < referentialRow.length - 1; i++) {
            String colValue = referentialRow[i].toString().toLowerCase().trim();
            if (!colValue.isEmpty()) {
                colsList.add(colValue);
            }
        }
        return colsList.toArray(new String[0]);
    }
    public SimpleDateFormat getDateFormatter(String dateFormatString) {
        String pattern = switch (dateFormatString) {
            case "#yyyy-mm-dd#" -> "yyyy-MM-dd";
            case "dd/mm/yyyy" -> "dd/MM/yyyy";
            default -> throw new IllegalArgumentException("Unknown date format: " + dateFormatString);
        };

        return new SimpleDateFormat(pattern);
    }

}
