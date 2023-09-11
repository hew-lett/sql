package main.app;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static main.app.App.wd;

public class FileConfig {
    private static final String CONFIG_FILE_PATH = wd + "ref_fichiers.xlsx";

    private final Map<String, Map<Integer, String>> columnNamesToRead = new HashMap<>();
    private final Map<String, Map<Integer, DFnew.ColTypes>> columnTypes = new HashMap<>();
    private final Map<String, Map<Integer, String>> columnNamesAttributed = new HashMap<>();

    private FileConfig() throws IOException {
        // Read the configuration file
        InputStream is = Files.newInputStream(new File(CONFIG_FILE_PATH).toPath());
        Workbook workbook = new XSSFWorkbook(is);
        Sheet sheet = workbook.getSheetAt(0);

        // Loop through rows of the sheet
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row currentRow = sheet.getRow(i);
            if (currentRow != null) {
                String reference = currentRow.getCell(0).getStringCellValue();
                String type = currentRow.getCell(1).getStringCellValue();
                switch (type) {
                    case "name" -> columnNamesToRead.put(reference, extractData(currentRow));
                    case "type" -> {
                        Map<Integer, DFnew.ColTypes> colTypesMap = new HashMap<>();
                        for (Map.Entry<Integer, String> entry : extractData(currentRow).entrySet()) {
                            colTypesMap.put(entry.getKey(), DFnew.ColTypes.valueOf(entry.getValue()));
                        }
                        columnTypes.put(reference, colTypesMap);
                    }

                    case "rename" -> columnNamesAttributed.put(reference, extractData(currentRow));
                }
            }
        }

        workbook.close();
    }

    private Map<Integer, String> extractData(Row row) {
        Map<Integer, String> data = new HashMap<>();
        for (int j = 2; j < row.getLastCellNum(); j++) {
            Cell cell = row.getCell(j);
            if (cell != null) {
                String value = switch (cell.getCellType()) {
                    case STRING -> cell.getStringCellValue();
                    case NUMERIC -> {
                        double num = cell.getNumericCellValue();
                        if (num == Math.floor(num)) {
                            yield Integer.toString((int) num);
                        } else {
                            yield Double.toString(num);
                        }
                    }
                    default -> null;
                    // Add cases for other types if necessary
                };
                if (value != null && !value.isEmpty()) {
                    data.put(j - 2, value);
                }
            }

        }
        return data;
    }


    private static final class InstanceHolder {
        private static final FileConfig instance;

        static {
            try {
                instance = new FileConfig();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // Singleton Pattern to get instance
    public static FileConfig getInstance() throws IOException {
        return InstanceHolder.instance;
    }

    // Getters
    public ArrayList<String> getColumnNamesToRead(String refFichier) {
        return new ArrayList<>(columnNamesToRead.getOrDefault(refFichier, new HashMap<>()).values());
    }

    public ArrayList<DFnew.ColTypes> getColumnTypes(String refFichier) {
        Map<Integer, DFnew.ColTypes> colTypes = columnTypes.getOrDefault(refFichier, new HashMap<>());
        return new ArrayList<>(colTypes.values());
    }

    public ArrayList<String> getColumnNamesAttributed(String refFichier) {
        return new ArrayList<>(columnNamesAttributed.getOrDefault(refFichier, new HashMap<>()).values());
    }
}
