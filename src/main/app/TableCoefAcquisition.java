package main.app;

import java.text.SimpleDateFormat;
import java.util.*;
import static main.app.App.*;
import static main.app.DF.find_in_arr_first_index;

public class TableCoefAcquisition {

    private TableCoefAcquisition() {
        // Private constructor to prevent instantiation
    }

    public static Map<String, List<Object>> processDF(DFnew dataframe) {
        Map<String, List<Object>> localResultMap = new HashMap<>();

        int identContratIndex = dataframe.headers.indexOf("identifiant_contrat");
        int dateSousIndex = dataframe.headers.indexOf("date_debut_periode_souscription");
        int numAdheIndex = dataframe.headers.indexOf("nombre_adhesions");
        int primeAssureurIndex = dataframe.headers.indexOf("montant_net_compagnie");
        int mIndex = dataframe.headers.indexOf("m");

        int totalRows = dataframe.nrow;
        for (int i = 0; i < totalRows; i++) {
            ArrayList<Object> row = dataframe.getRow(i);

            String contrat = (String) row.get(identContratIndex);
            Date date = (Date) row.get(dateSousIndex);

            String combinedKey = contrat + "_" + date;

            Integer nombre_adhesions = (Integer) row.get(numAdheIndex);
            Double montant_prime_assureur = (Double) row.get(primeAssureurIndex);

            ArrayList<Float> coefficients = new ArrayList<>();
            for (int j = 0; j < 201; j++) {
                coefficients.add((Float) row.get(mIndex + j));
            }

            coefficients = FloatArrayDictionary.getOrAdd(coefficients);
            List<Object> valuesList = Arrays.asList(nombre_adhesions, montant_prime_assureur, coefficients);
            localResultMap.put(combinedKey, valuesList);
        }

        return localResultMap;
    }
}



