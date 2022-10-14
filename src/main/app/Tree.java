package main.app;

import org.w3c.dom.ls.LSOutput;

import java.util.ArrayList;
import java.util.Arrays;

import static main.app.App.*;

public class Tree {
    public int value;
    public Tree[] branches;
    public static DF refer;
    public boolean[] vec;
    public int level = 0;
    public static DF base;
    Tree (DF base,  DF refer) {
        System.out.println("initializing tree");
        Tree.base = base;
        Tree.refer = refer;
        this.vec = new boolean[base.nrow];
        Arrays.fill(this.vec,true);
        this.getchilds();
    }
    Tree () {

    }
    public void getchilds() {
        System.out.println("getchilds");
//        System.out.println(Arrays.toString(refer.header));
//        System.out.println(Arrays.toString(refer.r(0)));
//        System.out.println(Arrays.toString(base.header));
//        System.out.println(Arrays.toString(base.r(0)));
        if (this.level == Tree.refer.ncol) return;
        String colname = refer.header[this.level];
        System.out.println(colname);
        Object[] col_ref = refer.c(colname);
        Object[] col_base = base.c(colname);
//        System.out.println(Arrays.toString(col_ref));
//        System.out.println(Arrays.toString(col_base));
        Object[] childs = unique_of(keep_from_array(base.c(colname), vec));
        int len = col_ref.length;
        System.out.println("col_ref");
        System.out.println(Arrays.toString(col_ref));
        System.out.println("childs");
        System.out.println(Arrays.toString(childs));

        this.branches = new Tree[len];
        int ind = 0;
        for (int i = 0; i < len; i++) {
            System.out.println("child value " + col_ref[i]);
            if (in(col_ref[i], childs)) {
                this.branches[ind] = new Tree();
                this.branches[ind].level = this.level + 1;
                System.out.println(i);
                this.branches[ind].value = i;
                this.branches[ind].vec = b_and(this.vec,find_in_arr(col_base,col_ref[i]));
                this.branches[ind].getchilds();
                ind++;
            }
        }
        System.out.println();

    }
}
