package main.app;

import java.util.Arrays;
import java.util.HashMap;

public class FloatArrayDictionary {
    private static final HashMap<String, float[]> uniqueArrays = new HashMap<>();
    private static int totalArraysPassed = 0;
    private static int uniqueArraysStored = 0;

    public static float[] getOrAdd(float[] array) {
        totalArraysPassed++;

        String key = Arrays.toString(array);  // This creates a string representation of the array
        if (!uniqueArrays.containsKey(key)) {
            uniqueArrays.put(key, array);
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

