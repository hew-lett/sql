package main.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static main.app.App.in;

public class Grille_columns {
    public static Map<String, ArrayList<String>> multiValueMap = new HashMap<>();

    Grille_columns() {
        multiValueMap.put("BH", new ArrayList<>());
        multiValueMap.get("BH").add("Valeur_Catalogue Borne haute");
        multiValueMap.get("BH").add("Valeur_Achat Borne haute");
        multiValueMap.get("BH").add("Date_Clôture borne haute");
        multiValueMap.put("BB", new ArrayList<>());
        multiValueMap.get("BB").add("Valeur_Catalogue Borne basse");
        multiValueMap.get("BB").add("Valeur_Achat Borne basse");
        multiValueMap.get("BB").add("Date_Clôture borne basse");
        multiValueMap.put("SMIP", new ArrayList<>());
        multiValueMap.get("SMIP").add("Signe Montant_Indemnité_Principale");
        multiValueMap.put("PMIP", new ArrayList<>());
        multiValueMap.get("PMIP").add("Pourcentage Montant_Indemnité_Principale");
        multiValueMap.put("MIP", new ArrayList<>());
        multiValueMap.get("MIP").add("Valeur Montant_Indemnité_Principale");
        multiValueMap.put("A", new ArrayList<>());
        multiValueMap.get("A").add("Age");
        multiValueMap.put("RCC", new ArrayList<>());
        multiValueMap.get("RCC").add("Retraitement Code_Client");
        multiValueMap.put("RM", new ArrayList<>());
        multiValueMap.get("RM").add("Référentiel Marque");
        multiValueMap.put("DEFAULT", new ArrayList<>());
        multiValueMap.get("DEFAULT").add("DEFAULT");
    }

    public static String get_type(String value) {
        for (Map.Entry<String, ArrayList<String>> entry : multiValueMap.entrySet()) {
            if (in(value,entry.getValue().toArray())) {
                return entry.getKey();
            }
        }
        return null;
    }

}
