package main.app;

import java.text.SimpleDateFormat;
import java.util.*;
import static main.app.App.*;
import static main.app.DF.find_in_arr_first_index;

public class TableCoefAcquisition {
    private static Map<String, List<Object>> resultMap = new HashMap<>();

    public TableCoefAcquisition(DF dataframe) {
        int identContratIndex = find_in_arr_first_index(dataframe.header, "identifiant_contrat");
        int dateSousIndex = find_in_arr_first_index(dataframe.header, "date_debut_periode_souscription");
        int numAdheIndex = find_in_arr_first_index(dataframe.header, "nombre_adhesions");
        int primeAssureurIndex = find_in_arr_first_index(dataframe.header, "montant_net_compagnie");
        int mIndex = find_in_arr_first_index(dataframe.header, "m");
        for (int i = 0; i < dataframe.nrow; i++) {
            Object[] row = dataframe.r(i);
            String identifiant_contrat = (String) row[identContratIndex];
            SimpleDateFormat sdf = new SimpleDateFormat("MM-yyyy");
            String date = sdf.format(row[dateSousIndex]);

            // Concatenate keys
            String combinedKey = identifiant_contrat + "_" + date;

            Double nombre_adhesions = (Double) row[numAdheIndex];
            Double montant_prime_assureur = (Double) row[primeAssureurIndex];
            float[] coefficients = new float[201];
            for (int j = 0; j < 201; j++) {
                coefficients[j] = (Float) row[mIndex + j];
            }
            coefficients = FloatArrayDictionary.getOrAdd(coefficients);
            List<Object> valuesList = Arrays.asList(nombre_adhesions, montant_prime_assureur, coefficients);
            resultMap.put(combinedKey, valuesList);
        }
    }

    public static Map<String, List<Object>> getResultMap() {
        return resultMap;
    }
}


