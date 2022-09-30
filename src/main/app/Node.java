package main.app;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static main.app.App.*;
import static main.app.App.b_and;

public class Node {
    public String value;
    public Node[] child_arr;
    public boolean[] vec;
    public int level = 0;

    public Node(String value, Node[] child_arr) {
        this.value = value;
        this.child_arr = child_arr;
    }
    public Node(DF base) {
        value = "root";
        vec = new boolean[base.nrow];
        Arrays.fill(vec, true);
        this.getchilds(base,this.vec);

    }
    public Node() {

    }
    public void getchilds(DF base, boolean[] vec) {
//        this.vec = b_and(this.vec,unique(cut(base.c(level),this.vec)));
        if (this.level < base.ncol) {
            String[] childs = wunique(cut(base.c(this.level),this.vec));
            int len = childs.length;
            System.out.println(len);
            this.child_arr = new Node[len];
            for (int i = 0; i < len; i++) {
                this.child_arr[i] = new Node();
                this.child_arr[i].value = childs[i];
                this.child_arr[i].level = this.level+1;
                this.child_arr[i].vec = b_and(this.vec, find_in_arr(base.c(this.level),childs[i]));
//                System.out.println(Arrays.toString(base.c(this.level)));
//                System.out.println("childs: " + childs[i]);
//                System.out.println(Arrays.toString(b_and(this.vec, find_in_arr(base.c(this.level),childs[i]))));
                this.child_arr[i].getchilds(base,this.child_arr[i].vec);
            }
//            for (Node s : this.child_arr) {
//                System.out.print(s.value + " ");
//            }
//            System.out.println();

        }

    }
//    public void getchilds(String[] vec) {
//        int len = vec.length;
//        this.child_arr = new Node[len];
//        for (int j = 0; j < len; j++) {
//            this.child_arr[j] = new Node(vec[j]);
//        }
//    }

}
