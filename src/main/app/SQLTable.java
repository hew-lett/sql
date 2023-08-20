package main.app;
import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
public class SQLTable {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/ICI";
    private static final String USER = "root";
    private static final String PASSWORD = "Hewlett77*";
    private static final String CSV_PATH = "E:/202305/wd/source SIN/SPB France/Sinistre_Historique_ICIBPOT21_470_20230712.txt";

    public static void main(String[] args) {
        try {
            Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);

            String createTableSQL = createTableFromCsvHeader(CSV_PATH);
            Statement stmt = connection.createStatement();
            stmt.execute(createTableSQL);

            // Step 2: Insert CSV Data into the Table
            PreparedStatement pstmt = prepareInsertStatementFromCsv(CSV_PATH, connection);
            pstmt.executeBatch();

            connection.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String createTableFromCsvHeader(String csvFilePath) throws Exception {
        BufferedReader reader = new BufferedReader(new FileReader(csvFilePath));
        String header = reader.readLine();  // First line
        reader.close();

        String[] columns = header.split(";");

        StringBuilder sqlBuilder = new StringBuilder("CREATE TABLE csv_data (");

        for (int i = 0; i < columns.length; i++) {
            if (i > 0) {
                sqlBuilder.append(", ");
            }
            sqlBuilder.append(columns[i]).append(" ");

            if (columns[i].equals("age")) {
                sqlBuilder.append("INT");
            } else {
                sqlBuilder.append("VARCHAR(255)");
            }
        }
        sqlBuilder.append(");");

        return sqlBuilder.toString();
    }

    private static PreparedStatement prepareInsertStatementFromCsv(String csvFilePath, Connection connection) throws Exception {
        BufferedReader reader = new BufferedReader(new FileReader(csvFilePath));
        String header = reader.readLine();  // Skip header

        String[] columns = header.split(";");
        StringBuilder sqlBuilder = new StringBuilder("INSERT INTO csv_data (");

        for (int i = 0; i < columns.length; i++) {
            if (i > 0) {
                sqlBuilder.append(", ");
            }
            sqlBuilder.append(columns[i]);
        }
        sqlBuilder.append(") VALUES (");
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) {
                sqlBuilder.append(", ");
            }
            sqlBuilder.append("?");
        }
        sqlBuilder.append(");");

        PreparedStatement pstmt = connection.prepareStatement(sqlBuilder.toString());

        String line;
        while ((line = reader.readLine()) != null) {
            String[] values = line.split(";");
            for (int i = 0; i < values.length; i++) {
                if (columns[i].equals("age")) {
                    pstmt.setInt(i + 1, Integer.parseInt(values[i]));
                } else {
                    pstmt.setString(i + 1, values[i]);
                }
            }
            pstmt.addBatch();
        }

        reader.close();
        return pstmt;
    }
}