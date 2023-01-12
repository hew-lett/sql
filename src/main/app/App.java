package main.app;

import com.monitorjbl.xlsx.StreamingReader;
import com.opencsv.CSVWriter;
import com.opencsv.CSVWriterBuilder;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.apache.poi.ss.usermodel.Workbook;
import org.w3c.dom.ls.LSOutput;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLOutput;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

import static java.lang.Math.round;
import static java.util.Arrays.fill;

public class App {

//    public static final String wd = "E:/java_certif/wd/";
    public static final String wd = "C:/Users/ozhukov/Desktop/wd/";
    public static final String path_grilles = wd + "grilles/";
    public static final String path_grille_SS = "Grille SS sinistre BI.xlsx";
    public static String encoding = "UTF-8";
    public static CsvParserSettings csv_settings = new CsvParserSettings();
    public static final String regex_digits = "[0-9]+";
    public static final String regex_letters = ".*[a-zA-Z].*";
    public static final Double NA_DBL = 9999099d;
    public static final String NA_STR = "n.a.";
    public static final SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy");
    public static final Date NA_DAT;

    static {
        try {
            NA_DAT = format.parse("01/01/2100");
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public static final LocalDate NA_LDAT = to_Date(NA_DAT);
    public static DF dispatch_pol;
    public static DF mapping_sin_g;
    public static DF mapping_adh_g;
    public static String mapping_sin_col = "default";
    public static String mapping_fic_col = "default";
    public static String mapping_adh_col = "default";
    public static DF grille_gen_g;
    public static DF paths;
    public static DF parametrage;
    public static String Pays_en_cours = "default";
    public static String Gestionnaire_en_cours = "default";
    public static String Police_en_cours = "default";
    public static String Police_en_cours_maj = "default";
    public static String Controle_en_cours = "default";
    public static String Flux_en_cours = "default";
    public static ArrayList<ArrayList<String>> Rapport = new ArrayList<>();
    public static ArrayList<ArrayList<String>> Rapport_temps_exec = new ArrayList<>();
    public static ArrayList<ArrayList<String>> Log_err = new ArrayList<>();
    public static HashMap<String, DF.Col_types> coltypes_G = new HashMap<String, DF.Col_types>();
    public static HashMap<String, DF.Col_types> coltypes_B = new HashMap<String, DF.Col_types>();
    public static HashMap<String, DF> grilles_G = new HashMap<String, DF>();
    public static HashMap<String, Method> controles_G = new HashMap<>();
    public static HashMap<String, Method> controles_fic_G = new HashMap<>();
    public static HashMap<String, Boolean> params_G = new HashMap<>();
    public static HashMap<String, Boolean> params_fic_G = new HashMap<>();
    public static String yyyymm = "default";

    public static void main(String[] args) throws IOException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, InterruptedException {
//        grilles_collect(path_grille_SS); // le premier lancement chaque mois
        rapport_init();
        get_paths_et_parametrage();
        get_coltypes();
        get_controles();
        get_grilles();
        grille_gen_global_init();
        mapping_global_init();
        get_yyyymm();
        paths.print();
        parametrage.print();

        // RAPPORT SIN
        Object[] list_pays = unique_of(paths.c("Pays"));
        for (Object pays : list_pays) {
            long startTime = System.nanoTime();

            Pays_en_cours = (String) pays;
            Object[] list_gestionnaire = unique_of(paths.c_filtre("Gestionnaire","Pays",Pays_en_cours));
            for (Object gest : list_gestionnaire) {
                Gestionnaire_en_cours = (String) gest;
                System.out.println();
                System.out.println("---" + Gestionnaire_en_cours + "---");
                if(!Gestionnaire_en_cours.equals("Supporter")) {
                    encoding = "UTF-8";
                } else {
                    encoding = "Cp1252";
                }
                get_map_cols();
                DF map_fic = new DF();  DF map_sin = new DF(); DF map_adh = new DF();
                if (!Gestionnaire_en_cours.equals("Gamestop")) {
                    map_sin = mapping_filtre(true);
                    if(!Objects.equals(mapping_fic_col, "N.A.")){
                        map_fic = mapping_filtre_fic();
                    }
                    map_adh = mapping_filtre(false);
                }
//                System.out.println("checkk");
//                System.out.println(Gestionnaire_en_cours);
//                System.out.println(Flux_en_cours);
                int ind = paths.ind_filtre_2_crit_1_value("Gestionnaire",Gestionnaire_en_cours,"Flux","Sinistre");
                String dossier_sin = (String) paths.c("Path")[ind];
                char delim_sin = get_delim((String) paths.c("Delimiter")[ind]);

                ind = paths.ind_filtre_2_crit_1_value("Gestionnaire",Gestionnaire_en_cours,"Flux","Comptable");
                String dossier_fic = (String) paths.c("Path")[ind];
                char delim_fic = get_delim((String) paths.c("Delimiter")[ind]);

                ind = paths.ind_filtre_2_crit_1_value("Gestionnaire",Gestionnaire_en_cours,"Flux","Adhesion");
                String dossier_adh = (String) paths.c("Path")[ind];
                char delim_adh = get_delim((String) paths.c("Delimiter")[ind]);

                String[] list_sin = new File(wd+dossier_sin).list();
                String[] list_fic = new File(wd+dossier_fic).list();
                String[] list_adh = new File(wd+dossier_adh).list();
                list_sin = filtre_path_par_gest(list_sin,"Sinistre");
                list_adh = filtre_path_par_gest(list_adh,"Adhésion");

                if (check_flux("Sinistre") | check_flux("Comptable")) {
                    write_temps_exec(Gestionnaire_en_cours,"","prep",((System.nanoTime() - startTime) / 1e7f) / 100.0 + "");
                }
                startTime = System.nanoTime();
                Flux_en_cours = "Sinistre";
                if (check_flux(Flux_en_cours)) {
                    if (list_sin == null) {
                        err_simple("dossier sinistres vide!");
                        continue;
                    }
                    if (list_adh == null) {
                        err_simple("dossier adhesions vide!");
                        continue;
                    }

                    for (String path_sin : list_sin) {
                        Police_en_cours_maj = get_name(path_sin);
                        Police_en_cours = Police_en_cours_maj.toLowerCase();
                        if(check_grille_gen()) continue;
                        if(!Police_en_cours_maj.equals("ICIMWGP17")) continue;
//                        System.out.println(((System.nanoTime() - startTime) / 1e7f) / 100.0);

                        System.out.println("sin " + Police_en_cours_maj);

                        if(!Objects.equals(Gestionnaire_en_cours, "Gamestop")) {
                            get_map_cols();
                            map_sin = mapping_filtre(true);
                            map_adh = mapping_filtre(false);
                        }
//                        DF base = new DF(wd + dossier_sin + path_sin, delim_sin, true, map_sin);
//                        DF base_adh = new DF(wd + dossier_adh + get_path_adh(list_adh), delim_adh, true, map_adh);
                        DF base = new DF("C:/Users/ozhukov/Desktop/b.csv", delim_sin, true, map_sin);
                        DF base_adh = new DF("C:/Users/ozhukov/Desktop/m.csv", delim_sin, true, map_sin);
                        base.get_grille_gen();
                        if(base.grille_gen.df == null) {
                            err_simple("grille gen absente!");
                            continue;
                        }

                        write_temps_exec(Police_en_cours_maj,Flux_en_cours,"prep",((System.nanoTime() - startTime) / 1e7f) / 100.0 + "");
                        startTime = System.nanoTime();

                        for (Map.Entry<String, Method> set : controles_G.entrySet()) {
                            if(Police_en_cours_maj.equals("ICICDDP19")) {
                                System.out.println(((System.nanoTime() - startTime) / 1e7f) / 100.0);
                                System.out.println(set.getKey());
                            }
//                            if(!Objects.equals(set.getKey(), "controle_807")) continue;
                            if (params_G.get(set.getKey())) {
                                set.getValue().invoke(base, base_adh);
                            } else {
                                set.getValue().invoke(base);
                            }
                            write_temps_exec(Police_en_cours_maj,Flux_en_cours,set.getKey(),((System.nanoTime() - startTime) / 1e7f) / 100.0 + "");
                            startTime = System.nanoTime();
                        }
                        rapport_save();

                        write_temps_exec(Police_en_cours_maj,Flux_en_cours,"prep",((System.nanoTime() - startTime) / 1e7f) / 100.0 + "");
                        startTime = System.nanoTime();
                    } // par police
                }
                Flux_en_cours = "Comptable";
                if (check_flux(Flux_en_cours)) {
                    if (list_fic == null) {
                        err_simple("dossier fic vide!");
                        continue;
                    }
                    if (list_sin == null) {
                        err_simple("dossier sinistres vide!");
                        continue;
                    }

                    DF base_fic_total = new DF();
                    if(Gestionnaire_en_cours.equals("SPB France")) {
                        base_fic_total = new DF(wd+dossier_fic, map_fic);
                        ind = find_in_arr_first_index(base_fic_total.header,"Montant_Indemnité_Principale");  // bequille france
                        base_fic_total.header[ind] = "FIC_Montant_reglement";  // bequille france
                    }
                    if(Gestionnaire_en_cours.equals("SPB Italie")) {
                        ind = which_contains_first_index(list_fic,"DBCLAIMS");
                        base_fic_total = new DF(wd + dossier_fic + list_fic[ind], delim_fic, true, map_fic);
                    }

                    write_temps_exec(Gestionnaire_en_cours,"","prep",((System.nanoTime() - startTime) / 1e7f) / 100.0 + "");
                    startTime = System.nanoTime();

                    for (String path_sin : list_sin) {
//                        System.out.println(path_sin);

                        Police_en_cours_maj = get_name(path_sin);
                        Police_en_cours = Police_en_cours_maj.toLowerCase();
//                        if(!Police_en_cours_maj.contains("MMPC")) continue;

                        System.out.println("fic " + Police_en_cours_maj);
                        if(check_grille_gen()) continue;

                        if(Objects.equals(Gestionnaire_en_cours, "Gamestop")) {
                            get_map_cols();
                            map_sin = mapping_filtre(true);
                            map_fic = mapping_filtre_fic();
                        }

                        DF base = new DF(wd + dossier_sin + path_sin, delim_sin, true, map_sin);
                        DF base_fic = get_fic(dossier_fic, list_fic, delim_fic, map_fic, base_fic_total);
                        if(base_fic.df == null) {
                            err_simple("fic absent!");
                            continue;
                        }

                        base_fic.get_grille_gen();
                        if(base_fic.grille_gen.df == null) {
                            err_simple("grille gen absente!");
                            continue;
                        }

                        write_temps_exec(Police_en_cours_maj,Flux_en_cours,"prep",((System.nanoTime() - startTime) / 1e7f) / 100.0 + "");
                        startTime = System.nanoTime();

                        base_fic.fic_controle_K0(map_fic);
                        for (Map.Entry<String, Method> set : controles_fic_G.entrySet()) {
//                            System.out.println(set.getKey());
                            if (params_fic_G.get(set.getKey())) {
                                set.getValue().invoke(base_fic, base);
                            } else {
                                set.getValue().invoke(base_fic);
                            }
                            write_temps_exec(Police_en_cours_maj,Flux_en_cours,set.getKey(),((System.nanoTime() - startTime) / 1e7f) / 100.0 + "");
                            startTime = System.nanoTime();
                        }
                        rapport_save();

                        write_temps_exec(Police_en_cours_maj,Flux_en_cours,"prep",((System.nanoTime() - startTime) / 1e7f) / 100.0 + "");
                        startTime = System.nanoTime();
                    } // par police
                }

//                System.out.println(Gestionnaire_en_cours + " terminé à:");
//                System.out.println(((System.nanoTime() - startTime) / 1e7f) / 100.0);

            } // par gest
        } // par pays
        System.out.println(Rapport_temps_exec);
        temps_exec_save();
        log_err_save();
//        System.out.println(((System.nanoTime() - startTime) / 1e7f) / 100.0);

    }
    public static ArrayList<String> filter_out (ArrayList<ArrayList<String>> df, String crit1, String val1, String crit2, String val2, String field) {
        ArrayList<String> out = new ArrayList<>();
        int ncol = df.size();
        int nrow = df.get(0).size();
        int ind1 = -1; int ind2 = -1; int ind3 = -1;
        for (int i = 0; i < ncol; i++) {
            if (Objects.equals(df.get(i).get(0), crit1)) {
                ind1 = i;
            }
            if (Objects.equals(df.get(i).get(0), crit2)) {
                ind2 = i;
            }
            if (Objects.equals(df.get(i).get(0), field)) {
                ind3 = i;
            }
        }
        for (int i = 0; i < nrow; i++) {
            if(df.get(ind1).get(i).equals(val1)) {
                if(df.get(ind2).get(i).equals(val2)) {
                    out.add(df.get(ind3).get(i));
                }
            }
        }
        return out;
    }
    public static String[] filtre_path_par_gest(String[] listSin, String flux) {
        String filtering_pattern;

        switch (Gestionnaire_en_cours) {
            case "Supporter", "SPB Pologne", "SPB Espagne", "SPB France" -> {
                return listSin;
            }
            case "SPB Italie" -> filtering_pattern = "ICIMW";
            case "Expert" -> filtering_pattern = "ICIEXTR";
            case "Distante" -> filtering_pattern = "ICIEXDI";
            case "Gamestop" -> {
                if (flux.equals("Sinistre")) {
                    filtering_pattern = "Gamestop";
                } else {
                    filtering_pattern = "GS";
                }
            }
            default -> {
                err_simple("probleme filtering files");
                return listSin;
            }
        }
        return(filter_array_by_containing(listSin, filtering_pattern));
    }
    // INTEGRATION
    public static boolean check_grille_gen() {
        boolean[] keep = find_in_arr(grille_gen_g.c("Numero_Police"), Police_en_cours_maj);
        boolean[] keep2 = find_in_arr(grille_gen_g.c("Flux"), Flux_en_cours);
        boolean[] crit = b_and(keep, keep2);
        return (sum_boolean(crit) == 0);
    }
    public static boolean check_flux(String flux) {
        return (parametrage.c_filtre_2("Statut", "Gestionnaire", Gestionnaire_en_cours, "Flux", flux)[0].equals("oui"));
    }
    public static void get_coltypes() throws IOException {
        String coltypes_g = "coltypes.csv";
        String coltypes_b = "coltypes_base.csv";
        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true, ',');
        settings.trimValues(true);
        try (Reader inputReader = new InputStreamReader(Files.newInputStream(
                new File(wd + coltypes_g).toPath()), encoding)) {
            CsvParser parser = new CsvParser(settings);
            List<String[]> parsedRows = parser.parseAll(inputReader);
            for (String[] values : parsedRows) {
                switch (values[1]) {
                    case "1":
                        coltypes_G.put(values[0], DF.Col_types.STR);
                        break;
                    case "2":
                        coltypes_G.put(values[0], DF.Col_types.DBL);
                        break;
                    case "3":
                        coltypes_G.put(values[0], DF.Col_types.DAT);
                        break;
                    case "4":
                        coltypes_G.put(values[0], DF.Col_types.SKP);
                }
            }
        }

        try (Reader inputReader = new InputStreamReader(Files.newInputStream(
                new File(wd + coltypes_b).toPath()), encoding)) {
            CsvParser parser = new CsvParser(settings);
            List<String[]> parsedRows = parser.parseAll(inputReader);
            Iterator<String[]> rows = parsedRows.iterator();
            int i = 0;
            while (rows.hasNext()) {
                String[] values = rows.next();
                switch (values[1]) {
                    case "1":
                        coltypes_B.put(values[0], DF.Col_types.STR);
                        break;
                    case "2":
                        coltypes_B.put(values[0], DF.Col_types.DBL);
                        break;
                    case "3":
                        coltypes_B.put(values[0], DF.Col_types.DAT);
                        break;
                    case "4":
                        coltypes_B.put(values[0], DF.Col_types.SKP);
                }
            }
        }
    }
    public static void get_controles() {
        Class<DF> classobj = DF.class;
        Method[] methods = classobj.getMethods();
        for (Method method : methods) {
            String name = method.getName();
            if (name.startsWith("controle")) {
                controles_G.put(name, method);
                Class<?>[] types = method.getParameterTypes();
                if (types.length > 0) {
                    params_G.put(name, true);
                } else {
                    params_G.put(name, false);
                }
            } else if (name.startsWith("fic_controle")) {
//                System.out.println(name);
//                System.out.println(name.startsWith("fic_controle_KO"));
                if (name.equals("fic_controle_K0")) continue;
                controles_fic_G.put(name, method);
                Class<?>[] types = method.getParameterTypes();
                if (types.length > 0) {
                    params_fic_G.put(name, true);
                } else {
                    params_fic_G.put(name, false);
                }
            }
        }
    }
    public static char get_delim(String delim) {
        if(delim.length() > 1) {
            return '\t';
        } else {
            return delim.charAt(0);
        }
    }
    public static DF get_fic(String dossier_fic, String[] list_fic, char delim_fic, DF map_fic, DF base_fic_total) {
        switch (Gestionnaire_en_cours) {
            case "SPB France":
            case "SPB Italie":
                return base_fic_total.filter_out("Numéro_Police", Police_en_cours);
            case "Expert":
                int ind = which_contains_first_index(list_fic,"EXPERT");
                return new DF(wd + dossier_fic + list_fic[ind], delim_fic, true, map_fic);
            case "Distante":
                ind = which_contains_first_index(list_fic,"DISTANTE");
                return new DF(wd + dossier_fic + list_fic[ind], delim_fic, true, map_fic);
            case "Gamestop":
                ind = which_contains_first_index(list_fic,"GS");
                return new DF(wd + dossier_fic + list_fic[ind], delim_fic, true, map_fic);
            case "SPB Pologne":
                ind = which_contains_first_index(list_fic,Police_en_cours_maj);
                return new DF(wd + dossier_fic + list_fic[ind], delim_fic, true, map_fic);
            default:
                return new DF();
        }
    }
    public static String get_name(String path) {
        int debut = path.indexOf("ICI");

        switch (Gestionnaire_en_cours) {
            case "SPB France":
                ArrayList<Integer> ind = get_all_occurences(path, '_');
                if (ind.isEmpty()) {
                    err("pb naming france: " + path);
                    return "";
                } else {
                    return path.substring(ind.get(1) + 1, ind.get(2));
                }
            case "SPB Italie":
            case "Expert":
            case "Distante":
                ind = get_all_occurences(path, '.');
                if (ind.isEmpty()) {
                    err("pb naming italie: " + path);
                    return "";
                } else {
                    return path.substring(debut, ind.get(0));
                }
            case "Gamestop":
                int fin = path.indexOf(" at");
                if (fin == -1) {
                    err("pb naming italie: " + path);
                    return "";
                } else {
                    return path.substring(debut, fin);
                }
            case "SPB Pologne":
                ind = get_all_occurences(path, '_');
                ArrayList<Integer> ind2 = get_all_occurences(path, '.');
                if (ind.isEmpty()) {
                    err("pb naming pol: " + path);
                    return "";
                } else {
                    return path.substring(ind.get(1) + 1, ind2.get(0));
                }
            case "SPB Espagne":
            case "Supporter":
                ind = get_all_occurences(path, '_');
                if (ind.isEmpty()) {
                    err("pb naming esp/sup: " + path);
                    return "";
                } else {
                    return path.substring(0, ind.get(0));
                }
        }
        return "";
    }
    public static void get_map_cols() {
        if(Gestionnaire_en_cours.equals("Gamestop")) {
            boolean[] crit1 = paths.bool_filtre("Flux","Comptable");
            boolean[] crit2 = paths.bool_filtre("Gestionnaire", Gestionnaire_en_cours);
            int ind = (int) whichf(b_and(crit1,crit2));
            mapping_fic_col = (String) paths.c("Mapping")[ind];
        } else {
            boolean[] crit1 = paths.bool_filtre("Flux","Sinistre");
            boolean[] crit2 = paths.bool_filtre("Gestionnaire", Gestionnaire_en_cours);
            int ind = (int) whichf(b_and(crit1,crit2));
            mapping_sin_col = (String) paths.c("Mapping")[ind];
            crit1 = paths.bool_filtre("Flux", "Comptable");
            ind = (int) whichf(b_and(crit1,crit2));
            mapping_fic_col = (String) paths.c("Mapping")[ind];
            crit1 = paths.bool_filtre("Flux", "Adhesion");
            ind = (int) whichf(b_and(crit1,crit2));
            mapping_adh_col = (String) paths.c("Mapping")[ind];
        }
    }
    public static String get_path_adh(String[] listfiles) {
        for (String listfile : listfiles) {
            if (listfile.contains(Police_en_cours_maj)) {
                return listfile;
            }
        }
        return listfiles[0];
    }
    public static void get_paths_et_parametrage() throws IOException {
        paths = new DF(wd+"paths.xlsx",0,true,false);
        parametrage = new DF(wd+"parametrage lancement.xlsx",0,true,false);
        csv_settings.trimValues(true);
        csv_settings.setIgnoreLeadingWhitespaces(true);
        csv_settings.setIgnoreTrailingWhitespaces(true);
        csv_settings.setIgnoreLeadingWhitespacesInQuotes(true);
        csv_settings.setIgnoreTrailingWhitespacesInQuotes(true);
    }
    public static void get_yyyymm() {
        Date today = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(today);
        int month = cal.get(Calendar.MONTH) - 1;
        int year = cal.get(Calendar.YEAR);
        yyyymm = year +  String.format("%02d", month);
    }
    public static void grille_gen_global_init() {
        String path_gg = "Grille Générique.csv";
        char delim_gg = ';';
        grille_gen_g = new DF(wd + path_gg, delim_gg, false);
    }
    public static DF mapping_filtre(boolean sinistre) {
        if (sinistre) {
            boolean[] vec = logvec(mapping_sin_g.ncol, false);
            int ind;
            if(Gestionnaire_en_cours.equals("Gamestop")) {
                ind = which_contains_first_index(mapping_sin_g.r(0),Police_en_cours_maj);
            } else {
                ind = find_in_arr_first_index(mapping_sin_g.header, mapping_sin_col);
            }
            vec[0] = true; // sous condition que la colonne format ICI était toujours la premiere
            vec[ind] = true;
            return new DF(mapping_sin_g, vec, true);
        } else {
            boolean[] vec = logvec(mapping_adh_g.ncol, false);
            int ind;
            if(Gestionnaire_en_cours.equals("Gamestop")) {
                ind = which_contains_first_index(mapping_adh_g.r(0),Police_en_cours_maj);
            } else {
                ind = find_in_arr_first_index(mapping_adh_g.header, mapping_adh_col);
            }
            assert (ind != -1);
            vec[0] = true; // sous condition que la colonne format ICI était toujours la premiere
            vec[ind] = true;
            return new DF(mapping_adh_g, vec, true);
        }
    }
    public static DF mapping_filtre_fic() {
        boolean[] vec = logvec(mapping_sin_g.ncol, false);
        int ind = find_in_arr_first_index(mapping_sin_g.header, mapping_fic_col);
        assert (ind != -1);
        vec[0] = true; // sous condition que la colonne format ICI était toujours la premiere
        vec[ind] = true;
        return new DF(mapping_sin_g, vec, true);
    }
    public static void mapping_global_init() throws IOException {
        String path_mapping = "Mapping des flux adhésion et sinistre gestionnaire.xlsx";
        String mapping_sin_onglet = "Mapping entrant sinistres";
        String mapping_adh_onglet = "Mapping entrant adhésions";
        String dispatch_onglet = "Regles calcul dispatch";
        mapping_sin_g = new DF(wd + path_mapping, mapping_sin_onglet, true, false);
        mapping_adh_g = new DF(wd + path_mapping, mapping_adh_onglet, true, false);
        dispatch_pol = new DF(wd + path_mapping, dispatch_onglet, true, false);
        dispatch_pol.filter_in("Flux Entrant","FIC Pologne");
        dispatch_pol.print();
//        mapping_sin_g.delete_blanks_first_col();
//        mapping_adh_g.delete_blanks_first_col();
    }
    public static void write_temps_exec(String quoi, String flux, String controle, String temps) {
        Rapport_temps_exec.get(0).add(quoi);
        Rapport_temps_exec.get(1).add(flux);
        Rapport_temps_exec.get(2).add(controle);
        Rapport_temps_exec.get(3).add(temps);
    }
    public static void rapport_init() {
        String[] rapport_cols = {"Police", "Flux", "Controle", "ID"};
        for (int i = 0; i < rapport_cols.length; i++) {
            Rapport.add(new ArrayList<>());
            Rapport.get(i).add(rapport_cols[i]);
        }

        String[] rapport_log_cols = {"Police", "Flux", "Controle", "Commentaire"};
        for (int i = 0; i < rapport_log_cols.length; i++) {
            Log_err.add(new ArrayList<>());
            Log_err.get(i).add(rapport_log_cols[i]);
        }

        String[] temps_exec_cols = {"Police", "Flux", "Controle", "Temps"};
        for (int i = 0; i < temps_exec_cols.length; i++) {
            Rapport_temps_exec.add(new ArrayList<>());
            Rapport_temps_exec.get(i).add(temps_exec_cols[i]);
        }
    }
    public static void rapport_print() {
        for (int i = 0; i < Rapport.get(0).size(); i++) {
            System.out.print("| ");
            for (ArrayList<String> strings : Rapport) {
                System.out.print(strings.get(i) + " | ");
            }
            System.out.println();
        }
    }
    public static void rapport_save() {
        BufferedWriter br = null;
        try {
            br = new BufferedWriter(new FileWriter(wd + "Rapports/" + Flux_en_cours + "_" + Police_en_cours_maj + ".csv"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        StringBuilder sb = new StringBuilder();

// Append strings from array
        for (int i = 0; i < Rapport.get(0).size(); i++) {
            for (ArrayList<String> col : Rapport) {
                sb.append(col.get(i));
                sb.append(';');
            }
            sb.replace(sb.length() - 1, sb.length(), "\r\n");
//            sb.append("\r\n");
        }

        try {
            br.write(sb.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            br.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Rapport = new ArrayList<>();
        String[] rapport_cols = {"Police", "Flux", "Controle", "ID"};
        for (int i = 0; i < rapport_cols.length; i++) {
            Rapport.add(new ArrayList<>());
            Rapport.get(i).add(rapport_cols[i]);
        }
    }
    public static void log_err_save() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM HH.mm");
        LocalDateTime now = LocalDateTime.now();
        BufferedWriter br = null;
        try {
            br = new BufferedWriter(new FileWriter(wd + "Rapports/log "+dtf.format(now)+".csv"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < Log_err.get(0).size(); i++) {
            for (ArrayList<String> col : Log_err) {
                sb.append(col.get(i));
                sb.append(';');
            }
            sb.replace(sb.length() - 1, sb.length(), "\r\n");
        }

        try {
            br.write(sb.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            br.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static void temps_exec_save() {
        int nrow = Rapport_temps_exec.get(0).size();

        boolean[] rem = logvec(nrow,false);

        for (int i = 1; i < nrow; i++) {
            if (Rapport_temps_exec.get(2).get(i).equals("prep") & (i != nrow-1)) {
                for (int j = i+1; j < nrow; j++) {
                    if (Rapport_temps_exec.get(2).get(j).equals("prep") & Rapport_temps_exec.get(0).get(j).equals(Rapport_temps_exec.get(0).get(i))) {
                        double v1 = Double.parseDouble(Rapport_temps_exec.get(3).get(i));
                        double v2 = Double.parseDouble(Rapport_temps_exec.get(3).get(j));
                        double v3 = v1 + v2;
                        Rapport_temps_exec.get(3).set(i,v3 + "");
                        rem[j] = true;
                    }
                }
            }
        }
        for (int i = nrow-1; i > -1; i--) {
            if (rem[i]) {
                for (int k = 0; k < 4; k++) {
                    Rapport_temps_exec.get(k).remove(i);
                }
            }
        }

        Object[] controles_obj = unique_of(Rapport_temps_exec.get(2).toArray());
        Object[] polices_obj = unique_of(Rapport_temps_exec.get(0).toArray());
        String[] controles = Arrays.stream(controles_obj).map(Object::toString).
                toArray(String[]::new);
        String[] polices = Arrays.stream(polices_obj).map(Object::toString).
                toArray(String[]::new);
        int ncol = polices.length + 1;
        nrow = controles.length;
        ArrayList<ArrayList<String>> df = new ArrayList<>();
        df.add(new ArrayList<String>());
        for (int i = 0; i < nrow; i++) {
            df.get(0).add(controles[i]);
        }

        for (int i = 1; i < ncol; i++) {
//            if (Objects.equals(polices[i - 1], "Police")) continue;
            df.add(new ArrayList<String>());
            if (i == ncol-1) {
                df.get(i).add("total");
            } else {
                df.get(i).add(polices[i]);
            }
            for (int j = 1; j < nrow; j++) {
                df.get(i).add("");
            }
        }

        int nrow_1 = Rapport_temps_exec.get(0).size();
        for (int i = 1; i < nrow; i++) {
            for (int j = 1; j < ncol-1; j++) {
                String pol = polices[j];
                String controle = controles[i];
//                if (Objects.equals(pol, "Police") | Objects.equals(controle, "Controle")) continue;
                for (int k = 1; k < nrow_1; k++) {
                    if(Rapport_temps_exec.get(0).get(k).equals(pol) & Rapport_temps_exec.get(2).get(k).equals(controle)) {
                        df.get(j).set(i,Rapport_temps_exec.get(3).get(k));
                    }
                }
            }
            double sum = 0;
            for (int j = 1; j < ncol-1; j++) {
                if (!Objects.equals(df.get(j).get(i), "")) {
                    sum += Double.parseDouble(df.get(j).get(i));
                }
            }
            df.get(ncol-1).set(i, String.valueOf(sum));
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM HH.mm");
        LocalDateTime now = LocalDateTime.now();
        BufferedWriter br = null;
        try {
            br = new BufferedWriter(new FileWriter(wd + "Rapports/temps exec "+dtf.format(now)+".csv"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        StringBuilder sb = new StringBuilder();


        for (int i = 0; i < nrow; i++) {
            for (ArrayList<String> col : df) {
                sb.append(col.get(i));
                sb.append(';');
            }
            sb.replace(sb.length() - 1, sb.length(), "\r\n");
        }

        try {
            br.write(sb.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            br.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    // DATA
    public static ArrayList <String> not_in(String[] what, String[] where) {
        ArrayList <String> notin = new ArrayList<>();

        for (String value : what) {
            boolean check = false;
            for (String ref : where) {
                if (value.equals(ref)) {
                    check = true;
                }
            }
            if (!check) notin.add(value);
        }
        return notin;
    }
    public static ArrayList <String> not_in(String what, String[] where) {
        ArrayList <String> notin = new ArrayList<>();

        boolean check = false;
        for (String ref : where) {
            if (what.equals(ref)) {
                check = true;
            }
        }
        if (!check) notin.add(what);
        return notin;
    }

    public static boolean  check_in(String[] what, String[] where) {
        int counter = 0;
        for (String value : what) {
            for (String ref : where) {
                if (value.equals(ref)) {
                    counter++;
                    break;
                }
            }
        }
        return counter == what.length;
    }
    public static boolean  check_in(String what, String[] arr) {
        for (String where : arr) {
            if (what.equals(where)) {
                return true;
            }
        }
        return false;
    }
    public static boolean  check_in(Object what, Object[] arr) {
        for (Object where : arr) {
            if (what.equals(where)) {
                return true;
            }
        }
        return false;
    }
    public static ArrayList<Integer> get_all_occurences(String str, char c) {
        ArrayList<Integer> ind = new ArrayList<>();
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == c) {
                ind.add(i);
            }
        }
        return ind;
    }

    // VECTORS
    public static String[] swap(String[] array, int a, int b) {
        int dim = array.length;
        String[] out = new String[dim];
        System.arraycopy(array, 0, out, 0, dim);
        if (a >= dim | b >= dim | a < 0 | b < 0) {
            err("problem in swap");
            return array;
        } else {
            out[a] = array[b];
            out[b] = array[a];
        }
        return out;
    }
    public boolean[] is_in_arr ( String[] arr, String value) {
        final int len = arr.length;
        boolean[] out = new boolean[len];
        for (int i = 0; i < len; i++) {
            out[i] = arr[i].equals(value);
        }
        return out;
    }
    public static String[] filter_array_by(String[] arr, String by) {
        return filter_array_by(arr, by, false);
    }
    public static String[] filter_array_by_containing(String[] arr, String by) {
        ArrayList<String> out_list = new ArrayList<>();
        for (String s : arr) {
            if (s.contains(by)) {
                out_list.add(s);
            }
        }
        return(out_list.toArray(new String[0]));
    }
    public static String[] filter_array_by(String[] arr, String by, boolean filter_out) {
        int j = 0;
        int i = 0;
        for (; i < arr.length; i++) {
            if (Objects.equals(arr[i], by) ^ filter_out) {
                arr[j] = arr[i];
                j++;
            }
        }
        if (j == i) {
            return arr;
        }
        String[] out = new String[j];
        System.arraycopy(arr, 0, out, 0, j);
        return out;
    }
    public static Object[] keep_from_array(Object[] arr, boolean[] which) {
        int len = sum_boolean(which);
        Object[] out = new Object[len];
        int j = 0;
        for (int i = 0; i < which.length; i++) {
            if (which[i]) {
                out[j] = arr[i];
                j++;
            }
        }
        return out;
    }
    public static String[] keep_from_array(String[] arr, boolean[] which) {
        int len = sum_boolean(which);
        String[] out = new String[len];
        int j = 0;
        for (int i = 0; i < which.length; i++) {
            if (which[i]) {
                out[j] = arr[i];
                j++;
            }
        }
        return out;
    }
    public static int[] keep_from_array(int[] arr, boolean[] which) {
        int len = sum_boolean(which);
        int[] out = new int[len];
        int j = 0;
        for (int i = 0; i < which.length; i++) {
            if (which[i]) {
                out[j] = arr[i];
                j++;
            }
        }
        return out;
    }
    public static DF.Col_types[] keep_from_array(DF.Col_types[] arr, boolean[] which) {
        int len = sum_boolean(which);
        DF.Col_types[] out = new DF.Col_types[len];

        int j = 0;
        for (int i = 0; i < which.length; i++) {
            if (which[i]) {
                out[j] = arr[i];
                j++;
            }
        }
        return out;
    }
    public static int[] which(boolean[] bool) {
        int sum = sum_boolean(bool);
        if (sum == 0) {
            return null;
        }
        int[] vec = new int[sum];
        int j = 0;
        for (int i = 0; i < bool.length; i++) {
            if (bool[i]) {
                vec[j] = i;
                j++;
            }
        }
        return vec;
    }
    public static Object whichf(boolean[] bool) {
        for (int i = 0; i < bool.length; i++) {
            if (bool[i]) {
                return i;
            }
        }
        return -1;
    }
    public static boolean[] find_in_arr(Object[] arr, Object value) {
        final int len = arr.length;
        boolean[] out = new boolean[len];
        for (int i = 0; i < len; i++) {
            out[i] = arr[i].equals(value);
        }
        return out;
    }
    public static boolean[] find_in_arr2(String[] arr, String value1, String value2) {
        final int len = arr.length;
        boolean[] out = new boolean[len];
        for (int i = 0; i < len; i++) {
            out[i] = arr[i].equals(value1) | arr[i].equals(value2);
        }
        return out;
    }
    public static boolean[] find_in_arr3(String[] arr, String value1, String value2, String value3) {
        final int len = arr.length;
        boolean[] out = new boolean[len];
        for (int i = 0; i < len; i++) {
            out[i] = arr[i].equals(value1) | arr[i].equals(value2) | arr[i].equals(value3);
        }
        return out;
    }
    public static int find_in_arr_first_index(Object[] arr, Object value) {
        final int len = arr.length;
        int out = -1;
        for (int i = 0; i < len; i++) {
            if (arr[i] == null) continue;
            if (arr[i].equals(value)) {
                out = i;
                break;
            }
        }
        return out;
    }
    public static int which_contains_first_index(String[] arr, String value) {
        final int len = arr.length;
        int out = -1;
        for (int i = 0; i < len; i++) {
            if (arr[i] == null) continue;
            if (arr[i].contains(value)) {
                out = i;
            }
        }
        return out;
    }
    public static int which_contains_first_index(Object[] arr, String value) {
        final int len = arr.length;
        int out = -1;
        for (int i = 0; i < len; i++) {
            if (arr[i] == null) continue;
            if (((String) arr[i]).contains(value)) {
                out = i;
            }
        }
        return out;
    }
    public static Object[] unique_of(Object[] arr) {
        if (arr.length == 1) return arr;
        Set<Object> hash = new LinkedHashSet<>(Arrays.asList(Optional.of(arr).orElse(new Object[0]))); //ofNullable bilo ranshe hz
        return hash.toArray(new Object[0]);
    }
    public static String[] unique_of(String[] arr) {
        if (arr.length == 1) return arr;
        Set<String> hash = new LinkedHashSet<>(Arrays.asList(Optional.of(arr).orElse(new String[0]))); //ofNullable bilo ranshe hz
        return hash.toArray(new String[0]);
    }
    public static Integer[] unique_of(Integer[] arr) {
        if (arr.length == 1) return arr;
        Set<Integer> hash = new LinkedHashSet<>(Arrays.asList(Optional.of(arr).orElse(new Integer[0]))); //ofNullable bilo ranshe hz
        return hash.toArray(new Integer[0]);
    }
    public static int[] unique_of(int[] arr) {
        if (arr.length == 1) return arr;
        HashMap<Integer, Integer> hashmap = new HashMap<Integer, Integer>();
        for (int j = 0; j < arr.length; j++) {
            hashmap.put(arr[j], j);
        }
        Object[] key_arr = hashmap.keySet().toArray();
        int[] int_arr = new int[key_arr.length];
        for (int i = 0; i < key_arr.length; i++) {
            int_arr[i] = (int) key_arr[i];
        }
        return int_arr;
    }
    public static boolean[] unique_bool(Object[] arr) {
        Set<Object> hash = new LinkedHashSet<>();
        boolean[] out = new boolean[arr.length];
        for (int i = 0; i < arr.length; i++) {
            out[i] = hash.add(arr[i]);
        }
        return out;
    }
    public static int sum_boolean(boolean[] vector_boolean) {
        int sum = 0;
        for (boolean b : vector_boolean) {
            sum += b ? 1 : 0;
        }
        return sum;
    }
    public static boolean[] b_and(boolean[] arr1, boolean[] arr2) {
        assert (arr1.length == arr2.length);
        boolean[] out = new boolean[arr1.length];
        for (int i = 0; i < arr1.length; i++) {
            out[i] = arr1[i] & arr2[i];
        }
        return out;
    }
    public static boolean[] a_and_b_and_c(boolean[] arr1, boolean[] arr2, boolean[] arr3) {
        assert (arr1.length == arr2.length);
        boolean[] out = new boolean[arr1.length];
        for (int i = 0; i < arr1.length; i++) {
            out[i] = arr1[i] & arr2[i] & arr3[i];
        }
        return out;
    }
    public static Integer[] push_to_end_ind(String[] arr, String[] pushed) {
        Integer[] out = new Integer[arr.length];
        int j = 0;
        int k = arr.length - pushed.length;
        for (int i = 0; i < arr.length; i++) {
            if (!in(arr[i], pushed)) {
                out[j] = i;
                j++;
            } else {
                out[k] = i;
                k++;
            }
        }
        return out;
    }
    public static Integer[] arr_concat(Integer[] arr1, Integer[] arr2) {
        int fal = arr1.length;
        int sal = arr2.length;
        Integer[] result = new Integer[fal + sal];
        System.arraycopy(arr1, 0, result, 0, fal);
        System.arraycopy(arr2, 0, result, fal, sal);
        return result;
    }
    public static Object[] arr_concat(Object[] arr1, Object[] arr2) {
        int fal = arr1.length;
        int sal = arr2.length;
        Object[] result = new Object[fal + sal];
        System.arraycopy(arr1, 0, result, 0, fal);
        System.arraycopy(arr2, 0, result, fal, sal);
        return result;
    }
    public static Object[] arr_merge(Object[] arr1, Object[] arr2) {
        int fal = arr1.length;
        int sal = arr2.length;
        Object[] result = new Object[fal + sal];
        System.arraycopy(arr1, 0, result, 0, fal);
        System.arraycopy(arr2, 0, result, fal, sal);
        result = unique_of(result);
        return result;
    }
    public static int[] arr_merge(int[] arr1, int[] arr2) {
        int fal = arr1.length;
        int sal = arr2.length;
        int[] result = new int[fal + sal];
        System.arraycopy(arr1, 0, result, 0, fal);
        System.arraycopy(arr2, 0, result, fal, sal);
        result = unique_of(result);
        return result;
    }
    public static boolean in(Object str, Object[] arr) {
        for (Object s : arr) {
            if (s.equals(str)) {
                return true;
            }
        }
        return false;
    }
    public static boolean arr1_contains_arr2(String[] arr1, String[] arr2) {
        for (String s : arr2) {
            if (!in(s, arr1)) {
                return false;
            }
        }
        return true;
    }
    public static Integer[] sortIndices(int[] input, boolean descending) {

        Integer[] indices = new Integer[input.length];

        for (int i = 0; i < input.length; i++)
            indices[i] = i;
        if (descending) {
            Arrays.sort(indices, new Comparator<Integer>() {
                @Override
                public int compare(Integer o1, Integer o2) {
                    return input[o2] - input[o1];
                }
            });
        } else {
            Arrays.sort(indices, new Comparator<Integer>() {
                @Override
                public int compare(Integer o1, Integer o2) {
                    return input[o1] - input[o2];
                }
            });
        }

        return indices;
    }
    public static Integer[] sort_by_2_vars(int[] arr1, int[] arr2, int interval, int total) {
        int[] var1 = Arrays.copyOf(arr1, arr1.length);
        int[] var2 = Arrays.copyOf(arr2, arr2.length);

        Integer[] first_sort = sortIndices(var1, false);
        Integer[] second_sort = new Integer[0];
        var2 = shuffle(var2, first_sort);
        Arrays.sort(var1);

        int len = var1.length;
        int interval_counter = 1;
        int debut = 0;
        int fin;
        for (int i = 0; i < len; i++) {
            if (i == len - 1) {
                fin = i + 1;
                int[] var2_tranche = Arrays.copyOfRange(var2, debut, fin);
                second_sort = arr_concat(second_sort, sortIndices(var2_tranche, false));
                break;
            }
            if (var1[i] * 100.0 / total > interval * interval_counter) {
                fin = i - 1;
                int[] var2_tranche = Arrays.copyOfRange(var2, debut, fin);
                second_sort = arr_concat(second_sort, sortIndices(var2_tranche, false));
                debut = fin + 1;
                interval_counter++;
            }

        }
        for (int i = 0; i < len; i++) {
            second_sort[i] = first_sort[second_sort[i]];
        }

        return second_sort;
    }
    public static Object[] shuffle(Object[] arr, Integer[] idx) {
        int len = arr.length;
        Object[] out = new Object[len];
        for (int i = 0; i < len; i++) {
            out[i] = arr[idx[i]];
        }
        return out;
    }
    public static int[] shuffle(int[] arr, Integer[] idx) {
        int len = arr.length;
        int[] out = new int[len];
        for (int i = 0; i < len; i++) {
            out[i] = arr[idx[i]];
        }
        return out;
    }
    public static String[] shuffle(String[] arr, Integer[] idx) {
        int len = arr.length;
        String[] out = new String[len];
        for (int i = 0; i < len; i++) {
            out[i] = arr[idx[i]];
        }
        return out;
    }
    public static Integer[] shuffle(Integer[] arr, Integer[] idx) {
        int len = arr.length;
        Integer[] out = new Integer[len];
        for (int i = 0; i < len; i++) {
            out[i] = arr[idx[i]];
        }
        return out;
    }
    public static LocalDate to_Date(Date input) {
        return input.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
    public static void write_csv(Integer[] arr) {
        BufferedWriter br = null;
        try {
            br = new BufferedWriter(new FileWriter(wd + "tester.csv"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        StringBuilder sb = new StringBuilder();

        for (Object element : arr) {
            sb.append(element);
            sb.append("\n");
        }

        try {
            br.write(sb.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            br.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static void grilles_collect(String path) throws IOException {
        path = wd + path;
        InputStream is = Files.newInputStream(new File(path).toPath());
        Workbook workbook = StreamingReader.builder().rowCacheSize(1).bufferSize(4096).open(is);
        List<String> sheetNames = new ArrayList<>();
        List<String> sheetNames_read = new ArrayList<>();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String name = workbook.getSheetName(i);
            if (name.charAt(0) == 'C') {
                sheetNames_read.add(name);
                if (name.charAt(1) == 'S') {
                    sheetNames.add(name.replace("S", ""));
                } else {
                    sheetNames.add(name);
                }
            }
        }
//        System.out.println(sheetNames_read);
//        System.out.println(sheetNames);
        int sheet_ind = 0;
        for (String s : sheetNames) {
//            System.out.println(s);
//            System.out.println(sheetNames_read.get(sheet_ind));
            CSVWriter writer = (CSVWriter) new CSVWriterBuilder(new FileWriter(path_grilles + s + ".csv"))
                    .withSeparator('\t')
                    .build();
            DF grille = new DF(path, sheetNames_read.get(sheet_ind), true, true);
            grille.dna();

            writer.writeNext(grille.header);
            for (int i = 0; i < grille.nrow; i++) {
                String[] vec = new String[grille.ncol];
                for (int j = 0; j < grille.ncol; j++) {
                    vec[j] = grille.r(i)[j].toString();
                }
                writer.writeNext(vec);
            }
            writer.close();
            sheet_ind++;

        }
    }
    public static void get_grilles() throws IOException {
        File f = new File(path_grilles);
        String[] grilles = f.list();
        if (grilles == null) {
            System.out.println("grilles empty!");
            return;
        }
        for (String g : grilles) {
            String name = g.substring(0, g.indexOf('.'));
            DF df = new DF(path_grilles + g, '\t',  true);
            df.dna();
            grilles_G.put(name, df);
        }
    }
    public static void err(String msg) {
//        System.err.println(new Throwable().getStackTrace()[0].getLineNumber());
        System.out.println(msg);
        System.out.println(Police_en_cours);
        System.out.println(Controle_en_cours);
    }
    public static void err_simple(String msg) {
        System.out.println(msg + " " + Police_en_cours + " " + Flux_en_cours);
    }
    public static boolean[] logvec(int dim, boolean values) {
        boolean[] out = new boolean[dim];
        Arrays.fill(out, values);
        return out;
    }
}