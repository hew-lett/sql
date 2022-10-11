package main.app;

import java.util.*;

import static main.app.App.*;
import static main.app.App.b_and;

public class Node {
    public static Double sizes = 0d;
    public String column;
    public Object value;
    public Node[] child_arr;
    public boolean[] vec;
//    public int level = 0;
    public static String[] order;
    public int order_pos;
    public int size;
    public String type;
    public Node(String value, Node[] child_arr) {
        this.value = value;
        this.child_arr = child_arr;
    }
    public Node(DF base, String[] order) {
        value = "root";
        vec = new boolean[base.nrow];
        Arrays.fill(vec, true);
        Node.order = order;
        order_pos = 0;
        getchilds(base);

    }
    public Node() {

    }
    public void getchilds(DF base) {
        if (order_pos < order.length) {
            column = order[order_pos];
            Object[] childs = unique_of(keep_from_array(base.c(column),vec));
//            for (int ii = 0; ii < childs.length; ii++) {
//                if (childs[ii] == null) return 1;
//            }
//            Node.sizes = sizes + childs.length;
            System.out.println(Node.sizes);
            DF.Col_types type = base.coltypes[find_in_arr_first_index(base.header, column)];
            size = childs.length;
            child_arr = new Node[size];
            for (int i = 0; i < size; i++) {
                child_arr[i] = new Node();
                child_arr[i].value = childs[i];
                child_arr[i].type = Grille_columns.get_type(column);
                child_arr[i].order_pos = order_pos+1;
//                System.out.println(vec.length);
                boolean[] x = find_in_arr(base.c(column),childs[i]);
//                System.out.println(x.length);
                child_arr[i].vec = b_and(vec, x);
                child_arr[i].getchilds(base);
            }
        }
//        return 0;
    }
//    public boolean find(String[] row, String[] header) {
//        if (child_arr.length==0) return true;
//        String cell = row[find_in_arr_first_index(header, column)];
//        switch(type) {
//            case DEFAULT:
//                if (cell.equals("")) {
//                    for (Node n : child_arr) {
//                        if (n.value.equals(cell) | n.value.equals("N.A.") | n.value.equals("{ vide }")) {
//                            return n.find(row,header);
//                        }
//                    }
//                } else {
//                    for (Node n : child_arr) {
//                        if (n.value.equals(cell) | n.value.equals("N.A.") | n.value.equals("{ renseigné }")) {
//                            return n.find(row,header);
//                        }
//                    }
//                }
//                break;
//            case DCBB:
//
//
//
//        }
//        return false;
//    }
//
//    public boolean get_type() {
//        int i = switch (this.value) {
//            case "Valeur_Catalogue Borne haute":
//
//        }
//    }


//    public void keep_from_node (boolean[] vec) {
//        value = "root";
//        childs = keep_from_array(childs, vec);
//        Node[] temp = new Node[sum_boolean(vec)];
//        int j = 0;
//        for (int i = 0; i < size; i++) {
//            if(vec[i]) {
//                temp[j] = child_arr[i];
//                j++;
//            }
//        }
//        size = j;
//        child_arr = temp;
//    }

}
