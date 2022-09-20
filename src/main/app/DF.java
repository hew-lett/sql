package main.app;

import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
//import org.dhatim.fastexcel.reader.ReadableWorkbook;
//import org.dhatim.fastexcel.reader.Row;
//import org.dhatim.fastexcel.reader.Sheet;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.stream.Stream;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFName;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Sheet;

import static java.lang.String.valueOf;

public class DF {
    private char delim;
    private String path;
    public String[][] df;
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
    public DF (String path, int sheet_n) {
        File excelFile = new File(path);
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(excelFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        XSSFWorkbook workbook = null;
        try {
            workbook = new XSSFWorkbook(fis);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        XSSFSheet sheet = workbook.getSheetAt(sheet_n);
        int rows = Iterables.size(sheet);
        Row row = sheet.getRow(0);
        int cols = Iterables.size(row);

        header = new String[cols];
        Iterator<Cell> cellIterator = row.cellIterator();
        int i = 0;
        int j = 0;
        while (cellIterator.hasNext()) {
            Cell cell = cellIterator.next();
            header[j] = cell.toString();
            j++;
        }

        df = new String[cols][rows-1];
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

        Iterator<Row> rowIt = sheet.iterator();
        row = rowIt.next();
        while (rowIt.hasNext()) {
            row = rowIt.next();
            cellIterator = row.cellIterator();
            j = 0;
            while (cellIterator.hasNext()) {
                Cell cell = cellIterator.next();
                try {
                    evaluator.evaluate(cell);
                } catch (Exception e) {
                    String f = cell.getCellFormula();
                    cell.setCellType(CellType.STRING);
                    cell.setCellValue(f);
                }
                switch (cell.getCellTypeEnum()) {
                    case BOOLEAN:
                        df[j][i] = valueOf(cell.getBooleanCellValue());
                        break;
                    case NUMERIC:
                        df[j][i] = valueOf(cell.getNumericCellValue());
                        break;
                    case STRING:
                        df[j][i] = cell.getStringCellValue();
                        break;
                    case BLANK:
                        break;
                    case ERROR:
                        System.out.print("error" + cell.getErrorCellValue() + " / ");
                        break;
                    case FORMULA:
                        switch (cell.getCachedFormulaResultTypeEnum()) {
                            case BOOLEAN:
                                df[j][i] = valueOf(cell.getBooleanCellValue());
                                break;
                            case NUMERIC:
                                df[j][i] = valueOf(cell.getNumericCellValue());
                                break;
                            case STRING:
                                df[j][i] = cell.getStringCellValue();
                                break;
                            case BLANK:
                                break;
                        }
                        break;
                }
                j++;
            }
            i++;
        }
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
    public DF (String path, String sheet_n) {
        File excelFile = new File(path);
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(excelFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        XSSFWorkbook workbook = null;
        try {
            workbook = new XSSFWorkbook(fis);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        int x = workbook.getSheetIndex(sheet_n);
        XSSFSheet sheet = workbook.getSheetAt(x);

        int rows = Iterables.size(sheet);
        Row row = sheet.getRow(0);
        int cols = Iterables.size(row);

        header = new String[cols];
        Iterator<Cell> cellIterator = row.cellIterator();
        int i = 0;
        int j = 0;
        while (cellIterator.hasNext()) {
            Cell cell = cellIterator.next();
            header[j] = cell.toString();
            j++;
        }
        df = new String[cols][rows-1];
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

        Iterator<Row> rowIt = sheet.iterator();
        row = rowIt.next();
        while (rowIt.hasNext()) {
            row = rowIt.next();
            cellIterator = row.cellIterator();
            j = 0;
            while (cellIterator.hasNext()) {
                Cell cell = cellIterator.next();
                try {
                    evaluator.evaluate(cell);
                } catch (Exception e) {
                    String f = cell.getCellFormula();
                    cell.setCellType(CellType.STRING);
                    cell.setCellValue(f);
                }
                switch (cell.getCellTypeEnum()) {
                    case BOOLEAN:
                        df[j][i] = valueOf(cell.getBooleanCellValue());
                        break;
                    case NUMERIC:
                        df[j][i] = valueOf(cell.getNumericCellValue());
                        break;
                    case STRING:
                        df[j][i] = cell.getStringCellValue();
                        break;
                    case BLANK:
                        break;
                    case ERROR:
                        System.out.print("error" + cell.getErrorCellValue() + " / ");
                        break;
                    case FORMULA:
                        switch (cell.getCachedFormulaResultTypeEnum()) {
                            case BOOLEAN:
                                df[j][i] = valueOf(cell.getBooleanCellValue());
                                break;
                            case NUMERIC:
                                df[j][i] = valueOf(cell.getNumericCellValue());
                                break;
                            case STRING:
                                df[j][i] = cell.getStringCellValue();
                                break;
                            case BLANK:
                                break;
                        }
                        break;
                }
                j++;
            }
            i++;
        }
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
