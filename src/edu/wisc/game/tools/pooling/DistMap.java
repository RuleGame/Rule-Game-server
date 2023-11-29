package edu.wisc.game.tools.pooling;


import java.util.*;
import  edu.wisc.game.tools.pooling.Clustering.Node;

/** A structure that can be stored the elements of a sparse matrix
    whose rows and columns are identified by alphanumeric labels
    (Node.label), rather than integer values. This is implemented as a
    HashMap where the key is a string in the form
    "rowLabel,columnLabel".
 */
class DistMap extends HashMap<String,Double> {
    /** Creates the key of the HashMap entry that will store the
	vaue corresponding to a pair of Node objects. */
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
    

