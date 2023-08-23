package main.app;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class DateConverter {

    private static final Map<String, Integer> MONTHS_MAP = new HashMap<>();

    static {
        MONTHS_MAP.put("jan", 1);
        MONTHS_MAP.put("feb", 2);
        MONTHS_MAP.put("mar", 3);
        MONTHS_MAP.put("apr", 4);
        MONTHS_MAP.put("may", 5);
        MONTHS_MAP.put("jun", 6);
        MONTHS_MAP.put("jul", 7);
        MONTHS_MAP.put("aug", 8);
        MONTHS_MAP.put("sep", 9);
        MONTHS_MAP.put("oct", 10);
        MONTHS_MAP.put("nov", 11);
        MONTHS_MAP.put("dec", 12);
    }

    public static Date stringToDate(String input) {
        // Splitting by '.' to get month and year
        String[] parts = input.split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid input format");
        }

        // Extract month and year from input string
        String monthStr = parts[0];
        Integer month = MONTHS_MAP.get(monthStr.toLowerCase());

        if (month == null) {
            throw new IllegalArgumentException("Invalid month abbreviation");
        }

        String year = parts[1];
        String formattedDate = "1." + month + "." + year;

        SimpleDateFormat sdf = new SimpleDateFormat("d.M.yyyy");
        try {
            return sdf.parse(formattedDate);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Error parsing the date", e);
        }
    }

    public static void main(String[] args) {
        System.out.println(stringToDate("mar.2034"));
    }
}
