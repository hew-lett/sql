package main.app;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static main.app.App.*;
import static main.app.App.b_and;

public class Node {
    public String column;
    public String value;
    public Node[] child_arr;
    public boolean[] vec;
//    public int level = 0;
    public static String[] order;
    public int order_pos;
    public int size;
    public Special_columns_c811 type;
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
        getchilds(base,vec,0);

    }
    public Node() {

    }
    public void getchilds(DF base, boolean[] vec, int order_col) {
        if (order_pos < order.length) {
            column = order[order_pos];
            String[] childs = wunique(cut(base.c(column),vec));
            Arrays.sort(childs);
            size = childs.length;
            child_arr = new Node[size];
            for (int i = 0; i < size; i++) {
                child_arr[i] = new Node();
                child_arr[i].value = childs[i];
                child_arr[i].type = Special_columns_c811.get(column);
                child_arr[i].order_pos = order_pos+1;
                child_arr[i].vec = b_and(vec, find_in_arr(base.c(column),childs[i]));
                child_arr[i].getchilds(base,child_arr[i].vec,child_arr[i].order_pos);
            }
        }
    }
    public boolean find(String[] row, String[] header) {
        if (child_arr.length==0) return true;
        String cell = row[find_in_arr_first(header, column)];
        switch(type) {
            case DEFAULT:
                if (cell.equals("")) {
                    for (Node n : child_arr) {
                        if (n.value.equals(cell) | n.value.equals("N.A.") | n.value.equals("{ vide }")) {
                            return n.find(row,header);
                        }
                    }
                } else {
                    for (Node n : child_arr) {
                        if (n.value.equals(cell) | n.value.equals("N.A.") | n.value.equals("{ renseigné }")) {
                            return n.find(row,header);
                        }
                    }
                }
                break;
            case DCBB:



        }
        return false;
    }
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
