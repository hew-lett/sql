package main.app;

import com.monitorjbl.xlsx.StreamingReader;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;


import java.io.*;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.List;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

public class DF {
    private char delim;
    private String path;
    public String[][] df;
    String[] header;

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
            df = new String[parsedRows.size()-1][parsedRows.get(0).length];
            int i = 0;
            while(rows.hasNext()) {
                int j = 0;
                String[] parsedRow = rows.next();
                for (String s : parsedRow) {
                    df[i][j] = s;
                    j++;
                }
                i++;
            }
        } catch (IOException e) {
            // handle exception
        }
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

        Iterator<Row> rowIter = sheet.rowIterator();    // make row iterator
        Row row = rowIter.next();                       // take first row
        header = new String[row.getLastCellNum()];      // init header array
        int col_count = 0;                              // from 0
        for (Cell c : row) {                            // iterate
            header[col_count] = c.getStringCellValue(); // fill header
            col_count++;                                // count columns
        }
        System.out.println(sheet.getLastRowNum());
        System.out.println(col_count);
        df = new String[sheet.getLastRowNum()][col_count]; // -1 header

        int l = 0,k = 0;
        while(rowIter.hasNext()) {
            row = rowIter.next();
            k = 0;
            for (Cell c : row) {
                if(c.getCellTypeEnum().name().equals("FORMULA")) {
                    if(c.getCachedFormulaResultTypeEnum().name().equals("ERROR")) {
                        df[l][k] = c.getCellFormula();      // bad formula
                    } else {
                        df[l][k] = c.getStringCellValue();  // good formula
                    }
                } else {
                    df[l][k] = c.getStringCellValue();      // no formula
                }
                k++;
            }
            l++;
        }
    }

    public String[] r(int index){
        return df[index];
    }
    public String[] c(int index){
        String[] column = new String[df.length];
        for(int i=0; i<column.length; i++){
            column[i] = df[i][index];
            System.out.println(i);
        }
        return column;
    }
}
