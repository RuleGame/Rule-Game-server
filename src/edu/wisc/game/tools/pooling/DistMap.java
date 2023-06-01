package edu.wisc.game.tools.pooling;


//import java.io.*;
import java.util.*;
import  edu.wisc.game.tools.pooling.Clustering.Node;

class DistMap extends HashMap<String,Double> {

    private String pairLabel(Node n1, Node n2) {
	return n1.label  +"," + n2.label;
    }


    //	dist = new HashMap<>();
    Double get2(Node n1, Node n2) {
	return get(pairLabel(n1,n2));
    }
    Double put2(Node n1, Node n2, Double d) {
	    return put(pairLabel(n1,n2), d);
    }
    Double put2(String  n1, String n2, Double d) {
	    return put(n1 + "," + n2, d);
    }


}
    

