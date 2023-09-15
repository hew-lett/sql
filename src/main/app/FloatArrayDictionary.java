package main.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import java.util.stream.Collectors;

public class FloatArrayDictionary {
    private static final HashMap<String, ArrayList<Float>> uniqueArrays = new HashMap<>();
    private static int totalArraysPassed = 0;
    private static int uniqueArraysStored = 0;

    public static ArrayList<Float> getOrAdd(ArrayList<Float> list) {
        totalArraysPassed++;

        // Convert the list to a string representation
        String key = list.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        if (!uniqueArrays.containsKey(key)) {
            uniqueArrays.put(key, list);
            uniqueArraysStored++;
        }
        return uniqueArrays.get(key);
    }

    public static int getTotalArraysPassed() {
        return totalArraysPassed;
    }

    public static int getUniqueArraysStored() {
        return uniqueArraysStored;
    }
}


