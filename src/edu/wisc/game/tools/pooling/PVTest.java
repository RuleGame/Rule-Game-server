package edu.wisc.game.tools.pooling;

import java.io.*;
import java.util.*;
//import java.util.regex.*;
//import java.text.*;

//import javax.persistence.*;

//import org.apache.commons.math3.optim.*;
import org.apache.commons.math3.stat.inference.*;


import edu.wisc.game.util.*;
/*
import edu.wisc.game.rest.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.saved.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.parser.RuleParseException;
*/

/** Testing Apache Commons methods for MW and KS p-values.
 */
public class PVTest {


    static private void usage() {
	usage(null);
    }
    static private void usage(String msg) {
	System.err.println("Usage:\n");
	System.err.println("Pvtest x1 x2 x3 ...  , y1 y2 y3 ....\n");
	if (msg!=null) 	System.err.println(msg + "\n");
	System.exit(1);
    }

    static public void main(String [] argv) {
	double[][] x = new double[2][];
	Vector<Double> v = new Vector<>();
	int k = 0;
	for(int ja=0; ja<argv.length; ja++) {
	    String a = argv[ja];
	    boolean hasComma = a.endsWith(",");
	    if (hasComma) a = a.substring(0, a.length()-1);
	    if (a.length()>0) v.add( Double.parseDouble(a));
	    if (hasComma) {
		if (k>0) usage("Error: only one comma is allowed");
		x[k++] = moveToArray(v);
	    }
	}
	if (k!=1) usage("Error: no comma found");
	x[k++] = moveToArray(v);

	MannWhitneyUTest mw = new MannWhitneyUTest();
	//double	u = mw.mannWhitneyU(x[0], x[1]);
	double	mwp = mw.mannWhitneyUTest(x[0], x[1]);
	//System.out.println("mannWhitneyU(" + fmtArg(x)+")=" + u);
	System.out.println("mannWhitneyUTest(" + fmtArg(x)+")=" + mwp);

	KolmogorovSmirnovTest ks = new 	KolmogorovSmirnovTest();
	double ksp = ks.kolmogorovSmirnovTest(x[0],x[1]);
	System.out.println("kolmogorovSmirnovTest(" + fmtArg(x)+")=" + ksp);
	
    }
    
    private static double[] moveToArray(Vector<Double> v) {
	if (v.size()==0) usage("Error: empty list");
	double x[] = new double[v.size()];
	int i=0;
	for(Double z: v) x[i++] = z;
	v.clear();
	return x;
    }

    private static String fmtArg(double x[][]) {
	String s = "";
	for(double[] q: x) {
	    if (s.length()>0) s += ", ";
	    s += "["+Util.joinNonBlank(" ", q) +"]";
	}
	return s;
    }
    
    
}
