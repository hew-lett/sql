package main.app;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Iterator;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
public class DF {
    private char delim;
    private String path;
    String[][] df;
    String[] header;
    public DF (char delim, String path) {
        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true, delim);
        settings.trimValues(true);
        try(Reader inputReader = new InputStreamReader(Files.newInputStream(
                new File(path).toPath()), StandardCharsets.UTF_8)){
            CsvParser parser = new CsvParser(settings);
            List<String[]> parsedRows = parser.parseAll(inputReader);
            df = new String[parsedRows.size()][parsedRows.get(0).length];

            int i = 0;
            for (String[] parsedRow : parsedRows) {
                int j = 0;
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
    public DF (char delim, String path, String encoding) {
        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true, delim);
        settings.trimValues(true);
        try(Reader inputReader = new InputStreamReader(Files.newInputStream(
                new File(path).toPath()), encoding)){
            CsvParser parser = new CsvParser(settings);
            List<String[]> parsedRows = parser.parseAll(inputReader);
            df = new String[parsedRows.size()][parsedRows.get(0).length];

            int i = 0;
            for (String[] parsedRow : parsedRows) {
                int j = 0;
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
//    public DF (String path, int sheet_n) {
//        FileInputStream fis= null;
//        try {
//            fis = new FileInputStream(new File(path));
//        } catch (FileNotFoundException e) {
//            throw new RuntimeException(e);
//        }
//        XSSFWorkbook wb= null;
//        try {
//            wb = new XSSFWorkbook(fis);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//        XSSFSheet sheet=wb.getSheetAt(sheet_n);
//        Header i;
//        i = sheet.getHeader();
//        System.out.println(i);
//        df = new String[sheet.getLastRowNum()][sheet.getHeader() .length];
//
//        int i = 0;
//        for(Row row: sheet)     //iteration over row using for each loop
//        {
//            int j = 0;
//            for(Cell cell: row)    //iteration over cell using for each loop
//            {
//                System.out.println(i);
//                System.out.println(j);
//                df[i][j] = cell.toString();
//                j++;
//            }
//            i++;
//        }
//    }
    public DF (String path, int sheet_n) {
        File excelFile = new File(path);
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(excelFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        // we create an XSSF Workbook object for our XLSX Excel File
        XSSFWorkbook workbook = null;
        try {
            workbook = new XSSFWorkbook(fis);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // we get first sheet
        XSSFSheet sheet = workbook.getSheetAt(sheet_n);
        Iterator<Row> rowIt = sheet.iterator();

        // we iterate on rows
        int i = 0;
        int j = 0;
        while (rowIt.hasNext()) {
            Row row = rowIt.next();
            i++;
        }
        Row row = sheet.getRow(0);
        Iterator<Cell> cellIterator = row.cellIterator();
        j=0;
        while (cellIterator.hasNext()) {
            Cell cell = cellIterator.next();
            j++;
        }
        df = new String[i][j];
        i = 0;
        j=0;
        rowIt = sheet.iterator();
        while (rowIt.hasNext()) {
            row = rowIt.next();
//        for (Row row : sheet) {
            // iterate on cells for the current row
            cellIterator = row.cellIterator();
            j = 0;
            while (cellIterator.hasNext()) {
                Cell cell = cellIterator.next();
                df[i][j] = cell.toString();

                j++;
//                System.out.print(cell.toString() + ";");
            }
            i++;

//            System.out.println();
        }
        System.out.println(i + " " + j);
        try {
            workbook.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            fis.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
