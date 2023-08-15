package main.app;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;

import static main.app.App.*;
import static main.app.DF.Col_types.*;

public class BaseFic extends BaseAccum {
    public static final String LAPARISIENNE = "LaParisienne";
    public static final String MEDIA = "DBCLAIMS";
    public static void main(String[] args) throws IOException {
        long startTime = System.nanoTime();long endTime;long duration;long minutes;long seconds;

        DF map_filtered = mapping.mappingFiltre("DB Claims Italie");
        map_filtered.print(10);
//        DF fic_FRA = new BaseFic(wd + "source FIC/SPB France/","FIC France");
//        DF fic_ITA = new BaseFic(wd + "source FIC/SPB Italie/","DB Claims Italie");

        DF fic_POL = new BaseFic(wd + "source FIC/SPB Pologne/","FIC Pologne");
        fic_POL.print(10);
        System.out.println(fic_POL.nrow);
        System.out.println(Arrays.toString(fic_POL.r(130000 )));

    }
    public BaseFic(String path, String map_col) throws IOException {
        switch (map_col) {
            case "FIC France" -> {
                this.source = true;
                referentialRow = getReferentialRow(new String[]{"source"});

                List<File> fileList = Arrays.asList(Objects.requireNonNull(new File(path).listFiles()));
                if (fileList.isEmpty()) return;

                int dim = computeDimFICFrance(path);

                fileList.sort(Comparator.comparing(f -> !f.getName().contains(LAPARISIENNE)));

                File mainFile = fileList.get(0);
                if (!mainFile.getName().contains(LAPARISIENNE)) return;

                CsvParserSettings settings = new CsvParserSettings();
                settings.setDelimiterDetectionEnabled(true, DEFAULT_DELIMITER);
                settings.trimValues(true);

                try (Reader inputReader = Files.newBufferedReader(mainFile.toPath(), Charset.forName(encoding))) {
                    CsvParser parser = new CsvParser(settings);
                    List<String[]> parsedRows = parser.parseAll(inputReader);
                    Iterator<String[]> rows = parsedRows.iterator();
                    header = rows.next();
                    header = Arrays.stream(header)
                            .filter(h -> h != null && !h.trim().isEmpty())
                            .toArray(String[]::new);
                    ncol = header.length;
                    boolean[] cols_kept = this.mapColnamesAndKeepNeededMain(map_col);
                    header_unify();

                    coltypes = new Col_types[ncol];
                    for (int i = 0; i < ncol; i++) {
                        if (cols_kept[i]) {
                            coltypes[i] = Col_types.STR;
                        } else {
                            coltypes[i] = Col_types.SKP;
                        }
                    }
                    List<String> refTriangleHeaders = Arrays.asList(ref_triangle.header);
                    for (int i = 0; i < header.length; i++) {
                        if (refTriangleHeaders.contains(header[i]) && header[i].startsWith("date")) {
                            coltypes[i] = DAT;
                        }
                    }

                    nrow = dim;
                    assert (coltypes.length == parsedRows.get(0).length);
                    ncol = get_len(coltypes);
                    df = new ArrayList<>(get_len(coltypes));
                    this.df_populate(coltypes);

                    int i = 0;
                    while (rows.hasNext()) {
                        int j = 0;
                        int k = 0;
                        String[] parsedRow = rows.next();
                        parsedRow = Arrays.copyOf(parsedRow, header.length);
                        for (String s : parsedRow) {
                            if (coltypes[k] != Col_types.SKP) {
                                df.get(j)[i] = get_lowercase_cell_of_type(s, coltypes[k], dateDefault);
                                j++;
                            }
                            k++;
                        }
                        i++;
                    }

                    this.headerAndColtypesDropSKP();

                    for (File file : fileList) {
                        if (file.getName().contains(LAPARISIENNE)) continue;

                        try (Reader secondaryInputReader = Files.newBufferedReader(file.toPath(), Charset.forName(encoding))) {
                            settings.setDelimiterDetectionEnabled(true, '\t');
                            parser = new CsvParser(settings);
                            parsedRows = parser.parseAll(secondaryInputReader);
                            rows = parsedRows.iterator();

                            // 1. Header treatment
                            String[] auxHeader = rows.next().clone();  // Cloning to avoid accidental modifications
                            boolean[] auxColsKept = this.mapColnamesAndKeepNeededAux(auxHeader, mapping.mappingFiltre(map_col));

                            Col_types[] auxColtypes = new Col_types[auxHeader.length];
                            for (int j = 0; j < auxHeader.length; j++) {
                                if (auxColsKept[j]) {
                                    auxColtypes[j] = Col_types.STR;
                                } else {
                                    auxColtypes[j] = Col_types.SKP;
                                }
                            }

                            String[] auxUnifiedHeader = this.header_unify_return(auxHeader);

                            // 2. Map positions from auxiliary header to main header
                            int[] auxToMainMapping = new int[auxUnifiedHeader.length];
                            for (int j = 0; j < auxUnifiedHeader.length; j++) {
                                int ind = find_in_arr_first_index(header, auxUnifiedHeader[j]);
                                if (ind != -1 && coltypes[ind] != Col_types.SKP) {
                                    auxToMainMapping[j] = ind;
                                } else {
                                    auxToMainMapping[j] = -1;
                                }
                            }
                            auxToMainMapping = remapIndices(auxToMainMapping);

                            // 3. Populate main data frame based on mapped positions
                            while (rows.hasNext()) {
                                String[] parsedRow = rows.next();
                                for (int j = 0, k = 0; j < parsedRow.length; j++) {
                                    if (auxColtypes[j] == Col_types.SKP) {
                                        //j++;
                                        continue;
                                    }
                                    int mainIndex = auxToMainMapping[j];
                                    if (mainIndex != -1) {
                                        df.get(mainIndex)[i] = get_lowercase_cell_of_type(parsedRow[j], coltypes[mainIndex], dateDefault);
                                    }
                                    k++;
                                }
                                i++;
                            }
                        }
                    }
                }
                date_autofill_agg();
                this.remove_leading_zeros();
            }
            case "DB Claims Italie" -> {
                this.source = true;
                referentialRow = getReferentialRow(new String[]{"source"});

                List<File> fileList = Arrays.asList(Objects.requireNonNull(new File(path).listFiles()));
                if (fileList.isEmpty()) return;

                int dim = computeDimFICItaPol(path, "Italie");
                fileList.sort(Comparator.comparing(f -> !f.getName().contains(MEDIA)));

                CsvParserSettings settings = new CsvParserSettings();
                settings.setDelimiterDetectionEnabled(true, DEFAULT_DELIMITER);
                settings.trimValues(true);

                int i = 0;
                boolean initialized = false;

                for (File file : fileList) {
                    try (Reader inputReader = Files.newBufferedReader(file.toPath(), Charset.forName(encoding))) {
                        CsvParser parser = new CsvParser(settings);
                        List<String[]> parsedRows = parser.parseAll(inputReader);
                        Iterator<String[]> rows = parsedRows.iterator();

                        if (!initialized) {
                            header = rows.next();
                            header = Arrays.stream(header)
                                    .filter(h -> h != null && !h.trim().isEmpty())
                                    .toArray(String[]::new);
                            ncol = header.length;
                            boolean[] cols_kept = this.mapColnamesAndKeepNeededMain(map_col);
                            header_unify();
                            coltypes_populate(cols_kept);

                            nrow = dim;
                            assert (coltypes.length == parsedRows.get(0).length);
                            ncol = get_len(coltypes);
                            df = new ArrayList<>(get_len(coltypes));
                            this.df_populate(coltypes);

                            initialized = true;
                        } else {
                            rows.next(); // Skipping the header for all subsequent files
                        }

                        while (rows.hasNext()) {
                            int j = 0;
                            int k = 0;
                            String[] parsedRow = rows.next();
                            parsedRow = Arrays.copyOf(parsedRow, header.length);
                            for (String s : parsedRow) {
                                if (coltypes[k] != Col_types.SKP) {
                                    df.get(j)[i] = get_lowercase_cell_of_type(s, coltypes[k], dateDefault);
                                    j++;
                                }
                                k++;
                            }
                            i++;
                        }

                    }
                }

                this.headerAndColtypesDropSKP();
                date_autofill_agg();
                this.remove_leading_zeros();
            }
            case "FIC Pologne" -> {
                this.source = true;
                referentialRow = getReferentialRow(new String[]{"source"});

                List<File> fileList = Arrays.asList(Objects.requireNonNull(new File(path).listFiles()));
                if (fileList.isEmpty()) return;

                int dim = computeDimFICItaPol(path, "Pologne");
                System.out.println(dim);
                CsvParserSettings settings = new CsvParserSettings();
                settings.setDelimiterDetectionEnabled(true, TAB_DELIMITER);
                settings.trimValues(true);

                int i = 0;
                boolean initialized = false;

                for (File file : fileList) {

                    try (Reader inputReader = Files.newBufferedReader(file.toPath(), Charset.forName(encoding))) {
                        CsvParser parser = new CsvParser(settings);
                        List<String[]> parsedRows = parser.parseAll(inputReader);
                        Iterator<String[]> rows = parsedRows.iterator();

                        if (!initialized) {
                            header = rows.next();
                            header = Arrays.stream(header)
                                    .filter(h -> h != null && !h.trim().isEmpty())
                                    .toArray(String[]::new);
                            ncol = header.length;
                            boolean[] cols_kept = this.mapColnamesAndKeepNeededMain(map_col);
                            header_unify();
                            coltypes_populate(cols_kept);

                            nrow = dim;
                            assert (coltypes.length == parsedRows.get(0).length);
                            ncol = get_len(coltypes);
                            df = new ArrayList<>(get_len(coltypes));
                            this.df_populate(coltypes);

                            initialized = true;
                        } else {
                            rows.next(); // Skipping the header for all subsequent files
                        }

                        while (rows.hasNext()) {
                            int j = 0;
                            int k = 0;
                            String[] parsedRow = rows.next();
                            parsedRow = Arrays.copyOf(parsedRow, header.length);
                            for (String s : parsedRow) {
                                if (coltypes[k] != Col_types.SKP) {
                                    df.get(j)[i] = get_lowercase_cell_of_type(s, coltypes[k], dateDefault);
                                    j++;
                                }
                                k++;
                            }
                            i++;
                        }

                    }
                }

                this.headerAndColtypesDropSKP();
                date_autofill_agg();
                this.remove_leading_zeros();
            }
        }
        populateUniqueStatuts();
        populateUniqueNumPoliceValues();
        computeMinMaxDatesForPolicies();
    }
    private int[] remapIndices(int[] auxToMainMapping) {
        int[] newMapping = new int[auxToMainMapping.length];
        int countKept = 0;  // Count of columns that are not -1

        for (int i = 0; i < auxToMainMapping.length; i++) {
            if (auxToMainMapping[i] != -1) {
                newMapping[i] = countKept;
                countKept++;
            } else {
                newMapping[i] = -1;
            }
        }
        return newMapping;
    }
    private int computeDimFICFrance(String path) throws IOException {
        File[] files = new File(path).listFiles();
        if (files == null || files.length == 0) return 0;

        List<File> fileList = new ArrayList<>(Arrays.asList(files));

        int dim;
        String metadataCurrent = wd + "metadata/fic_france_nb_lignes_" + CURRENT_MONTH + ".txt";
        if (new File(metadataCurrent).exists()) {
            return readDimFromMetadata(metadataCurrent);
        }

        String metadataPrevious = wd + "metadata/fic_france_nb_lignes_" + PREVIOUS_MONTH + ".txt";

        if (new File(metadataPrevious).exists()) {
            dim = readDimFromMetadata(metadataPrevious);
            for (File file : fileList) {
                if (file.getName().contains(CURRENT_MONTH)) {
                    dim += csv_get_nrows(file.getPath(), '\t');
                }
            }
        } else {
            dim = getDimFrom0_FIC(fileList, "France");
        }
        writeDimToMetadata(metadataCurrent, dim);

        return dim;
    }
    private int computeDimFICItaPol(String path, String pays) throws IOException {
        File[] files = new File(path).listFiles();
        if (files == null || files.length == 0) return 0;

        List<File> fileList = new ArrayList<>(Arrays.asList(files));

        int dim;
        String metadataCurrent = wd + "metadata/fic_" + pays + "_nb_lignes_" + CURRENT_MONTH + ".txt";
        if (new File(metadataCurrent).exists()) {
            return readDimFromMetadata(metadataCurrent);
        }

        dim = getDimFrom0_FIC(fileList, pays);
        writeDimToMetadata(metadataCurrent, dim);

        return dim;
    }
    private int getDimFrom0_FIC(List<File> fileList, String pays) throws IOException {
        int dim = 0;
        char delim = ';'; //ita ;
        if (pays.equals("Pologne")) {
            delim = '\t';
        }
        if (pays.equals("France")) {
            for (File file : fileList) {
                delim = file.getName().contains("LaParisienne") ? ';' : '\t';
                dim += csv_get_nrows(file.getPath(), delim);
            }
        } else {
            for (File file : fileList) {
                dim += csv_get_nrows(file.getPath(), delim);
            }
        }
        return dim;
    }
}
