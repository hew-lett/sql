package main.app;

import java.io.Serializable;
import java.util.*;

public class TableCoefAcquisition implements Serializable {
    private static final long serialVersionUID = 1L;
    private TableCoefAcquisition() {
        // Private constructor to prevent instantiation
    }

    public static Map<String, ArrayList<Float>> processDF(DF dataframe) {
        Map<String, ArrayList<Float>> localResultMap = new HashMap<>();

        int identContratIndex = dataframe.headers.indexOf("identifiant_contrat");
        int dateSousIndex = dataframe.headers.indexOf("date_debut_periode_souscription");
        int mIndex = dataframe.headers.indexOf("m");

        int totalRows = dataframe.nrow;
        for (int i = 0; i < totalRows; i++) {
            ArrayList<Object> row = dataframe.getRow(i);

            String contrat = (String) row.get(identContratIndex);
            Date date = (Date) row.get(dateSousIndex);

            String combinedKey = contrat + "_" + date;

            ArrayList<Float> coefficients = new ArrayList<>();
            for (int j = 0; j < 201; j++) {
                coefficients.add((Float) row.get(mIndex + j));
            }

            coefficients = FloatArrayDictionary.getOrAdd(coefficients);
            localResultMap.put(combinedKey, coefficients);
        }

        return localResultMap;
    }
}



