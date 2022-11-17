package main.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

import static main.app.App.*;

public class Net {
//    public static DF base;
    public ArrayList<Object[]> refer;
    public DF.Col_types[] types;
    public Tree tree;
    Net(DF base, DF.Col_types[] types, String[] calc_cols) {
        System.out.println("ncol dans une grille" + base.ncol);

        this.types = types;
        int[] na = new int[base.ncol];
        int[] uniques = new int[base.ncol];
        refer = new ArrayList<>(base.ncol);
        for (int i = 0; i < base.ncol; i++) {
            refer.add(i, unique_of(base.c(i)));
        }
        for (int i = 0; i < base.ncol; i++) {
            uniques[i] = refer.get(i).length;
            switch (types[i]) {
                case STR:
                    for (int j = 0; j < base.nrow; j++) {
                        if (base.c(i)[j].equals(NA_STR)) {
                            na[i]++;
                        }
                    }
                    break;
                case DBL:
                    for (int j = 0; j < base.nrow; j++) {
                        if (base.c(i)[j].equals(NA_DBL)) {
                            na[i]++;
                        }
                    }
                    break;
                case DAT:

                    for (int j = 0; j < base.nrow; j++) {
                        if (base.c(i)[j].equals(NA_DAT)) {
                            na[i]++;
                        }
                    }
                    break;

            }
        }
        boolean[] vec = new boolean[base.ncol];
        Arrays.fill(vec,true);
        for (int i = 0; i < base.ncol; i++) {
            if (na[i] == base.nrow & uniques[i] == 1) {
                vec[i] = false;
            }
        }
        na = keep_from_array(na,vec);
        uniques = keep_from_array(uniques,vec);
        base.keep_cols(vec);

        Integer[] idx = sort_by_2_vars(uniques, na, 5, base.nrow);

        System.out.println(Arrays.toString(idx));
        na = shuffle(na,idx); // mojno udalit
        uniques = shuffle(uniques,idx); // mojno udalit
        String[] header_temp = base.header;
        base.header = shuffle(base.header, idx);
        for (int ii = 0; ii < base.ncol; ii++) {
            System.out.println(base.header[ii] + " " + na[ii] + " " + uniques[ii]);
        }
        Integer[] calc_idx = push_to_end_ind(base.header,calc_cols);
        na = shuffle(na,calc_idx); // mojno udalit
        uniques = shuffle(uniques,calc_idx); // mojno udalit
        base.header = shuffle(base.header, calc_idx);
        idx = shuffle(idx,calc_idx);

        for (int ii = 0; ii < base.ncol; ii++) {
            System.out.println(base.header[ii] + " " + na[ii] + " " + uniques[ii]);
        }
        ArrayList<Object[]> refer_2 = new ArrayList<>(base.ncol);
        Object[] obj = new Object[1];
        for (int i = 0; i < base.ncol; i++) {
            refer_2.add(i, obj);
        }
        for (int i = 0; i < base.ncol; i++) {
            refer_2.set(i, refer.get(idx[i]));
        }
        DF out = new DF(refer_2); // tak ne rabotaet!
        out.header = base.header;
        base.header = header_temp;
        out.ncol = base.ncol;
        out.print_cols();
        Tree t = new Tree(base,out);
    }

}
