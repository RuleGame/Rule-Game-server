package edu.wisc.game.math;

import java.io.*;
import java.util.*;
import java.text.*;

import javax.persistence.*;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.formatter.*;
import edu.wisc.game.tools.MwByHuman;
import edu.wisc.game.tools.MwByHuman.MwSeries;


/** Represents a thing to be compared using the Mann-Whitney-test:
    either an algo (which is to be compared with other algos based on
    its performance on some rule set), or a rule set (which is being
    compare to other rule set based on how a particular algo, or the
    set of humans, perform on it).
     */
public class Comparandum implements Comparable<Comparandum> {
	/** The name of this algo or this rule set, as the case may be */
	final String key;
	final boolean learned;
	final int[] a;
	final MlcEntry[] mlc;
	final MwSeries[] humanSer;
	private double ev;

	/** Initializes a Comparandum based on a set of MlcEntry objects */
	Comparandum(String _name, boolean _learned, MlcEntry[] z) {
	    mlc = z;
	    humanSer=null;
	    learned = _learned;
	    //cm.name = w[j0][0].getKey();
	    key = _name;
	    a = new int[ z.length ];

	    for(int k=0; k<z.length; k++) {
		a[k] = z[k].getTotalErrors();
	    }
	}

	/** Creates a Comparandum for a rule set, based on an array of
	    MwSeries objects, each of which describes the performance
	    of a human on the same rule set */
	Comparandum(String ruleSetName, MwSeries[] z) {
	    learned = true; // with human populations, this flag does not make much sense, as you never have *everybody* in the population learn
	    key = ruleSetName;
	    a = new int[ z.length ];
	    for(int k=0; k<z.length; k++) {
		a[k] = z[k].getMStar();
	    }
	    mlc=null;
	    humanSer=z;
	}
	
	static int[][] asArray(Comparandum []q) {
	    int[][] a = new int[q.length][];
	    for(int j=0; j<q.length; j++) a[j] = q[j].a;
	    return a;
	}

	void setEv(double _ev) { ev= _ev; }
	
	public int	compareTo(Comparandum o) {
	    return (int)Math.signum(	o.ev - ev);
	}


    /** Creates a list of comparanda for a number of rule sets, based
	on human performance data for these rule sets.
       @return {learnedOnes[], nonLearnedOnes[]}
     */
    public static Comparandum[][] mkMlcComparanda(MwSeries[] res) {
	
	// distinct keys
	Vector<String> keys = new Vector<>();
	// maps each key to its position in the "keys" array
	HashMap<String,Integer> keysOrder = new HashMap<>();
	// how many runs have been done for each key
	Vector<Integer> counts = new Vector<>();
	    
	// How many distinct keys (algo nicknames or rule set names)
	int n = 0;
	for(MwSeries ser: res) {
	    String key  = ser.ruleSetName;
	    boolean isNew = (keysOrder.get(key)==null);
	    
	    int j = isNew? n++ :  keysOrder.get(key);
	    if (isNew) {
		keysOrder.put( key, j);
		keys.add(key);
		counts.add(1);
	    } else {		
		int m = counts.get(j);
		counts.set(j,m+1);
	    }
	}

	// All entries (human series) separated by key (rule set name)
	MwSeries [][]w = new MwSeries[n][];
	for(int j=0; j<n; j++) w[j] = new MwSeries[ counts.get(j) ];
	int p[] = new int[n];
	for(MwSeries ser: res) {
	    int j = keysOrder.get( ser.ruleSetName);
	    MwSeries [] row = w[j];
	    int k = p[j]++;
	    row[k] = ser;	    
	}

	Comparandum[] learnedOnes=new Comparandum[n],  dummy = new Comparandum[0];
	    
	for(int j=0; j<n; j++) {
	    String key = keys.get(j);
	    learnedOnes[j]=new Comparandum(key, w[j]);
	}

	Comparandum [][] allComp = {learnedOnes, dummy};
	return allComp;
	
    }

    
}
