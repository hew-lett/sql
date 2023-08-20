package main.app;

import org.apache.poi.ss.usermodel.*;

import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.io.BufferedReader;
import java.io.FileReader;
public class Compa {
    // Chemins d'accès aux dossiers contenant les fichiers a comparé
    public static String wd = "E:/";
    public static final String rapportPath = wd + "Rapports";
    public static final String controlePath = wd + "Lot1-controles/";
    public static final String filePath = wd + "CompaRapport.xlsx";
    public static String flux = "";
    public static String police = "";
    public static List<String> controle = new ArrayList<>(); // Initialize with an empty list
    public static List<String> controlecode = new ArrayList<>(); // Initialize with an empty list
    public static List<String> ListID = new ArrayList<>(); // Initialize with an empty list
    public static List<Integer> controlecodeindex = new ArrayList<>(); // Initialize with an empty list
    public static List<Integer> IDlistIndex = new ArrayList<>(); // Initialize with an empty list
    public static int codeindex=-1;
    public static int IDindex=-1;

    public static void main(String[] args) throws IOException {
        File JavaFiles = new File(rapportPath);
        File[] jfolder = JavaFiles.listFiles();
        File BIFiles = new File(controlePath);
        File[] bifolder = BIFiles.listFiles();
        if (jfolder != null && bifolder != null) {
            Workbook workbook = new XSSFWorkbook();
            CreationHelper creationHelper = workbook.getCreationHelper();
            Sheet sheet = workbook.createSheet("Data");
            int rowNum = 0;
            int javaLineNum=-1;
            // Add column headers
            Row headerRow = sheet.createRow(rowNum++);
            Cell headerFlux = headerRow.createCell(0);
            headerFlux.setCellValue("flux");
            Cell headerPolice = headerRow.createCell(1);
            headerPolice.setCellValue("police");
            Cell headerID = headerRow.createCell(2);
            headerID.setCellValue("ID");
            Cell headerCodecontrole = headerRow.createCell(3);
            headerCodecontrole.setCellValue("Controle Code");
            int x=-1;
            for (File jfile : jfolder) {
                for (File bifile : bifolder) {
                    if (jfile != null && bifile != null) {
                        //System.out.println(extractenumpolice(jfile.getName()));
                        if (extractenumpolice(jfile.getName().toString()).equals("ICIMWTV19")){
                            //System.out.println("1111111111111111111111111111111111111111111111111");
                        };
                        boolean isFic = comparefilesFic(jfile, bifile);
                        boolean isSin = comparefilesSin(jfile, bifile);
                        boolean isAdh = comparefilesAdh(jfile, bifile);
                        // System.out.print(y++ +"     ");
                        if (isFic || isSin) {
                            if (isFic){
                                boolean result = compareCSVs(jfile.getPath().toString(), bifile.getPath().toString());
                                if (result) {
                                    flux = "fic";
                                    police = extractenumpolice(jfile.getName());
                                    // Add values of flux and police to the controle list
                                    controle.add(flux);
                                    controle.add(police);
                                    x++;
                                    controle.add(controlecode.get(controlecodeindex.get(x)));
                                    Row row = sheet.createRow(rowNum++);
                                    Cell cellFlux = row.createCell(0);
                                    cellFlux.setCellValue(flux);
                                    Cell cellPolice = row.createCell(1);
                                    cellPolice.setCellValue(police);
                                    Cell cellID = row.createCell(2);
                                    cellID.setCellValue(ListID.get(IDlistIndex.get(x)));
                                    Cell cellControle = row.createCell(3);
                                    cellControle.setCellValue(controlecode.get(controlecodeindex.get(x)));
                                    //System.out.println(" rows have matching columns.");
                                } else {
                                    //System.out.println("not mtch .");
                                }
                            }
                            /*if (isAdh){
                                boolean result = compareCSVs(jfile.getPath().toString(), bifile.getPath().toString());

                                if (result) {
                                    flux = "Adhesion";
                                    police = extractenumpolice(jfile.getName());
                                    // Add values of flux and police to the controle list
                                    controle.add(flux);
                                    controle.add(police);
                                    x++;
                                    controle.add(controlecode.get(controlecodeindex.get(x)));
                                    Row row = sheet.createRow(rowNum++);
                                    Cell cellFlux = row.createCell(0);
                                    cellFlux.setCellValue(flux);
                                    Cell cellPolice = row.createCell(1);
                                    cellPolice.setCellValue(police);
                                    Cell cellID = row.createCell(2);
                                    cellID.setCellValue(ListID.get(IDlistIndex.get(x)));
                                    Cell cellControle = row.createCell(3);
                                    cellControle.setCellValue(controlecode.get(controlecodeindex.get(x)));
                                    //System.out.println(" rows have matching columns.");
                                } else {
                                    //System.out.println("not mtch .");
                                }
                            }*/
                            if (isSin){
                                boolean result = compareCSVs(jfile.getPath().toString(), bifile.getPath().toString());
                                if (result) {
                                    x++;
                                    flux = "sinistre";
                                    police = extractenumpolice(jfile.getName());
                                    // Add values of flux and police to the controle list
                                    controle.add(flux);
                                    controle.add(police);
                                    controle.add(controlecode.get(controlecodeindex.get(x)));
                                    Row row = sheet.createRow(rowNum++);
                                    Cell cellFlux = row.createCell(0);
                                    cellFlux.setCellValue(flux);
                                    Cell cellPolice = row.createCell(1);
                                    cellPolice.setCellValue(police);
                                    Cell cellID = row.createCell(2);
                                    cellID.setCellValue(ListID.get(IDlistIndex.get(x)));
                                    Cell cellControle = row.createCell(3);
                                    cellControle.setCellValue(controlecode.get(controlecodeindex.get(x)));
                                } else {
                                    //System.out.println("Values in third and fourth columns are not the same in all rows.");
                                }
                            }
                        }
                    }
                }
            }
            try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                workbook.write(fileOut);
                System.out.println("Excel file written successfully.");
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("Error writing Excel file: " + e.getMessage());
            }
            /*for (String controlValue : controle) {
                Row row = sheet.createRow(rowNum++);
                Cell cellFlux = row.createCell(0);
                cellFlux.setCellValue(flux);
                Cell cellPolice = row.createCell(1);
                cellPolice.setCellValue(police);
                Cell cellControle = row.createCell(2);
                cellControle.setCellValue(controlValue);
                Cell celljava = row.createCell(3);
                cellControle.setCellValue(2);
                row = sheet.createRow(rowNum++);
            }
            try {
                FileOutputStream outputStream = new FileOutputStream(wd + "compaResult2.xlsx");
                workbook.write(outputStream);
                outputStream.close(); // Close the stream here
                workbook.close(); // Close the workbook
            } catch (IOException e) {
                e.printStackTrace();
            }*/
        }
    }
    public static boolean compareCSVs(String filePath1, String filePath2) {
        try (BufferedReader reader1 = new BufferedReader(new FileReader(filePath1))) {
            // Skip the header rows in both files
            reader1.readLine();
            List<String> linesFromFile2 = new ArrayList<>();
            try (BufferedReader reader2 = new BufferedReader(new FileReader(filePath2))) {
                reader2.readLine(); // Skip the header row in the second file
                String line2;
                while ((line2 = reader2.readLine()) != null) {
                    linesFromFile2.add(line2);
                }
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
            String line1;
            while ((line1 = reader1.readLine()) != null) {
                String[] columns1 = line1.split(";");
                //System.out.println(line1);
                if (columns1.length >= 4) {
                    String thirdCol1 = columns1[2];
                    String fourthCol1 = columns1[3];
                    boolean foundMatchingRow = false;
                    for (String line2 : linesFromFile2) {
                        //System.out.println(line2);
                        String[] columns2 = line2.split(";");
                        if (columns2.length >= 4) {
                            String thirdCol2 = columns2[2];
                            String fourthCol2 = columns2[3];
                            if (!thirdCol1.equals(thirdCol2) && !fourthCol1.equals(fourthCol2)) {
                                IDindex++;
                                IDlistIndex.add(IDindex);
                                ListID.add(columns1[2]);

                                codeindex++;
                                controlecodeindex.add(codeindex);
                                controlecode.add(columns1[3]);

                                foundMatchingRow = true;
                                break;
                            }
                        }
                    }

                    if (!foundMatchingRow) {
                        return false; // No matching row found in the second file
                    }
                } else {
                    // Handle columns not present in the row
                }
            }

            return true; // All rows have matching third and fourth columns
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    private static boolean comparefilesFic(File jfile, File filebi) {
        char firstCharacterjava = extractFirstCharacterFromFileName(jfile.getName().toLowerCase());
        char firstCharacterbi = extractFirstCharacterFromFileName(filebi.getName().toLowerCase());
        // traitement
        String s = extractenumpolice(jfile.getName());
        String S2 = extractenumpolice(filebi.getName());
        if (firstCharacterjava == 'c' && firstCharacterbi == 'f' && s.equals(S2)) {
            return true;
        }else {
            return false;
        }
    }
    private static boolean comparefilesSin(File jfile, File filebi) {
        char firstCharacterjava = extractFirstCharacterFromFileName(jfile.getName().toLowerCase());
        char firstCharacterbi = extractFirstCharacterFromFileName(filebi.getName().toLowerCase());
        // traitement
        String s = extractenumpolice(jfile.getName());
        String S2 = extractenumpolice(filebi.getName());
        if (firstCharacterjava == 's' && firstCharacterbi == 's' && s.equals(S2)) {
            return true;
        }else {
            return false;
        }
    }
    private static boolean comparefilesAdh(File jfile, File filebi) {
        char firstCharacterjava = extractFirstCharacterFromFileName(jfile.getName().toLowerCase());
        char firstCharacterbi = extractFirstCharacterFromFileName(filebi.getName().toLowerCase());
        // traitement
        String s = extractenumpolice(jfile.getName());
        String S2 = extractenumpolice(filebi.getName());
        if (firstCharacterjava == 'a' && firstCharacterbi == 'a' && s.equals(S2)) {
            return true;
        }else {
            return false;
        }
    }
    private static char extractFirstCharacterFromFileName(String fileName) {
        if (!fileName.isEmpty()) {
            return fileName.charAt(0);
        } else {
            return '\0'; // Return null character if file name is empty
        }
    }
    // Méthode pour extraire le code du nom du fichier (après le '_')
    private static String extractenumpolice(String name) {
        int startIndex = name.indexOf('_') + 1;
        int endIndex = name.lastIndexOf('.');
        if (startIndex >= 0 && endIndex >= 0) {
            return name.substring(startIndex, endIndex);
        } else {
            return "";
        }
    }

}
